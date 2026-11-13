package ca.vina.nexu.organisme.domaine;

/**
 * Ce qu'une alerte dit d'un événement : qui, quand, quoi — jamais le contenu téléchargé.
 *
 * @param quand instant ISO 8601 UTC
 */
public record Circonstances(String qui, String quand, String quoi) {
}
