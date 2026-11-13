package ca.vina.nexu.pays.domaine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static ca.vina.nexu.pays.domaine.Arrondi.r2;

/**
 * Fichiers de remise au format officiel du pays.
 *
 * <p>Portage de {@code backend/src/countries/officialFormats.ts} : DSN française, formulaire 941
 * américain, déclaration IRSA/CNaPS malgache. Les trois sont portés.
 *
 * <p>{@link FormatNonPorteException} reste en place : elle garde le principe. Un format que le
 * Node connaît et que le Java ne saurait pas produire doit lever une exception explicite, jamais
 * fabriquer un fichier plausible — celui-ci partirait chez une administration fiscale.
 */
public final class FormatsOfficiels {

    private FormatsOfficiels() {
    }

    /** Pays disposant d'un format officiel — identique à {@code hasOfficialFormat} du Node. */
    private static final List<String> AVEC_FORMAT = List.of("FR", "US", "MG");

    /** Ce qui est effectivement porté en Java — les trois, désormais. */
    private static final List<String> PORTES = List.of("FR", "US", "MG");

    public record Salarie(String name, String id, MoteurPaie.ResultatPaie payroll) {
    }

    public record Resume(int employeeCount, double grossTotal, double incomeTaxTotal,
                         double contributionsTotal, double grandTotal) {
    }

    public record Remise(String country, String format, String authority, String filename,
                         String mimeType, String content, Resume summary) {
    }

    public static class FormatNonPorteException extends UnsupportedOperationException {
        public FormatNonPorteException(String code) {
            super("format officiel " + code + " pas encore porté en Java — le module countries ne doit "
                    + "pas être routé vers le backend Java tant que FR, US et MG ne le sont pas tous");
        }
    }

    public static boolean aUnFormatOfficiel(String code) {
        return AVEC_FORMAT.contains(code);
    }

    public static Remise generer(PaysPack pack, List<Salarie> salaries,
                                 Map<String, String> employeur, String libellePeriode) {
        if (!PORTES.contains(pack.code())) {
            throw new FormatNonPorteException(pack.code());
        }
        Map<String, String> emp = employeur == null ? Map.of() : employeur;
        return switch (pack.code()) {
            case "FR" -> dsn(salaries, emp, libellePeriode);
            case "US" -> formulaire941(salaries, emp, libellePeriode);
            case "MG" -> declarationMg(salaries, emp, libellePeriode);
            default -> throw new FormatNonPorteException(pack.code());
        };
    }

    /** `?? 0` du Node : une cotisation absente vaut zéro, elle n'annule pas la ligne. */
    private static double part(Cumuls a, String code, int index) {
        double[] c = a.cotisations().get(code);
        return c == null ? 0 : c[index];
    }

    /** `(x ?? "période").replace(/[^0-9A-Za-z-]/g, "")` — nettoyage du nom de fichier. */
    private static String periodePourNomFichier(String libelle, String defaut) {
        return (libelle != null ? libelle : defaut).replaceAll("[^0-9A-Za-z-]", "");
    }

    // ── 🇺🇸 États-Unis — IRS Form 941 ──────────────────────────────────────
    private static Remise formulaire941(List<Salarie> salaries, Map<String, String> emp, String periode) {
        Cumuls a = agreger(salaries);
        double ss = r2(part(a, "SS", 0) + part(a, "SS", 1));
        double medicare = r2(part(a, "MEDICARE", 0) + part(a, "MEDICARE", 1));
        double retenuFederal = a.incomeTax();
        double totalImpots = r2(retenuFederal + ss + medicare);
        String trimestre = periode != null ? periode : "AAAA-Qn";

        List<String> l = new java.util.ArrayList<>();
        l.add("IRS FORM 941 — Employer's QUARTERLY Federal Tax Return");
        l.add("EIN: " + ou(emp, "ein", "00-0000000") + "    Name: " + ou(emp, "name", "EMPLOYER")
                + "    Quarter: " + trimestre);
        l.add("");
        l.add("Line 1: Number of employees: " + a.count());
        l.add("Line 2: Wages, tips, and other compensation: " + montant(a.gross()));
        l.add("Line 3: Federal income tax withheld: " + montant(retenuFederal));
        l.add("Line 5a: Taxable social security wages: " + montant(a.gross()) + " x 12.4% = "
                + montant(a.gross() * 0.124) + " (declared: " + montant(ss) + ")");
        l.add("Line 5c: Taxable Medicare wages & tips: " + montant(a.gross()) + " x 2.9% = "
                + montant(a.gross() * 0.029) + " (declared: " + montant(medicare) + ")");
        l.add("Line 5e: Total social security and Medicare taxes: " + montant(r2(ss + medicare)));
        l.add("Line 6: Total taxes before adjustments (3 + 5e): " + montant(totalImpots));
        l.add("Line 10: Total taxes after adjustments: " + montant(totalImpots));
        l.add("Line 12: Total taxes after adjustments and credits: " + montant(totalImpots));
        l.add("");
        l.add("FUTA (Form 940, separate): employer " + montant(part(a, "FUTA", 1)));

        return new Remise("US", "IRS Form 941", "IRS",
                "IRS-941-" + periodePourNomFichier(periode, "period") + ".txt", "text/plain",
                String.join("\n", l),
                new Resume(a.count(), a.gross(), retenuFederal, totalCotisations(a), totalImpots));
    }

