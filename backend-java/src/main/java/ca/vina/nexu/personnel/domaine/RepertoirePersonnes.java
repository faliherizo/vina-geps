package ca.vina.nexu.personnel.domaine;

import java.util.List;
import java.util.Optional;

/**
 * Port de lecture : les personnes d'un établissement telles qu'un champ « personne » les montre —
 * un nom, un titre, un courriel, un statut. Jamais un identifiant seul : personne ne connaît les
 * identifiants d'employé, et c'est en les recopiant qu'on rattache une ligne à la mauvaise personne.
 */
public interface RepertoirePersonnes {

    /** En deçà de trois caractères, rien n'est cherché : trois lettres est le seuil, côté écran comme ici. */
    int SEUIL = 3;

    record Personne(String id, String prenom, String nom, String titre, String courriel, String statut) {
        public String nomComplet() {
            return ((prenom == null ? "" : prenom) + " " + (nom == null ? "" : nom)).trim();
        }
    }

    List<Personne> parTexte(String tenantId, String texte, int limite);

    Optional<Personne> lire(String tenantId, String id);

    List<Personne> tous(String tenantId);
}
