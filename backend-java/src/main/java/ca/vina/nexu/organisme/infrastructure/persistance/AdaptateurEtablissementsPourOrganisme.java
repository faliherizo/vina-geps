package ca.vina.nexu.organisme.infrastructure.persistance;

import ca.vina.nexu.organisme.domaine.EtablissementsPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Le statut d'un établissement, lu dans {@code tenants} (la table du contexte SaaS, en lecture seule). */
@Repository("organismeAdaptateurEtablissements")
public class AdaptateurEtablissementsPourOrganisme implements EtablissementsPort {

    private final JdbcClient jdbc;

    public AdaptateurEtablissementsPourOrganisme(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> statut(String tenantId) {
        return jdbc.sql("SELECT status FROM tenants WHERE id = ?").param(tenantId).query(String.class).optional();
    }
}
