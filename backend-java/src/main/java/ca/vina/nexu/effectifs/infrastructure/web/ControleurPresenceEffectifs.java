package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.commandes.GestionnaireDecisionsPresence;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireHorodatagesExternes;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePointagesEffectifs;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireProfilsPointage;
import ca.vina.nexu.effectifs.application.requetes.LectureAuditLocalisations;
import ca.vina.nexu.effectifs.application.requetes.LecturePointagesADecider;
import ca.vina.nexu.effectifs.application.requetes.LecturePresenceEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureProfilsPointage;
import ca.vina.nexu.effectifs.domaine.LecturePointagesEffectifsPort;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code /api/effectifs/presence} — la présence (AO 26-015-DO, lot R8), jumeau de
 * {@code backend/src/effectifs/presence/routes.ts} : mêmes chemins, mêmes codes, mêmes messages. Pointer et déclarer un
 * quart non pointé sont des gestes de l'employé pour lui-même : la personne vient de la session, jamais du corps (une
 * saisie pour autrui exige le niveau GESTION de l'unité). Décider, importer et afficher le code QR d'un milieu relèvent
 * de l'encadrement ({@code effectifs.manage}).
 */
@RestController
@RequestMapping("/api/effectifs/presence")
@Transactional
public class ControleurPresenceEffectifs {

    private static final String MODULE = "effectifs_presence";

    /** Les écritures du lot. */
    public record Gestes(GestionnairePointagesEffectifs pointages, GestionnaireDecisionsPresence decisions, GestionnaireHorodatagesExternes horodatages,
                         GestionnaireProfilsPointage profils) { }

    /** Les lectures du lot qui ne passent pas par la décision : les profils (E-GP-1) et l'audit des localisations (E-GP-14). */
    public record Lectures(LecturePresenceEffectifs presence, LectureProfilsPointage profils, LectureAuditLocalisations audit) { }

    /** La valeur de {@code ecartSeul} qui ne retient que les écarts de lieu, comme le Node. */
    private static final String OUI = "1";
    private static final String CSV = "text/csv; charset=utf-8";
    private static final String FICHIER_CSV = "attachment; filename=\"audit-localisations.csv\"";

    private final GardeEffectifs garde;
    private final LecturePresenceEffectifs lire;
    private final Lectures lectures;
    private final LecturePointagesADecider aDecider;
    private final Gestes agir;

    public ControleurPresenceEffectifs(GardeEffectifs garde, Lectures lectures, LecturePointagesADecider aDecider, Gestes agir) {
        this.garde = garde;
        this.lire = lectures.presence();
        this.lectures = lectures;
        this.aDecider = aDecider;
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

    @GetMapping("/moi")
    public ResponseEntity<Object> moi(HttpServletRequest r) {
        return garde.repondre(r, module(r), 200, lire::maPresence);
    }

    @GetMapping("/referentiels-profils")
    public ResponseEntity<Object> referentielsProfils(HttpServletRequest r) {
        return garde.repondre(r, module(r), 200, ctx -> LectureProfilsPointage.referentiels());
    }

    @GetMapping("/mon-profil")
    public ResponseEntity<Object> monProfil(HttpServletRequest r) {
        return garde.repondre(r, module(r), 200, ctx -> lectures.profils().monProfil(ctx));
    }

    @GetMapping("/profils")
    public ResponseEntity<Object> profils(HttpServletRequest r) {
        return garde.repondre(r, gerer(r), 200, ctx -> lectures.profils().lister(ctx.tenantId()));
    }

    @PutMapping("/profils")
    public ResponseEntity<Object> poserProfil(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.profils().poser(ctx, corps(c)));
    }

