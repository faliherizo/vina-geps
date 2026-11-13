package ca.vina.nexu.organisme.domaine;

import java.util.List;

/**
 * Une ligne du journal des alertes : qui a été prévenu de quoi, et si le courriel est parti. Les champs sont
 * dans l'ordre où le Node les rend ({@code SELECT *} sans l'organisme, destinataires en dernier).
 *
 * @param envoyees le nombre de courriels partis
 * @param erreur   les erreurs de transport, jointes ; {@code null} si tout est parti
 */
public record AlerteConsignee(String id, String regleId, String evenement, String quoi, String qui, int envoyees, String erreur,
                              String horodatage, List<String> destinataires) {
}
