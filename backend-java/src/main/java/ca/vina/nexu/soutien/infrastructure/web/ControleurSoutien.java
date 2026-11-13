package ca.vina.nexu.soutien.infrastructure.web;

import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.Bord;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.soutien.application.commandes.GestionnaireSoutien;
import ca.vina.nexu.soutien.application.requetes.LectureSoutien;
import ca.vina.nexu.soutien.domaine.Soutien;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * {@code /api/soutien} — billets, réponses et pièces, satisfaction, dispositif d'avis, maintenances
 * annoncées et disponibilité (grille de l'AO CSSDC). Le superadmin est le soutien ; l'auteur ne voit
 * que ses billets. Un refus s'exprime comme au Node : 404 introuvable, 403 réservé, 409 transition.
 */
@RestController
@RequestMapping("/api/soutien")
public class ControleurSoutien {

    private final LectureSoutien lecture;
    private final GestionnaireSoutien gestionnaire;
    private final GardeAcces garde;

    public ControleurSoutien(LectureSoutien lecture, GestionnaireSoutien gestionnaire, GardeAcces garde) {
        this.lecture = lecture;
        this.gestionnaire = gestionnaire;
        this.garde = garde;
    }

    private static Soutien.Acteur acteur(HttpServletRequest requete) {
        ContexteRequete c = GardeAcces.contexte(requete);
        return new Soutien.Acteur(c.userId(), c.username() == null ? "inconnu" : c.username(), c.tenantId(), c.estSuperadmin());
    }

    /** Le superadmin seulement — sans exiger d'établissement, il n'en a pas. */
    private GardeAcces.Verdict soutien(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerSession(requete);
        if (v.refuse()) {
            return v;
        }
        return GardeAcces.contexte(requete).estSuperadmin() ? v : new GardeAcces.Verdict(null, Bord.erreur(403, "forbidden"));
    }

    /** Publique : sans session, la politique standard ; avec, celle de l'organisme de l'appelant. */
    @GetMapping("/politique")
    public ResponseEntity<Object> politique(HttpServletRequest requete) {
        ContexteRequete c = GardeAcces.contexte(requete);
        return ResponseEntity.ok(lecture.politique(c == null ? null : c.tenantId()));
    }

    @GetMapping("/tickets")
    public ResponseEntity<Object> tickets(HttpServletRequest requete, @RequestParam(required = false) String statut) {
        return Bord.repondre(garde.exigerSession(requete), t -> lecture.tickets(acteur(requete), statut));
    }

    /** Le soutien lit les billets par établissement : qui a envoyé quoi, où sont les retards. */
    @GetMapping("/tickets/par-etablissement")
    public ResponseEntity<Object> ticketsParEtablissement(HttpServletRequest requete) {
        return Bord.repondre(soutien(requete), t -> lecture.ticketsParEtablissement(acteur(requete)));
    }

    /** Le résumé du soutien — billets ouverts, nouveaux, en retard — pour le badge du superadmin. */
    @GetMapping("/tickets/resume")
    public ResponseEntity<Object> resume(HttpServletRequest requete) {
        return Bord.repondre(soutien(requete), t -> lecture.resume());
    }

    @PostMapping("/tickets")
    public ResponseEntity<Object> ouvrir(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> c) {
        return Bord.repondre(garde.exigerSession(requete), 201, t -> gestionnaire.ouvrir(acteur(requete), Bord.corps(c)));
    }

    @GetMapping("/tickets/{id}")
    public ResponseEntity<Object> ticket(HttpServletRequest requete, @PathVariable String id) {
        return Bord.repondre(garde.exigerSession(requete), t -> lecture.ticket(acteur(requete), id));
    }

    @PostMapping("/tickets/{id}/messages")
    public ResponseEntity<Object> message(HttpServletRequest requete, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return Bord.repondre(garde.exigerSession(requete), 201, t -> gestionnaire.repondre(acteur(requete), id, Bord.corps(c)));
    }

    /** Une pièce se télécharge telle quelle, sous son nom ; jamais rendue dans la page (nosniff, attachment). */
    @GetMapping("/tickets/{id}/pieces/{pieceId}")
    public ResponseEntity<Object> piece(HttpServletRequest requete, @PathVariable String id, @PathVariable String pieceId) {
        GardeAcces.Verdict v = garde.exigerSession(requete);
        if (v.refuse()) {
            return v.refus();
        }
        ResponseEntity<Object> r = Bord.repondre(v, t -> lecture.piece(acteur(requete), id, pieceId));
        if (!(r.getBody() instanceof Soutien.Contenu p)) {
            return r;
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, p.typeMime()).header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + URLEncoder.encode(p.nom(), StandardCharsets.UTF_8).replace("+", "%20") + "\"")
                .body(p.contenu());
    }

    @PostMapping("/tickets/{id}/satisfaction")
    public ResponseEntity<Object> satisfaction(HttpServletRequest requete, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return Bord.repondre(garde.exigerSession(requete), t -> gestionnaire.noterSatisfaction(acteur(requete), id, Bord.corps(c)));
    }

    /** Le dispositif d'avis au soutien : qui reçoit, par quel transport — et le courriel du compte, réglable ici. */
    @GetMapping("/notification")
    public ResponseEntity<Object> notification(HttpServletRequest requete) {
        return Bord.repondre(soutien(requete), t -> lecture.notification(acteur(requete).id()));
    }

    @PutMapping("/notification")
    public ResponseEntity<Object> reglerNotification(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> c) {
        return Bord.repondre(soutien(requete), t -> gestionnaire.reglerCourriel(acteur(requete).id(), Bord.corps(c)));
    }

    /** Le soutien fait avancer ; l'auteur ne peut que fermer un billet résolu (le dépôt le vérifie). */
    @PostMapping("/tickets/{id}/statut")
    public ResponseEntity<Object> statut(HttpServletRequest requete, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return Bord.repondre(garde.exigerSession(requete), t -> gestionnaire.changerStatut(acteur(requete), id, Bord.corps(c)));
    }

    @GetMapping("/maintenances")
    public ResponseEntity<Object> maintenances(HttpServletRequest requete) {
        return Bord.repondre(garde.exigerSession(requete), t -> lecture.maintenances());
    }

    @PostMapping("/maintenances")
    public ResponseEntity<Object> planifier(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> c) {
        return Bord.repondre(soutien(requete), 201, t -> gestionnaire.planifier(acteur(requete).id(), Bord.corps(c)));
    }

    @PostMapping("/maintenances/{id}/annuler")
    public ResponseEntity<Object> annuler(HttpServletRequest requete, @PathVariable String id) {
        return Bord.repondre(soutien(requete), t -> gestionnaire.annuler(id));
    }

    @GetMapping("/disponibilite")
    public ResponseEntity<Object> disponibilite(HttpServletRequest requete, @RequestParam(required = false) String mois) {
        return Bord.repondre(soutien(requete), t -> lecture.disponibilite(mois));
    }
}
