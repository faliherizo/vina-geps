package ca.vina.nexu.paie.infrastructure.persistance;

import ca.vina.nexu.paie.domaine.UniteDeTravail;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** L'unité de travail de la paie, tenue par Spring : une exception dans le geste annule tout ce qui précède. */
public class UniteDeTravailSpring implements UniteDeTravail {

    private final TransactionTemplate transaction;

    public UniteDeTravailSpring(TransactionTemplate transaction) {
        this.transaction = transaction;
    }

    @Override
    public <T> T executer(Supplier<T> geste) {
        return transaction.execute(statut -> geste.get());
    }
}
