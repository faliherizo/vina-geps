package ca.vina.nexu.effectifs.domaine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <strong>Unités organisationnelles et périmètres</strong> — E-GN-7, E-GN-8 ; le pur de
 * {@code backend/src/effectifs/socle/perimetres.ts}. Un utilisateur n'agit que sur les unités qu'on lui a
 * données, et le <strong>refus est le défaut</strong>. Un périmètre porte une étendue (l'unité seule, ou
 * sa descendance) et un niveau (lire, planifier, gérer) ; il est daté, et se ferme au lieu de s'effacer.
 */
public final class Perimetres {

    private Perimetres() {
    }

    public enum Etendue { UNITE, SOUS_ARBRE }

    /** Ordonnés : chaque niveau inclut les précédents. */
    public enum Niveau { LECTURE, PLANIFICATION, GESTION }

    public record Unite(String code, String parent) { }

    public record Perimetre(String id, String utilisateurId, String uniteCode, Etendue etendue, Niveau niveau, String depuis, String jusqua) { }

    public static Etendue etendueDe(Object v) {
        return Arrays.stream(Etendue.values()).filter(e -> e.name().equals(v)).findFirst()
                .orElseThrow(() -> new EffectifsInvalideException("étendue inconnue : « " + v + " » (attendu : " + noms(Etendue.values()) + ")"));
    }

    public static Niveau niveauDe(Object v) {
        return Arrays.stream(Niveau.values()).filter(n -> n.name().equals(v)).findFirst()
                .orElseThrow(() -> new EffectifsInvalideException("niveau inconnu : « " + v + " » (attendu : " + noms(Niveau.values()) + ")"));
    }

    private static String noms(Enum<?>[] v) {
        return Arrays.stream(v).map(Enum::name).collect(Collectors.joining(", "));
    }

    /** Refuse un parent qui créerait un cycle : sans cette garde, un sous-arbre ne se termine jamais. */
    public static void verifierParent(List<Unite> unites, String code, String parent) {
        if (parent == null) {
            return;
        }
        if (parent.equals(code)) {
            throw new EffectifsInvalideException("une unité ne peut pas être sa propre parente");
        }
        Map<String, String> parents = new HashMap<>();
        unites.forEach(u -> parents.put(u.code(), u.parent()));
        Set<String> vus = new HashSet<>();
        String courant = parent;
        while (courant != null && vus.add(courant)) {
            if (courant.equals(code)) {
                throw new EffectifsInvalideException("« " + parent + " » descend de « " + code + " » : ce rattachement créerait un cycle");
            }
            courant = parents.get(courant);
        }
    }

    /** L'unité et toute sa descendance. */
    public static Set<String> sousArbre(List<Unite> unites, String racine) {
        Map<String, List<String>> enfants = new HashMap<>();
        for (Unite u : unites) {
            if (u.parent() != null) {
                enfants.computeIfAbsent(u.parent(), k -> new ArrayList<>()).add(u.code());
            }
        }
        Set<String> resultat = new LinkedHashSet<>();
        Deque<String> pile = new ArrayDeque<>(List.of(racine));
        while (!pile.isEmpty()) {
            String c = pile.pop();
            if (resultat.add(c)) {
                pile.addAll(enfants.getOrDefault(c, List.of()));
            }
        }
        return resultat;
    }

    private static boolean actif(Perimetre p, String date) {
        return p.depuis().compareTo(date) <= 0 && (p.jusqua() == null || date.compareTo(p.jusqua()) < 0);
    }

    /** Les unités sur lesquelles un utilisateur a au moins le niveau demandé, à une date. */
    public static Set<String> unitesAccessibles(List<Perimetre> perimetres, List<Unite> unites, Niveau niveau, String date) {
        Set<String> resultat = new LinkedHashSet<>();
        for (Perimetre p : perimetres) {
            if (!actif(p, date) || p.niveau().ordinal() < niveau.ordinal()) {
                continue;
            }
            resultat.addAll(p.etendue() == Etendue.UNITE ? Set.of(p.uniteCode()) : sousArbre(unites, p.uniteCode()));
        }
        return resultat;
    }

    /** Un périmètre se ferme à une date, jamais dans le passé, jamais avant son début. */
    public static void verifierFermeture(Perimetre p, String date, String aujourdhui) {
        if (p.jusqua() != null) {
            throw new EffectifsInvalideException("ce périmètre est déjà fermé");
        }
        if (date.compareTo(aujourdhui) < 0) {
            throw new EffectifsInvalideException("une fermeture ne peut pas être rétroactive (" + date + " < " + aujourdhui + ")");
        }
        if (date.compareTo(p.depuis()) <= 0) {
            throw new EffectifsInvalideException("la fermeture doit suivre le début (" + p.depuis() + ")");
        }
    }
}
