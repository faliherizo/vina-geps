package ca.vina.nexu.communication.domaine;

/** Port d'écriture des brouillons. */
public interface EcritureBrouillonsMessagesPort {

    /** Remplace le brouillon de l'auteur dans la conversation, ou le crée. */
    void enregistrer(String tenantId, String auteurId, String nouvelId, BrouillonsMessages.Brouillon brouillon);

    /** Le nombre de brouillons effacés (0 ou 1). */
    int supprimer(String tenantId, String auteurId, String conversationId);
}
