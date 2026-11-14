package ca.vina.nexu.effectifs.domaine;

import ca.vina.nexu.partage.securite.Role;

import java.time.Instant;
import java.time.ZoneId;

/**
 * <strong>Le contexte d'un appel</strong> : qui agit, pour quel établissement, et quelle heure il est
 * (jumeau de {@code backend/src/effectifs/contexte.ts}). L'horloge entre ici, à la frontière, et nulle
 * part ailleurs : un service qui lirait l'heure lui-même ne se prouverait pas non rétroactif.
 *
 * @param maintenant instant ISO 8601 UTC
 */
public record Contexte(String tenantId, String acteurId, String role, String maintenant) {

    /** Les rôles qui voient et paramètrent tout l'établissement, sans périmètre : administrateur et superadmin. */
    public boolean estAdministrateur() {
        return Role.de(role).filter(r -> r == Role.ADMIN || r == Role.SUPERADMIN).isPresent();
    }

    /**
     * Le jour <strong>à Montréal</strong>, celui où sont datés les quarts ({@link PresenceEffectifs#FUSEAU}).
     * Tronquer l'instant UTC donnait le lendemain dès 19 h (20 h l'été) : une règle créée le soir n'était
     * pas en vigueur pour le quart du jour, et la dater du jour était refusé comme « dans le passé ».
     */
    public String aujourdhui() {
        return Instant.parse(maintenant).atZone(ZoneId.of(PresenceEffectifs.FUSEAU)).toLocalDate().toString();
    }
}
