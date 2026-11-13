package ca.vina.nexu.partage.securite;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Lit la session à chaque requête et pose le contexte de l'appelant.
 *
 * <p>Équivalent du middleware d'authentification optionnelle du Node : le jeton peut venir du
 * <strong>cookie {@code session}</strong> — c'est ainsi que le navigateur l'envoie — ou d'un
 * en-tête {@code Authorization: Bearer}, pour les clients d'API. Le cookie prime, comme côté
 * Node.
 *
 * <p>Le filtre <strong>ne refuse rien</strong> : il renseigne le contexte quand il le peut et
 * laisse passer sinon. C'est aux routes de décider ce qu'elles exigent — certaines sont publiques
 * (le catalogue des pays alimente la page de connexion), d'autres non. Refuser ici casserait ces
 * routes-là.
 */
@Component
public class FiltreSession extends OncePerRequestFilter {

    private static final String REFUS_HOTE = "{\"errors\":[\"tenant mismatch\"]}";

    private static final String REFUS_EMPRUNT_TERMINE = "{\"errors\":[\"" + SessionsEmpruntees.MESSAGE_TERMINE + "\"]}";
    private static final String REFUS_EMPRUNT_INTERDIT = "{\"errors\":[\"" + SessionsEmpruntees.MESSAGE_INTERDIT + "\"]}";

    private final JetonDeSession jetons;
    private final HoteEtablissement hote;
    private final SessionsEmpruntees emprunts;

    public FiltreSession(String secret, HoteEtablissement hote) {
        this(secret, hote, SessionsEmpruntees.AUCUNE);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public FiltreSession(@Value("${nexu.jwt.secret}") String secret, HoteEtablissement hote, SessionsEmpruntees emprunts) {
        this.jetons = new JetonDeSession(secret);
        this.hote = hote;
        this.emprunts = emprunts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite)
            throws ServletException, IOException {
        String jeton = jetonDe(requete);
        if (jeton != null) {
            JetonDeSession.Revendications r = jetons.lire(jeton);
            if (r != null) {
                requete.setAttribute(ContexteRequete.ATTRIBUT, new ContexteRequete(
                        r.sub(), r.username(), r.role(), r.employeeId(), r.tenantId(), r.emprunteur(), r.empruntId()));
                // En sous-domaine (ADR 0033), un jeton d'établissement ne vaut que sur l'hôte de cet
                // établissement : celui de cssdc. présenté sur csspi. est refusé, quel que soit le chemin.
                if (r.tenantId() != null && hoteContredit(requete, r.tenantId())) {
                    refuser(reponse, 403, REFUS_HOTE);
                    return;
                }
                // Session empruntée (ADR 0038) : terminée, elle ne vaut plus ; les secrets de la cible restent hors de portée.
                if (r.emprunteur() != null && !emprunts.active(r.empruntId())) {
                    refuser(reponse, 401, REFUS_EMPRUNT_TERMINE);
                    return;
                }
                if (r.emprunteur() != null && SessionsEmpruntees.interdit(requete.getMethod(), requete.getRequestURI())) {
                    refuser(reponse, 403, REFUS_EMPRUNT_INTERDIT);
                    return;
                }
            }
        }
        suite.doFilter(requete, reponse);
    }

    private static void refuser(HttpServletResponse reponse, int code, String corps) throws IOException {
        reponse.setStatus(code);
        reponse.setContentType("application/json");
        reponse.getWriter().write(corps);
    }

    private boolean hoteContredit(HttpServletRequest requete, String tenantDuJeton) {
        String deLHote = hote.tenantDeLHote(requete);
        return deLHote != null && !deLHote.equals(tenantDuJeton);
    }

    private static String jetonDe(HttpServletRequest requete) {
        Cookie[] cookies = requete.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if ("session".equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        String entete = requete.getHeader("Authorization");
        return entete != null && entete.startsWith("Bearer ") ? entete.substring(7) : null;
    }
}
