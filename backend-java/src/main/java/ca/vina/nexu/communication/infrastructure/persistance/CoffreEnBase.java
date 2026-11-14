package ca.vina.nexu.communication.infrastructure.persistance;

import java.util.Base64;
import java.util.Optional;

/**
 * Le contenu dans la colonne {@code contenu} de {@code msg_pieces_jointes}, en base64 — comme le Node depuis la
 * migration 0171. Il suit la base partout où elle va : sauvegarde, restauration, copie par organisme.
 */
public final class CoffreEnBase implements CoffrePiecesJointes {

    @Override
    public Optional<String> enLigne(byte[] contenu) {
        return Optional.of(Base64.getEncoder().encodeToString(contenu));
    }

    @Override
    public void deposer(String tenantId, String cle, byte[] contenu) {
        // Le contenu voyage avec la ligne : rien à ranger ailleurs.
    }

    @Override
    public Optional<byte[]> lire(String tenantId, String cle, String colonne) {
        return Optional.ofNullable(colonne).map(c -> Base64.getDecoder().decode(c));
    }

    @Override
    public void retirer(String tenantId, String cle) {
        // Rien n'a été rangé hors de la ligne.
    }
}
