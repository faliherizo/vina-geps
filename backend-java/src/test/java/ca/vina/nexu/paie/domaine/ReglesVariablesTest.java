package ca.vina.nexu.paie.domaine;

import ca.vina.nexu.partage.paie.HeuresApprouveesPort.EtatFeuille;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort.FeuilleAPayer;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort.FeuilleDeLaPeriode;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort.LigneHeures;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort.NatureHeures;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort.Plage;
import ca.vina.nexu.partage.paie.PersonnesPourPaiePort.Personne;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static ca.vina.nexu.paie.domaine.CalculRetenuesTest.B2026;
import static ca.vina.nexu.paie.domaine.CalculRetenuesTest.REGLAGES;
import static ca.vina.nexu.paie.domaine.CalculRetenuesTest.d;
import static ca.vina.nexu.paie.domaine.DomainePaieTest.BASE;
import static ca.vina.nexu.paie.domaine.DomainePaieTest.refus;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Règles de paie variable (ADR 0084) — échelle, primes de quart, forfaits, validation des temps")
class ReglesVariablesTest {

    static final LocalDate DU = LocalDate.parse("2026-01-01");
    static final ProfilPaie INFIRMIERE_E3 = new ProfilPaie("INF", "FIQ", 3);
    static final LocalDate PAIE = LocalDate.parse("2026-03-05");

    static ReglePaie regle(String code, TypeRegle type, String valeur, ConditionsRegle c) {
        return new ReglePaie("id-" + code, code, "Libellé " + code, type, d(valeur), c, DU, null, true);
    }

    static ConditionsRegle profil(String titre, String convention, Integer echelon) {
        return new ConditionsRegle(titre, convention, echelon, null, Set.of(), false, Set.of());
    }

    static ConditionsRegle heures(PlageHoraire plage, Set<DayOfWeek> jours, boolean feries, Set<String> codes) {
        return new ConditionsRegle(null, null, null, plage, jours, feries, codes);
    }

    static PlageHoraire plage(String debut, String fin) {
        return new PlageHoraire(LocalTime.parse(debut), LocalTime.parse(fin));
    }

    static Plage segment(String debut, String fin, String code) {
        return new Plage(LocalDateTime.parse(debut), LocalDateTime.parse(fin), code);
    }

    static DossierRemuneration aLEchelon(int echelon) {
        return new DossierRemuneration("r1", ModeRemuneration.HORAIRE, Frequence.AUX_DEUX_SEMAINES, null, null, BASE, echelon);
    }

    static FeuilleAPayer feuille(String id, List<LigneHeures> lignes, List<Plage> plages) {
        return new FeuilleAPayer(id, "r1", "P1", "Julie Roy", "2026-02-16", lignes, plages);
    }

    static LigneHeures ligne(String date, NatureHeures nature, String heures) {
        return new LigneHeures(date, nature == NatureHeures.REGULIERE ? "REG" : "HS", nature, d(heures));
    }

    static CalculGains.Contexte contexte(List<ReglePaie> regles, Set<LocalDate> feries) {
        return new CalculGains.Contexte(regles, feries, d("0.5"), PAIE);
    }

