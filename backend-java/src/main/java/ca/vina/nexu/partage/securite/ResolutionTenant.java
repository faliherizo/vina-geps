package ca.vina.nexu.partage.securite;

/**
 * Détermine l'établissement d'une requête — et refuse quand il n'y a pas de réponse sûre.
 *
 * <p>Portage de {@code requireTenant} et {@code requireBaseTenant}. La règle tient en trois cas,
 * et le deuxième est celui qui protège l'isolation :
 *
 * <ol>
 *   <li>utilisateur <strong>rattaché</strong> à un établissement, sans en-tête : son
 *       établissement, tiré du jeton ;</li>
 *   <li>utilisateur rattaché, avec un en-tête <strong>divergent</strong> : <em>refus</em>. Un
 *       client ne choisit pas l'établissement dont il lit les données ;</li>
 *   <li>compte <strong>sans</strong> établissement — administrateur système, superadmin : il
 *       doit en désigner un, puisque son jeton n'en porte pas.</li>
 * </ol>
 */
public final class ResolutionTenant {

    private ResolutionTenant() {
    }

    /** Ce que la résolution a décidé, et pourquoi — le message part tel quel à l'appelant. */
    public sealed interface Resultat {
        record Retenu(String tenantId) implements Resultat { }
        /** 403 — l'en-tête contredit le jeton. */
        record Discordance() implements Resultat { }
        /** 400 — compte global qui n'a désigné aucun établissement. */
        record Manquant() implements Resultat { }
        /** 401 — aucune session. */
        record NonAuthentifie() implements Resultat { }
    }

    public static Resultat resoudre(ContexteRequete contexte, String enteteTenant) {
        if (contexte == null) {
            return new Resultat.NonAuthentifie();
        }

        String entete = enteteTenant == null ? null : enteteTenant.trim();
        String duJeton = contexte.tenantId();

        if (duJeton != null && !duJeton.isEmpty()) {
            if (entete != null && !entete.isEmpty() && !entete.equals(duJeton)) {
                return new Resultat.Discordance();
            }
            // Le jeton fait autorité, même quand l'en-tête concorde : on ne lit jamais le client.
            return new Resultat.Retenu(duJeton);
        }

        if (entete == null || entete.isEmpty()) {
            return new Resultat.Manquant();
        }
        return new Resultat.Retenu(entete);
    }
}
