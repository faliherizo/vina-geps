package ca.vina.nexu.partage.securite;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Protection CSRF « double-submit cookie » — port du middleware de {@code backend/src/app.ts}.
 *
 * <p>Une mutation portée par le <strong>cookie</strong> de session doit présenter, dans
 * l'en-tête {@code X-CSRF-Token}, la valeur du cookie {@code csrf} que la connexion a posé. Un
 * client d'API authentifié par {@code Bearer} n'a pas de cookie : un site tiers ne peut pas le
 * faire parler à son insu, il est donc hors CSRF. La connexion et la déconnexion sont exemptées
 * (l'une pose le cookie, l'autre l'efface), ainsi que le jeton OAuth (authentifié par secret).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class FiltreCsrf extends OncePerRequestFilter {

    private static final Set<String> SURES = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<String> EXEMPTES = Set.of("/api/auth/login", "/api/auth/logout", "/api/auth/_reset", "/api/oauth/token");

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite)
            throws ServletException, IOException {
        // Les tiers de démonstration (`/mock/…`) ne lisent jamais la session de l'application.
        if (SURES.contains(requete.getMethod()) || EXEMPTES.contains(requete.getRequestURI()) || requete.getRequestURI().startsWith("/mock/")) {
            suite.doFilter(requete, reponse);
            return;
        }
        String session = cookie(requete, "session");
        if (session == null) {
            suite.doFilter(requete, reponse);   // Bearer ou anonyme : la route décidera
            return;
        }
        String attendu = cookie(requete, "csrf");
        String recu = requete.getHeader("X-CSRF-Token");
        if (attendu == null || recu == null || !attendu.equals(recu)) {
            reponse.setStatus(403);
            reponse.setContentType("application/json;charset=UTF-8");
            reponse.getOutputStream().write("{\"errors\":[\"CSRF token mismatch\"]}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        suite.doFilter(requete, reponse);
    }

    private static String cookie(HttpServletRequest requete, String nom) {
        Cookie[] cookies = requete.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (nom.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
