package ca.vina.nexu.effectifs.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Choix annuel des congés (addenda 8, M-02) — campagne, semaines, choix, octroi")
class CongesAnnuelsTest {

    private static Map<String, Object> entree() {
        Map<String, Object> e = new HashMap<>(Map.of("nom", "Été", "uniteCode", "EST", "corpsEmploiCode", "PARA", "annee", 2027, "du", "2027-07-01",
                "au", "2027-07-25", "codeAbsence", "VAC", "plafondParSemaine", 1));
        return e;
    }

    private static CongesAnnuels.Campagne ouverte(int plafond, int max) {
        return new CongesAnnuels.Campagne("c", "Été", "EST", "PARA", 2027, "2027-07-05", "2027-07-25", "VAC", plafond, max, CongesAnnuels.Etat.OUVERTE);
    }

    @Test
    @DisplayName("la saisie : les semaines entières de la saison, du lundi au dimanche ; maximum par défaut ; refus nommés")
    void saisie() {
        CongesAnnuels.Campagne c = CongesAnnuels.saisie("c1", entree());
        assertThat(c.semaines()).containsExactly("2027-07-05", "2027-07-12", "2027-07-19");
        assertThat(List.of(c.etat(), c.maxSemainesParPersonne())).containsExactly(CongesAnnuels.Etat.BROUILLON, 52);
        Map<String, Object> e = entree();
        e.put("maxSemainesParPersonne", 2);
        assertThat(CongesAnnuels.saisie("c2", e).maxSemainesParPersonne()).isEqualTo(2);
        e.put("au", "2027-07-03");
        assertThatThrownBy(() -> CongesAnnuels.saisie("c3", e)).hasMessage("la saison ne contient aucune semaine entière (du lundi au dimanche)");
        e.put("du", "2026-07-01");
        assertThatThrownBy(() -> CongesAnnuels.saisie("c3", e)).hasMessageContaining("doit commencer en 2027");
        Map<String, Object> f = entree();
        f.put("nom", " ");
        assertThatThrownBy(() -> CongesAnnuels.saisie("c4", f)).hasMessage("« nom » requis");
        Map<String, Object> g = entree();
        g.put("plafondParSemaine", 0);
        assertThatThrownBy(() -> CongesAnnuels.saisie("c5", g)).hasMessage("« plafondParSemaine » : un entier de 1 à 500");
        g.put("plafondParSemaine", 1.5);
        assertThatThrownBy(() -> CongesAnnuels.saisie("c5", g)).hasMessageContaining("plafondParSemaine");
        g.put("plafondParSemaine", "2");
        assertThatThrownBy(() -> CongesAnnuels.saisie("c5", g)).hasMessageContaining("plafondParSemaine");
    }

    @Test
    @DisplayName("le cycle : brouillon → ouverte → fermée ; le reste est refusé")
    void cycle() {
        assertThat(CongesAnnuels.Etat.BROUILLON.vers(CongesAnnuels.Etat.OUVERTE)).isEqualTo(CongesAnnuels.Etat.OUVERTE);
        assertThat(CongesAnnuels.Etat.OUVERTE.vers(CongesAnnuels.Etat.FERMEE)).isEqualTo(CongesAnnuels.Etat.FERMEE);
        assertThatThrownBy(() -> CongesAnnuels.Etat.BROUILLON.vers(CongesAnnuels.Etat.FERMEE)).isInstanceOf(ConflitException.class);
        assertThatThrownBy(() -> CongesAnnuels.Etat.FERMEE.vers(CongesAnnuels.Etat.OUVERTE)).isInstanceOf(ConflitException.class);
        assertThatThrownBy(() -> CongesAnnuels.Etat.OUVERTE.vers(CongesAnnuels.Etat.OCTROYEE)).isInstanceOf(ConflitException.class);
        assertThat(CongesAnnuels.Etat.de("OCTROYEE")).isEqualTo(CongesAnnuels.Etat.OCTROYEE);
        assertThatThrownBy(() -> CongesAnnuels.Etat.de(null)).hasMessageContaining("« etat »");
        assertThat(CongesAnnuels.finDeSemaine("2027-07-05")).isEqualTo("2027-07-11");
    }

