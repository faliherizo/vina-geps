package ca.vina.nexu.organisme.infrastructure.persistance;

import ca.vina.nexu.organisme.domaine.EvenementAlerte;
import ca.vina.nexu.organisme.domaine.RegleAlerte;
import ca.vina.nexu.organisme.domaine.ReglesAlertePort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Adaptateur SQLite des règles d'alerte ({@code org_regles_alerte}). */
@Repository("organismeDepotReglesAlerte")
public class DepotReglesAlerte implements ReglesAlertePort {

    private final JdbcClient jdbc;

    public DepotReglesAlerte(JdbcClient jdbc, SchemaOrganisme schema) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RegleAlerte> lister(String tenantId) {
        return jdbc.sql("SELECT id, evenement, destinataires, creeLe, creePar FROM org_regles_alerte WHERE tenantId = ? ORDER BY creeLe, id").param(tenantId)
                .query((rs, i) -> new RegleAlerte(rs.getString("id"), EvenementAlerte.de(rs.getString("evenement")), JsonOrganisme.textes(rs.getString("destinataires")),
                        rs.getString("creeLe"), rs.getString("creePar")))
                .list();
    }

    @Override
    public void inserer(String tenantId, RegleAlerte r) {
        jdbc.sql("INSERT INTO org_regles_alerte (id, tenantId, evenement, destinataires, creeLe, creePar) VALUES (?, ?, ?, ?, ?, ?)")
                .params(r.id(), tenantId, r.evenement().code(), JsonOrganisme.ecrire(r.destinataires()), r.creeLe(), r.creePar()).update();
    }

    @Override
    public boolean retirer(String tenantId, String regleId) {
        return jdbc.sql("DELETE FROM org_regles_alerte WHERE tenantId = ? AND id = ?").params(tenantId, regleId).update() > 0;
    }
}
