package ca.vina.nexu.demonstration.domaine;

import ca.vina.nexu.partage.crypto.Primitives;
import ca.vina.nexu.partage.json.JsonMinimal;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;

/**
 * Microsoft Entra ID de démonstration — un fournisseur OpenID Connect qui parle le protocole
 * publié par Microsoft : découverte, page de choix de compte (PKCE S256 exigé), échange du code
 * contre un {@code id_token} RS256, JWKS. Les revendications sont celles d'Entra ({@code oid},
 * {@code tid}, {@code preferred_username}, {@code roles}, {@code ver: "2.0"}) et les erreurs
 * reprennent ses codes AADSTS. La clé de signature est générée à la construction : rien de ce
 * simulateur ne vaut hors du processus.
 */
public final class EntraDemo {

    private static final long DUREE_CODE_MS = 10 * 60_000L;

    public record Erreur(String erreur, String description) {
    }

    /** Une réponse du point de terminaison des jetons : le code HTTP et le corps (jeton ou erreur). */
    public record Reponse(int statut, Map<String, Object> corps) {
    }

    private record CodeEnAttente(DonneesDemo.CompteEntra compte, String clientId, String redirectUri, String codeChallenge, String nonce, String scope, long creeLe) {
    }

    private final Clock horloge;
    private final RandomGenerator alea;
    private final KeyPair cles;
    private final String kid;
    private final Map<String, CodeEnAttente> codes = new ConcurrentHashMap<>();

    public EntraDemo(Clock horloge, RandomGenerator alea) {
        this.horloge = horloge;
        this.alea = alea;
        this.cles = Primitives.rsa2048();
        this.kid = Primitives.base64Url(Primitives.sha256(cles.getPublic().getEncoded())).substring(0, 20);
    }

    /** Un grand entier non signé en base64url, sans l'octet de signe que {@code toByteArray} ajoute. */
    private static String entier(BigInteger n) {
        byte[] b = n.toByteArray();
        if (b.length > 1 && b[0] == 0) {
            byte[] sans = new byte[b.length - 1];
            System.arraycopy(b, 1, sans, 0, sans.length);
            b = sans;
        }
        return Primitives.base64Url(b);
    }

    /** Le JWKS publié : la clé publique au format JWK, avec l'identifiant que porte l'en-tête du jeton. */
    public Map<String, Object> jwks() {
        RSAPublicKey pub = (RSAPublicKey) cles.getPublic();
        Map<String, Object> cle = new LinkedHashMap<>();
        cle.put("kty", "RSA");
        cle.put("use", "sig");
        cle.put("kid", kid);
        cle.put("n", entier(pub.getModulus()));
        cle.put("e", entier(pub.getPublicExponent()));
        return Map.of("keys", List.of(cle));
    }

    public static String emetteur(String base, String tenant) {
        return base + "/mock/entra/" + tenant + "/v2.0";
    }

    public static Map<String, Object> documentDecouverte(String base, String tenant) {
        String racine = base + "/mock/entra/" + tenant;
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("issuer", emetteur(base, tenant));
        d.put("authorization_endpoint", racine + "/oauth2/v2.0/authorize");
        d.put("token_endpoint", racine + "/oauth2/v2.0/token");
        d.put("jwks_uri", racine + "/discovery/v2.0/keys");
        d.put("end_session_endpoint", racine + "/oauth2/v2.0/logout");
        d.put("token_endpoint_auth_methods_supported", List.of("client_secret_post"));
        d.put("response_types_supported", List.of("code"));
        d.put("response_modes_supported", List.of("query"));
        d.put("subject_types_supported", List.of("pairwise"));
        d.put("id_token_signing_alg_values_supported", List.of("RS256"));
        d.put("code_challenge_methods_supported", List.of("S256"));
        d.put("scopes_supported", List.of("openid", "profile", "email", "offline_access"));
        d.put("claims_supported", List.of("sub", "iss", "aud", "exp", "iat", "nbf", "name", "preferred_username", "email", "oid", "tid", "roles", "nonce", "ver"));
        d.put("tenant_region_scope", "NA");
        return d;
    }

    private static String texte(Map<String, ?> d, String cle) {
        Object v = d.get(cle);
        return v == null ? null : String.valueOf(v);
    }

