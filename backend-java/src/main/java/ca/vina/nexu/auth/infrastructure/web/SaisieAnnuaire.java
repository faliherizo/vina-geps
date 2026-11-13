package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.auth.domaine.Annuaires;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * La frontière : un corps JSON devient une {@link Annuaires.Saisie}, ou un 400 qui nomme chaque
 * champ mal typé. Le contenu, lui, se juge dans le domaine.
 */
final class SaisieAnnuaire {

    private SaisieAnnuaire() {
    }

    static Annuaires.Saisie de(Map<String, Object> corps) {
        Map<String, Object> c = corps == null ? Map.of() : corps;
        List<String> motifs = new ArrayList<>();
        Annuaires.Saisie s = new Annuaires.Saisie(texte(c, "nom", motifs), texte(c, "locataireEntra", motifs), texte(c, "clientId", motifs),
                texte(c, "variableSecret", motifs), textes(c, "domaines", motifs), texte(c, "revendication", motifs),
                correspondances(c.get("correspondances"), motifs), texte(c, "roleParDefaut", motifs), actif(c.get("actif"), motifs));
        if (!motifs.isEmpty()) {
            throw new Annuaires.AnnuaireInvalideException(motifs);
        }
        return s;
    }

    private static String texte(Map<String, Object> c, String cle, List<String> motifs) {
        Object v = c.get(cle);
        if (v == null || v instanceof String) {
            return (String) v;
        }
        motifs.add("« " + cle + " » doit être un texte");
        return null;
    }

    private static List<String> textes(Map<String, Object> c, String cle, List<String> motifs) {
        Object v = c.get(cle);
        if (v == null) {
            return List.of();
        }
        if (v instanceof List<?> l && l.stream().allMatch(String.class::isInstance)) {
            return l.stream().map(String.class::cast).toList();
        }
        motifs.add("« " + cle + " » doit être une liste de textes");
        return List.of();
    }

    private static List<Annuaires.Correspondance> correspondances(Object v, List<String> motifs) {
        if (v == null) {
            return List.of();
        }
        if (v instanceof List<?> l && l.stream().allMatch(SaisieAnnuaire::estRegle)) {
            return l.stream().map(o -> (Map<?, ?>) o).map(m -> new Annuaires.Correspondance((String) m.get("groupe"), (String) m.get("role"))).toList();
        }
        motifs.add("« correspondances » doit être une liste de { groupe, role } textuels");
        return List.of();
    }

    private static boolean estRegle(Object o) {
        return o instanceof Map<?, ?> m && m.get("groupe") instanceof String && m.get("role") instanceof String;
    }

    /** Absent : actif. Un annuaire se déclare pour servir. */
    private static boolean actif(Object v, List<String> motifs) {
        if (v == null || v instanceof Boolean) {
            return v == null || (Boolean) v;
        }
        motifs.add("« actif » doit être vrai ou faux");
        return true;
    }
}
