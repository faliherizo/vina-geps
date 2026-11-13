package ca.vina.nexu.effectifs.domaine;

import java.util.List;

/** Port des lectures internes que les connecteurs sortants publient : équipages, quarts, journal. */
public interface DonneesIntegrationPort {

    /** Une place d'un équipage d'une période publiée, avec le matricule de son titulaire (nul si vacante). */
    record LigneEquipage(String equipageId, String date, int debut, int fin, String uniteCode, String milieuId, String matricule, String corpsEmploiCode) { }

    record QuartPublie(String placeId, String date, int debut, int fin, String milieu) { }

    /** Une entrée du journal ; {@code apres} déjà relu depuis son JSON (nul s'il n'y en a pas). */
    record EntreeJournal(String id, String horodatage, String action, String objetType, String objetId, String uniteCode, Object apres) { }

    /** Les places des équipages publiés d'une date et d'une unité, par heure de début, équipage et rang. */
    List<LigneEquipage> equipagesPublies(String tenantId, String date, String uniteCode);

    /** Les quarts publiés d'une personne à partir d'une date, dans l'ordre. */
    List<QuartPublie> quartsPublies(String tenantId, String ressourceId, String depuisDate);

    /** Les entrées du journal après le curseur (nul : depuis le début), dans l'ordre de lecture. */
    List<EntreeJournal> journalApres(String tenantId, String curseur, int limite);
}
