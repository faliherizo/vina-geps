package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.commandes.GestionnaireEchangesEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureEchangesEffectifs;
import ca.vina.nexu.effectifs.domaine.EchangeEffectifs.Geste;
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
 * {@code /api/effectifs/echanges} — les échanges de quarts (AO 26-015-DO, lot R6), jumeau de
 * {@code backend/src/effectifs/echanges/routes.ts} : mêmes chemins, mêmes codes, mêmes messages, gardés par le module
 * {@code effectifs_echanges}. Un échange est un geste de pair à pair : proposer, accepter, refuser et annuler sont
 * ouverts aux deux personnes concernées — l'application vérifie que l'acteur <strong>est</strong> la partie (ou
 * l'encadrement de son unité). Valider ou rejeter relève de l'encadrement ({@code effectifs.manage}, niveau GESTION
 * des deux unités).
 */
@RestController
@RequestMapping("/api/effectifs/echanges")
@Transactional
public class ControleurEchangesEffectifs {

    private static final String MODULE = "effectifs_echanges";
    private static final int CREE = 201;
    private static final int OK = 200;

    private final GardeEffectifs garde;
    private final LectureEchangesEffectifs lire;
    private final GestionnaireEchangesEffectifs agir;

    public ControleurEchangesEffectifs(GardeEffectifs garde, LectureEchangesEffectifs lire, GestionnaireEchangesEffectifs agir) {
        this.garde = garde;
        this.lire = lire;
        this.agir = agir;
    }

    private static Map<String, Object> corps(Map<String, Object> recu) {
        return recu == null ? Map.of() : recu;
    }

    private GardeAcces.Verdict module(HttpServletRequest r) {
        return garde.module(r, MODULE);
    }

    @GetMapping("/moi")
    public ResponseEntity<Object> moi(HttpServletRequest r) {
        return garde.repondre(r, module(r), OK, lire::mesQuarts);
    }

    @GetMapping("/ressources/{id}/quarts")
    public ResponseEntity<Object> quartsDe(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, module(r), OK, ctx -> lire.quartsDe(ctx, id));
    }

    @GetMapping("/places/{placeId}/demandables")
    public ResponseEntity<Object> demandables(HttpServletRequest r, @PathVariable String placeId) {
        return garde.repondre(r, module(r), OK, ctx -> lire.demandables(ctx, placeId));
    }

    @GetMapping
    public ResponseEntity<Object> lister(HttpServletRequest r, @RequestParam(required = false) String statut) {
        return garde.repondre(r, module(r), OK, ctx -> lire.lister(ctx, statut));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> lire(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, module(r), OK, ctx -> lire.exiger(ctx, id));
    }

    @PostMapping
    public ResponseEntity<Object> proposer(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, module(r), CREE, ctx -> agir.proposer(ctx, corps(c)));
    }

    private ResponseEntity<Object> geste(HttpServletRequest r, GardeAcces.Verdict v, String id, Geste g, Map<String, Object> c) {
        return garde.repondre(r, v, OK, ctx -> agir.gerer(ctx, id, g, corps(c)));
    }

    @PostMapping("/{id}/accepter")
    public ResponseEntity<Object> accepter(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return geste(r, module(r), id, Geste.ACCEPTER, c);
    }

    @PostMapping("/{id}/refuser")
    public ResponseEntity<Object> refuser(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return geste(r, module(r), id, Geste.REFUSER, c);
    }

    @PostMapping("/{id}/annuler")
    public ResponseEntity<Object> annuler(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return geste(r, module(r), id, Geste.ANNULER, c);
    }

    @PostMapping("/{id}/valider")
    public ResponseEntity<Object> valider(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return geste(r, garde.capacite(r, MODULE, GardeEffectifs.Capacite.GERER), id, Geste.VALIDER, c);
    }

    @PostMapping("/{id}/rejeter")
    public ResponseEntity<Object> rejeter(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return geste(r, garde.capacite(r, MODULE, GardeEffectifs.Capacite.GERER), id, Geste.REJETER, c);
    }
}
