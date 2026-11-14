package ca.vina.nexu.alertes.infrastructure.web;

import ca.vina.nexu.alertes.domaine.Priorite;
import ca.vina.nexu.alertes.domaine.RefusAlerte;
import ca.vina.nexu.alertes.domaine.Semantique;

import java.util.List;
import java.util.Map;

/**
 * La frontière : un corps JSON devient des valeurs typées, <strong>une fois</strong>. Un code inconnu
 * (priorité, sémantique) ou un champ de mauvais type répond 400 en le nommant — jamais une erreur 500
 * sur un corps incomplet.
 */
final class SaisiesAlertes {

    private SaisiesAlertes() {
    }

    static Map<String, Object> corps(Map<String, Object> c) {
        return c == null ? Map.of() : c;
    }

    static String texte(Map<String, Object> c, String champ) {
        Object v = c.get(champ);
        if (v != null && !(v instanceof String)) {
            throw RefusAlerte.invalide("« " + champ + " » doit être un texte");
        }
        return (String) v;
    }

    static List<String> textes(Map<String, Object> c, String champ) {
        Object v = c.get(champ);
        if (v == null) {
            return List.of();
        }
        if (!(v instanceof List<?> l) || l.stream().anyMatch(x -> !(x instanceof String))) {
            throw RefusAlerte.invalide("« " + champ + " » doit être une liste de textes");
        }
        return l.stream().map(String.class::cast).toList();
    }

    static boolean booleen(Map<String, Object> c, String champ) {
        if (!(c.get(champ) instanceof Boolean b)) {
            throw RefusAlerte.invalide("« " + champ + " » doit valoir vrai ou faux");
        }
        return b;
    }

    static Priorite priorite(Map<String, Object> c) {
        String code = texte(c, "priorite");
        return Priorite.de(code == null ? "" : code)
                .orElseThrow(() -> RefusAlerte.invalide("« priorite » : information, urgente ou critique"));
    }

    static Semantique semantique(Map<String, Object> c) {
        String code = texte(c, "semantique");
        return Semantique.de(code == null ? "" : code)
                .orElseThrow(() -> RefusAlerte.invalide("« semantique » : prise_en_charge, refus ou information"));
    }
}
