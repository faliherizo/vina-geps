package ca.vina.nexu.partage.securite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FiltreTenantEssai — l'établissement « default » des essais, là où le Node l'injecte")
class FiltreTenantEssaiTest {

    private static HttpServletRequest passer(String chemin, String enTete) throws Exception {
        return passer(chemin, enTete, true);
    }

    private static HttpServletRequest passer(String chemin, String enTete, boolean essai) throws Exception {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", chemin);
        r.setRequestURI(chemin);
        if (enTete != null) {
            r.addHeader("X-Tenant-Id", enTete);
        }
        MockFilterChain chaine = new MockFilterChain();
        new FiltreTenantEssai(essai).doFilter(r, new MockHttpServletResponse(), chaine);
        return (HttpServletRequest) chaine.getRequest();
    }

    @Test
    void injecteSurPaieEtIaSeulement_saufLeFluxEtSaufEnTetePresent() throws Exception {
        HttpServletRequest paie = passer("/api/payroll/employees", null);
        assertThat(paie.getHeader("x-tenant-id")).isEqualTo("default");
        assertThat(Collections.list(paie.getHeaders("X-Tenant-Id"))).containsExactly("default");
        assertThat(Collections.list(paie.getHeaderNames())).contains("X-Tenant-Id");
        assertThat(paie.getHeader("Accept")).isNull();
        assertThat(passer("/api/ai/verify-accounting", " ").getHeader("X-Tenant-Id")).isEqualTo("default");
        assertThat(passer("/api/payroll/employees", "t-1").getHeader("X-Tenant-Id")).isEqualTo("t-1");
        assertThat(passer("/api/payroll/events", null).getHeader("X-Tenant-Id")).isNull();
        assertThat(passer("/api/saas/tenants", null).getHeader("X-Tenant-Id")).isNull();
        // hors essai, le filtre ne touche à rien : la production exige l'en-tête
        assertThat(passer("/api/payroll/employees", null, false).getHeader("X-Tenant-Id")).isNull();
    }
}
