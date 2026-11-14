package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.auth.infrastructure.services.ServiceSso;

import ca.vina.nexu.auth.domaine.Annuaires;
import ca.vina.nexu.auth.domaine.Oidc;
import io.jsonwebtoken.Jwts;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.Base64;
import java.util.Map;

/**
 * L'échangeur par défaut : échange le code (PKCE + secret client) au point de terminaison
 * {@code token}, puis vérifie l'{@code id_token} comme le prescrit Microsoft — signature RS256 par
 * une clé du JWKS publié, {@code aud} = notre client, émetteur et nonce attendus.
 */
public class EchangeurOidcHttp implements ServiceSso.EchangeurOidc, Annuaires.Decouverte {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> CARTE = new TypeReference<>() { };

    private final HttpClient client;
    private final Duration delai;
    private final Clock horloge;

    public EchangeurOidcHttp(HttpClient client, Duration delai, Clock horloge) {
        this.client = client;
        this.delai = delai;
        this.horloge = horloge;
    }

    private HttpResponse<String> envoyer(HttpRequest r) {
        try {
            return client.send(r, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Entra : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Entra : interrompu", e);
        }
    }

    private Map<String, Object> lire(String url) {
        return JSON.readValue(envoyer(HttpRequest.newBuilder(URI.create(url)).timeout(delai).GET().build()).body(), CARTE);
    }

    private static Map<String, Object> json(String corps) {
        try {
            return JSON.readValue(corps, CARTE);
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    @Override
    public Map<String, Object> echanger(String code, Oidc.Configuration cfg, Oidc.Demande demande) {
        HttpResponse<String> r = envoyer(HttpRequest.newBuilder(URI.create(cfg.tokenUrl())).timeout(delai)
                .header("content-type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(Oidc.formulaireEchange(code, cfg, demande))).build());
        Map<String, Object> j = json(r.body());
        if (r.statusCode() >= 300 || !(j.get("id_token") instanceof String idToken)) {
            throw new IllegalStateException("Entra : " + j.getOrDefault("error", r.statusCode()) + " — " + j.getOrDefault("error_description", "échange du code refusé"));
        }
        return Oidc.normaliser(verifierIdToken(idToken, cfg, demande.nonce()));
    }

    /** Les adresses publiées par un fournisseur OIDC ; un document sans elles est refusé. */
    @Override
    public Annuaires.Decouverte.Points points(String emetteur) {
        Map<String, Object> d;
        try {
            d = lire(emetteur + Oidc.CHEMIN_DECOUVERTE);
        } catch (tools.jackson.core.JacksonException e) {
            throw new IllegalStateException("document de découverte illisible", e);
        }
        if (!(d.get("authorization_endpoint") instanceof String autorisation) || !(d.get("token_endpoint") instanceof String jeton)) {
            throw new IllegalStateException("document de découverte sans authorization_endpoint ni token_endpoint");
        }
        return new Annuaires.Decouverte.Points(autorisation, jeton);
    }

    /** L'en-tête d'un JWT, pour son {@code kid}. */
    public static Map<String, Object> enTete(String jwt) {
        return json(new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[0]), StandardCharsets.UTF_8));
    }

    public Map<String, Object> verifierIdToken(String idToken, Oidc.Configuration cfg, String nonce) {
        Map<String, Object> decouverte = lire(cfg.discoveryUrl());
        Map<String, Object> jwks = lire(String.valueOf(decouverte.get("jwks_uri")));
        Object kid = enTete(idToken).get("kid");
        var cle = Oidc.clePubliqueDuJwks(jwks, kid == null ? null : String.valueOf(kid));
        Map<String, Object> claims;
        try {
            claims = new java.util.LinkedHashMap<>(Jwts.parser().clock(() -> Date.from(horloge.instant())).verifyWith(cle).requireAudience(cfg.clientId()).build().parseSignedClaims(idToken).getPayload());
        } catch (io.jsonwebtoken.JwtException e) {
            throw new IllegalStateException("Entra : " + e.getMessage(), e);
        }
        Oidc.verifierEmetteurEtNonce(claims, String.valueOf(decouverte.getOrDefault("issuer", "")), nonce);
        return claims;
    }
}
