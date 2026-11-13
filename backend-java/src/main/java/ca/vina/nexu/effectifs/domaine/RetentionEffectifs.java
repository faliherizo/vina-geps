package ca.vina.nexu.effectifs.domaine;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <strong>La politique de rétention des effectifs</strong> — AO 26-015-DO, lot R11 (Loi 25 : ne garder que le
 * temps nécessaire) ; le pur de {@code backend/src/effectifs/retention/domaine.ts}.
 * <p>
 * L'organisme fixe, par classe de données, une durée en années. Rien ne se purge sans politique : l'absence de
 * durée vaut « conserver ». Les requêtes de suppression vivent dans l'adaptateur ; le domaine décide seulement
 * des classes, des durées admises et de la date limite.
 */
public final class RetentionEffectifs {

    /** Une durée de rétention se compte en années entières, bornées comme au Node. */
    public static final int ANNEES_MIN = 1;
    public static final int ANNEES_MAX = 30;

    private RetentionEffectifs() {
    }

    /** Les classes de données, dans l'ordre du Node (l'aperçu les rend dans cet ordre). */
    public enum Classe {
        HORAIRES("Horaires (quarts, places, échanges)"),
        PRESENCE("Présence (pointages, preuves, horodatages externes)"),
        TEMPS("Feuilles de temps approuvées"),
        ABSENCES("Absences d'effectifs"),
        JOURNAUX("Journal des décisions et évaluations des règles");

        private final String libelle;

        Classe(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }
    }

    /** Une ligne de l'aperçu : la durée de la classe, sa date limite et ce qu'elle purgerait aujourd'hui. */
    public record Ligne(Classe classe, String libelle, Integer annees, String limite, long aPurger) {
    }

    public static Classe classeDe(String v) {
        return Arrays.stream(Classe.values()).filter(c -> c.name().equals(v)).findFirst()
                .orElseThrow(() -> new EffectifsInvalideException("classe : "
                        + Arrays.stream(Classe.values()).map(Classe::name).collect(Collectors.joining(", "))));
    }

    /**
     * Des durées par classe, de 1 à 30 ans ; une valeur nulle retire la durée (conserver). L'ordre de la demande
     * est gardé : le journal consigne la politique telle qu'elle a été posée.
     */
    public static Map<Classe, Integer> politiqueDe(Object brut) {
        if (!(brut instanceof Map<?, ?> carte)) {
            throw new EffectifsInvalideException("« politique » : un objet { classe: années }");
        }
        Map<Classe, Integer> p = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : carte.entrySet()) {
            Classe c = classeDe(String.valueOf(e.getKey()));
            p.put(c, e.getValue() == null ? null : annees(c, e.getValue()));
        }
        return p;
    }

    /** Le {@code Number(v)} du Node : un nombre, un texte numérique ou un booléen ; tout le reste est refusé. */
    private static int annees(Classe c, Object v) {
        double n = switch (v) {
            case Number x -> x.doubleValue();
            case Boolean b -> b ? 1 : 0;
            case String s -> nombre(s.strip());
            default -> Double.NaN;
        };
        if (n != Math.rint(n) || n < ANNEES_MIN || n > ANNEES_MAX) {
            throw new EffectifsInvalideException("« " + c.name() + " » : un nombre entier d'années, de 1 à 30");
        }
        return (int) n;
    }

    private static double nombre(String s) {
        if (s.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** La date limite d'une classe : aujourd'hui moins la durée, en années civiles (le 29 février devient le 28). */
    public static String limiteDe(String aujourdhui, int annees) {
        return LocalDate.parse(aujourdhui).minusYears(annees).toString();
    }
}
