package ca.vina.nexu.integrations.domaine;

import java.util.List;
import java.util.Map;

/**
 * Export XML générique (AO-5338 #NF-06).
 *
 * <p>Portage de {@code toXml} / {@code xmlEscape} de {@code backend/src/integrations/integrations.ts}.
 *
 * <p>La difficulté n'est pas le XML : c'est la <strong>conversion en chaîne</strong>. Le Node
 * applique {@code String(v ?? "")}, dont les règles JavaScript ne sont pas celles de
 * {@link String#valueOf}. {@code String(20.0)} rend « 20 », {@code Double.toString(20.0)} rend
 * « 20.0 » — le même piège que celui repéré sur les corps JSON. Ici il n'est pas rattrapable
 * après coup : le fichier XML part chez un partenaire.
 */
public final class SerialisationXml {

    private SerialisationXml() {
    }

    public static String echapper(Object valeur) {
        return chaineJs(valeur)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * {@code String(v ?? "")} de JavaScript, y compris ses conversions surprenantes.
     *
     * <p>Un objet imbriqué rend « [object Object] » et un tableau ses éléments joints par une
     * virgule. Ce n'est pas un comportement souhaitable, mais c'est celui du backend en service :
     * l'« améliorer » dans le Java produirait deux exports différents pour un même appel, et le
     * partenaire qui consomme le fichier verrait le format changer sans préavis.
     */
    static String chaineJs(Object v) {
        return switch (v) {
            case null -> "";
            case String s -> s;
            // Tous les nombres passent par la même règle : JavaScript n'a qu'un type numérique,
            // et distinguer Double de BigDecimal ici créerait des chemins que rien n'emprunte.
            case Number n -> nombreJs(n);
            case Map<?, ?> ignored -> "[object Object]";
            case List<?> l -> {
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < l.size(); i++) {
                    if (i > 0) {
                        b.append(',');
                    }
                    // `String([null])` rend "" pour l'élément nul, pas "null".
                    b.append(l.get(i) == null ? "" : chaineJs(l.get(i)));
                }
                yield b.toString();
            }
            default -> String.valueOf(v);
        };
    }

    /**
     * {@code String(20.0)} donne « 20 » : un nombre entier n'a pas de partie décimale en JS.
     *
     * <p>{@code NaN} et l'infini n'ont pas de traitement particulier : {@link String#valueOf(double)}
     * rend déjà « NaN » et « Infinity », exactement comme JavaScript. Les cas restants — les
     * entiers au-delà de 10²¹, que JS écrit en notation exponentielle — ne peuvent pas provenir
     * d'un document JSON reçu, et ne sont donc pas traités ici.
     */
    private static String nombreJs(Number n) {
        double d = n.doubleValue();
        if (d == Math.rint(d) && Math.abs(d) < 1e21) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }

    /**
     * Sérialise une liste d'objets plats — un élément {@code nomElement} par enregistrement.
     *
     * <p>L'ordre des champs est celui du document reçu : les cartes doivent donc préserver
     * l'ordre d'insertion, faute de quoi deux appels identiques rendraient des fichiers
     * différents.
     */
    public static String versXml(List<Map<String, Object>> enregistrements, String nomRacine, String nomElement) {
        StringBuilder elements = new StringBuilder();
        for (int i = 0; i < enregistrements.size(); i++) {
            if (i > 0) {
                elements.append('\n');
            }
            elements.append("  <").append(nomElement).append(">\n");
            Map<String, Object> r = enregistrements.get(i);
            boolean premier = true;
            for (Map.Entry<String, Object> champ : r.entrySet()) {
                if (!premier) {
                    elements.append('\n');
                }
                premier = false;
                elements.append("    <").append(champ.getKey()).append('>')
                        .append(echapper(champ.getValue()))
                        .append("</").append(champ.getKey()).append('>');
            }
            elements.append("\n  </").append(nomElement).append('>');
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<" + nomRacine + ">\n"
                + elements + "\n</" + nomRacine + ">\n";
    }
}
