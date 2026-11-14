package ca.vina.nexu.communication.domaine;

/**
 * Port : l'analyse d'une pièce venue de l'extérieur avant qu'elle ne rejoigne un fil (grille 33). Une pièce
 * infectée est refusée en nommant la signature ; un analyseur injoignable fait échouer l'envoi — jamais l'accepter
 * faute d'avoir pu vérifier.
 */
@FunctionalInterface
public interface AnalysePieces {

    void exigerSaine(byte[] contenu);
}
