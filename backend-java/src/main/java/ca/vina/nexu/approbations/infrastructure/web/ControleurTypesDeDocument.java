package ca.vina.nexu.approbations.infrastructure.web;

import ca.vina.nexu.approbations.application.commandes.GestionnaireTypesDeDocument;
import ca.vina.nexu.approbations.application.requetes.LectureTypesDeDocument;
import ca.vina.nexu.partage.web.Bord;
import ca.vina.nexu.partage.web.GardeAcces;
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

import java.util.List;
import java.util.Map;

/**
 * {@code /api/approvals} — le catalogue des types de document (lisible par tous dans l'organisme,
 * modifiable par l'administration seule) et la suggestion de délégataires (réservée à qui peut
 * lui-même approuver). Jumeau de {@code backend/src/approvals/routes.ts}.
 */
@RestController
@RequestMapping("/api/approvals")
@Transactional
public class ControleurTypesDeDocument {

    private static final String TOUS = "1";
    private static final String ADMIN = "admin";
    private static final String INTROUVABLE = "document type not found";

    private final GardeAcces garde;
    private final GardeApprobateurs approbateurs;
    private final LectureTypesDeDocument lecture;
    private final GestionnaireTypesDeDocument gestionnaire;

    public ControleurTypesDeDocument(GardeAcces garde, GardeApprobateurs approbateurs, LectureTypesDeDocument lecture,
                                     GestionnaireTypesDeDocument gestionnaire) {
        this.garde = garde;
        this.approbateurs = approbateurs;
        this.lecture = lecture;
        this.gestionnaire = gestionnaire;
    }

    @GetMapping("/document-types")
    public ResponseEntity<Object> types(HttpServletRequest r, @RequestParam(required = false) String tous) {
        return Bord.repondre(garde.exigerTenant(r), t -> lecture.types(t, !TOUS.equals(tous)));
    }

    @PostMapping("/document-types")
    public ResponseEntity<Object> creer(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> corps) {
        return Bord.repondre(garde.exigerRole(r, ADMIN), 201, t -> gestionnaire.creer(t, Bord.corps(corps)));
    }

    @PutMapping("/document-types/{id}")
    public ResponseEntity<Object> modifier(HttpServletRequest r, @PathVariable String id,
                                           @RequestBody(required = false) Map<String, Object> corps) {
        return Bord.repondre(garde.exigerRole(r, ADMIN), t -> gestionnaire.modifier(t, id, Bord.corps(corps)));
    }

    @DeleteMapping("/document-types/{id}")
    public ResponseEntity<Object> supprimer(HttpServletRequest r, @PathVariable String id) {
        ResponseEntity<Object> reponse = Bord.repondre(garde.exigerRole(r, ADMIN), 204,
                t -> gestionnaire.supprimer(t, id) ? null : List.of());
        return reponse.getBody() instanceof List<?> ? Bord.erreur(404, INTROUVABLE) : reponse;
    }

    @GetMapping("/delegataires")
    public ResponseEntity<Object> delegataires(HttpServletRequest r, @RequestParam(required = false) String q) {
        return Bord.repondre(approbateurs.approbateur(r), t -> lecture.delegataires(t, q, GardeAcces.contexte(r).userId()));
    }
}
