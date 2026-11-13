package ca.vina.nexu.communication.application.requetes;

import ca.vina.nexu.communication.domaine.Appelant;
import ca.vina.nexu.communication.domaine.LectureListesDistributionPort;
import ca.vina.nexu.communication.domaine.ListesDistribution;
import ca.vina.nexu.communication.domaine.RefusCommunication;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Les listes qu'on peut employer, une liste, et ses destinataires résolus maintenant. */
public final class LectureListesDistribution {

    static final String INTROUVABLE = "liste de distribution introuvable";

    private final LectureListesDistributionPort lecture;

    public LectureListesDistribution(LectureListesDistributionPort lecture) {
        this.lecture = lecture;
    }

    public List<Map<String, Object>> lister(Appelant a) {
        return lecture.visibles(a.tenantId(), a.acteurId()).stream().map(ListesDistribution.Liste::enCarte).toList();
    }

    /** Une liste privée d'autrui répond comme une liste inexistante : distinguer les deux révélerait qu'elle existe. */
    public ListesDistribution.Liste lire(Appelant a, String listeId) {
        return lecture.lire(a.tenantId(), listeId).filter(l -> l.visiblePar(a.acteurId()))
                .orElseThrow(() -> RefusCommunication.introuvable(INTROUVABLE));
    }

    /** Les groupes sont dépliés à chaque appel : une arrivée dans le service est jointe sans toucher la liste. */
    public Map<String, Object> destinataires(Appelant a, String listeId) {
        ListesDistribution.Liste l = lire(a, listeId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("listeId", l.id());
        m.put("nom", l.nom());
        m.put("destinataires", ListesDistribution.destinataires(l.membres(), g -> lecture.membresActifsDuGroupe(a.tenantId(), g)));
        return m;
    }
}
