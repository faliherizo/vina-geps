package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;

/** Port des soldes des banques reçus de l'organisme (AD-06b) : poser, lire celui d'une banque, lire ceux d'une personne. */
public interface SoldesBanquesPort {

    /** Remplace, banque par banque, le solde reçu ; les autres banques de la personne restent. */
    void poser(String tenantId, List<SoldesBanques.Solde> soldes);

    Optional<SoldesBanques.Solde> solde(String tenantId, String ressourceId, String banqueCode);

    List<SoldesBanques.Solde> deLaRessource(String tenantId, String ressourceId);
}
