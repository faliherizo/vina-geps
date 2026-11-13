package ca.vina.nexu.superadmin.infrastructure.persistance;

import ca.vina.nexu.superadmin.domaine.AdministrationSysteme;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** L'unité de travail, tenue par Spring : un échec dans le geste annule tout ce qui précède. */
@Component
public class TransactionsSpring implements AdministrationSysteme.Transactions {

    private final TransactionTemplate transaction;

    public TransactionsSpring(TransactionTemplate transaction) {
        this.transaction = transaction;
    }

    @Override
    public <T> T executer(Supplier<T> geste) {
        return transaction.execute(statut -> geste.get());
    }
}
