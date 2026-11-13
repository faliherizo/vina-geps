package ca.vina.nexu.notifications.infrastructure.persistance;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

/**
 * Ce que l'envoi manuel doit savoir des personnes, sans toucher au contexte de la paie : le dossier
 * existe dans l'établissement, son nom, et s'il relève de tel gestionnaire — par la **hiérarchie**
 * ({@code employees.managerId}, récursive), comme {@code payroll/scope.ts}.
 */
@Repository
public class DepotEquipe {

    private final JdbcClient jdbc;

    public DepotEquipe(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Personne(String id, String prenom, String nom) {
        public String nomComplet() {
            return prenom + " " + nom;
        }
    }

    public Optional<Personne> lire(String tenantId, String employeeId) {
        return jdbc.sql("SELECT id, firstName, lastName FROM employees WHERE id = ? AND COALESCE(tenantId, 'default') = ?").params(employeeId, tenantId)
                .query((rs, i) -> new Personne(rs.getString("id"), rs.getString("firstName"), rs.getString("lastName"))).optional();
    }

    /** L'employé relève-t-il du gestionnaire (lui-même compris), directement ou par un chef intermédiaire ? */
    public boolean dansEquipe(String managerEmployeeId, String employeeId) {
        if (managerEmployeeId == null) {
            return false;
        }
        if (managerEmployeeId.equals(employeeId)) {
            return true;
        }
        Map<String, Object> r = jdbc.sql("WITH RECURSIVE equipe(id) AS (SELECT id FROM employees WHERE managerId = ? UNION SELECT e.id FROM employees e JOIN equipe q ON e.managerId = q.id) "
                        + "SELECT COUNT(*) AS n FROM equipe WHERE id = ?").params(managerEmployeeId, employeeId).query().singleRow();
        return ((Number) r.get("n")).intValue() > 0;
    }
}
