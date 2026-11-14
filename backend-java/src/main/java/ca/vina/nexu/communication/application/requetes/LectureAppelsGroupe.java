package ca.vina.nexu.communication.application.requetes;

import ca.vina.nexu.communication.domaine.Appelant;
import ca.vina.nexu.communication.domaine.AppelsGroupe;
import ca.vina.nexu.communication.domaine.LectureAppelsGroupePort;
import ca.vina.nexu.partage.temps.Iso;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * Ce qui sonne ou se parle en groupe pour moi : le bandeau d'appel le demande en boucle. Un appel qui a trop sonné
 * ne s'affiche plus, même avant que le geste suivant ne range son expiration.
 */
public final class LectureAppelsGroupe {

    private final LectureAppelsGroupePort lecture;
    private final Clock horloge;

    public LectureAppelsGroupe(LectureAppelsGroupePort lecture, Clock horloge) {
        this.lecture = lecture;
        this.horloge = horloge;
    }

    public List<Map<String, Object>> enCours(Appelant a) {
        String now = Iso.de(horloge.instant());
        return lecture.actifsDe(a.tenantId(), a.acteurId()).stream()
                .map(x -> AppelsGroupe.expirerSi(x, now))
                .filter(x -> !x.etat().terminal())
                .filter(x -> x.invite(a.acteurId()).map(i -> i.etat() != AppelsGroupe.EtatInvite.IGNORE).orElse(false))
                .map(x -> x.enCarte(a.acteurId()))
                .toList();
    }
}
