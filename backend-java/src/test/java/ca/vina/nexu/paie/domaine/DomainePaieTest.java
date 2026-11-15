package ca.vina.nexu.paie.domaine;

import ca.vina.nexu.partage.paie.HeuresApprouveesPort.FeuilleAPayer;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort.LigneHeures;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort.NatureHeures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static ca.vina.nexu.paie.domaine.CalculRetenuesTest.B2026;
import static ca.vina.nexu.paie.domaine.CalculRetenuesTest.REGLAGES;
import static ca.vina.nexu.paie.domaine.CalculRetenuesTest.d;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Domaine de la paie — dossiers, cycles, réglages, préparation et déclarations")
class DomainePaieTest {

    static final CalculRetenues.MontantsPersonnels BASE = CalculRetenues.MontantsPersonnels.deBase(B2026);
    static final Trace TRACE = new Trace("T", "paie.admin", "2026-03-02T15:00:00Z");

    static DossierRemuneration horaire(String ressourceId, String taux) {
        return new DossierRemuneration(ressourceId, ModeRemuneration.HORAIRE, Frequence.AUX_DEUX_SEMAINES, d(taux), null, BASE);
    }

    static FeuilleAPayer feuille(String id, String ressourceId, String regulieres, String supplementaires) {
        return new FeuilleAPayer(id, ressourceId, "M-" + ressourceId, "Nom " + ressourceId, "2026-02-16", List.of(
                new LigneHeures("2026-02-16", "REG", NatureHeures.REGULIERE, d(regulieres)),
                new LigneHeures("2026-02-17", "HS", NatureHeures.SUPPLEMENTAIRE, d(supplementaires))));
    }

    static void refus(Runnable geste, RefusPaie.Nature nature, String message) {
        assertThatThrownBy(geste::run).isInstanceOfSatisfying(RefusPaie.class, e -> assertThat(e.nature()).isEqualTo(nature)).hasMessage(message);
    }

    @Test
    @DisplayName("les codes : fréquences, modes et états se lisent par leur code, et un code inconnu est nommé")
    void codes() {
        assertThat(Frequence.de("aux-deux-semaines")).isEqualTo(Frequence.AUX_DEUX_SEMAINES);
        assertThat(Frequence.MENSUELLE.paiesParAn()).isEqualTo(12);
        assertThat(Frequence.HEBDOMADAIRE.code()).isEqualTo("hebdomadaire");
        assertThat(ModeRemuneration.de("salaire")).isEqualTo(ModeRemuneration.SALAIRE);
        assertThat(EtatCycle.de("traite")).isEqualTo(EtatCycle.TRAITE);
        refus(() -> Frequence.de("annuelle"), RefusPaie.Nature.INVALIDE, "« frequence » : hebdomadaire, aux-deux-semaines, bimensuelle ou mensuelle");
        refus(() -> ModeRemuneration.de(null), RefusPaie.Nature.INVALIDE, "« mode » : horaire ou salaire");
        refus(() -> EtatCycle.de("x"), RefusPaie.Nature.INVALIDE, "« etat » : prepare, traite ou annule");
        assertThat(RefusPaie.introuvable("x").nature()).isEqualTo(RefusPaie.Nature.INTROUVABLE);
        assertThat(RefusPaie.conflit("x").nature()).isEqualTo(RefusPaie.Nature.CONFLIT);
    }

