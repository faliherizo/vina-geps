package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.ActionRegle;
import ca.vina.nexu.effectifs.domaine.ConditionsRegles;
import ca.vina.nexu.effectifs.domaine.ContenuRegle;
import ca.vina.nexu.effectifs.domaine.ContexteRegle;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotReglesPort;
import ca.vina.nexu.effectifs.domaine.EvaluationRegle;
import ca.vina.nexu.effectifs.domaine.PorteeRegle;
import ca.vina.nexu.effectifs.domaine.Regle;
import ca.vina.nexu.effectifs.domaine.VersionRegle;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Adaptateur SQLite du moteur de règles ({@code eff_regles}, {@code eff_regle_versions},
 * {@code eff_regle_evaluations}). Une condition relue passe par la validation du domaine : c'est ce qui la
 * remet en objets typés, et une ligne écrite à la main qui ne vaudrait rien ne s'évaluerait pas en silence.
 */
@Repository("effectifsDepotRegles")
public class DepotRegles implements DepotReglesPort {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final int LIMITE_EVALUATIONS = 500;
    private static final String SELECT_VERSIONS = "SELECT v.id, v.regleId, v.version, r.cle, r.contexte, r.porteeType, r.porteeCode, v.condition, v.action, "
            + "v.message, v.priorite, v.effectifDepuis, v.effectifJusqua FROM eff_regle_versions v JOIN eff_regles r ON r.id = v.regleId AND r.tenantId = v.tenantId";
    private static final RowMapper<Regle> REGLE = (rs, i) -> new Regle(rs.getString("id"), ContexteRegle.valueOf(rs.getString("contexte")), rs.getString("cle"),
            PorteeRegle.valueOf(rs.getString("porteeType")), rs.getString("porteeCode"));
    private static final RowMapper<VersionRegle> VERSION = (rs, i) -> version(rs);
    private static final RowMapper<EvaluationRegle> EVALUATION = (rs, i) -> new EvaluationRegle(rs.getString("id"), rs.getString("contexte"),
            rs.getString("objetType"), rs.getString("objetId"), rs.getString("action"), lire(rs.getString("motifs")), lire(rs.getString("versions")),
            lire(rs.getString("faits")), rs.getString("dateDuFait"), rs.getString("horodatage"), rs.getString("declencheur"));

    private final JdbcClient jdbc;

    public DepotRegles(JdbcClient jdbc, SchemaReglesEffectifs schema) {
        this.jdbc = jdbc;
    }

    private static VersionRegle version(ResultSet rs) throws SQLException {
        ContexteRegle contexte = ContexteRegle.valueOf(rs.getString("contexte"));
        Regle regle = new Regle(rs.getString("regleId"), contexte, rs.getString("cle"), PorteeRegle.valueOf(rs.getString("porteeType")), rs.getString("porteeCode"));
        ContenuRegle contenu = new ContenuRegle(ConditionsRegles.valider(contexte, lire(rs.getString("condition"))), ActionRegle.valueOf(rs.getString("action")),
                rs.getString("message"), rs.getInt("priorite"));
        return new VersionRegle(rs.getString("id"), regle, rs.getInt("version"), contenu, rs.getString("effectifDepuis"), rs.getString("effectifJusqua"));
    }

    private static Object lire(String json) {
        return JSON.readValue(json, Object.class);
    }

    private static String ecrire(Object v) {
        return JSON.writeValueAsString(v);
    }

    @Override
    public Optional<Regle> regle(String tenantId, String id) {
        return jdbc.sql("SELECT id, contexte, cle, porteeType, porteeCode FROM eff_regles WHERE tenantId = ? AND id = ?").params(tenantId, id).query(REGLE).optional();
    }

    @Override
    public boolean existe(String tenantId, Regle c) {
        return jdbc.sql("SELECT COUNT(*) FROM eff_regles WHERE tenantId = ? AND contexte = ? AND cle = ? AND porteeType = ? AND IFNULL(porteeCode, '') = ?")
                .params(tenantId, c.contexte().code(), c.cle(), c.porteeType().code(), c.porteeCode() == null ? "" : c.porteeCode())
                .query(Integer.class).single() > 0;
    }

