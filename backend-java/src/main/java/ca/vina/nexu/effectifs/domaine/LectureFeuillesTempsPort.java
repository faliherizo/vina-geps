package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Port de lecture des feuilles de temps (lot R9) : paramètres, feuilles, segments versionnés, décisions. */
public interface LectureFeuillesTempsPort {

    /** La feuille et son instantané d'anomalies (nul tant qu'elle n'a pas été soumise). */
    record FeuilleStockee(FeuilleTemps feuille, List<TempsEffectifs.Anomalie> instantane) { }

    /** Un segment tel que la base le garde, toutes versions confondues. */
    record SegmentStocke(String id, String tenantId, String feuilleId, String placeId, String date, String debut, String fin, String typeHeureCode,
                         TempsEffectifs.Source source, int version, String remplaceId, boolean actif, String motif, String creeLe, String creePar,
                         String sourceExterne, String confirmeLe, String confirmePar, String description, java.util.Map<String, Object> attributs) { }

    record DecisionFeuille(int palier, TempsEffectifs.Decision decision, String motif, String decideLe, String decidePar) { }

    Optional<TempsEffectifs.Parametres> parametres(String tenantId);

    Optional<FeuilleStockee> feuille(String tenantId, String id);

    /** L'identifiant de la feuille d'une personne qui commence à cette date, s'il y en a une. */
    Optional<String> feuilleDe(String tenantId, String ressourceId, String debut);

    /** Les feuilles d'une personne, de la plus récente à la plus ancienne. */
    List<FeuilleTemps> deLaRessource(String tenantId, String ressourceId);

    /** Les feuilles de l'établissement, par début décroissant puis nom ; chaque critère nul est ignoré. */
    List<FeuilleTemps> lister(String tenantId, String statut, String debut);

    /** Les segments actifs (dernières versions) d'une feuille, par début. */
    List<SegmentStocke> segmentsActifs(String tenantId, String feuilleId);

    Optional<SegmentStocke> segment(String tenantId, String feuilleId, String id);

    /** Les places dont un segment actif reste sur la feuille. */
    Set<String> placesActives(String tenantId, String feuilleId);

    /** Les décisions, dans l'ordre où elles ont été prises. */
    List<DecisionFeuille> decisions(String tenantId, String feuilleId);

    /** Les champs de la feuille propres à l'organisme, actifs et désactivés, dans leur ordre. */
    List<ChampsFeuille.ChampFeuille> champs(String tenantId);
}
