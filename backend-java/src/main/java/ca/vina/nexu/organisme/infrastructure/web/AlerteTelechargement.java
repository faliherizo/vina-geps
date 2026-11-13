package ca.vina.nexu.organisme.infrastructure.web;

import ca.vina.nexu.organisme.domaine.Circonstances;
import ca.vina.nexu.organisme.domaine.EvenementAlerte;
import ca.vina.nexu.organisme.domaine.SignalementPort;
import ca.vina.nexu.organisme.domaine.Telechargements;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.temps.Iso;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Clock;

/**
 * <strong>Alerte au téléchargement</strong> (P-TN-10) — jumelle de {@code telechargements.ts}. Tout export que
 * l'application rend en pièce jointe ({@code Content-Disposition: attachment}, statut 2xx) déclenche les
 * règles « téléchargement d'un export » de l'organisme, sans que chaque écran d'export ait à y penser — et donc
 * sans qu'un export neuf puisse l'oublier. Le signalement part une fois la réponse terminée et ne la retient
 * pas ; ses échecs sont journalisés par le signalement différé.
 */
@Component("organismeAlerteTelechargement")
public class AlerteTelechargement implements HandlerInterceptor {

    private static final String INCONNU = "inconnu";

    private final SignalementPort signalement;
    private final Clock horloge;

    public AlerteTelechargement(SignalementPort signalement, Clock horloge) {
        this.signalement = signalement;
        this.horloge = horloge;
    }

    @Override
    public void afterCompletion(HttpServletRequest requete, HttpServletResponse reponse, Object gestionnaire, Exception ex) {
        String tenantId = tenantDe(requete);
        String disposition = reponse.getHeader(HttpHeaders.CONTENT_DISPOSITION);
        if (tenantId == null || !Telechargements.estTelechargement(reponse.getStatus(), disposition)) {
            return;
        }
        signalement.signaler(tenantId, EvenementAlerte.EXPORT_TELECHARGE, new Circonstances(quiDe(GardeAcces.contexte(requete)), Iso.de(horloge.instant()),
                Telechargements.quoi(disposition, requete.getMethod(), requete.getRequestURI())));
    }

    /**
     * L'organisme de la session ; pour un compte global, celui qu'il a désigné — sans session, aucun : un
     * en-tête anonyme ne déclenche pas les alertes d'un organisme.
     */
    private static String tenantDe(HttpServletRequest requete) {
        ContexteRequete c = GardeAcces.contexte(requete);
        if (c == null) {
            return null;
        }
        if (c.tenantId() != null && !c.tenantId().isEmpty()) {
            return c.tenantId();
        }
        String entete = requete.getHeader("X-Tenant-Id");
        return entete == null || entete.isBlank() ? null : entete.strip();
    }

    private static String quiDe(ContexteRequete c) {
        if (c.username() != null && !c.username().isEmpty()) {
            return c.username();
        }
        return c.userId() == null || c.userId().isEmpty() ? INCONNU : c.userId();
    }
}
