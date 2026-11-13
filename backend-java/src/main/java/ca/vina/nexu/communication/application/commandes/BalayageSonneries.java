package ca.vina.nexu.communication.application.commandes;

import ca.vina.nexu.communication.domaine.AppelsDirects;
import ca.vina.nexu.communication.domaine.EcritureAppelsDirectsPort;
import ca.vina.nexu.communication.domaine.JournalCommunicationPort;
import ca.vina.nexu.communication.domaine.LectureAppelsDirectsPort;

/**
 * <strong>Clôt les sonneries trop vieilles</strong>, avant toute lecture : sans cela, un appel abandonné
 * sonnerait des jours chez son destinataire. Fait à la lecture plutôt que par une tâche de fond — une
 * tâche arrêtée laisserait revenir le défaut sans rien signaler.
 */
public final class BalayageSonneries {

    private final LectureAppelsDirectsPort lecture;
    private final EcritureAppelsDirectsPort ecriture;
    private final JournalCommunicationPort journal;

    public BalayageSonneries(LectureAppelsDirectsPort lecture, EcritureAppelsDirectsPort ecriture, JournalCommunicationPort journal) {
        this.lecture = lecture;
        this.ecriture = ecriture;
        this.journal = journal;
    }

    public void clore(String tenantId, String personId, String maintenant) {
        for (AppelsDirects.Appel a : lecture.sonnants(tenantId, personId)) {
            if (AppelsDirects.sonnerieExpiree(a, maintenant)) {
                ecriture.changerEtat(tenantId, a.avec(AppelsDirects.Etat.MANQUE, a.decrocheLe(), maintenant, null));
                journal.consigner(tenantId, new JournalCommunicationPort.Trace(a.appelantId(), "appel.manque", a.appeleId(), null, null));
            }
        }
    }
}
