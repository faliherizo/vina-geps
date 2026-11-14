package ca.vina.nexu.communication.application.requetes;

import ca.vina.nexu.communication.domaine.AcheminementPort;
import ca.vina.nexu.communication.domaine.Appelant;
import ca.vina.nexu.communication.domaine.JournalCommunicationPort;
import ca.vina.nexu.communication.domaine.LectureMessageriePort;
import ca.vina.nexu.communication.domaine.Messagerie;
import ca.vina.nexu.communication.domaine.RefusCommunication;
import ca.vina.nexu.partage.temps.Iso;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ce qu'on lit de la messagerie : conversations, messages, non-lus, recherche, pièces jointes et
 * acheminement. Tout passe par la <b>portée de lecture</b> de l'appelant ({@link Messagerie}) : une
 * conversation hors portée répond comme une conversation inexistante.
 */
public final class LectureMessagerie {

    static final String CONVERSATION_INTROUVABLE = "conversation introuvable";
    static final String PIECE_INTROUVABLE = "pièce jointe introuvable";
    static final String MESSAGE_INTROUVABLE = "message introuvable";
    static final String CONTENU_ABSENT = "le fichier de cette pièce jointe n'a pas été conservé";
    /** L'événement dont l'acheminement s'affiche à côté de chaque participant. */
    static final String EVENEMENT_MESSAGE = "message";

    private final LectureMessageriePort lecture;
    private final AcheminementPort acheminement;
    private final JournalCommunicationPort journal;
    private final Clock horloge;

    public LectureMessagerie(LectureMessageriePort lecture, AcheminementPort acheminement, JournalCommunicationPort journal, Clock horloge) {
        this.lecture = lecture;
        this.acheminement = acheminement;
        this.journal = journal;
        this.horloge = horloge;
    }

    String maintenant() {
        return Iso.de(horloge.instant());
    }

    public Messagerie.Conversation conversation(String tenantId, String conversationId) {
        return lecture.conversation(tenantId, conversationId).orElseThrow(() -> RefusCommunication.introuvable(CONVERSATION_INTROUVABLE));
    }

    /** Une conversation à laquelle l'appelant participe ou a participé ; toute autre est introuvable, pas interdite. */
    public Messagerie.Conversation conversationLisible(Appelant a, String conversationId) {
        if (!portee(a).connait(conversationId)) {
            throw RefusCommunication.introuvable(CONVERSATION_INTROUVABLE);
        }
        return conversation(a.tenantId(), conversationId);
    }

    public List<Messagerie.Conversation> conversations(Appelant a) {
        List<Messagerie.Conversation> liste = new ArrayList<>(lecture.conversationsDe(a.tenantId(), a.acteurId()).stream()
                .map(id -> conversation(a.tenantId(), id)).toList());
        liste.sort(Comparator.comparing(Messagerie.Conversation::createdAt).reversed());
        return liste;
    }

    private Messagerie.PorteeLecture portee(Appelant a) {
        return Messagerie.porteeDeLecture(conversations(a), a.acteurId());
    }

    /** « L'ai-je lu ? » voyage avec le message : un fil de quarante messages ne fait pas quarante requêtes. */
    public List<Messagerie.Message> messages(Appelant a, String conversationId) {
        Messagerie.PorteeLecture portee = portee(a);
        List<Messagerie.Message> visibles = lecture.messages(a.tenantId(), conversationId).stream().filter(m -> Messagerie.dansLaPortee(portee, m)).toList();
        if (visibles.isEmpty() && !portee.connait(conversationId)) {
            throw RefusCommunication.introuvable(CONVERSATION_INTROUVABLE);
        }
        Set<String> lus = lecture.lusPar(a.tenantId(), a.acteurId());
        List<Messagerie.ReactionPosee> reactions = lecture.reactions(a.tenantId(), conversationId);
        Acheminements acheminements = acheminements(a, conversationId, visibles);
        return visibles.stream().map(m -> m.avecLecture(m.auteurId().equals(a.acteurId()) || lus.contains(m.id()))
                .avecReactions(Messagerie.compterReactions(m.id(), reactions, a.acteurId()))
                .avecAcheminement(acheminements.de(m))).toList();
    }

