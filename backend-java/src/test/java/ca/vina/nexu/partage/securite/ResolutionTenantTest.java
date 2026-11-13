package ca.vina.nexu.partage.securite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Résolution de l'établissement — l'invariante d'isolation du produit.
 *
 * <p>Ces huit cas décident si un établissement peut lire les données d'un autre. Le plus
 * important est le troisième : un utilisateur rattaché qui envoie un en-tête divergent est
 * <strong>refusé</strong>, jamais servi selon son en-tête.
 */
@DisplayName("Résolution de l'établissement — isolation multi-tenant")
class ResolutionTenantTest {

    private static ContexteRequete rattache(String tenantId) {
        return new ContexteRequete("usr-1", "admin@@t1", "admin", "emp-1", tenantId);
    }

    private static ContexteRequete global(String role) {
        return new ContexteRequete("usr-0", role, role, null, null);
    }

    @Test
    @DisplayName("utilisateur rattaché, sans en-tête : son établissement, tiré du jeton")
    void rattacheSansEntete() {
        assertThat(ResolutionTenant.resoudre(rattache("t-001"), null))
                .isEqualTo(new ResolutionTenant.Resultat.Retenu("t-001"));
    }

    @Test
    @DisplayName("utilisateur rattaché, en-tête concordant : le jeton fait quand même autorité")
    void rattacheEnteteConcordant() {
        assertThat(ResolutionTenant.resoudre(rattache("t-001"), "t-001"))
                .isEqualTo(new ResolutionTenant.Resultat.Retenu("t-001"));
    }

    @Test
    @DisplayName("utilisateur rattaché, en-tête divergent : refus")
    void rattacheEnteteDivergent() {
        // Le cœur de l'isolation : un client ne choisit pas l'établissement dont il lit les
        // données. Servir l'en-tête ici ouvrirait tous les établissements à qui sait en écrire un.
        assertThat(ResolutionTenant.resoudre(rattache("t-001"), "t-002"))
                .isInstanceOf(ResolutionTenant.Resultat.Discordance.class);
    }

    @Test
    @DisplayName("un en-tête vide ou en blancs ne vaut pas divergence")
    void enteteVide() {
        assertThat(ResolutionTenant.resoudre(rattache("t-001"), ""))
                .isEqualTo(new ResolutionTenant.Resultat.Retenu("t-001"));
        assertThat(ResolutionTenant.resoudre(rattache("t-001"), "   "))
                .isEqualTo(new ResolutionTenant.Resultat.Retenu("t-001"));
    }

    @Test
    @DisplayName("compte global : il doit désigner un établissement")
    void globalDoitChoisir() {
        // Son jeton n'en porte pas : sans en-tête, il n'y a pas de réponse sûre.
        assertThat(ResolutionTenant.resoudre(global("superadmin"), null))
                .isInstanceOf(ResolutionTenant.Resultat.Manquant.class);
        assertThat(ResolutionTenant.resoudre(global("superadmin"), "t-009"))
                .isEqualTo(new ResolutionTenant.Resultat.Retenu("t-009"));
    }

    @Test
    @DisplayName("sans session : non authentifié, et non « établissement manquant »")
    void sansSession() {
        // Distinguer les deux compte : 401 dit « identifiez-vous », 400 dit « précisez ».
        assertThat(ResolutionTenant.resoudre(null, "t-001"))
                .isInstanceOf(ResolutionTenant.Resultat.NonAuthentifie.class);
    }

    @Test
    @DisplayName("l'en-tête est comparé après rognage des blancs")
    void enteteRogne() {
        assertThat(ResolutionTenant.resoudre(rattache("t-001"), "  t-001  "))
                .isEqualTo(new ResolutionTenant.Resultat.Retenu("t-001"));
    }

    @Test
    @DisplayName("le contexte sait reconnaître un superadmin")
    void reconnaitSuperadmin() {
        assertThat(global("superadmin").estSuperadmin()).isTrue();
        assertThat(rattache("t-001").estSuperadmin()).isFalse();
    }


    @Test
    @DisplayName("un tenant vide dans le jeton n'est pas un tenant")
    void tenantVideDansLeJeton() {
        // Une chaîne vide en base se lit comme un rattachement : sans ce test, un compte au
        // tenantId vide verrait tous les établissements via l'en-tête.
        assertThat(ResolutionTenant.resoudre(rattache(""), "t-002"))
                .isEqualTo(new ResolutionTenant.Resultat.Retenu("t-002"));
        assertThat(ResolutionTenant.resoudre(rattache(""), null))
                .isInstanceOf(ResolutionTenant.Resultat.Manquant.class);
    }

    @Test
    @DisplayName("un en-tête réduit à des espaces vaut un en-tête absent")
    void enteteEnEspaces() {
        assertThat(ResolutionTenant.resoudre(global("superadmin"), "   "))
                .isInstanceOf(ResolutionTenant.Resultat.Manquant.class);
    }
}
