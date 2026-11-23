package ca.vina.nexu.auth.infrastructure.persistance;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.text.Collator;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Ce que la page de connexion sait des établissements : le sélecteur (actifs ou à l'essai) et la résolution par slug. */
@Repository
public class DepotEtablissementsConnexion {

    /** Un organisme qu'on rejoint par son hôte : son nom et son sous-domaine (ADR 0094). */
    public record Joignable(String nom, String slug) {
    }

    private final JdbcClient jdbc;

    public DepotEtablissementsConnexion(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Du plus récent au plus ancien, comme {@code listTenants} ; les suspendus et résiliés n'y sont pas. */
    public List<Map<String, Object>> selecteur() {
        return jdbc.sql("SELECT id, name, slug, country, status FROM tenants WHERE status IN ('active', 'trial') ORDER BY createdAt DESC")
                .query((rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("name", rs.getString("name"));
                    m.put("slug", rs.getString("slug"));
                    m.put("country", rs.getString("country"));
                    return m;
                }).list();
    }

    /**
     * Les organismes ouverts (actifs ou à l'essai, comme le sélecteur) qui ont un sous-domaine, par nom : ceux que la page
     * de choix de la vitrine propose, dans l'ordre du français. Un organisme sans slug n'est joignable nulle part en déploiement par sous-domaine.
     */
    public List<Joignable> joignables() {
        // L'ordre d'une liste déroulante est celui du français (« à » près de « a ») ; SQLite compare des octets.
        Collator francais = Collator.getInstance(Locale.CANADA_FRENCH);
        return jdbc.sql("SELECT name, slug FROM tenants WHERE status IN ('active', 'trial') AND slug IS NOT NULL AND slug <> ''")
                .query((rs, i) -> new Joignable(rs.getString("name"), rs.getString("slug"))).list()
                .stream().sorted(Comparator.comparing(Joignable::nom, francais)).toList();
    }

    public Optional<Map<String, Object>> parSlug(String slug) {
        return jdbc.sql("SELECT id, name, slug FROM tenants WHERE slug = ?").params(slug.toLowerCase(Locale.ROOT))
                .query((rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("name", rs.getString("name"));
                    m.put("slug", rs.getString("slug"));
                    return m;
                }).optional();
    }

    /** Nom et modules de l'établissement d'un utilisateur, pour « Qui suis-je ? ». */
    public Optional<String> nom(String tenantId) {
        return jdbc.sql("SELECT name FROM tenants WHERE id = ?").params(tenantId).query(String.class).optional();
    }
}
