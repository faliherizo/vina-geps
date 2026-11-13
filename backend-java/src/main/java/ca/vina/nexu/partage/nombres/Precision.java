package ca.vina.nexu.partage.nombres;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * <strong>La précision des calculs</strong> — STO / AI-2026-03, exigence 18.
 *
 * <p>Jumeau de {@code backend/src/precision/index.ts}.
 *
 * <p>Le socle arrondit <strong>au cent</strong> partout, et c'est correct pour de la comptabilité :
 * on ne paie pas un tiers de cent. Mais un <strong>inducteur de coût</strong> — un pourcentage
 * d'utilisation, une clé de répartition — n'est pas un montant : c'est un <strong>facteur</strong>,
 * et arrondi au centième il propage son erreur sur tout un modèle.
 *
 * <p>Trois services se partagent 1 000 000 $ au prorata de 17 342, 17 343 et 17 344 pieds carrés :
 * à deux décimales chacun reçoit 0,33, soit 990 000 $ au total. <strong>Dix mille dollars
 * disparaissent</strong>, et personne ne les cherche, parce que chaque ligne paraît juste.
 *
 * <p>Deux échelles, nommées : <strong>montant</strong> (2 décimales) pour ce qui s'impute et se
 * rapproche d'un relevé, <strong>facteur</strong> (4 décimales) pour ce qui multiplie. L'arrondi au
 * montant n'intervient qu'à l'imputation finale.
 *
 * <p>Là où le Node doit ruser avec la virgule flottante ({@code 1.005} n'est pas stocké comme
 * 1,005), {@code BigDecimal} calcule en décimal : la ruse n'a pas de jumeau ici, seulement son
 * résultat.
 */
public final class Precision {

    private Precision() {
    }

    /** Les échelles déclarées. Un nombre de décimales écrit dans un appel ne se relit pas. */
    public enum Echelle {
        MONTANT("montant", 2),
        FACTEUR("facteur", 4);

        private final String code;
        private final int decimales;

        Echelle(String code, int decimales) {
            this.code = code;
            this.decimales = decimales;
        }

        public String code() {
            return code;
        }

        public int decimales() {
            return decimales;
        }

        public static Echelle de(String code) {
            return Arrays.stream(values()).filter(e -> e.code.equals(code)).findFirst()
                    .orElseThrow(() -> new PrecisionInvalideException("échelle inconnue : « " + code
                            + " » (attendu : montant, facteur)"));
        }
    }

    /**
     * Les modes d'arrondi. <strong>Le défaut n'est pas innocent</strong> : {@code demi_pair}
     * (arrondi du banquier) évite le biais systématique vers le haut de {@code demi_haut} sur de
     * longues séries — c'est la règle des normes comptables internationales, et elle change le
     * total sur dix mille lignes.
     */
    public enum Mode {
        DEMI_HAUT("demi_haut", RoundingMode.HALF_UP),
        DEMI_PAIR("demi_pair", RoundingMode.HALF_EVEN),
        PLANCHER("plancher", RoundingMode.FLOOR),
        PLAFOND("plafond", RoundingMode.CEILING);

        private final String code;
        private final RoundingMode regle;

        Mode(String code, RoundingMode regle) {
            this.code = code;
            this.regle = regle;
        }

        public String code() {
            return code;
        }

        public static Mode de(String code) {
            return Arrays.stream(values()).filter(m -> m.code.equals(code)).findFirst()
                    .orElseThrow(() -> new PrecisionInvalideException("mode d'arrondi inconnu : « " + code
                            + " » (attendu : demi_haut, demi_pair, plancher, plafond)"));
        }
    }

    public static class PrecisionInvalideException extends RuntimeException {
        public PrecisionInvalideException(String message) {
            super(message);
        }
    }

    private static BigDecimal verifier(BigDecimal n, String sujet) {
        if (n == null) {
            throw new PrecisionInvalideException(sujet + " est un nombre fini");
        }
        return n;
    }

