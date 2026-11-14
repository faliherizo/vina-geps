package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;

/** Port de lecture de la présence (lot R8) : pointages, preuves jugées, horodatages externes, quarts publiés. */
public interface LecturePointagesEffectifsPort {

    /** Le filtre de l'écran de l'encadrement ; chaque critère nul est ignoré. {@code au} est exclu. */
    /** {@code milieuId} : le filtre par site (E-GP-7) — sans lui, l'encadrement d'une caserne lit tout son périmètre. */
    record Filtre(String statut, String du, String au, String milieuId) { }

    Optional<PointageEffectifs> lire(String tenantId, String id);

    /** Les pointages d'une saisie a posteriori, dans l'ordre chronologique. */
    List<PointageEffectifs> deLaSaisie(String tenantId, String saisieId);

    /** Les pointages d'une personne, du plus récent au plus ancien, depuis un instant (nul : depuis toujours). */
    List<PointageEffectifs> deLaRessource(String tenantId, String ressourceId, String depuis, int limite);

    /** Du plus récent au plus ancien. */
    List<PointageEffectifs> lister(String tenantId, Filtre filtre, int limite);

    /** Les pointages actifs (non rejetés) d'une personne sur une place, dans l'ordre chronologique. */
    List<PresenceEffectifs.PointageCourt> actifsDeLaPlace(String tenantId, String ressourceId, String placeId);

    /**
     * Les pointages actifs d'une personne <strong>hors de tout quart</strong> (sans place), horodatés dans
     * {@code [du, au[}, dans l'ordre chronologique : ce qu'une saisie hors quart ne doit pas recouvrir (P-GP-4).
     */
    List<PresenceEffectifs.PointageCourt> actifsHorsQuart(String tenantId, String ressourceId, String du, String au);

    /** Les preuves jugées d'un pointage, par mécanisme. */
    List<PresenceEffectifs.VerdictPreuve> preuves(String tenantId, String pointageId);

    List<PresenceEffectifs.PointageCourt> externes(String tenantId, String ressourceId);

    /** Les places de la personne dans les quarts des périodes publiées, entre deux dates incluses. */
    List<PresenceEffectifs.QuartPublie> quartsPublies(String tenantId, String ressourceId, String du, String au);

    Optional<String> ressourceParMatricule(String tenantId, String matricule);
}
