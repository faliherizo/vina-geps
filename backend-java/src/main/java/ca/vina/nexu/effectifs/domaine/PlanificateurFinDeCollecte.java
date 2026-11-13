package ca.vina.nexu.effectifs.domaine;

/**
 * Ce que le cycle d'une période demande à l'ordonnanceur (P-GH-4) : planifier la fin de collecte à l'ouverture de la
 * collecte, l'annuler à la publication. Étroit à dessein : le changement d'état n'a rien à lire de l'ordonnanceur.
 */
public interface PlanificateurFinDeCollecte {

    /** La tâche à planifier : la période visée, l'instant d'échéance, la date de fin de collecte, qui l'a demandé. */
    record Echeance(String periodeId, String echeanceLe, String finCollecte, String creePar) { }

    void planifier(String tenantId, Echeance e);

    /** Annule ce qui attend encore pour cette période. */
    void annuler(String tenantId, String periodeId);
}
