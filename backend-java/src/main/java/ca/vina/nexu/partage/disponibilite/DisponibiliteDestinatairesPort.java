package ca.vina.nexu.partage.disponibilite;

/**
 * Port : une personne est-elle disponible à cet instant, selon le calendrier interne (FX-AO-20260810, critère 25) ?
 * Les cascades « prochain disponible » le consultent ; les effectifs l'implantent (absence acceptée, indisponibilité
 * déclarée). Un organisme sans effectifs n'a pas de calendrier : tout le monde y est disponible.
 */
@FunctionalInterface
public interface DisponibiliteDestinatairesPort {

    /** @param personId le nom de connexion, celui que portent les alertes */
    boolean disponible(String tenantId, String personId, String instant);

    DisponibiliteDestinatairesPort TOUJOURS = (tenantId, personId, instant) -> true;
}
