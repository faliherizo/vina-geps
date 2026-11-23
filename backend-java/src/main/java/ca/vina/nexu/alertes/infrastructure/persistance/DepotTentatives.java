package ca.vina.nexu.alertes.infrastructure.persistance;

import ca.vina.nexu.alertes.domaine.Cascades;
import ca.vina.nexu.alertes.domaine.TentativesPort;
import ca.vina.nexu.alertes.domaine.UtilisateursActifs.Tentative;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Les trois sources d'une tentative de transmission : la remise (envoi et escalade), l'étape de cascade (ses personnes
 * visées), l'appel automatique. La base compare des textes ISO dont la précision varie ({@code …00Z} ou
 * {@code …00.000Z}) : la requête prend un jour de marge de chaque côté, et le domaine coupe à l'instant près.
 */
public class DepotTentatives implements TentativesPort {

    private static final Duration MARGE = Duration.ofDays(1);
    private static final TypeReference<List<String>> PERSONNES = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final ObjectMapper json = JsonMapper.builder().build();

    public DepotTentatives(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Tentative> tentatives(String tenantId, Instant debut, Instant fin) {
        String de = debut.minus(MARGE).toString();
        String a = fin.plus(MARGE).toString();
        List<Tentative> l = new ArrayList<>(remises(tenantId, de, a));
        l.addAll(etapes(tenantId, de, a));
        l.addAll(appels(tenantId, de, a));
        return l;
    }

    private List<Tentative> remises(String tenantId, String de, String a) {
        return jdbc.sql("SELECT r.personId, r.nom, r.envoyeeLe, a.id, a.titre FROM alr_remises r JOIN alr_alertes a ON a.tenantId = r.tenantId AND a.id = r.alerteId "
                        + "WHERE r.tenantId = ? AND r.envoyeeLe >= ? AND r.envoyeeLe < ?").params(tenantId, de, a)
                .query((rs, i) -> new Tentative(rs.getString("personId"), rs.getString("nom"), rs.getString("envoyeeLe"), rs.getString("id"), rs.getString("titre"))).list();
    }

    private record Etape(String alerteId, String titre, String personnes, String instant) { }

    /** Une étape vise des personnes déjà nommées dans les remises de l'alerte : le nom s'y relit. */
    private List<Tentative> etapes(String tenantId, String de, String a) {
        List<Etape> etapes = jdbc.sql("SELECT t.alerteId, a.titre, t.personnes, t.instant FROM alr_cascade_traces t "
                        + "JOIN alr_alertes a ON a.tenantId = t.tenantId AND a.id = t.alerteId WHERE t.tenantId = ? AND t.evenement = ? AND t.instant >= ? AND t.instant < ?")
                .params(tenantId, Cascades.Evenement.ETAPE.code(), de, a)
                .query((rs, i) -> new Etape(rs.getString("alerteId"), rs.getString("titre"), rs.getString("personnes"), rs.getString("instant"))).list();
        List<Tentative> l = new ArrayList<>();
        for (Etape e : etapes) {
            Map<String, String> noms = noms(tenantId, e.alerteId());
            json.readValue(e.personnes(), PERSONNES).forEach(p -> l.add(new Tentative(p, noms.getOrDefault(p, p), e.instant(), e.alerteId(), e.titre())));
        }
        return l;
    }

    private Map<String, String> noms(String tenantId, String alerteId) {
        return jdbc.sql("SELECT personId, nom FROM alr_remises WHERE tenantId = ? AND alerteId = ?").params(tenantId, alerteId)
                .query((rs, i) -> Map.entry(rs.getString("personId"), rs.getString("nom"))).list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (x, y) -> x));
    }

    private List<Tentative> appels(String tenantId, String de, String a) {
        return jdbc.sql("SELECT ap.personId, COALESCE(r.nom, ap.personId) AS nom, ap.lanceLe, a.id, a.titre FROM alr_appels ap "
                        + "JOIN alr_alertes a ON a.tenantId = ap.tenantId AND a.id = ap.alerteId "
                        + "LEFT JOIN alr_remises r ON r.tenantId = ap.tenantId AND r.alerteId = ap.alerteId AND r.personId = ap.personId "
                        + "WHERE ap.tenantId = ? AND ap.lanceLe >= ? AND ap.lanceLe < ?").params(tenantId, de, a)
                .query((rs, i) -> new Tentative(rs.getString("personId"), rs.getString("nom"), rs.getString("lanceLe"), rs.getString("id"), rs.getString("titre"))).list();
    }
}
