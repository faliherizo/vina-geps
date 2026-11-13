package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.requetes.LectureEchangesEffectifs;
import ca.vina.nexu.effectifs.domaine.FenetreHoraire;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/effectifs/mon-horaire} — les quarts publiés qui m'attendent (maquette s03), jumeau de
 * {@code backend/src/effectifs/planification/routesMonHoraire.ts}.
 *
 * <p>Un contrôleur à une route, et c'est une décision plutôt qu'une paresse :</p>
 *
 * <ul>
 *   <li><b>Pas dans la planification</b> : elle exige la capacité {@code effectifs.manage}, faite
 *       pour l'encadrement. Un paramédic n'a pas à devenir planificateur pour lire son horaire.</li>
 *   <li><b>Pas dans les échanges</b> : la lecture y existe ({@code /echanges/moi}) parce que ses
 *       quarts sont ce qu'on peut offrir en échange, mais un employé dont l'organisme n'a pas
 *       acheté ce module a quand même un horaire.</li>
 * </ul>
 *
 * <p>La garde est donc celle de la <b>planification</b> — l'horaire existe parce que la
 * planification existe — sans la capacité qui la réserve à l'encadrement. La lecture ne rend que
 * la ressource de l'appelant : aucun paramètre ne permet de demander l'horaire d'autrui.</p>
 */
@RestController
@RequestMapping("/api/effectifs/mon-horaire")
public class ControleurMonHoraire {

    private static final String MODULE = "effectifs_planification";
    private static final int OK = 200;

    private final GardeEffectifs garde;
    private final LectureEchangesEffectifs lire;

    public ControleurMonHoraire(GardeEffectifs garde, LectureEchangesEffectifs lire) {
        this.garde = garde;
        this.lire = lire;
    }

    /**
     * {@code du}/{@code au} sont facultatifs et <b>validés avant d'entrer</b> : sans eux, la lecture
     * garde son sens d'origine (les quarts à venir), de sorte qu'aucun appelant existant ne change.
     */
    @GetMapping
    public ResponseEntity<Object> monHoraire(HttpServletRequest r,
                                             @RequestParam(name = "du", required = false) String du,
                                             @RequestParam(name = "au", required = false) String au) {
        return garde.repondre(r, garde.module(r, MODULE), OK, ctx -> lire.mesQuarts(ctx, FenetreHoraire.demandee(du, au)));
    }
}
