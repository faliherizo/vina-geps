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

    /**
     * Les personnes actives dont le nom ou le nom de connexion contient le texte, au plus {@code limite} : la recherche
     * d'un destinataire par son nom (FX-AO-20260810, critère 3). Un nom et un identifiant, jamais une coordonnée (critère 29).
     */
    List<Destinataire> rechercher(String tenantId, String texte, int limite);
}
