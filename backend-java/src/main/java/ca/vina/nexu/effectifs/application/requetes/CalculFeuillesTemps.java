package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.domaine.ActionRegle;
import ca.vina.nexu.effectifs.domaine.ContexteRegle;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.CorroborationTemps;
import ca.vina.nexu.effectifs.domaine.DemandesFlux;
import ca.vina.nexu.effectifs.domaine.FluxApprobation;
import ca.vina.nexu.effectifs.domaine.EcritureFeuillesTempsPort;
import ca.vina.nexu.effectifs.domaine.FeuilleTemps;
import ca.vina.nexu.effectifs.domaine.LectureFeuillesTempsPort;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs;
import ca.vina.nexu.effectifs.domaine.RefusParRegleException;
import ca.vina.nexu.effectifs.domaine.Regles;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.domaine.SourcesTempsEffectifsPort;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <strong>Le calcul d'une feuille</strong> — jumeau de {@code calculer}, {@code anomaliesDePresence}, {@code anomaliesDeRegles}
 * et {@code jugerJournee} ({@code backend/src/effectifs/temps/service.ts}). Tout ce qui se déduit des segments se calcule
 * ici, à la lecture ; les règles ne laissent une trace que pour une décision (la soumission).
 */
public class CalculFeuillesTemps {

    private static final String OBJET = "feuille";
    private static final String SEPARATEUR = " ; ";

    private static final long MINUTE_MS = 60_000L;

    private final LectureFeuillesTemps lecture;
    private final SourcesFeuillesTemps sources;
    private final EvaluateurRegles regles;
    private final LectureFluxApprobations flux;

    public CalculFeuillesTemps(LectureFeuillesTemps lecture, SourcesFeuillesTemps sources, EvaluateurRegles regles, LectureFluxApprobations flux) {
        this.lecture = lecture;
        this.sources = sources;
        this.regles = regles;
        this.flux = flux;
    }

    public FeuilleDetailleeTemps lire(Contexte ctx, String id) {
        return calculer(ctx, ligneADecider(ctx, id), false);
    }

    /**
     * La feuille visible par le périmètre comme toute feuille, <strong>ou</strong> portée par l'acteur au palier courant de son
     * flux (un rôle ou une personne désignés, E-GP-6) : qui doit décider doit pouvoir lire. Sinon 404.
     */
    public LectureFeuillesTempsPort.FeuilleStockee ligneADecider(Contexte ctx, String id) {
        LectureFeuillesTempsPort.FeuilleStockee l = lecture.ligne(ctx.tenantId(), id);
        FeuilleTemps f = l.feuille();
        boolean porte = f.statut() == TempsEffectifs.StatutFeuille.SOUMISE
                && flux.porteLEtapeCourante(ctx, flux.fluxDe(ctx.tenantId(), FluxApprobation.ObjetFlux.FEUILLE, id), f.uniteCode());
        return porte ? l : lecture.exigerLigne(ctx, id);
    }

    /** Les feuilles du périmètre, et celles dont l'acteur porte le palier courant par son rôle ou nommément (E-GP-6). */
    public List<FeuilleTemps> aDecider(Contexte ctx, String statut, String debut) {
        Set<String> portees = ctx.estAdministrateur() ? Set.of() : flux.enAttenteDe(ctx, FluxApprobation.ObjetFlux.FEUILLE);
        return lecture.lister(ctx, statut, debut, portees);
    }

    /** Ce qu'un calcul lit de la feuille et de ses sources. */
    private record Lu(FeuilleTemps feuille, List<TempsEffectifs.SegmentLu> segments, TempsEffectifs.Parametres parametres,
                      List<TempsEffectifs.QuartPlanifie> quarts, List<CorroborationTemps.HorodatageExterne> externes) { }

    private Lu lire(Contexte ctx, FeuilleTemps f) {
        return new Lu(f, lecture.segmentsActifs(ctx, f.id(), lecture.typesHeure(ctx, f.debut())), lecture.parametres(ctx.tenantId()),
                sources.quartsPlanifies(ctx.tenantId(), f.ressourceId(), f.debut(), f.fin()), sources.externes(ctx.tenantId(), f.ressourceId(), f.debut(), f.fin()));
    }

