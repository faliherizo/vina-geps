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

    /** Une personne de l'organisme, par son nom de connexion. */
    Optional<Destinataire> personne(String tenantId, String personId);
}
