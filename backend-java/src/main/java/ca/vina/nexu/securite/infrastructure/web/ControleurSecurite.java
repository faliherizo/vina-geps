package ca.vina.nexu.securite.infrastructure.web;

import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.Bord;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.partage.modules.ModulesActives;
import ca.vina.nexu.securite.application.commandes.GestionnaireIncidents;
import ca.vina.nexu.securite.application.dto.FichierDto;
import ca.vina.nexu.securite.application.requetes.LectureIncidents;
import ca.vina.nexu.securite.domaine.Incidents;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.function.Function;

/**
 * {@code /api/securite} — le registre des incidents de confidentialité (Loi 25) : consignation,
 * correction, clôture, vocabulaire, exports CSV et PDF par établissement, et le registre de tous
 * les établissements pour l'administration du système.
 */
@RestController
@RequestMapping("/api/securite")
public class ControleurSecurite {

    private final LectureIncidents lecture;
    private final GestionnaireIncidents gestionnaire;
    private final ModulesActives modules;
    private final GardeAcces garde;

    public ControleurSecurite(LectureIncidents lecture, GestionnaireIncidents gestionnaire, ModulesActives modules, GardeAcces garde) {
        this.lecture = lecture;
        this.gestionnaire = gestionnaire;
        this.modules = modules;
        this.garde = garde;
    }

    /** Session + établissement + module + rôle : le module d'abord, comme le Node ({@code requireModule} avant {@code requireRole}). */
    private GardeAcces.Verdict admin(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        if (v.refuse()) {
            return v;
        }
        if (!modules.estActive(v.tenantId(), Incidents.MODULE)) {
            return new GardeAcces.Verdict(null, Bord.erreur(403, "module '" + Incidents.MODULE + "' not included in plan"));
        }
        return garde.exigerRole(requete, Incidents.ROLES_ADMIN);
    }

    /** Administration du système : le superadmin, ou l'admin sans établissement ({@code requireSystemAdmin}) — sans établissement à résoudre. */
    private static GardeAcces.Verdict systeme(HttpServletRequest requete) {
        ContexteRequete c = GardeAcces.contexte(requete);
        if (c == null) {
            return new GardeAcces.Verdict(null, Bord.erreur(401, "authentication required"));
        }
        boolean systeme = "superadmin".equals(c.role()) || ("admin".equals(c.role()) && c.tenantId() == null);
        return systeme ? new GardeAcces.Verdict(null, null) : new GardeAcces.Verdict(null, Bord.erreur(403, "réservé à l'administration du système"));
    }

    private static ResponseEntity<Object> fichier(GardeAcces.Verdict v, Function<String, FichierDto> action) {
        ResponseEntity<Object> r = Bord.repondre(v, t -> action.apply(t));
        if (!(r.getBody() instanceof FichierDto f)) {
            return r;
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.typeMime()))
                .header("Content-Disposition", "attachment; filename=\"" + f.nom() + "\"").body(f.octets());
    }

    @GetMapping("/incidents/vocabulaire")
    public ResponseEntity<Object> vocabulaire(HttpServletRequest requete) {
        return Bord.repondre(admin(requete), t -> lecture.vocabulaire());
    }

    @GetMapping("/incidents/tous")
    public ResponseEntity<Object> tous(HttpServletRequest requete, @RequestParam(required = false) String statut, @RequestParam(required = false) String gravite) {
        return Bord.repondre(systeme(requete), t -> lecture.lister(null, statut, gravite));
    }

    @GetMapping("/incidents/tous.csv")
    public ResponseEntity<Object> tousCsv(HttpServletRequest requete, @RequestParam(required = false) String statut, @RequestParam(required = false) String gravite) {
        return fichier(systeme(requete), t -> lecture.csv(null, statut, gravite));
    }

    @GetMapping("/incidents/tous.pdf")
    public ResponseEntity<Object> tousPdf(HttpServletRequest requete, @RequestParam(required = false) String statut, @RequestParam(required = false) String gravite) {
        return fichier(systeme(requete), t -> lecture.pdf(null, statut, gravite));
    }

    @GetMapping("/incidents.csv")
    public ResponseEntity<Object> incidentsCsv(HttpServletRequest requete, @RequestParam(required = false) String statut, @RequestParam(required = false) String gravite) {
        return fichier(admin(requete), t -> lecture.csv(t, statut, gravite));
    }

    @GetMapping("/incidents.pdf")
    public ResponseEntity<Object> incidentsPdf(HttpServletRequest requete, @RequestParam(required = false) String statut, @RequestParam(required = false) String gravite) {
        return fichier(admin(requete), t -> lecture.pdf(t, statut, gravite));
    }

    @GetMapping("/incidents")
    public ResponseEntity<Object> lister(HttpServletRequest requete, @RequestParam(required = false) String statut, @RequestParam(required = false) String gravite) {
        return Bord.repondre(admin(requete), t -> lecture.lister(t, statut, gravite));
    }

    @PostMapping("/incidents")
    public ResponseEntity<Object> consigner(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        return Bord.repondre(admin(requete), 201, t -> lecture.vue(gestionnaire.consigner(t, GardeAcces.contexte(requete).userId(), Bord.corps(corps))));
    }

    @PutMapping("/incidents/{id}")
    public ResponseEntity<Object> modifier(HttpServletRequest requete, @PathVariable String id, @RequestBody(required = false) Map<String, Object> corps) {
        return Bord.repondre(admin(requete), t -> lecture.vue(gestionnaire.modifier(t, id, Bord.corps(corps))));
    }

    @PostMapping("/incidents/{id}/clore")
    public ResponseEntity<Object> clore(HttpServletRequest requete, @PathVariable String id) {
        return Bord.repondre(admin(requete), t -> lecture.vue(gestionnaire.clore(t, id)));
    }
}
