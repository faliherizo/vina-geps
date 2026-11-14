package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.ComptesPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Le compte d'un utilisateur, lu dans {@code users} : son établissement et son nom de connexion. */
@Repository("effectifsAdaptateurComptesPourEffectifs")
public class AdaptateurComptesPourEffectifs implements ComptesPort {

    private final JdbcClient jdbc;

    public AdaptateurComptesPourEffectifs(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> etablissementDe(String utilisateurId) {
        return jdbc.sql("SELECT tenantId FROM users WHERE id = ?").param(utilisateurId).query(String.class).optional();
    }

    @Override
    public Optional<String> superieurDe(String tenantId, String utilisateurId) {
        return jdbc.sql("SELECT m.id FROM users u JOIN employees e ON e.id = u.employeeId AND e.tenantId = u.tenantId "
                        + "JOIN users m ON m.employeeId = e.managerId AND m.tenantId = u.tenantId AND m.active = 1 "
                        + "WHERE u.tenantId = ? AND u.id = ? AND e.managerId IS NOT NULL LIMIT 1")
                .params(tenantId, utilisateurId).query(String.class).optional();
    }

    @Override
    public Optional<String> nomDe(String utilisateurId) {
        return jdbc.sql("SELECT COALESCE(loginName, username) FROM users WHERE id = ?").param(utilisateurId).query(String.class).optional();
    }
}
