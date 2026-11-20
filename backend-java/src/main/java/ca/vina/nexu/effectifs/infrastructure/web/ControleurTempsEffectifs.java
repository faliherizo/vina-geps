package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.commandes.ExportPaieTemps;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireApprobationTemps;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireFluxApprobations;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireParametresTemps;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePreparationTemps;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireSegmentsTemps;
import ca.vina.nexu.effectifs.application.requetes.CalculFeuillesTemps;
import ca.vina.nexu.effectifs.application.requetes.LectureFeuillesTemps;
import ca.vina.nexu.effectifs.application.requetes.LectureFluxApprobations;
import ca.vina.nexu.effectifs.application.requetes.RapportsTemps;
import ca.vina.nexu.effectifs.domaine.FormatResultats;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /api/effectifs/temps} — les feuilles de temps (AO 26-015-DO, lot R9), jumeau de
 * {@code backend/src/effectifs/temps/routes.ts} : mêmes chemins, mêmes codes, mêmes messages. Préparer, corriger et soumettre
 * sa <strong>propre</strong> feuille sont des gestes de l'employé : l'application vérifie que la feuille est la sienne, sinon
 * le niveau PLANIFICATION de l'unité. Approuver, retourner, exporter la paie et lire les rapports relèvent de l'encadrement
 * ({@code effectifs.manage}, puis le niveau GESTION) ; régler les seuils, de l'administration ({@code effectifs.configure}).
 */
@RestController
@RequestMapping("/api/effectifs/temps")
@Transactional
public class ControleurTempsEffectifs {

    private static final String MODULE = "effectifs_temps";
    private static final double MINUTES_PAR_HEURE = 60.0;
    private static final double CENTIEMES = 100.0;

    /** Les lectures du lot. */
    public record Lectures(LectureFeuillesTemps feuilles, CalculFeuillesTemps calcul, RapportsTemps rapports, LectureFluxApprobations flux) { }

    /** Les écritures du lot. */
    public record Gestes(GestionnaireParametresTemps parametres, GestionnairePreparationTemps preparation, GestionnaireSegmentsTemps segments,
                         GestionnaireApprobationTemps approbation, ExportPaieTemps exportPaie, GestionnaireFluxApprobations flux) { }

    private final GardeEffectifs garde;
    private final Lectures lire;
    private final Gestes agir;

    public ControleurTempsEffectifs(GardeEffectifs garde, Lectures lire, Gestes agir) {
        this.garde = garde;
        this.lire = lire;
        this.agir = agir;
    }

    private static Map<String, Object> corps(Map<String, Object> recu) {
        return recu == null ? Map.of() : recu;
    }

    /** Une valeur de la chaîne de requête, si elle est un texte non vide (comme {@code requete} du Node). */
    private static String requete(String v) {
        return v == null || v.isBlank() ? null : v.strip();
    }

    private GardeAcces.Verdict module(HttpServletRequest r) {
        return garde.module(r, MODULE);
    }

    private GardeAcces.Verdict gerer(HttpServletRequest r) {
        return garde.capacite(r, MODULE, GardeEffectifs.Capacite.GERER);
    }

    @GetMapping("/parametres")
    public ResponseEntity<Object> parametres(HttpServletRequest r) {
        return garde.repondre(r, module(r), 200, ctx -> lire.feuilles().parametres(ctx.tenantId()));
    }

    // ── Champs de la feuille propres à l'organisme (ETUDE-FEUILLE-DE-TEMPS-DYNAMIQUE.md) ──

    @GetMapping("/champs")
    public ResponseEntity<Object> champs(HttpServletRequest r) {
        return garde.repondre(r, module(r), 200, ctx -> agir.parametres().champs(ctx));
    }

