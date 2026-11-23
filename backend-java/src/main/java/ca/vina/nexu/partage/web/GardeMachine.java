package ca.vina.nexu.partage.web;

import ca.vina.nexu.partage.securite.JetonsMachine;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * La garde des routes <strong>machine</strong> ({@code /api/echanges/v1/**}, 26-015-DO addenda 5, AD-06) : seul un jeton
 * {@code client_credentials} porteur de la portée demandée y entre. Une session humaine — jeton ou témoin — est refusée,
 * comme un jeton machine l'est sur les routes de session : les deux frontières se tiennent l'une l'autre.
 * Refus par défaut : sans jeton 401, jeton illisible 401, portée absente 403.
 */
@Component
public class GardeMachine {

    private static final String PREFIXE = "Bearer ";
    private static final int NON_AUTHENTIFIE = 401;
    private static final int INTERDIT = 403;

    private final JetonsMachine jetons;

    public GardeMachine(JetonsMachine jetons) {
        this.jetons = jetons;
    }

    /** Le verdict porte l'organisme du jeton : jamais celui d'un en-tête, qu'une application pourrait choisir. */
    public GardeAcces.Verdict exiger(HttpServletRequest requete, JetonsMachine.Portee portee) {
        Optional<JetonsMachine.Application> application = jeton(requete).flatMap(jetons::lire);
        if (application.isEmpty()) {
            return new GardeAcces.Verdict(null, Bord.erreur(NON_AUTHENTIFIE, "jeton d'application (client_credentials) exigé"));
        }
        JetonsMachine.Application a = application.get();
        if (a.tenantId() == null || a.tenantId().isBlank()) {
            return new GardeAcces.Verdict(null, Bord.erreur(INTERDIT, "jeton d'application sans organisme"));
        }
        if (!a.peut(portee)) {
            return new GardeAcces.Verdict(null, Bord.erreur(INTERDIT, "portée « " + portee.code() + " » exigée"));
        }
        return new GardeAcces.Verdict(a.tenantId(), null);
    }

    /** L'application du jeton, pour signer ce qu'elle écrit au journal ; à n'appeler qu'après un verdict favorable. */
    public Optional<JetonsMachine.Application> application(HttpServletRequest requete) {
        return jeton(requete).flatMap(jetons::lire);
    }

    private static Optional<String> jeton(HttpServletRequest requete) {
        // RFC 6750 et 7235 : le schéma d'authentification ne tient pas compte de la casse (« bearer » vaut « Bearer »).
        String entete = requete.getHeader(HttpHeaders.AUTHORIZATION);
        return entete != null && entete.regionMatches(true, 0, PREFIXE, 0, PREFIXE.length())
                ? Optional.of(entete.substring(PREFIXE.length()).strip()) : Optional.empty();
    }
}
