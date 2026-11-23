package ca.vina.nexu.paie.domaine;

import java.math.BigDecimal;

/**
 * Le calcul d'une paie : le brut, les retenues de la personne, les cotisations de l'employeur, et les gains soumis à
 * chaque régime (ce sont eux que reportent les relevés et qui tiennent les plafonds de l'année).
 */
public record Retenues(BigDecimal brut, BigDecimal impotFederal, BigDecimal impotQuebec, BigDecimal rrq, BigDecimal rrq2,
                       BigDecimal ae, BigDecimal rqap, BigDecimal gainsRrq, BigDecimal gainsAe, BigDecimal gainsRqap,
                       Employeur employeur) {

    /** Les cotisations de l'employeur pour cette paie. */
    public record Employeur(BigDecimal rrq, BigDecimal rrq2, BigDecimal ae, BigDecimal rqap, BigDecimal fss, BigDecimal cnt,
                            BigDecimal cnesst) {

        public BigDecimal total() {
            return rrq.add(rrq2).add(ae).add(rqap).add(fss).add(cnt).add(cnesst);
        }
    }

    public BigDecimal totalRetenues() {
        return impotFederal.add(impotQuebec).add(rrq).add(rrq2).add(ae).add(rqap);
    }

    public BigDecimal net() {
        return brut.subtract(totalRetenues());
    }

    public BigDecimal coutEmployeur() {
        return brut.add(employeur.total());
    }
}
