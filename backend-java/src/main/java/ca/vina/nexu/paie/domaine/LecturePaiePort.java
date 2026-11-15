package ca.vina.nexu.paie.domaine;

import java.util.List;
import java.util.Optional;

/** Port de lecture de la paie. */
public interface LecturePaiePort {

    List<DossierRemuneration> dossiers(String tenantId);

    Optional<ReglagesPaie> reglages(String tenantId);

    List<CyclePaie> cycles(String tenantId);

    Optional<CyclePaie> cycle(String tenantId, String id);

    List<Bulletin> bulletins(String tenantId, String cycleId);

    /** Les bulletins des cycles <strong>traités</strong> dont la date de paie tombe dans l'année. */
    List<Bulletin> bulletinsTraites(String tenantId, int annee);

    /** Les bulletins des cycles traités d'une personne, du plus récent au plus ancien. */
    List<Bulletin> bulletinsTraitesDe(String tenantId, String ressourceId);
}
