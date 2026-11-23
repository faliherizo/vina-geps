package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.commandes.GestesEchangesApi;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireAbsencesEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureEchangesApi;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.EchangesApi;
import ca.vina.nexu.effectifs.domaine.ModulesPort;
import ca.vina.nexu.partage.securite.JetonsMachine;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.partage.securite.Role;
import ca.vina.nexu.partage.temps.Iso;
import ca.vina.nexu.partage.web.GardeMachine;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.Map;
import java.util.function.Function;

/**
 * {@code /api/echanges/v1} — l'API d'échange de l'organisme (26-015-DO, addenda 5, AD-06), réservée aux applications
 * ({@code client_credentials}, portées {@code read} et {@code write}) : l'organisme y lit les quarts planifiés et leurs
 * correctifs, et les relevés de présence validés ; il y transmet ses employés, leurs soldes et les absences constatées aux
 * opérations, pour alimenter lui-même ses systèmes internes. Une session humaine n'y entre pas.
 */
@RestController
@RequestMapping("/api/echanges/v1")
public class ControleurEchangesApi {

    private static final String MODULE = "effectifs";
    private static final int OK = 200;
    private static final int INTERDIT = 403;
    private static final int CREE = 201;
    /** L'acteur d'une écriture machine au journal : l'application, jamais une personne. */
    private static final String PREFIXE_ACTEUR = "application:";

    private final GardeMachine garde;
    private final ModulesPort modules;
    private final LectureEchangesApi lecture;
    private final GestesEchangesApi gestes;
    private final Clock horloge;

    /** Les lectures et les écritures de l'API ; l'horloge date les écritures. */
    public record Services(LectureEchangesApi lecture, GestesEchangesApi gestes, Clock horloge) { }

    public ControleurEchangesApi(GardeMachine garde, ModulesPort modules, Services services) {
        this.garde = garde;
        this.modules = modules;
        this.lecture = services.lecture();
        this.gestes = services.gestes();
        this.horloge = services.horloge();
    }

    /** Les quarts planifiés d'une fenêtre ({@code du}, {@code au} inclus, 62 jours au plus), périodes publiées ou closes. */
    @GetMapping("/quarts")
    public ResponseEntity<Object> quarts(HttpServletRequest r, @RequestParam(required = false) String du, @RequestParam(required = false) String au) {
        return repondre(r, t -> Map.of("quarts", lecture.quarts(t, EchangesApi.Fenetre.de(du, au))));
    }

    /** Les correctifs des quarts depuis un curseur : rappeler avec le {@code curseur} rendu pour la suite. */
    @GetMapping("/quarts/modifications")
    public ResponseEntity<Object> modifications(HttpServletRequest r, @RequestParam(required = false) String depuis,
                                                @RequestParam(required = false) String limite) {
        return repondre(r, t -> page("modifications", lecture.modifications(t, EchangesApi.curseur(depuis), EchangesApi.limite(limite))));
    }

    /** Les relevés de présence validés (feuilles approuvées ou payées) depuis un curseur. */
    @GetMapping("/releves-presence")
    public ResponseEntity<Object> releves(HttpServletRequest r, @RequestParam(required = false) String depuis,
                                          @RequestParam(required = false) String limite) {
        return repondre(r, t -> page("releves", lecture.releves(t, EchangesApi.curseur(depuis), EchangesApi.limite(limite))));
    }

    /** Transmettre un employé (création ou mise à jour), clé : le matricule de BD Corpo. Portée {@code write}. */
    @PutMapping("/employes/{matricule}")
    public ResponseEntity<Object> employe(HttpServletRequest r, @PathVariable("matricule") String matricule,
                                          @RequestBody(required = false) Map<String, Object> corps) {
        return ecrire(r, ctx -> {
            GestesEchangesApi.EmployeTransmis t = gestes.employe(ctx, matricule, corps == null ? Map.of() : corps);
            return ResponseEntity.status(t.creee() ? CREE : OK).body(t.ressource());
        });
    }

