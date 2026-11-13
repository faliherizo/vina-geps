package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;

/**
 * Ce que la feuille de temps lit des autres lots : les quarts publiés ou clos de la planification (R3) et les pointages
 * de la présence (R8). Ni R3 ni R8 n'offrent ces lectures sur une période entière.
 */
public interface SourcesTempsEffectifsPort {

    /** Une place d'un quart publié ou clos (heures en minutes depuis minuit). */
    record QuartBrut(String placeId, String date, int debut, int fin, String natureCode) { }

    /** Un pointage non rejeté, avec sa place (nulle hors quart) et sa saisie a posteriori (nulle sinon). */
    record PointageTemps(String placeId, String saisieId, PresenceEffectifs.Sens sens, String horodatage, PresenceEffectifs.StatutPointage statut) {
        public PresenceEffectifs.PointageCourt court() {
            return new PresenceEffectifs.PointageCourt(sens, horodatage);
        }
    }

    /** Les places de la personne dans les quarts des périodes publiées ou closes, entre deux dates incluses, par date puis début. */
    List<QuartBrut> quartsPlanifies(String tenantId, String ressourceId, String du, String au);

    /** Les pointages non rejetés de la personne entre deux instants ({@code au} exclu), dans l'ordre chronologique. */
    List<PointageTemps> pointages(String tenantId, String ressourceId, String du, String au);

    /** Les horodatages tiers de la personne, avec leur source, entre deux bornes ({@code au} exclue), dans l'ordre chronologique. */
    List<CorroborationTemps.HorodatageExterne> horodatagesExternes(String tenantId, String ressourceId, String du, String au);

    /** L'état de la période de l'horaire qui couvre ce jour dans l'unité (la plus récente), s'il y en a une. */
    Optional<Planification.EtatPeriode> etatPeriode(String tenantId, String uniteCode, String date);
}
