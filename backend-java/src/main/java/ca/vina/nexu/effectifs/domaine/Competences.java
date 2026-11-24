package ca.vina.nexu.effectifs.domaine;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <strong>Les compétences et leurs échéances</strong> (AO 26-015-DO, addenda 8, M-04) : permis, accréditations, formations
 * obligatoires. Elles sont entrées dans la Solution, avec leur date d'échéance ; une compétence exigée par le corps
 * d'emploi et échue à la date du quart rend la personne inadmissible, <em>selon une règle paramétrable</em> : le domaine
 * calcule les faits, la règle de l'organisme décide. Pur : ni base ni horloge.
 */
public final class Competences {

    /** Attribut du référentiel {@code competences} : les corps d'emploi qui l'exigent (vide = suivie, non exigée). */
    public static final String ATTRIBUT_CORPS = "corps";
    /** Le fait « jours avant l'échéance » quand aucune compétence exigée n'échoit : assez loin pour ne rien déclencher. */
    public static final int JOURS_SANS_ECHEANCE = 9999;
    /** L'horizon par défaut du relevé des échéances : ce qui échoit dans les deux mois. */
    public static final int HORIZON_PAR_DEFAUT_JOURS = 60;
    private static final int HORIZON_MAX_JOURS = 3650;
    private static final java.util.regex.Pattern ENTIER = java.util.regex.Pattern.compile("^\\d{1,4}$");

    private Competences() {
    }

    /** Une compétence que détient une personne : obtenue tel jour, valide jusqu'à son échéance incluse (nulle = sans échéance). */
    public record Detenue(String ressourceId, String code, String obtenueLe, String echeance) {

        /** Valide le jour {@code date} : obtenue au plus tard ce jour-là, et pas encore échue. */
        public boolean valideLe(String date) {
            return obtenueLe.compareTo(date) <= 0 && (echeance == null || echeance.compareTo(date) >= 0);
        }
    }

    /** L'état d'une compétence au relevé : échue, à échoir dans l'horizon, ou valide au-delà. */
    public enum Etat {
        ECHUE("echue"), A_ECHOIR("a_echoir"), VALIDE("valide");

        private final String code;

        Etat(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** Ce que les compétences disent d'une personne pour un quart : les exigées qui manquent, les jours avant la prochaine échéance. */
    public record Faits(List<String> manquantes, int joursAvantEcheance) {
        /** Aucune compétence exigée : rien ne manque, rien n'échoit. */
        public static final Faits AUCUNE = new Faits(List.of(), JOURS_SANS_ECHEANCE);

        public Faits {
            manquantes = List.copyOf(manquantes);
        }

        public static Faits de(List<String> exigees, List<Detenue> detenues, String date) {
            return new Faits(Competences.manquantes(exigees, detenues, date), Competences.joursAvantEcheance(exigees, detenues, date));
        }
    }

    /** D'où viennent les faits d'une personne pour un quart : le référentiel de l'organisme et ses compétences détenues. */
    @FunctionalInterface
    public interface Source {
        /** Aucune compétence n'est exigée nulle part : le comportement d'un organisme qui n'en déclare pas. */
        Source AUCUNE = (tenantId, ressourceId, corpsEmploiCode, date) -> Faits.AUCUNE;

        Faits faits(String tenantId, String ressourceId, String corpsEmploiCode, String date);
    }

    /** Une ligne du relevé des échéances. */
    public record Echeance(Detenue competence, Etat etat, long jours) { }

    /** Une détention saisie : la date d'obtention est exigée, l'échéance la suit ou manque. */
    public static Detenue saisie(String ressourceId, String code, Object obtenueLe, Object echeance) {
        String obtenue = Referentiels.dateValide(obtenueLe, "obtenueLe");
        String fin = echeance == null || "".equals(echeance) ? null : Referentiels.dateValide(echeance, "echeance");
        if (fin != null && fin.compareTo(obtenue) < 0) {
            throw new EffectifsInvalideException("« echeance » (" + fin + ") précède « obtenueLe » (" + obtenue + ")");
        }
        return new Detenue(ressourceId, code, obtenue, fin);
    }

    /** Les codes exigés que la personne n'a pas, ou n'a plus, le jour {@code date} — triés. */
    public static List<String> manquantes(List<String> exigees, List<Detenue> detenues, String date) {
        Map<String, Detenue> parCode = detenues.stream().collect(Collectors.toMap(Detenue::code, Function.identity(), (a, b) -> a));
        return exigees.stream().filter(code -> !Optional.ofNullable(parCode.get(code)).map(d -> d.valideLe(date)).orElse(false)).sorted().toList();
    }

    /** Les jours qui restent, à la date du quart, avant la première échéance d'une compétence exigée et encore valide. */
    public static int joursAvantEcheance(List<String> exigees, List<Detenue> detenues, String date) {
        return detenues.stream().filter(d -> exigees.contains(d.code()) && d.echeance() != null && d.valideLe(date))
                .mapToLong(d -> jours(date, d.echeance())).mapToInt(Math::toIntExact).min().orElse(JOURS_SANS_ECHEANCE);
    }

    /** L'horizon du relevé : un nombre de jours borné ; absent, deux mois. */
    public static int horizon(String brut) {
        if (brut == null || brut.isBlank()) {
            return HORIZON_PAR_DEFAUT_JOURS;
        }
        String t = brut.trim();
        if (!ENTIER.matcher(t).matches() || Integer.parseInt(t) > HORIZON_MAX_JOURS) {
            throw new EffectifsInvalideException("« dans » : un nombre de jours entre 0 et " + HORIZON_MAX_JOURS);
        }
        return Integer.parseInt(t);
    }

    /** Le relevé : les compétences échues ou qui échoient dans l'horizon, la plus pressante d'abord. */
    public static List<Echeance> releve(List<Detenue> detenues, String aujourdhui, int horizonJours) {
        return detenues.stream().filter(d -> d.echeance() != null).map(d -> {
            long jours = jours(aujourdhui, d.echeance());
            Etat etat = jours < 0 ? Etat.ECHUE : jours <= horizonJours ? Etat.A_ECHOIR : Etat.VALIDE;
            return new Echeance(d, etat, jours);
        }).filter(e -> e.etat() != Etat.VALIDE).sorted(Comparator.comparingLong(Echeance::jours)).toList();
    }

    private static long jours(String de, String a) {
        return ChronoUnit.DAYS.between(LocalDate.parse(de), LocalDate.parse(a));
    }
}
