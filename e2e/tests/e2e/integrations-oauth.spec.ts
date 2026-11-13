/**
 * E2E — **clients d'API OAuth 2.0 (client_credentials), côté consommateur** : la carte crée un
 * client de démonstration et l'essai obtient puis vérifie un jeton ; un client créé à l'écran
 * Administration donne un secret (montré une fois) avec lequel on obtient un jeton sur
 * `/api/oauth/token` et l'on appelle la ressource protégée de démonstration — 401 sans jeton ou
 * avec un jeton faux, 403 sans la portée, 200 avec.
 */
import { test, expect, request as pwRequest, type Page, type APIRequestContext } from "@playwright/test";
import { superadminContext, forcerFrancais } from "./helpers/auth";
import { createTenant, selectTenant, setTenantModules, MODULES_SOCLE, MODULES_TRANSVERSAUX, TENANT_ADMIN_PASS, TENANT_ADMIN_USER, uniq } from "./helpers/organisme";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;
test.use({ storageState: { cookies: [], origins: [] } });

/** Un établissement avec le module intégrations, et un contexte API de son administrateur. */
async function preparer(prefixe: string, modules: string[] = []): Promise<{ tenantId: string; api: APIRequestContext }> {
  const sa = await superadminContext();
  let tenantId = "";
  try {
    tenantId = await createTenant(sa, uniq(prefixe), "enterprise");
    await setTenantModules(sa, tenantId, [...MODULES_SOCLE, ...MODULES_TRANSVERSAUX, ...modules]);
  } finally { await sa.dispose(); }
  const connexion = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
  const jeton = (await (await connexion.post(`${API}/api/auth/login`, { data: { username: TENANT_ADMIN_USER, password: TENANT_ADMIN_PASS, tenantId } })).json()).token;
  await connexion.dispose();
  const api = await pwRequest.newContext({ storageState: { cookies: [], origins: [] }, extraHTTPHeaders: { Authorization: `Bearer ${jeton}`, "X-Tenant-Id": tenantId } });
  return { tenantId, api };
}

import donnees from "./donnees/integrations/oauth.json";
import { ouvrirNav } from "./helpers/nav";

test.describe.serial("Intégrations — clients d'API de démonstration (OAuth 2.0)", () => {
  test.beforeEach(async ({ page }) => { await forcerFrancais(page); });
  let tenantId = ""; let api: APIRequestContext;
  test.beforeAll(async () => { ({ tenantId, api } = await preparer("OAuth")); });
  test.afterAll(async () => { await api.post(`${API}/api/integrations/demonstration/api/debrancher`).catch(() => {}); await api.dispose(); });

  test("**brancher crée le client de démonstration ; l'essai obtient et vérifie un jeton ; débrancher le révoque**", async ({ page }) => {
    test.setTimeout(90_000);
    await selectTenant(page, tenantId);
    await ouvrirNav(page, "nav-integrations");
    await expect(page.getByTestId("carte-api-etat")).toContainText("Non configuré");
    await page.getByTestId("carte-api-demo").click();
    await expect(page.getByTestId("carte-api-etat")).toContainText("Démonstration");
    await expect(page.getByTestId("carte-api")).toContainText("client de démonstration cid_");
    await page.getByTestId("carte-api-essai").click();
    await expect(page.getByTestId("carte-api-essai-resultat")).toContainText("Essai réussi");
    await expect(page.getByTestId("carte-api-essai-resultat")).toContainText(donnees.essaiAttendu);
    const clients = await (await api.get(`${API}/api/integrations/api-clients`)).json();
    expect(clients.find((c: any) => c.name === donnees.clientDemo)?.active).toBe(true);
    await page.getByTestId("carte-api-demo").click();
    await expect(page.getByTestId("carte-api-etat")).not.toContainText("Démonstration");
    const apres = await (await api.get(`${API}/api/integrations/api-clients`)).json();
    expect(apres.find((c: any) => c.name === donnees.clientDemo)?.active).toBe(false);
  });

  test("**côté consommateur : secret montré une fois, jeton client_credentials, ressource protégée 401/403/200**", async () => {
    const cree = await (await api.post(`${API}/api/integrations/api-clients`, { data: donnees.clientEssai })).json();
    expect(cree.clientSecret).toMatch(/^csec_/);
    const anonyme = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
    try {
      expect((await anonyme.get(`${API}/mock/oauth/ressource`)).status()).toBe(donnees.ressource.sansJeton);
      expect((await anonyme.get(`${API}/mock/oauth/ressource`, { headers: { Authorization: "Bearer faux" } })).status()).toBe(donnees.ressource.jetonFaux);
      expect((await anonyme.post(`${API}/api/oauth/token`, { data: { grant_type: "client_credentials", client_id: cree.clientId, client_secret: "faux" } })).status()).toBe(401);
      const jeton = await (await anonyme.post(`${API}/api/oauth/token`, { data: { grant_type: "client_credentials", client_id: cree.clientId, client_secret: cree.clientSecret } })).json();
      expect(jeton.token_type).toBe("Bearer");
      const ok = await anonyme.get(`${API}/mock/oauth/ressource`, { headers: { Authorization: `Bearer ${jeton.access_token}` } });
      expect(ok.status()).toBe(200);
      expect(await ok.json()).toMatchObject({ client: cree.clientId, tenantId, scopes: donnees.clientEssai.scopes, message: donnees.ressource.message });
      const trop = await anonyme.get(`${API}/mock/oauth/ressource?scope=write`, { headers: { Authorization: `Bearer ${jeton.access_token}` } });
      expect(trop.status()).toBe(donnees.ressource.porteeInsuffisante);
      expect(trop.headers()["www-authenticate"]).toContain("insufficient_scope");
    } finally { await anonyme.dispose(); }
  });
});
