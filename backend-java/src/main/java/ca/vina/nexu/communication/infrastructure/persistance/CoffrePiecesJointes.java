package ca.vina.nexu.communication.infrastructure.persistance;

import java.util.Optional;

/**
 * <strong>Où se range le contenu d'une pièce jointe</strong> — un choix d'hébergement, pas une règle métier.
 *
 * Le dépôt de la messagerie range la description d'une pièce (nom, type, taille, clé) dans la base, et confie
 * son contenu à un coffre : la base elle-même ({@link CoffreEnBase}), ou le volume de données de l'hébergement
 * ({@link CoffreSurVolume}). Le mode se règle par {@code PIECES_JOINTES_STOCKAGE} ({@link ModeStockagePieces}).
 */
public interface CoffrePiecesJointes {

    /** Ce que la ligne de la pièce porte dans sa colonne {@code contenu} : le contenu même, ou rien s'il vit ailleurs. */
    Optional<String> enLigne(byte[] contenu);

    /** Range le contenu hors de la base, si c'est là qu'il vit ; sans effet pour le coffre en base. */
    void deposer(String tenantId, String cle, byte[] contenu);

    /** Le contenu d'une pièce, de sa colonne ou de son emplacement ; vide s'il n'a pas été conservé. */
    Optional<byte[]> lire(String tenantId, String cle, String colonne);

    /** Défait un dépôt dont la ligne n'a pas pu s'écrire : un fichier sans ligne ne serait plus jamais servi. */
    void retirer(String tenantId, String cle);
}
