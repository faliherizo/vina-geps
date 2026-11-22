package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.commandes.CascadeOffresQuart;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireParametresOffres;
import ca.vina.nexu.effectifs.application.commandes.ReponsesOffresQuart;
import ca.vina.nexu.effectifs.application.requetes.LectureOffresQuart;
import ca.vina.nexu.effectifs.domaine.Contexte;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
 * {@code /api/effectifs/offres} — l'offre d'un quart par ancienneté et à tour de rôle (AD-07 ; art. 15.10, 16.02,
 * 16.03). L'employé lit ce qui lui est offert et y répond ; l'encadrement règle le mode et les délais, et relit l'ordre
 * de chaque offre. Toute lecture et tout geste rattrapent d'abord les échéances passées.
 *
 * <p>Les gestes de l'employé sont gardés par le socle {@code effectifs} : on lui offre un quart parce qu'il est
 * planifiable, non parce qu'il gère les absences. Le réglage et la trace relèvent du module des absences, dont le
 * comblement ouvre les offres.
 */
@RestController
@RequestMapping("/api/effectifs/offres")
@Transactional
public class ControleurOffresQuart {

    private static final String SOCLE = "effectifs";
    private static final String MODULE = "effectifs_absences";
    private static final int OK = 200;

    /** Ce que le contrôleur appelle : la cascade qui rattrape, les réponses, le réglage, les lectures. */
    public record Gestes(CascadeOffresQuart cascade, ReponsesOffresQuart reponses, GestionnaireParametresOffres parametres, LectureOffresQuart lecture) { }

    private final GardeEffectifs garde;
    private final Gestes gestes;

    public ControleurOffresQuart(GardeEffectifs garde, Gestes gestes) {
        this.garde = garde;
        this.gestes = gestes;
    }

    private Contexte rattrape(Contexte ctx) {
        gestes.cascade().rattraper(ctx);
        return ctx;
    }

    @GetMapping("/miennes")
    public ResponseEntity<Object> miennes(HttpServletRequest r) {
        return garde.repondre(r, garde.module(r, SOCLE), OK, ctx -> gestes.lecture().mesOffres(rattrape(ctx)));
    }

    @PostMapping("/{id}/accepter")
    public ResponseEntity<Object> accepter(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, garde.module(r, SOCLE), OK, ctx -> gestes.reponses().accepter(ctx, id));
    }

    @PostMapping("/{id}/refuser")
    public ResponseEntity<Object> refuser(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, garde.module(r, SOCLE), OK, ctx -> gestes.reponses().refuser(ctx, id));
    }

    @GetMapping
    public ResponseEntity<Object> deLAbsence(HttpServletRequest r, @RequestParam String absenceId) {
        return garde.repondre(r, garde.capacite(r, MODULE, GardeEffectifs.Capacite.GERER), OK, ctx -> gestes.lecture().deLAbsence(rattrape(ctx), absenceId));
    }

    @GetMapping("/parametres")
    public ResponseEntity<Object> parametres(HttpServletRequest r) {
        return garde.repondre(r, garde.capacite(r, MODULE, GardeEffectifs.Capacite.GERER), OK, ctx -> gestes.parametres().lire(ctx));
    }

    @PutMapping("/parametres")
    public ResponseEntity<Object> modifierParametres(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> corps) {
        return garde.repondre(r, garde.capacite(r, MODULE, GardeEffectifs.Capacite.GERER), OK,
                ctx -> gestes.parametres().modifier(ctx, corps == null ? Map.of() : corps));
    }
}
