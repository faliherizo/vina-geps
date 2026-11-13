package ca.vina.nexu.communication.application.requetes;

import ca.vina.nexu.communication.application.commandes.BalayageSonneries;
import ca.vina.nexu.communication.domaine.Appelant;
import ca.vina.nexu.communication.domaine.AppelsDirects;
import ca.vina.nexu.communication.domaine.LectureAppelsDirectsPort;
import ca.vina.nexu.partage.temps.Iso;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * Ce qui sonne ou se parle pour moi, et mon historique. Chaque lecture referme d'abord les sonneries
 * abandonnées : c'est le seul moment où la réponse compte.
 */
public final class LectureAppelsDirects {

    public static final int LIMITE_PAR_DEFAUT = 50;
    public static final int LIMITE_MAX = 200;

    private final LectureAppelsDirectsPort lecture;
    private final BalayageSonneries balayage;
    private final Clock horloge;

    public LectureAppelsDirects(LectureAppelsDirectsPort lecture, BalayageSonneries balayage, Clock horloge) {
        this.lecture = lecture;
        this.balayage = balayage;
        this.horloge = horloge;
    }

    public List<Map<String, Object>> enCours(Appelant a) {
        balayage.clore(a.tenantId(), a.acteurId(), Iso.de(horloge.instant()));
        return lecture.enCours(a.tenantId(), a.acteurId()).stream().map(x -> x.enCarte(a.acteurId())).toList();
    }

    /** La borne est appliquée en base : rapatrier dix ans d'appels pour en montrer cinquante grossirait sans bruit. */
    public List<Map<String, Object>> historique(Appelant a, int limite) {
        balayage.clore(a.tenantId(), a.acteurId(), Iso.de(horloge.instant()));
        int borne = Math.min(Math.max(1, limite), LIMITE_MAX);
        return lecture.historique(a.tenantId(), a.acteurId(), borne).stream().map((AppelsDirects.Appel x) -> x.enCarte(a.acteurId())).toList();
    }
}
