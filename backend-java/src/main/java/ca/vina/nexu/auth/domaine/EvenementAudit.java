package ca.vina.nexu.auth.domaine;

import java.util.LinkedHashMap;
import java.util.Map;

/** Une entrée du journal d'audit telle que les écrans et le SIEM la lisent ({@code AuditEntry}). */
public record EvenementAudit(String id, String at, String userId, String username, String action, String target, String details) {

    /** Les champs dans l'ordre de la table, comme {@code SELECT *} les rend au Node. */
    public Map<String, Object> vue() {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", id);
        v.put("at", at);
        v.put("userId", userId);
        v.put("username", username);
        v.put("action", action);
        v.put("target", target);
        v.put("details", details);
        return v;
    }
}
