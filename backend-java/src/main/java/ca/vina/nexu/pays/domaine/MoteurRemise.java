package ca.vina.nexu.pays.domaine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static ca.vina.nexu.pays.domaine.Arrondi.r2;

/**
 * Remises : regroupement des retenues par autorité + fichier plat.
 *
 * <p>Portage de {@code computeCountryRemittance}. Deux points décident du résultat et sont
 * reproduits littéralement :
 * <ul>
 *   <li>l'<strong>ordre</strong> des lignes — {@code Map} d'insertion côté Node, donc
 *       {@code LinkedHashMap} ici, et l'impôt inséré <em>en tête</em> ({@code unshift}) ;</li>
 *   <li>le <strong>format</strong> du fichier plat, y compris son séparateur et ses deux
 *       décimales : c'est un extrant destiné à un dépôt réglementaire.</li>
 * </ul>
 */
public final class MoteurRemise {

    private MoteurRemise() {
    }

    /** Cotisation → autorité de remise, par convention de pack. */
    private static final Map<String, String> AUTORITE_DE = Map.ofEntries(
            Map.entry("RRQ", "RQ"), Map.entry("RQAP", "RQ"), Map.entry("FSS", "RQ"), Map.entry("AE", "CRA"),
            Map.entry("SS_MAL", "URSSAF"), Map.entry("RETRAITE", "URSSAF"),
            Map.entry("CSG_CRDS", "URSSAF"), Map.entry("CHOMAGE", "URSSAF"),
            Map.entry("CNAPS", "CNAPS"), Map.entry("OSTIE", "OSTIE"),
            Map.entry("SS", "SSA"), Map.entry("MEDICARE", "SSA"), Map.entry("FUTA", "IRS"));

    /** Autorité fiscale (impôt retenu) par pays. */
    private static final Map<String, String> AUTORITE_FISCALE =
            Map.of("CA", "RQ", "FR", "DGFIP", "MG", "DGI", "US", "IRS");

    public record LigneRemise(String code, String label, String authority,
                              double employee, double employer, double total) {
    }

    public record TotalAutorite(String authority, String label, double total) {
    }

    public record ResultatRemise(
            String country, String currency, int employeeCount, double incomeTaxWithheld,
            List<LigneRemise> lines, List<TotalAutorite> byAuthority,
            double grandTotal, String remittanceFile) {
    }

    public static ResultatRemise calculer(PaysPack pack, List<MoteurPaie.ResultatPaie> paies) {
        // LinkedHashMap : le `Map` de JavaScript conserve l'ordre d'insertion, et cet ordre est
        // celui des lignes de la réponse comme du fichier plat.
        Map<String, LigneRemise> accumulateur = new LinkedHashMap<>();
        double impot = 0;

        for (MoteurPaie.ResultatPaie p : paies) {
            impot = r2(impot + p.annualIncomeTax());
            for (MoteurPaie.LigneCotisation l : p.employeeContributions()) {
                cumuler(accumulateur, pack, l.code(), l.label(), l.annual(), 0);
            }
            for (MoteurPaie.LigneCotisation l : p.employerContributions()) {
                cumuler(accumulateur, pack, l.code(), l.label(), 0, l.annual());
            }
        }

        List<LigneRemise> lignes = new ArrayList<>(accumulateur.values());
        if (impot > 0) {
            String autoriteFiscale = AUTORITE_FISCALE.getOrDefault(pack.code(), premiereAutorite(pack, "TAX"));
            // `unshift` : en tête, pas en queue.
            lignes.addFirst(new LigneRemise("IRPP", "Impôt sur le revenu retenu", autoriteFiscale, impot, 0, impot));
        }

        Map<String, Double> parAutorite = new LinkedHashMap<>();
        for (LigneRemise l : lignes) {
            parAutorite.put(l.authority(), r2(parAutorite.getOrDefault(l.authority(), 0d) + l.total()));
        }
        List<TotalAutorite> totaux = new ArrayList<>();
        parAutorite.forEach((autorite, total) -> totaux.add(new TotalAutorite(autorite, libelleAutorite(pack, autorite), total)));

        double cumul = 0;
        for (LigneRemise l : lignes) {
            cumul += l.total();
        }
        double totalGeneral = r2(cumul);

        return new ResultatRemise(pack.code(), pack.currency(), paies.size(), impot,
                lignes, totaux, totalGeneral, fichierPlat(pack, paies.size(), totalGeneral, lignes));
    }

    private static void cumuler(Map<String, LigneRemise> acc, PaysPack pack,
                                String code, String label, double salariale, double patronale) {
        String autorite = AUTORITE_DE.getOrDefault(code, premiereAutorite(pack, code));
        LigneRemise courante = acc.get(code);
        double emp = r2((courante != null ? courante.employee() : 0) + salariale);
        double empr = r2((courante != null ? courante.employer() : 0) + patronale);
        acc.put(code, new LigneRemise(code, label, autorite, emp, empr, r2(emp + empr)));
    }

    private static String premiereAutorite(PaysPack pack, String defaut) {
        List<PaysPack.Autorite> autorites = pack.remittanceAuthorities();
        return autorites != null && !autorites.isEmpty() ? autorites.getFirst().code() : defaut;
    }

    private static String libelleAutorite(PaysPack pack, String code) {
        if (pack.remittanceAuthorities() != null) {
            for (PaysPack.Autorite a : pack.remittanceAuthorities()) {
                if (a.code().equals(code)) {
                    return a.label();
                }
            }
        }
        return code;
    }

    /**
     * Fichier plat CSV — analogue des extrants SAGIR/DSN/941.
     *
     * <p>{@code Locale.ROOT} n'est pas un détail : sur une JVM en français, {@code %.2f} écrirait
     * « 1234,56 » là où le Node écrit « 1234.56 », et le fichier serait rejeté à la lecture.
     */
    private static String fichierPlat(PaysPack pack, int nbSalaries, double totalGeneral, List<LigneRemise> lignes) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "PAYS;%s;DEVISE;%s;SALARIES;%d;TOTAL;%.2f",
                pack.code(), pack.currency(), nbSalaries, totalGeneral));
        sb.append("\nAUTORITE;CODE;LIBELLE;PART_SALARIALE;PART_PATRONALE;TOTAL");
        for (LigneRemise l : lignes) {
            sb.append(String.format(Locale.ROOT, "%n%s;%s;%s;%.2f;%.2f;%.2f",
                    l.authority(), l.code(), l.label(), l.employee(), l.employer(), l.total()));
        }
        return sb.toString();
    }
}
