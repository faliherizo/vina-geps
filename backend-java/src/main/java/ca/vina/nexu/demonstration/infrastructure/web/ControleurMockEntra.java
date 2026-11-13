package ca.vina.nexu.demonstration.infrastructure.web;

import ca.vina.nexu.demonstration.domaine.DonneesDemo;
import ca.vina.nexu.demonstration.domaine.EntraDemo;
import ca.vina.nexu.demonstration.domaine.PageHtml;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

/**
 * Le Microsoft Entra ID de démonstration sur HTTP — hors authentification : c'est un tiers, pas
 * un écran de la plateforme. Formulaires {@code application/x-www-form-urlencoded} pour la page
 * de connexion et l'échange du code, comme le vrai.
 */
@RestController
@RequestMapping("/mock/entra")
public class ControleurMockEntra {

    private final EntraDemo entra;
    private final UrlServeur url;

    public ControleurMockEntra(EntraDemo entra, UrlServeur url) {
        this.entra = entra;
        this.url = url;
    }

    private static ResponseEntity<Object> rediriger(String cible) {
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, cible).build();
    }

    private static ResponseEntity<Object> html(int statut, String page) {
        return ResponseEntity.status(statut).contentType(MediaType.TEXT_HTML).body(page);
    }

    @GetMapping("/{tenant}/v2.0/.well-known/openid-configuration")
    public ResponseEntity<Object> decouverte(@PathVariable("tenant") String tenant) {
        return ResponseEntity.ok(EntraDemo.documentDecouverte(url.base(), tenant));
    }

    @GetMapping("/{tenant}/discovery/v2.0/keys")
    public ResponseEntity<Object> cles(@PathVariable("tenant") String tenant) {
        return ResponseEntity.ok(entra.jwks());
    }

    @GetMapping("/{tenant}/oauth2/v2.0/authorize")
    public ResponseEntity<Object> autoriser(@PathVariable("tenant") String tenant, @RequestParam Map<String, String> d) {
        EntraDemo.Erreur e = EntraDemo.validerDemande(d);
        String redirect = d.get("redirect_uri");
        if (e != null) {
            // Sans `redirect_uri` fiable, Entra affiche l'erreur ; sinon il la renvoie à l'application.
            if ("unauthorized_client".equals(e.erreur()) || redirect == null || redirect.isEmpty()) {
                return html(400, EntraDemo.pageErreur(e));
            }
            return rediriger(EntraDemo.redirigerAvecErreur(redirect, d.get("state"), e.erreur(), e.description()));
        }
        boolean silencieux = "none".equals(d.get("prompt"));
        DonneesDemo.CompteEntra compte = silencieux ? DonneesDemo.compte(d.get("login_hint")) : null;
        if (silencieux && compte == null) {
            return rediriger(EntraDemo.redirigerAvecErreur(redirect, d.get("state"), "login_required", "AADSTS50058: A silent sign-in request was sent but no user is signed in."));
        }
        if (compte != null) {
            return rediriger(entra.retourAvecCode(d, compte));
        }
        return html(200, entra.pageChoix(tenant, d));
    }

    @PostMapping("/{tenant}/login")
    public ResponseEntity<Object> connexion(@PathVariable("tenant") String tenant, @RequestParam Map<String, String> d) {
        EntraDemo.Erreur e = EntraDemo.validerDemande(d);
        if (e != null) {
            return ResponseEntity.status(400).body(Map.of("error", e.erreur(), "error_description", e.description()));
        }
        DonneesDemo.CompteEntra compte = DonneesDemo.compte(d.get("compte"));
        if (compte == null) {
            return rediriger(EntraDemo.redirigerAvecErreur(d.get("redirect_uri"), d.get("state"), "access_denied", "AADSTS50020: User account does not exist in tenant."));
        }
        return rediriger(entra.retourAvecCode(d, compte));
    }

    @PostMapping("/{tenant}/oauth2/v2.0/token")
    public ResponseEntity<Object> jeton(@PathVariable("tenant") String tenant, @RequestParam Map<String, String> d) {
        EntraDemo.Reponse r = entra.echangerCode(d, url.base(), tenant);
        return ResponseEntity.status(r.statut()).body(r.corps());
    }

    @GetMapping("/{tenant}/oauth2/v2.0/logout")
    public ResponseEntity<Object> deconnexion(@PathVariable("tenant") String tenant, @RequestParam(name = "post_logout_redirect_uri", required = false) String cible) {
        if (cible != null && !cible.isEmpty()) {
            return rediriger(cible);
        }
        return html(200, PageHtml.page("Déconnexion — Entra ID (démonstration)", "<h1>Vous êtes déconnecté</h1>"));
    }
}
