package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.DestinataireIndisponibleException;
import ca.vina.nexu.effectifs.domaine.DestinationIntegrationPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.Acceptee;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.CodeConnecteur;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.ConfigConnecteur;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.ConnecteurEnregistre;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.DossierEmploye;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.HorodatageExterne;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.Issue;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.MessageALivrer;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.Mode;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.Rejetee;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.TypeMessage;
import ca.vina.nexu.effectifs.domaine.SecretsIntegrationPort;
import ca.vina.nexu.effectifs.domaine.SourceDossiersIntegrationPort;
import ca.vina.nexu.effectifs.domaine.SourceHorodatagesIntegrationPort;
import ca.vina.nexu.effectifs.domaine.SourceIndisponibleException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <strong>Intégrations des effectifs (lot R10) — le contrat des ports</strong>, jumeau de {@code effectifs-integrations-contrat.test.ts}.
 *
 * <p>Chaque port passe la <strong>même suite</strong> (une classe abstraite) contre le simulateur et contre l'adaptateur HTTP
 * branché sur un vrai serveur HTTP local, joint par le vrai client du JDK : ce que le simulateur promet en démonstration, le
 * client HTTP le tient en production (LSP). Le faux serveur ne fait qu'appliquer la convention documentée : 2xx accepté,
 * 409/422 rejeté, 5xx passager.
 */
