package ca.vina.nexu.effectifs.domaine;

import java.util.List;

/**
 * Une absence d'effectifs telle que la base la garde (lot R5) : la demande, sa décision automatique conservée
 * (action et motifs des règles), la décision humaine éventuelle et la dérogation. {@code ressourceNom} vient de
 * la ressource, pour l'écran ; il est nul sur une absence pas encore enregistrée.
 */
public record AbsenceEffectifs(String id, String ressourceId, String ressourceNom, String uniteCode, String codeAbsence, String du, String au,
                               String motif, AbsencesEffectifs.Statut statut, ActionRegle actionRegles, List<Regles.Motif> motifsRegles,
                               Trace trace) {

    /** Qui a déclaré, qui a décidé, et la dérogation éventuelle. */
    public record Trace(String declareeLe, String declareePar, String decideeLe, String decideePar, String motifDecision,
                        String derogationCode, String derogationMotif) { }

    public AbsenceEffectifs {
        motifsRegles = List.copyOf(motifsRegles);
    }
}