    // ── 🇲🇬 Madagascar — IRSA (DGI) + CNaPS ────────────────────────────────
    private static Remise declarationMg(List<Salarie> salaries, Map<String, String> emp, String periode) {
        Cumuls a = agreger(salaries);
        double cnapsSal = part(a, "CNAPS", 0);
        double cnapsPat = part(a, "CNAPS", 1);
        double ostieSal = part(a, "OSTIE", 0);
        double ostiePat = part(a, "OSTIE", 1);
        double irsa = a.incomeTax();
        String mois = periode != null ? periode : "AAAA-MM";

        List<String> l = new java.util.ArrayList<>();
        l.add("DECLARATION MENSUELLE — DGI / CNaPS (Madagascar)");
        l.add("NIF: " + ou(emp, "nif", "0000000000") + "   N° CNaPS employeur: "
                + ou(emp, "cnapsNumber", "000000") + "   Employeur: " + ou(emp, "name", "EMPLOYEUR"));
        l.add("Periode: " + mois + "   Devise: MGA   Effectif: " + a.count());
        l.add("");
        l.add("— IRSA (Impot sur les Revenus Salariaux et Assimiles) — a verser a la DGI —");
        l.add("  Base salariale (brut): " + montant(a.gross()));
        l.add("  IRSA retenu (bareme): " + montant(irsa));
        l.add("");
        l.add("— CNaPS (Prevoyance sociale) —");
        l.add("  Part salariale (1%): " + montant(cnapsSal));
        l.add("  Part patronale (13%): " + montant(cnapsPat));
        l.add("  Total CNaPS: " + montant(r2(cnapsSal + cnapsPat)));
        l.add("");
        l.add("— OSTIE (Sante au travail) —");
        l.add("  Part salariale: " + montant(ostieSal) + "   Part patronale: " + montant(ostiePat));
        l.add("");
        l.add("TOTAL A VERSER: " + montant(r2(irsa + cnapsSal + cnapsPat + ostieSal + ostiePat)) + " MGA");

        return new Remise("MG", "IRSA + CNaPS (DGI)", "DGI",
                "MG-IRSA-CNaPS-" + periodePourNomFichier(periode, "periode") + ".txt", "text/plain",
                String.join("\n", l),
                new Resume(a.count(), a.gross(), irsa, totalCotisations(a),
                        r2(irsa + totalCotisations(a))));
    }

    /** Cumuls de la <strong>période</strong> (et non de l'année) — c'est ce qu'agrège le Node. */
    private record Cumuls(int count, double gross, double incomeTax,
                          Map<String, double[]> cotisations, Map<String, String> libelles) {
    }

    private static Cumuls agreger(List<Salarie> salaries) {
        // LinkedHashMap : l'ordre d'insertion décide de l'ordre des blocs S21.G00.23 du fichier.
        Map<String, double[]> cot = new LinkedHashMap<>();
        Map<String, String> libelles = new LinkedHashMap<>();
        double brut = 0;
        double impot = 0;
        for (Salarie s : salaries) {
            MoteurPaie.ResultatPaie p = s.payroll();
            brut = r2(brut + p.period().gross());
            impot = r2(impot + p.period().incomeTax());
            for (MoteurPaie.LigneCotisation l : p.employeeContributions()) {
                cumuler(cot, libelles, l.code(), l.label(), l.period(), 0);
            }
            for (MoteurPaie.LigneCotisation l : p.employerContributions()) {
                cumuler(cot, libelles, l.code(), l.label(), 0, l.period());
            }
        }
        return new Cumuls(salaries.size(), brut, impot, cot, libelles);
    }

    private static void cumuler(Map<String, double[]> cot, Map<String, String> libelles,
                                String code, String label, double salariale, double patronale) {
        double[] c = cot.computeIfAbsent(code, k -> new double[2]);
        libelles.putIfAbsent(code, label);
        c[0] = r2(c[0] + salariale);
        c[1] = r2(c[1] + patronale);
    }

    private static double totalCotisations(Cumuls a) {
        double t = 0;
        for (double[] c : a.cotisations().values()) {
            t = r2(t + c[0] + c[1]);
        }
        return t;
    }

    private static String rubrique(String code, Object valeur) {
        return code + ",'" + valeur + "'";
    }

    /** Deux décimales, séparateur point — comme {@code toFixed(2)}, quelle que soit la locale. */
    private static String montant(double n) {
        return String.format(Locale.ROOT, "%.2f", r2(n));
    }

    private static String ou(Map<String, String> m, String cle, String defaut) {
        String v = m.get(cle);
        return v != null ? v : defaut;
    }

