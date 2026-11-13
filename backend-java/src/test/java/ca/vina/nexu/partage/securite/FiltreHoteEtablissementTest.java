package ca.vina.nexu.partage.securite;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Filtres de l'hôte (ADR 0033) — l'établissement de l'hôte vaut en-tête ; un jeton d'un autre établissement est refusé")
class FiltreHoteEtablissementTest {

    private static final String SECRET = "un-secret-assez-long-pour-les-essais-1234567890";

    /** Un déploiement par sous-domaine où seul cssdc. est un établissement. */
    static final HoteEtablissement SOUS_DOMAINE = new HoteEtablissement() {
        private final ResolutionHote.Configuration c = ResolutionHote.Configuration.depuis("sous_domaine", "nexus.example", null);

        @Override
        public ResolutionHote.Configuration configuration() {
            return c;
        }

        @Override
        public Resolution de(HttpServletRequest requete) {
            return switch (ResolutionHote.genreDe(ResolutionHote.hoteDe(requete), c)) {
                case ResolutionHote.Genre.Etablissement e -> "cssdc".equals(e.slug()) ? new Resolution.Etablissement("T1", "CSSDC", "cssdc") : new Resolution.Inconnu(e.slug());
                case ResolutionHote.Genre.Racine r -> new Resolution.Global();
                case ResolutionHote.Genre.Reserve r -> new Resolution.Global();
                case ResolutionHote.Genre.HorsDomaine h -> new Resolution.HorsDomaine(h.hote());
                case ResolutionHote.Genre.Selecteur s -> new Resolution.Selecteur();
            };
        }
    };

    private static MockHttpServletRequest requete(String hote, String enTete) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/api/payroll/employees");
        r.addHeader("Host", hote);
        if (enTete != null) {
            r.addHeader("X-Tenant-Id", enTete);
        }
        return r;
    }

    private static HttpServletRequest passerHote(HoteEtablissement hote, String hoteHttp, String enTete) throws Exception {
        MockFilterChain chaine = new MockFilterChain();
        new FiltreHoteEtablissement(hote).doFilter(requete(hoteHttp, enTete), new MockHttpServletResponse(), chaine);
        return (HttpServletRequest) chaine.getRequest();
    }

    @Test
    @DisplayName("l'en-tête X-Tenant-Id est posé depuis l'hôte quand il manque ; jamais écrasé, jamais hors établissement")
    void enTeteDeLHote() throws Exception {
        HttpServletRequest r = passerHote(SOUS_DOMAINE, "cssdc.nexus.example", null);
        assertThat(r.getHeader("x-tenant-id")).isEqualTo("T1");
        assertThat(Collections.list(r.getHeaders("X-Tenant-Id"))).containsExactly("T1");
        assertThat(Collections.list(r.getHeaderNames())).contains("X-Tenant-Id");
        assertThat(r.getHeader("Host")).isEqualTo("cssdc.nexus.example");
        assertThat(passerHote(SOUS_DOMAINE, "cssdc.nexus.example", "T9").getHeader("X-Tenant-Id")).isEqualTo("T9");
        assertThat(passerHote(SOUS_DOMAINE, "cssdc.nexus.example", " ").getHeader("X-Tenant-Id")).isEqualTo("T1");
        assertThat(passerHote(SOUS_DOMAINE, "admin.nexus.example", null).getHeader("X-Tenant-Id")).isNull();
        assertThat(passerHote(SOUS_DOMAINE, "inconnu.nexus.example", null).getHeader("X-Tenant-Id")).isNull();
        assertThat(passerHote(HoteEtablissement.selecteur(), "cssdc.nexus.example", null).getHeader("X-Tenant-Id")).isNull();
        assertThat(HoteEtablissement.selecteur().configuration().mode()).isEqualTo(ModeResolution.SELECTEUR);
    }

    private static MockHttpServletResponse passerSession(HoteEtablissement hote, String hoteHttp, String tenantDuJeton) throws Exception {
        String jeton = new JetonDeSession(SECRET).emettre(new JetonDeSession.Revendications("u-1", "u", "admin", null, tenantDuJeton));
        MockHttpServletRequest r = requete(hoteHttp, null);
        r.addHeader("Authorization", "Bearer " + jeton);
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        MockFilterChain chaine = new MockFilterChain();
        new FiltreSession(SECRET, hote).doFilter(r, reponse, chaine);
        if (chaine.getRequest() != null) {
            assertThat(((HttpServletRequest) chaine.getRequest()).getAttribute(ContexteRequete.ATTRIBUT)).isNotNull();
        }
        return reponse;
    }

    @Test
    @DisplayName("un jeton d'établissement ne vaut que sur son hôte : 403 ailleurs ; un compte global passe partout")
    void jetonEtHote() throws Exception {
        assertThat(passerSession(SOUS_DOMAINE, "cssdc.nexus.example", "T1").getStatus()).isEqualTo(200);
        MockHttpServletResponse refus = passerSession(SOUS_DOMAINE, "cssdc.nexus.example", "T2");
        assertThat(refus.getStatus()).isEqualTo(403);
        assertThat(refus.getContentAsString()).contains("tenant mismatch");
        assertThat(passerSession(SOUS_DOMAINE, "admin.nexus.example", "T2").getStatus()).isEqualTo(200);
        assertThat(passerSession(SOUS_DOMAINE, "cssdc.nexus.example", null).getStatus()).isEqualTo(200);
        assertThat(passerSession(HoteEtablissement.selecteur(), "cssdc.nexus.example", "T2").getStatus()).isEqualTo(200);
        // Sans jeton, rien à comparer : la requête passe, sans contexte ; un jeton illisible ou un autre témoin, de même.
        MockFilterChain chaine = new MockFilterChain();
        new FiltreSession(SECRET, SOUS_DOMAINE).doFilter(requete("csspi.nexus.example", null), new MockHttpServletResponse(), chaine);
        assertThat(chaine.getRequest().getAttribute(ContexteRequete.ATTRIBUT)).isNull();
        MockHttpServletRequest illisible = requete("csspi.nexus.example", null);
        illisible.addHeader("Authorization", "Bearer pas-un-jeton");
        MockFilterChain chaineIllisible = new MockFilterChain();
        new FiltreSession(SECRET, SOUS_DOMAINE).doFilter(illisible, new MockHttpServletResponse(), chaineIllisible);
        assertThat(chaineIllisible.getRequest().getAttribute(ContexteRequete.ATTRIBUT)).isNull();
        // Le témoin de session vaut l'en-tête Authorization — c'est ainsi que le navigateur se présente.
        MockHttpServletRequest parTemoin = requete("cssdc.nexus.example", null);
        String jeton = new JetonDeSession(SECRET).emettre(new JetonDeSession.Revendications("u-2", "u2", "employee", "emp-2", "T1"));
        parTemoin.setCookies(new jakarta.servlet.http.Cookie("autre", "x"), new jakarta.servlet.http.Cookie("session", jeton));
        MockFilterChain chaineTemoin = new MockFilterChain();
        new FiltreSession(SECRET, SOUS_DOMAINE).doFilter(parTemoin, new MockHttpServletResponse(), chaineTemoin);
        assertThat(((ContexteRequete) chaineTemoin.getRequest().getAttribute(ContexteRequete.ATTRIBUT)).tenantId()).isEqualTo("T1");
    }
}
