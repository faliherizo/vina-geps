package ca.vina.nexu.communication.application.commandes;

import ca.vina.nexu.communication.application.requetes.LectureMessagerie;
import ca.vina.nexu.communication.domaine.AnalysePieces;
import ca.vina.nexu.communication.domaine.Appelant;
import ca.vina.nexu.communication.domaine.EcritureMessageriePort;
import ca.vina.nexu.communication.domaine.JournalCommunicationPort;
import ca.vina.nexu.communication.domaine.Messagerie;
import ca.vina.nexu.communication.domaine.Reaction;
import ca.vina.nexu.communication.domaine.RefusCommunication;
import ca.vina.nexu.communication.domaine.RoleParticipant;
import ca.vina.nexu.communication.domaine.TypeConversation;
import ca.vina.nexu.partage.temps.Iso;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Les gestes de la messagerie : ouvrir une conversation, en gérer les participants, l'archiver,
 * écrire, marquer lu. Chaque règle est décidée ici ou dans {@link Messagerie} ; l'adaptateur ne fait
 * que ranger. Les identifiants viennent d'une source injectée, pour des essais déterministes.
 */
public final class GestionnaireMessagerie {

    /** Ce dont le gestionnaire a besoin : relire, ranger, tracer, nommer. */
    public record Dependances(LectureMessagerie lecture, EcritureMessageriePort ecriture, JournalCommunicationPort journal, Supplier<String> identifiants,
                              AnalysePieces antivirus) { }

    public record DemandeConversation(String sujet, String type, String groupeId, List<String> participants) { }

    /** Une pièce telle que le corps HTTP la décrit, avant validation : le fichier en base64, qui peut manquer ou être illisible. */
    public record PieceDemandee(String nom, String typeMime, Object contenu) { }

    /** Une pièce validée : sa description et son fichier. */
    private record PieceValidee(String nom, String typeMime, byte[] contenu) { }

    /** Ce qu'on envoie : le texte, les pièces, et à qui — tout le fil ou une seule personne. */
    public record Envoi(String corps, List<PieceDemandee> pieces, Messagerie.Adresse adresse) { }

    /** Le fil à deux rendu, et s'il vient de naître : la frontière en tire 201 ou 200. */
    public record FilDirect(Messagerie.Conversation conversation, boolean cree) { }

    static final String SUJET_FIL_DIRECT = "Conversation à deux";

    private final Dependances d;
    private final Clock horloge;

    public GestionnaireMessagerie(Dependances dependances, Clock horloge) {
        this.d = dependances;
        this.horloge = horloge;
    }

    private String maintenant() {
        return Iso.de(horloge.instant());
    }

    private static void valider(List<String> erreurs) {
        if (!erreurs.isEmpty()) {
            throw RefusCommunication.invalide(String.join(" · ", erreurs));
        }
    }

    private void tracer(Appelant a, String action, String cible, Object apres) {
        d.journal().consigner(a.tenantId(), new JournalCommunicationPort.Trace(a.acteurId(), action, cible, null, apres));
    }

    /** L'auteur entre d'office et modère ; une conversation sans type est un groupe. */
    public Messagerie.Conversation creer(Appelant a, DemandeConversation demande) {
        String type = demande.type() == null ? TypeConversation.GROUPE.code() : demande.type();
        List<String> participants = Messagerie.participantsAvec(a.acteurId(), demande.participants());
        valider(Messagerie.validerConversation(demande.sujet(), type, participants));
        String id = d.identifiants().get();
        String now = maintenant();
        d.ecriture().creer(a.tenantId(), new EcritureMessageriePort.NouvelleConversation(id, demande.sujet().trim(), type, demande.groupeId(), a.acteurId(), now));
        for (String personId : participants) {
            RoleParticipant role = personId.equals(a.acteurId()) ? RoleParticipant.MODERATEUR : RoleParticipant.MEMBRE;
            d.ecriture().ajouter(a.tenantId(), new EcritureMessageriePort.NouvelleParticipation(d.identifiants().get(), id, personId, role.code(), now));
        }
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("sujet", demande.sujet());
        apres.put("type", type);
        apres.put("participants", participants.size());
        tracer(a, "conversation.creation", id, apres);
        return d.lecture().conversation(a.tenantId(), id);
    }

