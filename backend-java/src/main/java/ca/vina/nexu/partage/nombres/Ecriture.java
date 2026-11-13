package ca.vina.nexu.partage.nombres;

import java.math.BigDecimal;

/**
 * <b>Écrire un nombre comme JavaScript l'écrit</b>, pour que les messages des deux piles soient
 * identiques au caractère près.
 *
 * <p>Ce n'est pas une coquetterie. {@code BigDecimal} conserve son échelle : {@code 20.00} reste
 * « 20.00 », là où le Node écrit « 20 ». Les deux piles rendraient alors « 20.00 points de moins »
 * et « 20 points de moins » pour le <b>même</b> écart — deux textes différents sur la même route,
 * selon le serveur qui sert la requête. Un utilisateur qui compare deux captures d'écran y verrait
 * une incohérence, et un essai de bout en bout qui cherche un libellé échouerait une fois sur deux.
 *
 * <p>Le zéro négatif est ramené à « 0 » : {@code -0} existe en virgule flottante, jamais dans une
 * phrase.
 */
public final class Ecriture {

    private Ecriture() {
    }

    /** Le nombre sans ses zéros décimaux inutiles, en notation simple. */
    public static String js(BigDecimal n) {
        if (n == null) {
            return "0";
        }
        BigDecimal sans = n.stripTrailingZeros();
        // `stripTrailingZeros` rend 1E+2 pour 100 : `toPlainString` défait la notation scientifique,
        // que personne n'attend dans une phrase.
        String texte = sans.signum() == 0 ? "0" : sans.toPlainString();
        return "-0".equals(texte) ? "0" : texte;
    }
}
