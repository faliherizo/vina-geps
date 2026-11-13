package ca.vina.nexu.communication.infrastructure.persistance;

import ca.vina.nexu.communication.application.commandes.GestionnaireMessagerie;
import ca.vina.nexu.communication.application.requetes.LectureMessagerie;
import ca.vina.nexu.communication.domaine.Appelant;
import ca.vina.nexu.communication.domaine.ConversationsPourListesPort;
import ca.vina.nexu.communication.domaine.Messagerie;

import java.util.List;

/** Les conversations des listes et des brouillons passent par la messagerie : la règle de création n'existe qu'une fois. */
public final class AdaptateurConversationsPourListes implements ConversationsPourListesPort {

    private final LectureMessagerie lecture;
    private final GestionnaireMessagerie gestionnaire;

    public AdaptateurConversationsPourListes(LectureMessagerie lecture, GestionnaireMessagerie gestionnaire) {
        this.lecture = lecture;
        this.gestionnaire = gestionnaire;
    }

    @Override
    public Messagerie.Conversation lire(String tenantId, String conversationId) {
        return lecture.conversation(tenantId, conversationId);
    }

    @Override
    public Messagerie.Conversation ouvrirGroupe(String tenantId, String acteurId, String sujet, List<String> participants) {
        // Type omis : la messagerie retient alors « groupe », et une diffusion n'est jamais un échange à deux.
        return gestionnaire.creer(new Appelant(tenantId, acteurId, null, null), new GestionnaireMessagerie.DemandeConversation(sujet, null, null, participants));
    }
}
