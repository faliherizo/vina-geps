package ca.vina.nexu.effectifs.domaine;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <strong>Planification des effectifs</strong> — AO 26-015-DO, lot R3 ; le pur de
 * {@code backend/src/effectifs/planification/domaine.ts}.
 *
 * <p>Un quart au sens du métier est un <strong>équipage</strong> : une plage, un milieu, une composition.
 * Chaque personne y occupe une <strong>place</strong> ; une place sans personne est vacante, et c'est la
 * vacance qui fait tout le reste. Une période ne revient jamais en arrière dans son cycle ; un équipage
 * est opérationnel si ses places pourvues atteignent son minimum (calculé, jamais stocké) ; une surcharge
 * ne commence jamais dans le passé ; une attribution forcée porte une justification. Heures en minutes
 * depuis minuit ; une fin qui précède le début passe minuit.
 */
public final class Planification {

    private static final long MINUTES_JOUR = 1440;
    public static final int PERIODE_JOURS_MAX = 366;
    public static final int PLACES_MAX = 50;
    public static final int CYCLE_SEMAINES_MAX = 12;
    public static final int SURCHARGE_JOURS_MAX = 90;
    public static final int JUSTIFICATION_MIN = 10;
    private static final int QUOTA_MAX = 10_000;

    private Planification() {
    }

    // ── Cycle d'une période ─────────────────────────────────────────────────

    public enum EtatPeriode {
        PREPARATION, COLLECTE, CONFECTION, PUBLIEE, CLOSE;

        /** Seules transitions admises ; la collecte peut être sautée (période sans dépôt de disponibilités). */
        public Set<EtatPeriode> suivants() {
            return switch (this) {
                case PREPARATION -> EnumSet.of(COLLECTE, CONFECTION);
                case COLLECTE -> EnumSet.of(CONFECTION);
                case CONFECTION -> EnumSet.of(PUBLIEE);
                case PUBLIEE -> EnumSet.of(CLOSE);
                case CLOSE -> EnumSet.noneOf(EtatPeriode.class);
            };
        }
    }

    public enum ModeAttribution { AUTOMATISE, MANUEL }

    public enum StatutOperationnel { OPERATIONNEL, SOUS_MINIMUM }

    public enum EtatPoste { BROUILLON, PUBLIE, ATTRIBUE, VACANT }

    public enum OrigineQuota { SURCHARGE, VERSION }

