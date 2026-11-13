package ca.vina.nexu.integrations.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Les cartes décrivent une plateforme d'après l'environnement injecté — jamais un secret — et disent quand un tiers embarqué répond à la place du réel. */
@DisplayName("Cartes d'état des intégrations")
class CartesIntegrationsTest {

    private static final Webhooks.Webhook ACTIF = new Webhooks.Webhook("w1", "https://x.ca", null, List.of("employe.cree"), true, false, 0, "2026-09-01T00:00:00.000Z");
    private static final Webhooks.Webhook SUSPENDU = new Webhooks.Webhook("w2", "https://y.ca", null, List.of("employe.cree"), true, true, 10, "2026-09-02T00:00:00.000Z");
    private static final CartesIntegrations.Demonstration POSSIBLE = new CartesIntegrations.Demonstration(true, "aucune configuration réelle : la démonstration peut être branchée", false);

    private static Map<String, CartesIntegrations.Carte> parCode(List<CartesIntegrations.Carte> cartes) {
        return cartes.stream().collect(Collectors.toMap(CartesIntegrations.Carte::code, c -> c, (a, b) -> a, LinkedHashMap::new));
    }

    @Test
    void plateformeConfiguree() {
        Map<String, String> env = Map.of("GRICS_CLIENT_ID", "id", "GRICS_CLIENT_SECRET", "s", "ATS_WEBHOOK_SECRET", "x", "AZURE_CLIENT_ID", "abcdef123", "AZURE_TENANT_ID", "css",
                "SAML_IDP_SSO_URL", "https://idp", "SAML_IDP_CERT", "MIIC", "SMS_URL", "https://sms.exemple.qc.ca/api", "SMS_JETON", "j", "CLAMD_HOST", "clam");
        CartesIntegrations.Chiffres chiffres = new CartesIntegrations.Chiffres("2026-09-10T00:00:00.000Z", 2, 3, List.of(ACTIF, SUSPENDU), Map.of("w1", 4, "w2", 1),
                Arrays.asList(null, "2026-09-11T00:00:00.000Z", "2026-09-12T00:00:00.000Z"), 7);
        Map<String, CartesIntegrations.Carte> c = parCode(CartesIntegrations.cartes(env, chiffres));
        assertThat(c.keySet()).containsExactly("grics", "ats", "entra", "saml", "sms", "antivirus", "api", "webhooks");
        assertThat(c.get("grics")).isEqualTo(new CartesIntegrations.Carte("grics", "GRICS — Paie et GRH (Espace API)", "configure", "identifiants client ou jeton configurés (environnement)", "2026-09-10T00:00:00.000Z", 2, "scolaire:reprise", null));
        assertThat(c.get("ats").etat()).isEqualTo("configure");
        assertThat(c.get("entra").detail()).isEqualTo("client abcdef…, locataire css");
        assertThat(c.get("entra").demonstration()).isEqualTo(POSSIBLE);
        assertThat(c.get("saml").etat()).isEqualTo("configure");
        assertThat(c.get("sms").detail()).isEqualTo("fournisseur sms.exemple.qc.ca");
        assertThat(c.get("antivirus").detail()).isEqualTo("clamd clam:3310");
        assertThat(c.get("api")).isEqualTo(new CartesIntegrations.Carte("api", "Clients d'API (OAuth 2.0 client_credentials)", "configure", "3 client(s) ; documentation : /api/integrations/swagger", null, 0, "admin:api", POSSIBLE));
        assertThat(c.get("webhooks")).isEqualTo(new CartesIntegrations.Carte("webhooks", "Webhooks sortants (événements métier)", "configure", "2 webhook(s), 1 suspendu(s), 5 envoi(s) en attente", "2026-09-12T00:00:00.000Z", 7, "admin:integrations", POSSIBLE));
        // GRICS par URL et jeton ; Entra court ; port et hôte clamd explicites (CLAMD_HOTE l'emporte) ; fournisseur SMS nommé.
        Map<String, String> jeton = Map.of("GRICS_URL", "https://grics", "GRICS_JETON", "t", "AZURE_CLIENT_ID", "abc", "CLAMD_HOTE", "clam2", "CLAMD_HOST", "clam", "CLAMD_PORT", "3311", "SMS_FOURNISSEUR", "twilio");
        Map<String, CartesIntegrations.Carte> j = parCode(CartesIntegrations.cartes(jeton, chiffres));
        assertThat(j.get("grics").etat()).isEqualTo("configure");
        assertThat(j.get("entra").detail()).isEqualTo("client abc…, locataire common");
        assertThat(j.get("antivirus").detail()).isEqualTo("clamd clam2:3311");
        assertThat(j.get("sms").detail()).isEqualTo("fournisseur twilio");
    }

