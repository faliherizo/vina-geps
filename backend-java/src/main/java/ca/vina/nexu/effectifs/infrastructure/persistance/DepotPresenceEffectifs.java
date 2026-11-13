package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.ActionRegle;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.EcriturePointagesEffectifsPort;
import ca.vina.nexu.effectifs.domaine.LecturePointagesEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Milieux;
import ca.vina.nexu.effectifs.domaine.NaturePause;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.PointageEffectifs;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adaptateur SQLite de la présence : {@code eff_pointages}, {@code eff_pointage_preuves},
 * {@code eff_horodatages_externes} ; et la lecture des quarts publiés d'une personne, jointe sur les tables de la
 * planification (R3) et des milieux (socle) — R3 n'offre pas cette lecture croisée.
 */
@Repository("effectifsDepotPresence")
public class DepotPresenceEffectifs implements LecturePointagesEffectifsPort, EcriturePointagesEffectifsPort {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<Map<String, Object>>> MOTIFS = new TypeReference<>() { };
    private static final String SELECT = "SELECT p.*, r.nom FROM eff_pointages p JOIN eff_ressources r ON r.id = p.ressourceId AND r.tenantId = p.tenantId";
    /** Les statuts qui comptent dans la séquence d'un quart, tirés de l'énumération : un nouveau statut s'y range seul. */
    private static final List<String> ACTIFS = Arrays.stream(PresenceEffectifs.StatutPointage.values())
            .filter(PresenceEffectifs.StatutPointage::estActif).map(Enum::name).toList();

    private final JdbcClient jdbc;

    public DepotPresenceEffectifs(JdbcClient jdbc, SchemaPresenceEffectifs presence, SchemaPlanification planification, SchemaEffectifs socle) {
        this.jdbc = jdbc;
    }

    // ── Lecture ──────────────────────────────────────────────────────────────

    private static Long entierOuNul(ResultSet rs, String colonne) throws SQLException {
        long v = rs.getLong(colonne);
        return rs.wasNull() ? null : v;
    }

    private static <E extends Enum<E>> E enumOuNul(Class<E> type, String v) {
        return v == null ? null : Enum.valueOf(type, v);
    }

    private static PointageEffectifs pointage(ResultSet rs, int i) throws SQLException {
        PointageEffectifs.Constat constat = new PointageEffectifs.Constat(PresenceEffectifs.Resultat.valueOf(rs.getString("resultat")),
                enumOuNul(PresenceEffectifs.Ecart.class, rs.getString("ecart")), entierOuNul(rs, "distanceMetres"),
                ActionRegle.valueOf(rs.getString("actionRegles")), JSON.readValue(rs.getString("motifsRegles"), MOTIFS));
        PointageEffectifs.Suivi suivi = new PointageEffectifs.Suivi(PresenceEffectifs.StatutPointage.valueOf(rs.getString("statut")),
                rs.getString("justification"), rs.getString("saisiLe"), rs.getString("saisiPar"), rs.getString("decideLe"), rs.getString("decidePar"),
                rs.getString("motifDecision"));
        return new PointageEffectifs(rs.getString("id"), rs.getString("ressourceId"), rs.getString("nom"),
                new PointageEffectifs.Lieu(rs.getString("placeId"), rs.getString("milieuId"), rs.getString("uniteCode")),
                PresenceEffectifs.Sens.valueOf(rs.getString("sens")), rs.getString("horodatage"),
                PresenceEffectifs.OriginePointage.valueOf(rs.getString("origine")), rs.getString("saisieId"), constat, suivi,
                new PointageEffectifs.Qualification(enumOuNul(NaturePause.class, rs.getString("naturePause")), rs.getString("activiteCode")));
    }

    private static PresenceEffectifs.PointageCourt court(ResultSet rs, int i) throws SQLException {
        return new PresenceEffectifs.PointageCourt(PresenceEffectifs.Sens.valueOf(rs.getString("sens")), rs.getString("horodatage"));
    }

    /** Un pointage de quart, avec son site, sa nature et son activité : ce que les segments et les répartitions lisent. */
    private static PresenceEffectifs.PointageCourt courtComplet(ResultSet rs, int i) throws SQLException {
        return new PresenceEffectifs.PointageCourt(PresenceEffectifs.Sens.valueOf(rs.getString("sens")), rs.getString("horodatage"),
                rs.getString("milieuId"), enumOuNul(NaturePause.class, rs.getString("naturePause")), rs.getString("activiteCode"));
    }

