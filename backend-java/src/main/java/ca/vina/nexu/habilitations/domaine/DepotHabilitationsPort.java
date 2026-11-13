package ca.vina.nexu.habilitations.domaine;

import java.util.List;

/**
 * <strong>Le port de persistance du contexte {@code habilitations}</strong>, déclaré par le domaine.
 *
 * <p>Aucune méthode ne rend une permission : la permission se <strong>résout</strong>, à une date,
 * à partir des octrois et des profils. La demander au dépôt reviendrait à la figer au jour de la
 * saisie — exactement ce que l'exigence 84 interdit.
 */
public interface DepotHabilitationsPort {

    /** Ce qui n'existe pas se dit — et devient un 404, jamais un 500. */
    class IntrouvableException extends RuntimeException {
        public IntrouvableException(String quoi, String id) {
            super(quoi + " introuvable : " + id);
        }
    }

    List<Habilitations.Objet> listerObjets(String tenantId);

    void poserObjet(String tenantId, Habilitations.Objet objet, String creeLe);

    List<Habilitations.Octroi> listerOctrois(String tenantId);

    void poserOctroi(String tenantId, Habilitations.Octroi octroi, String auteurId, String creeLe);

    /** Rend vrai si un octroi a bien été retiré : « rien à révoquer » est un 404, pas un succès. */
    boolean retirerOctroi(String tenantId, Habilitations.Sujet sujetType, String sujetId,
                          String objetCode, String effectifDu);

    List<String> profilsDe(String tenantId, String utilisateurId);

    void rattacherProfil(String tenantId, String utilisateurId, String profilId, String creeLe);

    boolean detacherProfil(String tenantId, String utilisateurId, String profilId);
}