    @Test
    @DisplayName("les types : codes, maxima, et ce qu'une règle sans heures ne peut pas viser")
    void types() {
        assertThat(TypeRegle.de("prime-horaire")).isEqualTo(TypeRegle.PRIME_HORAIRE);
        assertThat(TypeRegle.FORFAIT.estUnePrime()).isFalse();
        assertThat(TypeRegle.PRIME_POURCENTAGE.estUnePrime()).isTrue();
        refus(() -> TypeRegle.de("bonus"), RefusPaie.Nature.INVALIDE, "« type » : taux-de-base, prime-pourcentage, prime-horaire, forfait");
        refus(() -> regle("TAUX", TypeRegle.TAUX_DE_BASE, "0", ConditionsRegle.AUCUNE), RefusPaie.Nature.INVALIDE,
                "« valeur » d'une règle « taux-de-base » : plus de 0 et au plus 1000");
        refus(() -> regle("NUIT", TypeRegle.PRIME_POURCENTAGE, "2.5", ConditionsRegle.AUCUNE), RefusPaie.Nature.INVALIDE,
                "« valeur » d'une règle « prime-pourcentage » : 0 ou plus et au plus 2");
        refus(() -> regle("NUIT", TypeRegle.PRIME_HORAIRE, "-1", ConditionsRegle.AUCUNE), RefusPaie.Nature.INVALIDE,
                "« valeur » d'une règle « prime-horaire » : 0 ou plus et au plus 1000");
        refus(() -> new ReglePaie(null, "NUIT", "Nuit", TypeRegle.PRIME_HORAIRE, null, ConditionsRegle.AUCUNE, DU, null, true), RefusPaie.Nature.INVALIDE,
                "« valeur » d'une règle « prime-horaire » : 0 ou plus et au plus 1000");
        assertThat(regle("ZERO", TypeRegle.PRIME_HORAIRE, "0", ConditionsRegle.AUCUNE).valeur()).isZero();
    }

    @Test
    @DisplayName("une règle : un code de gain, un libellé, une validité dans l'ordre ; ses conditions cohérentes avec son type")
    void validationDesRegles() {
        refus(() -> regle("nuit", TypeRegle.FORFAIT, "10", ConditionsRegle.AUCUNE), RefusPaie.Nature.INVALIDE,
                "« code » : 2 à 20 caractères parmi A-Z, 0-9 et _ (par exemple PRIME_NUIT)");
        refus(() -> new ReglePaie(null, null, "x", TypeRegle.FORFAIT, d("1"), ConditionsRegle.AUCUNE, DU, null, true), RefusPaie.Nature.INVALIDE,
                "« code » : 2 à 20 caractères parmi A-Z, 0-9 et _ (par exemple PRIME_NUIT)");
        refus(() -> new ReglePaie(null, "NUIT", " ", TypeRegle.FORFAIT, d("1"), ConditionsRegle.AUCUNE, DU, null, true), RefusPaie.Nature.INVALIDE,
                "« libelle » : de 2 à 80 caractères");
        refus(() -> new ReglePaie(null, "NUIT", null, TypeRegle.FORFAIT, d("1"), ConditionsRegle.AUCUNE, DU, null, true), RefusPaie.Nature.INVALIDE,
                "« libelle » : de 2 à 80 caractères");
        refus(() -> new ReglePaie(null, "NUIT", "x".repeat(81), TypeRegle.FORFAIT, d("1"), ConditionsRegle.AUCUNE, DU, null, true), RefusPaie.Nature.INVALIDE,
                "« libelle » : de 2 à 80 caractères");
        refus(() -> regle("NUIT", TypeRegle.FORFAIT, "1", heures(plage("23:00", "07:00"), Set.of(), false, Set.of())), RefusPaie.Nature.INVALIDE,
                "une règle « forfait » ne dépend pas des heures : ni plage, ni jours, ni férié, ni code de paie");
        for (ConditionsRegle c : List.of(heures(null, Set.of(DayOfWeek.MONDAY), false, Set.of()), heures(null, Set.of(), true, Set.of()),
                heures(null, Set.of(), false, Set.of("REG")))) {
            refus(() -> regle("TAUX", TypeRegle.TAUX_DE_BASE, "30", c), RefusPaie.Nature.INVALIDE,
                    "une règle « taux-de-base » ne dépend pas des heures : ni plage, ni jours, ni férié, ni code de paie");
        }
        refus(() -> new ReglePaie(null, "NUIT", "Nuit", TypeRegle.FORFAIT, d("1"), ConditionsRegle.AUCUNE, null, null, true), RefusPaie.Nature.INVALIDE,
                "« valideDu » est obligatoire");
        refus(() -> new ReglePaie(null, "NUIT", "Nuit", TypeRegle.FORFAIT, d("1"), ConditionsRegle.AUCUNE, DU, DU.minusDays(1), true), RefusPaie.Nature.INVALIDE,
                "« valideAu » ne précède pas « valideDu »");
        refus(() -> profil(null, null, 0), RefusPaie.Nature.INVALIDE, "« echelon » : de 1 à 30");
        refus(() -> profil(null, null, 31), RefusPaie.Nature.INVALIDE, "« echelon » : de 1 à 30");
        refus(() -> plage("08:00", "08:00"), RefusPaie.Nature.INVALIDE, "une plage horaire a un début et une fin distincts (« heureDebut », « heureFin »)");
        refus(() -> new PlageHoraire(null, LocalTime.NOON), RefusPaie.Nature.INVALIDE, "une plage horaire a un début et une fin distincts (« heureDebut », « heureFin »)");
        refus(() -> new PlageHoraire(LocalTime.NOON, null), RefusPaie.Nature.INVALIDE, "une plage horaire a un début et une fin distincts (« heureDebut », « heureFin »)");
        ReglePaie r = new ReglePaie(null, "NUIT", "  Prime de nuit ", TypeRegle.PRIME_POURCENTAGE, d("0.12"), ConditionsRegle.AUCUNE, DU, DU.plusDays(30), true);
        assertThat(r.libelle()).isEqualTo("Prime de nuit");
        assertThat(r.avecId("x").id()).isEqualTo("x");
        assertThat(r.enVigueurLe(DU)).isTrue();
        assertThat(r.enVigueurLe(DU.plusDays(30))).isTrue();
        assertThat(r.enVigueurLe(DU.plusDays(31))).isFalse();
        assertThat(r.enVigueurLe(DU.minusDays(1))).isFalse();
        assertThat(new ReglePaie(null, "NUIT", "Nuit", TypeRegle.FORFAIT, d("1"), ConditionsRegle.AUCUNE, DU, null, false).enVigueurLe(DU)).isFalse();
    }

