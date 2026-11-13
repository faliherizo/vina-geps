package ca.vina.nexu.effectifs.domaine;

import java.util.List;

/** Port de persistance des référentiels datés, déclaré par le domaine (l'application ne dépend pas de l'infrastructure). */
public interface DepotReferentielsPort {

    /** Toutes les versions d'une valeur, par date de début. */
    List<ValeurReferentiel> versions(String tenantId, TypeReferentiel type, String code);

    /** Les valeurs en vigueur à une date, par ordre puis code. */
    List<ValeurReferentiel> enVigueur(String tenantId, TypeReferentiel type, String date);

    /** Toutes les versions de toutes les valeurs d'un type, par code puis date. */
    List<ValeurReferentiel> historique(String tenantId, TypeReferentiel type);

    void inserer(String tenantId, ValeurReferentiel v, String creeLe, String creePar);

    void fermer(String tenantId, String id, String date);
}
