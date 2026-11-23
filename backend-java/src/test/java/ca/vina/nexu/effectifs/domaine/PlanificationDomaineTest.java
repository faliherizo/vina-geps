package ca.vina.nexu.effectifs.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static ca.vina.nexu.effectifs.domaine.Planification.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <strong>Domaine de la planification des effectifs</strong> (lot R3, jumeau de
 * {@code effectifs-planification-domaine.test.ts}) — logique pure, 100 % exigé. Un cycle de période ne recule
 * jamais ; un quart de nuit déborde sur le lendemain sans perdre une minute ; le repos se mesure dans les deux
 * sens ; un équipage est opérationnel à son minimum ; une surcharge relue dans le passé n'a jamais changé.
 */
@DisplayName("Domaine — planification des effectifs")
class PlanificationDomaineTest {

    @Nested
    @DisplayName("Cycle d'une période (E-GR-10)")
    class Cycle {

        @Test
        @DisplayName("avance, peut sauter la collecte, ne recule jamais")
        void transitions() {
            assertThatCode(() -> verifierTransition(EtatPeriode.PREPARATION, EtatPeriode.COLLECTE)).doesNotThrowAnyException();
            assertThatCode(() -> verifierTransition(EtatPeriode.PREPARATION, EtatPeriode.CONFECTION)).doesNotThrowAnyException();
            assertThatCode(() -> verifierTransition(EtatPeriode.COLLECTE, EtatPeriode.CONFECTION)).doesNotThrowAnyException();
            assertThatCode(() -> verifierTransition(EtatPeriode.CONFECTION, EtatPeriode.PUBLIEE)).doesNotThrowAnyException();
            assertThatThrownBy(() -> verifierTransition(EtatPeriode.PUBLIEE, EtatPeriode.CONFECTION))
                    .hasMessage("une période « PUBLIEE » ne passe pas à « CONFECTION » (admis : CLOSE)");
            assertThatThrownBy(() -> verifierTransition(EtatPeriode.CLOSE, EtatPeriode.PUBLIEE)).hasMessageContaining("admis : aucun");
            assertThatThrownBy(() -> etatPeriodeDe("OUVERTE"))
                    .hasMessage("état de période inconnu : « OUVERTE » (attendu : PREPARATION, COLLECTE, CONFECTION, PUBLIEE, CLOSE)");
            assertThat(etatPeriodeDe("CLOSE")).isEqualTo(EtatPeriode.CLOSE);
            assertThat(etatPosteDe("VACANT")).isEqualTo(EtatPoste.VACANT);
        }

        @Test
        @DisplayName("seule la collecte ouvre les disponibilités ; seule la clôture fige la période")
        void collecteEtCloture() {
            assertThat(List.of(collecteOuverte(EtatPeriode.PREPARATION), collecteOuverte(EtatPeriode.COLLECTE), collecteOuverte(EtatPeriode.PUBLIEE)))
                    .containsExactly(false, true, false);
            assertThat(List.of(modifiable(EtatPeriode.PUBLIEE), modifiable(EtatPeriode.CLOSE))).containsExactly(true, false);
        }
    }

    @Nested
    @DisplayName("Dates et créneaux")
    class Creneaux {

        @Test
        @DisplayName("valide les dates et les bornes d'une période")
        void dates() {
            assertThat(dateDe(" 2027-03-01 ", "d")).isEqualTo("2027-03-01");
            assertThatThrownBy(() -> dateDe("01/03/2027", "début")).hasMessageContaining("début");
            assertThatThrownBy(() -> dateDe(20270301, "fin")).hasMessageContaining("fin");
            assertThatThrownBy(() -> verifierBornes("2027-03-02", "2027-03-01")).hasMessage("la fin précède le début");
            assertThatThrownBy(() -> verifierBornes("2027-01-01", "2028-01-02")).hasMessage("une période compte au plus 366 jours");
            assertThatCode(() -> verifierBornes("2027-01-01", "2027-12-31")).doesNotThrowAnyException();
            assertThat(joursEntre("2027-02-27", "2027-03-01")).containsExactly("2027-02-27", "2027-02-28", "2027-03-01");
        }

