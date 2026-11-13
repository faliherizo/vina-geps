package ca.vina.nexu.effectifs.domaine;

/**
 * Une panne <strong>passagère</strong> d'un destinataire (injoignable, 5xx, 401…) : la livraison lève, et l'ordonnanceur
 * retente selon sa politique. Un refus du destinataire n'en est pas une : c'est une issue, rejetée.
 */
public class DestinataireIndisponibleException extends RuntimeException {
    public DestinataireIndisponibleException(String message) {
        super(message);
    }
}
