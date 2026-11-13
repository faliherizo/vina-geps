package ca.vina.nexu.auth.domaine;

/** Port d'écriture des annuaires (P-TN-2), réservé à l'administration de l'organisme. */
public interface EcritureAnnuaires {

    /** Enregistre un annuaire validé : création s'il est nouveau, remplacement sinon. */
    void enregistrer(Annuaires.Annuaire annuaire, String auteur);

    /** Retire l'annuaire de cet organisme ; faux s'il n'en fait pas partie. */
    boolean retirer(String tenantId, String id);
}
