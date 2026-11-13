package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.commandes.GestionnaireMilieux;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePerimetres;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureJournalEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureMilieux;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.domaine.FormatResultats;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /api/effectifs} — le <strong>socle</strong> de la gestion des effectifs (AO 26-015-DO, lot R1) :
 * référentiels datés, périmètres, milieux, journal. Jumeau de {@code backend/src/effectifs/socle/routes.ts} :
 * mêmes chemins, mêmes codes, mêmes messages. Paramétrer relève de {@code effectifs.configure} ; gérer les
 * milieux et lire le journal, de {@code effectifs.manage}. Chaque geste s'écrit avec sa trace, dans une
 * transaction.
 */
@RestController
@RequestMapping("/api/effectifs")
@Transactional
public class ControleurSocleEffectifs {

    private static final String MODULE = "effectifs";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final GardeEffectifs garde;
    private final LectureReferentiels referentiels;
    private final GestionnaireReferentiels gestionReferentiels;
    private final LecturePerimetres perimetres;
    private final GestionnairePerimetres gestionPerimetres;
    private final LectureMilieux milieux;
    private final GestionnaireMilieux gestionMilieux;
    private final LectureJournalEffectifs journal;

    public ControleurSocleEffectifs(GardeEffectifs garde, LectureReferentiels referentiels, GestionnaireReferentiels gestionReferentiels,
                                    LecturePerimetres perimetres, GestionnairePerimetres gestionPerimetres, LectureMilieux milieux,
                                    GestionnaireMilieux gestionMilieux, LectureJournalEffectifs journal) {
        this.garde = garde;
        this.referentiels = referentiels;
        this.gestionReferentiels = gestionReferentiels;
        this.perimetres = perimetres;
        this.gestionPerimetres = gestionPerimetres;
        this.milieux = milieux;
        this.gestionMilieux = gestionMilieux;
        this.journal = journal;
    }

    private static Map<String, Object> corps(Map<String, Object> recu) {
        return recu == null ? Map.of() : recu;
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

    // ── Référentiels ─────────────────────────────────────────────────────────

    @GetMapping("/referentiels")
    public ResponseEntity<Object> descripteurs(HttpServletRequest r) {
        return garde.repondre(r, lire(r), 200, ctx -> referentiels.descripteurs());
    }

    @GetMapping("/referentiels/{type}")
    public ResponseEntity<Object> valeurs(HttpServletRequest r, @PathVariable String type, @RequestParam(required = false) String date,
                                          @RequestParam(required = false) String historique) {
        return garde.repondre(r, lire(r), 200, ctx -> referentiels.lister(ctx, type, date, "1".equals(historique)));
    }

    @GetMapping("/referentiels/{type}/{code}/historique")
    public ResponseEntity<Object> historique(HttpServletRequest r, @PathVariable String type, @PathVariable String code) {
        return garde.repondre(r, lire(r), 200, ctx -> referentiels.historique(ctx, type, code));
    }

    @PostMapping("/referentiels/{type}")
    public ResponseEntity<Object> creerValeur(HttpServletRequest r, @PathVariable String type, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, configurer(r), 201, ctx -> gestionReferentiels.creer(ctx, type, corps(c)));
    }

