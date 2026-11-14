package ca.vina.nexu.communication.infrastructure.persistance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Le contenu en fichier sur le volume de données de l'hébergement : {@code <dossier>/<organisme>/<clé>}. Sur AWS,
 * {@code NEXU_DONNEES} est l'EFS régional, chiffré et protégé par AWS Backup (COPIES-DE-SECURITE.md).
 *
 * <p>Un fichier s'écrit d'abord sous un nom temporaire puis se renomme : une panne au milieu ne laisse jamais une
 * pièce à moitié écrite sous son vrai nom. L'organisme et la clé forment le chemin : ils sont vérifiés, pour
 * qu'aucun identifiant ne sorte du dossier.
 */
public final class CoffreSurVolume implements CoffrePiecesJointes {

    private static final Pattern SEGMENT_SUR = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");
    private static final String SUFFIXE_TEMPORAIRE = ".partiel";

    private final Path dossier;

    public CoffreSurVolume(Path dossier) {
        this.dossier = dossier.toAbsolutePath().normalize();
    }

    private Path chemin(String tenantId, String cle) {
        if (!SEGMENT_SUR.matcher(tenantId).matches() || !SEGMENT_SUR.matcher(cle).matches()) {
            throw new IllegalStateException("identifiant de pièce jointe impropre à un chemin de fichier");
        }
        return dossier.resolve(tenantId).resolve(cle);
    }

    @Override
    public Optional<String> enLigne(byte[] contenu) {
        return Optional.empty();
    }

    @Override
    public void deposer(String tenantId, String cle, byte[] contenu) {
        Path cible = chemin(tenantId, cle);
        Path temporaire = cible.resolveSibling(cle + SUFFIXE_TEMPORAIRE);
        try {
            Files.createDirectories(cible.getParent());
            Files.write(temporaire, contenu);
            Files.move(temporaire, cible, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("pièce jointe : écriture sur le volume impossible", e);
        }
    }

    @Override
    public Optional<byte[]> lire(String tenantId, String cle, String colonne) {
        try {
            return Optional.of(Files.readAllBytes(chemin(tenantId, cle)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("pièce jointe : lecture sur le volume impossible", e);
        }
    }

    @Override
    public void retirer(String tenantId, String cle) {
        try {
            Files.deleteIfExists(chemin(tenantId, cle));
        } catch (IOException e) {
            throw new UncheckedIOException("pièce jointe : retrait sur le volume impossible", e);
        }
    }
}
