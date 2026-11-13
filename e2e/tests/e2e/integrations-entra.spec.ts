/**
 * E2E — **Microsoft Entra ID de démonstration** : l'administrateur branche la démonstration depuis
 * la carte ; l'écran de connexion envoie alors le **navigateur** vers l'IdP embarqué (Authorization
 * Code + PKCE), qui propose trois comptes ; au retour, le serveur a échangé le code, vérifié
 * l'`id_token` RS256 contre le JWKS, posé la session — et l'application s'ouvre connectée avec
 * le rôle propagé. Puis le protocole, erreur par erreur, piloté par `donnees/integrations/entra.json`.
 */
import { test, expect, request as pwRequest, type Page } from "@playwright/test";
import { superadminContext, forcerFrancais } from "./helpers/auth";
import { createTenant, selectTenant, setTenantModules, MODULES_SOCLE, MODULES_TRANSVERSAUX, TENANT_ADMIN_PASS, TENANT_ADMIN_USER, uniq } from "./helpers/organisme";
import donnees from "./donnees/integrations/entra.json";
import { ouvrirNav } from "./helpers/nav";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;
test.use({ storageState: { cookies: [], origins: [] } });

test.describe.serial("Intégrations — Entra ID de démonstration", () => {
  test.beforeEach(async ({ page }) => { await forcerFrancais(page); });
  let tenantId = ""; let jeton = "";
  const entetes = () => ({ Authorization: `Bearer ${jeton}`, "X-Tenant-Id": tenantId });

  test.beforeAll(async () => {
    const sa = await superadminContext();
    try {
      tenantId = await createTenant(sa, uniq("Entra"), "enterprise");
      await setTenantModules(sa, tenantId, [...MODULES_SOCLE, ...MODULES_TRANSVERSAUX]);
    } finally { await sa.dispose(); }
    const ctx = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
    jeton = (await (await ctx.post(`${API}/api/auth/login`, { data: { username: TENANT_ADMIN_USER, password: TENANT_ADMIN_PASS, tenantId } })).json()).token;
    await ctx.dispose();
  });
  // La démonstration est un état du processus : on la débranche quoi qu'il arrive, pour les campagnes suivantes.
  test.afterAll(async () => {
    const ctx = await pwRequest.newContext({ storageState: { cookies: [], origins: [] }, extraHTTPHeaders: entetes() });
    await ctx.post(`${API}/api/integrations/demonstration/entra/debrancher`).catch(() => {});
    await ctx.dispose();
  });

  test("**brancher depuis la carte, se connecter par le navigateur via l'IdP mock, revenir connecté avec le rôle propagé, débrancher**", async ({ page }) => {
    test.setTimeout(120_000);
    await selectTenant(page, tenantId);
    await ouvrirNav(page, "nav-integrations");
    await expect(page.getByTestId("carte-entra-etat")).toContainText("Simulé");
    await page.getByTestId("carte-entra-demo").click();
    await expect(page.getByTestId("integrations-succes")).toContainText("Démonstration branchée");
    await expect(page.getByTestId("carte-entra-etat")).toContainText("Démonstration");
    await expect(page.getByTestId("carte-entra")).toContainText("/mock/entra");
    await page.getByTestId("carte-entra-essai").click();
    await expect(page.getByTestId("carte-entra-essai-resultat")).toContainText("Essai réussi");
    await expect(page.getByTestId("carte-entra-essai-resultat")).toContainText("1 clé(s) de signature");

    // Le flux navigateur : déconnexion, bouton Microsoft, page de choix de compte de l'IdP mock.
    await page.getByTestId("logout").click();
    await expect(page.getByTestId("login-sso-entra")).toBeVisible();
    await page.getByTestId("login-sso-entra").click();
    await page.waitForURL((u) => u.pathname.includes(`/mock/entra/${donnees.tenantEntra}/oauth2/v2.0/authorize`), { timeout: 20_000 });
    const u = new URL(page.url());
    expect(u.searchParams.get("client_id")).toBe(donnees.clientId);
    expect(u.searchParams.get("code_challenge_method")).toBe(donnees.decouverte.pkce);
    expect(u.searchParams.get("redirect_uri")).toContain("/api/auth/sso/entra/callback");
    await expect(page.locator("h1")).toHaveText("Choisir un compte");
    await page.getByTestId(donnees.compte.bouton).click();
    // Retour : le callback a échangé le code et posé la session ; l'application s'ouvre connectée.
    await expect(page.getByTestId("logout")).toBeVisible({ timeout: 20_000 });
    const moi = await (await page.request.get(`${API}/api/auth/me`)).json();
    expect(moi.username ?? moi.user?.username).toBe(donnees.compte.courriel);
    expect(moi.role ?? moi.user?.role).toBe(donnees.compte.role);

    // Débrancher : la carte revient à « Simulé », et l'écran de connexion reprend le mode simulé.
    await selectTenant(page, tenantId);
    await ouvrirNav(page, "nav-integrations");
    await page.getByTestId("carte-entra-demo").click();
    await expect(page.getByTestId("integrations-succes")).toContainText("Démonstration débranchée");
    await expect(page.getByTestId("carte-entra-etat")).toContainText("Simulé");
    expect((await (await page.request.get(`${API}/api/auth/sso/entra/status`)).json()).mock).toBe(true);
  });

  test("le protocole : découverte, JWKS, erreurs d'autorisation et d'échange nommées comme Entra", async () => {
    const api = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
    try {
      const d = await (await api.get(`${API}/mock/entra/${donnees.tenantEntra}/v2.0/.well-known/openid-configuration`)).json();
      expect(d.id_token_signing_alg_values_supported).toEqual([donnees.decouverte.alg]);
      expect(d.code_challenge_methods_supported).toEqual([donnees.decouverte.pkce]);
      expect(d.token_endpoint_auth_methods_supported).toEqual([donnees.decouverte.authMethod]);
      const k = await (await api.get(d.jwks_uri)).json();
      expect(k.keys[0]).toMatchObject({ kty: "RSA", use: "sig" });
      for (const e of donnees.erreursJeton) {
        const r = await api.post(d.token_endpoint, { form: e.corps as Record<string, string> });
        expect(r.status(), e.cas).toBe(e.statut);
        const j = await r.json();
        expect(j.error, e.cas).toBe(e.error);
        expect(j.error_codes, e.cas).toEqual([e.codeAad]);
      }
      for (const e of donnees.erreursAutorisation) {
        const r = await api.get(`${d.authorization_endpoint}?${e.query}`, { maxRedirects: 0 });
        expect(r.status(), e.cas).toBe(302);
        expect(new URL(r.headers().location).searchParams.get("error"), e.cas).toBe(e.error);
      }
      expect((await api.get(`${d.authorization_endpoint}?client_id=autre`)).status()).toBe(400);
    } finally { await api.dispose(); }
  });
});
