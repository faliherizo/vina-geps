package ca.vina.nexu.organisme.domaine;

import java.util.List;

/** Port de persistance des pays autorisés : une liste par organisme ; vide, aucune restriction. */
public interface PaysAutorisesPort {

    List<String> lire(String tenantId);

    void enregistrer(String tenantId, List<String> pays, Modification modification);
}
