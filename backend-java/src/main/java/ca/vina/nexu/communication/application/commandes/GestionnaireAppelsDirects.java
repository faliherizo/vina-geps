package ca.vina.nexu.communication.application.commandes;

import ca.vina.nexu.communication.domaine.Appelant;
import ca.vina.nexu.communication.domaine.AppelsDirects;
import ca.vina.nexu.communication.domaine.EcritureAppelsDirectsPort;
import ca.vina.nexu.communication.domaine.JournalCommunicationPort;
import ca.vina.nexu.communication.domaine.LectureAppelsDirectsPort;
import ca.vina.nexu.communication.domaine.RefusCommunication;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.temps.Iso;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lancer un appel entre collègues, et y poser un geste : la décision vient du domaine, ici on l'écrit et on la trace. */
public final class GestionnaireAppelsDirects {

    /** Les dépendances d'écriture, groupées. */
    public record Ecritures(EcritureAppelsDirectsPort appels, JournalCommunicationPort journal, Sequence sequence, Clock horloge) { }

    private final LectureAppelsDirectsPort lecture;
    private final BalayageSonneries balayage;
    private final Ecritures e;

    public GestionnaireAppelsDirects(LectureAppelsDirectsPort lecture, BalayageSonneries balayage, Ecritures ecritures) {
        this.lecture = lecture;
        this.balayage = balayage;
        this.e = ecritures;
    }

    /** Un appel qui sonne ; s'il sonne déjà entre les deux mêmes personnes, c'est celui-là qui est rendu. */
    public Map<String, Object> lancer(Appelant a, String appeleId, String conversationId) {
        List<String> erreurs = AppelsDirects.validerInvitation(a.acteurId(), appeleId);
        if (!erreurs.isEmpty()) {
            throw RefusCommunication.invalide(String.join(" · ", erreurs));
        }
        String appele = appeleId.trim();
        String now = Iso.de(e.horloge().instant());
        balayage.clore(a.tenantId(), a.acteurId(), now);
        return lecture.actifEntre(a.tenantId(), a.acteurId(), appele).orElseGet(() -> nouveau(a, appele, conversationId, now)).enCarte(a.acteurId());
    }

    private AppelsDirects.Appel nouveau(Appelant a, String appele, String conversationId, String now) {
        AppelsDirects.Appel appel = new AppelsDirects.Appel(e.sequence().uuid(), a.acteurId(), appele,
                conversationId, AppelsDirects.Etat.SONNE, now, null, null, null);
        e.appels().inserer(a.tenantId(), appel);
        e.journal().consigner(a.tenantId(), new JournalCommunicationPort.Trace(a.acteurId(), "appel.lancement", appele, null, null));
        return appel;
    }

    /** Pose un geste ; le motif d'un refus remonte tel quel. */
    public Map<String, Object> poser(Appelant a, String appelId, AppelsDirects.Geste geste) {
        AppelsDirects.Appel appel = lecture.lire(a.tenantId(), appelId).orElseThrow(() -> RefusCommunication.introuvable("appel introuvable"));
        AppelsDirects.Etat etat = AppelsDirects.appliquer(appel, geste, a.acteurId());
        String now = Iso.de(e.horloge().instant());
        boolean decroche = etat == AppelsDirects.Etat.EN_COURS;
        AppelsDirects.Appel apres = appel.avec(etat, decroche ? now : appel.decrocheLe(), decroche ? null : now, decroche ? null : a.acteurId());
        e.appels().changerEtat(a.tenantId(), apres);
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("etat", etat.code());
        trace.put("secondes", AppelsDirects.dureeSecondes(apres));
        e.journal().consigner(a.tenantId(), new JournalCommunicationPort.Trace(a.acteurId(), "appel." + geste.code(), appel.id(), null, trace));
        return apres.enCarte(a.acteurId());
    }
}
