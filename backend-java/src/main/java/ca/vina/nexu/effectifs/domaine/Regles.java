package ca.vina.nexu.effectifs.domaine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <strong>Le moteur de règles des effectifs</strong> — AO 26-015-DO, lot R2 (ADR 0048) ; le pur de
 * {@code backend/src/effectifs/regles/domaine.ts}.
 *
 * <p>Les règles des conventions collectives changent à chaque renouvellement : codées, elles seraient à
 * redévelopper. Ici une règle est une <strong>donnée versionnée</strong> évaluée par une fonction pure qui
 * <strong>explique</strong> sa décision (E-GN-12, E-GN-16, E-GN-9). Trois décisions :
 * <ul>
 *   <li><strong>le particulier précise le général</strong> : à clé égale, la portée la plus particulière
 *       remplace la plus générale ; deux clés différentes se combinent ;</li>
 *   <li><strong>l'action la plus restrictive l'emporte</strong>, et tous les motifs déclenchés sont rendus :
 *       l'employé corrige tout d'un coup ;</li>
 *   <li><strong>un fait manquant est une erreur</strong> ({@link ConditionsRegles}).</li>
 * </ul>
 * Aucune E/S, aucune horloge.
 */
public final class Regles {

    public static final int MESSAGE_MAX = 500;
    public static final int PRIORITE_MAX = 1000;

    private static final Pattern JETON = Pattern.compile("\\{([A-Za-z0-9_]+)\\}");
    private static final Pattern CLE = Pattern.compile("^[a-z0-9][a-z0-9-]{1,59}$");
    /** Précision d'abord, priorité ensuite : ce qui départage deux versions de même clé. */
    private static final Comparator<VersionRegle> PRECISION = Comparator.comparingInt((VersionRegle v) -> v.regle().porteeType().ordinal())
            .thenComparingInt(v -> v.contenu().priorite());
    /** Le plus particulier, puis le plus prioritaire, puis la clé : un ordre stable d'une évaluation à l'autre. */
    private static final Comparator<VersionRegle> ORDRE = PRECISION.reversed().thenComparing(v -> v.regle().cle());

    private Regles() {
    }

    /**
     * Ce que le contexte appelant soumet au moteur. Le moteur ne lit aucune table métier : l'appelant fournit
     * les faits et les portées de la personne.
     *
     * @param date    la date du fait : c'est elle, pas l'heure de l'évaluation, qui choisit les versions
     * @param unite   l'unité du dossier (ses ancêtres sont visés aussi), ou nulle
     * @param portees {@code convention}, {@code statutEmploi}, {@code corpsEmploi}, {@code titreEmploi} ; nulle au besoin
     */
    public record Dossier(ContexteRegle contexte, String objetType, String objetId, String date, String unite,
                          Map<String, Object> faits, Map<String, String> portees) { }

    /** Un motif déclenché, avec son message rendu : ce que la personne lit pour corriger. */
    public record Motif(String regleId, int version, String cle, ActionRegle action, String message) { }

    public record VersionAppliquee(String regleId, int version) { }

    /**
     * @param versions toutes les versions évaluées, déclenchées ou non : ce qu'il faut pour rejouer la décision
     */
    public record Verdict(ActionRegle action, List<Motif> motifs, List<VersionAppliquee> versions) {
        public Verdict {
            motifs = List.copyOf(motifs);
            versions = List.copyOf(versions);
        }
    }

    /** La conversion d'un code à la frontière, et le refus qui nomme les valeurs attendues. */
    static <E extends Enum<E>> E enumDe(E[] valeurs, Function<E, String> code, Object v, String nom) {
        return Arrays.stream(valeurs).filter(e -> code.apply(e).equals(v)).findFirst()
                .orElseThrow(() -> new EffectifsInvalideException(nom + " inconnu : « " + v + " » (attendu : "
                        + Arrays.stream(valeurs).map(code).collect(Collectors.joining(", ")) + ")"));
    }

    public static boolean estBloquant(ActionRegle a) {
        return a.estBloquant();
    }

    // ── Messages (E-GN-16) ───────────────────────────────────────────────────

    /** Rend un message d'organisme : {@code {fait}} est remplacé par sa valeur ; un jeton sans fait reste visible. */
    /**
     * Le message d'une règle <b>avant</b> tout fait : entier, ou rien.
     *
     * <p>Un modèle porte des jetons (« Au moins {min} quarts par semaine ») que seuls les faits d'un
     * dépôt remplissent. Annoncer la règle avant le dépôt est l'objet du panneau de la planche s02 —
     * mais afficher « {min} » se lit comme une panne, et un lecteur qui prend un gabarit pour un
     * défaut cesse de lire le reste.</p>
     */
    public static String messageAffichable(String modele) {
        return JETON.matcher(modele).find() ? null : modele;
    }

