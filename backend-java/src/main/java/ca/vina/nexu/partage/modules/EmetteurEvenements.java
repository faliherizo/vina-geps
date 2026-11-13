package ca.vina.nexu.partage.modules;

import java.util.Map;

/**
 * Port de sortie : ce que la couche scolaire annonce au reste du système (absence déclarée,
 * besoin créé, attribué, annulé). Qui écoute — webhooks, journal — n'est pas son affaire.
 */
@FunctionalInterface
public interface EmetteurEvenements {
    /** @return le nombre de destinataires servis */
    int emettre(String tenantId, String evenement, Map<String, Object> donnees, String cle, String instant);
}
