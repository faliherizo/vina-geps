package ca.vina.nexu.pays.domaine;

import ca.vina.nexu.pays.infrastructure.persistance.RegistrePays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valeurs de référence des moteurs pays, relevées sur le <strong>backend Node</strong>.
 *
 * <p>Le diff de contrat prouve l'équivalence à un instant donné, mais il exige les deux backends
 * en marche : il ne protège pas un développeur qui modifierait un arrondi demain. Ces valeurs
 * figent le comportement de référence dans la suite de tests, où elles échouent en quelques
 * secondes.
 *
 * <p>Elles ne sont pas calculées à la main : elles sont extraites du corpus enregistré contre le
 * Node (`contract/corpus/pays-node.jsonl`). Les recalculer soi-même reviendrait à tester sa
 * propre compréhension plutôt que le comportement réel.
 */
@DisplayName("Moteurs pays — équivalence avec le backend Node")
class MoteursPaysTest {

    private final RegistrePays registre = new RegistrePays();

    @Test
    @DisplayName("paie française : 48 000 € sur 12 périodes")
    void paieFrance() {
        MoteurPaie.ResultatPaie r = MoteurPaie.calculer(registre.pack("FR").orElseThrow(), 48000, 12);

        assertThat(r.annualIncomeTax()).isEqualTo(7686.23);
        assertThat(r.annualEmployeeDeductions()).isEqualTo(15541.62);
        assertThat(r.annualNet()).isEqualTo(32458.38);
        assertThat(r.annualEmployerContributions()).isEqualTo(12148.46);
        assertThat(r.annualEmployerCost()).isEqualTo(60148.46);
    }

    @Test
    @DisplayName("le plafond borne la base, sans annuler la cotisation")
    void plafondBorneLaBase() {
        // RRQ canadienne : plafond 68 500 $. Au-delà, la base reste au plafond — la confondre
        // avec « pas de cotisation » retirerait des milliers de dollars de la remise.
        MoteurPaie.ResultatPaie r = MoteurPaie.calculer(registre.pack("CA").orElseThrow(), 150000, 26);
        MoteurPaie.LigneCotisation rrq = r.employeeContributions().stream()
                .filter(l -> l.code().equals("RRQ")).findFirst().orElseThrow();

        assertThat(rrq.base()).isEqualTo(68500);
        assertThat(rrq.base()).isLessThan(r.annualGross());
        assertThat(rrq.annual()).isGreaterThan(0);
    }

    @Test
    @DisplayName("impôt société : taux unique, sans détail par tranche")
    void impotSociete() {
        MoteurImpot.ResultatImpot r = MoteurImpot.calculer(registre.pack("CA").orElseThrow(), "corporate", 250000);

        assertThat(r.profile()).isEqualTo("corporate");
        // Le libellé est du contrat : le point décimal ne doit pas devenir une virgule sur une
        // JVM francophone.
        assertThat(r.method()).matches("corporateTaxRate \\d+\\.\\d%");
        assertThat(r.brackets()).as("absent — et non vide — pour une société").isNull();
    }

    @Test
    @DisplayName("impôt particulier : détail par tranche, arrêté à la tranche atteinte")
    void impotParticulier() {
        MoteurImpot.ResultatImpot r = MoteurImpot.calculer(registre.pack("CA").orElseThrow(), "individual", 75000);

        assertThat(r.brackets()).isNotEmpty();
        assertThat(r.tax()).isGreaterThan(0);
        assertThat(r.effectiveRate()).isBetween(0.0, 1.0);
        // La boucle s'arrête à la tranche où le revenu tombe : le détail ne liste pas les
        // tranches supérieures non atteintes.
        assertThat(r.brackets().size()).isLessThanOrEqualTo(registre.pack("CA").orElseThrow().personalIncomeTax().size());
    }

    @Test
    @DisplayName("un revenu nul donne un taux effectif nul, pas une division par zéro")
    void revenuNul() {
        MoteurImpot.ResultatImpot r = MoteurImpot.calculer(registre.pack("FR").orElseThrow(), "individual", 0);
        assertThat(r.tax()).isZero();
        assertThat(r.effectiveRate()).isZero();
    }

