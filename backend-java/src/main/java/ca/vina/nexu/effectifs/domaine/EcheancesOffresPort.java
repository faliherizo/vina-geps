package ca.vina.nexu.effectifs.domaine;

/**
 * Ce que l'offre en cascade demande à l'ordonnanceur (AD-07) : réveiller l'offre à l'échéance de sa proposition,
 * oublier ce réveil quand l'offre se ferme. Planifier se fait dans la transaction de la proposition : une proposition
 * sans réveil n'expirerait qu'au prochain passage d'un lecteur.
 */
public interface EcheancesOffresPort {

    /** Ce que l'ordonnanceur appelle à l'échéance ; l'instant est celui du tic, jamais l'horloge. */
    @FunctionalInterface
    interface Executeur {
        void executer(String tenantId, String offreId, String maintenant);
    }

    /** Planifie le réveil, ou en remplace l'échéance s'il attend déjà. */
    void planifier(String tenantId, String offreId, String echeance);

    void annuler(String tenantId, String offreId);

    void enregistrerExecuteur(Executeur executeur);
}
