package ca.vina.nexu.pays.domaine;

import ca.vina.nexu.pays.infrastructure.persistance.RegistrePays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Moteurs pays — données dégénérées et bornes.
 *
 * <p>Les quatre packs livrés ne contiennent aujourd'hui ni barème borné en haut, ni pays sans
 * autorité de remise, ni cotisation à taux absent. Les moteurs traitent pourtant ces cas — c'est
 * le principe ouvert/fermé : ajouter un pays est un ajout de <em>données</em>. Ces tests montent
 * donc des packs de synthèse, pour que le cinquième pays ne découvre pas ces chemins en
 * production.
 */
@DisplayName("Moteurs pays — données dégénérées et bornes")
class MoteursBornesTest {

    private final RegistrePays registre = new RegistrePays();

    /** Pack minimal : c'est l'objet de ces tests que de le rendre volontairement incomplet. */
    private static PaysPack pack(List<PaysPack.Cotisation> cotisations,
                                 List<PaysPack.Tranche> bareme,
                                 List<PaysPack.Autorite> autorites) {
        return new PaysPack("ZZ", "Zetopia", "🏳", "EUR", "fr-ZZ", "IFRS", "année civile",
                "TVA", List.of(), List.of(), cotisations, 0.15, bareme, autorites, true, "pack d'essai");
    }

    // ── Moteur de paie ──────────────────────────────────────────────────────

    @Test
    @DisplayName("un nombre de périodes absent ou nul retombe sur 12")
    void periodesAbsentesOuNulles() {
        PaysPack fr = registre.pack("FR").orElseThrow();
        MoteurPaie.ResultatPaie reference = MoteurPaie.calculer(fr, 48000, 12);

        // `periodsPerYear || 12` du Node. Zéro n'est pas « aucune période » : ce serait une
        // division par zéro dans tous les montants par période.
        assertThat(MoteurPaie.calculer(fr, 48000, null)).isEqualTo(reference);
        assertThat(MoteurPaie.calculer(fr, 48000, 0)).isEqualTo(reference);
    }

    @Test
    @DisplayName("un nombre de périodes négatif est ramené à un, jamais propagé")
    void periodesNegatives() {
        MoteurPaie.ResultatPaie r = MoteurPaie.calculer(registre.pack("FR").orElseThrow(), 48000, -4);
        assertThat(r.periodsPerYear()).isEqualTo(1);
        assertThat(r.period().gross()).isEqualTo(r.annualGross());
    }

    @Test
    @DisplayName("une cotisation sans taux salarial ne produit pas de ligne salariale")
    void cotisationSansTauxSalarial() {
        // Taux absent et taux à zéro sont tous deux faux en JavaScript : ni l'un ni l'autre ne
        // doit produire une ligne à 0,00 € sur le bulletin.
        PaysPack zz = pack(List.of(
                new PaysPack.Cotisation("PATRONALE", "Patronale seule", null, 0.05, null),
                new PaysPack.Cotisation("NEANT", "Taux nuls", 0d, 0d, null)),
                List.of(new PaysPack.Tranche(null, 0.1)), List.of());

        MoteurPaie.ResultatPaie r = MoteurPaie.calculer(zz, 40000, 12);

        assertThat(r.employeeContributions()).isEmpty();
        assertThat(r.employerContributions()).extracting(MoteurPaie.LigneCotisation::code)
                .containsExactly("PATRONALE");
    }

    // ── Moteur d'impôt ──────────────────────────────────────────────────────

    @Test
    @DisplayName("un barème borné en haut n'ampute pas le revenu qui le dépasse")
    void baremeBorneEnHaut() {
        // Aucun pays livré n'a de tranche supérieure finie ; un pays qui plafonnerait son barème
        // ferait sortir la boucle par sa condition d'arrêt, chemin jamais emprunté jusqu'ici.
        PaysPack zz = pack(List.of(), List.of(
                new PaysPack.Tranche(10000d, 0.10),
                new PaysPack.Tranche(20000d, 0.20)), List.of());

        MoteurImpot.ResultatImpot r = MoteurImpot.calculer(zz, "individual", 50000);

        assertThat(r.brackets()).hasSize(2);
        // 10 000 × 10 % + 10 000 × 20 % = 3 000 : au-delà de la dernière tranche, plus rien n'est
        // imposé — le revenu excédentaire n'est ni ignoré ni taxé au taux maximal.
        assertThat(r.tax()).isEqualTo(3000);
    }