    @Test
    @DisplayName("les conditions visent un profil : titre d'emploi, convention, échelon ; une condition absente ne filtre rien")
    void conditions() {
        assertThat(profil(null, null, null).visent(INFIRMIERE_E3)).isTrue();
        assertThat(profil("INF", "FIQ", 3).visent(INFIRMIERE_E3)).isTrue();
        assertThat(profil("PAB", null, null).visent(INFIRMIERE_E3)).isFalse();
        assertThat(profil(null, "APTS", null).visent(INFIRMIERE_E3)).isFalse();
        assertThat(profil(null, null, 4).visent(INFIRMIERE_E3)).isFalse();
        assertThat(profil("INF", "FIQ", 3).precision()).isEqualTo(7);
        assertThat(profil(null, null, null).precision()).isZero();
        ConditionsRegle fds = heures(null, Set.of(DayOfWeek.SATURDAY), true, Set.of("REG"));
        assertThat(fds.admettent(LocalDate.parse("2026-02-21"), Set.of(LocalDate.parse("2026-02-21")))).isTrue();
        assertThat(fds.admettent(LocalDate.parse("2026-02-21"), Set.of())).isFalse();
        assertThat(fds.admettent(LocalDate.parse("2026-02-20"), Set.of(LocalDate.parse("2026-02-20")))).isFalse();
        assertThat(fds.admettentLeCode("REG")).isTrue();
        assertThat(fds.admettentLeCode("HS")).isFalse();
        assertThat(ConditionsRegle.AUCUNE.admettentLeCode("HS")).isTrue();
        assertThat(ConditionsRegle.AUCUNE.visentLesHeures()).isFalse();
    }