    private static Remise dsn(List<Salarie> salaries, Map<String, String> emp, String libellePeriode) {
        Cumuls a = agreger(salaries);
        String brut = (libellePeriode == null ? "" : libellePeriode).replace("-", "");
        String periode = brut.isEmpty() ? "AAAAMM" : brut.substring(0, Math.min(6, brut.length()));

        List<String> lignes = new java.util.ArrayList<>();
        // S10 — envoi (émetteur et logiciel).
        lignes.add(rubrique("S10.G00.00.001", ou(emp, "siren", "000000000")));
        lignes.add(rubrique("S10.G00.00.002", "01"));
        lignes.add(rubrique("S10.G00.00.003", "01"));
        lignes.add(rubrique("S10.G00.00.005", "NexuVINA"));
        lignes.add(rubrique("S10.G00.00.006", "1.0"));
        lignes.add(rubrique("S10.G00.01.001", ou(emp, "raisonSociale", "ETABLISSEMENT")));
        // S20 — déclaration mensuelle normale.
        lignes.add(rubrique("S20.G00.05.001", "01"));
        lignes.add(rubrique("S20.G00.05.002", "01"));
        lignes.add(rubrique("S20.G00.05.003", periode));
        lignes.add(rubrique("S20.G00.05.004", "01"));
        lignes.add(rubrique("S20.G00.05.005", periode + "01"));
        // S21.G00.06 / 11 — entreprise et établissement.
        lignes.add(rubrique("S21.G00.06.001", ou(emp, "siren", "000000000")));
        lignes.add(rubrique("S21.G00.06.002", ou(emp, "nic", "00000")));
        lignes.add(rubrique("S21.G00.06.003", ou(emp, "ape", "0000Z")));
        lignes.add(rubrique("S21.G00.11.001", ou(emp, "nic", "00000")));
        lignes.add(rubrique("S21.G00.11.003", ou(emp, "ape", "0000Z")));
        lignes.add(rubrique("S21.G00.11.004", ou(emp, "adresse", "ADRESSE")));
        lignes.add(rubrique("S21.G00.11.006", ou(emp, "codePostal", "00000")));
        lignes.add(rubrique("S21.G00.11.007", ou(emp, "ville", "VILLE")));

        for (int i = 0; i < salaries.size(); i++) {
            Salarie s = salaries.get(i);
            MoteurPaie.ResultatPaie p = s.payroll();
            // `"X".split(" ")` de JavaScript : sans espace, le prénom est `undefined` et le
            // gabarit `${undefined}` écrirait « undefined ». Le Node retombe alors sur "PRENOM"
            // via `?? "PRENOM"` — on reproduit ce repli.
            String nomComplet = s.name() != null ? s.name() : "SALARIE " + (i + 1);
            String[] parties = nomComplet.split(" ");
            String nom = parties.length > 0 ? parties[0] : "SALARIE" + (i + 1);
            String prenom = parties.length > 1 ? parties[1] : "PRENOM";

            String nir = s.id() != null ? s.id() : nirParDefaut(i);
            lignes.add(rubrique("S21.G00.30.001", nir));
            lignes.add(rubrique("S21.G00.30.002", nom));
            lignes.add(rubrique("S21.G00.30.004", prenom));
            lignes.add(rubrique("S21.G00.40.001", periode + "01"));
            lignes.add(rubrique("S21.G00.40.007", "01"));
            lignes.add(rubrique("S21.G00.50.001", periode + "28"));
            lignes.add(rubrique("S21.G00.50.002", montant(p.period().net())));
            lignes.add(rubrique("S21.G00.51.011", "001"));
            lignes.add(rubrique("S21.G00.51.013", montant(p.period().gross())));
            lignes.add(rubrique("S21.G00.50.011", montant(p.period().incomeTax())));
        }

        for (Map.Entry<String, double[]> e : a.cotisations().entrySet()) {
            lignes.add(rubrique("S21.G00.23.001", e.getKey()));
            lignes.add(rubrique("S21.G00.23.004", montant(e.getValue()[0] + e.getValue()[1])));
        }

        // S90 — le total compte cette ligne-ci, d'où le « +1 ».
        lignes.add(rubrique("S90.G00.90.001", String.valueOf(lignes.size() + 1)));
        lignes.add(rubrique("S90.G00.90.002", "1"));

        double cotis = totalCotisations(a);
        return new Remise("FR", "DSN (phase 3)", "URSSAF", "DSN-" + periode + ".dsn", "text/plain",
                String.join("\r\n", lignes),
                new Resume(a.count(), a.gross(), a.incomeTax(), cotis, r2(cotis + a.incomeTax())));
    }

    /** `` `0000000000000${i+1}`.slice(-13) `` — treize caractères, remplis à gauche par des zéros. */
    private static String nirParDefaut(int i) {
        String s = "0000000000000" + (i + 1);
        return s.substring(s.length() - 13);
    }
}
