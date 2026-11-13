package ca.vina.nexu.effectifs.domaine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <b>Profils de pointage configurables</b> — AO 26-015-DO, E-GP-1, E-GP-2 et P-GP-1. Jumeau de
 * {@code backend/src/effectifs/presence/profils.ts}.
 *
 * <p>Les trois exigences nomment la même chose : « selon des profils configurables ». Un paramédic en véhicule
 * pointe en temps réel ; un cadre en garde déclare après coup ; un employé de soutien fait les deux. Sans profil,
 * l'application impose un seul comportement à tous.</p>
 *
 * <p>Le profil <b>le plus particulier</b> s'applique : titre d'emploi avant corps, avant statut, avant convention,
 * avant unité, avant l'organisme — l'ordre des règles ({@link PorteeRegle}), pour n'en retenir qu'un.</p>
 *
 * <p>Pur : il décide, il ne lit ni n'écrit rien, et reçoit le temps écoulé plutôt que de lire l'horloge.</p>
 */
public final class ProfilsPointage {

    /** La présence préalable par défaut : un quart d'heure, comme le prévoyait le calcul. */
    public static final int PRESENCE_PREALABLE_DEFAUT_MINUTES = 15;
    /** Au-delà, une présence préalable n'est plus une préparation : c'est un autre quart. */
    public static final int PRESENCE_PREALABLE_MAX_MINUTES = 240;
    /** Au-delà, une déclaration arrive après la paie : il n'y a plus rien à corriger. */
    public static final int FENETRE_A_POSTERIORI_MAX_JOURS = 90;
    /** Le code réservé au profil appliqué quand aucun n'est configuré. */
    public static final String CODE_PAR_DEFAUT = "__defaut__";

    private static final long JOUR_MS = Duration.ofDays(1).toMillis();

    /** Le mode de saisie qu'un profil impose. C'est lui qui rend le profil opposable plutôt que descriptif. */
    public enum ModeSaisie {
        TEMPS_REEL, A_POSTERIORI, MIXTE;

