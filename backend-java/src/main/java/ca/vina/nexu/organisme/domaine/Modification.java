package ca.vina.nexu.organisme.domaine;

/**
 * Qui a modifié une option, et quand : consigné avec l'option, pour qu'on sache à qui demander pourquoi.
 *
 * @param le instant ISO 8601 UTC
 */
public record Modification(String le, String par) {
}