    @Test
    @DisplayName("un dossier à l'heure exige un taux ; au salaire, un salaire ; rien de négatif")
    void dossiers() {
        refus(() -> new DossierRemuneration("r1", ModeRemuneration.HORAIRE, Frequence.MENSUELLE, null, null, BASE),
                RefusPaie.Nature.INVALIDE, "à l'heure, « tauxHoraire » est obligatoire et positif");
        refus(() -> new DossierRemuneration("r1", ModeRemuneration.SALAIRE, Frequence.MENSUELLE, null, d("0"), BASE),
                RefusPaie.Nature.INVALIDE, "au salaire, « salaireAnnuel » est obligatoire et positif");
        refus(() -> new DossierRemuneration(" ", ModeRemuneration.HORAIRE, Frequence.MENSUELLE, d("30"), null, BASE),
                RefusPaie.Nature.INVALIDE, "« ressourceId » est obligatoire");
        refus(() -> new DossierRemuneration(null, ModeRemuneration.HORAIRE, Frequence.MENSUELLE, d("30"), null, BASE),
                RefusPaie.Nature.INVALIDE, "« ressourceId » est obligatoire");
        refus(() -> new DossierRemuneration("r1", ModeRemuneration.HORAIRE, Frequence.MENSUELLE, d("-1"), null, BASE),
                RefusPaie.Nature.INVALIDE, "« tauxHoraire » ne peut pas être négatif");
        refus(() -> new DossierRemuneration("r1", ModeRemuneration.HORAIRE, Frequence.MENSUELLE, d("30"), null,
                new CalculRetenues.MontantsPersonnels(d("-5"), d("0"))), RefusPaie.Nature.INVALIDE, "« montantPersonnelFederal » ne peut pas être négatif");
    }

    @Test
    @DisplayName("le brut : heures au taux, supplémentaires majorées ; au salaire, la part de l'année, et les supplémentaires au taux s'il y en a un")
    void brut() {
        assertThat(horaire("r1", "32.50").brut(d("70"), d("4"), d("0.5"))).isEqualByComparingTo("2470.00");
        DossierRemuneration salarie = new DossierRemuneration("r2", ModeRemuneration.SALAIRE, Frequence.AUX_DEUX_SEMAINES, null, d("65000"), BASE);
        assertThat(salarie.brut(d("70"), d("4"), d("0.5"))).isEqualByComparingTo("2500.00");
        DossierRemuneration avecTaux = new DossierRemuneration("r3", ModeRemuneration.SALAIRE, Frequence.AUX_DEUX_SEMAINES, d("30"), d("65000"), BASE);
        assertThat(avecTaux.brut(d("70"), d("2"), d("1"))).isEqualByComparingTo("2620.00");
    }

    @Test
    @DisplayName("les réglages : FSS et CNESST en fraction, majoration d'au moins 50 % (LNT, art. 55)")
    void reglages() {
        assertThat(REGLAGES.tauxFss()).isEqualByComparingTo("0.0426");
        assertThat(REGLAGES.majorationSupplementaire()).isEqualByComparingTo("0.5");
        refus(() -> new ReglagesPaie(d("0.3"), d("0.01"), d("0.5")), RefusPaie.Nature.INVALIDE, "« tauxFss » : une fraction entre 0 et 0,2");
        refus(() -> new ReglagesPaie(d("0.01"), null, d("0.5")), RefusPaie.Nature.INVALIDE, "« tauxCnesst » : une fraction entre 0 et 0,2");
        refus(() -> new ReglagesPaie(d("0.01"), d("0.01"), d("0.25")), RefusPaie.Nature.INVALIDE,
                "« majorationSupplementaire » : au moins 0,5 (LNT, art. 55) et au plus 1");
        refus(() -> new ReglagesPaie(d("0.01"), d("-0.01"), d("0.5")), RefusPaie.Nature.INVALIDE, "« tauxCnesst » : une fraction entre 0 et 0,2");
    }

    @Test
    @DisplayName("la période d'un cycle : des dates, dans l'ordre, sur un mois au plus, payée après son début")
    void periodes() {
        CyclePaie.Periode p = new CyclePaie.Periode("2026-02-15", "2026-02-28", "2026-03-05");
        assertThat(p.annee()).isEqualTo(2026);
        assertThat(new CyclePaie.Periode("2025-12-14", "2025-12-27", "2026-01-02").annee()).isEqualTo(2026);
        refus(() -> new CyclePaie.Periode("2026-02-15", "2026-02-01", "2026-03-05"), RefusPaie.Nature.INVALIDE, "la période va de « debut » à « fin », sur un mois au plus");
        refus(() -> new CyclePaie.Periode("2026-01-01", "2026-03-01", "2026-03-05"), RefusPaie.Nature.INVALIDE, "la période va de « debut » à « fin », sur un mois au plus");
        refus(() -> new CyclePaie.Periode("2026-02-15", "2026-02-28", "2026-02-01"), RefusPaie.Nature.INVALIDE, "la date de paie ne précède pas le début de la période");
        refus(() -> new CyclePaie.Periode("15/02/2026", "2026-02-28", "2026-03-05"), RefusPaie.Nature.INVALIDE, "« debut » : une date AAAA-MM-JJ");
        refus(() -> new CyclePaie.Periode("2026-02-15", null, "2026-03-05"), RefusPaie.Nature.INVALIDE, "« fin » : une date AAAA-MM-JJ");
    }

