package ca.vina.nexu.effectifs.domaine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * <strong>Les champs de la feuille de temps propres à l'organisme</strong> — ce qui rend la feuille adaptable à
 * n'importe quel emploi sans code par emploi ({@code docs/urgences-sante/ETUDE-FEUILLE-DE-TEMPS-DYNAMIQUE.md}).
 *
 * <p>L'administration définit un champ (code, libellé, type, valeurs admises, obligation) ; chaque segment porte
 * ses valeurs, vérifiées à la saisie. Un champ se <strong>désactive</strong>, jamais ne s'efface : les valeurs
 * déjà saisies restent lisibles. La description des tâches, commune à tous les emplois, n'est pas un champ à
 * définir : elle est intégrée au segment ({@link #description}).
 */
public final class ChampsFeuille {

    private ChampsFeuille() {
    }

    public static final int DESCRIPTION_MAX = 500;
    public static final int TEXTE_MAX = 200;
    public static final int LIBELLE_MAX = 60;
    public static final int VALEURS_MAX = 30;
    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]{1,39}$");

    /** Le type d'une valeur ; un ensemble fermé, converti une fois à la frontière. */
    public enum TypeChamp {
        TEXTE("texte"), NOMBRE("nombre"), BOOLEEN("booleen"), LISTE("liste");

        private final String code;

        TypeChamp(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Optional<TypeChamp> de(Object code) {
            return Arrays.stream(values()).filter(t -> t.code.equals(code)).findFirst();
        }
    }

    /** Un champ défini par l'organisme. {@code valeurs} ne vaut que pour une liste. */
    public record ChampFeuille(String code, String libelle, TypeChamp type, List<String> valeurs, boolean requis, boolean actif, int ordre) {

        public ChampFeuille {
            valeurs = List.copyOf(valeurs);
        }

        public ChampFeuille avecActif(boolean a) {
            return new ChampFeuille(code, libelle, type, valeurs, requis, a, ordre);
        }
    }

    /** Une définition saisie par l'administration, validée : code, libellé, type, valeurs d'une liste. */
    public static ChampFeuille definir(Map<String, Object> e, int ordre) {
        String code = e.get("code") instanceof String c ? c.strip() : "";
        if (!CODE.matcher(code).matches()) {
            throw new EffectifsInvalideException("« code » : 2 à 40 caractères, minuscules, chiffres et _ (ex. vehicule, km_parcourus)");
        }
        String libelle = texte(e.get("libelle"), "libelle", LIBELLE_MAX, true);
        TypeChamp type = TypeChamp.de(e.get("type")).orElseThrow(() -> new EffectifsInvalideException("« type » : texte, nombre, booleen ou liste"));
        List<String> valeurs = type == TypeChamp.LISTE ? valeursDeListe(e.get("valeurs")) : List.of();
        return new ChampFeuille(code, libelle, type, valeurs, Boolean.TRUE.equals(e.get("requis")), true, ordre);
    }

    private static List<String> valeursDeListe(Object brut) {
        if (!(brut instanceof List<?> l) || l.isEmpty() || l.size() > VALEURS_MAX) {
            throw new EffectifsInvalideException("« valeurs » : une liste de 1 à " + VALEURS_MAX + " choix");
        }
        List<String> v = new ArrayList<>();
        for (Object x : l) {
            String s = texte(x, "valeurs", LIBELLE_MAX, true);
            if (!v.contains(s)) {
                v.add(s);
            }
        }
        return v;
    }

    /**
     * Les valeurs d'un segment, vérifiées contre les champs <strong>actifs</strong> : un code inconnu ou désactivé est
     * refusé, un champ requis absent aussi, et chaque valeur doit avoir le type de son champ. Le résultat est canonique
     * (nombre en nombre, booléen en booléen) et ne garde que les valeurs données.
     */
    public static Map<String, Object> valeurs(List<ChampFeuille> champs, Object brut) {
        Map<String, Object> saisies = brut == null ? Map.of() : objet(brut);
        List<ChampFeuille> actifs = champs.stream().filter(ChampFeuille::actif).toList();
        saisies.keySet().stream().filter(k -> actifs.stream().noneMatch(c -> c.code().equals(k))).findFirst().ifPresent(k -> {
            throw new EffectifsInvalideException("« attributs." + k + " » : champ inconnu ou désactivé");
        });
        Map<String, Object> l = new LinkedHashMap<>();
        for (ChampFeuille c : actifs) {
            Object v = saisies.get(c.code());
            if (v == null || (v instanceof String s && s.isBlank())) {
                if (c.requis()) {
                    throw new EffectifsInvalideException("« " + c.libelle() + " » est requis");
                }
                continue;
            }
            l.put(c.code(), valeur(c, v));
        }
        return l;
    }

    private static Object valeur(ChampFeuille c, Object v) {
        String nom = "« " + c.libelle() + " »";
        return switch (c.type()) {
            case TEXTE -> texte(v, c.libelle(), TEXTE_MAX, false);
            case NOMBRE -> {
                if (!(v instanceof Number n) || !Double.isFinite(n.doubleValue())) {
                    throw new EffectifsInvalideException(nom + " : un nombre");
                }
                yield n;
            }
            case BOOLEEN -> {
                if (!(v instanceof Boolean b)) {
                    throw new EffectifsInvalideException(nom + " : oui ou non");
                }
                yield b;
            }
            case LISTE -> {
                if (!(v instanceof String s) || !c.valeurs().contains(s)) {
                    throw new EffectifsInvalideException(nom + " : l'un de " + String.join(", ", c.valeurs()));
                }
                yield s;
            }
        };
    }

    /** La description des tâches : libre, bornée, nulle si vide. */
    public static String description(Object v) {
        if (v == null || (v instanceof String s && s.isBlank())) {
            return null;
        }
        return texte(v, "description", DESCRIPTION_MAX, false);
    }

    private static String texte(Object v, String champ, int max, boolean requis) {
        if (!(v instanceof String s)) {
            throw new EffectifsInvalideException("« " + champ + " » : un texte");
        }
        String t = s.strip();
        if (requis && t.isEmpty()) {
            throw new EffectifsInvalideException("« " + champ + " » est requis");
        }
        if (t.length() > max) {
            throw new EffectifsInvalideException("« " + champ + " » : " + max + " caractères au plus");
        }
        return t;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objet(Object brut) {
        if (!(brut instanceof Map<?, ?> m)) {
            throw new EffectifsInvalideException("« attributs » : un objet { code: valeur }");
        }
        return (Map<String, Object>) m;
    }
}
