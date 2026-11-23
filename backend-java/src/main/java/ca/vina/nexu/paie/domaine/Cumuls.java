package ca.vina.nexu.paie.domaine;

import java.math.BigDecimal;

/**
 * Le cumul de l'année civile <strong>avant</strong> une paie. Il se dérive des bulletins des cycles traités et ne se
 * stocke nulle part : un cumul tenu sur la fiche divergerait au premier cycle annulé (ADR 0076).
 */
public record Cumuls(BigDecimal brut, BigDecimal gainsRrq, BigDecimal rrq, BigDecimal rrq2, BigDecimal gainsAe,
                     BigDecimal ae, BigDecimal gainsRqap, BigDecimal rqap) {

    public static final Cumuls AUCUN = new Cumuls(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    /** Le cumul après la paie {@code r}. */
    public Cumuls plus(Retenues r) {
        return new Cumuls(brut.add(r.brut()), gainsRrq.add(r.gainsRrq()), rrq.add(r.rrq()), rrq2.add(r.rrq2()),
                gainsAe.add(r.gainsAe()), ae.add(r.ae()), gainsRqap.add(r.gainsRqap()), rqap.add(r.rqap()));
    }
}