    @Test
    @DisplayName("seul un cycle préparé se recalcule, se traite ou s'annule")
    void etats() {
        CyclePaie prepare = CyclePaie.prepare("c1", new CyclePaie.Periode("2026-02-15", "2026-02-28", "2026-03-05"), TRACE);
        assertThat(prepare.etat()).isEqualTo(EtatCycle.PREPARE);
        assertThat(prepare.preparePar()).isEqualTo("paie.admin");
        assertThat(prepare.periode().datePaie()).isEqualTo("2026-03-05");
        prepare.exigerPrepare();
        CyclePaie traite = new CyclePaie("c1", "2026-02-15", "2026-02-28", "2026-03-05", EtatCycle.TRAITE, "x", "y", "z", "w");
        refus(traite::exigerPrepare, RefusPaie.Nature.CONFLIT, "cycle traité : il est figé, avec les feuilles qu'il a payées");
        CyclePaie annule = new CyclePaie("c1", "2026-02-15", "2026-02-28", "2026-03-05", EtatCycle.ANNULE, "x", "y", null, null);
        refus(annule::exigerPrepare, RefusPaie.Nature.CONFLIT, "cycle annulé : préparez-en un nouveau");
    }

    @Test
    @DisplayName("préparer : une personne, un bulletin sur toutes ses feuilles ; sans dossier, nommée et non payée")
    void preparation() {
        CyclePaie cycle = CyclePaie.prepare("c1", new CyclePaie.Periode("2026-02-15", "2026-02-28", "2026-03-05"), TRACE);
        AtomicInteger n = new AtomicInteger();
        PreparationPaie.Sources s = new PreparationPaie.Sources(B2026, REGLAGES, Map.of("r1", horaire("r1", "30")),
                r -> Cumuls.AUCUN, () -> "b" + n.incrementAndGet());
        PreparationPaie.Resultat r = PreparationPaie.preparer(cycle, List.of(feuille("f1", "r1", "35", "2"), feuille("f2", "r1", "35", "0"),
                feuille("f3", "r9", "40", "0")), s);
        assertThat(r.bulletins()).singleElement().satisfies(b -> {
            assertThat(b.id()).isEqualTo("b1");
            assertThat(b.feuilles()).containsExactly("f1", "f2");
            assertThat(b.heuresRegulieres()).isEqualByComparingTo("70");
            assertThat(b.heuresSupplementaires()).isEqualByComparingTo("2");
            assertThat(b.retenues().brut()).isEqualByComparingTo("2190.00");
            assertThat(b.datePaie()).isEqualTo("2026-03-05");
            assertThat(b.nom()).isEqualTo("Nom r1");
        });
        assertThat(r.sansDossier()).containsExactly(new PreparationPaie.Personne("r9", "M-r9", "Nom r9"));
        assertThat(PreparationPaie.preparer(cycle, List.of(), s).bulletins()).isEmpty();
    }

