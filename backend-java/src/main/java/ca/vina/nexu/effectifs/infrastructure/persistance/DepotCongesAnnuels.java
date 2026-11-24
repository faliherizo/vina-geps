package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.CongesAnnuels;
import ca.vina.nexu.effectifs.domaine.CongesAnnuelsPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Adaptateur SQLite des campagnes de choix des congés ({@code eff_conges_campagnes}, {@code eff_conges_choix} ; changeset 0031). */
@Repository
public class DepotCongesAnnuels implements CongesAnnuelsPort {

    private static final String CAMPAGNE = "id, nom, uniteCode, corpsEmploiCode, annee, du, au, codeAbsence, plafondParSemaine, maxSemainesParPersonne, etat";
    private static final String CHOIX = "ressourceId, rang, semaine, statut, motif";

    private final JdbcClient jdbc;

    public DepotCongesAnnuels(JdbcClient jdbc, SchemaEffectifs socle) {
        this.jdbc = jdbc;
    }

    @Override
    public void enregistrer(String tenantId, CongesAnnuels.Campagne c) {
        jdbc.sql("INSERT INTO eff_conges_campagnes (tenantId, " + CAMPAGNE + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenantId, id) DO UPDATE SET etat = excluded.etat")
                .params(tenantId, c.id(), c.nom(), c.uniteCode(), c.corpsEmploiCode(), c.annee(), c.du(), c.au(), c.codeAbsence(), c.plafondParSemaine(),
                        c.maxSemainesParPersonne(), c.etat().name()).update();
    }

    @Override
    public Optional<CongesAnnuels.Campagne> lire(String tenantId, String id) {
        return jdbc.sql("SELECT " + CAMPAGNE + " FROM eff_conges_campagnes WHERE tenantId = ? AND id = ?").params(tenantId, id).query((rs, i) -> campagne(rs)).optional();
    }

    @Override
    public List<CongesAnnuels.Campagne> lister(String tenantId) {
        return jdbc.sql("SELECT " + CAMPAGNE + " FROM eff_conges_campagnes WHERE tenantId = ? ORDER BY annee DESC, du, nom").param(tenantId).query((rs, i) -> campagne(rs)).list();
    }

    @Override
    public void remplacerChoix(String tenantId, String campagneId, String ressourceId, List<CongesAnnuels.Choix> choix) {
        jdbc.sql("DELETE FROM eff_conges_choix WHERE tenantId = ? AND campagneId = ? AND ressourceId = ?").params(tenantId, campagneId, ressourceId).update();
        choix.forEach(x -> jdbc.sql("INSERT INTO eff_conges_choix (tenantId, campagneId, " + CHOIX + ") VALUES (?, ?, ?, ?, ?, ?, ?)")
                .params(tenantId, campagneId, x.ressourceId(), x.rang(), x.semaine(), x.statut().name(), x.motif()).update());
    }

    @Override
    public void deciderChoix(String tenantId, String campagneId, CongesAnnuels.Choix x) {
        jdbc.sql("UPDATE eff_conges_choix SET statut = ?, motif = ? WHERE tenantId = ? AND campagneId = ? AND ressourceId = ? AND semaine = ?")
                .params(x.statut().name(), x.motif(), tenantId, campagneId, x.ressourceId(), x.semaine()).update();
    }

    @Override
    public List<CongesAnnuels.Choix> choix(String tenantId, String campagneId) {
        return jdbc.sql("SELECT " + CHOIX + " FROM eff_conges_choix WHERE tenantId = ? AND campagneId = ? ORDER BY ressourceId, rang")
                .params(tenantId, campagneId).query((rs, i) -> new CongesAnnuels.Choix(rs.getString("ressourceId"), rs.getInt("rang"), rs.getString("semaine"),
                        CongesAnnuels.StatutChoix.valueOf(rs.getString("statut")), rs.getString("motif"))).list();
    }

    private static CongesAnnuels.Campagne campagne(ResultSet rs) throws SQLException {
        return new CongesAnnuels.Campagne(rs.getString("id"), rs.getString("nom"), rs.getString("uniteCode"), rs.getString("corpsEmploiCode"), rs.getInt("annee"),
                rs.getString("du"), rs.getString("au"), rs.getString("codeAbsence"), rs.getInt("plafondParSemaine"), rs.getInt("maxSemainesParPersonne"),
                CongesAnnuels.Etat.valueOf(rs.getString("etat")));
    }
}
