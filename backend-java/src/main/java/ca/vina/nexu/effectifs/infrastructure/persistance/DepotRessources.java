package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.DepotRessourcesPort;
import ca.vina.nexu.effectifs.domaine.Ressource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Adaptateur SQLite des ressources planifiables ({@code eff_ressources}). */
@Repository("effectifsDepotRessources")
public class DepotRessources implements DepotRessourcesPort {

    private final JdbcClient jdbc;

    public DepotRessources(JdbcClient jdbc, SchemaPlanification schema) {
        this.jdbc = jdbc;
    }

    private static Ressource ressource(ResultSet rs, int i) throws SQLException {
        return new Ressource(rs.getString("id"), rs.getString("matricule"), rs.getString("nom"), rs.getString("utilisateurId"), rs.getString("uniteCode"),
                rs.getString("corpsEmploiCode"), rs.getString("titreEmploiCode"), rs.getString("statutEmploiCode"), rs.getString("conventionCode"),
                rs.getDouble("anciennete"), rs.getInt("actif") == 1);
    }

    @Override
    public Optional<Ressource> lire(String tenantId, String id) {
        return jdbc.sql("SELECT * FROM eff_ressources WHERE tenantId = ? AND id = ?").params(tenantId, id).query(DepotRessources::ressource).optional();
    }

    @Override
    public List<Ressource> lister(String tenantId) {
        return jdbc.sql("SELECT * FROM eff_ressources WHERE tenantId = ? ORDER BY nom").param(tenantId).query(DepotRessources::ressource).list();
    }

    @Override
    public boolean matriculeExiste(String tenantId, String matricule) {
        return !jdbc.sql("SELECT 1 FROM eff_ressources WHERE tenantId = ? AND matricule = ?").params(tenantId, matricule).query().listOfRows().isEmpty();
    }

    @Override
    public void inserer(String tenantId, String id, String matricule, Champs c, String creeLe) {
        jdbc.sql("INSERT INTO eff_ressources (id, tenantId, matricule, nom, utilisateurId, uniteCode, corpsEmploiCode, titreEmploiCode, statutEmploiCode, "
                        + "conventionCode, anciennete, actif, creeLe) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)")
                .params(id, tenantId, matricule, c.nom(), c.utilisateurId(), c.uniteCode(), c.corpsEmploiCode(), c.titreEmploiCode(), c.statutEmploiCode(),
                        c.conventionCode(), c.anciennete(), creeLe).update();
    }

    @Override
    public void modifier(String tenantId, String id, Champs c, boolean actif) {
        jdbc.sql("UPDATE eff_ressources SET nom = ?, utilisateurId = ?, uniteCode = ?, corpsEmploiCode = ?, titreEmploiCode = ?, statutEmploiCode = ?, "
                        + "conventionCode = ?, anciennete = ?, actif = ? WHERE tenantId = ? AND id = ?")
                .params(c.nom(), c.utilisateurId(), c.uniteCode(), c.corpsEmploiCode(), c.titreEmploiCode(), c.statutEmploiCode(), c.conventionCode(),
                        c.anciennete(), actif ? 1 : 0, tenantId, id).update();
    }

    @Override
    public void poserStatutEmploi(String tenantId, String id, String statutEmploiCode) {
        jdbc.sql("UPDATE eff_ressources SET statutEmploiCode = ? WHERE tenantId = ? AND id = ?").params(statutEmploiCode, tenantId, id).update();
    }

    @Override
    public Optional<Ressource> deLUtilisateur(String tenantId, String utilisateurId) {
        return jdbc.sql("SELECT * FROM eff_ressources WHERE tenantId = ? AND utilisateurId = ? ORDER BY creeLe LIMIT 1").params(tenantId, utilisateurId)
                .query(DepotRessources::ressource).optional();
    }
}
