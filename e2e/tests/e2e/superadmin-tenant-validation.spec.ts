/**
 * E2E — Robustesse de la création d'établissement (Superadmin) :
 *  1) une erreur de validation (mot de passe admin non conforme) ne doit **PAS** créer le tenant
 *     (transaction atomique — plus d'établissement orphelin) ;
 *  2) un tenant **suspendu** disparaît de la liste publique de login, mais reste côté superadmin.
 */
import { test, expect, request as pwRequest } from "@playwright/test";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;

/** Jeton superadmin via un contexte **jetable** (le cookie de session posé par /login ne doit pas
 * contaminer les appels API suivants, sinon la protection CSRF renverrait 403). */
async function superToken(): Promise<string> {
  const loginCtx = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
  const tok = (await loginCtx.post(`${API}/api/auth/login`, { data: { username: "superadmin", password: "superadmin" } }).then((r) => r.json())).token;
  await loginCtx.dispose();
  return tok;
}

test.describe("Superadmin — création atomique & cycle de vie", () => {
  test("mot de passe faible → 400 et aucun établissement créé", async () => {
    const tok = await superToken();
    const ctx = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
    const SH = { Authorization: `Bearer ${tok}` };

    const name = `test-fr-${Date.now()}`;
    // Reproduit le bug signalé : mot de passe « admin » (refusé par la politique).
    const bad = await ctx.post(`${API}/api/superadmin/tenants`, {
      headers: SH,
      data: {
        name, type: "public_institution", country: "FR", region: "IDF", planCode: "professional",
        address: "76 Allée de Bercy", city: "Paris", postalCode: "75012",
        adminUsername: "admin", adminPassword: "admin",
      },
    });
    expect(bad.status()).toBe(400);

    // L'établissement ne doit PAS exister (rollback).
    const overview = await ctx.get(`${API}/api/superadmin/tenants`, { headers: SH }).then((r) => r.json());
    expect(overview.some((t: any) => t.name === name)).toBe(false);

    // Avec un mot de passe conforme, la création réussit (201).
    const ok = await ctx.post(`${API}/api/superadmin/tenants`, {
      headers: SH,
      data: { name: `${name}-ok`, type: "public_institution", country: "FR", planCode: "professional", adminUsername: `adm-${Date.now()}`, adminPassword: "Str0ng!Pass12" },
    });
    expect(ok.status()).toBe(201);
    await ctx.dispose();
  });

  test("un tenant suspendu n'apparaît plus dans la liste de login", async () => {
    const tok = await superToken();
    const ctx = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
    const SH = { Authorization: `Bearer ${tok}` };

    const created = await ctx.post(`${API}/api/superadmin/tenants`, {
      headers: SH,
      data: { name: `Susp-${Date.now()}`, type: "public_institution", country: "CA", region: "QC", planCode: "professional", adminUsername: `adm-s-${Date.now()}`, adminPassword: "Str0ng!Pass12" },
    }).then((r) => r.json());
    const id = created.tenant.id;

    // Actif : présent dans /api/auth/tenants (sélecteur de login).
    let login = await ctx.get(`${API}/api/auth/tenants`).then((r) => r.json());
    expect(login.some((t: any) => t.id === id)).toBe(true);

    // Suspendu : disparaît du login, reste côté superadmin.
    await ctx.post(`${API}/api/superadmin/tenants/${id}/suspend`, { headers: SH }).then((r) => expect(r.status()).toBe(200));
    login = await ctx.get(`${API}/api/auth/tenants`).then((r) => r.json());
    expect(login.some((t: any) => t.id === id)).toBe(false);
    const overview = await ctx.get(`${API}/api/superadmin/tenants`, { headers: SH }).then((r) => r.json());
    expect(overview.some((t: any) => t.id === id)).toBe(true);
    await ctx.dispose();
  });
});
