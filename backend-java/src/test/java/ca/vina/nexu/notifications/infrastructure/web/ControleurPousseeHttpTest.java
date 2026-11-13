package ca.vina.nexu.notifications.infrastructure.web;

import ca.vina.nexu.notifications.infrastructure.configuration.ConfigurationFournisseurs;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotAbonnementsPoussee;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotAppareils;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.partage.json.infrastructure.Json;
import ca.vina.nexu.notifications.domaine.PousseeNative;
import ca.vina.nexu.notifications.domaine.PousseeWeb;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.notifications.domaine.AbonnementsPoussee;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Port de `poussee-web.integration.test.ts` (partie abonnements) : la clé se lit, un abonnement se prend, se liste, se retire. */
@DisplayName("API notifications — abonnements poussés")
class ControleurPousseeHttpTest {

    private static final ContexteRequete SUP = new ContexteRequete("usr-sup", "sup@t", "employee", "p-1", "t-1");
    private static final ContexteRequete AUTRE = new ContexteRequete("usr-autre", "autre@t", "employee", "p-2", "t-1");
    private ClientHttpEssai http;
    private JdbcClient jdbc;
    private final PousseeNative.Simulee simulateur = new PousseeNative.Simulee();

    private static final String ABONNEMENT = "{\"abonnement\":{\"endpoint\":\"https://push.exemple.qc.ca/a1\",\"keys\":{\"p256dh\":\"BC\",\"auth\":\"AA\"}},\"navigateur\":\"Firefox Android\"}";

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        Clock horloge = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);
        DepotAppareils appareils = new DepotAppareils(jdbc, horloge, simulateur);
        DepotAbonnementsPoussee abonnements = new DepotAbonnementsPoussee(jdbc, horloge, new ConfigurationFournisseurs.ClesInstance(new PousseeWeb.ClesVapid("cle-publique", "", PousseeWeb.SUJET_PAR_DEFAUT), false), new PousseeWeb.PousseeSimulee(Set.of(), Set.of()), appareils);
        http = new ClientHttpEssai(new ControleurPoussee(abonnements, appareils, new GardeAcces(new RegistrePolitiqueAcces())));
    }

    @Test
    void laCleSeLitUnAbonnementSePrendSeListeEtSeRetire() throws Exception {
        http.get("/api/notifications/push/cle-publique", SUP).andExpect(status().isOk()).andExpect(jsonPath("$.cle").value("cle-publique")).andExpect(jsonPath("$.configuree").value(false));
        http.get("/api/notifications/push/cle-publique", null).andExpect(status().isUnauthorized());
        assertThat(new DepotAbonnementsPoussee(jdbc, Clock.systemUTC(), new ConfigurationFournisseurs.ClesInstance(new PousseeWeb.ClesVapid("pub", "priv", "mailto:x"), true), new PousseeWeb.PousseeSimulee(Set.of(), Set.of()), new DepotAppareils(jdbc, Clock.systemUTC(), new PousseeNative.Simulee())).etat()).containsEntry("configuree", true);
        http.post("/api/notifications/push/abonnements", SUP, ABONNEMENT).andExpect(status().isCreated())
                .andExpect(jsonPath("$.endpoint").value("https://push.exemple.qc.ca/a1")).andExpect(jsonPath("$.navigateur").value("Firefox Android"))
                .andExpect(jsonPath("$.dernierStatut").isEmpty()).andExpect(jsonPath("$.creeLe").value("2026-09-14T12:00:00.000Z"));
        http.post("/api/notifications/push/abonnements", SUP, "{\"abonnement\":{\"endpoint\":\"http://x\",\"keys\":{}}}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("endpoint : URL https attendue; keys.p256dh requis; keys.auth requis"));
        http.post("/api/notifications/push/abonnements", SUP, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("abonnement attendu (endpoint, keys.p256dh, keys.auth)"));
        http.post("/api/notifications/push/abonnements", SUP, null).andExpect(status().isBadRequest());
        http.post("/api/notifications/push/abonnements", null, ABONNEMENT).andExpect(status().isUnauthorized());
        // Se réabonner depuis le même navigateur remplace ; un autre compte ne voit rien.
        http.post("/api/notifications/push/abonnements", SUP, ABONNEMENT.replace("Firefox Android", "Chrome")).andExpect(status().isCreated());
        http.get("/api/notifications/push/abonnements", SUP).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].navigateur").value("Chrome"));
        http.get("/api/notifications/push/abonnements", AUTRE).andExpect(jsonPath("$", hasSize(0)));
        http.delete("/api/notifications/push/abonnements", SUP, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("endpoint requis"));
        http.delete("/api/notifications/push/abonnements", AUTRE, "{\"endpoint\":\"https://push.exemple.qc.ca/a1\"}").andExpect(status().isOk()).andExpect(jsonPath("$.retire").value(false));
        http.delete("/api/notifications/push/abonnements?endpoint=https://push.exemple.qc.ca/a1", SUP).andExpect(status().isOk()).andExpect(jsonPath("$.retire").value(true));
        http.delete("/api/notifications/push/abonnements", SUP, "{\"endpoint\":\"https://push.exemple.qc.ca/a1\"}").andExpect(jsonPath("$.retire").value(false));
        http.delete("/api/notifications/push/abonnements", null, "{}").andExpect(status().isUnauthorized());
        // Le domaine : un navigateur long est tronqué à 200 caractères ; les clés se lisent.
        String longNavigateur = "x".repeat(300);
        http.post("/api/notifications/push/abonnements", SUP, "{\"abonnement\":{\"endpoint\":\"https://p/2\",\"keys\":{\"p256dh\":\"BC\",\"auth\":\"AA\"}},\"navigateur\":\"" + longNavigateur + "\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.navigateur").value("x".repeat(200)));
        assertThat(AbonnementsPoussee.lire(Map.of("endpoint", "https://p", "keys", Map.of("p256dh", "a", "auth", "b")))).isEqualTo(new AbonnementsPoussee.Abonnement("https://p", "a", "b"));
        assertThat(AbonnementsPoussee.valider(List.of())).containsExactly("abonnement attendu (endpoint, keys.p256dh, keys.auth)");
        assertThat(AbonnementsPoussee.valider(Map.of("endpoint", "https://p", "keys", Map.of("p256dh", "", "auth", "")))).containsExactly("keys.p256dh requis", "keys.auth requis");
        assertThat(AbonnementsPoussee.valider(Map.of("endpoint", 12, "keys", "x"))).containsExactly("endpoint : URL https attendue", "keys.p256dh requis", "keys.auth requis");
        assertThat(AbonnementsPoussee.valider(Map.of("endpoint", "https://p", "keys", Map.of("p256dh", 1, "auth", 2)))).containsExactly("keys.p256dh requis", "keys.auth requis");
    }

    @Test
    @DisplayName("les appareils mobiles (refonte E ★) : enregistrer, lister, retirer les siens ; un jeton mal formé est refusé en nommant les champs")
    void appareilsMobiles() throws Exception {
        http.get("/api/notifications/appareils", null).andExpect(status().isUnauthorized());
        http.post("/api/notifications/appareils", null, "{}").andExpect(status().isUnauthorized());
        http.delete("/api/notifications/appareils/x", null).andExpect(status().isUnauthorized());
        http.post("/api/notifications/appareils", SUP, "{\"jeton\":\"ExponentPushToken[abcdefghij]\",\"plateforme\":\"ios\",\"nom\":\" iPhone \"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.jeton").value("ExponentPushToken[abcdefghij]")).andExpect(jsonPath("$.plateforme").value("ios")).andExpect(jsonPath("$.nom").value("iPhone"))
                .andExpect(jsonPath("$.enregistreLe").value("2026-09-14T12:00:00.000Z")).andExpect(jsonPath("$.dernierStatut").isEmpty());
        http.post("/api/notifications/appareils", SUP, "{\"jeton\":\"abc\",\"plateforme\":\"blackberry\",\"nom\":\"" + "x".repeat(81) + "\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("jeton : jeton de poussée Expo attendu (ExponentPushToken[…]) ; plateforme : l'une de ios, android, web ; nom : texte de 80 caractères au plus"));
        http.post("/api/notifications/appareils", SUP, null).andExpect(status().isBadRequest());
        // Réenregistré : un seul ; enregistré par un autre compte, il change de main.
        http.post("/api/notifications/appareils", SUP, "{\"jeton\":\"ExponentPushToken[abcdefghij]\",\"plateforme\":\"ios\"}").andExpect(status().isCreated());
        http.get("/api/notifications/appareils", SUP).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        ContexteRequete autre = new ContexteRequete("usr-autre", "autre", "employee", "p-9", "t-1");
        String id = (String) Json.carte(http.post("/api/notifications/appareils", autre, "{\"jeton\":\"ExponentPushToken[abcdefghij]\",\"plateforme\":\"android\"}").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id");
        http.get("/api/notifications/appareils", SUP).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        http.delete("/api/notifications/appareils/" + id, SUP).andExpect(status().isNotFound());
        http.delete("/api/notifications/appareils/" + id, autre).andExpect(status().isNoContent());
        http.get("/api/notifications/appareils", autre).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
}
