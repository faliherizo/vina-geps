package ca.vina.nexu.communication.application.requetes;

import ca.vina.nexu.communication.domaine.AcheminementPort;
import ca.vina.nexu.communication.domaine.AcheminementUnites;
import ca.vina.nexu.communication.domaine.GroupesDeTravailPort;
import ca.vina.nexu.communication.domaine.ReglagesAcheminementPort;

import java.util.List;

/**
 * L'acheminement d'une personne : le réglage de son unité, ou le défaut de l'établissement. Le
 * même pour tout événement de la communication — un réglage par événement n'a pas été demandé.
 */
public final class AcheminementParUnite implements AcheminementPort {

    private final GroupesDeTravailPort groupes;
    private final ReglagesAcheminementPort.Lecture reglages;

    public AcheminementParUnite(GroupesDeTravailPort groupes, ReglagesAcheminementPort.Lecture reglages) {
        this.groupes = groupes;
        this.reglages = reglages;
    }

    @Override
    public List<Canal> acheminement(String tenantId, String personId, String evenement) {
        return AcheminementUnites.canaux(groupes.groupesDe(tenantId, personId).stream()
                .map(u -> reglages.reglage(tenantId, u)).flatMap(java.util.Optional::stream).findFirst());
    }
}
