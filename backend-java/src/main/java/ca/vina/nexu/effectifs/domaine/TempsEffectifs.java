package ca.vina.nexu.effectifs.domaine;

import ca.vina.nexu.partage.crypto.Primitives;
import ca.vina.nexu.partage.temps.Iso;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static ca.vina.nexu.effectifs.domaine.Comportements.TypeHeure;

/**
 * <strong>Feuille de temps d'effectifs</strong> — AO 26-015-DO, lot R9 (E-GP-3, E-GP-5, E-GP-6, E-GP-8, E-RF-1 à E-RF-7,
 * E-RF-9, E-RF-11, E-RF-12, E-RF-14, P-RF-1) ; le pur de {@code backend/src/effectifs/temps/domaine.ts}.
 *
 * <p>La feuille regroupe des <strong>segments</strong> typés (types d'heures de l'organisme) : générés depuis l'horaire,
 * repris des pointages, ou saisis. Tout ce qui s'en déduit — totaux, heures supplémentaires, écart au planifié,
 * anomalies — se <strong>calcule à la lecture</strong> ; seul l'instantané des anomalies est figé à la soumission. Une
 * correction crée une version, jamais un écrasement. Aucune E/S : l'empreinte du lot de paie est un calcul.
 */
public final class TempsEffectifs {

    /** Le fuseau des jours de travail : celui des horaires planifiés. */
    public static final String FUSEAU = PresenceEffectifs.FUSEAU;
    /** Le premier palier d'approbation, celui où entre une feuille soumise. */
    public static final int PREMIER_PALIER = 1;
    /** Le palier d'une feuille qui n'attend aucune décision (brouillon, retournée). */
    public static final int AUCUN_PALIER = 0;
    public static final int TEXTE_MAX = 500;
    public static final int MOTIF_MIN = 5;
    public static final Parametres PARAMETRES_DEFAUT = new Parametres(8, 40, 1, 15, 5, ActionCorroboration.SIGNALER, ActionCorroboration.BLOQUER);

    private static final long MINUTE_MS = 60_000L;
    private static final long JOURNEE_MS = 24 * 3_600_000L;
    private static final int MINUTES_HEURE = 60;
    private static final double CENTIEMES = 100.0;
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    /** Les heures qui comptent comme travaillées (seuils d'heures supplémentaires) ; une absence payée est payée, pas travaillée. */
    private static final Set<TypeHeure> TRAVAILLEES = EnumSet.of(TypeHeure.TRAVAILLEE, TypeHeure.PAUSE_PAYEE, TypeHeure.PRESENCE_PREALABLE,
            TypeHeure.SUPPLEMENTAIRE);

    private TempsEffectifs() {
    }

    // ── Énumérations ─────────────────────────────────────────────────────────

    public enum StatutFeuille {
        BROUILLON, SOUMISE, RETOURNEE, APPROUVEE,
        /** Prise dans une paie traitée : plus rien ne la change — ni correction, ni rappel, ni retour. */
        PAYEE;

        /** Une feuille se corrige tant qu'elle n'est pas soumise, ou quand elle revient (E-GP-3). */
        public boolean estModifiable() {
            return this == BROUILLON || this == RETOURNEE;
        }
    }

    /**
     * D'où vient un segment : de l'horaire, des pointages, d'une saisie — ou d'un horodatage d'un logiciel tiers quand aucun
     * pointage n'existe (E-RF-13). {@code EXTERNE} se distingue de {@code GENERE} parce que la différence compte : un temps
     * constaté par une borne est une preuve, un temps déduit du planifié n'en est pas une.
     */
    public enum Source { GENERE, POINTAGE, SAISIE, EXTERNE }

    public enum CodeAnomalie {
        CHEVAUCHEMENT, ECART_PLANIFIE, SANS_POINTAGE, POINTAGE_EN_ATTENTE, POINTAGE_INCOMPLET, NON_CORROBORE, SOURCES_DIVERGENTES, EXTERNE_A_CONFIRMER, REGLE
    }

