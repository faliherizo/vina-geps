package ca.vina.nexu.organisme.infrastructure.persistance;

import ca.vina.nexu.organisme.domaine.Charte;
import ca.vina.nexu.organisme.domaine.ChartesPort;
import ca.vina.nexu.organisme.domaine.Modification;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Adaptateur SQLite des chartes ({@code org_charte}). */
@Repository("organismeDepotCharte")
public class DepotCharteOrganisme implements ChartesPort {

    private final JdbcClient jdbc;

    public DepotCharteOrganisme(JdbcClient jdbc, SchemaOrganisme schema) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Charte> lire(String tenantId) {
        return jdbc.sql("SELECT nomAffiche, couleurPrincipale, couleurLaterale, logo FROM org_charte WHERE tenantId = ?").param(tenantId)
                .query((rs, i) -> new Charte(rs.getString("nomAffiche"), rs.getString("couleurPrincipale"), rs.getString("couleurLaterale"), rs.getString("logo")))
                .optional();
    }

    @Override
    public void enregistrer(String tenantId, Charte c, Modification m) {
        jdbc.sql("INSERT INTO org_charte (tenantId, nomAffiche, couleurPrincipale, couleurLaterale, logo, modifieLe, modifiePar) VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenantId) DO UPDATE SET nomAffiche = excluded.nomAffiche, couleurPrincipale = excluded.couleurPrincipale, "
                        + "couleurLaterale = excluded.couleurLaterale, logo = excluded.logo, modifieLe = excluded.modifieLe, modifiePar = excluded.modifiePar")
                .params(tenantId, c.nomAffiche(), c.couleurPrincipale(), c.couleurLaterale(), c.logo(), m.le(), m.par()).update();
    }

    @Override
    public boolean retirer(String tenantId) {
        return jdbc.sql("DELETE FROM org_charte WHERE tenantId = ?").param(tenantId).update() > 0;
    }
}
