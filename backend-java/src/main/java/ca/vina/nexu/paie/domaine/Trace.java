package ca.vina.nexu.paie.domaine;

/** Qui agit, pour quel organisme, et quand : l'heure entre à la frontière, jamais lue dans le domaine. */
public record Trace(String tenantId, String acteur, String instant) { }
