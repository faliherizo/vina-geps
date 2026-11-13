package ca.vina.nexu.effectifs.domaine;

import ca.vina.nexu.partage.temps.Iso;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * <strong>AGPH et remplacements long terme</strong> — AO 26-015-DO, lot R7 (E-CH-1, E-CH-7 à E-CH-10, P-CH-1 à
 * P-CH-7, P-CH-9) ; le pur de {@code backend/src/effectifs/agph/domaine.ts}.
 *
 * <p>L'attribution générale des postes et des horaires se déroule en campagne annuelle : les rangs se figent par
 * ancienneté à l'ouverture — un recalcul pendant le tour de rôle déplacerait des rangs déjà servis —, chacun choisit
 * <strong>un</strong> poste dans son délai, ou le choix se fait pour lui. Le temps entre en paramètre (ISO), jamais
 * par l'horloge.
 */
public final class Agph {

    public static final int DELAI_MIN = 5;
    /** Une semaine : au-delà, le tour de rôle d'une unité durerait des mois. */
    public static final int DELAI_MAX = 10_080;
    private static final long SECONDES_PAR_MINUTE = 60;
    /** Où vont les postes et les quarts que l'AGPH ou un remplacement n'a pas pourvus (E-CH-10, E-GR-2) : le journal le nomme. */
    public static final String VERS_GESTION_REGULIERE = "gestion régulière";
    /** Au-delà, le budget d'une unité n'est plus une saisie mais une faute de frappe. */
    public static final int BUDGET_MAX = 100_000;

    private Agph() {
    }

    /** Les états d'une campagne, et le geste qui manque pour passer au suivant. */
    public enum EtatCampagne {
        PREPARATION("ouvrir"), DEPOT("démarrer le tour de rôle"), CHOIX("attendre la fin du tour de rôle"), ATTRIBUTION("réviser ou clore"), REVISION("clore"),
        CLOSE("rien");

        private final String geste;

        EtatCampagne(String geste) {
            this.geste = geste;
        }

        /**
         * Les gestes manuels : ouvrir, démarrer, réviser, clore. Le passage CHOIX → ATTRIBUTION se fait seul, au dernier
         * rang servi. La révision (E-CH-1) est facultative : on clôt directement une campagne sans correction à faire.
         */
        List<EtatCampagne> suivantsManuels() {
            return switch (this) {
                case PREPARATION -> List.of(DEPOT);
                case DEPOT -> List.of(CHOIX);
                case ATTRIBUTION -> List.of(REVISION, CLOSE);
                case REVISION -> List.of(CLOSE);
                case CHOIX, CLOSE -> List.of();
            };
        }

        /** Les disponibilités se déposent pendant le dépôt, et se corrigent pendant la révision (E-CH-1). */
        public boolean depotOuvert() {
            return this == DEPOT || this == REVISION;
        }
    }

    public enum EtatRang { EN_ATTENTE, A_SON_TOUR, A_CHOISI, PASSE }

    public enum ModeChoix { EMPLOYE, PLANIFICATEUR, AUTOMATIQUE }

    /**
     * {@code NON_COMBLE} est un état final (E-CH-9) : la fenêtre s'est fermée sans intéressé admissible, ou l'encadrement a
     * choisi de ne pas combler. Ses quarts vacants sont alors transférés à la gestion régulière (E-CH-10) — {@code transfereLe}
     * le date, {@code quartsTransferes} les compte.
     */
    public enum EtatRemplacement { OUVERT, ATTRIBUE, NON_COMBLE }

    public enum OrigineRemplacement { ABSENCE, POSTE_VACANT }

    public record Candidat(String ressourceId, double anciennete, String matricule) { }

    public record RangCalcule(Candidat candidat, int rang) { }

    public record Interesse(String ressourceId, double anciennete, String deposeLe) { }

    /**
     * L'admissibilité d'une personne à un poste ou à un remplacement : les motifs lisibles, et le verdict du
     * moteur quand il a été consulté (nul quand une exigence dure — le corps d'emploi — a tranché avant lui).
     */
    public record Jugement(boolean admissible, List<String> motifs, Regles.Verdict verdict) {
        public Jugement {
            motifs = List.copyOf(motifs);
        }

        public static Jugement refuse(String motif) {
            return new Jugement(false, List.of(motif), null);
        }

        public static Jugement de(Regles.Verdict verdict) {
            return new Jugement(!Regles.estBloquant(verdict.action()), verdict.motifs().stream().map(Regles.Motif::message).toList(), verdict);
        }

        public String message() {
            return String.join(" ; ", motifs);
        }

        /** Refuse un jugement défavorable : 409 motivé par le moteur, ou 422 pour l'exigence dure. */
        public void exiger() {
            if (admissible) {
                return;
            }
            if (verdict != null) {
                throw new RefusParRegleException(message(), verdict);
            }
            throw new EffectifsInvalideException(message());
        }
    }

