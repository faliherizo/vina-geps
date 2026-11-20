package ca.vina.nexu.alertes.application.requetes;

import ca.vina.nexu.alertes.domaine.TentativesPort;
import ca.vina.nexu.alertes.domaine.UtilisateursActifs;

import java.util.List;

/**
 * Le bordereau des utilisateurs actifs d'un mois (ST-G08) : combien, qui, la pièce justificative de chacun, et le
 * nombre facturable une fois le minimum du contrat appliqué (ST-H01).
 */
public class LectureUtilisateursActifs {

    public record Bilan(String mois, int total, int minimumFacturable, int facturables, List<UtilisateursActifs.Actif> actifs) { }

    private final TentativesPort tentatives;
    private final UtilisateursActifs.MinimumFacturable minimum;

    public LectureUtilisateursActifs(TentativesPort tentatives, UtilisateursActifs.MinimumFacturable minimum) {
        this.tentatives = tentatives;
        this.minimum = minimum;
    }

    public Bilan du(String tenantId, String moisCode) {
        UtilisateursActifs.Mois mois = UtilisateursActifs.Mois.de(moisCode);
        List<UtilisateursActifs.Actif> actifs = UtilisateursActifs.actifs(mois, tentatives.tentatives(tenantId, mois.debut(), mois.fin()));
        return new Bilan(mois.code(), actifs.size(), minimum.valeur(), minimum.facturables(actifs.size()), actifs);
    }
}
