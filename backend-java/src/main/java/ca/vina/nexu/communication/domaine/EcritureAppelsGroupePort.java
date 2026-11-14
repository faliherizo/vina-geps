package ca.vina.nexu.communication.domaine;

/** Port d'écriture des appels de groupe : chaque état déjà décidé par le domaine, rangé tel quel. */
public interface EcritureAppelsGroupePort {

    void inserer(String tenantId, AppelsGroupe.Appel appel);

    /** Réécrit l'état de l'appel et celui de chacun de ses invités. */
    void enregistrer(String tenantId, AppelsGroupe.Appel appel);
}
