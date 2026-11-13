package ca.vina.nexu.partage.journal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Le masquage (ADR 0041), port de {@code journalisation.test.ts} : mêmes règles, mêmes résultats que le Node. */
@DisplayName("Journal — masquage des chemins, des personnes, niveaux et libellés")
class MasquageTest {

    @Test
    @DisplayName("un jeton ne se journalise jamais : segment long et opaque → {jeton}, dernier segment des liens publics → {jeton}, UUID gardé, noms des paramètres seulement")
    void chemins() {
        assertThat(Masquage.masquerChemin("/public/scolaire/disponibilites/abc", null)).isEqualTo("/public/scolaire/disponibilites/{jeton}");
        assertThat(Masquage.masquerChemin("/sign/tRfL9w-3kQ0mZpXyV2bN8cH7jD5gS1aE", null)).isEqualTo("/sign/{jeton}");
        assertThat(Masquage.masquerChemin("/api/contracts/tRfL9w-3kQ0mZpXyV2bN8cH7jD5gS1aE/sign", null)).isEqualTo("/api/contracts/{jeton}/sign");
        assertThat(Masquage.masquerChemin("/api/scolaire/absences/3f2c9a10-1b2c-4d5e-8f90-1234567890ab/approuver", null)).isEqualTo("/api/scolaire/absences/3f2c9a10-1b2c-4d5e-8f90-1234567890ab/approuver");
        assertThat(Masquage.masquerChemin("/api/auth/audit", "q=Dupont&page=2&q=x&jeton=abc&=vide")).isEqualTo("/api/auth/audit?q&page&jeton");
        assertThat(Masquage.masquerChemin("/api/health", "")).isEqualTo("/api/health");
        assertThat(Masquage.masquerChemin("/mock/idp/sso", null)).isEqualTo("/mock/idp/{jeton}");
        assertThat(Masquage.masquerChemin("/public", null)).isEqualTo("/public");
    }

    @Test
    void personnes() {
        assertThat(Masquage.masquer("nour@exemple.qc.ca")).isEqualTo("n***@exemple.qc.ca");
        assertThat(Masquage.masquer("418 555-0142")).isEqualTo("***0142");
        assertThat(Masquage.masquer("ab")).isEqualTo("***");
        assertThat(Masquage.masquer("Léa")).isEqualTo("Lé***");
        assertThat(Masquage.masquer(" ")).isEqualTo("—");
        assertThat(Masquage.masquer(null)).isEqualTo("—");
    }

    @Test
    void niveauxEtLibelles() {
        assertThat(Masquage.niveauDeLaRequete("GET", 200)).isEqualTo(Niveau.DEBUG);
        assertThat(Masquage.niveauDeLaRequete("POST", 201)).isEqualTo(Niveau.INFO);
        assertThat(Masquage.niveauDeLaRequete("GET", 404)).isEqualTo(Niveau.INFO);
        assertThat(Masquage.niveauDeLaRequete("DELETE", 500)).isEqualTo(Niveau.ERROR);
        assertThat(Masquage.estMutante("PATCH")).isTrue();
        assertThat(Masquage.estMutante("HEAD")).isFalse();
        assertThat(java.util.stream.IntStream.of(200, 400, 401, 403, 404, 409, 413, 429, 422, 500).mapToObj(Masquage::libelleDuStatut).toList())
                .containsExactly(null, "validation", "non authentifie", "refuse", "introuvable", "conflit", "trop volumineux", "trop de requetes", "validation", null);
        assertThat(Niveau.lu(" WARN ")).isEqualTo(Niveau.WARN);
        assertThat(Niveau.lu(null)).isEqualTo(Niveau.INFO);
        assertThat(Niveau.de("x")).isEmpty();
        assertThatThrownBy(() -> Niveau.lu("verbose")).hasMessage("LOG_NIVEAU « verbose » inconnu : debug, info, warn, error");
    }
}
