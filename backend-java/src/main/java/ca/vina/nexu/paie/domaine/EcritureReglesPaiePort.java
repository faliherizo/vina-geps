package ca.vina.nexu.paie.domaine;

/** Port d'écriture des règles de paie variable : créer ou remplacer une règle par son identifiant. */
public interface EcritureReglesPaiePort {

    void enregistrer(Trace trace, ReglePaie regle);
}
