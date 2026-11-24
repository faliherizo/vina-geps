package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.commandes.GestionnaireCongesAnnuels;
import ca.vina.nexu.effectifs.application.requetes.LectureCongesAnnuels;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code /api/effectifs/absences/conges} — <strong>le choix annuel des congés</strong> (AO 26-015-DO, addenda 8, M-02),
 * sous le module des absences : la personne lit ses campagnes et classe ses semaines ; l'encadrement mène la campagne.
 */
@RestController
@RequestMapping("/api/effectifs/absences/conges")
@Transactional
public class ControleurCongesAnnuels {

    private static final String MODULE = "effectifs_absences";
    private static final int OK = 200;
    private static final int CREE = 201;

    private final GardeEffectifs garde;
    private final LectureCongesAnnuels lire;
    private final GestionnaireCongesAnnuels agir;

    public ControleurCongesAnnuels(GardeEffectifs garde, LectureCongesAnnuels lire, GestionnaireCongesAnnuels agir) {
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

    @GetMapping("/miennes")
    public ResponseEntity<Object> miennes(HttpServletRequest r) {
        return garde.repondre(r, membre(r), OK, lire::miennes);
    }

    @PutMapping("/{id}/choix")
    public ResponseEntity<Object> choisir(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, membre(r), OK, ctx -> agir.choisir(ctx, id, corps(c)));
    }

    @GetMapping
    public ResponseEntity<Object> lister(HttpServletRequest r) {
        return garde.repondre(r, gerer(r), OK, lire::lister);
    }

    @PostMapping
    public ResponseEntity<Object> creer(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), CREE, ctx -> agir.creer(ctx, corps(c)));
    }

    @PostMapping("/{id}/etat")
    public ResponseEntity<Object> etat(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), OK, ctx -> agir.changerEtat(ctx, id, corps(c)));
    }

    @PostMapping("/{id}/octroyer")
    public ResponseEntity<Object> octroyer(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, gerer(r), OK, ctx -> agir.octroyer(ctx, id));
    }

    @GetMapping("/{id}/resultats")
    public ResponseEntity<Object> resultats(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, gerer(r), OK, ctx -> lire.resultats(ctx, id));
    }
}
