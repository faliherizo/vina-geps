package ca.vina.nexu.paie.application.commandes;

import ca.vina.nexu.paie.application.requetes.LectureVariablesPaie;
import ca.vina.nexu.paie.domaine.AnomalieTemps;
import ca.vina.nexu.paie.domaine.ControleTemps;
import ca.vina.nexu.paie.domaine.CyclePaie;
import ca.vina.nexu.paie.domaine.EcritureValidationsPort;
import ca.vina.nexu.paie.domaine.RefusPaie;
import ca.vina.nexu.paie.domaine.Trace;
import ca.vina.nexu.paie.domaine.UniteDeTravail;
import ca.vina.nexu.paie.domaine.ValidationTemps;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort.EtatFeuille;

import java.util.List;
import java.util.stream.Collectors;

/**
 * La validation des temps par la paie (ADR 0084) : valider une feuille approuvée, valider en lot celles qui n'ont aucune
 * anomalie, ou retourner une feuille à la personne avec ce qu'il faut corriger. Valider ne réapprouve rien : c'est le
 * contrôle de la paie, distinct de l'approbation du gestionnaire.
 */
public class GestionnaireValidationTemps {

    static final int MOTIF_MAXIMUM = 500;

    public record Dependances(LectureVariablesPaie lecture, HeuresApprouveesPort heures, EcritureValidationsPort ecriture,
                              UniteDeTravail unite, Sequence sequence) { }

    private final Dependances d;

    public GestionnaireValidationTemps(Dependances dependances) {
        this.d = dependances;
    }

    public ValidationTemps valider(Trace trace, String feuilleId) {
        ControleTemps.Ligne ligne = d.lecture().ligne(trace.tenantId(), feuilleId);
        if (!ligne.estValidable()) {
            throw RefusPaie.conflit("feuille non validable : " + motifDeRefus(ligne));
        }
        return consigner(trace, ligne, ValidationTemps.Decision.VALIDEE, null);
    }

    /** Valide d'un coup les feuilles approuvées de la période qui n'ont aucune anomalie et ne sont pas déjà validées. */
    public List<ValidationTemps> validerSansAnomalie(Trace trace, CyclePaie.Periode periode) {
        return d.lecture().controle(trace.tenantId(), periode).feuilles().stream()
                .filter(l -> l.estValidable() && l.anomalies().isEmpty() && !l.estValidee())
                .map(l -> consigner(trace, l, ValidationTemps.Decision.VALIDEE, null)).toList();
    }

    /** Rouvre la feuille dans les effectifs et consigne le retour, en une seule unité de travail. */
    public ValidationTemps retourner(Trace trace, String feuilleId, String motif) {
        String propre = motif == null ? "" : motif.strip();
        if (propre.isEmpty() || propre.length() > MOTIF_MAXIMUM) {
            throw RefusPaie.invalide("« motif » : dites ce qu'il faut corriger (au plus " + MOTIF_MAXIMUM + " caractères)");
        }
        ControleTemps.Ligne ligne = d.lecture().ligne(trace.tenantId(), feuilleId);
        if (ligne.feuille().etat() != EtatFeuille.APPROUVEE) {
            throw RefusPaie.conflit("seule une feuille approuvée, et pas encore payée, se retourne");
        }
        return d.unite().executer(() -> {
            rouvrir(trace, feuilleId, propre);
            return consigner(trace, ligne, ValidationTemps.Decision.RETOURNEE, propre);
        });
    }

    private void rouvrir(Trace trace, String feuilleId, String motif) {
        try {
            d.heures().retourner(new HeuresApprouveesPort.Verrou(trace.tenantId(), trace.acteur(), trace.instant(), null), feuilleId, motif);
        } catch (HeuresApprouveesPort.FeuilleNonApprouvee e) {
            throw RefusPaie.conflit(e.getMessage());
        }
    }

    private ValidationTemps consigner(Trace trace, ControleTemps.Ligne ligne, ValidationTemps.Decision decision, String motif) {
        String empreinte = ligne.feuille().heures().map(ValidationTemps::empreinteDe).orElse("");
        ValidationTemps v = new ValidationTemps(d.sequence().uuid(), ligne.feuille().feuilleId(), decision, empreinte, motif, trace.acteur(), trace.instant());
        d.ecriture().consigner(trace.tenantId(), v);
        return v;
    }

    private static String motifDeRefus(ControleTemps.Ligne ligne) {
        if (ligne.feuille().etat() == EtatFeuille.PAYEE) {
            return "elle est déjà payée";
        }
        return ligne.anomalies().stream().filter(AnomalieTemps::bloquante).map(AnomalieTemps::code).collect(Collectors.joining(", "));
    }
}
