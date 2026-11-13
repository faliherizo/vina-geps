package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.AbsenceEffectifs;
import ca.vina.nexu.effectifs.domaine.AbsencesEffectifs;
import ca.vina.nexu.effectifs.domaine.ActionRegle;
import ca.vina.nexu.effectifs.domaine.DepotAbsencesEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Regles;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Adaptateur SQLite des absences d'effectifs ({@code eff_absences}), jointes au nom de la ressource. */
@Repository("effectifsDepotAbsences")
public class DepotAbsencesEffectifs implements DepotAbsencesEffectifsPort {

    private static final String SELECT = "SELECT a.*, r.nom AS ressourceNom FROM eff_absences a "
            + "JOIN eff_ressources r ON r.id = a.ressourceId AND r.tenantId = a.tenantId";
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<Map<String, Object>>> MOTIFS = new TypeReference<>() { };

    private final JdbcClient jdbc;

    public DepotAbsencesEffectifs(JdbcClient jdbc, SchemaPlanification planification, SchemaAbsencesEffectifs schema) {
        this.jdbc = jdbc;
    }

    private static AbsenceEffectifs absence(ResultSet rs, int i) throws SQLException {
        return new AbsenceEffectifs(rs.getString("id"), rs.getString("ressourceId"), rs.getString("ressourceNom"), rs.getString("uniteCode"),
                rs.getString("codeAbsence"), rs.getString("du"), rs.getString("au"), rs.getString("motif"),
                AbsencesEffectifs.Statut.de(rs.getString("statut")), ActionRegle.de(rs.getString("actionRegles")), motifs(rs.getString("motifsRegles")),
                new AbsenceEffectifs.Trace(rs.getString("declareeLe"), rs.getString("declareePar"), rs.getString("decideeLe"), rs.getString("decideePar"),
                        rs.getString("motifDecision"), rs.getString("derogationCode"), rs.getString("derogationMotif")));
    }

    /** Les motifs relus dans la forme du verdict : ce que l'écran affiche d'un refus, et que la dérogation renverse. */
    static List<Regles.Motif> motifs(String json) {
        return JSON.readValue(json, MOTIFS).stream().map(m -> new Regles.Motif((String) m.get("regleId"), ((Number) m.get("version")).intValue(),
                (String) m.get("cle"), ActionRegle.de(m.get("action")), (String) m.get("message"))).toList();
    }

    @Override
    public Optional<AbsenceEffectifs> lire(String tenantId, String id) {
        return jdbc.sql(SELECT + " WHERE a.tenantId = ? AND a.id = ?").params(tenantId, id).query(DepotAbsencesEffectifs::absence).optional();
    }

    @Override
    public List<AbsenceEffectifs> lister(String tenantId, String du, String au) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE a.tenantId = ?");
        List<Object> params = new ArrayList<>(List.of(tenantId));
        if (du != null) {
            sql.append(" AND a.au >= ?");
            params.add(du);
        }
        if (au != null) {
            sql.append(" AND a.du <= ?");
            params.add(au);
        }
        return jdbc.sql(sql.append(" ORDER BY a.du DESC, a.id").toString()).params(params).query(DepotAbsencesEffectifs::absence).list();
    }

    @Override
    public List<AbsenceEffectifs> deLaRessource(String tenantId, String ressourceId) {
        return jdbc.sql(SELECT + " WHERE a.tenantId = ? AND a.ressourceId = ? ORDER BY a.du, a.id").params(tenantId, ressourceId)
                .query(DepotAbsencesEffectifs::absence).list();
    }

    @Override
    public int accepteesSimultanees(String tenantId, String uniteCode, String du, String au) {
        return jdbc.sql("SELECT COUNT(*) FROM eff_absences WHERE tenantId = ? AND uniteCode = ? AND statut = ? AND du <= ? AND au >= ?")
                .params(tenantId, uniteCode, AbsencesEffectifs.Statut.ACCEPTEE.code(), au, du).query(Integer.class).single();
    }

    @Override
    public void inserer(String tenantId, AbsenceEffectifs a) {
        jdbc.sql("INSERT INTO eff_absences (id, tenantId, ressourceId, uniteCode, codeAbsence, du, au, motif, statut, actionRegles, motifsRegles, "
                        + "declareeLe, declareePar) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(a.id(), tenantId, a.ressourceId(), a.uniteCode(), a.codeAbsence(), a.du(), a.au(), a.motif(), a.statut().code(),
                        a.actionRegles().code(), JSON.writeValueAsString(a.motifsRegles()), a.trace().declareeLe(), a.trace().declareePar()).update();
    }

    @Override
    public void decider(String tenantId, String id, DecisionEnregistree d) {
        AbsencesEffectifs.Derogation g = d.derogation();
        jdbc.sql("UPDATE eff_absences SET statut = ?, codeAbsence = ?, decideeLe = ?, decideePar = ?, motifDecision = ?, "
                        + "derogationCode = COALESCE(?, derogationCode), derogationMotif = COALESCE(?, derogationMotif) WHERE tenantId = ? AND id = ?")
                .params(d.vers().code(), d.codeAbsence(), d.decideeLe(), d.decideePar(), d.motifDecision(), g == null ? null : g.codeAbsence(),
                        g == null ? null : g.motif(), tenantId, id).update();
    }
}
