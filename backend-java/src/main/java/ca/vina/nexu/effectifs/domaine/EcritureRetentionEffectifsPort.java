package ca.vina.nexu.effectifs.domaine;

/** Port d'écriture de la rétention : poser ou retirer une durée, purger une classe. */
public interface EcritureRetentionEffectifsPort {

    void poser(Contexte ctx, RetentionEffectifs.Classe classe, int annees);

    void retirer(String tenantId, RetentionEffectifs.Classe classe);

    /** Supprime ce que la classe désigne avant la date limite ; rend le nombre de lignes supprimées. */
    long purger(String tenantId, RetentionEffectifs.Classe classe, String limite);
}