    /** Ce qu'Entra vérifie avant d'afficher la page de connexion : le client, puis la forme de la demande. */
    public static Erreur validerDemande(Map<String, ?> d) {
        String clientId = texte(d, "client_id");
        if (!DonneesDemo.ENTRA_CLIENT_ID.equals(clientId)) {
            return new Erreur("unauthorized_client", "AADSTS700016: Application with identifier '" + (clientId == null ? "" : clientId) + "' was not found in the directory '" + DonneesDemo.ENTRA_TENANT + "'.");
        }
        String redirect = texte(d, "redirect_uri");
        if (redirect == null || redirect.isEmpty()) {
            return new Erreur("invalid_request", "AADSTS90014: The request body must contain the following parameter: 'redirect_uri'.");
        }
        if (!"code".equals(texte(d, "response_type"))) {
            return new Erreur("unsupported_response_type", "AADSTS700054: response_type 'code' is the only one supported by this demonstration.");
        }
        String scope = texte(d, "scope");
        if (scope == null || !List.of(scope.split(" ")).contains("openid")) {
            return new Erreur("invalid_request", "AADSTS70011: The provided value for the input parameter 'scope' must include 'openid'.");
        }
        String challenge = texte(d, "code_challenge");
        if (challenge == null || challenge.isEmpty() || !"S256".equals(texte(d, "code_challenge_method"))) {
            return new Erreur("invalid_request", "AADSTS501481: The demonstration requires PKCE (code_challenge, code_challenge_method=S256).");
        }
        return null;
    }

    private static String encoder(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** L'URL de retour avec ses paramètres ajoutés, comme {@code URLSearchParams.set} le ferait. */
    public static String avecParametres(String redirectUri, Map<String, String> parametres) {
        StringBuilder u = new StringBuilder(redirectUri);
        u.append(redirectUri.contains("?") ? "&" : "?");
        boolean premier = true;
        for (Map.Entry<String, String> e : parametres.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            if (!premier) {
                u.append('&');
            }
            premier = false;
            u.append(encoder(e.getKey())).append('=').append(encoder(e.getValue()));
        }
        return u.toString();
    }

    public static String redirigerAvecErreur(String redirectUri, String state, String erreur, String description) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("error", erreur);
        p.put("error_description", description);
        p.put("state", state);
        return avecParametres(redirectUri, p);
    }

    /** Émet un code pour un compte : c'est ce que fait Entra une fois l'utilisateur authentifié. */
    public String emettreCode(Map<String, ?> d, DonneesDemo.CompteEntra compte) {
        long maintenant = horloge.millis();
        codes.entrySet().removeIf(e -> maintenant - e.getValue().creeLe() > DUREE_CODE_MS);
        byte[] b = new byte[24];
        alea.nextBytes(b);
        String code = "0." + Primitives.base64Url(b);
        String scope = texte(d, "scope");
        codes.put(code, new CodeEnAttente(compte, texte(d, "client_id"), texte(d, "redirect_uri"), texte(d, "code_challenge"), texte(d, "nonce"), scope == null ? "openid" : scope, maintenant));
        return code;
    }