    /**
     * Une règle <b>telle qu'on l'annonce</b> à la personne qu'elle juge : sa clé, ce qui arrive si
     * elle n'est pas respectée, le message quand il se lit sans jeton, et pourquoi elle s'applique
     * à elle (sa portée).
     */
    public record RegleApplicable(String cle, ActionRegle action, String message, PorteeRegle porteeType, String porteeCode) {

        public static RegleApplicable de(VersionRegle v) {
            return new RegleApplicable(v.regle().cle(), v.contenu().action(), messageAffichable(v.contenu().message()),
                    v.regle().porteeType(), v.regle().porteeCode());
        }
    }

    public static String rendreMessage(String modele, Map<String, Object> faits) {
        return JETON.matcher(modele).replaceAll(m -> Matcher.quoteReplacement(jeton(m.group(), faits.get(m.group(1)))));
    }

    private static String jeton(String brut, Object valeur) {
        return valeur == null ? brut : texte(valeur);
    }

    /** Une valeur comme le {@code String(v)} du Node : 2.0 s'écrit 2, une liste s'écrit « a,b ». */
    static String texte(Object v) {
        if (v instanceof Double d && d == Math.rint(d) && !d.isInfinite()) {
            return String.valueOf(d.longValue());
        }
        if (v instanceof List<?> l) {
            return l.stream().map(Regles::texte).collect(Collectors.joining(","));
        }
        return String.valueOf(v);
    }

    public static String messageValide(Object v) {
        String m = v instanceof String s ? s.trim() : "";
        if (m.isEmpty()) {
            throw new EffectifsInvalideException("« message » est obligatoire : il dit à la personne quoi corriger");
        }
        if (m.length() > MESSAGE_MAX) {
            throw new EffectifsInvalideException("« message » : " + MESSAGE_MAX + " caractères au plus");
        }
        return m;
    }

    public static String cleValide(Object v) {
        String c = v instanceof String s ? s.trim() : "";
        if (!CLE.matcher(c).matches()) {
            throw new EffectifsInvalideException("« clé » : 2 à 60 minuscules, chiffres ou tirets (ex. minimum-quarts-semaine)");
        }
        return c;
    }

    /** Absente ou vide : 0. Sinon un entier entre 0 et 1000, donné en nombre ou en texte. */
    public static int prioriteValide(Object v) {
        if (v == null || "".equals(v)) {
            return 0;
        }
        if (!(v instanceof Number) && !(v instanceof String)) {
            throw prioriteInvalide();
        }
        String t = v.toString().trim();
        try {
            BigDecimal p = t.isEmpty() ? BigDecimal.ZERO : new BigDecimal(t);
            if (p.stripTrailingZeros().scale() <= 0 && p.signum() >= 0 && p.compareTo(BigDecimal.valueOf(PRIORITE_MAX)) <= 0) {
                return p.intValue();
            }
        } catch (NumberFormatException e) {
            // « abc », NaN : repli sur le refus nommé ci-dessous
        }
        throw prioriteInvalide();
    }

    private static EffectifsInvalideException prioriteInvalide() {
        return new EffectifsInvalideException("« priorité » : entier entre 0 et " + PRIORITE_MAX);
    }

    // ── Sélection et évaluation ──────────────────────────────────────────────

    /**
     * Les règles qui s'appliquent : du bon contexte, en vigueur à la date du fait, visant le dossier — puis,
     * pour chaque clé, <strong>la plus particulière</strong> (à précision égale, la plus prioritaire ; à
     * égalité parfaite, la première rencontrée).
     */
    public static List<VersionRegle> selectionner(List<VersionRegle> versions, ContexteRegle contexte, PorteesDuDossier p, String date) {
        Map<String, VersionRegle> parCle = new LinkedHashMap<>();
        versions.stream().filter(v -> v.regle().contexte() == contexte && v.enVigueurLe(date) && v.regle().porteeType().vise(v.regle().porteeCode(), p))
                .forEach(v -> parCle.merge(v.regle().cle(), v, (deja, autre) -> PRECISION.compare(autre, deja) > 0 ? autre : deja));
        return parCle.values().stream().sorted(ORDRE).toList();
    }

    /** L'action la plus restrictive des règles déclenchées ; les motifs, du plus sévère au moins sévère. */
    public static Verdict evaluer(List<VersionRegle> applicables, Map<String, Object> faits) {
        List<Motif> motifs = new ArrayList<>();
        for (VersionRegle v : applicables) {
            if (ConditionsRegles.evaluer(v.contenu().condition(), faits)) {
                motifs.add(new Motif(v.regle().id(), v.version(), v.regle().cle(), v.contenu().action(), rendreMessage(v.contenu().message(), faits)));
            }
        }
        ActionRegle action = motifs.stream().map(Motif::action).reduce(ActionRegle.ACCEPTER, ActionRegle::laPlusRestrictive);
        motifs.sort(Comparator.comparingInt((Motif m) -> m.action().ordinal()).reversed());
        return new Verdict(action, motifs, applicables.stream().map(v -> new VersionAppliquee(v.regle().id(), v.version())).toList());
    }
}
