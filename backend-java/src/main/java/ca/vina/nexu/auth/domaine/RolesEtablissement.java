package ca.vina.nexu.auth.domaine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Les règles des rôles <strong>par établissement</strong> — port de la partie décisionnelle de
 * {@code backend/src/auth/tenantRoles.ts}.
 *
 * <p>Trois invariants : les défauts des rôles livrés restent la référence ; un rôle personnalisé
 * ne reçoit rien d'office ; l'administrateur ne peut pas se verrouiller dehors — ses droits ne
 * se retirent pas, personne ne pourrait les rendre.
 */
public final class RolesEtablissement {

    /** Les rôles livrés avec le produit, dans l'ordre de {@code BUILTIN_ROLES} : il paraît dans les listes et les messages. */
    public static final List<String> LIVRES = List.of("admin", "manager", "rh", "director", "accountant", "employee");

    public static final Map<String, String> LIBELLES_LIVRES = Map.of(
            "admin", "Administrateur", "manager", "Gestionnaire", "rh", "Ressources humaines",
            "director", "Directeur", "accountant", "Comptable", "employee", "Employé");

    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_-]{1,31}$");

    private RolesEtablissement() {
    }

    /** Un rôle tel que l'écran des accès le montre. */
    public record Role(String code, String label, boolean builtin, boolean active, List<String> capabilities) {
        public Map<String, Object> vue() {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("code", code);
            v.put("label", label);
            v.put("builtin", builtin);
            v.put("active", active);
            v.put("capabilities", capabilities);
            return v;
        }
    }

    public static boolean estLivre(String code) {
        return LIVRES.contains(code);
    }

    /** Le code d'un rôle personnalisé, normalisé et validé — messages du Node. */
    public static String codeValide(String brut) {
        String code = (brut == null ? "" : brut).trim().toLowerCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("code must be 2-32 chars: lowercase letter first, then a-z 0-9 _ -");
        }
        if (estLivre(code) || "superadmin".equals(code)) {
            throw new IllegalArgumentException("'" + code + "' is a built-in role");
        }
        return code;
    }

    public static String libelleValide(String brut) {
        String label = (brut == null ? "" : brut).trim();
        if (label.isEmpty()) {
            throw new IllegalArgumentException("label required");
        }
        return label;
    }

    /** Ce qu'une surcharge de capacité a le droit de faire, avant même de regarder la base. */
    public static void verifierSurcharge(String role, boolean capaciteConnue, boolean autorisee) {
        if (!capaciteConnue) {
            throw new IllegalArgumentException("unknown capability");
        }
        if ("superadmin".equals(role)) {
            throw new IllegalArgumentException("le superadmin détient toutes les capacités");
        }
        if ("admin".equals(role) && !autorisee) {
            throw new IllegalArgumentException("les droits de l'administrateur ne peuvent pas être retirés : personne ne pourrait les rendre");
        }
    }

    public static void exigerPersonnalise(String code, String action) {
        if (estLivre(code)) {
            throw new IllegalArgumentException("built-in role cannot be " + action);
        }
    }

    /** Un rôle encore porté ne se supprime pas : ses comptes n'auraient plus aucun droit, en silence. */
    public static void exigerSansPorteurs(int porteurs) {
        if (porteurs > 0) {
            throw new IllegalArgumentException(porteurs + " compte(s) portent encore ce rôle : reclassez-les d'abord");
        }
    }
}
