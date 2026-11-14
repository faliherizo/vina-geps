package ca.vina.nexu.auth.domaine;

import ca.vina.nexu.partage.crypto.Primitives;
import ca.vina.nexu.partage.json.JsonMinimal;

import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * SSO fédéré <strong>Microsoft Entra ID</strong> (OIDC, AO-5338 NF-19/20) — la part pure du flux
 * Authorization Code + PKCE : la configuration d'après l'environnement, la demande (state, PKCE,
 * nonce), l'URL d'autorisation, et les contrôles sur l'{@code id_token} une fois sa signature
 * vérifiée. Trois modes :
 * <ul>
 *   <li><strong>simulé</strong> (sans {@code AZURE_CLIENT_ID}) : le code d'autorisation encode des
 *       revendications de démonstration, et l'on revient aussitôt sur le rappel ;</li>
 *   <li><strong>réel</strong> : redirection vers Entra, échange du code, {@code id_token} vérifié par JWKS ;</li>
 *   <li><strong>démonstration</strong> : le flux réel complet contre l'Entra ID embarqué ; seule l'autorité change.</li>
 * </ul>
 */
public final class Oidc {

    private Oidc() {
    }

    public static final long DUREE_DEMANDE_MS = 10 * 60_000L;

    /** Le chemin normalisé du document de découverte OpenID Connect, sous l'émetteur. */
    public static final String CHEMIN_DECOUVERTE = "/.well-known/openid-configuration";

    public record Configuration(boolean mock, boolean demonstration, String tenantId, String clientId, String clientSecret, String redirectUri,
                                String authority, String authorizeUrl, String tokenUrl, String discoveryUrl) {
    }

    /** Ce que le registre des démonstrations fournit quand l'Entra ID embarqué est branché. */
    public record Demo(String tenantId, String clientId, String clientSecret, String urlServeur, Optional<String> urlApplication) {
    }

    private static String env(Map<String, String> env, String cle, String defaut) {
        String v = env.get(cle);
        return v == null || v.isEmpty() ? defaut : v;
    }

    public static Configuration configuration(Map<String, String> env, Optional<Demo> demo) {
        String clientId = demo.map(Demo::clientId).orElse(env(env, "AZURE_CLIENT_ID", ""));
        String clientSecret = demo.map(Demo::clientSecret).orElse(env(env, "AZURE_CLIENT_SECRET", ""));
        String tenantId = demo.map(Demo::tenantId).orElse(env(env, "AZURE_TENANT_ID", "common"));
        String authority = demo.map(d -> d.urlServeur() + "/mock/entra/" + tenantId).orElse(env(env, "AZURE_AUTHORITY", "https://login.microsoftonline.com/" + tenantId)).replaceAll("/$", "");
        // En démonstration, le retour passe par l'origine de l'application (son mandataire `/api`), apprise au branchement.
        String redirectUri = demo.map(d -> d.urlApplication().orElse(d.urlServeur()) + "/api/auth/sso/entra/callback").orElse(env(env, "AZURE_REDIRECT_URI", "http://localhost:4000/api/auth/sso/entra/callback"));
        return new Configuration(clientId.isEmpty(), demo.isPresent(), tenantId, clientId, clientSecret, redirectUri, authority,
                authority + "/oauth2/v2.0/authorize", authority + "/oauth2/v2.0/token", authority + "/v2.0/.well-known/openid-configuration");
    }

    // ── Demande ─────────────────────────────────────────────────────────────

    public record Demande(String state, String codeVerifier, String nonce, long creeLe) {
        public boolean expiree(long maintenant) {
            return maintenant - creeLe > DUREE_DEMANDE_MS;
        }
    }

    private static String alea(RandomGenerator alea, int octets) {
        byte[] b = new byte[octets];
        alea.nextBytes(b);
        return Primitives.base64Url(b);
    }

    public static Demande nouvelleDemande(RandomGenerator alea, long maintenant) {
        return new Demande(alea(alea, 16), alea(alea, 32), alea(alea, 16), maintenant);
    }

    public static String pkceChallenge(String verifier) {
        return Primitives.base64Url(Primitives.sha256(verifier.getBytes(StandardCharsets.UTF_8)));
    }

    public record Debut(String authUrl, String state) {
    }