    @DeleteMapping("/profils/{code}")
    public ResponseEntity<Object> retirerProfil(HttpServletRequest r, @PathVariable String code) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.profils().retirer(ctx, code));
    }

    @GetMapping("/audit-localisations")
    public ResponseEntity<Object> auditLocalisations(HttpServletRequest r, @RequestParam(required = false) String du, @RequestParam(required = false) String au,
                                                     @RequestParam(required = false) String milieuId, @RequestParam(required = false) String ecartSeul) {
        LectureAuditLocalisations.Filtre f = filtreAudit(du, au, milieuId, ecartSeul);
        return garde.repondre(r, gerer(r), 200, ctx -> lectures.audit().auditer(ctx, f));
    }

    /** E-GP-14 : l'audit en CSV, le format qu'un vérificateur emporte. Un refus garde sa forme JSON. */
    @GetMapping("/audit-localisations.csv")
    public ResponseEntity<Object> auditLocalisationsCsv(HttpServletRequest r, @RequestParam(required = false) String du, @RequestParam(required = false) String au,
                                                        @RequestParam(required = false) String milieuId, @RequestParam(required = false) String ecartSeul) {
        LectureAuditLocalisations.Filtre f = filtreAudit(du, au, milieuId, ecartSeul);
        ResponseEntity<Object> reponse = garde.repondre(r, gerer(r), 200, ctx -> lectures.audit().enCsv(ctx, f));
        if (!reponse.getStatusCode().is2xxSuccessful()) {
            return reponse;
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, CSV).header(HttpHeaders.CONTENT_DISPOSITION, FICHIER_CSV).body(reponse.getBody());
    }

    private static LectureAuditLocalisations.Filtre filtreAudit(String du, String au, String milieuId, String ecartSeul) {
        return new LectureAuditLocalisations.Filtre(requete(du), requete(au), requete(milieuId), OUI.equals(requete(ecartSeul)));
    }

    @PostMapping("/pointages")
    public ResponseEntity<Object> pointer(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, module(r), 201, ctx -> agir.pointages().pointer(ctx, corps(c), r.getRemoteAddr()));
    }

    @PostMapping("/saisies")
    public ResponseEntity<Object> saisir(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, module(r), 201, ctx -> agir.pointages().saisirAPosteriori(ctx, corps(c)));
    }

    @GetMapping("/pointages")
    public ResponseEntity<Object> pointages(HttpServletRequest r, @RequestParam(required = false) String statut, @RequestParam(required = false) String du,
                                            @RequestParam(required = false) String au, @RequestParam(required = false) String milieuId) {
        // E-GP-7 : le filtre par site, pour vérifier une caserne et non tout le périmètre.
        LecturePointagesEffectifsPort.Filtre f = new LecturePointagesEffectifsPort.Filtre(requete(statut), requete(du), requete(au), requete(milieuId));
        return garde.repondre(r, gerer(r), 200, ctx -> aDecider.lister(ctx, f));
    }

    @GetMapping("/pointages/{id}")
    public ResponseEntity<Object> pointage(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, module(r), 200, ctx -> aDecider.detaille(ctx, id));
    }

    @PostMapping("/pointages/{id}/approuver")
    public ResponseEntity<Object> approuver(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.decisions().decider(ctx, id, PresenceEffectifs.DecisionPointage.APPROUVER, corps(c)));
    }

    @PostMapping("/pointages/{id}/rejeter")
    public ResponseEntity<Object> rejeter(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.decisions().decider(ctx, id, PresenceEffectifs.DecisionPointage.REJETER, corps(c)));
    }

    @GetMapping("/ressources/{id}/pointages")
    public ResponseEntity<Object> presenceDe(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, module(r), 200, ctx -> lire.presenceDe(ctx, id));
    }

    @GetMapping("/milieux/{id}/code-qr")
    public ResponseEntity<Object> codeQr(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, gerer(r), 200, ctx -> lire.codeQrDuMilieu(ctx, id));
    }

    @PostMapping("/horodatages")
    public ResponseEntity<Object> horodatages(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 201, ctx -> agir.horodatages().importer(ctx, corps(c)));
    }
}
