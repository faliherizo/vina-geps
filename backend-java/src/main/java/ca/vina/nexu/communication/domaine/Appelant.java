package ca.vina.nexu.communication.domaine;


/**
 * Qui agit, et pour quel établissement : l'identité que le Node lit dans {@code req.user} et
 * {@code req.tenant}. {@code acteurId} est l'identifiant de connexion (la trace du journal),
 * {@code employeeId} le dossier d'employé (l'appartenance aux groupes).
 */
public record Appelant(String tenantId, String acteurId, String role, String employeeId) { }
