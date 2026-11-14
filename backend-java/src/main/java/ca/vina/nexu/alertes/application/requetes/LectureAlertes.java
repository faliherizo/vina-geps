package ca.vina.nexu.alertes.application.requetes;

import ca.vina.nexu.alertes.domaine.Acteur;
import ca.vina.nexu.alertes.domaine.Alertes.Alerte;
import ca.vina.nexu.alertes.domaine.Alertes.Destinataire;
import ca.vina.nexu.alertes.domaine.Alertes.Modele;
import ca.vina.nexu.alertes.domaine.Alertes.ReponsePredefinie;
import ca.vina.nexu.alertes.domaine.AnnuaireAlertes;
import ca.vina.nexu.alertes.domaine.EtatSuivi;
import ca.vina.nexu.alertes.domaine.LectureAlertesPort;
import ca.vina.nexu.alertes.domaine.RefusAlerte;

import java.util.List;

/**
 * Ce qu'on lit des alertes : le catalogue (réponses, modèles, groupes, listes), le suivi partagé des expéditeurs,
 * filtrable (R7, ST-G03), et les alertes reçues par une personne (CU-4).
 */
public final class LectureAlertes {

    /** Le suivi et « Mes alertes » s'arrêtent aux plus récentes : l'historique complet est un export. */
    public static final int LIMITE = 200;
    /** L'export, lui, va plus loin : c'est la pièce qu'on archive. */
    public static final int LIMITE_EXPORT = 5_000;

    /** Le filtre du suivi : ce que la base trie, puis l'état, jugé sur l'alerte complète ({@code etat} nul : tous). */
    public record Filtre(LectureAlertesPort.Critere critere, EtatSuivi etat) {
        public static final Filtre TOUT = new Filtre(LectureAlertesPort.Critere.TOUT, null);
    }

    private final LectureAlertesPort lecture;
    private final AnnuaireAlertes annuaire;

    public LectureAlertes(LectureAlertesPort lecture, AnnuaireAlertes annuaire) {
        this.lecture = lecture;
        this.annuaire = annuaire;
    }

    public List<ReponsePredefinie> reponses(String tenantId) {
        return lecture.reponses(tenantId);
    }

    /** Tous les modèles : l'administration les tient, actifs ou non. */
    public List<Modele> modeles(String tenantId) {
        return lecture.modeles(tenantId);
    }

    /** Les modèles qu'un expéditeur peut employer : actifs, et dans son périmètre (ST-G02). */
    public List<Modele> modelesPour(Acteur acteur) {
        List<String> groupes = annuaire.groupesDe(acteur.tenantId(), acteur.personId());
        return lecture.modeles(acteur.tenantId()).stream().filter(Modele::actif).filter(m -> m.visiblePour(groupes)).toList();
    }

    public List<AnnuaireAlertes.Groupe> groupes(String tenantId) {
        return annuaire.groupes(tenantId);
    }

    public List<Destinataire> membres(String tenantId, String groupeId) {
        return annuaire.membres(tenantId, groupeId);
    }

    public List<AnnuaireAlertes.Liste> listes(Acteur acteur) {
        return annuaire.listes(acteur.tenantId(), acteur.personId());
    }

    public List<Alerte> envoyees(String tenantId, Filtre filtre) {
        return filtrer(lecture.envoyees(tenantId, filtre.critere(), LIMITE), filtre);
    }

    public List<Alerte> aExporter(String tenantId, Filtre filtre) {
        return filtrer(lecture.envoyees(tenantId, filtre.critere(), LIMITE_EXPORT), filtre);
    }

    private static List<Alerte> filtrer(List<Alerte> alertes, Filtre filtre) {
        return filtre.etat() == null ? alertes : alertes.stream().filter(filtre.etat()::decrit).toList();
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
