package ca.vina.nexu.saas.infrastructure.persistance;

import ca.vina.nexu.partage.temps.Iso;
import ca.vina.nexu.saas.domaine.Destruction;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Destruction sécuritaire des données d'un établissement : chaque table portant un {@code tenantId}
 * est **découverte** dans {@code sqlite_master} — jamais énumérée à la main, qui oublierait la
 * prochaine table — puis vidée sous {@code secure_delete} ; le fichier est compacté ; l'attestation
 * est gardée. Une destruction ne se rejoue pas.
 */
@Repository
public class DepotDestruction {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<Destruction.Table>> TABLES = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final Clock horloge;

    public DepotDestruction(JdbcClient jdbc, Clock horloge) {
        this.jdbc = jdbc;
        this.horloge = horloge;
        jdbc.sql("""
                CREATE TABLE IF NOT EXISTS saas_attestations_destruction (
                  id TEXT PRIMARY KEY, tenantId TEXT NOT NULL UNIQUE, tenantNom TEXT NOT NULL, detruitLe TEXT NOT NULL,
                  detruitPar TEXT NOT NULL, motif TEXT, tablesJson TEXT NOT NULL, totalLignes INTEGER NOT NULL, empreinte TEXT NOT NULL)""").update();
    }

    public List<String> tablesParEtablissement() {
        List<String> tables = jdbc.sql("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name").query(String.class).list();
        List<String> sortie = new ArrayList<>();
        for (String t : tables) {
            if (Destruction.CONSERVEES.contains(t)) {
                continue;
            }
            boolean porteTenant = jdbc.sql("PRAGMA table_info(\"" + t + "\")").query((rs, i) -> rs.getString("name")).list().contains("tenantId");
            if (porteTenant) {
                sortie.add(t);
            }
        }
        return sortie;
    }

    public Optional<Destruction.Attestation> lireAttestation(String tenantId) {
        return jdbc.sql("SELECT * FROM saas_attestations_destruction WHERE tenantId = ?").param(tenantId).query((rs, i) ->
                new Destruction.Attestation(rs.getString("id"), rs.getString("tenantId"), rs.getString("tenantNom"), rs.getString("detruitLe"), rs.getString("detruitPar"),
                        rs.getString("motif"), JSON.readValue(rs.getString("tablesJson"), TABLES), rs.getInt("totalLignes"), rs.getString("empreinte"))).optional();
    }

    public record Locataire(String nom, String statut) { }

    public Locataire locataire(String tenantId) {
        return jdbc.sql("SELECT name, status FROM tenants WHERE id = ?").param(tenantId).query((rs, i) -> new Locataire(rs.getString("name"), rs.getString("status"))).optional()
                .orElseThrow(() -> new ca.vina.nexu.partage.erreurs.domaine.IntrouvableException("tenant not found"));
    }

    public Destruction.Attestation detruire(String tenantId, String acteur, String confirmation, String motif) {
        Locataire l = locataire(tenantId);
        Destruction.verifierVerrous(l.statut(), l.nom(), confirmation == null ? "" : confirmation, lireAttestation(tenantId).isPresent());
        // Le pragma répond une ligne : il se lit, il ne s'« exécute » pas.
        jdbc.sql("PRAGMA secure_delete = ON").query().listOfRows();
        List<Destruction.Table> detail = new ArrayList<>();
        for (String table : tablesParEtablissement()) {
            int n = jdbc.sql("DELETE FROM \"" + table + "\" WHERE tenantId = ?").param(tenantId).update();
            if (n > 0) {
                detail.add(new Destruction.Table(table, n));
            }
        }
        String instant = Iso.de(horloge.instant());
        int total = detail.stream().mapToInt(Destruction.Table::lignes).sum();
        String empreinte = Destruction.empreinte(tenantId, instant, acteur, detail);
        jdbc.sql("INSERT INTO saas_attestations_destruction (id, tenantId, tenantNom, detruitLe, detruitPar, motif, tablesJson, totalLignes, empreinte) VALUES (?,?,?,?,?,?,?,?,?)")
                .params(UUID.randomUUID().toString(), tenantId, l.nom(), instant, acteur, motif, JSON.writeValueAsString(detail), total, empreinte).update();
        try {
            jdbc.sql("VACUUM").update();
        } catch (RuntimeException e) {
            // Base verrouillée par une autre connexion : les pages sont déjà réécrites par secure_delete.
        }
        return lireAttestation(tenantId).orElseThrow();
    }
}