    /** La décision de l'encadrement au palier courant. */
    public enum Decision {
        APPROUVER, RETOURNER,
        /** La personne (ou qui a soumis pour elle) reprend sa feuille avant la paie, pour la corriger. */
        RAPPELER,
        /** L'encadrement rouvre une feuille approuvée, avant la paie, et la renvoie à la personne avec un motif. */
        ROUVRIR;

        public String actionJournal() {
            return "temps." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Les regroupements des relevés (E-RF-7, P-RF-1), avec leur code d'URL. */
    public enum Regroupement {
        UNITE("unite"), RESSOURCE("ressource"), TYPE_HEURE("typeHeure"), SEMAINE("semaine");

        private final String code;

        Regroupement(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** Les paramètres numériques réglables de l'organisme, avec leurs bornes, dans l'ordre des composantes de {@link Parametres}. */
    public enum Parametre {
        SEUIL_JOUR("seuilJourHeures", 1, 24), SEUIL_SEMAINE("seuilSemaineHeures", 1, 168), PALIERS("paliersApprobation", 1, 3),
        TOLERANCE("toleranceEcartMinutes", 0, 240), TOLERANCE_CORROBORATION("toleranceCorroborationMinutes", 0, 60);

        private final String code;
        private final int min;
        private final int max;

        Parametre(String code, int min, int max) {
            this.code = code;
            this.min = min;
            this.max = max;
        }

        public String code() {
            return code;
        }
    }

    /** Les sorts réglables d'un constat de corroboration (E-RF-13). */
    public enum ChoixCorroboration {
        NON_CORROBORE("actionNonCorrobore"), SOURCES_DIVERGENTES("actionSourcesDivergentes");

        private final String code;

        ChoixCorroboration(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    // ── Valeurs ──────────────────────────────────────────────────────────────

    /**
     * Les valeurs par défaut suivent la Loi sur les normes du travail (40 h par semaine) ; l'organisme les règle.
     * {@code toleranceCorroborationMinutes} : l'écart admis entre un pointage et l'horodatage tiers qui le confirme, et
     * entre deux sources.
     */
    public record Parametres(int seuilJourHeures, int seuilSemaineHeures, int paliersApprobation, int toleranceEcartMinutes,
                             int toleranceCorroborationMinutes, ActionCorroboration actionNonCorrobore, ActionCorroboration actionSourcesDivergentes) {

        Parametres avec(Parametre p, int v) {
            int[] n = {seuilJourHeures, seuilSemaineHeures, paliersApprobation, toleranceEcartMinutes, toleranceCorroborationMinutes};
            n[p.ordinal()] = v;
            return new Parametres(n[0], n[1], n[2], n[3], n[4], actionNonCorrobore, actionSourcesDivergentes);
        }

        Parametres avec(ChoixCorroboration c, ActionCorroboration a) {
            return switch (c) {
                case NON_CORROBORE -> new Parametres(seuilJourHeures, seuilSemaineHeures, paliersApprobation, toleranceEcartMinutes,
                        toleranceCorroborationMinutes, a, actionSourcesDivergentes);
                case SOURCES_DIVERGENTES -> new Parametres(seuilJourHeures, seuilSemaineHeures, paliersApprobation, toleranceEcartMinutes,
                        toleranceCorroborationMinutes, actionNonCorrobore, a);
            };
        }

        /** La tolérance de corroboration, en millisecondes. */
        public long toleranceCorroborationMs() {
            return toleranceCorroborationMinutes * MINUTE_MS;
        }
    }

    /** Ce que les calculs lisent d'un segment, qu'il vienne de la base ou d'un essai. */
    public interface SegmentDeTemps {
        String date();

        String debut();

        String fin();

        String typeHeureCode();

        TypeHeure comportement();

        Source source();

        String placeId();
    }

    public record SegmentTemps(String id, String date, String debut, String fin, String typeHeureCode, TypeHeure comportement, Source source,
                               String placeId) implements SegmentDeTemps { }

    /** Un segment lu avec sa filiation de versions : la forme du contrat HTTP. */
    public record SegmentLu(String id, String date, String debut, String fin, String typeHeureCode, TypeHeure comportement, Source source,
                            String placeId, int version, String remplaceId, String motif, String creeLe, String creePar, String sourceExterne,
                            String confirmeLe, String confirmePar, String description, Map<String, Object> attributs) implements SegmentDeTemps { }

    public record SegmentSaisi(String date, String debut, String fin, String typeHeureCode) { }

    /** Un quart publié de la personne : le planifié, en instants UTC. */
    public record QuartPlanifie(String placeId, String date, String natureCode, String debutPrevu, String finPrevue) { }

    /** Le planifié et le déclaré d'un quart, en minutes. */
    public record EcartPlanifie(String placeId, String date, String natureCode, String debutPrevu, String finPrevue, long planifie, long declare,
                                long ecart) { }

    public record Anomalie(CodeAnomalie code, String date, String message, boolean bloquante) { }

    public record HeuresSupplementaires(Map<String, Long> parJour, long total) { }

    /** L'issue d'une décision : le statut et le palier de la feuille après elle. */
    public record Issue(StatutFeuille statut, int palier) { }

    /** Les codes de paie : par type d'heure (à défaut, le code du type), le régulier et le supplémentaire. */
    public record CodesPaie(Map<String, String> parType, String codeRegulier, String codeSupplementaire) {
        String codePaieDe(String typeHeureCode) {
            return parType.getOrDefault(typeHeureCode, typeHeureCode);
        }
    }

    /** {@code heures} : un entier quand il l'est, comme le JSON du Node (8, pas 8.0). */
    public record LignePaie(String matricule, String date, String codePaie, Number heures) { }

    // ── Statuts et paliers d'approbation ─────────────────────────────────────

    public static StatutFeuille statutDe(Object v) {
        return Arrays.stream(StatutFeuille.values()).filter(s -> s.name().equals(v)).findFirst()
                .orElseThrow(() -> new EffectifsInvalideException("« statut » : " + noms(StatutFeuille.values())));
    }

    private static String noms(Enum<?>[] valeurs) {
        return Arrays.stream(valeurs).map(Enum::name).collect(Collectors.joining(", "));
    }

    public static void verifierModifiable(StatutFeuille s) {
        if (!s.estModifiable()) {
            throw new EffectifsInvalideException(switch (s) {
                case APPROUVEE -> "feuille approuvée : rouvrez-la ou rappelez-la avant la paie pour la corriger";
                case PAYEE -> FEUILLE_PAYEE;
                default -> "feuille soumise : elle attend une décision ; rappelez-la ou faites-la retourner pour la corriger";
            });
        }
    }

    public static void verifierSoumission(StatutFeuille s) {
        if (!s.estModifiable()) {
            throw new EffectifsInvalideException(s == StatutFeuille.PAYEE ? FEUILLE_PAYEE
                    : "une feuille " + (s == StatutFeuille.APPROUVEE ? "approuvée" : "déjà soumise") + " ne se soumet pas");
        }
    }

    /** L'issue d'une approbation au palier courant : le palier suivant, ou l'approbation finale. */
    public static Issue apresApprobation(StatutFeuille s, int palier, int paliersRequis) {
        if (s != StatutFeuille.SOUMISE) {
            throw new EffectifsInvalideException("seule une feuille soumise s'approuve");
        }
        return palier >= paliersRequis ? new Issue(StatutFeuille.APPROUVEE, palier) : new Issue(StatutFeuille.SOUMISE, palier + 1);
    }

    public static final String FEUILLE_PAYEE = "feuille payée : elle ne se modifie plus (une erreur se corrige à la paie suivante)";

    /**
     * Le rappel : avant la paie, une feuille soumise ou approuvée revient au brouillon, pour être corrigée puis soumise de
     * nouveau. Une feuille payée ne revient jamais ; une feuille qui n'est pas partie n'a rien à rappeler.
     */
    public static Issue apresRappel(StatutFeuille s) {
        return switch (s) {
            case SOUMISE, APPROUVEE -> new Issue(StatutFeuille.BROUILLON, AUCUN_PALIER);
            case PAYEE -> throw new EffectifsInvalideException(FEUILLE_PAYEE);
            case BROUILLON, RETOURNEE -> throw new EffectifsInvalideException("une feuille qui n'est pas soumise n'a rien à rappeler");
        };
    }

    /** La réouverture : avant la paie, une feuille approuvée revient à la personne, retournée, avec le motif de l'encadrement. */
    public static Issue apresReouverture(StatutFeuille s) {
        return switch (s) {
            case APPROUVEE -> new Issue(StatutFeuille.RETOURNEE, AUCUN_PALIER);
            case PAYEE -> throw new EffectifsInvalideException(FEUILLE_PAYEE);
            case BROUILLON, SOUMISE, RETOURNEE -> throw new EffectifsInvalideException("seule une feuille approuvée se rouvre ; une feuille soumise se retourne");
        };
    }

    /** Le verrou de la paie : seule une feuille approuvée se paie, une fois. */
    public static Issue apresPaie(StatutFeuille s) {
        if (s != StatutFeuille.APPROUVEE) {
            throw new EffectifsInvalideException(s == StatutFeuille.PAYEE ? "feuille déjà payée" : "seule une feuille approuvée entre dans une paie");
        }
        return new Issue(StatutFeuille.PAYEE, AUCUN_PALIER);
    }

    public static Issue apresRetour(StatutFeuille s) {
        if (s != StatutFeuille.SOUMISE) {
            throw new EffectifsInvalideException("seule une feuille soumise se retourne");
        }
        return new Issue(StatutFeuille.RETOURNEE, AUCUN_PALIER);
    }

    // ── Paramètres ───────────────────────────────────────────────────────────

    /**
     * Part des valeurs courantes ; chaque clé numérique présente est un entier dans ses bornes (un {@code null} explicite vaut 0,
     * comme {@code Number(null)}), chaque sort de corroboration présent est une {@link ActionCorroboration}.
     */
    public static Parametres parametresDe(Map<String, Object> brut, Parametres actuels) {
        Parametres p = numeriques(brut, actuels);
        for (ChoixCorroboration cle : ChoixCorroboration.values()) {
            if (brut.containsKey(cle.code())) {
                p = p.avec(cle, actionDe(brut.get(cle.code()), cle.code()));
            }
        }
        return p;
    }

    private static ActionCorroboration actionDe(Object v, String cle) {
        return Arrays.stream(ActionCorroboration.values()).filter(a -> a.name().equals(v)).findFirst()
                .orElseThrow(() -> new EffectifsInvalideException("« " + cle + " » : " + noms(ActionCorroboration.values())));
    }

    private static Parametres numeriques(Map<String, Object> brut, Parametres actuels) {
        Parametres p = actuels;
        for (Parametre cle : Parametre.values()) {
            if (!brut.containsKey(cle.code())) {
                continue;
            }
            double n = nombre(brut.get(cle.code()));
            if (n != Math.rint(n) || n < cle.min || n > cle.max) {
                throw new EffectifsInvalideException("« " + cle.code() + " » : entier de " + cle.min + " à " + cle.max);
            }
            p = p.avec(cle, (int) n);
        }
        return p;
    }

    /** Un nombre comme {@code Number(v)} du Node ; NaN pour ce qui n'en est pas un. */
    static double nombre(Object v) {
        return switch (v) {
            case null -> 0;
            case Number n -> n.doubleValue();
            case Boolean b -> b ? 1 : 0;
            case String s when s.isBlank() -> 0;
            case String s -> texteEnNombre(s.strip());
            default -> Double.NaN;
        };
    }

    private static double texteEnNombre(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    // ── Segments ─────────────────────────────────────────────────────────────

    private static long ms(String iso) {
        return Planification.instantDe(iso).orElseThrow().toEpochMilli();
    }

    public static long minutesDe(String debut, String fin) {
        return Math.round((ms(fin) - ms(debut)) / (double) MINUTE_MS);
    }

    /** Les heures payées : tout, sauf la pause non payée. */
    public static boolean estPaye(TypeHeure c) {
        return c != TypeHeure.PAUSE_NON_PAYEE;
    }

    /** Le comportement du temps que les pointages déduisent. */
    public static TypeHeure comportementDu(PresenceEffectifs.TypeSegment type) {
        return switch (type) {
            case TRAVAIL -> TypeHeure.TRAVAILLEE;
            case PAUSE -> TypeHeure.PAUSE_NON_PAYEE;
            case PRESENCE_PREALABLE -> TypeHeure.PRESENCE_PREALABLE;
        };
    }

    private static String instant(Object v, String champ) {
        String iso = v instanceof String s ? Planification.instantDe(s).map(Iso::de).orElse(null) : null;
        if (iso == null) {
            throw new EffectifsInvalideException("« " + champ + " » : instant ISO 8601");
        }
        return iso;
    }

    /** Un segment saisi : date du jour de travail, instants valides dans l'ordre, 24 h au plus, un type. */
    public static SegmentSaisi segmentSaisi(Map<String, Object> brut) {
        if (!(brut.get("date") instanceof String date) || !DATE.matcher(date).matches()) {
            throw new EffectifsInvalideException("« date » : AAAA-MM-JJ");
        }
        String debut = instant(brut.get("debut"), "debut");
        String fin = instant(brut.get("fin"), "fin");
        if (fin.compareTo(debut) <= 0) {
            throw new EffectifsInvalideException("la fin doit suivre le début");
        }
        if (ms(fin) - ms(debut) > JOURNEE_MS) {
            throw new EffectifsInvalideException("un segment couvre 24 heures au plus");
        }
        String type = brut.get("typeHeureCode") instanceof String t ? t.strip() : "";
        if (type.isEmpty()) {
            throw new EffectifsInvalideException("« typeHeureCode » : le type d'heure du segment");
        }
        return new SegmentSaisi(date, debut, fin, type);
    }

    /** Un motif libre, nettoyé et borné ; nul s'il est vide. */
    public static String texteLibre(Object v) {
        String m = v instanceof String s ? s.strip() : "";
        m = m.length() > TEXTE_MAX ? m.substring(0, TEXTE_MAX) : m;
        return m.isEmpty() ? null : m;
    }

    /** Une correction se motive quand elle touche le temps d'un autre ou un temps prouvé (E-RF-14). */
    public static String motifDeCorrection(Object v, boolean exige) {
        String m = texteLibre(v);
        if (exige && (m == null || m.length() < MOTIF_MIN)) {
            throw new EffectifsInvalideException("« motif » : une correction du temps d'autrui ou d'un temps pointé se motive");
        }
        return m;
    }

    /** La date locale (Québec) d'un instant : le jour de travail d'un segment saisi après coup. */
    public static String dateLocaleDe(String iso) {
        return dateLocaleDe(iso, FUSEAU);
    }

    public static String dateLocaleDe(String iso, String fuseau) {
        return Instant.parse(iso).atZone(ZoneId.of(fuseau)).toLocalDate().toString();
    }

    /** Une anomalie bloquante empêche la soumission ; les autres s'expliquent et se jugent à l'approbation. */
    public static List<Anomalie> bloquantes(List<Anomalie> a) {
        return a.stream().filter(Anomalie::bloquante).toList();
    }

    // ── Calculs de la feuille ────────────────────────────────────────────────

    public static Map<String, Long> totauxParType(List<? extends SegmentDeTemps> segments) {
        Map<String, Long> t = new LinkedHashMap<>();
        segments.forEach(s -> t.merge(s.typeHeureCode(), minutesDe(s.debut(), s.fin()), Long::sum));
        return t;
    }

    public static Map<String, Long> minutesTravailleesParJour(List<? extends SegmentDeTemps> segments) {
        Map<String, Long> m = new LinkedHashMap<>();
        segments.stream().filter(s -> TRAVAILLEES.contains(s.comportement())).forEach(s -> m.merge(s.date(), minutesDe(s.debut(), s.fin()), Long::sum));
        return m;
    }

    /**
     * Les heures supplémentaires : l'excédent du jour, puis l'excédent de la semaine sur ce qui reste — une minute n'est
     * jamais comptée deux fois. Chaque jour reçoit sa part de l'excédent de la semaine, du dernier jour vers le premier
     * (les dernières heures sont celles qui dépassent).
     */
    public static HeuresSupplementaires heuresSupplementaires(List<? extends SegmentDeTemps> segments, Parametres p) {
        Map<String, Long> parJour = new LinkedHashMap<>();
        Map<String, List<Map.Entry<String, Long>>> semaines = new LinkedHashMap<>();
        minutesTravailleesParJour(segments).entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(j -> {
            long excedent = Math.max(0, j.getValue() - p.seuilJourHeures() * (long) MINUTES_HEURE);
            if (excedent > 0) {
                parJour.put(j.getKey(), excedent);
            }
            semaines.computeIfAbsent(Planification.lundiDe(j.getKey()), k -> new ArrayList<>()).add(Map.entry(j.getKey(), j.getValue() - excedent));
        });
        semaines.values().forEach(jours -> repartirLaSemaine(jours, p.seuilSemaineHeures() * (long) MINUTES_HEURE, parJour));
        // Une heure déclarée supplémentaire l'est même sous le seuil ; au-dessus, l'excédent la contient déjà.
        supplementairesDeclarees(segments).forEach((jour, minutes) -> parJour.merge(jour, minutes, Math::max));
        return new HeuresSupplementaires(parJour, parJour.values().stream().mapToLong(Long::longValue).sum());
    }

    /** Les minutes que la personne a déclarées elle-même en heures supplémentaires (type de comportement SUPPLEMENTAIRE), par jour. */
    public static Map<String, Long> supplementairesDeclarees(List<? extends SegmentDeTemps> segments) {
        Map<String, Long> m = new LinkedHashMap<>();
        segments.stream().filter(s -> s.comportement() == TypeHeure.SUPPLEMENTAIRE).forEach(s -> m.merge(s.date(), minutesDe(s.debut(), s.fin()), Long::sum));
        return m;
    }

    private static void repartirLaSemaine(List<Map.Entry<String, Long>> jours, long seuil, Map<String, Long> parJour) {
        long reste = Math.max(0, jours.stream().mapToLong(Map.Entry::getValue).sum() - seuil);
        // L'excédent ne dépasse jamais le total de la semaine : la boucle s'arrête avant le premier jour au plus tard.
        for (int i = jours.size() - 1; reste > 0; i--) {
            long part = Math.min(reste, jours.get(i).getValue());
            parJour.merge(jours.get(i).getKey(), part, Long::sum);
            reste -= part;
        }
    }

    /** Le planifié et le déclaré, quart par quart (E-GP-8) : le déclaré compte le temps payé rattaché à la place. */
    public static List<EcartPlanifie> ecartsAuPlanifie(List<? extends SegmentDeTemps> segments, List<QuartPlanifie> quarts) {
        return quarts.stream().map(q -> {
            long planifie = minutesDe(q.debutPrevu(), q.finPrevue());
            long declare = segments.stream().filter(s -> q.placeId().equals(s.placeId()) && estPaye(s.comportement()))
                    .mapToLong(s -> minutesDe(s.debut(), s.fin())).sum();
            return new EcartPlanifie(q.placeId(), q.date(), q.natureCode(), q.debutPrevu(), q.finPrevue(), planifie, declare, declare - planifie);
        }).toList();
    }

    /** Une durée lisible : {@code -3 h 00}. */
    static String hm(long m) {
        long a = Math.abs(m);
        return (m < 0 ? "-" : "") + a / MINUTES_HEURE + " h " + String.format(Locale.ROOT, "%02d", a % MINUTES_HEURE);
    }

    /** Les anomalies qui se voient sans règle : chevauchements, écarts au planifié hors tolérance, temps non pointé. */
    public static List<Anomalie> anomaliesDeBase(List<? extends SegmentDeTemps> segments, List<QuartPlanifie> quarts, int toleranceMinutes) {
        List<Anomalie> a = new ArrayList<>();
        List<? extends SegmentDeTemps> tries = segments.stream().sorted(Comparator.comparing(SegmentDeTemps::debut)).toList();
        for (int i = 1; i < tries.size(); i++) {
            if (tries.get(i).debut().compareTo(tries.get(i - 1).fin()) < 0) {
                a.add(new Anomalie(CodeAnomalie.CHEVAUCHEMENT, tries.get(i).date(), "deux segments se chevauchent le " + tries.get(i).date(), true));
            }
        }
        ecartsAuPlanifie(segments, quarts).stream().filter(e -> Math.abs(e.ecart()) > toleranceMinutes).forEach(e -> a.add(new Anomalie(
                CodeAnomalie.ECART_PLANIFIE, e.date(), "déclaré " + hm(e.declare()) + " pour " + hm(e.planifie()) + " planifiées (écart " + hm(e.ecart()) + ")",
                false)));
        joursDeSource(segments, Source.GENERE)
                .forEach(d -> a.add(new Anomalie(CodeAnomalie.SANS_POINTAGE, d, "le " + d + " n'a pas été pointé : le temps vient de l'horaire", false)));
        joursDeSource(segments, Source.EXTERNE).forEach(d -> a.add(new Anomalie(CodeAnomalie.SANS_POINTAGE, d,
                "le " + d + " n'a pas été pointé : le temps vient d'un horodatage externe, qui en est la seule preuve", false)));
        return a;
    }

    private static TreeSet<String> joursDeSource(List<? extends SegmentDeTemps> segments, Source source) {
        return segments.stream().filter(s -> s.source() == source).map(SegmentDeTemps::date).collect(Collectors.toCollection(TreeSet::new));
    }

    /** Le tri des anomalies d'une feuille : par date, puis par code. */
    public static List<Anomalie> triees(List<Anomalie> a) {
        return a.stream().sorted(Comparator.comparing(Anomalie::date).thenComparing(x -> x.code().name())).toList();
    }

    // ── Faits des règles ─────────────────────────────────────────────────────

    /** Ce qu'une journée expose aux règles : ses heures, celles de sa semaine, son écart au planifié et son pointage. */
    public record Journee(String date, boolean jourFerie) { }

    /** Les faits d'une journée, par contexte de règle, dans l'ordre du catalogue. */
    public static Map<ContexteRegle, Map<String, Object>> faitsDeLaJournee(List<? extends SegmentDeTemps> segments, List<QuartPlanifie> quarts, Journee j) {
        Map<String, Long> parJour = minutesTravailleesParJour(segments);
        String lundi = Planification.lundiDe(j.date());
        double heuresSemaine = parJour.entrySet().stream().filter(e -> Planification.lundiDe(e.getKey()).equals(lundi)).mapToLong(Map.Entry::getValue).sum()
                / (double) MINUTES_HEURE;
        double heuresJour = parJour.getOrDefault(j.date(), 0L) / (double) MINUTES_HEURE;
        Map<String, Object> feuille = new LinkedHashMap<>();
        feuille.put("heuresJour", heuresJour);
        feuille.put("heuresSemaine", heuresSemaine);
        feuille.put("ecartPlanifieMinutes", ecartsAuPlanifie(segments, quarts).stream().filter(e -> e.date().equals(j.date())).mapToLong(EcartPlanifie::ecart).sum());
        feuille.put("sansPointage", segments.stream().anyMatch(s -> s.date().equals(j.date()) && s.source() == Source.GENERE));
        Map<String, Object> supplementaires = new LinkedHashMap<>();
        supplementaires.put("heuresJour", heuresJour);
        supplementaires.put("heuresSemaine", heuresSemaine);
        supplementaires.put("jourFerie", j.jourFerie());
        Map<ContexteRegle, Map<String, Object>> faits = new LinkedHashMap<>();
        faits.put(ContexteRegle.FEUILLE_DE_TEMPS, feuille);
        faits.put(ContexteRegle.HEURES_SUPPLEMENTAIRES, supplementaires);
        return faits;
    }

    // ── Lot de paie (PFT) ────────────────────────────────────────────────────

    /**
     * Les lignes de paie d'une feuille : les minutes payées par jour et par code de paie, les heures supplémentaires
     * <strong>reclassées</strong> du travail régulier vers le code supplémentaire, déduction faite de celles déclarées comme telles.
     */
    public static List<LignePaie> lignesDePaie(String matricule, List<? extends SegmentDeTemps> segments, CodesPaie codes, Map<String, Long> hs) {
        Map<List<String>, Long> parCle = new LinkedHashMap<>();
        segments.stream().filter(s -> estPaye(s.comportement()))
                .forEach(s -> parCle.merge(List.of(s.date(), codes.codePaieDe(s.typeHeureCode())), minutesDe(s.debut(), s.fin()), Long::sum));
        Map<String, Long> declarees = supplementairesDeclarees(segments);
        hs.forEach((date, minutes) -> {
            // Les heures déclarées supplémentaires sont déjà à leur code : seul le reste se reclasse — sinon elles se paieraient deux fois majorées.
            long aReclasser = Math.max(0, minutes - declarees.getOrDefault(date, 0L));
            long deplace = Math.min(parCle.getOrDefault(List.of(date, codes.codeRegulier()), 0L), aReclasser);
            parCle.merge(List.of(date, codes.codeRegulier()), -deplace, Long::sum);
            parCle.merge(List.of(date, codes.codeSupplementaire()), deplace, Long::sum);
        });
        return parCle.entrySet().stream().filter(e -> e.getValue() > 0)
                .map(e -> new LignePaie(matricule, e.getKey().get(0), e.getKey().get(1), heures(e.getValue())))
                .sorted(Comparator.comparing(LignePaie::date).thenComparing(LignePaie::codePaie)).toList();
    }

    private static Number heures(long minutes) {
        double h = Math.round(minutes / (double) MINUTES_HEURE * CENTIEMES) / CENTIEMES;
        return h == Math.rint(h) ? (Number) (long) h : (Number) h;
    }

    /** L'empreinte d'un lot : SHA-256 de ses lignes dans un ordre stable — le destinataire la recalcule pour s'assurer du lot reçu. */
    public static String empreinteDe(List<LignePaie> lignes) {
        String texte = lignes.stream().map(l -> l.matricule() + ";" + l.date() + ";" + l.codePaie() + ";"
                + String.format(Locale.ROOT, "%.2f", l.heures().doubleValue())).collect(Collectors.joining("\n"));
        return HexFormat.of().formatHex(Primitives.sha256(texte.getBytes(StandardCharsets.UTF_8)));
    }

    // ── Rapports (E-RF-7, P-RF-1) ────────────────────────────────────────────

    public static Regroupement regroupementDe(Object v) {
        if (v == null || "".equals(v)) {
            return Regroupement.UNITE;
        }
        return Arrays.stream(Regroupement.values()).filter(r -> r.code().equals(v)).findFirst()
                .orElseThrow(() -> new EffectifsInvalideException("« regroupement » : "
                        + Arrays.stream(Regroupement.values()).map(Regroupement::code).collect(Collectors.joining(", "))));
    }
}
