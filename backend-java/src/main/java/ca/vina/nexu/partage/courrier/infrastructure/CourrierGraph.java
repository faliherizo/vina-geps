package ca.vina.nexu.partage.courrier.infrastructure;

import ca.vina.nexu.partage.courrier.Courrier;
import ca.vina.nexu.partage.journal.Masquage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Le courrier par <strong>Microsoft Graph</strong> (FX-AO-20260810, ST-G11, critère 40) : une application inscrite dans
 * Entra ID, avec la permission d'application {@code Mail.Send}, obtient un jeton par le flux « client credentials », puis
 * envoie au nom de la boîte d'expédition ({@code /users/{boîte}/sendMail}). Le jeton se garde jusqu'à une minute de son
 * expiration. Comme pour SMTP, un refus ne lève jamais : il se journalise, et le geste métier continue.
 *
 * <p>Variables : {@code GRAPH_TENANT_ID}, {@code GRAPH_CLIENT_ID}, {@code GRAPH_CLIENT_SECRET}, {@code MAIL_FROM} (la
 * boîte d'expédition) ; {@code GRAPH_AUTORITE} et {@code GRAPH_URL} ne changent que pour un nuage national ou un essai.
 */
public class CourrierGraph implements Courrier {

    private static final Logger JOURNAL = LoggerFactory.getLogger(CourrierGraph.class);
    static final String AUTORITE = "https://login.microsoftonline.com";
    static final String GRAPH = "https://graph.microsoft.com";
    private static final Duration DELAI = Duration.ofSeconds(10);
    private static final Duration MARGE_JETON = Duration.ofMinutes(1);
    private static final int ACCEPTE = 202;
    private static final int OK = 200;

    /** Où et comment s'authentifier, et au nom de quelle boîte envoyer. */
    public record Reglages(String autorite, String graph, String locataire, String client, String secret, String expediteur) { }

    private record Jeton(String valeur, Instant expire) { }

    private final Reglages r;
    private final HttpClient http;
    private final Clock horloge;
    private final ObjectMapper json = JsonMapper.builder().build();
    private Jeton jeton;

    public CourrierGraph(Reglages reglages, HttpClient http, Clock horloge) {
        this.r = reglages;
        this.http = http;
        this.horloge = horloge;
    }

    public static CourrierGraph depuis(Map<String, String> env, Clock horloge) {
        String expediteur = adresse(env.getOrDefault("MAIL_FROM", ""));
        return new CourrierGraph(new Reglages(env.getOrDefault("GRAPH_AUTORITE", AUTORITE).trim(), env.getOrDefault("GRAPH_URL", GRAPH).trim(),
                env.getOrDefault("GRAPH_TENANT_ID", "").trim(), env.getOrDefault("GRAPH_CLIENT_ID", "").trim(), env.getOrDefault("GRAPH_CLIENT_SECRET", ""),
                expediteur.isEmpty() ? EXPEDITEUR_PAR_DEFAUT : expediteur), HttpClient.newBuilder().connectTimeout(DELAI).build(), horloge);
    }

    /** {@code Nexu VINA <nexu@exemple.ca>} devient {@code nexu@exemple.ca} : Graph nomme la boîte, pas l'affichage. */
    static String adresse(String mailFrom) {
        String v = mailFrom.trim();
        int ouvre = v.indexOf('<');
        int ferme = v.indexOf('>', ouvre + 1);
        return ouvre >= 0 && ferme > ouvre ? v.substring(ouvre + 1, ferme).trim() : v;
    }

    @Override
    public boolean envoyer(Message message) {
        try {
            HttpResponse<String> reponse = http.send(HttpRequest.newBuilder(URI.create(r.graph() + "/v1.0/users/" + encoder(r.expediteur()) + "/sendMail"))
                    .timeout(DELAI).header("Authorization", "Bearer " + jeton()).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corps(message)))).build(), HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() == ACCEPTE) {
                return true;
            }
            refus(message, "Graph répond " + reponse.statusCode(), null);
        } catch (IOException | IllegalStateException e) {
            refus(message, "Graph injoignable ou jeton refusé", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            refus(message, "envoi interrompu", e);
        }
        return false;
    }

    /** Le message au format de Graph : texte brut, un destinataire, rien dans les éléments envoyés. */
    static Map<String, Object> corps(Message m) {
        return Map.of("message", Map.of("subject", m.subject(), "body", Map.of("contentType", "Text", "content", m.text()),
                "toRecipients", List.of(Map.of("emailAddress", Map.of("address", m.to())))), "saveToSentItems", false);
    }

    /** Le jeton d'application, gardé jusqu'à une minute de son expiration. */
    synchronized String jeton() throws IOException, InterruptedException {
        Instant maintenant = horloge.instant();
        if (jeton != null && maintenant.isBefore(jeton.expire().minus(MARGE_JETON))) {
            return jeton.valeur();
        }
        String formulaire = "grant_type=client_credentials&scope=" + encoder(r.graph() + "/.default") + "&client_id=" + encoder(r.client())
                + "&client_secret=" + encoder(r.secret());
        HttpResponse<String> reponse = http.send(HttpRequest.newBuilder(URI.create(r.autorite() + "/" + encoder(r.locataire()) + "/oauth2/v2.0/token"))
                .timeout(DELAI).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(formulaire)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (reponse.statusCode() != OK) {
            throw new IllegalStateException("jeton Graph refusé (" + reponse.statusCode() + ")");
        }
        Map<?, ?> j = json.readValue(reponse.body(), Map.class);
        jeton = new Jeton(String.valueOf(j.get("access_token")), maintenant.plusSeconds(((Number) j.get("expires_in")).longValue()));
        return jeton.valeur();
    }

    private static String encoder(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private void refus(Message message, String raison, Exception cause) {
        JOURNAL.atError().addKeyValue("evenement", "courrier").addKeyValue("destinataire", Masquage.masquer(message.to())).setCause(cause)
                .log("Microsoft Graph refuse un courriel : {}", raison);
    }

    @Override
    public Transport transport() {
        return new Transport("graph", r.graph(), r.expediteur());
    }
}
