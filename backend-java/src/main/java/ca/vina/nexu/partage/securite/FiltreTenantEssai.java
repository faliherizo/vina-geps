package ca.vina.nexu.partage.securite;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * <strong>Essais seulement</strong> ({@code NEXU_ESSAI=1}) : comme le Node en mode test, pose
 * {@code X-Tenant-Id: default} sur {@code /api/payroll} et {@code /api/ai} quand l'en-tête manque —
 * les données opérateur historiques, que la campagne e2e manipule sans annoter chaque requête.
 * Le flux SSE {@code /api/payroll/events} est exclu : {@code EventSource} ne pose pas d'en-tête,
 * et un {@code default} injecté contredirait le jeton d'un compte rattaché (403).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class FiltreTenantEssai extends OncePerRequestFilter {

    static final String EN_TETE = "X-Tenant-Id";

    private final boolean essai;

    public FiltreTenantEssai(@Value("${nexu.essai}") boolean essai) {
        this.essai = essai;
    }

    static boolean vise(String chemin) {
        return (chemin.startsWith("/api/payroll") || chemin.startsWith("/api/ai")) && !"/api/payroll/events".equals(chemin);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite) throws ServletException, java.io.IOException {
        boolean absent = requete.getHeader(EN_TETE) == null || requete.getHeader(EN_TETE).isBlank();
        suite.doFilter(essai && vise(requete.getRequestURI()) && absent ? new AvecTenantParDefaut(requete) : requete, reponse);
    }

    /** La même requête, avec l'en-tête d'établissement en plus. */
    static final class AvecTenantParDefaut extends HttpServletRequestWrapper {
        AvecTenantParDefaut(HttpServletRequest requete) {
            super(requete);
        }

        @Override
        public String getHeader(String nom) {
            return EN_TETE.equalsIgnoreCase(nom) ? "default" : super.getHeader(nom);
        }

        @Override
        public Enumeration<String> getHeaders(String nom) {
            return EN_TETE.equalsIgnoreCase(nom) ? Collections.enumeration(List.of("default")) : super.getHeaders(nom);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> noms = new java.util.ArrayList<>(Collections.list(super.getHeaderNames()));
            noms.add(EN_TETE);
            return Collections.enumeration(noms);
        }
    }
}
