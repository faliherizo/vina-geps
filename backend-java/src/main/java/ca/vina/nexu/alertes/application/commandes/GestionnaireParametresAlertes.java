package ca.vina.nexu.alertes.application.commandes;

import ca.vina.nexu.alertes.domaine.Acteur;
import ca.vina.nexu.alertes.domaine.Alertes;
import ca.vina.nexu.alertes.domaine.Alertes.ContenuModele;
import ca.vina.nexu.alertes.domaine.Alertes.Modele;
import ca.vina.nexu.alertes.domaine.Alertes.ReponsePredefinie;
import ca.vina.nexu.alertes.domaine.AnnuaireAlertes;
import ca.vina.nexu.alertes.domaine.EcritureAlertesPort;
import ca.vina.nexu.alertes.domaine.LectureAlertesPort;
import ca.vina.nexu.alertes.domaine.RefusAlerte;
import ca.vina.nexu.alertes.domaine.Semantique;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * <strong>Tenir les réponses prédéfinies, les modèles et les réglages</strong> (CU-1, CU-2 ; critères 6, 8, 10, 32). Un
 * modèle ne propose que des réponses qui existent et ne vise que des groupes connus ; chaque modification en fait une
 * version datée et signée ; rien ne s'efface, tout se désactive (R9).
 */
public final class GestionnaireParametresAlertes {

    /** Les ports et l'horloge du geste, groupés pour garder le constructeur court. */
    public record Dependances(LectureAlertesPort lecture, EcritureAlertesPort ecriture, AnnuaireAlertes annuaire, Clock horloge) {
    }

    private final Dependances d;
    private final Supplier<String> identifiants;

    public GestionnaireParametresAlertes(Dependances d, Supplier<String> identifiants) {
        this.d = d;
        this.identifiants = identifiants;
    }

    public ReponsePredefinie creerReponse(String tenantId, String libelle, Semantique semantique) {
        ReponsePredefinie r = ReponsePredefinie.nouvelle(identifiants.get(), libelle, semantique);
        d.ecriture().enregistrer(tenantId, r);
        return r;
    }

    public ReponsePredefinie activerReponse(String tenantId, String id, boolean actif) {
        ReponsePredefinie r = d.lecture().reponses(tenantId).stream().filter(x -> x.id().equals(id)).findFirst()
                .orElseThrow(() -> RefusAlerte.introuvable("réponse introuvable")).avecActive(actif);
        d.ecriture().enregistrer(tenantId, r);
        return r;
    }

    public Modele creerModele(Acteur acteur, ContenuModele contenu) {
        exigerConnus(acteur.tenantId(), contenu);
        return ranger(acteur, Modele.nouveau(identifiants.get(), contenu));
    }

    /** Une modification fait une version nouvelle ; l'ancienne reste lisible, et les alertes parties gardent la leur. */
    public Modele modifierModele(Acteur acteur, String id, ContenuModele contenu) {
        exigerConnus(acteur.tenantId(), contenu);
        return ranger(acteur, exiger(acteur.tenantId(), id).modifie(contenu));
    }

    public Modele activerModele(Acteur acteur, String id, boolean actif) {
        return ranger(acteur, exiger(acteur.tenantId(), id).avecActif(actif));
    }

    public List<LectureAlertesPort.VersionModele> versions(String tenantId, String id) {
        exiger(tenantId, id);
        return d.lecture().versions(tenantId, id);
    }

    public Alertes.Reglages reglages(String tenantId) {
        return d.lecture().reglages(tenantId);
    }

    public Alertes.Reglages definirReglages(String tenantId, Alertes.Reglages reglages) {
        d.ecriture().enregistrer(tenantId, reglages);
        return reglages;
    }

    private Modele exiger(String tenantId, String id) {
        return d.lecture().modele(tenantId, id).orElseThrow(() -> RefusAlerte.introuvable("modèle introuvable"));
    }

    private Modele ranger(Acteur acteur, Modele m) {
        d.ecriture().enregistrer(acteur.tenantId(), m, Instant.now(d.horloge()).toString(), acteur.personId());
        return m;
    }

    private void exigerConnus(String tenantId, ContenuModele c) {
        List<String> reponses = d.lecture().reponses(tenantId).stream().filter(ReponsePredefinie::active).map(ReponsePredefinie::id).toList();
        c.reponseIds().stream().filter(id -> !reponses.contains(id)).findFirst().ifPresent(id -> {
            throw RefusAlerte.invalide("« reponseIds » : la réponse « " + id + " » n'existe pas ou n'est plus active");
        });
        List<String> groupes = d.annuaire().groupes(tenantId).stream().map(AnnuaireAlertes.Groupe::id).toList();
        c.groupeIds().stream().filter(g -> !groupes.contains(g)).findFirst().ifPresent(g -> {
            throw RefusAlerte.invalide("« groupeIds » : le groupe « " + g + " » n'existe pas");
        });
    }
}
