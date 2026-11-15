package ca.vina.nexu.alertes.domaine;

import ca.vina.nexu.alertes.domaine.Alertes.Alerte;

/** Armer la cascade d'une alerte qui vient de partir, selon la politique choisie à l'envoi. */
@FunctionalInterface
public interface ArmementCascades {

    void armer(Alerte alerte, String politiqueId);

    ArmementCascades AUCUN = (alerte, politiqueId) -> { };
}
