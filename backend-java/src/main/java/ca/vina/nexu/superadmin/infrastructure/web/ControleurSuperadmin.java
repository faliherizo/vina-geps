package ca.vina.nexu.superadmin.infrastructure.web;

import ca.vina.nexu.partage.web.Bord;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.superadmin.application.commandes.GestionnaireAdministration;
import ca.vina.nexu.superadmin.application.requetes.LectureAdministration;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.function.Function;

/**
 * {@code /api/superadmin} — l'administration du système : établissements et leurs administrateurs.
 * Le superadmin seulement. Les refus se disent comme le Node :
 * 401 sans session, 403 « insufficient role », 404 pour ce qui n'existe pas (y compris un message
 * « … not found »), 400 pour une saisie refusée (motifs listés), 409 pour un slug déjà pris.
 */
@RestController
@RequestMapping("/api/superadmin")
public class ControleurSuperadmin {

    private static final Function<RuntimeException, ResponseEntity<Object>> NON_TROUVE_EN_MESSAGE =
            e -> (e instanceof IllegalArgumentException || e instanceof IllegalStateException) && e.getMessage() != null && e.getMessage().contains("not found")
                    && !(e instanceof ca.vina.nexu.partage.erreurs.domaine.RefusException) && !(e instanceof ca.vina.nexu.partage.erreurs.domaine.VerrouException)
                    ? Bord.erreur(404, e.getMessage()) : null;

    private final LectureAdministration lecture;
    private final GestionnaireAdministration gestionnaire;
    private final GardeAcces garde;

    public ControleurSuperadmin(LectureAdministration lecture, GestionnaireAdministration gestionnaire, GardeAcces garde) {
        this.lecture = lecture;
        this.gestionnaire = gestionnaire;
        this.garde = garde;
    }

    private GardeAcces.Verdict superadmin(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerSession(requete);
        if (v.refuse()) {
            return new GardeAcces.Verdict(null, Bord.erreur(401, "unauthenticated"));
        }
        return GardeAcces.contexte(requete).estSuperadmin() ? v : new GardeAcces.Verdict(null, Bord.erreur(403, "insufficient role"));
    }

    private ResponseEntity<Object> repondre(HttpServletRequest requete, int code, Function<String, Object> action) {
        return Bord.repondre(superadmin(requete), code, action, NON_TROUVE_EN_MESSAGE);
    }

    @GetMapping("/tenants")
    public ResponseEntity<Object> etablissements(HttpServletRequest requete) {
        return repondre(requete, 200, t -> lecture.etablissements());
    }

    @PostMapping("/tenants")
    public ResponseEntity<Object> creerEtablissement(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(requete, 201, t -> gestionnaire.creerEtablissement(Bord.corps(c)));
    }

    @PatchMapping("/tenants/{id}")
    public ResponseEntity<Object> modifierEtablissement(HttpServletRequest requete, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(requete, 200, t -> gestionnaire.modifierEtablissement(id, Bord.corps(c)));
    }

    @PostMapping("/tenants/{id}/activate")
    public ResponseEntity<Object> activer(HttpServletRequest requete, @PathVariable String id) {
        return repondre(requete, 200, t -> gestionnaire.activer(id));
    }

    @PostMapping("/tenants/{id}/suspend")
    public ResponseEntity<Object> suspendre(HttpServletRequest requete, @PathVariable String id) {
        return repondre(requete, 200, t -> gestionnaire.changerStatut(id, "suspended"));
    }

    @PostMapping("/tenants/{id}/terminate")
    public ResponseEntity<Object> resilier(HttpServletRequest requete, @PathVariable String id) {
        return repondre(requete, 200, t -> gestionnaire.changerStatut(id, "terminated"));
    }

    @GetMapping("/tenants/{id}/admins")
    public ResponseEntity<Object> administrateurs(HttpServletRequest requete, @PathVariable String id) {
        return repondre(requete, 200, t -> lecture.administrateurs(id));
    }

    @PostMapping("/tenants/{id}/admins")
    public ResponseEntity<Object> creerAdministrateur(HttpServletRequest requete, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(requete, 201, t -> gestionnaire.creerAdministrateur(id, Bord.corps(c)));
    }

    @PostMapping("/tenants/{id}/admins/{userId}/reset-password")
    public ResponseEntity<Object> reinitialiser(HttpServletRequest requete, @PathVariable String id, @PathVariable String userId) {
        return repondre(requete, 200, t -> gestionnaire.reinitialiserMotDePasse(id, userId));
    }

    @PostMapping("/tenants/{id}/admins/{userId}/activate")
    public ResponseEntity<Object> activerAdmin(HttpServletRequest requete, @PathVariable String id, @PathVariable String userId) {
        return repondre(requete, 200, t -> gestionnaire.activerAdministrateur(id, userId, true));
    }

    @PostMapping("/tenants/{id}/admins/{userId}/deactivate")
    public ResponseEntity<Object> desactiverAdmin(HttpServletRequest requete, @PathVariable String id, @PathVariable String userId) {
        return repondre(requete, 200, t -> gestionnaire.activerAdministrateur(id, userId, false));
    }

}
