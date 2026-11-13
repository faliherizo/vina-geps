package ca.vina.nexu.effectifs.domaine;

/** Un poste choisi dans une campagne, et par quel moyen (la personne, son planificateur, le mécanisme). */
public record AgphChoix(String ressourceId, String posteId, String posteCode, Agph.ModeChoix mode, String choisiLe) {
}
