package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;

/** Port des périodes de planification ({@code eff_periodes}). */
public interface DepotPeriodesPort {

    Optional<Periode> lire(String tenantId, String id);

    /** Les plus récentes d'abord. */
    List<Periode> lister(String tenantId);

    void inserer(String tenantId, String id, String uniteCode, String nom, String debut, String fin, String creeLe);

    /** Change l'état ; horodate la publication ou la clôture quand c'est l'état visé. */
    void changerEtat(String tenantId, String id, Planification.EtatPeriode etat, String finCollecte, String maintenant);
}
