package ca.vina.nexu.paie.domaine;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Ce que produit une règle de paie variable (ADR 0084). Le maximum garde d'une faute de frappe : une prime de 1 200 %
 * ou un taux de 3 400 $ de l'heure ne sont jamais voulus.
 */
public enum TypeRegle {
    /** Le taux horaire de l'échelle salariale : $ par heure. */
    TAUX_DE_BASE("taux-de-base", new BigDecimal("1000"), true),
    /** Une fraction du taux de base pour chaque heure admissible (0,12 = 12 %). */
    PRIME_POURCENTAGE("prime-pourcentage", new BigDecimal("2"), false),
    /** Un montant par heure admissible. */
    PRIME_HORAIRE("prime-horaire", new BigDecimal("1000"), false),
    /** Un montant fixe par paie. */
    FORFAIT("forfait", new BigDecimal("100000"), false);

    private final String code;
    private final BigDecimal maximum;
    private final boolean strictementPositif;

    TypeRegle(String code, BigDecimal maximum, boolean strictementPositif) {
        this.code = code;
        this.maximum = maximum;
        this.strictementPositif = strictementPositif;
    }

    public String code() {
        return code;
    }

    /** Une prime dépend des heures : plage, jours, férié et codes de paie n'ont de sens que pour elle. */
    public boolean estUnePrime() {
        return this == PRIME_POURCENTAGE || this == PRIME_HORAIRE;
    }

    void exigerValeur(BigDecimal valeur) {
        boolean tropBas = valeur == null || valeur.signum() < 0 || strictementPositif && valeur.signum() == 0;
        if (tropBas || valeur.compareTo(maximum) > 0) {
            throw RefusPaie.invalide("« valeur » d'une règle « " + code + " » : " + (strictementPositif ? "plus de 0" : "0 ou plus")
                    + " et au plus " + maximum.toPlainString());
        }
    }

    public static TypeRegle de(Object code) {
        return Arrays.stream(values()).filter(t -> t.code.equals(code)).findFirst()
                .orElseThrow(() -> RefusPaie.invalide("« type » : " + Arrays.stream(values()).map(TypeRegle::code).collect(Collectors.joining(", "))));
    }
}