    @Test
    @DisplayName("les heures admissibles : une nuit de samedi à dimanche se coupe à minuit ; une plage de nuit déborde de la veille")
    void heuresAdmissibles() {
        Plage nuit = segment("2026-02-21T22:00", "2026-02-22T06:00", "REG");
        // Une carte triée par jour : l'ordre attendu s'écrit donc dans l'ordre des jours, et non dans celui d'un Map.of.
        assertThat(HeuresAdmissibles.minutes(nuit, null, j -> true))
                .containsExactlyEntriesOf(new java.util.TreeMap<>(Map.of(LocalDate.parse("2026-02-21"), 120L, LocalDate.parse("2026-02-22"), 360L)));
        assertThat(HeuresAdmissibles.minutes(nuit, null, j -> j.getDayOfWeek() == DayOfWeek.SUNDAY)).containsExactlyEntriesOf(Map.of(LocalDate.parse("2026-02-22"), 360L));
        assertThat(HeuresAdmissibles.minutes(nuit, plage("00:00", "04:00"), j -> true)).containsExactlyEntriesOf(Map.of(LocalDate.parse("2026-02-22"), 240L));
        Plage matin = segment("2026-02-16T05:00", "2026-02-16T13:00", "REG");
        assertThat(HeuresAdmissibles.minutes(matin, plage("23:00", "07:00"), j -> true)).containsExactlyEntriesOf(Map.of(LocalDate.parse("2026-02-16"), 120L));
        assertThat(HeuresAdmissibles.minutes(matin, plage("16:00", "23:59"), j -> true)).isEmpty();
        assertThat(HeuresAdmissibles.minutes(segment("2026-02-16T08:00", "2026-02-16T08:00", "REG"), null, j -> true)).isEmpty();
        assertThat(HeuresAdmissibles.minutes(segment("2026-02-16T08:00", "2026-02-16T07:00", "REG"), null, j -> true)).isEmpty();
    }

    @Test
    @DisplayName("le taux de base : celui du dossier prime ; sinon l'échelle la plus précise, puis la plus récente, en vigueur ce jour-là")
    void tauxDeBase() {
        ReglePaie general = regle("TAUX_INF", TypeRegle.TAUX_DE_BASE, "30", profil("INF", null, null));
        ReglePaie echelon3 = regle("TAUX_INF_E3", TypeRegle.TAUX_DE_BASE, "34", profil("INF", null, 3));
        ReglePaie avril = new ReglePaie("a", "TAUX_INF_E3", "Échelon 3 au 1er avril", TypeRegle.TAUX_DE_BASE, d("35"), profil("INF", null, 3),
                LocalDate.parse("2026-04-01"), null, true);
        ReglePaie pab = regle("TAUX_PAB", TypeRegle.TAUX_DE_BASE, "24", profil("PAB", null, null));
        List<ReglePaie> regles = List.of(general, echelon3, avril, pab, regle("NUIT", TypeRegle.PRIME_HORAIRE, "2", ConditionsRegle.AUCUNE));
        assertThat(TauxDeBase.resoudre(aLEchelon(3), INFIRMIERE_E3, regles, LocalDate.parse("2026-03-31"))).contains(d("34"));
        assertThat(TauxDeBase.resoudre(aLEchelon(3), INFIRMIERE_E3, regles, LocalDate.parse("2026-04-01"))).contains(d("35"));
        assertThat(TauxDeBase.resoudre(aLEchelon(5), new ProfilPaie("INF", null, 5), regles, LocalDate.parse("2026-03-01"))).contains(d("30"));
        assertThat(TauxDeBase.resoudre(aLEchelon(3), new ProfilPaie("PAR", null, 3), regles, LocalDate.parse("2026-03-01"))).isEmpty();
        assertThat(TauxDeBase.resoudre(DomainePaieTest.horaire("r1", "41"), INFIRMIERE_E3, regles, LocalDate.parse("2026-03-01"))).contains(d("41"));
    }

