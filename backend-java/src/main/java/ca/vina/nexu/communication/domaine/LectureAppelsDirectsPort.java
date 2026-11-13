package ca.vina.nexu.communication.domaine;

import java.util.List;
import java.util.Optional;

/** Port de lecture des appels entre collègues ({@code msg_appels}). */
public interface LectureAppelsDirectsPort {

    Optional<AppelsDirects.Appel> lire(String tenantId, String appelId);

    /** Les appels qui sonnent, reçus ou lancés par la personne. */
    List<AppelsDirects.Appel> sonnants(String tenantId, String personId);

    /** L'appel déjà en sonnerie ou en cours de l'appelant vers l'appelé, s'il y en a un. */
    Optional<AppelsDirects.Appel> actifEntre(String tenantId, String appelantId, String appeleId);

    /** Sonnerie ou conversation en cours, pour la personne, du plus ancien au plus récent. */
    List<AppelsDirects.Appel> enCours(String tenantId, String personId);

    /** L'historique de la personne, du plus récent au plus ancien, borné en base. */
    List<AppelsDirects.Appel> historique(String tenantId, String personId, int limite);
}
