package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;

/** Port de lecture de l'API d'échange (AD-06) : quarts exposés, journal des quarts, relevés validés — par organisme. */
public interface EchangesApiPort {

    /** Les quarts des périodes exposées dont la date tombe dans la fenêtre, triés par date, début, identifiant. */
    List<EchangesApi.Quart> quartsExposes(String tenantId, EchangesApi.Fenetre fenetre);

    /** Le quart, s'il est exposé, que touche un objet du journal (un équipage, ou l'une de ses places). */
    Optional<EchangesApi.Quart> quartExpose(String tenantId, String objetType, String objetId);

    /** Les entrées du journal portant sur un quart, après le curseur ({@code horodatage || id}), dans l'ordre. */
    List<DonneesIntegrationPort.EntreeJournal> journalQuartsApres(String tenantId, String curseur, int limite);

    /** Les relevés validés après le curseur ({@code approuveeLe || id}), dans l'ordre. */
    List<EchangesApi.Releve> relevesApres(String tenantId, String curseur, int limite);
}
