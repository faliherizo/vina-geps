/**
 * **Liaison des comptes par un vrai Keycloak — de bout en bout** (ADR 0093 ; docs/securite/CONFIGURER-ENTRA-ID.md § 9).
 *
 * Jean a déjà un compte de l'application, au courriel que Keycloak vérifie. Sa première connexion par l'annuaire ne
 * crée pas de second compte : le serveur ne trouve pas (iss, sub), la page « Relier mon compte » s'ouvre, Jean saisit
 * le mot de passe de son compte, l'identité est reliée et la session s'ouvre ; la connexion suivante entre
 * directement. Un inconnu authentifié par Keycloak n'obtient ni compte ni session, et la page le dit.
 *
 * Prérequis : `docker compose -f bac-a-sable/docker-compose.yml up -d keycloak` (port 18180), puis la campagne avec
 * `E2E_KEYCLOAK_LIAISON_URL=http://localhost:18180 ANNUAIRE_KEYCLOAK_LIAISON=bac-a-sable-liaison-secret-essai-uniquement
 * PUBLIC_APP_URL=http://localhost:5371`. Sans `E2E_KEYCLOAK_LIAISON_URL`, le fichier est sauté — et le rapport le dit.
 */
import { test, expect, request as pwRequest, type APIRequestContext, type Browser, type Page } from "@playwright/test";
import { superadminContext } from "./helpers/auth";
import { createTenant, setTenantModules, MODULES_SOCLE, TENANT_ADMIN_PASS, TENANT_ADMIN_USER, uniq } from "./helpers/organisme";
import { expectNoA11yViolations } from "./helpers/a11y";
import jeu from "../../donnees/keycloak/liaison-identite.json";

test.use({ storageState: { cookies: [], origins: [] } });

const KEYCLOAK = process.env.E2E_KEYCLOAK_LIAISON_URL ?? "";
const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;

test.skip(!KEYCLOAK, "E2E_KEYCLOAK_LIAISON_URL absent : docker compose -f bac-a-sable/docker-compose.yml up -d keycloak");

