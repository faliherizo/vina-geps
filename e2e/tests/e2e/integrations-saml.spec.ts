/**
 * E2E — **IdP SAML 2.0 de démonstration** : brancher depuis la carte fait apparaître le bouton
 * « SAML » de l'écran de connexion ; le navigateur suit la redirection HTTP-Redirect (AuthnRequest
 * déflatée), choisit un compte, et le formulaire HTTP-POST porte une `SAMLResponse` signée
 * RSA-SHA256 que l'ACS vérifie avant de poser la session. Métadonnées et demande illisible ensuite.
 */
import { test, expect, request as pwRequest, type Page } from "@playwright/test";
import { superadminContext, forcerFrancais } from "./helpers/auth";
import { createTenant, selectTenant, setTenantModules, MODULES_SOCLE, MODULES_TRANSVERSAUX, TENANT_ADMIN_PASS, TENANT_ADMIN_USER, uniq } from "./helpers/organisme";
import donnees from "./donnees/integrations/saml.json";
import { ouvrirNav } from "./helpers/nav";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;
test.use({ storageState: { cookies: [], origins: [] } });

test.describe.serial("Intégrations — IdP SAML de démonstration", () => {
  test.beforeEach(async ({ page }) => { await forcerFrancais(page); });
  let tenantId = ""; let jeton = "";
  const entetes = () => ({ Authorization: `Bearer ${jeton}`, "X-Tenant-Id": tenantId });

  test.beforeAll(async () => {
    const sa = await superadminContext();
    try {
      tenantId = await createTenant(sa, uniq("Saml"), "enterprise");
      await setTenantModules(sa, tenantId, [...MODULES_SOCLE, ...MODULES_TRANSVERSAUX]);
    } finally { await sa.dispose(); }
    const ctx = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
    jeton = (await (await ctx.post(`${API}/api/auth/login`, { data: { username: TENANT_ADMIN_USER, password: TENANT_ADMIN_PASS, tenantId } })).json()).token;
    await ctx.dispose();
  });
  test.afterAll(async () => {
    const ctx = await pwRequest.newContext({ storageState: { cookies: [], origins: [] }, extraHTTPHeaders: entetes() });
    await ctx.post(`${API}/api/integrations/demonstration/saml/debrancher`).catch(() => {});
    await ctx.dispose();
  });

  test("**brancher, se connecter par SAML via le navigateur (réponse signée vérifiée), débrancher**", async ({ page }) => {
    test.setTimeout(120_000);
    await selectTenant(page, tenantId);
    await ouvrirNav(page, "nav-integrations");
    await expect(page.getByTestId("carte-saml-etat")).toContainText("Non configuré");
    await page.getByTestId("carte-saml-demo").click();
    await expect(page.getByTestId("carte-saml-etat")).toContainText("Démonstration");
    await page.getByTestId("carte-saml-essai").click();
    await expect(page.getByTestId("carte-saml-essai-resultat")).toContainText("Essai réussi");

    await page.getByTestId("logout").click();
    await expect(page.getByTestId("login-sso-saml")).toBeVisible();
    await page.getByTestId("login-sso-saml").click();
    await page.waitForURL((u) => u.pathname === "/mock/saml/sso", { timeout: 20_000 });
    expect(new URL(page.url()).searchParams.get("SAMLRequest")).toBeTruthy();
    await expect(page.locator("h1")).toHaveText("Choisir un compte");
    await page.getByTestId(donnees.compte.bouton).click();
    await expect(page.getByTestId("logout")).toBeVisible({ timeout: 20_000 });
    const moi = await (await page.request.get(`${API}/api/auth/me`)).json();
    expect(moi.username ?? moi.user?.username).toBe(donnees.compte.courriel);
    expect(moi.role ?? moi.user?.role).toBe(donnees.compte.role);

    await selectTenant(page, tenantId);
    await ouvrirNav(page, "nav-integrations");
    await page.getByTestId("carte-saml-demo").click();
    await expect(page.getByTestId("carte-saml-etat")).toContainText("Non configuré");
    await page.getByTestId("logout").click();
    await expect(page.getByTestId("login-sso-saml")).toHaveCount(0);
  });

  test("les métadonnées de l'IdP et une demande illisible", async () => {
    const api = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
    try {
      const meta = await api.get(`${API}/mock/saml/metadata`);
      expect(meta.headers()["content-type"]).toContain("xml");
      const xml = await meta.text();
      expect(xml).toContain(`entityID="${donnees.issuer}"`);
      expect(xml).toContain(donnees.metadonnees.element);
      expect(xml).toContain(donnees.metadonnees.binding);
      expect(xml).toContain(donnees.metadonnees.cle);
      const illisible = await api.get(`${API}/mock/saml/sso?SAMLRequest=${donnees.demandeIllisible}`);
      expect(illisible.status()).toBe(400);
      expect(await illisible.text()).toContain("Demande illisible");
    } finally { await api.dispose(); }
  });
});
