package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureIntegrations;
import ca.vina.nexu.effectifs.domaine.BoiteEnvoiIntegrationPort;
import ca.vina.nexu.effectifs.domaine.ConnecteursIntegrationPort;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.ConnecteurEnregistre;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.EtatMessage;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.Issue;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.MessageALivrer;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.MessageSortant;
import ca.vina.nexu.effectifs.domaine.OrdonnanceurIntegrationPort;

/**
 * Le <strong>livreur</strong> que l'ordonnanceur appelle (jumeau de {@code livrer}) : il livre le message et consigne
 * l'issue. Une panne passagère se consigne puis <strong>lève</strong> — l'ordonnanceur retente selon sa politique ; au
 * dernier essai, le message passe en échec, visible et rejouable.
 */
public class LivraisonIntegration implements OrdonnanceurIntegrationPort.Livreur {

    /** Le nombre d'essais de la politique de l'ordonnanceur (trois) : au-delà, il ne retentera plus, et le message le dit. */
    public static final int ESSAIS_MAX = 3;
    private static final int MOTIF_MAX = 500;

    private final LectureIntegrations lecture;
    private final BoiteEnvoiIntegrationPort boite;
    private final ConnecteursIntegrationPort registre;

    public LivraisonIntegration(LectureIntegrations lecture, BoiteEnvoiIntegrationPort boite, ConnecteursIntegrationPort registre) {
        this.lecture = lecture;
        this.boite = boite;
        this.registre = registre;
    }

    /** Une tâche en retard ne relivre pas un message déjà traité (accepté, rejeté ou rejoué entre-temps). */
    @Override
    public void livrer(OrdonnanceurIntegrationPort.TacheLivraison t, String maintenant) {
        MessageSortant m = lecture.message(t.tenantId(), t.messageId());
        if (m.etat() != EtatMessage.EN_ATTENTE) {
            return;
        }
        ConnecteurEnregistre c = lecture.connecteur(t.tenantId(), m.connecteur());
        try {
            Issue issue = registre.destination(t.tenantId(), c).livrer(new MessageALivrer(m.id(), m.connecteur(), m.type(), m.cle(), m.charge()));
            boite.consigner(t.tenantId(), m.id(), issue, maintenant);
        } catch (RuntimeException e) {
            String motif = String.valueOf(e.getMessage());
            boite.consignerPanne(t.tenantId(), m.id(), motif.substring(0, Math.min(MOTIF_MAX, motif.length())),
                    t.tentatives() + 1 >= ESSAIS_MAX ? EtatMessage.ECHEC : EtatMessage.EN_ATTENTE);
            throw e;
        }
    }
}
