package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.ActionRegle;
import ca.vina.nexu.effectifs.domaine.DepotEchangesEffectifsPort;
import ca.vina.nexu.effectifs.domaine.EchangeEffectifs;
import ca.vina.nexu.effectifs.domaine.FenetreHoraire;
import ca.vina.nexu.effectifs.domaine.EchangeEffectifs.Statut;
import ca.vina.nexu.effectifs.domaine.Equipage;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.SourceEchangesEffectifsPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Adaptateur SQLite des échanges de quarts ({@code eff_echanges}), et des places publiées qu'ils lisent et
 * réaffectent ({@code eff_places}, {@code eff_equipages}, {@code eff_periodes} du lot R3). Les états se passent en
 * paramètres, jamais en littéraux : la liste des statuts ouverts vit dans le domaine.
 */
@Repository("effectifsDepotEchanges")
public class DepotEchangesEffectifs implements SourceEchangesEffectifsPort, DepotEchangesEffectifsPort {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<Object>> LISTE = new TypeReference<>() { };

    private static final String FICHE = "SELECT x.id, x.placeCedeeId, x.placeVoulueId, x.ressourceAId, x.ressourceBId, x.uniteA, x.uniteB, x.statut, x.actionRegles, "
            + "x.motifsRegles, x.proposeLe, x.proposePar, x.decideLe, x.decidePar, x.motifDecision, ra.nom AS nomA, rb.nom AS nomB, "
            + "ea.date AS dateA, ea.debut AS debutA, ea.fin AS finA, eb.date AS dateB, eb.debut AS debutB, eb.fin AS finB FROM eff_echanges x "
            + "JOIN eff_ressources ra ON ra.id = x.ressourceAId AND ra.tenantId = x.tenantId "
            + "JOIN eff_ressources rb ON rb.id = x.ressourceBId AND rb.tenantId = x.tenantId "
            + "JOIN eff_places pa ON pa.id = x.placeCedeeId AND pa.tenantId = x.tenantId JOIN eff_equipages ea ON ea.id = pa.equipageId AND ea.tenantId = pa.tenantId "
            + "JOIN eff_places pb ON pb.id = x.placeVoulueId AND pb.tenantId = x.tenantId JOIN eff_equipages eb ON eb.id = pb.equipageId AND eb.tenantId = pb.tenantId ";

    private static final String QUARTS = "SELECT p.id AS placeId, e.date, e.debut, e.fin, e.uniteCode, p.corpsEmploiCode, p.ressourceId, r.nom, "
            + "e.milieuId, m.libelle AS milieu "
            + "FROM eff_places p JOIN eff_equipages e ON e.id = p.equipageId AND e.tenantId = p.tenantId "
            + "JOIN eff_periodes pr ON pr.id = e.periodeId AND pr.tenantId = e.tenantId AND pr.etat = :publiee "
            + "JOIN eff_ressources r ON r.id = p.ressourceId AND r.tenantId = p.tenantId "
            // LEFT JOIN : un quart ad hoc peut n'avoir aucun milieu, et le perdre de la liste
            // serait pire que l'afficher sans site.
            + "LEFT JOIN eff_milieux m ON m.id = e.milieuId AND m.tenantId = e.tenantId "
            + "WHERE p.tenantId = :tenantId AND e.date >= :depuis ";

    private final JdbcClient jdbc;

    public DepotEchangesEffectifs(JdbcClient jdbc, SchemaEchangesEffectifs schema, SchemaPlanification planification) {
        this.jdbc = jdbc;
    }

    private static List<String> ouverts() {
        return Arrays.stream(Statut.values()).filter(Statut::estOuvert).map(Enum::name).toList();
    }

