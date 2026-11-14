package ca.vina.nexu.alertes.infrastructure.web;

import ca.vina.nexu.alertes.application.requetes.LectureAlertes;
import ca.vina.nexu.alertes.domaine.Alertes;
import ca.vina.nexu.alertes.domaine.EtatSuivi;
import ca.vina.nexu.alertes.domaine.LectureAlertesPort;
import ca.vina.nexu.alertes.domaine.Priorite;
import ca.vina.nexu.alertes.domaine.RefusAlerte;
import ca.vina.nexu.alertes.domaine.Semantique;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * La frontière : un corps JSON ou une requête deviennent des valeurs typées, <strong>une fois</strong>. Un code inconnu
 * (priorité, sémantique, état, format) ou un champ de mauvais type répond 400 en le nommant — jamais une erreur 500
 * sur un corps incomplet.
 */
final class SaisiesAlertes {

    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    /** Les formats d'export du suivi : ceux du sérialiseur partagé. */
    enum FormatExport {
        CSV("csv"), XLSX("xlsx"), PDF("pdf"), JSON("json");

        private final String code;

        FormatExport(String code) {
            this.code = code;
        }

        String code() {
            return code;
        }

        static FormatExport de(String code) {
            return Arrays.stream(values()).filter(f -> f.code.equals(code)).findFirst()
                    .orElseThrow(() -> RefusAlerte.invalide("« format » : csv, xlsx, pdf ou json"));
        }
    }

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

    /** Le contenu d'un modèle : texte, priorité, réponses proposées et périmètre. */
    static Alertes.ContenuModele contenuModele(Map<String, Object> c) {
        return new Alertes.ContenuModele(texte(c, "titre"), texte(c, "corps"), priorite(c), textes(c, "reponseIds"), textes(c, "groupeIds"));
    }

    /** Le filtre du suivi, lu dans la requête ; un paramètre vide vaut « sans borne ». */
    static LectureAlertes.Filtre filtre(Map<String, String> q) {
        Priorite priorite = present(q, "priorite") == null ? null
                : Priorite.de(q.get("priorite")).orElseThrow(() -> RefusAlerte.invalide("« priorite » : information, urgente ou critique"));
        EtatSuivi etat = present(q, "etat") == null ? null
                : EtatSuivi.de(q.get("etat")).orElseThrow(() -> RefusAlerte.invalide("« etat » : prise_en_charge, sans_prise_en_charge ou non_lue"));
        return new LectureAlertes.Filtre(new LectureAlertesPort.Critere(date(q, "du"), date(q, "au"), priorite, present(q, "expediteur")), etat);
    }

    private static String present(Map<String, String> q, String cle) {
        String v = q.get(cle);
        return v == null || v.isBlank() ? null : v.strip();
    }

    private static String date(Map<String, String> q, String cle) {
        String v = present(q, cle);
        if (v != null && !DATE.matcher(v).matches()) {
            throw RefusAlerte.invalide("« " + cle + " » : une date AAAA-MM-JJ");
        }
        return v;
    }
}