    /**
     * La feuille détaillée ; {@code trace} vrai : chaque jugement de règle est conservé (la soumission). E-RF-13 : ce que les
     * sources tierces confirment, et ce sur quoi elles se contredisent, à la tolérance et avec le sort que l'organisme a choisis.
     */
    public FeuilleDetailleeTemps calculer(Contexte ctx, LectureFeuillesTempsPort.FeuilleStockee l, boolean trace) {
        Lu lu = lire(ctx, l.feuille());
        FeuilleTemps f = lu.feuille();
        long tolerance = lu.parametres().toleranceCorroborationMs();
        List<FeuilleDetailleeTemps.SegmentDetaille> prouves = avecPreuve(ctx, lu, tolerance);
        List<CorroborationTemps.CorrelationSources> correlation = CorroborationTemps.correlationParJour(lu.externes(), tolerance);
        Ressource r = lecture.ressources().exiger(ctx, f.ressourceId());
        List<TempsEffectifs.Anomalie> anomalies = TempsEffectifs.triees(Stream.of(TempsEffectifs.anomaliesDeBase(lu.segments(), lu.quarts(),
                        lu.parametres().toleranceEcartMinutes()), anomaliesDePresence(ctx, f, lu.quarts()), anomaliesDeCorroboration(lu, prouves, correlation),
                anomaliesDeRegles(ctx, r, lu, trace ? f.id() : null)).flatMap(List::stream).toList());
        FeuilleDetailleeTemps.Calculs c = new FeuilleDetailleeTemps.Calculs(lu.segments(), prouves, TempsEffectifs.heuresSupplementaires(lu.segments(),
                lu.parametres()), TempsEffectifs.ecartsAuPlanifie(lu.segments(), lu.quarts()), anomalies, correlation);
        DemandesFlux.Flux fluxDeLaFeuille = f.soumiseLe() == null ? null : flux.fluxDe(ctx.tenantId(), FluxApprobation.ObjetFlux.FEUILLE, f.id()).orElse(null);
        return FeuilleDetailleeTemps.de(l, c, lecture.depot().decisions(ctx.tenantId(), f.id()), fluxDeLaFeuille);
    }

    private static List<TempsEffectifs.Anomalie> anomaliesDeCorroboration(Lu lu, List<FeuilleDetailleeTemps.SegmentDetaille> prouves,
                                                                         List<CorroborationTemps.CorrelationSources> correlation) {
        Set<String> joursAvecSource = CorroborationTemps.joursAvecSource(lu.externes());
        return Stream.of(CorroborationTemps.anomaliesDeCorroboration(prouves, joursAvecSource::contains, lu.parametres().actionNonCorrobore()),
                CorroborationTemps.anomaliesDeDivergence(correlation, lu.parametres().actionSourcesDivergentes()),
                CorroborationTemps.anomaliesAConfirmer(prouves)).flatMap(List::stream).toList();
    }

    /**
     * Les segments <strong>enrichis de ce qui les confirme</strong> (E-RF-13), recalculés à chaque lecture : un horodatage importé
     * après la préparation doit confirmer les segments déjà là. Le pointage qui a <em>ouvert</em> un segment pointé est celui
     * dont la source confirme l'horodatage.
     */
    private List<FeuilleDetailleeTemps.SegmentDetaille> avecPreuve(Contexte ctx, Lu lu, long tolerance) {
        List<SourcesTempsEffectifsPort.PointageTemps> pointages = sources.pointages(ctx.tenantId(), lu.feuille().ressourceId(), lu.feuille().debut(),
                lu.feuille().fin());
        return lu.segments().stream().map(s -> FeuilleDetailleeTemps.SegmentDetaille.de(s, s.source() != TempsEffectifs.Source.POINTAGE ? null
                : ouvrant(pointages, s.debut()).map(p -> CorroborationTemps.corroborationDe(p.court(), lu.externes(), tolerance)).orElse(null))).toList();
    }

    private static Optional<SourcesTempsEffectifsPort.PointageTemps> ouvrant(List<SourcesTempsEffectifsPort.PointageTemps> pointages, String debut) {
        return pointages.stream().filter(p -> p.horodatage().equals(debut)).findFirst()
                .or(() -> pointages.stream().filter(p -> Math.abs(Instant.parse(p.horodatage()).toEpochMilli() - Instant.parse(debut).toEpochMilli()) < MINUTE_MS)
                        .findFirst());
    }

    /** Les anomalies propres aux pointages : en attente d'approbation, ou quart sans sortie. */
    private List<TempsEffectifs.Anomalie> anomaliesDePresence(Contexte ctx, FeuilleTemps f, List<TempsEffectifs.QuartPlanifie> planifies) {
        Map<String, String> quarts = new LinkedHashMap<>();
        planifies.forEach(q -> quarts.put(q.placeId(), q.date()));
        List<SourcesTempsEffectifsPort.PointageTemps> pointages = sources.pointages(ctx.tenantId(), f.ressourceId(), f.debut(), f.fin()).stream()
                .filter(p -> p.placeId() == null || quarts.containsKey(p.placeId())).toList();
        List<TempsEffectifs.Anomalie> a = new ArrayList<>();
        pointages.stream().filter(p -> p.statut() == PresenceEffectifs.StatutPointage.A_APPROUVER).forEach(p -> a.add(new TempsEffectifs.Anomalie(
                TempsEffectifs.CodeAnomalie.POINTAGE_EN_ATTENTE, p.placeId() != null ? quarts.get(p.placeId()) : TempsEffectifs.dateLocaleDe(p.horodatage()),
                "un pointage du " + TempsEffectifs.dateLocaleDe(p.horodatage()) + " attend l'approbation de l'encadrement", true)));
        quarts.forEach((placeId, date) -> {
            List<PresenceEffectifs.Sens> sens = pointages.stream().filter(p -> placeId.equals(p.placeId())).map(SourcesTempsEffectifsPort.PointageTemps::sens).toList();
            if (!sens.isEmpty() && sens.getLast() != PresenceEffectifs.Sens.SORTIE) {
                a.add(new TempsEffectifs.Anomalie(TempsEffectifs.CodeAnomalie.POINTAGE_INCOMPLET, date, "le quart du " + date + " n'a pas de sortie pointée", true));
            }
        });
        return a;
    }