async function adminDeLOrganisme(tenantId: string): Promise<APIRequestContext> {
  const ctx = await pwRequest.newContext();
  const { token } = await (await ctx.post(`${API}/api/auth/login`, { data: { username: TENANT_ADMIN_USER, password: TENANT_ADMIN_PASS, tenantId } })).json();
  await ctx.dispose();
  return pwRequest.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${token}`, "X-Tenant-Id": tenantId } });
}

/** La page de connexion, l'organisme choisi, l'annuaire cliqué, puis la vraie page de Keycloak. */
async function parKeycloak(browser: Browser, tenantId: string, annuaireId: string, identifiant: string): Promise<Page> {
  const ctx = await browser.newContext({ storageState: { cookies: [], origins: [] }, locale: "fr-CA" });
  const page = await ctx.newPage();
  await page.goto("/");
  await page.getByTestId("login-tenant").selectOption(tenantId);
  await page.getByTestId(`login-annuaire-${annuaireId}`).click();
  await page.waitForURL(new RegExp(`/realms/${jeu.royaume}/protocol/openid-connect/auth`));
  await page.locator("#username").fill(identifiant);
  await page.locator("#password").fill(jeu.motDePasseKeycloak);
  await page.locator("#kc-login").click();
  return page;
}

async function comptesAuCourriel(adm: APIRequestContext, courriel: string): Promise<number> {
  const liste = await (await adm.get(`${API}/api/auth/users`)).json() as Array<{ username: string; email?: string | null }>;
  return liste.filter((u) => u.username === courriel || u.email === courriel).length;
}

test.describe.serial("Liaison des comptes — Keycloak, première connexion, preuve du mot de passe", () => {
  let tenantId = "";
  let annuaireId = "";
  let jeanId = "";
  let adm: APIRequestContext;

  test.beforeAll(async () => {
    const sa = await superadminContext();
    try {
      tenantId = await createTenant(sa, uniq("Liaison"));
      await setTenantModules(sa, tenantId, [...MODULES_SOCLE]);
    } finally { await sa.dispose(); }
    adm = await adminDeLOrganisme(tenantId);
    const a = jeu.annuaire;
    const r = await adm.post(`${API}/api/auth/annuaires`, { data: { ...a, emetteur: `${KEYCLOAK}${a.emetteurChemin}` } });
    expect(r.status(), await r.text()).toBe(201);
    annuaireId = (await r.json()).id;
    // Le compte existant de Jean, au même courriel que Keycloak — avec un mot de passe qu'il connaît.
    const c = await adm.post(`${API}/api/auth/users`, { data: { username: jeu.jean.courriel, password: jeu.jean.motDePasseApplication, role: "employee",
      mustChangePassword: false } });
    expect(c.status(), await c.text()).toBe(201);
    jeanId = (await c.json()).id;
  });

  test.afterAll(async () => { await adm?.dispose(); });

  test("Jean : Keycloak l'authentifie, aucun second compte, « Relier mon compte », mot de passe, relié ; la seconde connexion entre directement", async ({ browser }) => {
    test.setTimeout(120_000);
    let page = await parKeycloak(browser, tenantId, annuaireId, jeu.jean.identifiantKeycloak);
    try {
      await page.waitForURL(/\/connexion\/relier$/);
      await expect(page.getByTestId("relier-indice")).toContainText(jeu.jean.indice);
      await expect(page.getByTestId("relier-sans-compte")).toHaveCount(0);
      expect((await page.request.get("/api/auth/me")).status(), "aucune session avant la preuve").toBe(401);
      expect(await comptesAuCourriel(adm, jeu.jean.courriel), "aucun second compte").toBe(1);
      await expectNoA11yViolations(page);
      // Un mauvais mot de passe est refusé, annoncé ; le bon relie.
      await page.getByTestId("relier-mdp").fill("pas-le-bon");
      await page.getByTestId("relier-mdp-envoyer").click();
      await expect(page.getByTestId("relier-erreur")).toContainText("la preuve ne permet pas de relier ce compte");
      await expectNoA11yViolations(page);
      await page.getByTestId("relier-mdp").fill(jeu.jean.motDePasseApplication);
      await page.getByTestId("relier-mdp-envoyer").click();
      await page.waitForURL((u) => !u.pathname.startsWith("/connexion/relier"), { timeout: 20_000 });
      const moi = await (await page.request.get("/api/auth/me")).json();
      expect([moi.id, moi.tenantId], "la session est celle du compte existant").toEqual([jeanId, tenantId]);
    } finally { await page.context().close(); }

    page = await parKeycloak(browser, tenantId, annuaireId, jeu.jean.identifiantKeycloak);
    try {
      await page.waitForURL((u) => !u.pathname.includes("/realms/") && !u.pathname.includes("/api/auth/sso/"), { timeout: 20_000 });
      expect(new URL(page.url()).pathname).not.toBe("/connexion/relier");
      expect((await (await page.request.get("/api/auth/me")).json()).id, "droit dans le même compte").toBe(jeanId);
      expect(await comptesAuCourriel(adm, jeu.jean.courriel)).toBe(1);
    } finally { await page.context().close(); }
  });

  test("un inconnu authentifié par Keycloak : aucun compte créé, aucune session, un message clair", async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await parKeycloak(browser, tenantId, annuaireId, jeu.inconnu.identifiantKeycloak);
    try {
      await page.waitForURL(/\/connexion\/relier$/);
      await expect(page.getByTestId("relier-sans-compte")).toContainText(jeu.inconnu.message);
      await expect(page.getByTestId("relier-demande")).toBeDisabled();
      expect((await page.request.get("/api/auth/me")).status()).toBe(401);
      expect(await comptesAuCourriel(adm, jeu.inconnu.courriel), "aucun compte créé").toBe(0);
      await expectNoA11yViolations(page);
    } finally { await page.context().close(); }
  });
});
