package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Port de la répercussion d'un poste sur ses quarts déjà générés (E-CH-3) : {@code eff_places}, {@code eff_equipages}, {@code eff_pointages}. */
public interface DepotRepercussionPort {

    /** Une place future d'un poste, avec ce qu'il faut pour la déplacer ou la retirer. */
    record PlaceFuture(String placeId, String equipageId, String periodeId, String ressourceId, RepercussionPoste.PlaceGeneree place) { }

    /** Ce qui désigne l'équipage qui reçoit une place : même période, jour, plage, milieu et nature. */
    record Cle(String periodeId, String uniteCode, String date, String plageCode, String milieuId, String natureCode) { }

    record Bornes(String uniteCode, String debut, String fin) { }

    /** Les places du poste après {@code jour}, dans une période non close, triées par date ; {@code pointee} lue des pointages actifs. */
    List<PlaceFuture> placesFutures(String tenantId, String posteId, String jour);

    /** L'équipage de cette clé, s'il existe (le plus ancien). */
    Optional<String> equipageDe(String tenantId, Cle cle);

    void insererEquipage(String tenantId, String id, Cle cle, Planification.Plage plage, String creeLe);

    int nombrePlaces(String tenantId, String equipageId);

    int rangMax(String tenantId, String equipageId);

    int minimum(String tenantId, String equipageId);

    void poserMinimum(String tenantId, String equipageId, int minimum);

    void supprimerEquipage(String tenantId, String equipageId);

    void supprimerPlace(String tenantId, String placeId);

    void deplacerPlace(String tenantId, String placeId, String equipageId, int rang, String corpsEmploiCode);

    void requalifier(String tenantId, String placeId, String corpsEmploiCode);

    Planification.Creneau creneau(String tenantId, String equipageId);

    Optional<String> corpsDeRessource(String tenantId, String ressourceId);

    Bornes bornes(String tenantId, String periodeId);

    /** Les jours où le poste tient déjà une place dans la période. */
    Set<String> datesTenues(String tenantId, String posteId, String periodeId);
}