        @Test
        @DisplayName("valide un créneau ; une nuit passe minuit, une durée nulle ou de 24 h est refusée")
        void creneau() {
            assertThat(creneauDe(360, 1080)).isEqualTo(new Plage(360, 1080));
            assertThat(creneauDe("1080", "360")).isEqualTo(new Plage(1080, 360));
            assertThat(creneauDe(360.0, " 1080 ")).isEqualTo(new Plage(360, 1080));
            assertThat(creneauDe("", true)).isEqualTo(new Plage(0, 1));
            assertThatThrownBy(() -> creneauDe(-1, 60)).hasMessage("« début » : minutes depuis minuit, de 0 à 1439");
            assertThatThrownBy(() -> creneauDe(1440, 60)).hasMessageContaining("« début »");
            assertThatThrownBy(() -> creneauDe(null, 60)).hasMessageContaining("« début »");
            assertThatThrownBy(() -> creneauDe(1.5, 60)).hasMessageContaining("« début »");
            assertThatThrownBy(() -> creneauDe("x", 60)).hasMessageContaining("« début »");
            assertThatThrownBy(() -> creneauDe(Double.POSITIVE_INFINITY, 60)).hasMessageContaining("« début »");
            assertThatThrownBy(() -> creneauDe(60, 0)).hasMessage("« fin » : minutes depuis minuit, de 1 à 1440");
            assertThatThrownBy(() -> creneauDe(60, 1441)).hasMessageContaining("« fin »");
            assertThatThrownBy(() -> creneauDe(60, null)).hasMessageContaining("« fin »");
            assertThatThrownBy(() -> creneauDe(360, 360)).hasMessageContaining("zéro");
            assertThatThrownBy(() -> creneauDe(0, 1440)).hasMessageContaining("vingt-quatre");
            assertThat(List.of(dureeMinutes(360, 1080), dureeMinutes(1080, 360))).containsExactly(720, 720);
        }

        @Test
        @DisplayName("un quart de nuit chevauche le quart de jour du lendemain qui commence avant sa fin")
        void nuit() {
            assertThat(chevauchent(intervalle("2027-03-01", 1080, 420), intervalle("2027-03-02", 360, 1080))).isTrue();
            assertThat(chevauchent(intervalle("2027-03-01", 360, 1080), intervalle("2027-03-01", 1080, 360))).isFalse();
            assertThat(chevauchent(intervalle("2027-03-02", 360, 1080), intervalle("2027-03-01", 360, 1080))).isFalse();
        }

        @Test
        @DisplayName("mesure le repos dans les deux sens ; se toucher n'est pas se chevaucher")
        void repos() {
            List<Creneau> tenus = List.of(new Creneau("2027-03-01", 360, 1080), new Creneau("2027-03-03", 360, 1080));
            assertThat(reposHeures(List.of(), new Creneau("2027-03-02", 360, 1080))).isEmpty();
            assertThat(reposHeures(tenus, new Creneau("2027-03-02", 360, 1080))).contains(12.0);
            assertThat(reposHeures(tenus, new Creneau("2027-03-01", 1080, 1260))).contains(0.0);
            assertThat(chevaucheUnQuart(tenus, new Creneau("2027-03-01", 1080, 1260))).isFalse();
            assertThat(chevaucheUnQuart(tenus, new Creneau("2027-03-01", 1020, 1260))).isTrue();
            assertThat(reposHeures(tenus, new Creneau("2027-03-01", 1020, 1260))).contains(0.0);
            assertThat(reposHeures(List.of(tenus.get(0)), new Creneau("2027-02-28", 1080, 300))).contains(1.0);
            assertThat(reposHeures(List.of(new Creneau("2027-03-01", 0, 20)), new Creneau("2027-03-01", 60, 120))).contains(0.67);
        }

        @Test
        @DisplayName("heures sur 24 h (addenda 7, Q8) : la pire fenêtre qui touche le quart, nuit comprise")
        void heuresSur24hGlissantes() {
            assertThat(heuresSur24h(List.of(), new Creneau("2027-03-02", 360, 1080))).isEqualTo(12.0);
            // 6 h-18 h puis 19 h-7 h : 23 h dans la fenêtre qui commence à 6 h (la nuit déborde sur le lendemain).
            assertThat(heuresSur24h(List.of(new Creneau("2027-03-01", 360, 1080)), new Creneau("2027-03-01", 1140, 420))).isEqualTo(23.0);
            // Un quart à 25 h d'écart n'entre dans aucune fenêtre avec le nouveau.
            assertThat(heuresSur24h(List.of(new Creneau("2027-03-01", 0, 300)), new Creneau("2027-03-02", 360, 720))).isEqualTo(6.0);
            // 2 h-6 h, 10 h-18 h, puis 23 h-3 h : 16 h en tout sur 25 h, donc 15 h au plus sur 24 h.
            assertThat(heuresSur24h(List.of(new Creneau("2027-03-01", 120, 360), new Creneau("2027-03-01", 600, 1080)),
                    new Creneau("2027-03-01", 1380, 180))).isEqualTo(15.0);
        }

