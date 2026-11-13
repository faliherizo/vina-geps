package ca.vina.nexu.effectifs.domaine;

import ca.vina.nexu.partage.securite.Role;

/**
 * <strong>Le contexte d'un appel</strong> : qui agit, pour quel établissement, et quelle heure il est
 * (jumeau de {@code backend/src/effectifs/contexte.ts}). L'horloge entre ici, à la frontière, et nulle
 * part ailleurs : un service qui lirait l'heure lui-même ne se prouverait pas non rétroactif.
 *
 * @param maintenant instant ISO 8601 UTC
 */
public record Contexte(String tenantId, String acteurId, String role, String maintenant) {

    /** Les rôles qui voient et paramètrent tout l'établissement, sans périmètre : administrateur et superadmin. */
    public boolean estAdministrateur() {
        return Role.de(role).filter(r -> r == Role.ADMIN || r == Role.SUPERADMIN).isPresent();
    }

    public String aujourdhui() {
        return maintenant.substring(0, 10);
    }
}
