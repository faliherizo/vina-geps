import type { Page, APIRequestContext } from "@playwright/test";
import { superadminContext } from "./auth";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;

/** Les modules du socle qu'un organisme d'essai reçoit : approbations et avis. */
export const MODULES_SOCLE = ["approvals", "notifications"];
/** Les deux transversaux : intégrations et registre des incidents de confidentialité. */
export const MODULES_TRANSVERSAUX = ["integrations", "confidentialite"];

// Identifiants de l'admin d'établissement créé pour chaque tenant de test.
// Un mot de passe explicite évite le « changement de mot de passe » du compte par défaut.
export const TENANT_ADMIN_USER = "admin";
// Mot de passe permanent fort (politique de mot de passe élevé) — sans contenir le nom d'utilisateur.
export const TENANT_ADMIN_PASS = "Zephyr-9!Quartz";

export const uniq = (prefix: string) => `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1e6)}`;

/**
 * Crée un tenant **et son admin d'établissement** via l'espace superadmin (un admin par tenant).
 * Le paramètre `ctx` (contexte admin global) n'est plus utilisé : la gestion des tenants est
 * réservée au superadmin. Conservé pour compatibilité des appels existants.
 */
export async function createTenant(
  _ctx: APIRequestContext,
  name: string,
  planCode: "starter" | "professional" | "enterprise" | "sovereign" = "enterprise",
  // Les démonstrations filmées, elles, doivent **montrer** l'accueil sécurisé du premier
  // accès (mot de passe + questions secrètes) : elles conservent donc les exigences.
  opts: { keepSecuritySetup?: boolean } = {},
): Promise<string> {
  const sa = await superadminContext();
  try {
    const r = await sa.post(`${API}/api/superadmin/tenants`, {
      data: {
        name, type: "government", country: "CA", region: "QC", planCode,
        adminUsername: TENANT_ADMIN_USER, adminPassword: TENANT_ADMIN_PASS,
      },
    });
    if (!r.ok()) throw new Error(`Création tenant échouée (${r.status()})`);
    const body = await r.json();
    // Sécurité renforcée : tout admin créé doit configurer MFA + questions au 1er accès. Pour les
    // tests d'IHM (hors onboarding), on lève ces exigences afin d'accéder directement à l'app.
    if (body.admin?.userId && !opts.keepSecuritySetup) {
      await sa.patch(`${API}/api/auth/users/${body.admin.userId}`, {
        data: { securitySetupRequired: false, mustChangePassword: false },
      });
    }
    return body.tenant.id as string;
  } finally {
    await sa.dispose();
  }
}

/**
 * Pose **exactement** ces modules (et leurs prérequis, ajoutés d'office par le serveur).
 *
 * Le serveur lit un retrait par rapport à l'état précédent, et un socle retiré emporte ses
 * dépendants — même ceux qu'on vient de redemander : c'est la règle de la case qu'on décoche. Le
 * forfait accorde tout le module à la création ; demander un seul sous-module retirerait donc le
 * socle, puis le sous-module avec lui. On repart de rien, puis on pose la liste.
 */
export async function setTenantModules(ctx: APIRequestContext, tenantId: string, modules: string[]): Promise<void> {
  for (const liste of [[], modules]) {
    const r = await ctx.put(`${API}/api/saas/tenants/${tenantId}/modules`, { data: { modules: liste } });
    if (!r.ok()) throw new Error(`Mise à jour modules échouée (${r.status()})`);
  }
}

/** Ouvre l'application connectée comme administration de l'établissement donné. */
export async function selectTenant(page: Page, tenantId: string): Promise<void> {
  const r = await page.request.post(`${API}/api/auth/login`, {
    headers: { "X-Tenant-Id": tenantId },
    data: { username: TENANT_ADMIN_USER, password: TENANT_ADMIN_PASS, tenantId },
  });
  if (!r.ok()) throw new Error(`Connexion admin d'établissement échouée (${r.status()})`);
  await page.goto("/");
}
