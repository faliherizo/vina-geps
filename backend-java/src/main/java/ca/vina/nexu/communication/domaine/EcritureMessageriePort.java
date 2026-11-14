package ca.vina.nexu.communication.domaine;

import java.util.List;

/** Port d'écriture de la messagerie : chaque geste déjà décidé par l'application, rangé tel quel. */
public interface EcritureMessageriePort {

    record NouvelleConversation(String id, String sujet, String type, String groupeId, String creePar, String creeLe) { }

    record NouvelleParticipation(String id, String conversationId, String personId, String role, String depuis) { }

    /** {@code cle} désigne le contenu stocké de la pièce, distinct de son identifiant public ; {@code contenu} est le fichier. */
    record NouvellePiece(String id, String nom, String typeMime, long taille, String cle, byte[] contenu) { }

    /** {@code adresse} : tout le fil, ou une seule personne — décidé et validé par l'application. */
    record NouveauMessage(String id, String conversationId, String auteurId, String corps, String envoyeLe, List<NouvellePiece> pieces,
                          Messagerie.Adresse adresse) { }

    record NouvelleReaction(String id, String messageId, String personId, Reaction reaction, String poseeLe) { }

    record Reception(String id, String messageId, String personId, String recuLe) { }

    /** La version remplacée (son numéro, son texte, quand elle avait été écrite) et le nouveau texte. */
    record Modification(String versionId, String messageId, int version, String ancienCorps, String ecritLe, String nouveauCorps, String le) { }

    record Lecture(String id, String messageId, String personId, String luLe) { }

    void creer(String tenantId, NouvelleConversation conversation);

    void ajouter(String tenantId, NouvelleParticipation participation);

    /** Clôt la participation active ; faux si la personne ne participait pas. */
    boolean terminer(String tenantId, String conversationId, String personId, String jusqua);

    void archiver(String tenantId, String conversationId, String le);

    void envoyer(String tenantId, NouveauMessage message);

    /** Une lecture déjà notée n'est pas notée deux fois. */
    void marquerLu(String tenantId, Lecture lecture);

    void poserReaction(String tenantId, NouvelleReaction reaction);

    /** Une réception déjà notée n'est pas notée deux fois. */
    void accuserReception(String tenantId, Reception reception);

    /** Range la version remplacée, puis le nouveau texte et l'heure de modification. */
    void modifier(String tenantId, Modification modification);

    void supprimer(String tenantId, String messageId, String le, String par);

    /** Retire la réaction de cette personne ; faux si elle ne l'avait pas posée. */
    boolean retirerReaction(String tenantId, String messageId, String personId, Reaction reaction);
}