@DisplayName("Intégrations des effectifs — contrat des ports (simulateur et HTTP ; SFTP dans IntegrationsSftpTest)")
class ContratIntegrationsEffectifsTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    static final List<HorodatageExterne> JEU = List.of(
            new HorodatageExterne("a", "P1", "ENTREE", "2031-01-15T10:50:00.000Z"),
            new HorodatageExterne("b", "P1", "SORTIE", "2031-01-15T23:00:00.000Z"));
    static final List<DossierEmploye> DOSSIERS = List.of(new DossierEmploye("P1", 12), new DossierEmploye("P2", 3.5));

    /** Une requête reçue par le faux serveur. */
    private record Recue(String chemin, String requete, Map<String, List<String>> entetes, String corps) { }

    private HttpServer serveur;
    private String base;
    private final List<Recue> recues = new ArrayList<>();
    private final Map<String, Integer> references = new HashMap<>();
    private final ClientHttpIntegration.Appel reseau = ClientHttpIntegration.reseau(HttpClient.newHttpClient());

    @BeforeEach
    void demarrer() throws IOException {
        serveur = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        serveur.createContext("/", this::traiter);
        serveur.start();
        base = "http://127.0.0.1:" + serveur.getAddress().getPort();
    }

    @AfterEach
    void arreter() {
        serveur.stop(0);
    }

    private void traiter(HttpExchange x) throws IOException {
        String corps = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String chemin = x.getRequestURI().getPath();
        Map<String, List<String>> entetes = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        entetes.putAll(x.getRequestHeaders());
        recues.add(new Recue(chemin, x.getRequestURI().getRawQuery(), entetes, corps));
        Object[] reponse = reponse(chemin, x, corps);
        byte[] octets = String.valueOf(reponse[1]).getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().add("Content-Type", "application/json");
        x.sendResponseHeaders((int) reponse[0], octets.length);
        x.getResponseBody().write(octets);
        x.close();
    }

    /** La convention documentée, rien de plus. */
    private Object[] reponse(String chemin, HttpExchange x, String corps) {
        if (chemin.startsWith("/panne")) {
            return new Object[]{503, "{}"};
        }
        if (chemin.startsWith("/erreur")) {
            return new Object[]{500, "{}"};
        }
        if (chemin.startsWith("/pasliste")) {
            return new Object[]{200, "{\"dossiers\":[]}"};
        }
        if (chemin.startsWith("/pasjson")) {
            return new Object[]{200, "pas du json"};
        }
        if (chemin.startsWith("/api/")) {
            return destinataireConforme(x, corps);
        }
        if (chemin.endsWith("/horodatages")) {
            String depuis = URLDecoder.decode(x.getRequestURI().getRawQuery().substring("depuis=".length()), StandardCharsets.UTF_8);
            return new Object[]{200, JSON.writeValueAsString(JEU.stream().filter(h -> h.horodatage().compareTo(depuis) > 0).toList())};
        }
        List<?> matricules = (List<?>) JSON.readValue(corps, Map.class).get("matricules");
        return new Object[]{200, JSON.writeValueAsString(DOSSIERS.stream().filter(d -> matricules.contains(d.matricule())).toList())};
    }

    /** Le destinataire HTTP qui se comporte comme le simulateur : rejet sur le préfixe, idempotence par clé. */
    private Object[] destinataireConforme(HttpExchange x, String corps) {
        if (JSON.readValue(corps, Map.class).get("charge").toString().contains(DestinationSimuleeIntegration.PREFIXE_REJET_SIMULE)) {
            return new Object[]{422, "{\"message\":\"valeur refusée par le destinataire\"}"};
        }
        String cle = x.getRequestHeaders().getFirst("Idempotency-Key");
        references.putIfAbsent(cle, references.size() + 1);
        return new Object[]{201, "{\"id\":\"REF-" + references.get(cle) + "\"}"};
    }

    static MessageALivrer message(String cle, Map<String, Object> charge) {
        return new MessageALivrer("m-" + cle, CodeConnecteur.PFT, TypeMessage.LOT_PAIE, cle, charge);
    }

    static MessageALivrer message(String cle) {
        return message(cle, Map.of("lignes", List.of(Map.of("codePaie", "100"))));
    }

    private static ClientHttpIntegration.ConfigHttp config(String url, String jeton) {
        return new ClientHttpIntegration.ConfigHttp(url, Optional.ofNullable(jeton));
    }

    // ── La suite de contrat, une par port ────────────────────────────────────

    abstract static class ContratDestination {
        abstract DestinationIntegrationPort fabrique();

        @Test
        @DisplayName("accepte un message et rend une référence ; le même message rend la même référence")
        void accepte() {
            DestinationIntegrationPort d = fabrique();
            Issue a = d.livrer(message("k1"));
            assertThat(a).isInstanceOf(Acceptee.class);
            assertThat(d.livrer(message("k1"))).isEqualTo(a);
            assertThat(d.livrer(message("k2"))).isNotEqualTo(a);
        }
    }

    /**
     * Une destination qui <strong>répond</strong> (simulateur, HTTP) dit aussi son refus, avec un motif. Un dépôt de
     * fichier (SFTP) n'a pas de réponse : il ne passe que la suite de base, le refus éventuel revient par un autre lot.
     */
    abstract static class ContratDestinationQuiRepond extends ContratDestination {

        @Test
        @DisplayName("rejette une valeur refusée, avec un motif lisible")
        void rejette() {
            Issue r = fabrique().livrer(message("k3", Map.of("lignes", List.of(Map.of("codePaie", DestinationSimuleeIntegration.PREFIXE_REJET_SIMULE + "999")))));
            assertThat(r).isInstanceOfSatisfying(Rejetee.class, x -> assertThat(x.motif()).contains("refusée par le destinataire"));
        }
    }

    abstract static class ContratHorodatages {
        abstract SourceHorodatagesIntegrationPort fabrique();

        @Test
        @DisplayName("rend, dans l'ordre, ce qui suit l'instant demandé (exclu)")
        void apres() {
            assertThat(fabrique().lire("1970-01-01T00:00:00.000Z")).extracting(HorodatageExterne::cle).containsExactly("a", "b");
            assertThat(fabrique().lire("2031-01-15T10:50:00.000Z")).extracting(HorodatageExterne::cle).containsExactly("b");
            assertThat(fabrique().lire("2031-02-01T00:00:00.000Z")).isEmpty();
        }
    }

    abstract static class ContratDossiers {
        abstract SourceDossiersIntegrationPort fabrique();

        @Test
        @DisplayName("rend les dossiers demandés ; un matricule inconnu est simplement absent")
        void demandes() {
            assertThat(fabrique().lire(List.of("P2", "Z9"))).containsExactly(new DossierEmploye("P2", 3.5));
            assertThat(fabrique().lire(List.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("contrat DestinationIntegrationPort — simulateur")
    class DestinationSimulee extends ContratDestinationQuiRepond {
        @Override
        DestinationIntegrationPort fabrique() {
            return new DestinationSimuleeIntegration();
        }
    }

    @Nested
    @DisplayName("contrat DestinationIntegrationPort — HTTP")
    class DestinationHttp extends ContratDestinationQuiRepond {
        @Override
        DestinationIntegrationPort fabrique() {
            return new DestinationHttpIntegration(config(base + "/api/", "j"), reseau);
        }
    }

    @Nested
    @DisplayName("contrat SourceHorodatagesIntegrationPort — simulateur")
    class HorodatagesSimules extends ContratHorodatages {
        @Override
        SourceHorodatagesIntegrationPort fabrique() {
            return new HorodatagesSimulesIntegration(List.of(JEU.get(1), JEU.get(0)));
        }
    }

    @Nested
    @DisplayName("contrat SourceHorodatagesIntegrationPort — HTTP")
    class HorodatagesHttp extends ContratHorodatages {
        @Override
        SourceHorodatagesIntegrationPort fabrique() {
            return new HorodatagesHttpIntegration(config(base + "/rao", null), reseau);
        }
    }

    @Nested
    @DisplayName("contrat SourceDossiersIntegrationPort — simulateur")
    class DossiersSimules extends ContratDossiers {
        @Override
        SourceDossiersIntegrationPort fabrique() {
            return new DossiersSimulesIntegration(DOSSIERS);
        }
    }

    @Nested
    @DisplayName("contrat SourceDossiersIntegrationPort — HTTP")
    class DossiersHttp extends ContratDossiers {
        @Override
        SourceDossiersIntegrationPort fabrique() {
            return new DossiersHttpIntegration(config(base + "/corpo", "j"), reseau);
        }
    }

    // ── Ce que le contrat ne couvre pas ──────────────────────────────────────

    @Test
    @DisplayName("envoie la clé d'idempotence et le jeton ; une panne passagère lève ; une réponse sans corps est acceptée")
    void destinationHttp() {
        new DestinationHttpIntegration(config(base + "/api//", "j"), reseau).livrer(message("k"));
        Recue r = recues.getFirst();
        assertThat(r.chemin()).isEqualTo("/api/lot-paie");
        assertThat(r.entetes().get("Authorization")).containsExactly("Bearer j");
        assertThat(r.entetes().get("Idempotency-Key")).containsExactly("k");
        assertThat(JSON.readValue(r.corps(), Map.class)).isEqualTo(Map.of("id", "m-k", "type", "LOT_PAIE", "cle", "k", "charge", Map.of("lignes", List.of(Map.of("codePaie", "100")))));
        MessageALivrer versPanne = new MessageALivrer("m", CodeConnecteur.PFT, TypeMessage.LOT_PAIE, "k", Map.of());
        assertThatThrownBy(() -> new DestinationHttpIntegration(config(base + "/panne", null), reseau).livrer(versPanne))
                .isInstanceOf(DestinataireIndisponibleException.class).hasMessage("PFT en erreur (HTTP 503) : on retentera");
        assertThat(new DestinationHttpIntegration(config(base + "/sans", null), r2 -> new ClientHttpIntegration.Reponse(204, "")).livrer(versPanne))
                .isEqualTo(new Acceptee("HTTP-204"));
        assertThat(new DestinationHttpIntegration(config(base + "/sans", null), r2 -> new ClientHttpIntegration.Reponse(201, null)).livrer(versPanne))
                .isEqualTo(new Acceptee("HTTP-201"));
    }

    @Test
    @DisplayName("un destinataire injoignable ou un appel interrompu est une panne passagère, nommée")
    void destinataireInjoignable() {
        MessageALivrer m = message("k");
        assertThatThrownBy(() -> new DestinationHttpIntegration(config(base, null), r -> {
            throw new IOException("ECONNREFUSED");
        }).livrer(m)).isInstanceOf(DestinataireIndisponibleException.class).hasMessage("PFT injoignable : ECONNREFUSED");
        assertThatThrownBy(() -> new DestinationHttpIntegration(config(base, null), r -> {
            throw new InterruptedException();
        }).livrer(m)).hasMessage("PFT injoignable : appel interrompu");
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    @DisplayName("une source injoignable, en erreur ou qui ne rend pas une liste est indisponible")
    void sourceIndisponible() {
        assertThatThrownBy(() -> new HorodatagesHttpIntegration(config(base, null), r -> {
            throw new IOException("ECONNREFUSED");
        }).lire("x")).isInstanceOf(SourceIndisponibleException.class).hasMessage("source injoignable : ECONNREFUSED");
        assertThatThrownBy(() -> new HorodatagesHttpIntegration(config(base, null), r -> {
            throw new IOException();
        }).lire("x")).hasMessage("source injoignable : IOException");
        assertThatThrownBy(() -> new HorodatagesHttpIntegration(config(base + "/erreur", null), reseau).lire("x")).hasMessage("source en erreur (HTTP 500)");
        assertThatThrownBy(() -> new HorodatagesHttpIntegration(config(base, null), r -> new ClientHttpIntegration.Reponse(199, "[]")).lire("x"))
                .hasMessage("source en erreur (HTTP 199)");
        assertThatThrownBy(() -> new DossiersHttpIntegration(config(base + "/pasliste", null), reseau).lire(List.of("P1")))
                .hasMessage("la source n'a pas rendu une liste");
        assertThatThrownBy(() -> new DossiersHttpIntegration(config(base + "/pasjson", null), reseau).lire(List.of("P1")))
                .hasMessage("la source n'a pas rendu une liste");
        assertThat(new HorodatagesHttpIntegration(config(base, null), r -> new ClientHttpIntegration.Reponse(200, "[null,{\"cle\":1}]")).lire("x"))
                .containsExactly(new HorodatageExterne("", "", "", ""), new HorodatageExterne("1", "", "", ""));
    }

    @Test
    @DisplayName("une source réelle fermée : le client du JDK lève, et la source est dite injoignable")
    void sourceFermee() throws IOException {
        HttpServer ferme = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        int port = ferme.getAddress().getPort();
        ferme.start();
        ferme.stop(0);
        assertThatThrownBy(() -> new HorodatagesHttpIntegration(config("http://127.0.0.1:" + port, null), reseau).lire("x"))
                .isInstanceOf(SourceIndisponibleException.class).hasMessageStartingWith("source injoignable : ");
    }

    @Test
    @DisplayName("sans jeton, aucun en-tête d'autorisation ; l'instant est encodé comme encodeURIComponent")
    void horodatagesSansJeton() {
        new HorodatagesHttpIntegration(config(base + "/rao/", null), reseau).lire("2031-01-15 10:50:00+00:00");
        assertThat(recues.getFirst().entetes()).doesNotContainKey("Authorization");
        assertThat(recues.getFirst().requete()).isEqualTo("depuis=2031-01-15%2010%3A50%3A00%2B00%3A00");
    }

    @Test
    @DisplayName("le simulateur rejette aussi une valeur enfouie dans une liste, et garde ce qu'il a reçu")
    void simulateur() {
        DestinationSimuleeIntegration d = new DestinationSimuleeIntegration();
        assertThat(d.livrer(message("k", Map.of("membres", List.of(Map.of("matricule", "REJ-1")))))).isInstanceOf(Rejetee.class);
        Map<String, Object> sansRejet = new HashMap<>();
        sansRejet.put("n", 3);
        sansRejet.put("vide", null);
        assertThat(d.livrer(message("k", sansRejet))).isEqualTo(new Acceptee("PFT-SIM-1"));
        assertThat(d.livrer(message("k2"))).isEqualTo(new Acceptee("PFT-SIM-2"));
        assertThat(d.recus()).containsOnlyKeys("k", "k2");
    }

    @Test
    @DisplayName("registre : simulateur par organisme et connecteur, HTTP quand configuré, jeton lu à l'appel")
    void registre() {
        SecretsIntegrationPort env = nom -> nom.equals("PFT_JETON") ? Optional.of("secret") : Optional.empty();
        RegistreConnecteursIntegration registre = new RegistreConnecteursIntegration(env, reseau);
        ConnecteurEnregistre simule = new ConnecteurEnregistre(CodeConnecteur.PFT, ConfigConnecteur.DEFAUT,
                List.of(Map.of("cle", "a", "matricule", "P1", "sens", "ENTREE", "horodatage", "2031-01-15T10:50:00.000Z"), Map.of("matricule", "P2", "anciennete", 3.5)),
                null);
        ConnecteurEnregistre http = new ConnecteurEnregistre(CodeConnecteur.PFT, new ConfigConnecteur(Mode.HTTP, "https://x", "PFT_JETON", true), List.of(), null);
        assertThat(registre.destination("t1", simule)).isSameAs(registre.destinationSimulee("t1", CodeConnecteur.PFT));
        assertThat(registre.destination("t2", simule)).isNotSameAs(registre.destinationSimulee("t1", CodeConnecteur.PFT));
        assertThat(registre.destination("t1", http)).isInstanceOf(DestinationHttpIntegration.class);
        assertThat(registre.horodatages("t1", http)).isInstanceOf(HorodatagesHttpIntegration.class);
        assertThat(registre.dossiers("t1", http)).isInstanceOf(DossiersHttpIntegration.class);
        assertThat(registre.horodatages("t1", simule).lire("1970")).extracting(HorodatageExterne::cle).containsExactly("a");
        assertThat(registre.dossiers("t1", simule).lire(List.of("P2"))).containsExactly(new DossierEmploye("P2", 3.5));
        ConnecteurEnregistre sansJeton = new ConnecteurEnregistre(CodeConnecteur.PFT, new ConfigConnecteur(Mode.HTTP, "https://x", "ABSENT_JETON", true), List.of(), null);
        assertThatThrownBy(() -> registre.destination("t1", sansJeton)).isInstanceOf(EffectifsInvalideException.class)
                .hasMessageStartingWith("la variable d'environnement ABSENT_JETON n'est pas définie sur le serveur");
    }

    @Test
    @DisplayName("les variables d'environnement du serveur, lues à la demande")
    void environnement() {
        AdaptateurSecretsIntegration env = new AdaptateurSecretsIntegration();
        assertThat(env.variable("PATH")).isPresent();
        assertThat(env.variable("NEXU_VARIABLE_INEXISTANTE_R10")).isEmpty();
        assertThat(new ClientHttpIntegration.ConfigHttp("https://x", Optional.empty()).delai()).isEqualTo(ClientHttpIntegration.DELAI);
    }
}
