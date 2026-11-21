package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.requetes.LectureEchangesApi;
import ca.vina.nexu.effectifs.domaine.EchangesApi;
import ca.vina.nexu.effectifs.domaine.ModulesPort;
import ca.vina.nexu.partage.securite.JetonsMachine;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.partage.web.GardeMachine;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.function.Function;

/**
 * {@code /api/echanges/v1} — l'API d'échange de l'organisme (26-015-DO, addenda 5, AD-06), réservée aux applications
 * ({@code client_credentials}, portée {@code read}) : l'organisme y lit les quarts planifiés et leurs correctifs, et les
 * relevés de présence validés, pour alimenter lui-même ses systèmes internes. Une session humaine n'y entre pas.
 */
@RestController
@RequestMapping("/api/echanges/v1")
public class ControleurEchangesApi {

    private static final String MODULE = "effectifs";
    private static final int OK = 200;
    private static final int INTERDIT = 403;

    private final GardeMachine garde;
    private final ModulesPort modules;
    private final LectureEchangesApi lecture;

    public ControleurEchangesApi(GardeMachine garde, ModulesPort modules, LectureEchangesApi lecture) {
        this.garde = garde;
        this.modules = modules;
        this.lecture = lecture;
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

    private static Map<String, Object> page(String nom, EchangesApi.Page<?> page) {
        return page.curseur() == null ? Map.of(nom, page.elements()) : Map.of(nom, page.elements(), "curseur", page.curseur());
    }

    /** Garde machine (portée lecture), module actif, puis la lecture ; les erreurs du contexte gardent leur statut. */
    private ResponseEntity<Object> repondre(HttpServletRequest r, Function<String, Object> lire) {
        GardeAcces.Verdict v = garde.exiger(r, JetonsMachine.Portee.LECTURE);
        if (v.refuse()) {
            return v.refus();
        }
        if (!modules.estActive(v.tenantId(), MODULE)) {
            return GardeEffectifs.erreur(INTERDIT, "module '" + MODULE + "' non activé pour cet organisme");
        }
        try {
            return ResponseEntity.ok(lire.apply(v.tenantId()));
        } catch (RuntimeException e) {
            int statut = GardeEffectifs.statutDe(e);
            GardeEffectifs.consigner(statut, e);
            return GardeEffectifs.erreur(statut, e.getMessage() == null ? "erreur inattendue" : e.getMessage());
        }
    }
}