    @Override
    public Optional<PointageEffectifs> lire(String tenantId, String id) {
        return jdbc.sql(SELECT + " WHERE p.tenantId = ? AND p.id = ?").params(tenantId, id).query(DepotPresenceEffectifs::pointage).optional();
    }

    @Override
    public List<PointageEffectifs> deLaSaisie(String tenantId, String saisieId) {
        return jdbc.sql(SELECT + " WHERE p.tenantId = ? AND p.saisieId = ? ORDER BY p.horodatage").params(tenantId, saisieId)
                .query(DepotPresenceEffectifs::pointage).list();
    }

    @Override
    public List<PointageEffectifs> deLaRessource(String tenantId, String ressourceId, String depuis, int limite) {
        if (depuis == null) {
            return jdbc.sql(SELECT + " WHERE p.tenantId = ? AND p.ressourceId = ? ORDER BY p.horodatage DESC LIMIT ?").params(tenantId, ressourceId, limite)
                    .query(DepotPresenceEffectifs::pointage).list();
        }
        return jdbc.sql(SELECT + " WHERE p.tenantId = ? AND p.ressourceId = ? AND p.horodatage >= ? ORDER BY p.horodatage DESC LIMIT ?")
                .params(tenantId, ressourceId, depuis, limite).query(DepotPresenceEffectifs::pointage).list();
    }

