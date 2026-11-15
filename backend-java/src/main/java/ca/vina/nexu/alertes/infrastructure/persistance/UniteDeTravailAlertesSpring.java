package ca.vina.nexu.alertes.infrastructure.persistance;

import ca.vina.nexu.alertes.domaine.UniteDeTravailAlertes;
import org.springframework.transaction.support.TransactionTemplate;

/** L'unité de travail des cascades, tenue par Spring : une exception dans le geste annule tout ce qui précède. */
public class UniteDeTravailAlertesSpring implements UniteDeTravailAlertes {

    private final TransactionTemplate transaction;

    public UniteDeTravailAlertesSpring(TransactionTemplate transaction) {
        this.transaction = transaction;
    }

    @Override
    public void executer(Runnable geste) {
        transaction.executeWithoutResult(statut -> geste.run());
    }
}
