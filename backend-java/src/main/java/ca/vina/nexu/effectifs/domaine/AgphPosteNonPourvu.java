package ca.vina.nexu.effectifs.domaine;

/** Un poste offert par la campagne que personne n'a obtenu : à la clôture, il devient vacant et passe à la gestion régulière. */
public record AgphPosteNonPourvu(String posteId, String posteCode, Planification.EtatPoste etat) {
}
