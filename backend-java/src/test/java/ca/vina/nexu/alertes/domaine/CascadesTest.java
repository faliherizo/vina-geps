package ca.vina.nexu.alertes.domaine;

import ca.vina.nexu.alertes.domaine.Alertes.Alerte;
import ca.vina.nexu.alertes.domaine.Alertes.Destinataire;
import ca.vina.nexu.alertes.domaine.Alertes.Remise;
import ca.vina.nexu.alertes.domaine.Alertes.ReponsePredefinie;
import ca.vina.nexu.alertes.domaine.Cascades.Cascade;
import ca.vina.nexu.alertes.domaine.Cascades.Cible;
import ca.vina.nexu.alertes.domaine.Cascades.CritereArret;
import ca.vina.nexu.alertes.domaine.Cascades.Etape;
import ca.vina.nexu.alertes.domaine.Cascades.Etat;
import ca.vina.nexu.alertes.domaine.Cascades.Evenement;
import ca.vina.nexu.alertes.domaine.Cascades.Politique;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Cascades (ST-G04) — politiques versionnées, étapes idempotentes, arrêt et reprise")
class CascadesTest {

    static final List<Priorite.Canal> POUSSEE = List.of(Priorite.Canal.POUSSEE);
    static final String T0 = "2026-11-16T14:00:00Z";

    static Politique troisEtapes() {
        return Politique.nouvelle("p1", "Code bleu", List.of(new Etape(Cible.INITIAUX, null, POUSSEE, 5),
                new Etape(Cible.GROUPE, "EST", List.of(Priorite.Canal.TEXTO, Priorite.Canal.TEXTO), 10),
                new Etape(Cible.PERSONNE, "chef", List.of(Priorite.Canal.COURRIEL), 15)), CritereArret.PRISE_EN_CHARGE);
    }

    static Alerte alerte(boolean lue, String reponseId) {
        ReponsePredefinie arrive = new ReponsePredefinie("rp", "J'arrive", Semantique.PRISE_EN_CHARGE, true);
        ReponsePredefinie info = new ReponsePredefinie("ri", "Vu", Semantique.INFORMATION, true);
        Remise julie = new Remise(new Destinataire("julie", "Julie", "u-julie"), T0, lue ? T0 : null, reponseId, reponseId == null ? null : T0);
        return new Alerte("a1", "T", "Code bleu", "x", Priorite.CRITIQUE, "chef", T0, List.of(arrive, info), List.of(julie), null);
    }

    static void refus(Runnable r, String message) {
        assertThatThrownBy(r::run).isInstanceOf(RefusAlerte.class).hasMessage(message);
    }

    @Test
    @DisplayName("une politique : de 1 à 10 étapes, cible nommée quand il le faut, au moins un canal, délai de 1 min à 24 h")
    void validation() {
        Politique p = troisEtapes();
        assertThat(p.version()).isEqualTo(1);
        assertThat(p.etapes().get(1).canaux()).containsExactly(Priorite.Canal.TEXTO);
        assertThat(p.etapes().getFirst().cibleId()).isNull();
        assertThat(p.etapes().get(2).codesCanaux()).containsExactly("courriel");
        refus(() -> Politique.nouvelle("p", "X", List.of(), CritereArret.TOUTES_LUES), "une politique compte de 1 à 10 étapes");
        refus(() -> Politique.nouvelle("p", "X", Collections.nCopies(11, new Etape(Cible.INITIAUX, null, POUSSEE, 5)), CritereArret.TOUTES_LUES),
                "une politique compte de 1 à 10 étapes");
        refus(() -> Politique.nouvelle("p", "X", List.of(new Etape(Cible.GROUPE, " ", POUSSEE, 5)), CritereArret.TOUTES_LUES),
                "étape 1 : « cibleId » est requis pour la cible « groupe »");
        refus(() -> Politique.nouvelle("p", "X", List.of(new Etape(Cible.INITIAUX, null, List.of(), 5)), CritereArret.TOUTES_LUES), "étape 1 : au moins un canal");
        refus(() -> Politique.nouvelle("p", "X", List.of(new Etape(Cible.INITIAUX, null, POUSSEE, 0)), CritereArret.TOUTES_LUES),
                "étape 1 : « delaiMinutes » entre 1 et 1440");
        refus(() -> Politique.nouvelle("p", "X", List.of(new Etape(Cible.INITIAUX, null, POUSSEE, 1441)), CritereArret.TOUTES_LUES),
                "étape 1 : « delaiMinutes » entre 1 et 1440");
        refus(() -> Politique.nouvelle("p", "X", List.of(new Etape(null, null, POUSSEE, 5)), CritereArret.TOUTES_LUES), "étape 1 : « cible » est requise");
        refus(() -> Politique.nouvelle("p", "X", List.of(new Etape(Cible.PERSONNE, null, POUSSEE, 5)), CritereArret.TOUTES_LUES),
                "étape 1 : « cibleId » est requis pour la cible « personne »");
        refus(() -> Politique.nouvelle("p", "", List.of(new Etape(Cible.INITIAUX, null, POUSSEE, 5)), CritereArret.TOUTES_LUES), "« nom » est requis");
        refus(() -> Politique.nouvelle("p", "X", List.of(new Etape(Cible.INITIAUX, null, POUSSEE, 5)), null), "« arret » est requis");
    }