    @Test
    @DisplayName("la tranche supérieure sans plafond absorbe tout le reste")
    void trancheSuperieureSansPlafond() {
        MoteurImpot.ResultatImpot r = MoteurImpot.calculer(registre.pack("CA").orElseThrow(), "individual", 1_000_000);
        PaysPack ca = registre.pack("CA").orElseThrow();

        assertThat(r.brackets()).hasSize(ca.personalIncomeTax().size());
        assertThat(r.brackets().getLast().to()).as("dernière tranche : pas de plafond").isNull();
        assertThat(r.tax()).isGreaterThan(0);
    }

    // ── Moteur de remise ────────────────────────────────────────────────────

    @Test
    @DisplayName("sans impôt retenu, aucune ligne d'impôt n'est ajoutée")
    void sansImpotRetenu() {
        // Un brut nul ne produit pas d'impôt : la ligne IRPP à 0,00 $ ne doit pas apparaître en
        // tête de la remise, sinon le fichier plat annonce un versement inexistant.
        PaysPack ca = registre.pack("CA").orElseThrow();
        MoteurRemise.ResultatRemise r = MoteurRemise.calculer(ca, List.of(MoteurPaie.calculer(ca, 0, 12)));

        assertThat(r.incomeTaxWithheld()).isZero();
        assertThat(r.lines()).extracting(MoteurRemise.LigneRemise::code).doesNotContain("IRPP");
    }

    @Test
    @DisplayName("un pays sans autorité de remise garde le code comme libellé")
    void paysSansAutorite() {
        // `remittanceAuthorities` absent : le moteur ne doit ni planter, ni inventer un
        // organisme. Le code fait alors office de libellé.
        PaysPack zz = pack(List.of(new PaysPack.Cotisation("XYZ", "Contribution XYZ", 0.02, 0.03, null)),
                List.of(new PaysPack.Tranche(null, 0.1)), null);

        MoteurRemise.ResultatRemise r = MoteurRemise.calculer(zz, List.of(MoteurPaie.calculer(zz, 40000, 12)));

        assertThat(r.lines()).extracting(MoteurRemise.LigneRemise::authority).contains("XYZ");
        assertThat(r.byAuthority()).extracting(MoteurRemise.TotalAutorite::label).contains("XYZ");
    }

    @Test
    @DisplayName("une liste d'autorités vide se comporte comme une liste absente")
    void listeDAutoritesVide() {
        PaysPack zz = pack(List.of(new PaysPack.Cotisation("XYZ", "Contribution XYZ", 0.02, 0.03, null)),
                List.of(new PaysPack.Tranche(null, 0.1)), List.of());

        MoteurRemise.ResultatRemise r = MoteurRemise.calculer(zz, List.of(MoteurPaie.calculer(zz, 40000, 12)));

        // L'impôt retenu suit le même repli : le pays n'ayant pas d'autorité fiscale connue, la
        // ligne d'impôt s'adresse à « TAX » — un marqueur, et non un organisme inventé.
        assertThat(r.lines()).extracting(MoteurRemise.LigneRemise::authority)
                .containsExactly("TAX", "XYZ");
    }

    @Test
    @DisplayName("une autorité connue du code mais absente du pack garde son code")
    void autoriteHorsDuPack() {
        // « RRQ » est associé à « RQ » dans le code du moteur. Si le pack ne déclare pas RQ, le
        // libellé doit retomber sur le code plutôt que de rendre celui d'un autre organisme.
        PaysPack zz = pack(List.of(new PaysPack.Cotisation("RRQ", "Régime de rentes", 0.064, 0.064, null)),
                List.of(new PaysPack.Tranche(null, 0.1)),
                List.of(new PaysPack.Autorite("AUTRE", "Autre organisme", null, null)));

        MoteurRemise.ResultatRemise r = MoteurRemise.calculer(zz, List.of(MoteurPaie.calculer(zz, 40000, 12)));

        MoteurRemise.TotalAutorite rq = r.byAuthority().stream()
                .filter(t -> t.authority().equals("RQ")).findFirst().orElseThrow();
        assertThat(rq.label()).isEqualTo("RQ");
    }
}
