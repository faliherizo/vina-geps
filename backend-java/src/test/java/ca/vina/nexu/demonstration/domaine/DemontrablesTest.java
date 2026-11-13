package ca.vina.nexu.demonstration.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Démonstrations — codes, possibilité, origine de l'application, variable de démarrage")
class DemontrablesTest {

    @Test
    void possibilite() {
        assertThat(Demontrables.exiger("sms")).isEqualTo("sms");
        assertThatThrownBy(() -> Demontrables.exiger("fax")).hasMessage("intégration introuvable : fax");
        assertThat(Demontrables.peutBrancher("entra", Map.of())).isEqualTo(new Demontrables.Possibilite(true, "aucune configuration réelle : la démonstration peut être branchée"));
        assertThat(Demontrables.peutBrancher("entra", Map.of("AZURE_CLIENT_ID", "x")).motif()).isEqualTo("configuration réelle présente (AZURE_CLIENT_ID)");
        assertThat(Demontrables.peutBrancher("saml", Map.of("SAML_IDP_SSO_URL", "u", "SAML_IDP_CERT", "c")).motif()).isEqualTo("configuration réelle présente (SAML_IDP_SSO_URL, SAML_IDP_CERT)");
        assertThat(Demontrables.peutBrancher("sms", Map.of("APPEL_URL", "u", "SMS_FOURNISSEUR", "")).motif()).isEqualTo("configuration réelle présente (APPEL_URL)");
        assertThat(Demontrables.peutBrancher("antivirus", Map.of("CLAMD_HOST", "h")).possible()).isFalse();
        assertThat(Demontrables.peutBrancher("api", Map.of("AZURE_CLIENT_ID", "x"))).isEqualTo(new Demontrables.Possibilite(true, "données du locataire : la démonstration peut être branchée"));
        assertThat(Demontrables.variables()).contains("DEMONSTRATION_INTEGRATIONS", "MOCK_CLAMAV_PORT");
    }

    @Test
    void origine() {
        assertThat(Demontrables.origineApplication("http://localhost:5173/")).isEqualTo("http://localhost:5173");
        assertThat(Demontrables.origineApplication("https://app.exemple.qc.ca/admin/")).isEqualTo("https://app.exemple.qc.ca/admin");
        assertThat(Demontrables.origineApplication("https://app.exemple.qc.ca")).isEqualTo("https://app.exemple.qc.ca");
        assertThatThrownBy(() -> Demontrables.origineApplication("pas une url")).hasMessage("urlApplication illisible : « pas une url »");
        assertThatThrownBy(() -> Demontrables.origineApplication("ftp://x.ca")).hasMessage("urlApplication : http ou https seulement");
        assertThatThrownBy(() -> Demontrables.origineApplication("http://x.ca/autre")).hasMessage("urlApplication : l'origine seule, ou son préfixe /admin");
        assertThatThrownBy(() -> Demontrables.origineApplication("chemin/relatif")).hasMessage("urlApplication illisible : « chemin/relatif »");
    }

    @Test
    void demarrage() {
        assertThat(Demontrables.lireDemonstrationsEnv(Map.of())).isEmpty();
        assertThat(Demontrables.lireDemonstrationsEnv(Map.of("DEMONSTRATION_INTEGRATIONS", " entra, sms ,"))).containsExactly("entra", "sms");
        assertThatThrownBy(() -> Demontrables.lireDemonstrationsEnv(Map.of("DEMONSTRATION_INTEGRATIONS", "api"))).hasMessage("DEMONSTRATION_INTEGRATIONS : « api » n'est pas un port du processus (admis : entra, saml, sms, antivirus)");
        assertThatThrownBy(() -> Demontrables.lireDemonstrationsEnv(Map.of("DEMONSTRATION_INTEGRATIONS", "entra", "AZURE_CLIENT_ID", "x"))).hasMessage("DEMONSTRATION_INTEGRATIONS : entra — configuration réelle présente (AZURE_CLIENT_ID)");
    }
}