    /** <strong>Arrondir à une échelle nommée.</strong> Le zéro négatif n'existe pas en comptabilité. */
    public static BigDecimal arrondir(BigDecimal n, Echelle echelle, Mode mode) {
        BigDecimal arrondi = verifier(n, "la valeur à arrondir")
                .setScale(echelle.decimales(), mode.regle);
        return arrondi.signum() == 0 ? arrondi.abs() : arrondi;
    }

    /** Un montant : deux décimales. C'est ce qui s'impute et se rapproche d'un relevé. */
    public static BigDecimal montant(BigDecimal n) {
        return arrondir(n, Echelle.MONTANT, Mode.DEMI_PAIR);
    }

    public static BigDecimal montant(BigDecimal n, Mode mode) {
        return arrondir(n, Echelle.MONTANT, mode);
    }

    /** Un facteur : quatre décimales. C'est ce qui multiplie — taux, clés, inducteurs. */
    public static BigDecimal facteur(BigDecimal n) {
        return arrondir(n, Echelle.FACTEUR, Mode.DEMI_PAIR);
    }

    public static BigDecimal facteur(BigDecimal n, Mode mode) {
        return arrondir(n, Echelle.FACTEUR, mode);
    }

    public record Poids(String cle, BigDecimal poids) { }

    public record PartRepartie(String cle, BigDecimal poids, BigDecimal facteur, BigDecimal montant) { }

    /** {@code ecartAbsorbe} vaut zéro quand la répartition tombe juste — il est rendu, jamais dissimulé. */
    public record Repartition(BigDecimal total, List<PartRepartie> parts, BigDecimal ecartAbsorbe) { }

    /**
     * <strong>Répartir un montant au prorata, sans perdre un cent</strong> (exigence 44).
     *
     * <p>Deux décisions, et chacune répond à un défaut observé :
     *
     * <p>1. <strong>Les facteurs sont calculés à 4 décimales</strong>, les montants arrondis à 2 —
     * dans cet ordre. L'inverse fait disparaître de l'argent.
     *
     * <p>2. <strong>La dernière part absorbe l'écart d'arrondi</strong>, et cet écart est
     * <em>rendu</em> plutôt que dissimulé. Une répartition qui perd un cent rend le rapprochement
     * impossible, et personne ne sait laquelle des parts a été lésée.
     */
    public static Repartition repartir(BigDecimal total, List<Poids> poids) {
        verifier(total, "le montant à répartir");
        if (poids.isEmpty()) {
            throw new PrecisionInvalideException("une répartition porte au moins une part");
        }
        BigDecimal somme = BigDecimal.ZERO;
        for (Poids p : poids) {
            somme = somme.add(verifier(p.poids(), "le poids de « " + p.cle() + " »"));
        }
        if (somme.signum() <= 0) {
            throw new PrecisionInvalideException(
                    "la somme des poids est strictement positive : sans elle, aucune clé de répartition");
        }

        BigDecimal cible = montant(total);
        List<PartRepartie> parts = new ArrayList<>();
        BigDecimal attribue = BigDecimal.ZERO;
        BigDecimal theoriqueBrut = BigDecimal.ZERO;

        for (int i = 0; i < poids.size(); i++) {
            Poids p = poids.get(i);
            BigDecimal f = facteur(p.poids().divide(somme, Echelle.FACTEUR.decimales() + 4, RoundingMode.HALF_EVEN));
            theoriqueBrut = theoriqueBrut.add(cible.multiply(f));
            // La dernière part prend le reste : c'est ce qui conserve le total au cent près.
            BigDecimal m = i == poids.size() - 1 ? montant(cible.subtract(attribue)) : montant(cible.multiply(f));
            attribue = montant(attribue.add(m));
            parts.add(new PartRepartie(p.cle(), p.poids(), f, m));
        }

        return new Repartition(cible, parts, montant(cible.subtract(montant(theoriqueBrut))));
    }
}
