package ca.vina.nexu.bdd;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Les trois mécanismes des scénarios, repris de l'interprète e2e des comportements :
 * <ul>
 *   <li><b>gabarit</b> — {@code {{nom}}} rend une valeur retenue ; seul dans une chaîne, il garde son
 *       type (un nombre reste un nombre) ;</li>
 *   <li><b>capture</b> — {@code places.0.id} lit la valeur à ce chemin ;</li>
 *   <li><b>sous-ensemble</b> — chaque clé attendue vaut ce qui est écrit ; un tableau se compare
 *       élément par élément sur ses premiers éléments.</li>
 * </ul>
 */
final class Gabarits {

    private static final Pattern SEUL = Pattern.compile("^\\{\\{([\\w:-]+)}}$");
    private static final Pattern GABARIT = Pattern.compile("\\{\\{([\\w:-]+)}}");

    private Gabarits() {
    }

    static JsonNode rendre(JsonNode n, Map<String, JsonNode> vars) {
        if (n == null) {
            return null;
        }
        if (n.isString()) {
            Matcher seul = SEUL.matcher(n.asString());
            return seul.matches() ? lire(vars, seul.group(1)) : JsonNodeFactory.instance.stringNode(rendreTexte(n.asString(), vars));
        }
        if (n.isArray()) {
            ArrayNode a = JsonNodeFactory.instance.arrayNode();
            n.forEach(e -> a.add(rendre(e, vars)));
            return a;
        }
        if (n.isObject()) {
            ObjectNode o = JsonNodeFactory.instance.objectNode();
            n.properties().forEach(e -> o.set(e.getKey(), rendre(e.getValue(), vars)));
            return o;
        }
        return n;
    }

    static String rendreTexte(String texte, Map<String, JsonNode> vars) {
        Matcher m = GABARIT.matcher(texte);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            JsonNode v = lire(vars, m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v.isString() ? v.asString() : v.toString()));
        }
        return m.appendTail(sb).toString();
    }

    private static JsonNode lire(Map<String, JsonNode> vars, String nom) {
        JsonNode v = vars.get(nom);
        if (v == null) {
            throw new IllegalStateException("gabarit « {{" + nom + "}} » : aucune valeur retenue sous ce nom");
        }
        return v;
    }

    static JsonNode capturer(JsonNode corps, String chemin) {
        JsonNode v = corps;
        for (String cle : chemin.split("\\.")) {
            if (v == null || v.isNull() || v.isMissingNode()) {
                throw new IllegalStateException("capture « " + chemin + " » : rien sous « " + cle + " »");
            }
            v = v.isArray() ? v.get(Integer.parseInt(cle)) : v.get(cle);
        }
        if (v == null || v.isMissingNode()) {
            throw new IllegalStateException("capture « " + chemin + " » : le chemin ne mène nulle part");
        }
        return v;
    }

    /** Les écarts entre l'attendu (sous-ensemble) et le reçu, nommés par leur chemin ; vide si conforme. */
    static List<String> ecarts(JsonNode attendu, JsonNode recu, String chemin) {
        List<String> e = new ArrayList<>();
        if (attendu.isArray()) {
            if (recu == null || !recu.isArray()) {
                return List.of(chemin + " : un tableau était attendu, reçu " + recu);
            }
            for (int i = 0; i < attendu.size(); i++) {
                e.addAll(ecarts(attendu.get(i), recu.get(i), chemin + "[" + i + "]"));
            }
            return e;
        }
        if (attendu.isObject()) {
            if (recu == null || !recu.isObject()) {
                return List.of(chemin + " : un objet était attendu, reçu " + recu);
            }
            attendu.properties().forEach(p -> e.addAll(ecarts(p.getValue(), recu.get(p.getKey()), chemin + "." + p.getKey())));
            return e;
        }
        return egaux(attendu, recu) ? List.of() : List.of(chemin + " : attendu " + attendu + ", reçu " + recu);
    }

    /** Un nombre se compare par sa valeur : 3 et 3.0 sont la même quantité. */
    private static boolean egaux(JsonNode attendu, JsonNode recu) {
        if (recu == null) {
            return attendu.isNull();
        }
        if (attendu.isNumber() && recu.isNumber()) {
            return attendu.decimalValue().compareTo(recu.decimalValue()) == 0;
        }
        return attendu.equals(recu);
    }
}
