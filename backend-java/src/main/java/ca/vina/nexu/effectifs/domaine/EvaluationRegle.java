package ca.vina.nexu.effectifs.domaine;

/**
 * Une décision automatique conservée (E-GN-9, E-GN-10) : les versions appliquées et l'instantané des faits,
 * de quoi la rejouer et la justifier des années après. {@code motifs}, {@code versions} et {@code faits} sont
 * les structures JSON telles qu'enregistrées : la trace ne se réinterprète pas.
 */
public record EvaluationRegle(String id, String contexte, String objetType, String objetId, String action, Object motifs, Object versions,
                              Object faits, String dateDuFait, String horodatage, String declencheur) {

    /** Les filtres de la lecture ; un champ nul ne filtre pas. */
    public record Filtre(String objetType, String objetId, ContexteRegle contexte) { }
}
