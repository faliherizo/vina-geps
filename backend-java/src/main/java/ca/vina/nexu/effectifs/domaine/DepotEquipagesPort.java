package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;

/** Port des équipages ({@code eff_equipages}) et de leurs places ({@code eff_places}). */
public interface DepotEquipagesPort {

    /** Ce qu'un équipage est, sans ses places ni son identité de période. */
    record Entete(String date, int debut, int fin, String natureCode, String plageCode, String milieuId, Planification.ModeAttribution modeAttribution,
                  int minimumOperationnel, String note) { }

    /** Une place à créer ; {@code ressourceId} non nul = pourvue par le titulaire du poste. */
    record NouvellePlace(String corpsEmploiCode, String posteId, String ressourceId) { }

    Optional<Equipage> lire(String tenantId, String id);

    /** {@code date} nulle : toute la période. Triés par date, début, milieu. */
    List<Equipage> lister(String tenantId, String periodeId, String date);

    void inserer(String tenantId, String id, Periode periode, Entete e, String creeLe);

    void insererPlace(String tenantId, String id, String equipageId, int rang, NouvellePlace p, String maintenant);

    void modifier(String tenantId, String id, Entete e);

    Optional<String> equipageDeLaPlace(String tenantId, String placeId);

    boolean posteDejaGenere(String tenantId, String posteId, String date);

    /** Les créneaux tenus par une ressource, sauf une place. */
    List<Planification.Creneau> creneauxDe(String tenantId, String ressourceId, String saufPlaceId);

    void attribuer(String tenantId, String placeId, String ressourceId, Equipage.OrigineAttribution origine, String justification, String le);

    void liberer(String tenantId, String placeId);
}
