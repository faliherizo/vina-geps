package ca.vina.nexu.communication.infrastructure.persistance;

import ca.vina.nexu.communication.domaine.EcritureMessageriePort;
import ca.vina.nexu.communication.domaine.LectureMessageriePort;
import ca.vina.nexu.communication.domaine.Messagerie;
import ca.vina.nexu.communication.domaine.Reaction;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** La messagerie sur SQLite : lire et ranger, sans règle — les gestes sont décidés par l'application. */
@Repository
public class DepotMessagerie implements LectureMessageriePort, EcritureMessageriePort {

    private final JdbcClient jdbc;
    private final CoffrePiecesJointes coffre;

    /** Le coffre par défaut : le contenu dans la base, qui le suit partout où elle va. */
    public DepotMessagerie(JdbcClient jdbc) {
        this(jdbc, new CoffreEnBase());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DepotMessagerie(JdbcClient jdbc, CoffrePiecesJointes coffre) {
        this.jdbc = jdbc;
        this.coffre = coffre;
    }

    private List<Messagerie.Participation> participants(String tenantId, String conversationId) {
        return jdbc.sql("SELECT personId, role, depuis, jusqua FROM msg_participants WHERE tenantId = ? AND conversationId = ? ORDER BY depuis").params(tenantId, conversationId)
                .query((rs, i) -> new Messagerie.Participation(rs.getString("personId"), rs.getString("role"), rs.getString("depuis"), rs.getString("jusqua"))).list();
    }

    /** L'en-tête d'une conversation, lu seul : ses participants se lisent après, la première lecture refermée. */
    private record Entete(String id, String sujet, String type, String groupeId, String creePar, String createdAt, String archiveeLe) { }

    /**
     * Aucune lecture n'est lancée depuis le lecteur d'une autre : la connexion de la première resterait
     * tenue pendant que la seconde en attend une. Avec un pool de quatre connexions, quatre requêtes
     * simultanées suffisaient à le vider, et chacune attendait 30 s la connexion que les autres tenaient
     * (balayage JMeter du 13 novembre).
     */
    @Override
    public Optional<Messagerie.Conversation> conversation(String tenantId, String conversationId) {
        return jdbc.sql("SELECT id, sujet, type, groupeId, creePar, createdAt, archiveeLe FROM msg_conversations WHERE tenantId = ? AND id = ?").params(tenantId, conversationId)
                .query((rs, i) -> new Entete(rs.getString("id"), rs.getString("sujet"), rs.getString("type"), rs.getString("groupeId"), rs.getString("creePar"),
                        rs.getString("createdAt"), rs.getString("archiveeLe")))
                .optional()
                .map(e -> new Messagerie.Conversation(e.id(), e.sujet(), e.type(), e.groupeId(), e.creePar(), e.createdAt(), e.archiveeLe(), participants(tenantId, e.id())));
    }

    @Override
    public List<String> conversationsDe(String tenantId, String personId) {
        return jdbc.sql("SELECT DISTINCT conversationId FROM msg_participants WHERE tenantId = ? AND personId = ?").params(tenantId, personId).query(String.class).list();
    }

    private record Ligne(String id, String conversationId, String auteurId, String corps, String envoyeLe, String editeLe, Messagerie.Adresse adresse,
                         Messagerie.Suppression suppression) { }

    private record PieceDuMessage(String messageId, Messagerie.PieceJointe piece) { }

    /** Les messages, puis leurs pièces jointes d'une seule lecture : jamais une requête par message, jamais une lecture imbriquée. */
    @Override
    public List<Messagerie.Message> messages(String tenantId, String conversationId) {
        String filtre = conversationId != null ? " AND conversationId = ?" : "";
        Object[] parametres = conversationId != null ? new Object[]{tenantId, conversationId} : new Object[]{tenantId};
        List<Ligne> lignes = jdbc.sql("SELECT id, conversationId, auteurId, corps, envoyeLe, editeLe, priveA, repondAuMessageId, supprimeLe, supprimePar "
                        + "FROM msg_messages WHERE tenantId = ?"
                        + filtre + " ORDER BY envoyeLe")
                .params(parametres)
                .query((rs, i) -> new Ligne(rs.getString("id"), rs.getString("conversationId"), rs.getString("auteurId"), rs.getString("corps"), rs.getString("envoyeLe"),
                        rs.getString("editeLe"), new Messagerie.Adresse(rs.getString("priveA"), rs.getString("repondAuMessageId")),
                        rs.getString("supprimeLe") == null ? null : new Messagerie.Suppression(rs.getString("supprimeLe"), rs.getString("supprimePar"))))
                .list();
        Map<String, List<Messagerie.PieceJointe>> pieces = jdbc.sql("SELECT p.messageId, p.id, p.nom, p.typeMime, p.taille FROM msg_pieces_jointes p "
                        + "JOIN msg_messages m ON m.id = p.messageId AND m.tenantId = p.tenantId WHERE p.tenantId = ?" + filtre.replace("conversationId", "m.conversationId"))
                .params(parametres)
                .query((rs, i) -> new PieceDuMessage(rs.getString("messageId"), new Messagerie.PieceJointe(rs.getString("id"), rs.getString("nom"), rs.getString("typeMime"), rs.getLong("taille"))))
                .list().stream().collect(Collectors.groupingBy(PieceDuMessage::messageId, Collectors.mapping(PieceDuMessage::piece, Collectors.toList())));
        return lignes.stream().map(l -> new Messagerie.Message(l.id(), l.conversationId(), l.auteurId(), l.corps(), l.envoyeLe(), l.editeLe(),
                pieces.getOrDefault(l.id(), List.of()), l.adresse()).avecSuppression(l.suppression())).toList();
    }

    @Override
    public Set<String> lusPar(String tenantId, String personId) {
        return new HashSet<>(jdbc.sql("SELECT messageId FROM msg_lectures WHERE tenantId = ? AND personId = ?").params(tenantId, personId).query(String.class).list());
    }

    @Override
    public List<Map<String, Object>> lecteurs(String tenantId, String messageId) {
        return jdbc.sql("SELECT personId, luLe FROM msg_lectures WHERE tenantId = ? AND messageId = ? ORDER BY luLe").params(tenantId, messageId).query().listOfRows();
    }

    @Override
    public Optional<PieceStockee> piece(String tenantId, String pieceId) {
        return jdbc.sql("SELECT p.id, p.nom, p.typeMime, p.taille, m.conversationId, m.envoyeLe, m.auteurId, m.priveA, m.supprimeLe FROM msg_pieces_jointes p "
                        + "JOIN msg_messages m ON m.id = p.messageId AND m.tenantId = p.tenantId WHERE p.tenantId = ? AND p.id = ?")
                .params(tenantId, pieceId)
                .query((rs, i) -> new PieceStockee(rs.getString("id"), rs.getString("nom"), rs.getString("typeMime"), rs.getLong("taille"),
                        rs.getString("conversationId"), rs.getString("envoyeLe"), rs.getString("auteurId"), rs.getString("priveA"),
                        rs.getString("supprimeLe") != null))
                .optional();
    }

    private record Rangement(String cle, String contenu) { }

    @Override
    public Optional<byte[]> contenuPiece(String tenantId, String pieceId) {
        return jdbc.sql("SELECT cle, contenu FROM msg_pieces_jointes WHERE tenantId = ? AND id = ?").params(tenantId, pieceId)
                .query((rs, i) -> new Rangement(rs.getString("cle"), rs.getString("contenu"))).optional()
                .flatMap(r -> coffre.lire(tenantId, r.cle(), r.contenu()));
    }

    @Override
    public Optional<String> conversationDuMessage(String tenantId, String messageId) {
        return jdbc.sql("SELECT conversationId FROM msg_messages WHERE tenantId = ? AND id = ?").params(tenantId, messageId).query(String.class).optional();
    }

    private List<Accuse> accuses(String table, String tenantId, String conversationId) {
        return jdbc.sql("SELECT a.messageId, a.personId FROM " + table + " a JOIN msg_messages m ON m.id = a.messageId AND m.tenantId = a.tenantId "
                        + "WHERE a.tenantId = ? AND m.conversationId = ?")
                .params(tenantId, conversationId).query((rs, i) -> new Accuse(rs.getString("messageId"), rs.getString("personId"))).list();
    }

    @Override
    public List<Accuse> receptions(String tenantId, String conversationId) {
        return accuses("msg_distributions", tenantId, conversationId);
    }

    @Override
    public List<Accuse> lectures(String tenantId, String conversationId) {
        return accuses("msg_lectures", tenantId, conversationId);
    }

    @Override
    public Set<String> recusPar(String tenantId, String personId) {
        return new HashSet<>(jdbc.sql("SELECT messageId FROM msg_distributions WHERE tenantId = ? AND personId = ?").params(tenantId, personId)
                .query(String.class).list());
    }

    @Override
    public List<VersionStockee> versions(String tenantId, String messageId) {
        return jdbc.sql("SELECT version, corps, ecritLe, remplaceLe FROM msg_message_versions WHERE tenantId = ? AND messageId = ? ORDER BY version")
                .params(tenantId, messageId)
                .query((rs, i) -> new VersionStockee(rs.getInt("version"), rs.getString("corps"), rs.getString("ecritLe"), rs.getString("remplaceLe")))
                .list();
    }

    @Override
    public List<Messagerie.ReactionPosee> reactions(String tenantId, String conversationId) {
        return jdbc.sql("SELECT r.messageId, r.personId, r.reaction FROM msg_reactions r JOIN msg_messages m ON m.id = r.messageId AND m.tenantId = r.tenantId "
                        + "WHERE r.tenantId = ? AND m.conversationId = ? ORDER BY r.poseeLe")
                .params(tenantId, conversationId)
                .query((rs, i) -> new Messagerie.ReactionPosee(rs.getString("messageId"), rs.getString("personId"), Reaction.de(rs.getString("reaction"))))
                .list();
    }

    @Override
    public void creer(String tenantId, NouvelleConversation c) {
        jdbc.sql("INSERT INTO msg_conversations (id, tenantId, sujet, type, groupeId, creePar, createdAt) VALUES (?,?,?,?,?,?,?)")
                .params(c.id(), tenantId, c.sujet(), c.type(), c.groupeId(), c.creePar(), c.creeLe()).update();
    }

    @Override
    public void ajouter(String tenantId, NouvelleParticipation p) {
        jdbc.sql("INSERT INTO msg_participants (id, tenantId, conversationId, personId, role, depuis) VALUES (?,?,?,?,?,?)")
                .params(p.id(), tenantId, p.conversationId(), p.personId(), p.role(), p.depuis()).update();
    }

    @Override
    public boolean terminer(String tenantId, String conversationId, String personId, String jusqua) {
        return jdbc.sql("UPDATE msg_participants SET jusqua = ? WHERE tenantId = ? AND conversationId = ? AND personId = ? AND jusqua IS NULL")
                .params(jusqua, tenantId, conversationId, personId).update() > 0;
    }

    @Override
    public void archiver(String tenantId, String conversationId, String le) {
        jdbc.sql("UPDATE msg_conversations SET archiveeLe = ? WHERE tenantId = ? AND id = ?").params(le, tenantId, conversationId).update();
    }

    @Override
    public void envoyer(String tenantId, NouveauMessage m) {
        jdbc.sql("INSERT INTO msg_messages (id, tenantId, conversationId, auteurId, corps, envoyeLe, priveA, repondAuMessageId) VALUES (?,?,?,?,?,?,?,?)")
                .params(m.id(), tenantId, m.conversationId(), m.auteurId(), m.corps(), m.envoyeLe(), m.adresse().priveA(), m.adresse().repondAuMessageId()).update();
        for (NouvellePiece p : m.pieces()) {
            // Le contenu se range d'abord (sur le volume, s'il y vit) ; une ligne qui ne s'écrit pas défait ce dépôt.
            coffre.deposer(tenantId, p.cle(), p.contenu());
            try {
                jdbc.sql("INSERT INTO msg_pieces_jointes (id, tenantId, messageId, nom, typeMime, taille, cle, televerseLe, contenu) VALUES (?,?,?,?,?,?,?,?,?)")
                        .params(p.id(), tenantId, m.id(), p.nom(), p.typeMime(), p.taille(), p.cle(), m.envoyeLe(), coffre.enLigne(p.contenu()).orElse(null)).update();
            } catch (RuntimeException e) {
                coffre.retirer(tenantId, p.cle());
                throw e;
            }
        }
    }

    @Override
    public void marquerLu(String tenantId, Lecture l) {
        jdbc.sql("INSERT INTO msg_lectures (id, tenantId, messageId, personId, luLe) VALUES (?,?,?,?,?) ON CONFLICT (tenantId, messageId, personId) DO NOTHING")
                .params(l.id(), tenantId, l.messageId(), l.personId(), l.luLe()).update();
    }

    @Override
    public void poserReaction(String tenantId, NouvelleReaction r) {
        jdbc.sql("INSERT INTO msg_reactions (id, tenantId, messageId, personId, reaction, poseeLe) VALUES (?,?,?,?,?,?) "
                        + "ON CONFLICT (tenantId, messageId, personId, reaction) DO NOTHING")
                .params(r.id(), tenantId, r.messageId(), r.personId(), r.reaction().code(), r.poseeLe()).update();
    }

    @Override
    public boolean retirerReaction(String tenantId, String messageId, String personId, Reaction reaction) {
        return jdbc.sql("DELETE FROM msg_reactions WHERE tenantId = ? AND messageId = ? AND personId = ? AND reaction = ?")
                .params(tenantId, messageId, personId, reaction.code()).update() > 0;
    }

    @Override
    public void accuserReception(String tenantId, Reception r) {
        jdbc.sql("INSERT INTO msg_distributions (id, tenantId, messageId, personId, recuLe) VALUES (?,?,?,?,?) "
                        + "ON CONFLICT (tenantId, messageId, personId) DO NOTHING")
                .params(r.id(), tenantId, r.messageId(), r.personId(), r.recuLe()).update();
    }

    @Override
    public void modifier(String tenantId, Modification m) {
        jdbc.sql("INSERT INTO msg_message_versions (id, tenantId, messageId, version, corps, ecritLe, remplaceLe) VALUES (?,?,?,?,?,?,?)")
                .params(m.versionId(), tenantId, m.messageId(), m.version(), m.ancienCorps(), m.ecritLe(), m.le()).update();
        jdbc.sql("UPDATE msg_messages SET corps = ?, editeLe = ? WHERE tenantId = ? AND id = ?").params(m.nouveauCorps(), m.le(), tenantId, m.messageId()).update();
    }

    @Override
    public void supprimer(String tenantId, String messageId, String le, String par) {
        jdbc.sql("UPDATE msg_messages SET supprimeLe = ?, supprimePar = ? WHERE tenantId = ? AND id = ?").params(le, par, tenantId, messageId).update();
    }
}
