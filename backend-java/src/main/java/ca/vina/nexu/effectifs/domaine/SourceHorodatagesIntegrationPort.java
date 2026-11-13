package ca.vina.nexu.effectifs.domaine;

import java.util.List;

/** Port d'une source d'horodatages (RAO). */
@FunctionalInterface
public interface SourceHorodatagesIntegrationPort {

    /** Les horodatages produits depuis l'instant donné (exclu), dans l'ordre. Lève {@link SourceIndisponibleException} si la source ne répond pas. */
    List<IntegrationsEffectifs.HorodatageExterne> lire(String depuis);
}
