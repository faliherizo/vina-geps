package ca.vina.nexu.effectifs.domaine;

import java.util.Optional;

/**
 * Port vers l'ordonnanceur pour la fin de collecte automatique (P-GH-4) : l'ouverture de la collecte planifie une tâche
 * à l'échéance, la publication l'annule ; l'exécuteur ferme la collecte et attribue. Planifier se fait dans la
 * transaction du changement d'état : une collecte ouverte sans tâche ne se fermerait jamais d'elle-même.
 */
public interface FinDeCollectePort extends PlanificateurFinDeCollecte {

    /** La dernière tâche de fin de collecte d'une période : quand elle part, où elle en est (statut de l'ordonnanceur). */
    record Tache(String echeanceLe, String statut) { }

    /** Le bilan de la dernière attribution automatique consignée au journal par le mécanisme. */
    record Bilan(String le, int attribuees, int restees) { }

    /** Ce que l'ordonnanceur appelle à l'échéance ; l'instant est celui du tic, jamais l'horloge. */
    @FunctionalInterface
    interface Executeur {
        void executer(String tenantId, String periodeId, String maintenant);
    }

    Optional<Tache> derniereTache(String tenantId, String periodeId);

    /** {@code acteurId} : le mécanisme au nom duquel l'attribution s'est consignée, qu'elle soit partie seule ou sur demande. */
    Optional<Bilan> dernierBilan(String tenantId, String periodeId, String acteurId);

    void enregistrerExecuteur(Executeur executeur);
}
