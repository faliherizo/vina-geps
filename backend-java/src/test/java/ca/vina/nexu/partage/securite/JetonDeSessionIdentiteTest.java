package ca.vina.nexu.partage.securite;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/** PT-02 : chaque jeton de session porte un identifiant propre et son échéance, que la déconnexion révoque. */
@DisplayName("Jeton de session — identité")
class JetonDeSessionIdentiteTest {

    private static final String SECRET = "secret-d-essai-de-trente-deux-octets-au-moins";
    private final JetonDeSession jetons = new JetonDeSession(SECRET);
    private final JetonDeSession.Revendications julie = new JetonDeSession.Revendications("u-1", "julie", "employee", null, "t-1");

    @Test
    @DisplayName("deux émissions, deux identifiants ; l'échéance est celle de la validité")
    void identifiantEtEcheance() {
        Instant avant = Instant.now();
        JetonDeSession.Lecture a = jetons.lireAvecIdentite(jetons.emettre(julie));
        JetonDeSession.Lecture b = jetons.lireAvecIdentite(jetons.emettre(julie));
        assertThat(a.revendications()).isEqualTo(julie);
        assertThat(a.identite().id()).isNotBlank().isNotEqualTo(b.identite().id());
        assertThat(a.identite().expireLe()).isBetween(avant.plusSeconds(JetonDeSession.VALIDITE_SECONDES - 5), avant.plusSeconds(JetonDeSession.VALIDITE_SECONDES + 5));
    }

    @Test
    @DisplayName("un jeton sans échéance ni identifiant se lit ; un jeton machine ou altéré, non")
    void casLimites() {
        SecretKeySpec cle = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JetonDeSession.Lecture sans = jetons.lireAvecIdentite(Jwts.builder().subject("u-1").claim("role", "admin").signWith(cle).compact());
        assertThat(sans.identite().id()).isNull();
        assertThat(sans.identite().expireLe()).isNull();
        String machine = Jwts.builder().subject("cli").claim("typ", JetonDeSession.TYP_MACHINE).expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(cle).compact();
        assertThat(jetons.lireAvecIdentite(machine)).isNull();
        assertThat(jetons.lireAvecIdentite("pas.un.jeton")).isNull();
        assertThat(jetons.lire("pas.un.jeton")).isNull();
    }

    @Test
    @DisplayName("le port par défaut ne révoque rien : une session révoquée par lui reste valable")
    void aucuneRevocation() {
        SessionsRevoquees.AUCUNE.revoquer("j-1", Instant.now());
        assertThat(SessionsRevoquees.AUCUNE.revoquee("j-1")).isFalse();
    }
}