        @Test
        @DisplayName("compte les heures de la semaine ISO, du lundi au dimanche")
        void semaine() {
            assertThat(List.of(lundiDe("2027-03-01"), lundiDe("2027-03-07"), lundiDe("1970-01-01"), lundiDe("1969-12-28")))
                    .containsExactly("2027-03-01", "2027-03-01", "1969-12-29", "1969-12-22");
            List<Creneau> tenus = List.of(new Creneau("2027-03-01", 360, 1080), new Creneau("2027-02-28", 360, 1080));
            assertThat(heuresSemaine(tenus, new Creneau("2027-03-03", 360, 1080))).isEqualTo(24.0);
        }
    }

    @Nested
    @DisplayName("Équipages (E-GN-1, E-GN-2, E-GN-4)")
    class Equipages {

        @Test
        @DisplayName("valide une composition par corps d'emploi")
        void composition() {
            assertThat(compositionDe(List.of(Map.of("corpsEmploiCode", " PARA ", "nombre", 2), Map.of("corpsEmploiCode", "RMU", "nombre", "1"))))
                    .containsExactly(new Composition("PARA", 2), new Composition("RMU", 1));
            assertThatThrownBy(() -> compositionDe(List.of())).hasMessageContaining("au moins une ligne");
            assertThatThrownBy(() -> compositionDe("PARA")).hasMessageContaining("au moins une ligne");
            assertThatThrownBy(() -> compositionDe(List.of(Map.of("nombre", 1)))).hasMessageContaining("nomme un corps");
            assertThatThrownBy(() -> compositionDe(Collections.singletonList(null))).hasMessageContaining("nomme un corps");
            assertThatThrownBy(() -> compositionDe(List.of(Map.of("corpsEmploiCode", "A", "nombre", 1), Map.of("corpsEmploiCode", "A", "nombre", 1))))
                    .hasMessage("corps d'emploi en double dans la composition : A");
            assertThatThrownBy(() -> compositionDe(List.of(Map.of("corpsEmploiCode", "A", "nombre", 0)))).hasMessage("« nombre » de A : entier positif");
            assertThatThrownBy(() -> compositionDe(List.of(Map.of("corpsEmploiCode", "A")))).hasMessageContaining("entier positif");
            assertThatThrownBy(() -> compositionDe(List.of(Map.of("corpsEmploiCode", "A", "nombre", 51)))).hasMessage("un équipage compte au plus 50 places");
        }

        @Test
        @DisplayName("le minimum opérationnel vaut par défaut toutes les places, et reste entre 1 et ce nombre")
        void minimum() {
            assertThat(List.of(minimumDe(null, 3), minimumDe("", 3), minimumDe(2, 3))).containsExactly(3, 3, 2);
            assertThatThrownBy(() -> minimumDe(0, 3)).hasMessage("« minimum opérationnel » : entre 1 et 3 (le nombre de places)");
            assertThatThrownBy(() -> minimumDe(4, 3)).hasMessageContaining("entre 1 et 3");
            assertThatThrownBy(() -> minimumDe("deux", 3)).hasMessageContaining("entre 1 et 3");
        }

        @Test
        @DisplayName("un équipage est opérationnel à son minimum ; taux au dixième, 100 % sans place")
        void statut() {
            assertThat(List.of(statutOperationnel(1, 2), statutOperationnel(2, 2))).containsExactly(StatutOperationnel.SOUS_MINIMUM, StatutOperationnel.OPERATIONNEL);
            assertThat(List.of(tauxPourcent(2, 3), tauxPourcent(0, 0))).containsExactly(66.7, 100.0);
            assertThat(modeAttributionDe("MANUEL")).isEqualTo(ModeAttribution.MANUEL);
            assertThatThrownBy(() -> modeAttributionDe("AUTO")).hasMessageContaining("AUTOMATISE, MANUEL");
        }

