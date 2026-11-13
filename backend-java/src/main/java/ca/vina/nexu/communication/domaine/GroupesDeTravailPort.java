package ca.vina.nexu.communication.domaine;

import java.util.List;

/**
 * Port : les groupes de travail où le personnel se joint, et leurs membres. Dans Urgences-santé, un
 * groupe est une unité d'effectifs et ses membres sont les ressources actives rattachées à un compte.
 * {@code personId} est le **nom de connexion** : c'est par lui que la messagerie désigne un participant.
 */
public interface GroupesDeTravailPort {

    record Groupe(String id, String code, String nom) { }

    record Membre(String personId, String nom) { }

    List<Groupe> groupes(String tenantId);

    List<Membre> membres(String tenantId, String groupeId);

    /** Les groupes où la personne est membre aujourd'hui. Une implémentation peut le servir d'une seule lecture. */
    default List<String> groupesDe(String tenantId, String personId) {
        return groupes(tenantId).stream().map(Groupe::id)
                .filter(g -> membres(tenantId, g).stream().anyMatch(m -> m.personId().equals(personId))).toList();
    }
}
