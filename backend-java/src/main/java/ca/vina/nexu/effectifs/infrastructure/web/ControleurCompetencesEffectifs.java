package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.commandes.GestionnaireCompetences;
import ca.vina.nexu.effectifs.application.requetes.LectureCompetences;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code /api/effectifs/planification} — <strong>les compétences et leurs échéances</strong> (AO 26-015-DO, addenda 8, M-04),
 * sous la même porte que les ressources : le module {@code effectifs_planification} et la capacité de gestion. Le
 * périmètre de l'unité décide ensuite qui lit et qui pose.
 */
@RestController
@RequestMapping("/api/effectifs/planification")
@Transactional
public class ControleurCompetencesEffectifs {

    private static final String MODULE = "effectifs_planification";
    private static final int OK = 200;

    private final GardeEffectifs garde;
    private final LectureCompetences lire;
    private final GestionnaireCompetences agir;

    public ControleurCompetencesEffectifs(GardeEffectifs garde, LectureCompetences lire, GestionnaireCompetences agir) {
        this.garde = garde;
        this.lire = lire;
        this.agir = agir;
    }

    private GardeAcces.Verdict gerer(HttpServletRequest r) {
        return garde.capacite(r, MODULE, GardeEffectifs.Capacite.GERER);
    }

    @GetMapping("/ressources/{id}/competences")
    public ResponseEntity<Object> competences(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, gerer(r), OK, ctx -> lire.deLaRessource(ctx, id));
    }

    @PutMapping("/ressources/{id}/competences/{code}")
    public ResponseEntity<Object> poser(HttpServletRequest r, @PathVariable String id, @PathVariable String code,
                                       @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), OK, ctx -> agir.poser(ctx, id, code, c == null ? Map.of() : c));
    }

    @DeleteMapping("/ressources/{id}/competences/{code}")
    public ResponseEntity<Object> retirer(HttpServletRequest r, @PathVariable String id, @PathVariable String code) {
        return garde.repondre(r, gerer(r), OK, ctx -> {
            agir.retirer(ctx, id, code);
            return Map.of("retiree", code);
        });
    }

    /** Le relevé des échéances : les compétences échues ou qui échoient dans {@code dans} jours (60 par défaut). */
    @GetMapping("/competences/echeances")
    public ResponseEntity<Object> echeances(HttpServletRequest r, @RequestParam(required = false) String dans) {
        return garde.repondre(r, gerer(r), OK, ctx -> lire.releve(ctx, dans));
    }
}