    @Override
    public List<Regle> regles(String tenantId, ContexteRegle contexte) {
        StringBuilder sql = new StringBuilder("SELECT id, contexte, cle, porteeType, porteeCode FROM eff_regles WHERE tenantId = ?");
        List<Object> params = new ArrayList<>(List.of(tenantId));
        if (contexte != null) {
            sql.append(" AND contexte = ?");
            params.add(contexte.code());
        }
        return jdbc.sql(sql.append(" ORDER BY contexte, cle, porteeType").toString()).params(params).query(REGLE).list();
    }

    @Override
    public List<VersionRegle> versions(String tenantId, String regleId) {
        return jdbc.sql(SELECT_VERSIONS + " WHERE v.tenantId = ? AND v.regleId = ? ORDER BY v.version").params(tenantId, regleId).query(VERSION).list();
    }

    @Override
    public List<VersionRegle> versionsDuContexte(String tenantId, ContexteRegle contexte) {
        return jdbc.sql(SELECT_VERSIONS + " WHERE v.tenantId = ? AND r.contexte = ?").params(tenantId, contexte.code()).query(VERSION).list();
    }

    @Override
    public void creer(Contexte ctx, Regle r) {
        jdbc.sql("INSERT INTO eff_regles (id, tenantId, contexte, cle, porteeType, porteeCode, creeLe, creePar) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(r.id(), ctx.tenantId(), r.contexte().code(), r.cle(), r.porteeType().code(), r.porteeCode(), ctx.maintenant(), ctx.acteurId()).update();
    }

    @Override
    public void insererVersion(Contexte ctx, VersionRegle v) {
        jdbc.sql("INSERT INTO eff_regle_versions (id, tenantId, regleId, version, condition, action, message, priorite, effectifDepuis, effectifJusqua, creeLe, creePar) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(v.id(), ctx.tenantId(), v.regle().id(), v.version(), ecrire(v.contenu().condition().enDonnees()), v.contenu().action().code(),
                        v.contenu().message(), v.contenu().priorite(), v.effectifDepuis(), v.effectifJusqua(), ctx.maintenant(), ctx.acteurId()).update();
    }

    @Override
    public void fermerVersion(String tenantId, String versionId, String date) {
        jdbc.sql("UPDATE eff_regle_versions SET effectifJusqua = ? WHERE tenantId = ? AND id = ?").params(date, tenantId, versionId).update();
    }

    @Override
    public void enregistrer(String tenantId, EvaluationRegle e) {
        jdbc.sql("INSERT INTO eff_regle_evaluations (id, tenantId, contexte, objetType, objetId, action, motifs, versions, faits, dateDuFait, horodatage, declencheur) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(e.id(), tenantId, e.contexte(), e.objetType(), e.objetId(), e.action(), ecrire(e.motifs()), ecrire(e.versions()), ecrire(e.faits()),
                        e.dateDuFait(), e.horodatage(), e.declencheur()).update();
    }

    @Override
    public List<EvaluationRegle> evaluations(String tenantId, EvaluationRegle.Filtre f) {
        StringBuilder sql = new StringBuilder("SELECT * FROM eff_regle_evaluations WHERE tenantId = ?");
        List<Object> params = new ArrayList<>(List.of(tenantId));
        egal(sql, params, "objetType", f.objetType());
        egal(sql, params, "objetId", f.objetId());
        egal(sql, params, "contexte", f.contexte() == null ? null : f.contexte().code());
        sql.append(" ORDER BY horodatage DESC, rowid DESC LIMIT ?");
        params.add(LIMITE_EVALUATIONS);
        return jdbc.sql(sql.toString()).params(params).query(EVALUATION).list();
    }

    private static void egal(StringBuilder sql, List<Object> params, String colonne, String valeur) {
        if (valeur != null) {
            sql.append(" AND ").append(colonne).append(" = ?");
            params.add(valeur);
        }
    }
}
