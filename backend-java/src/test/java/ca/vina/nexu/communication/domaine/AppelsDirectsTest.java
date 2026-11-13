package ca.vina.nexu.communication.domaine;

import ca.vina.nexu.communication.domaine.AppelsDirects.Appel;
import ca.vina.nexu.communication.domaine.AppelsDirects.Etat;
import ca.vina.nexu.communication.domaine.AppelsDirects.Geste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Appels entre collègues — la machine à états")
class AppelsDirectsTest {

    private static Appel appel(Etat etat) {
        return new Appel("a1", "roy", "ngu", null, etat, "2026-11-01T10:00:00.000Z", null, null, null);
    }

    private static void refus(Appel a, Geste g, String acteur, String motif) {
        assertThatThrownBy(() -> AppelsDirects.appliquer(a, g, acteur)).isInstanceOf(RefusCommunication.class).hasMessage(motif)
                .satisfies(e -> assertThat(((RefusCommunication) e).nature()).isEqualTo(RefusCommunication.Nature.INVALIDE));
    }

    @Test
    @DisplayName("seul l'appelé répond ; un appel décroché ne se redécroche pas")
    void repondre() {
        assertThat(AppelsDirects.appliquer(appel(Etat.SONNE), Geste.DECROCHER, "ngu")).isEqualTo(Etat.EN_COURS);
        assertThat(AppelsDirects.appliquer(appel(Etat.SONNE), Geste.REFUSER, "ngu")).isEqualTo(Etat.REFUSE);
        refus(appel(Etat.SONNE), Geste.DECROCHER, "roy", "seule la personne appelée peut répondre à cet appel");
        refus(appel(Etat.EN_COURS), Geste.REFUSER, "ngu", "cet appel a déjà été décroché");
    }

    @Test
    @DisplayName("annuler, raccrocher, expirer, et aucun geste sur un appel terminé")
    void autresGestes() {
        assertThat(AppelsDirects.appliquer(appel(Etat.SONNE), Geste.ANNULER, "roy")).isEqualTo(Etat.ANNULE);
        refus(appel(Etat.SONNE), Geste.ANNULER, "ngu", "seule la personne qui appelle peut annuler avant la réponse");
        refus(appel(Etat.EN_COURS), Geste.ANNULER, "roy", "cet appel est déjà décroché : raccrochez");
        assertThat(AppelsDirects.appliquer(appel(Etat.SONNE), Geste.RACCROCHER, "roy")).isEqualTo(Etat.ANNULE);
        assertThat(AppelsDirects.appliquer(appel(Etat.SONNE), Geste.RACCROCHER, "ngu")).isEqualTo(Etat.REFUSE);
        assertThat(AppelsDirects.appliquer(appel(Etat.EN_COURS), Geste.RACCROCHER, "ngu")).isEqualTo(Etat.TERMINE);
        refus(appel(Etat.SONNE), Geste.RACCROCHER, "tiers", "cet appel ne vous concerne pas");
        assertThat(AppelsDirects.appliquer(appel(Etat.SONNE), Geste.EXPIRER, "x")).isEqualTo(Etat.MANQUE);
        refus(appel(Etat.EN_COURS), Geste.EXPIRER, "x", "cet appel a été décroché");
        refus(appel(Etat.MANQUE), Geste.DECROCHER, "ngu", "cet appel est déjà manqué");
        assertThat(Geste.EXPIRER.code()).isEqualTo("expirer");
    }

    @Test
    @DisplayName("durée parlée : du décrochage à la fin, zéro sinon")
    void duree() {
        Appel a = appel(Etat.TERMINE);
        assertThat(AppelsDirects.dureeSecondes(a)).isZero();
        assertThat(AppelsDirects.dureeSecondes(a.avec(Etat.TERMINE, "2026-11-01T10:00:00.000Z", "2026-11-01T10:01:30.600Z", "roy"))).isEqualTo(91);
        assertThat(AppelsDirects.dureeSecondes(a.avec(Etat.TERMINE, "2026-11-01T10:00:00.000Z", "2026-11-01T09:00:00.000Z", "roy"))).isZero();
        assertThat(AppelsDirects.dureeSecondes(a.avec(Etat.TERMINE, "illisible", "2026-11-01T09:00:00.000Z", "roy"))).isZero();
        assertThat(AppelsDirects.dureeSecondes(a.avec(Etat.TERMINE, "2026-11-01T10:00:00.000Z", null, "roy"))).isZero();
    }

    @Test
    @DisplayName("une sonnerie expire après 45 s, et seulement une sonnerie")
    void expiration() {
        assertThat(AppelsDirects.sonnerieExpiree(appel(Etat.SONNE), "2026-11-01T10:00:45.000Z")).isFalse();
        assertThat(AppelsDirects.sonnerieExpiree(appel(Etat.SONNE), "2026-11-01T10:00:45.001Z")).isTrue();
        assertThat(AppelsDirects.sonnerieExpiree(appel(Etat.EN_COURS), "2026-11-02T10:00:00.000Z")).isFalse();
        assertThat(AppelsDirects.sonnerieExpiree(appel(Etat.SONNE), "pas une date")).isFalse();
    }

    @Test
    @DisplayName("invitation : quelqu'un, pas soi ; carte servie selon le lecteur")
    void invitationEtCarte() {
        assertThat(AppelsDirects.validerInvitation("roy", null)).containsExactly("la personne à appeler est requise");
        assertThat(AppelsDirects.validerInvitation("roy", "  ")).containsExactly("la personne à appeler est requise");
        assertThat(AppelsDirects.validerInvitation("roy", " roy ")).containsExactly("on ne s'appelle pas soi-même");
        assertThat(AppelsDirects.validerInvitation("roy", "ngu")).isEmpty();
        Map<String, Object> m = appel(Etat.SONNE).enCarte("ngu");
        assertThat(m.keySet()).containsExactly("id", "appelantId", "appeleId", "conversationId", "etat", "lanceLe", "decrocheLe", "termineLe", "termineParId", "dureeSecondes", "entrant");
        assertThat(m.get("entrant")).isEqualTo(true);
        assertThat(appel(Etat.SONNE).enCarte("roy").get("entrant")).isEqualTo(false);
        assertThat(Etat.de("en_cours")).isEqualTo(Etat.EN_COURS);
        assertThatThrownBy(() -> Etat.de("??")).isInstanceOf(IllegalArgumentException.class);
        assertThat(List.of(Etat.values())).hasSize(6);
    }
}