    public Messagerie.Conversation ajouterParticipant(Appelant a, String conversationId, String personId, String role) {
        Messagerie.Conversation c = d.lecture().conversation(a.tenantId(), conversationId);
        String now = maintenant();
        if (!Messagerie.peutGererParticipants(c, a.acteurId(), now)) {
            throw RefusCommunication.invalide(TypeConversation.INDIVIDUELLE.est(c.type()) ? "la composition d'une conversation individuelle ne se modifie pas" : "seul un modérateur ajoute un participant");
        }
        if (c.participants().stream().anyMatch(p -> p.personId().equals(personId) && p.jusqua() == null)) {
            throw RefusCommunication.invalide("cette personne participe déjà");
        }
        String r = role == null ? RoleParticipant.MEMBRE.code() : role;
        d.ecriture().ajouter(a.tenantId(), new EcritureMessageriePort.NouvelleParticipation(d.identifiants().get(), conversationId, personId, r, now));
        tracer(a, "conversation.participant_ajoute", conversationId + "/" + personId, null);
        return d.lecture().conversation(a.tenantId(), conversationId);
    }

    /** On se retire soi-même ; retirer un autre est l'affaire d'un modérateur. */
    public Messagerie.Conversation retirerParticipant(Appelant a, String conversationId, String personId) {
        Messagerie.Conversation c = d.lecture().conversation(a.tenantId(), conversationId);
        String now = maintenant();
        if (!personId.equals(a.acteurId()) && !Messagerie.peutGererParticipants(c, a.acteurId(), now)) {
            throw RefusCommunication.invalide("seul un modérateur retire un autre participant");
        }
        if (!d.ecriture().terminer(a.tenantId(), conversationId, personId, now)) {
            throw RefusCommunication.invalide("cette personne ne participe pas à la conversation");
        }
        tracer(a, "conversation.participant_retire", conversationId + "/" + personId, null);
        return d.lecture().conversation(a.tenantId(), conversationId);
    }

    public Messagerie.Conversation archiver(Appelant a, String conversationId) {
        Messagerie.Conversation c = d.lecture().conversation(a.tenantId(), conversationId);
        if (c.archiveeLe() != null) {
            throw RefusCommunication.invalide("conversation déjà archivée");
        }
        if (!Messagerie.peutGererParticipants(c, a.acteurId(), maintenant()) && !TypeConversation.INDIVIDUELLE.est(c.type())) {
            throw RefusCommunication.invalide("seul un modérateur archive la conversation");
        }
        d.ecriture().archiver(a.tenantId(), conversationId, maintenant());
        tracer(a, "conversation.archivage", conversationId, null);
        return d.lecture().conversation(a.tenantId(), conversationId);
    }

    public Messagerie.Message envoyer(Appelant a, String conversationId, String corps, List<PieceDemandee> pieces) {
        return envoyer(a, conversationId, new Envoi(corps, pieces, Messagerie.Adresse.PUBLIQUE));
    }

    /** Écrire au fil, ou à une seule personne du fil : l'adresse est jugée avant que rien ne parte. */
    public Messagerie.Message envoyer(Appelant a, String conversationId, Envoi envoi) {
        Messagerie.Conversation c = d.lecture().conversation(a.tenantId(), conversationId);
        String now = maintenant();
        if (!Messagerie.peutEcrire(c, a.acteurId(), now)) {
            throw RefusCommunication.invalide(c.archiveeLe() != null ? "cette conversation est archivée" : "vous ne participez pas à cette conversation");
        }
        valider(Messagerie.validerMessage(envoi.corps()));
        List<PieceValidee> pieces = validerPieces(envoi.pieces());
        valider(Messagerie.validerAdresse(c, a.acteurId(), envoi.adresse(), d.lecture().messages(a, conversationId), now));
        // L'antivirus passe sur toutes les pièces avant toute écriture : un envoi dont une pièce est infectée n'en laisse aucune.
        pieces.forEach(p -> d.antivirus().exigerSaine(p.contenu()));
        String id = d.identifiants().get();
        d.ecriture().envoyer(a.tenantId(), new EcritureMessageriePort.NouveauMessage(id, conversationId, a.acteurId(), envoi.corps().trim(), now,
                nouvellesPieces(pieces), envoi.adresse()));
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("pieces", pieces.size());
        // Le journal dit qu'un message est privé et à qui ; jamais ce qu'il dit.
        trace.put("prive", envoi.adresse().priveA() != null);
        tracer(a, "message.envoi", conversationId, trace);
        return d.lecture().messages(a, conversationId).stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow();
    }

