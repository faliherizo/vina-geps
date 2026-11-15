package ca.vina.nexu.paie.domaine;

import ca.vina.nexu.paie.infrastructure.persistance.BaremesFichiers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Les cas de référence ont été calculés à part, en Python {@code Decimal}, d'après les formules de l'ADR 0076 (et non
 * d'après ce code). Avant toute production, ils se comparent à WinRAS : écart toléré de 0,02 $ par retenue.
 */
@DisplayName("Calcul des retenues 2026 — annualisation, plafonds de l'année, cotisations de l'employeur")
class CalculRetenuesTest {

    static final Bareme B2026 = new BaremesFichiers().pour(2026).orElseThrow();
    static final ReglagesPaie REGLAGES = ReglagesPaie.parDefaut(B2026);

    static Retenues calculer(String brut, Frequence f, Cumuls cumuls) {
        return CalculRetenues.calculer(B2026, REGLAGES, new CalculRetenues.Entree(new BigDecimal(brut), f, cumuls,
                CalculRetenues.MontantsPersonnels.deBase(B2026)));
    }

    static Cumuls cumuls(String gainsRrq, String rrq, String gainsAe, String brut) {
        BigDecimal z = BigDecimal.ZERO;
        return new Cumuls(new BigDecimal(brut), new BigDecimal(gainsRrq), new BigDecimal(rrq), z, new BigDecimal(gainsAe), z, z, z);
    }

    static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("le barème 2026 : RRQ 5,30 + 1,00 %, MGA 74 600 $, AE 1,30 %, RQAP 0,430 %, paliers indexés")
    void bareme() {
        assertThat(B2026.annee()).isEqualTo(2026);
        assertThat(B2026.rrq().taux()).isEqualByComparingTo("0.063");
        assertThat(B2026.rrq().mga()).isEqualByComparingTo("74600");
        assertThat(B2026.federal().tauxCredit()).isEqualByComparingTo("0.14");
        assertThat(B2026.quebec().montantPersonnel()).isEqualByComparingTo("18952");
        assertThat(new BaremesFichiers().pour(1999)).isEmpty();
    }

    @Test
    @DisplayName("2 000 $ aux deux semaines, début d'année : chaque retenue au cent près")
    void deuxSemaines() {
        Retenues r = calculer("2000", Frequence.AUX_DEUX_SEMAINES, Cumuls.AUCUN);
        assertThat(r.impotFederal()).isEqualByComparingTo("135.30");
        assertThat(r.impotQuebec()).isEqualByComparingTo("167.53");
        assertThat(r.rrq()).isEqualByComparingTo("117.52");
        assertThat(r.rrq2()).isEqualByComparingTo("0.00");
        assertThat(r.ae()).isEqualByComparingTo("26.00");
        assertThat(r.rqap()).isEqualByComparingTo("8.60");
        assertThat(r.totalRetenues()).isEqualByComparingTo("454.95");
        assertThat(r.net()).isEqualByComparingTo("1545.05");
        Retenues.Employeur e = r.employeur();
        assertThat(e.rrq()).isEqualByComparingTo("117.52");
        assertThat(e.ae()).isEqualByComparingTo("36.40");
        assertThat(e.rqap()).isEqualByComparingTo("12.04");
        assertThat(e.fss()).isEqualByComparingTo("85.20");
        assertThat(e.cnt()).isEqualByComparingTo("1.20");
        assertThat(e.cnesst()).isEqualByComparingTo("35.60");
        assertThat(r.coutEmployeur()).isEqualByComparingTo("2287.96");
        assertThat(r.gainsRrq()).isEqualByComparingTo("2000");
    }

    @Test
    @DisplayName("mensuel et hebdomadaire : le facteur d'annualisation change l'impôt, pas les taux")
    void autresFrequences() {
        Retenues m = calculer("6000", Frequence.MENSUELLE, Cumuls.AUCUN);
        assertThat(m.impotFederal()).isEqualByComparingTo("530.58");
        assertThat(m.impotQuebec()).isEqualByComparingTo("658.65");
        assertThat(m.rrq()).isEqualByComparingTo("359.63");
        Retenues h = calculer("500", Frequence.HEBDOMADAIRE, Cumuls.AUCUN);
        assertThat(h.impotFederal()).isEqualByComparingTo("13.89");
        assertThat(h.impotQuebec()).isEqualByComparingTo("14.47");
        assertThat(h.rrq()).isEqualByComparingTo("27.26");
        assertThat(h.employeur().rqap()).isEqualByComparingTo("3.01");
    }

    @Test
    @DisplayName("le MGA franchi en cours de paie : le RRQ s'arrête à son maximum, la RRQ2 prend la tranche au-delà")
    void rrq2() {
        Retenues r = calculer("3000", Frequence.AUX_DEUX_SEMAINES, cumuls("74000", "4400", "0", "74000"));
        assertThat(r.rrq()).isEqualByComparingTo("79.30");
        assertThat(r.rrq2()).isEqualByComparingTo("96.00");
        assertThat(r.employeur().rrq2()).isEqualByComparingTo("96.00");
        assertThat(r.impotFederal()).isEqualByComparingTo("278.72");
        assertThat(r.impotQuebec()).isEqualByComparingTo("332.21");
        // Au plafond supplémentaire, plus rien.
        Retenues fin = calculer("3000", Frequence.AUX_DEUX_SEMAINES,
                new Cumuls(d("85000"), d("85000"), d("4479.30"), d("416"), d("68900"), d("895.70"), d("103000"), d("442.90")));
        assertThat(fin.rrq()).isEqualByComparingTo("0");
        assertThat(fin.rrq2()).isEqualByComparingTo("0");
        assertThat(fin.ae()).isEqualByComparingTo("0");
        assertThat(fin.rqap()).isEqualByComparingTo("0");
        assertThat(fin.gainsRrq()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("le maximum assurable de l'AE atteint : seule la part restante cotise, l'employeur à 1,4 fois sur la base")
    void plafondAe() {
        Retenues r = calculer("2000", Frequence.AUX_DEUX_SEMAINES, cumuls("0", "0", "68000", "0"));
        assertThat(r.gainsAe()).isEqualByComparingTo("900");
        assertThat(r.ae()).isEqualByComparingTo("11.70");
        assertThat(r.employeur().ae()).isEqualByComparingTo("16.38");
        assertThat(r.impotFederal()).isEqualByComparingTo("136.97");
    }

    @Test
    @DisplayName("la CNT s'arrête à 103 000 $ de rémunération dans l'année")
    void plafondCnt() {
        Retenues r = calculer("2000", Frequence.AUX_DEUX_SEMAINES, cumuls("0", "0", "0", "102000"));
        assertThat(r.employeur().cnt()).isEqualByComparingTo("0.60");
    }

    @Test
    @DisplayName("un brut nul ne retient rien ; un brut négatif est refusé")
    void bornes() {
        Retenues r = calculer("0", Frequence.AUX_DEUX_SEMAINES, Cumuls.AUCUN);
        assertThat(r.totalRetenues()).isEqualByComparingTo("0");
        assertThat(r.employeur().total()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> calculer("-1", Frequence.MENSUELLE, Cumuls.AUCUN)).isInstanceOf(RefusPaie.class)
                .hasMessage("un brut ne peut pas être négatif");
    }

    @Test
    @DisplayName("un montant personnel déclaré plus élevé réduit l'impôt ; le barème se lit par tranches")
    void montantsDeclares() {
        Retenues base = calculer("2000", Frequence.AUX_DEUX_SEMAINES, Cumuls.AUCUN);
        Retenues declare = CalculRetenues.calculer(B2026, REGLAGES, new CalculRetenues.Entree(d("2000"), Frequence.AUX_DEUX_SEMAINES, Cumuls.AUCUN,
                new CalculRetenues.MontantsPersonnels(d("30000"), d("30000"))));
        assertThat(declare.impotFederal()).isLessThan(base.impotFederal());
        assertThat(declare.impotQuebec()).isLessThan(base.impotQuebec());
        // 100 000 $ : 14 % jusqu'à 58 523, puis 20,5 %.
        assertThat(CalculRetenues.progressif(d("100000"), B2026.federal())).isEqualByComparingTo("16696.005");
        assertThat(CalculRetenues.progressif(d("0"), B2026.federal())).isEqualByComparingTo("0");
        assertThat(CalculRetenues.progressif(d("300000"), B2026.quebec())).isEqualByComparingTo("66784.4625");
    }

    @Test
    @DisplayName("un salaire élevé : l'impôt fédéral annualisé franchit les paliers supérieurs")
    void hautsRevenus() {
        Retenues r = calculer("20000", Frequence.MENSUELLE, Cumuls.AUCUN);
        assertThat(r.impotFederal()).isPositive();
        assertThat(r.impotQuebec()).isGreaterThan(r.impotFederal());
        assertThat(r.gainsAe()).isEqualByComparingTo("20000");
    }
}
