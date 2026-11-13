package ca.vina.nexu.organisme.application.commandes;

import ca.vina.nexu.organisme.domaine.AlerteConsignee;
import ca.vina.nexu.organisme.domaine.Alertes;
import ca.vina.nexu.organisme.domaine.Circonstances;
import ca.vina.nexu.organisme.domaine.EvenementAlerte;
import ca.vina.nexu.organisme.domaine.JournalAlertesPort;
import ca.vina.nexu.organisme.domaine.RegleAlerte;
import ca.vina.nexu.organisme.domaine.ReglesAlertePort;
import ca.vina.nexu.organisme.domaine.SignalementPort;
import ca.vina.nexu.partage.courrier.Courrier;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;

import java.util.List;
import java.util.Objects;

/**
 * Déclenche les règles d'un événement : un courriel par destinataire, et une ligne au journal des alertes par
 * règle — <strong>courriel parti ou non</strong>. Une alerte dont l'envoi a échoué doit rester visible : c'est
 * justement celle que personne n'a reçue.
 */
public class DeclencheurAlertes implements SignalementPort {

    /** Le journal borne le texte d'erreur, comme le Node : une panne de transport ne remplit pas la base. */
    static final int ERREUR_MAX = 500;
    static final String TRANSPORT_REFUSE = "envoi refusé par le transport de courriel";

    private final ReglesAlertePort regles;
    private final JournalAlertesPort journal;
    private final Courrier courrier;
    private final Sequence sequence;

    public DeclencheurAlertes(ReglesAlertePort regles, JournalAlertesPort journal, Courrier courrier, Sequence sequence) {
        this.regles = regles;
        this.journal = journal;
        this.courrier = courrier;
        this.sequence = sequence;
    }

    @Override
    public void signaler(String tenantId, EvenementAlerte evenement, Circonstances circonstances) {
        alerter(tenantId, evenement, circonstances);
    }

    /** Le nombre de règles déclenchées. */
    public int alerter(String tenantId, EvenementAlerte evenement, Circonstances d) {
        List<RegleAlerte> declenchees = regles.lister(tenantId).stream().filter(r -> r.evenement() == evenement).toList();
        Alertes.TexteAlerte texte = Alertes.texte(evenement, d);
        for (RegleAlerte r : declenchees) {
            List<String> erreurs = r.destinataires().stream().map(a -> envoyer(a, texte)).filter(Objects::nonNull).toList();
            String erreur = erreurs.isEmpty() ? null : String.join(" ; ", erreurs);
            journal.consigner(tenantId, new AlerteConsignee(sequence.uuid(), r.id(), evenement.code(), d.quoi(), d.qui(),
                    r.destinataires().size() - erreurs.size(), erreur == null || erreur.length() <= ERREUR_MAX ? erreur : erreur.substring(0, ERREUR_MAX),
                    d.quand(), r.destinataires()));
        }
        return declenchees.size();
    }

    /** L'erreur d'un envoi, ou {@code null} s'il est parti. */
    private String envoyer(String a, Alertes.TexteAlerte texte) {
        try {
            return courrier.envoyer(new Courrier.Message(a, texte.sujet(), texte.texte())) ? null : TRANSPORT_REFUSE;
        } catch (RuntimeException e) {
            return e.getMessage() == null ? TRANSPORT_REFUSE : e.getMessage();
        }
    }
}
