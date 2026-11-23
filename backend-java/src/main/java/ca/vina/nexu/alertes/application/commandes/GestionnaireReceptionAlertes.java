package ca.vina.nexu.alertes.application.commandes;

import ca.vina.nexu.alertes.domaine.Acteur;
import ca.vina.nexu.alertes.domaine.Alertes.Alerte;
import ca.vina.nexu.alertes.domaine.EcritureAlertesPort;
import ca.vina.nexu.alertes.domaine.LectureAlertesPort;
import ca.vina.nexu.alertes.domaine.ReactionsAlertes;
import ca.vina.nexu.alertes.domaine.RefusAlerte;

import java.time.Clock;
import java.time.Instant;

/**
 * <strong>Lire et répondre</strong> (CU-4, CU-5 ; critères 9, 10, 24). Une alerte n'existe, pour qui n'en est
 * pas destinataire, que sous la forme d'un 404 (R6).
 */
public final class GestionnaireReceptionAlertes {

    private final LectureAlertesPort lecture;
    private final EcritureAlertesPort ecriture;
    private final Clock horloge;
    private final ReactionsAlertes reactions;

    public GestionnaireReceptionAlertes(LectureAlertesPort lecture, EcritureAlertesPort ecriture, Clock horloge) {
        this(lecture, ecriture, horloge, ReactionsAlertes.AUCUNE);
    }

    /** Avec les cascades (ST-G04) : une lecture ou une réponse qui atteint le critère arrête la cascade aussitôt. */
    public GestionnaireReceptionAlertes(LectureAlertesPort lecture, EcritureAlertesPort ecriture, Clock horloge, ReactionsAlertes reactions) {
        this.lecture = lecture;
        this.ecriture = ecriture;
        this.horloge = horloge;
        this.reactions = reactions;
    }

    public Alerte lire(Acteur acteur, String alerteId) {
        Alerte avant = alerte(acteur, alerteId);
        Alerte apres = avant.lue(acteur.personId(), Instant.now(horloge).toString());
        if (apres != avant) {
            ecriture.mettreAJour(acteur.tenantId(), alerteId, apres.remiseDe(acteur.personId()));
            reactions.apresReaction(acteur.tenantId(), alerteId, apres.remiseDe(acteur.personId()).lueLe());
        }
        return apres;
    }

    public Alerte repondre(Acteur acteur, String alerteId, String reponseId) {
        String maintenant = Instant.now(horloge).toString();
        Alerte apres = alerte(acteur, alerteId).repondue(acteur.personId(), reponseId, maintenant);
        ecriture.mettreAJour(acteur.tenantId(), alerteId, apres.remiseDe(acteur.personId()));
        reactions.apresReaction(acteur.tenantId(), alerteId, maintenant);
        return apres;
    }

    private Alerte alerte(Acteur acteur, String alerteId) {
        Alerte a = lecture.alerte(acteur.tenantId(), alerteId).orElseThrow(() -> RefusAlerte.introuvable("alerte introuvable"));
        a.remiseDe(acteur.personId());
        return a;
    }
}
