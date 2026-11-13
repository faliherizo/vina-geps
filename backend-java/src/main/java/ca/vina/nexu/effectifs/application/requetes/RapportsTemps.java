package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.FeuilleTemps;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Les <strong>relevés</strong> d'une période (E-RF-7, P-RF-1) — jumeau de {@code rapport} : les feuilles regroupées par
 * unité, personne, type d'heure ou semaine ; les minutes planifiées, déclarées (payées) et supplémentaires, les anomalies.
 */
public class RapportsTemps {

    public record LigneRapport(String groupe, long feuilles, long planifie, long declare, long supplementaires, long anomalies) {

        LigneRapport plus(LigneRapport d) {
            return new LigneRapport(groupe, feuilles + d.feuilles, planifie + d.planifie, declare + d.declare, supplementaires + d.supplementaires,
                    anomalies + d.anomalies);
        }
    }

    private final LectureFeuillesTemps lecture;
    private final CalculFeuillesTemps calcul;

    public RapportsTemps(LectureFeuillesTemps lecture, CalculFeuillesTemps calcul) {
        this.lecture = lecture;
        this.calcul = calcul;
    }

    public List<LigneRapport> rapport(Contexte ctx, Object debutBrut, Object regroupementBrut) {
        String debut = Planification.dateDe(debutBrut, "debut");
        TempsEffectifs.Regroupement regroupement = TempsEffectifs.regroupementDe(regroupementBrut);
        Map<String, LigneRapport> groupes = new LinkedHashMap<>();
        for (FeuilleTemps f : lecture.lister(ctx, null, debut)) {
            FeuilleDetailleeTemps d = calcul.calculer(ctx, lecture.ligne(ctx.tenantId(), f.id()), false);
            deltas(f, d, regroupement).forEach(l -> groupes.merge(l.groupe(), l, LigneRapport::plus));
        }
        return groupes.values().stream().sorted(Comparator.comparing(LigneRapport::groupe)).toList();
    }

    private static List<LigneRapport> deltas(FeuilleTemps f, FeuilleDetailleeTemps d, TempsEffectifs.Regroupement regroupement) {
        return switch (regroupement) {
            case TYPE_HEURE -> d.totaux().entrySet().stream().map(e -> new LigneRapport(e.getKey(), 0, 0, e.getValue(), 0, 0)).toList();
            case SEMAINE -> TempsEffectifs.minutesTravailleesParJour(d.segments()).entrySet().stream().map(e -> new LigneRapport(Planification.lundiDe(e.getKey()),
                    0, 0, e.getValue(), d.heuresSupplementaires().parJour().getOrDefault(e.getKey(), 0L), 0)).toList();
            case UNITE -> List.of(parFeuille(f.uniteCode(), d));
            case RESSOURCE -> List.of(parFeuille(f.matricule() + " — " + f.nom(), d));
        };
    }

    private static LigneRapport parFeuille(String groupe, FeuilleDetailleeTemps d) {
        long planifie = d.ecarts().stream().mapToLong(TempsEffectifs.EcartPlanifie::planifie).sum();
        long declare = d.segments().stream().filter(s -> TempsEffectifs.estPaye(s.comportement()))
                .mapToLong(s -> TempsEffectifs.minutesDe(s.debut(), s.fin())).sum();
        return new LigneRapport(groupe, 1, planifie, declare, d.heuresSupplementaires().total(), d.anomalies().size());
    }
}
