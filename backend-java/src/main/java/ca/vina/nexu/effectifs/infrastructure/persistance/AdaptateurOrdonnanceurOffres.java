package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.EcheancesOffresPort;
import ca.vina.nexu.effectifs.domaine.OffresQuart;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.DepotTaches;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.RegistreExecuteurs;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * L'ordonnanceur partagé (ADR 0014) vu de l'offre en cascade (AD-07) : une tâche différée de type
 * {@link OffresQuart#TACHE_ECHEANCE} par offre, dont l'échéance suit la proposition en cours, annulée à la fermeture.
 */
@Component("effectifsOrdonnanceurOffres")
public class AdaptateurOrdonnanceurOffres implements EcheancesOffresPort {

    private final DepotTaches taches;
    private final RegistreExecuteurs executeurs;

    public AdaptateurOrdonnanceurOffres(DepotTaches taches, RegistreExecuteurs executeurs) {
        this.taches = taches;
        this.executeurs = executeurs;
    }

    @Override
    public void planifier(String tenantId, String offreId, String echeance) {
        taches.planifier(tenantId, OffresQuart.TACHE_ECHEANCE, offreId, echeance, Map.of(), OffresQuart.MECANISME);
    }

    @Override
    public void annuler(String tenantId, String offreId) {
        taches.annulerPour(tenantId, OffresQuart.TACHE_ECHEANCE, offreId);
    }

    /** Une échéance ne se reporte jamais : l'offre avance, ou l'exécuteur lève et l'ordonnanceur retente. */
    @Override
    public void enregistrerExecuteur(Executeur executeur) {
        executeurs.enregistrer(OffresQuart.TACHE_ECHEANCE, (t, maintenant) -> {
            executeur.executer(t.tenantId(), t.cibleId(), maintenant);
            return Optional.empty();
        });
    }
}