    /** L'URL de retour vers l'application avec le code — et l'état si l'application en a donné un. */
    public String retourAvecCode(Map<String, ?> d, DonneesDemo.CompteEntra compte) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("code", emettreCode(d, compte));
        p.put("state", texte(d, "state"));
        return avecParametres(texte(d, "redirect_uri"), p);
    }

    private static Reponse erreur(int statut, String error, int code, String texte) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("error", error);
        corps.put("error_description", "AADSTS" + code + ": " + texte);
        corps.put("error_codes", List.of(code));
        return new Reponse(statut, corps);
    }

    /**
     * L'échange du code : chaque contrôle est celui du vrai point de terminaison, dans le même
     * ordre — client, secret, type d'octroi, code, PKCE, {@code redirect_uri} — et un code ne
     * s'échange qu'une fois.
     */
    public Reponse echangerCode(Map<String, ?> d, String base, String tenant) {
        String clientId = texte(d, "client_id");
        if (!DonneesDemo.ENTRA_CLIENT_ID.equals(clientId)) {
            return erreur(400, "unauthorized_client", 700016, "Application with identifier '" + (clientId == null ? "" : clientId) + "' was not found in the directory.");
        }
        if (!DonneesDemo.ENTRA_CLIENT_SECRET.equals(texte(d, "client_secret"))) {
            return erreur(401, "invalid_client", 7000215, "Invalid client secret provided.");
        }
        String grant = texte(d, "grant_type");
        if (!"authorization_code".equals(grant)) {
            return erreur(400, "unsupported_grant_type", 70003, "The app requested an unsupported grant type '" + (grant == null ? "" : grant) + "'.");
        }
        long maintenant = horloge.millis();
        String code = texte(d, "code");
        CodeEnAttente attente = code == null ? null : codes.get(code);
        if (attente == null || maintenant - attente.creeLe() > DUREE_CODE_MS) {
            return erreur(400, "invalid_grant", 70000, "The provided authorization code is invalid, expired or already redeemed.");
        }
        codes.remove(code);
        String verifier = texte(d, "code_verifier");
        if (!sha256Url(verifier == null ? "" : verifier).equals(attente.codeChallenge())) {
            return erreur(400, "invalid_grant", 501481, "The Code_Verifier does not match the code_challenge supplied in the authorization request.");
        }
        if (!attente.redirectUri().equals(texte(d, "redirect_uri"))) {
            return erreur(400, "invalid_grant", 50011, "The redirect URI specified in the request does not match the redirect URI of the authorization request.");
        }
        long iat = maintenant / 1000;
        DonneesDemo.CompteEntra compte = attente.compte();
        Map<String, Object> revendications = new LinkedHashMap<>();
        revendications.put("aud", attente.clientId());
        revendications.put("iss", emetteur(base, tenant));
        revendications.put("iat", iat);
        revendications.put("nbf", iat);
        revendications.put("exp", iat + 3599);
        revendications.put("name", compte.nom());
        revendications.put("oid", compte.oid());
        revendications.put("preferred_username", compte.courriel());
        revendications.put("email", compte.courriel());
        revendications.put("roles", compte.roles());
        revendications.put("sub", sha256Url(attente.clientId() + ":" + compte.oid()));
        revendications.put("tid", DonneesDemo.ENTRA_TENANT);
        revendications.put("ver", "2.0");
        if (attente.nonce() != null) {
            revendications.put("nonce", attente.nonce());
        }
        byte[] acces = new byte[16];
        alea.nextBytes(acces);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("token_type", "Bearer");
        r.put("scope", attente.scope());
        r.put("expires_in", 3599);
        r.put("ext_expires_in", 3599);
        r.put("access_token", "at." + Primitives.base64Url(acces));
        r.put("id_token", signer(revendications));
        return new Reponse(200, r);
    }

    static String sha256Url(String s) {
        return Primitives.base64Url(Primitives.sha256(s.getBytes(StandardCharsets.UTF_8)));
    }

    /** Un JWT RS256 signé à la main : en-tête, charge, signature — le format de la RFC 7519, sans bibliothèque. */
    String signer(Map<String, Object> charge) {
        Map<String, Object> enTete = new LinkedHashMap<>();
        enTete.put("alg", "RS256");
        enTete.put("typ", "JWT");
        enTete.put("kid", kid);
        String donnees = Primitives.base64Url(JsonMinimal.ecrire(enTete).getBytes(StandardCharsets.UTF_8)) + "." + Primitives.base64Url(JsonMinimal.ecrire(charge).getBytes(StandardCharsets.UTF_8));
        return donnees + "." + Primitives.base64Url(Primitives.signerRs256(cles.getPrivate(), donnees.getBytes(StandardCharsets.UTF_8)));
    }

    public String pageChoix(String tenant, Map<String, ?> d) {
        StringBuilder cache = new StringBuilder();
        for (String k : List.of("client_id", "response_type", "redirect_uri", "scope", "state", "nonce", "code_challenge", "code_challenge_method")) {
            String v = texte(d, k);
            cache.append("<input type=\"hidden\" name=\"").append(k).append("\" value=\"").append(PageHtml.echapper(v == null ? "" : v)).append("\">");
        }
        String refus = redirigerAvecErreur(texte(d, "redirect_uri"), texte(d, "state"), "access_denied", "AADSTS65004: The user declined to consent to access the app.");
        return PageHtml.page("Choisir un compte — Microsoft Entra ID (démonstration)", "\n    <h1>Choisir un compte</h1>\n"
                + "    <p>Fournisseur d'identité <strong>de démonstration</strong> : aucun mot de passe, les comptes sont fictifs. Le locataire est <code>" + PageHtml.echapper(tenant) + "</code>.</p>\n"
                + "    <form method=\"post\" action=\"/mock/entra/" + PageHtml.echapper(tenant) + "/login\">" + cache + "<ul class=\"comptes\">" + PageHtml.boutonsComptes("entra-compte") + "</ul></form>\n"
                + "    <p><a href=\"" + PageHtml.echapper(refus) + "\" data-testid=\"entra-refuser\">Annuler et revenir à l'application</a></p>");
    }

    public static String pageErreur(Erreur e) {
        return PageHtml.page("Erreur — Entra ID (démonstration)", "<h1>Demande refusée</h1><p><code>" + PageHtml.echapper(e.erreur()) + "</code> — " + PageHtml.echapper(e.description()) + "</p>");
    }

    public List<String> codesEnAttente() {
        return new ArrayList<>(codes.keySet());
    }

    public void vider() {
        codes.clear();
    }
}
