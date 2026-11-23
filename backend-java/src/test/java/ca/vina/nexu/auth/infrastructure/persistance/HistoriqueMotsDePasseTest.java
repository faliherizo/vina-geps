package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.auth.domaine.HistoriqueMotsDePasse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ST-G10 : les 10 dernières empreintes ; un mot de passe des 6 derniers mois ne revient pas")
class HistoriqueMotsDePasseTest {

    private static final class Horloge extends Clock {
        private Instant maintenant = Instant.parse("2026-01-10T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return maintenant;
        }
    }

    @Test
    @DisplayName("la règle : parmi les 10 plus récentes, retirées depuis moins de 6 mois")
    void regle() {
        Instant maintenant = Instant.parse("2026-07-10T00:00:00Z");
        List<HistoriqueMotsDePasse.Empreinte> h = List.of(new HistoriqueMotsDePasse.Empreinte("courant", maintenant),
                new HistoriqueMotsDePasse.Empreinte("ancien", Instant.parse("2026-01-09T00:00:00Z")));
        assertThat(HistoriqueMotsDePasse.reemploye(h, "courant"::equals, maintenant)).isTrue();
        assertThat(HistoriqueMotsDePasse.reemploye(h, "ancien"::equals, maintenant)).isFalse();
        assertThat(HistoriqueMotsDePasse.reemploye(h, "inconnu"::equals, maintenant)).isFalse();
    }

    @Test
    @DisplayName("réemploi refusé ; onze changements n'en gardent que dix ; six mois plus tard, l'ancien revient")
    void parLeDepot(@TempDir Path dossier) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        Horloge horloge = new Horloge();
        DepotComptes comptes = new DepotComptes(jdbc, horloge);
        String id = comptes.creer(new DepotComptes.Creation("julie", "Depart!2026", "employee", null, "T", false, false, null, false)).id();
        DepotComptes.Modification m = new DepotComptes.Modification(null, null, "Depart!2026", null, null, null, null);
        assertThatThrownBy(() -> comptes.modifier(id, m)).isInstanceOf(IllegalArgumentException.class).hasMessage(HistoriqueMotsDePasse.REFUS);
        for (int i = 1; i <= 11; i++) {
            horloge.maintenant = horloge.maintenant.plusSeconds(60);
            comptes.modifier(id, new DepotComptes.Modification(null, null, "Suivant!" + i, null, null, null, null));
        }
        assertThat(jdbc.sql("SELECT COUNT(*) FROM users_mots_de_passe WHERE userId = ?").param(id).query(Integer.class).single()).isEqualTo(10);
        assertThatThrownBy(() -> comptes.modifier(id, new DepotComptes.Modification(null, null, "Suivant!5", null, null, null, null)))
                .hasMessage(HistoriqueMotsDePasse.REFUS);
        // « Depart!2026 » est sorti des dix dernières : il n'est plus retenu, quel que soit son âge.
        comptes.modifier(id, new DepotComptes.Modification(null, null, "Depart!2026", null, null, null, null));
        horloge.maintenant = horloge.maintenant.plusSeconds(200L * 24 * 3600);
        comptes.modifier(id, new DepotComptes.Modification(null, null, "Suivant!9", null, null, null, null));
        assertThat(comptes.verifierMotDePasse(comptes.parId(id).orElseThrow(), "Suivant!9")).isTrue();
    }
}
