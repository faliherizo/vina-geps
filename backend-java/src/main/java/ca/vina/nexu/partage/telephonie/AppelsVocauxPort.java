package ca.vina.nexu.partage.telephonie;

/**
 * Port : passer un appel vocal automatique (FX-AO-20260810, ST-G06). Le message est dit en synthèse française, puis
 * l'appel attend un chiffre et le rappelle à {@code urlChiffre} ; chaque fin d'appel rappelle {@code urlStatut}. Les
 * notifications l'implantent (Twilio, ou un simulateur) ; le contexte qui appelle ne connaît pas le fournisseur.
 */
@FunctionalInterface
public interface AppelsVocauxPort {

    /** {@code reference} : l'identifiant de l'appel chez l'appelant, repris dans les deux URL de rappel. */
    record Appel(String tenantId, String reference, String numero, String message, String urlChiffre, String urlStatut) { }

    /** Rend l'identifiant de l'appel chez le fournisseur ; lève si le fournisseur refuse. */
    String appeler(Appel appel);
}
