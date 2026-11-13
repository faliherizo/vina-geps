package ca.vina.nexu.partage.nombres;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La précision (exigence 18) — jumeau de {@code backend/src/precision/index.ts}.
 *
 * <p>L'essai qui compte est celui du fichier d'en-tête : trois services au prorata de surfaces
 * presque égales. À deux décimales, dix mille dollars disparaissent.
 */
@DisplayName("Précision — deux échelles nommées, une répartition qui ne perd rien")
class PrecisionTest {

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    @Nested
    @DisplayName("Les échelles et les modes")
    class Echelles {

        @Test
        @DisplayName("montant à 2 décimales, facteur à 4 — et les codes se relisent")
        void deuxEchelles() {
            assertThat(Precision.Echelle.de("montant").decimales()).isEqualTo(2);
            assertThat(Precision.Echelle.de("facteur").decimales()).isEqualTo(4);
            assertThat(Precision.montant(n("1.005"))).isEqualByComparingTo("1.00");
            assertThat(Precision.facteur(n("0.333333"))).isEqualByComparingTo("0.3333");
        }

        @Test
        @DisplayName("**l'arrondi du banquier** évite le biais vers le haut sur de longues séries")
        void demiPair() {
            assertThat(Precision.montant(n("2.345"))).isEqualByComparingTo("2.34");
            assertThat(Precision.montant(n("2.355"))).isEqualByComparingTo("2.36");
            assertThat(Precision.montant(n("2.345"), Precision.Mode.DEMI_HAUT)).isEqualByComparingTo("2.35");
        }

        @Test
        @DisplayName("plancher et plafond suivent l'axe des réels, même en négatif")
        void plancherEtPlafond() {
            assertThat(Precision.montant(n("-1.999"), Precision.Mode.PLANCHER)).isEqualByComparingTo("-2.00");
            assertThat(Precision.montant(n("-1.001"), Precision.Mode.PLAFOND)).isEqualByComparingTo("-1.00");
        }

        @Test
        @DisplayName("**le zéro négatif n'existe pas** dans un état financier")
        void zeroNegatif() {
            assertThat(Precision.montant(n("-0.001")).toPlainString()).isEqualTo("0.00");
        }

        @Test
        @DisplayName("chaque échelle et chaque mode rend son code — c'est lui que l'API écrit")
        void codes() {
            assertThat(Precision.Echelle.MONTANT.code()).isEqualTo("montant");
            assertThat(Precision.Echelle.FACTEUR.code()).isEqualTo("facteur");
            assertThat(Precision.Mode.values()).extracting(Precision.Mode::code)
                    .containsExactly("demi_haut", "demi_pair", "plancher", "plafond");
            assertThat(Precision.Mode.de("plafond")).isEqualTo(Precision.Mode.PLAFOND);
            // Le facteur à mode explicite : l'inducteur qu'on veut plafonner, pas arrondir au pair.
            assertThat(Precision.facteur(n("0.33335"), Precision.Mode.PLAFOND)).isEqualByComparingTo("0.3334");
        }

        @Test
        @DisplayName("une échelle ou un mode hors de l'ensemble est nommé")
        void inconnus() {
            assertThatThrownBy(() -> Precision.Echelle.de("millieme"))
                    .hasMessageContaining("échelle inconnue : « millieme » (attendu : montant, facteur)");
            assertThatThrownBy(() -> Precision.Mode.de("tronque"))
                    .hasMessageContaining("mode d'arrondi inconnu : « tronque »");
            assertThatThrownBy(() -> Precision.arrondir(null, Precision.Echelle.MONTANT, Precision.Mode.DEMI_PAIR))
                    .hasMessageContaining("la valeur à arrondir est un nombre fini");
        }
    }

    @Nested
    @DisplayName("Répartir sans perdre un cent")
    class Repartir {

        @Test
        @DisplayName("**le cas du fichier d'en-tête** : 1 000 000 $ sur trois surfaces presque égales")
        void troisServices() {
            Precision.Repartition r = Precision.repartir(n("1000000"), List.of(
                    new Precision.Poids("A", n("17342")),
                    new Precision.Poids("B", n("17343")),
                    new Precision.Poids("C", n("17344"))));
            BigDecimal somme = r.parts().stream().map(Precision.PartRepartie::montant)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(somme).isEqualByComparingTo("1000000.00");
            assertThat(r.parts()).extracting(Precision.PartRepartie::facteur)
                    .allSatisfy(f -> assertThat(f.scale()).isEqualTo(4));
        }

        @Test
        @DisplayName("**la dernière part absorbe l'écart**, et l'écart est rendu plutôt que dissimulé")
        void ecartRendu() {
            Precision.Repartition r = Precision.repartir(n("100"), List.of(
                    new Precision.Poids("A", BigDecimal.ONE),
                    new Precision.Poids("B", BigDecimal.ONE),
                    new Precision.Poids("C", BigDecimal.ONE)));
            assertThat(r.parts()).extracting(Precision.PartRepartie::montant)
                    .containsExactly(n("33.33"), n("33.33"), n("33.34"));
            assertThat(r.total()).isEqualByComparingTo("100.00");
            assertThat(r.ecartAbsorbe()).isNotNull();
        }

        @Test
        @DisplayName("refuse une répartition sans part, et une somme de poids nulle")
        void refus() {
            assertThatThrownBy(() -> Precision.repartir(n("100"), List.of()))
                    .hasMessageContaining("une répartition porte au moins une part");
            assertThatThrownBy(() -> Precision.repartir(n("100"),
                    List.of(new Precision.Poids("A", BigDecimal.ZERO))))
                    .hasMessageContaining("la somme des poids est strictement positive");
            assertThatThrownBy(() -> Precision.repartir(null, List.of(new Precision.Poids("A", BigDecimal.ONE))))
                    .hasMessageContaining("le montant à répartir est un nombre fini");
            assertThatThrownBy(() -> Precision.repartir(n("100"), List.of(new Precision.Poids("A", null))))
                    .hasMessageContaining("le poids de « A » est un nombre fini");
        }

        @Test
        @DisplayName("une seule part reçoit tout")
        void unePart() {
            assertThat(Precision.repartir(n("100"), List.of(new Precision.Poids("A", n("7"))))
                    .parts()).singleElement()
                    .extracting(Precision.PartRepartie::montant).isEqualTo(n("100.00"));
        }
    }
}
