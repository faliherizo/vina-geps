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
    private static final String ETAPE = "etape";
    private static final String COMPTES = "comptes";
    private static final String CANAUX = "canaux";
    private static final String SUJET = "sujet";
    private static final String CORPS = "corps";

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
        charge.put(SUJET, r.sujet());
        charge.put(CORPS, r.corps());
        taches.planifier(tenantId, TACHE_RELANCE, r.alerteId() + ":" + r.etape(), maintenant, charge, GestionnaireCascades.MECANISME);
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
            avis.publier(new PublicationAvis.Avis(t.tenantId(), GestionnaireEnvoiAlertes.EVENEMENT, (List<String>) c.get(COMPTES), (String) c.get(SUJET),
                    (String) c.get(CORPS), GestionnaireEnvoiAlertes.URL_MES_ALERTES, Map.of(), (List<String>) c.get(CANAUX)));
            return Optional.empty();
        });
    }
}
