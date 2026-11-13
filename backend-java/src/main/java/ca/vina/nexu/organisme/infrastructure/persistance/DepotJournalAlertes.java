package ca.vina.nexu.organisme.infrastructure.persistance;

import ca.vina.nexu.organisme.domaine.AlerteConsignee;
import ca.vina.nexu.organisme.domaine.JournalAlertesPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Adaptateur SQLite du journal des alertes ({@code org_alertes}). */
@Repository("organismeDepotJournalAlertes")
public class DepotJournalAlertes implements JournalAlertesPort {

    private final JdbcClient jdbc;

    public DepotJournalAlertes(JdbcClient jdbc, SchemaOrganisme schema) {
        this.jdbc = jdbc;
    }

    @Override
    public void consigner(String tenantId, AlerteConsignee a) {
        jdbc.sql("INSERT INTO org_alertes (id, tenantId, regleId, evenement, quoi, qui, destinataires, envoyees, erreur, horodatage) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(a.id(), tenantId, a.regleId(), a.evenement(), a.quoi(), a.qui(), JsonOrganisme.ecrire(a.destinataires()), a.envoyees(), a.erreur(), a.horodatage())
                .update();
    }

    /** À horodatage égal, la plus récente insérée d'abord : ce que l'index parcouru à rebours rend au Node. */
    @Override
    public List<AlerteConsignee> lire(String tenantId, int limite) {
        return jdbc.sql("SELECT * FROM org_alertes WHERE tenantId = ? ORDER BY horodatage DESC, rowid DESC LIMIT ?").params(tenantId, limite)
                .query((rs, i) -> new AlerteConsignee(rs.getString("id"), rs.getString("regleId"), rs.getString("evenement"), rs.getString("quoi"),
                        rs.getString("qui"), rs.getInt("envoyees"), rs.getString("erreur"), rs.getString("horodatage"), JsonOrganisme.textes(rs.getString("destinataires"))))
                .list();
    }
}
