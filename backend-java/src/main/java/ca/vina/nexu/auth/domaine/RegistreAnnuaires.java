package ca.vina.nexu.auth.domaine;

import java.util.List;
import java.util.Optional;

/** Port de lecture des annuaires (P-TN-2) : la page de connexion et le rappel n'écrivent jamais. */
public interface RegistreAnnuaires {

    /** Tous les annuaires d'un organisme, actifs ou non, dans l'ordre de leur nom. */
    List<Annuaires.Annuaire> de(String tenantId);

    Optional<Annuaires.Annuaire> parId(String id);
}