        private static final String ATTENDUS = Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));

        public String code() {
            return name();
        }

        public static ModeSaisie de(Object v) {
            return Arrays.stream(values()).filter(m -> m.name().equals(v)).findFirst()
                    .orElseThrow(() -> new EffectifsInvalideException("« mode » : " + ATTENDUS));
        }
    }

    public record ProfilPointage(String code, String libelle, ModeSaisie mode, PorteeRegle porteeType, String porteeCode,
                                 List<NaturePause> naturesNonPayees, boolean pausesManuelles, int presencePrealableMinutes,
                                 Integer fenetreAPosterioriJours, boolean actif) {

        public ProfilPointage {
            naturesNonPayees = List.copyOf(naturesNonPayees);
        }

        boolean vise(PorteesDuDossier d) {
            return actif && porteeType.vise(porteeCode, d);
        }
    }

    public record VerdictSaisie(boolean admise, List<String> motifs) {

        public VerdictSaisie {
            motifs = List.copyOf(motifs);
        }
    }

    /**
     * Le profil qui s'applique à défaut : <b>{@code MIXTE}</b>, pour reproduire le comportement d'avant les profils.
     * Un défaut plus strict aurait retiré une capacité acquise le jour de l'installation, sans que personne ne l'ait
     * demandé.
     */
    public static final ProfilPointage PROFIL_PAR_DEFAUT = new ProfilPointage(CODE_PAR_DEFAUT, "Profil par défaut (les deux modes)",
            ModeSaisie.MIXTE, PorteeRegle.ORGANISME, null, List.of(NaturePause.values()), false, PRESENCE_PREALABLE_DEFAUT_MINUTES, null, true);

    private ProfilsPointage() {
    }

    /**
     * <b>Valider un profil.</b> Une portée autre qu'{@code ORGANISME} exige son code : une portée « titre d'emploi »
     * sans titre ne viserait personne, et le profil paraîtrait configuré tout en ne s'appliquant jamais.
     */
    public static ProfilPointage valider(Map<String, Object> brut) {
        PorteeRegle portee = portee(brut.getOrDefault("porteeType", PorteeRegle.ORGANISME.code()));
        String code = texteOuNull(brut.get("porteeCode"));
        if (portee != PorteeRegle.ORGANISME && code == null) {
            throw new EffectifsInvalideException("une portée « %s » exige son code : sans lui, le profil ne viserait personne tout en paraissant configuré"
                    .formatted(portee.code()));
        }
        List<NaturePause> natures = natures(brut.get("naturesNonPayees"));
        // **Le défaut est MIXTE, et ce n'est pas un choix de confort** : un défaut TEMPS_REEL aurait retiré la saisie
        // après coup à tout le monde le jour de l'installation — une régression déguisée en valeur par défaut.
        ModeSaisie mode = ModeSaisie.de(brut.getOrDefault("mode", ModeSaisie.MIXTE.code()));
        Integer fenetre = fenetre(brut.get("fenetreAPosterioriJours"), mode);
        String identifiant = obligatoire(brut.get("code"), "code");
        String libelle = texteOuNull(brut.get("libelle"));
        return new ProfilPointage(identifiant, libelle == null ? identifiant : libelle, mode, portee, code, natures,
                Boolean.TRUE.equals(brut.get("pausesManuelles")),
                entier(brut.getOrDefault("presencePrealableMinutes", PRESENCE_PREALABLE_DEFAUT_MINUTES), "presencePrealableMinutes", PRESENCE_PREALABLE_MAX_MINUTES),
                fenetre, !Boolean.FALSE.equals(brut.get("actif")));
    }

    private static PorteeRegle portee(Object v) {
        return Arrays.stream(PorteeRegle.values()).filter(p -> p.code().equals(v)).findFirst()
                .orElseThrow(() -> new EffectifsInvalideException("« porteeType » : "
                        + Arrays.stream(PorteeRegle.values()).map(PorteeRegle::code).collect(Collectors.joining(", "))));
    }

    /** Une liste absente vaut toutes les natures ; une liste vide est refusée — aucune pause ne pourrait se déclarer. */
    private static List<NaturePause> natures(Object brutes) {
        List<?> liste = brutes instanceof List<?> l ? l : List.of(NaturePause.values()).stream().map(NaturePause::code).toList();
        LinkedHashSet<NaturePause> natures = new LinkedHashSet<>();
        liste.forEach(n -> natures.add(NaturePause.de(n, "« nature » :")));
        if (natures.isEmpty()) {
            throw new EffectifsInvalideException("un profil admet au moins une nature non payée : sans elle, aucune pause ne peut être déclarée");
        }
        return new ArrayList<>(natures);
    }

    /**
     * La fenêtre de saisie a posteriori, en jours ; <b>aucune par défaut</b> ({@code null}) — l'application n'en
     * imposait aucune avant les profils, et la poser refuserait le jour de l'installation ce qui passait la veille.
     * À zéro, elle interdirait toute déclaration : un mode qui en admet doit le dire.
     */
    private static Integer fenetre(Object brute, ModeSaisie mode) {
        if (brute == null) {
            return null;
        }
        int jours = entier(brute, "fenetreAPosterioriJours", FENETRE_A_POSTERIORI_MAX_JOURS);
        if (mode != ModeSaisie.TEMPS_REEL && jours == 0) {
            throw new EffectifsInvalideException("un profil « %s » exige une fenêtre de saisie non nulle : à zéro jour, toute déclaration après coup serait refusée"
                    .formatted(mode.code()));
        }
        return jours;
    }

    private static int entier(Object v, String sujet, int max) {
        double n = v instanceof Number x ? x.doubleValue() : nombre(v);
        if (n != Math.rint(n) || n < 0 || n > max) {
            throw new EffectifsInvalideException("« %s » : un entier de 0 à %d".formatted(sujet, max));
        }
        return (int) n;
    }

    private static double nombre(Object v) {
        try {
            return Double.parseDouble(String.valueOf(v).strip());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static String texteOuNull(Object v) {
        String t = v == null ? "" : String.valueOf(v).strip();
        return t.isEmpty() ? null : t;
    }

    private static String obligatoire(Object v, String sujet) {
        String t = texteOuNull(v);
        if (t == null) {
            throw new EffectifsInvalideException("« %s » est obligatoire".formatted(sujet));
        }
        return t;
    }

    /**
     * <b>Le profil d'une personne</b> : le plus particulier de ceux qui la visent. À spécificité égale, le <b>code
     * alphabétique</b> départage, et non l'ordre d'insertion — deux lectures doivent rendre le même profil.
     */
    public static ProfilPointage profilDe(List<ProfilPointage> profils, PorteesDuDossier d) {
        return profils.stream().filter(p -> p.vise(d))
                .min(Comparator.comparingInt((ProfilPointage p) -> p.porteeType().ordinal()).reversed().thenComparing(ProfilPointage::code))
                .orElse(PROFIL_PAR_DEFAUT);
    }

    /**
     * <b>Le profil admet-il ce mode de saisie ?</b> (E-GP-1, P-GP-1) C'est ici qu'il cesse d'être une étiquette.
     * {@code joursEcoules} est reçu : le domaine ne lit pas l'horloge.
     */
    public static VerdictSaisie admetSaisie(ProfilPointage p, boolean tempsReel, long joursEcoules) {
        List<String> motifs = new ArrayList<>();
        if (tempsReel && p.mode() == ModeSaisie.A_POSTERIORI) {
            motifs.add("le profil « %s » est à saisie après coup : le pointage en temps réel n'y est pas admis".formatted(p.libelle()));
        }
        if (!tempsReel && p.mode() == ModeSaisie.TEMPS_REEL) {
            motifs.add("le profil « %s » est à pointage en temps réel : la déclaration après coup n'y est pas admise".formatted(p.libelle()));
        }
        boolean horsFenetre = p.fenetreAPosterioriJours() != null && joursEcoules > p.fenetreAPosterioriJours();
        if (!tempsReel && p.mode() != ModeSaisie.TEMPS_REEL && horsFenetre) {
            motifs.add("la fenêtre de saisie du profil « %s » est de %d jour(s) : cette déclaration en a %d"
                    .formatted(p.libelle(), p.fenetreAPosterioriJours(), joursEcoules));
        }
        return new VerdictSaisie(motifs.isEmpty(), motifs);
    }

    /** Le profil admet-il cette nature non payée ? (E-GP-2) */
    public static VerdictSaisie admetNature(ProfilPointage p, NaturePause nature) {
        if (p.naturesNonPayees().contains(nature)) {
            return new VerdictSaisie(true, List.of());
        }
        String admises = p.naturesNonPayees().stream().map(NaturePause::libelle).collect(Collectors.joining(", "));
        return new VerdictSaisie(false, List.of("le profil « %s » n'admet pas la %s (admises : %s)".formatted(p.libelle(), nature.libelle(), admises)));
    }

    /** Les jours écoulés entre deux instants, arrondis vers le bas ; jamais négatifs. */
    public static long joursEcoules(String depuis, String maintenant) {
        return Math.max(0, Math.floorDiv(Instant.parse(maintenant).toEpochMilli() - Instant.parse(depuis).toEpochMilli(), JOUR_MS));
    }
}
