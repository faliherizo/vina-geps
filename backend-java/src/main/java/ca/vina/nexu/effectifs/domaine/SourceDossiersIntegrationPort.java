package ca.vina.nexu.effectifs.domaine;

import java.util.List;

/** Port d'une source de dossiers d'employés (BD Corpo). */
@FunctionalInterface
public interface SourceDossiersIntegrationPort {

    /** Les dossiers des matricules demandés ; un matricule inconnu de la source est simplement absent. Lève {@link SourceIndisponibleException}. */
    List<IntegrationsEffectifs.DossierEmploye> lire(List<String> matricules);
}
