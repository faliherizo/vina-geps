package ca.vina.nexu.partage.avis.domaine;

import java.util.List;
import java.util.Map;

/**
 * **Le port par lequel un contexte fait savoir qu'un événement s'est produit** (ADR 0029) : une
 * feuille de temps décidée, une absence demandée. Le contexte qui publie ne connaît ni les canaux,
 * ni les préférences, ni les fournisseurs — l'adaptateur du contexte des notifications s'en charge.
 * Publier ne lève jamais : le fait métier est déjà écrit quand on l'annonce.
 */
public interface PublicationAvis {

    /**
     * @param evenement     code du catalogue ({@code feuille.decidee}…)
     * @param destinataires identifiants d'employé
     * @param url           chemin de l'application ouvert par l'avis (défaut : le tableau de bord)
     * @param variables     substituées dans le sujet et le corps ({@code {{nom}}})
     * @param canaux        restreindre aux canaux nommés (envoi manuel) ; {@code null} = les canaux effectifs de la personne
     */
    record Avis(String tenantId, String evenement, List<String> destinataires, String sujet, String corps, String url, Map<String, Object> variables, List<String> canaux) {

        public static Avis de(String tenantId, String evenement, List<String> destinataires, String sujet, String corps, String url) {
            return new Avis(tenantId, evenement, destinataires, sujet, corps, url, Map.of(), null);
        }
    }

    /** Ce qui est parti, refusé ou échoué, par personne et par canal. */
    record Envoi(String employeeId, String canal, String statut, String erreur) {
    }

    List<Envoi> publier(Avis avis);

    /** Un port muet, pour les contextes ou essais qui n'avisent personne. */
    PublicationAvis MUET = avis -> List.of();
}
