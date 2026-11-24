package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.commandes.GestionnaireDemandes;
import ca.vina.nexu.effectifs.application.requetes.LectureDemandes;
import ca.vina.nexu.partage.web.GardeAcces;
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
 * {@code /api/effectifs/planification/demandes} — <strong>les demandes opérationnelles</strong> (AO 26-015-DO, addenda 8,
 * M-08). La personne soumet et suit les siennes sous le seul module ; l'encadrement les décide avec la capacité de
 * gestion, et lit la liste de temps supplémentaire du jour.
 */
@RestController
@RequestMapping("/api/effectifs/planification/demandes")
@Transactional
public class ControleurDemandesEffectifs {

    private static final String MODULE = "effectifs_planification";
    private static final int OK = 200;
    private static final int CREE = 201;

    private final GardeEffectifs garde;
    private final LectureDemandes lire;
    private final GestionnaireDemandes agir;

    public ControleurDemandesEffectifs(GardeEffectifs garde, LectureDemandes lire, GestionnaireDemandes agir) {
        this.garde = garde;
        this.lire = lire;
        this.agir = agir;
    }

    private GardeAcces.Verdict membre(HttpServletRequest r) {
        return garde.module(r, MODULE);
    }

    private GardeAcces.Verdict gerer(HttpServletRequest r) {
        return garde.capacite(r, MODULE, GardeEffectifs.Capacite.GERER);
    }

    private static Map<String, Object> corps(Map<String, Object> c) {
        return c == null ? Map.of() : c;
    }

    @GetMapping("/types")
    public ResponseEntity<Object> types(HttpServletRequest r) {
        return garde.repondre(r, membre(r), OK, lire::types);
    }

    @GetMapping("/miennes")
    public ResponseEntity<Object> miennes(HttpServletRequest r) {
        return garde.repondre(r, membre(r), OK, lire::miennes);
    }

    @PostMapping
    public ResponseEntity<Object> soumettre(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, membre(r), CREE, ctx -> agir.soumettre(ctx, corps(c)));
    }

    @PostMapping("/{id}/annuler")
    public ResponseEntity<Object> annuler(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, membre(r), OK, ctx -> agir.annuler(ctx, id));
    }

    @GetMapping
    public ResponseEntity<Object> lister(HttpServletRequest r, @RequestParam(required = false) String statut) {
        return garde.repondre(r, gerer(r), OK, ctx -> lire.lister(ctx, statut));
    }

    @PostMapping("/{id}/decision")
    public ResponseEntity<Object> decider(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), OK, ctx -> agir.decider(ctx, id, corps(c)));
    }

    /** La liste de temps supplémentaire d'un jour, par ancienneté. */
    @GetMapping("/liste-ts")
    public ResponseEntity<Object> listeTempsSupplementaire(HttpServletRequest r, @RequestParam(required = false) String date) {
        return garde.repondre(r, gerer(r), OK, ctx -> lire.listeTempsSupplementaire(ctx, date));
    }
}
