package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.Rattachements;
import ca.vina.nexu.effectifs.domaine.RattachementsPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Adaptateur SQLite des rattachements ({@code eff_rattachements}, changeset 0030). */
@Repository
public class DepotRattachements implements RattachementsPort {

    private static final String COLONNES = "ressourceId, uniteCode, depuis, jusqua";

    private final JdbcClient jdbc;

    public DepotRattachements(JdbcClient jdbc, SchemaEffectifs socle) {
        this.jdbc = jdbc;
    }

    @Override
    public void ajouter(String tenantId, Rattachements.Rattachement r, String par, String le) {
        jdbc.sql("INSERT INTO eff_rattachements (tenantId, " + COLONNES + ", poseePar, poseeLe) VALUES (?, ?, ?, ?, ?, ?, ?)")
                .params(tenantId, r.ressourceId(), r.uniteCode(), r.depuis(), r.jusqua(), par, le).update();
    }

    @Override
    public boolean retirer(String tenantId, String ressourceId, String uniteCode, String depuis) {
        return jdbc.sql("DELETE FROM eff_rattachements WHERE tenantId = ? AND ressourceId = ? AND uniteCode = ? AND depuis = ?")
                .params(tenantId, ressourceId, uniteCode, depuis).update() > 0;
    }

    @Override
    public List<Rattachements.Rattachement> deLaRessource(String tenantId, String ressourceId) {
        return jdbc.sql("SELECT " + COLONNES + " FROM eff_rattachements WHERE tenantId = ? AND ressourceId = ? ORDER BY depuis, uniteCode")
                .params(tenantId, ressourceId).query((rs, i) -> rattachement(rs)).list();
    }

    @Override
    public List<Rattachements.Rattachement> tous(String tenantId) {
        return jdbc.sql("SELECT " + COLONNES + " FROM eff_rattachements WHERE tenantId = ?").param(tenantId).query((rs, i) -> rattachement(rs)).list();
    }

    private static Rattachements.Rattachement rattachement(ResultSet rs) throws SQLException {
        return new Rattachements.Rattachement(rs.getString("ressourceId"), rs.getString("uniteCode"), rs.getString("depuis"), rs.getString("jusqua"));
    }
}
