package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.auth.domaine.Invitations;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Les invitations ({@code auth_invitations}) ; le jeton n'y est jamais écrit, seulement son empreinte. */
@Repository
public class DepotInvitations implements Invitations.Depot {

    private static final String CHAMPS = "id, tenantId, courriel, username, role, employeeId, expireLe, accepteeLe,"
            + " revoqueeLe, creeLe, creePar, compteId";

    private final JdbcClient jdbc;

    public DepotInvitations(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Invitations.Invitation> lister(String tenantId) {
        return jdbc.sql("SELECT " + CHAMPS + " FROM auth_invitations WHERE tenantId IS ? ORDER BY creeLe DESC")
                .param(tenantId).query(DepotInvitations::invitation).list();
    }

    @Override
    public Optional<Invitations.Invitation> lire(String tenantId, String id) {
        return jdbc.sql("SELECT " + CHAMPS + " FROM auth_invitations WHERE tenantId IS ? AND id = ?")
                .params(tenantId, id).query(DepotInvitations::invitation).optional();
    }

    @Override
    public Optional<Invitations.Invitation> parEmpreinte(String empreinte) {
        return jdbc.sql("SELECT " + CHAMPS + " FROM auth_invitations WHERE empreinte = ?")
                .param(empreinte).query(DepotInvitations::invitation).optional();
    }

    @Override
    public boolean compteExiste(String tenantId, String username) {
        return !jdbc.sql("SELECT id FROM users WHERE loginName = ? AND tenantId IS ?").params(username, tenantId)
                .query(String.class).list().isEmpty();
    }

    @Override
    public Optional<Invitations.Invitation> enCours(String tenantId, String username, String maintenant) {
        return jdbc.sql("SELECT " + CHAMPS + " FROM auth_invitations WHERE tenantId IS ? AND username = ?"
                        + " AND accepteeLe IS NULL AND revoqueeLe IS NULL AND expireLe > ?")
                .params(tenantId, username, maintenant).query(DepotInvitations::invitation).list().stream().findFirst();
    }

    @Override
    public void poser(Invitations.Invitation i, String empreinte) {
        jdbc.sql("INSERT INTO auth_invitations (id, tenantId, courriel, username, role, employeeId, empreinte,"
                        + " expireLe, accepteeLe, compteId, revoqueeLe, creeLe, creePar) VALUES (?,?,?,?,?,?,?,?,NULL,NULL,NULL,?,?)")
                .params(i.id(), i.tenantId(), i.courriel(), i.username(), i.role(), i.employeeId(), empreinte, i.expireLe(),
                        i.creeLe(), i.creePar())
                .update();
    }

    @Override
    public void revoquer(String id, String le) {
        jdbc.sql("UPDATE auth_invitations SET revoqueeLe = ? WHERE id = ?").params(le, id).update();
    }

    @Override
    public void accepter(String id, String le, String compteId, String username) {
        jdbc.sql("UPDATE auth_invitations SET accepteeLe = ?, compteId = ?, username = ? WHERE id = ?")
                .params(le, compteId, username, id).update();
    }

    private static Invitations.Invitation invitation(ResultSet rs, int i) throws SQLException {
        return new Invitations.Invitation(rs.getString("id"), rs.getString("tenantId"), rs.getString("courriel"),
                rs.getString("username"), rs.getString("role"), rs.getString("employeeId"), rs.getString("expireLe"),
                rs.getString("accepteeLe"), rs.getString("revoqueeLe"), rs.getString("creeLe"), rs.getString("creePar"),
                rs.getString("compteId"));
    }
}
