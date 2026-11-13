package ca.vina.nexu.habilitations.infrastructure.web;

import ca.vina.nexu.habilitations.application.commandes.GestionnaireHabilitations;
import ca.vina.nexu.habilitations.application.requetes.LectureHabilitations;
import ca.vina.nexu.habilitations.domaine.Habilitations;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code /api/habilitations} — périodes d'effectivité et consultation centralisée des droits
 * (AI-2026-03, exigences 84, 85, 86, 90, 91).
 *
 * <p>Jumeau de {@code backend/src/habilitations/routes.ts} : mêmes chemins, mêmes codes, mêmes
 * messages.
 */
@RestController
@RequestMapping("/api/habilitations")
@Transactional
public class ControleurHabilitations {

    private final GardeHabilitations garde;
    private final LectureHabilitations lecture;
    private final GestionnaireHabilitations commandes;

    public ControleurHabilitations(GardeHabilitations garde, LectureHabilitations lecture,
                                   GestionnaireHabilitations commandes) {
        this.garde = garde;
        this.lecture = lecture;
        this.commandes = commandes;
    }

    private static Map<String, Object> corps(Map<String, Object> recu) {
        return recu == null ? Map.of() : recu;
    }

    private static String texte(String v) {
        return v == null ? "" : v.trim();
    }

    @GetMapping("/permissions")
    public ResponseEntity<Object> permissions(HttpServletRequest r) {
        return GardeHabilitations.repondre(garde.base(r), 200, t -> Habilitations.Permission.codes());
    }

    @GetMapping("/types-objet")
    public ResponseEntity<Object> typesObjet(HttpServletRequest r) {
        return GardeHabilitations.repondre(garde.base(r), 200, t -> Habilitations.TypeObjet.codes());
    }

    // ── Les objets protégés ─────────────────────────────────────────────────

    @GetMapping("/objets")
    public ResponseEntity<Object> objets(HttpServletRequest r) {
        return GardeHabilitations.repondre(garde.base(r), 200, t -> VueHabilitations.objets(lecture.objets(t)));
    }

    @PutMapping("/objets")
    public ResponseEntity<Object> declarerObjet(HttpServletRequest r,
                                                @RequestBody(required = false) Map<String, Object> c) {
        return GardeHabilitations.repondre(garde.administrer(r), 200,
                t -> VueHabilitations.objet(commandes.declarerObjet(t, corps(c))));
    }

    // ── Les octrois, à effet borné ──────────────────────────────────────────

    @GetMapping("/octrois")
    public ResponseEntity<Object> octrois(HttpServletRequest r) {
        return GardeHabilitations.repondre(garde.base(r), 200, t -> VueHabilitations.octrois(lecture.octrois(t)));
    }

    /** Période d'effectivité par profil <strong>et</strong> par utilisateur (exigence 84). */
    @PostMapping("/octrois")
    public ResponseEntity<Object> octroyer(HttpServletRequest r,
                                           @RequestBody(required = false) Map<String, Object> c) {
        return GardeHabilitations.repondre(garde.administrer(r), 201,
                t -> VueHabilitations.octroi(commandes.octroyer(t, corps(c), garde.auteur(r))));
    }

    @DeleteMapping("/octrois")
    public ResponseEntity<Object> revoquer(HttpServletRequest r,
                                           @RequestParam(required = false) String sujetType,
                                           @RequestParam(required = false) String sujetId,
                                           @RequestParam(required = false) String objetCode,
                                           @RequestParam(required = false) String effectifDu) {
        return GardeHabilitations.repondre(garde.administrer(r), 200, t -> commandes.revoquer(t,
                texte(sujetType), texte(sujetId), texte(objetCode), texte(effectifDu)));
    }

    // ── Les profils d'un utilisateur ────────────────────────────────────────

    @GetMapping("/utilisateurs/{id}/profils")
    public ResponseEntity<Object> profils(HttpServletRequest r, @PathVariable String id) {
        return GardeHabilitations.repondre(garde.base(r), 200, t -> lecture.profilsDe(t, id));
    }

    @PostMapping("/utilisateurs/{id}/profils")
    public ResponseEntity<Object> rattacher(HttpServletRequest r, @PathVariable String id,
                                            @RequestBody(required = false) Map<String, Object> c) {
        Object profil = corps(c).get("profilId");
        return GardeHabilitations.repondre(garde.administrer(r), 201,
                t -> commandes.rattacherProfil(t, id, profil == null ? "" : profil.toString()));
    }

    @DeleteMapping("/utilisateurs/{id}/profils/{profilId}")
    public ResponseEntity<Object> detacher(HttpServletRequest r, @PathVariable String id,
                                           @PathVariable String profilId) {
        return GardeHabilitations.repondre(garde.administrer(r), 200,
                t -> commandes.detacherProfil(t, id, profilId));
    }

    // ── Les trois lectures que l'avis demande ───────────────────────────────

    /** La permission d'une personne sur un objet, à une date (exigences 85, 86). */
    @GetMapping("/utilisateurs/{id}/permission")
    public ResponseEntity<Object> permission(HttpServletRequest r, @PathVariable String id,
                                             @RequestParam(required = false) String objetCode,
                                             @RequestParam(required = false) String le) {
        return GardeHabilitations.repondre(garde.base(r), 200,
                t -> VueHabilitations.resolution(lecture.permission(t, id, texte(objetCode), le)));
    }

    /** <strong>Qui peut accéder à cet objet ?</strong> — la question qu'un auditeur pose (exigence 90). */
    @GetMapping("/objets/{code}/droits")
    public ResponseEntity<Object> droits(HttpServletRequest r, @PathVariable String code,
                                         @RequestParam(required = false) String le) {
        return GardeHabilitations.repondre(garde.base(r), 200,
                t -> VueHabilitations.droits(lecture.droits(t, code, le)));
    }

    /** <strong>Tous les droits d'une personne, dans une seule vue</strong>, avec les échéances (exigence 91). */
    @GetMapping("/utilisateurs/{id}/fiche")
    public ResponseEntity<Object> fiche(HttpServletRequest r, @PathVariable String id,
                                        @RequestParam(required = false) String le) {
        return GardeHabilitations.repondre(garde.base(r), 200,
                t -> VueHabilitations.fiche(lecture.fiche(t, id, le)));
    }
}
