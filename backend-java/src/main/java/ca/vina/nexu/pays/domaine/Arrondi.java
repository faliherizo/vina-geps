package ca.vina.nexu.pays.domaine;

/**
 * Arrondis monétaires, reproduits <strong>à l'identique</strong> du backend Node.
 *
 * <p>Le TypeScript calcule en {@code double} et arrondit par {@code Math.round(n * 100) / 100}.
 * Passer à {@code BigDecimal} serait plus juste — et changerait des résultats au centime. La règle
 * de la migration est de <strong>reproduire d'abord</strong> : un diff de contrat vide, puis une
 * amélioration séparée et assumée. Mélanger les deux rendrait tout écart inexplicable.
 *
 * <p>Attention : {@code Math.round} de JavaScript arrondit −0,5 vers 0 (« half up » vers +∞), et
 * {@code Math.round} de Java fait exactement pareil sur les {@code double}. Les deux coïncident
 * donc, mais pour une raison qu'il vaut mieux avoir vérifiée qu'espérée — d'où les tests.
 */
public final class Arrondi {

    private Arrondi() {
    }

    /** Deux décimales — les montants. */
    public static double r2(double n) {
        return Math.round(n * 100) / 100.0;
    }

    /** Quatre décimales — les taux effectifs. */
    public static double r4(double n) {
        return Math.round(n * 10000) / 10000.0;
    }
}
