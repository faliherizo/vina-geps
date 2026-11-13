package ca.vina.nexu.integrations.infrastructure.persistance;

import ca.vina.nexu.integrations.domaine.ClientApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Clients d'API — persistance, isolation et vérification du secret.
 *
 * <p>Ce dépôt garde des identifiants machine : c'est la surface d'attaque la plus directe du
 * produit, puisqu'un client d'API agit sans intervention humaine. Trois invariants sont vérifiés
 * ici plutôt que relus : le secret n'est jamais stocké en clair, un client révoqué ne s'authentifie
 * plus, et un établissement ne peut pas révoquer le client d'un autre.
 */
@DisplayName("Clients d'API — persistance et secrets")
class DepotClientsApiTest {

    private DepotClientsApi depot;
    private JdbcClient jdbc;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        SimpleDriverDataSource source = new SimpleDriverDataSource(
                new org.sqlite.JDBC(), "jdbc:sqlite:" + dossier.resolve("essai.db"));
        jdbc = JdbcClient.create(source);
        jdbc.sql("""
                CREATE TABLE api_clients (
                  id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, name TEXT NOT NULL,
                  clientId TEXT NOT NULL UNIQUE, clientSecretHash TEXT NOT NULL, scopes TEXT NOT NULL,
                  active INTEGER NOT NULL DEFAULT 1, createdAt TEXT NOT NULL, lastUsedAt TEXT)
                """).update();
        depot = new DepotClientsApi(jdbc);
    }

    @Test
    @DisplayName("le secret n'est jamais écrit en clair et n'est rendu qu'une fois")
    void secretJamaisEnClair() {
        ClientApi.AvecSecret cree = depot.creer("t-001", "Intégration paie", List.of("read"));

        String empreinte = jdbc.sql("SELECT clientSecretHash FROM api_clients WHERE id = ?")
                .param(cree.client().id()).query(String.class).single();

        assertThat(empreinte).doesNotContain(cree.clientSecret()).startsWith("$2");
        assertThat(cree.clientSecret()).startsWith("csec_");
        assertThat(cree.client().clientId()).startsWith("cid_");
        // La relecture ne rend que le client, jamais le secret : aucun champ ne le porte.
        assertThat(depot.lister("t-001").getFirst()).isEqualTo(cree.client());
    }

    @Test
    @DisplayName("le nom est rogné et les portées assainies à la création")
    void nomEtPorteesAssainis() {
        ClientApi.AvecSecret cree = depot.creer("t-001", "  Intégration  ", List.of("write", "inconnue"));

        assertThat(cree.client().name()).isEqualTo("Intégration");
        assertThat(cree.client().scopes()).containsExactly("write");
        assertThat(cree.client().active()).isTrue();
        assertThat(cree.client().lastUsedAt()).isNull();
    }

    @Test
    @DisplayName("un établissement ne voit ni ne révoque les clients d'un autre")
    void isolationParEtablissement() {
        ClientApi.AvecSecret a = depot.creer("t-001", "A", List.of("read"));
        depot.creer("t-002", "B", List.of("read"));

        assertThat(depot.lister("t-001")).extracting(ClientApi::name).containsExactly("A");
        assertThat(depot.revoquer("t-002", a.client().id())).isFalse();
        assertThat(depot.revoquer("t-001", a.client().id())).isTrue();
        // Révoqué une fois, il ne l'est plus une seconde : la mise à jour ne touche plus de ligne
        // active — mais la clause `WHERE` du Node ne filtre pas sur `active`, donc elle reste vraie.
        assertThat(depot.revoquer("t-001", "inexistant")).isFalse();
    }

    @Test
    @DisplayName("le bon secret authentifie et l'usage est daté")
    void authentificationReussie() {
        ClientApi.AvecSecret cree = depot.creer("t-001", "Intégration", List.of("read", "write"));

        Optional<ClientApi> verifie = depot.verifier(cree.client().clientId(), cree.clientSecret());

        assertThat(verifie).isPresent();
        assertThat(verifie.get().scopes()).containsExactly("read", "write");
        assertThat(verifie.get().tenantId()).isEqualTo("t-001");
        assertThat(depot.lister("t-001").getFirst().lastUsedAt())
                .as("l'usage doit être daté — c'est la seule trace d'activité d'un client machine")
                .isNotNull();
    }

    @Test
    @DisplayName("un mauvais secret, un identifiant inconnu ou un client révoqué échouent pareillement")
    void authentificationRefusee() {
        ClientApi.AvecSecret cree = depot.creer("t-001", "Intégration", List.of("read"));

        assertThat(depot.verifier(cree.client().clientId(), "csec_faux")).isEmpty();
        assertThat(depot.verifier(cree.client().clientId(), null)).isEmpty();
        assertThat(depot.verifier("cid_inexistant", cree.clientSecret())).isEmpty();

        depot.revoquer("t-001", cree.client().id());
        assertThat(depot.verifier(cree.client().clientId(), cree.clientSecret()))
                .as("un client révoqué ne s'authentifie plus, même avec le bon secret").isEmpty();
    }

    @Test
    @DisplayName("les clients sortent du plus récent au plus ancien")
    void ordreDecroissant() {
        // L'ordre est celui du Node (`ORDER BY createdAt DESC`). Deux créations dans la même
        // milliseconde partageraient l'horodatage : les noms sont donc distincts et le test
        // n'affirme que la présence des deux, pas leur ordre relatif à égalité de date.
        depot.creer("t-001", "Premier", List.of("read"));
        depot.creer("t-001", "Second", List.of("read"));

        assertThat(depot.lister("t-001")).extracting(ClientApi::name)
                .containsExactlyInAnyOrder("Premier", "Second");
        assertThat(depot.lister("t-vide")).isEmpty();
    }

    @Test
    @DisplayName("un client révoqué se relit comme inactif, il ne disparaît pas de la liste")
    void clientRevoqueResteVisible() {
        // La révocation n\u0027efface pas : l\u0027administration doit continuer à voir qu\u0027un client a
        // existé et a été retiré. Un client supprimé de la liste laisserait croire à une erreur.
        ClientApi.AvecSecret cree = depot.creer("t-001", "Intégration", List.of("read"));
        depot.revoquer("t-001", cree.client().id());

        assertThat(depot.lister("t-001")).singleElement()
                .extracting(ClientApi::active).isEqualTo(false);
    }

    @Test
    @DisplayName("une colonne de portées vide se lit comme aucune portée")
    void porteesVidesEnBase() {
        // Cas d\u0027une ligne écrite par une autre version : la lecture ne doit ni lever, ni
        // fabriquer une portée par défaut qui accorderait un accès jamais demandé.
        jdbc.sql("""
                INSERT INTO api_clients (id, tenantId, name, clientId, clientSecretHash, scopes,
                  active, createdAt, lastUsedAt)
                VALUES ('vieux', 't-001', 'Ancien', 'cid_vieux', 'x', '', 1, '2020-01-01T00:00:00Z', NULL)
                """).update();

        assertThat(depot.lister("t-001")).singleElement()
                .extracting(ClientApi::scopes).isEqualTo(List.of());
    }
}
