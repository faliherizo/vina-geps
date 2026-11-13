package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;

/** Port des dépôts de disponibilités ({@code eff_depots}) et de leurs plages ({@code eff_disponibilites}). */
public interface DepotDisponibilitesPort {

    /** Une plage à enregistrer, avec l'identifiant qu'on lui donne. */
    record PlageIdentifiee(String id, Disponibilites.PlageDisponible plage) { }

    /** Un dépôt à enregistrer : il remplace le précédent et en efface l'approbation. */
    record NouveauDepot(String periodeId, String ressourceId, Disponibilites.Conformite conformite, List<Regles.Motif> motifs, String deposeLe,
                        String deposePar, List<PlageIdentifiee> plages) { }

    /** Le dépôt, plages triées par date puis début. */
    Optional<DisponibilitesDeposees> lire(String tenantId, String periodeId, String ressourceId);

    void remplacer(String tenantId, NouveauDepot depot);

    /** Rend le dépôt conforme et horodate l'approbation. */
    void approuver(String tenantId, String periodeId, String ressourceId, Approbation approbation);

    record Approbation(String le, String par) { }

    /**
     * La personne est-elle absente ce jour-là (absence d'effectifs accordée ou en attente de décision) ? Une absente
     * ne se propose pas : sans ce filtre, la personne dont l'absence vient de libérer la place en redeviendrait la
     * première candidate.
     */
    boolean absenteLe(String tenantId, String ressourceId, String date);
}
