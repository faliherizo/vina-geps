package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.ActionCorroboration;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.CorroborationTemps;
import ca.vina.nexu.effectifs.domaine.EcritureFeuillesTempsPort;
import ca.vina.nexu.effectifs.domaine.FeuilleTemps;
import ca.vina.nexu.effectifs.domaine.LectureFeuillesTempsPort;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs;
import ca.vina.nexu.effectifs.domaine.SourcesTempsEffectifsPort;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Adaptateur SQLite des feuilles de temps : {@code eff_temps_parametres}, {@code eff_feuilles}, {@code eff_segments_temps},
 * {@code eff_feuille_decisions} ; et les lectures croisées sur la planification (quarts publiés ou clos) et la présence
 * (pointages non rejetés) que la feuille exige sur une période entière.
 */
@Repository("effectifsDepotFeuillesTemps")
public class DepotFeuillesTemps implements LectureFeuillesTempsPort, EcritureFeuillesTempsPort, SourcesTempsEffectifsPort {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<TempsEffectifs.Anomalie>> ANOMALIES = new TypeReference<>() { };
    private static final String SELECT = "SELECT f.*, r.nom, r.matricule FROM eff_feuilles f JOIN eff_ressources r ON r.id = f.ressourceId AND r.tenantId = f.tenantId";
    private static final int ACTIF = 1;
    private static final int INACTIF = 0;

    private final JdbcClient jdbc;

    /** Les schémas lus (planification, présence) sont injectés pour exister avant la première requête croisée. */
    public DepotFeuillesTemps(JdbcClient jdbc, SchemaTempsEffectifs temps, SchemaPresenceEffectifs presence, SchemaPlanification planification) {
        this.jdbc = jdbc;
    }

    // ── Lecture ──────────────────────────────────────────────────────────────

    private static FeuilleTemps feuille(ResultSet rs, int i) throws SQLException {
        return new FeuilleTemps(rs.getString("id"), rs.getString("ressourceId"), rs.getString("uniteCode"), rs.getString("debut"), rs.getString("fin"),
                TempsEffectifs.StatutFeuille.valueOf(rs.getString("statut")), rs.getInt("palier"), rs.getInt("paliersRequis"), rs.getString("soumiseLe"),
                rs.getString("approuveeLe"), rs.getString("nom"), rs.getString("matricule"));
    }

    private static SegmentStocke segment(ResultSet rs, int i) throws SQLException {
        return new SegmentStocke(rs.getString("id"), rs.getString("tenantId"), rs.getString("feuilleId"), rs.getString("placeId"), rs.getString("date"),
                rs.getString("debut"), rs.getString("fin"), rs.getString("typeHeureCode"), TempsEffectifs.Source.valueOf(rs.getString("source")),
                rs.getInt("version"), rs.getString("remplaceId"), rs.getInt("actif") == ACTIF, rs.getString("motif"), rs.getString("creeLe"),
                rs.getString("creePar"), rs.getString("sourceExterne"), rs.getString("confirmeLe"), rs.getString("confirmePar"));
    }

    @Override
    public Optional<TempsEffectifs.Parametres> parametres(String tenantId) {
        return jdbc.sql("SELECT seuilJourHeures, seuilSemaineHeures, paliersApprobation, toleranceEcartMinutes, toleranceCorroborationMinutes, "
                        + "actionNonCorrobore, actionSourcesDivergentes FROM eff_temps_parametres WHERE tenantId = ?")
                .params(tenantId).query((rs, i) -> new TempsEffectifs.Parametres(rs.getInt("seuilJourHeures"), rs.getInt("seuilSemaineHeures"),
                        rs.getInt("paliersApprobation"), rs.getInt("toleranceEcartMinutes"), rs.getInt("toleranceCorroborationMinutes"),
                        ActionCorroboration.valueOf(rs.getString("actionNonCorrobore")), ActionCorroboration.valueOf(rs.getString("actionSourcesDivergentes"))))
                .optional();
    }

