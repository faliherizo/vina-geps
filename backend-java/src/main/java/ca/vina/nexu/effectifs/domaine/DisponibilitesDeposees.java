package ca.vina.nexu.effectifs.domaine;

import java.util.List;

/**
 * Le dépôt de disponibilités d'une ressource pour une période : sa conformité aux règles au moment du dépôt, les
 * motifs qui l'expliquent, son approbation éventuelle, et ses plages.
 */
public record DisponibilitesDeposees(String periodeId, String ressourceId, Disponibilites.Conformite conformite, List<Regles.Motif> motifs,
                                     String deposeLe, String deposePar, String approuveLe, String approuvePar,
                                     List<Disponibilites.PlageDisponible> plages) {

    public DisponibilitesDeposees {
        motifs = List.copyOf(motifs);
        plages = List.copyOf(plages);
    }

    public boolean attendApprobation() {
        return conformite == Disponibilites.Conformite.APPROBATION_REQUISE;
    }
}
