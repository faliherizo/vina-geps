package ca.vina.nexu.effectifs.domaine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <strong>Les axes des relevés statistiques d'effectifs</strong> (P-RF-1) — jumeau de
 * {@code backend/src/effectifs/releves/domaine.ts}.
 *
 * <p>Chaque fait d'effectifs porte les mêmes axes : sa période (jour, semaine, mois), son unité et ses
 * ancêtres, la personne et ses attributs d'emploi, le milieu et la nature du quart. Les calculer ici,
 * une fois, garantit qu'une même personne tombe dans le même groupe quelle que soit la source lue.
 */
public final class RelevesEffectifs {

    private RelevesEffectifs() {
    }

    /** Les sources d'effectifs du constructeur de rapports, par leur code au catalogue. */
    public enum Source {
        HEURES("effectifs_heures"), JOURNEES("effectifs_journees"), POINTAGES("effectifs_pointages"), PLACES("effectifs_places"),
        ABSENCES("effectifs_absences"),
        /** Addenda 8, M-18 : l'horaire de chacun, une ligne par quart tenu. */
        HORAIRES("effectifs_horaires"),
        /** Addenda 8, M-18 : le budget d'heures de la campagne AGPH, confronté aux heures planifiées et pourvues de la semaine. */
        BUDGET("effectifs_budget");

        private final String code;

        Source(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Source de(String code) {
            return java.util.Arrays.stream(values()).filter(s -> s.code.equals(code)).findFirst()
                    .orElseThrow(() -> new EffectifsInvalideException("source de relevé inconnue : « " + code + " »"));
        }
    }

    /** Nombre de niveaux d'unité exposés comme axes : au-delà, l'unité elle-même suffit. */
    public static final int NIVEAUX_UNITE = 3;

    private static final int LONGUEUR_MOIS = 7;
    private static final double MINUTES_PAR_HEURE = 60;
    private static final int MINUTES_HEURE = 60;
    private static final int MINUTES_PAR_JOUR = 1440;

    /** Une période de relevé, bornes comprises (AAAA-MM-JJ). */
    public record Periode(String du, String au) {

        public boolean contient(String date) {
            return date.compareTo(du) >= 0 && date.compareTo(au) <= 0;
        }
    }

    /** Les axes de période d'une date : le jour, le lundi de sa semaine, son mois. */
    public static Map<String, Object> axesDePeriode(String date) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jour", date);
        m.put("semaine", Planification.lundiDe(date));
        m.put("mois", date.substring(0, LONGUEUR_MOIS));
        return m;
    }

    /** Le chemin d'une unité, de la racine à elle-même ; une boucle de parents s'arrête au premier retour. */
    public static List<String> cheminDe(Map<String, String> parents, String code) {
        List<String> chemin = new ArrayList<>();
        for (String u = code; u != null && !chemin.contains(u); u = parents.get(u)) {
            chemin.addFirst(u);
        }
        return chemin;
    }

    /** Les axes d'organisation d'une unité : elle, sa parente, ses ancêtres par niveau depuis la racine. */
    public static Map<String, Object> axesDUnite(Map<String, String> parents, String code) {
        List<String> chemin = cheminDe(parents, code);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unite", code);
        m.put("uniteParente", chemin.size() > 1 ? chemin.get(chemin.size() - 2) : null);
        for (int n = 1; n <= NIVEAUX_UNITE; n++) {
            m.put("uniteNiveau" + n, n <= chemin.size() ? chemin.get(n - 1) : null);
        }
        return m;
    }

    /** Une personne telle qu'un relevé la lit : son matricule, son nom, ses attributs d'emploi. */
    public record PersonneLue(String matricule, String nom, String corpsEmploiCode, String titreEmploiCode, String statutEmploiCode,
                              String conventionCode) { }

    public static Map<String, Object> axesDePersonne(PersonneLue p) {
        Map<String, Object> m = new HashMap<>();
        m.put("ressource", p == null ? null : p.matricule() + " — " + p.nom());
        m.put("corpsEmploi", p == null ? null : p.corpsEmploiCode());
        m.put("titreEmploi", p == null ? null : p.titreEmploiCode());
        m.put("statutEmploi", p == null ? null : p.statutEmploiCode());
        m.put("convention", p == null ? null : p.conventionCode());
        return m;
    }

    /** Le quart d'une place : la nature et le code du milieu. */
    public record QuartLu(String natureCode, String milieu) { }

    public static Map<String, Object> axesDeQuart(QuartLu q) {
        Map<String, Object> m = new HashMap<>();
        m.put("milieu", q == null ? null : q.milieu());
        m.put("natureQuart", q == null ? null : q.natureCode());
        return m;
    }

    /** Les jours d'un intervalle fermé, bornés à la période demandée. */
    public static List<String> joursEntre(String du, String au, Periode borne) {
        LocalDate debut = LocalDate.parse(du.compareTo(borne.du()) < 0 ? borne.du() : du);
        LocalDate fin = LocalDate.parse(au.compareTo(borne.au()) > 0 ? borne.au() : au);
        List<String> jours = new ArrayList<>();
        for (LocalDate j = debut; !j.isAfter(fin); j = j.plusDays(1)) {
            jours.add(j.toString());
        }
        return jours;
    }

    /** Des minutes en heures décimales, à deux décimales : un relevé se lit en heures. */
    public static Number heures(long minutes) {
        BigDecimal h = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(MINUTES_PAR_HEURE), 2, RoundingMode.HALF_UP).stripTrailingZeros();
        return h.scale() <= 0 ? (Number) h.longValueExact() : (Number) h.doubleValue();
    }

    /** La durée d'un quart, en minutes ; un quart de nuit qui passe minuit finit le lendemain. */
    public static int dureeMinutes(int debut, int fin) {
        return fin > debut ? fin - debut : fin + MINUTES_PAR_JOUR - debut;
    }

    /** Des minutes depuis minuit en {@code HH:MM} ; 1440 (minuit du lendemain) se lit {@code 24:00}. */
    public static String hhmm(int minutes) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / MINUTES_HEURE, minutes % MINUTES_HEURE);
    }

    /** Une semaine de budget : son lundi, l'unité et le corps d'emploi ; l'écart est planifié moins budget. */
    public record SemaineBudget(String lundi, String uniteCode, String corpsEmploiCode) { }

    /** Les heures d'une semaine de budget : budgétées (nulles sans campagne), planifiées (toutes les places) et pourvues. */
    public record HeuresSemaine(double budget, long minutesPlanifiees, long minutesPourvues) {
        public Number ecart() {
            return heures(Math.round(minutesPlanifiees - budget * MINUTES_HEURE));
        }
    }
}
