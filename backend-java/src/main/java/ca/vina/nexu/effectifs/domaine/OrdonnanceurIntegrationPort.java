package ca.vina.nexu.effectifs.domaine;

/**
 * Port vers l'ordonnanceur : la livraison d'un message déposé est une tâche différée, retentée sur une panne passagère et
 * abandonnée après trois essais. Planifier se fait dans la transaction du dépôt : un message sans tâche ne partirait jamais.
 */
public interface OrdonnanceurIntegrationPort {

    /** La tâche que l'ordonnanceur remet au livreur : le message visé et le nombre d'essais déjà faits. */
    record TacheLivraison(String tenantId, String messageId, int tentatives) { }

    /** Ce que l'ordonnanceur appelle ; une panne passagère <strong>lève</strong>, et l'ordonnanceur retente. */
    @FunctionalInterface
    interface Livreur {
        void livrer(TacheLivraison tache, String maintenant);
    }

    void planifierLivraison(String tenantId, String messageId, String echeanceLe, String creePar);

    void enregistrerLivreur(Livreur livreur);
}