    @Test
    @DisplayName("les choix : dans l'ordre, offerts, sans doublon, seulement quand les choix sont ouverts ; la campagne vise un groupe")
    void choix() {
        assertThat(CongesAnnuels.choix(ouverte(1, 1), "r1", List.of("2027-07-12", "2027-07-05")))
                .extracting(CongesAnnuels.Choix::rang, CongesAnnuels.Choix::semaine)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1, "2027-07-12"), org.assertj.core.groups.Tuple.tuple(2, "2027-07-05"));
        assertThatThrownBy(() -> CongesAnnuels.choix(ouverte(1, 1), "r1", List.of("2027-07-05", "2027-07-05"))).hasMessageContaining("deux fois");
        assertThatThrownBy(() -> CongesAnnuels.choix(ouverte(1, 1), "r1", List.of("2027-08-02"))).hasMessageContaining("pas une semaine offerte");
        assertThatThrownBy(() -> CongesAnnuels.choix(ouverte(1, 1), "r1", "2027-07-05")).hasMessageContaining("une liste de lundis");
        assertThatThrownBy(() -> CongesAnnuels.choix(ouverte(1, 1).avec(CongesAnnuels.Etat.FERMEE), "r1", List.of())).isInstanceOf(ConflitException.class);
        Ressource julie = new Ressource("r1", "P1", "Julie", null, "EST", "PARA", null, null, null, 4, true);
        assertThat(List.of(ouverte(1, 1).visePour(julie), ouverte(1, 1).visePour(new Ressource("r2", "P2", "X", null, "OUEST", "PARA", null, null, null, 1, true)),
                ouverte(1, 1).visePour(new Ressource("r3", "P3", "Y", null, "EST", "RMU", null, null, null, 1, true)))).containsExactly(true, false, false);
    }

    @Test
    @DisplayName("l'octroi : ancienneté décroissante puis nom ; plafond par semaine ; maximum par personne ; chaque choix décidé")
    void octroi() {
        List<CongesAnnuels.Choix> choix = List.of(
                new CongesAnnuels.Choix("julie", 1, "2027-07-05", CongesAnnuels.StatutChoix.DEMANDE, null),
                new CongesAnnuels.Choix("julie", 2, "2027-07-12", CongesAnnuels.StatutChoix.DEMANDE, null),
                new CongesAnnuels.Choix("marc", 2, "2027-07-19", CongesAnnuels.StatutChoix.DEMANDE, null),
                new CongesAnnuels.Choix("marc", 1, "2027-07-05", CongesAnnuels.StatutChoix.DEMANDE, null),
                new CongesAnnuels.Choix("ana", 1, "2027-07-12", CongesAnnuels.StatutChoix.DEMANDE, null));
        List<CongesAnnuels.Candidat> gens = List.of(new CongesAnnuels.Candidat("julie", "Julie", 4), new CongesAnnuels.Candidat("marc", "Marc", 9),
                new CongesAnnuels.Candidat("ana", "Ana", 4));
        assertThat(CongesAnnuels.octroi(ouverte(1, 1), gens, choix)).extracting(CongesAnnuels.Choix::ressourceId, CongesAnnuels.Choix::semaine, CongesAnnuels.Choix::statut)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("marc", "2027-07-05", CongesAnnuels.StatutChoix.OCTROYE),
                        org.assertj.core.groups.Tuple.tuple("marc", "2027-07-19", CongesAnnuels.StatutChoix.REFUSE),
                        org.assertj.core.groups.Tuple.tuple("ana", "2027-07-12", CongesAnnuels.StatutChoix.OCTROYE),
                        org.assertj.core.groups.Tuple.tuple("julie", "2027-07-05", CongesAnnuels.StatutChoix.REFUSE),
                        org.assertj.core.groups.Tuple.tuple("julie", "2027-07-12", CongesAnnuels.StatutChoix.REFUSE));
        assertThat(CongesAnnuels.octroi(ouverte(2, 2), gens, choix).stream().filter(x -> x.statut() == CongesAnnuels.StatutChoix.OCTROYE).count()).isEqualTo(5);
    }
}
