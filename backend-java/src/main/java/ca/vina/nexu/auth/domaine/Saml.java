package ca.vina.nexu.auth.domaine;

import ca.vina.nexu.partage.crypto.Primitives;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * SSO SAML 2.0 (grille 41), la partie pure — port de {@code auth/ssoSaml.ts} : la demande
 * d'authentification (HTTP-Redirect, déflatée), la réception de l'assertion (HTTP-POST sur
 * l'ACS) et, pour les essais et l'IdP de démonstration, la fabrique d'une réponse signée.
 *
 * <p>Ce qui décide de la sécurité est vérifié, jamais supposé : la signature XML (réponse ou
 * assertion, contre la clé de l'IdP, et la référence doit désigner l'élément signé lui-même —
 * une signature valide posée sur un autre fragment ne compte pas), l'émetteur, les conditions
 * ({@code NotBefore}, {@code NotOnOrAfter}, {@code Audience}), le {@code InResponseTo} d'une
 * demande en cours (ce qui empêche de rejouer une assertion capturée), et le statut
 * {@code Success}.
 */
public final class Saml {

    private Saml() {
    }

    public static final String NS_SAMLP = "urn:oasis:names:tc:SAML:2.0:protocol";
    public static final String NS_SAML = "urn:oasis:names:tc:SAML:2.0:assertion";
    public static final String NS_DS = "http://www.w3.org/2000/09/xmldsig#";
    public static final String SUCCES = "urn:oasis:names:tc:SAML:2.0:status:Success";
    private static final String RSA_SHA256 = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";

    /** Une réponse qui ne peut pas être acceptée, et pourquoi — le message est celui du Node. */
    public static final class ErreurSaml extends RuntimeException {
        public ErreurSaml(String message) {
            super(message);
        }
    }

    public record Configuration(boolean enabled, boolean demonstration, String entityId, String acsUrl, String idpSsoUrl, String idpIssuer, PublicKey idpCle) {
    }

    public record Revendications(String email, String name, List<String> roles, String oid) {
    }

    public record AssertionValidee(Revendications claims, String inResponseTo) {
    }

    public record DemandeAuthn(String id, String issuer, String acsUrl) {
    }

    // ── Configuration ──────────────────────────────────────────────────────

    /** Ce que le registre des démonstrations fournit quand l'IdP SAML embarqué est branché. */
    public record Demo(String issuer, PublicKey cle, String urlServeur, Optional<String> urlApplication) {
    }

    private static String env(Map<String, String> env, String cle, String defaut) {
        String v = env.get(cle);
        return v == null || v.isEmpty() ? defaut : v;
    }

    /**
     * Sans {@code SAML_IDP_SSO_URL} et {@code SAML_IDP_CERT}, l'adaptateur est désactivé — il n'y a
     * pas de « mode simulé » côté SAML : le seul moyen de le vérifier est une assertion signée.
     */
    public static Configuration configuration(Map<String, String> env, Optional<Demo> demo) {
        String entityId = env(env, "SAML_SP_ENTITY_ID", "urn:nexu-vina:sp");
        if (demo.isPresent()) {
            Demo d = demo.get();
            return new Configuration(true, true, entityId, d.urlApplication().orElse(d.urlServeur()) + "/api/auth/sso/saml/acs", d.urlServeur() + "/mock/saml/sso", d.issuer(), d.cle());
        }
        String idpSsoUrl = env(env, "SAML_IDP_SSO_URL", "");
        String cert = env(env, "SAML_IDP_CERT", "").replace("\\n", "\n");
        return new Configuration(!idpSsoUrl.isEmpty() && !cert.isEmpty(), false, entityId, env(env, "SAML_SP_ACS_URL", "http://localhost:4000/api/auth/sso/saml/acs"),
                idpSsoUrl, env(env, "SAML_IDP_ISSUER", ""), cert.isEmpty() ? null : clePublique(cert));
    }

    /** La clé publique d'un certificat X.509 en PEM — ou d'une clé publique en PEM, ce que les IdP exportent aussi. */
    public static PublicKey clePublique(String pem) {
        String corps = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(corps);
        if (pem.contains("CERTIFICATE")) {
            return Primitives.surement("certificat X.509", () -> CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der)).getPublicKey());
        }
        return Primitives.surement("clé publique", () -> KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der)));
    }

    /** Les métadonnées SP : ce que l'administrateur de l'IdP (Entra, ADFS…) importe pour nous déclarer. */
    public static String metadonneesSp(Configuration cfg) {
        return "<?xml version=\"1.0\"?><md:EntityDescriptor xmlns:md=\"urn:oasis:names:tc:SAML:2.0:metadata\" entityID=\"" + cfg.entityId() + "\">"
                + "<md:SPSSODescriptor AuthnRequestsSigned=\"false\" WantAssertionsSigned=\"true\" protocolSupportEnumeration=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
                + "<md:NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress</md:NameIDFormat>"
                + "<md:AssertionConsumerService Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\" Location=\"" + cfg.acsUrl() + "\" index=\"0\" isDefault=\"true\"/>"
                + "</md:SPSSODescriptor></md:EntityDescriptor>";
    }

    // ── XML ────────────────────────────────────────────────────────────────

    static Document lireXml(byte[] xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(null);
            return b.parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            return null;
        }
    }

    private static Element un(Document doc, String ns, String nom) {
        NodeList l = doc.getElementsByTagNameNS(ns, nom);
        return l.getLength() > 0 ? (Element) l.item(0) : null;
    }

    private static String texte(Node n) {
        return n == null ? "" : n.getTextContent().trim();
    }

    static String serialiser(Document doc) {
        return Primitives.surement("sérialisation XML", () -> {
            StringWriter w = new StringWriter();
            var t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            t.transform(new DOMSource(doc), new StreamResult(w));
            return w.toString();
        });
    }

    /** Les attributs {@code ID} de la réponse et de l'assertion, déclarés comme identifiants : XML-DSig résout {@code #id} par là. */
    private static void declarerIds(Document doc) {
        for (String nom : List.of("Response", "Assertion")) {
            NodeList l = doc.getElementsByTagNameNS(nom.equals("Response") ? NS_SAMLP : NS_SAML, nom);
            for (int i = 0; i < l.getLength(); i++) {
                Element e = (Element) l.item(i);
                if (e.hasAttribute("ID")) {
                    e.setIdAttribute("ID", true);
                }
            }
        }
    }

    // ── Demande (HTTP-Redirect) ────────────────────────────────────────────

    public static byte[] deflater(byte[] donnees) {
        Deflater d = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        d.setInput(donnees);
        d.finish();
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        byte[] tampon = new byte[4096];
        while (!d.finished()) {
            sortie.write(tampon, 0, d.deflate(tampon));
        }
        d.end();
        return sortie.toByteArray();
    }

    public static byte[] gonfler(byte[] donnees) throws DataFormatException {
        Inflater i = new Inflater(true);
        i.setInput(donnees);
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        byte[] tampon = new byte[4096];
        while (!i.finished()) {
            int n = i.inflate(tampon);
            if (n == 0 && (i.needsInput() || i.needsDictionary())) {
                break;
            }
            sortie.write(tampon, 0, n);
        }
        i.end();
        return sortie.toByteArray();
    }

    /** L'AuthnRequest et l'URL de redirection : déflatée, base64, en paramètre {@code SAMLRequest}. */
    public static String urlDeConnexion(Configuration cfg, String id, Instant maintenant) {
        if (!cfg.enabled()) {
            throw new IllegalStateException("SSO SAML non configuré (SAML_IDP_SSO_URL, SAML_IDP_CERT)");
        }
        String xml = "<samlp:AuthnRequest xmlns:samlp=\"" + NS_SAMLP + "\" xmlns:saml=\"" + NS_SAML + "\" ID=\"" + id + "\" Version=\"2.0\" "
                + "IssueInstant=\"" + maintenant + "\" Destination=\"" + cfg.idpSsoUrl() + "\" "
                + "AssertionConsumerServiceURL=\"" + cfg.acsUrl() + "\" ProtocolBinding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\">"
                + "<saml:Issuer>" + cfg.entityId() + "</saml:Issuer>"
                + "<samlp:NameIDPolicy Format=\"urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress\" AllowCreate=\"true\"/>"
                + "</samlp:AuthnRequest>";
        String encode = Base64.getEncoder().encodeToString(deflater(xml.getBytes(StandardCharsets.UTF_8)));
        String param = java.net.URLEncoder.encode(encode, StandardCharsets.UTF_8).replace("+", "%20");
        return cfg.idpSsoUrl() + (cfg.idpSsoUrl().contains("?") ? "&" : "?") + "SAMLRequest=" + param;
    }

    /** Lit une AuthnRequest HTTP-Redirect : base64 d'un XML déflaté (RFC 1951, sans en-tête). */
    public static DemandeAuthn lireAuthnRequest(String samlRequestB64) {
        byte[] xml;
        try {
            xml = gonfler(Base64.getDecoder().decode(samlRequestB64));
        } catch (IllegalArgumentException | DataFormatException e) {
            throw new IllegalArgumentException("SAMLRequest illisible : ni base64, ni déflaté");
        }
        Document doc = lireXml(xml);
        Element req = doc == null ? null : un(doc, NS_SAMLP, "AuthnRequest");
        if (req == null) {
            throw new IllegalArgumentException("SAMLRequest : AuthnRequest absente");
        }
        String id = req.getAttribute("ID");
        String acs = req.getAttribute("AssertionConsumerServiceURL");
        String issuer = texte(un(doc, NS_SAML, "Issuer"));
        if (id.isEmpty() || acs.isEmpty() || issuer.isEmpty()) {
            throw new IllegalArgumentException("SAMLRequest : ID, Issuer et AssertionConsumerServiceURL requis");
        }
        return new DemandeAuthn(id, issuer, acs);
    }

    // ── Réception ──────────────────────────────────────────────────────────

    /** La signature enfant direct de l'élément, vérifiée contre la clé, et dont la référence désigne l'élément lui-même. */
    static boolean signatureValide(Element element, PublicKey cle) {
        Element signature = null;
        for (Node n = element.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && NS_DS.equals(e.getNamespaceURI()) && "Signature".equals(e.getLocalName())) {
                signature = e;
                break;
            }
        }
        if (signature == null) {
            return false;
        }
        try {
            DOMValidateContext ctx = new DOMValidateContext(cle, signature);
            XMLSignature sig = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(ctx);
            if (!sig.validate(ctx)) {
                return false;
            }
            String id = element.getAttribute("ID");
            return sig.getSignedInfo().getReferences().stream().anyMatch(r -> ("#" + id).equals(r.getURI()) || id.equals(r.getURI()));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Traite une {@code SAMLResponse} (base64 du XML). Lève une {@link ErreurSaml} nommant ce qui
     * manque ; {@code demandeEnCours} dit si l'{@code InResponseTo} correspond à une demande que
     * nous avons émise (et la consomme).
     */
    public static AssertionValidee recevoirReponse(String samlResponseB64, Configuration cfg, Instant maintenant, Predicate<String> demandeEnCours) {
        if (!cfg.enabled()) {
            throw new ErreurSaml("SSO SAML non configuré");
        }
        byte[] xml;
        try {
            xml = Base64.getDecoder().decode(samlResponseB64);
        } catch (IllegalArgumentException e) {
            xml = new byte[0];
        }
        Document doc = lireXml(xml);
        Element reponse = doc == null ? null : un(doc, NS_SAMLP, "Response");
        if (reponse == null) {
            throw new ErreurSaml("réponse SAML illisible");
        }
        declarerIds(doc);
        Element code = un(doc, NS_SAMLP, "StatusCode");
        String statut = code == null ? "" : code.getAttribute("Value");
        if (!SUCCES.equals(statut)) {
            throw new ErreurSaml("statut SAML : " + (statut.isEmpty() ? "absent" : statut));
        }
        Element assertion = un(doc, NS_SAML, "Assertion");
        if (assertion == null) {
            throw new ErreurSaml("assertion absente (assertion chiffrée non prise en charge)");
        }
        if (!signatureValide(reponse, cfg.idpCle()) && !signatureValide(assertion, cfg.idpCle())) {
            throw new ErreurSaml("signature SAML absente ou invalide");
        }
        String issuer = texte(assertion.getElementsByTagNameNS(NS_SAML, "Issuer").item(0));
        if (cfg.idpIssuer() != null && !cfg.idpIssuer().isEmpty() && !issuer.equals(cfg.idpIssuer())) {
            throw new ErreurSaml("émetteur inattendu : " + issuer);
        }
        String inResponseTo = reponse.getAttribute("InResponseTo");
        if (!demandeEnCours.test(inResponseTo)) {
            throw new ErreurSaml("assertion sans demande en cours (rejeu ou demande expirée)");
        }
        verifierConditions(assertion, cfg, maintenant);
        Map<String, List<String>> attributs = attributs(assertion);
        String nameId = texte(assertion.getElementsByTagNameNS(NS_SAML, "NameID").item(0));
        String email = premier(attributs, "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress", "email", "mail");
        if (email == null) {
            email = nameId;
        }
        if (email.isEmpty() || !email.contains("@")) {
            throw new ErreurSaml("assertion sans courriel");
        }
        List<String> roles = liste(attributs, "http://schemas.microsoft.com/ws/2008/06/identity/claims/role", "roles", "groups");
        String name = premier(attributs, "http://schemas.microsoft.com/identity/claims/displayname", "displayName");
        return new AssertionValidee(new Revendications(email, name, roles, nameId), inResponseTo);
    }

    private static void verifierConditions(Element assertion, Configuration cfg, Instant maintenant) {
        NodeList l = assertion.getElementsByTagNameNS(NS_SAML, "Conditions");
        if (l.getLength() == 0) {
            return;
        }
        Element conditions = (Element) l.item(0);
        String nb = conditions.getAttribute("NotBefore");
        String na = conditions.getAttribute("NotOnOrAfter");
        if (!nb.isEmpty() && Instant.parse(nb).isAfter(maintenant.plusSeconds(60))) {
            throw new ErreurSaml("assertion pas encore valide");
        }
        if (!na.isEmpty() && !Instant.parse(na).isAfter(maintenant.minusSeconds(60))) {
            throw new ErreurSaml("assertion expirée");
        }
        NodeList audiences = conditions.getElementsByTagNameNS(NS_SAML, "Audience");
        if (audiences.getLength() == 0) {
            return;
        }
        for (int i = 0; i < audiences.getLength(); i++) {
            if (texte(audiences.item(i)).equals(cfg.entityId())) {
                return;
            }
        }
        throw new ErreurSaml("assertion destinée à un autre service (Audience)");
    }

    private static Map<String, List<String>> attributs(Element assertion) {
        Map<String, List<String>> attributs = new LinkedHashMap<>();
        NodeList liste = assertion.getElementsByTagNameNS(NS_SAML, "Attribute");
        for (int i = 0; i < liste.getLength(); i++) {
            Element a = (Element) liste.item(i);
            NodeList valeurs = a.getElementsByTagNameNS(NS_SAML, "AttributeValue");
            List<String> v = new ArrayList<>();
            for (int k = 0; k < valeurs.getLength(); k++) {
                v.add(texte(valeurs.item(k)));
            }
            attributs.put(a.getAttribute("Name"), v);
        }
        return attributs;
    }

    private static List<String> liste(Map<String, List<String>> attributs, String... noms) {
        for (String n : noms) {
            if (attributs.containsKey(n)) {
                return attributs.get(n);
            }
        }
        return List.of();
    }

    private static String premier(Map<String, List<String>> attributs, String... noms) {
        for (String n : noms) {
            List<String> v = attributs.get(n);
            if (v != null && !v.isEmpty()) {
                return v.get(0);
            }
        }
        return null;
    }

    // ── IdP factice, pour les essais et la démonstration ───────────────────

    /** Où poser la signature : {@code enveloppe} = sous la réponse mais désignant l'assertion — l'attaque par enveloppe. */
    public enum Signer { REPONSE, ASSERTION, AUCUNE, ENVELOPPE }

    public record ReponseFactice(String inResponseTo, String email, List<String> roles, String issuer, String audience,
                                 String notBefore, String notOnOrAfter, String statut, Signer signer, PrivateKey cle,
                                 Map<String, String> attributsSupplementaires, boolean sansConditions, boolean sansCourrielAttribut) {
        public ReponseFactice {
            roles = roles == null ? List.of() : roles;
            statut = statut == null ? SUCCES : statut;
            signer = signer == null ? Signer.ASSERTION : signer;
            attributsSupplementaires = attributsSupplementaires == null ? Map.of() : attributsSupplementaires;
        }

        /** La forme courante : un compte, ses rôles, signée sur l'assertion. */
        public static ReponseFactice pour(String inResponseTo, String email, List<String> roles, String issuer, String audience, PrivateKey cle) {
            return new ReponseFactice(inResponseTo, email, roles, issuer, audience, null, null, null, null, cle, null, false, false);
        }
    }

    /** Produit une {@code SAMLResponse} signée avec une clé locale — c'est ainsi qu'on prouve la chaîne sans IdP. */
    public static String forgerReponse(ReponseFactice r, String idReponse, String idAssertion, Instant maintenant) {
        String instant = maintenant.toString();
        String nb = r.notBefore() != null ? r.notBefore() : maintenant.minusSeconds(60).toString();
        String na = r.notOnOrAfter() != null ? r.notOnOrAfter() : maintenant.plusSeconds(300).toString();
        StringBuilder attrs = new StringBuilder();
        r.attributsSupplementaires().forEach((n, v) -> attrs.append("<saml:Attribute Name=\"").append(n).append("\"><saml:AttributeValue>").append(v).append("</saml:AttributeValue></saml:Attribute>"));
        StringBuilder roles = new StringBuilder();
        r.roles().forEach(x -> roles.append("<saml:AttributeValue>").append(x).append("</saml:AttributeValue>"));
        String assertion = "<saml:Assertion xmlns:saml=\"" + NS_SAML + "\" ID=\"" + idAssertion + "\" Version=\"2.0\" IssueInstant=\"" + instant + "\">"
                + "<saml:Issuer>" + r.issuer() + "</saml:Issuer>"
                + "<saml:Subject><saml:NameID Format=\"urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress\">" + r.email() + "</saml:NameID></saml:Subject>"
                + (r.sansConditions() ? "" : "<saml:Conditions NotBefore=\"" + nb + "\" NotOnOrAfter=\"" + na + "\"><saml:AudienceRestriction><saml:Audience>" + r.audience() + "</saml:Audience></saml:AudienceRestriction></saml:Conditions>")
                + "<saml:AttributeStatement>"
                + (r.sansCourrielAttribut() ? "" : "<saml:Attribute Name=\"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress\"><saml:AttributeValue>" + r.email() + "</saml:AttributeValue></saml:Attribute>")
                + (roles.isEmpty() ? "" : "<saml:Attribute Name=\"http://schemas.microsoft.com/ws/2008/06/identity/claims/role\">" + roles + "</saml:Attribute>")
                + attrs
                + "</saml:AttributeStatement></saml:Assertion>";
        String xml = "<samlp:Response xmlns:samlp=\"" + NS_SAMLP + "\" xmlns:saml=\"" + NS_SAML + "\" ID=\"" + idReponse + "\" Version=\"2.0\" IssueInstant=\"" + instant + "\" InResponseTo=\"" + r.inResponseTo() + "\">"
                + "<saml:Issuer>" + r.issuer() + "</saml:Issuer>"
                + "<samlp:Status><samlp:StatusCode Value=\"" + r.statut() + "\"/></samlp:Status>"
                + assertion + "</samlp:Response>";
        if (r.signer() == Signer.AUCUNE) {
            return Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
        }
        Document doc = lireXml(xml.getBytes(StandardCharsets.UTF_8));
        declarerIds(doc);
        Element reference = un(doc, r.signer() == Signer.REPONSE ? NS_SAMLP : NS_SAML, r.signer() == Signer.REPONSE ? "Response" : "Assertion");
        Element emplacement = un(doc, r.signer() == Signer.ASSERTION ? NS_SAML : NS_SAMLP, r.signer() == Signer.ASSERTION ? "Assertion" : "Response");
        signer(doc, reference, emplacement, r.cle());
        return Base64.getEncoder().encodeToString(serialiser(doc).getBytes(StandardCharsets.UTF_8));
    }

    /** Signature enveloppée RSA-SHA256, exc-c14n, posée après l'{@code Issuer} de l'emplacement. */
    private static void signer(Document doc, Element reference, Element emplacement, PrivateKey cle) {
        Primitives.surement("signature XML", () -> {
            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
            Reference ref = fac.newReference("#" + reference.getAttribute("ID"), fac.newDigestMethod(DigestMethod.SHA256, null),
                    List.of(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null), fac.newTransform(CanonicalizationMethod.EXCLUSIVE, (TransformParameterSpec) null)), null, null);
            SignedInfo si = fac.newSignedInfo(fac.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE, (C14NMethodParameterSpec) null), fac.newSignatureMethod(RSA_SHA256, null), List.of(ref));
            Element issuer = (Element) emplacement.getElementsByTagNameNS(NS_SAML, "Issuer").item(0);
            DOMSignContext ctx = new DOMSignContext(cle, emplacement, issuer.getNextSibling());
            ctx.setDefaultNamespacePrefix("ds");
            fac.newXMLSignature(si, null).sign(ctx);
            return null;
        });
    }
}
