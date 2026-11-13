package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.OrdonnanceurIntegrationPort;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.DepotTaches;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.RegistreExecuteurs;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * L'ordonnanceur partagé (ADR 0014) vu des intégrations : une livraison est une tâche différée de type
 * {@value #TYPE_TACHE}, planifiée dans la transaction du dépôt, et son exécuteur est le livreur du lot.
 */
@Component("effectifsOrdonnanceurIntegration")
public class AdaptateurOrdonnanceurIntegration implements OrdonnanceurIntegrationPort {

    public static final String TYPE_TACHE = "effectifs_livraison";

    private final DepotTaches taches;
    private final RegistreExecuteurs executeurs;

    public AdaptateurOrdonnanceurIntegration(DepotTaches taches, RegistreExecuteurs executeurs) {
        this.taches = taches;
        this.executeurs = executeurs;
    }

    @Override
    public void planifierLivraison(String tenantId, String messageId, String echeanceLe, String creePar) {
        taches.planifier(tenantId, TYPE_TACHE, messageId, echeanceLe, null, creePar);
    }

    /** Une livraison ne se reporte jamais : elle réussit, ou elle lève et l'ordonnanceur retente. */
    @Override
    public void enregistrerLivreur(Livreur livreur) {
        executeurs.enregistrer(TYPE_TACHE, (t, maintenant) -> {
            livreur.livrer(new TacheLivraison(t.tenantId(), t.cibleId(), t.tentatives()), maintenant);
            return Optional.empty();
        });
    }
}
