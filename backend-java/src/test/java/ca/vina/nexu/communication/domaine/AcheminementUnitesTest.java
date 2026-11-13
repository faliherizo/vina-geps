package ca.vina.nexu.communication.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Acheminement par unité — le réglage de l'unité, ou le défaut de l'établissement")
class AcheminementUnitesTest {

    private static AcheminementUnites.Etape etape(CanalReception c, ModeReception m) {
        return new AcheminementUnites.Etape(c, m);
    }

    @Test
    @DisplayName("sans réglage, le courriel en sonnerie, marqué comme défaut ; avec, les étapes dans l'ordre d'escalade")
    void canaux() {
        assertThat(AcheminementUnites.canaux(Optional.empty())).containsExactly(new AcheminementPort.Canal("courriel", "sonnerie", 1, true));
        var r = new AcheminementUnites.Reglage("EST", List.of(etape(CanalReception.SMS, ModeReception.PRIORITAIRE), etape(CanalReception.APPEL, ModeReception.SONNERIE)), "2026-11-13", "adm");
        assertThat(AcheminementUnites.canaux(Optional.of(r))).containsExactly(
                new AcheminementPort.Canal("sms", "prioritaire", 1, false), new AcheminementPort.Canal("appel", "sonnerie", 2, false));
        assertThat(r.enCarte()).containsEntry("uniteId", "EST").containsEntry("modifiePar", "adm")
                .containsEntry("etapes", List.of(java.util.Map.of("canal", "sms", "mode", "prioritaire"), java.util.Map.of("canal", "appel", "mode", "sonnerie")));
    }

    @Test
    @DisplayName("une chaîne vide, trop longue ou qui répète un canal est refusée, et chaque motif est nommé")
    void validation() {
        assertThat(AcheminementUnites.valider(List.of())).containsExactly("au moins un canal est requis");
        assertThat(AcheminementUnites.valider(List.of(etape(CanalReception.SMS, ModeReception.SONNERIE), etape(CanalReception.SMS, ModeReception.VIBRATION))))
                .containsExactly("un canal est répété");
        List<AcheminementUnites.Etape> tous = Arrays.stream(CanalReception.values()).map(c -> etape(c, ModeReception.SONNERIE)).toList();
        assertThat(AcheminementUnites.valider(tous)).isEmpty();
        List<AcheminementUnites.Etape> trop = new java.util.ArrayList<>(tous);
        trop.add(etape(CanalReception.COURRIEL, ModeReception.SILENCIEUX));
        assertThat(AcheminementUnites.valider(trop)).containsExactly("au plus 6 canaux", "un canal est répété");
    }

    @Test
    @DisplayName("un canal ou un mode inconnu est refusé à la frontière en nommant ceux qui existent")
    void codes() {
        assertThat(CanalReception.de("poussee_web")).isEqualTo(CanalReception.POUSSEE_WEB);
        assertThat(CanalReception.POUSSEE_WEB.libelle()).isEqualTo("Notification du navigateur");
        assertThat(ModeReception.de("tonalite_distincte")).isEqualTo(ModeReception.TONALITE_DISTINCTE);
        assertThat(ModeReception.PRIORITAIRE.libelle()).startsWith("Prioritaire");
        assertThatThrownBy(() -> CanalReception.de("pigeon")).isInstanceOf(RefusCommunication.class)
                .hasMessage("canal inconnu « pigeon » (attendus : courriel, sms, pagette, poussee_mobile, poussee_web, appel)");
        assertThatThrownBy(() -> ModeReception.de("fort")).hasMessageContaining("sonnerie, vibration, tonalite_distincte, silencieux, prioritaire");
    }
}
