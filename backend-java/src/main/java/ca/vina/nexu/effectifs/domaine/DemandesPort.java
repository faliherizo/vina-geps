package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;

/** Port des demandes opérationnelles (addenda 8, M-08). */
public interface DemandesPort {

    void enregistrer(String tenantId, DemandesOperationnelles.Demande demande);

    Optional<DemandesOperationnelles.Demande> lire(String tenantId, String id);

    List<DemandesOperationnelles.Demande> deLaRessource(String tenantId, String ressourceId);

    /** Toutes, ou celles d'un statut ; les plus récentes d'abord. */
    List<DemandesOperationnelles.Demande> lister(String tenantId, DemandesOperationnelles.Statut statut);

    /** Les demandes acceptées d'un des types donnés qui couvrent la date. */
    List<DemandesOperationnelles.Demande> accepteesCouvrant(String tenantId, List<String> typeCodes, String date);
}
