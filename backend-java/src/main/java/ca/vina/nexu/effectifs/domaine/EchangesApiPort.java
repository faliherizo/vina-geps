package ca.vina.nexu.effectifs.domaine;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Port de lecture de l'API d'échange (AD-06) : quarts exposés, journal des quarts, relevés validés — par organisme. */
public interface EchangesApiPort {

    /** Les quarts des périodes exposées dont la date tombe dans la fenêtre, triés par date, début, identifiant. */
    List<EchangesApi.Quart> quartsExposes(String tenantId, EchangesApi.Fenetre fenetre);

    /** L'équipage de chaque place nommée (place → équipage) ; une place disparue est absente de la table rendue. */
    Map<String, String> equipagesDesPlaces(String tenantId, Collection<String> placeIds);

    /** Les quarts exposés parmi ces équipages, par identifiant — en deux requêtes, quel que soit leur nombre. */
    Map<String, EchangesApi.Quart> quartsExposesParId(String tenantId, Collection<String> equipageIds);

    /** Les entrées du journal portant sur un quart, après le curseur ({@code horodatage || id}), dans l'ordre. */
    List<DonneesIntegrationPort.EntreeJournal> journalQuartsApres(String tenantId, String curseur, int limite);

    /** Les relevés validés après le curseur ({@code approuveeLe || id}), dans l'ordre. */
    List<EchangesApi.Releve> relevesApres(String tenantId, String curseur, int limite);
}
