package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;

/** Port des semaines types déposées dans les campagnes AGPH ({@code eff_agph_depots}, {@code eff_agph_plages}). */
public interface DepotSemainesTypeAgphPort {

    /** Une semaine type à enregistrer : elle remplace la précédente de la même personne dans la même campagne. */
    record NouvelleSemaine(String campagneId, String ressourceId, Disponibilites.Conformite conformite, List<Regles.Motif> motifs, double heures,
                           String deposeLe, String deposePar, List<AgphSemaineType.PlageType> plages) { }

    /** La semaine déposée, plages triées par jour puis début. */
    Optional<AgphSemaineDeposee> lire(String tenantId, String campagneId, String ressourceId);

    /** Les heures hebdomadaires déjà offertes par les autres personnes de la campagne. */
    double heuresDesAutres(String tenantId, String campagneId, String ressourceId);

    void enregistrer(String tenantId, NouvelleSemaine semaine);
}
