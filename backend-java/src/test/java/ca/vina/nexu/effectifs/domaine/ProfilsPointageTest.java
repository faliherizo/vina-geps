package ca.vina.nexu.effectifs.domaine;

import ca.vina.nexu.effectifs.domaine.ProfilsPointage.ModeSaisie;
import ca.vina.nexu.effectifs.domaine.ProfilsPointage.ProfilPointage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Les profils de pointage (E-GP-1, E-GP-2, P-GP-1) — mêmes refus, mêmes motifs que {@code presence/profils.ts}. */
@DisplayName("Profils de pointage — domaine pur")
class ProfilsPointageTest {

    private static Map<String, Object> brut(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", "AMB");
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static ProfilPointage profil(ModeSaisie mode, PorteeRegle portee, String porteeCode, String code, Integer fenetre) {
        return new ProfilPointage(code, "Profil " + code, mode, portee, porteeCode, List.of(NaturePause.REPAS), false, 15, fenetre, true);
    }

    private static final PorteesDuDossier DOSSIER = new PorteesDuDossier(List.of("EST", "ORG"), "CONV", "TP", "PARAMED", "TAP");

    @Nested
    @DisplayName("valider")
    class Valider {

        @Test
        @DisplayName("les défauts : organisme, toutes les natures, MIXTE, 15 min, sans fenêtre, actif ; le libellé reprend le code")
        void defauts() {
            ProfilPointage p = ProfilsPointage.valider(brut());
            assertThat(p).isEqualTo(new ProfilPointage("AMB", "AMB", ModeSaisie.MIXTE, PorteeRegle.ORGANISME, null, List.of(NaturePause.values()), false, 15, null, true));
        }

        @Test
        @DisplayName("un profil complet se relit tel quel ; les doublons de natures sont fondus ; les nombres en texte sont admis")
        void complet() {
            ProfilPointage p = ProfilsPointage.valider(brut("libelle", " Paramédics ", "mode", "A_POSTERIORI", "porteeType", "TITRE_EMPLOI", "porteeCode", "TAP",
                    "naturesNonPayees", List.of("REPAS", "REPAS", "REPOS"), "pausesManuelles", true, "presencePrealableMinutes", "30",
                    "fenetreAPosterioriJours", 7, "actif", false));
            assertThat(p).isEqualTo(new ProfilPointage("AMB", "Paramédics", ModeSaisie.A_POSTERIORI, PorteeRegle.TITRE_EMPLOI, "TAP",
                    List.of(NaturePause.REPAS, NaturePause.REPOS), true, 30, 7, false));
        }

        @Test
        @DisplayName("chaque refus nomme ce qu'il faut corriger")
        void refus() {
            assertThatThrownBy(() -> ProfilsPointage.valider(brut("porteeType", "PLANETE"))).hasMessageStartingWith("« porteeType » : ORGANISME, UNITE");
            assertThatThrownBy(() -> ProfilsPointage.valider(brut("porteeType", "UNITE", "porteeCode", "  ")))
                    .hasMessage("une portée « UNITE » exige son code : sans lui, le profil ne viserait personne tout en paraissant configuré");
            assertThatThrownBy(() -> ProfilsPointage.valider(brut("naturesNonPayees", List.of())))
                    .hasMessage("un profil admet au moins une nature non payée : sans elle, aucune pause ne peut être déclarée");
            assertThatThrownBy(() -> ProfilsPointage.valider(brut("naturesNonPayees", List.of("SIESTE")))).hasMessage("« nature » : PAUSE, REPAS, REPOS");
            assertThatThrownBy(() -> ProfilsPointage.valider(brut("mode", "PARFOIS"))).hasMessage("« mode » : TEMPS_REEL, A_POSTERIORI, MIXTE");
            assertThatThrownBy(() -> ProfilsPointage.valider(brut("mode", "MIXTE", "fenetreAPosterioriJours", 0)))
                    .hasMessage("un profil « MIXTE » exige une fenêtre de saisie non nulle : à zéro jour, toute déclaration après coup serait refusée");
            assertThatThrownBy(() -> ProfilsPointage.valider(brut("fenetreAPosterioriJours", 91))).hasMessage("« fenetreAPosterioriJours » : un entier de 0 à 90");
            assertThatThrownBy(() -> ProfilsPointage.valider(brut("presencePrealableMinutes", 1.5))).hasMessage("« presencePrealableMinutes » : un entier de 0 à 240");
            assertThatThrownBy(() -> ProfilsPointage.valider(brut("presencePrealableMinutes", -1))).hasMessage("« presencePrealableMinutes » : un entier de 0 à 240");
            assertThatThrownBy(() -> ProfilsPointage.valider(brut("presencePrealableMinutes", "beaucoup"))).hasMessage("« presencePrealableMinutes » : un entier de 0 à 240");
            assertThatThrownBy(() -> ProfilsPointage.valider(brut("code", " "))).hasMessage("« code » est obligatoire");
            assertThatThrownBy(() -> ProfilsPointage.valider(brut("code", null))).hasMessage("« code » est obligatoire");
        }

        @Test
        @DisplayName("un profil à pointage en temps réel admet une fenêtre nulle : il n'admet de toute façon aucune saisie après coup")
        void tempsReelFenetreNulle() {
            assertThat(ProfilsPointage.valider(brut("mode", "TEMPS_REEL", "fenetreAPosterioriJours", 0)).fenetreAPosterioriJours()).isZero();
        }
    }

    @Nested
    @DisplayName("choisir et juger")
    class Juger {

        @Test
        @DisplayName("le plus particulier l'emporte ; à égalité le code alphabétique ; un profil inactif ou hors portée ne vise personne ; sinon le défaut")
        void profilDe() {
            ProfilPointage organisme = profil(ModeSaisie.MIXTE, PorteeRegle.ORGANISME, null, "Z-ORG", null);
            ProfilPointage unite = profil(ModeSaisie.TEMPS_REEL, PorteeRegle.UNITE, "ORG", "B-UNI", null);
            ProfilPointage uniteA = profil(ModeSaisie.TEMPS_REEL, PorteeRegle.UNITE, "EST", "A-UNI", null);
            ProfilPointage titreInactif = new ProfilPointage("T", "T", ModeSaisie.A_POSTERIORI, PorteeRegle.TITRE_EMPLOI, "TAP", List.of(NaturePause.PAUSE), false, 0, 1, false);
            ProfilPointage autreTitre = profil(ModeSaisie.A_POSTERIORI, PorteeRegle.TITRE_EMPLOI, "RHU", "AUTRE", 3);
            assertThat(ProfilsPointage.profilDe(List.of(organisme, unite, uniteA, titreInactif, autreTitre), DOSSIER)).isEqualTo(uniteA);
            assertThat(ProfilsPointage.profilDe(List.of(organisme), DOSSIER)).isEqualTo(organisme);
            assertThat(ProfilsPointage.profilDe(List.of(titreInactif, autreTitre), DOSSIER)).isSameAs(ProfilsPointage.PROFIL_PAR_DEFAUT);
        }

        @Test
        @DisplayName("le mode : temps réel refusé à un profil après coup, déclaration refusée à un profil en temps réel, MIXTE admet les deux")
        void mode() {
            ProfilPointage apres = profil(ModeSaisie.A_POSTERIORI, PorteeRegle.ORGANISME, null, "AP", null);
            ProfilPointage reel = profil(ModeSaisie.TEMPS_REEL, PorteeRegle.ORGANISME, null, "TR", 2);
            assertThat(ProfilsPointage.admetSaisie(apres, true, 0).motifs())
                    .containsExactly("le profil « Profil AP » est à saisie après coup : le pointage en temps réel n'y est pas admis");
            assertThat(ProfilsPointage.admetSaisie(apres, false, 400).admise()).isTrue();
            assertThat(ProfilsPointage.admetSaisie(reel, false, 9).motifs())
                    .containsExactly("le profil « Profil TR » est à pointage en temps réel : la déclaration après coup n'y est pas admise");
            assertThat(ProfilsPointage.admetSaisie(reel, true, 0).admise()).isTrue();
            assertThat(ProfilsPointage.admetSaisie(ProfilsPointage.PROFIL_PAR_DEFAUT, true, 0).admise()).isTrue();
            assertThat(ProfilsPointage.admetSaisie(ProfilsPointage.PROFIL_PAR_DEFAUT, false, 0).admise()).isTrue();
        }

        @Test
        @DisplayName("la fenêtre : atteinte, admise ; dépassée, refusée avec les deux nombres")
        void fenetre() {
            ProfilPointage p = profil(ModeSaisie.MIXTE, PorteeRegle.ORGANISME, null, "M", 3);
            assertThat(ProfilsPointage.admetSaisie(p, false, 3).admise()).isTrue();
            assertThat(ProfilsPointage.admetSaisie(p, false, 4))
                    .isEqualTo(new ProfilsPointage.VerdictSaisie(false, List.of("la fenêtre de saisie du profil « Profil M » est de 3 jour(s) : cette déclaration en a 4")));
            assertThat(ProfilsPointage.admetSaisie(p, true, 40).admise()).isTrue();
        }

        @Test
        @DisplayName("la nature : admise, ou refusée en nommant les natures admises dans les mots de l'écran")
        void nature() {
            ProfilPointage p = new ProfilPointage("N", "Nuit", ModeSaisie.MIXTE, PorteeRegle.ORGANISME, null, List.of(NaturePause.REPAS, NaturePause.REPOS), false, 0, null, true);
            assertThat(ProfilsPointage.admetNature(p, NaturePause.REPAS).admise()).isTrue();
            assertThat(ProfilsPointage.admetNature(p, NaturePause.PAUSE).motifs())
                    .containsExactly("le profil « Nuit » n'admet pas la pause (admises : pause-repas, période de repos)");
        }

        @Test
        @DisplayName("les jours écoulés : arrondis vers le bas, jamais négatifs")
        void jours() {
            assertThat(ProfilsPointage.joursEcoules("2031-03-01T10:00:00Z", "2031-03-03T09:59:59Z")).isEqualTo(1);
            assertThat(ProfilsPointage.joursEcoules("2031-03-01T10:00:00Z", "2031-03-03T10:00:00Z")).isEqualTo(2);
            assertThat(ProfilsPointage.joursEcoules("2031-03-03T10:00:00Z", "2031-03-01T10:00:00Z")).isZero();
        }

        @Test
        @DisplayName("les ensembles fermés se convertissent à la frontière, et le refus nomme les valeurs admises")
        void ensembles() {
            assertThat(ModeSaisie.de("MIXTE")).isEqualTo(ModeSaisie.MIXTE);
            assertThat(ModeSaisie.TEMPS_REEL.code()).isEqualTo("TEMPS_REEL");
            assertThat(NaturePause.de("REPOS", "x")).isEqualTo(NaturePause.REPOS);
            assertThat(NaturePause.REPAS.code()).isEqualTo("REPAS");
            assertThat(NaturePause.attendues()).isEqualTo("PAUSE, REPAS, REPOS");
            assertThatThrownBy(() -> NaturePause.de(null, "« nature » :")).isInstanceOf(EffectifsInvalideException.class).hasMessage("« nature » : PAUSE, REPAS, REPOS");
        }
    }
}
