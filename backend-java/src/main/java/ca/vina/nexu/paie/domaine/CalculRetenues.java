package ca.vina.nexu.paie.domaine;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Les retenues d'une paie, par la méthode de l'annualisation (ARC T4127, Revenu Québec TP-1015.F) : le revenu de la
 * période est porté à l'année, l'impôt annuel se calcule au barème moins les crédits, puis se ramène à la période.
 * Les cotisations (RRQ, AE, RQAP) se calculent sur la période et s'arrêtent à leur plafond annuel, que tient le cumul.
 *
 * <p>Chaque montant s'arrondit au cent <strong>par retenue</strong>, comme le font les tables officielles. La part de
 * l'employeur se calcule sur la base, et non sur la cotisation arrondie de la personne : multiplier l'arrondi décale
 * d'un cent tous les quinze bulletins.
 */
public final class CalculRetenues {

    private static final MathContext PRECISION = MathContext.DECIMAL64;
    private static final int CENTS = 2;

    public record MontantsPersonnels(BigDecimal federal, BigDecimal quebec) {

        public static MontantsPersonnels deBase(Bareme b) {
            return new MontantsPersonnels(b.federal().montantPersonnel(), b.quebec().montantPersonnel());
        }
    }

    public record Entree(BigDecimal brut, Frequence frequence, Cumuls cumuls, MontantsPersonnels montants) { }

    record PartRrq(BigDecimal gains, BigDecimal cotisation, BigDecimal rrq2) { }

    record PartAssuree(BigDecimal gains, BigDecimal cotisation, BigDecimal employeur) { }

    record Cotisations(PartRrq rrq, PartAssuree ae, PartAssuree rqap) { }

    private CalculRetenues() {
    }

    public static Retenues calculer(Bareme b, ReglagesPaie reglages, Entree e) {
        if (e.brut().signum() < 0) {
            throw RefusPaie.invalide("un brut ne peut pas être négatif");
        }
        BigDecimal brut = cent(e.brut());
        BigDecimal p = BigDecimal.valueOf(e.frequence().paiesParAn());
        Cotisations c = new Cotisations(rrq(b.rrq(), brut, p, e.cumuls()), assure(b.ae(), brut, e.cumuls().gainsAe()),
                assure(b.rqap(), brut, e.cumuls().gainsRqap()));
        BigDecimal annuel = positif(brut.subtract(deductible(b.rrq(), c.rrq()))).multiply(p);
        return new Retenues(brut, impotFederal(b, annuel, p, new Credits(e.montants().federal(), c)),
                impotQuebec(b.quebec(), annuel, p, e.montants().quebec()), c.rrq().cotisation(), c.rrq().rrq2(),
                c.ae().cotisation(), c.rqap().cotisation(), c.rrq().gains(), c.ae().gains(), c.rqap().gains(),
                employeur(b, reglages, brut, c, e.cumuls()));
    }

    // ── Cotisations ──────────────────────────────────────────────────────────

    static PartRrq rrq(Bareme.Rrq r, BigDecimal brut, BigDecimal p, Cumuls c) {
        BigDecimal restant = positif(r.mga().subtract(r.exemption()).multiply(r.taux()).subtract(c.rrq()));
        // (brut × p − exemption) × taux ÷ p : diviser en dernier, et arrondir au cent dans la division même. Diviser
        // l'exemption d'abord (3 500 ÷ 12) tronque, et 359,625 $ devenait 359,62 $.
        BigDecimal base = positif(brut.multiply(p).subtract(r.exemption()));
        BigDecimal cotisation = cent(parPaie(base.multiply(r.taux()), p).min(restant));
        BigDecimal gains = brut.min(positif(r.plafondSupplementaire().subtract(c.gainsRrq())));
        BigDecimal avant = c.gainsRrq();
        BigDecimal tranche = positif(avant.add(gains).min(r.plafondSupplementaire()).subtract(avant.max(r.mga())));
        BigDecimal restant2 = positif(r.plafondSupplementaire().subtract(r.mga()).multiply(r.tauxRrq2()).subtract(c.rrq2()));
        return new PartRrq(gains, cotisation, cent(tranche.multiply(r.tauxRrq2()).min(restant2)));
    }

    static PartAssuree assure(Bareme.Assurance a, BigDecimal brut, BigDecimal gainsCumules) {
        BigDecimal gains = brut.min(positif(a.maximumAssurable().subtract(gainsCumules)));
        return new PartAssuree(gains, cent(gains.multiply(a.tauxEmploye())), cent(gains.multiply(a.tauxEmployeur())));
    }

