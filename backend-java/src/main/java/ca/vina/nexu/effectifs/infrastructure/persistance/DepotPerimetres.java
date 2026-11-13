package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.DepotPerimetresPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Adaptateur SQLite des périmètres ({@code eff_perimetres}). */
@Repository("effectifsDepotPerimetres")
public class DepotPerimetres implements DepotPerimetresPort {

    private static final String COLONNES = "id, utilisateurId, uniteCode, etendue, niveau, depuis, jusqua";
    private static final RowMapper<Perimetres.Perimetre> PERIMETRE = (rs, i) -> new Perimetres.Perimetre(rs.getString("id"), rs.getString("utilisateurId"),
            rs.getString("uniteCode"), Perimetres.Etendue.valueOf(rs.getString("etendue")), Perimetres.Niveau.valueOf(rs.getString("niveau")),
            rs.getString("depuis"), rs.getString("jusqua"));

    private final JdbcClient jdbc;

    public DepotPerimetres(JdbcClient jdbc, SchemaEffectifs schema) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Perimetres.Perimetre> duCompte(String tenantId, String utilisateurId) {
        return jdbc.sql("SELECT " + COLONNES + " FROM eff_perimetres WHERE tenantId = ? AND utilisateurId = ? ORDER BY depuis, uniteCode")
                .params(tenantId, utilisateurId).query(PERIMETRE).list();
    }

    @Override
    public Optional<Perimetres.Perimetre> lire(String tenantId, String id) {
        return jdbc.sql("SELECT " + COLONNES + " FROM eff_perimetres WHERE tenantId = ? AND id = ?").params(tenantId, id).query(PERIMETRE).optional();
    }

    @Override
    public void inserer(String tenantId, Perimetres.Perimetre p, String creeLe) {
        jdbc.sql("INSERT INTO eff_perimetres (id, tenantId, utilisateurId, uniteCode, etendue, niveau, depuis, jusqua, creeLe) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?)")
                .params(p.id(), tenantId, p.utilisateurId(), p.uniteCode(), p.etendue().name(), p.niveau().name(), p.depuis(), creeLe).update();
    }

    @Override
    public void fermer(String tenantId, String id, String date) {
        jdbc.sql("UPDATE eff_perimetres SET jusqua = ? WHERE tenantId = ? AND id = ?").params(date, tenantId, id).update();
    }
}
