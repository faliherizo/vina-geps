package ca.vina.nexu.communication.domaine;

import java.util.List;
import java.util.Optional;

/** Port de lecture des listes de distribution et des membres actifs d'un groupe. */
public interface LectureListesDistributionPort {

    /** La liste, avec ses membres. */
    Optional<ListesDistribution.Liste> lire(String tenantId, String listeId);

    /** Les listes partagées et celles de l'auteur, par nom. */
    List<ListesDistribution.Liste> visibles(String tenantId, String acteurId);

    boolean nomPris(String tenantId, String auteurId, String nom);

    List<String> membresActifsDuGroupe(String tenantId, String groupeId);
}
