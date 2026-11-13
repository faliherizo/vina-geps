package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code /api/effectifs/ma-ressource} — mon dossier d'effectif, ou son absence ; jumeau de
 * {@code backend/src/effectifs/planification/routesMaRessource.ts}.
 *
 * <p>Un contrôleur à une route, en réponse à un défaut mesuré le 2 novembre 2026 : sur « Ma
 * feuille de temps », un compte sans dossier d'effectif lisait « Aucune feuille de temps ·
 * Préparez la feuille d'une période », pressait le bouton, et recevait la question d'un
 * planificateur. L'état vide invitait au geste impossible, parce qu'une liste vide ne distingue
 * pas « rien à montrer » de « vous n'êtes pas planifiable ».</p>
 *
 * <p><b>La garde est celle du socle</b> {@code effectifs}, et non celle d'un lot : la question se
 * pose depuis les feuilles de temps, les absences et la confection AGPH — trois modules vendus
 * séparément, dont le socle est le seul prérequis commun. La lecture ne rend que la ressource de
 * l'appelant : aucun paramètre ne permet de demander celle d'autrui.</p>
 */
@RestController
@RequestMapping("/api/effectifs/ma-ressource")
public class ControleurMaRessource {

    private static final String MODULE = "effectifs";
    private static final int OK = 200;

    private final GardeEffectifs garde;
    private final LectureRessources ressources;

    public ControleurMaRessource(GardeEffectifs garde, LectureRessources ressources) {
        this.garde = garde;
        this.ressources = ressources;
    }

    @GetMapping
    public ResponseEntity<Object> maRessource(HttpServletRequest r) {
        return garde.repondre(r, garde.module(r, MODULE), OK, ctx -> {
            // Une enveloppe, et non la ressource nue : `null` comme corps entier ne se distingue
            // pas d'une réponse vide, et elle laisse la place aux faits que ces écrans
            // demanderont ensuite.
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ressource", ressources.deLActeur(ctx).orElse(null));
            return m;
        });
    }
}
