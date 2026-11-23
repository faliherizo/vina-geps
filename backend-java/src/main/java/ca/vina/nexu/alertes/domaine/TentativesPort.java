package ca.vina.nexu.alertes.domaine;

import java.time.Instant;
import java.util.List;

/**
 * Port : les tentatives de transmission d'un organisme autour d'un intervalle (remises, étapes de cascade, appels). Le
 * dépôt peut en rendre un peu plus large que demandé ; le domaine coupe à l'instant près.
 */
@FunctionalInterface
public interface TentativesPort {
    List<UtilisateursActifs.Tentative> tentatives(String tenantId, Instant debut, Instant fin);
}
