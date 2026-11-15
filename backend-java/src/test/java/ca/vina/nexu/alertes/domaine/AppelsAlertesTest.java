package ca.vina.nexu.alertes.domaine;

import ca.vina.nexu.alertes.domaine.AppelsAlertes.Appel;
import ca.vina.nexu.alertes.domaine.AppelsAlertes.ReponduPar;
import ca.vina.nexu.alertes.domaine.AppelsAlertes.Resultat;
import ca.vina.nexu.alertes.domaine.AppelsAlertes.StatutFournisseur;
import ca.vina.nexu.alertes.domaine.AppelsAlertes.Touche;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Appel automatique (ST-G06) — résultat, touche, message dit au téléphone")
class AppelsAlertesTest {

    @Test
    @DisplayName("le statut du fournisseur devient un résultat ; un répondeur est une messagerie ; l'inconnu est un échec")
    void resultats() {
        assertThat(Resultat.duStatut("completed", null)).isEqualTo(Resultat.DECROCHE);
        assertThat(Resultat.duStatut("COMPLETED", "human")).isEqualTo(Resultat.DECROCHE);
        assertThat(Resultat.duStatut("completed", "machine_start")).isEqualTo(Resultat.MESSAGERIE);
        assertThat(Resultat.duStatut("completed", "fax")).isEqualTo(Resultat.MESSAGERIE);
        assertThat(Resultat.duStatut("busy", null)).isEqualTo(Resultat.OCCUPE);
        assertThat(Resultat.duStatut("no-answer", null)).isEqualTo(Resultat.SANS_REPONSE);
        assertThat(Resultat.duStatut("ringing", null)).isEqualTo(Resultat.LANCE);
        assertThat(Resultat.duStatut("failed", null)).isEqualTo(Resultat.ECHEC);
        assertThat(Resultat.duStatut("canceled", null)).isEqualTo(Resultat.ECHEC);
        assertThat(Resultat.duStatut("inconnu", null)).isEqualTo(Resultat.ECHEC);
        assertThat(Resultat.duStatut(null, null)).isEqualTo(Resultat.ECHEC);
        assertThat(StatutFournisseur.de("in-progress")).contains(StatutFournisseur.IN_PROGRESS);
        assertThat(ReponduPar.de("nimporte")).isEqualTo(ReponduPar.UNKNOWN);
        assertThat(ReponduPar.de(null).estUneMachine()).isFalse();
        assertThat(Resultat.de("messagerie")).isEqualTo(Resultat.MESSAGERIE);
        assertThat(Resultat.OCCUPE.code()).isEqualTo("occupe");
        assertThatThrownBy(() -> Resultat.de("x")).isInstanceOf(IllegalArgumentException.class);
        // Une touche reçue reste « répondu », quoi que dise la fin d'appel.
        assertThat(Resultat.REPONDU.apres(Resultat.DECROCHE)).isEqualTo(Resultat.REPONDU);
        assertThat(Resultat.LANCE.apres(Resultat.OCCUPE)).isEqualTo(Resultat.OCCUPE);
    }

    @Test
    @DisplayName("« 1 » prend en charge, « 2 » refuse, le reste ne compte pas ; la voix répond selon le cas")
    void touches() {
        assertThat(Touche.de("1")).isEqualTo(Touche.PRISE_EN_CHARGE);
        assertThat(Touche.de(" 2 ")).isEqualTo(Touche.REFUS);
        assertThat(Touche.de("12")).isEqualTo(Touche.AUTRE);
        assertThat(Touche.de(null)).isEqualTo(Touche.AUTRE);
        assertThat(Touche.REFUS.semantique()).isEqualTo(Semantique.REFUS);
        assertThat(Touche.PRISE_EN_CHARGE.semantique()).isEqualTo(Semantique.PRISE_EN_CHARGE);
        assertThatThrownBy(Touche.AUTRE::semantique).isInstanceOf(IllegalStateException.class);
        assertThat(AppelsAlertes.reponseVocale(Touche.PRISE_EN_CHARGE, true)).startsWith("Prise en charge enregistrée");
        assertThat(AppelsAlertes.reponseVocale(Touche.REFUS, true)).startsWith("Refus enregistré");
        assertThat(AppelsAlertes.reponseVocale(Touche.AUTRE, true)).startsWith("Touche non reconnue");
        assertThat(AppelsAlertes.reponseVocale(Touche.PRISE_EN_CHARGE, false)).startsWith("Une réponse est déjà enregistrée");
    }

    @Test
    @DisplayName("le message dit la priorité, le titre et le début du texte, puis la consigne ; un long texte est borné")
    void message() {
        Alertes.Alerte court = new Alertes.Alerte("a", "T", "Code bleu", "5e étage", Priorite.CRITIQUE, "chef", "x", List.of(), List.of(), null);
        assertThat(AppelsAlertes.message(court)).isEqualTo("Alerte critique de VINA GEPS : Code bleu. 5e étage. Appuyez sur 1 pour prendre en charge, sur 2 pour refuser.");
        Alertes.Alerte long_ = new Alertes.Alerte("a", "T", "T", "x".repeat(800), Priorite.URGENTE, "chef", "x", List.of(), List.of(), null);
        assertThat(AppelsAlertes.message(long_)).hasSize(AppelsAlertes.MESSAGE_MAX + ". Appuyez sur 1 pour prendre en charge, sur 2 pour refuser.".length());
    }

    @Test
    @DisplayName("un appel porte l'identifiant du fournisseur, la touche, puis le résultat de fin et la durée")
    void transitions() {
        Appel lance = new Appel("ap", "T", "a", 0, "julie", null, Resultat.LANCE, null, null, "t0", null);
        Appel touche = lance.avecFournisseur("CA1").touchee("1");
        assertThat(touche.fournisseurId()).isEqualTo("CA1");
        assertThat(touche.resultat()).isEqualTo(Resultat.REPONDU);
        Appel fini = touche.termine(Resultat.DECROCHE, 42, "t1");
        assertThat(fini.resultat()).isEqualTo(Resultat.REPONDU);
        assertThat(fini.dureeSecondes()).isEqualTo(42);
        assertThat(fini.termineLe()).isEqualTo("t1");
        assertThat(lance.termine(Resultat.SANS_REPONSE, 0, "t1").resultat()).isEqualTo(Resultat.SANS_REPONSE);
    }
}
