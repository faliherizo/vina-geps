package ca.vina.nexu.alertes.domaine;

/** Port : un geste en tout ou rien. Une étape de cascade avance, ajoute ses remises et planifie la suite ensemble. */
public interface UniteDeTravailAlertes {

    void executer(Runnable geste);

    /** Sans base transactionnelle (essais de domaine) : le geste s'exécute tel quel. */
    UniteDeTravailAlertes DIRECTE = Runnable::run;
}