    @Test
    @DisplayName("la remise place l'impôt en tête et regroupe par autorité")
    void remiseParAutorite() {
        PaysPack ca = registre.pack("CA").orElseThrow();
        List<MoteurPaie.ResultatPaie> paies = List.of(
                MoteurPaie.calculer(ca, 60000, 12), MoteurPaie.calculer(ca, 90000, 12));

        MoteurRemise.ResultatRemise r = MoteurRemise.calculer(ca, paies);

        assertThat(r.employeeCount()).isEqualTo(2);
        // `unshift` : l'impôt ouvre la liste. Son rang décide aussi de l'ordre du fichier plat.
        assertThat(r.lines().getFirst().code()).isEqualTo("IRPP");
        assertThat(r.lines().getFirst().authority()).isEqualTo("RQ");
        assertThat(r.byAuthority()).extracting(MoteurRemise.TotalAutorite::authority).contains("RQ", "CRA");
        assertThat(r.remittanceFile())
                .startsWith("PAYS;CA;DEVISE;CAD;SALARIES;2;TOTAL;")
                .contains("\nAUTORITE;CODE;LIBELLE;PART_SALARIALE;PART_PATRONALE;TOTAL");
        // Séparateur décimal point, jamais virgule : le fichier part chez une administration.
        assertThat(r.remittanceFile()).doesNotContainPattern("\\d,\\d\\d;");
    }

    @Test
    @DisplayName("la DSN française se termine par un compteur de lignes cohérent")
    void dsnFrance() {
        PaysPack fr = registre.pack("FR").orElseThrow();
        List<FormatsOfficiels.Salarie> salaries = List.of(
                new FormatsOfficiels.Salarie(null, null, MoteurPaie.calculer(fr, 60000, 12)),
                new FormatsOfficiels.Salarie(null, null, MoteurPaie.calculer(fr, 90000, 12)));

        FormatsOfficiels.Remise r = FormatsOfficiels.generer(
                fr, salaries, java.util.Map.of("raisonSociale", "ACME SAS", "siren", "123456789"), "2026-07");

        assertThat(r.authority()).isEqualTo("URSSAF");
        assertThat(r.filename()).isEqualTo("DSN-202607.dsn");
        assertThat(r.content()).contains("\r\n").startsWith("S10.G00.00.001,'123456789'");

        String[] lignes = r.content().split("\r\n");
        String compteur = lignes[lignes.length - 2];
        // S90.G00.90.001 compte toutes les lignes, y compris elle-même.
        assertThat(compteur).isEqualTo("S90.G00.90.001,'" + (lignes.length - 1) + "'");
    }

    @Test
    @DisplayName("formulaire 941 : les lignes clés et le total après ajustements")
    void formulaire941() {
        PaysPack us = registre.pack("US").orElseThrow();
        FormatsOfficiels.Remise r = FormatsOfficiels.generer(us,
                List.of(new FormatsOfficiels.Salarie("Doe John", "123", MoteurPaie.calculer(us, 84000, 12))),
                java.util.Map.of("ein", "12-3456789", "name", "ACME INC"), "2026-Q3");

        assertThat(r.authority()).isEqualTo("IRS");
        assertThat(r.filename()).isEqualTo("IRS-941-2026-Q3.txt");
        assertThat(r.content())
                .startsWith("IRS FORM 941")
                .contains("EIN: 12-3456789")
                .contains("Line 1: Number of employees: 1")
                .contains("Line 12: Total taxes after adjustments and credits: ");
        // Séparateur décimal point : le formulaire part chez l'IRS.
        assertThat(r.content()).doesNotContainPattern("\\d,\\d\\d\\b");
    }

    @Test
    @DisplayName("déclaration malgache : IRSA, CNaPS et OSTIE, total en MGA")
    void declarationMalgache() {
        PaysPack mg = registre.pack("MG").orElseThrow();
        FormatsOfficiels.Remise r = FormatsOfficiels.generer(mg,
                List.of(new FormatsOfficiels.Salarie(null, null, MoteurPaie.calculer(mg, 24000000, 12))),
                java.util.Map.of("nif", "1234567890", "name", "SOCIETE SA"), "2026-07");

        assertThat(r.authority()).isEqualTo("DGI");
        assertThat(r.filename()).isEqualTo("MG-IRSA-CNaPS-2026-07.txt");
        assertThat(r.content())
                .contains("NIF: 1234567890")
                .contains("— CNaPS (Prevoyance sociale) —")
                .contains("Devise: MGA")
                .containsPattern("TOTAL A VERSER: [0-9.]+ MGA");
    }

    @Test
    @DisplayName("un pays sans format officiel n'en reçoit pas un inventé")
    void paysSansFormatOfficiel() {
        // Le Canada passe par SAGIR/SAGIP + TPZ-1015/PD7A, pas par cet endpoint. Le refus est
        // porté par le contrôleur (400), et `aUnFormatOfficiel` en est la source de vérité.
        assertThat(FormatsOfficiels.aUnFormatOfficiel("CA")).isFalse();
        assertThat(FormatsOfficiels.aUnFormatOfficiel("FR")).isTrue();
    }
}
