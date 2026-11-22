package ca.vina.nexu.effectifs.domaine;

/** Port du réglage de l'offre des quarts d'un organisme ({@code eff_offres_parametres}, AD-07). */
public interface ParametresOffresPort {

    /** Le réglage de l'organisme ; sans réglage, celui de la convention en mode simultané. */
    OffresQuart.Parametres lire(String tenantId);

    void enregistrer(String tenantId, OffresQuart.Parametres p, String acteurId, String le);
}