    @Test
    @DisplayName("modifiée, une politique fait une version nouvelle ; désactivée, elle garde sa version et n'arme plus rien")
    void versions() {
        Politique v2 = troisEtapes().modifiee("Code bleu — nuit", List.of(new Etape(Cible.INITIAUX, null, POUSSEE, 3)), CritereArret.TOUTES_LUES);
        assertThat(v2.version()).isEqualTo(2);
        assertThat(v2.nom()).isEqualTo("Code bleu — nuit");
        Politique inactive = v2.avecActif(false);
        assertThat(inactive.version()).isEqualTo(2);
        assertThat(inactive.actif()).isFalse();
        refus(() -> Cascade.armer("a1", inactive, T0), "la politique « Code bleu — nuit » est désactivée");
    }

    @Test
    @DisplayName("armée à l'envoi, la cascade attend l'étape 1 ; chaque étape fait avancer, la dernière l'épuise")
    void avancement() {
        Cascade c = Cascade.armer("a1", troisEtapes(), T0);
        assertThat(c.etat()).isEqualTo(Etat.ARMEE);
        assertThat(c.echeanceLe()).isEqualTo("2026-11-16T14:05:00Z");
        assertThat(c.attend(0)).isTrue();
        assertThat(c.attend(1)).isFalse();
        assertThat(c.etapeCourante().cible()).isEqualTo(Cible.INITIAUX);
        Cascade deux = c.apresEtape("2026-11-16T14:05:00Z");
        assertThat(deux.etape()).isEqualTo(1);
        assertThat(deux.echeanceLe()).isEqualTo("2026-11-16T14:15:00Z");
        assertThat(deux.attend(0)).isFalse();
        Cascade epuisee = deux.apresEtape("2026-11-16T14:15:00Z").apresEtape("2026-11-16T14:30:00Z");
        assertThat(epuisee.etat()).isEqualTo(Etat.EPUISEE);
        assertThat(epuisee.prochaineEcheance()).isEmpty();
        assertThat(epuisee.attend(3)).isFalse();
        assertThat(deux.prochaineEcheance()).contains("2026-11-16T14:15:00Z");
    }

    @Test
    @DisplayName("arrêtée, plus rien ne part ; la reprise relance l'étape choisie tout de suite, dans les bornes de la politique")
    void arretEtReprise() {
        Cascade arretee = Cascade.armer("a1", troisEtapes(), T0).arretee();
        assertThat(arretee.etat()).isEqualTo(Etat.ARRETEE);
        assertThat(arretee.attend(0)).isFalse();
        Cascade reprise = arretee.reprise(2, "2026-11-16T15:00:00Z");
        assertThat(reprise.etat()).isEqualTo(Etat.ARMEE);
        assertThat(reprise.etape()).isEqualTo(2);
        assertThat(reprise.echeanceLe()).isEqualTo("2026-11-16T15:00:00Z");
        refus(() -> arretee.reprise(3, T0), "« etape » : de 1 à 3");
        refus(() -> arretee.reprise(-1, T0), "« etape » : de 1 à 3");
    }

    @Test
    @DisplayName("le critère d'arrêt : une prise en charge (pas une simple information), ou toutes les lectures")
    void criteres() {
        assertThat(CritereArret.PRISE_EN_CHARGE.atteint(alerte(true, "rp"))).isTrue();
        assertThat(CritereArret.PRISE_EN_CHARGE.atteint(alerte(true, "ri"))).isFalse();
        assertThat(CritereArret.PRISE_EN_CHARGE.atteint(alerte(false, null))).isFalse();
        assertThat(CritereArret.TOUTES_LUES.atteint(alerte(true, null))).isTrue();
        assertThat(CritereArret.TOUTES_LUES.atteint(alerte(false, null))).isFalse();
    }

    @Test
    @DisplayName("les codes : arrêt, cible, état, événement, canal ; un code inconnu est nommé")
    void codes() {
        assertThat(CritereArret.de("toutes_lues")).isEqualTo(CritereArret.TOUTES_LUES);
        assertThat(Cible.de("prochain_disponible")).isEqualTo(Cible.PROCHAIN_DISPONIBLE);
        assertThat(Cible.INITIAUX.exigeCibleId()).isFalse();
        assertThat(Etat.de("epuisee")).isEqualTo(Etat.EPUISEE);
        assertThat(Evenement.de("reprise")).isEqualTo(Evenement.REPRISE);
        assertThat(Priorite.Canal.de("texto")).isEqualTo(Priorite.Canal.TEXTO);
        refus(() -> CritereArret.de("x"), "« arret » : prise_en_charge ou toutes_lues");
        refus(() -> Cible.de("x"), "« cible » : initiaux, groupe, personne ou prochain_disponible");
        refus(() -> Etat.de("x"), "« etat » : armee, arretee ou epuisee");
        refus(() -> Evenement.de("x"), "événement de cascade inconnu : x");
        refus(() -> Priorite.Canal.de("pigeon"), "« canaux » : courriel, texto ou poussee");
        Cascades.Trace t = new Cascades.Trace("a1", Evenement.ETAPE, 0, new java.util.ArrayList<>(List.of("julie")), T0, "cascade");
        assertThat(t.personnes()).containsExactly("julie");
        assertThat(Cascades.plus(T0, 90)).isEqualTo("2026-11-16T15:30:00Z");
    }
}
