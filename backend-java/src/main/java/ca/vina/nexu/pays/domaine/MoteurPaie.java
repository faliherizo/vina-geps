package ca.vina.nexu.pays.domaine;

import java.util.ArrayList;
import java.util.List;

import static ca.vina.nexu.pays.domaine.Arrondi.r2;

/**
 * Moteur de paie multi-pays : brut annuel → net, piloté par le pack.
 *
 * <p>Portage de {@code backend/src/countries/engines.ts}. L'ordre des opérations et le moment des
 * arrondis sont reproduits pas à pas : arrondir la somme plutôt que les termes, ou l'inverse,
 * décale le résultat au centime — et c'est exactement ce que le diff de contrat rapporte comme
 * écart bloquant.
 */
public final class MoteurPaie {

    private MoteurPaie() {
    }

    public record LigneCotisation(String code, String label, double rate, double base,
                                  double annual, double period) {
    }

    public record Periode(double gross, double employeeContributions, double incomeTax,
                          double deductions, double net, double employerCost) {
    }

    public record ResultatPaie(
            String country, String currency, int periodsPerYear, double annualGross,
            List<LigneCotisation> employeeContributions,
            List<LigneCotisation> employerContributions,
            double annualIncomeTax, double annualEmployeeDeductions, double annualNet,
            double annualEmployerContributions, double annualEmployerCost,
            Periode period) {
    }

    /**
     * Impôt progressif marginal.
     *
     * <p>La boucle s'arrête dès que le revenu n'atteint pas la borne basse de la tranche — le
     * {@code else break} du TypeScript. Le remplacer par un {@code continue} donnerait le même
     * total ici, mais divergerait sur un barème mal ordonné : on reproduit le comportement, pas
     * l'intention supposée.
     */
    public static double impotProgressif(List<PaysPack.Tranche> tranches, double revenu) {
        double impot = 0;
        double borneBasse = 0;
        for (PaysPack.Tranche t : tranches) {
            double borneHaute = t.upTo() != null ? t.upTo() : Double.POSITIVE_INFINITY;
            if (revenu > borneBasse) {
                impot += (Math.min(revenu, borneHaute) - borneBasse) * t.rate();
                borneBasse = borneHaute;
            } else {
                break;
            }
        }
        return r2(impot);
    }

    public static ResultatPaie calculer(PaysPack pack, double brutAnnuel, Integer periodesParAn) {
        double g = Math.max(0, brutAnnuel);
        int ppy = Math.max(1, periodesParAn == null || periodesParAn == 0 ? 12 : periodesParAn);

        List<LigneCotisation> salariales = new ArrayList<>();
        List<LigneCotisation> patronales = new ArrayList<>();

        for (PaysPack.Cotisation c : pack.payrollContributions()) {
            // Plafond nul = pas de plafond. C'est le `!= null` du TypeScript : un plafond à zéro
            // annulerait la cotisation, ce qui n'est pas la même chose.
            double base = c.ceiling() != null ? Math.min(g, c.ceiling()) : g;
            if (estNonNul(c.employeeRate())) {
                double annuel = r2(base * c.employeeRate());
                salariales.add(new LigneCotisation(c.code(), c.label(), c.employeeRate(), base, annuel, r2(annuel / ppy)));
            }
            if (estNonNul(c.employerRate())) {
                double annuel = r2(base * c.employerRate());
                patronales.add(new LigneCotisation(c.code(), c.label(), c.employerRate(), base, annuel, r2(annuel / ppy)));
            }
        }

        double impotAnnuel = impotProgressif(pack.personalIncomeTax(), g);
        double cotisSalariales = r2(somme(salariales));
        double retenuesAnnuelles = r2(cotisSalariales + impotAnnuel);
        double netAnnuel = r2(g - retenuesAnnuelles);
        double cotisPatronales = r2(somme(patronales));

        return new ResultatPaie(
                pack.code(), pack.currency(), ppy, g,
                salariales, patronales,
                impotAnnuel, retenuesAnnuelles, netAnnuel,
                cotisPatronales, r2(g + cotisPatronales),
                new Periode(
                        r2(g / ppy), r2(cotisSalariales / ppy), r2(impotAnnuel / ppy),
                        r2(retenuesAnnuelles / ppy), r2(netAnnuel / ppy),
                        r2((g + cotisPatronales) / ppy)));
    }

    /** Le TypeScript teste `if (c.employeeRate)` : nul et zéro sont tous deux faux. */
    private static boolean estNonNul(Double taux) {
        return taux != null && taux != 0d;
    }

    private static double somme(List<LigneCotisation> lignes) {
        double s = 0;
        for (LigneCotisation l : lignes) {
            s += l.annual();
        }
        return s;
    }
}
