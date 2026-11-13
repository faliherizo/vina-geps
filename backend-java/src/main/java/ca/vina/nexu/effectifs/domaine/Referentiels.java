package ca.vina.nexu.effectifs.domaine;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * <strong>Les référentiels de l'organisme</strong> — le pur de {@code backend/src/effectifs/socle/referentiels.ts}.
 *
 * <p><strong>Une valeur est une suite de versions datées.</strong> Modifier un libellé en avril ne doit pas
 * réécrire le quart de mars : la version courante se ferme, une autre s'ouvre, et chaque fait se lit avec
 * la version en vigueur à sa date. Une modification ne prend effet ni avant aujourd'hui ni avant la
 * version qu'elle remplace (E-GR-9 : jamais rétroactif). Aucune E/S, aucune horloge : la date du jour est
 * un paramètre.
 */
public final class Referentiels {

    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private Referentiels() {
    }

    /** Une saisie validée. {@code attributs} n'a que des textes, nombres, booléens ou listes de textes. */
    public record ValeurSaisie(String code, String libelleFr, String libelleEn, String comportement, int ordre, Map<String, Object> attributs) { }

    /** Une référence que la base doit confirmer : le domaine sait qu'elle existe, pas si elle existe. */
    public record ReferenceAVerifier(TypeReferentiel type, String code, String attribut) { }

    public record Saisie(ValeurSaisie valeur, List<ReferenceAVerifier> references) { }

    /** Une version datée : {@code depuis ≤ date < jusqua}, {@code jusqua} nul = sans fin. */
    public interface Version {
        String id();

        String effectifDepuis();

        String effectifJusqua();
    }

    private static String texte(Object v) {
        return v instanceof String s ? s.trim() : "";
    }

    private static boolean vide(Object brut) {
        return brut == null || "".equals(brut) || brut instanceof Collection<?> c && c.isEmpty();
    }

    private static Object attributValide(DescripteurAttribut a, Object brut, List<ReferenceAVerifier> refs) {
        if (vide(brut)) {
            if (a.obligatoire()) {
                throw new EffectifsInvalideException("« " + a.libelle() + " » est obligatoire");
            }
            return a.type() == TypeAttribut.REFERENCES ? List.of() : null;
        }
        return switch (a.type()) {
            case TEXTE -> brut.toString().trim();
            case BOOLEEN -> {
                if (!(brut instanceof Boolean b)) {
                    throw new EffectifsInvalideException("« " + a.libelle() + " » doit être vrai ou faux");
                }
                yield b;
            }
            case NOMBRE -> nombre(a, brut);
            case CHOIX -> {
                String s = brut.toString();
                if (!a.valeurs().contains(s)) {
                    throw new EffectifsInvalideException("« " + a.libelle() + " » : « " + s + " » inconnu (attendu : " + String.join(", ", a.valeurs()) + ")");
                }
                yield s;
            }
            case REFERENCE -> {
                String s = brut.toString();
                refs.add(new ReferenceAVerifier(a.reference(), s, a.nom()));
                yield s;
            }
            case REFERENCES -> {
                if (!(brut instanceof Collection<?> c)) {
                    throw new EffectifsInvalideException("« " + a.libelle() + " » doit être une liste");
                }
                List<String> codes = c.stream().map(String::valueOf).toList();
                codes.forEach(x -> refs.add(new ReferenceAVerifier(a.reference(), x, a.nom())));
                yield codes;
            }
        };
    }

    private static Number nombre(DescripteurAttribut a, Object brut) {
        double n;
        try {
            n = brut instanceof Number x ? x.doubleValue() : Double.parseDouble(brut.toString().trim());
        } catch (NumberFormatException e) {
            throw new EffectifsInvalideException("« " + a.libelle() + " » doit être un nombre");
        }
        if (!Double.isFinite(n)) {
            throw new EffectifsInvalideException("« " + a.libelle() + " » doit être un nombre");
        }
        if (a.min() != null && n < a.min() || a.max() != null && n > a.max()) {
            throw new EffectifsInvalideException("« " + a.libelle() + " » doit être entre " + entier(a.min()) + " et " + entier(a.max()));
        }
        return n == Math.rint(n) ? (Number) (long) n : n;
    }

    private static String entier(Double d) {
        return d == null ? "" : d == Math.rint(d) ? String.valueOf(d.longValue()) : String.valueOf(d);
    }

