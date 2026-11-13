package ca.vina.nexu.effectifs.domaine;

import java.util.List;

/**
 * Ce que les absences d'effectifs lisent de la planification (lot R3) : les places tenues, le comblement d'une
 * unité, les ressources d'un compte. Une lecture seule — libérer une place reste le geste du dépôt des équipages.
 */
public interface PlanningAbsencesEffectifsPort {

    /** Les places tenues par la ressource entre deux dates, dans les périodes qui se modifient encore. */
    List<AbsencesEffectifs.PlaceTenue> placesTenues(String tenantId, String ressourceId, String du, String au);

    AbsencesEffectifs.Comblement comblement(String tenantId, String uniteCode, String du, String au);

    /** Toutes les ressources reliées à un compte (et non la première seulement). */
    List<String> ressourcesDuCompte(String tenantId, String utilisateurId);
}
