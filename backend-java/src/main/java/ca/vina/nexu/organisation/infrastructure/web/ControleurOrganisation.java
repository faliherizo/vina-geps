package ca.vina.nexu.organisation.infrastructure.web;

import ca.vina.nexu.organisation.application.commandes.GestionnaireOrganisation;
import ca.vina.nexu.organisation.application.requetes.LectureOrganisation;
import ca.vina.nexu.organisation.domaine.Organisation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code /api/organisation} — la structure organisationnelle datée (AI-2026-03, exigences 10, 11).
 *
 * <p>Jumeau de {@code backend/src/organisation/routes.ts} : mêmes chemins, mêmes codes, mêmes
 * messages.
 */
@RestController
@RequestMapping("/api/organisation")
@Transactional
public class ControleurOrganisation {

    private final GardeOrganisation garde;
    private final LectureOrganisation lecture;
    private final GestionnaireOrganisation commandes;

    public ControleurOrganisation(GardeOrganisation garde, LectureOrganisation lecture,
                                  GestionnaireOrganisation commandes) {
        this.garde = garde;
        this.lecture = lecture;
        this.commandes = commandes;
    }

    @GetMapping("/operations")
    public ResponseEntity<Object> operations(HttpServletRequest r) {
        return GardeOrganisation.repondre(garde.base(r), 200, t -> Organisation.Operation.codes());
    }

    @GetMapping("/versions")
    public ResponseEntity<Object> versions(HttpServletRequest r) {
        return GardeOrganisation.repondre(garde.base(r), 200,
                t -> VueOrganisation.versions(lecture.versions(t)));
    }

    /**
     * <strong>L'organigramme à une date</strong> (exigence 10).
     *
     * <p>Sans {@code le}, c'est aujourd'hui. Avec, c'est l'organigramme de ce jour-là — y compris
     * les rattachements de l'époque, et c'est toute la différence pour une reddition de comptes.
     */
    @GetMapping("/organigramme")
    public ResponseEntity<Object> organigramme(HttpServletRequest r, @RequestParam(required = false) String le) {
        return GardeOrganisation.repondre(garde.base(r), 200,
                t -> VueOrganisation.organigramme(lecture.organigramme(t, le)));
    }

    @GetMapping("/unites/{uniteId}/historique")
    public ResponseEntity<Object> historique(HttpServletRequest r, @PathVariable String uniteId) {
        return GardeOrganisation.repondre(garde.base(r), 200,
                t -> VueOrganisation.versions(lecture.historiqueUnite(t, uniteId)));
    }

    /** D'où vient cette unité ? La question qu'un écart budgétaire inexpliqué finit par poser. */
    @GetMapping("/versions/{id}/filiation")
    public ResponseEntity<Object> filiation(HttpServletRequest r, @PathVariable String id) {
        return GardeOrganisation.repondre(garde.base(r), 200,
                t -> VueOrganisation.filiation(lecture.filiationVersion(t, id)));
    }

    /** Création, suppression, fusion, fractionnement — chacune a sa forme (exigence 11). */
    @PostMapping("/reorganisations")
    public ResponseEntity<Object> reorganiser(HttpServletRequest r,
                                              @RequestBody(required = false) Map<String, Object> c) {
        return GardeOrganisation.repondre(garde.gouverner(r), 201, t -> VueOrganisation.resultat(
                commandes.appliquer(t, c == null ? Map.of() : c, garde.auteur(r))));
    }
}
