package ca.vina.nexu.effectifs.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <strong>Domaine de l'AGPH et des remplacements long terme</strong> (lot R7, jumeau de
 * {@code effectifs-agph-domaine.test.ts}) : un ordre total et reproductible, un tour qui finit à l'instant exact de sa
 * fin, des transitions qui disent quel geste manque, et un statut d'après campagne qui suit le poste obtenu.
 */
@DisplayName("Agph — domaine pur")
class AgphDomaineTest {

    @Nested
    @DisplayName("campagne : transitions et délai")
    class Campagne {

        @Test
        @DisplayName("ouvrir, démarrer, clore — dans cet ordre seulement")
        void transitions() {
            assertThat(List.of(Agph.etatSuivant(Agph.EtatCampagne.PREPARATION, Agph.EtatCampagne.DEPOT),
                    Agph.etatSuivant(Agph.EtatCampagne.DEPOT, Agph.EtatCampagne.CHOIX), Agph.etatSuivant(Agph.EtatCampagne.ATTRIBUTION, Agph.EtatCampagne.CLOSE)))
                    .containsExactly(Agph.EtatCampagne.DEPOT, Agph.EtatCampagne.CHOIX, Agph.EtatCampagne.CLOSE);
            assertThatThrownBy(() -> Agph.etatSuivant(Agph.EtatCampagne.PREPARATION, Agph.EtatCampagne.CHOIX))
                    .isInstanceOf(EffectifsInvalideException.class).hasMessage("une campagne en preparation ne passe pas à choix : il faut d'abord ouvrir");
            assertThatThrownBy(() -> Agph.etatSuivant(Agph.EtatCampagne.CHOIX, Agph.EtatCampagne.CLOSE)).hasMessageContaining("attendre la fin du tour de rôle");
            assertThatThrownBy(() -> Agph.etatSuivant(Agph.EtatCampagne.CLOSE, Agph.EtatCampagne.DEPOT)).hasMessageEndingWith("rien");
            assertThatThrownBy(() -> Agph.etatSuivant(Agph.EtatCampagne.DEPOT, Agph.EtatCampagne.CLOSE)).hasMessageEndingWith("démarrer le tour de rôle");
        }

        @Test
        @DisplayName("la révision est facultative : d'attribution à révision ou à close, de révision à close seulement (E-CH-1)")
        void revision() {
            assertThat(List.of(Agph.etatSuivant(Agph.EtatCampagne.ATTRIBUTION, Agph.EtatCampagne.REVISION),
                    Agph.etatSuivant(Agph.EtatCampagne.REVISION, Agph.EtatCampagne.CLOSE))).containsExactly(Agph.EtatCampagne.REVISION, Agph.EtatCampagne.CLOSE);
            assertThatThrownBy(() -> Agph.etatSuivant(Agph.EtatCampagne.REVISION, Agph.EtatCampagne.REVISION))
                    .hasMessage("une campagne en revision ne passe pas à revision : il faut d'abord clore");
            assertThatThrownBy(() -> Agph.etatSuivant(Agph.EtatCampagne.ATTRIBUTION, Agph.EtatCampagne.DEPOT)).hasMessageEndingWith("réviser ou clore");
        }

        @Test
        @DisplayName("les disponibilités se déposent au dépôt et se corrigent à la révision, jamais ailleurs")
        void depotOuvert() {
            assertThat(java.util.Arrays.stream(Agph.EtatCampagne.values()).filter(Agph.EtatCampagne::depotOuvert).toList())
                    .containsExactly(Agph.EtatCampagne.DEPOT, Agph.EtatCampagne.REVISION);
        }

        @Test
        @DisplayName("le budget d'heures est absent, ou un nombre d'heures positif borné")
        void budget() {
            assertThat(java.util.Arrays.asList(Agph.budgetDe(null), Agph.budgetDe(""), Agph.budgetDe(40), Agph.budgetDe("37.5"), Agph.budgetDe(100_000)))
                    .containsExactly(null, null, 40.0, 37.5, 100_000.0);
            for (Object v : new Object[]{0, -1, 100_001, "x", " ", List.of(), Double.POSITIVE_INFINITY}) {
                assertThatThrownBy(() -> Agph.budgetDe(v)).as(String.valueOf(v)).isInstanceOf(EffectifsInvalideException.class)
                        .hasMessage("« budgetHeuresSemaine » : un nombre d'heures de 0 exclu à 100000");
            }
        }

