package ca.vina.nexu.auth.domaine;

import ca.vina.nexu.partage.crypto.Primitives;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OIDC Entra ID — configuration, demande PKCE, revendications")
class OidcTest {

    /** Le mode simulé : demandé, et en essai. */
    private static final Map<String, String> SIMULE = Map.of(Oidc.VARIABLE_SIMULATION, "1", "NEXU_ESSAI", "1");

    @Test
    @DisplayName("la configuration suit l'environnement : simulé sans client, autorité et rappel par défaut, surcharges, puis démonstration")
    void configuration() {
        // Rien de déclaré : désactivé — jamais simulé par défaut (balayage des accès du 22 novembre 2026).
        Oidc.Configuration rien = Oidc.configuration(Map.of(), Optional.empty());
        assertThat(List.of(rien.mock(), rien.actif())).containsExactly(false, false);
        assertThat(Oidc.configuration(Map.of(Oidc.VARIABLE_SIMULATION, "1"), Optional.empty()).actif()).isFalse();
        assertThat(Oidc.configuration(Map.of("NEXU_ESSAI", "1"), Optional.empty()).actif()).isFalse();
        assertThat(Oidc.configuration(Map.of(Oidc.VARIABLE_SIMULATION, "0", "NEXU_ESSAI", "true"), Optional.empty()).actif()).isFalse();
        Oidc.Configuration simule = Oidc.configuration(SIMULE, Optional.empty());
        assertThat(simule.mock()).isTrue();
        assertThat(simule.actif()).isTrue();
        assertThat(simule.demonstration()).isFalse();
        assertThat(simule.tenantId()).isEqualTo("common");
        assertThat(simule.authority()).isEqualTo("https://login.microsoftonline.com/common");
        assertThat(simule.authorizeUrl()).isEqualTo("https://login.microsoftonline.com/common/oauth2/v2.0/authorize");
        assertThat(simule.tokenUrl()).isEqualTo("https://login.microsoftonline.com/common/oauth2/v2.0/token");
        assertThat(simule.discoveryUrl()).isEqualTo("https://login.microsoftonline.com/common/v2.0/.well-known/openid-configuration");
        assertThat(simule.redirectUri()).isEqualTo("http://localhost:4000/api/auth/sso/entra/callback");

        Oidc.Configuration reel = Oidc.configuration(Map.of("AZURE_CLIENT_ID", "cid", "AZURE_CLIENT_SECRET", "s", "AZURE_TENANT_ID", "tid", "AZURE_AUTHORITY", "https://login.microsoftonline.us/tid/", "AZURE_REDIRECT_URI", "https://app/api/auth/sso/entra/callback"), Optional.empty());
        assertThat(reel.mock()).isFalse();
        assertThat(reel.clientId()).isEqualTo("cid");
        assertThat(reel.authority()).isEqualTo("https://login.microsoftonline.us/tid");
        assertThat(reel.redirectUri()).isEqualTo("https://app/api/auth/sso/entra/callback");

        // En démonstration, les identifiants du tiers embarqué l'emportent, et le retour passe par l'application si elle est connue.
        Oidc.Demo demo = new Oidc.Demo("t-demo", "c-demo", "s-demo", "http://localhost:4000", Optional.of("http://localhost:5173/admin"));
        Oidc.Configuration d = Oidc.configuration(Map.of("AZURE_CLIENT_ID", "cid"), Optional.of(demo));
        assertThat(d.mock()).isFalse();
        assertThat(d.demonstration()).isTrue();
        assertThat(d.clientId()).isEqualTo("c-demo");
        assertThat(d.authority()).isEqualTo("http://localhost:4000/mock/entra/t-demo");
        assertThat(d.redirectUri()).isEqualTo("http://localhost:5173/admin/api/auth/sso/entra/callback");
        Oidc.Configuration sansApp = Oidc.configuration(Map.of(), Optional.of(new Oidc.Demo("t", "c", "s", "http://localhost:4000", Optional.empty())));
        assertThat(sansApp.redirectUri()).isEqualTo("http://localhost:4000/api/auth/sso/entra/callback");
        assertThat(sansApp.authorizeUrl()).isEqualTo("http://localhost:4000/mock/entra/t/oauth2/v2.0/authorize");

        // Déployée par sous-domaine : la page de connexion et le retour passent par l'hôte de la requête ; l'échange du
        // code reste interne. Sans cela, le navigateur était envoyé vers http://localhost:4000 (constaté le 23 nov. 2026).
        Oidc.Configuration publique = Oidc.configuration(Map.of(), Optional.of(new Oidc.Demo("t", "c", "s", "http://localhost:4000",
                Optional.of("http://localhost:5173"), Optional.of("https://clincom.geps.exemple"))));
        assertThat(publique.authorizeUrl()).isEqualTo("https://clincom.geps.exemple/mock/entra/t/oauth2/v2.0/authorize");
        assertThat(publique.redirectUri()).isEqualTo("https://clincom.geps.exemple/api/auth/sso/entra/callback");
        assertThat(publique.tokenUrl()).isEqualTo("http://localhost:4000/mock/entra/t/oauth2/v2.0/token");
        assertThat(publique.authority()).isEqualTo("http://localhost:4000/mock/entra/t");
    }

