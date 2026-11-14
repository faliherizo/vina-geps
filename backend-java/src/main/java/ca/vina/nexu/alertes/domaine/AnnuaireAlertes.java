package ca.vina.nexu.alertes.domaine;

import ca.vina.nexu.alertes.domaine.Alertes.Destinataire;

import java.util.List;
import java.util.Optional;

/**
 * <strong>Qui peut-on joindre ?</strong> Le port par lequel une alerte résout ses destinataires, sans
 * connaître ni les unités d'effectifs ni la table des comptes. Un organisme « communications
 * seulement » et Urgences-santé l'implantent pareillement : ses groupes sont ses unités en vigueur.
 */
public interface AnnuaireAlertes {

    /** Un groupe qu'on peut viser d'un coup. */
    record Groupe(String id, String nom) {
    }

    List<Groupe> groupes(String tenantId);

    /** Les membres actifs du groupe, nommés ; vide si le groupe n'existe pas. */
    List<Destinataire> membres(String tenantId, String groupeId);

    /** Une liste de distribution que l'acteur peut viser : partagée, ou la sienne. */
    record Liste(String id, String nom) {
    }

    List<Liste> listes(String tenantId, String acteurId);

    /** Les personnes d'une liste, dépliée à l'envoi ; vide si l'acteur ne voit pas cette liste. */
    Optional<List<Destinataire>> membresDeListe(String tenantId, String acteurId, String listeId);

    /** Les groupes dont la personne est membre aujourd'hui : ils bornent les modèles qu'elle voit. */
    List<String> groupesDe(String tenantId, String personId);

    /** Une personne de l'organisme, par son nom de connexion. */
    Optional<Destinataire> personne(String tenantId, String personId);
}
