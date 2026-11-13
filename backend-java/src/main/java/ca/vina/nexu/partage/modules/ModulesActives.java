package ca.vina.nexu.partage.modules;

/**
 * Port : les modules activés pour un établissement (ADR 0013 — garde serveur à refus par défaut).
 * Le domaine ne sait pas d'où vient la réponse ; l'adaptateur lit {@code tenant_quotas}.
 */
public interface ModulesActives {
    boolean estActive(String tenantId, String module);
}
