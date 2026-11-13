package ca.vina.nexu.approbations.domaine;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * <strong>Les comptes à qui l'on peut confier ses approbations</strong>, cherchés par nom —
 * jumeau de {@code suggererDelegataires} ({@code backend/src/approvals/service.ts}).
 *
 * <p>Deux bornes : le seuil de trois caractères est appliqué <i>ici</i>, pas seulement à l'écran
 * (sans lui, la suggestion énumère les comptes trois lettres à la fois) ; la portée est
 * l'organisme, celle de la délégation que la suggestion prépare.
 */
public final class Delegataires {

    /** En deçà, la recherche n'en est pas une. */
    public static final int SEUIL_SUGGESTION = 3;
    /** Au-delà, la liste ne se lit plus : on affine plutôt que de dérouler. */
    public static final int LIMITE_SUGGESTION = 20;
    private static final String ACTIF = "actif";

    private Delegataires() {
    }

    /** Un compte de l'organisme, avec le nom qu'on lui montre (fiche d'employé, sinon nom de connexion). */
    public record Compte(String id, String loginName, String email, String role, boolean actif, String nom) {
    }

    /** Le compte à qui l'on confie ses approbations, tel que la délégation doit le vérifier. */
    public record Destinataire(String tenantId, boolean actif, String role, String nom) {
    }

    public record Suggestion(String id, String nom, String titre, String courriel, String statut) {
    }

    /** Le port qui rend les comptes d'un organisme et ceux qui peuvent approuver. */
    public interface Comptes {
        List<Compte> deLOrganisme(String tenantId);

        /** Une délégation reçue, en vigueur aujourd'hui, est une source d'autorité au même titre que le rôle. */
        boolean aRecuUneDelegation(String tenantId, String userId);

        /** Le compte désigné par son identifiant, quel que soit son organisme : la vérification en dépend. */
        Optional<Destinataire> destinataire(String tenantId, String userId);
    }

    private static final String SUPERADMIN = "superadmin";

    /**
     * <strong>Le délégataire doit exister, être actif, et appartenir au même organisme.</strong> Sans
     * ces trois contrôles, une délégation vers un identifiant inventé ne confiait rien à personne, et
     * une délégation vers le compte d'un autre établissement le laissait décider sur des employés
     * qu'il n'a pas le droit de voir. Un compte global fait exception : il décide déjà partout.
     */
    public static void verifier(String tenantId, Optional<Destinataire> trouve) {
        Destinataire d = trouve.orElseThrow(() -> new IllegalArgumentException("delegate not found"));
        if (!d.actif()) {
            throw new IllegalArgumentException("delegate account is inactive");
        }
        if (!java.util.Objects.equals(d.tenantId(), tenantId) && !SUPERADMIN.equals(d.role())) {
            throw new IllegalArgumentException("delegate belongs to another tenant");
        }
    }

    public static List<Suggestion> suggerer(List<Compte> comptes, String q, String exclureUserId) {
        String terme = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        if (terme.length() < SEUIL_SUGGESTION) {
            return List.of();
        }
        return comptes.stream()
                .filter(c -> c.actif() && !c.id().equals(exclureUserId))
                .filter(c -> contient(c.nom(), terme) || contient(c.loginName(), terme) || contient(c.email(), terme))
                .limit(LIMITE_SUGGESTION)
                .map(c -> new Suggestion(c.id(), c.nom(), c.role(), c.email(), ACTIF))
                .toList();
    }

    private static boolean contient(String valeur, String terme) {
        return valeur != null && valeur.toLowerCase(Locale.ROOT).contains(terme);
    }
}
