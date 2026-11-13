package ca.vina.nexu.communication.domaine;

import java.util.List;

/** Port d'écriture de la messagerie : chaque geste déjà décidé par l'application, rangé tel quel. */
public interface EcritureMessageriePort {

    record NouvelleConversation(String id, String sujet, String type, String groupeId, String creePar, String creeLe) { }

    record NouvelleParticipation(String id, String conversationId, String personId, String role, String depuis) { }

    /** {@code cle} désigne le contenu stocké de la pièce, distinct de son identifiant public. */
    record NouvellePiece(String id, String nom, String typeMime, long taille, String cle) { }

    record NouveauMessage(String id, String conversationId, String auteurId, String corps, String envoyeLe, List<NouvellePiece> pieces) { }

    record Lecture(String id, String messageId, String personId, String luLe) { }

    void creer(String tenantId, NouvelleConversation conversation);

    void ajouter(String tenantId, NouvelleParticipation participation);

    /** Clôt la participation active ; faux si la personne ne participait pas. */
    boolean terminer(String tenantId, String conversationId, String personId, String jusqua);

    void archiver(String tenantId, String conversationId, String le);

    void envoyer(String tenantId, NouveauMessage message);

    /** Une lecture déjà notée n'est pas notée deux fois. */
    void marquerLu(String tenantId, Lecture lecture);
}