    @Test
    void plateformeSimulee() {
        CartesIntegrations.Chiffres vide = new CartesIntegrations.Chiffres(null, 0, 0, List.of(SUSPENDU), Map.of("w2", 0), List.of(), 0);
        Map<String, CartesIntegrations.Carte> c = parCode(CartesIntegrations.cartes(Map.of("GRICS_CLIENT_ID", "seul", "SMS_URL", "https://sms.x"), vide));
        assertThat(List.of(c.get("grics").etat(), c.get("entra").etat(), c.get("sms").etat(), c.get("antivirus").etat(), c.get("saml").etat(), c.get("ats").etat(), c.get("api").etat(), c.get("webhooks").etat()))
                .containsExactly("simule", "simule", "configure", "simule", "non_configure", "non_configure", "non_configure", "non_configure");
        assertThat(c.get("grics").detail()).startsWith("simulateur");
        assertThat(c.get("sms").detail()).isEqualTo("fournisseur sms.x");
        assertThat(c.get("webhooks").detail()).isEqualTo("1 webhook(s), 1 suspendu(s), 0 envoi(s) en attente");
        assertThat(c.get("webhooks").derniereActiviteLe()).isNull();
        assertThat(c.get("antivirus").demonstration().possible()).isTrue();
        Map<String, CartesIntegrations.Carte> nu = parCode(CartesIntegrations.cartes(Map.of(), vide));
        assertThat(nu.get("sms").detail()).startsWith("simulateur");
        assertThat(nu.get("sms").etat()).isEqualTo("simule");
        assertThat(nu.get("api").detail()).isEqualTo("0 client(s) ; documentation : /api/integrations/swagger");
    }

    @Test
    @DisplayName("un tiers embarqué branché l'emporte sur l'environnement, et les sources scolaires réglées par l'établissement disent leur mode")
    void demonstration() {
        CartesIntegrations.Chiffres vide = new CartesIntegrations.Chiffres(null, 0, 1, List.of(ACTIF), Map.of("w1", 0), List.of(), 0);
        CartesIntegrations.Demonstration branchee = new CartesIntegrations.Demonstration(true, "ok", true);
        CartesIntegrations.Demonstration impossible = new CartesIntegrations.Demonstration(false, "configuration réelle présente (AZURE_CLIENT_ID)", false);
        Map<String, CartesIntegrations.Demonstration> parCode = Map.of("entra", impossible, "sms", branchee, "antivirus", branchee, "api", branchee, "webhooks", branchee, "saml", branchee);
        CartesIntegrations.Demonstrations d = new CartesIntegrations.Demonstrations("mock", "http", parCode, "cid-demo", 3311, "http://localhost:4000");
        Map<String, CartesIntegrations.Carte> c = parCode(CartesIntegrations.cartes(Map.of("AZURE_CLIENT_ID", "abc"), vide, d));
        assertThat(c.get("grics").etat()).isEqualTo("demonstration");
        assertThat(c.get("grics").detail()).startsWith("GRICS de démonstration embarquée");
        assertThat(c.get("ats").etat()).isEqualTo("configure");
        assertThat(c.get("ats").detail()).isEqualTo("ATS configuré pour cet établissement (jeton et webhook)");
        assertThat(c.get("entra").etat()).isEqualTo("configure");
        assertThat(c.get("entra").demonstration()).isEqualTo(impossible);
        assertThat(c.get("sms").etat()).isEqualTo("demonstration");
        assertThat(c.get("sms").detail()).startsWith("Twilio de démonstration embarqué");
        assertThat(c.get("antivirus").detail()).isEqualTo("clamd de démonstration embarqué (TCP 127.0.0.1:3311) : INSTREAM, PING, VERSION");
        assertThat(c.get("api").detail()).startsWith("client de démonstration cid-demo");
        assertThat(c.get("saml").detail()).startsWith("IdP SAML de démonstration embarqué");
        assertThat(c.get("webhooks").detail()).isEqualTo("récepteur de démonstration abonné à tous les événements ; journal : http://localhost:4000/mock/webhooks/recepteur/journal.html");
        // Le mode « http » de la GRICS est « configuré » même sans variable ; sans client de démonstration ni port clamd, les libellés le disent.
        CartesIntegrations.Demonstrations h = new CartesIntegrations.Demonstrations("http", "mock", Map.of(), null, null, "");
        Map<String, CartesIntegrations.Carte> hc = parCode(CartesIntegrations.cartes(Map.of(), vide, h));
        assertThat(hc.get("grics").detail()).isEqualTo("Espace API GRICS configuré pour cet établissement");
        assertThat(hc.get("ats").etat()).isEqualTo("demonstration");
        assertThat(hc.get("antivirus").demonstration().branchee()).isFalse();
        CartesIntegrations.Demonstrations tout = new CartesIntegrations.Demonstrations("simule", "simule", Map.of("antivirus", branchee, "api", branchee), null, null, "");
        Map<String, CartesIntegrations.Carte> tc = parCode(CartesIntegrations.cartes(Map.of(), vide, tout));
        assertThat(tc.get("antivirus").detail()).contains("127.0.0.1:?");
        assertThat(tc.get("api").detail()).contains("(créé par un autre processus)");
    }
}
