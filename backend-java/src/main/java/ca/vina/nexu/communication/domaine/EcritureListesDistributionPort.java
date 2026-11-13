package ca.vina.nexu.communication.domaine;

import java.util.List;

/** Port d'écriture des listes de distribution. */
public interface EcritureListesDistributionPort {

    void creer(String tenantId, ListesDistribution.Liste liste);

    /** Nom et partage ; les membres seulement quand {@code membres} est fourni. */
    void modifier(String tenantId, ListesDistribution.Liste liste, List<ListesDistribution.Membre> membres);

    void supprimer(String tenantId, String listeId);
}
