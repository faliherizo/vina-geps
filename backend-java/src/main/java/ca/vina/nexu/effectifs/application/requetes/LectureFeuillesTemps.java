package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.Comportements;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.FeuilleTemps;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.LectureFeuillesTempsPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Les lectures des feuilles de temps — jumeau des lectures de {@code backend/src/effectifs/temps/service.ts} : paramètres,
 * types d'heures en vigueur, feuille visible (la personne ou l'encadrement de son unité), listes et historique d'un segment.
 */
public class LectureFeuillesTemps {

    private static final String CODE_PAIE = "codePaie";

    /** Un type d'heure de l'organisme : son code, ce que le logiciel en comprend, son code de paie. */
    public record TypeHeureTemps(String code, Comportements.TypeHeure comportement, String codePaie) { }

    private final LectureFeuillesTempsPort depot;
    private final LectureRessources ressources;
    private final LecturePerimetres perimetres;
    private final LectureReferentiels referentiels;

    public LectureFeuillesTemps(LectureFeuillesTempsPort depot, LectureRessources ressources, LecturePerimetres perimetres, LectureReferentiels referentiels) {
        this.depot = depot;
        this.ressources = ressources;
        this.perimetres = perimetres;
        this.referentiels = referentiels;
    }

    public LectureFeuillesTempsPort depot() {
        return depot;
    }

    public LectureRessources ressources() {
        return ressources;
    }

    public TempsEffectifs.Parametres parametres(String tenantId) {
        return depot.parametres(tenantId).orElse(TempsEffectifs.PARAMETRES_DEFAUT);
    }

    // ── Types d'heures ───────────────────────────────────────────────────────

    public List<TypeHeureTemps> typesHeure(Contexte ctx, String date) {
        return referentiels.lister(ctx, TypeReferentiel.TYPES_HEURE.code(), date, false).stream()
                .map(v -> new TypeHeureTemps(v.code(), Comportements.TypeHeure.valueOf(v.comportement()),
                        String.valueOf(v.attributs().getOrDefault(CODE_PAIE, v.code())))).toList();
    }

    public static TypeHeureTemps typeDuComportement(List<TypeHeureTemps> types, Comportements.TypeHeure c) {
        return types.stream().filter(t -> t.comportement() == c).findFirst().orElseThrow(() -> new EffectifsInvalideException("aucun type d'heure de comportement "
                + c + " n'est en vigueur : configurez-le dans Paramétrage des effectifs › Référentiels › Types d'heures"));
    }

    /** Un type inconnu de la base compte comme du travail : l'historique survit au retrait d'un type. */
    public static Comportements.TypeHeure comportementDe(Map<String, Comportements.TypeHeure> types, String code) {
        return types.getOrDefault(code, Comportements.TypeHeure.TRAVAILLEE);
    }

    public static Map<String, Comportements.TypeHeure> parCode(List<TypeHeureTemps> types) {
        return types.stream().collect(Collectors.toMap(TypeHeureTemps::code, TypeHeureTemps::comportement, (a, b) -> a, LinkedHashMap::new));
    }

    // ── Feuille ──────────────────────────────────────────────────────────────

    private static IntrouvableException introuvable(String id) {
        return new IntrouvableException("feuille « " + id + " » introuvable");
    }

    /** La feuille, sans garde : pour les calculs d'une feuille déjà autorisée. */
    public LectureFeuillesTempsPort.FeuilleStockee ligne(String tenantId, String id) {
        return depot.feuille(tenantId, id).orElseThrow(() -> introuvable(id));
    }

    public boolean estALui(Contexte ctx, String ressourceId) {
        return ctx.acteurId().equals(ressources.exiger(ctx, ressourceId).utilisateurId());
    }

    /** La feuille, pour la personne ou l'encadrement de son unité ; sinon 404. */
    public LectureFeuillesTempsPort.FeuilleStockee exigerLigne(Contexte ctx, String id) {
        LectureFeuillesTempsPort.FeuilleStockee l = ligne(ctx.tenantId(), id);
        FeuilleTemps f = l.feuille();
        if (ctx.estAdministrateur() || estALui(ctx, f.ressourceId()) || perimetres.unitesVisibles(ctx, Perimetres.Niveau.LECTURE).contains(f.uniteCode())) {
            return l;
        }
        throw introuvable(id);
    }