    /** Un geste manuel n'est admis que vers l'état qui suit ; le refus dit quel geste manque. */
    public static EtatCampagne etatSuivant(EtatCampagne etat, EtatCampagne vise) {
        if (!etat.suivantsManuels().contains(vise)) {
            throw new EffectifsInvalideException("une campagne en " + etat.name().toLowerCase() + " ne passe pas à " + vise.name().toLowerCase()
                    + " : il faut d'abord " + etat.geste);
        }
        return vise;
    }

    /** {@code Number(v)} du Node : NaN pour ce qui n'est pas un nombre, 0 pour un texte vide. */
    public static double nombre(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (!(v instanceof String s)) {
            return Double.NaN;
        }
        try {
            return s.isBlank() ? 0 : Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    public static boolean estEntier(double n) {
        return !Double.isInfinite(n) && n == Math.rint(n);
    }

    /**
     * Le budget d'heures hebdomadaires de la campagne (E-CH-1) : nul quand il n'est pas fixé (absent ou texte vide), sinon
     * un nombre d'heures positif et borné.
     */
    public static Double budgetDe(Object v) {
        if (v == null || v instanceof String s && s.isEmpty()) {
            return null;
        }
        double n = nombre(v);
        if (Double.isNaN(n) || Double.isInfinite(n) || n <= 0 || n > BUDGET_MAX) {
            throw new EffectifsInvalideException("« budgetHeuresSemaine » : un nombre d'heures de 0 exclu à " + BUDGET_MAX);
        }
        return n;
    }

    public static int delaiDe(Object v) {
        double n = nombre(v);
        if (!estEntier(n) || n < DELAI_MIN || n > DELAI_MAX) {
            throw new EffectifsInvalideException("« delaiChoixMinutes » : de " + DELAI_MIN + " à " + DELAI_MAX + " minutes");
        }
        return (int) n;
    }

    /** Le rang : ancienneté décroissante, puis matricule — un ordre total, donc reproductible. */
    public static List<RangCalcule> ordonner(List<Candidat> candidats) {
        List<Candidat> tries = candidats.stream()
                .sorted(Comparator.comparingDouble(Candidat::anciennete).reversed().thenComparing(Candidat::matricule)).toList();
        return IntStream.range(0, tries.size()).mapToObj(i -> new RangCalcule(tries.get(i), i + 1)).toList();
    }

    public static String ajouterMinutes(String iso, long minutes) {
        return Iso.de(Instant.parse(iso).plusSeconds(minutes * SECONDES_PAR_MINUTE));
    }

    /** Le tour est-il écoulé à cet instant ? (fin exclue : à l'instant de la fin, c'est terminé) */
    public static boolean tourEcoule(String tourFin, String maintenant) {
        return !Instant.parse(maintenant).isBefore(Instant.parse(tourFin));
    }

    /** Le statut d'emploi après la campagne (P-CH-5 à P-CH-7) : celui du poste obtenu, ou le temps partiel de l'organisme. */
    public static String statutApres(String statutDuPoste, String statutTempsPartiel) {
        return statutDuPoste != null ? statutDuPoste : statutTempsPartiel;
    }

    // ── Remplacements long terme ────────────────────────────────────────────

    public static OrigineRemplacement origineDe(Object v) {
        return Arrays.stream(OrigineRemplacement.values()).filter(o -> o.name().equals(v)).findFirst()
                .orElseThrow(() -> new EffectifsInvalideException("« origine » : "
                        + Arrays.stream(OrigineRemplacement.values()).map(Enum::name).collect(Collectors.joining(", "))));
    }

    /** La fenêtre de manifestation est-elle encore ouverte ? */
    public static boolean fenetreOuverte(String finManifestation, String maintenant) {
        return Instant.parse(maintenant).isBefore(Instant.parse(finManifestation));
    }

    /**
     * Un instant saisi, normalisé comme {@code new Date(v).toISOString()} : vide s'il ne se lit pas. Une date seule
     * vaut minuit UTC, comme en JavaScript.
     */
    public static String instantDe(Object v) {
        if (!(v instanceof String s)) {
            return "";
        }
        try {
            return Iso.de(OffsetDateTime.parse(s).toInstant());
        } catch (DateTimeParseException e) {
            return dateSeule(s);
        }
    }

    private static String dateSeule(String s) {
        try {
            return Iso.de(LocalDate.parse(s).atStartOfDay().toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException e) {
            return "";
        }
    }

    /** L'ordre d'attribution d'un remplacement long terme (P-CH-9) : ancienneté, puis premier arrivé, puis identifiant. */
    public static List<Interesse> classerInteresses(List<Interesse> interesses) {
        return interesses.stream().sorted(Comparator.comparingDouble(Interesse::anciennete).reversed()
                .thenComparing(Interesse::deposeLe).thenComparing(Interesse::ressourceId)).toList();
    }
}
