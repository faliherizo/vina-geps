package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.Agph;
import ca.vina.nexu.effectifs.domaine.AgphInteresse;
import ca.vina.nexu.effectifs.domaine.AgphRemplacement;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotRemplacementsAgphPort;
import ca.vina.nexu.effectifs.domaine.Planification;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Adaptateur SQLite des remplacements long terme ; le nombre d'intérêts se compte à la lecture. */
@Repository("effectifsDepotRemplacementsAgph")
public class DepotRemplacementsAgph implements DepotRemplacementsAgphPort {

    private static final String SELECT = "SELECT x.*, p.code AS posteCode, r.nom AS attribueANom, (SELECT COUNT(*) FROM eff_remplacement_interets i "
            + "WHERE i.tenantId = x.tenantId AND i.remplacementId = x.id AND i.retireLe IS NULL) AS interesses FROM eff_remplacements x "
            + "JOIN eff_postes p ON p.id = x.posteId AND p.tenantId = x.tenantId LEFT JOIN eff_ressources r ON r.id = x.attribueA AND r.tenantId = x.tenantId";

    private final JdbcClient jdbc;

    public DepotRemplacementsAgph(JdbcClient jdbc, SchemaAgph schema, SchemaPlanification planification) {
        this.jdbc = jdbc;
    }

    private static AgphRemplacement remplacement(ResultSet rs, int i) throws SQLException {
        return new AgphRemplacement(rs.getString("id"), rs.getString("uniteCode"), rs.getString("posteId"),
                Agph.OrigineRemplacement.valueOf(rs.getString("origine")), rs.getString("referenceId"), rs.getString("debut"), rs.getString("fin"),
                rs.getString("finManifestation"), Agph.EtatRemplacement.valueOf(rs.getString("etat")), rs.getString("attribueA"),
                rs.getInt("placesAttribuees"), rs.getString("creeLe"), rs.getString("decideLe"), rs.getString("transfereLe"), rs.getInt("quartsTransferes"),
                rs.getString("motifNonComble"), rs.getString("posteCode"), rs.getString("attribueANom"), rs.getInt("interesses"));
    }

    @Override
    public Optional<AgphRemplacement> lire(String tenantId, String id) {
        return jdbc.sql(SELECT + " WHERE x.tenantId = ? AND x.id = ?").params(tenantId, id).query(DepotRemplacementsAgph::remplacement).optional();
    }

    @Override
    public List<AgphRemplacement> lister(String tenantId) {
        return jdbc.sql(SELECT + " WHERE x.tenantId = ? ORDER BY x.debut, x.id").param(tenantId).query(DepotRemplacementsAgph::remplacement).list();
    }

    @Override
    public void inserer(String tenantId, String id, Nouveau n, Contexte auteur) {
        jdbc.sql("INSERT INTO eff_remplacements (id, tenantId, uniteCode, posteId, origine, referenceId, debut, fin, finManifestation, etat, creeLe, creePar) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(id, tenantId, n.uniteCode(), n.posteId(), n.origine().name(), n.referenceId(), n.debut(), n.fin(), n.finManifestation(),
                        Agph.EtatRemplacement.OUVERT.name(), auteur.maintenant(), auteur.acteurId()).update();
    }

    @Override
    public void attribuer(String tenantId, String id, String ressourceId, int places, String decideLe) {
        jdbc.sql("UPDATE eff_remplacements SET etat = ?, attribueA = ?, placesAttribuees = ?, decideLe = ? WHERE tenantId = ? AND id = ?")
                .params(Agph.EtatRemplacement.ATTRIBUE.name(), ressourceId, places, decideLe, tenantId, id).update();
    }

    @Override
    public void laisserNonComble(String tenantId, String id, NonComble n) {
        jdbc.sql("UPDATE eff_remplacements SET etat = ?, decideLe = ?, transfereLe = ?, quartsTransferes = ?, motifNonComble = ? WHERE tenantId = ? AND id = ?")
                .params(Agph.EtatRemplacement.NON_COMBLE.name(), n.instant(), n.instant(), n.quarts(), n.motif(), tenantId, id).update();
    }

