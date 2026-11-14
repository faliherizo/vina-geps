package ca.vina.nexu.alertes.application.commandes;

import ca.vina.nexu.alertes.domaine.Alertes.Modele;
import ca.vina.nexu.alertes.domaine.Alertes.ReponsePredefinie;
import ca.vina.nexu.alertes.domaine.EcritureAlertesPort;
import ca.vina.nexu.alertes.domaine.LectureAlertesPort;
import ca.vina.nexu.alertes.domaine.Priorite;
import ca.vina.nexu.alertes.domaine.RefusAlerte;
import ca.vina.nexu.alertes.domaine.Semantique;

import java.util.List;
import java.util.function.Supplier;

/**
 * <strong>Tenir les réponses prédéfinies et les modèles</strong> (CU-1, CU-2 ; critères 6, 10, 32). Un
 * modèle ne propose que des réponses qui existent ; rien ne s'efface, tout se désactive (R9).
 */
public final class GestionnaireParametresAlertes {

    private final LectureAlertesPort lecture;
    private final EcritureAlertesPort ecriture;
    private final Supplier<String> identifiants;

    public GestionnaireParametresAlertes(LectureAlertesPort lecture, EcritureAlertesPort ecriture, Supplier<String> identifiants) {
        this.lecture = lecture;
        this.ecriture = ecriture;
        this.identifiants = identifiants;
    }

    public ReponsePredefinie creerReponse(String tenantId, String libelle, Semantique semantique) {
        ReponsePredefinie r = ReponsePredefinie.nouvelle(identifiants.get(), libelle, semantique);
        ecriture.enregistrer(tenantId, r);
        return r;
    }

    public ReponsePredefinie activerReponse(String tenantId, String id, boolean actif) {
        ReponsePredefinie r = lecture.reponses(tenantId).stream().filter(x -> x.id().equals(id)).findFirst()
                .orElseThrow(() -> RefusAlerte.introuvable("réponse introuvable")).avecActive(actif);
        ecriture.enregistrer(tenantId, r);
        return r;
    }

    public Modele creerModele(String tenantId, String titre, String corps, Priorite priorite, List<String> reponseIds) {
        exigerReponsesConnues(tenantId, reponseIds);
        Modele m = Modele.nouveau(identifiants.get(), titre, corps, priorite, reponseIds);
        ecriture.enregistrer(tenantId, m);
        return m;
    }

    public Modele activerModele(String tenantId, String id, boolean actif) {
        Modele m = lecture.modele(tenantId, id).orElseThrow(() -> RefusAlerte.introuvable("modèle introuvable")).avecActif(actif);
        ecriture.enregistrer(tenantId, m);
        return m;
    }

    private void exigerReponsesConnues(String tenantId, List<String> ids) {
        List<String> connues = lecture.reponses(tenantId).stream().filter(ReponsePredefinie::active).map(ReponsePredefinie::id).toList();
        ids.stream().filter(id -> !connues.contains(id)).findFirst().ifPresent(id -> {
            throw RefusAlerte.invalide("« reponseIds » : la réponse « " + id + " » n'existe pas ou n'est plus active");
        });
    }
}
