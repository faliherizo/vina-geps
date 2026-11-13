package ca.vina.nexu.approbations.infrastructure.persistance;

import ca.vina.nexu.approbations.domaine.Delegataires;
import ca.vina.nexu.personnel.domaine.AnnuaireEmployes;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Les comptes d'un organisme, nommés comme le Node les nomme ({@code nomDuCompte} : la fiche
 * d'employé, sinon le nom de connexion — jamais la clé technique {@code login@@organisme}), et les
 * délégations reçues en vigueur.
 */
@Component
public class AdaptateurComptesApprobations implements Delegataires.Comptes {

    private final JdbcClient jdbc;
    private final AnnuaireEmployes personnes;
    private final Clock horloge;

    public AdaptateurComptesApprobations(JdbcClient jdbc, AnnuaireEmployes personnes, Clock horloge) {
        this.jdbc = jdbc;
        this.personnes = personnes;
        this.horloge = horloge;
    }

    /** Le nom montré : la fiche d'employé de l'organisme, sinon le nom de connexion — jamais la clé technique. */
    private String nom(String tenantId, String employe, String login) {
        String nom = employe == null ? "" : personnes.lire(tenantId, employe)
                .map(AnnuaireEmployes.Fiche::nomComplet).orElse("");
        return nom.isEmpty() ? login : nom;
    }

    private static String login(java.sql.ResultSet rs) throws java.sql.SQLException {
        return rs.getString("loginName") != null ? rs.getString("loginName") : rs.getString("username");
    }

    @Override
    public List<Delegataires.Compte> deLOrganisme(String tenantId) {
        return jdbc.sql("SELECT id, username, loginName, email, role, active, employeeId FROM users WHERE tenantId = ? ORDER BY rowid")
                .param(tenantId)
                .query((rs, i) -> new Delegataires.Compte(rs.getString("id"), login(rs), rs.getString("email"), rs.getString("role"),
                        rs.getInt("active") != 0, nom(tenantId, rs.getString("employeeId"), login(rs))))
                .list();
    }

    @Override
    public Optional<Delegataires.Destinataire> destinataire(String tenantId, String userId) {
        return jdbc.sql("SELECT id, username, loginName, tenantId, role, active, employeeId FROM users WHERE id = ?")
                .param(userId)
                .query((rs, i) -> new Delegataires.Destinataire(rs.getString("tenantId"), rs.getInt("active") != 0, rs.getString("role"),
                        nom(tenantId, rs.getString("employeeId"), login(rs))))
                .optional();
    }

    @Override
    public boolean aRecuUneDelegation(String tenantId, String userId) {
        String jour = LocalDate.now(horloge).toString();
        return !jdbc.sql("SELECT 1 FROM approval_delegations WHERE toUserId = ? AND tenantId = ?"
                        + " AND (fromDate IS NULL OR fromDate <= ?) AND (toDate IS NULL OR ? <= toDate) LIMIT 1")
                .params(userId, tenantId, jour, jour).query(Integer.class).list().isEmpty();
    }
}