    /** Écrire à une personne : son fil à deux s'il existe, sinon un nouveau — jamais deux fils à deux entre les mêmes personnes. */
    public FilDirect filDirect(Appelant a, String personId) {
        String autre = personId == null ? "" : personId.trim();
        if (autre.isEmpty()) {
            throw RefusCommunication.invalide("« personId » : la personne à qui écrire est requise");
        }
        if (autre.equals(a.acteurId())) {
            throw RefusCommunication.invalide("on n'ouvre pas une conversation avec soi-même");
        }
        return Messagerie.filADeux(d.lecture().conversations(a), a.acteurId(), autre, maintenant())
                .map(c -> new FilDirect(c, false))
                .orElseGet(() -> new FilDirect(creer(a, new DemandeConversation(SUJET_FIL_DIRECT, TypeConversation.INDIVIDUELLE.code(), null, List.of(autre))), true));
    }

    /** Réagir à un message lisible : la même réaction posée deux fois se retire. Un fil archivé ne se réagit plus. */
    public Map<String, Object> reagir(Appelant a, String messageId, String code) {
        Reaction reaction = Reaction.de(code);
        Messagerie.Message m = d.lecture().message(a, messageId);
        String now = maintenant();
        exigerModifiable(a, m, now);
        boolean pose = !d.ecriture().retirerReaction(a.tenantId(), messageId, a.acteurId(), reaction);
        if (pose) {
            d.ecriture().poserReaction(a.tenantId(), new EcritureMessageriePort.NouvelleReaction(d.identifiants().get(), messageId, a.acteurId(), reaction, now));
        }
        tracer(a, pose ? "message.reaction" : "message.reaction_retiree", messageId, Map.of("reaction", reaction.code()));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("messageId", messageId);
        r.put("reaction", reaction.code());
        r.put("pose", pose);
        r.put("reactions", d.lecture().message(a, messageId).reactions().stream().map(Messagerie.CompteReaction::enCarte).toList());
        return r;
    }

