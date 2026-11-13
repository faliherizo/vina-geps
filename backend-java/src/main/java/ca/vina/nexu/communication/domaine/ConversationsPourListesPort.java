package ca.vina.nexu.communication.domaine;

import java.util.List;

/** Port : les conversations de la messagerie (L6), vues des listes et des brouillons. */
public interface ConversationsPourListesPort {

    /** La conversation ; introuvable, un refus de nature {@code INTROUVABLE}. */
    Messagerie.Conversation lire(String tenantId, String conversationId);

    /** Ouvre une conversation de groupe, avec la règle de création de la messagerie. */
    Messagerie.Conversation ouvrirGroupe(String tenantId, String acteurId, String sujet, List<String> participants);
}