    @PostMapping("/champs")
    public ResponseEntity<Object> definirChamp(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, configurer(r), 201, ctx -> agir.parametres().definirChamp(ctx, corps(c)));
    }

    @PatchMapping("/champs/{code}")
    public ResponseEntity<Object> activerChamp(HttpServletRequest r, @PathVariable String code, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, configurer(r), 200, ctx -> agir.parametres().activerChamp(ctx, code, corps(c)));
    }

    @PutMapping("/parametres")
    public ResponseEntity<Object> modifierParametres(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, configurer(r), 200, ctx -> agir.parametres().modifier(ctx, corps(c)));
    }

    /** AD-03 (addenda 7, Q15) : les seuils d'heures supplémentaires propres à une convention collective. */
    @GetMapping("/seuils-conventions")
    public ResponseEntity<Object> seuilsConventions(HttpServletRequest r) {
        return garde.repondre(r, module(r), 200, ctx -> lire.feuilles().seuilsConventions(ctx.tenantId()));
    }

    @PutMapping("/seuils-conventions/{code}")
    public ResponseEntity<Object> modifierSeuilsConvention(HttpServletRequest r, @PathVariable String code, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, configurer(r), 200, ctx -> agir.parametres().modifierSeuilsConvention(ctx, code, corps(c)));
    }

    private GardeAcces.Verdict configurer(HttpServletRequest r) {
        return garde.capacite(r, MODULE, GardeEffectifs.Capacite.CONFIGURER);
    }

    /** E-GP-6 : les flux d'approbation des feuilles et des pointages, branchés sur le moteur générique. */
    @GetMapping("/flux")
    public ResponseEntity<Object> flux(HttpServletRequest r) {
        return garde.repondre(r, gerer(r), 200, lire.flux()::lireFlux);
    }

    @PostMapping("/flux")
    public ResponseEntity<Object> ajouterRegleFlux(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, configurer(r), 201, ctx -> agir.flux().ajouterRegle(ctx, corps(c)));
    }

    @DeleteMapping("/flux/{id}")
    public ResponseEntity<Object> retirerRegleFlux(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, configurer(r), 200, ctx -> agir.flux().retirerRegle(ctx, id));
    }

    @GetMapping("/moi")
    public ResponseEntity<Object> moi(HttpServletRequest r) {
        return garde.repondre(r, module(r), 200, lire.feuilles()::mesFeuilles);
    }

    @PostMapping("/feuilles/preparer")
    public ResponseEntity<Object> preparer(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, module(r), 201, ctx -> agir.preparation().preparer(ctx, corps(c)));
    }

    @GetMapping("/feuilles")
    public ResponseEntity<Object> feuilles(HttpServletRequest r, @RequestParam(required = false) String statut, @RequestParam(required = false) String debut) {
        return garde.repondre(r, gerer(r), 200, ctx -> lire.calcul().aDecider(ctx, requete(statut), requete(debut)));
    }

    @GetMapping("/feuilles/{id}")
    public ResponseEntity<Object> feuille(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, module(r), 200, ctx -> lire.calcul().lire(ctx, id));
    }

    @PostMapping("/feuilles/{id}/regenerer")
    public ResponseEntity<Object> regenerer(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, module(r), 200, ctx -> agir.preparation().regenerer(ctx, id));
    }

    @PostMapping("/feuilles/{id}/segments")
    public ResponseEntity<Object> ajouter(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, module(r), 201, ctx -> agir.segments().ajouter(ctx, id, corps(c)));
    }

    @PutMapping("/feuilles/{id}/segments/{segmentId}")
    public ResponseEntity<Object> corriger(HttpServletRequest r, @PathVariable String id, @PathVariable String segmentId,
                                           @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, module(r), 200, ctx -> agir.segments().corriger(ctx, id, segmentId, corps(c)));
    }

    @DeleteMapping("/feuilles/{id}/segments/{segmentId}")
    public ResponseEntity<Object> retirer(HttpServletRequest r, @PathVariable String id, @PathVariable String segmentId,
                                          @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, module(r), 200, ctx -> agir.segments().retirer(ctx, id, segmentId, corps(c)));
    }

    @PostMapping("/feuilles/{id}/segments/{segmentId}/confirmer")
    public ResponseEntity<Object> confirmer(HttpServletRequest r, @PathVariable String id, @PathVariable String segmentId) {
        return garde.repondre(r, module(r), 200, ctx -> agir.segments().confirmer(ctx, id, segmentId));
    }

    @GetMapping("/feuilles/{id}/segments/{segmentId}/historique")
    public ResponseEntity<Object> historique(HttpServletRequest r, @PathVariable String id, @PathVariable String segmentId) {
        return garde.repondre(r, module(r), 200, ctx -> lire.feuilles().historique(ctx, id, segmentId));
    }

    @PostMapping("/feuilles/{id}/soumettre")
    public ResponseEntity<Object> soumettre(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, module(r), 200, ctx -> agir.approbation().soumettre(ctx, id));
    }

    @PostMapping("/feuilles/{id}/approuver")
    public ResponseEntity<Object> approuver(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.approbation().decider(ctx, id, TempsEffectifs.Decision.APPROUVER, corps(c)));
    }

    @PostMapping("/feuilles/{id}/retourner")
    public ResponseEntity<Object> retourner(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.approbation().decider(ctx, id, TempsEffectifs.Decision.RETOURNER, corps(c)));
    }

    /** La personne (ou qui a soumis pour elle) reprend sa feuille avant la paie, pour la corriger. */
    @PostMapping("/feuilles/{id}/rappeler")
    public ResponseEntity<Object> rappeler(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, module(r), 200, ctx -> agir.approbation().rappeler(ctx, id, corps(c)));
    }

    /** L'encadrement rouvre une feuille approuvée, avant la paie, et la renvoie avec un motif. */
    @PostMapping("/feuilles/{id}/rouvrir")
    public ResponseEntity<Object> rouvrir(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.approbation().rouvrir(ctx, id, corps(c)));
    }

    @GetMapping("/export-paie")
    public ResponseEntity<Object> exportPaie(HttpServletRequest r, @RequestParam(required = false) String debut, @RequestParam(required = false) String uniteCode) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.exportPaie().exporter(ctx, requete(debut), requete(uniteCode)));
    }

    @GetMapping("/rapports")
    public ResponseEntity<Object> rapports(HttpServletRequest r, @RequestParam(required = false) String debut,
                                           @RequestParam(required = false) String regroupement, @RequestParam(required = false) String format) {
        FormatResultats[] f = new FormatResultats[1];
        String[] genereLe = new String[1];
        ResponseEntity<Object> reponse = garde.repondre(r, gerer(r), 200, ctx -> {
            f[0] = FormatResultats.de(format);
            genereLe[0] = ctx.maintenant();
            return lire.rapports().rapport(ctx, requete(debut), requete(regroupement));
        });
        // Le rapport a validé la date avant de répondre : elle peut nommer le fichier sans risque d'en-tête forgé.
        if (!reponse.getStatusCode().is2xxSuccessful() || f[0] == FormatResultats.JSON) {
            return reponse;
        }
        @SuppressWarnings("unchecked")
        List<RapportsTemps.LigneRapport> lignes = (List<RapportsTemps.LigneRapport>) reponse.getBody();
        return ResultatsTelecharges.fichier(f[0], new ResultatsTelecharges.Document("Relevé des feuilles de temps du " + requete(debut), "releve-temps-" + requete(debut), genereLe[0]),
                lignes.stream().map(ControleurTempsEffectifs::ligneExportee).toList());
    }

    /** Une ligne du relevé en heures décimales : un fichier se relit hors de l'écran, qui seul sait afficher « 36 h 00 ». */
    private static Map<String, Object> ligneExportee(RapportsTemps.LigneRapport l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Groupe", l.groupe());
        m.put("Feuilles", l.feuilles());
        m.put("Planifié (h)", heures(l.planifie()));
        m.put("Déclaré (h)", heures(l.declare()));
        m.put("Supplémentaires (h)", heures(l.supplementaires()));
        m.put("Anomalies", l.anomalies());
        return m;
    }

    private static double heures(long minutes) {
        return Math.round(minutes / MINUTES_PAR_HEURE * CENTIEMES) / CENTIEMES;
    }
}
