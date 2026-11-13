package ca.vina.nexu.pays.domaine;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static ca.vina.nexu.pays.domaine.Arrondi.r2;
import static ca.vina.nexu.pays.domaine.Arrondi.r4;

/**
 * Impôt sur le revenu : société (taux unique) ou particulier (barème progressif).
 *
 * <p>Portage de {@code computeCountryIncomeTax}. Le détail par tranche est reconstruit avec la
 * même boucle — y compris son {@code break} après mise à jour de la borne, qui décide du nombre
 * de tranches présentes dans la réponse. Une tranche de plus ou de moins est un écart de contrat.
 */
public final class MoteurImpot {

    private MoteurImpot() {
    }

    public record DetailTranche(double from, Double to, double rate, double taxOnBracket) {
    }

    /** {@code brackets} est absent — et non nul — pour le profil société : c'est le `brackets?`
     *  optionnel du TypeScript, et le front distingue les deux. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResultatImpot(
            String country, String currency, String profile,
            double taxableIncome, double tax, double netAfterTax, double effectiveRate,
            String method, List<DetailTranche> brackets) {
    }

    public static ResultatImpot calculer(PaysPack pack, String profil, double revenuImposable) {
        double ti = Math.max(0, revenuImposable);

        if ("corporate".equals(profil)) {
            double impot = r2(ti * pack.corporateTaxRate());
            return new ResultatImpot(
                    pack.code(), pack.currency(), "corporate", ti, impot, r2(ti - impot),
                    r4(pack.corporateTaxRate()),
                    // `toFixed(1)` du TypeScript : séparateur décimal point, quelle que soit la
                    // locale du serveur. Sans `Locale.ROOT`, une JVM en français écrirait « 26,5 »
                    // et le libellé différerait — un écart de contrat pour une virgule.
                    String.format(Locale.ROOT, "corporateTaxRate %.1f%%", pack.corporateTaxRate() * 100),
                    null);
        }

        List<DetailTranche> detail = new ArrayList<>();
        double borneBasse = 0;
        for (PaysPack.Tranche b : pack.personalIncomeTax()) {
            Double borneHaute = b.upTo();
            double plafond = borneHaute != null ? borneHaute : Double.POSITIVE_INFINITY;
            double portion = ti > borneBasse ? Math.min(ti, plafond) - borneBasse : 0;
            detail.add(new DetailTranche(borneBasse, borneHaute, b.rate(), r2(Math.max(0, portion) * b.rate())));
            borneBasse = plafond;
            if (ti <= borneBasse) {
                break;
            }
        }

        double impot = MoteurPaie.impotProgressif(pack.personalIncomeTax(), ti);
        return new ResultatImpot(
                pack.code(), pack.currency(), "individual", ti, impot, r2(ti - impot),
                ti != 0 ? r4(impot / ti) : 0,
                "barème progressif", detail);
    }
}
