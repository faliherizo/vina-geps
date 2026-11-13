import { test, expect, request as pwRequest } from "@playwright/test";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;

/**
 * E2E Superadmin : le compte superadmin par défaut administre les établissements (tenants)
 * et crée des admins par établissement. Vérifie le gating (admin normal refusé).
 */
test.describe("Superadmin — multi-établissement", () => {
  test("crée un établissement + admin, met à jour ses infos, refuse un admin normal", async () => {
    // Contexte de connexion (jetable) uniquement pour récupérer les jetons.
    const loginCtx = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
    const superTok = (await loginCtx.post(`${API}/api/auth/login`, { data: { username: "superadmin", password: "superadmin" } }).then((r) => r.json())).token;
    const adminTok = (await loginCtx.post(`${API}/api/auth/login`, { data: { username: "admin", password: "admin" } }).then((r) => r.json())).token;
    await loginCtx.dispose();
    // Contexte **sans cookie** pour les appels API : Bearer seul → la protection CSRF
    // (active uniquement sur les requêtes porteuses d'un cookie de session) ne s'applique pas.
    const ctx = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
    const SH = { Authorization: `Bearer ${superTok}` };

    const created = await ctx.post(`${API}/api/superadmin/tenants`, {
      headers: SH,
      data: {
        name: "Établissement E2E", type: "public_institution", country: "CA", region: "QC", planCode: "professional",
        address: "1 rue Test", city: "Lévis", adminUsername: `adm-${Date.now()}`, adminPassword: "Secret-Pass-2025!",
      },
    });
    expect(created.status()).toBe(201);
    const body = await created.json();
    expect(body.tenant.city).toBe("Lévis");
    expect(body.admin.username).toBeTruthy();

    const patched = await ctx.patch(`${API}/api/superadmin/tenants/${body.tenant.id}`, { headers: SH, data: { phone: "418-000-1111" } });
    expect((await patched.json()).phone).toBe("418-000-1111");

    const overview = await ctx.get(`${API}/api/superadmin/tenants`, { headers: SH });
    const row = (await overview.json()).find((t: any) => t.id === body.tenant.id);
    expect(row.admins.length).toBeGreaterThanOrEqual(1);

    // gating : un admin normal est refusé
    const denied = await ctx.get(`${API}/api/superadmin/tenants`, { headers: { Authorization: `Bearer ${adminTok}` } });
    expect(denied.status()).toBe(403);

    await ctx.dispose();
  });
});