        @Test
        @DisplayName("le délai de choix va de 5 minutes à une semaine")
        void delai() {
            assertThat(List.of(Agph.delaiDe(30), Agph.delaiDe("5"), Agph.delaiDe(10_080), Agph.delaiDe(" 7 "))).containsExactly(30, 5, 10_080, 7);
            for (Object v : new Object[]{4, 10_081, 12.5, "x", null, "", true, List.of(), Double.POSITIVE_INFINITY}) {
                assertThatThrownBy(() -> Agph.delaiDe(v)).isInstanceOf(EffectifsInvalideException.class).hasMessage("« delaiChoixMinutes » : de 5 à 10080 minutes");
            }
        }

        @Test
        @DisplayName("Number(v) du Node : booléens, textes vides, textes numériques")
        void nombre() {
            assertThat(Agph.nombre(true)).isEqualTo(1);
            assertThat(Agph.nombre(false)).isZero();
            assertThat(Agph.nombre("  ")).isZero();
            assertThat(Agph.nombre("2031")).isEqualTo(2031);
            assertThat(Agph.nombre("abc")).isNaN();
            assertThat(Agph.nombre(null)).isNaN();
            assertThat(Agph.estEntier(Double.NaN)).isFalse();
            assertThat(Agph.estEntier(Double.NEGATIVE_INFINITY)).isFalse();
        }
    }

    @Nested
    @DisplayName("rangs et tours")
    class Rangs {

        @Test
        @DisplayName("ancienneté décroissante, puis matricule")
        void ordre() {
            List<Agph.RangCalcule> rangs = Agph.ordonner(List.of(new Agph.Candidat("a", 3, "P3"), new Agph.Candidat("b", 9, "P9"),
                    new Agph.Candidat("c", 3, "P1")));
            assertThat(rangs.stream().map(r -> r.candidat().ressourceId() + r.rang()).toList()).containsExactly("b1", "c2", "a3");
            assertThat(Agph.ordonner(List.of())).isEmpty();
        }

        @Test
        @DisplayName("le tour finit à l'instant exact de sa fin")
        void tour() {
            String fin = Agph.ajouterMinutes("2030-01-06T13:00:00.000Z", 30);
            assertThat(fin).isEqualTo("2030-01-06T13:30:00.000Z");
            assertThat(List.of(Agph.tourEcoule(fin, "2030-01-06T13:29:59.999Z"), Agph.tourEcoule(fin, fin), Agph.tourEcoule(fin, "2030-01-06T14:00:00.000Z")))
                    .containsExactly(false, true, true);
        }

        @Test
        @DisplayName("le statut suit le poste obtenu, ou le temps partiel (P-CH-5 à P-CH-7)")
        void statut() {
            assertThat(List.of(Agph.statutApres("TC", "TP"), Agph.statutApres("TPR", "TP"), Agph.statutApres(null, "TP"))).containsExactly("TC", "TPR", "TP");
        }
    }

    @Nested
    @DisplayName("jugement d'admissibilité")
    class Jugements {

        private final Regles.Motif motif = new Regles.Motif("r1", 1, "tc", ActionRegle.BLOQUER, "Réservé aux deux premiers rangs");

        @Test
        @DisplayName("une exigence dure refuse sans verdict : 422 motivé")
        void exigenceDure() {
            Agph.Jugement j = Agph.Jugement.refuse("le poste X requiert le corps PARA");
            assertThat(j.admissible()).isFalse();
            assertThat(j.verdict()).isNull();
            assertThatThrownBy(j::exiger).isExactlyInstanceOf(EffectifsInvalideException.class).hasMessage("le poste X requiert le corps PARA");
        }

