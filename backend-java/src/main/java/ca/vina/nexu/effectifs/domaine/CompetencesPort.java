package ca.vina.nexu.effectifs.domaine;

import java.util.List;

/** Port des compétences détenues (addenda 8, M-04) : poser ou retirer celle d'une personne, lire les siennes, lire toutes. */
public interface CompetencesPort {

    /** Pose la compétence de la personne ; une seconde pose du même code la remplace (renouvellement). */
    void poser(String tenantId, Competences.Detenue competence, String par, String le);

    boolean retirer(String tenantId, String ressourceId, String code);

    List<Competences.Detenue> deLaRessource(String tenantId, String ressourceId);

    /** Toutes celles qui ont une échéance : le relevé des échéances en part. */
    List<Competences.Detenue> avecEcheance(String tenantId);
}
