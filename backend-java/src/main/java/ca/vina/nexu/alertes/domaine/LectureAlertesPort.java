package ca.vina.nexu.alertes.domaine;

import ca.vina.nexu.alertes.domaine.Alertes.Alerte;
import ca.vina.nexu.alertes.domaine.Alertes.Modele;
import ca.vina.nexu.alertes.domaine.Alertes.ReponsePredefinie;

import java.util.List;
import java.util.Optional;

/** La lecture des alertes : séparée de l'écriture, pour qu'un écran de suivi ne puisse rien changer. */
public interface LectureAlertesPort {

    List<ReponsePredefinie> reponses(String tenantId);

    List<Modele> modeles(String tenantId);

    Optional<Modele> modele(String tenantId, String id);

    Optional<Alerte> alerte(String tenantId, String id);

    /** Les plus récentes d'abord, au plus {@code limite}. */
    List<Alerte> envoyees(String tenantId, int limite);

    /** Celles qui visent cette personne, les plus récentes d'abord, au plus {@code limite}. */
    List<Alerte> recues(String tenantId, String personId, int limite);
}
