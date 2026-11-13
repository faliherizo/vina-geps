package ca.vina.nexu.demonstration.infrastructure.web;

import ca.vina.nexu.auth.domaine.Saml;
import ca.vina.nexu.demonstration.domaine.SamlIdpDemo;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** L'IdP SAML de démonstration sur HTTP : métadonnées, page de choix (HTTP-Redirect), formulaire ACS (HTTP-POST). */
@RestController
@RequestMapping("/mock/saml")
public class ControleurMockSaml {

    private final SamlIdpDemo idp;
    private final UrlServeur url;

    public ControleurMockSaml(SamlIdpDemo idp, UrlServeur url) {
        this.idp = idp;
        this.url = url;
    }

    private static ResponseEntity<Object> html(int statut, String page) {
        return ResponseEntity.status(statut).contentType(MediaType.TEXT_HTML).body(page);
    }

    @GetMapping("/metadata")
    public ResponseEntity<Object> metadonnees() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(idp.metadonnees(url.base()));
    }

    /**
     * La chaîne de requête est lue à la main : une demande mal encodée ({@code SAMLRequest=%%%})
     * doit produire la page « Demande illisible » de l'IdP, pas le 400 générique du conteneur qui
     * refuse de décoder les paramètres — c'est ce que l'Express fait en gardant la valeur brute.
     */
    @GetMapping("/sso")
    public ResponseEntity<Object> choisir(HttpServletRequest requete) {
        String demande = parametre(requete, "SAMLRequest");
        try {
            return html(200, SamlIdpDemo.pageChoix(demande, parametre(requete, "RelayState"), Saml.lireAuthnRequest(demande)));
        } catch (IllegalArgumentException e) {
            return html(400, SamlIdpDemo.pageIllisible(e.getMessage()));
        }
    }

    /** La chaîne brute d'abord ; le conteneur ensuite (essais MockMvc) — mais jamais sur une chaîne qu'il refuserait de décoder. */
    private static String parametre(HttpServletRequest requete, String nom) {
        Map<String, String> bruts = parametresBruts(requete.getQueryString());
        if (bruts.containsKey(nom) || requete.getQueryString() != null) {
            return bruts.getOrDefault(nom, "");
        }
        String p = requete.getParameter(nom);
        return p == null ? "" : p;
    }

    public static Map<String, String> parametresBruts(String chaine) {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        for (String paire : (chaine == null ? "" : chaine).split("&")) {
            int i = paire.indexOf('=');
            if (i <= 0) {
                continue;
            }
            m.put(paire.substring(0, i), decoderSansEchouer(paire.substring(i + 1)));
        }
        return m;
    }

    private static String decoderSansEchouer(String brut) {
        try {
            return URLDecoder.decode(brut, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return brut;
        }
    }

    @PostMapping("/sso")
    public ResponseEntity<Object> repondre(@RequestParam Map<String, String> b) {
        Saml.DemandeAuthn d;
        try {
            d = Saml.lireAuthnRequest(b.getOrDefault("SAMLRequest", ""));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of("errors", List.of(e.getMessage())));
        }
        String reponse = idp.reponsePourCompte(d, b.getOrDefault("compte", ""));
        if (reponse == null) {
            reponse = idp.reponseRequester(d);
        }
        return html(200, SamlIdpDemo.formulaireAcs(d.acsUrl(), reponse, b.getOrDefault("RelayState", "")));
    }
}
