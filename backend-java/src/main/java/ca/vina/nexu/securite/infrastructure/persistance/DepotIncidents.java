package ca.vina.nexu.securite.infrastructure.persistance;

import ca.vina.nexu.partage.temps.Iso;
import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;
import ca.vina.nexu.partage.erreurs.domaine.VerrouException;
import ca.vina.nexu.securite.domaine.Incidents;
import ca.vina.nexu.securite.domaine.Incidents.Incident;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.type.TypeReference;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Le registre des incidents en base (table {@code securite_incidents}, migration Node 0074) — SQL
 * repris du Node. Un incident se consigne, se modifie, se clôt ; jamais ne se supprime. Le
 * responsable est un compte de l'établissement, son nom figé au registre. {@code tenantId} nul
 * = tous les locataires (superadmin).
 */
@Repository
public class DepotIncidents implements Incidents.Registre {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> LISTE = new TypeReference<>() { };
    private static final List<String> RESERVES = List.of("id", "tenantId", "statut", "closLe", "consigneLe", "consignePar");


    private final JdbcClient jdbc;

    public DepotIncidents(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static Incident versIncident(ResultSet rs, int i) throws SQLException {
        List<String> renseignements;
        renseignements = JSON.readValue(rs.getString("renseignementsJson"), LISTE);
        Object personnes = rs.getObject("personnesTouchees");
        return new Incident(rs.getString("id"), rs.getString("tenantId"), rs.getString("survenuLe"), rs.getString("decouvertLe"),
                rs.getString("nature"), rs.getString("description"), renseignements, personnes == null ? null : ((Number) personnes).intValue(),
                rs.getString("gravite"), rs.getString("mesures"), rs.getString("responsableId"), rs.getString("responsableNom"),
                rs.getInt("notifieCai") != 0, rs.getString("notifieCaiLe"), rs.getInt("notifiePersonnes") != 0, rs.getString("notifiePersonnesLe"),
                rs.getString("statut"), rs.getString("closLe"), rs.getString("consigneLe"), rs.getString("consignePar"));
    }

    /** Du plus récent au plus ancien ; filtres statut et gravité ; {@code tenantId} nul = tous. */
    public List<Incident> lister(String tenantId, String statut, String gravite) {
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (tenantId != null) {
            conditions.add("tenantId = ?");
            params.add(tenantId);
        }
        if (statut != null && !statut.isBlank()) {
            conditions.add("statut = ?");
            params.add(statut);
        }
        if (gravite != null && !gravite.isBlank()) {
            conditions.add("gravite = ?");
            params.add(gravite);
        }
        String ou = conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions);
        return jdbc.sql("SELECT * FROM securite_incidents " + ou + " ORDER BY survenuLe DESC, consigneLe DESC")
                .params(params).query(DepotIncidents::versIncident).list();
    }

    public Incident lire(String tenantId, String id) {
        return jdbc.sql("SELECT * FROM securite_incidents WHERE id = ? AND tenantId = ?").params(id, tenantId)
                .query(DepotIncidents::versIncident).optional().orElseThrow(() -> new IntrouvableException("incident introuvable"));
    }

    /** Le nom de l'établissement, pour la vue et le registre du superadmin. */
    public Map<String, String> nomsEtablissements() {
        Map<String, String> noms = new LinkedHashMap<>();
        jdbc.sql("SELECT id, name FROM tenants").query((rs, i) -> noms.put(rs.getString("id"), rs.getString("name"))).list();
        return noms;
    }

    private Optional<String> nomResponsable(String tenantId, Object responsableId) {
        if (responsableId == null || String.valueOf(responsableId).isBlank()) {
            return Optional.empty();
        }
        return Optional.of(jdbc.sql("SELECT firstName || ' ' || lastName AS nom FROM employees WHERE id = ? AND COALESCE(tenantId, 'default') = ?")
                .params(String.valueOf(responsableId), tenantId).query(String.class).optional()
                .orElseThrow(() -> new IllegalArgumentException("responsable introuvable dans l'établissement")));
    }

