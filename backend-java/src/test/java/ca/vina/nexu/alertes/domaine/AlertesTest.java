package ca.vina.nexu.alertes.domaine;

import ca.vina.nexu.alertes.domaine.Alertes.Alerte;
import ca.vina.nexu.alertes.domaine.Alertes.Composition;
import ca.vina.nexu.alertes.domaine.Alertes.Destinataire;
import ca.vina.nexu.alertes.domaine.Alertes.Modele;
import ca.vina.nexu.alertes.domaine.Alertes.ReponsePredefinie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Communications cliniques — les règles R1 à R9 de l'analyse métier, sans base ni serveur")
class AlertesTest {

    private static final ReponsePredefinie ARRIVE = new ReponsePredefinie("r1", "J'arrive", Semantique.PRISE_EN_CHARGE, true);
    private static final ReponsePredefinie INDISPO = new ReponsePredefinie("r2", "Indisponible", Semantique.REFUS, true);
    private static final ReponsePredefinie VIEILLE = new ReponsePredefinie("r3", "Ancienne", Semantique.INFORMATION, false);
    private static final List<ReponsePredefinie> CATALOGUE = List.of(ARRIVE, INDISPO, VIEILLE);
    private static final Destinataire JULIE = new Destinataire("julie", "Julie Roy", "E1");
    private static final Destinataire MARC = new Destinataire("marc", "Marc Dubé", "E2");
    private static final Destinataire CHEF = new Destinataire("chef", "Claire Nadeau", "E3");

    private static Alerte alerte() {
        return Alertes.composer("a1", "T", "chef", "2026-11-14T14:02:00Z",
                new Composition("Code bleu 512", "Chambre 512, 5e étage", Priorite.CRITIQUE, List.of("r1", "r2", "r1")),
                List.of(JULIE, MARC, JULIE, CHEF), CATALOGUE);
    }

    private static void refus(Runnable geste, RefusAlerte.Nature nature, String message) {
        assertThatThrownBy(geste::run).isInstanceOf(RefusAlerte.class).hasMessageContaining(message)
                .satisfies(e -> assertThat(((RefusAlerte) e).nature()).isEqualTo(nature));
    }

    @Test
    @DisplayName("R2 : les destinataires sont dédoublonnés, l'expéditeur exclu ; R3 : les réponses proposées sont copiées une fois chacune")
    void composer() {
        Alerte a = alerte();
        assertThat(a.remises()).extracting(r -> r.destinataire().personId()).containsExactly("julie", "marc");
        assertThat(a.remises()).allSatisfy(r -> {
            assertThat(r.envoyeeLe()).isEqualTo("2026-11-14T14:02:00Z");
            assertThat(r.lue()).isFalse();
            assertThat(r.repondue()).isFalse();
        });
        assertThat(a.reponses()).containsExactly(ARRIVE, INDISPO);
        assertThat(a.bilan()).isEqualTo(new Alertes.Bilan(2, 0, 0, java.util.Optional.empty()));
    }

    @Test
    @DisplayName("R1 : titre, texte et priorité requis et bornés ; au moins un destinataire ; R3 : une réponse inactive ou inconnue est refusée")
    void composerRefuse() {
        Composition bonne = new Composition("Titre", "Texte", Priorite.URGENTE, List.of());
        refus(() -> Alertes.composer("a", "T", "chef", "x", new Composition(" ", "Texte", Priorite.URGENTE, List.of()), List.of(JULIE), CATALOGUE),
                RefusAlerte.Nature.INVALIDE, "« titre » est requis");
        refus(() -> Alertes.composer("a", "T", "chef", "x", new Composition("T".repeat(121), "Texte", Priorite.URGENTE, List.of()), List.of(JULIE), CATALOGUE),
                RefusAlerte.Nature.INVALIDE, "120 caractères au plus");
        refus(() -> Alertes.composer("a", "T", "chef", "x", new Composition("Titre", null, Priorite.URGENTE, List.of()), List.of(JULIE), CATALOGUE),
                RefusAlerte.Nature.INVALIDE, "« corps » est requis");
        refus(() -> Alertes.composer("a", "T", "chef", "x", new Composition("Titre", "Texte", null, List.of()), List.of(JULIE), CATALOGUE),
                RefusAlerte.Nature.INVALIDE, "« priorite » est requis");
        refus(() -> Alertes.composer("a", "T", "chef", "x", bonne, List.of(CHEF), CATALOGUE),
                RefusAlerte.Nature.INVALIDE, "aucun destinataire");
        refus(() -> Alertes.composer("a", "T", "chef", "x", new Composition("Titre", "Texte", Priorite.URGENTE, List.of("r3")), List.of(JULIE), CATALOGUE),
                RefusAlerte.Nature.INVALIDE, "la réponse « r3 » n'existe pas ou n'est plus active");
        assertThat(Alertes.composer("a", "T", "chef", "x", new Composition("  Titre  ", " Texte ", Priorite.INFORMATION, List.of()), List.of(JULIE), CATALOGUE).titre())
                .isEqualTo("Titre");
    }

