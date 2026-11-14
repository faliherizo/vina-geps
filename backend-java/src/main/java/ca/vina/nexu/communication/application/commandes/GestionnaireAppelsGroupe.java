package ca.vina.nexu.communication.application.commandes;

import ca.vina.nexu.communication.application.requetes.LectureMessagerie;
import ca.vina.nexu.communication.domaine.Appelant;
import ca.vina.nexu.communication.domaine.AppelsGroupe;
import ca.vina.nexu.communication.domaine.EcritureAppelsGroupePort;
import ca.vina.nexu.communication.domaine.JournalCommunicationPort;
import ca.vina.nexu.communication.domaine.LectureAppelsGroupePort;
import ca.vina.nexu.communication.domaine.Messagerie;
import ca.vina.nexu.communication.domaine.RefusCommunication;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.temps.Iso;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lancer un appel de groupe depuis un fil, et y poser un geste : la décision vient du domaine ({@link AppelsGroupe}),
 * ici on vérifie la portée, on écrit et on trace. On n'appelle que dans un fil où l'on participe.
 */
public final class GestionnaireAppelsGroupe {

    /** Les dépendances d'écriture, groupées. */
    public record Ecritures(EcritureAppelsGroupePort appels, JournalCommunicationPort journal, Sequence sequence, Clock horloge) { }

    private final LectureMessagerie messagerie;
    private final LectureAppelsGroupePort lecture;
    private final Ecritures e;

    public GestionnaireAppelsGroupe(LectureMessagerie messagerie, LectureAppelsGroupePort lecture, Ecritures ecritures) {
        this.messagerie = messagerie;
        this.lecture = lecture;
        this.e = ecritures;
    }

    private String maintenant() {
        return Iso.de(e.horloge().instant());
    }

    /** Un fil ne porte qu'un appel à la fois ; un appel expiré en sonnant est d'abord clos, puis un nouveau peut partir. */
    public Map<String, Object> lancer(Appelant a, String conversationId) {
        Messagerie.Conversation c = messagerie.conversationLisible(a, conversationId);
        String now = maintenant();
        if (!Messagerie.peutEcrire(c, a.acteurId(), now)) {
            throw RefusCommunication.conflit(c.archiveeLe() != null ? "cette conversation est archivée" : "vous ne participez plus à cette conversation");
        }
        lecture.actifDuFil(a.tenantId(), conversationId).map(x -> clore(a, x, now)).filter(x -> !x.etat().terminal()).ifPresent(x -> {
            throw RefusCommunication.conflit("un appel de groupe est déjà en cours dans ce fil : rejoignez-le");
        });
        List<String> actifs = c.participants().stream().filter(p -> Messagerie.participantActif(c, p.personId(), now))
                .map(Messagerie.Participation::personId).toList();
        AppelsGroupe.Appel appel = AppelsGroupe.lancer(e.sequence().uuid(), conversationId, a.acteurId(), actifs, now);
        e.appels().inserer(a.tenantId(), appel);
        e.journal().consigner(a.tenantId(), new JournalCommunicationPort.Trace(a.acteurId(), "appel_groupe.lancement", appel.id(), null,
                Map.of("invites", appel.invites().size() - 1)));
        return appel.enCarte(a.acteurId());
    }

    /** Pose un geste ; le motif d'un refus remonte tel quel. Un appel où l'on n'est pas invité est introuvable. */
    public Map<String, Object> poser(Appelant a, String appelId, AppelsGroupe.Geste geste) {
        AppelsGroupe.Appel appel = lecture.lire(a.tenantId(), appelId).filter(x -> x.invite(a.acteurId()).isPresent())
                .orElseThrow(() -> RefusCommunication.introuvable("appel introuvable"));
        String now = maintenant();
        AppelsGroupe.Appel apres = AppelsGroupe.appliquer(clore(a, appel, now), geste, a.acteurId(), now);
        e.appels().enregistrer(a.tenantId(), apres);
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("etat", apres.etat().code());
        trace.put("presents", apres.presents());
        e.journal().consigner(a.tenantId(), new JournalCommunicationPort.Trace(a.acteurId(), "appel_groupe." + geste.code(), appel.id(), null, trace));
        return apres.enCarte(a.acteurId());
    }

    /** Range l'expiration d'un appel qui a trop sonné, au nom du mécanisme qui la constate. */
    private AppelsGroupe.Appel clore(Appelant a, AppelsGroupe.Appel appel, String now) {
        AppelsGroupe.Appel expire = AppelsGroupe.expirerSi(appel, now);
        if (expire != appel) {
            e.appels().enregistrer(a.tenantId(), expire);
            e.journal().consigner(a.tenantId(), new JournalCommunicationPort.Trace(MECANISME, "appel_groupe.manque", appel.id(), null, null));
        }
        return expire;
    }

    /** L'auteur d'une expiration : personne ne l'a décidée, le délai l'a constatée (E-GN-9). */
    public static final String MECANISME = "sonnerie-expiree";
}
