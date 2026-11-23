package ca.vina.nexu.alertes.domaine;

import java.util.Map;

/**
 * Combien de fois chaque alerte a été <strong>relancée</strong> : une relance par étape de cascade exécutée (ST-G03,
 * critère 11). Un port à part, en lecture seule : le suivi n'a pas à connaître les politiques ni la trace complète.
 */
public interface RelancesAlertesPort {

    /** Pour les alertes de l'organisme envoyées à partir de {@code depuis} (instant ISO) : alerte → relances ; absente = aucune. */
    Map<String, Integer> relancesDepuis(String tenantId, String depuis);
}
