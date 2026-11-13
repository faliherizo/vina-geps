package ca.vina.nexu.acces.domaine;

import java.util.Map;

/**
 * Port : les décisions d'un établissement sur les capacités d'un rôle (capacité → autorisée ou
 * refusée). Le domaine les reçoit, il ne va pas les chercher ; sans établissement, il n'y en a pas.
 */
public interface SurchargesAcces {
    Map<String, Boolean> pour(String tenantId, String role);
}
