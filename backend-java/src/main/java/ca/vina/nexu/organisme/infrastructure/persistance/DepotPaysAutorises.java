package ca.vina.nexu.organisme.infrastructure.persistance;

import ca.vina.nexu.organisme.domaine.Modification;
import ca.vina.nexu.organisme.domaine.PaysAutorisesPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Adaptateur SQLite des pays autorisés ({@code org_pays_autorises}, la liste en tableau JSON). */
@Repository("organismeDepotPaysAutorises")
public class DepotPaysAutorises implements PaysAutorisesPort {

    private final JdbcClient jdbc;

    public DepotPaysAutorises(JdbcClient jdbc, SchemaOrganisme schema) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> lire(String tenantId) {
        return jdbc.sql("SELECT pays FROM org_pays_autorises WHERE tenantId = ?").param(tenantId).query(String.class).optional()
                .map(JsonOrganisme::textes).orElse(List.of());
    }

    @Override
    public void enregistrer(String tenantId, List<String> pays, Modification m) {
        jdbc.sql("INSERT INTO org_pays_autorises (tenantId, pays, modifieLe, modifiePar) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (tenantId) DO UPDATE SET pays = excluded.pays, modifieLe = excluded.modifieLe, modifiePar = excluded.modifiePar")
                .params(tenantId, JsonOrganisme.ecrire(pays), m.le(), m.par()).update();
    }
}
