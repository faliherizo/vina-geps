package ca.vina.nexu.integrations.infrastructure.persistance;

import ca.vina.nexu.integrations.domaine.ClientApi;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Repository;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Clients d'API — accès aux données (AO-5338 #NF-08).
 *
 * <p>Le secret n'est stocké que <strong>haché</strong>, au coût 8, comme côté Node : le même
 * facteur de travail des deux côtés, sinon un secret créé par l'un serait rejeté ou vérifié plus
 * lentement par l'autre pendant l'étranglement.
 *
 * <p>Les identifiants sont des UUID et non le {@code prefixe-horodatage-séquence} du Node.
 * Volontaire : la séquence du Node vit dans une table partagée, et deux backends qui écrivent
 * dans la même base la feraient collisionner. Un UUID n'a pas d'état commun à corrompre.
 */
@Repository
public class DepotClientsApi {

    private static final SecureRandom ALEA = new SecureRandom();
    private static final BCryptPasswordEncoder ENCODEUR = new BCryptPasswordEncoder(8);

    private final JdbcClient jdbc;

    public DepotClientsApi(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static String hexAleatoire(int octets) {
        byte[] b = new byte[octets];
        ALEA.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    public ClientApi.AvecSecret creer(String tenantId, String nom, List<String> porteesDemandees) {
        List<String> portees = ClientApi.assainirPortees(porteesDemandees);
        String id = UUID.randomUUID().toString();
        // Mêmes longueurs que le Node : 9 octets pour l'identifiant public, 24 pour le secret.
        String clientId = "cid_" + hexAleatoire(9);
        String secret = "csec_" + hexAleatoire(24);
        String creeLe = Instant.now().toString();

        jdbc.sql("""
                INSERT INTO api_clients
                  (id, tenantId, name, clientId, clientSecretHash, scopes, active, createdAt, lastUsedAt)
                VALUES (?, ?, ?, ?, ?, ?, 1, ?, NULL)
                """)
                .params(id, tenantId, nom.trim(), clientId, ENCODEUR.encode(secret),
                        enJson(portees), creeLe)
                .update();

        return new ClientApi.AvecSecret(
                new ClientApi(id, tenantId, nom.trim(), clientId, portees, true, creeLe, null), secret);
    }

    public List<ClientApi> lister(String tenantId) {
        return jdbc.sql("SELECT * FROM api_clients WHERE tenantId = ? ORDER BY createdAt DESC")
                .param(tenantId).query(DepotClientsApi::versClient).list();
    }

    /** @return vrai si un client a bien été révoqué — faux si aucun ne correspond à ce tenant. */
    public boolean revoquer(String tenantId, String id) {
        return jdbc.sql("UPDATE api_clients SET active = 0 WHERE tenantId = ? AND id = ?")
                .params(tenantId, id).update() > 0;
    }

    /**
     * Vérifie un couple identifiant/secret et note l'usage.
     *
     * <p>Un client révoqué est introuvable, et non « trouvé mais inactif » : la distinction ne
     * doit pas remonter jusqu'à l'appelant, qui apprendrait ainsi qu'un identifiant a existé.
     */
    public Optional<ClientApi> verifier(String clientId, String secret) {
        Optional<Ligne> ligne = jdbc.sql("SELECT * FROM api_clients WHERE clientId = ? AND active = 1")
                .param(clientId).query((rs, n) -> new Ligne(versClient(rs, n), rs.getString("clientSecretHash")))
                .optional();

        if (ligne.isEmpty() || secret == null || !ENCODEUR.matches(secret, ligne.get().empreinte())) {
            return Optional.empty();
        }
        jdbc.sql("UPDATE api_clients SET lastUsedAt = ? WHERE id = ?")
                .params(Instant.now().toString(), ligne.get().client().id()).update();
        return Optional.of(ligne.get().client());
    }

    private record Ligne(ClientApi client, String empreinte) {
    }

    private static ClientApi versClient(ResultSet rs, int n) throws SQLException {
        return new ClientApi(
                rs.getString("id"), rs.getString("tenantId"), rs.getString("name"),
                rs.getString("clientId"), depuisJson(rs.getString("scopes")),
                rs.getInt("active") != 0, rs.getString("createdAt"), rs.getString("lastUsedAt"));
    }

    /**
     * Les portées sont stockées en JSON par le Node. Deux valeurs seulement, sans caractère à
     * échapper : un encodage à la main évite d'introduire une dépendance de sérialisation dans
     * une couche qui n'en a pas besoin.
     */
    private static String enJson(List<String> portees) {
        return portees.stream().map(p -> '"' + p + '"')
                .reduce((a, b) -> a + "," + b).map(s -> "[" + s + "]").orElse("[]");
    }

    private static List<String> depuisJson(String json) {
        if (json == null) {
            return List.of();
        }
        return java.util.Arrays.stream(json.replaceAll("[\\[\\]\"\\s]", "").split(","))
                .filter(s -> !s.isEmpty()).toList();
    }
}
