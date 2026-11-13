package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.DepotConnecteursIntegrationPort;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.CodeConnecteur;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.ConfigConnecteur;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.ConnecteurEnregistre;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.Mode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

/** Adaptateur SQLite des connecteurs configurés ({@code eff_connecteurs}). */
@Repository("effectifsDepotConnecteursIntegration")
public class DepotConnecteursIntegration implements DepotConnecteursIntegrationPort {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<Object>> LISTE = new TypeReference<>() { };

    private final JdbcClient jdbc;

    public DepotConnecteursIntegration(JdbcClient jdbc, SchemaIntegrationsEffectifs schema) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ConnecteurEnregistre> lire(String tenantId, CodeConnecteur code) {
        return jdbc.sql("SELECT * FROM eff_connecteurs WHERE tenantId = ? AND code = ?").params(tenantId, code.name())
                .query((rs, i) -> new ConnecteurEnregistre(code, new ConfigConnecteur(Mode.valueOf(rs.getString("mode")), rs.getString("url"),
                        rs.getString("jetonRef"), rs.getInt("actif") == 1), JSON.readValue(rs.getString("jeuEssai"), LISTE), rs.getString("curseur"))).optional();
    }

    @Override
    public void enregistrer(String tenantId, ConnecteurEnregistre c, String modifieLe, String modifiePar) {
        jdbc.sql("INSERT INTO eff_connecteurs (tenantId, code, mode, url, jetonRef, actif, jeuEssai, curseur, modifieLe, modifiePar) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenantId, code) DO UPDATE SET mode = excluded.mode, url = excluded.url, jetonRef = excluded.jetonRef, actif = excluded.actif, "
                        + "jeuEssai = excluded.jeuEssai, modifieLe = excluded.modifieLe, modifiePar = excluded.modifiePar")
                .params(tenantId, c.code().name(), c.config().mode().name(), c.config().url(), c.config().jetonRef(), c.config().actif() ? 1 : 0,
                        JSON.writeValueAsString(c.jeuEssai()), c.curseur(), modifieLe, modifiePar).update();
    }

    @Override
    public void poserCurseur(String tenantId, CodeConnecteur code, String curseur) {
        jdbc.sql("UPDATE eff_connecteurs SET curseur = ? WHERE tenantId = ? AND code = ?").params(curseur, tenantId, code.name()).update();
    }
}
