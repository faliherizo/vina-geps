package ca.vina.nexu.alertes.infrastructure.persistance;

import ca.vina.nexu.alertes.application.commandes.GestionnaireCascades;
import ca.vina.nexu.alertes.application.commandes.GestionnaireEnvoiAlertes;
import ca.vina.nexu.alertes.domaine.EcheancierCascades;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.DepotTaches;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.RegistreExecuteurs;
import ca.vina.nexu.partage.avis.domaine.PublicationAvis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * L'ordonnanceur partagé (ADR 0014) vu des cascades : deux types de tâches. {@code alerte.etape} — une par alerte, sa
 * prochaine étape — décide ; {@code alerte.relance} — une par étape exécutée — envoie. Une étape reprise après un
 * redémarrage trouve la cascade déjà avancée et ne fait rien ; une relance planifiée dans la même transaction que
 * l'étape part même si le serveur s'arrête juste après.
 */
public class EcheancierOrdonnanceur implements EcheancierCascades {

    public static final String TACHE_ETAPE = "alerte.etape";
    public static final String TACHE_RELANCE = "alerte.relance";
    public static final String TACHE_APPEL = "alerte.appel";
    private static final String PERSONNE = "personId";
    private static final String COMPTE = "compteAvis";
    private static final String ETAPE = "etape";
    private static final String COMPTES = "comptes";
    private static final String CANAUX = "canaux";
    private static final String SUJET = "sujet";
    private static final String CORPS = "corps";
    private static final String EVENEMENT = "evenement";

    private final DepotTaches taches;
    private final RegistreExecuteurs executeurs;

    public EcheancierOrdonnanceur(DepotTaches taches, RegistreExecuteurs executeurs) {
        this.taches = taches;
        this.executeurs = executeurs;
    }

    @Override
    public void planifierEtape(String tenantId, String alerteId, int etape, String echeanceLe) {
        taches.planifier(tenantId, TACHE_ETAPE, alerteId, echeanceLe, Map.of(ETAPE, etape), GestionnaireCascades.MECANISME);
    }

    @Override
    public void annulerEtape(String tenantId, String alerteId) {
        taches.annulerPour(tenantId, TACHE_ETAPE, alerteId);
    }

    @Override
    public void planifierRelance(String tenantId, Relance r, String maintenant) {
        Map<String, Object> charge = new LinkedHashMap<>();
        charge.put(COMPTES, r.comptes());
        charge.put(CANAUX, r.canaux());
        charge.put(EVENEMENT, r.message().evenement());
        charge.put(SUJET, r.message().sujet());
        charge.put(CORPS, r.message().corps());
        taches.planifier(tenantId, TACHE_RELANCE, r.alerteId() + ":" + r.etape(), maintenant, charge, GestionnaireCascades.MECANISME);
    }

    @Override
    public void planifierAppel(String tenantId, ca.vina.nexu.alertes.domaine.AppelsAlertes.AAppeler a, String maintenant) {
        taches.planifier(tenantId, TACHE_APPEL, a.alerteId() + ":" + a.etape() + ":" + a.personId(), maintenant,
                Map.of(ETAPE, a.etape(), PERSONNE, a.personId(), COMPTE, a.compteAvis()), GestionnaireCascades.MECANISME);
    }

    /** Branche l'exécuteur des appels (ST-G06) : la cible porte l'alerte, la charge l'étape, la personne et son compte. */
    public void brancherAppels(ca.vina.nexu.alertes.application.commandes.GestionnaireAppelsAlertes appels) {
        executeurs.enregistrer(TACHE_APPEL, (t, maintenant) -> {
            Map<String, Object> c = t.charge();
            String alerteId = t.cibleId().substring(0, t.cibleId().indexOf(':'));
            appels.lancer(t.tenantId(), new ca.vina.nexu.alertes.domaine.AppelsAlertes.AAppeler(alerteId, ((Number) c.get(ETAPE)).intValue(),
                    (String) c.get(PERSONNE), (String) c.get(COMPTE)));
            return Optional.empty();
        });
    }

    /** Branche les deux exécuteurs : l'étape décide par le gestionnaire, la relance publie par le port d'avis. */
    @SuppressWarnings("unchecked")
    public void brancher(GestionnaireCascades cascades, PublicationAvis avis) {
        executeurs.enregistrer(TACHE_ETAPE, (t, maintenant) -> {
            cascades.executerEtape(t.tenantId(), t.cibleId(), ((Number) t.charge().get(ETAPE)).intValue(), maintenant);
            return Optional.empty();
        });
        executeurs.enregistrer(TACHE_RELANCE, (t, maintenant) -> {
            Map<String, Object> c = t.charge();
            // Une relance planifiée avant ST-G07 n'a pas d'événement : c'est l'événement ordinaire.
            String evenement = c.get(EVENEMENT) instanceof String e ? e : GestionnaireEnvoiAlertes.EVENEMENT;
            avis.publier(new PublicationAvis.Avis(t.tenantId(), evenement, (List<String>) c.get(COMPTES), (String) c.get(SUJET),
                    (String) c.get(CORPS), GestionnaireEnvoiAlertes.URL_MES_ALERTES, Map.of(), (List<String>) c.get(CANAUX)));
            return Optional.empty();
        });
    }
}
