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

    /** Les plus récentes d'abord, au plus {@code limite}, selon le critère (période, priorité, expéditeur ; nul : sans borne). */
    List<Alerte> envoyees(String tenantId, Critere critere, int limite);

    /** Ce que le suivi demande à la base ; l'état se juge ensuite, sur l'alerte complète. */
    record Critere(String du, String au, Priorite priorite, String expediteurId) {
        public static final Critere TOUT = new Critere(null, null, null, null);
    }

    /** Les versions d'un modèle, la plus récente d'abord. */
    List<VersionModele> versions(String tenantId, String modeleId);

    /** Une version datée et signée : qui l'a faite, et quand. */
    record VersionModele(Modele modele, String creeLe, String creePar) { }

    Alertes.Reglages reglages(String tenantId);

    /** Celles qui visent cette personne, les plus récentes d'abord, au plus {@code limite}. */
    List<Alerte> recues(String tenantId, String personId, int limite);
}
