/**
 * E2E — **récepteur de webhooks de démonstration** : brancher abonne un récepteur embarqué à
 * tous les événements ; l'essai émet un événement que l'ordonnanceur livre **réellement**, signé ;
 * le journal du récepteur (JSON et page lisible) dit « Valide » ; une panne réglée à 500 fait
 * échouer la livraison, visible dans le journal des envois de l'écran, et la retentative est planifiée.
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

import donnees from "./donnees/integrations/webhooks.json";
import { ouvrirNav } from "./helpers/nav";

test.describe.serial("Intégrations — récepteur de webhooks de démonstration", () => {
  test.beforeEach(async ({ page }) => { await forcerFrancais(page); });
  let tenantId = ""; let api: APIRequestContext;
  test.beforeAll(async () => { ({ tenantId, api } = await preparer("Hook")); await api.delete(`${API}/mock/webhooks/recepteur/journal`); });
  test.afterAll(async () => {
    await api.put(`${API}/mock/webhooks/recepteur/reglages`, { data: { codeReponse: 200 } }).catch(() => {});
    await api.post(`${API}/api/integrations/demonstration/webhooks/debrancher`).catch(() => {});
    await api.dispose();
  });

  test("**brancher, essayer : livraison réelle signée au journal ; panne 500 : échec visible et retentative ; débrancher**", async ({ page }) => {
    test.setTimeout(120_000);
    await selectTenant(page, tenantId);
    await ouvrirNav(page, "nav-integrations");
    await expect(page.getByTestId("carte-webhooks-etat")).toContainText("Non configuré");
    await page.getByTestId("carte-webhooks-demo").click();
    await expect(page.getByTestId("carte-webhooks-etat")).toContainText("Démonstration");
    await expect(page.getByTestId("carte-webhooks")).toContainText("/mock/webhooks/recepteur/journal.html");
    const ligne = page.getByTestId("webhooks-table").locator("tbody tr").filter({ hasText: donnees.description });
    await expect(ligne).toHaveCount(1);
    await page.getByTestId("carte-webhooks-essai").click();
    await expect(page.getByTestId("carte-webhooks-essai-resultat")).toContainText("Essai réussi");
    // L'ordonnanceur livre (tic toutes les 3 s en e2e) ; le récepteur a vérifié la signature.
    await expect.poll(async () => (await (await api.get(`${API}/mock/webhooks/recepteur/journal`)).json()).length, { timeout: 30_000 }).toBeGreaterThanOrEqual(1);
    const journal = await (await api.get(`${API}/mock/webhooks/recepteur/journal`)).json();
    expect(journal[0]).toMatchObject({ evenement: donnees.evenementEssai, signatureValide: true, codeRendu: 200 });
    // La page lisible du journal, servie par le serveur : titre, tableau avec légende, « Valide ».
    await page.goto(`${API}/mock/webhooks/recepteur/journal.html`);
    await expect(page.locator("h1")).toHaveText(donnees.journalPage.titre);
    await expect(page.locator("table caption")).toHaveCount(1);
    await expect(page.locator("tbody")).toContainText(donnees.journalPage.valide);
    await expect(page.locator("html")).toHaveAttribute("lang", "fr");

    // Panne : le récepteur répond 500 ; l'envoi échoue, reste « en attente » de sa retentative.
    await api.put(`${API}/mock/webhooks/recepteur/reglages`, { data: { codeReponse: donnees.panne } });
    await api.post(`${API}/api/integrations/demonstration/webhooks/essai`);
    await expect.poll(async () => (await (await api.get(`${API}/mock/webhooks/recepteur/journal`)).json())[0]?.codeRendu, { timeout: 30_000 }).toBe(donnees.panne);
    await selectTenant(page, tenantId);
    await ouvrirNav(page, "nav-integrations");
    await page.getByTestId("webhooks-table").locator("tbody tr").filter({ hasText: donnees.description }).locator("[data-testid^='webhook-envois-']").click();
    const envois = page.getByTestId("envois-table").locator("tbody tr");
    await expect(envois.first()).toContainText("En attente");
    await expect(envois.first()).toContainText(String(donnees.panne));
    await api.put(`${API}/mock/webhooks/recepteur/reglages`, { data: { codeReponse: 200 } });

    // Débrancher désactive le webhook de démonstration (il a livré : il ne se supprime pas).
    await page.getByTestId("carte-webhooks-demo").click();
    await expect(page.getByTestId("carte-webhooks-etat")).toContainText("Non configuré");
    await expect(page.getByTestId("webhooks-table").locator("tbody tr").filter({ hasText: donnees.description }).locator('[data-testid$="-etat"]')).toHaveText("Désactivé");
  });
});