    @Test
    @DisplayName("R4 : la première lecture compte, les suivantes ne changent rien ; R6 : un non-destinataire ne trouve pas l'alerte")
    void lire() {
        Alerte lue = alerte().lue("julie", "2026-11-14T14:03:00Z");
        assertThat(lue.remiseDe("julie").lueLe()).isEqualTo("2026-11-14T14:03:00Z");
        assertThat(lue.lue("julie", "2026-11-14T15:00:00Z")).isSameAs(lue);
        assertThat(lue.remiseDe("marc").lue()).isFalse();
        refus(() -> alerte().lue("lea", "x"), RefusAlerte.Nature.INTROUVABLE, "alerte introuvable");
    }

    @Test
    @DisplayName("R5 : une réponse proposée, une seule fois ; répondre vaut lecture ; la première prise en charge est retenue")
    void repondre() {
        Alerte a = alerte().lue("marc", "2026-11-14T14:02:30Z")
                .repondue("marc", "r2", "2026-11-14T14:04:00Z")
                .repondue("julie", "r1", "2026-11-14T14:03:00Z");
        assertThat(a.remiseDe("julie").lueLe()).isEqualTo("2026-11-14T14:03:00Z");
        assertThat(a.remiseDe("marc").lueLe()).isEqualTo("2026-11-14T14:02:30Z");
        Alertes.Bilan b = a.bilan();
        assertThat(b.lues()).isEqualTo(2);
        assertThat(b.repondues()).isEqualTo(2);
        assertThat(b.premierePriseEnCharge()).map(r -> r.destinataire().nom()).contains("Julie Roy");
        refus(() -> a.repondue("julie", "r2", "x"), RefusAlerte.Nature.CONFLIT, "déjà répondu");
        refus(() -> alerte().repondue("julie", "r3", "x"), RefusAlerte.Nature.INVALIDE, "n'est pas proposée");
        assertThat(alerte().reponse("inconnue")).isEmpty();
    }

    @Test
    @DisplayName("de deux prises en charge, la plus ancienne est la première")
    void premierePriseEnCharge() {
        Alerte a = alerte().repondue("marc", "r1", "2026-11-14T14:05:00Z").repondue("julie", "r1", "2026-11-14T14:03:00Z");
        assertThat(a.bilan().premierePriseEnCharge()).map(r -> r.destinataire().personId()).contains("julie");
    }

    @Test
    @DisplayName("R9 : une réponse et un modèle se désactivent, jamais ne s'effacent ; leurs textes sont validés")
    void parametres() {
        ReponsePredefinie r = ReponsePredefinie.nouvelle("r9", "  Je prends  ", Semantique.PRISE_EN_CHARGE);
        assertThat(r).isEqualTo(new ReponsePredefinie("r9", "Je prends", Semantique.PRISE_EN_CHARGE, true));
        assertThat(r.avecActive(false).active()).isFalse();
        refus(() -> ReponsePredefinie.nouvelle("x", "x".repeat(61), Semantique.REFUS), RefusAlerte.Nature.INVALIDE, "60 caractères");
        refus(() -> ReponsePredefinie.nouvelle("x", "Ok", null), RefusAlerte.Nature.INVALIDE, "« semantique » est requis");
        Modele m = Modele.nouveau("m1", "Code bleu", "Chambre {{chambre}}", Priorite.CRITIQUE, List.of("r1"));
        assertThat(m.actif()).isTrue();
        assertThat(m.avecActif(false).actif()).isFalse();
        refus(() -> Modele.nouveau("m", "Titre", "x".repeat(2_001), Priorite.URGENTE, List.of()), RefusAlerte.Nature.INVALIDE, "2000 caractères");
    }

    @Test
    @DisplayName("R8 : la priorité critique impose tous les canaux ; les autres laissent décider les préférences ; les codes se lisent et se refusent")
    void prioritesEtSemantiques() {
        assertThat(Priorite.CRITIQUE.canauxImposes()).containsExactly("courriel", "texto", "poussee");
        assertThat(Priorite.URGENTE.canauxImposes()).isNull();
        assertThat(Priorite.INFORMATION.canauxImposes()).isNull();
        assertThat(Priorite.de("urgente")).contains(Priorite.URGENTE);
        assertThat(Priorite.de("haute")).isEmpty();
        assertThat(Priorite.CRITIQUE.code()).isEqualTo("critique");
        assertThat(Semantique.de("prise_en_charge")).contains(Semantique.PRISE_EN_CHARGE);
        assertThat(Semantique.de("oui")).isEmpty();
        assertThat(Semantique.REFUS.code()).isEqualTo("refus");
    }
}