        @Test
        @DisplayName("un équipage calcule ses places pourvues, son statut et son créneau")
        void equipage() {
            Equipage.Place pourvue = new Equipage.Place("p1", "e", 1, "PARA", null, "r1", "Julie", Equipage.OrigineAttribution.POSTE, null, "t");
            Equipage.Place vacante = new Equipage.Place("p2", "e", 2, "PARA", null, null, null, null, null, null);
            Equipage e = new Equipage("e", "p", "EST", "2027-03-01", 360, 1080, "REG", "J", "m", ModeAttribution.AUTOMATISE, 2, null, List.of(pourvue, vacante));
            assertThat(List.of(e.pourvues(), e.statut(), e.creneau())).containsExactly(1, StatutOperationnel.SOUS_MINIMUM, new Creneau("2027-03-01", 360, 1080));
        }
    }

    @Nested
    @DisplayName("Postes et cycles (E-CH-2, E-CH-5)")
    class Postes {

        private final List<List<String>> cycle = List.of(Arrays.asList("J", "J", null, null, "N", "N", null), Arrays.asList(null, null, "J", "J", null, null, "N"));

        @Test
        @DisplayName("valide un cycle de 1 à 12 semaines de 7 jours")
        void cycles() {
            assertThat(cycleDe(List.of(Arrays.asList("J", "", null, "N", "N", null, "J")))).containsExactly(Arrays.asList("J", null, null, "N", "N", null, "J"));
            assertThatThrownBy(() -> cycleDe(List.of())).hasMessage("« cycle » : de 1 à 12 semaines");
            assertThatThrownBy(() -> cycleDe("J")).hasMessageContaining("1 à 12");
            assertThatThrownBy(() -> cycleDe(IntStream.range(0, 13).mapToObj(i -> Collections.nCopies(7, (String) null)).toList())).hasMessageContaining("1 à 12");
            assertThatThrownBy(() -> cycleDe(List.of(List.of("J")))).hasMessage("semaine 1 du cycle : 7 jours attendus");
            assertThatThrownBy(() -> cycleDe(List.of("J"))).hasMessageContaining("semaine 1");
            assertThat(plagesDuCycle(cycle)).isEqualTo(Set.of("J", "N"));
        }

        @Test
        @DisplayName("lit la plage d'un jour, avant comme après le début du cycle")
        void plageDuJourLue() {
            assertThat(plageDuJour(cycle, "2027-03-01", "2027-03-01")).isEqualTo("J");
            assertThat(plageDuJour(cycle, "2027-03-01", "2027-03-05")).isEqualTo("N");
            assertThat(plageDuJour(cycle, "2027-03-01", "2027-03-10")).isEqualTo("J");
            assertThat(plageDuJour(cycle, "2027-03-01", "2027-03-15")).isEqualTo("J");
            assertThat(plageDuJour(cycle, "2027-03-01", "2027-02-28")).isEqualTo("N");
            assertThat(plageDuJour(cycle, "2027-03-01", "2027-03-03")).isNull();
        }
    }

    @Nested
    @DisplayName("Quotas et surcharges (E-GR-8, E-GR-9)")
    class Quotas {

        private final List<VersionQuota> versions = List.of(new VersionQuota(10, "2027-01-01", "2027-06-01"), new VersionQuota(12, "2027-06-01", null));

        @Test
        @DisplayName("valide un quota")
        void quota() {
            assertThat(quotaDe("8")).isEqualTo(8);
            assertThat(quotaDe(true)).isEqualTo(1);
            assertThatThrownBy(() -> quotaDe(-1)).hasMessage("« quota » : entier de 0 à 10 000");
            assertThatThrownBy(() -> quotaDe(1.5)).hasMessageContaining("0 à 10 000");
            assertThatThrownBy(() -> quotaDe(10_001)).hasMessageContaining("0 à 10 000");
        }

