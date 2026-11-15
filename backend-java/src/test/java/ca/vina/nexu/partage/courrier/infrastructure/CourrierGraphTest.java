package ca.vina.nexu.partage.courrier.infrastructure;

import ca.vina.nexu.partage.courrier.Courrier;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** ST-G11 (critère 40) : le courrier par Microsoft Graph, contre un faux Entra et un faux Graph sur un port local. */
@DisplayName("Courrier par Microsoft Graph — jeton d'application gardé, sendMail au nom de la boîte, refus journalisés")
class CourrierGraphTest {

    private static final class Horloge extends Clock {
        private Instant maintenant = Instant.parse("2026-11-15T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return maintenant;
        }
    }

    private record Requete(String methode, String chemin, String autorisation, String corps) { }

    private HttpServer serveur;
    private final List<Requete> requetes = new ArrayList<>();
    private int statutJeton = 200;
    private int statutEnvoi = 202;
    private final Horloge horloge = new Horloge();
    private CourrierGraph graph;

    @BeforeEach
    void demarrer() throws IOException {
        serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext("/", e -> {
            String corps = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requetes.add(new Requete(e.getRequestMethod(), e.getRequestURI().getPath(), e.getRequestHeaders().getFirst("Authorization"), corps));
            boolean jeton = e.getRequestURI().getPath().endsWith("/token");
            byte[] reponse = (jeton ? "{\"access_token\":\"AT-" + requetes.size() + "\",\"expires_in\":3600}" : "").getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(jeton ? statutJeton : statutEnvoi, reponse.length == 0 ? -1 : reponse.length);
            if (reponse.length > 0) {
                e.getResponseBody().write(reponse);
            }
            e.close();
        });
        serveur.start();
        String base = "http://127.0.0.1:" + serveur.getAddress().getPort();
        graph = new CourrierGraph(new CourrierGraph.Reglages(base, base, "locataire-1", "client-1", "secret &=", "alertes@cusante.example"),
                HttpClient.newHttpClient(), horloge);
    }

    @AfterEach
    void arreter() {
        serveur.stop(0);
    }

    @Test
    @DisplayName("un jeton par le flux client, gardé jusqu'à une minute de son expiration ; sendMail au nom de la boîte, en texte brut")
    void envoi() {
        assertThat(graph.envoyer(new Courrier.Message("julie@exemple.ca", "Code bleu", "5e étage"))).isTrue();
        assertThat(requetes).hasSize(2);
        Requete jeton = requetes.get(0);
        assertThat(jeton.chemin()).isEqualTo("/locataire-1/oauth2/v2.0/token");
        String formulaire = URLDecoder.decode(jeton.corps(), StandardCharsets.UTF_8);
        assertThat(formulaire).contains("grant_type=client_credentials").contains("client_id=client-1").contains("client_secret=secret &=")
                .contains("scope=http://127.0.0.1:" + serveur.getAddress().getPort() + "/.default");
        Requete envoi = requetes.get(1);
        assertThat(envoi.chemin()).isEqualTo("/v1.0/users/alertes@cusante.example/sendMail");
        assertThat(envoi.autorisation()).isEqualTo("Bearer AT-1");
        assertThat(envoi.corps()).contains("\"subject\":\"Code bleu\"").contains("\"contentType\":\"Text\"").contains("\"content\":\"5e étage\"")
                .contains("\"address\":\"julie@exemple.ca\"").contains("\"saveToSentItems\":false");
        // Le jeton sert encore ; à moins d'une minute de son expiration, il se renouvelle.
        graph.envoyer(new Courrier.Message("marc@exemple.ca", "Relance", "x"));
        assertThat(requetes).hasSize(3);
        horloge.maintenant = horloge.maintenant.plusSeconds(3600 - 30);
        graph.envoyer(new Courrier.Message("marc@exemple.ca", "Relance", "x"));
        assertThat(requetes).hasSize(5);
        assertThat(requetes.get(4).autorisation()).isEqualTo("Bearer AT-4");
        assertThat(graph.transport()).isEqualTo(new Courrier.Transport("graph", "http://127.0.0.1:" + serveur.getAddress().getPort(), "alertes@cusante.example"));
    }

    @Test
    @DisplayName("un refus de Graph, un jeton refusé, un service injoignable ou un fil interrompu : faux, jamais une exception")
    void refus() {
        Courrier.Message m = new Courrier.Message("julie@exemple.ca", "Code bleu", "x");
        statutEnvoi = 403;
        assertThat(graph.envoyer(m)).isFalse();
        statutJeton = 401;
        horloge.maintenant = horloge.maintenant.plusSeconds(7200);
        assertThat(graph.envoyer(m)).isFalse();
        Thread.currentThread().interrupt();
        assertThat(graph.envoyer(m)).isFalse();
        assertThat(Thread.interrupted()).isTrue();
        serveur.stop(0);
        statutJeton = 200;
        assertThat(graph.envoyer(m)).isFalse();
    }

    @Test
    @DisplayName("Graph est choisi dès qu'une application Entra est réglée ; à défaut de MAIL_FROM, l'expéditeur par défaut")
    void choix() {
        CourrierJournal journal = new CourrierJournal();
        Courrier choisi = ConfigurationCourrier.choisir(Map.of("GRAPH_CLIENT_ID", "c", "GRAPH_TENANT_ID", "t", "SMTP_HOST", "smtp.exemple.qc"), journal);
        assertThat(choisi).isInstanceOf(CourrierGraph.class);
        assertThat(choisi.transport().mode()).isEqualTo("graph");
        assertThat(choisi.transport().hote()).isEqualTo(CourrierGraph.GRAPH);
        assertThat(choisi.transport().expediteur()).isEqualTo(Courrier.EXPEDITEUR_PAR_DEFAUT);
        assertThat(CourrierGraph.depuis(Map.of("MAIL_FROM", " boite@cusante.example "), Clock.systemUTC()).transport().expediteur()).isEqualTo("boite@cusante.example");
        assertThat(ConfigurationCourrier.choisir(Map.of("GRAPH_CLIENT_ID", " "), journal)).isSameAs(journal);
        assertThat(CourrierGraph.adresse("Nexu VINA <nexu@cusante.example>")).isEqualTo("nexu@cusante.example");
        assertThat(CourrierGraph.adresse("nexu@cusante.example")).isEqualTo("nexu@cusante.example");
        assertThat(CourrierGraph.adresse("Nexu <incomplet")).isEqualTo("Nexu <incomplet");
    }
}