    @PostMapping("/referentiels/{type}/{code}/versions")
    public ResponseEntity<Object> remplacerValeur(HttpServletRequest r, @PathVariable String type, @PathVariable String code,
                                                  @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, configurer(r), 201, ctx -> gestionReferentiels.remplacer(ctx, type, code, corps(c)));
    }

    @PostMapping("/referentiels/{type}/{code}/fermer")
    public ResponseEntity<Object> fermerValeur(HttpServletRequest r, @PathVariable String type, @PathVariable String code,
                                               @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, configurer(r), 200, ctx -> gestionReferentiels.fermer(ctx, type, code, corps(c)));
    }

    // ── Périmètres ───────────────────────────────────────────────────────────

    @GetMapping("/perimetres/moi")
    public ResponseEntity<Object> mesUnites(HttpServletRequest r) {
        return garde.repondre(r, lire(r), 200, perimetres::mesUnites);
    }

    @GetMapping("/perimetres")
    public ResponseEntity<Object> perimetres(HttpServletRequest r, @RequestParam(required = false) String utilisateurId) {
        return garde.repondre(r, configurer(r), 200, ctx -> perimetres.duCompte(ctx, utilisateurId == null ? "" : utilisateurId));
    }

    @PostMapping("/perimetres")
    public ResponseEntity<Object> ajouterPerimetre(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, configurer(r), 201, ctx -> gestionPerimetres.ajouter(ctx, corps(c)));
    }

    @PostMapping("/perimetres/{id}/fermer")
    public ResponseEntity<Object> fermerPerimetre(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, configurer(r), 200, ctx -> gestionPerimetres.fermer(ctx, id, corps(c)));
    }

    // ── Milieux ──────────────────────────────────────────────────────────────

    @GetMapping("/milieux")
    public ResponseEntity<Object> milieux(HttpServletRequest r) {
        return garde.repondre(r, lire(r), 200, milieux::lister);
    }

    @PostMapping("/milieux")
    public ResponseEntity<Object> creerMilieu(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 201, ctx -> gestionMilieux.creer(ctx, corps(c)));
    }

    @PutMapping("/milieux/{id}")
    public ResponseEntity<Object> modifierMilieu(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 200, ctx -> gestionMilieux.modifier(ctx, id, corps(c)));
    }

    @PutMapping("/milieux/{id}/preuves")
    public ResponseEntity<Object> preuves(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 200, ctx -> gestionMilieux.definirPreuves(ctx, id, corps(c).get("preuves")));
    }

    // ── Journal (E-GN-9, E-GN-10) ────────────────────────────────────────────

    @GetMapping("/journal")
    public ResponseEntity<Object> journal(HttpServletRequest r, @RequestParam Map<String, String> q) {
        GardeAcces.Verdict v = gerer(r);
        FormatResultats[] format = new FormatResultats[1];
        String[] genereLe = new String[1];
        ResponseEntity<Object> reponse = garde.repondre(r, v, 200, ctx -> {
            format[0] = FormatResultats.de(q.get("format"));
            genereLe[0] = ctx.maintenant();
            String origine = q.get("origine");
            JournalEffectifs.Filtre f = new JournalEffectifs.Filtre(q.get("acteurId"), origine == null || origine.isEmpty() ? null : JournalEffectifs.origineDe(origine),
                    q.get("action"), q.get("objetType"), q.get("objetId"), q.get("du"), q.get("au"));
            return journal.lire(ctx, f, q.get("limite"));
        });
        if (!reponse.getStatusCode().is2xxSuccessful()) {
            return reponse;
        }
        @SuppressWarnings("unchecked")
        List<JournalEffectifs.Entree> entrees = (List<JournalEffectifs.Entree>) reponse.getBody();
        return switch (format[0]) {
            case JSON -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(entrees.stream().map(ControleurSocleEffectifs::vue).toList());
            // Le CSV garde l'en-tête et l'ordre de colonnes du Node : des outils d'audit le lisent déjà.
            case CSV -> ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=utf-8")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"journal-effectifs.csv\"").body(JournalEffectifs.enCsv(entrees));
            case XLSX, PDF -> ResultatsTelecharges.fichier(format[0], new ResultatsTelecharges.Document("Journal des effectifs", "journal-effectifs", genereLe[0]),
                    entrees.stream().map(e -> ligneExportee(e, format[0])).toList());
        };
    }

    /**
     * Une entrée telle qu'elle s'emporte. Le PDF laisse de côté {@code avant} et {@code apres} : un
     * instantané JSON sur une ligne de page ne se lit pas ; le tableur, lui, les garde en cellules.
     */
    private static Map<String, Object> ligneExportee(JournalEffectifs.Entree e, FormatResultats format) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("horodatage", e.horodatage());
        m.put("origine", e.origine().name());
        m.put("acteurId", e.acteurId());
        m.put("action", e.action());
        m.put("objetType", e.objetType());
        m.put("objetId", e.objetId());
        m.put("uniteCode", e.uniteCode());
        if (format == FormatResultats.XLSX) {
            m.put("avant", e.avantJson());
            m.put("apres", e.apresJson());
        }
        return m;
    }

    /** Une entrée comme le Node la rend : {@code avant} et {@code apres} relus comme du JSON, pas comme du texte. */
    private static Map<String, Object> vue(JournalEffectifs.Entree e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.id());
        m.put("horodatage", e.horodatage());
        m.put("acteurId", e.acteurId());
        m.put("origine", e.origine().name());
        m.put("action", e.action());
        m.put("objetType", e.objetType());
        m.put("objetId", e.objetId());
        m.put("uniteCode", e.uniteCode());
        m.put("avant", e.avantJson() == null ? null : JSON.readTree(e.avantJson()));
        m.put("apres", e.apresJson() == null ? null : JSON.readTree(e.apresJson()));
        return m;
    }
}
