package ca.vina.nexu.communication.application.requetes;

import ca.vina.nexu.communication.domaine.Appelant;
import ca.vina.nexu.communication.domaine.BrouillonsMessages;
import ca.vina.nexu.communication.domaine.LectureBrouillonsMessagesPort;

import java.util.List;
import java.util.Optional;

/** Le brouillon de l'appelant, ou rien : un brouillon n'est jamais lisible d'un tiers. */
public final class LectureBrouillonsMessages {

    private final LectureBrouillonsMessagesPort lecture;

    public LectureBrouillonsMessages(LectureBrouillonsMessagesPort lecture) {
        this.lecture = lecture;
    }

    public Optional<BrouillonsMessages.Brouillon> lire(Appelant a, String conversationId) {
        return lecture.lire(a.tenantId(), a.acteurId(), conversationId);
    }

    /** Les brouillons en cours de l'appelant, pour reprendre un message laissé en plan. */
    public List<BrouillonsMessages.Brouillon> miens(Appelant a) {
        return lecture.deLAuteur(a.tenantId(), a.acteurId());
    }
}
