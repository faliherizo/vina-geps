package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.DepotComblementsAbsencesPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Adaptateur SQLite du comblement des places libérées par une absence (lot U5). */
@Repository("effectifsDepotComblementsAbsences")
public class DepotComblementsAbsences implements DepotComblementsAbsencesPort {

    private final JdbcClient jdbc;

    public DepotComblementsAbsences(JdbcClient jdbc, SchemaAbsencesEffectifs schema) {
        this.jdbc = jdbc;
    }

    @Override
    public void inserer(String tenantId, Comblement c, String mecanisme) {
        jdbc.sql("INSERT INTO eff_comblements (id, tenantId, absenceId, placeId, mode, resultat, attribueeA, mecanisme, creeLe) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(c.id(), tenantId, c.absenceId(), c.placeId(), c.mode().name(), c.resultat().name(), c.attribueeA(), mecanisme, c.creeLe()).update();
    }

    @Override
    public void aviser(String tenantId, Avis a) {
        jdbc.sql("INSERT INTO eff_comblement_avis (comblementId, tenantId, ressourceId, canal, statut, erreur, envoyeLe) VALUES (?, ?, ?, ?, ?, ?, ?)")
                .params(a.comblementId(), tenantId, a.ressourceId(), a.canal(), a.statut(), a.erreur(), a.envoyeLe()).update();
    }

    @Override
    public List<LigneComblement> deLAbsence(String tenantId, String absenceId) {
        return jdbc.sql("SELECT c.id, c.placeId, e.date, e.debut, e.fin, c.mode, c.resultat, c.attribueeA, ra.nom AS attribueeANom, "
                        + "p.ressourceId AS occupeeParId, ro.nom AS occupeeParNom, c.creeLe FROM eff_comblements c "
                        + "JOIN eff_places p ON p.id = c.placeId AND p.tenantId = c.tenantId "
                        + "JOIN eff_equipages e ON e.id = p.equipageId AND e.tenantId = p.tenantId "
                        + "LEFT JOIN eff_ressources ra ON ra.id = c.attribueeA AND ra.tenantId = c.tenantId "
                        + "LEFT JOIN eff_ressources ro ON ro.id = p.ressourceId AND ro.tenantId = p.tenantId "
                        + "WHERE c.tenantId = ? AND c.absenceId = ? ORDER BY e.date, e.debut, c.creeLe")
                .params(tenantId, absenceId)
                .query((rs, i) -> new LigneComblement(rs.getString("id"), rs.getString("placeId"), rs.getString("date"), rs.getInt("debut"), rs.getInt("fin"),
                        rs.getString("mode"), rs.getString("resultat"), rs.getString("attribueeA"), rs.getString("attribueeANom"), rs.getString("occupeeParId"),
                        rs.getString("occupeeParNom"), rs.getString("creeLe"))).list();
    }

    @Override
    public List<LigneAvis> avisDeLAbsence(String tenantId, String absenceId) {
        return jdbc.sql("SELECT v.comblementId, v.ressourceId, r.nom, v.canal, v.statut, v.erreur, v.envoyeLe FROM eff_comblement_avis v "
                        + "JOIN eff_comblements c ON c.id = v.comblementId AND c.tenantId = v.tenantId "
                        + "JOIN eff_ressources r ON r.id = v.ressourceId AND r.tenantId = v.tenantId "
                        + "WHERE v.tenantId = ? AND c.absenceId = ? ORDER BY r.nom, v.canal")
                .params(tenantId, absenceId)
                .query((rs, i) -> new LigneAvis(rs.getString("comblementId"), rs.getString("ressourceId"), rs.getString("nom"), rs.getString("canal"),
                        rs.getString("statut"), rs.getString("erreur"), rs.getString("envoyeLe"))).list();
    }

    @Override
    public Optional<String> employeDuCompte(String utilisateurId) {
        return jdbc.sql("SELECT employeeId FROM users WHERE id = ?").params(utilisateurId).query(String.class).optional();
    }
}
