package ca.vina.nexu.paie.application.commandes;

import ca.vina.nexu.paie.application.requetes.LectureVariablesPaie;
import ca.vina.nexu.paie.domaine.EcritureReglesPaiePort;
import ca.vina.nexu.paie.domaine.ReglePaie;
import ca.vina.nexu.paie.domaine.Trace;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;

/**
 * Tenir les règles de paie variable de l'organisme (ADR 0084). Désactiver une règle, c'est l'enregistrer inactive : une
 * paie traitée garde ses gains figés, et l'historique des règles reste lisible.
 */
public class GestionnaireReglesPaie {

    private final LectureVariablesPaie lecture;
    private final EcritureReglesPaiePort ecriture;
    private final Sequence sequence;

    public GestionnaireReglesPaie(LectureVariablesPaie lecture, EcritureReglesPaiePort ecriture, Sequence sequence) {
        this.lecture = lecture;
        this.ecriture = ecriture;
        this.sequence = sequence;
    }

    public ReglePaie creer(Trace trace, ReglePaie regle) {
        ReglePaie creee = regle.avecId(sequence.uuid());
        ecriture.enregistrer(trace, creee);
        return creee;
    }

    public ReglePaie modifier(Trace trace, String id, ReglePaie regle) {
        lecture.exigerRegle(trace.tenantId(), id);
        ReglePaie modifiee = regle.avecId(id);
        ecriture.enregistrer(trace, modifiee);
        return modifiee;
    }
}
