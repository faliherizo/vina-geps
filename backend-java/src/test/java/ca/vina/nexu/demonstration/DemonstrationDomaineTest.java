package ca.vina.nexu.demonstration;

import ca.vina.nexu.auth.domaine.Saml;
import ca.vina.nexu.demonstration.domaine.DonneesDemo;
import ca.vina.nexu.demonstration.domaine.EntraDemo;
import ca.vina.nexu.demonstration.domaine.PageHtml;
import ca.vina.nexu.demonstration.domaine.SamlIdpDemo;
import ca.vina.nexu.demonstration.domaine.TwilioDemo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tiers de démonstration — les décisions pures")
class DemonstrationDomaineTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("la page HTML échappe tout ce que le HTML interprète et porte lang, un h1, un style")
    void pageHtml() {
        assertThat(PageHtml.echapper("a&b<c>d\"e'f")).isEqualTo("a&amp;b&lt;c&gt;d&quot;e&#39;f");
        String page = PageHtml.page("Titre <x>", "<h1>Corps</h1>");
        assertThat(page).startsWith("<!doctype html><html lang=\"fr\">").contains("<title>Titre &lt;x&gt;</title>").contains("min-height: 44px").contains("<main><h1>Corps</h1></main>");
        assertThat(PageHtml.boutonsComptes("p")).contains("data-testid=\"p-admin\"").contains("data-testid=\"p-employee\"");
    }

    @Test
    @DisplayName("Twilio : le Basic est refusé s'il ne porte pas exactement le compte et son jeton")
    void authentificationTwilio() {
        String sid = DonneesDemo.TWILIO_ACCOUNT_SID;
        assertThat(TwilioDemo.authentifie(null, sid)).isFalse();
        assertThat(TwilioDemo.authentifie("Bearer x", sid)).isFalse();
        assertThat(TwilioDemo.authentifie("Basic " + Base64.getEncoder().encodeToString(("autre:" + DonneesDemo.TWILIO_AUTH_TOKEN).getBytes(StandardCharsets.UTF_8)), sid)).isFalse();
        assertThat(TwilioDemo.authentifie("Basic " + Base64.getEncoder().encodeToString((sid + ":" + DonneesDemo.TWILIO_AUTH_TOKEN).getBytes(StandardCharsets.UTF_8)), sid)).isTrue();
        TwilioDemo t = new TwilioDemo(HORLOGE, new SecureRandom());
        // un appel vers un numéro ordinaire avec un TwiML qui compose un numéro ordinaire : rien d'injoignable
        assertThat(t.creerAppel(Map.of("To", "+15550100151", "From", DonneesDemo.TWILIO_FROM, "Twiml", "<Dial>+15550100152</Dial>"), sid).corps().get("status")).isEqualTo("queued");
        assertThat(t.creerAppel(Map.of("To", "+15550100151", "From", DonneesDemo.TWILIO_FROM, "Twiml", "<Say>x</Say>"), sid).corps().get("status")).isEqualTo("queued");
    }

    @Test
    @DisplayName("l'IdP SAML de démonstration signe avec la clé qu'il publie, et un compte inconnu n'a pas de réponse")
    void idpSaml() {
        SamlIdpDemo idp = new SamlIdpDemo(HORLOGE, new SecureRandom());
        Saml.DemandeAuthn d = new Saml.DemandeAuthn("_d9", "urn:sp", "http://sp/acs");
        assertThat(idp.reponsePourCompte(d, "inconnu@x")).isNull();
        String b64 = idp.reponsePourCompte(d, "amina.roy@cssdc-demo.qc.ca");
        Saml.Configuration cfg = new Saml.Configuration(true, true, "urn:sp", "http://sp/acs", "http://idp/sso", DonneesDemo.SAML_ISSUER, idp.clePublique());
        Saml.AssertionValidee a = Saml.recevoirReponse(b64, cfg, HORLOGE.instant(), "_d9"::equals);
        assertThat(a.claims().name()).isEqualTo("Amina Roy");
        assertThat(a.claims().roles()).containsExactly("Nexu.Employee");
        assertThat(idp.metadonnees("http://x")).contains("entityID=\"" + DonneesDemo.SAML_ISSUER + "\"");
        assertThat(SamlIdpDemo.pageIllisible("<oups>")).contains("&lt;oups&gt;");
    }

    @Test
    @DisplayName("Entra : le document de découverte et la validation d'une demande complète")
    void entra() {
        Map<String, Object> d = EntraDemo.documentDecouverte("http://x", "t");
        assertThat(d.get("issuer")).isEqualTo("http://x/mock/entra/t/v2.0");
        assertThat(EntraDemo.avecParametres("http://a/b", Map.of("k", "v w"))).isEqualTo("http://a/b?k=v%20w");
        assertThat(EntraDemo.redirigerAvecErreur("http://a/b", null, "e", "d")).isEqualTo("http://a/b?error=e&error_description=d");
    }

    /** Une horloge qu'on avance à la main : le seul moyen d'éprouver une expiration sans attendre. */
    private static final class HorlogeMobile extends Clock {
        private Instant instant = Instant.parse("2026-09-14T12:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @Test
    @DisplayName("Entra : un code expire après dix minutes, et les demandes incomplètes sont refusées une à une")
    void entraExpirationEtDemandes() {
        HorlogeMobile h = new HorlogeMobile();
        EntraDemo e = new EntraDemo(h, new SecureRandom());
        Map<String, String> demande = Map.of("client_id", DonneesDemo.ENTRA_CLIENT_ID, "redirect_uri", "http://app/cb", "code_challenge", EntraDemo.validerDemande(Map.of()) == null ? "" : "c");
        String code = e.emettreCode(demande, DonneesDemo.COMPTES_ENTRA.get(0));
        h.instant = h.instant.plusSeconds(11 * 60);
        EntraDemo.Reponse r = e.echangerCode(Map.of("client_id", DonneesDemo.ENTRA_CLIENT_ID, "client_secret", DonneesDemo.ENTRA_CLIENT_SECRET, "grant_type", "authorization_code", "code", code), "http://x", "t");
        assertThat(r.corps().get("error_codes")).isEqualTo(java.util.List.of(70000));
        // le nettoyage à l'émission suivante oublie le code expiré
        String nouveau = e.emettreCode(demande, DonneesDemo.COMPTES_ENTRA.get(0));
        assertThat(e.codesEnAttente()).containsExactly(nouveau);
        assertThat(EntraDemo.validerDemande(Map.of("client_id", DonneesDemo.ENTRA_CLIENT_ID, "redirect_uri", "")).erreur()).isEqualTo("invalid_request");
        assertThat(EntraDemo.validerDemande(Map.of("client_id", DonneesDemo.ENTRA_CLIENT_ID, "redirect_uri", "http://x", "response_type", "code")).description()).contains("AADSTS70011");
        assertThat(EntraDemo.validerDemande(Map.of("client_id", DonneesDemo.ENTRA_CLIENT_ID, "redirect_uri", "http://x", "response_type", "code", "scope", "openid", "code_challenge", "")).description()).contains("AADSTS501481");
        assertThat(e.pageChoix("t", Map.of("client_id", DonneesDemo.ENTRA_CLIENT_ID, "redirect_uri", "http://x"))).contains("name=\"nonce\" value=\"\"");
    }

    @Test
    @DisplayName("gonfler s'arrête proprement sur un flux tronqué")
    void gonflerTronque() throws Exception {
        byte[] complet = Saml.deflater("x".repeat(20000).getBytes(StandardCharsets.UTF_8));
        byte[] partiel = Saml.gonfler(Arrays.copyOf(complet, 5));
        assertThat(partiel.length).isLessThan(20000);
    }
}
