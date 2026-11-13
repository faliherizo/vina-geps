package ca.vina.nexu.auth.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Politique de mot de passe — mêmes manquements, mêmes mots que le Node")
class PolitiqueMotDePasseTest {

    @Test
    void unMotDePasseVideCumuleTousLesManquementsDeForme() {
        assertThat(PolitiqueMotDePasse.manquements(null, null)).containsExactly(
                "au moins 12 caractères", "une lettre minuscule", "une lettre majuscule", "un chiffre", "un caractère spécial");
    }

    @Test
    void espaceNomEtMotCourantSontNommes() {
        assertThat(PolitiqueMotDePasse.manquements("Admin Istrateur 1!", "administrateur"))
                .containsExactly("aucun espace");
        assertThat(PolitiqueMotDePasse.manquements("xAlice.Tremblay9!", "alice.tremblay"))
                .containsExactly("ne pas contenir le nom d'utilisateur");
        assertThat(PolitiqueMotDePasse.manquements("Administrateur", "  "))
                .containsExactly("un chiffre", "un caractère spécial", "ne pas être un mot de passe courant");
    }

    @Test
    void unNomDeMoinsDeTroisCaracteresNEstPasRecherche() {
        assertThat(PolitiqueMotDePasse.manquements("Ab-solument9Fort", "ab")).isEmpty();
    }

    @Test
    void exigerLeveAvecLeMessageDuNode() {
        assertThatCode(() -> PolitiqueMotDePasse.exiger("Tres-Solide-2026!", "alice")).doesNotThrowAnyException();
        assertThatThrownBy(() -> PolitiqueMotDePasse.exiger("court", "alice"))
                .isInstanceOf(PolitiqueMotDePasse.MotDePasseFaibleException.class)
                .hasMessage("mot de passe trop faible — il doit contenir : au moins 12 caractères, une lettre majuscule, un chiffre, un caractère spécial")
                .extracting(e -> ((PolitiqueMotDePasse.MotDePasseFaibleException) e).manquements())
                .isEqualTo(List.of("au moins 12 caractères", "une lettre majuscule", "un chiffre", "un caractère spécial"));
    }

    @Test
    void unMotDePasseProvisoireEstFortEtSansCaracteresAmbigus() {
        java.util.Random alea = new java.util.Random(7);
        String p = PolitiqueMotDePasse.motDePasseProvisoire(16, alea);
        assertThat(p).hasSize(16).doesNotContainPattern("[lI1O0]");
        assertThat(PolitiqueMotDePasse.manquements(p, "admin")).isEmpty();
        assertThat(PolitiqueMotDePasse.motDePasseProvisoire(4, alea)).hasSize(PolitiqueMotDePasse.LONGUEUR_MINIMALE);
        assertThat(PolitiqueMotDePasse.motDePasseProvisoire(16, new java.util.Random(7))).isEqualTo(p);   // l'aléa injecté rend le tirage rejouable
    }
}
