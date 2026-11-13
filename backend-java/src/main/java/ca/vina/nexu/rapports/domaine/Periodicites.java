package ca.vina.nexu.rapports.domaine;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * <b>Périodicités et comparaison historique</b> (BF-132) — jumeau de {@code rapports/periodicites.ts}.
 *
 * <p>La période se calcule à partir d'une date de référence, jamais de l'horloge : un rapport dont
 * les bornes dépendent de l'horloge du serveur est intestable. Et une absence de donnée n'est
 * <b>pas</b> un zéro : « pas de ligne cardiologie ce trimestre » se rend {@code null}, parce que
 * l'écraser en 0 fabrique des écarts de −100 % sur des services fermés.
 */
public final class Periodicites {

    private Periodicites() {
    }

    public enum Periodicite {
        QUOTIDIEN("quotidien", "la journée"), HEBDOMADAIRE("hebdomadaire", "la semaine"), MENSUEL("mensuel", "le mois"),
        TRIMESTRIEL("trimestriel", "le trimestre"), ANNUEL("annuel", "l'année"), PERSONNALISE("personnalise", "une plage choisie");

        private final String code;
        private final String libelle;

        Periodicite(String code, String libelle) {
            this.code = code;
            this.libelle = libelle;
        }

        public String code() {
            return code;
        }

        public String libelle() {
            return libelle;
        }

        public static List<String> codes() {
            return Arrays.stream(values()).map(Periodicite::code).toList();
        }

        public static Periodicite de(Object brut) {
            String v = brut == null ? "" : String.valueOf(brut).trim();
            return Arrays.stream(values()).filter(p -> p.code.equals(v)).findFirst().orElseThrow(() -> new CatalogueRapports.RapportInvalideException(
                    "périodicité inconnue « " + v + " » — attendu : " + String.join(", ", codes())));
        }
    }

    /** Le point de comparaison : la période précédente (mars contre février) ou la même l'an passé. */
    public enum Contre {
        PRECEDENTE("precedente"), AN_PASSE("an_passe");

        private final String code;

        Contre(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Contre de(Object brut) {
            String v = brut == null ? PRECEDENTE.code : String.valueOf(brut);
            return Arrays.stream(values()).filter(c -> c.code.equals(v)).findFirst()
                    .orElseThrow(() -> new CatalogueRapports.RapportInvalideException("« contre » attend « precedente » ou « an_passe »"));
        }
    }

    public record Bornes(String du, String au) {
    }

    /** {@code null} = aucune ligne pour cette clé sur cette période. <b>Pas</b> zéro. */
    public record LigneComparee(Map<String, Object> cles, Map<String, Double> courant, Map<String, Double> precedent,
                                Map<String, Double> ecart, Map<String, Double> ecartPourcent) {
    }

    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final int MOIS_PAR_TRIMESTRE = 3;
    private static final double CENT = 100;

    private static LocalDate exigerDate(Object valeur, String role) {
        String v = valeur == null ? "" : String.valueOf(valeur).trim();
        if (DATE.matcher(v).matches()) {
            try {
                return LocalDate.parse(v);
            } catch (DateTimeException e) {
                // Une date de la bonne forme mais impossible (31 février) tombe dans le même refus.
            }
        }
        throw new CatalogueRapports.RapportInvalideException(role + " attend une date AAAA-MM-JJ");
    }

    private static Bornes bornes(LocalDate du, LocalDate au) {
        return new Bornes(du.toString(), au.toString());
    }

    /** Les bornes de la période qui <b>contient</b> la date de référence ; une plage personnalisée porte les siennes. */
    public static Bornes bornesDe(Periodicite p, Object reference) {
        LocalDate ref = exigerDate(reference, "la date de référence");
        return switch (p) {
            case QUOTIDIEN -> bornes(ref, ref);
            case HEBDOMADAIRE -> {
                LocalDate lundi = ref.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield bornes(lundi, lundi.plusDays(6));
            }
            case MENSUEL -> bornes(ref.withDayOfMonth(1), ref.with(TemporalAdjusters.lastDayOfMonth()));
            case TRIMESTRIEL -> {
                LocalDate premier = ref.withDayOfMonth(1).withMonth((ref.getMonthValue() - 1) / MOIS_PAR_TRIMESTRE * MOIS_PAR_TRIMESTRE + 1);
                yield bornes(premier, premier.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth()));
            }
            case ANNUEL -> bornes(ref.withDayOfYear(1), ref.with(TemporalAdjusters.lastDayOfYear()));
            case PERSONNALISE -> throw new CatalogueRapports.RapportInvalideException(
                    "une plage personnalisée porte ses propres bornes : fournissez « du » et « au »");
        };
    }

