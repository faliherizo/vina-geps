package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.commandes.GestionnaireAbsencesEffectifs;
import ca.vina.nexu.effectifs.application.commandes.SuiviComblementAbsence;
import ca.vina.nexu.effectifs.application.requetes.LectureAbsencesEffectifs;
import ca.vina.nexu.effectifs.domaine.AbsencesEffectifs;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /api/effectifs/absences} — les absences d'effectifs (AO 26-015-DO, lot R5), jumeau de
 * {@code backend/src/effectifs/absences/routes.ts}, gardé par le module {@code effectifs_absences}.
 * <p>
 * On y <strong>déclare</strong> une absence (la personne, soi-même par défaut ; le code ; les dates) : elle est jugée
 * par les règles publiées et enregistrée même refusée, pour que la dérogation reste possible. On y lit les absences
 * de son périmètre, leur impact sur le comblement, et on y décide — approuver, refuser, déroger
 * ({@code effectifs.manage}, puis le niveau GESTION de l'unité). La personne annule sa propre absence ; pour
 * autrui, le gestionnaire exige le périmètre. Le comblement d'une absence (ce que le mécanisme a fait de chaque place,
 * les avis envoyés) se lit, et le remplacement long terme d'une absence longue s'ouvre d'ici, lié à elle (lot U5).
 */
@RestController
@RequestMapping("/api/effectifs/absences")
@Transactional
public class ControleurAbsencesEffectifs {

    private static final String MODULE = "effectifs_absences";
    /** La catégorie que l'interface lit dans la réponse : celle de l'écran qui a déclaré. */
    private static final String MODULE_LIEN = "effectifs";

    private final GardeEffectifs garde;
    private final LectureAbsencesEffectifs lire;
    private final GestionnaireAbsencesEffectifs agir;
    private final SuiviComblementAbsence suivi;

    public ControleurAbsencesEffectifs(GardeEffectifs garde, LectureAbsencesEffectifs lire, GestionnaireAbsencesEffectifs agir, SuiviComblementAbsence suivi) {
        this.garde = garde;
        this.lire = lire;
        this.agir = agir;
        this.suivi = suivi;
    }

    private static Map<String, Object> corps(Map<String, Object> recu) {
        return recu == null ? Map.of() : recu;
    }

    /** Une valeur de la chaîne de requête, élaguée ; vide vaut absente, comme le {@code requete()} du Node. */
    private static String requete(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    @GetMapping
    public ResponseEntity<Object> lister(HttpServletRequest r, @RequestParam(required = false) String du, @RequestParam(required = false) String au,
                                         @RequestParam(required = false) String statut) {
        return garde.repondre(r, garde.module(r, MODULE), 200, ctx -> lire.lister(ctx, new LectureAbsencesEffectifs.Filtre(requete(du), requete(au), requete(statut)))
                .stream().map(LectureAbsencesEffectifs::vue).toList());
    }

    /** La réponse d'une déclaration : l'absence, la décision automatique, ses motifs, les places rendues vacantes et le bilan du comblement. */
    static Map<String, Object> declaree(GestionnaireAbsencesEffectifs.Resultat r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("categorie", MODULE_LIEN);
        m.put("ids", List.of(r.absence().id()));
        m.put("absences", List.of(r.vue()));
        m.put("statut", r.absence().statut().name());
        m.put("motifs", r.absence().motifsRegles());
        m.put("placesLiberees", r.placesLiberees());
        m.put("comblement", r.comblement().vue());
        return m;
    }

    @PostMapping
    public ResponseEntity<Object> declarer(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        Map<String, Object> d = corps(c);
        return garde.repondre(r, garde.module(r, MODULE), 201, ctx -> declaree(agir.declarer(ctx,
                new GestionnaireAbsencesEffectifs.Declaration(d.get("personneId"), d.get("motif"), d.get("du"), d.get("au"), d.get("commentaire")))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> lire(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, garde.module(r, MODULE), 200, ctx -> LectureAbsencesEffectifs.vue(lire.exiger(ctx, id)));
    }

    @GetMapping("/{id}/impact")
    public ResponseEntity<Object> impact(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, garde.module(r, MODULE), 200, ctx -> LectureAbsencesEffectifs.vue(lire.impact(ctx, id)));
    }

    @PostMapping("/{id}/decisions")
    public ResponseEntity<Object> decider(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, garde.capacite(r, MODULE, GardeEffectifs.Capacite.GERER), 200,
                ctx -> agir.decider(ctx, id, AbsencesEffectifs.Decision.humaineDe(corps(c).get("decision")), corps(c)).vue());
    }

    @GetMapping("/{id}/comblement")
    public ResponseEntity<Object> comblement(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, garde.capacite(r, MODULE, GardeEffectifs.Capacite.GERER), 200, ctx -> suivi.lire(ctx, lire.exiger(ctx, id)));
    }

    @PostMapping("/{id}/remplacement")
    public ResponseEntity<Object> remplacement(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, garde.capacite(r, MODULE, GardeEffectifs.Capacite.GERER), 201,
                ctx -> suivi.ouvrirRemplacement(ctx, lire.exiger(ctx, id), corps(c)));
    }

    @PostMapping("/{id}/annuler")
    public ResponseEntity<Object> annuler(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, garde.module(r, MODULE), 200, ctx -> agir.decider(ctx, id, AbsencesEffectifs.Decision.ANNULER, corps(c)).vue());
    }
}
