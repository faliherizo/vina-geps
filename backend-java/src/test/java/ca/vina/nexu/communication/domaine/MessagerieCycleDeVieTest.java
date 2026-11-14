package ca.vina.nexu.communication.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Messagerie — cycle de vie d'un message : destinataires et acheminement")
class MessagerieCycleDeVieTest {

    private static final String T0 = "2026-09-16T12:00:00.000Z";

    private static Messagerie.Conversation fil(Messagerie.Participation... participants) {
        return new Messagerie.Conversation("c1", "Garde", "groupe", null, "julie", T0, null, List.of(participants));
    }

    private static Messagerie.Message message(Messagerie.Adresse adresse) {
        return new Messagerie.Message("m1", "c1", "julie", "x", "2026-09-16T12:05:00.000Z", null, List.of(), adresse);
    }

    @Test
    @DisplayName("seuls comptent les participants présents à l'envoi, l'auteur excepté ; un message privé n'a qu'un destinataire")
    void destinataires() {
        Messagerie.Conversation c = fil(new Messagerie.Participation("julie", "moderateur", T0, null),
                new Messagerie.Participation("marc", "membre", T0, null),
                new Messagerie.Participation("lea", "membre", "2026-09-16T13:00:00.000Z", null),
                new Messagerie.Participation("chef", "membre", T0, "2026-09-16T12:01:00.000Z"));
        assertThat(Messagerie.destinataires(c, message(Messagerie.Adresse.PUBLIQUE))).containsExactly("marc");
        assertThat(Messagerie.destinataires(c, message(new Messagerie.Adresse("lea", null)))).containsExactly("lea");
    }

    @Test
    @DisplayName("le libellé dit l'étape la plus avancée ; une lecture vaut réception ; un accusé d'un non-destinataire ne compte pas")
    void acheminement() {
        Messagerie.Conversation c = fil(new Messagerie.Participation("julie", "moderateur", T0, null),
                new Messagerie.Participation("marc", "membre", T0, null), new Messagerie.Participation("lea", "membre", T0, null));
        Messagerie.Message m = message(Messagerie.Adresse.PUBLIQUE);
        assertThat(Messagerie.acheminement(c, m, Set.of(), Set.of()).libelle()).isEqualTo("Envoyé");
        assertThat(Messagerie.acheminement(c, m, Set.of("marc", "intrus"), Set.of()).libelle()).isEqualTo("Reçu par 1 sur 2");
        Messagerie.Acheminement lu = Messagerie.acheminement(c, m, Set.of(), Set.of("lea"));
        assertThat(lu).isEqualTo(new Messagerie.Acheminement(2, 1, 1));
        assertThat(lu.libelle()).isEqualTo("Lu par 1 sur 2");
        // Seule dans le fil, l'autrice n'a personne à qui l'envoyer : le message reste « envoyé ».
        Messagerie.Conversation seule = fil(new Messagerie.Participation("julie", "moderateur", T0, null));
        assertThat(Messagerie.acheminement(seule, m, Set.of(), Set.of()).enCarte()).containsEntry("destinataires", 0).containsEntry("libelle", "Envoyé");
    }
}