    /** Ce qu'il faut pour dire à l'auteur où en sont ses messages : le fil, ses réceptions, ses lectures. */
    private record Acheminements(Messagerie.Conversation fil, Map<String, Set<String>> recus, Map<String, Set<String>> lus, String auteur) {
        /** Seul l'auteur voit l'acheminement de son message ; un message supprimé n'en a plus. */
        Messagerie.Acheminement de(Messagerie.Message m) {
            if (fil == null || !m.auteurId().equals(auteur) || m.supprime()) {
                return null;
            }
            return Messagerie.acheminement(fil, m, recus.getOrDefault(m.id(), Set.of()), lus.getOrDefault(m.id(), Set.of()));
        }
    }

    private Acheminements acheminements(Appelant a, String conversationId, List<Messagerie.Message> visibles) {
        if (visibles.stream().noneMatch(m -> m.auteurId().equals(a.acteurId()))) {
            return new Acheminements(null, Map.of(), Map.of(), a.acteurId());
        }
        return new Acheminements(conversation(a.tenantId(), conversationId), parMessage(lecture.receptions(a.tenantId(), conversationId)),
                parMessage(lecture.lectures(a.tenantId(), conversationId)), a.acteurId());
    }

    private static Map<String, Set<String>> parMessage(List<LectureMessageriePort.Accuse> accuses) {
        return accuses.stream().collect(Collectors.groupingBy(LectureMessageriePort.Accuse::messageId,
                Collectors.mapping(LectureMessageriePort.Accuse::personId, Collectors.toSet())));
    }

    /**
     * L'historique d'un message modifié : ses versions antérieures, puis la version courante. Un message supprimé
     * n'a plus d'historique à servir — son texte, sous quelque version que ce soit, ne se lit plus.
     */
    public List<Map<String, Object>> historique(Appelant a, String messageId) {
        Messagerie.Message m = message(a, messageId);
        if (m.supprime()) {
            throw RefusCommunication.conflit("ce message est supprimé : son historique ne se sert plus");
        }
        List<Map<String, Object>> versions = new ArrayList<>();
        for (LectureMessageriePort.VersionStockee v : lecture.versions(a.tenantId(), messageId)) {
            versions.add(version(v.version(), v.corps(), v.ecritLe(), v.remplaceLe()));
        }
        String ecritLe = m.editeLe() != null ? m.editeLe() : m.envoyeLe();
        versions.add(version(versions.size() + 1, m.corps(), ecritLe, null));
        return versions;
    }

