package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.DepotPostesPort;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.Poste;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Adaptateur SQLite des postes ({@code eff_postes}) ; cycle et paramètres en JSON, comme le Node. */
@Repository("effectifsDepotPostes")
public class DepotPostes implements DepotPostesPort {

    private final JdbcClient jdbc;

    public DepotPostes(JdbcClient jdbc, SchemaPlanification schema) {
        this.jdbc = jdbc;
    }

    private static Poste poste(ResultSet rs, int i) throws SQLException {
        return new Poste(rs.getString("id"), rs.getString("uniteCode"), rs.getString("code"), rs.getString("libelle"), rs.getString("statutEmploiCode"),
                rs.getString("corpsEmploiCode"), rs.getString("milieuId"), rs.getString("natureCode"), JsonEffectifs.cycle(rs.getString("cycle")),
                rs.getString("debutCycle"), JsonEffectifs.carte(rs.getString("parametres")), Planification.EtatPoste.valueOf(rs.getString("etat")),
                rs.getString("titulaireId"));
    }

    @Override
    public Optional<Poste> lire(String tenantId, String id) {
        return jdbc.sql("SELECT * FROM eff_postes WHERE tenantId = ? AND id = ?").params(tenantId, id).query(DepotPostes::poste).optional();
    }

    @Override
    public List<Poste> lister(String tenantId) {
        return jdbc.sql("SELECT * FROM eff_postes WHERE tenantId = ? ORDER BY code").param(tenantId).query(DepotPostes::poste).list();
    }

    @Override
    public boolean codeExiste(String tenantId, String code) {
        return !jdbc.sql("SELECT 1 FROM eff_postes WHERE tenantId = ? AND code = ?").params(tenantId, code).query().listOfRows().isEmpty();
    }

    @Override
    public void inserer(String tenantId, String id, String code, Champs c, String creeLe) {
        jdbc.sql("INSERT INTO eff_postes (id, tenantId, uniteCode, code, libelle, statutEmploiCode, corpsEmploiCode, milieuId, natureCode, cycle, debutCycle, "
                        + "parametres, etat, titulaireId, creeLe) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)")
                .params(id, tenantId, c.uniteCode(), code, c.libelle(), c.statutEmploiCode(), c.corpsEmploiCode(), c.milieuId(), c.natureCode(),
                        JsonEffectifs.ecrire(c.cycle()), c.debutCycle(), JsonEffectifs.ecrire(c.parametres()), Planification.EtatPoste.BROUILLON.name(), creeLe)
                .update();
    }

    @Override
    public void modifier(String tenantId, String id, Champs c) {
        jdbc.sql("UPDATE eff_postes SET libelle = ?, uniteCode = ?, statutEmploiCode = ?, corpsEmploiCode = ?, milieuId = ?, natureCode = ?, cycle = ?, "
                        + "debutCycle = ?, parametres = ? WHERE tenantId = ? AND id = ?")
                .params(c.libelle(), c.uniteCode(), c.statutEmploiCode(), c.corpsEmploiCode(), c.milieuId(), c.natureCode(), JsonEffectifs.ecrire(c.cycle()),
                        c.debutCycle(), JsonEffectifs.ecrire(c.parametres()), tenantId, id).update();
    }

    @Override
    public void poserEtat(String tenantId, String id, Planification.EtatPoste etat, String titulaireId) {
        jdbc.sql("UPDATE eff_postes SET etat = ?, titulaireId = ? WHERE tenantId = ? AND id = ?").params(etat.name(), titulaireId, tenantId, id).update();
    }
}
