package ca.vina.nexu.notifications.domaine;

import ca.vina.nexu.partage.securite.Role;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * <strong>Annonces</strong> (CSSMB 21 ; ADR 0039) — les règles pures, port de {@code notifications/serviceAnnonces.ts} :
 * ce qu'une annonce doit porter (titre, texte, rôles visés, période), et qui la voit.
 */
public final class Annonces {

    private Annonces() {
    }

    public static final int TITRE_MAX = 120;
    public static final int CORPS_MAX = 2000;
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    /** Les rôles qu'une annonce peut viser — ceux d'un compte d'organisme ; le superadmin n'en fait pas partie. */
    public static final List<Role> ROLES_VISABLES = List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER, Role.RH, Role.ACCOUNTANT, Role.EMPLOYEE);

    public record Annonce(String id, String tenantId, String titre, String corps, List<String> roles, String du, String au, String auteur, String creeeLe) {
        public Annonce {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }

        /** Vise-t-elle ce rôle ? Sans rôle, tout le monde. */
        public boolean vise(String role) {
            return roles.isEmpty() || roles.contains(role);
        }

        public boolean enCours(String jour) {
            return du.compareTo(jour) <= 0 && au.compareTo(jour) >= 0;
        }

        public Map<String, Object> vue() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("tenantId", tenantId);
            m.put("titre", titre);
            m.put("corps", corps);
            m.put("roles", roles);
            m.put("du", du);
            m.put("au", au);
            m.put("auteur", auteur);
            m.put("creeeLe", creeeLe);
            return m;
        }
    }

    /** Ce qu'une publication demande, relu à la frontière. */
    public record Nouvelle(String titre, String corps, List<String> roles, String du, String au) {
    }

    /** Une annonce relue : la demande, ou chaque manque nommé — dans l'ordre du Node. */
    public static Map.Entry<Optional<Nouvelle>, List<String>> lire(Map<String, Object> brut) {
        Map<String, Object> c = brut == null ? Map.of() : brut;
        List<String> erreurs = new ArrayList<>();
        String titre = c.get("titre") instanceof String s ? s.trim() : "";
        String corps = c.get("corps") instanceof String s ? s.trim() : "";
        if (titre.isEmpty()) {
            erreurs.add("titre requis");
        } else if (titre.length() > TITRE_MAX) {
            erreurs.add("titre : " + TITRE_MAX + " caractères au plus");
        }
        if (corps.isEmpty()) {
            erreurs.add("corps requis");
        } else if (corps.length() > CORPS_MAX) {
            erreurs.add("corps : " + CORPS_MAX + " caractères au plus");
        }
        List<String> roles = new ArrayList<>();
        Object brutRoles = c.get("roles");
        if (brutRoles != null && !(brutRoles instanceof List<?>)) {
            erreurs.add("roles : une liste de rôles, vide pour tous");
        } else if (brutRoles instanceof List<?> liste) {
            for (Object r : liste) {
                if (!(r instanceof String s) || Role.de(s).filter(ROLES_VISABLES::contains).isEmpty()) {
                    erreurs.add("roles : parmi " + String.join(", ", ROLES_VISABLES.stream().map(Role::code).toList()));
                    break;
                }
                roles.add(s);
            }
        }
        String du = c.get("du") instanceof String s && DATE.matcher(s).matches() ? s : "";
        String au = c.get("au") instanceof String s && DATE.matcher(s).matches() ? s : "";
        if (du.isEmpty()) {
            erreurs.add("du : date AAAA-MM-JJ requise");
        }
        if (au.isEmpty()) {
            erreurs.add("au : date AAAA-MM-JJ requise");
        }
        if (!du.isEmpty() && !au.isEmpty() && au.compareTo(du) < 0) {
            erreurs.add("au doit suivre du");
        }
        Optional<Nouvelle> nouvelle = erreurs.isEmpty() ? Optional.of(new Nouvelle(titre, corps, List.copyOf(new LinkedHashSet<>(roles)), du, au)) : Optional.empty();
        return Map.entry(nouvelle, List.copyOf(erreurs));
    }
}