    private static Map<String, Object> version(int numero, String corps, String ecritLe, String remplaceLe) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("version", numero);
        v.put("corps", corps);
        v.put("ecritLe", ecritLe);
        v.put("remplaceLe", remplaceLe);
        v.put("courante", remplaceLe == null);
        return v;
    }

    /** Un message que l'appelant peut lire ; tout autre — inexistant, hors portée, privé à un autre — est introuvable. */
    public Messagerie.Message message(Appelant a, String messageId) {
        String conversationId = lecture.conversationDuMessage(a.tenantId(), messageId).filter(c -> portee(a).connait(c))
                .orElseThrow(() -> RefusCommunication.introuvable(MESSAGE_INTROUVABLE));
        return messages(a, conversationId).stream().filter(m -> m.id().equals(messageId)).findFirst()
                .orElseThrow(() -> RefusCommunication.introuvable(MESSAGE_INTROUVABLE));
    }

    /** Ce qui m'attend, conversation par conversation ; une conversation archivée ne réclame plus d'attention. */
    public Map<String, Object> nonLus(Appelant a) {
        Messagerie.PorteeLecture portee = portee(a);
        Set<String> lus = lecture.lusPar(a.tenantId(), a.acteurId());
        List<Messagerie.ConversationNonLue> comptes = conversations(a).stream()
                .filter(c -> c.archiveeLe() == null)
                .map(c -> Messagerie.compterNonLus(portee, c, lecture.messages(a.tenantId(), c.id()), lus))
                .sorted(Comparator.comparingInt(Messagerie.ConversationNonLue::nonLus).reversed()
                        .thenComparing(c -> Objects.toString(c.dernierMessageLe(), ""), Comparator.reverseOrder()))
                .toList();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", comptes.stream().mapToInt(Messagerie.ConversationNonLue::nonLus).sum());
        r.put("conversations", comptes.stream().map(Messagerie.ConversationNonLue::enCarte).toList());
        return r;
    }

    /** Qui a lu un message : seulement pour un message que l'appelant peut lire lui-même. */
    public List<Map<String, Object>> lecteurs(Appelant a, String messageId) {
        message(a, messageId);
        return lecture.lecteurs(a.tenantId(), messageId);
    }

    /** Les messages reçus par l'appelant et pas encore accusés : ce qu'une notification vient de lui apporter. */
    public List<String> aAccuser(Appelant a) {
        Messagerie.PorteeLecture portee = portee(a);
        Set<String> recus = lecture.recusPar(a.tenantId(), a.acteurId());
        return conversations(a).stream().flatMap(c -> lecture.messages(a.tenantId(), c.id()).stream())
                .filter(m -> Messagerie.dansLaPortee(portee, m) && !m.auteurId().equals(a.acteurId()) && !m.supprime() && !recus.contains(m.id()))
                .map(Messagerie.Message::id).distinct().toList();
    }

    /** La recherche s'exécute dans la portée de l'appelant, jamais dans l'ensemble des messages. */
    public List<Map<String, Object>> chercher(Appelant a, String requete) {
        return Messagerie.rechercher(portee(a), lecture.messages(a.tenantId(), null), requete == null ? "" : requete);
    }

    /** Une pièce jointe servie : sa description et son fichier. */
    public record Fichier(String nom, String typeMime, long taille, byte[] contenu) { }

    /**
     * Une pièce hors portée répond comme une pièce inexistante ; chaque consultation est journalisée. Une pièce dont
     * le fichier n'a pas été conservé (déposée avant que les pièces ne transportent leur contenu) est introuvable aussi.
     */
    public Fichier pieceJointe(Appelant a, String pieceId) {
        LectureMessageriePort.PieceStockee p = lecture.piece(a.tenantId(), pieceId)
                .filter(x -> portee(a).couvre(x.conversationId(), x.envoyeLe())
                        && new Messagerie.Adresse(x.priveA(), null).visiblePar(a.acteurId(), x.auteurId()) && !x.messageSupprime())
                .orElseThrow(() -> RefusCommunication.introuvable(PIECE_INTROUVABLE));
        byte[] contenu = lecture.contenuPiece(a.tenantId(), pieceId).orElseThrow(() -> RefusCommunication.introuvable(CONTENU_ABSENT));
        journal.consigner(a.tenantId(), new JournalCommunicationPort.Trace(a.acteurId(), "piece_jointe.consultation", pieceId, null, null));
        return new Fichier(p.nom(), Messagerie.typeServi(p.typeMime()), p.taille(), contenu);
    }

    /** Par où chaque autre participant actif sera joint pour un message (3.7) — sans l'auteur. */
    public List<Map<String, Object>> acheminement(Appelant a, String conversationId) {
        String now = maintenant();
        List<Map<String, Object>> liste = new ArrayList<>();
        for (Messagerie.Participation p : conversationLisible(a, conversationId).participants()) {
            if (!p.personId().equals(a.acteurId()) && (p.jusqua() == null || now.compareTo(p.jusqua()) <= 0)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("personId", p.personId());
                m.put("canaux", acheminement.acheminement(a.tenantId(), p.personId(), EVENEMENT_MESSAGE).stream().map(AcheminementPort.Canal::enCarte).toList());
                liste.add(m);
            }
        }
        return liste;
    }
}
