package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.Competences;
import ca.vina.nexu.effectifs.domaine.CompetencesPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Adaptateur SQLite des compétences détenues ({@code eff_competences}, changeset 0027) : une par personne et par code. */
@Repository
public class DepotCompetences implements CompetencesPort {

    private static final String COLONNES = "ressourceId, code, obtenueLe, echeance";

    private final JdbcClient jdbc;

    public DepotCompetences(JdbcClient jdbc, SchemaEffectifs socle) {
        this.jdbc = jdbc;
    }

    @Override
    public void poser(String tenantId, Competences.Detenue c, String par, String le) {
        jdbc.sql("INSERT INTO eff_competences (tenantId, " + COLONNES + ", poseePar, poseeLe) VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenantId, ressourceId, code) DO UPDATE SET obtenueLe = excluded.obtenueLe, echeance = excluded.echeance, "
                        + "poseePar = excluded.poseePar, poseeLe = excluded.poseeLe")
                .params(tenantId, c.ressourceId(), c.code(), c.obtenueLe(), c.echeance(), par, le).update();
    }

    @Override
    public boolean retirer(String tenantId, String ressourceId, String code) {
        return jdbc.sql("DELETE FROM eff_competences WHERE tenantId = ? AND ressourceId = ? AND code = ?").params(tenantId, ressourceId, code).update() > 0;
    }

    @Override
    public List<Competences.Detenue> deLaRessource(String tenantId, String ressourceId) {
        return jdbc.sql("SELECT " + COLONNES + " FROM eff_competences WHERE tenantId = ? AND ressourceId = ? ORDER BY code")
                .params(tenantId, ressourceId).query((rs, i) -> detenue(rs)).list();
    }

    @Override
    public List<Competences.Detenue> avecEcheance(String tenantId) {
        return jdbc.sql("SELECT " + COLONNES + " FROM eff_competences WHERE tenantId = ? AND echeance IS NOT NULL ORDER BY echeance, ressourceId, code")
                .param(tenantId).query((rs, i) -> detenue(rs)).list();
    }

    private static Competences.Detenue detenue(ResultSet rs) throws SQLException {
        return new Competences.Detenue(rs.getString("ressourceId"), rs.getString("code"), rs.getString("obtenueLe"), rs.getString("echeance"));
    }
}
