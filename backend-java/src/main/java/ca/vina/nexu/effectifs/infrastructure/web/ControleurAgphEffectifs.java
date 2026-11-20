package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.commandes.ClotureAgph;
import ca.vina.nexu.effectifs.application.commandes.DisponibilitesCampagneAgph;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireAgph;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRemplacementsAgph;
import ca.vina.nexu.effectifs.application.commandes.RevisionAgph;
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
import tools.jackson.databind.node.NullNode;

import java.util.Map;

/**
 * {@code /api/effectifs/agph} — l'AGPH et les remplacements long terme (AO 26-015-DO, lot R7), jumeau de
 * {@code backend/src/effectifs/agph/routes.ts}. L'encadrement mène les campagnes et les remplacements
 * ({@code effectifs.manage}, puis le niveau GESTION de l'unité). Choisir son poste à son tour et se manifester pour un
 * remplacement sont des gestes de l'employé pour lui-même : la personne vient de la session, jamais du corps.
 */
@RestController
@RequestMapping("/api/effectifs/agph")
@Transactional
public class ControleurAgphEffectifs {

    private static final String MODULE = "effectifs_agph";

    /** Les cas d'utilisation du lot. */
    public record Gestes(GestionnaireAgph campagnes, ClotureAgph cloture, GestionnaireRemplacementsAgph remplacements, RevisionAgph revision,
                         DisponibilitesCampagneAgph disponibilites) { }

    private final GardeEffectifs garde;
    private final Gestes agir;

    public ControleurAgphEffectifs(GardeEffectifs garde, Gestes agir) {
        this.garde = garde;
        this.agir = agir;
    }

    private static Map<String, Object> corps(Map<String, Object> recu) {
        return recu == null ? Map.of() : recu;
    }

    private GardeAcces.Verdict membre(HttpServletRequest r) {
        return garde.module(r, MODULE);
    }

    private GardeAcces.Verdict gerer(HttpServletRequest r) {
        return garde.capacite(r, MODULE, GardeEffectifs.Capacite.GERER);
    }

    // ── Campagnes ────────────────────────────────────────────────────────────

    @GetMapping("/campagnes")
    public ResponseEntity<Object> campagnes(HttpServletRequest r) {
        return garde.repondre(r, membre(r), 200, ctx -> agir.campagnes().lister(ctx));
    }

    @PostMapping("/campagnes")
    public ResponseEntity<Object> creer(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 201, ctx -> agir.campagnes().creer(ctx, corps(c)));
    }

    @GetMapping("/campagnes/{id}")
    public ResponseEntity<Object> campagne(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, membre(r), 200, ctx -> agir.campagnes().lire(ctx, id));
    }

    @PostMapping("/campagnes/{id}/ouvrir")
    public ResponseEntity<Object> ouvrir(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.campagnes().ouvrir(ctx, id));
    }

    @PostMapping("/campagnes/{id}/demarrer")
    public ResponseEntity<Object> demarrer(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.campagnes().demarrer(ctx, id));
    }

    @PostMapping("/campagnes/{id}/clore")
    public ResponseEntity<Object> clore(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.cloture().clore(ctx, id));
    }

    @GetMapping("/campagnes/{id}/postes-disponibles")
    public ResponseEntity<Object> postesDisponibles(HttpServletRequest r, @PathVariable String id, @RequestParam(required = false) String ressourceId) {
        return garde.repondre(r, membre(r), 200, ctx -> agir.campagnes().postesDisponibles(ctx, id, ressourceId));
    }

    @PostMapping("/campagnes/{id}/choix")
    public ResponseEntity<Object> choisir(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, membre(r), 200, ctx -> agir.campagnes().choisir(ctx, id, corps(c)));
    }

    // ── E-CH-1 : paramètres (budget), semaine type de disponibilités, révision des attributions ─────────────────

    @PutMapping("/campagnes/{id}/parametres")
    public ResponseEntity<Object> parametrer(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.campagnes().parametrer(ctx, id, corps(c)));
    }

    @PostMapping("/campagnes/{id}/reviser")
    public ResponseEntity<Object> reviser(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.revision().reviser(ctx, id));
    }

    /** AD-05 (addenda 7, Q12) : à son tour, la personne renonce à tout poste. */
    @PostMapping("/campagnes/{id}/renoncer")
    public ResponseEntity<Object> renoncer(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, membre(r), 200, ctx -> agir.campagnes().renoncer(ctx, id));
    }

    @PostMapping("/campagnes/{id}/revision/choix")
    public ResponseEntity<Object> reviserChoix(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.revision().reviserChoix(ctx, id, corps(c)));
    }

    @GetMapping("/campagnes/{id}/disponibilites")
    public ResponseEntity<Object> depotsDeLaCampagne(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.disponibilites().depotsDeLaCampagne(ctx, id));
    }

    /** AD-04 (addenda 7, Q11) : la proposition d'optimisation du budget d'heures, en lecture seule. */
    @GetMapping("/campagnes/{id}/optimisation")
    public ResponseEntity<Object> optimisation(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.disponibilites().optimisation(ctx, id));
    }

    /** Sans dépôt, le corps est {@code null}, comme le {@code res.json(null)} du Node. */
    @GetMapping("/campagnes/{id}/disponibilites/moi")
    public ResponseEntity<Object> monDepot(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, membre(r), 200, ctx -> agir.disponibilites().monDepot(ctx, id).<Object>map(d -> d).orElse(NullNode.getInstance()));
    }

    @PutMapping("/campagnes/{id}/disponibilites/moi")
    public ResponseEntity<Object> deposerLesMiennes(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, membre(r), 200, ctx -> agir.disponibilites().deposer(ctx, id, null, corps(c)));
    }

    @PutMapping("/campagnes/{id}/disponibilites/{ressourceId}")
    public ResponseEntity<Object> deposerPour(HttpServletRequest r, @PathVariable String id, @PathVariable String ressourceId,
                                              @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.disponibilites().deposer(ctx, id, ressourceId, corps(c)));
    }

    // ── Remplacements long terme ─────────────────────────────────────────────

    @GetMapping("/remplacements")
    public ResponseEntity<Object> remplacements(HttpServletRequest r, @RequestParam(required = false) String etat) {
        return garde.repondre(r, membre(r), 200, ctx -> agir.remplacements().lister(ctx, etat));
    }

    @PostMapping("/remplacements")
    public ResponseEntity<Object> ouvrirRemplacement(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 201, ctx -> agir.remplacements().ouvrir(ctx, corps(c)));
    }

    @GetMapping("/remplacements/{id}")
    public ResponseEntity<Object> remplacement(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, membre(r), 200, ctx -> agir.remplacements().lire(ctx, id));
    }

    @GetMapping("/remplacements/{id}/interesses")
    public ResponseEntity<Object> interesses(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.remplacements().interesses(ctx, id));
    }

    @PostMapping("/remplacements/{id}/fermer")
    public ResponseEntity<Object> fermer(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.remplacements().fermer(ctx, id));
    }

    @PostMapping("/remplacements/{id}/non-comble")
    public ResponseEntity<Object> laisserNonComble(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.remplacements().laisserNonComble(ctx, id, corps(c)));
    }

    @PostMapping("/remplacements/{id}/interet")
    public ResponseEntity<Object> manifester(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, membre(r), 200, ctx -> agir.remplacements().manifester(ctx, id));
    }

    @DeleteMapping("/remplacements/{id}/interet")
    public ResponseEntity<Object> retirerInteret(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, membre(r), 200, ctx -> agir.remplacements().retirerInteret(ctx, id));
    }
}
