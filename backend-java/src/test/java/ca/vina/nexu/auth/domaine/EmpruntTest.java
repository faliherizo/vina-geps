package ca.vina.nexu.auth.domaine;

import ca.vina.nexu.partage.securite.SessionsEmpruntees;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** L'emprunt d'identité — les règles pures (CSSMB 25) : qui emprunte qui, pour combien de temps, et ce qui reste interdit. */
@DisplayName("Emprunt d'identité — règles pures")
class EmpruntTest {

    private static final Emprunt.Emprunteur ADMIN = new Emprunt.Emprunteur("u-admin", "admin", "t-1", null);
    private static final Emprunt.Cible CIBLE = new Emprunt.Cible("u-emp", "employee", "t-1", true);

    @Test
    void permisEtRefusNommes() {
        assertThat(Emprunt.refus(ADMIN, CIBLE, true)).isEmpty();
        assertThat(Emprunt.refus(new Emprunt.Emprunteur("sa", "superadmin", null, null), CIBLE, true)).isEmpty();
        assertThat(Emprunt.refus(ADMIN, new Emprunt.Cible("u-dir", "director", "t-1", true), true)).isEmpty();
        assertThat(Emprunt.refus(new Emprunt.Emprunteur("u-rh", "rh", "t-1", null), CIBLE, true)).contains("réservé à l'administration de l'organisme");
        assertThat(Emprunt.refus(new Emprunt.Emprunteur("u-rh", "invente", "t-1", null), CIBLE, true)).contains("réservé à l'administration de l'organisme");
        assertThat(Emprunt.refus(new Emprunt.Emprunteur("u-admin", "admin", "t-1", "u-0"), CIBLE, true)).contains("une session empruntée n'emprunte pas à son tour");
        assertThat(Emprunt.refus(ADMIN, CIBLE, false)).get().asString().contains("n'est pas autorisé pour cet organisme");
        assertThat(Emprunt.refus(ADMIN, null, true)).contains("compte introuvable");
        assertThat(Emprunt.refus(ADMIN, new Emprunt.Cible("u-admin", "employee", "t-1", true), true)).contains("on n'emprunte pas sa propre identité");
        assertThat(Emprunt.refus(ADMIN, new Emprunt.Cible("u-2", "admin", "t-1", true), true)).contains("un compte d'administration ne s'emprunte pas");
        assertThat(Emprunt.refus(ADMIN, new Emprunt.Cible("u-2", "superadmin", "t-1", true), true)).contains("un compte d'administration ne s'emprunte pas");
        assertThat(Emprunt.refus(ADMIN, new Emprunt.Cible("u-2", "employee", "t-2", true), true)).contains("compte hors de votre organisme");
        assertThat(Emprunt.refus(ADMIN, new Emprunt.Cible("u-2", "employee", "t-1", false), true)).contains("compte désactivé");
    }

    @Test
    void dureeMotifEtInterdits() {
        assertThat(Emprunt.DUREE_MINUTES).isEqualTo(60);
        assertThat(Emprunt.expiration("2026-09-17T10:00:00.000Z", 60)).isEqualTo("2026-09-17T11:00:00.000Z");
        assertThat(Emprunt.expiration("2026-09-17T10:00:00.000Z", 5)).isEqualTo("2026-09-17T10:05:00.000Z");
        assertThat(Emprunt.refusDuMotif("reproduire")).isEmpty();
        assertThat(Emprunt.refusDuMotif(" x ")).isPresent();
        assertThat(Emprunt.refusDuMotif(null)).isPresent();
        assertThat(Emprunt.refusDuMotif(12)).isPresent();
        assertThat(SessionsEmpruntees.interdit("POST", "/api/auth/change-password")).isTrue();
        assertThat(SessionsEmpruntees.interdit("post", "/api/auth/security/setup")).isTrue();
        assertThat(SessionsEmpruntees.interdit("POST", "/api/auth/security/totp/init")).isTrue();
        assertThat(SessionsEmpruntees.interdit("DELETE", "/api/auth/webauthn/credentials/c-1")).isTrue();
        assertThat(SessionsEmpruntees.interdit("POST", "/api/auth/emprunt")).isTrue();
        assertThat(SessionsEmpruntees.interdit("POST", "/api/auth/users/u-1/reset-security")).isTrue();
        assertThat(SessionsEmpruntees.interdit("GET", "/api/auth/security/status")).isFalse();
        assertThat(SessionsEmpruntees.interdit("POST", "/api/auth/emprunt/terminer")).isFalse();
        assertThat(SessionsEmpruntees.interdit("POST", "/api/scolaire/absences")).isFalse();
        assertThat(SessionsEmpruntees.AUCUNE.active("x")).isFalse();
    }

    @Test
    @DisplayName("sous emprunt, les portes de première connexion de la cible restent fermées — ce sont ses secrets")
    void portesDePremiereConnexion() {
        java.util.Map<String, Object> vue = Emprunt.sansPortesDePremiereConnexion(java.util.Map.of("id", "u", "mustChangePassword", true, "securitySetupRequired", true));
        assertThat(vue).containsEntry("id", "u").containsEntry("mustChangePassword", false).containsEntry("securitySetupRequired", false);
    }
}