    /** Écrire dans la feuille : la personne elle-même (vrai), ou le niveau PLANIFICATION de son unité (faux). */
    public boolean exigerEcriture(Contexte ctx, FeuilleTemps f) {
        if (estALui(ctx, f.ressourceId())) {
            return true;
        }
        perimetres.exiger(ctx, f.uniteCode(), Perimetres.Niveau.PLANIFICATION);
        return false;
    }

    public List<TempsEffectifs.SegmentLu> segmentsActifs(Contexte ctx, String feuilleId, List<TypeHeureTemps> types) {
        Map<String, Comportements.TypeHeure> comportements = parCode(types);
        return depot.segmentsActifs(ctx.tenantId(), feuilleId).stream().map(s -> new TempsEffectifs.SegmentLu(s.id(), s.date(), s.debut(), s.fin(),
                s.typeHeureCode(), comportementDe(comportements, s.typeHeureCode()), s.source(), s.placeId(), s.version(), s.remplaceId(), s.motif(),
                s.creeLe(), s.creePar(), s.sourceExterne(), s.confirmeLe(), s.confirmePar(), s.description(), s.attributs())).toList();
    }

    // ── Listes ───────────────────────────────────────────────────────────────

    public List<FeuilleTemps> mesFeuilles(Contexte ctx) {
        return ressources.deLActeur(ctx).map(r -> depot.deLaRessource(ctx.tenantId(), r.id())).orElse(List.of());
    }

    public List<FeuilleTemps> lister(Contexte ctx, String statut, String debut) {
        return lister(ctx, statut, debut, Set.of());
    }

    /**
     * Les feuilles du périmètre, plus celles dont l'acteur porte le palier courant par son rôle ou nommément ({@code portees},
     * E-GP-6) : une personne désignée hors de l'unité doit trouver ce qu'elle a à décider.
     */
    public List<FeuilleTemps> lister(Contexte ctx, String statut, String debut, Set<String> portees) {
        Set<String> visibles = ctx.estAdministrateur() ? null : perimetres.unitesVisibles(ctx, Perimetres.Niveau.LECTURE);
        return depot.lister(ctx.tenantId(), statut, debut).stream()
                .filter(f -> visibles == null || visibles.contains(f.uniteCode()) || portees.contains(f.id())).toList();
    }

    /** Le jour est-il férié au calendrier de l'organisme ? */
    public boolean estFerie(Contexte ctx, String date) {
        return referentiels.lister(ctx, TypeReferentiel.JOURS_FERIES.code(), date, false).stream().anyMatch(v -> v.code().equals(date));
    }

    /** Toutes les versions d'un segment, de la plus récente à l'origine (E-RF-14), dans la forme des lignes de la base. */
    public List<Map<String, Object>> historique(Contexte ctx, String feuilleId, String segmentId) {
        FeuilleTemps f = exigerLigne(ctx, feuilleId).feuille();
        Map<String, Comportements.TypeHeure> types = parCode(typesHeure(ctx, f.debut()));
        List<Map<String, Object>> chaine = new ArrayList<>();
        Optional<LectureFeuillesTempsPort.SegmentStocke> s = depot.segment(ctx.tenantId(), feuilleId, segmentId);
        while (s.isPresent()) {
            chaine.add(vue(s.get(), comportementDe(types, s.get().typeHeureCode())));
            String precedent = s.get().remplaceId();
            s = precedent == null ? Optional.empty() : depot.segment(ctx.tenantId(), feuilleId, precedent);
        }
        if (chaine.isEmpty()) {
            throw new IntrouvableException("segment « " + segmentId + " » introuvable");
        }
        return chaine;
    }

    private static Map<String, Object> vue(LectureFeuillesTempsPort.SegmentStocke s, Comportements.TypeHeure comportement) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.id());
        m.put("tenantId", s.tenantId());
        m.put("feuilleId", s.feuilleId());
        m.put("placeId", s.placeId());
        m.put("date", s.date());
        m.put("debut", s.debut());
        m.put("fin", s.fin());
        m.put("typeHeureCode", s.typeHeureCode());
        m.put("source", s.source());
        m.put("version", s.version());
        m.put("remplaceId", s.remplaceId());
        // La colonne SQLite est un entier : le Node la rend telle quelle.
        m.put("actif", s.actif() ? 1 : 0);
        m.put("motif", s.motif());
        m.put("creeLe", s.creeLe());
        m.put("creePar", s.creePar());
        m.put("sourceExterne", s.sourceExterne());
        m.put("confirmeLe", s.confirmeLe());
        m.put("confirmePar", s.confirmePar());
        m.put("comportement", comportement);
        return m;
    }
}