    @Test
    @DisplayName("les remises du mois et les relevés de l'année se tirent des bulletins traités")
    void declarations() {
        Retenues janvier = CalculRetenuesTest.calculer("2000", Frequence.AUX_DEUX_SEMAINES, Cumuls.AUCUN);
        Retenues fevrier = CalculRetenuesTest.calculer("2000", Frequence.AUX_DEUX_SEMAINES, Cumuls.AUCUN.plus(janvier));
        List<Bulletin> bulletins = List.of(
                new Bulletin("b1", "c1", "r1", "M1", "Julie Roy", "2026-01-15", d("70"), d("0"), janvier, List.of("f1")),
                new Bulletin("b2", "c2", "r1", "M1", "Julie Roy", "2026-02-12", d("70"), d("0"), fevrier, List.of("f2")),
                new Bulletin("b3", "c2", "r2", "M2", "Marc Dubé", "2026-02-12", d("70"), d("0"), janvier, List.of("f3")));
        List<Declarations.Remise> remises = Declarations.remises(bulletins);
        assertThat(remises).extracting(Declarations.Remise::mois).containsExactly("2026-01", "2026-02");
        Declarations.Remise jan = remises.getFirst();
        assertThat(jan.echeance()).isEqualTo("2026-02-15");
        assertThat(jan.impotQuebec()).isEqualByComparingTo("167.53");
        assertThat(jan.rrq()).isEqualByComparingTo("235.04");
        assertThat(jan.rqap()).isEqualByComparingTo("20.64");
        assertThat(jan.fss()).isEqualByComparingTo("85.20");
        assertThat(jan.revenuQuebec()).isEqualByComparingTo("508.41");
        assertThat(jan.ae()).isEqualByComparingTo("62.40");
        assertThat(jan.arc()).isEqualByComparingTo("197.70");
        assertThat(jan.cnt()).isEqualByComparingTo("1.20");
        assertThat(jan.cnesst()).isEqualByComparingTo("35.60");
        assertThat(remises.get(1).echeance()).isEqualTo("2026-03-15");

        List<Declarations.Releve> releves = Declarations.releves(bulletins);
        assertThat(releves).extracting(Declarations.Releve::ressourceId).containsExactly("r1", "r2");
        Declarations.Releve julie = releves.getFirst();
        assertThat(julie.nom()).isEqualTo("Julie Roy");
        assertThat(julie.rl1().a()).isEqualByComparingTo("4000");
        assertThat(julie.rl1().b()).isEqualByComparingTo("235.04");
        assertThat(julie.rl1().e()).isEqualByComparingTo(janvier.impotQuebec().add(fevrier.impotQuebec()));
        assertThat(julie.rl1().g()).isEqualByComparingTo("4000");
        assertThat(julie.rl1().h()).isEqualByComparingTo("17.20");
        assertThat(julie.rl1().i()).isEqualByComparingTo("4000");
        assertThat(julie.rl1().c()).isEqualByComparingTo("52.00");
        assertThat(julie.rl1().rrq2()).isEqualByComparingTo("0");
        assertThat(julie.t4().c14()).isEqualByComparingTo("4000");
        assertThat(julie.t4().c22()).isEqualByComparingTo(janvier.impotFederal().add(fevrier.impotFederal()));
        assertThat(julie.t4().c24()).isEqualByComparingTo("4000");
        assertThat(julie.t4().c26()).isEqualByComparingTo("4000");
        assertThat(julie.t4().c17()).isEqualByComparingTo("235.04");
        assertThat(julie.t4().c18()).isEqualByComparingTo("52.00");
        assertThat(julie.t4().c55()).isEqualByComparingTo("17.20");
        assertThat(julie.t4().c56()).isEqualByComparingTo("4000");
        assertThat(julie.t4().rrq2()).isEqualByComparingTo("0");
        assertThat(Declarations.remises(List.of())).isEmpty();
    }

    @Test
    @DisplayName("le cumul s'additionne paie après paie ; un bulletin copie ses feuilles")
    void cumulsEtBulletin() {
        Retenues r = CalculRetenuesTest.calculer("2000", Frequence.AUX_DEUX_SEMAINES, Cumuls.AUCUN);
        Cumuls deux = Cumuls.AUCUN.plus(r).plus(r);
        assertThat(deux.brut()).isEqualByComparingTo("4000");
        assertThat(deux.rrq()).isEqualByComparingTo("235.04");
        assertThat(deux.gainsAe()).isEqualByComparingTo("4000");
        assertThat(deux.rqap()).isEqualByComparingTo("17.20");
        List<String> feuilles = new java.util.ArrayList<>(List.of("f1"));
        Bulletin b = new Bulletin("b", "c", "r", "m", "n", "2026-01-01", BigDecimal.ONE, BigDecimal.ZERO, r, feuilles);
        feuilles.add("f2");
        assertThat(b.feuilles()).containsExactly("f1");
    }
}
