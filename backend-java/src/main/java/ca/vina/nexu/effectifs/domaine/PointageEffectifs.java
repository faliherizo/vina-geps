package ca.vina.nexu.effectifs.domaine;

import java.util.List;

/**
 * Un pointage tel que la base le garde ({@code eff_pointages}), avec le nom de la personne. Aucune position : seule la
 * distance au milieu est conservée (Loi 25, minimisation).
 */
public record PointageEffectifs(String id, String ressourceId, String nom, Lieu lieu, PresenceEffectifs.Sens sens, String horodatage,
                                PresenceEffectifs.OriginePointage origine, String saisieId, Constat constat, Suivi suivi, Qualification qualification) {

    /**
     * Ce que le pointage <b>ouvre</b> : la nature de la pause qu'il commence (E-GP-2), l'activité du travail qu'il
     * commence (E-GP-15). Nulles ailleurs. Le Node les portait seul : relu par le Java, un pointage les perdait.
     */
    public record Qualification(NaturePause naturePause, String activiteCode) { }

    /** Où le pointage se rattache : la place et le milieu du quart (nuls pour une saisie hors quart), l'unité. */
    public record Lieu(String placeId, String milieuId, String uniteCode) { }

    /**
     * Ce que la preuve et les règles ont constaté.
     *
     * @param motifsRegles les motifs du verdict : {@link Regles.Motif} à l'écriture, leur forme JSON relue à la lecture
     */
    public record Constat(PresenceEffectifs.Resultat resultat, PresenceEffectifs.Ecart ecart, Long distanceMetres, ActionRegle actionRegles,
                          List<?> motifsRegles) { }

    /** La vie du pointage : sa saisie, puis la décision de l'encadrement. */
    public record Suivi(PresenceEffectifs.StatutPointage statut, String justification, String saisiLe, String saisiPar, String decideLe,
                        String decidePar, String motifDecision) { }

    /** Le pointage réduit à ce que le calcul lit — site, nature et activité compris : sans eux, segments et répartitions les perdaient. */
    public PresenceEffectifs.PointageCourt court() {
        return new PresenceEffectifs.PointageCourt(sens, horodatage, lieu.milieuId(), qualification.naturePause(), qualification.activiteCode());
    }
}
