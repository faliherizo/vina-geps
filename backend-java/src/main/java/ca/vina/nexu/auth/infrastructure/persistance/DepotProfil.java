package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.auth.domaine.Profil;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** La fiche employé d'un compte, son département, son gestionnaire et la hiérarchie — lus dans les tables de la paie. */
@Repository
public class DepotProfil {

    private static final String COLONNES = "id, firstName, lastName, email, phone, address, dateOfBirth, jobTitle, role, departmentId, managerId, hireDate, status, "
            + "weeklyCapacityHours, workerType, payType, annualSalary, hourlyRate, sin, isTeacher";

    private final JdbcClient jdbc;

    public DepotProfil(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static Double reel(ResultSet rs, String colonne) throws SQLException {
        double v = rs.getDouble(colonne);
        return rs.wasNull() ? null : v;
    }

    private static Profil.Employe lire(ResultSet rs, int i) throws SQLException {
        return new Profil.Employe(rs.getString("id"), rs.getString("firstName"), rs.getString("lastName"), rs.getString("email"), rs.getString("phone"), rs.getString("address"),
                rs.getString("dateOfBirth"), rs.getString("jobTitle"), rs.getString("role"), rs.getString("departmentId"), rs.getString("managerId"), rs.getString("hireDate"),
                rs.getString("status"), reel(rs, "weeklyCapacityHours"), rs.getString("workerType"), rs.getString("payType"), reel(rs, "annualSalary"), reel(rs, "hourlyRate"),
                rs.getString("sin"), rs.getInt("isTeacher") == 1);
    }

    public Optional<Profil.Employe> employe(String employeeId) {
        if (employeeId == null || employeeId.isEmpty()) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT " + COLONNES + " FROM employees WHERE id = ?").param(employeeId).query(DepotProfil::lire).optional();
    }

    public String nomDuDepartement(String departmentId) {
        return departmentId == null ? null : jdbc.sql("SELECT name FROM departments WHERE id = ?").param(departmentId).query(String.class).optional().orElse(null);
    }

    public String nomDeLEmploye(String employeeId) {
        return employe(employeeId).map(e -> e.firstName() + " " + e.lastName()).orElse(null);
    }

    /** Tous les liens hiérarchiques : la fiche porte la relation, pas le compte. */
    public List<Profil.Lien> liens() {
        return jdbc.sql("SELECT id, managerId FROM employees").query((rs, i) -> new Profil.Lien(rs.getString("id"), rs.getString("managerId"))).list();
    }

    public Map<String, String> gestionnaireDe() {
        return liens().stream().collect(Collectors.toMap(Profil.Lien::id, l -> l.managerId() == null ? "" : l.managerId(), (a, b) -> a));
    }

    public void enregistrerIdentite(Profil.Employe e) {
        jdbc.sql("UPDATE employees SET firstName = ?, lastName = ?, email = ?, phone = ?, address = ?, dateOfBirth = ? WHERE id = ?")
                .params(e.firstName(), e.lastName(), e.email(), e.phone(), e.address(), e.dateOfBirth(), e.id()).update();
    }
}
