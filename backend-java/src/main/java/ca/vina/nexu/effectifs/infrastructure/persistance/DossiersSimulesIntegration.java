package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.DossierEmploye;
import ca.vina.nexu.effectifs.domaine.SourceDossiersIntegrationPort;

import java.util.List;

/** La source BD Corpo simulée : les dossiers du jeu d'essai dont le matricule est demandé. */
public class DossiersSimulesIntegration implements SourceDossiersIntegrationPort {

    private final List<DossierEmploye> jeu;

    public DossiersSimulesIntegration(List<DossierEmploye> jeu) {
        this.jeu = List.copyOf(jeu);
    }

    @Override
    public List<DossierEmploye> lire(List<String> matricules) {
        return jeu.stream().filter(d -> matricules.contains(d.matricule())).toList();
    }
}
