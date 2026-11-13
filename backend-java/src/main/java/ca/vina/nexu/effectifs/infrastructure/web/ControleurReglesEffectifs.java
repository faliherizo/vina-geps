package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRegles;
import ca.vina.nexu.effectifs.application.requetes.LectureRegles;
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
 * {@code /api/effectifs/regles} — le <strong>moteur de règles des effectifs</strong> (AO 26-015-DO, lot R2),
 * gardé par le module {@code effectifs_regles}. Jumeau de {@code backend/src/effectifs/regles/routes.ts} :
 * mêmes chemins, mêmes codes, mêmes messages. Le paramétrage exige {@code effectifs.configure} ; la lecture
 * des décisions, {@code effectifs.manage} — un gestionnaire doit pouvoir expliquer un refus.
 */
@RestController
@RequestMapping("/api/effectifs/regles")
@Transactional
public class ControleurReglesEffectifs {

    private static final String MODULE = "effectifs_regles";

    private final GardeEffectifs garde;
    private final LectureRegles lecture;
    private final GestionnaireRegles gestion;
    private final EvaluateurRegles evaluateur;

    public ControleurReglesEffectifs(GardeEffectifs garde, LectureRegles lecture, GestionnaireRegles gestion, EvaluateurRegles evaluateur) {
        this.garde = garde;
        this.lecture = lecture;
        this.gestion = gestion;
        this.evaluateur = evaluateur;
    }

    private static Map<String, Object> corps(Map<String, Object> recu) {
        return recu == null ? Map.of() : recu;
    }

    /** Une valeur de la chaîne de requête si elle est un texte non vide, comme {@code requete()} du Node. */
    private static String filtre(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private GardeAcces.Verdict lire(HttpServletRequest r) {
        return garde.module(r, MODULE);
    }

    private GardeAcces.Verdict configurer(HttpServletRequest r) {
        return garde.capacite(r, MODULE, GardeEffectifs.Capacite.CONFIGURER);
    }

    private GardeAcces.Verdict gerer(HttpServletRequest r) {
        return garde.capacite(r, MODULE, GardeEffectifs.Capacite.GERER);
    }

    @GetMapping("/faits")
    public ResponseEntity<Object> faits(HttpServletRequest r) {
        return garde.repondre(r, lire(r), 200, ctx -> lecture.faits());
    }

    @GetMapping("/evaluations")
    public ResponseEntity<Object> evaluations(HttpServletRequest r, @RequestParam(required = false) String objetType,
                                              @RequestParam(required = false) String objetId, @RequestParam(required = false) String contexte) {
        return garde.repondre(r, gerer(r), 200, ctx -> lecture.evaluations(ctx, filtre(objetType), filtre(objetId), filtre(contexte)));
    }

    @PostMapping("/apercu")
    public ResponseEntity<Object> apercu(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, configurer(r), 200, ctx -> lecture.apercu(corps(c)));
    }

    @PostMapping("/simuler")
    public ResponseEntity<Object> simuler(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, configurer(r), 200, ctx -> evaluateur.simuler(ctx, corps(c)));
    }

    @GetMapping
    public ResponseEntity<Object> lister(HttpServletRequest r, @RequestParam(required = false) String contexte) {
        return garde.repondre(r, lire(r), 200, ctx -> lecture.lister(ctx, filtre(contexte)));
    }

    @PostMapping
    public ResponseEntity<Object> creer(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, configurer(r), 201, ctx -> gestion.creer(ctx, corps(c)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> lireRegle(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, lire(r), 200, ctx -> lecture.lire(ctx, id));
    }

    @PostMapping("/{id}/versions")
    public ResponseEntity<Object> publierVersion(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, configurer(r), 201, ctx -> gestion.publierVersion(ctx, id, corps(c)));
    }

    @PostMapping("/{id}/retirer")
    public ResponseEntity<Object> retirer(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, configurer(r), 200, ctx -> gestion.retirer(ctx, id, corps(c)));
    }
}
