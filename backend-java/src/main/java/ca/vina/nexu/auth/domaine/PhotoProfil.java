package ca.vina.nexu.auth.domaine;

import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * <strong>La photo de profil</strong> (maquettes P04 et P32) — jumeau de
 * {@code backend/src/auth/photoProfil.ts}. Trois types d'image, une taille bornée : une photo de
 * profil s'affiche en quelques dizaines de pixels, et un fichier de plusieurs mégaoctets ne serait
 * qu'un vecteur de saturation.
 */
public final class PhotoProfil {

    public static final List<String> TYPES = List.of("image/png", "image/jpeg", "image/webp");
    public static final int TAILLE_MAX_OCTETS = 512 * 1024;
    private static final int OCTETS_PAR_KO = 1024;
    private static final String PREFIXE_DATA = "data:";

    private PhotoProfil() {
    }

    public record Saisie(String typeMime, String contenu, byte[] octets) {
    }

    public record Photo(String typeMime, String contenu, int taille, String modifieLe) {
    }

    public static Saisie valider(Object typeBrut, Object contenuBrut) {
        String type = typeBrut == null ? "" : String.valueOf(typeBrut).trim().toLowerCase(Locale.ROOT);
        if (!TYPES.contains(type)) {
            throw new IllegalArgumentException("type d'image non admis « " + (type.isEmpty() ? "inconnu" : type)
                    + " » — attendu : " + String.join(", ", TYPES));
        }
        String brut = contenuBrut == null ? "" : String.valueOf(contenuBrut).trim();
        String contenu = brut.startsWith(PREFIXE_DATA) ? apresVirgule(brut) : brut;
        if (contenu.isEmpty()) {
            throw new IllegalArgumentException("aucune image reçue");
        }
        byte[] octets = decoder(contenu);
        if (octets.length == 0) {
            throw new IllegalArgumentException("l'image reçue est vide");
        }
        if (octets.length > TAILLE_MAX_OCTETS) {
            throw new IllegalArgumentException("l'image pèse " + Math.round(octets.length / (double) OCTETS_PAR_KO) + " Ko ; la limite est de "
                    + TAILLE_MAX_OCTETS / OCTETS_PAR_KO + " Ko — une photo de profil s'affiche en quelques dizaines de pixels");
        }
        return new Saisie(type, contenu, octets);
    }

    private static String apresVirgule(String brut) {
        int virgule = brut.indexOf(',');
        return virgule < 0 ? "" : brut.substring(virgule + 1);
    }

    /**
     * {@code Buffer.from(x, "base64")} ignore ce qui n'est pas de l'alphabet base64 au lieu d'échouer,
     * s'arrête au premier rembourrage et n'exige pas qu'il soit présent : on fait de même, sans quoi un
     * contenu que le Node réduisait à « image vide » deviendrait ici une erreur de décodage.
     */
    private static byte[] decoder(String contenu) {
        int fin = contenu.indexOf('=');
        String utile = (fin < 0 ? contenu : contenu.substring(0, fin)).replace('-', '+').replace('_', '/').replaceAll("[^A-Za-z0-9+/]", "");
        String complet = utile.length() % 4 == 1 ? utile.substring(0, utile.length() - 1) : utile;
        return Base64.getDecoder().decode(complet);
    }
}
