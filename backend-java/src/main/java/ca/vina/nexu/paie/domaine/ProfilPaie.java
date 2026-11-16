package ca.vina.nexu.paie.domaine;

/**
 * Ce qu'une règle de paie variable peut viser chez une personne : son titre d'emploi et sa convention (des effectifs),
 * son échelon (de son dossier de rémunération). Chacun peut manquer.
 */
public record ProfilPaie(String titreEmploi, String convention, Integer echelon) { }
