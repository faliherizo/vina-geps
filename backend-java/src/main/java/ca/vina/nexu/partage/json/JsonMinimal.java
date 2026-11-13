package ca.vina.nexu.partage.json;

import java.util.List;
import java.util.Map;

/**
 * Un écrivain JSON minimal, sans bibliothèque, pour ce que le domaine doit signer ou hacher
 * lui-même (charge d'un jeton, corps d'un webhook) : Jackson est interdit dans le domaine, et une
 * signature porte sur des octets précis — pas sur ce qu'un sérialiseur configurable choisirait.
 * Même ordre que {@code JSON.stringify} : les cartes ordonnées gardent l'ordre d'insertion.
 */
public final class JsonMinimal {

    private JsonMinimal() {
    }

    public static String ecrire(Object valeur) {
        StringBuilder b = new StringBuilder();
        ecrire(b, valeur);
        return b.toString();
    }

    private static void ecrire(StringBuilder b, Object v) {
        if (v == null) {
            b.append("null");
        } else if (v instanceof String s) {
            chaine(b, s);
        } else if (v instanceof Boolean || v instanceof Integer || v instanceof Long) {
            b.append(v);
        } else if (v instanceof Number n) {
            double d = n.doubleValue();
            b.append(d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf((long) d) : String.valueOf(d));
        } else if (v instanceof Map<?, ?> m) {
            b.append('{');
            boolean premier = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!premier) {
                    b.append(',');
                }
                premier = false;
                chaine(b, String.valueOf(e.getKey()));
                b.append(':');
                ecrire(b, e.getValue());
            }
            b.append('}');
        } else if (v instanceof List<?> l) {
            b.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) {
                    b.append(',');
                }
                ecrire(b, l.get(i));
            }
            b.append(']');
        } else {
            chaine(b, String.valueOf(v));
        }
    }

    private static void chaine(StringBuilder b, String s) {
        b.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        b.append('"');
    }
}
