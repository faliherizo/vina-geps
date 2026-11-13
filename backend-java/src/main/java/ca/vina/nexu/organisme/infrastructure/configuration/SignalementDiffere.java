package ca.vina.nexu.organisme.infrastructure.configuration;

import ca.vina.nexu.organisme.domaine.Circonstances;
import ca.vina.nexu.organisme.domaine.EvenementAlerte;
import ca.vina.nexu.organisme.domaine.SignalementPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

/**
 * Le signalement <strong>différé</strong> : l'alerte part sur un autre fil, après la réponse, et un échec se
 * journalise sans rien lever. Un transport de courriel lent ne doit ni retarder une connexion ni un
 * téléchargement ; une alerte en panne ne doit pas transformer un export réussi en erreur 500.
 */
public class SignalementDiffere implements SignalementPort {

    private static final Logger JOURNAL = LoggerFactory.getLogger(SignalementDiffere.class);

    private final SignalementPort cible;
    private final Executor executeur;

    public SignalementDiffere(SignalementPort cible, Executor executeur) {
        this.cible = cible;
        this.executeur = executeur;
    }

    @Override
    public void signaler(String tenantId, EvenementAlerte evenement, Circonstances circonstances) {
        try {
            executeur.execute(() -> executer(tenantId, evenement, circonstances));
        } catch (RuntimeException e) {
            consignerEchec(tenantId, evenement, e);
        }
    }

    private void executer(String tenantId, EvenementAlerte evenement, Circonstances circonstances) {
        try {
            cible.signaler(tenantId, evenement, circonstances);
        } catch (RuntimeException e) {
            consignerEchec(tenantId, evenement, e);
        }
    }

    // Ni le texte ni les destinataires de l'alerte au journal (ADR 0041) : l'organisme et l'événement suffisent à enquêter.
    private static void consignerEchec(String tenantId, EvenementAlerte evenement, RuntimeException e) {
        JOURNAL.atError().addKeyValue("categorie", "securite").addKeyValue("tenantId", tenantId).addKeyValue("evenement", evenement.code())
                .setCause(e).log("alerte de l'organisme non envoyée");
    }
}
