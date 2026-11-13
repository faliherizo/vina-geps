package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotMilieuxPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.LecturePointagesEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Milieu;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.PointageEffectifs;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs;
import ca.vina.nexu.effectifs.domaine.RepartitionPresence;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.partage.temps.Iso;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Les lectures de la présence (lot R8) — jumeau de la partie lecture de {@code presence/service.ts} : le pointage pour
 * soi ou pour l'encadrement de son unité, l'écran de l'employé, celui de l'encadrement, et le code QR à afficher.
 */
public class LecturePresenceEffectifs {

    private static final long QUINZAINE_MS = 14 * 86_400_000L;
    private static final int RECENTS_MAX = 50;
    private static final int LISTE_MAX = 500;
    private static final int HISTORIQUE_MAX = 200;
    /** Ce qu'affiche un temps dont le milieu n'a pas été déclaré (saisie hors quart) : jamais un identifiant nul. */
    static final String MILIEU_NON_DECLARE = "milieu non déclaré";

    /** Les lectures du socle et de la planification que la présence emprunte. */
    public record Socle(LectureRessources ressources, LecturePerimetres perimetres, LectureMilieux milieux, DepotMilieuxPort secrets) { }

    private final LecturePointagesEffectifsPort depot;
    private final LectureQuartsPresence quarts;
    private final Socle socle;

    public LecturePresenceEffectifs(LecturePointagesEffectifsPort depot, LectureQuartsPresence quarts, Socle socle) {
        this.depot = depot;
        this.quarts = quarts;
        this.socle = socle;
    }

    private static IntrouvableException introuvable(String id) {
        return new IntrouvableException("pointage « " + id + " » introuvable");
    }

    public PointageEffectifs lire(String tenantId, String id) {
        return depot.lire(tenantId, id).orElseThrow(() -> introuvable(id));
    }

    /** Le pointage, pour la personne qui l'a fait ou pour l'encadrement de son unité ; sinon 404. */
    public PointageEffectifs exiger(Contexte ctx, String id) {
        PointageEffectifs p = lire(ctx.tenantId(), id);
        if (ctx.estAdministrateur()) {
            return p;
        }
        boolean aMoi = socle.ressources().deLActeur(ctx).map(r -> r.id().equals(p.ressourceId())).orElse(false);
        if (aMoi || socle.perimetres().unitesVisibles(ctx, Perimetres.Niveau.LECTURE).contains(p.lieu().uniteCode())) {
            return p;
        }
        throw introuvable(id);
    }

    public List<PointageEffectifs> deLaSaisie(String tenantId, String saisieId) {
        return depot.deLaSaisie(tenantId, saisieId);
    }

    public List<PresenceEffectifs.PointageCourt> actifsDeLaPlace(String tenantId, String ressourceId, String placeId) {
        return depot.actifsDeLaPlace(tenantId, ressourceId, placeId);
    }

    /** Ce que voit l'employé : son quart du moment (et ce qu'il peut pointer) et ses pointages récents. */
    public Map<String, Object> maPresence(Contexte ctx) {
        Map<String, Object> vue = new LinkedHashMap<>();
        Optional<Ressource> r = socle.ressources().deLActeur(ctx);
        vue.put("ressource", r.orElse(null));
        vue.put("quart", r.flatMap(x -> quarts.autourDe(ctx.tenantId(), x.id(), ctx.maintenant())).map(q -> quartDuMoment(ctx, r.get(), q)).orElse(null));
        String depuis = Iso.de(Instant.parse(ctx.maintenant()).minusMillis(QUINZAINE_MS));
        vue.put("pointages", r.map(x -> vues(ctx.tenantId(), depot.deLaRessource(ctx.tenantId(), x.id(), depuis, RECENTS_MAX))).orElse(List.of()));
        return vue;
    }

    private Map<String, Object> quartDuMoment(Contexte ctx, Ressource r, PresenceEffectifs.QuartPointe q) {
        List<PresenceEffectifs.PointageCourt> faits = depot.actifsDeLaPlace(ctx.tenantId(), r.id(), q.placeId());
        List<PresenceEffectifs.Segment> segments = PresenceEffectifs.segmentsDe(faits, q.debutPrevu(), q.presencePrealableMinutes(), ctx.maintenant());
        Map<String, Object> m = vue(q);
        m.put("prochains", PresenceEffectifs.sensPermis(faits.isEmpty() ? null : faits.getLast().sens()));
        m.put("segments", segments);
        m.put("minutesPayees", PresenceEffectifs.minutesPayees(segments));
        repartition(ctx.tenantId(), m, segments, faits);
        return m;
    }

