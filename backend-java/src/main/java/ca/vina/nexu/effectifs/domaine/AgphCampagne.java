package ca.vina.nexu.effectifs.domaine;

/**
 * Une campagne AGPH : une unité, un corps d'emploi, une année, un délai de choix par rang. {@code budgetHeuresSemaine}
 * est le budget d'heures hebdomadaires que le service de planification fixe à la campagne (E-CH-1) — nul tant qu'il
 * n'est pas fixé ; {@code reviseeLe} date l'ouverture de la révision.
 */
public record AgphCampagne(String id, String uniteCode, int annee, String nom, String corpsEmploiCode, Agph.EtatCampagne etat, int delaiChoixMinutes,
                           String creeLe, String ouverteLe, String closeLe, Double budgetHeuresSemaine, String reviseeLe) {
}
