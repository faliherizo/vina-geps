package ca.vina.nexu.communication.application.commandes;

import ca.vina.nexu.communication.domaine.Appelant;
import ca.vina.nexu.communication.domaine.BrouillonsMessages;
import ca.vina.nexu.communication.domaine.ConversationsPourListesPort;
import ca.vina.nexu.communication.domaine.EcritureBrouillonsMessagesPort;
import ca.vina.nexu.communication.domaine.Messagerie;
import ca.vina.nexu.communication.domaine.RefusCommunication;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.temps.Iso;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/** Enregistrer ou effacer son brouillon (BF-038). Aucune route ne prend d'identité : un brouillon est celui de l'appelant. */
public final class GestionnaireBrouillonsMessages {

    private final ConversationsPourListesPort conversations;
    private final EcritureBrouillonsMessagesPort ecriture;
    private final Sequence sequence;
    private final Clock horloge;

    public GestionnaireBrouillonsMessages(ConversationsPourListesPort conversations, EcritureBrouillonsMessagesPort ecriture, Sequence sequence, Clock horloge) {
        this.conversations = conversations;
        this.ecriture = ecriture;
        this.sequence = sequence;
        this.horloge = horloge;
    }

    /**
     * On n'écrit un brouillon que là où l'on pourrait écrire un message ; un corps vide l'efface —
     * une coquille vide réapparaîtrait sinon comme « brouillon présent » à la reprise.
     */
    public Optional<BrouillonsMessages.Brouillon> enregistrer(Appelant a, String conversationId, String corps) {
        Messagerie.Conversation c = conversations.lire(a.tenantId(), conversationId);
        String now = Iso.de(horloge.instant());
        if (!Messagerie.peutEcrire(c, a.acteurId(), now)) {
            throw RefusCommunication.invalide("vous ne participez pas à cette conversation");
        }
        List<String> erreurs = BrouillonsMessages.valider(corps);
        if (!erreurs.isEmpty()) {
            throw RefusCommunication.invalide(String.join(" · ", erreurs));
        }
        String texte = corps.trim();
        if (texte.isEmpty()) {
            ecriture.supprimer(a.tenantId(), a.acteurId(), conversationId);
            return Optional.empty();
        }
        BrouillonsMessages.Brouillon b = new BrouillonsMessages.Brouillon(conversationId, texte, now);
        ecriture.enregistrer(a.tenantId(), a.acteurId(), sequence.uuid(), b);
        return Optional.of(b);
    }

    public boolean supprimer(Appelant a, String conversationId) {
        return ecriture.supprimer(a.tenantId(), a.acteurId(), conversationId) > 0;
    }
}
