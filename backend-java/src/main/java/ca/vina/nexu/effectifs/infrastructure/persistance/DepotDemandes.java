package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.DemandesOperationnelles;
import ca.vina.nexu.effectifs.domaine.DemandesPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Adaptateur SQLite des demandes opérationnelles ({@code eff_demandes}, changeset 0028). */
@Repository
public class DepotDemandes implements DemandesPort {

    private static final String COLONNES = "id, ressourceId, uniteCode, typeCode, du, au, commentaire, statut, creeLe, decideePar, decideeLe, motifDecision";

    private final JdbcClient jdbc;

    public DepotDemandes(JdbcClient jdbc, SchemaEffectifs socle) {
        this.jdbc = jdbc;
    }

    @Override
    public void enregistrer(String tenantId, DemandesOperationnelles.Demande d) {
        jdbc.sql("INSERT INTO eff_demandes (tenantId, " + COLONNES + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenantId, id) DO UPDATE SET statut = excluded.statut, decideePar = excluded.decideePar, "
                        + "decideeLe = excluded.decideeLe, motifDecision = excluded.motifDecision")
                .params(tenantId, d.id(), d.ressourceId(), d.uniteCode(), d.typeCode(), d.du(), d.au(), d.commentaire(), d.statut().name(), d.creeLe(),
                        d.decideePar(), d.decideeLe(), d.motifDecision()).update();
    }

    @Override
    public Optional<DemandesOperationnelles.Demande> lire(String tenantId, String id) {
        return jdbc.sql("SELECT " + COLONNES + " FROM eff_demandes WHERE tenantId = ? AND id = ?").params(tenantId, id).query((rs, i) -> demande(rs)).optional();
    }

    @Override
    public List<DemandesOperationnelles.Demande> deLaRessource(String tenantId, String ressourceId) {
        return jdbc.sql("SELECT " + COLONNES + " FROM eff_demandes WHERE tenantId = ? AND ressourceId = ? ORDER BY creeLe DESC, id DESC")
                .params(tenantId, ressourceId).query((rs, i) -> demande(rs)).list();
    }

    @Override
    public List<DemandesOperationnelles.Demande> lister(String tenantId, DemandesOperationnelles.Statut statut) {
        return statut == null
                ? jdbc.sql("SELECT " + COLONNES + " FROM eff_demandes WHERE tenantId = ? ORDER BY creeLe DESC, id DESC").param(tenantId).query((rs, i) -> demande(rs)).list()
                : jdbc.sql("SELECT " + COLONNES + " FROM eff_demandes WHERE tenantId = ? AND statut = ? ORDER BY creeLe DESC, id DESC")
                        .params(tenantId, statut.name()).query((rs, i) -> demande(rs)).list();
    }

    @Override
    public List<DemandesOperationnelles.Demande> accepteesCouvrant(String tenantId, List<String> typeCodes, String date) {
        return lister(tenantId, DemandesOperationnelles.Statut.ACCEPTEE).stream().filter(d -> typeCodes.contains(d.typeCode()) && d.couvre(date)).toList();
    }

    private static DemandesOperationnelles.Demande demande(ResultSet rs) throws SQLException {
        return new DemandesOperationnelles.Demande(rs.getString("id"), rs.getString("ressourceId"), rs.getString("uniteCode"), rs.getString("typeCode"),
                rs.getString("du"), rs.getString("au"), rs.getString("commentaire"), DemandesOperationnelles.Statut.valueOf(rs.getString("statut")),
                rs.getString("creeLe"), rs.getString("decideePar"), rs.getString("decideeLe"), rs.getString("motifDecision"));
    }
}
