package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <strong>Une personne dans plusieurs groupes</strong> (AO 26-015-DO, addenda 8, M-09) : un paramédic affecté à deux
 * secteurs, un prêt temporaire. Un rattachement est daté ; en vigueur, il fait figurer la personne parmi les ressources et
 * les candidats de l'autre unité. Qui voit ses quarts reste décidé par les périmètres (profil et droits). Pur.
 */
public final class Rattachements {

    private Rattachements() {
    }

    /** Une autre unité de la personne, du {@code depuis} au {@code jusqua} inclus (nul = sans fin). */
    public record Rattachement(String ressourceId, String uniteCode, String depuis, String jusqua) {

        public boolean enVigueurLe(String date) {
            return depuis.compareTo(date) <= 0 && (jusqua == null || jusqua.compareTo(date) >= 0);
        }

        public boolean chevauche(Rattachement autre) {
            return uniteCode.equals(autre.uniteCode()) && (jusqua == null || jusqua.compareTo(autre.depuis()) >= 0)
                    && (autre.jusqua() == null || autre.jusqua().compareTo(depuis) >= 0);
        }
    }

    /** Une saisie : l'unité diffère de l'unité principale, les dates s'ordonnent, et aucun rattachement à la même unité ne chevauche. */
    public static Rattachement saisie(Ressource r, Object uniteCode, Object depuis, Object jusqua, List<Rattachement> existants) {
        String unite = uniteCode instanceof String s && !s.isBlank() ? s.strip() : null;
        if (unite == null) {
            throw new EffectifsInvalideException("« uniteCode » requis");
        }
        if (unite.equals(r.uniteCode())) {
            throw new EffectifsInvalideException("« " + unite + " » est déjà l'unité principale de « " + r.nom() + " »");
        }
        String debut = Referentiels.dateValide(depuis, "depuis");
        String fin = jusqua == null || "".equals(jusqua) ? null : Referentiels.dateValide(jusqua, "jusqua");
        if (fin != null && fin.compareTo(debut) < 0) {
            throw new EffectifsInvalideException("« jusqua » (" + fin + ") précède « depuis » (" + debut + ")");
        }
        Rattachement nouveau = new Rattachement(r.id(), unite, debut, fin);
        if (existants.stream().anyMatch(nouveau::chevauche)) {
            throw new ConflitException("« " + r.nom() + " » est déjà rattachée à « " + unite + " » sur cette période");
        }
        return nouveau;
    }

    /** Les autres unités en vigueur le jour donné, par personne. */
    public static Map<String, Set<String>> unitesLe(List<Rattachement> rattachements, String date) {
        return rattachements.stream().filter(x -> x.enVigueurLe(date))
                .collect(Collectors.groupingBy(Rattachement::ressourceId, Collectors.mapping(Rattachement::uniteCode, Collectors.toSet())));
    }
}
