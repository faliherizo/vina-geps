package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.AbsencesEffectifs;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.PlanningAbsencesEffectifsPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Adaptateur SQLite des lectures de planification propres aux absences d'effectifs ({@code eff_places}, {@code eff_equipages}). */
@Repository("effectifsDepotPlanningAbsences")
public class DepotPlanningAbsencesEffectifs implements PlanningAbsencesEffectifsPort {

    private final JdbcClient jdbc;

    public DepotPlanningAbsencesEffectifs(JdbcClient jdbc, SchemaPlanification schema) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AbsencesEffectifs.PlaceTenue> placesTenues(String tenantId, String ressourceId, String du, String au) {
        return jdbc.sql("SELECT p.id, e.date, e.debut, e.fin FROM eff_places p JOIN eff_equipages e ON e.id = p.equipageId AND e.tenantId = p.tenantId "
                        + "JOIN eff_periodes pr ON pr.id = e.periodeId AND pr.tenantId = e.tenantId "
                        + "WHERE p.tenantId = ? AND p.ressourceId = ? AND e.date BETWEEN ? AND ? AND pr.etat <> ? ORDER BY e.date, e.debut")
                .params(tenantId, ressourceId, du, au, Planification.EtatPeriode.CLOSE.name())
                .query((rs, i) -> new AbsencesEffectifs.PlaceTenue(rs.getString("id"), rs.getString("date"), rs.getInt("debut"), rs.getInt("fin"))).list();
    }

    @Override
    public AbsencesEffectifs.Comblement comblement(String tenantId, String uniteCode, String du, String au) {
        return jdbc.sql("SELECT COUNT(p.id) AS places, COUNT(p.ressourceId) AS pourvues FROM eff_places p "
                        + "JOIN eff_equipages e ON e.id = p.equipageId AND e.tenantId = p.tenantId WHERE p.tenantId = ? AND e.uniteCode = ? AND e.date BETWEEN ? AND ?")
                .params(tenantId, uniteCode, du, au).query((rs, i) -> new AbsencesEffectifs.Comblement(rs.getInt("places"), rs.getInt("pourvues"))).single();
    }

    @Override
    public List<String> ressourcesDuCompte(String tenantId, String utilisateurId) {
        return jdbc.sql("SELECT id FROM eff_ressources WHERE tenantId = ? AND utilisateurId = ?").params(tenantId, utilisateurId).query(String.class).list();
    }
}
