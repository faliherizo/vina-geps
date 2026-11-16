package ca.vina.nexu.paie.application.requetes;

import ca.vina.nexu.paie.domaine.ControleTemps;
import ca.vina.nexu.paie.domaine.CyclePaie;
import ca.vina.nexu.paie.domaine.LectureReglesPaiePort;
import ca.vina.nexu.paie.domaine.LectureValidationsPort;
import ca.vina.nexu.paie.domaine.PreparationPaie;
import ca.vina.nexu.paie.domaine.RefusPaie;
import ca.vina.nexu.paie.domaine.ReglePaie;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort.FeuilleAPayer;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort.FeuilleDeLaPeriode;
import ca.vina.nexu.partage.paie.PersonnesPourPaiePort;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ce que l'ADR 0084 ajoute aux lectures de la paie : les règles de paie variable, le contrôle des temps d'une période,
 * et les données variables d'une préparation (règles, jours fériés, personnes, validations).
 */
public class LectureVariablesPaie {

    public record Dependances(LectureReglesPaiePort regles, LectureValidationsPort validations, HeuresApprouveesPort heures,
                              PersonnesPourPaiePort personnes, LecturePaie paie) { }

    private final Dependances d;

    public LectureVariablesPaie(Dependances dependances) {
        this.d = dependances;
    }

    public List<ReglePaie> regles(String tenantId) {
        return d.regles().regles(tenantId);
    }

    public ReglePaie exigerRegle(String tenantId, String id) {
        return d.regles().regle(tenantId, id).orElseThrow(() -> RefusPaie.introuvable("règle de paie « " + id + " » introuvable"));
    }

    /** Le contrôle des temps de la période : chaque feuille, ses anomalies et sa validation ; et qui n'a pas de feuille. */
    public ControleTemps.Etat controle(String tenantId, CyclePaie.Periode periode) {
        return controler(tenantId, d.heures().feuilles(tenantId, periode.debut(), periode.fin()));
    }

    /** Le contrôle d'une seule feuille, pour décider de la valider. */
    public ControleTemps.Ligne ligne(String tenantId, String feuilleId) {
        FeuilleDeLaPeriode f = d.heures().feuille(tenantId, feuilleId)
                .orElseThrow(() -> RefusPaie.introuvable("feuille de temps « " + feuilleId + " » introuvable"));
        return controler(tenantId, List.of(f)).feuilles().getFirst();
    }

    private ControleTemps.Etat controler(String tenantId, List<FeuilleDeLaPeriode> feuilles) {
        return ControleTemps.controler(new ControleTemps.Donnees(feuilles, d.personnes().personnes(tenantId), d.paie().dossiersParPersonne(tenantId),
                d.validations().dernieres(tenantId, feuilles.stream().map(FeuilleDeLaPeriode::feuilleId).toList()), regles(tenantId)));
    }

    /** Les données variables d'une préparation : les fériés couvrent le lendemain de la période, où finit un quart de nuit. */
    public PreparationPaie.Variables pourPreparer(String tenantId, CyclePaie cycle, List<FeuilleAPayer> feuilles) {
        Map<String, PersonnesPourPaiePort.Personne> personnes = d.personnes().personnes(tenantId).stream()
                .collect(Collectors.toMap(PersonnesPourPaiePort.Personne::ressourceId, Function.identity(), (a, b) -> a));
        return new PreparationPaie.Variables(regles(tenantId),
                d.heures().joursFeries(tenantId, LocalDate.parse(cycle.debut()), LocalDate.parse(cycle.fin()).plusDays(1)), personnes,
                d.validations().dernieres(tenantId, feuilles.stream().map(FeuilleAPayer::feuilleId).toList()));
    }
}
