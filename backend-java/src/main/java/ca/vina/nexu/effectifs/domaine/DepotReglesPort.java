package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;

/** Port de persistance du moteur de règles : règles, versions datées, évaluations conservées. */
public interface DepotReglesPort {

    Optional<Regle> regle(String tenantId, String id);

    /** Une règle de même contexte, même clé et même portée existe-t-elle déjà ? L'identifiant du candidat est ignoré. */
    boolean existe(String tenantId, Regle candidate);

    /** Les règles, par contexte, clé puis portée ; {@code contexte} nul = tous les contextes. */
    List<Regle> regles(String tenantId, ContexteRegle contexte);

    /** Les versions d'une règle, par numéro. */
    List<VersionRegle> versions(String tenantId, String regleId);

    /** Toutes les versions de toutes les règles d'un contexte : la sélection se fait dans le domaine. */
    List<VersionRegle> versionsDuContexte(String tenantId, ContexteRegle contexte);

    void creer(Contexte ctx, Regle regle);

    void insererVersion(Contexte ctx, VersionRegle version);

    void fermerVersion(String tenantId, String versionId, String date);

    void enregistrer(String tenantId, EvaluationRegle evaluation);

    /** Les évaluations, les plus récentes d'abord, 500 au plus. */
    List<EvaluationRegle> evaluations(String tenantId, EvaluationRegle.Filtre filtre);
}
