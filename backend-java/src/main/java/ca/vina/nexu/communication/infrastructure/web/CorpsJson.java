package ca.vina.nexu.communication.infrastructure.web;

import java.util.List;
import java.util.Map;

/**
 * La lecture d'un corps JSON non typé à la frontière, avec la sémantique du Node : un champ qui n'est
 * pas du type attendu est traité comme le Node le traite — jamais un {@code ClassCastException} devenu 500.
 */
final class CorpsJson {

    private CorpsJson() {
    }

    static Object valeur(Map<String, Object> corps, String cle) {
        return corps == null ? null : corps.get(cle);
    }

    /** Le champ s'il est une chaîne ; sinon nul ({@code typeof x === "string"}). */
    static String chaine(Map<String, Object> corps, String cle) {
        return valeur(corps, cle) instanceof String s ? s : null;
    }

    /** {@code String(x ?? "")} : toute valeur présente devient son texte. */
    static String texte(Map<String, Object> corps, String cle) {
        Object v = valeur(corps, cle);
        return v == null ? "" : String.valueOf(v);
    }

    /** La vérité JavaScript d'une valeur présente ; nul quand elle est absente. */
    static Boolean booleen(Map<String, Object> corps, String cle) {
        Object v = valeur(corps, cle);
        return switch (v) {
            case null -> null;
            case Boolean b -> b;
            case Number n -> n.doubleValue() != 0;
            case String s -> !s.isEmpty();
            default -> true;
        };
    }

    /** Une liste si le champ en est une ; nulle s'il est absent ; vide sinon. */
    static List<?> liste(Map<String, Object> corps, String cle) {
        Object v = valeur(corps, cle);
        if (v == null) {
            return null;
        }
        return v instanceof List<?> l ? l : List.of();
    }
}
