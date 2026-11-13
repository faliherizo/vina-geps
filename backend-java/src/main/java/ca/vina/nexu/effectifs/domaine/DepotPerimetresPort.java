package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;

/** Port de persistance des périmètres. */
public interface DepotPerimetresPort {

    List<Perimetres.Perimetre> duCompte(String tenantId, String utilisateurId);

    Optional<Perimetres.Perimetre> lire(String tenantId, String id);

    void inserer(String tenantId, Perimetres.Perimetre p, String creeLe);

    void fermer(String tenantId, String id, String date);
}
