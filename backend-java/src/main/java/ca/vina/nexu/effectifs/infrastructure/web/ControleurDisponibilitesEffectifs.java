package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.commandes.GestionnaireAttributionFinDeCollecte;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireDisponibilites;
import ca.vina.nexu.effectifs.application.requetes.LectureAttributionAutomatique;
import ca.vina.nexu.effectifs.application.requetes.LectureDisponibilites;
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
import tools.jackson.databind.node.NullNode;

import java.util.Map;

/**
 * {@code /api/effectifs/disponibilites} — les disponibilités par période (AO 26-015-DO, lot R4), jumeau de
 * {@code backend/src/effectifs/disponibilites/routes.ts}. {@code /moi} est ouvert à tout compte relié à une
 * ressource : l'employé dépose ses propres plages. Le reste exige {@code effectifs.manage}, puis le périmètre.
 */
@RestController
@RequestMapping("/api/effectifs/disponibilites")
@Transactional
public class ControleurDisponibilitesEffectifs {

    private static final String MODULE = "effectifs_disponibilites";

    private final GardeEffectifs garde;
    private final LectureDisponibilites lecture;
    private final GestionnaireDisponibilites depots;
    private final GestionnaireAttributionFinDeCollecte attribution;
    private final LectureAttributionAutomatique automatique;

    public ControleurDisponibilitesEffectifs(GardeEffectifs garde, LectureDisponibilites lecture, GestionnaireDisponibilites depots,
                                             GestionnaireAttributionFinDeCollecte attribution, LectureAttributionAutomatique automatique) {
        this.garde = garde;
        this.lecture = lecture;
        this.depots = depots;
        this.attribution = attribution;
        this.automatique = automatique;
    }

    private static Map<String, Object> corps(Map<String, Object> recu) {
        return recu == null ? Map.of() : recu;
    }

    private GardeAcces.Verdict gerer(HttpServletRequest r) {
        return garde.capacite(r, MODULE, GardeEffectifs.Capacite.GERER);
    }

    @GetMapping("/moi")
    public ResponseEntity<Object> moi(HttpServletRequest r) {
        return garde.repondre(r, garde.module(r, MODULE), 200, lecture::mesDisponibilites);
    }

    @PutMapping("/moi/{periodeId}")
    public ResponseEntity<Object> deposerLesMiennes(HttpServletRequest r, @PathVariable String periodeId, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, garde.module(r, MODULE), 200, ctx -> depots.deposerLesMiennes(ctx, periodeId, corps(c)));
    }

    @GetMapping("/periodes/{periodeId}")
    public ResponseEntity<Object> depotsDeLaPeriode(HttpServletRequest r, @PathVariable String periodeId) {
        return garde.repondre(r, gerer(r), 200, ctx -> lecture.listerDepots(ctx, periodeId));
    }

    /** Sans dépôt, le corps est {@code null}, comme le {@code res.json(null)} du Node. */
    @GetMapping("/periodes/{periodeId}/ressources/{ressourceId}")
    public ResponseEntity<Object> consulter(HttpServletRequest r, @PathVariable String periodeId, @PathVariable String ressourceId) {
        return garde.repondre(r, gerer(r), 200, ctx -> lecture.consulter(ctx, periodeId, ressourceId).<Object>map(d -> d).orElse(NullNode.getInstance()));
    }

    @PutMapping("/periodes/{periodeId}/ressources/{ressourceId}")
    public ResponseEntity<Object> deposer(HttpServletRequest r, @PathVariable String periodeId, @PathVariable String ressourceId,
                                          @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 200, ctx -> depots.deposer(ctx, periodeId, ressourceId, corps(c)));
    }

    @PostMapping("/periodes/{periodeId}/ressources/{ressourceId}/approuver")
    public ResponseEntity<Object> approuver(HttpServletRequest r, @PathVariable String periodeId, @PathVariable String ressourceId) {
        return garde.repondre(r, gerer(r), 200, ctx -> depots.approuver(ctx, periodeId, ressourceId));
    }

    /** Où en est l'attribution automatique de fin de collecte (P-GH-4) : quand elle part, et ce qu'elle a donné. */
    @GetMapping("/periodes/{periodeId}/attribution-automatique")
    public ResponseEntity<Object> attributionAutomatique(HttpServletRequest r, @PathVariable String periodeId) {
        return garde.repondre(r, gerer(r), 200, ctx -> automatique.lire(ctx, periodeId));
    }

    @PostMapping("/periodes/{periodeId}/attribuer")
    public ResponseEntity<Object> attribuer(HttpServletRequest r, @PathVariable String periodeId) {
        return garde.repondre(r, gerer(r), 200, ctx -> attribution.attribuer(ctx, periodeId));
    }

    @GetMapping("/places/{placeId}/candidats")
    public ResponseEntity<Object> candidats(HttpServletRequest r, @PathVariable String placeId) {
        return garde.repondre(r, gerer(r), 200, ctx -> lecture.candidats(ctx, placeId));
    }
}
