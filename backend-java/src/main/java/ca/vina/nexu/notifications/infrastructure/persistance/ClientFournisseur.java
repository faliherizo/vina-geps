package ca.vina.nexu.notifications.infrastructure.persistance;

import ca.vina.nexu.partage.crypto.Primitives;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Le socle commun des adaptateurs de fournisseurs (SMS, appels, poussée) : une requête bornée dans
 * le temps, un corps JSON ou formulaire, une réponse lue en JSON. Les adaptateurs ne savent rien
 * de l'environnement ; ils reçoivent leur configuration.
 */
public final class ClientFournisseur {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> CARTE = new TypeReference<>() { };

    public record Reponse(int statut, String corps) {
        public Map<String, Object> json() {
            try {
                return corps == null || corps.isBlank() ? Map.of() : JSON.readValue(corps, CARTE);
            } catch (RuntimeException e) {
                return Map.of();
            }
        }

        public boolean ok() {
            return statut >= 200 && statut < 300;
        }

        public String extrait() {
            String c = corps == null ? "" : corps;
            return c.substring(0, Math.min(200, c.length()));
        }
    }

    private final HttpClient http;
    private final long delaiMs;

    public ClientFournisseur(HttpClient http, long delaiMs) {
        this.http = http;
        this.delaiMs = delaiMs;
    }

    public static String basique(String utilisateur, String secret) {
        return "Basic " + Base64.getEncoder().encodeToString((utilisateur + ":" + secret).getBytes(StandardCharsets.UTF_8));
    }

    public static String formulaire(Map<String, String> champs) {
        return champs.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
    }

    public static String json(Object valeur) {
        return JSON.writeValueAsString(valeur);
    }

    public static String encoder(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8);
    }

    /** {@code corps} nul : GET ; sinon POST avec le type indiqué. */
    public Reponse appeler(String url, Map<String, String> enTetes, String typeCorps, String corps, String nomFournisseur) {
        return appeler(url, enTetes, typeCorps, corps == null ? null : corps.getBytes(StandardCharsets.UTF_8), nomFournisseur, Function.identity());
    }

    public Reponse appeler(String url, Map<String, String> enTetes, String typeCorps, byte[] corps, String nomFournisseur, Function<HttpRequest.Builder, HttpRequest.Builder> ajustement) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(delaiMs));
        enTetes.forEach(b::header);
        if (corps == null) {
            b.GET();
        } else {
            if (!enTetes.containsKey("content-type")) {
                b.header("content-type", typeCorps);
            }
            b.POST(HttpRequest.BodyPublishers.ofByteArray(corps));
        }
        HttpRequest requete = ajustement.apply(b).build();
        return Primitives.surement(nomFournisseur + " injoignable", () -> {
            HttpResponse<String> r = http.send(requete, HttpResponse.BodyHandlers.ofString());
            return new Reponse(r.statusCode(), r.body());
        });
    }

    @SuppressWarnings("unchecked")
    public static Object chemin(Map<String, Object> carte, String... cles) {
        Object courant = carte;
        for (String cle : cles) {
            if (courant instanceof Map<?, ?> m) {
                courant = m.get(cle);
            } else if (courant instanceof List<?> l && cle.matches("\\d+")) {
                int i = Integer.parseInt(cle);
                courant = i < l.size() ? l.get(i) : null;
            } else {
                return null;
            }
        }
        return courant;
    }
}
