package ca.vina.nexu.partage.securite;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * En déploiement par sous-domaine (ADR 0033), l'hôte désigne l'établissement : il vaut en-tête
 * {@code X-Tenant-Id} pour tout ce qui suit — le compte global y est chez lui sans rien poser, et
 * l'injection « default » des essais n'a plus lieu d'être. Le jeton d'un autre établissement est
 * refusé par {@link FiltreSession}, une fois la session lue.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltreHoteEtablissement extends OncePerRequestFilter {

    static final String EN_TETE = "X-Tenant-Id";

    private final HoteEtablissement hote;

    public FiltreHoteEtablissement(HoteEtablissement hote) {
        this.hote = hote;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite) throws ServletException, IOException {
        String present = requete.getHeader(EN_TETE);
        String tenant = present == null || present.isBlank() ? hote.tenantDeLHote(requete) : null;
        suite.doFilter(tenant == null ? requete : new AvecTenantDeLHote(requete, tenant), reponse);
    }

    /** La même requête, avec l'établissement de l'hôte en en-tête. */
    static final class AvecTenantDeLHote extends HttpServletRequestWrapper {
        private final String tenant;

        AvecTenantDeLHote(HttpServletRequest requete, String tenant) {
            super(requete);
            this.tenant = tenant;
        }

        @Override
        public String getHeader(String nom) {
            return EN_TETE.equalsIgnoreCase(nom) ? tenant : super.getHeader(nom);
        }

        @Override
        public Enumeration<String> getHeaders(String nom) {
            return EN_TETE.equalsIgnoreCase(nom) ? Collections.enumeration(List.of(tenant)) : super.getHeaders(nom);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> noms = new ArrayList<>(Collections.list(super.getHeaderNames()));
            noms.add(EN_TETE);
            return Collections.enumeration(noms);
        }
    }
}
