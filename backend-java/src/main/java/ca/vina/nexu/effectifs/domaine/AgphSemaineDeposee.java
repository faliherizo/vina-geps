package ca.vina.nexu.effectifs.domaine;

import java.util.List;

/**
 * La semaine type qu'une personne a déposée dans une campagne AGPH (E-CH-1) : sa conformité aux règles au moment du
 * dépôt, les motifs qui l'expliquent, les heures hebdomadaires offertes, et ses plages.
 */
public record AgphSemaineDeposee(String ressourceId, Disponibilites.Conformite conformite, List<Regles.Motif> motifs, double heures, String deposeLe,
                                 String deposePar, List<AgphSemaineType.PlageType> plages) {

    public AgphSemaineDeposee {
        motifs = List.copyOf(motifs);
        plages = List.copyOf(plages);
    }
}
