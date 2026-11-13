package ca.vina.nexu.auth.infrastructure.persistance;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Ce que la page de connexion sait des établissements : le sélecteur (actifs ou à l'essai) et la résolution par slug. */
@Repository
public class DepotEtablissementsConnexion {

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
