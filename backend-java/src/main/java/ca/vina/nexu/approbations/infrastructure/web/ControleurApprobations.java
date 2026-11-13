package ca.vina.nexu.approbations.infrastructure.web;

import ca.vina.nexu.approbations.application.commandes.GestionnaireApprobations;
import ca.vina.nexu.approbations.application.requetes.LectureApprobations;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.Bord;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** {@code /api/approvals} — règles, demandes, étapes, délégations et relances du moteur d'approbation. */
@RestController
@RequestMapping("/api/approvals")
public class ControleurApprobations {

    private final LectureApprobations lecture;
    private final GestionnaireApprobations gestionnaire;
    private final GardeAcces garde;

    public ControleurApprobations(LectureApprobations lecture, GestionnaireApprobations gestionnaire, GardeAcces garde) {
        this.lecture = lecture;
        this.gestionnaire = gestionnaire;
        this.garde = garde;
    }

    @GetMapping("/rules")
    public ResponseEntity<Object> regles(HttpServletRequest requete, @RequestParam(required = false) String documentType) {
        return Bord.repondre(garde.exigerTenant(requete), t -> lecture.regles(t, documentType));
    }

    @PostMapping("/rules")
    public ResponseEntity<Object> creerRegle(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> c) {
        return Bord.repondre(garde.exigerRole(requete, "admin", "manager"), 201, t -> gestionnaire.creerRegle(t, Bord.corps(c)));
    }

    @GetMapping("/requests")
    public ResponseEntity<Object> demandes(HttpServletRequest requete, @RequestParam(required = false) String status) {
        return Bord.repondre(garde.exigerTenant(requete), t -> lecture.demandes(t, status));
    }

    @PostMapping("/requests")
    public ResponseEntity<Object> creerDemande(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> c) {
        return Bord.repondre(garde.exigerRole(requete, "admin", "manager"), 201, t -> gestionnaire.creerDemande(t, GardeAcces.contexte(requete).userId(), Bord.corps(c)));
    }

    @GetMapping("/requests/{id}")
    public ResponseEntity<Object> demande(HttpServletRequest requete, @PathVariable String id) {
        return Bord.repondre(garde.exigerTenant(requete), t -> lecture.demande(t, id));
    }

    @PostMapping("/requests/{id}/additional-approver")
    public ResponseEntity<Object> approbateurAdditionnel(HttpServletRequest requete, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return Bord.repondre(garde.exigerRole(requete, "admin", "manager"), t -> gestionnaire.ajouterApprobateur(t, id, Bord.corps(c)));
    }

    @PostMapping("/steps/{id}/decide")
    public ResponseEntity<Object> decider(HttpServletRequest requete, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        ContexteRequete ctx = GardeAcces.contexte(requete);
        return Bord.repondre(garde.exigerTenant(requete), t -> gestionnaire.decider(t, id, Bord.corps(c), ctx.userId(), ctx.role()));
    }

    @GetMapping("/delegations")
    public ResponseEntity<Object> delegations(HttpServletRequest requete, @RequestParam(required = false) String mine) {
        return Bord.repondre(garde.exigerTenant(requete), t -> lecture.delegations(t, GardeAcces.contexte(requete).userId(), mine));
    }

    @PostMapping("/delegations")
    public ResponseEntity<Object> deleguer(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> c) {
        return Bord.repondre(garde.exigerTenant(requete), 201, t -> gestionnaire.deleguer(t, GardeAcces.contexte(requete).userId(), Bord.corps(c)));
    }

    @GetMapping("/by-document")
    public ResponseEntity<Object> parDocument(HttpServletRequest requete, @RequestParam(required = false) String documentType, @RequestParam(required = false) String documentId) {
        return Bord.repondre(garde.exigerTenant(requete), t -> lecture.parDocument(t, documentType, documentId));
    }

    @PostMapping("/relaunch")
    public ResponseEntity<Object> relancer(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> c) {
        return Bord.repondre(garde.exigerRole(requete, "admin", "manager", "director"), t -> gestionnaire.relancer(t, Bord.corps(c)));
    }
}
