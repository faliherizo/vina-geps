package ca.vina.nexu.effectifs.domaine;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Valider et évaluer une condition de règle — le pur de {@code validerCondition}, {@code evaluerCondition}
 * et {@code faitsLus} ({@code backend/src/effectifs/regles/domaine.ts}).
 *
 * <p><strong>Un fait manquant est une erreur</strong>, pas un « faux » silencieux : une règle qui ne peut
 * pas s'évaluer ne doit pas laisser passer ce qu'elle devait arrêter. Une valeur nulle compte comme
 * manquante — c'est l'{@code undefined} du Node, celui qu'un appelant Java pose faute de mieux.
 */
public final class ConditionsRegles {

    public static final int PROFONDEUR_MAX = 5;
    public static final int FEUILLES_MAX = 30;

    private ConditionsRegles() {
    }

    /**
     * Valide une condition brute (cartes et listes d'un JSON) contre le catalogue du contexte : faits connus,
     * opérateurs compatibles avec leur type, valeurs du bon type, profondeur et taille bornées — sans quoi une
     * règle mal écrite ferait tomber chaque évaluation.
     */
    public static ConditionRegle valider(ContexteRegle contexte, Object brut) {
        return new Validation(contexte).visiter(brut, 1);
    }

    /** Les faits qu'une condition lit : le contexte appelant doit les fournir tous. */
    public static Set<String> faitsLus(ConditionRegle c) {
        return switch (c) {
            case ConditionRegle.Tous t -> union(t.enfants());
            case ConditionRegle.UnDe u -> union(u.enfants());
            case ConditionRegle.Non n -> faitsLus(n.enfant());
            case ConditionRegle.Comparaison k -> Set.of(k.fait());
        };
    }

    private static Set<String> union(List<ConditionRegle> enfants) {
        Set<String> s = new LinkedHashSet<>();
        enfants.forEach(x -> s.addAll(faitsLus(x)));
        return s;
    }

    public static boolean evaluer(ConditionRegle c, Map<String, Object> faits) {
        return switch (c) {
            case ConditionRegle.Tous t -> t.enfants().stream().allMatch(x -> evaluer(x, faits));
            case ConditionRegle.UnDe u -> u.enfants().stream().anyMatch(x -> evaluer(x, faits));
            case ConditionRegle.Non n -> !evaluer(n.enfant(), faits);
            case ConditionRegle.Comparaison k -> comparer(valeurDuFait(k.fait(), faits), k.op(), k.valeur());
        };
    }

    private static Object valeurDuFait(String fait, Map<String, Object> faits) {
        Object v = faits.get(fait);
        if (v == null) {
            throw new EffectifsInvalideException("fait manquant pour évaluer la règle : « " + fait + " »");
        }
        return v;
    }

    static boolean comparer(Object a, OperateurRegle op, Object b) {
        return switch (op) {
            case EGAL -> egal(a, b);
            case DIFFERENT -> !egal(a, b);
            case INFERIEUR -> ordonne(a, b, r -> r < 0);
            case INFERIEUR_OU_EGAL -> ordonne(a, b, r -> r <= 0);
            case SUPERIEUR -> ordonne(a, b, r -> r > 0);
            case SUPERIEUR_OU_EGAL -> ordonne(a, b, r -> r >= 0);
            case DANS -> dans(a, b);
            case HORS -> !dans(a, b);
        };
    }

    /** L'égalité stricte du Node, où 3 et 3.0 sont un même nombre (Jackson, lui, les lit Integer et Double). */
    private static boolean egal(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return x.doubleValue() == y.doubleValue();
        }
        return Objects.equals(a, b);
    }

    /** Nombres entre eux, textes (dates AAAA-MM-JJ) entre eux ; des types mêlés ne s'ordonnent pas : faux. */
    private static boolean ordonne(Object a, Object b, IntPredicate test) {
        if (a instanceof Number x && b instanceof Number y) {
            return test.test(Double.compare(x.doubleValue(), y.doubleValue()));
        }
        if (a instanceof String x && b instanceof String y) {
            return test.test(x.compareTo(y));
        }
        return false;
    }

    private static boolean dans(Object a, Object b) {
        return b instanceof List<?> liste && liste.stream().anyMatch(x -> egal(a, x));
    }

    /** Une passe de validation : elle compte les comparaisons de tout l'arbre, pas celles d'une branche. */
    private static final class Validation {

        private final ContexteRegle contexte;
        private int feuilles;

        Validation(ContexteRegle contexte) {
            this.contexte = contexte;
        }

        ConditionRegle visiter(Object c, int profondeur) {
            if (profondeur > PROFONDEUR_MAX) {
                throw new EffectifsInvalideException("condition trop imbriquée (" + PROFONDEUR_MAX + " niveaux au plus)");
            }
            if (!(c instanceof Map<?, ?> o)) {
                throw new EffectifsInvalideException("une condition est un objet");
            }
            if (o.containsKey(ConditionRegle.TOUS)) {
                return new ConditionRegle.Tous(enfants(o, ConditionRegle.TOUS, profondeur));
            }
            if (o.containsKey(ConditionRegle.UN_DE)) {
                return new ConditionRegle.UnDe(enfants(o, ConditionRegle.UN_DE, profondeur));
            }
            if (o.containsKey(ConditionRegle.NON)) {
                return new ConditionRegle.Non(visiter(o.get(ConditionRegle.NON), profondeur + 1));
            }
            return feuille(o);
        }

        private List<ConditionRegle> enfants(Map<?, ?> o, String groupe, int profondeur) {
            if (!(o.get(groupe) instanceof List<?> liste) || liste.isEmpty()) {
                throw new EffectifsInvalideException("« " + groupe + " » attend une liste non vide");
            }
            return liste.stream().map(x -> visiter(x, profondeur + 1)).toList();
        }

        private ConditionRegle feuille(Map<?, ?> o) {
            feuilles += 1;
            if (feuilles > FEUILLES_MAX) {
                throw new EffectifsInvalideException("condition trop longue (" + FEUILLES_MAX + " comparaisons au plus)");
            }
            Object brutFait = o.get(ConditionRegle.FAIT);
            FaitRegle fait = contexte.fait(brutFait == null ? "" : String.valueOf(brutFait));
            OperateurRegle op = OperateurRegle.de(o.get(ConditionRegle.OP));
            Object valeur = o.get(ConditionRegle.VALEUR);
            if (op.ensembliste()) {
                return new ConditionRegle.Comparaison(fait.code(), op, liste(fait, op, valeur));
            }
            if (op.ordonne() && !fait.type().ordonnable()) {
                throw new EffectifsInvalideException("l'opérateur « " + op.code() + " » ne s'applique pas à « " + fait.libelle() + " » (" + fait.type().code() + ")");
            }
            if (!fait.type().admet(valeur)) {
                throw new EffectifsInvalideException("« " + fait.libelle() + " » attend une valeur de type " + fait.type().code());
            }
            return new ConditionRegle.Comparaison(fait.code(), op, valeur);
        }

        private static List<Object> liste(FaitRegle fait, OperateurRegle op, Object valeur) {
            if (!(valeur instanceof List<?> l) || l.isEmpty() || !l.stream().allMatch(fait.type()::admet)) {
                throw new EffectifsInvalideException("« " + fait.libelle() + " » " + op.code() + " : une liste non vide de valeurs de type "
                        + fait.type().code() + " est attendue");
            }
            return List.copyOf(l);
        }
    }
}
