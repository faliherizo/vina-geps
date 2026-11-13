package ca.vina.nexu.superadmin.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Administration du système — mot de passe provisoire, dernier admin, courriel d'accès")
class AdministrationSystemeTest {

    @Test
    void leMotDePasseProvisoireFaitHuitCaracteresAuMoins() {
        assertThatThrownBy(() -> AdministrationSysteme.verifierMotDePasseAdmin("court", true)).hasMessage("temporary password must be at least 8 characters");
        assertThatThrownBy(() -> AdministrationSysteme.verifierMotDePasseAdmin("", false)).hasMessage("temporary password must be at least 8 characters");
        assertThatThrownBy(() -> AdministrationSysteme.verifierMotDePasseAdmin(null, false)).hasMessageStartingWith("temporary password");
        assertThatCode(() -> AdministrationSysteme.verifierMotDePasseAdmin("court", false)).doesNotThrowAnyException();
        assertThatCode(() -> AdministrationSysteme.verifierMotDePasseAdmin("huitcar8", true)).doesNotThrowAnyException();
    }

    @Test
    void leDernierAdministrateurActifNeSeDesactivePas() {
        assertThatThrownBy(() -> AdministrationSysteme.verifierDesactivation(1)).hasMessage("dernier administrateur actif : l'établissement deviendrait inadministrable");
        assertThatCode(() -> AdministrationSysteme.verifierDesactivation(2)).doesNotThrowAnyException();
    }

    @Test
    void leCourrielDAccesEstCeluiDuNodeMotPourMot() {
        var creation = new AdministrationSysteme.Acces("marie", "marie", "Tmp-1234!", "CSS des Chênes", false, "https://app.exemple.qc.ca/login");
        assertThat(creation.sujet()).isEqualTo("Votre accès Nexu VINA — CSS des Chênes");
        assertThat(creation.texte()).isEqualTo(String.join("\n", "Bonjour marie,", "", "Un compte vous a été créé.", "", "  établissement : CSS des Chênes",
                "  identifiant : marie", "  mot de passe temporaire : Tmp-1234!", "", "  connexion : https://app.exemple.qc.ca/login", "",
                "À votre première connexion, vous devrez :\n  1. changer votre mot de passe\n  2. configurer une couche de sécurité (question secrète ou MFA)", "",
                "Si vous détenez un accès dans plusieurs établissements, le vôtre est identifié ci-dessus.", "", "Merci."));
        var reinit = new AdministrationSysteme.Acces("admin", "admin", "x", null, true, null);
        assertThat(reinit.sujet()).isEqualTo("Réinitialisation de votre mot de passe — Nexu VINA");
        assertThat(reinit.texte()).contains("Votre mot de passe a été réinitialisé par un administrateur.").contains("À votre prochaine connexion, vous devrez le changer.")
                .doesNotContain("établissement").doesNotContain("connexion :");
        assertThat(AdministrationSysteme.urlConnexion(" https://app.exemple.qc.ca// ")).contains("https://app.exemple.qc.ca/login");
        assertThat(AdministrationSysteme.urlConnexion("  ")).isEmpty();
        assertThat(AdministrationSysteme.urlConnexion(null)).isEmpty();
    }
}
