package ca.vina.nexu.communication.domaine;

import java.util.List;
import java.util.Optional;

/** Port de lecture des brouillons : toujours ceux de l'auteur, jamais d'un autre. */
public interface LectureBrouillonsMessagesPort {

    Optional<BrouillonsMessages.Brouillon> lire(String tenantId, String auteurId, String conversationId);

    /** Tous les brouillons de l'auteur, le plus récemment modifié d'abord. */
    List<BrouillonsMessages.Brouillon> deLAuteur(String tenantId, String auteurId);
}
