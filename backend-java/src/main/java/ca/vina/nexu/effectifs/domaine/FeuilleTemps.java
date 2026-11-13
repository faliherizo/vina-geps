package ca.vina.nexu.effectifs.domaine;

/**
 * Une feuille de temps telle que l'API la montre : la personne, la période de paie, le statut et le palier
 * d'approbation. L'instantané des anomalies, figé à la soumission, voyage à part ({@link LectureFeuillesTempsPort.FeuilleStockee}).
 */
public record FeuilleTemps(String id, String ressourceId, String uniteCode, String debut, String fin, TempsEffectifs.StatutFeuille statut, int palier,
                           int paliersRequis, String soumiseLe, String approuveeLe, String nom, String matricule) { }
