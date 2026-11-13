package ca.vina.nexu.effectifs.domaine;

import java.util.List;

/** Port de l'ancienneté des ressources, que BD Corpo fait autorité pour mettre à jour. */
public interface AnciennetesIntegrationPort {

    record RessourceAnciennete(String id, String matricule, double anciennete) { }

    List<RessourceAnciennete> actives(String tenantId, String uniteCode);

    void poser(String tenantId, String ressourceId, double anciennete);
}
