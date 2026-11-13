package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.DepotPeriodesPort;
import ca.vina.nexu.effectifs.domaine.Periode;
import ca.vina.nexu.effectifs.domaine.Planification;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Adaptateur SQLite des périodes de planification ({@code eff_periodes}). */
@Repository("effectifsDepotPeriodes")
public class DepotPeriodes implements DepotPeriodesPort {

    private static final String COLONNES = "id, uniteCode, nom, debut, fin, etat, finCollecte, creeLe, publieeLe, closeLe";

    private final JdbcClient jdbc;

    public DepotPeriodes(JdbcClient jdbc, SchemaPlanification schema) {
        this.jdbc = jdbc;
    }

    private static Periode periode(ResultSet rs, int i) throws SQLException {
        return new Periode(rs.getString("id"), rs.getString("uniteCode"), rs.getString("nom"), rs.getString("debut"), rs.getString("fin"),
                Planification.EtatPeriode.valueOf(rs.getString("etat")), rs.getString("finCollecte"), rs.getString("creeLe"), rs.getString("publieeLe"),
                rs.getString("closeLe"));
    }

    @Override
    public Optional<Periode> lire(String tenantId, String id) {
        return jdbc.sql("SELECT " + COLONNES + " FROM eff_periodes WHERE tenantId = ? AND id = ?").params(tenantId, id).query(DepotPeriodes::periode).optional();
    }

    @Override
    public List<Periode> lister(String tenantId) {
        return jdbc.sql("SELECT " + COLONNES + " FROM eff_periodes WHERE tenantId = ? ORDER BY debut DESC").param(tenantId).query(DepotPeriodes::periode).list();
    }

    @Override
    public void inserer(String tenantId, String id, String uniteCode, String nom, String debut, String fin, String creeLe) {
        jdbc.sql("INSERT INTO eff_periodes (id, tenantId, uniteCode, nom, debut, fin, etat, creeLe) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(id, tenantId, uniteCode, nom, debut, fin, Planification.EtatPeriode.PREPARATION.name(), creeLe).update();
    }

    @Override
    public void changerEtat(String tenantId, String id, Planification.EtatPeriode etat, String finCollecte, String maintenant) {
        jdbc.sql("UPDATE eff_periodes SET etat = :etat, finCollecte = :finCollecte, publieeLe = CASE WHEN :etat = :publiee THEN :maintenant ELSE publieeLe END, "
                        + "closeLe = CASE WHEN :etat = :close THEN :maintenant ELSE closeLe END WHERE tenantId = :tenantId AND id = :id")
                .param("etat", etat.name()).param("finCollecte", finCollecte).param("publiee", Planification.EtatPeriode.PUBLIEE.name())
                .param("close", Planification.EtatPeriode.CLOSE.name()).param("maintenant", maintenant).param("tenantId", tenantId).param("id", id).update();
    }
}
