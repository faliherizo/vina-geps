package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.commandes.GestionnaireEquipages;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePeriodes;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePlaces;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePostes;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireQuotas;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRessources;
import ca.vina.nexu.effectifs.application.requetes.LectureEquipages;
import ca.vina.nexu.effectifs.application.requetes.LecturePeriodes;
import ca.vina.nexu.effectifs.application.requetes.LecturePostes;
import ca.vina.nexu.effectifs.application.requetes.LectureQuotas;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code /api/effectifs/planification} — la planification des effectifs (AO 26-015-DO, lot R3), jumeau de
 * {@code backend/src/effectifs/planification/routes.ts} : mêmes chemins, mêmes codes, mêmes messages. Le module
 * {@code effectifs_planification} et la capacité {@code effectifs.manage} ouvrent la porte ; le
 * <strong>périmètre</strong> de l'unité décide de ce qu'on voit et de ce qu'on modifie.
 */
@RestController
@RequestMapping("/api/effectifs/planification")
@Transactional
public class ControleurPlanificationEffectifs {

    private static final String MODULE = "effectifs_planification";

    /** Les lectures du lot. */
    public record Lectures(LectureRessources ressources, LecturePeriodes periodes, LecturePostes postes, LectureEquipages equipages, LectureQuotas quotas) { }

    /** Les écritures du lot. */
    public record Gestes(GestionnaireRessources ressources, GestionnairePeriodes periodes, GestionnairePostes postes, GestionnaireEquipages equipages,
                         GestionnairePlaces places, GestionnaireQuotas quotas) { }

    private final GardeEffectifs garde;
    private final Lectures lire;
    private final Gestes agir;

    public ControleurPlanificationEffectifs(GardeEffectifs garde, Lectures lire, Gestes agir) {
        this.garde = garde;
        this.lire = lire;
        this.agir = agir;
    }

    private static Map<String, Object> corps(Map<String, Object> recu) {
        return recu == null ? Map.of() : recu;
    }

    private GardeAcces.Verdict gerer(HttpServletRequest r) {
        return garde.capacite(r, MODULE, GardeEffectifs.Capacite.GERER);
    }

    // ── Ressources ───────────────────────────────────────────────────────────

    @GetMapping("/ressources")
    public ResponseEntity<Object> ressources(HttpServletRequest r, @RequestParam(required = false) String uniteCode,
                                             @RequestParam(required = false) String corpsEmploiCode) {
        return garde.repondre(r, gerer(r), 200, ctx -> lire.ressources().lister(ctx, uniteCode, corpsEmploiCode));
    }

    @PostMapping("/ressources")
    public ResponseEntity<Object> creerRessource(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 201, ctx -> agir.ressources().creer(ctx, corps(c)));
    }

    @PutMapping("/ressources/{id}")
    public ResponseEntity<Object> modifierRessource(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.ressources().modifier(ctx, id, corps(c)));
    }

    // ── Périodes ─────────────────────────────────────────────────────────────

    @GetMapping("/periodes")
    public ResponseEntity<Object> periodes(HttpServletRequest r) {
        return garde.repondre(r, gerer(r), 200, ctx -> lire.periodes().lister(ctx));
    }

    @PostMapping("/periodes")
    public ResponseEntity<Object> creerPeriode(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 201, ctx -> agir.periodes().creer(ctx, corps(c)));
    }

    @PostMapping("/periodes/{id}/etat")
    public ResponseEntity<Object> changerEtat(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.periodes().changerEtat(ctx, id, corps(c)));
    }

    @PostMapping("/periodes/{id}/generer")
    public ResponseEntity<Object> generer(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.equipages().genererDepuisPostes(ctx, id));
    }

    @GetMapping("/periodes/{id}/equipages")
    public ResponseEntity<Object> equipages(HttpServletRequest r, @PathVariable String id, @RequestParam(required = false) String date,
                                            @RequestParam(required = false) String statut) {
        return garde.repondre(r, gerer(r), 200, ctx -> lire.equipages().lister(ctx, id, date, statut).stream().map(LectureEquipages::vue).toList());
    }

    @GetMapping("/periodes/{id}/comblement")
    public ResponseEntity<Object> comblement(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, gerer(r), 200, ctx -> lire.equipages().comblement(ctx, id));
    }

    // ── Postes ───────────────────────────────────────────────────────────────

    @GetMapping("/postes")
    public ResponseEntity<Object> postes(HttpServletRequest r, @RequestParam(required = false) String etat) {
        return garde.repondre(r, gerer(r), 200, ctx -> lire.postes().lister(ctx, etat, null));
    }

    @PostMapping("/postes")
    public ResponseEntity<Object> creerPoste(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 201, ctx -> agir.postes().creer(ctx, corps(c)));
    }

    @PostMapping("/postes/lot")
    public ResponseEntity<Object> modifierPostesEnLot(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.postes().modifierEnLot(ctx, corps(c)));
    }

    @PostMapping("/postes/publier")
    public ResponseEntity<Object> publierPostes(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.postes().publier(ctx, corps(c).get("ids")));
    }

    @PutMapping("/postes/{id}")
    public ResponseEntity<Object> modifierPoste(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.postes().modifier(ctx, id, corps(c)));
    }

    // ── Équipages et places ──────────────────────────────────────────────────

    @PostMapping("/equipages")
    public ResponseEntity<Object> creerEquipage(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 201, ctx -> LectureEquipages.vue(agir.equipages().creer(ctx, corps(c))));
    }

    @PostMapping("/equipages/lot")
    public ResponseEntity<Object> modifierEnLot(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.equipages().modifierEnLot(ctx, corps(c)).stream().map(LectureEquipages::vue).toList());
    }

    @PatchMapping("/equipages/{id}")
    public ResponseEntity<Object> modifierEquipage(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 200, ctx -> LectureEquipages.vue(agir.equipages().modifier(ctx, id, corps(c))));
    }

    @PostMapping("/places/{id}/attribuer")
    public ResponseEntity<Object> attribuer(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.places().attribuer(ctx, id, corps(c), null));
    }

    @PostMapping("/places/{id}/liberer")
    public ResponseEntity<Object> liberer(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 200, ctx -> LectureEquipages.vue(agir.places().liberer(ctx, id, corps(c))));
    }

    // ── Quotas ───────────────────────────────────────────────────────────────

    @GetMapping("/quotas")
    public ResponseEntity<Object> quotas(HttpServletRequest r) {
        return garde.repondre(r, gerer(r), 200, ctx -> lire.quotas().lister(ctx).stream().map(q -> LectureEquipages.vue(q, ctx.maintenant())).toList());
    }

    @PostMapping("/quotas")
    public ResponseEntity<Object> definirQuota(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 201, ctx -> LectureEquipages.vue(agir.quotas().definir(ctx, corps(c)), ctx.maintenant()));
    }

    @PostMapping("/quotas/{id}/surcharges")
    public ResponseEntity<Object> surcharger(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 201, ctx -> LectureEquipages.vue(agir.quotas().surcharger(ctx, id, corps(c)), ctx.maintenant()));
    }
}
