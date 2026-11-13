package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.AnciennetesIntegrationPort;
import ca.vina.nexu.effectifs.domaine.DonneesIntegrationPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Les lectures que publient les connecteurs (équipages et quarts publiés, journal) et l'ancienneté que BD Corpo met à jour —
 * les requêtes du Node, telles quelles, sur les tables de la planification (R3) et du socle (R1).
 */
@Repository("effectifsDepotDonneesIntegration")
public class DepotDonneesIntegration implements DonneesIntegrationPort, AnciennetesIntegrationPort {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    public DepotDonneesIntegration(JdbcClient jdbc, SchemaPlanification planification, SchemaEffectifs socle) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LigneEquipage> equipagesPublies(String tenantId, String date, String uniteCode) {
        return jdbc.sql("SELECT e.id AS equipageId, e.date, e.debut, e.fin, e.uniteCode, e.milieuId, r.matricule, p.corpsEmploiCode FROM eff_equipages e "
                        + "JOIN eff_periodes pr ON pr.id = e.periodeId AND pr.tenantId = e.tenantId AND pr.etat = 'PUBLIEE' "
                        + "JOIN eff_places p ON p.equipageId = e.id AND p.tenantId = e.tenantId LEFT JOIN eff_ressources r ON r.id = p.ressourceId AND r.tenantId = p.tenantId "
                        + "WHERE e.tenantId = ? AND e.date = ? AND e.uniteCode = ? ORDER BY e.debut, e.id, p.rang").params(tenantId, date, uniteCode)
                .query((rs, i) -> new LigneEquipage(rs.getString("equipageId"), rs.getString("date"), rs.getInt("debut"), rs.getInt("fin"), rs.getString("uniteCode"),
                        rs.getString("milieuId"), rs.getString("matricule"), rs.getString("corpsEmploiCode"))).list();
    }

    @Override
    public List<QuartPublie> quartsPublies(String tenantId, String ressourceId, String depuisDate) {
        return jdbc.sql("SELECT p.id AS placeId, e.date, e.debut, e.fin, m.libelle AS milieu FROM eff_places p "
                        + "JOIN eff_equipages e ON e.id = p.equipageId AND e.tenantId = p.tenantId "
                        + "JOIN eff_periodes pr ON pr.id = e.periodeId AND pr.tenantId = e.tenantId AND pr.etat = 'PUBLIEE' "
                        + "JOIN eff_milieux m ON m.id = e.milieuId AND m.tenantId = e.tenantId "
                        + "WHERE p.tenantId = ? AND p.ressourceId = ? AND e.date >= ? ORDER BY e.date, e.debut").params(tenantId, ressourceId, depuisDate)
                .query((rs, i) -> new QuartPublie(rs.getString("placeId"), rs.getString("date"), rs.getInt("debut"), rs.getInt("fin"), rs.getString("milieu"))).list();
    }

    @Override
    public List<EntreeJournal> journalApres(String tenantId, String curseur, int limite) {
        return jdbc.sql("SELECT id, horodatage, action, objetType, objetId, uniteCode, apres FROM eff_journal "
                        + "WHERE tenantId = ? AND (? IS NULL OR horodatage || id > ?) ORDER BY horodatage, id LIMIT ?").params(tenantId, curseur, curseur, limite)
                .query((rs, i) -> new EntreeJournal(rs.getString("id"), rs.getString("horodatage"), rs.getString("action"), rs.getString("objetType"),
                        rs.getString("objetId"), rs.getString("uniteCode"), rs.getString("apres") == null ? null : JSON.readValue(rs.getString("apres"), Object.class)))
                .list();
    }

    @Override
    public List<RessourceAnciennete> actives(String tenantId, String uniteCode) {
        return jdbc.sql("SELECT id, matricule, anciennete FROM eff_ressources WHERE tenantId = ? AND uniteCode = ? AND actif = 1").params(tenantId, uniteCode)
                .query((rs, i) -> new RessourceAnciennete(rs.getString("id"), rs.getString("matricule"), rs.getDouble("anciennete"))).list();
    }

    @Override
    public void poser(String tenantId, String ressourceId, double anciennete) {
        jdbc.sql("UPDATE eff_ressources SET anciennete = ? WHERE tenantId = ? AND id = ?").params(anciennete, tenantId, ressourceId).update();
    }
}
