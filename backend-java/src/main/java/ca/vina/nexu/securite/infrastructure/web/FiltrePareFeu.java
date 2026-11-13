package ca.vina.nexu.securite.infrastructure.web;

import ca.vina.nexu.securite.domaine.PareFeu;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Optional;

/**
 * Le pare-feu applicatif posé devant {@code /api} : en-têtes de durcissement sur toute réponse,
 * inspection du chemin et de la requête, débit par adresse, mesure de capacité.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class FiltrePareFeu extends OncePerRequestFilter {

    private final PareFeu pareFeu;
    private final Clock horloge;
    private final boolean https;

    public FiltrePareFeu(PareFeu pareFeu, Clock horloge, @Value("${nexu.https:false}") boolean https) {
        this.pareFeu = pareFeu;
        this.horloge = horloge;
        this.https = https;
    }

    /** En-têtes de durcissement sur l'API elle-même — nginx les pose aussi, mais l'API ne s'y fie pas. */
    void poserEnTetes(HttpServletResponse reponse) {
        reponse.setHeader("X-Content-Type-Options", "nosniff");
        reponse.setHeader("X-Frame-Options", "DENY");
        reponse.setHeader("Referrer-Policy", "no-referrer");
        reponse.setHeader("Cache-Control", "private, no-cache, max-age=0, must-revalidate");
        reponse.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        reponse.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        if (https) {
            reponse.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        return !requete.getRequestURI().startsWith("/api");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite) throws ServletException, IOException {
        poserEnTetes(reponse);
        long debut = System.nanoTime();
        PareFeu.Mesure mesure = pareFeu.mesure();
        mesure.entrer();
        try {
            String chaine = requete.getQueryString() == null ? "" : requete.getQueryString();
            Optional<PareFeu.Refus> refus = pareFeu.controler(requete.getRequestURI(), chaine, requete.getMethod(), requete.getRemoteAddr(), requete.getHeader("User-Agent"), requete.getHeader("Referer"));
            if (refus.isPresent()) {
                reponse.setStatus(refus.get().code());
                reponse.setContentType("application/json;charset=UTF-8");
                reponse.getOutputStream().write(("{\"errors\":[\"" + refus.get().message() + "\"]}").getBytes(StandardCharsets.UTF_8));
                return;
            }
            suite.doFilter(requete, reponse);
        } finally {
            mesure.sortir();
            mesure.noter(horloge.millis(), (System.nanoTime() - debut) / 1_000_000L);
        }
    }
}