        @Test
        @DisplayName("un verdict bloquant refuse avec le verdict : 409 ; un verdict acceptant laisse passer")
        void verdict() {
            Regles.Verdict bloque = new Regles.Verdict(ActionRegle.BLOQUER, List.of(motif, motif), List.of());
            Agph.Jugement j = Agph.Jugement.de(bloque);
            assertThat(j.message()).isEqualTo("Réservé aux deux premiers rangs ; Réservé aux deux premiers rangs");
            assertThatThrownBy(j::exiger).isInstanceOf(RefusParRegleException.class)
                    .satisfies(e -> assertThat(((RefusParRegleException) e).verdict()).isSameAs(bloque));
            Agph.Jugement ok = Agph.Jugement.de(new Regles.Verdict(ActionRegle.ACCEPTER, List.of(), List.of()));
            assertThat(ok.admissible()).isTrue();
            assertThatCode(ok::exiger).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("remplacements long terme")
    class Remplacements {

        @Test
        @DisplayName("lit l'origine à la frontière")
        void origine() {
            assertThat(List.of(Agph.origineDe("ABSENCE"), Agph.origineDe("POSTE_VACANT")))
                    .containsExactly(Agph.OrigineRemplacement.ABSENCE, Agph.OrigineRemplacement.POSTE_VACANT);
            assertThatThrownBy(() -> Agph.origineDe("AGPH")).isInstanceOf(EffectifsInvalideException.class).hasMessage("« origine » : ABSENCE, POSTE_VACANT");
            assertThatThrownBy(() -> Agph.origineDe(null)).isInstanceOf(EffectifsInvalideException.class);
        }

        @Test
        @DisplayName("la fenêtre se ferme à l'instant dit")
        void fenetre() {
            assertThat(List.of(Agph.fenetreOuverte("2030-02-01T12:00:00.000Z", "2030-02-01T11:59:00.000Z"),
                    Agph.fenetreOuverte("2030-02-01T12:00:00.000Z", "2030-02-01T12:00:00.000Z"))).containsExactly(true, false);
        }

        @Test
        @DisplayName("un instant saisi se normalise comme toISOString ; une date seule vaut minuit UTC ; le reste est vide")
        void instant() {
            assertThat(Agph.instantDe("2031-06-01T08:00:00-04:00")).isEqualTo("2031-06-01T12:00:00.000Z");
            assertThat(Agph.instantDe("2031-06-01T12:00:00Z")).isEqualTo("2031-06-01T12:00:00.000Z");
            assertThat(Agph.instantDe("2031-06-01")).isEqualTo("2031-06-01T00:00:00.000Z");
            assertThat(Agph.instantDe("demain")).isEmpty();
            assertThat(Agph.instantDe(12)).isEmpty();
        }

        @Test
        @DisplayName("classe par ancienneté, puis premier arrivé, puis identifiant (P-CH-9)")
        void classement() {
            assertThat(Agph.classerInteresses(List.of(new Agph.Interesse("z", 5, "2030-01-02"), new Agph.Interesse("y", 5, "2030-01-01"),
                    new Agph.Interesse("x", 8, "2030-01-03"), new Agph.Interesse("w", 5, "2030-01-01"))).stream().map(Agph.Interesse::ressourceId).toList())
                    .containsExactly("x", "w", "y", "z");
            assertThat(new AgphInteresse("a", "Ana", "P1", 20, "2030-01-01").pourClassement()).isEqualTo(new Agph.Interesse("a", 20, "2030-01-01"));
        }

        @Test
        @DisplayName("les enregistrements portent ce que la lecture y joint")
        void enregistrements() {
            AgphRemplacement x = new AgphRemplacement("erlt-1", "EST", "p", Agph.OrigineRemplacement.ABSENCE, "abs", "2031-01-01", "2031-01-31",
                    "2030-12-31T00:00:00.000Z", Agph.EtatRemplacement.NON_COMBLE, null, 0, "c", null, null, 0, null, "A-TC", null, 2);
            assertThat(x.interesses()).isEqualTo(2);
            assertThat(Agph.EtatRemplacement.values()).containsExactly(Agph.EtatRemplacement.OUVERT, Agph.EtatRemplacement.ATTRIBUE,
                    Agph.EtatRemplacement.NON_COMBLE);
            assertThat(Map.of("etats", Agph.EtatRang.values().length, "modes", Agph.ModeChoix.values().length)).containsEntry("etats", 5).containsEntry("modes", 3);
        }
    }
    @Nested
    @DisplayName("semaine type de la campagne (E-CH-1)")
    class SemaineType {

        private Map<String, Object> j(Object jour, int debut, int fin, String type) {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("jour", jour);
            m.put("debut", debut);
            m.put("fin", fin);
            m.put("type", type);
            return m;
        }

        private Map<String, Object> j(int jour) {
            return j(jour, 360, 1080, "DISPONIBLE");
        }

        @Test
        @DisplayName("trie par jour puis par heure ; pose la semaine sur le lundi de référence")
        void tri() {
            List<AgphSemaineType.PlageType> p = AgphSemaineType.plagesTypeDe(List.of(j(3), j(1, 1080, 1320, "PREFERENCE"), j(1)));
            assertThat(p.stream().map(x -> x.jour() + "/" + x.debut()).toList()).containsExactly("1/360", "1/1080", "3/360");
            assertThat(AgphSemaineType.versPlages(p).stream().map(Disponibilites.PlageDisponible::date).toList())
                    .containsExactly("2001-01-01", "2001-01-01", "2001-01-03");
            assertThat(AgphSemaineType.plagesTypeDe(List.of(j("7", 0, 1440, "INDISPONIBLE"))))
                    .containsExactly(new AgphSemaineType.PlageType(7, 0, 1440, Disponibilites.TypeDisponibilite.INDISPONIBLE));
            assertThat(AgphSemaineType.LUNDI_REFERENCE).isEqualTo("2001-01-01");
        }

        @Test
        @DisplayName("refuse une liste absente ou trop longue, un jour hors 1–7, un type inconnu, un chevauchement nommé par son jour")
        void refus() {
            assertThatThrownBy(() -> AgphSemaineType.plagesTypeDe("x")).hasMessage("« plages » : une liste de { jour, debut, fin, type } est attendue");
            List<Map<String, Object>> trop = java.util.Collections.nCopies(51, j(1));
            assertThatThrownBy(() -> AgphSemaineType.plagesTypeDe(trop)).hasMessage("une semaine type compte au plus 50 plages");
            for (Object jour : new Object[]{0, 8, 1.5, "lundi", null}) {
                assertThatThrownBy(() -> AgphSemaineType.plagesTypeDe(List.of(j(jour, 360, 1080, "DISPONIBLE")))).as(String.valueOf(jour))
                        .hasMessage("plage 1 : « jour » de 1 (lundi) à 7 (dimanche)");
            }
            assertThatThrownBy(() -> AgphSemaineType.plagesTypeDe(java.util.Collections.singletonList(null))).hasMessageContaining("« jour »");
            assertThatThrownBy(() -> AgphSemaineType.plagesTypeDe(List.of(j(2, 360, 1080, "PEUT_ETRE")))).hasMessageContaining("type inconnu « PEUT_ETRE »");
            assertThatThrownBy(() -> AgphSemaineType.plagesTypeDe(List.of(j(2), j(2, 600, 1200, "DISPONIBLE"))))
                    .hasMessage("deux plages se chevauchent le mardi : corrigez l'une des deux");
            // Une nuit du dimanche déborde sur le lundi suivant : pas sur le lundi de la même semaine.
            assertThat(AgphSemaineType.plagesTypeDe(List.of(j(7, 1200, 360, "DISPONIBLE"), j(1, 0, 300, "DISPONIBLE")))).hasSize(2);
        }

        @Test
        @DisplayName("résume la semaine type et son poids sur le budget")
        void faits() {
            List<AgphSemaineType.PlageType> p = AgphSemaineType.plagesTypeDe(List.of(j(1), j(2), j(6), j(3, 0, 1440, "INDISPONIBLE")));
            AgphSemaineType.Faits f = AgphSemaineType.faits(p, 60, 120.0);
            assertThat(f).isEqualTo(new AgphSemaineType.Faits(3, 720, 1, 0, 36, 96, 120, 80));
            assertThat(f.enCarte()).containsEntry("heuresParSemaine", 36.0).containsEntry("tauxBudget", 80.0).hasSize(8);
            assertThat(AgphSemaineType.faits(p, 0, 24.0).tauxBudget()).isEqualTo(150);
        }

        @Test
        @DisplayName("sans budget, le taux vaut zéro : aucune règle budgétaire ne se déclenche sur une donnée absente")
        void sansBudget() {
            assertThat(AgphSemaineType.faits(List.of(), 10.555, null)).isEqualTo(new AgphSemaineType.Faits(0, 0, 0, 0, 0, 10.56, 0, 0));
        }
    }
}
