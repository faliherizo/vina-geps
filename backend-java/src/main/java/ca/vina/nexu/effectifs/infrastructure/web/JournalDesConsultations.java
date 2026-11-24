package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.domaine.Consultations;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.ReglagesJournalPort;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.temps.Iso;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Clock;

/**
 * <strong>Le journal des consultations</strong> (addenda 8, M-10) : après une lecture réussie d'un dossier ou d'un horaire,
 * si l'organisme l'a activé, l'action « consultation » entre au journal des décisions avec son lecteur. Rien n'est lu ni
 * écrit pour un organisme qui ne l'a pas activé, ni pour une lecture refusée.
 */
public class JournalDesConsultations implements HandlerInterceptor {

    private static final String LECTURE = "GET";
    private static final int OK = 200;

    private final ReglagesJournalPort reglages;
    private final JournalEffectifsPort journal;
    private final Clock horloge;

    public JournalDesConsultations(ReglagesJournalPort reglages, JournalEffectifsPort journal, Clock horloge) {
        this.reglages = reglages;
        this.journal = journal;
        this.horloge = horloge;
    }

    @Override
    public void afterCompletion(HttpServletRequest requete, HttpServletResponse reponse, Object geste, Exception erreur) {
        if (!LECTURE.equals(requete.getMethod()) || reponse.getStatus() != OK) {
            return;
        }
        ContexteRequete c = GardeAcces.contexte(requete);
        if (c == null || c.tenantId() == null) {
            return;
        }
        Consultations.objetDe(requete.getRequestURI()).filter(o -> reglages.consultations(c.tenantId())).ifPresent(o ->
                journal.ecrire(new Contexte(c.tenantId(), c.userId(), c.role(), Iso.de(horloge.instant())),
                        JournalEffectifs.Consignation.de(Consultations.ACTION, o.type().code(), o.id(), null, null, null)));
    }
}
