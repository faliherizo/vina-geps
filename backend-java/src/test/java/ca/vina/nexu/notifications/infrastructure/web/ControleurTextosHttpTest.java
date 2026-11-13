package ca.vina.nexu.notifications.infrastructure.web;

import ca.vina.nexu.notifications.infrastructure.persistance.DepotJournalTextos;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotPreferences;
import ca.vina.nexu.notifications.infrastructure.persistance.Twilio;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.notifications.domaine.JournalTextos;
import ca.vina.nexu.notifications.domaine.Textos;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Port de {@code textos.integration.test.ts} : état, journal masqué, envoi direct (400/429/502/201), quota, rafraîchissement, rappel Twilio signé, canaux du compte. */
@DisplayName("API notifications — textos, quota, rappel Twilio, canaux")
class ControleurTextosHttpTest {

    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", "t-1");
    private static final ContexteRequete EMPLOYE = ClientHttpEssai.session("employee", "t-1");
    private static final ContexteRequete ADMIN_SYSTEME = new ContexteRequete("usr-sys", "sys", "admin", null, null);
    private static final String JETON = "jeton-twilio-secret";
    private static final String RAPPEL = "https://api.exemple.qc.ca/api/notifications/sms/statut/twilio";
    private ClientHttpEssai http;
    private JdbcClient jdbc;
    private DepotJournalTextos journal;
    private Textos.SmsSimule sms;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        Clock horloge = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);
        sms = new Textos.SmsSimule(List.of("+14185550199"), () -> "2026-09-14T12:00:01.000Z", "NexuVINA");
        JournalTextos.Reglages reglages = JournalTextos.Reglages.de(Map.of("SMS_FOURNISSEUR", "twilio", "TWILIO_ACCOUNT_SID", "AC1", "TWILIO_AUTH_TOKEN", JETON, "TWILIO_FROM", "+15145550000",
                "PUBLIC_API_URL", "https://api.exemple.qc.ca/", "SMS_QUOTA_MENSUEL", "2"));
        journal = new DepotJournalTextos(jdbc, horloge, sms, reglages);
        http = new ClientHttpEssai(new ControleurTextos(journal, new DepotPreferences(jdbc, horloge), reglages, new GardeAcces(new RegistrePolitiqueAcces())));
    }

    private static String signature(String url, Map<String, String> params) throws Exception {
        StringBuilder donnees = new StringBuilder(url);
        new TreeMap<>(params).forEach((k, v) -> donnees.append(k).append(v));
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
        mac.init(new javax.crypto.spec.SecretKeySpec(JETON.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA1"));
        return java.util.Base64.getEncoder().encodeToString(mac.doFinal(donnees.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("l'état nomme le fournisseur et le rappel ; l'envoi direct valide, journalise, respecte le quota ; le journal masque les numéros")
    void envoiDirectEtJournal() throws Exception {
        http.get("/api/notifications/sms/etat", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.fournisseur").value("twilio")).andExpect(jsonPath("$.simule").value(false))
                .andExpect(jsonPath("$.manquantes", hasSize(0))).andExpect(jsonPath("$.rappel").value(RAPPEL)).andExpect(jsonPath("$.appel").value("twilio"));
        http.get("/api/notifications/sms/etat", EMPLOYE).andExpect(status().isForbidden());
        http.get("/api/notifications/sms/etat", null).andExpect(status().isUnauthorized());

        http.post("/api/notifications/sms", ADMIN, "{\"numero\":\"12345\",\"texte\":\"\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("numéro invalide : format international (+15145551234) ou nord-américain à dix chiffres attendu")).andExpect(jsonPath("$.errors[1]").value("texte requis"));
        http.post("/api/notifications/sms", ADMIN, null).andExpect(status().isBadRequest());
        http.post("/api/notifications/sms", ADMIN, "{\"numero\":\"+33 6 12 34 56 78\",\"texte\":\"Bonjour\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value("••••••••5678")).andExpect(jsonPath("$.statut").value("envoye")).andExpect(jsonPath("$.contexte").value("manuel:usr-admin"))
                .andExpect(jsonPath("$.fournisseur").value("twilio")).andExpect(jsonPath("$.identifiantFournisseur").value("sim-1")).andExpect(jsonPath("$.envoyeLe").value("2026-09-14T12:00:01.000Z"));
        assertThat(sms.envois().get(0).numero()).isEqualTo("+33612345678");
        http.post("/api/notifications/sms", ADMIN, "{\"numero\":\"418 555 0199\",\"texte\":\"Bonjour\"}").andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errors[0]").value("refusé par le fournisseur")).andExpect(jsonPath("$.texto.statut").value("echoue"));
        http.get("/api/notifications/sms/quota", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.limite").value(2)).andExpect(jsonPath("$.utilises").value(2)).andExpect(jsonPath("$.restants").value(0)).andExpect(jsonPath("$.longueurMax").value(480));
        http.post("/api/notifications/sms", ADMIN, "{\"numero\":\"4185550100\",\"texte\":\"Encore\"}").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errors[0]").value("quota mensuel atteint (2/2 segments en 2026-09) ; ce message en coûte 1"));
        http.post("/api/notifications/sms", EMPLOYE, "{\"numero\":\"4185550100\",\"texte\":\"x\"}").andExpect(status().isForbidden());

        http.get("/api/notifications/sms", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3))).andExpect(jsonPath("$[0].statut").value("echoue")).andExpect(jsonPath("$[0].numero").value("••••••••0100"));
        http.get("/api/notifications/sms?statut=envoye&limite=5", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        http.get("/api/notifications/sms?contexte=manuel:usr-admin", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)));
        http.get("/api/notifications/sms", ClientHttpEssai.session("admin", "t-2")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        http.get("/api/notifications/sms/resume", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.parStatut.envoye").value(1)).andExpect(jsonPath("$.parStatut.echoue").value(2));

        // La limite se règle par l'administration du système, sur l'établissement désigné ; jamais par l'établissement.
        http.put("/api/notifications/sms/quota", ADMIN, "{\"limite\":50}").andExpect(status().isForbidden());
        http.put("/api/notifications/sms/quota", ADMIN_SYSTEME, "{\"limite\":50}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("X-Tenant-Id header required"));
        http.envoyer(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/notifications/sms/quota").header("X-Tenant-Id", "t-1").contentType(MediaType.APPLICATION_JSON).content("{\"limite\":50}"), ADMIN_SYSTEME)
                .andExpect(status().isOk()).andExpect(jsonPath("$.limite").value(50)).andExpect(jsonPath("$.restants").value(48));
        http.envoyer(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/notifications/sms/quota").header("X-Tenant-Id", "t-1").contentType(MediaType.APPLICATION_JSON).content("{\"limite\":-3}"), ADMIN_SYSTEME)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("limite mensuelle : entier de 0 (illimité) à 1 000 000 attendu"));
        http.put("/api/notifications/sms/quota", ClientHttpEssai.superadmin(), null).andExpect(status().isBadRequest());
        http.put("/api/notifications/sms/quota", null, "{}").andExpect(status().isUnauthorized());

        // Rafraîchir : le statut redemandé au fournisseur ; introuvable hors établissement ; rien à redemander sans identifiant.
        http.post("/api/notifications/sms/1/rafraichir", ADMIN, null).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("envoye"));
        http.post("/api/notifications/sms/1/rafraichir", ClientHttpEssai.session("admin", "t-2"), null).andExpect(status().isNotFound());
        http.post("/api/notifications/sms/3/rafraichir", ADMIN, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("texto sans identifiant chez le fournisseur : rien à redemander"));
        http.post("/api/notifications/sms/1/rafraichir", EMPLOYE, null).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("le rappel Twilio est public mais signé : sans signature 403, mal formé 400, signé il met le journal à jour")
    void rappelTwilio() throws Exception {
        http.post("/api/notifications/sms", ADMIN, "{\"numero\":\"4185550100\",\"texte\":\"Bonjour\"}").andExpect(status().isCreated()).andExpect(jsonPath("$.identifiantFournisseur").value("sim-1"));
        Map<String, String> livre = Map.of("MessageSid", "sim-1", "MessageStatus", "delivered");
        http.envoyer(post("/api/notifications/sms/statut/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("MessageSid", "sim-1").param("MessageStatus", "delivered"), null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("signature Twilio absente ou invalide"));
        http.envoyer(post("/api/notifications/sms/statut/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("MessageSid", "sim-1").param("MessageStatus", "delivered").header("X-Twilio-Signature", "faux"), null)
                .andExpect(status().isForbidden());
        http.envoyer(post("/api/notifications/sms/statut/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("MessageSid", "sim-1").param("MessageStatus", "delivered").header("X-Twilio-Signature", signature(RAPPEL, livre)), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.misesAJour").value(1));
        assertThat(journal.lire(1).orElseThrow().statut()).isEqualTo("livre");
        Map<String, String> echec = Map.of("MessageSid", "sim-1", "MessageStatus", "undelivered", "ErrorCode", "30003", "ErrorMessage", "Unreachable destination handset");
        http.envoyer(post("/api/notifications/sms/statut/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED).params(org.springframework.util.CollectionUtils.toMultiValueMap(echec.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> List.of(e.getValue())))))
                .header("X-Twilio-Signature", signature(RAPPEL, echec)), null).andExpect(status().isOk()).andExpect(jsonPath("$.misesAJour").value(1));
        assertThat(journal.lire(1).orElseThrow().erreur()).isEqualTo("30003 Unreachable destination handset");
        Map<String, String> incomplet = Map.of("MessageSid", "sim-1");
        http.envoyer(post("/api/notifications/sms/statut/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("MessageSid", "sim-1").header("X-Twilio-Signature", signature(RAPPEL, incomplet)), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("MessageSid et MessageStatus requis"));
        Map<String, String> inconnu = Map.of("MessageSid", "SMinconnu", "MessageStatus", "sent");
        http.envoyer(post("/api/notifications/sms/statut/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("MessageSid", "SMinconnu").param("MessageStatus", "sent").header("X-Twilio-Signature", signature(RAPPEL, inconnu)), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.misesAJour").value(0));
        // Sans PUBLIC_API_URL, c'est l'URL de la requête qui est signée — et sans jeton, rien ne passe.
        JournalTextos.Reglages sansRappel = JournalTextos.Reglages.de(Map.of("TWILIO_AUTH_TOKEN", JETON));
        ClientHttpEssai local = new ClientHttpEssai(new ControleurTextos(journal, new DepotPreferences(jdbc, Clock.systemUTC()), sansRappel, new GardeAcces(new RegistrePolitiqueAcces())));
        local.envoyer(post("/api/notifications/sms/statut/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("MessageSid", "sim-1").param("MessageStatus", "sent").header("X-Twilio-Signature", signature("http://localhost/api/notifications/sms/statut/twilio", inconnu.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getKey().equals("MessageSid") ? "sim-1" : e.getValue())))), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.misesAJour").value(1));
        ClientHttpEssai sansJeton = new ClientHttpEssai(new ControleurTextos(journal, new DepotPreferences(jdbc, Clock.systemUTC()), JournalTextos.Reglages.de(Map.of()), new GardeAcces(new RegistrePolitiqueAcces())));
        sansJeton.envoyer(post("/api/notifications/sms/statut/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("MessageSid", "sim-1").param("MessageStatus", "sent").header("X-Twilio-Signature", signature(RAPPEL, inconnu)), null)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("les canaux du compte : lisibles et réglables par lui seul, jamais les deux canaux écrits fermés")
    void canaux() throws Exception {
        http.get("/api/notifications/preferences", EMPLOYE).andExpect(status().isOk()).andExpect(jsonPath("$.courriel").value(true)).andExpect(jsonPath("$.texto").value(true)).andExpect(jsonPath("$.poussee").value(true)).andExpect(jsonPath("$.updatedAt").isEmpty());
        http.get("/api/notifications/preferences", null).andExpect(status().isUnauthorized());
        http.put("/api/notifications/preferences", EMPLOYE, "{\"texto\":false}").andExpect(status().isOk()).andExpect(jsonPath("$.texto").value(false)).andExpect(jsonPath("$.updatedAt").value("2026-09-14T12:00:00.000Z"));
        http.put("/api/notifications/preferences", EMPLOYE, "{\"courriel\":false}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("au moins un canal écrit (courriel ou texto) doit rester actif"));
        http.put("/api/notifications/preferences", EMPLOYE, null).andExpect(status().isOk()).andExpect(jsonPath("$.texto").value(false));
        http.put("/api/notifications/preferences", null, "{}").andExpect(status().isUnauthorized());
        http.get("/api/notifications/preferences", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.texto").value(true));
        assertThat(jdbc.sql("SELECT tenantId FROM notification_preferences WHERE userId = 'usr-employee'").query(String.class).single()).isEqualTo("t-1");
    }
}