    @Test
    @DisplayName("les gains : heures à leur taux du jour, une ligne par taux ; primes de nuit, de fin de semaine et de férié ; forfait")
    void gains() {
        List<ReglePaie> regles = List.of(
                regle("TAUX_E3", TypeRegle.TAUX_DE_BASE, "30", profil("INF", null, 3)),
                new ReglePaie("a", "TAUX_E3", "Échelle avril", TypeRegle.TAUX_DE_BASE, d("40"), profil("INF", null, 3), LocalDate.parse("2026-02-17"), null, true),
                regle("NUIT", TypeRegle.PRIME_POURCENTAGE, "0.10", heures(plage("00:00", "08:00"), Set.of(), false, Set.of())),
                regle("FDS", TypeRegle.PRIME_HORAIRE, "2", heures(null, Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), false, Set.of("REG"))),
                regle("FERIE", TypeRegle.PRIME_POURCENTAGE, "0.5", heures(null, Set.of(), true, Set.of())),
                regle("HS_SEUL", TypeRegle.PRIME_HORAIRE, "9", heures(null, Set.of(), false, Set.of("HS"))),
                regle("RETENTION", TypeRegle.FORFAIT, "100", profil("INF", null, null)),
                regle("PAB_SEUL", TypeRegle.PRIME_HORAIRE, "5", profil("PAB", null, null)));
        FeuilleAPayer f = feuille("f1", List.of(ligne("2026-02-16", NatureHeures.REGULIERE, "8"), ligne("2026-02-17", NatureHeures.REGULIERE, "8"),
                        ligne("2026-02-17", NatureHeures.SUPPLEMENTAIRE, "2")),
                List.of(segment("2026-02-15T23:00", "2026-02-16T07:00", "REG"), segment("2026-02-17T08:00", "2026-02-17T16:00", "REG")));
        List<Gain> gains = CalculGains.calculer(new CalculGains.Personne(aLEchelon(3), INFIRMIERE_E3), List.of(f),
                contexte(regles, Set.of(LocalDate.parse("2026-02-17")))).orElseThrow();
        assertThat(gains).extracting(Gain::code).containsExactly("REG", "REG", "HS", "NUIT", "FDS", "FERIE", "RETENTION");
        assertThat(gains.get(0)).satisfies(g -> {
            assertThat(g.taux()).isEqualByComparingTo("30");
            assertThat(g.montant()).isEqualByComparingTo("240.00");
            assertThat(g.nature()).isEqualTo(Gain.Nature.HEURES_REGULIERES);
        });
        assertThat(gains.get(1).montant()).isEqualByComparingTo("320.00");
        assertThat(gains.get(2)).satisfies(g -> {
            assertThat(g.taux()).isEqualByComparingTo("60");
            assertThat(g.montant()).isEqualByComparingTo("120.00");
        });
        // Nuit : 7 h le 16 (de 0 h à 7 h) au taux de 30 $ × 10 %.
        assertThat(gains.get(3)).satisfies(g -> {
            assertThat(g.quantite()).isEqualByComparingTo("7");
            assertThat(g.montant()).isEqualByComparingTo("21.00");
            assertThat(g.nature()).isEqualTo(Gain.Nature.PRIME_POURCENTAGE);
        });
        // Fin de semaine : 1 h le dimanche 15 février, à 2 $.
        assertThat(gains.get(4)).satisfies(g -> {
            assertThat(g.quantite()).isEqualByComparingTo("1");
            assertThat(g.montant()).isEqualByComparingTo("2.00");
            assertThat(g.nature()).isEqualTo(Gain.Nature.PRIME_HORAIRE);
        });
        // Férié du 17 : 8 h au taux d'avril anticipé (40 $) × 50 %.
        assertThat(gains.get(5).montant()).isEqualByComparingTo("160.00");
        assertThat(gains.get(6)).satisfies(g -> {
            assertThat(g.montant()).isEqualByComparingTo("100.00");
            assertThat(g.nature()).isEqualTo(Gain.Nature.FORFAIT);
        });
        assertThat(CalculGains.brut(gains)).isEqualByComparingTo("963.00");
    }

    @Test
    @DisplayName("sans taux, rien n'est payé ; au salaire, la part et les supplémentaires seulement si un taux est connu ; un forfait exige des heures")
    void casLimites() {
        FeuilleAPayer f = feuille("f1", List.of(ligne("2026-02-16", NatureHeures.REGULIERE, "8"), ligne("2026-02-16", NatureHeures.SUPPLEMENTAIRE, "2")),
                List.of(segment("2026-02-16T08:00", "2026-02-16T18:00", "REG")));
        assertThat(CalculGains.calculer(new CalculGains.Personne(aLEchelon(3), INFIRMIERE_E3), List.of(f), contexte(List.of(), Set.of()))).isEmpty();
        DossierRemuneration salarie = new DossierRemuneration("r1", ModeRemuneration.SALAIRE, Frequence.MENSUELLE, null, d("60000"), BASE, null);
        List<ReglePaie> pourcentage = List.of(regle("JOUR", TypeRegle.PRIME_POURCENTAGE, "0.1", ConditionsRegle.AUCUNE),
                regle("FORFAIT", TypeRegle.FORFAIT, "50", ConditionsRegle.AUCUNE));
        List<Gain> sansTaux = CalculGains.calculer(new CalculGains.Personne(salarie, INFIRMIERE_E3), List.of(f), contexte(pourcentage, Set.of())).orElseThrow();
        assertThat(sansTaux).extracting(Gain::code).containsExactly("SAL", "JOUR", "FORFAIT");
        assertThat(sansTaux.get(0).montant()).isEqualByComparingTo("5000.00");
        assertThat(sansTaux.get(1).montant()).isZero();
        List<ReglePaie> avecEchelle = List.of(regle("TAUX", TypeRegle.TAUX_DE_BASE, "30", ConditionsRegle.AUCUNE));
        assertThat(CalculGains.calculer(new CalculGains.Personne(salarie, INFIRMIERE_E3), List.of(f), contexte(avecEchelle, Set.of())).orElseThrow())
                .extracting(Gain::code).containsExactly("SAL", "HS");
        List<ReglePaie> forfait = List.of(regle("TAUX", TypeRegle.TAUX_DE_BASE, "30", ConditionsRegle.AUCUNE), regle("FORFAIT", TypeRegle.FORFAIT, "50", ConditionsRegle.AUCUNE),
                new ReglePaie("x", "VIEUX", "Expiré", TypeRegle.FORFAIT, d("9"), ConditionsRegle.AUCUNE, DU, DU, true));
        assertThat(CalculGains.calculer(new CalculGains.Personne(aLEchelon(3), INFIRMIERE_E3), List.of(), contexte(forfait, Set.of())).orElseThrow()).isEmpty();
    }

    @Test
    @DisplayName("la validation : l'empreinte couvre les heures validées ; une heure changée la périme ; un retour ne couvre rien")
    void validations() {
        FeuilleAPayer f = feuille("f1", List.of(ligne("2026-02-16", NatureHeures.REGULIERE, "8.0")), List.of());
        FeuilleAPayer change = feuille("f1", List.of(ligne("2026-02-16", NatureHeures.REGULIERE, "9")), List.of());
        String empreinte = ValidationTemps.empreinteDe(f);
        assertThat(empreinte).hasSize(64).isEqualTo(ValidationTemps.empreinteDe(feuille("f1", List.of(ligne("2026-02-16", NatureHeures.REGULIERE, "8")), List.of())));
        ValidationTemps v = new ValidationTemps("v", "f1", ValidationTemps.Decision.VALIDEE, empreinte, null, "comptable", "t");
        assertThat(v.couvre(f)).isTrue();
        assertThat(v.couvre(change)).isFalse();
        assertThat(new ValidationTemps("v", "f1", ValidationTemps.Decision.RETOURNEE, empreinte, "x", "c", "t").couvre(f)).isFalse();
        assertThat(ValidationTemps.Decision.de("retournee")).isEqualTo(ValidationTemps.Decision.RETOURNEE);
        refus(() -> ValidationTemps.Decision.de("x"), RefusPaie.Nature.INVALIDE, "décision de validation inconnue : x");
        assertThat(AnomalieTemps.NON_APPROUVEE.bloquante()).isTrue();
        assertThat(AnomalieTemps.SANS_DOSSIER.bloquante()).isFalse();
        assertThat(AnomalieTemps.AUCUNE_FEUILLE.code()).isEqualTo("aucune-feuille");
    }

    @Test
    @DisplayName("le contrôle des temps : chaque anomalie nommée ; qui n'a pas de feuille ; validable, validée, périmée")
    void controle() {
        FeuilleAPayer heuresOk = feuille("ok", List.of(ligne("2026-02-16", NatureHeures.REGULIERE, "40")), List.of());
        FeuilleAPayer longues = new FeuilleAPayer("long", "r2", "P2", "Marc", "2026-02-16",
                List.of(ligne("2026-02-16", NatureHeures.REGULIERE, "55"), ligne("2026-02-17", NatureHeures.SUPPLEMENTAIRE, "6")), List.of());
        List<FeuilleDeLaPeriode> feuilles = List.of(
                new FeuilleDeLaPeriode("ok", "r1", "P1", "Julie", "2026-02-16", EtatFeuille.APPROUVEE, Optional.of(heuresOk), null),
                new FeuilleDeLaPeriode("long", "r2", "P2", "Marc", "2026-02-16", EtatFeuille.APPROUVEE, Optional.of(longues), null),
                new FeuilleDeLaPeriode("soumise", "r3", "P3", "Léa", "2026-02-16", EtatFeuille.SOUMISE, Optional.empty(), "type manquant"),
                new FeuilleDeLaPeriode("payee", "r1", "P1", "Julie", "2026-02-09", EtatFeuille.PAYEE, Optional.of(heuresOk), null));
        List<Personne> personnes = List.of(new Personne("r1", "P1", "Julie", "INF", null), new Personne("r2", "P2", "Marc", "PAB", null),
                new Personne("r4", "P4", "Sans feuille", null, null));
        Map<String, DossierRemuneration> dossiers = Map.of("r1", aLEchelon(3), "r2",
                new DossierRemuneration("r2", ModeRemuneration.SALAIRE, Frequence.MENSUELLE, null, d("50000"), BASE, null));
        ValidationTemps perimee = new ValidationTemps("v", "long", ValidationTemps.Decision.VALIDEE, "autre", null, "c", "t");
        ValidationTemps bonne = new ValidationTemps("v2", "payee", ValidationTemps.Decision.VALIDEE, ValidationTemps.empreinteDe(heuresOk), null, "c", "t");
        ControleTemps.Etat e = ControleTemps.controler(new ControleTemps.Donnees(feuilles, personnes, dossiers, Map.of("long", perimee, "payee", bonne),
                List.of(regle("TAUX", TypeRegle.TAUX_DE_BASE, "30", profil("PAB", null, null)))));
        assertThat(e.feuilles()).extracting(ControleTemps.Ligne::anomalies).containsExactly(
                List.of(AnomalieTemps.SANS_TAUX),
                List.of(AnomalieTemps.HEURES_ELEVEES, AnomalieTemps.VALIDATION_PERIMEE),
                List.of(AnomalieTemps.NON_APPROUVEE, AnomalieTemps.INCALCULABLE, AnomalieTemps.SANS_DOSSIER),
                List.of(AnomalieTemps.SANS_TAUX));
        assertThat(e.feuilles()).extracting(ControleTemps.Ligne::estValidable).containsExactly(true, true, false, false);
        assertThat(e.feuilles()).extracting(ControleTemps.Ligne::estValidee).containsExactly(false, false, false, true);
        assertThat(e.feuilles().get(1).heuresSupplementaires()).isEqualByComparingTo("6");
        assertThat(e.sansFeuille()).extracting(Personne::ressourceId).containsExactly("r4");
        assertThat(ControleTemps.profil(null, null)).isEqualTo(new ProfilPaie(null, null, null));
        ValidationTemps retour = new ValidationTemps("v3", "soumise", ValidationTemps.Decision.VALIDEE, "x", null, "c", "t");
        assertThat(ControleTemps.controler(new ControleTemps.Donnees(List.of(feuilles.get(2)), List.of(), Map.of(), Map.of("soumise", retour), List.of()))
                .feuilles().getFirst().anomalies()).contains(AnomalieTemps.VALIDATION_PERIMEE);
    }

    @Test
    @DisplayName("préparer avec validation exigée : les feuilles non validées sont nommées ; sans taux, la personne aussi ; les gains sont figés")
    void preparationAvecValidation() {
        CyclePaie cycle = CyclePaie.prepare("c1", new CyclePaie.Periode("2026-02-15", "2026-02-28", "2026-03-05"), DomainePaieTest.TRACE);
        FeuilleAPayer validee = feuille("f1", List.of(ligne("2026-02-16", NatureHeures.REGULIERE, "8")), List.of());
        FeuilleAPayer nonValidee = feuille("f2", List.of(ligne("2026-02-23", NatureHeures.REGULIERE, "8")), List.of());
        FeuilleAPayer sansTaux = new FeuilleAPayer("f3", "r3", "P3", "Léa", "2026-02-16", List.of(ligne("2026-02-16", NatureHeures.REGULIERE, "8")), List.of());
        ReglagesPaie exige = new ReglagesPaie(REGLAGES.tauxFss(), REGLAGES.tauxCnesst(), REGLAGES.majorationSupplementaire(), true);
        Map<String, ValidationTemps> validations = Map.of(
                "f1", new ValidationTemps("v", "f1", ValidationTemps.Decision.VALIDEE, ValidationTemps.empreinteDe(validee), null, "c", "t"),
                "f3", new ValidationTemps("v", "f3", ValidationTemps.Decision.VALIDEE, ValidationTemps.empreinteDe(sansTaux), null, "c", "t"));
        AtomicInteger n = new AtomicInteger();
        PreparationPaie.Variables v = new PreparationPaie.Variables(List.of(regle("TAUX", TypeRegle.TAUX_DE_BASE, "30", profil("INF", null, null))), Set.of(),
                Map.of("r1", new Personne("r1", "P1", "Julie Roy", "INF", null)), validations);
        PreparationPaie.Resultat r = PreparationPaie.preparer(cycle, List.of(validee, nonValidee, sansTaux), new PreparationPaie.Sources(B2026, exige,
                Map.of("r1", aLEchelon(3), "r3", new DossierRemuneration("r3", ModeRemuneration.HORAIRE, Frequence.AUX_DEUX_SEMAINES, null, null, BASE, 3)),
                x -> Cumuls.AUCUN, () -> "b" + n.incrementAndGet(), v));
        assertThat(r.nonValidees()).containsExactly(new PreparationPaie.FeuilleNonValidee("f2", "r1", "Julie Roy", "2026-02-16"));
        assertThat(r.sansTaux()).containsExactly(new PreparationPaie.Personne("r3", "P3", "Léa"));
        assertThat(r.bulletins()).singleElement().satisfies(b -> {
            assertThat(b.gains()).singleElement().extracting(Gain::montant).isEqualTo(d("240.00"));
            assertThat(b.retenues().brut()).isEqualByComparingTo("240.00");
        });
    }
}
