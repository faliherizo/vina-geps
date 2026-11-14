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

    /** Une pièce jointe telle qu'elle est rangée, avec le message qui la porte (pour juger la portée, privé compris). */
    record PieceStockee(String id, String nom, String typeMime, long taille, String conversationId, String envoyeLe, String auteurId, String priveA,
                        boolean messageSupprime) { }

    /** Un accusé — réception ou lecture — d'un message par une personne. */
    record Accuse(String messageId, String personId) { }

    /** Une version antérieure d'un message modifié : son texte, quand il avait été écrit, quand il a été remplacé. */
    record VersionStockee(int version, String corps, String ecritLe, String remplaceLe) { }

    Optional<Messagerie.Conversation> conversation(String tenantId, String conversationId);

    /** Les conversations où la personne a participé, même si elle en est sortie depuis. */
    List<String> conversationsDe(String tenantId, String personId);

    /** Les messages d'une conversation — ou de tout l'établissement si {@code conversationId} est nul —, dans l'ordre d'envoi. */
    List<Messagerie.Message> messages(String tenantId, String conversationId);

    Set<String> lusPar(String tenantId, String personId);

    List<Map<String, Object>> lecteurs(String tenantId, String messageId);

    Optional<PieceStockee> piece(String tenantId, String pieceId);

    /** Le fichier d'une pièce, là où l'hébergement le range ; vide s'il n'a pas été conservé. */
    Optional<byte[]> contenuPiece(String tenantId, String pieceId);

    /** La conversation d'un message, s'il existe dans l'établissement. */
    Optional<String> conversationDuMessage(String tenantId, String messageId);

    /** Les accusés de réception des messages d'une conversation. */
    List<Accuse> receptions(String tenantId, String conversationId);

    /** Les lectures des messages d'une conversation. */
    List<Accuse> lectures(String tenantId, String conversationId);

    /** Les messages que la personne a déjà reçus, pour n'accuser réception qu'une fois. */
    Set<String> recusPar(String tenantId, String personId);

    /** Les versions antérieures d'un message, de la plus ancienne à la plus récente. */
    List<VersionStockee> versions(String tenantId, String messageId);

    /** Les réactions posées sur les messages d'une conversation. */
    List<Messagerie.ReactionPosee> reactions(String tenantId, String conversationId);
}
