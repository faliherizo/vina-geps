package ca.vina.nexu.alertes.infrastructure.persistance;

import ca.vina.nexu.alertes.domaine.Cascades;
import ca.vina.nexu.alertes.domaine.Cascades.Cascade;
import ca.vina.nexu.alertes.domaine.Cascades.Etape;
import ca.vina.nexu.alertes.domaine.Cascades.Politique;
import ca.vina.nexu.alertes.domaine.Cascades.Trace;
import ca.vina.nexu.alertes.domaine.EcritureCascadesPort;
import ca.vina.nexu.alertes.domaine.LectureCascadesPort;
import ca.vina.nexu.alertes.domaine.Priorite;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Politiques, cascades et trace en base (changeset 0013). Une politique se lit dans sa <strong>version</strong> : la
 * courante pour l'administration, celle de l'armement pour une cascade en cours. La trace est en ajout seul.
 */
public class DepotCascades implements LectureCascadesPort, EcritureCascadesPort {

    private static final String VERSION = "SELECT p.id, p.actif, v.version, v.nom, v.arret, v.etapes FROM alr_politiques p "
            + "JOIN alr_politique_versions v ON v.tenantId = p.tenantId AND v.politiqueId = p.id ";
    private static final TypeReference<List<Map<String, Object>>> ETAPES = new TypeReference<>() { };
    private static final TypeReference<List<String>> PERSONNES = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final ObjectMapper json = JsonMapper.builder().build();

    public DepotCascades(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Politique> politiques(String tenantId) {
        return jdbc.sql(VERSION + "AND v.version = p.version WHERE p.tenantId = ? ORDER BY v.nom").params(tenantId).query((rs, i) -> politique(rs)).list();
    }

    @Override
    public Optional<Politique> politique(String tenantId, String id) {
        return jdbc.sql(VERSION + "AND v.version = p.version WHERE p.tenantId = ? AND p.id = ?").params(tenantId, id).query((rs, i) -> politique(rs)).optional();
    }

    private Optional<Politique> version(String tenantId, String id, int version) {
        return jdbc.sql(VERSION + "AND v.version = ? WHERE p.tenantId = ? AND p.id = ?").params(version, tenantId, id).query((rs, i) -> politique(rs)).optional();
    }

    @Override
    public Optional<Cascade> cascade(String tenantId, String alerteId) {
        return jdbc.sql("SELECT politiqueId, politiqueVersion, etape, echeanceLe, etat FROM alr_cascades WHERE tenantId = ? AND alerteId = ?")
                .params(tenantId, alerteId)
                .query((rs, i) -> new Ligne(rs.getString("politiqueId"), rs.getInt("politiqueVersion"), rs.getInt("etape"), rs.getString("echeanceLe"),
                        Cascades.Etat.de(rs.getString("etat")))).optional()
                .flatMap(l -> version(tenantId, l.politiqueId(), l.version()).map(p -> new Cascade(alerteId, p, l.etape(), l.echeanceLe(), l.etat())));
    }

    private record Ligne(String politiqueId, int version, int etape, String echeanceLe, Cascades.Etat etat) { }

    @Override
    public List<Trace> traces(String tenantId, String alerteId) {
        return jdbc.sql("SELECT evenement, etape, personnes, instant, par FROM alr_cascade_traces WHERE tenantId = ? AND alerteId = ? ORDER BY rang")
                .params(tenantId, alerteId).query((rs, i) -> new Trace(alerteId, Cascades.Evenement.de(rs.getString("evenement")), rs.getInt("etape"),
                        json.readValue(rs.getString("personnes"), PERSONNES), rs.getString("instant"), rs.getString("par"))).list();
    }

    @Override
    public void enregistrer(String tenantId, Politique p, String instant, String auteur) {
        jdbc.sql("INSERT INTO alr_politiques (tenantId, id, version, actif) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (tenantId, id) DO UPDATE SET version = excluded.version, actif = excluded.actif")
                .params(tenantId, p.id(), p.version(), p.actif() ? 1 : 0).update();
        jdbc.sql("INSERT INTO alr_politique_versions (tenantId, politiqueId, version, nom, arret, etapes, creeLe, creePar) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenantId, politiqueId, version) DO NOTHING")
                .params(tenantId, p.id(), p.version(), p.nom(), p.arret().code(), json.writeValueAsString(p.etapes().stream().map(DepotCascades::etape).toList()),
                        instant, auteur).update();
    }

    @Override
    public void armer(String tenantId, Cascade c) {
        jdbc.sql("INSERT INTO alr_cascades (tenantId, alerteId, politiqueId, politiqueVersion, etape, echeanceLe, etat) VALUES (?, ?, ?, ?, ?, ?, ?)")
                .params(tenantId, c.alerteId(), c.politique().id(), c.politique().version(), c.etape(), c.echeanceLe(), c.etat().code()).update();
    }

    @Override
    public boolean avancer(String tenantId, Cascade avant, Cascade apres) {
        return jdbc.sql("UPDATE alr_cascades SET etape = ?, echeanceLe = ?, etat = ? WHERE tenantId = ? AND alerteId = ? AND etape = ? AND etat = ?")
                .params(apres.etape(), apres.echeanceLe(), apres.etat().code(), tenantId, avant.alerteId(), avant.etape(), avant.etat().code()).update() == 1;
    }

    @Override
    public void tracer(String tenantId, Trace t) {
        jdbc.sql("INSERT INTO alr_cascade_traces (tenantId, alerteId, rang, evenement, etape, personnes, instant, par) "
                        + "VALUES (?, ?, (SELECT COALESCE(MAX(rang), 0) + 1 FROM alr_cascade_traces WHERE tenantId = ? AND alerteId = ?), ?, ?, ?, ?, ?)")
                .params(tenantId, t.alerteId(), tenantId, t.alerteId(), t.evenement().code(), t.etape(), json.writeValueAsString(t.personnes()), t.instant(), t.par())
                .update();
    }

    // ── Lignes ───────────────────────────────────────────────────────────────

    private Politique politique(ResultSet rs) throws SQLException {
        List<Etape> etapes = json.readValue(rs.getString("etapes"), ETAPES).stream().map(DepotCascades::etape).toList();
        return new Politique(rs.getString("id"), rs.getString("nom"), etapes, Cascades.CritereArret.de(rs.getString("arret")), rs.getInt("version"),
                rs.getInt("actif") == 1);
    }

    static Map<String, Object> etape(Etape e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cible", e.cible().code());
        m.put("cibleId", e.cibleId());
        m.put("canaux", e.codesCanaux());
        m.put("delaiMinutes", e.delaiMinutes());
        return m;
    }

    @SuppressWarnings("unchecked")
    static Etape etape(Map<String, Object> m) {
        return new Etape(Cascades.Cible.de((String) m.get("cible")), (String) m.get("cibleId"),
                ((List<String>) m.get("canaux")).stream().map(Priorite.Canal::de).toList(), ((Number) m.get("delaiMinutes")).intValue());
    }
}
