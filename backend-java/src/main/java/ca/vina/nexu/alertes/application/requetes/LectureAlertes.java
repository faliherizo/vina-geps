package ca.vina.nexu.alertes.application.requetes;

import ca.vina.nexu.alertes.domaine.Acteur;
import ca.vina.nexu.alertes.domaine.Alertes.Alerte;
import ca.vina.nexu.alertes.domaine.Alertes.Modele;
import ca.vina.nexu.alertes.domaine.Alertes.ReponsePredefinie;
import ca.vina.nexu.alertes.domaine.AnnuaireAlertes;
import ca.vina.nexu.alertes.domaine.LectureAlertesPort;
import ca.vina.nexu.alertes.domaine.RefusAlerte;

import java.util.List;

/**
 * Ce qu'on lit des alertes : le catalogue (réponses, modèles, groupes), le suivi partagé des expéditeurs
 * (R7) et les alertes reçues par une personne (CU-4).
 */
public final class LectureAlertes {

    /** Le suivi et « Mes alertes » s'arrêtent aux plus récentes : l'historique complet est un rapport. */
    public static final int LIMITE = 200;

    private final LectureAlertesPort lecture;
    private final AnnuaireAlertes annuaire;

    public LectureAlertes(LectureAlertesPort lecture, AnnuaireAlertes annuaire) {
        this.lecture = lecture;
        this.annuaire = annuaire;
    }

    public List<ReponsePredefinie> reponses(String tenantId) {
        return lecture.reponses(tenantId);
    }

    public List<Modele> modeles(String tenantId) {
        return lecture.modeles(tenantId);
    }

    public List<AnnuaireAlertes.Groupe> groupes(String tenantId) {
        return annuaire.groupes(tenantId);
    }

    public List<ca.vina.nexu.alertes.domaine.Alertes.Destinataire> membres(String tenantId, String groupeId) {
        return annuaire.membres(tenantId, groupeId);
    }

    public List<Alerte> envoyees(String tenantId) {
        return lecture.envoyees(tenantId, LIMITE);
    }

    /** La fiche d'une alerte pour un expéditeur autorisé : toutes ses remises. */
    public Alerte fiche(String tenantId, String id) {
        return lecture.alerte(tenantId, id).orElseThrow(() -> RefusAlerte.introuvable("alerte introuvable"));
    }

    /** Les non lues d'abord, puis les plus récentes. */
    public List<Alerte> recues(Acteur acteur) {
        return lecture.recues(acteur.tenantId(), acteur.personId(), LIMITE).stream()
                .sorted((a, b) -> Boolean.compare(a.remiseDe(acteur.personId()).lue(), b.remiseDe(acteur.personId()).lue()))
                .toList();
    }
}