    @Override
    public int quartsVacants(String tenantId, String posteId, String debut, String fin) {
        return jdbc.sql("SELECT COUNT(*) FROM eff_places p JOIN eff_equipages e ON e.id = p.equipageId AND e.tenantId = p.tenantId "
                        + "JOIN eff_periodes pr ON pr.id = e.periodeId AND pr.tenantId = e.tenantId WHERE p.tenantId = ? AND p.posteId = ? "
                        + "AND p.ressourceId IS NULL AND e.date BETWEEN ? AND ? AND pr.etat <> ?")
                .params(tenantId, posteId, debut, fin, Planification.EtatPeriode.CLOSE.name()).query(Integer.class).single();
    }

    @Override
    public void fermerFenetre(String tenantId, String id, String instant) {
        jdbc.sql("UPDATE eff_remplacements SET finManifestation = ? WHERE tenantId = ? AND id = ?").params(instant, tenantId, id).update();
    }

    @Override
    public List<Agph.Interesse> interessesRetenables(String tenantId, String id) {
        return jdbc.sql("SELECT i.ressourceId, i.deposeLe, r.anciennete FROM eff_remplacement_interets i JOIN eff_ressources r ON r.id = i.ressourceId "
                        + "AND r.tenantId = i.tenantId WHERE i.tenantId = ? AND i.remplacementId = ? AND i.retireLe IS NULL AND r.actif = 1")
                .params(tenantId, id)
                .query((rs, i) -> new Agph.Interesse(rs.getString("ressourceId"), rs.getDouble("anciennete"), rs.getString("deposeLe"))).list();
    }

    @Override
    public List<AgphInteresse> interesses(String tenantId, String id) {
        return jdbc.sql("SELECT i.ressourceId, r.nom, r.matricule, r.anciennete, i.deposeLe FROM eff_remplacement_interets i JOIN eff_ressources r "
                        + "ON r.id = i.ressourceId AND r.tenantId = i.tenantId WHERE i.tenantId = ? AND i.remplacementId = ? AND i.retireLe IS NULL")
                .params(tenantId, id)
                .query((rs, i) -> new AgphInteresse(rs.getString("ressourceId"), rs.getString("nom"), rs.getString("matricule"), rs.getDouble("anciennete"),
                        rs.getString("deposeLe"))).list();
    }

    @Override
    public void manifester(String tenantId, String id, String ressourceId, String instant) {
        jdbc.sql("INSERT INTO eff_remplacement_interets (remplacementId, tenantId, ressourceId, deposeLe) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (tenantId, remplacementId, ressourceId) DO UPDATE SET retireLe = NULL").params(id, tenantId, ressourceId, instant).update();
    }

    @Override
    public int retirer(String tenantId, String id, String ressourceId, String instant) {
        return jdbc.sql("UPDATE eff_remplacement_interets SET retireLe = ? WHERE tenantId = ? AND remplacementId = ? AND ressourceId = ? AND retireLe IS NULL")
                .params(instant, tenantId, id, ressourceId).update();
    }

    @Override
    public List<PlaceVacante> placesVacantes(String tenantId, String posteId, String debut, String fin) {
        return jdbc.sql("SELECT p.id, e.date, e.debut, e.fin FROM eff_places p JOIN eff_equipages e ON e.id = p.equipageId AND e.tenantId = p.tenantId "
                        + "JOIN eff_periodes pr ON pr.id = e.periodeId AND pr.tenantId = e.tenantId WHERE p.tenantId = ? AND p.posteId = ? "
                        + "AND p.ressourceId IS NULL AND e.date BETWEEN ? AND ? AND pr.etat <> ? ORDER BY e.date, e.debut")
                .params(tenantId, posteId, debut, fin, Planification.EtatPeriode.CLOSE.name())
                .query((rs, i) -> new PlaceVacante(rs.getString("id"), new Planification.Creneau(rs.getString("date"), rs.getInt("debut"), rs.getInt("fin"))))
                .list();
    }
}
