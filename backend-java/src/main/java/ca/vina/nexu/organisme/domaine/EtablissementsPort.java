package ca.vina.nexu.organisme.domaine;

import java.util.Optional;

/** Port de lecture des établissements : le statut d'un établissement, vide s'il n'existe pas. */
public interface EtablissementsPort {

    Optional<String> statut(String tenantId);
}
