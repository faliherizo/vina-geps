package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;

/** Port des ressources planifiables ({@code eff_ressources}). */
public interface DepotRessourcesPort {

    record Champs(String nom, String uniteCode, String corpsEmploiCode, double anciennete, String utilisateurId, String titreEmploiCode,
                  String statutEmploiCode, String conventionCode) { }

    Optional<Ressource> lire(String tenantId, String id);

    /** Triées par nom. */
    List<Ressource> lister(String tenantId);

    boolean matriculeExiste(String tenantId, String matricule);

    void inserer(String tenantId, String id, String matricule, Champs c, String creeLe);

    void modifier(String tenantId, String id, Champs c, boolean actif);

    void poserStatutEmploi(String tenantId, String id, String statutEmploiCode);

    /** La plus ancienne ressource reliée à ce compte. */
    Optional<Ressource> deLUtilisateur(String tenantId, String utilisateurId);
}
