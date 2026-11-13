package ca.vina.nexu.personnel.infrastructure.persistance;

import ca.vina.nexu.personnel.domaine.AnnuaireEmployes;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** L'annuaire lu dans {@code employees} : quatre colonnes, l'établissement toujours en filtre. */
@Repository
public class DepotAnnuaireEmployes implements AnnuaireEmployes {

    private static final String COLONNES = "SELECT id, firstName, lastName, managerId FROM employees WHERE tenantId = ?";

    private final JdbcClient jdbc;

    public DepotAnnuaireEmployes(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static Fiche fiche(ResultSet rs, int i) throws SQLException {
        return new Fiche(rs.getString("id"), rs.getString("firstName"), rs.getString("lastName"), rs.getString("managerId"));
    }

    @Override
    public Optional<Fiche> lire(String tenantId, String id) {
        return jdbc.sql(COLONNES + " AND id = ?").params(tenantId, id).query(DepotAnnuaireEmployes::fiche).optional();
    }

    @Override
    public List<Fiche> tous(String tenantId) {
        return jdbc.sql(COLONNES + " ORDER BY lastName, firstName").param(tenantId).query(DepotAnnuaireEmployes::fiche).list();
    }
}
