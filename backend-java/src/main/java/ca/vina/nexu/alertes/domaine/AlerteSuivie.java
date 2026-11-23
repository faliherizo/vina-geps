package ca.vina.nexu.alertes.domaine;

import ca.vina.nexu.alertes.domaine.Alertes.Alerte;

/** Une alerte telle que l'expéditeur la suit : l'alerte et ses remises, plus le nombre de relances parties (critère 11). */
public record AlerteSuivie(Alerte alerte, int relances) {

    public AlerteSuivie {
        if (relances < 0) {
            throw RefusAlerte.invalide("un nombre de relances ne peut pas être négatif");
        }
    }

    /** Une alerte qui vient de partir : aucune relance encore. */
    public static AlerteSuivie nouvelle(Alerte alerte) {
        return new AlerteSuivie(alerte, 0);
    }

    public boolean relancee() {
        return relances > 0;
    }
}
