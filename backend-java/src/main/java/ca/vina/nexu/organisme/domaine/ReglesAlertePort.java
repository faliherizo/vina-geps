package ca.vina.nexu.organisme.domaine;

import java.util.List;

/** Port de persistance des règles d'alerte. */
public interface ReglesAlertePort {

    /** Les règles de l'organisme, par date de création puis identifiant. */
    List<RegleAlerte> lister(String tenantId);

    void inserer(String tenantId, RegleAlerte regle);

    /** Vrai si la règle existait dans cet organisme. */
    boolean retirer(String tenantId, String regleId);
}
