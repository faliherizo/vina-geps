package ca.vina.nexu.paie.domaine;

import java.math.BigDecimal;

/**
 * Ce qui varie d'un organisme à l'autre : le taux du FSS (selon sa masse salariale et son secteur), celui de la
 * CNESST (son unité de classification) et la majoration des heures supplémentaires (50 % au minimum, LNT art. 55,
 * davantage si la convention collective le prévoit).
 */
public record ReglagesPaie(BigDecimal tauxFss, BigDecimal tauxCnesst, BigDecimal majorationSupplementaire) {

    /** La majoration minimale de la Loi sur les normes du travail. */
    public static final BigDecimal MAJORATION_LEGALE = new BigDecimal("0.5");
    private static final BigDecimal TAUX_MAXIMAL = new BigDecimal("0.2");
    private static final BigDecimal MAJORATION_MAXIMALE = BigDecimal.ONE;

    public ReglagesPaie {
        exigerEntre(tauxFss, BigDecimal.ZERO, TAUX_MAXIMAL, "« tauxFss » : une fraction entre 0 et 0,2");
        exigerEntre(tauxCnesst, BigDecimal.ZERO, TAUX_MAXIMAL, "« tauxCnesst » : une fraction entre 0 et 0,2");
        exigerEntre(majorationSupplementaire, MAJORATION_LEGALE, MAJORATION_MAXIMALE,
                "« majorationSupplementaire » : au moins 0,5 (LNT, art. 55) et au plus 1");
    }

    /** Les réglages d'un organisme qui n'en a pas encore enregistré : ceux du barème, et la majoration légale. */
    public static ReglagesPaie parDefaut(Bareme b) {
        return new ReglagesPaie(b.tauxFssParDefaut(), b.tauxCnesstParDefaut(), MAJORATION_LEGALE);
    }

    private static void exigerEntre(BigDecimal v, BigDecimal min, BigDecimal max, String message) {
        if (v == null || v.compareTo(min) < 0 || v.compareTo(max) > 0) {
            throw RefusPaie.invalide(message);
        }
    }
}
