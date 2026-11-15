package ca.vina.nexu.alertes.infrastructure.persistance;

import ca.vina.nexu.alertes.domaine.AppelsAlertes;
import ca.vina.nexu.alertes.domaine.AppelsAlertesPort;
import ca.vina.nexu.partage.telephonie.Telephonie;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Les appels d'alerte en base (changeset 0015). Le numéro appelé n'y est pas rangé : il se relit sur la fiche. */
public class DepotAppelsAlertes implements AppelsAlertesPort {

    private static final String COLONNES = "SELECT id, tenantId, alerteId, etape, personId, fournisseurId, resultat, touche, dureeSecondes, lanceLe, termineLe FROM alr_appels ";

    private final JdbcClient jdbc;

    public DepotAppelsAlertes(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enregistrer(AppelsAlertes.Appel a) {
        jdbc.sql("INSERT INTO alr_appels (id, tenantId, alerteId, etape, personId, fournisseurId, resultat, touche, dureeSecondes, lanceLe, termineLe) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET fournisseurId = excluded.fournisseurId, resultat = excluded.resultat, "
                        + "touche = excluded.touche, dureeSecondes = excluded.dureeSecondes, termineLe = excluded.termineLe")
                .params(a.id(), a.tenantId(), a.alerteId(), a.etape(), a.personId(), a.fournisseurId(), a.resultat().code(), a.touche(), a.dureeSecondes(),
                        a.lanceLe(), a.termineLe()).update();
    }

    @Override
    public Optional<AppelsAlertes.Appel> appel(String id) {
        return jdbc.sql(COLONNES + "WHERE id = ?").param(id).query((rs, i) -> appel(rs)).optional();
    }

    @Override
    public List<AppelsAlertes.Appel> appelsDe(String tenantId, String alerteId) {
        return jdbc.sql(COLONNES + "WHERE tenantId = ? AND alerteId = ? ORDER BY lanceLe, id").params(tenantId, alerteId).query((rs, i) -> appel(rs)).list();
    }

    @Override
    public Optional<String> numeroDe(String tenantId, String compteAvis) {
        return jdbc.sql("SELECT phone FROM employees WHERE id = ? AND COALESCE(tenantId, ?) = ?").params(compteAvis, tenantId, tenantId)
                .query(String.class).optional().map(Telephonie::e164).filter(Objects::nonNull);
    }

    private static AppelsAlertes.Appel appel(ResultSet rs) throws SQLException {
        int lue = rs.getInt("dureeSecondes");
        Integer duree = rs.wasNull() ? null : lue;
        return new AppelsAlertes.Appel(rs.getString("id"), rs.getString("tenantId"), rs.getString("alerteId"), rs.getInt("etape"), rs.getString("personId"),
                rs.getString("fournisseurId"), AppelsAlertes.Resultat.de(rs.getString("resultat")), rs.getString("touche"), duree,
                rs.getString("lanceLe"), rs.getString("termineLe"));
    }
}
