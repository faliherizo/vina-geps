package ca.vina.nexu.communication.application.commandes;

import ca.vina.nexu.communication.application.requetes.LectureMessagerie;
import ca.vina.nexu.communication.domaine.Appelant;
import ca.vina.nexu.communication.domaine.EcritureMessageriePort;
import ca.vina.nexu.communication.domaine.JournalCommunicationPort;
import ca.vina.nexu.communication.domaine.Messagerie;
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
    public record Dependances(LectureMessagerie lecture, EcritureMessageriePort ecriture, JournalCommunicationPort journal, Supplier<String> identifiants) { }

    public record DemandeConversation(String sujet, String type, String groupeId, List<String> participants) { }

    /** Une pièce telle que le corps HTTP la décrit, avant validation : la taille peut ne pas être un nombre. */
    public record PieceDemandee(String nom, String typeMime, Object taille) { }

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
        Messagerie.Conversation c = d.lecture().conversation(a.tenantId(), conversationId);
        String now = maintenant();
        if (!Messagerie.peutEcrire(c, a.acteurId(), now)) {
            throw RefusCommunication.invalide(c.archiveeLe() != null ? "cette conversation est archivée" : "vous ne participez pas à cette conversation");
        }
        valider(Messagerie.validerMessage(corps));
        validerPieces(pieces);
        String id = d.identifiants().get();
        d.ecriture().envoyer(a.tenantId(), new EcritureMessageriePort.NouveauMessage(id, conversationId, a.acteurId(), corps.trim(), now, nouvellesPieces(pieces)));
        tracer(a, "message.envoi", conversationId, Map.of("pieces", pieces.size()));
        return d.lecture().messages(a, conversationId).stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow();
    }

    private static void validerPieces(List<PieceDemandee> pieces) {
        for (int i = 0; i < pieces.size(); i++) {
            PieceDemandee p = pieces.get(i);
            List<String> fautes = Messagerie.validerPieceJointe(p.nom(), p.typeMime(), p.taille());
            if (!fautes.isEmpty()) {
                throw RefusCommunication.invalide("pièce jointe " + (i + 1) + " : " + String.join(" · ", fautes));
            }
        }
    }

    /** Chaque pièce reçoit son identifiant puis la clé de son contenu, dans cet ordre. */
    private List<EcritureMessageriePort.NouvellePiece> nouvellesPieces(List<PieceDemandee> pieces) {
        List<EcritureMessageriePort.NouvellePiece> liste = new ArrayList<>();
        for (PieceDemandee p : pieces) {
            String id = d.identifiants().get();
            liste.add(new EcritureMessageriePort.NouvellePiece(id, p.nom(), p.typeMime(), ((Number) p.taille()).longValue(), d.identifiants().get()));
        }
        return liste;
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