    @Override
    public List<PointageEffectifs> lister(String tenantId, Filtre f, int limite) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE p.tenantId = ?");
        List<Object> params = new ArrayList<>(List.of(tenantId));
        condition(sql, params, "p.statut = ?", f.statut());
        condition(sql, params, "p.horodatage >= ?", f.du());
        condition(sql, params, "p.horodatage < ?", f.au());
        condition(sql, params, "p.milieuId = ?", f.milieuId());
        params.add(limite);
        return jdbc.sql(sql.append(" ORDER BY p.horodatage DESC LIMIT ?").toString()).params(params).query(DepotPresenceEffectifs::pointage).list();
    }

    private static void condition(StringBuilder sql, List<Object> params, String clause, String valeur) {
        if (valeur != null) {
            sql.append(" AND ").append(clause);
            params.add(valeur);
        }
    }

    @Override
    public List<PresenceEffectifs.PointageCourt> actifsDeLaPlace(String tenantId, String ressourceId, String placeId) {
        List<Object> params = new ArrayList<>(List.of(tenantId, ressourceId, placeId));
        params.addAll(ACTIFS);
        return jdbc.sql("SELECT sens, horodatage, milieuId, naturePause, activiteCode FROM eff_pointages WHERE tenantId = ? AND ressourceId = ? AND placeId = ? "
                        + "AND statut IN (" + String.join(", ", Collections.nCopies(ACTIFS.size(), "?")) + ") ORDER BY horodatage")
                .params(params).query(DepotPresenceEffectifs::courtComplet).list();
    }

    @Override
    public List<PresenceEffectifs.VerdictPreuve> preuves(String tenantId, String pointageId) {
        return jdbc.sql("SELECT * FROM eff_pointage_preuves WHERE tenantId = ? AND pointageId = ? ORDER BY mecanisme").params(tenantId, pointageId)
                .query((rs, i) -> new PresenceEffectifs.VerdictPreuve(Milieux.Mecanisme.valueOf(rs.getString("mecanisme")), rs.getInt("exigee") == 1,
                        rs.getInt("fournie") == 1, rs.getInt("satisfaite") == 1, entierOuNul(rs, "distanceMetres"),
                        enumOuNul(PresenceEffectifs.VerdictQr.class, rs.getString("detail")))).list();
    }

    @Override
    public List<PresenceEffectifs.PointageCourt> externes(String tenantId, String ressourceId) {
        return jdbc.sql("SELECT sens, horodatage FROM eff_horodatages_externes WHERE tenantId = ? AND ressourceId = ?").params(tenantId, ressourceId)
                .query(DepotPresenceEffectifs::court).list();
    }

    @Override
    public List<PresenceEffectifs.QuartPublie> quartsPublies(String tenantId, String ressourceId, String du, String au) {
        return jdbc.sql("SELECT p.id AS placeId, e.milieuId, m.libelle AS milieu, e.uniteCode, e.natureCode, e.date, e.debut, e.fin "
                        + "FROM eff_places p JOIN eff_equipages e ON e.id = p.equipageId AND e.tenantId = p.tenantId "
                        + "JOIN eff_periodes pr ON pr.id = e.periodeId AND pr.tenantId = e.tenantId AND pr.etat = ? "
                        + "JOIN eff_milieux m ON m.id = e.milieuId AND m.tenantId = e.tenantId "
                        + "WHERE p.tenantId = ? AND p.ressourceId = ? AND e.date BETWEEN ? AND ?")
                .params(Planification.EtatPeriode.PUBLIEE.name(), tenantId, ressourceId, du, au)
                .query((rs, i) -> new PresenceEffectifs.QuartPublie(rs.getString("placeId"), rs.getString("milieuId"), rs.getString("milieu"),
                        rs.getString("uniteCode"), rs.getString("natureCode"), rs.getString("date"), rs.getInt("debut"), rs.getInt("fin"))).list();
    }

    @Override
    public Optional<String> ressourceParMatricule(String tenantId, String matricule) {
        return jdbc.sql("SELECT id FROM eff_ressources WHERE tenantId = ? AND matricule = ?").params(tenantId, matricule).query(String.class).optional();
    }

    // ── Écriture ─────────────────────────────────────────────────────────────

    private static String nom(Enum<?> e) {
        return e == null ? null : e.name();
    }

    @Override
    public void inserer(Contexte ctx, NouveauPointage p) {
        PointageEffectifs.Constat c = p.constat();
        jdbc.sql("INSERT INTO eff_pointages (id, tenantId, ressourceId, placeId, milieuId, uniteCode, sens, horodatage, origine, saisieId, resultat, ecart, "
                        + "distanceMetres, actionRegles, motifsRegles, statut, justification, naturePause, activiteCode, saisiLe, saisiPar) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(p.id(), ctx.tenantId(), p.ressourceId(), p.lieu().placeId(), p.lieu().milieuId(), p.lieu().uniteCode(), p.pointage().sens().name(),
                        p.pointage().horodatage(), p.origine().name(), p.saisieId(), c.resultat().name(), nom(c.ecart()), c.distanceMetres(),
                        c.actionRegles().name(), JsonEffectifs.ecrire(c.motifsRegles()), p.statut().name(), p.justification(),
                        nom(p.pointage().nature()), p.pointage().activiteCode(), ctx.maintenant(), ctx.acteurId())
                .update();
    }

    @Override
    public void inscrirePreuve(String tenantId, String pointageId, PresenceEffectifs.VerdictPreuve v) {
        jdbc.sql("INSERT INTO eff_pointage_preuves (tenantId, pointageId, mecanisme, exigee, fournie, satisfaite, distanceMetres, detail) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(tenantId, pointageId, v.mecanisme().name(), v.exigee() ? 1 : 0, v.fournie() ? 1 : 0, v.satisfaite() ? 1 : 0, v.distanceMetres(),
                        nom(v.detail())).update();
    }

    @Override
    public void decider(Contexte ctx, String id, PresenceEffectifs.StatutPointage vers, String motif) {
        jdbc.sql("UPDATE eff_pointages SET statut = ?, decideLe = ?, decidePar = ?, motifDecision = ? WHERE tenantId = ? AND id = ?")
                .params(vers.name(), ctx.maintenant(), ctx.acteurId(), motif, ctx.tenantId(), id).update();
    }

    @Override
    public boolean importer(Contexte ctx, HorodatageExterne h) {
        return jdbc.sql("INSERT OR IGNORE INTO eff_horodatages_externes (id, tenantId, source, cle, ressourceId, sens, horodatage, importeLe, importePar) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(h.id(), ctx.tenantId(), h.source(), h.cle(), h.ressourceId(), h.pointage().sens().name(), h.pointage().horodatage(), ctx.maintenant(),
                        ctx.acteurId()).update() > 0;
    }
}