        @Test
        @DisplayName("une surcharge commence maintenant ou plus tard, finit après, dure 90 jours au plus")
        void surcharge() {
            String m = "2027-03-01T10:00:00.000Z";
            assertThatCode(() -> verifierSurcharge("2027-03-01T10:00:00.000Z", "2027-03-02T00:00:00.000Z", m)).doesNotThrowAnyException();
            assertThatCode(() -> verifierSurcharge("2027-03-02", "2027-03-02T05:00:00-05:00", m)).doesNotThrowAnyException();
            assertThatThrownBy(() -> verifierSurcharge("2027-03-01T09:59:00.000Z", "2027-03-02T00:00:00.000Z", m)).hasMessageContaining("rétroactive");
            assertThatThrownBy(() -> verifierSurcharge("2027-03-02T00:00:00.000Z", "2027-03-02T00:00:00.000Z", m)).hasMessage("une surcharge finit après son début");
            assertThatThrownBy(() -> verifierSurcharge("2027-03-02T00:00:00.000Z", "2027-07-01T00:00:00.000Z", m)).hasMessage("une surcharge dure 90 jours au plus");
            assertThatThrownBy(() -> verifierSurcharge("demain", "2027-07-01T00:00:00.000Z", m)).hasMessageContaining("ISO 8601");
            assertThat(instantDe(null)).isEmpty();
            assertThat(instantDe("2027-03-02")).contains(Instant.parse("2027-03-02T00:00:00Z"));
        }

        @Test
        @DisplayName("le quota effectif : surcharge active la plus récente, sinon la version du jour, sinon rien")
        void effectif() {
            List<Surcharge> surcharges = List.of(new Surcharge(15, "2027-03-10T00:00:00.000Z", "2027-03-20T00:00:00.000Z", "2027-03-01T00:00:00.000Z"),
                    new Surcharge(18, "2027-03-15T00:00:00.000Z", "2027-03-16T00:00:00.000Z", "2027-03-02T00:00:00.000Z"));
            assertThat(quotaEffectif(versions, surcharges, "2027-03-12T12:00:00.000Z")).contains(new QuotaEffectif(15, OrigineQuota.SURCHARGE));
            assertThat(quotaEffectif(versions, surcharges, "2027-03-15T12:00:00.000Z")).contains(new QuotaEffectif(18, OrigineQuota.SURCHARGE));
            assertThat(quotaEffectif(versions, surcharges, "2027-03-20T00:00:00.000Z")).contains(new QuotaEffectif(10, OrigineQuota.VERSION));
            assertThat(quotaEffectif(versions, surcharges, "2027-03-09T23:59:59.000Z")).contains(new QuotaEffectif(10, OrigineQuota.VERSION));
            assertThat(quotaEffectif(versions, List.of(), "2027-07-01T00:00:00.000Z")).contains(new QuotaEffectif(12, OrigineQuota.VERSION));
            assertThat(quotaEffectif(versions, List.of(), "2026-12-31T00:00:00.000Z")).isEmpty();
        }

        @Test
        @DisplayName("un quota porte sa clé et relit celui qui est en vigueur")
        void quotaAgrege() {
            assertThat(List.of(Quota.cleDe("EST", null, null), Quota.cleDe("EST", "J", "REG"))).containsExactly("EST|*|*", "EST|J|REG");
            Quota q = new Quota("EST|*|*", "EST", null, null, List.of(new Quota.Version("v1", 3, "2027-01-01", null)),
                    List.of(new Quota.SurchargeMotivee("s1", 5, "2027-03-01T00:00:00.000Z", "2027-03-02T00:00:00.000Z", "2027-02-01T00:00:00.000Z", "m", "a")));
            assertThat(q.enVigueur("2027-03-01T12:00:00.000Z")).contains(new QuotaEffectif(5, OrigineQuota.SURCHARGE));
            assertThat(q.enVigueur("2027-03-05T12:00:00.000Z")).isEqualTo(Optional.of(new QuotaEffectif(3, OrigineQuota.VERSION)));
        }
    }

    @Test
    @DisplayName("forcer une attribution exige une justification d'au moins dix caractères (E-GN-11)")
    void justification() {
        assertThat(justificationDe("  Pénurie critique, aucun autre PSA  ")).isEqualTo("Pénurie critique, aucun autre PSA");
        assertThatThrownBy(() -> justificationDe("urgent")).hasMessage("forcer une attribution exige une justification d'au moins 10 caractères");
        assertThatThrownBy(() -> justificationDe(null)).hasMessageContaining("justification");
    }
}
