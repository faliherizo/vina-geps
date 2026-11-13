package ca.vina.nexu.partage.securite;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CSRF double-submit — mêmes exemptions et même refus que le Node")
class FiltreCsrfTest {

    private final FiltreCsrf filtre = new FiltreCsrf();

    private MockHttpServletResponse passer(String methode, String uri, Cookie[] cookies, String enTete) throws Exception {
        MockHttpServletRequest r = new MockHttpServletRequest(methode, uri);
        r.setRequestURI(uri);
        if (cookies != null) {
            r.setCookies(cookies);
        }
        if (enTete != null) {
            r.addHeader("X-CSRF-Token", enTete);
        }
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        MockFilterChain suite = new MockFilterChain();
        filtre.doFilter(r, reponse, suite);
        reponse.setHeader("suite", suite.getRequest() == null ? "arretee" : "poursuivie");
        return reponse;
    }

    private static Cookie[] session(String csrf) {
        return csrf == null ? new Cookie[] {new Cookie("session", "j")} : new Cookie[] {new Cookie("session", "j"), new Cookie("csrf", csrf)};
    }

    @Test
    void lesLecturesEtLesExemptionsPassentSansJeton() throws Exception {
        assertThat(passer("GET", "/api/auth/users", session(null), null).getHeader("suite")).isEqualTo("poursuivie");
        assertThat(passer("POST", "/api/auth/login", session(null), null).getHeader("suite")).isEqualTo("poursuivie");
        assertThat(passer("POST", "/api/auth/logout", session(null), null).getHeader("suite")).isEqualTo("poursuivie");
        assertThat(passer("POST", "/api/oauth/token", session(null), null).getHeader("suite")).isEqualTo("poursuivie");
        assertThat(passer("POST", "/api/auth/_reset", session(null), null).getHeader("suite")).isEqualTo("poursuivie");
        // un formulaire d'IdP de démonstration, posté par le navigateur d'un administrateur connecté
        assertThat(passer("POST", "/mock/mifi/sag/login", session(null), null).getHeader("suite")).isEqualTo("poursuivie");
    }

    @Test
    void sansCookieDeSessionRienNEstExige() throws Exception {
        assertThat(passer("POST", "/api/auth/users", null, null).getHeader("suite")).isEqualTo("poursuivie");
        assertThat(passer("POST", "/api/auth/users", new Cookie[] {new Cookie("autre", "x")}, null).getHeader("suite")).isEqualTo("poursuivie");
    }

    @Test
    void uneMutationParCookieExigeLeJetonQuiCorrespond() throws Exception {
        MockHttpServletResponse refus = passer("POST", "/api/auth/users", session("abc"), null);
        assertThat(refus.getStatus()).isEqualTo(403);
        assertThat(refus.getContentAsString()).isEqualTo("{\"errors\":[\"CSRF token mismatch\"]}");
        assertThat(passer("POST", "/api/auth/users", session(null), "abc").getStatus()).isEqualTo(403);
        assertThat(passer("PATCH", "/api/auth/users/1", session("abc"), "autre").getStatus()).isEqualTo(403);
        assertThat(passer("DELETE", "/api/auth/users/1", session("abc"), "abc").getHeader("suite")).isEqualTo("poursuivie");
    }
}