    @Test
    @DisplayName("la demande porte state, vérificateur PKCE et nonce ; l'URL d'autorisation les transmet, le mode simulé revient aussitôt avec un code lisible")
    void demande() {
        Oidc.Demande d = Oidc.nouvelleDemande(new Random(1), 1_000);
        assertThat(d.state()).hasSize(22);
        assertThat(d.codeVerifier()).hasSize(43);
        assertThat(d.nonce()).hasSize(22);
        assertThat(d.expiree(1_000 + Oidc.DUREE_DEMANDE_MS)).isFalse();
        assertThat(d.expiree(1_001 + Oidc.DUREE_DEMANDE_MS)).isTrue();
        // RFC 7636, annexe B.
        assertThat(Oidc.pkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")).isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");

        Oidc.Configuration reel = Oidc.configuration(Map.of("AZURE_CLIENT_ID", "cid", "AZURE_REDIRECT_URI", "https://app/cb?x=1"), Optional.empty());
        Oidc.Debut debut = Oidc.debuter(reel, d);
        assertThat(debut.state()).isEqualTo(d.state());
        assertThat(debut.authUrl()).isEqualTo("https://login.microsoftonline.com/common/oauth2/v2.0/authorize?client_id=cid&response_type=code&redirect_uri=https%3A%2F%2Fapp%2Fcb%3Fx%3D1"
                + "&response_mode=query&scope=openid+profile+email&state=" + d.state() + "&nonce=" + d.nonce() + "&code_challenge=" + Oidc.pkceChallenge(d.codeVerifier()) + "&code_challenge_method=S256");

        Oidc.Debut simule = Oidc.debuter(Oidc.configuration(SIMULE, Optional.empty()), d);
        assertThat(simule.authUrl()).startsWith("http://localhost:4000/api/auth/sso/entra/callback?code=mock.").endsWith("&state=" + d.state());
        String code = java.net.URLDecoder.decode(simule.authUrl().substring(simule.authUrl().indexOf("code=") + 5, simule.authUrl().indexOf("&state=")), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(Oidc.estCodeSimule(code)).isTrue();
        assertThat(Oidc.jsonDuCodeSimule(code)).isEqualTo("{\"email\":\"sso.user@nexuvina.ca\",\"name\":\"Utilisateur SSO\",\"roles\":[\"Nexu.Manager\"],\"oid\":\"mock-oid-1\"}");
        assertThat(Oidc.estCodeSimule("0.abc")).isFalse();

        assertThat(Oidc.formulaireEchange("c0de", reel, d)).isEqualTo("grant_type=authorization_code&code=c0de&redirect_uri=https%3A%2F%2Fapp%2Fcb%3Fx%3D1&client_id=cid&client_secret=&code_verifier=" + d.codeVerifier() + "&scope=openid+profile+email");
    }

    @Test
    @DisplayName("les revendications : courriel exigé, clé du JWKS par kid, émetteur (gabarit compris) et nonce, normalisation")
    void revendications() {
        assertThatThrownBy(() -> Oidc.courriel(Map.of("name", "x"))).isInstanceOf(IllegalArgumentException.class).hasMessage("SSO claims missing email");
        assertThatThrownBy(() -> Oidc.courriel(Map.of("email", ""))).hasMessage("SSO claims missing email");
        assertThat(Oidc.courriel(Map.of("email", "a@b.ca"))).isEqualTo("a@b.ca");

        KeyPair paire = Primitives.rsa2048();
        RSAPublicKey publique = (RSAPublicKey) paire.getPublic();
        Map<String, Object> jwk = Map.of("kid", "k1", "kty", "RSA", "n", Primitives.base64Url(sansSigne(publique.getModulus().toByteArray())), "e", Primitives.base64Url(publique.getPublicExponent().toByteArray()));
        Map<String, Object> jwks = Map.of("keys", List.of(Map.of("kid", "autre"), jwk));
        assertThat(Oidc.clePubliqueDuJwks(jwks, "k1").getModulus()).isEqualTo(publique.getModulus());
        assertThatThrownBy(() -> Oidc.clePubliqueDuJwks(jwks, "k9")).hasMessage("Entra : clé de signature « k9 » absente du JWKS");
        assertThatThrownBy(() -> Oidc.clePubliqueDuJwks(Map.of(), null)).hasMessage("Entra : clé de signature « ? » absente du JWKS");

        Map<String, Object> claims = new HashMap<>(Map.of("iss", "https://login.microsoftonline.com/tid/v2.0", "tid", "tid", "nonce", "n1"));
        Oidc.verifierEmetteurEtNonce(claims, "https://login.microsoftonline.com/tid/v2.0", "n1");
        Oidc.verifierEmetteurEtNonce(claims, "https://login.microsoftonline.com/{tenantid}/v2.0", "n1");
        assertThatThrownBy(() -> Oidc.verifierEmetteurEtNonce(claims, "https://login.microsoftonline.com/autre/v2.0", "n1")).hasMessage("Entra : émetteur inattendu");
        assertThatThrownBy(() -> Oidc.verifierEmetteurEtNonce(claims, "https://login.microsoftonline.com/tid/v2.0", "n2")).hasMessage("Entra : nonce inattendu (rejeu ?)");
        claims.remove("tid");
        assertThatThrownBy(() -> Oidc.verifierEmetteurEtNonce(claims, "https://login.microsoftonline.com/{tenantid}/v2.0", "n1")).hasMessage("Entra : émetteur inattendu");

        // Discovery § 4.3 : pour un fournisseur générique, l'émetteur publié est l'adresse de l'annuaire ; Entra (/v2.0) n'y est pas soumis.
        Oidc.Configuration generique = new Oidc.Configuration(false, false, "", "c", "s", "r", "https://kc/realms/r", "a", "t", "https://kc/realms/r" + Oidc.CHEMIN_DECOUVERTE);
        Oidc.verifierEmetteurPublie(generique, "https://kc/realms/r");
        assertThatThrownBy(() -> Oidc.verifierEmetteurPublie(generique, "https://pirate/realms/r")).isInstanceOf(IllegalStateException.class)
                .hasMessage("OIDC : l'émetteur publié (https://pirate/realms/r) n'est pas celui de l'annuaire (https://kc/realms/r)");
        Oidc.verifierEmetteurPublie(Oidc.configuration(Map.of("AZURE_CLIENT_ID", "cid", "AZURE_TENANT_ID", "tid"), Optional.empty()), "https://login.microsoftonline.com/tid/v2.0");
        // exp et sub exigés ; azp contrôlé quand l'audience en nomme plusieurs.
        Oidc.verifierRevendicationsExigees(Map.of("exp", 1, "sub", "s", "aud", "c"), "c");
        Oidc.verifierRevendicationsExigees(Map.of("exp", 1, "sub", "s", "aud", List.of("c", "autre"), "azp", "c"), "c");
        Oidc.verifierRevendicationsExigees(Map.of("exp", 1, "sub", "s", "aud", List.of("c")), "c");
        assertThatThrownBy(() -> Oidc.verifierRevendicationsExigees(Map.of("sub", "s"), "c")).hasMessage("OIDC : jeton sans échéance (exp)");
        assertThatThrownBy(() -> Oidc.verifierRevendicationsExigees(Map.of("exp", 1), "c")).hasMessage("OIDC : jeton sans sujet (sub)");
        assertThatThrownBy(() -> Oidc.verifierRevendicationsExigees(Map.of("exp", 1, "sub", " "), "c")).hasMessage("OIDC : jeton sans sujet (sub)");
        assertThatThrownBy(() -> Oidc.verifierRevendicationsExigees(Map.of("exp", 1, "sub", "s", "aud", List.of("c", "autre"), "azp", "autre"), "c"))
                .isInstanceOf(Oidc.AuthentificationRefuseeException.class).hasMessage("OIDC : partie autorisée (azp) inattendue");
        assertThat(new Oidc.DesactiveException()).hasMessage("SSO Entra ID non configuré");

        Map<String, Object> n = Oidc.normaliser(Map.of("preferred_username", "p@x.ca", "roles", "pas-une-liste"));
        assertThat(n).containsEntry("email", "p@x.ca").containsEntry("roles", List.of());
        Map<String, Object> n2 = Oidc.normaliser(Map.of("email", "e@x.ca", "preferred_username", "p@x.ca", "roles", List.of("Nexu.Admin")));
        assertThat(n2).containsEntry("email", "e@x.ca").containsEntry("roles", List.of("Nexu.Admin"));
        assertThat(Oidc.revendicationsSimulees()).containsEntry("roles", List.of("Nexu.Manager"));
        assertThat(Oidc.MAPPAGE_PAR_DEFAUT).containsEntry("Nexu.RH", "rh").hasSize(6);
    }

    private static byte[] sansSigne(byte[] b) {
        return b[0] == 0 ? java.util.Arrays.copyOfRange(b, 1, b.length) : b;
    }
}
