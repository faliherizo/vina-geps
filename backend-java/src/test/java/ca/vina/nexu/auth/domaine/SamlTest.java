package ca.vina.nexu.auth.domaine;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le SP SAML refuse tout ce qu'il ne peut pas prouver : chaque contrôle de sécurité est un essai
 * qui forge la réponse fautive avec une clé locale et vérifie le message rendu.
 */
@DisplayName("SAML — le fournisseur de services vérifie, jamais ne suppose")
class SamlTest {

    private static final Instant MAINTENANT = Instant.parse("2026-09-14T12:00:00Z");
    private static KeyPair idp;
    private static KeyPair autre;
    private static Saml.Configuration cfg;

    @BeforeAll
    static void cles() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        idp = g.generateKeyPair();
        autre = g.generateKeyPair();
        cfg = new Saml.Configuration(true, false, "urn:sp", "http://sp/acs", "http://idp/sso?x=1", "urn:idp", idp.getPublic());
    }

    private static Saml.ReponseFactice base() {
        return Saml.ReponseFactice.pour("_d1", "a@b.qc", List.of("Nexu.RH"), "urn:idp", "urn:sp", idp.getPrivate());
    }

    private static Saml.ReponseFactice variante(Saml.ReponseFactice b, String notBefore, String notOnOrAfter, String statut, Saml.Signer signer, java.security.PrivateKey cle, Map<String, String> attrs, boolean sansConditions, boolean sansCourriel) {
        return new Saml.ReponseFactice(b.inResponseTo(), b.email(), b.roles(), b.issuer(), b.audience(), notBefore, notOnOrAfter, statut, signer, cle, attrs, sansConditions, sansCourriel);
    }

    private static String forger(Saml.ReponseFactice r) {
        return Saml.forgerReponse(r, "_r1", "_a1", MAINTENANT);
    }

    private static Saml.AssertionValidee recevoir(String b64) {
        return Saml.recevoirReponse(b64, cfg, MAINTENANT, "_d1"::equals);
    }

    @Test
    @DisplayName("une assertion signée par l'IdP, adressée à nous, en réponse à notre demande, est acceptée")
    void assertionAcceptee() {
        Saml.AssertionValidee a = recevoir(forger(base()));
        assertThat(a.claims().email()).isEqualTo("a@b.qc");
        assertThat(a.claims().roles()).containsExactly("Nexu.RH");
        assertThat(a.claims().name()).isNull();
        assertThat(a.claims().oid()).isEqualTo("a@b.qc");
        assertThat(a.inResponseTo()).isEqualTo("_d1");
        // signée sur la réponse plutôt que sur l'assertion : Entra fait l'un ou l'autre
        assertThat(recevoir(forger(variante(base(), null, null, null, Saml.Signer.REPONSE, idp.getPrivate(), null, false, false))).claims().email()).isEqualTo("a@b.qc");
        // sans conditions, sans attribut courriel (le NameID suffit), avec un nom d'affichage
        Saml.AssertionValidee b = recevoir(forger(variante(base(), null, null, null, null, idp.getPrivate(), Map.of("http://schemas.microsoft.com/identity/claims/displayname", "A B"), true, true)));
        assertThat(b.claims().name()).isEqualTo("A B");
        assertThat(b.claims().email()).isEqualTo("a@b.qc");
    }

    @Test
    @DisplayName("l'URL de connexion porte une AuthnRequest déflatée que l'IdP sait relire")
    void demande() {
        String url = Saml.urlDeConnexion(cfg, "_d1", MAINTENANT);
        assertThat(url).startsWith("http://idp/sso?x=1&SAMLRequest=");
        String b64 = java.net.URLDecoder.decode(url.substring(url.indexOf("SAMLRequest=") + 12), StandardCharsets.UTF_8);
        Saml.DemandeAuthn d = Saml.lireAuthnRequest(b64);
        assertThat(d.id()).isEqualTo("_d1");
        assertThat(d.issuer()).isEqualTo("urn:sp");
        assertThat(d.acsUrl()).isEqualTo("http://sp/acs");
        Saml.Configuration sans = new Saml.Configuration(false, false, "urn:sp", "http://sp/acs", "http://idp/sso", "urn:idp", idp.getPublic());
        assertThatThrownBy(() -> Saml.urlDeConnexion(sans, "_d", MAINTENANT)).isInstanceOf(IllegalStateException.class).hasMessageContaining("non configuré");
        assertThat(Saml.urlDeConnexion(new Saml.Configuration(true, false, "urn:sp", "http://sp/acs", "http://idp/sso", "urn:idp", idp.getPublic()), "_d1", MAINTENANT)).startsWith("http://idp/sso?SAMLRequest=");
        assertThatThrownBy(() -> Saml.lireAuthnRequest(Base64.getEncoder().encodeToString("pas déflaté".getBytes(StandardCharsets.UTF_8)))).hasMessageContaining("ni déflaté");
        assertThatThrownBy(() -> Saml.lireAuthnRequest(Base64.getEncoder().encodeToString(Saml.deflater("<samlp:AuthnRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" ID=\"x\" AssertionConsumerServiceURL=\"u\"><saml:Issuer> </saml:Issuer></samlp:AuthnRequest>".getBytes(StandardCharsets.UTF_8)))))
                .hasMessageContaining("ID, Issuer");
        assertThatThrownBy(() -> Saml.lireAuthnRequest(Base64.getEncoder().encodeToString(Saml.deflater("<pas du xml".getBytes(StandardCharsets.UTF_8))))).hasMessageContaining("AuthnRequest absente");
    }

    @Test
    @DisplayName("les refus, chacun nommé : configuration, forme, statut, assertion, signature, émetteur, demande, conditions, courriel")
    void refus() {
        Saml.Configuration sans = new Saml.Configuration(false, false, "urn:sp", "http://sp/acs", "http://idp/sso", "urn:idp", idp.getPublic());
        assertThatThrownBy(() -> Saml.recevoirReponse("x", sans, MAINTENANT, s -> true)).hasMessage("SSO SAML non configuré");
        assertThatThrownBy(() -> recevoir("!!!")).hasMessage("réponse SAML illisible");
        assertThatThrownBy(() -> recevoir(Base64.getEncoder().encodeToString("<x/>".getBytes(StandardCharsets.UTF_8)))).hasMessage("réponse SAML illisible");
        assertThatThrownBy(() -> recevoir(forger(variante(base(), null, null, "urn:oasis:names:tc:SAML:2.0:status:Requester", Saml.Signer.REPONSE, idp.getPrivate(), null, false, false)))).hasMessage("statut SAML : urn:oasis:names:tc:SAML:2.0:status:Requester");
        String sansStatut = Base64.getEncoder().encodeToString("<samlp:Response xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" ID=\"_r\"/>".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> recevoir(sansStatut)).hasMessage("statut SAML : absent");
        String sansAssertion = Base64.getEncoder().encodeToString("<samlp:Response xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" ID=\"_r\"><samlp:Status><samlp:StatusCode Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/></samlp:Status></samlp:Response>".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> recevoir(sansAssertion)).hasMessage("assertion absente (assertion chiffrée non prise en charge)");
        assertThatThrownBy(() -> recevoir(forger(variante(base(), null, null, null, Saml.Signer.AUCUNE, idp.getPrivate(), null, false, false)))).hasMessage("signature SAML absente ou invalide");
        assertThatThrownBy(() -> recevoir(forger(variante(base(), null, null, null, null, autre.getPrivate(), null, false, false)))).hasMessage("signature SAML absente ou invalide");
        // l'attaque par enveloppe : signature sous la réponse, mais désignant l'assertion
        assertThatThrownBy(() -> recevoir(forger(variante(base(), null, null, null, Saml.Signer.ENVELOPPE, idp.getPrivate(), null, false, false)))).hasMessage("signature SAML absente ou invalide");
        // une signature valide dont le contenu a été altéré après coup
        String altere = new String(Base64.getDecoder().decode(forger(base())), StandardCharsets.UTF_8).replace("a@b.qc", "c@d.qc");
        assertThatThrownBy(() -> recevoir(Base64.getEncoder().encodeToString(altere.getBytes(StandardCharsets.UTF_8)))).hasMessage("signature SAML absente ou invalide");
        assertThatThrownBy(() -> recevoir(forger(Saml.ReponseFactice.pour("_d1", "a@b.qc", null, "urn:autre", "urn:sp", idp.getPrivate())))).hasMessage("émetteur inattendu : urn:autre");
        // Sans émetteur attendu, plus rien n'est « accepté tel quel » : l'émetteur de l'assertion doit être celui déclaré.
        Saml.Configuration libre = new Saml.Configuration(true, false, "urn:sp", "http://sp/acs", "http://idp/sso", "", idp.getPublic());
        assertThatThrownBy(() -> Saml.recevoirReponse(forger(Saml.ReponseFactice.pour("_d1", "a@b.qc", null, "urn:autre", "urn:sp", idp.getPrivate())), libre, MAINTENANT, "_d1"::equals))
                .hasMessage("émetteur inattendu : urn:autre");        assertThatThrownBy(() -> Saml.recevoirReponse(forger(base()), cfg, MAINTENANT, s -> false)).hasMessage("assertion sans demande en cours (rejeu ou demande expirée)");
        assertThatThrownBy(() -> recevoir(forger(variante(base(), "2026-09-14T13:00:00Z", null, null, null, idp.getPrivate(), null, false, false)))).hasMessage("assertion pas encore valide");
        assertThatThrownBy(() -> recevoir(forger(variante(base(), null, "2026-09-14T11:58:00Z", null, null, idp.getPrivate(), null, false, false)))).hasMessage("assertion expirée");
        assertThat(recevoir(forger(variante(base(), "2026-09-14T12:00:30Z", "2026-09-14T11:59:30Z", null, null, idp.getPrivate(), null, false, false))).claims().email()).isEqualTo("a@b.qc");
        assertThatThrownBy(() -> recevoir(forger(Saml.ReponseFactice.pour("_d1", "a@b.qc", null, "urn:idp", "urn:autre-sp", idp.getPrivate())))).hasMessage("assertion destinée à un autre service (Audience)");
        assertThatThrownBy(() -> recevoir(forger(variante(Saml.ReponseFactice.pour("_d1", "pas-un-courriel", null, "urn:idp", "urn:sp", idp.getPrivate()), null, null, null, null, idp.getPrivate(), null, false, true)))).hasMessage("assertion sans courriel");
        assertThatThrownBy(() -> recevoir(forger(variante(Saml.ReponseFactice.pour("_d1", "", null, "urn:idp", "urn:sp", idp.getPrivate()), null, null, null, null, idp.getPrivate(), null, false, true)))).hasMessage("assertion sans courriel");
    }

    @Test
    @DisplayName("les attributs de repli : email/mail, roles/groups, displayName")
    void attributsDeRepli() throws Exception {
        Saml.ReponseFactice r = variante(Saml.ReponseFactice.pour("_d1", "x@y.qc", null, "urn:idp", "urn:sp", idp.getPrivate()), null, null, null, null, idp.getPrivate(),
                Map.of("mail", "m@y.qc", "groups", "G1", "displayName", "Nom Repli"), false, true);
        Saml.AssertionValidee a = recevoir(forger(r));
        assertThat(a.claims().email()).isEqualTo("m@y.qc");
        assertThat(a.claims().roles()).containsExactly("G1");
        assertThat(a.claims().name()).isEqualTo("Nom Repli");
        Saml.AssertionValidee b = recevoir(forger(variante(Saml.ReponseFactice.pour("_d1", "x@y.qc", null, "urn:idp", "urn:sp", idp.getPrivate()), null, null, null, null, idp.getPrivate(), Map.of("email", "e@y.qc", "roles", "R"), false, true)));
        assertThat(b.claims().email()).isEqualTo("e@y.qc");
        assertThat(b.claims().roles()).containsExactly("R");
        String xml = new String(Base64.getDecoder().decode(forger(base())), StandardCharsets.UTF_8);
        assertThat(xml).contains("<ds:Signature");
        assertThat(Saml.gonfler(Saml.deflater("abc".getBytes(StandardCharsets.UTF_8)))).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("une signature dont l'identifiant signé n'existe plus est refusée, pas plantée")
    void signatureIrresolvable() {
        String xml = new String(Base64.getDecoder().decode(forger(base())), StandardCharsets.UTF_8).replace("ID=\"_a1\"", "ID=\"_a2\"");
        assertThatThrownBy(() -> recevoir(Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8)))).hasMessage("signature SAML absente ou invalide");
    }
}
