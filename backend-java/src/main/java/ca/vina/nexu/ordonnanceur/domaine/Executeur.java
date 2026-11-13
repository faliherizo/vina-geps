package ca.vina.nexu.ordonnanceur.domaine;

import java.util.Optional;

/**
 * Port : ce qu'un module sait faire d'une tâche de son type. Il <strong>retourne</strong> ou
 * <strong>lève</strong> ; il peut demander un report en rendant un instant — le cas d'une
 * recherche hors fenêtre d'appel, qui attend la prochaine ouverture.
 */
@FunctionalInterface
public interface Executeur {
    Optional<String> executer(TacheDifferee tache, String maintenant);
}
