package ca.vina.nexu.integrations.infrastructure.persistance;

import ca.vina.nexu.partage.securite.JetonDeSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Jeton d'accès machine — et sa frontière avec le jeton de session.
 *
 * <p>L'invariant tenu ici est une frontière de sécurité : un jeton machine ne doit jamais valoir
 * session humaine, et réciproquement. C'est la revendication {@code typ} qui l'assure, et un test
 * vaut mieux qu'une convention — les deux jetons sont signés avec le même secret, donc rien dans
 * la signature ne les distingue.
 */
@DisplayName("Jeton d'accès machine (client_credentials)")
class JetonClientTest {

    private static final String SECRET = "secret-de-test-parite-jwt-suffisamment-long-1234";

    private final JetonClient jetons = new JetonClient(SECRET);

    @Test
    @DisplayName("émet un jeton relisible, porteur du tenant et des portées")
    void allerRetour() {
        String jeton = jetons.emettre("cid_abc", "t-001", List.of("read", "write"));

        JetonClient.Revendications r = jetons.lireRevendications(jeton);
        assertThat(r).isNotNull();
        assertThat(r.sub()).isEqualTo("cid_abc");
        assertThat(r.tenantId()).isEqualTo("t-001");
        assertThat(r.scopes()).containsExactly("read", "write");
    }

    @Test
    @DisplayName("une seule portée, ou aucune, se relit sans surprise")
    void porteesDegenerees() {
        assertThat(jetons.lireRevendications(jetons.emettre("cid", "t", List.of("read"))).scopes())
                .containsExactly("read");
        assertThat(jetons.lireRevendications(jetons.emettre("cid", "t", List.of())).scopes()).isEmpty();
    }

    @Test
    @DisplayName("un jeton de session n'est pas accepté comme jeton machine")
    void unJetonDeSessionNestPasMachine() {
        // Même secret, même algorithme : seule la revendication `typ` sépare une intégration
        // d'un utilisateur. Sans cette vérification, un jeton de session ouvrirait l'API machine
        // — et un jeton machine, l'application.
        String session = new JetonDeSession(SECRET).emettre(new JetonDeSession.Revendications(
                "usr-1", "admin@@t-001", "admin", null, "t-001"));

        assertThat(jetons.lireRevendications(session)).isNull();
    }

    @Test
    @DisplayName("le port machine : organisme et portées converties, portée inconnue ignorée ; session ou jeton illisible : vide")
    void portMachine() {
        var a = jetons.lire(jetons.emettre("cli-rao", "t-1", List.of("read", "admin", "write"))).orElseThrow();
        assertThat(a.clientId()).isEqualTo("cli-rao");
        assertThat(a.tenantId()).isEqualTo("t-1");
        assertThat(a.portees()).containsExactlyInAnyOrder(ca.vina.nexu.partage.securite.JetonsMachine.Portee.LECTURE,
                ca.vina.nexu.partage.securite.JetonsMachine.Portee.ECRITURE);
        assertThat(a.peut(ca.vina.nexu.partage.securite.JetonsMachine.Portee.LECTURE)).isTrue();
        assertThat(jetons.lire(jetons.emettre("c", "t", List.of())).orElseThrow().peut(ca.vina.nexu.partage.securite.JetonsMachine.Portee.ECRITURE)).isFalse();
        String session = new JetonDeSession(SECRET).emettre(new JetonDeSession.Revendications("u", "julie", "admin", null, "t-1"));
        assertThat(jetons.lire(session)).isEmpty();
        assertThat(jetons.lire("pas-un-jeton")).isEmpty();
    }

    @Test
    @DisplayName("un jeton machine n'ouvre pas de session : même clé, mais typ « client » refusé par la lecture de session (AD-06)")
    void unJetonMachineNestPasUneSession() {
        String machine = jetons.emettre("cli-rao", "t-1", List.of("read", "write"));
        assertThat(jetons.lireRevendications(machine)).isNotNull();
        assertThat(new JetonDeSession(SECRET).lire(machine)).isNull();
    }

    @Test
    @DisplayName("un jeton altéré ou signé d'un autre secret est refusé")
    void jetonInvalide() {
        String bon = jetons.emettre("cid", "t-001", List.of("read"));
        String[] parties = bon.split("\\.");
        String charge = new String(Base64.getUrlDecoder().decode(parties[1]));
        String falsifiee = Base64.getUrlEncoder().withoutPadding().encodeToString(
                charge.replace("\"scope\":\"read\"", "\"scope\":\"write\"")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(jetons.lireRevendications(parties[0] + "." + falsifiee + "." + parties[2]))
                .as("une élévation de portée par réécriture doit être refusée").isNull();
        assertThat(new JetonClient("un-tout-autre-secret-lui-aussi-assez-long-1234567").lireRevendications(bon)).isNull();
        assertThat(jetons.lireRevendications("pas-un-jeton")).isNull();
    }

    @Test
    @DisplayName("refuse de démarrer sur un secret trop court pour HS256")
    void secretTropCourt() {
        // Même exigence que le jeton de session : durcir un seul des deux laisserait une porte
        // signée avec 200 bits.
        assertThatThrownBy(() -> new JetonClient("dev-only-secret-change-me"))
                .isInstanceOf(JetonDeSession.SecretTropCourtException.class);
    }

    @Test
    @DisplayName("la validité est d'une heure, et non de huit comme une session")
    void validiteDUneHeure() {
        assertThat(JetonClient.VALIDITE_SECONDES).isEqualTo(3600);
        assertThat(JetonClient.VALIDITE_SECONDES).isLessThan(JetonDeSession.VALIDITE_SECONDES);
    }
}