    /** La première cotisation supplémentaire et la RRQ2 se déduisent du revenu, au fédéral comme au Québec. */
    static BigDecimal deductible(Bareme.Rrq r, PartRrq part) {
        return part.cotisation().multiply(r.tauxSupplementaire()).divide(r.taux(), PRECISION).add(part.rrq2());
    }

    // ── Impôts ───────────────────────────────────────────────────────────────

    record Credits(BigDecimal montantPersonnel, Cotisations cotisations) { }

    /**
     * Fédéral : barème − crédits (montant personnel, part de base du RRQ, AE, RQAP, montant canadien pour emploi), au
     * taux du premier palier ; puis l'abattement du Québec.
     */
    static BigDecimal impotFederal(Bareme b, BigDecimal annuel, BigDecimal p, Credits cr) {
        Bareme.Impot f = b.federal();
        BigDecimal maxBase = b.rrq().mga().subtract(b.rrq().exemption()).multiply(b.rrq().tauxBase());
        BigDecimal partBase = cr.cotisations().rrq().cotisation().multiply(b.rrq().tauxBase()).divide(b.rrq().taux(), PRECISION);
        BigDecimal montants = cr.montantPersonnel()
                .add(partBase.multiply(p).min(maxBase))
                .add(cr.cotisations().ae().cotisation().multiply(p).min(maximum(b.ae())))
                .add(cr.cotisations().rqap().cotisation().multiply(p).min(maximum(b.rqap())))
                .add(annuel.min(f.montantEmploi()));
        BigDecimal impot = positif(progressif(annuel, f).subtract(montants.multiply(f.tauxCredit())));
        return parPaie(impot.multiply(BigDecimal.ONE.subtract(f.abattement())), p);
    }

    /** Québec : la déduction pour travailleur réduit le revenu ; seul le montant personnel ouvre un crédit. */
    static BigDecimal impotQuebec(Bareme.Impot q, BigDecimal annuel, BigDecimal p, BigDecimal montantPersonnel) {
        BigDecimal deduction = annuel.multiply(q.tauxEmploi()).min(q.montantEmploi());
        BigDecimal impot = positif(progressif(positif(annuel.subtract(deduction)), q).subtract(montantPersonnel.multiply(q.tauxCredit())));
        return parPaie(impot, p);
    }

    /** L'impôt au barème : chaque tranche de revenu à son taux. */
    static BigDecimal progressif(BigDecimal revenu, Bareme.Impot impot) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal borneBasse = BigDecimal.ZERO;
        for (Bareme.Tranche t : impot.tranches()) {
            BigDecimal borneHaute = t.jusqua() == null ? revenu : t.jusqua().min(revenu);
            total = total.add(positif(borneHaute.subtract(borneBasse)).multiply(t.taux()));
            borneBasse = t.jusqua() == null ? revenu : t.jusqua();
        }
        return total;
    }

    // ── Employeur ────────────────────────────────────────────────────────────

    static Retenues.Employeur employeur(Bareme b, ReglagesPaie reglages, BigDecimal brut, Cotisations c, Cumuls cumuls) {
        BigDecimal assujettiCnt = brut.min(positif(b.cnt().maximum().subtract(cumuls.brut())));
        return new Retenues.Employeur(c.rrq().cotisation(), c.rrq().rrq2(), c.ae().employeur(), c.rqap().employeur(),
                cent(brut.multiply(reglages.tauxFss())), cent(assujettiCnt.multiply(b.cnt().taux())),
                cent(brut.multiply(reglages.tauxCnesst())));
    }

    private static BigDecimal maximum(Bareme.Assurance a) {
        return a.maximumAssurable().multiply(a.tauxEmploye());
    }

    private static BigDecimal positif(BigDecimal v) {
        return v.max(BigDecimal.ZERO);
    }

    /** Un montant annuel ramené à la paie, arrondi au cent dans la division : aucun quotient tronqué n'est arrondi une seconde fois. */
    private static BigDecimal parPaie(BigDecimal annuel, BigDecimal p) {
        return annuel.divide(p, CENTS, RoundingMode.HALF_UP);
    }

    static BigDecimal cent(BigDecimal v) {
        return v.setScale(CENTS, RoundingMode.HALF_UP);
    }
}
