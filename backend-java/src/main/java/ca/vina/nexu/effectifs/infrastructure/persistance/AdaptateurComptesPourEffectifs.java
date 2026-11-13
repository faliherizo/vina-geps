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
    public Optional<String> nomDe(String utilisateurId) {
        return jdbc.sql("SELECT COALESCE(loginName, username) FROM users WHERE id = ?").param(utilisateurId).query(String.class).optional();
    }
}