    private static String chaine(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    public Incident consigner(String tenantId, String acteurId, Map<String, Object> saisie, String instant) {
        List<String> erreurs = Incidents.valider(saisie);
        if (!erreurs.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", erreurs));
        }
        String id = UUID.randomUUID().toString();
        jdbc.sql("""
                INSERT INTO securite_incidents (id, tenantId, survenuLe, decouvertLe, nature, description, renseignementsJson, personnesTouchees, gravite, mesures,
                  responsableId, responsableNom, notifieCai, notifieCaiLe, notifiePersonnes, notifiePersonnesLe, statut, closLe, consigneLe, consignePar)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'ouvert',NULL,?,?)""")
                .params(id, tenantId, saisie.get("survenuLe"), saisie.get("decouvertLe"), saisie.get("nature"), String.valueOf(saisie.get("description")).trim(),
                        JSON.writeValueAsString(saisie.get("renseignements")), saisie.get("personnesTouchees") == null ? null : ((Number) saisie.get("personnesTouchees")).intValue(),
                        saisie.get("gravite"), String.valueOf(saisie.get("mesures")).trim(), chaine(saisie.get("responsableId")),
                        nomResponsable(tenantId, saisie.get("responsableId")).orElse(null),
                        Boolean.TRUE.equals(saisie.get("notifieCai")) ? 1 : 0, chaine(saisie.get("notifieCaiLe")),
                        Boolean.TRUE.equals(saisie.get("notifiePersonnes")) ? 1 : 0, chaine(saisie.get("notifiePersonnesLe")), instant, acteurId)
                .update();
        return lire(tenantId, id);
    }

    /** Ouvert : tout se corrige. Clos : les notifications seulement (règle INC-3) — le reste est refusé, nommé. */
    public Incident modifier(String tenantId, String id, Map<String, Object> saisie) {
        Incident courant = lire(tenantId, id);
        boolean clos = "clos".equals(courant.statut());
        if (clos) {
            List<String> interdits = Incidents.champsInterditsSiClos(saisie);
            if (!interdits.isEmpty()) {
                throw new IllegalArgumentException("incident clos : seules les notifications se modifient (refusé : " + String.join(", ", interdits) + ")");
            }
        }
        Map<String, Object> fusion = new LinkedHashMap<>();
        fusion.put("survenuLe", courant.survenuLe()); fusion.put("decouvertLe", courant.decouvertLe()); fusion.put("nature", courant.nature());
        fusion.put("description", courant.description()); fusion.put("renseignements", courant.renseignements()); fusion.put("personnesTouchees", courant.personnesTouchees());
        fusion.put("gravite", courant.gravite()); fusion.put("mesures", courant.mesures()); fusion.put("responsableId", courant.responsableId());
        fusion.put("notifieCai", courant.notifieCai()); fusion.put("notifieCaiLe", courant.notifieCaiLe()); fusion.put("notifiePersonnes", courant.notifiePersonnes()); fusion.put("notifiePersonnesLe", courant.notifiePersonnesLe());
        for (Map.Entry<String, Object> e : saisie.entrySet()) {
            if (!RESERVES.contains(e.getKey())) {
                fusion.put(e.getKey(), e.getValue());
            }
        }
        List<String> erreurs = clos ? Incidents.validerNotifications(fusion) : Incidents.valider(fusion);
        if (!erreurs.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", erreurs));
        }
        // Le nom du responsable ne se recalcule que si le responsable change : le registre garde le nom d'alors.
        String responsableNom = saisie.containsKey("responsableId") && !clos ? nomResponsable(tenantId, fusion.get("responsableId")).orElse(null) : courant.responsableNom();
        jdbc.sql("""
                UPDATE securite_incidents SET survenuLe = ?, decouvertLe = ?, nature = ?, description = ?, renseignementsJson = ?, personnesTouchees = ?, gravite = ?, mesures = ?,
                  responsableId = ?, responsableNom = ?, notifieCai = ?, notifieCaiLe = ?, notifiePersonnes = ?, notifiePersonnesLe = ? WHERE id = ? AND tenantId = ?""")
                .params(fusion.get("survenuLe"), fusion.get("decouvertLe"), fusion.get("nature"), String.valueOf(fusion.get("description")).trim(),
                        JSON.writeValueAsString(fusion.get("renseignements")), fusion.get("personnesTouchees") == null ? null : ((Number) fusion.get("personnesTouchees")).intValue(),
                        fusion.get("gravite"), String.valueOf(fusion.get("mesures")).trim(), chaine(fusion.get("responsableId")), responsableNom,
                        Boolean.TRUE.equals(fusion.get("notifieCai")) ? 1 : 0, chaine(fusion.get("notifieCaiLe")),
                        Boolean.TRUE.equals(fusion.get("notifiePersonnes")) ? 1 : 0, chaine(fusion.get("notifiePersonnesLe")), id, tenantId)
                .update();
        return lire(tenantId, id);
    }

    public Incident clore(String tenantId, String id, String instant) {
        Incident courant = lire(tenantId, id);
        if ("clos".equals(courant.statut())) {
            throw new VerrouException("incident déjà clos");
        }
        jdbc.sql("UPDATE securite_incidents SET statut = 'clos', closLe = ? WHERE id = ? AND tenantId = ?").params(instant.substring(0, 10), id, tenantId).update();
        return lire(tenantId, id);
    }

}
