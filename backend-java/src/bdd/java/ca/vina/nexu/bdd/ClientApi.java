package ca.vina.nexu.bdd;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Le client HTTP des scénarios : un jeton Bearer et l'organisme en en-tête, jamais de témoin de
 * session — mêler les deux déclenche, à juste titre, la garde CSRF.
 */
final class ClientApi {

    static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration DELAI = Duration.ofSeconds(30);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(DELAI).build();

    /** Qui appelle : un jeton (vide pour un visiteur) et l'organisme visé (vide pour le superadmin). */
    record Session(String jeton, String tenantId) {
        static final Session ANONYME = new Session("", "");
    }

    /** Une réponse : son statut, son texte brut, son JSON quand c'en est (sinon {@code null}), et sa durée. */
    record Reponse(int statut, String texte, JsonNode json, long dureeMs) {
    }

    private ClientApi() {
    }

    static Reponse appeler(Session s, String methode, String chemin, JsonNode corps) {
        HttpRequest.Builder r = HttpRequest.newBuilder(URI.create(ServeurEssai.base() + chemin)).timeout(DELAI)
                .header("Accept", "application/json")
                .method(methode, corps == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(corps)));
        if (corps != null) {
            r.header("Content-Type", "application/json");
        }
        if (!s.jeton().isEmpty()) {
            r.header("Authorization", "Bearer " + s.jeton());
        }
        if (!s.tenantId().isEmpty()) {
            r.header("X-Tenant-Id", s.tenantId());
        }
        try {
            long debut = System.nanoTime();
            HttpResponse<String> h = HTTP.send(r.build(), HttpResponse.BodyHandlers.ofString());
            long duree = (System.nanoTime() - debut) / 1_000_000;
            return new Reponse(h.statusCode(), h.body(), lireJson(h.body()), duree);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("appel interrompu : " + methode + " " + chemin, e);
        }
    }

    /** Une réponse CSV ou HTML n'est pas du JSON : l'attendu « texte » la lit alors telle quelle. */
    private static JsonNode lireJson(String texte) {
        try {
            return texte.isBlank() ? null : JSON.readTree(texte);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Se connecter : le jeton, ou une erreur qui dit qui et pourquoi. */
    static Session connecter(String identifiant, String motDePasse, String tenantId) {
        var corps = JSON.createObjectNode().put("username", identifiant).put("password", motDePasse);
        if (!tenantId.isEmpty()) {
            corps.put("tenantId", tenantId);
        }
        Reponse r = appeler(Session.ANONYME, "POST", "/api/auth/login", corps);
        JsonNode jeton = r.json() == null ? null : r.json().get("token");
        if (jeton == null) {
            throw new IllegalStateException("connexion de " + identifiant + " refusée : " + r.statut() + " " + r.texte());
        }
        return new Session(jeton.asString(), tenantId);
    }
}