    static <E extends Enum<E>> E enumDe(Class<E> type, Object v, String nom) {
        return Arrays.stream(type.getEnumConstants()).filter(e -> e.name().equals(v)).findFirst()
                .orElseThrow(() -> new EffectifsInvalideException(nom + " inconnu : « " + v + " » (attendu : "
                        + Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", ")) + ")"));
    }

    public static EtatPeriode etatPeriodeDe(Object v) {
        return enumDe(EtatPeriode.class, v, "état de période");
    }

    public static ModeAttribution modeAttributionDe(Object v) {
        return enumDe(ModeAttribution.class, v, "mode d'attribution");
    }

    public static EtatPoste etatPosteDe(Object v) {
        return enumDe(EtatPoste.class, v, "état de poste");
    }

    public static void verifierTransition(EtatPeriode de, EtatPeriode vers) {
        if (!de.suivants().contains(vers)) {
            String admis = de.suivants().stream().map(Enum::name).collect(Collectors.joining(", "));
            throw new EffectifsInvalideException("une période « " + de + " » ne passe pas à « " + vers + " » (admis : " + (admis.isEmpty() ? "aucun" : admis) + ")");
        }
    }

    /** Une période close est en lecture seule ; publiée, elle n'accepte que des ajustements (E-GR-5). */
    public static boolean modifiable(EtatPeriode etat) {
        return etat != EtatPeriode.CLOSE;
    }

    public static boolean collecteOuverte(EtatPeriode etat) {
        return etat == EtatPeriode.COLLECTE;
    }

    // ── Dates et créneaux ───────────────────────────────────────────────────

    public static String dateDe(Object v, String champ) {
        return Referentiels.dateValide(v, champ);
    }

    static long jourEpoch(String date) {
        return LocalDate.parse(date).toEpochDay();
    }

    static String dateDuJour(long j) {
        return LocalDate.ofEpochDay(j).toString();
    }

    public static List<String> joursEntre(String debut, String fin) {
        List<String> out = new ArrayList<>();
        for (long j = jourEpoch(debut); j <= jourEpoch(fin); j++) {
            out.add(dateDuJour(j));
        }
        return out;
    }

    public static void verifierBornes(String debut, String fin) {
        if (fin.compareTo(debut) < 0) {
            throw new EffectifsInvalideException("la fin précède le début");
        }
        if (jourEpoch(fin) - jourEpoch(debut) + 1 > PERIODE_JOURS_MAX) {
            throw new EffectifsInvalideException("une période compte au plus " + PERIODE_JOURS_MAX + " jours");
        }
    }

    public record Plage(int debut, int fin) { }

    /**
     * Un entier comme {@code Number(v)} du Node l'entend : vrai vaut 1, un texte vide 0. {@code null} (un champ
     * absent, l'{@code undefined} du Node) n'en est pas un.
     */
    private static Integer entier(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Boolean b) {
            return b ? 1 : 0;
        }
        try {
            String texte = v.toString().trim();
            double d = v instanceof Number n ? n.doubleValue() : texte.isEmpty() ? 0 : Double.parseDouble(texte);
            return d == Math.rint(d) && !Double.isInfinite(d) ? (int) d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Plage creneauDe(Object debut, Object fin) {
        Integer d = entier(debut);
        Integer f = entier(fin);
        if (d == null || d < 0 || d > 1439) {
            throw new EffectifsInvalideException("« début » : minutes depuis minuit, de 0 à 1439");
        }
        if (f == null || f < 1 || f > 1440) {
            throw new EffectifsInvalideException("« fin » : minutes depuis minuit, de 1 à 1440");
        }
        if (d == f % 1440) {
            throw new EffectifsInvalideException("un quart ne peut pas durer zéro minute ni vingt-quatre heures pile");
        }
        return new Plage(d, f);
    }

    public static int dureeMinutes(int debut, int fin) {
        return fin > debut ? fin - debut : fin + 1440 - debut;
    }

    /** L'intervalle absolu d'un créneau, en minutes depuis l'époque — un quart de nuit déborde sur le lendemain. */
    public static long[] intervalle(String date, int debut, int fin) {
        long origine = jourEpoch(date) * MINUTES_JOUR + debut;
        return new long[]{origine, origine + dureeMinutes(debut, fin)};
    }

    public static boolean chevauchent(long[] a, long[] b) {
        return a[0] < b[1] && b[0] < a[1];
    }

    public record Creneau(String date, int debut, int fin) { }

    /** Deux quarts qui se touchent (18 h pile) ne se chevauchent pas. */
    public static boolean chevaucheUnQuart(Collection<Creneau> existants, Creneau nouveau) {
        long[] n = intervalle(nouveau.date(), nouveau.debut(), nouveau.fin());
        return existants.stream().anyMatch(c -> chevauchent(intervalle(c.date(), c.debut(), c.fin()), n));
    }

    private static double arrondi(double heures) {
        return Math.round(heures * 100) / 100.0;
    }

    /** Le repos, en heures, jusqu'au plus proche créneau tenu ; vide s'il n'y en a aucun. */
    public static Optional<Double> reposHeures(Collection<Creneau> existants, Creneau nouveau) {
        long[] n = intervalle(nouveau.date(), nouveau.debut(), nouveau.fin());
        return existants.stream().map(c -> {
            long[] i = intervalle(c.date(), c.debut(), c.fin());
            return chevauchent(i, n) ? 0L : Math.min(Math.abs(n[0] - i[1]), Math.abs(i[0] - n[1]));
        }).min(Comparator.naturalOrder()).map(m -> arrondi(m / 60.0));
    }

    /** Le lundi de la semaine ISO d'une date. */
    public static String lundiDe(String date) {
        long j = jourEpoch(date);
        long jourSemaine = ((j + 3) % 7 + 7) % 7;
        return dateDuJour(j - jourSemaine);
    }

    /** Heures planifiées dans la semaine ISO du nouveau créneau, nouveau compris. */
    public static double heuresSemaine(Collection<Creneau> existants, Creneau nouveau) {
        String lundi = lundiDe(nouveau.date());
        List<Creneau> tous = new ArrayList<>(existants);
        tous.add(nouveau);
        int minutes = tous.stream().filter(c -> lundiDe(c.date()).equals(lundi)).mapToInt(c -> dureeMinutes(c.debut(), c.fin())).sum();
        return arrondi(minutes / 60.0);
    }

    // ── Équipages ───────────────────────────────────────────────────────────

    public record Composition(String corpsEmploiCode, int nombre) { }

    /** La composition d'un équipage : au moins une place, au plus 50, un corps d'emploi par ligne. */
    public static List<Composition> compositionDe(Object v) {
        if (!(v instanceof List<?> lignes) || lignes.isEmpty()) {
            throw new EffectifsInvalideException("« composition » : au moins une ligne { corpsEmploiCode, nombre }");
        }
        Set<String> vus = new HashSet<>();
        List<Composition> out = lignes.stream().map(l -> ligneDe(l, vus)).toList();
        if (out.stream().mapToInt(Composition::nombre).sum() > PLACES_MAX) {
            throw new EffectifsInvalideException("un équipage compte au plus " + PLACES_MAX + " places");
        }
        return out;
    }

    private static Composition ligneDe(Object l, Set<String> vus) {
        Map<?, ?> m = l instanceof Map<?, ?> x ? x : Map.of();
        String corps = m.get("corpsEmploiCode") instanceof String s ? s.trim() : "";
        if (corps.isEmpty()) {
            throw new EffectifsInvalideException("chaque ligne de composition nomme un corps d'emploi");
        }
        if (!vus.add(corps)) {
            throw new EffectifsInvalideException("corps d'emploi en double dans la composition : " + corps);
        }
        Integer nombre = entier(m.get("nombre"));
        if (nombre == null || nombre < 1) {
            throw new EffectifsInvalideException("« nombre » de " + corps + " : entier positif");
        }
        return new Composition(corps, nombre);
    }

    public static int minimumDe(Object v, int places) {
        Integer m = v == null || "".equals(v) ? Integer.valueOf(places) : entier(v);
        if (m == null || m < 1 || m > places) {
            throw new EffectifsInvalideException("« minimum opérationnel » : entre 1 et " + places + " (le nombre de places)");
        }
        return m;
    }

    public static StatutOperationnel statutOperationnel(int pourvues, int minimum) {
        return pourvues >= minimum ? StatutOperationnel.OPERATIONNEL : StatutOperationnel.SOUS_MINIMUM;
    }

    public static double tauxPourcent(int pourvues, int total) {
        return total == 0 ? 100 : Math.round(pourvues * 1000.0 / total) / 10.0;
    }

    // ── Postes (lignes de quarts) ───────────────────────────────────────────

    /** Un cycle : de 1 à 12 semaines de 7 jours, chaque jour une plage (code) ou nul (congé). */
    public static List<List<String>> cycleDe(Object v) {
        if (!(v instanceof List<?> semaines) || semaines.isEmpty() || semaines.size() > CYCLE_SEMAINES_MAX) {
            throw new EffectifsInvalideException("« cycle » : de 1 à " + CYCLE_SEMAINES_MAX + " semaines");
        }
        List<List<String>> out = new ArrayList<>();
        for (int i = 0; i < semaines.size(); i++) {
            if (!(semaines.get(i) instanceof List<?> s) || s.size() != 7) {
                throw new EffectifsInvalideException("semaine " + (i + 1) + " du cycle : 7 jours attendus");
            }
            out.add(s.stream().map(j -> j == null || "".equals(j) ? null : String.valueOf(j)).collect(ArrayList<String>::new, ArrayList::add, ArrayList::addAll));
        }
        return out;
    }

    /** Les plages distinctes qu'un cycle utilise : le service les confronte au référentiel. */
    public static Set<String> plagesDuCycle(List<List<String>> cycle) {
        Set<String> p = new LinkedHashSet<>();
        cycle.forEach(s -> s.stream().filter(x -> x != null).forEach(p::add));
        return p;
    }

    /** La plage d'un jour selon un cycle qui démarre à {@code debutCycle}. */
    public static String plageDuJour(List<List<String>> cycle, String debutCycle, String date) {
        long ecart = jourEpoch(date) - jourEpoch(debutCycle);
        long longueur = cycle.size() * 7L;
        int rang = (int) ((ecart % longueur + longueur) % longueur);
        return cycle.get(rang / 7).get(rang % 7);
    }

    // ── Quotas et surcharges (E-GR-8, E-GR-9) ───────────────────────────────

    public record VersionQuota(int quota, String effectifDepuis, String effectifJusqua) { }

    public record Surcharge(int valeur, String depuis, String jusqua, String creeLe) { }

    public record QuotaEffectif(int quota, OrigineQuota origine) { }

    public static int quotaDe(Object v) {
        Integer q = entier(v);
        if (q == null || q < 0 || q > QUOTA_MAX) {
            throw new EffectifsInvalideException("« quota » : entier de 0 à 10 000");
        }
        return q;
    }

    /** Un instant ISO 8601 comme {@code Date.parse} du Node l'entend : avec décalage, en UTC, ou une date seule (minuit UTC). */
    public static Optional<Instant> instantDe(String s) {
        if (s == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(s).toInstant());
        } catch (DateTimeParseException e) {
            try {
                return Optional.of(LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant());
            } catch (DateTimeParseException ignore) {
                return Optional.empty();
            }
        }
    }

    private static Instant instant(String s) {
        return instantDe(s).orElseThrow(() -> new EffectifsInvalideException("« depuis » et « jusqu'à » : instants ISO 8601 attendus"));
    }

    /** Une surcharge commence maintenant ou plus tard, finit après son début, dure 90 jours au plus. */
    public static void verifierSurcharge(String depuis, String jusqua, String maintenant) {
        Instant d = instant(depuis);
        Instant j = instant(jusqua);
        if (d.isBefore(instant(maintenant))) {
            throw new EffectifsInvalideException("une surcharge ne peut pas être rétroactive : elle commence maintenant ou plus tard");
        }
        if (!j.isAfter(d)) {
            throw new EffectifsInvalideException("une surcharge finit après son début");
        }
        if (j.toEpochMilli() - d.toEpochMilli() > SURCHARGE_JOURS_MAX * 86_400_000L) {
            throw new EffectifsInvalideException("une surcharge dure " + SURCHARGE_JOURS_MAX + " jours au plus");
        }
    }

    /**
     * Le quota en vigueur à un instant : la surcharge active la plus récente, sinon la version datée en
     * vigueur ce jour-là, sinon aucun. Un instant passé relit toujours la même valeur.
     */
    public static Optional<QuotaEffectif> quotaEffectif(List<VersionQuota> versions, List<Surcharge> surcharges, String instant) {
        Instant t = instant(instant);
        Optional<Surcharge> active = surcharges.stream()
                .filter(s -> !instant(s.depuis()).isAfter(t) && t.isBefore(instant(s.jusqua())))
                .max(Comparator.comparing(Surcharge::creeLe));
        if (active.isPresent()) {
            return Optional.of(new QuotaEffectif(active.get().valeur(), OrigineQuota.SURCHARGE));
        }
        String jour = instant.substring(0, 10);
        return versions.stream().filter(x -> x.effectifDepuis().compareTo(jour) <= 0 && (x.effectifJusqua() == null || jour.compareTo(x.effectifJusqua()) < 0))
                .findFirst().map(x -> new QuotaEffectif(x.quota(), OrigineQuota.VERSION));
    }

    // ── Attribution forcée (E-GN-11) ────────────────────────────────────────

    public static String justificationDe(Object v) {
        String j = v instanceof String s ? s.trim() : "";
        if (j.length() < JUSTIFICATION_MIN) {
            throw new EffectifsInvalideException("forcer une attribution exige une justification d'au moins " + JUSTIFICATION_MIN + " caractères");
        }
        return j;
    }
}
