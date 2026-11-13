package ca.vina.nexu.acces.domaine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Décide si un rôle détient une capacité <strong>dans un établissement donné</strong>.
 *
 * <p>Trois règles, portées telles quelles depuis {@code auth/tenantRoles.ts}. Elles ne se
 * devinent pas, et chacune protège quelque chose :
 *
 * <ol>
 *   <li><strong>Le superadmin passe partout.</strong> Il n'a pas d'établissement, donc pas de
 *       table de droits où le chercher.</li>
 *   <li><strong>Un rôle personnalisé part de zéro.</strong> Un rôle créé par un établissement
 *       n'hérite d'aucune capacité : il n'a que ce qu'on lui accorde explicitement. L'inverse —
 *       hériter d'un rôle voisin — accorderait des droits que personne n'a demandés.</li>
 *   <li><strong>L'administrateur garde ses droits, quoi qu'on ait enregistré.</strong> Une
 *       surcharge d'établissement peut retirer une capacité à n'importe quel rôle, sauf à
 *       {@code admin} : sans cette garde, un établissement pourrait se verrouiller hors de sa
 *       propre administration, sans plus personne pour rouvrir.</li>
 * </ol>
 *
 * <p>Le domaine ne dépend de rien : les surcharges lui sont fournies, il ne va pas les chercher.
 */
public final class DecisionAcces {

    public static final String SUPERADMIN = "superadmin";
    public static final String ADMIN = "admin";

    /** Rôles intégrés au produit — les seuls à recevoir des droits par défaut. */
    public static final List<String> ROLES_INTEGRES =
            List.of("superadmin", "admin", "manager", "employee", "rh", "accountant", "director");

    private final PolitiqueAcces politique;

    public DecisionAcces(PolitiqueAcces politique) {
        this.politique = politique;
    }

    /**
     * Capacités effectives d'un rôle, surcharges d'établissement appliquées.
     *
     * @param surcharges capacité → autorisée ou refusée, telles que l'établissement les a
     *                   enregistrées ; vide s'il n'en a aucune.
     */
    public List<String> capacitesDe(String role, Map<String, Boolean> surcharges) {
        boolean integre = ROLES_INTEGRES.contains(role);
        List<String> base = integre ? politique.defautsPour(role) : List.of();

        if (surcharges == null || surcharges.isEmpty()) {
            return base;
        }

        Set<String> effectives = new LinkedHashSet<>(base);
        for (Map.Entry<String, Boolean> s : surcharges.entrySet()) {
            // Une capacité disparue du code est ignorée : une surcharge enregistrée hier ne doit
            // pas ressusciter un droit que le produit ne connaît plus.
            if (politique.regle(s.getKey()) == null) {
                continue;
            }
            if (Boolean.TRUE.equals(s.getValue())) {
                effectives.add(s.getKey());
            } else {
                effectives.remove(s.getKey());
            }
        }

        if (ADMIN.equals(role)) {
            effectives.addAll(politique.defautsPour(ADMIN));
        }

        // Rendu dans l'ordre de la politique, et non dans celui des surcharges : c'est l'ordre
        // que l'écran des rôles affiche.
        List<String> sortie = new ArrayList<>();
        for (String c : politique.capacites()) {
            if (effectives.contains(c)) {
                sortie.add(c);
            }
        }
        return sortie;
    }

    /** Ce rôle détient-il cette capacité dans cet établissement ? */
    public boolean peut(String role, String capacite, Map<String, Boolean> surcharges) {
        if (role == null || role.isEmpty()) {
            return false;
        }
        if (SUPERADMIN.equals(role)) {
            return true;
        }
        return capacitesDe(role, surcharges).contains(capacite);
    }
}
