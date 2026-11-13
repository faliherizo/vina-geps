package ca.vina.nexu.communication.domaine;

import java.util.Optional;

/** Port : les réglages d'acheminement rangés, unité par unité. */
public interface ReglagesAcheminementPort {

    /** Lecture seule : ce qui est réglé, pour résoudre un avis ou l'afficher. */
    interface Lecture {
        Optional<AcheminementUnites.Reglage> reglage(String tenantId, String uniteId);
    }

    /** Écriture : un réglage remplace le précédent en entier ; le retirer rend l'unité au défaut. */
    interface Ecriture {
        void enregistrer(String tenantId, AcheminementUnites.Reglage reglage);

        boolean retirer(String tenantId, String uniteId);
    }
}
