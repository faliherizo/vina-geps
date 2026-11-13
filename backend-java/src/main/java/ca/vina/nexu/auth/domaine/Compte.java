package ca.vina.nexu.auth.domaine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Un compte tel que la table {@code users} le porte — port de {@code User} dans
 * {@code backend/src/auth/auth.ts}.
 *
 * <p>{@code username} est la clé technique globale : le nom de connexion pour un compte global,
 * {@code login@@tenantId} pour un compte d'établissement. C'est ce suffixe qui rend le nom de
 * connexion unique <em>par établissement</em> — deux commissions scolaires peuvent avoir chacune
 * leur {@code s.tremblay}.
 */
public record Compte(
        String id,
        String username,
        String loginName,
        String tenantId,
        String passwordHash,
        String role,
        String employeeId,
        String createdAt,
        boolean active,
        boolean mustChangePassword,
        boolean securitySetupRequired,
        String email,
        String securityMethod,
        String totpSecret,
        String pendingTotpSecret) {

    /** Clé technique d'un nom de connexion dans un établissement. */
    public static String nomStocke(String loginName, String tenantId) {
        return tenantId == null || tenantId.isEmpty() ? loginName : loginName + "@@" + tenantId;
    }

    /** Le nom que l'utilisateur a saisi, jamais la clé technique. */
    public String nomDeConnexion() {
        return loginName != null ? loginName : username;
    }

    /**
     * Compte <strong>orphelin</strong> : adossé à un employé d'un véritable établissement, mais
     * lui-même rattaché à aucun. L'établissement par défaut n'en fait pas un orphelin — une
     * installation mono-établissement s'y appuie légitimement.
     *
     * @param tenantDeLEmploye établissement de l'employé lié, ou {@code null} s'il n'en a pas
     */
    public boolean estOrphelin(String tenantDeLEmploye) {
        if (tenantId != null || employeeId == null) {
            return false;
        }
        return tenantDeLEmploye != null && !"default".equals(tenantDeLEmploye);
    }

    /** La forme publique, clé pour clé celle de {@code toPublic} côté Node. */
    public Map<String, Object> vue() {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", id);
        v.put("username", nomDeConnexion());
        v.put("role", role);
        v.put("tenantId", tenantId);
        v.put("employeeId", employeeId);
        v.put("active", active);
        v.put("mustChangePassword", mustChangePassword);
        v.put("securitySetupRequired", securitySetupRequired);
        v.put("securityMethod", securityMethod);
        v.put("email", email);
        return v;
    }
}
