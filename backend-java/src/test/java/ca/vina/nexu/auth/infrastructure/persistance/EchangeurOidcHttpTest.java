package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.auth.domaine.Oidc;
import ca.vina.nexu.notifications.infrastructure.FournisseurFactice;
import ca.vina.nexu.partage.crypto.Primitives;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La vérification de l'{@code id_token} d'un fournisseur OIDC générique (Keycloak) contre un vrai serveur HTTP local :
 * signature par le JWKS publié, émetteur (celui de l'annuaire et celui du document de découverte), audience,
 * {@code azp}, échéance (avec la tolérance d'horloge), sujet, nonce. Chaque refus est nommé.
 */
@DisplayName("EchangeurOidcHttp — id_token : signature, iss, aud, azp, exp (tolérance), sub, nonce")
class EchangeurOidcHttpTest {

    private static final Instant MAINTENANT = Instant.parse("2031-01-15T10:00:00Z");
    private static final Clock HORLOGE = Clock.fixed(MAINTENANT, ZoneOffset.UTC);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CLIENT = "vina-geps";
    private static final String NONCE = "nonce-1";

    private final KeyPair cle = Primitives.rsa2048();
    private FournisseurFactice idp;
    private String emetteurPublie;
    private Oidc.Configuration cfg;
    private final EchangeurOidcHttp echangeur = new EchangeurOidcHttp(HttpClient.newHttpClient(), Duration.ofSeconds(5), HORLOGE);

    @BeforeEach
    void preparer() throws Exception {
        idp = new FournisseurFactice();
        String emetteur = idp.url() + "/realms/r";
        emetteurPublie = emetteur;
        RSAPublicKey pub = (RSAPublicKey) cle.getPublic();
        Map<String, Object> jwk = Map.of("kid", "k1", "kty", "RSA", "n", Primitives.base64Url(sansSigne(pub.getModulus().toByteArray())),
                "e", Primitives.base64Url(pub.getPublicExponent().toByteArray()));
        idp.repondre = r -> new FournisseurFactice.Reponse(200, r.chemin().endsWith("/certs") ? JSON.writeValueAsString(Map.of("keys", List.of(jwk)))
                : JSON.writeValueAsString(Map.of("issuer", emetteurPublie, "jwks_uri", emetteur + "/certs")));
        cfg = new Oidc.Configuration(false, false, emetteur, CLIENT, "s", "r", emetteur, emetteur + "/auth", emetteur + "/token", emetteur + Oidc.CHEMIN_DECOUVERTE);
    }

    @AfterEach
    void fermer() {
        idp.close();
    }

    private static byte[] sansSigne(byte[] b) {
        return b[0] == 0 ? java.util.Arrays.copyOfRange(b, 1, b.length) : b;
    }

    private String jeton(Consumer<Map<String, Object>> retouche) {
        Map<String, Object> c = new HashMap<>(Map.of("iss", cfg.authority(), "sub", "sub-1", "aud", CLIENT, "nonce", NONCE,
                "exp", Date.from(MAINTENANT.plusSeconds(300)), "iat", Date.from(MAINTENANT)));
        retouche.accept(c);
        return Jwts.builder().header().keyId("k1").and().claims(c).signWith(cle.getPrivate()).compact();
    }

    @Test
    @DisplayName("un jeton conforme passe ; l'échéance tolère une minute d'écart d'horloge, pas davantage")
    void conforme() {
        assertThat(echangeur.verifierIdToken(jeton(c -> { }), cfg, NONCE)).containsEntry("sub", "sub-1");
        assertThat(echangeur.verifierIdToken(jeton(c -> c.put("exp", Date.from(MAINTENANT.minusSeconds(30)))), cfg, NONCE)).containsKey("exp");
        assertThatThrownBy(() -> echangeur.verifierIdToken(jeton(c -> c.put("exp", Date.from(MAINTENANT.minusSeconds(120)))), cfg, NONCE))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("expired");
    }

    @Test
    @DisplayName("refus : nonce rejoué, émetteur du jeton ou du document de découverte étranger, autre audience, azp étranger, sans exp, sans sub")
    void refus() {
        assertThatThrownBy(() -> echangeur.verifierIdToken(jeton(c -> { }), cfg, "autre-nonce")).hasMessage("Entra : nonce inattendu (rejeu ?)");
        assertThatThrownBy(() -> echangeur.verifierIdToken(jeton(c -> c.put("iss", "https://pirate")), cfg, NONCE)).hasMessage("Entra : émetteur inattendu");
        assertThatThrownBy(() -> echangeur.verifierIdToken(jeton(c -> c.put("aud", "autre-client")), cfg, NONCE)).hasMessageContaining("Entra : ");
        assertThatThrownBy(() -> echangeur.verifierIdToken(jeton(c -> {
            c.put("aud", List.of(CLIENT, "autre"));
            c.put("azp", "autre");
        }), cfg, NONCE)).hasMessage("OIDC : partie autorisée (azp) inattendue");
        assertThatThrownBy(() -> echangeur.verifierIdToken(jeton(c -> c.remove("exp")), cfg, NONCE)).hasMessage("OIDC : jeton sans échéance (exp)");
        assertThatThrownBy(() -> echangeur.verifierIdToken(jeton(c -> c.remove("sub")), cfg, NONCE)).hasMessage("OIDC : jeton sans sujet (sub)");
        emetteurPublie = "https://pirate/realms/r";
        assertThatThrownBy(() -> echangeur.verifierIdToken(jeton(c -> { }), cfg, NONCE)).isInstanceOf(Oidc.AuthentificationRefuseeException.class)
                .hasMessageStartingWith("OIDC : l'émetteur publié (https://pirate/realms/r)");
    }
}