    private static EchangeEffectifs.Fiche fiche(ResultSet rs, int i) throws SQLException {
        return new EchangeEffectifs.Fiche(rs.getString("id"), rs.getString("placeCedeeId"), rs.getString("placeVoulueId"), rs.getString("ressourceAId"),
                rs.getString("ressourceBId"), rs.getString("uniteA"), rs.getString("uniteB"), Statut.valueOf(rs.getString("statut")),
                ActionRegle.valueOf(rs.getString("actionRegles")), JSON.readValue(rs.getString("motifsRegles"), LISTE), rs.getString("proposeLe"),
                rs.getString("proposePar"), rs.getString("decideLe"), rs.getString("decidePar"), rs.getString("motifDecision"), rs.getString("nomA"),
                rs.getString("nomB"), new EchangeEffectifs.Quart(rs.getString("dateA"), rs.getInt("debutA"), rs.getInt("finA")),
                new EchangeEffectifs.Quart(rs.getString("dateB"), rs.getInt("debutB"), rs.getInt("finB")));
    }

    private static EchangeEffectifs.QuartEchangeable quart(ResultSet rs, int i) throws SQLException {
        return new EchangeEffectifs.QuartEchangeable(rs.getString("placeId"), rs.getString("date"), rs.getInt("debut"), rs.getInt("fin"),
                rs.getString("uniteCode"), rs.getString("corpsEmploiCode"), rs.getString("ressourceId"), rs.getString("nom"),
                rs.getString("milieuId"), rs.getString("milieu"));
    }

    @Override
    public Optional<EchangeEffectifs.PlaceDetaillee> place(String tenantId, String placeId) {
        return jdbc.sql("SELECT p.id, p.ressourceId, p.corpsEmploiCode, e.date, e.debut, e.fin, e.periodeId, pr.etat AS periodeEtat, e.uniteCode "
                        + "FROM eff_places p JOIN eff_equipages e ON e.id = p.equipageId AND e.tenantId = p.tenantId "
                        + "JOIN eff_periodes pr ON pr.id = e.periodeId AND pr.tenantId = e.tenantId WHERE p.tenantId = ? AND p.id = ?")
                .params(tenantId, placeId)
                .query((rs, i) -> new EchangeEffectifs.PlaceDetaillee(rs.getString("id"), rs.getString("ressourceId"), rs.getString("corpsEmploiCode"),
                        rs.getString("date"), rs.getInt("debut"), rs.getInt("fin"), rs.getString("periodeId"),
                        Planification.EtatPeriode.valueOf(rs.getString("periodeEtat")), rs.getString("uniteCode"))).optional();
    }

    @Override
    public Optional<EchangeEffectifs.Fiche> lire(String tenantId, String id) {
        return jdbc.sql(FICHE + "WHERE x.tenantId = ? AND x.id = ?").params(tenantId, id).query(DepotEchangesEffectifs::fiche).optional();
    }

    @Override
    public List<EchangeEffectifs.Fiche> lister(String tenantId) {
        return jdbc.sql(FICHE + "WHERE x.tenantId = ? ORDER BY x.proposeLe DESC, x.id").param(tenantId).query(DepotEchangesEffectifs::fiche).list();
    }

    @Override
    public Optional<String> ouvertSur(String tenantId, String placeA, String placeB) {
        return jdbc.sql("SELECT id FROM eff_echanges WHERE tenantId = :tenantId AND statut IN (:ouverts) "
                        + "AND (placeCedeeId IN (:a, :b) OR placeVoulueId IN (:a, :b)) LIMIT 1")
                .param("tenantId", tenantId).param("ouverts", ouverts()).param("a", placeA).param("b", placeB).query(String.class).optional();
    }

    @Override
    public Set<String> ressourcesDuCompte(String tenantId, String utilisateurId) {
        return new LinkedHashSet<>(jdbc.sql("SELECT id FROM eff_ressources WHERE tenantId = ? AND utilisateurId = ?").params(tenantId, utilisateurId)
                .query(String.class).list());
    }

