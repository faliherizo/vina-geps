package ca.vina.nexu.paie.domaine;

import java.math.BigDecimal;

/**
 * Une ligne de gains d'une fiche de paie (ADR 0084) : heures régulières à un taux, heures supplémentaires, part de
 * salaire, ou une prime de règle variable. Le brut est la somme des montants, arrondis au cent ligne par ligne.
 *
 * @param quantite des heures, ou 1 pour une part de salaire et un forfait
 * @param taux     $ par heure ; la fraction d'une prime en pourcentage ; le montant d'un forfait
 */
public record Gain(String code, String libelle, Nature nature, BigDecimal quantite, BigDecimal taux, BigDecimal montant) {

    public enum Nature {
        HEURES_REGULIERES("heures-regulieres"), HEURES_SUPPLEMENTAIRES("heures-supplementaires"), SALAIRE("salaire"),
        PRIME_POURCENTAGE("prime-pourcentage"), PRIME_HORAIRE("prime-horaire"), FORFAIT("forfait");

        private final String code;

        Nature(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public static final String CODE_REGULIER = "REG";
    public static final String CODE_SUPPLEMENTAIRE = "HS";
    public static final String CODE_SALAIRE = "SAL";
}
