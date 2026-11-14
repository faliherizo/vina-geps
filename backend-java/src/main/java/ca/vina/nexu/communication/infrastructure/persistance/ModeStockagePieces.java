package ca.vina.nexu.communication.infrastructure.persistance;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Les modes de rangement du contenu des pièces jointes. {@code base} suit la base dans chacune de ses copies
 * (sauvegardes, essais de restauration, copies par organisme) ; {@code volume} pose un fichier sur le volume de
 * données de l'hébergement — sur AWS, l'EFS que protège AWS Backup — et allège la base.
 */
public enum ModeStockagePieces {
    BASE("base"), VOLUME("volume");

    private final String code;

    ModeStockagePieces(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** Le mode d'un réglage ; un réglage inconnu arrête le démarrage en nommant les modes possibles. */
    public static ModeStockagePieces de(String code) {
        String c = code == null || code.isBlank() ? BASE.code : code.trim();
        return Arrays.stream(values()).filter(m -> m.code.equals(c)).findFirst()
                .orElseThrow(() -> new IllegalStateException("PIECES_JOINTES_STOCKAGE « " + c + " » inconnu : "
                        + Arrays.stream(values()).map(ModeStockagePieces::code).collect(Collectors.joining(", "))));
    }
}
