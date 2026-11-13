package ca.vina.nexu.organisation.domaine;

import java.util.List;

/**
 * <strong>Le port de persistance du contexte {@code organisation}</strong>, déclaré par le domaine.
 *
 * <p>Aucune méthode ne rend un organigramme : l'organigramme se recompose <strong>à une
 * date</strong>, et le demander au dépôt reviendrait à figer une hiérarchie qui change.
 */
public interface DepotOrganisationPort {

    /** Ce qui n'existe pas se dit — et devient un 404, jamais un 500. */
    class IntrouvableException extends RuntimeException {
        public IntrouvableException(String quoi, String id) {
            super(quoi + " introuvable : " + id);
        }
    }

    List<Organisation.VersionUnite> lister(String tenantId);

    /**
     * Termine des versions et en crée d'autres, <strong>en une seule transaction</strong>. Sans
     * elle, une fusion interrompue laisserait les anciennes unités terminées et la nouvelle
     * absente : un organigramme avec un trou, découvert au prochain rapport.
     */
    void appliquer(String tenantId, Organisation.ResultatReorganisation resultat, String motif,
                   String auteurId, String creeLe);
}
