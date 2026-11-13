package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;

/** Port des remplacements long terme ({@code eff_remplacements}, {@code eff_remplacement_interets}). */
public interface DepotRemplacementsAgphPort {

    record Nouveau(String uniteCode, String posteId, Agph.OrigineRemplacement origine, String referenceId, String debut, String fin,
                   String finManifestation) { }

    /** Une place encore vacante du poste, avec son créneau. */
    record PlaceVacante(String id, Planification.Creneau creneau) { }

    Optional<AgphRemplacement> lire(String tenantId, String id);

    /** Par début, puis identifiant. */
    List<AgphRemplacement> lister(String tenantId);

    void inserer(String tenantId, String id, Nouveau n, Contexte auteur);

    void attribuer(String tenantId, String id, String ressourceId, int places, String decideLe);

    /**
     * Le remplacement demeure non comblé (E-CH-9) — daté, motivé s'il s'agit d'une décision — et ses quarts vacants sont
     * transférés à la gestion régulière (E-CH-10) : {@code motif} nul pour la décision du mécanisme.
     */
    void laisserNonComble(String tenantId, String id, NonComble n);

    /** Ce que la décision « non comblé » consigne : l'instant, le nombre de quarts transférés, le motif éventuel. */
    record NonComble(String instant, int quarts, String motif) { }

    /** Les quarts du poste sur la durée qui n'ont personne, hors périodes closes : ceux que la gestion régulière reprend. */
    int quartsVacants(String tenantId, String posteId, String debut, String fin);

    void fermerFenetre(String tenantId, String id, String instant);

    /** Les intérêts en cours des ressources encore actives : ceux que l'attribution considère. */
    List<Agph.Interesse> interessesRetenables(String tenantId, String id);

    /** Les intérêts en cours, avec nom et matricule. */
    List<AgphInteresse> interesses(String tenantId, String id);

    /** Dépose (ou redépose, après retrait) un intérêt. */
    void manifester(String tenantId, String id, String ressourceId, String instant);

    /** Rend le nombre d'intérêts retirés (0 : il n'y en avait pas). */
    int retirer(String tenantId, String id, String ressourceId, String instant);

    /** Les places vacantes du poste sur la durée, hors périodes closes, par date puis début. */
    List<PlaceVacante> placesVacantes(String tenantId, String posteId, String debut, String fin);
}
