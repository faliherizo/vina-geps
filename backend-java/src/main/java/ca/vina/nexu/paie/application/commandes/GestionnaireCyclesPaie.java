package ca.vina.nexu.paie.application.commandes;

import ca.vina.nexu.paie.application.requetes.LecturePaie;
import ca.vina.nexu.paie.domaine.Bulletin;
import ca.vina.nexu.paie.domaine.Cumuls;
import ca.vina.nexu.paie.domaine.CyclePaie;
import ca.vina.nexu.paie.domaine.DossierRemuneration;
import ca.vina.nexu.paie.domaine.EcriturePaiePort;
import ca.vina.nexu.paie.domaine.EtatCycle;
import ca.vina.nexu.paie.domaine.PreparationPaie;
import ca.vina.nexu.paie.domaine.RefusPaie;
import ca.vina.nexu.paie.domaine.Trace;
import ca.vina.nexu.paie.domaine.UniteDeTravail;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Le cycle de paie (ADR 0076) : préparer à partir des seules feuilles approuvées, recalculer tant qu'il n'est pas
 * traité, traiter — ce qui fige le cycle et passe ses feuilles à PAYEE, en tout ou rien —, ou annuler.
 */
public class GestionnaireCyclesPaie {

    /** Le résultat d'une préparation : le cycle, ses bulletins, et les personnes approuvées sans dossier, non payées. */
    public record Preparation(CyclePaie cycle, List<Bulletin> bulletins, List<PreparationPaie.Personne> sansDossier) { }

    public record Dependances(LecturePaie lecture, EcriturePaiePort ecriture, HeuresApprouveesPort heures,
                              UniteDeTravail unite, Sequence sequence) { }

    private final Dependances d;

    public GestionnaireCyclesPaie(Dependances dependances) {
        this.d = dependances;
    }

    public Preparation preparer(Trace trace, CyclePaie.Periode periode) {
        CyclePaie cycle = CyclePaie.prepare(d.sequence().uuid(), periode, trace);
        return d.unite().executer(() -> {
            d.ecriture().creerCycle(trace.tenantId(), cycle);
            return calculer(trace.tenantId(), cycle);
        });
    }

    public Preparation recalculer(Trace trace, String cycleId) {
        CyclePaie cycle = d.lecture().exigerCycle(trace.tenantId(), cycleId);
        cycle.exigerPrepare();
        return d.unite().executer(() -> calculer(trace.tenantId(), cycle));
    }

    /**
     * Recalcule d'abord : un dossier ou une feuille a pu changer depuis la préparation. Une feuille rappelée ou rouverte
     * entre-temps fait refuser le verrou, et le traitement entier est annulé.
     */
    public CyclePaie traiter(Trace trace, String cycleId) {
        CyclePaie cycle = d.lecture().exigerCycle(trace.tenantId(), cycleId);
        cycle.exigerPrepare();
        return d.unite().executer(() -> {
            Preparation p = calculer(trace.tenantId(), cycle);
            List<String> feuilles = p.bulletins().stream().flatMap(b -> b.feuilles().stream()).toList();
            verrouiller(new HeuresApprouveesPort.Verrou(trace.tenantId(), trace.acteur(), trace.instant(), cycleId), feuilles);
            d.ecriture().changerEtat(trace, cycleId, EtatCycle.TRAITE);
            return d.lecture().exigerCycle(trace.tenantId(), cycleId);
        });
    }

    public CyclePaie annuler(Trace trace, String cycleId) {
        CyclePaie cycle = d.lecture().exigerCycle(trace.tenantId(), cycleId);
        cycle.exigerPrepare();
        d.ecriture().changerEtat(trace, cycleId, EtatCycle.ANNULE);
        return d.lecture().exigerCycle(trace.tenantId(), cycleId);
    }

    private void verrouiller(HeuresApprouveesPort.Verrou verrou, List<String> feuilles) {
        try {
            d.heures().marquerPayees(verrou, feuilles);
        } catch (HeuresApprouveesPort.FeuilleNonApprouvee e) {
            throw RefusPaie.conflit(e.getMessage() + " ; recalculez le cycle, puis traitez-le de nouveau");
        }
    }

    private List<HeuresApprouveesPort.FeuilleAPayer> heuresApprouvees(String tenantId, CyclePaie cycle) {
        try {
            return d.heures().approuvees(tenantId, cycle.debut(), cycle.fin());
        } catch (HeuresApprouveesPort.HeuresIncalculables e) {
            throw RefusPaie.invalide(e.getMessage());
        }
    }

    private Preparation calculer(String tenantId, CyclePaie cycle) {
        int annee = cycle.periode().annee();
        Map<String, DossierRemuneration> dossiers = d.lecture().dossiersParPersonne(tenantId);
        Function<String, Cumuls> cumuls = d.lecture().cumulsAvant(tenantId, annee);
        PreparationPaie.Resultat r = PreparationPaie.preparer(cycle, heuresApprouvees(tenantId, cycle),
                new PreparationPaie.Sources(d.lecture().bareme(annee), d.lecture().reglages(tenantId, annee), dossiers, cumuls, d.sequence()::uuid));
        d.ecriture().remplacerBulletins(tenantId, cycle.id(), r.bulletins());
        return new Preparation(cycle, r.bulletins(), r.sansDossier());
    }
}
