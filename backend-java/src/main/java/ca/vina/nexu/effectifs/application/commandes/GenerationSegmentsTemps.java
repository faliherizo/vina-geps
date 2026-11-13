package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureFeuillesTemps;
import ca.vina.nexu.effectifs.application.requetes.SourcesFeuillesTemps;
import ca.vina.nexu.effectifs.domaine.Comportements;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.CorroborationTemps;
import ca.vina.nexu.effectifs.domaine.EcritureFeuillesTempsPort.NouveauSegment;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs;
import ca.vina.nexu.effectifs.domaine.SourcesTempsEffectifsPort.PointageTemps;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Les segments <strong>générés</strong> d'une personne sur une période (E-RF-1) — jumeau de {@code segmentsGeneres} : une
 * place pointée donne ses segments pointés, une place non pointée le temps qu'une source tierce a constaté (source EXTERNE,
 * E-RF-13) ou, à défaut, le temps planifié (source GENERE, signalée) ; s'y ajoutent ses saisies a posteriori approuvées.
 */
public class GenerationSegmentsTemps {

    private final LectureFeuillesTemps lecture;
    private final SourcesFeuillesTemps sources;

    public GenerationSegmentsTemps(LectureFeuillesTemps lecture, SourcesFeuillesTemps sources) {
        this.lecture = lecture;
        this.sources = sources;
    }

    /** La période d'une personne. */
    public record Periode(String ressourceId, String debut, String fin) { }

    /** Ce qu'une génération connaît : le contexte et les types d'heures en vigueur. */
    private record Generation(Contexte ctx, List<LectureFeuillesTemps.TypeHeureTemps> types, List<NouveauSegment> nouveaux) {
        String code(Comportements.TypeHeure c) {
            return LectureFeuillesTemps.typeDuComportement(types, c).code();
        }
    }

    public List<NouveauSegment> generer(Contexte ctx, Periode p) {
        Generation g = new Generation(ctx, lecture.typesHeure(ctx, p.debut()), new ArrayList<>());
        // Le type du travail régulier se vérifie d'abord : c'est le seul dont toute feuille a besoin.
        g.code(Comportements.TypeHeure.TRAVAILLEE);
        List<PointageTemps> pointages = sources.pointages(ctx.tenantId(), p.ressourceId(), p.debut(), p.fin());
        List<CorroborationTemps.HorodatageExterne> externes = sources.externes(ctx.tenantId(), p.ressourceId(), p.debut(), p.fin());
        for (TempsEffectifs.QuartPlanifie q : sources.quartsPlanifies(ctx.tenantId(), p.ressourceId(), p.debut(), p.fin())) {
            List<PointageTemps> siens = pointages.stream().filter(x -> q.placeId().equals(x.placeId())).toList();
            if (siens.isEmpty()) {
                sansPointage(g, q, externes);
                continue;
            }
            depuisPointages(g, new Rattachement(q.placeId(), q.date(), q.debutPrevu(), sources.presencePrealable(ctx.tenantId(), q)), siens);
        }
        saisies(pointages, p).values().forEach(faits -> depuisPointages(g, new Rattachement(null, null, null, 0), faits));
        return List.copyOf(g.nouveaux());
    }

    /**
     * <strong>Aucun pointage : une source tierce vaut mieux que l'horaire</strong> (E-RF-13). Un temps déduit du planifié dit ce
     * qui <em>devait</em> arriver ; un horodatage de borne, ce qui est arrivé. Quand une source couvre le quart, le segment vient
     * d'elle (EXTERNE), borné au quart ; sinon seulement, il vient de l'horaire (GENERE).
     */
    private static void sansPointage(Generation g, TempsEffectifs.QuartPlanifie q, List<CorroborationTemps.HorodatageExterne> externes) {
        String code = g.code(Comportements.TypeHeure.TRAVAILLEE);
        Optional<CorroborationTemps.PlagesExternes> couvrantes = CorroborationTemps.plagesDuQuart(externes, q.debutPrevu(), q.finPrevue());
        couvrantes.ifPresentOrElse(c -> c.plages().forEach(x -> g.nouveaux().add(new NouveauSegment(q.placeId(), q.date(), x.debut(), x.fin(), code,
                        TempsEffectifs.Source.EXTERNE, c.source()))),
                () -> g.nouveaux().add(new NouveauSegment(q.placeId(), q.date(), q.debutPrevu(), q.finPrevue(), code, TempsEffectifs.Source.GENERE)));
    }

    /** Où rattacher des segments pointés : la place et le jour du quart (nuls pour une saisie hors quart), son début prévu. */
    private record Rattachement(String placeId, String date, String debutPrevu, int prealable) { }

    private static void depuisPointages(Generation g, Rattachement r, List<PointageTemps> faits) {
        PresenceEffectifs.segmentsDe(faits.stream().map(PointageTemps::court).toList(), r.debutPrevu(), r.prealable(), g.ctx().maintenant()).stream()
                .filter(s -> s.fin() != null)
                .forEach(s -> g.nouveaux().add(new NouveauSegment(r.placeId(), r.date() != null ? r.date() : TempsEffectifs.dateLocaleDe(s.debut()), s.debut(),
                        s.fin(), g.code(TempsEffectifs.comportementDu(s.type())), TempsEffectifs.Source.POINTAGE)));
    }

    /** Les saisies a posteriori approuvées hors quart dont le jour tombe dans la période, par saisie. */
    private static Map<String, List<PointageTemps>> saisies(List<PointageTemps> pointages, Periode p) {
        Map<String, List<PointageTemps>> parSaisie = new LinkedHashMap<>();
        pointages.stream().filter(x -> x.placeId() == null && x.saisieId() != null && x.statut() == PresenceEffectifs.StatutPointage.APPROUVE)
                .filter(x -> TempsEffectifs.dateLocaleDe(x.horodatage()).compareTo(p.debut()) >= 0 && TempsEffectifs.dateLocaleDe(x.horodatage()).compareTo(p.fin()) <= 0)
                .forEach(x -> parSaisie.computeIfAbsent(x.saisieId(), k -> new ArrayList<>()).add(x));
        return parSaisie;
    }
}