    /** E-GP-7 et E-GP-15 : où le temps a été fait, ce que la personne faisait, et les déplacements qui l'expliquent. */
    private void repartition(String tenantId, Map<String, Object> m, List<PresenceEffectifs.Segment> segments, List<PresenceEffectifs.PointageCourt> faits) {
        m.put("parMilieu", RepartitionPresence.tempsParMilieu(segments).stream().map(t -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("milieuId", t.milieuId());
            x.put("minutes", t.minutes());
            x.put("milieu", libelleMilieu(tenantId, t.milieuId()));
            return x;
        }).toList());
        m.put("parActivite", RepartitionPresence.tempsParActivite(segments));
        m.put("deplacements", RepartitionPresence.deplacementsDe(faits).stream().map(d -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("quand", d.quand());
            x.put("de", d.de());
            x.put("vers", d.vers());
            x.put("deLibelle", libelleMilieu(tenantId, d.de()));
            x.put("versLibelle", libelleMilieu(tenantId, d.vers()));
            return x;
        }).toList());
    }

    /** Le libellé d'un milieu, pour que l'écran n'affiche pas un identifiant ; un milieu disparu garde son identifiant. */
    public String libelleMilieu(String tenantId, String milieuId) {
        if (milieuId == null) {
            return MILIEU_NON_DECLARE;
        }
        return socle.secrets().lire(tenantId, milieuId).map(Milieu::libelle).orElse(milieuId);
    }

    /** La forme rendue d'un quart pointé, dans l'ordre des champs du Node. */
    public static Map<String, Object> vue(PresenceEffectifs.QuartPointe q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("placeId", q.placeId());
        m.put("milieuId", q.milieuId());
        m.put("milieu", q.milieu());
        m.put("uniteCode", q.uniteCode());
        m.put("natureCode", q.natureCode());
        m.put("date", q.date());
        m.put("debut", q.debut());
        m.put("fin", q.fin());
        m.put("debutPrevu", q.debutPrevu());
        m.put("finPrevue", q.finPrevue());
        m.put("presencePrealableMinutes", q.presencePrealableMinutes());
        return m;
    }

    /**
     * L'écran de l'encadrement : les pointages du périmètre, plus ceux dont le document ({@code portes} : la saisie, ou le pointage seul) attend une
     * décision de l'acteur par son rôle ou nommément (E-GP-6).
     */
    public List<Map<String, Object>> lister(Contexte ctx, LecturePointagesEffectifsPort.Filtre filtre, Set<String> portes) {
        return vues(ctx.tenantId(), duPerimetre(ctx, filtre, portes));
    }

    /** Les pointages que l'acteur peut lire : son périmètre, et les documents que le flux lui fait décider. */
    public List<PointageEffectifs> duPerimetre(Contexte ctx, LecturePointagesEffectifsPort.Filtre filtre, Set<String> portes) {
        Set<String> visibles = ctx.estAdministrateur() ? null : socle.perimetres().unitesVisibles(ctx, Perimetres.Niveau.LECTURE);
        return depot.lister(ctx.tenantId(), filtre, LISTE_MAX).stream()
                .filter(p -> visibles == null || visibles.contains(p.lieu().uniteCode()) || portes.contains(documentDe(p))).toList();
    }

    /** Les preuves jugées d'un pointage, par mécanisme. */
    public List<PresenceEffectifs.VerdictPreuve> preuves(String tenantId, String pointageId) {
        return depot.preuves(tenantId, pointageId);
    }

    /** Le document que le flux d'un pointage approuve : la saisie entière, ou le pointage seul. */
    public static String documentDe(PointageEffectifs p) {
        return p.saisieId() != null ? p.saisieId() : p.id();
    }

    /** Un horodatage tiers confirme-t-il ce pointage (même sens, à la tolérance de la présence) ? */
    public boolean corrobore(String tenantId, String ressourceId, PresenceEffectifs.PointageCourt p) {
        return PresenceEffectifs.corrobore(p, depot.externes(tenantId, ressourceId));
    }

    /** Les pointages d'une personne : pour soi, ou dans le périmètre. */
    public List<Map<String, Object>> presenceDe(Contexte ctx, String ressourceId) {
        Ressource r = socle.ressources().exiger(ctx, ressourceId);
        socle.ressources().exigerDroitSur(ctx, r, Perimetres.Niveau.LECTURE);
        return vues(ctx.tenantId(), depot.deLaRessource(ctx.tenantId(), r.id(), null, HISTORIQUE_MAX));
    }

    /** Le code QR à afficher au milieu (écran de borne ou affichette rafraîchie) : il change toutes les cinq minutes. */
    public Map<String, Object> codeQrDuMilieu(Contexte ctx, String milieuId) {
        Milieu m = socle.milieux().exiger(ctx, milieuId);
        socle.perimetres().exiger(ctx, m.uniteCode(), Perimetres.Niveau.GESTION);
        String secret = socle.secrets().secretQr(ctx.tenantId(), m.id())
                .orElseThrow(() -> new EffectifsInvalideException("le milieu « " + m.code() + " » n'exige pas de code QR de lieu"));
        long t = Instant.parse(ctx.maintenant()).toEpochMilli();
        Map<String, Object> code = new LinkedHashMap<>();
        code.put("code", PresenceEffectifs.codeQr(secret, m.id(), t));
        code.put("valideJusqua", PresenceEffectifs.finDeFenetre(t));
        return code;
    }

    // ── Formes rendues ───────────────────────────────────────────────────────

    public List<Map<String, Object>> vues(String tenantId, List<PointageEffectifs> pointages) {
        return pointages.stream().map(p -> vue(tenantId, p)).toList();
    }

    /** Un pointage dans la forme du Node : ses colonnes, le nom, ses preuves jugées, et s'il est corroboré. */
    public Map<String, Object> vue(String tenantId, PointageEffectifs p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id());
        m.put("ressourceId", p.ressourceId());
        m.put("placeId", p.lieu().placeId());
        m.put("milieuId", p.lieu().milieuId());
        m.put("uniteCode", p.lieu().uniteCode());
        m.put("sens", p.sens());
        m.put("horodatage", p.horodatage());
        m.put("origine", p.origine());
        m.put("saisieId", p.saisieId());
        constat(m, p.constat());
        suivi(m, p.suivi());
        m.put("naturePause", p.qualification().naturePause());
        m.put("activiteCode", p.qualification().activiteCode());
        m.put("nom", p.nom());
        m.put("preuves", depot.preuves(tenantId, p.id()).stream().map(LecturePresenceEffectifs::vue).toList());
        m.put("corrobore", PresenceEffectifs.corrobore(p.court(), depot.externes(tenantId, p.ressourceId())));
        return m;
    }

    private static void constat(Map<String, Object> m, PointageEffectifs.Constat c) {
        m.put("resultat", c.resultat());
        m.put("ecart", c.ecart());
        m.put("distanceMetres", c.distanceMetres());
        m.put("actionRegles", c.actionRegles());
        m.put("motifsRegles", c.motifsRegles());
    }

    private static void suivi(Map<String, Object> m, PointageEffectifs.Suivi s) {
        m.put("statut", s.statut());
        m.put("justification", s.justification());
        m.put("saisiLe", s.saisiLe());
        m.put("saisiPar", s.saisiPar());
        m.put("decideLe", s.decideLe());
        m.put("decidePar", s.decidePar());
        m.put("motifDecision", s.motifDecision());
    }

    /** Une preuve jugée : la distance et le détail n'apparaissent que s'ils existent. */
    public static Map<String, Object> vue(PresenceEffectifs.VerdictPreuve v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mecanisme", v.mecanisme());
        m.put("exigee", v.exigee());
        m.put("fournie", v.fournie());
        m.put("satisfaite", v.satisfaite());
        if (v.distanceMetres() != null) {
            m.put("distanceMetres", v.distanceMetres());
        }
        if (v.detail() != null) {
            m.put("detail", v.detail());
        }
        return m;
    }
}
