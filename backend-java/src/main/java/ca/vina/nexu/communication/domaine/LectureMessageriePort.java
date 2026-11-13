package ca.vina.nexu.communication.domaine;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Port de lecture de la messagerie : ce qui est stocké, sans règle. La portée de lecture, les droits
 * et les comptes vivent dans {@link Messagerie} et dans l'application, jamais dans l'adaptateur.
 */
public interface LectureMessageriePort {

    /** Une pièce jointe telle qu'elle est rangée, avec le message qui la porte (pour juger la portée). */
    record PieceStockee(String id, String nom, String typeMime, long taille, String conversationId, String envoyeLe) { }

    Optional<Messagerie.Conversation> conversation(String tenantId, String conversationId);

    /** Les conversations où la personne a participé, même si elle en est sortie depuis. */
    List<String> conversationsDe(String tenantId, String personId);

    /** Les messages d'une conversation — ou de tout l'établissement si {@code conversationId} est nul —, dans l'ordre d'envoi. */
    List<Messagerie.Message> messages(String tenantId, String conversationId);

    Set<String> lusPar(String tenantId, String personId);

    List<Map<String, Object>> lecteurs(String tenantId, String messageId);

    Optional<PieceStockee> piece(String tenantId, String pieceId);
}