    /** Chaque pièce porte son fichier : une pièce déclarée sans contenu ne transporterait rien (défaut du 1er novembre 2026). */
    private static List<PieceValidee> validerPieces(List<PieceDemandee> pieces) {
        List<PieceValidee> valides = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            PieceDemandee p = pieces.get(i);
            java.util.Optional<byte[]> contenu = Messagerie.contenuDe(p.contenu());
            // Sans fichier, sa taille n'a pas de sens : seule l'absence est dite. Un fichier vide, lui, est refusé par sa taille.
            List<String> fautes = new ArrayList<>(Messagerie.validerPieceJointe(p.nom(), p.typeMime(), contenu.map(c -> c.length).orElse(1)));
            if (contenu.isEmpty()) {
                fautes.add(0, "contenu requis : le fichier, encodé en base64");
            }
            if (!fautes.isEmpty()) {
                throw RefusCommunication.invalide("pièce jointe " + (i + 1) + " : " + String.join(" · ", fautes));
            }
            valides.add(new PieceValidee(p.nom().trim(), p.typeMime(), contenu.get()));
        }
        return valides;
    }

    /** Chaque pièce reçoit son identifiant puis la clé de son contenu, dans cet ordre. */
    private List<EcritureMessageriePort.NouvellePiece> nouvellesPieces(List<PieceValidee> pieces) {
        List<EcritureMessageriePort.NouvellePiece> liste = new ArrayList<>();
        for (PieceValidee p : pieces) {
            String id = d.identifiants().get();
            liste.add(new EcritureMessageriePort.NouvellePiece(id, p.nom(), p.typeMime(), p.contenu().length, d.identifiants().get(), p.contenu()));
        }
        return liste;
    }

    /** Un geste sur un message existant exige un fil où l'on écrit encore, et un message qui n'est pas supprimé. */
    private Messagerie.Conversation exigerModifiable(Appelant a, Messagerie.Message m, String now) {
        Messagerie.Conversation c = d.lecture().conversation(a.tenantId(), m.conversationId());
        if (!Messagerie.peutEcrire(c, a.acteurId(), now)) {
            throw RefusCommunication.conflit(c.archiveeLe() != null ? "cette conversation est archivée" : "vous ne participez plus à cette conversation");
        }
        if (m.supprime()) {
            throw RefusCommunication.conflit("ce message est supprimé");
        }
        return c;
    }

    /**
     * Modifier son message : l'auteur seul. La version remplacée est gardée dans l'historique, que lit quiconque lit le
     * message ; une modification ne remet pas le message « non lu » — ce qui a été lu l'a été.
     */
    public Messagerie.Message modifier(Appelant a, String messageId, String corps) {
        Messagerie.Message m = d.lecture().message(a, messageId);
        String now = maintenant();
        exigerModifiable(a, m, now);
        if (!m.auteurId().equals(a.acteurId())) {
            throw RefusCommunication.interdit("seul l'auteur modifie son message");
        }
        valider(Messagerie.validerMessage(corps));
        if (corps.trim().equals(m.corps())) {
            throw RefusCommunication.invalide("aucun changement : le texte est le même");
        }
        int version = d.lecture().historique(a, messageId).size();
        String ecritLe = m.editeLe() != null ? m.editeLe() : m.envoyeLe();
        d.ecriture().modifier(a.tenantId(), new EcritureMessageriePort.Modification(d.identifiants().get(), messageId, version, m.corps(), ecritLe,
                corps.trim(), now));
        tracer(a, "message.modification", messageId, Map.of("version", version + 1));
        return d.lecture().message(a, messageId);
    }

    /** Supprimer : l'auteur, ou un modérateur du fil. Le message garde sa place, « supprimé par … » ; son texte ne se sert plus. */
    public Messagerie.Message supprimer(Appelant a, String messageId) {
        Messagerie.Message m = d.lecture().message(a, messageId);
        String now = maintenant();
        Messagerie.Conversation c = exigerModifiable(a, m, now);
        if (!m.auteurId().equals(a.acteurId()) && !Messagerie.peutGererParticipants(c, a.acteurId(), now)) {
            throw RefusCommunication.interdit("seul l'auteur ou un modérateur du fil supprime un message");
        }
        d.ecriture().supprimer(a.tenantId(), messageId, now, a.acteurId());
        // Le journal garde le geste et son auteur, jamais le texte supprimé (Loi 25).
        tracer(a, "message.suppression", messageId, Map.of("parAuteur", m.auteurId().equals(a.acteurId())));
        return d.lecture().message(a, messageId);
    }

    /** Ce que la notification vient d'apporter est désormais « reçu » : l'auteur le voit dans l'état de son message. */
    public int accuserReception(Appelant a) {
        List<String> aAccuser = d.lecture().aAccuser(a);
        String now = maintenant();
        aAccuser.forEach(id -> d.ecriture().accuserReception(a.tenantId(), new EcritureMessageriePort.Reception(d.identifiants().get(), id, a.acteurId(), now)));
        return aAccuser.size();
    }

    public void marquerLu(Appelant a, String messageId) {
        d.ecriture().marquerLu(a.tenantId(), new EcritureMessageriePort.Lecture(d.identifiants().get(), messageId, a.acteurId(), maintenant()));
    }

    /** Tout le fil d'un coup : rattraper une garde ne doit pas demander quarante clics. */
    public int marquerToutLu(Appelant a, String conversationId) {
        List<Messagerie.Message> aLire = d.lecture().messages(a, conversationId).stream().filter(m -> !m.luParMoi()).toList();
        aLire.forEach(m -> marquerLu(a, m.id()));
        return aLire.size();
    }
}