    @Override
    public Optional<FeuilleStockee> feuille(String tenantId, String id) {
        return jdbc.sql(SELECT + " WHERE f.tenantId = ? AND f.id = ?").params(tenantId, id)
                .query((rs, i) -> new FeuilleStockee(feuille(rs, i), anomalies(rs.getString("instantane")))).optional();
    }

    private static List<TempsEffectifs.Anomalie> anomalies(String json) {
        return json == null ? null : JSON.readValue(json, ANOMALIES);
    }

    @Override
    public Optional<String> feuilleDe(String tenantId, String ressourceId, String debut) {
        return jdbc.sql("SELECT id FROM eff_feuilles WHERE tenantId = ? AND ressourceId = ? AND debut = ?").params(tenantId, ressourceId, debut)
                .query(String.class).optional();
    }

    @Override
    public List<FeuilleTemps> deLaRessource(String tenantId, String ressourceId) {
        return jdbc.sql(SELECT + " WHERE f.tenantId = ? AND f.ressourceId = ? ORDER BY f.debut DESC").params(tenantId, ressourceId)
                .query(DepotFeuillesTemps::feuille).list();
    }

    @Override
    public List<FeuilleTemps> lister(String tenantId, String statut, String debut) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE f.tenantId = ?");
        List<Object> params = new ArrayList<>(List.of(tenantId));
        condition(sql, params, "f.statut = ?", statut);
        condition(sql, params, "f.debut = ?", debut);
        return jdbc.sql(sql.append(" ORDER BY f.debut DESC, r.nom").toString()).params(params).query(DepotFeuillesTemps::feuille).list();
    }

    private static void condition(StringBuilder sql, List<Object> params, String clause, String valeur) {
        if (valeur != null) {
            sql.append(" AND ").append(clause);
            params.add(valeur);
        }
    }

    @Override
    public List<SegmentStocke> segmentsActifs(String tenantId, String feuilleId) {
        return jdbc.sql("SELECT * FROM eff_segments_temps WHERE tenantId = ? AND feuilleId = ? AND actif = ? ORDER BY debut").params(tenantId, feuilleId, ACTIF)
                .query(DepotFeuillesTemps::segment).list();
    }

    @Override
    public Optional<SegmentStocke> segment(String tenantId, String feuilleId, String id) {
        return jdbc.sql("SELECT * FROM eff_segments_temps WHERE tenantId = ? AND feuilleId = ? AND id = ?").params(tenantId, feuilleId, id)
                .query(DepotFeuillesTemps::segment).optional();
    }

    @Override
    public Set<String> placesActives(String tenantId, String feuilleId) {
        return new LinkedHashSet<>(jdbc.sql("SELECT placeId FROM eff_segments_temps WHERE tenantId = ? AND feuilleId = ? AND actif = ? AND placeId IS NOT NULL")
                .params(tenantId, feuilleId, ACTIF).query(String.class).list());
    }

    @Override
    public List<DecisionFeuille> decisions(String tenantId, String feuilleId) {
        return jdbc.sql("SELECT palier, decision, motif, decideLe, decidePar FROM eff_feuille_decisions WHERE tenantId = ? AND feuilleId = ? ORDER BY decideLe, id")
                .params(tenantId, feuilleId).query((rs, i) -> new DecisionFeuille(rs.getInt("palier"), TempsEffectifs.Decision.valueOf(rs.getString("decision")),
                        rs.getString("motif"), rs.getString("decideLe"), rs.getString("decidePar"))).list();
    }

    // ── Sources (planification, présence) ────────────────────────────────────

    @Override
    public List<QuartBrut> quartsPlanifies(String tenantId, String ressourceId, String du, String au) {
        return jdbc.sql("SELECT p.id AS placeId, e.date, e.debut, e.fin, e.natureCode FROM eff_places p "
                        + "JOIN eff_equipages e ON e.id = p.equipageId AND e.tenantId = p.tenantId "
                        + "JOIN eff_periodes pr ON pr.id = e.periodeId AND pr.tenantId = e.tenantId AND pr.etat IN (?, ?) "
                        + "WHERE p.tenantId = ? AND p.ressourceId = ? AND e.date BETWEEN ? AND ? ORDER BY e.date, e.debut")
                .params(Planification.EtatPeriode.PUBLIEE.name(), Planification.EtatPeriode.CLOSE.name(), tenantId, ressourceId, du, au)
                .query((rs, i) -> new QuartBrut(rs.getString("placeId"), rs.getString("date"), rs.getInt("debut"), rs.getInt("fin"), rs.getString("natureCode")))
                .list();
    }

    @Override
    public List<PointageTemps> pointages(String tenantId, String ressourceId, String du, String au) {
        return jdbc.sql("SELECT placeId, saisieId, sens, horodatage, statut FROM eff_pointages WHERE tenantId = ? AND ressourceId = ? AND statut <> ? "
                        + "AND horodatage >= ? AND horodatage < ? ORDER BY horodatage")
                .params(tenantId, ressourceId, PresenceEffectifs.StatutPointage.REJETE.name(), du, au)
                .query((rs, i) -> new PointageTemps(rs.getString("placeId"), rs.getString("saisieId"), PresenceEffectifs.Sens.valueOf(rs.getString("sens")),
                        rs.getString("horodatage"), PresenceEffectifs.StatutPointage.valueOf(rs.getString("statut")))).list();
    }

    @Override
    public List<CorroborationTemps.HorodatageExterne> horodatagesExternes(String tenantId, String ressourceId, String du, String au) {
        return jdbc.sql("SELECT sens, horodatage, source FROM eff_horodatages_externes WHERE tenantId = ? AND ressourceId = ? AND horodatage >= ? "
                        + "AND horodatage < ? ORDER BY horodatage").params(tenantId, ressourceId, du, au)
                .query((rs, i) -> new CorroborationTemps.HorodatageExterne(PresenceEffectifs.Sens.valueOf(rs.getString("sens")), rs.getString("horodatage"),
                        rs.getString("source"))).list();
    }

    @Override
    public Optional<Planification.EtatPeriode> etatPeriode(String tenantId, String uniteCode, String date) {
        return jdbc.sql("SELECT etat FROM eff_periodes WHERE tenantId = ? AND uniteCode = ? AND debut <= ? AND fin >= ? ORDER BY debut DESC LIMIT 1")
                .params(tenantId, uniteCode, date, date).query(String.class).optional().map(Planification.EtatPeriode::valueOf);
    }

    // ── Écriture ─────────────────────────────────────────────────────────────

    @Override
    public void enregistrerParametres(Contexte ctx, TempsEffectifs.Parametres p) {
        jdbc.sql("INSERT INTO eff_temps_parametres (tenantId, seuilJourHeures, seuilSemaineHeures, paliersApprobation, toleranceEcartMinutes, "
                        + "toleranceCorroborationMinutes, actionNonCorrobore, actionSourcesDivergentes, modifieLe, modifiePar) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenantId) DO UPDATE SET seuilJourHeures = excluded.seuilJourHeures, "
                        + "seuilSemaineHeures = excluded.seuilSemaineHeures, paliersApprobation = excluded.paliersApprobation, "
                        + "toleranceEcartMinutes = excluded.toleranceEcartMinutes, toleranceCorroborationMinutes = excluded.toleranceCorroborationMinutes, "
                        + "actionNonCorrobore = excluded.actionNonCorrobore, actionSourcesDivergentes = excluded.actionSourcesDivergentes, "
                        + "modifieLe = excluded.modifieLe, modifiePar = excluded.modifiePar")
                .params(ctx.tenantId(), p.seuilJourHeures(), p.seuilSemaineHeures(), p.paliersApprobation(), p.toleranceEcartMinutes(),
                        p.toleranceCorroborationMinutes(), p.actionNonCorrobore().name(), p.actionSourcesDivergentes().name(), ctx.maintenant(), ctx.acteurId())
                .update();
    }

    @Override
    public void insererFeuille(Contexte ctx, NouvelleFeuille f) {
        jdbc.sql("INSERT INTO eff_feuilles (id, tenantId, ressourceId, uniteCode, debut, fin, statut, palier, paliersRequis, creeLe, creePar) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(f.id(), ctx.tenantId(), f.ressourceId(), f.uniteCode(), f.debut(), f.fin(), TempsEffectifs.StatutFeuille.BROUILLON.name(),
                        TempsEffectifs.AUCUN_PALIER, f.paliersRequis(), ctx.maintenant(), ctx.acteurId()).update();
    }

    @Override
    public void insererSegment(Contexte ctx, String feuilleId, NouveauSegment s, Filiation f) {
        jdbc.sql("INSERT INTO eff_segments_temps (id, tenantId, feuilleId, placeId, date, debut, fin, typeHeureCode, source, version, remplaceId, actif, motif, "
                        + "sourceExterne, confirmeLe, confirmePar, creeLe, creePar) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(f.id(), ctx.tenantId(), feuilleId, s.placeId(), s.date(), s.debut(), s.fin(), s.typeHeureCode(), s.source().name(), f.version(),
                        f.remplaceId(), ACTIF, f.motif(), s.sourceExterne(), f.confirme() ? ctx.maintenant() : null, f.confirme() ? ctx.acteurId() : null,
                        ctx.maintenant(), ctx.acteurId()).update();
    }

    @Override
    public void confirmerSegment(Contexte ctx, String id) {
        jdbc.sql("UPDATE eff_segments_temps SET confirmeLe = ?, confirmePar = ? WHERE tenantId = ? AND id = ?").params(ctx.maintenant(), ctx.acteurId(),
                ctx.tenantId(), id).update();
    }

    @Override
    public void desactiverSegment(String tenantId, String id) {
        jdbc.sql("UPDATE eff_segments_temps SET actif = ? WHERE tenantId = ? AND id = ?").params(INACTIF, tenantId, id).update();
    }

    @Override
    public void retirerSegment(String tenantId, String id, String motif) {
        jdbc.sql("UPDATE eff_segments_temps SET actif = ?, motif = COALESCE(?, motif) WHERE tenantId = ? AND id = ?").params(INACTIF, motif, tenantId, id).update();
    }

    @Override
    public int desactiverGeneres(String tenantId, String feuilleId) {
        return jdbc.sql("UPDATE eff_segments_temps SET actif = ? WHERE tenantId = ? AND feuilleId = ? AND actif = ? AND version = 1 AND source IN (?, ?)")
                .params(INACTIF, tenantId, feuilleId, ACTIF, TempsEffectifs.Source.GENERE.name(), TempsEffectifs.Source.POINTAGE.name()).update();
    }

    @Override
    public void soumettre(Contexte ctx, String id, int paliersRequis, List<TempsEffectifs.Anomalie> instantane) {
        jdbc.sql("UPDATE eff_feuilles SET statut = ?, palier = ?, paliersRequis = ?, instantane = ?, soumiseLe = ?, soumisePar = ? WHERE tenantId = ? AND id = ?")
                .params(TempsEffectifs.StatutFeuille.SOUMISE.name(), TempsEffectifs.PREMIER_PALIER, paliersRequis, JSON.writeValueAsString(instantane),
                        ctx.maintenant(), ctx.acteurId(), ctx.tenantId(), id).update();
    }

    @Override
    public void inscrireDecision(Contexte ctx, NouvelleDecision d) {
        jdbc.sql("INSERT INTO eff_feuille_decisions (id, tenantId, feuilleId, palier, decision, motif, decideLe, decidePar) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(d.id(), ctx.tenantId(), d.feuilleId(), d.palier(), d.decision().name(), d.motif(), ctx.maintenant(), ctx.acteurId()).update();
    }

    @Override
    public void poserIssue(Contexte ctx, String id, TempsEffectifs.Issue issue) {
        String approuveeLe = issue.statut() == TempsEffectifs.StatutFeuille.APPROUVEE ? ctx.maintenant() : null;
        jdbc.sql("UPDATE eff_feuilles SET statut = ?, palier = ?, approuveeLe = ? WHERE tenantId = ? AND id = ?")
                .params(issue.statut().name(), issue.palier(), approuveeLe, ctx.tenantId(), id).update();
    }
}
