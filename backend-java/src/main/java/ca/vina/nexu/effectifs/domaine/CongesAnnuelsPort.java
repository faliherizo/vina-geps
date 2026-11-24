package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;

/** Port des campagnes de choix des congés et de leurs choix (addenda 8, M-02). */
public interface CongesAnnuelsPort {

    void enregistrer(String tenantId, CongesAnnuels.Campagne campagne);

    Optional<CongesAnnuels.Campagne> lire(String tenantId, String id);

    List<CongesAnnuels.Campagne> lister(String tenantId);

    /** Remplace tous les choix de la personne dans la campagne. */
    void remplacerChoix(String tenantId, String campagneId, String ressourceId, List<CongesAnnuels.Choix> choix);

    /** Pose le statut d'un choix décidé. */
    void deciderChoix(String tenantId, String campagneId, CongesAnnuels.Choix choix);

    List<CongesAnnuels.Choix> choix(String tenantId, String campagneId);
}
