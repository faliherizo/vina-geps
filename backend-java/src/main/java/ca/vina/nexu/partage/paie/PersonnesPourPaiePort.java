package ca.vina.nexu.partage.paie;

import java.util.List;
import java.util.Optional;

/** Port : les personnes que la paie peut rémunérer — les ressources d'effectifs — et celle d'un compte. */
public interface PersonnesPourPaiePort {

    record Personne(String ressourceId, String matricule, String nom) { }

    List<Personne> personnes(String tenantId);

    /** La ressource d'effectifs reliée à ce compte, s'il en a une : c'est elle que portent ses bulletins. */
    Optional<String> ressourceDe(String tenantId, String utilisateurId);
}