    /** Les revendications du mode simulé : un gestionnaire, toujours le même, pour qu'une démonstration soit reproductible. */
    public static Map<String, Object> revendicationsSimulees() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("email", "sso.user@nexuvina.ca");
        claims.put("name", "Utilisateur SSO");
        claims.put("roles", List.of("Nexu.Manager"));
        claims.put("oid", "mock-oid-1");
        return claims;
    }

    private static String encoder(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static Debut debuter(Configuration cfg, Demande d) {
        if (cfg.mock()) {
            String code = "mock." + Primitives.base64Url(JsonMinimal.ecrire(revendicationsSimulees()).getBytes(StandardCharsets.UTF_8));
            return new Debut(cfg.redirectUri() + "?code=" + encoder(code) + "&state=" + d.state(), d.state());
        }
        Map<String, String> p = new LinkedHashMap<>();
        p.put("client_id", cfg.clientId());
        p.put("response_type", "code");
        p.put("redirect_uri", cfg.redirectUri());
        p.put("response_mode", "query");
        p.put("scope", "openid profile email");
        p.put("state", d.state());
        p.put("nonce", d.nonce());
        p.put("code_challenge", pkceChallenge(d.codeVerifier()));
        p.put("code_challenge_method", "S256");
        return new Debut(cfg.authorizeUrl() + "?" + formulaire(p), d.state());
    }

    /** Un corps {@code application/x-www-form-urlencoded}, dans l'ordre des paramètres. */
    public static String formulaire(Map<String, String> p) {
        List<String> parts = new ArrayList<>();
        p.forEach((k, v) -> parts.add(encoder(k) + "=" + encoder(v)));
        return String.join("&", parts);
    }

    /** Le corps de l'échange du code (PKCE + secret client), tel que le point de terminaison {@code token} l'attend. */
    public static String formulaireEchange(String code, Configuration cfg, Demande d) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("grant_type", "authorization_code");
        p.put("code", code);
        p.put("redirect_uri", cfg.redirectUri());
        p.put("client_id", cfg.clientId());
        p.put("client_secret", cfg.clientSecret());
        p.put("code_verifier", d.codeVerifier());
        p.put("scope", "openid profile email");
        return formulaire(p);
    }

    // ── Revendications ──────────────────────────────────────────────────────

    public static boolean estCodeSimule(String code) {
        return code.startsWith("mock.");
    }

    /** Le JSON que porte un code simulé. */
    public static String jsonDuCodeSimule(String code) {
        return new String(Base64.getUrlDecoder().decode(code.replaceFirst("^mock\\.", "")), StandardCharsets.UTF_8);
    }

    public static String courriel(Map<String, Object> claims) {
        Object email = claims.get("email");
        if (!(email instanceof String s) || s.isEmpty()) {
            throw new IllegalArgumentException("SSO claims missing email");
        }
        return s;
    }

    /** La clé publique d'un JWKS qui porte le {@code kid} de l'en-tête du jeton. */
    public static RSAPublicKey clePubliqueDuJwks(Map<String, Object> jwks, String kid) {
        Object cles = jwks.get("keys");
        Map<?, ?> jwk = null;
        if (cles instanceof List<?> l) {
            jwk = l.stream().filter(k -> k instanceof Map<?, ?> m && kid != null && kid.equals(m.get("kid"))).map(k -> (Map<?, ?>) k).findFirst().orElse(null);
        }
        if (jwk == null) {
            throw new IllegalStateException("Entra : clé de signature « " + (kid == null ? "?" : kid) + " » absente du JWKS");
        }
        BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(String.valueOf(jwk.get("n"))));
        BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(String.valueOf(jwk.get("e"))));
        return Primitives.surement("clé RSA du JWKS", () -> (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e)));
    }

    /**
     * Ce que Microsoft prescrit une fois la signature vérifiée : {@code iss} = l'émetteur du document
     * de découverte, {@code nonce} = celui de la demande. Avec l'autorité {@code common}, l'émetteur
     * publié est un gabarit ({@code {tenantid}}) : on vérifie alors la forme, avec le {@code tid} du jeton.
     */
    public static void verifierEmetteurEtNonce(Map<String, Object> claims, String emetteur, String nonce) {
        String attendu = emetteur.contains("{tenantid}") ? emetteur.replace("{tenantid}", String.valueOf(claims.getOrDefault("tid", ""))) : emetteur;
        if (!attendu.equals(claims.get("iss"))) {
            throw new IllegalStateException("Entra : émetteur inattendu");
        }
        if (!nonce.equals(claims.get("nonce"))) {
            throw new IllegalStateException("Entra : nonce inattendu (rejeu ?)");
        }
    }

    /** {@code email} ou, à défaut, {@code preferred_username} ; {@code roles} toujours une liste. */
    public static Map<String, Object> normaliser(Map<String, Object> claims) {
        Map<String, Object> m = new LinkedHashMap<>(claims);
        m.put("email", claims.get("email") == null ? claims.get("preferred_username") : claims.get("email"));
        m.put("roles", claims.get("roles") instanceof List<?> l ? l : List.of());
        return m;
    }

    /** Le mappage par défaut des revendications Entra ({@code roles}) → rôle plateforme. */
    public static final Map<String, String> MAPPAGE_PAR_DEFAUT = Map.of("Nexu.Admin", "admin", "Nexu.Manager", "manager", "Nexu.RH", "rh", "Nexu.Director", "director", "Nexu.Accountant", "accountant", "Nexu.Employee", "employee");
}