    public static Bornes bornesPersonnalisees(Object du, Object au) {
        LocalDate d = exigerDate(du, "le début de la période");
        LocalDate a = exigerDate(au, "la fin de la période");
        if (a.isBefore(d)) {
            throw new CatalogueRapports.RapportInvalideException("la fin de la période précède son début");
        }
        return bornes(d, a);
    }

    /**
     * La période comparable qui précède : la précédente du même calendrier (février pour mars), ou,
     * pour une plage personnalisée, la fenêtre de même durée immédiatement antérieure.
     */
    public static Bornes precedente(Periodicite p, Bornes b) {
        LocalDate du = LocalDate.parse(b.du());
        if (p == Periodicite.PERSONNALISE) {
            long jours = ChronoUnit.DAYS.between(du, LocalDate.parse(b.au())) + 1;
            return bornes(du.minusDays(jours), du.minusDays(1));
        }
        return bornesDe(p, du.minusDays(1).toString());
    }

    /** La même période un an plus tôt ; le 29 février devient le 28. */
    public static Bornes memePeriodeLAnPasse(Periodicite p, Bornes b) {
        if (p == Periodicite.PERSONNALISE) {
            return bornes(LocalDate.parse(b.du()).minusYears(1), LocalDate.parse(b.au()).minusYears(1));
        }
        return bornesDe(p, LocalDate.parse(b.du()).minusYears(1).toString());
    }

    public static Bornes anterieures(Periodicite p, Bornes courantes, Contre contre) {
        return contre == Contre.AN_PASSE ? memePeriodeLAnPasse(p, courantes) : precedente(p, courantes);
    }

    private static String cleDe(Map<String, Object> f, List<String> cles) {
        return String.join("\u0000", cles.stream().map(c -> f.get(c) == null ? "" : String.valueOf(f.get(c))).toList());
    }

    private static Double nombre(Map<String, Object> f, String m) {
        Object v = f == null ? null : f.get(m);
        if (v == null) {
            return null;
        }
        try {
            double d = v instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(v));
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double arrondir(double v) {
        return Math.round(v * CENT) / CENT;
    }

    /** Les deux côtés d'une clé : l'un peut manquer, et c'est justement ce qu'une comparaison doit montrer. */
    private record Paire(Map<String, Object> cles, Map<String, Object> courant, Map<String, Object> precedent) {
    }

    /** Compare deux jeux de lignes agrégées, groupe par groupe ; les clés absentes d'un côté apparaissent quand même. */
    public static List<LigneComparee> comparer(List<Map<String, Object>> courant, List<Map<String, Object>> precedent,
                                               List<String> cles, List<String> mesures) {
        Map<String, Paire> parCle = new LinkedHashMap<>();
        for (Map<String, Object> f : courant) {
            Paire p = parCle.get(cleDe(f, cles));
            parCle.put(cleDe(f, cles), new Paire(p == null ? clesDe(f, cles) : p.cles(), f, p == null ? null : p.precedent()));
        }
        for (Map<String, Object> f : precedent) {
            Paire p = parCle.get(cleDe(f, cles));
            parCle.put(cleDe(f, cles), new Paire(p == null ? clesDe(f, cles) : p.cles(), p == null ? null : p.courant(), f));
        }
        List<LigneComparee> lignes = new ArrayList<>();
        for (Paire p : parCle.values()) {
            lignes.add(ligne(p, mesures));
        }
        return lignes;
    }

    private static Map<String, Object> clesDe(Map<String, Object> f, List<String> cles) {
        Map<String, Object> m = new LinkedHashMap<>();
        cles.forEach(c -> m.put(c, f.get(c)));
        return m;
    }

    private static LigneComparee ligne(Paire p, List<String> mesures) {
        LigneComparee l = new LigneComparee(p.cles(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
        for (String m : mesures) {
            Double x = nombre(p.courant(), m);
            Double y = nombre(p.precedent(), m);
            l.courant().put(m, x);
            l.precedent().put(m, y);
            l.ecart().put(m, x == null || y == null ? null : arrondir(x - y));
            // Un dénominateur nul rend « indéterminé », comme les taux des indicateurs (ADR 0052).
            l.ecartPourcent().put(m, x == null || y == null || y == 0 ? null : arrondir(CENT * (x - y) / y));
        }
        return l;
    }
}
