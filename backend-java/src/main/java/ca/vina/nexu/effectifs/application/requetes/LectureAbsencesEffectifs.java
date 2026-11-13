package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.AbsenceEffectifs;
import ca.vina.nexu.effectifs.domaine.AbsencesEffectifs;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotAbsencesEffectifsPort;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.PlanningAbsencesEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Referentiels;
import ca.vina.nexu.effectifs.domaine.Ressource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Les lectures des absences d'effectifs (lot R5) — jumeau de la partie lecture de
 * {@code backend/src/effectifs/absences/service.ts}. Une absence se voit si elle est la sienne, ou si son unité est
 * dans le périmètre ; sinon elle n'existe pas — inexistante ou cachée, la réponse est la même.
 */
public class LectureAbsencesEffectifs {

    /** Ce que la façade {@code /api/absences} demande d'une absence visée : la personne, et si l'absence est décidée. */
    public record EtatPourFacade(String personneId, boolean decidee) { }

    /** Le filtre de la liste ; chaque champ nul ou vide est ignoré. */
    public record Filtre(String du, String au, String statut) { }

    private final DepotAbsencesEffectifsPort depot;
    private final PlanningAbsencesEffectifsPort planning;
    private final LectureRessources ressources;
    private final LecturePerimetres perimetres;

    public LectureAbsencesEffectifs(DepotAbsencesEffectifsPort depot, PlanningAbsencesEffectifsPort planning, LectureRessources ressources,
                                    LecturePerimetres perimetres) {
        this.depot = depot;
        this.planning = planning;
        this.ressources = ressources;
        this.perimetres = perimetres;
    }

    public Optional<AbsenceEffectifs> visible(Contexte ctx, String id) {
        return depot.lire(ctx.tenantId(), id).filter(a -> estVisible(ctx, a));
    }

    private boolean estVisible(Contexte ctx, AbsenceEffectifs a) {
        Ressource r = ressources.exiger(ctx, a.ressourceId());
        return ctx.acteurId().equals(r.utilisateurId()) || ctx.estAdministrateur()
                || perimetres.unitesVisibles(ctx, Perimetres.Niveau.LECTURE).contains(a.uniteCode());
    }

    public AbsenceEffectifs exiger(Contexte ctx, String id) {
        return visible(ctx, id).orElseThrow(() -> new IntrouvableException("absence « " + id + " » introuvable"));
    }

    /** Les absences visibles de l'acteur : les siennes, et celles des unités de son périmètre. */
    public List<AbsenceEffectifs> lister(Contexte ctx, Filtre f) {
        String du = rempli(f.du()) ? Referentiels.dateValide(f.du(), "du") : null;
        String au = rempli(f.au()) ? Referentiels.dateValide(f.au(), "au") : null;
        List<AbsenceEffectifs> toutes = depot.lister(ctx.tenantId(), du, au);
        Set<String> visibles = ctx.estAdministrateur() ? null : perimetres.unitesVisibles(ctx, Perimetres.Niveau.LECTURE);
        List<String> miennes = visibles == null ? List.of() : planning.ressourcesDuCompte(ctx.tenantId(), ctx.acteurId());
        return toutes.stream().filter(a -> !rempli(f.statut()) || a.statut().code().equals(f.statut()))
                .filter(a -> visibles == null || visibles.contains(a.uniteCode()) || miennes.contains(a.ressourceId())).toList();
    }

    private static boolean rempli(String s) {
        return s != null && !s.isEmpty();
    }

    /** Les places touchées, le taux de comblement avant et après — calculés, jamais stockés (E-GR-3). */
    public AbsencesEffectifs.Impact impact(Contexte ctx, String id) {
        AbsenceEffectifs a = exiger(ctx, id);
        return calculerImpact(ctx, ressources.exiger(ctx, a.ressourceId()), a.du(), a.au());
    }

    public AbsencesEffectifs.Impact calculerImpact(Contexte ctx, Ressource r, String du, String au) {
        return AbsencesEffectifs.Impact.de(planning.placesTenues(ctx.tenantId(), r.id(), du, au), planning.comblement(ctx.tenantId(), r.uniteCode(), du, au));
    }

    /**
     * Pour la façade (pièces justificatives) : l'absence visible de l'acteur, et si elle est décidée. Vide quand
     * elle n'existe pas <strong>ou</strong> sort du périmètre — la façade répond 404 dans les deux cas.
     */
    public Optional<EtatPourFacade> etatPourFacade(Contexte ctx, String id) {
        return visible(ctx, id).map(a -> new EtatPourFacade(a.ressourceId(), a.statut().estDecidee()));
    }

    // ── Vues : la forme JSON du Node ─────────────────────────────────────────

    /** L'absence à plat, comme la ligne du Node (sans {@code tenantId}). */
    public static Map<String, Object> vue(AbsenceEffectifs a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.id());
        m.put("ressourceId", a.ressourceId());
        m.put("uniteCode", a.uniteCode());
        m.put("codeAbsence", a.codeAbsence());
        m.put("du", a.du());
        m.put("au", a.au());
        m.put("motif", a.motif());
        m.put("statut", a.statut().code());
        m.put("actionRegles", a.actionRegles().code());
        m.put("motifsRegles", a.motifsRegles());
        AbsenceEffectifs.Trace t = a.trace();
        m.put("declareeLe", t.declareeLe());
        m.put("declareePar", t.declareePar());
        m.put("decideeLe", t.decideeLe());
        m.put("decideePar", t.decideePar());
        m.put("motifDecision", t.motifDecision());
        m.put("derogationCode", t.derogationCode());
        m.put("derogationMotif", t.derogationMotif());
        m.put("ressourceNom", a.ressourceNom());
        return m;
    }

    /** L'absence après un geste, avec le nombre de places qu'il a libérées. */
    public static Map<String, Object> vue(AbsenceEffectifs a, int placesLiberees) {
        Map<String, Object> m = vue(a);
        m.put("placesLiberees", placesLiberees);
        return m;
    }

    public static Map<String, Object> vue(AbsencesEffectifs.Impact i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("places", i.places());
        m.put("tauxAvant", nombre(i.tauxAvant()));
        m.put("tauxApres", nombre(i.tauxApres()));
        return m;
    }

    /** Un nombre comme le JSON du Node : 100 et non 100.0. */
    static Number nombre(double d) {
        return d == Math.rint(d) ? (Number) (long) d : (Number) d;
    }
}
