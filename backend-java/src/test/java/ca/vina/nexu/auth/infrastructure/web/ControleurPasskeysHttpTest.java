package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.auth.infrastructure.persistance.DepotPasskeys;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.auth.domaine.Passkeys;
import ca.vina.nexu.partage.securite.ContexteRequete;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Port de la partie WebAuthn de {@code auth.test.ts} : cérémonies, isolation par compte, anti-rejeu, expiration des défis. */
@DisplayName("/api/auth/webauthn — passkeys : enregistrement, authentification, identifiants")
class ControleurPasskeysHttpTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);
    private static final ContexteRequete U1 = new ContexteRequete("u1", "u1", "employee", "e1", "t-1");
    private static final ContexteRequete U2 = new ContexteRequete("u2", "u2", "employee", "e2", "t-1");
    private JdbcClient jdbc;
    private DepotPasskeys passkeys;
    private ClientHttpEssai http;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        passkeys = new DepotPasskeys(jdbc, HORLOGE, new Random(5), "", Passkeys.SIMULE);
        http = new ClientHttpEssai(new ControleurPasskeys(passkeys));
    }

    private static String champ(MvcResult r, String nom) throws Exception {
        return r.getResponse().getContentAsString().replaceAll("^.*?\"" + nom + "\":\"([^\"]+)\".*$", "$1");
    }

    @Test
    @DisplayName("enregistrement : défi, réponse vérifiée, identifiant listé pour son seul titulaire, supprimé par lui seul")
    void enregistrement() throws Exception {
        http.get("/api/auth/webauthn/credentials", null).andExpect(status().isUnauthorized());
        http.post("/api/auth/webauthn/register/begin", null, null).andExpect(status().isUnauthorized());
        http.post("/api/auth/webauthn/register/finish", U1, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("no pending registration (challenge expired)"));
        MvcResult debut = http.post("/api/auth/webauthn/register/begin", U1, null).andExpect(status().isOk()).andExpect(jsonPath("$.rp.id").value("nexuvina.ca")).andExpect(jsonPath("$.rp.name").value("Nexu VINA"))
                .andExpect(jsonPath("$.user.id").value("u1")).andExpect(jsonPath("$.pubKeyCredParams[0].alg").value(-7)).andReturn();
        String defi = champ(debut, "challenge");
        http.post("/api/auth/webauthn/register/finish", U1, "{\"challenge\":\"autre\",\"credentialId\":\"c1\",\"publicKey\":\"pk\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("registration verification failed"));
        // Le défi est consommé par l'essai raté : il faut recommencer.
        defi = champ(http.post("/api/auth/webauthn/register/begin", U1, null).andReturn(), "challenge");
        http.post("/api/auth/webauthn/register/finish", U1, "{\"challenge\":\"" + defi + "\",\"credentialId\":\"c1\",\"publicKey\":\"pk\",\"label\":\"Clé YubiKey\",\"counter\":3}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.credentialId").value("c1")).andExpect(jsonPath("$.counter").value(3)).andExpect(jsonPath("$.label").value("Clé YubiKey")).andExpect(jsonPath("$.createdAt").value("2026-09-14T12:00:00.000Z")).andExpect(jsonPath("$.id").isString());
        http.get("/api/auth/webauthn/credentials", U1).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].credentialId").value("c1"));
        http.get("/api/auth/webauthn/credentials", U2).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        String id = passkeys.lister("u1").get(0).id();
        http.envoyer(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/auth/webauthn/credentials/" + id), null).andExpect(status().isUnauthorized());
        http.envoyer(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/auth/webauthn/credentials/" + id), U2).andExpect(status().isNotFound());
        http.envoyer(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/auth/webauthn/credentials/" + id), U1).andExpect(status().isNoContent());
        assertThat(passkeys.lister("u1")).isEmpty();
    }

    @Test
    @DisplayName("authentification : sans passkey rien à faire ; puis défi, assertion, compteur croissant, rejeu refusé")
    void authentification() throws Exception {
        http.post("/api/auth/webauthn/authenticate/begin", U1, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("no registered passkey for this user"));
        http.post("/api/auth/webauthn/authenticate/finish", U1, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("no pending authentication (challenge expired)"));
        String defi = champ(http.post("/api/auth/webauthn/register/begin", U1, null).andReturn(), "challenge");
        http.post("/api/auth/webauthn/register/finish", U1, "{\"challenge\":\"" + defi + "\",\"credentialId\":\"c1\",\"publicKey\":\"pk\"}").andExpect(status().isCreated()).andExpect(jsonPath("$.counter").value(0)).andExpect(jsonPath("$.label").isEmpty());
        MvcResult debut = http.post("/api/auth/webauthn/authenticate/begin", U1, null).andExpect(status().isOk()).andExpect(jsonPath("$.rpId").value("nexuvina.ca")).andExpect(jsonPath("$.allowCredentials[0].id").value("c1")).andReturn();
        String d = champ(debut, "challenge");
        http.post("/api/auth/webauthn/authenticate/finish", U1, "{\"challenge\":\"" + d + "\",\"credentialId\":\"inconnu\",\"counter\":1}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("unknown credential"));
        d = champ(http.post("/api/auth/webauthn/authenticate/begin", U1, null).andReturn(), "challenge");
        http.post("/api/auth/webauthn/authenticate/finish", U1, "{\"challenge\":\"faux\",\"credentialId\":\"c1\",\"counter\":1}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("authentication verification failed"));
        d = champ(http.post("/api/auth/webauthn/authenticate/begin", U1, null).andReturn(), "challenge");
        http.post("/api/auth/webauthn/authenticate/finish", U1, "{\"challenge\":\"" + d + "\",\"credentialId\":\"c1\",\"counter\":0}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("replay detected (counter not incremented)"));
        d = champ(http.post("/api/auth/webauthn/authenticate/begin", U1, null).andReturn(), "challenge");
        http.post("/api/auth/webauthn/authenticate/finish", U1, "{\"challenge\":\"" + d + "\",\"credentialId\":\"c1\",\"counter\":5}").andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true)).andExpect(jsonPath("$.credentialId").value("c1"));
        assertThat(passkeys.lister("u1").get(0).counter()).isEqualTo(5);
        // Le passkey d'un autre compte n'est pas atteignable.
        http.post("/api/auth/webauthn/authenticate/begin", U2, null).andExpect(status().isBadRequest());
        http.post("/api/auth/webauthn/authenticate/finish", null, "{}").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("les défis expirent après cinq minutes ; le vérificateur et l'identifiant du RP sont injectés")
    void expiration() {
        java.util.concurrent.atomic.AtomicReference<Instant> maintenant = new java.util.concurrent.atomic.AtomicReference<>(HORLOGE.instant());
        Clock reglable = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return maintenant.get();
            }
        };
        DepotPasskeys reglables = new DepotPasskeys(jdbc, reglable, new Random(1), "app.exemple.qc.ca", Passkeys.SIMULE);
        reglables.debuterEnregistrement("u1");
        assertThatThrownBy(() -> reglables.terminerEnregistrement("u1", Map.of())).hasMessage("registration verification failed");
        Map<String, Object> options = reglables.debuterEnregistrement("u1");
        reglables.terminerEnregistrement("u1", Map.of("challenge", options.get("challenge"), "credentialId", "c1", "publicKey", "pk"));
        Map<String, Object> auth = reglables.debuterAuthentification("u1");
        assertThat(auth).containsEntry("rpId", "app.exemple.qc.ca");
        // Six minutes plus tard : le défi est périmé.
        maintenant.set(HORLOGE.instant().plus(Duration.ofMinutes(6)));
        assertThatThrownBy(() -> reglables.terminerAuthentification("u1", Map.of("challenge", auth.get("challenge"), "credentialId", "c1", "counter", 1))).hasMessage("no pending authentication (challenge expired)");
        // La table est créée si elle manque, sans écraser l'existante.
        assertThat(new DepotPasskeys(jdbc, HORLOGE, new Random(1), null, Passkeys.SIMULE).lister("u1")).hasSize(1);
        assertThat(new DepotPasskeys(jdbc, HORLOGE, "")).isNotNull();
    }
}
