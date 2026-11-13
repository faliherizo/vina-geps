package ca.vina.nexu.demonstration.domaine;

import ca.vina.nexu.auth.domaine.Saml;
import ca.vina.nexu.partage.crypto.Primitives;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * IdP SAML 2.0 de démonstration — les liaisons de l'OASIS : métadonnées ({@code IDPSSODescriptor},
 * clé de signature, {@code SingleSignOnService} HTTP-Redirect), réception de l'AuthnRequest
 * déflatée avec page de choix de compte, puis formulaire auto-soumis vers l'ACS du SP avec une
 * {@code SAMLResponse} signée XML-DSig RSA-SHA256 (HTTP-POST). La clé est générée à la
 * construction, jamais écrite sur disque ; le SP la lit par {@link #clePublique()}.
 */
public final class SamlIdpDemo {

    private final Clock horloge;
    private final RandomGenerator alea;
    private final KeyPair cles;

    public SamlIdpDemo(Clock horloge, RandomGenerator alea) {
        this.horloge = horloge;
        this.alea = alea;
        this.cles = Primitives.rsa2048();
    }

    public PublicKey clePublique() {
        return cles.getPublic();
    }

    private static String entier(BigInteger n) {
        byte[] b = n.toByteArray();
        if (b.length > 1 && b[0] == 0) {
            byte[] sans = new byte[b.length - 1];
            System.arraycopy(b, 1, sans, 0, sans.length);
            b = sans;
        }
        return Base64.getEncoder().encodeToString(b);
    }

    public String metadonnees(String base) {
        RSAPublicKey pub = (RSAPublicKey) cles.getPublic();
        return "<?xml version=\"1.0\"?><md:EntityDescriptor xmlns:md=\"urn:oasis:names:tc:SAML:2.0:metadata\" xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\" entityID=\"" + DonneesDemo.SAML_ISSUER + "\">"
                + "<md:IDPSSODescriptor WantAuthnRequestsSigned=\"false\" protocolSupportEnumeration=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
                + "<md:KeyDescriptor use=\"signing\"><ds:KeyInfo><ds:KeyValue><ds:RSAKeyValue><ds:Modulus>" + entier(pub.getModulus()) + "</ds:Modulus><ds:Exponent>" + entier(pub.getPublicExponent()) + "</ds:Exponent></ds:RSAKeyValue></ds:KeyValue></ds:KeyInfo></md:KeyDescriptor>"
                + "<md:NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress</md:NameIDFormat>"
                + "<md:SingleSignOnService Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect\" Location=\"" + base + "/mock/saml/sso\"/>"
                + "</md:IDPSSODescriptor></md:EntityDescriptor>";
    }

    private String identifiant(String prefixe) {
        byte[] b = new byte[8];
        alea.nextBytes(b);
        return "_" + prefixe + HexFormat.of().formatHex(b);
    }

    /** Une réponse signée pour le compte choisi, adressée au SP qui a demandé ({@code InResponseTo}, {@code Audience}) ; {@code null} si le compte est inconnu. */
    public String reponsePourCompte(Saml.DemandeAuthn d, String courriel) {
        DonneesDemo.CompteEntra compte = DonneesDemo.compte(courriel);
        if (compte == null) {
            return null;
        }
        Saml.ReponseFactice r = new Saml.ReponseFactice(d.id(), compte.courriel(), compte.roles(), DonneesDemo.SAML_ISSUER, d.issuer(), null, null, null, null,
                cles.getPrivate(), Map.of("http://schemas.microsoft.com/identity/claims/displayname", compte.nom()), false, false);
        return Saml.forgerReponse(r, identifiant("r"), identifiant("a"), horloge.instant());
    }

    /** Le statut {@code Requester} : l'IdP répond au SP qu'il n'a pas compris la demande, sans assertion. */
    public String reponseRequester(Saml.DemandeAuthn d) {
        Saml.ReponseFactice r = new Saml.ReponseFactice(d.id(), "", null, DonneesDemo.SAML_ISSUER, d.issuer(), null, null,
                "urn:oasis:names:tc:SAML:2.0:status:Requester", Saml.Signer.REPONSE, cles.getPrivate(), null, false, false);
        return Saml.forgerReponse(r, identifiant("r"), identifiant("a"), horloge.instant());
    }

    public static String pageChoix(String samlRequest, String relayState, Saml.DemandeAuthn d) {
        return PageHtml.page("Choisir un compte — IdP SAML (démonstration)", "\n    <h1>Choisir un compte</h1>\n"
                + "    <p>Fournisseur d'identité SAML <strong>de démonstration</strong> : aucun mot de passe, les comptes sont fictifs. Le service demandeur est <code>" + PageHtml.echapper(d.issuer()) + "</code>.</p>\n"
                + "    <form method=\"post\" action=\"sso\"><input type=\"hidden\" name=\"SAMLRequest\" value=\"" + PageHtml.echapper(samlRequest) + "\"><input type=\"hidden\" name=\"RelayState\" value=\"" + PageHtml.echapper(relayState) + "\"><ul class=\"comptes\">"
                + PageHtml.boutonsComptes("saml-compte") + "</ul></form>");
    }

    public static String pageIllisible(String message) {
        return PageHtml.page("Demande illisible — IdP SAML (démonstration)", "<h1>Demande illisible</h1><p>" + PageHtml.echapper(message) + "</p>");
    }

    /** Le formulaire auto-soumis du HTTP-POST binding ; un bouton reste, pour qui a désactivé le script. */
    public static String formulaireAcs(String acsUrl, String samlResponseB64, String relayState) {
        return PageHtml.page("Retour vers l'application — IdP SAML (démonstration)", "\n    <h1>Retour vers l'application</h1>\n"
                + "    <form method=\"post\" action=\"" + PageHtml.echapper(acsUrl) + "\" id=\"acs\"><input type=\"hidden\" name=\"SAMLResponse\" value=\"" + PageHtml.echapper(samlResponseB64) + "\"><input type=\"hidden\" name=\"RelayState\" value=\"" + PageHtml.echapper(relayState) + "\">\n"
                + "    <ul class=\"comptes\"><li><button type=\"submit\" data-testid=\"saml-continuer\">Continuer</button></li></ul></form>\n"
                + "    <script>document.getElementById(\"acs\").submit();</script>");
    }
}
