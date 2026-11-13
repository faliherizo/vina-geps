package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.SourceIndisponibleException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ce que les adaptateurs HTTP des intégrations partagent : l'appel réseau (injectable — la suite de contrat branche le client
 * sur un faux serveur), les en-têtes, et la lecture d'une réponse. Le jeton, s'il y en a un, voyage en {@code Bearer}.
 */
public final class ClientHttpIntegration {

    /** Au-delà, un destinataire est réputé injoignable : l'ordonnanceur retentera, l'écran ne reste pas suspendu. */
    public static final Duration DELAI = Duration.ofMillis(15_000);
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final int SUCCES_MIN = 200;
    private static final int SUCCES_MAX = 300;

    private ClientHttpIntegration() {
    }

    /** Une réponse : statut et corps brut (vide s'il n'y en a pas). */
    public record Reponse(int statut, String corps) { }

    /** L'appel réseau ; une panne de transport lève {@link IOException}. */
    @FunctionalInterface
    public interface Appel {
        Reponse appeler(HttpRequest requete) throws IOException, InterruptedException;
    }

    /** L'adresse de base d'un connecteur, le jeton lu au moment de l'appel, et le délai. */
    public record ConfigHttp(String url, Optional<String> jeton, Duration delai) {
        public ConfigHttp(String url, Optional<String> jeton) {
            this(url, jeton, DELAI);
        }
    }

    /** L'appel de production : le client HTTP du JDK. */
    public static Appel reseau(HttpClient client) {
        return r -> {
            HttpResponse<String> x = client.send(r, HttpResponse.BodyHandlers.ofString());
            return new Reponse(x.statusCode(), x.body());
        };
    }

    /** Une requête vers {@code {url}{chemin}}, sans barre finale doublée, avec les en-têtes convenus. */
    static HttpRequest.Builder requete(ConfigHttp c, String chemin) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(c.url().replaceAll("/+$", "") + chemin)).timeout(c.delai())
                .header("Content-Type", "application/json").header("Accept", "application/json");
        c.jeton().ifPresent(j -> b.header("Authorization", "Bearer " + j));
        return b;
    }

    static String json(Object v) {
        return JSON.writeValueAsString(v);
    }

    /** Une interruption n'est pas une réponse : le fil garde son drapeau, et l'appel échoue comme une panne de transport. */
    static Reponse appeler(Appel appel, HttpRequest r) throws IOException {
        try {
            return appel.appeler(r);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("appel interrompu", e);
        }
    }

    static String motif(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    static Object lu(String corps) {
        try {
            return corps == null || corps.isBlank() ? null : JSON.readValue(corps, Object.class);
        } catch (JacksonException e) {
            return null;
        }
    }

    /** Le corps d'une réponse, s'il est un objet JSON ; sinon vide (une réponse 204 n'a pas de corps). */
    @SuppressWarnings("unchecked")
    static Map<String, Object> carte(String corps) {
        return lu(corps) instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    /** Une source rend une liste JSON ; tout autre retour la rend indisponible. */
    static List<?> lireListe(Appel appel, HttpRequest r) {
        Reponse x;
        try {
            x = appeler(appel, r);
        } catch (IOException e) {
            throw new SourceIndisponibleException("source injoignable : " + motif(e));
        }
        if (x.statut() < SUCCES_MIN || x.statut() >= SUCCES_MAX) {
            throw new SourceIndisponibleException("source en erreur (HTTP " + x.statut() + ")");
        }
        if (!(lu(x.corps()) instanceof List<?> l)) {
            throw new SourceIndisponibleException("la source n'a pas rendu une liste");
        }
        return l;
    }
}
