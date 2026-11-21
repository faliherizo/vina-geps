package ca.vina.nexu.partage.web;

import ca.vina.nexu.partage.securite.JetonsMachine;
import ca.vina.nexu.partage.securite.JetonsMachine.Application;
import ca.vina.nexu.partage.securite.JetonsMachine.Portee;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 26-015-DO, addenda 5, AD-06 : seule une application de l'organisme, avec la portée voulue, franchit la garde machine. */
@DisplayName("Garde des routes machine : jeton client_credentials, organisme, portée ; refus par défaut")
class GardeMachineTest {

    private static final Map<String, Application> JETONS = Map.of(
            "lecteur", new Application("cli-rao", "t-1", Set.of(Portee.LECTURE)),
            "sans-organisme", new Application("cli-x", " ", Set.of(Portee.LECTURE)),
            "nul", new Application("cli-y", null, Set.of(Portee.LECTURE)));
    private static final JetonsMachine PORT = jeton -> Optional.ofNullable(JETONS.get(jeton));
    private final GardeMachine garde = new GardeMachine(PORT);

    private static MockHttpServletRequest requete(String autorisation) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        if (autorisation != null) {
            r.addHeader("Authorization", autorisation);
        }
        return r;
    }

    @Test
    @DisplayName("jeton lu et portée détenue : l'organisme du verdict est celui du jeton")
    void admis() {
        GardeAcces.Verdict v = garde.exiger(requete("Bearer lecteur "), Portee.LECTURE);
        assertThat(v.refuse()).isFalse();
        assertThat(v.tenantId()).isEqualTo("t-1");
    }

    @Test
    @DisplayName("sans jeton, schéma autre que Bearer, jeton illisible ou de session : 401")
    void nonAuthentifie() {
        for (String autorisation : new String[] {null, "Basic abc", "Bearer inconnu"}) {
            GardeAcces.Verdict v = garde.exiger(requete(autorisation), Portee.LECTURE);
            assertThat(v.refus().getStatusCode().value()).isEqualTo(401);
            assertThat(v.refus().getBody()).isEqualTo(Map.of("errors", java.util.List.of("jeton d'application (client_credentials) exigé")));
        }
    }

    @Test
    @DisplayName("portée absente ou jeton sans organisme : 403 nommé")
    void interdit() {
        assertThat(garde.exiger(requete("Bearer lecteur"), Portee.ECRITURE).refus().getBody())
                .isEqualTo(Map.of("errors", java.util.List.of("portée « write » exigée")));
        for (String jeton : new String[] {"sans-organisme", "nul"}) {
            GardeAcces.Verdict v = garde.exiger(requete("Bearer " + jeton), Portee.LECTURE);
            assertThat(v.refus().getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    @DisplayName("les portées : codes OAuth2, inconnue ignorée")
    void portees() {
        assertThat(Portee.de("read")).contains(Portee.LECTURE);
        assertThat(Portee.de("write")).contains(Portee.ECRITURE);
        assertThat(Portee.de("admin")).isEmpty();
        assertThat(Portee.ECRITURE.code()).isEqualTo("write");
    }
}
