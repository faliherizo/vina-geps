package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.commandes.GestionnaireAlignementOrganigramme;
import ca.vina.nexu.effectifs.application.requetes.LectureAlignementOrganigramme;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code /api/effectifs/liaisons} — les liaisons des effectifs (lot R12), jumeau de
 * {@code backend/src/effectifs/liaisons/routes.ts} : module {@code effectifs}, réservé à l'administration
 * ({@code effectifs.configure}) — aligner les unités sur l'organigramme change le référentiel de tous.
 */
@RestController
@RequestMapping("/api/effectifs/liaisons")
@Transactional
public class ControleurLiaisonsEffectifs {

    private static final String MODULE = "effectifs";
    private static final String DATE = "date";

    private final GardeEffectifs garde;
    private final LectureAlignementOrganigramme lecture;
    private final GestionnaireAlignementOrganigramme alignement;

    public ControleurLiaisonsEffectifs(GardeEffectifs garde, LectureAlignementOrganigramme lecture, GestionnaireAlignementOrganigramme alignement) {
        this.garde = garde;
        this.lecture = lecture;
        this.alignement = alignement;
    }

    private GardeAcces.Verdict configurer(HttpServletRequest r) {
        return garde.capacite(r, MODULE, GardeEffectifs.Capacite.CONFIGURER);
    }

    /** Une valeur de chaîne de requête ne compte que si elle est un texte non vide, comme {@code requete()} du Node. */
    @GetMapping("/organisation")
    public ResponseEntity<Object> apercu(HttpServletRequest r, @RequestParam(required = false) String date) {
        String d = date == null || date.isBlank() ? null : date.trim();
        return garde.repondre(r, configurer(r), 200, ctx -> lecture.apercu(ctx, d));
    }

    @PostMapping("/organisation")
    public ResponseEntity<Object> aligner(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, configurer(r), 200, ctx -> alignement.appliquer(ctx, c == null ? null : c.get(DATE)));
    }
}
