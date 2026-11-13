package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.HorodatageExterne;
import ca.vina.nexu.effectifs.domaine.SourceHorodatagesIntegrationPort;

import java.util.Comparator;
import java.util.List;

/** La source RAO simulée : le jeu d'essai du connecteur, dans l'ordre, après l'instant demandé. */
public class HorodatagesSimulesIntegration implements SourceHorodatagesIntegrationPort {

    private final List<HorodatageExterne> jeu;

    public HorodatagesSimulesIntegration(List<HorodatageExterne> jeu) {
        this.jeu = List.copyOf(jeu);
    }

    @Override
    public List<HorodatageExterne> lire(String depuis) {
        return jeu.stream().filter(h -> h.horodatage().compareTo(depuis) > 0).sorted(Comparator.comparing(HorodatageExterne::horodatage)).toList();
    }
}
