package ca.vina.nexu.integrations.domaine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SSO Entra ID → rôle plateforme (AO-5338 #NF-19/20).
 *
 * <p>Portage de {@code mapClaimsToRole}. Pur et déterministe : c'est ce qui permet de le tester
 * sans annuaire, et c'est aussi ce qui rend l'attribution de rôle auditable — un rôle plateforme
 * accordé par un fournisseur d'identité doit être explicable ligne à ligne.
 */
public final class MappageRole {

    private MappageRole() {
    }

    /**
     * @param claim    revendication OIDC à lire (« roles », « groups »…)
     * @param map      valeur de la revendication → rôle plateforme
     * @param fallback rôle attribué quand aucune valeur ne correspond ; nul = aucun rôle
     */
    public record Regle(String claim, Map<String, String> map, String fallback) {
    }

    /** Le premier mappage trouvé l'emporte, dans l'ordre des valeurs reçues. */
    public static String resoudre(Map<String, Object> revendications, Regle regle) {
        Object brut = revendications.get(regle.claim());

        List<String> valeurs = new ArrayList<>();
        if (brut instanceof List<?> l) {
            for (Object o : l) {
                // `Array.prototype.map(String)` : un élément nul devient la chaîne « null ».
                valeurs.add(String.valueOf(o));
            }
        } else if (revendications.containsKey(regle.claim())) {
            // `raw !== undefined` : une revendication présente mais nulle est bien « définie » en
            // JavaScript, et devient la chaîne « null ». Un annuaire qui renvoie `roles: null`
            // ne doit donc pas se comporter comme un annuaire qui ne renvoie rien.
            valeurs.add(String.valueOf(brut));
        }

        Map<String, String> table = regle.map() == null ? Map.of() : regle.map();
        for (String v : valeurs) {
            String role = table.get(v);
            if (role != null) {
                return role;
            }
        }
        return regle.fallback();
    }
}