    /** Les règles FEUILLE_DE_TEMPS et HEURES_SUPPLEMENTAIRES, jour par jour ; {@code objetId} non nul : jugements tracés. */
    private List<TempsEffectifs.Anomalie> anomaliesDeRegles(Contexte ctx, Ressource r, Lu lu, String objetId) {
        return anomaliesDeRegles(ctx, r, new Journees(lu.segments(), lu.quarts()), objetId);
    }

    /** Les segments et les quarts que les règles jugent, jour par jour. */
    private record Journees(List<? extends TempsEffectifs.SegmentDeTemps> segments, List<TempsEffectifs.QuartPlanifie> quarts) { }

    private List<TempsEffectifs.Anomalie> anomaliesDeRegles(Contexte ctx, Ressource r, Journees j, String objetId) {
        List<? extends TempsEffectifs.SegmentDeTemps> segments = j.segments();
        List<TempsEffectifs.QuartPlanifie> quarts = j.quarts();
        List<TempsEffectifs.Anomalie> a = new ArrayList<>();
        for (String date : segments.stream().map(TempsEffectifs.SegmentDeTemps::date).collect(Collectors.toCollection(TreeSet::new))) {
            Map<ContexteRegle, Map<String, Object>> faits = TempsEffectifs.faitsDeLaJournee(segments, quarts, new TempsEffectifs.Journee(date, lecture.estFerie(ctx, date)));
            faits.forEach((contexte, f) -> {
                Regles.Dossier d = new Regles.Dossier(contexte, OBJET, objetId == null ? null : objetId + ":" + date, date, r.uniteCode(), f, portees(r));
                Regles.Verdict v = objetId == null ? regles.jugerSansTrace(ctx, d) : regles.evaluerDossier(ctx, d);
                v.motifs().forEach(m -> a.add(new TempsEffectifs.Anomalie(TempsEffectifs.CodeAnomalie.REGLE, date, m.message(), Regles.estBloquant(m.action()))));
            });
        }
        return a;
    }

    private static Map<String, String> portees(Ressource r) {
        Map<String, String> p = new HashMap<>();
        p.put("convention", r.conventionCode());
        p.put("statutEmploi", r.statutEmploiCode());
        p.put("corpsEmploi", r.corpsEmploiCode());
        p.put("titreEmploi", r.titreEmploiCode());
        return p;
    }

    /**
     * Juge en temps réel la journée touchée, <strong>avant</strong> d'écrire : les segments actifs, moins celui que la
     * correction remplace, plus le nouveau. Une règle bloquante refuse la saisie (409, motifs nommés) et rien n'est écrit —
     * le Node écrit puis se défait par un point de reprise ; juger la projection rend le même verdict sans rien défaire.
     */
    public void jugerJournee(Contexte ctx, FeuilleTemps f, String remplaceId, EcritureFeuillesTempsPort.NouveauSegment nouveau) {
        List<LectureFeuillesTemps.TypeHeureTemps> types = lecture.typesHeure(ctx, f.debut());
        List<TempsEffectifs.SegmentDeTemps> segments = new ArrayList<>(lecture.segmentsActifs(ctx, f.id(), types).stream()
                .filter(s -> !s.id().equals(remplaceId)).toList());
        segments.add(new TempsEffectifs.SegmentTemps(null, nouveau.date(), nouveau.debut(), nouveau.fin(), nouveau.typeHeureCode(),
                LectureFeuillesTemps.comportementDe(LectureFeuillesTemps.parCode(types), nouveau.typeHeureCode()), nouveau.source(), nouveau.placeId()));
        List<TempsEffectifs.QuartPlanifie> quarts = sources.quartsPlanifies(ctx.tenantId(), f.ressourceId(), f.debut(), f.fin());
        List<TempsEffectifs.Anomalie> refus = anomaliesDeRegles(ctx, lecture.ressources().exiger(ctx, f.ressourceId()), new Journees(segments, quarts), null).stream()
                .filter(a -> a.bloquante() && a.date().equals(nouveau.date())).toList();
        if (!refus.isEmpty()) {
            List<Regles.Motif> motifs = refus.stream().map(a -> new Regles.Motif("", 0, OBJET, ActionRegle.BLOQUER, a.message())).toList();
            throw new RefusParRegleException(refus.stream().map(TempsEffectifs.Anomalie::message).collect(Collectors.joining(SEPARATEUR)),
                    new Regles.Verdict(ActionRegle.BLOQUER, motifs, List.of()));
        }
    }
}