    @Override
    public List<EchangeEffectifs.QuartEchangeable> quartsDe(String tenantId, String ressourceId, String depuis) {
        return jdbc.sql(QUARTS + "AND p.ressourceId = :ressourceId ORDER BY e.date, e.debut")
                .param("publiee", Planification.EtatPeriode.PUBLIEE.name()).param("tenantId", tenantId).param("depuis", depuis)
                .param("ressourceId", ressourceId).query(DepotEchangesEffectifs::quart).list();
    }

    @Override
    public List<EchangeEffectifs.QuartEchangeable> quartsEntre(String tenantId, String ressourceId, FenetreHoraire fenetre) {
        return jdbc.sql(QUARTS + "AND e.date <= :au AND p.ressourceId = :ressourceId ORDER BY e.date, e.debut")
                .param("publiee", Planification.EtatPeriode.PUBLIEE.name()).param("tenantId", tenantId)
                .param("depuis", fenetre.du()).param("au", fenetre.au())
                .param("ressourceId", ressourceId).query(DepotEchangesEffectifs::quart).list();
    }

    @Override
    public List<EchangeEffectifs.QuartEchangeable> demandables(String tenantId, EchangeEffectifs.PlaceDetaillee cedee, String depuis) {
        return jdbc.sql(QUARTS + "AND e.uniteCode = :unite AND p.corpsEmploiCode = :corps AND p.ressourceId <> :ressourceId "
                        + "AND NOT EXISTS (SELECT 1 FROM eff_echanges x WHERE x.tenantId = p.tenantId AND x.statut IN (:ouverts) "
                        + "AND (x.placeCedeeId = p.id OR x.placeVoulueId = p.id)) ORDER BY e.date, e.debut")
                .param("publiee", Planification.EtatPeriode.PUBLIEE.name()).param("tenantId", tenantId).param("depuis", depuis)
                .param("unite", cedee.uniteCode()).param("corps", cedee.corpsEmploiCode()).param("ressourceId", cedee.ressourceId())
                .param("ouverts", ouverts()).query(DepotEchangesEffectifs::quart).list();
    }

    @Override
    public void inserer(String tenantId, NouvelEchange e) {
        jdbc.sql("INSERT INTO eff_echanges (id, tenantId, placeCedeeId, placeVoulueId, ressourceAId, ressourceBId, uniteA, uniteB, statut, actionRegles, "
                        + "motifsRegles, proposeLe, proposePar) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(e.id(), tenantId, e.cedee().id(), e.voulue().id(), e.cedee().ressourceId(), e.voulue().ressourceId(), e.cedee().uniteCode(),
                        e.voulue().uniteCode(), Statut.PROPOSE.name(), e.verdict().action().code(), JSON.writeValueAsString(e.verdict().motifs()),
                        e.proposeLe(), e.proposePar()).update();
    }

    @Override
    public void decider(String tenantId, String id, Decision d) {
        String action = d.rejuge() == null ? null : d.rejuge().action().code();
        String motifs = d.rejuge() == null ? null : JSON.writeValueAsString(d.rejuge().motifs());
        jdbc.sql("UPDATE eff_echanges SET statut = ?, actionRegles = COALESCE(?, actionRegles), motifsRegles = COALESCE(?, motifsRegles), "
                        + "decideLe = ?, decidePar = ?, motifDecision = ? WHERE tenantId = ? AND id = ?")
                .params(d.vers().name(), action, motifs, d.decideLe(), d.decidePar(), d.motif(), tenantId, id).update();
    }

    @Override
    public void reaffecter(String tenantId, String placeId, String ressourceId, String le) {
        jdbc.sql("UPDATE eff_places SET ressourceId = ?, attribueePar = ?, justificationForcage = NULL, attribueeLe = ? WHERE tenantId = ? AND id = ?")
                .params(ressourceId, Equipage.OrigineAttribution.UTILISATEUR.name(), le, tenantId, placeId).update();
    }
}
