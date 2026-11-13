package ca.vina.nexu.rapports.domaine;

import ca.vina.nexu.rapports.domaine.CatalogueRapports.Agregat;
import ca.vina.nexu.rapports.domaine.CatalogueRapports.ColonnePlanifiee;
import ca.vina.nexu.rapports.domaine.CatalogueRapports.Filtre;
import ca.vina.nexu.rapports.domaine.CatalogueRapports.PlanRapport;
import ca.vina.nexu.rapports.domaine.CatalogueRapports.TypeChamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * <b>L'agrégation en mémoire d'un plan de rapport</b> — pour les sources de faits calculés (P-RF-1),
 * jumeau de {@code backend/src/rapports/agregation.ts}.
 *
 * <p>Une source de table s'agrège en SQL ; une source de faits n'a pas de table : ses lignes sont
 * filtrées, regroupées, agrégées, triées et limitées ici, <b>avec la même sémantique</b> que le SQL.
 * Fonction pure : ni base, ni horloge.
 */
public final class Agregation {

    private Agregation() {
    }

    private static final List<TypeChamp> NOMBRES = List.of(TypeChamp.NOMBRE, TypeChamp.MONTANT);
    private static final int CENT = 100;

    /** Deux décimales : un taux ou une moyenne au millionième ne se lit pas, et ne se compare pas. */
    static Number arrondir(double n) {
        BigDecimal b = BigDecimal.valueOf(n).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return b.scale() <= 0 ? (Number) b.longValueExact() : (Number) b.doubleValue();
    }

    static Double versNombre(Object v) {
        if (v == null || "".equals(v)) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String versTexte(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    static int comparer(Object a, Object b, boolean numerique) {
        if (numerique) {
            return Double.compare(Objects.requireNonNullElse(versNombre(a), 0d), Objects.requireNonNullElse(versNombre(b), 0d));
        }
        return versTexte(a).compareTo(versTexte(b));
    }

    static boolean retenu(Filtre f, Object v, boolean numerique) {
        List<Object> x = f.valeurs();
        return switch (f.operateur()) {
            case EGAL -> comparer(v, x.get(0), numerique) == 0;
            case DIFFERENT -> comparer(v, x.get(0), numerique) != 0;
            // `LIKE` de SQLite ignore la casse : la recherche en mémoire fait de même.
            case CONTIENT -> versTexte(v).toLowerCase().contains(versTexte(x.get(0)).toLowerCase());
            case SUPERIEUR -> comparer(v, x.get(0), numerique) > 0;
            case INFERIEUR -> comparer(v, x.get(0), numerique) < 0;
            case ENTRE -> comparer(v, x.get(0), numerique) >= 0 && comparer(v, x.get(1), numerique) <= 0;
            case PARMI -> x.stream().anyMatch(y -> comparer(v, y, numerique) == 0);
        };
    }

    private static double somme(List<Map<String, Object>> lignes, String champ) {
        return lignes.stream().map(l -> versNombre(l.get(champ))).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
    }

    /** Une valeur agrégée, avec la sémantique de SQL : une moyenne ou un extrême d'aucune valeur est nul. */
    static Object agreger1(ColonnePlanifiee c, List<Map<String, Object>> lignes) {
        if (c.agregat() == Agregat.COMPTE) {
            return (long) lignes.stream().filter(l -> l.get(c.champ()) != null).count();
        }
        if (c.agregat() == Agregat.TAUX) {
            double d = somme(lignes, c.denominateur());
            return d == 0 ? null : arrondir(CENT * somme(lignes, c.champ()) / d);
        }
        List<Double> nombres = lignes.stream().map(l -> versNombre(l.get(c.champ()))).filter(Objects::nonNull).toList();
        if (nombres.isEmpty()) {
            return null;
        }
        return switch (c.agregat()) {
            case SOMME -> arrondir(nombres.stream().mapToDouble(Double::doubleValue).sum());
            case MOYENNE -> arrondir(nombres.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            case MINIMUM -> arrondir(nombres.stream().mapToDouble(Double::doubleValue).min().orElse(0));
            case MAXIMUM, COMPTE, TAUX -> arrondir(nombres.stream().mapToDouble(Double::doubleValue).max().orElse(0));
        };
    }

    /** Les lignes regroupées : une par combinaison de valeurs des regroupements, dans l'ordre d'apparition. */
    private static List<Map<String, Object>> regrouper(PlanRapport plan, List<Map<String, Object>> lignes) {
        Map<List<Object>, List<Map<String, Object>>> groupes = new LinkedHashMap<>();
        for (Map<String, Object> l : lignes) {
            List<Object> cle = plan.groupes().stream().map(l::get).toList();
            groupes.computeIfAbsent(new ArrayList<>(cle), k -> new ArrayList<>()).add(l);
        }
        // Sans regroupement, un rapport agrégé rend **une** ligne — même sur zéro fait, comme SQL.
        if (plan.groupes().isEmpty() && groupes.isEmpty()) {
            groupes.put(List.of(), List.of());
        }
        List<Map<String, Object>> sortie = new ArrayList<>();
        for (List<Map<String, Object>> membres : groupes.values()) {
            Map<String, Object> ligne = new HashMap<>();
            // Une colonne non agrégée est forcément un regroupement (règle de `planifier`) : elle y est déjà.
            plan.groupes().forEach(g -> ligne.put(g, membres.get(0).get(g)));
            plan.colonnes().stream().filter(c -> c.agregat() != null).forEach(c -> ligne.put(c.champ(), agreger1(c, membres)));
            sortie.add(ligne);
        }
        return sortie;
    }

    /**
     * <b>Exécuter un plan sur des faits</b> : filtrer, regrouper ou projeter, trier, limiter. Rend une
     * ligne de plus que la limite quand il y en a : c'est ainsi que l'appelant sait que le résultat est
     * tronqué, exactement comme la requête SQL.
     */
    public static List<Map<String, Object>> agreger(PlanRapport plan, List<Map<String, Object>> faits) {
        Map<String, TypeChamp> types = new HashMap<>();
        plan.source().champs().forEach(c -> types.put(c.code(), c.type()));
        List<Map<String, Object>> filtres = faits.stream()
                .filter(l -> plan.filtres().stream().allMatch(f -> retenu(f, l.get(f.champ()), NOMBRES.contains(types.get(f.champ())))))
                .toList();
        List<Map<String, Object>> lignes = new ArrayList<>(plan.colonnes().stream().anyMatch(c -> c.agregat() != null)
                ? regrouper(plan, filtres) : filtres);
        trier(plan, lignes, types);
        return lignes.stream().limit(plan.limite() + 1L).map(l -> projeter(plan, l)).toList();
    }

    /** Le tri se fait <b>avant</b> la projection : on trie par un champ qu'on n'affiche pas, comme en SQL. */
    private static void trier(PlanRapport plan, List<Map<String, Object>> lignes, Map<String, TypeChamp> types) {
        if (plan.tri() == null) {
            return;
        }
        String champ = plan.tri().champ();
        boolean numerique = NOMBRES.contains(types.get(champ))
                || plan.colonnes().stream().anyMatch(c -> c.champ().equals(champ) && c.agregat() != null);
        Comparator<Map<String, Object>> ordre = (a, b) -> comparer(a.get(champ), b.get(champ), numerique);
        lignes.sort(plan.tri().descendant() ? ordre.reversed() : ordre);
    }

    private static Map<String, Object> projeter(PlanRapport plan, Map<String, Object> l) {
        Map<String, Object> m = new LinkedHashMap<>();
        plan.colonnes().forEach(c -> m.put(c.champ(), l.get(c.champ())));
        return m;
    }
}