    /**
     * Valide une saisie contre son type et dit quelles références la base doit confirmer. Un attribut
     * inconnu est refusé : un champ silencieusement ignoré est un paramétrage perdu.
     */
    @SuppressWarnings("unchecked")
    public static Saisie validerSaisie(TypeReferentiel type, Map<String, Object> entree) {
        String code = texte(entree.get("code"));
        if (!CODE.matcher(code).matches()) {
            throw new EffectifsInvalideException("« code » : 1 à 40 lettres, chiffres, tirets ou soulignés");
        }
        if (type.formatCode().filter(f -> !f.matcher(code).matches()).isPresent()) {
            throw new EffectifsInvalideException(type.messageFormatCode());
        }
        String libelleFr = texte(entree.get("libelleFr"));
        if (libelleFr.isEmpty()) {
            throw new EffectifsInvalideException("« libellé » est obligatoire");
        }
        String libelleEn = texte(entree.get("libelleEn"));
        String comportement = texte(entree.get("comportement"));
        verifierComportement(type, comportement);
        int ordre = ordre(entree.get("ordre"));
        Map<String, Object> brut = entree.get("attributs") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Set<String> connus = type.attributs().stream().map(DescripteurAttribut::nom).collect(java.util.stream.Collectors.toSet());
        List<String> inconnus = brut.keySet().stream().filter(k -> !connus.contains(k)).toList();
        if (!inconnus.isEmpty()) {
            throw new EffectifsInvalideException("attribut inconnu pour " + type.libelle() + " : " + String.join(", ", inconnus));
        }
        List<ReferenceAVerifier> references = new ArrayList<>();
        Map<String, Object> attributs = new LinkedHashMap<>();
        for (DescripteurAttribut a : type.attributs()) {
            Object v = attributValide(a, brut.get(a.nom()), references);
            if (v != null) {
                attributs.put(a.nom(), v);
            }
        }
        if (type == TypeReferentiel.PLAGES_HORAIRES && Objects.equals(attributs.get("debut"), attributs.get("fin"))) {
            throw new EffectifsInvalideException("une plage horaire ne peut pas être vide (début = fin)");
        }
        return new Saisie(new ValeurSaisie(code, libelleFr, libelleEn.isEmpty() ? null : libelleEn,
                comportement.isEmpty() ? null : comportement, ordre, attributs), references);
    }

    private static void verifierComportement(TypeReferentiel type, String comportement) {
        if (type.aDesComportements()) {
            if (!type.comportements().contains(comportement)) {
                throw new EffectifsInvalideException("« comportement » requis pour " + type.libelle() + " (attendu : " + String.join(", ", type.comportements()) + ")");
            }
        } else if (!comportement.isEmpty()) {
            throw new EffectifsInvalideException(type.libelle() + " n'a pas de comportement");
        }
    }

    private static int ordre(Object brut) {
        if (brut == null || "".equals(brut)) {
            return 0;
        }
        try {
            double d = brut instanceof Number n ? n.doubleValue() : Double.parseDouble(brut.toString());
            if (d == Math.rint(d)) {
                return (int) d;
            }
        } catch (NumberFormatException e) {
            // repli sur le refus nommé ci-dessous
        }
        throw new EffectifsInvalideException("« ordre » doit être un entier");
    }

    // ── Datation ───────────────────────────────────────────────────────────

    public static String dateValide(Object v, String champ) {
        String s = texte(v);
        if (!DATE.matcher(s).matches()) {
            throw new EffectifsInvalideException("« " + champ + " » : date AAAA-MM-JJ attendue");
        }
        try {
            LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new EffectifsInvalideException("« " + champ + " » : date AAAA-MM-JJ attendue");
        }
        return s;
    }

    public static <V extends Version> Optional<V> enVigueur(List<V> versions, String date) {
        return versions.stream().filter(v -> v.effectifDepuis().compareTo(date) <= 0 && (v.effectifJusqua() == null || date.compareTo(v.effectifJusqua()) < 0)).findFirst();
    }

    public static <V extends Version> Optional<V> versionOuverte(List<V> versions) {
        return versions.stream().filter(v -> v.effectifJusqua() == null).findFirst();
    }

    /** Le remplacement d'une version : jamais rétroactif, jamais avant le début de la version remplacée. Rend l'id à fermer. */
    public static String planifierRemplacement(List<? extends Version> versions, String depuis, String aujourdhui) {
        Version ouverte = versionOuverte(versions).orElseThrow(() -> new EffectifsInvalideException("cette valeur est fermée : elle ne peut plus être modifiée"));
        if (depuis.compareTo(aujourdhui) < 0) {
            throw new EffectifsInvalideException("une modification ne peut pas être rétroactive (" + depuis + " < " + aujourdhui + ")");
        }
        if (depuis.compareTo(ouverte.effectifDepuis()) <= 0) {
            throw new EffectifsInvalideException("la nouvelle version doit commencer après " + ouverte.effectifDepuis());
        }
        return ouverte.id();
    }

    /** La fermeture d'une valeur à une date : jamais dans le passé, jamais avant son début. Rend l'id à fermer. */
    public static String planifierFermeture(List<? extends Version> versions, String date, String aujourdhui) {
        Version ouverte = versionOuverte(versions).orElseThrow(() -> new EffectifsInvalideException("cette valeur est déjà fermée"));
        if (date.compareTo(aujourdhui) < 0) {
            throw new EffectifsInvalideException("une fermeture ne peut pas être rétroactive (" + date + " < " + aujourdhui + ")");
        }
        if (date.compareTo(ouverte.effectifDepuis()) <= 0) {
            throw new EffectifsInvalideException("la fermeture doit suivre le début (" + ouverte.effectifDepuis() + ")");
        }
        return ouverte.id();
    }
}
