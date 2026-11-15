package ca.vina.nexu.alertes.application.requetes;

import ca.vina.nexu.alertes.domaine.TentativesPort;
import ca.vina.nexu.alertes.domaine.UtilisateursActifs;

import java.util.List;

/** Le bordereau des utilisateurs actifs d'un mois (ST-G08) : combien, qui, et la pièce justificative de chacun. */
public class LectureUtilisateursActifs {

    public record Bilan(String mois, int total, List<UtilisateursActifs.Actif> actifs) { }

    private final TentativesPort tentatives;

    public LectureUtilisateursActifs(TentativesPort tentatives) {
        this.tentatives = tentatives;
    }

    public Bilan du(String tenantId, String moisCode) {
        UtilisateursActifs.Mois mois = UtilisateursActifs.Mois.de(moisCode);
        List<UtilisateursActifs.Actif> actifs = UtilisateursActifs.actifs(mois, tentatives.tentatives(tenantId, mois.debut(), mois.fin()));
        return new Bilan(mois.code(), actifs.size(), actifs);
    }
}
