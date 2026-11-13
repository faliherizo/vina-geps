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

/**
 * Ce qu'on lit de la messagerie : conversations, messages, non-lus, recherche, pièces jointes et
 * acheminement. Tout passe par la <b>portée de lecture</b> de l'appelant ({@link Messagerie}) : une
 * conversation hors portée répond comme une conversation inexistante.
 */
public final class LectureMessagerie {

    static final String CONVERSATION_INTROUVABLE = "conversation introuvable";
    static final String PIECE_INTROUVABLE = "pièce jointe introuvable";
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
        return visibles.stream().map(m -> m.avecLecture(m.auteurId().equals(a.acteurId()) || lus.contains(m.id()))).toList();
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

    public List<Map<String, Object>> lecteurs(Appelant a, String messageId) {
        return lecture.lecteurs(a.tenantId(), messageId);
    }

    /** La recherche s'exécute dans la portée de l'appelant, jamais dans l'ensemble des messages. */
    public List<Map<String, Object>> chercher(Appelant a, String requete) {
        return Messagerie.rechercher(portee(a), lecture.messages(a.tenantId(), null), requete == null ? "" : requete);
    }

    /** Une pièce hors portée répond comme une pièce inexistante ; chaque consultation est journalisée. */
    public Map<String, Object> pieceJointe(Appelant a, String pieceId) {
        LectureMessageriePort.PieceStockee p = lecture.piece(a.tenantId(), pieceId)
                .filter(x -> portee(a).couvre(x.conversationId(), x.envoyeLe()))
                .orElseThrow(() -> RefusCommunication.introuvable(PIECE_INTROUVABLE));
        journal.consigner(a.tenantId(), new JournalCommunicationPort.Trace(a.acteurId(), "piece_jointe.consultation", pieceId, null, null));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("nom", p.nom());
        r.put("typeMime", Messagerie.typeServi(p.typeMime()));
        r.put("taille", p.taille());
        return r;
    }

    /** Par où chaque autre participant actif sera joint pour un message (3.7) — sans l'auteur. */
    public List<Map<String, Object>> acheminement(Appelant a, String conversationId) {
        String now = maintenant();
        List<Map<String, Object>> liste = new ArrayList<>();
        for (Messagerie.Participation p : conversation(a.tenantId(), conversationId).participants()) {
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
