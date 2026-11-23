package ca.vina.nexu.communication.domaine;

import java.util.List;
import java.util.Optional;

/** Les groupes transverses en base : lecture et écriture séparées. */
public final class GroupesTransversesPort {

    private GroupesTransversesPort() {
    }

    public interface Lecture {
        List<GroupesTransverses.Groupe> groupes(String tenantId);

        Optional<GroupesTransverses.Groupe> groupe(String tenantId, String id);

        /** Toutes les appartenances, passées et à venir comprises : l'écran d'administration les montre. */
        List<GroupesTransverses.Appartenance> appartenances(String tenantId, String groupeId);

        /** Le nom d'une personne de l'organisme, par son nom de connexion ; vide si elle n'existe pas. */
        Optional<String> nomDe(String tenantId, String personId);

        /** Les personnes actives dont le nom ou le nom de connexion contient le texte, au plus {@code limite}. */
        List<Personne> personnes(String tenantId, String texte, int limite);
    }

    /** Une personne à inscrire : son nom de connexion (l'identifiant de la messagerie) et son nom. */
    public record Personne(String personId, String nom) {
    }

    public interface Ecriture {
        void enregistrer(String tenantId, GroupesTransverses.Groupe groupe);

        void enregistrer(String tenantId, String groupeId, GroupesTransverses.Appartenance appartenance);

        boolean retirer(String tenantId, String groupeId, String personId);
    }
}
