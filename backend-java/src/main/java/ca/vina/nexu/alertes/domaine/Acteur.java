package ca.vina.nexu.alertes.domaine;

/** Qui agit, pour quel organisme : {@code personId} est le nom de connexion, celui que portent les remises. */
public record Acteur(String tenantId, String personId) {
}