    /** Transmettre les soldes des banques d'un employé ({@code {"soldes":[{banque, heures, au}]}}). Portée {@code write}. */
    @PutMapping("/employes/{matricule}/soldes")
    public ResponseEntity<Object> poserSoldes(HttpServletRequest r, @PathVariable("matricule") String matricule,
                                              @RequestBody(required = false) Map<String, Object> corps) {
        return ecrire(r, ctx -> ResponseEntity.ok(Map.of("soldes", gestes.soldes(ctx, matricule, corps))));
    }

    /**
     * AD-06c (addenda 5 d) : une absence constatée aux opérations, sur un quart réel ({@code quartId}) ou sur des dates.
     * Acceptée d'office ; ses places deviennent vacantes et leur comblement part aussitôt. {@code 201} à la première
     * transmission, {@code 200} quand la même absence est renvoyée. Portée {@code write}.
     */
    @PostMapping("/employes/{matricule}/absences")
    public ResponseEntity<Object> absence(HttpServletRequest r, @PathVariable("matricule") String matricule,
                                          @RequestBody(required = false) Map<String, Object> corps) {
        return ecrire(r, ctx -> {
            GestionnaireAbsencesEffectifs.Constat c = gestes.absence(ctx, matricule, corps == null ? Map.of() : corps);
            return ResponseEntity.status(c.nouvelle() ? CREE : OK).body(c.resultat().vue());
        });
    }

    /** Relire les soldes tenus pour un employé. Portée {@code read}. */
    @GetMapping("/employes/{matricule}/soldes")
    public ResponseEntity<Object> soldes(HttpServletRequest r, @PathVariable("matricule") String matricule) {
        return repondre(r, t -> Map.of("soldes", gestes.soldesDe(contexte(r, t), matricule)));
    }

    private static Map<String, Object> page(String nom, EchangesApi.Page<?> page) {
        return page.curseur() == null ? Map.of(nom, page.elements()) : Map.of(nom, page.elements(), "curseur", page.curseur());
    }

    /** Garde machine (portée lecture), module actif, puis la lecture ; les erreurs du contexte gardent leur statut. */
    private ResponseEntity<Object> repondre(HttpServletRequest r, Function<String, Object> lire) {
        return executer(r, JetonsMachine.Portee.LECTURE, t -> ResponseEntity.ok(lire.apply(t)));
    }

    /** Garde machine (portée écriture) ; le geste reçoit le contexte de l'application, signé au journal. */
    private ResponseEntity<Object> ecrire(HttpServletRequest r, Function<Contexte, ResponseEntity<Object>> geste) {
        return executer(r, JetonsMachine.Portee.ECRITURE, t -> geste.apply(contexte(r, t)));
    }

    private ResponseEntity<Object> executer(HttpServletRequest r, JetonsMachine.Portee portee, Function<String, ResponseEntity<Object>> geste) {
        GardeAcces.Verdict v = garde.exiger(r, portee);
        if (v.refuse()) {
            return v.refus();
        }
        if (!modules.estActive(v.tenantId(), MODULE)) {
            return GardeEffectifs.erreur(INTERDIT, "module '" + MODULE + "' non activé pour cet organisme");
        }
        try {
            return geste.apply(v.tenantId());
        } catch (RuntimeException e) {
            int statut = GardeEffectifs.statutDe(e);
            GardeEffectifs.consigner(statut, e);
            return GardeEffectifs.erreur(statut, e.getMessage() == null ? "erreur inattendue" : e.getMessage());
        }
    }

    /**
     * Le contexte d'une application de l'organisme : elle agit pour tout l'organisme (rôle administrateur, sans périmètre
     * d'unité), et le journal la nomme par son identifiant client.
     */
    private Contexte contexte(HttpServletRequest r, String tenantId) {
        String client = garde.application(r).map(JetonsMachine.Application::clientId).orElse("inconnue");
        return new Contexte(tenantId, PREFIXE_ACTEUR + client, Role.ADMIN.code(), Iso.de(horloge.instant()));
    }
}
