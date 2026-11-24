package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.commandes.GestionnaireRattachements;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code /api/effectifs/planification/ressources/{id}/rattachements} — <strong>les autres unités d'une personne</strong>
 * (AO 26-015-DO, addenda 8, M-09), sous la porte des ressources : module de planification et capacité de gestion.
 */
@RestController
@RequestMapping("/api/effectifs/planification/ressources/{id}/rattachements")
@Transactional
public class ControleurRattachementsEffectifs {

    private static final String MODULE = "effectifs_planification";
    private static final int OK = 200;
    private static final int CREE = 201;

    private final GardeEffectifs garde;
    private final GestionnaireRattachements agir;

    public ControleurRattachementsEffectifs(GardeEffectifs garde, GestionnaireRattachements agir) {
        this.garde = garde;
        this.agir = agir;
    }

    private GardeAcces.Verdict gerer(HttpServletRequest r) {
        return garde.capacite(r, MODULE, GardeEffectifs.Capacite.GERER);
    }

    @GetMapping
    public ResponseEntity<Object> lister(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, gerer(r), OK, ctx -> agir.deLaRessource(ctx, id));
    }

    @PostMapping
    public ResponseEntity<Object> ajouter(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), CREE, ctx -> agir.ajouter(ctx, id, c == null ? Map.of() : c));
    }

    @DeleteMapping("/{uniteCode}")
    public ResponseEntity<Object> retirer(HttpServletRequest r, @PathVariable String id, @PathVariable String uniteCode,
                                          @RequestParam(required = false) String depuis) {
        return garde.repondre(r, gerer(r), OK, ctx -> agir.retirer(ctx, id, uniteCode, depuis));
    }
}
