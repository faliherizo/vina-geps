package ca.vina.nexu.communication.domaine;

/** Port d'écriture des appels entre collègues. */
public interface EcritureAppelsDirectsPort {

    void inserer(String tenantId, AppelsDirects.Appel appel);

    /** Écrit l'état, le décrochage, la fin et son auteur. */
    void changerEtat(String tenantId, AppelsDirects.Appel appel);
}
