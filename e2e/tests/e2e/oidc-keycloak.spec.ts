/**
 * **OIDC contre un vrai Keycloak — de bout en bout, profil par profil.** ADR 0075 ;
 * docs/urgences-sante/OIDC-KEYCLOAK.md.
 *
 * L'administration de l'organisme déclare à l'écran un annuaire « OpenID Connect » qui pointe vers le
 * royaume `vina-geps` (e2e/donnees/keycloak/realm-vina-geps.json). Puis chaque profil de
 * `profils-oidc.json` se connecte par la vraie page de Keycloak, et l'essai vérifie ce que le jeu
 * annonce : le rôle obtenu dans l'application, les menus visibles et absents, le droit d'administrer
 * les annuaires — ou le refus, avec son motif. La hiérarchie se vérifie deux fois : statique (rôles
 * composites, groupes hérités, priorité des règles) et dynamique (une promotion dans Keycloak suit à
 * la connexion suivante ; une désactivation locale l'emporte sur Keycloak).
 *
 * Prérequis : `scripts/keycloak-essai.sh start`, puis la campagne avec
 * `E2E_KEYCLOAK_URL=http://localhost:8180 ANNUAIRE_KEYCLOAK=vina-geps-secret-essai-uniquement PUBLIC_APP_URL=http://localhost:5331`.
 * Sans `E2E_KEYCLOAK_URL`, le fichier est sauté — et le rapport le dit.
 */
import { test, expect, request as pwRequest, type APIRequestContext, type Browser, type Page } from "@playwright/test";
import { superadminContext } from "./helpers/auth";
import { createTenant, selectTenant, setTenantModules, MODULES_SOCLE, MODULES_TRANSVERSAUX, TENANT_ADMIN_PASS, TENANT_ADMIN_USER, uniq } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";
import { expectNoA11yViolations } from "./helpers/a11y";
import jeu from "../../donnees/keycloak/profils-oidc.json";

test.use({ storageState: { cookies: [], origins: [] } });

const KEYCLOAK = process.env.E2E_KEYCLOAK_URL ?? "";
const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;
const ROYAUME = "vina-geps";

interface Attendu {
  issue: "admis" | "refuse" | "refuse_par_keycloak"; role?: string; statut?: number; message?: string;
  menusVisibles?: string[]; menusAbsents?: string[]; administreAnnuaires?: boolean;
}
interface Profil { cle: string; identifiant: string; courriel: string; groupes: string[]; hierarchie?: string; attendu: Attendu }
const PROFILS = jeu.profils as Profil[];
const profil = (cle: string) => PROFILS.find((p) => p.cle === cle)!;

test.skip(!KEYCLOAK, "E2E_KEYCLOAK_URL absent : lancez scripts/keycloak-essai.sh start (docs/urgences-sante/OIDC-KEYCLOAK.md)");

/** L'API d'administration de Keycloak, pour faire évoluer la hiérarchie pendant l'essai. */
async function adminKeycloak(): Promise<APIRequestContext> {
  const ctx = await pwRequest.newContext();
  const r = await ctx.post(`${KEYCLOAK}/realms/master/protocol/openid-connect/token`,
    { form: { client_id: "admin-cli", username: "admin", password: "admin", grant_type: "password" } });
  const { access_token } = await r.json();
  await ctx.dispose();
  return pwRequest.newContext({ baseURL: `${KEYCLOAK}/admin/realms/${ROYAUME}/`, extraHTTPHeaders: { Authorization: `Bearer ${access_token}` } });
}

async function idKeycloak(kc: APIRequestContext, chemin: string, filtre: (x: Record<string, unknown>) => boolean): Promise<string> {
  const liste = await (await kc.get(chemin)).json() as Array<Record<string, unknown>>;
  const trouve = liste.find(filtre);
  if (!trouve) throw new Error(`Keycloak : rien ne correspond à ${chemin}`);
  return String(trouve.id);
}

async function adminDeLOrganisme(tenantId: string): Promise<APIRequestContext> {
  const ctx = await pwRequest.newContext();
  const { token } = await (await ctx.post(`${API}/api/auth/login`, { data: { username: TENANT_ADMIN_USER, password: TENANT_ADMIN_PASS, tenantId } })).json();
  await ctx.dispose();
  return pwRequest.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${token}`, "X-Tenant-Id": tenantId } });
}

/** La page de connexion de l'application, l'organisme choisi, l'annuaire cliqué, puis la vraie page de Keycloak. */
async function parKeycloak(browser: Browser, tenantId: string, annuaireId: string, identifiant: string): Promise<Page> {
  const ctx = await browser.newContext({ storageState: { cookies: [], origins: [] }, locale: "fr-CA" });
  const page = await ctx.newPage();
  await page.goto("/");
  await page.getByTestId("login-tenant").selectOption(tenantId);
  const bouton = page.getByTestId(`login-annuaire-${annuaireId}`);
  await expect(bouton).toContainText(jeu.annuaire.nom);
  await bouton.click();
  await page.waitForURL(new RegExp(`/realms/${ROYAUME}/protocol/openid-connect/auth`));
  await page.locator("#username").fill(identifiant);
  await page.locator("#password").fill(jeu.motDePasse);
  await page.locator("#kc-login").click();
  return page;
}

async function verifierAdmis(page: Page, p: Profil, tenantId: string): Promise<string> {
  await expect(page.getByTestId("logout"), `${p.cle} : la session est ouverte`).toBeVisible({ timeout: 30_000 });
  const moi = await (await page.request.get("/api/auth/me")).json();
  expect([moi.role, moi.tenantId, moi.email ?? moi.username], `${p.cle} : rôle et organisme`).toEqual([p.attendu.role, tenantId, p.courriel]);
  for (const s of await page.locator(".nav-group summary").all()) await s.click({ timeout: 2_000 }).catch(() => {});
  for (const nav of p.attendu.menusVisibles ?? []) await expect(page.getByTestId(nav), `${p.cle} voit ${nav}`).toHaveCount(1);
  for (const nav of p.attendu.menusAbsents ?? []) await expect(page.getByTestId(nav), `${p.cle} ne voit pas ${nav}`).toHaveCount(0);
  // Le menu masqué n'est pas une garde : l'API doit refuser aussi.
  const annuaires = await page.request.get("/api/auth/annuaires");
  expect(annuaires.status(), `${p.cle} : administrer les annuaires`).toBe(p.attendu.administreAnnuaires ? 200 : 403);
  return String(moi.id);
}

test.describe.serial("OIDC — Keycloak, profils et hiérarchies", () => {
  let tenantId = "";
  let annuaireId = "";

  test.beforeAll(async () => {
    const sa = await superadminContext();
    try {
      tenantId = await createTenant(sa, uniq("OIDC"));
      await setTenantModules(sa, tenantId, [...MODULES_SOCLE, ...MODULES_TRANSVERSAUX, "effectifs", "effectifs_regles", "effectifs_planification",
        "effectifs_disponibilites", "effectifs_absences", "effectifs_echanges", "effectifs_agph", "effectifs_presence", "effectifs_temps"]);
    } finally { await sa.dispose(); }
  });

  test("l'administration déclare l'annuaire Keycloak à l'écran ; la page de connexion le propose", async ({ page }) => {
    const a = jeu.annuaire;
    await selectTenant(page, tenantId);
    await ouvrirNav(page, "nav-admin");
    await page.getByTestId("admin-tab-organisme").click();
    await page.getByTestId("annuaire-nouveau-ouvrir").click();
    await page.getByTestId("annuaire-nouveau-nom").fill(a.nom);
    await page.getByTestId("annuaire-nouveau-fournisseur").selectOption(a.fournisseur);
    await page.getByTestId("annuaire-nouveau-emetteur").fill(`${KEYCLOAK}${a.emetteurChemin}`);
    await page.getByTestId("annuaire-nouveau-client").fill(a.clientId);
    await page.getByTestId("annuaire-nouveau-variable").fill(a.variableSecret);
    await page.getByTestId("annuaire-nouveau-domaines").fill(a.domaines.join(", "));
    await page.getByTestId("annuaire-nouveau-revendication").selectOption(a.revendication);
    await page.getByTestId("annuaire-nouveau-regles").fill(a.correspondances.map((c) => `${c.groupe} = ${c.role}`).join("\n"));
    await page.getByTestId("annuaire-nouveau-defaut").selectOption("");
    await expectNoA11yViolations(page);
    await page.getByTestId("annuaire-nouveau-enregistrer").click();
    await expect(page.getByTestId("annuaires-info")).toContainText(`Annuaire « ${a.nom} » déclaré.`);
    const ligne = page.locator('[data-testid^="annuaire-ann-"]').filter({ hasText: a.nom });
    await expect(ligne).toContainText("OpenID Connect");
    // Le secret est celui du client Keycloak, déployé dans l'environnement du serveur : sans lui, aucune connexion.
    await expect(ligne).toContainText("✓ déployé");
    annuaireId = (await ligne.getAttribute("data-testid"))!.replace(/^annuaire-/, "");

    await page.context().clearCookies();
    await page.goto("/");
    await page.getByTestId("login-tenant").selectOption(tenantId);
    await expect(page.getByTestId(`login-annuaire-${annuaireId}`)).toContainText(a.nom);
    await expectNoA11yViolations(page);
  });

  for (const p of PROFILS) {
    test(`${p.cle} (${p.identifiant}, ${p.groupes.join(" + ") || "aucun groupe"}) → ${p.attendu.issue === "admis" ? `rôle ${p.attendu.role}` : "refusé"}`, async ({ browser }) => {
      test.setTimeout(120_000);
      const page = await parKeycloak(browser, tenantId, annuaireId, p.identifiant);
      try {
        if (p.attendu.issue === "admis") {
          await verifierAdmis(page, p, tenantId);
          await expectNoA11yViolations(page);
        } else if (p.attendu.issue === "refuse") {
          // Keycloak a authentifié ; c'est l'organisme qui refuse, au retour, avec le motif.
          await page.waitForURL(/\/api\/auth\/sso\/annuaires\/callback/);
          await expect(page.locator("body")).toContainText(p.attendu.message!);
          const moi = await page.request.get("/api/auth/me");
          expect(moi.status(), `${p.cle} : aucune session`).toBe(401);
        } else {
          // Keycloak refuse lui-même : l'usager ne quitte jamais sa page, l'application ne voit rien.
          await expect(page).toHaveURL(new RegExp(`/realms/${ROYAUME}/`));
          await expect(page.locator("body")).toContainText(new RegExp(p.attendu.message!, "i"));
        }
      } finally { await page.context().close(); }
    });
  }

  test("hiérarchie dynamique : promue chef dans Keycloak, Julie devient gestionnaire ; retirée, elle redevient employée ; désactivée ici, elle est refusée", async ({ browser }) => {
    test.setTimeout(240_000);
    const julie = profil("employe");
    const kc = await adminKeycloak();
    const adm = await adminDeLOrganisme(tenantId);
    const userId = await idKeycloak(kc, `users?username=${julie.identifiant}&exact=true`, () => true);
    const chefs = await (await kc.get(`group-by-path/${encodeURI("Urgences-santé/Opérations/Chefs d'équipe")}`)).json() as { id: string };
    try {
      let page = await parKeycloak(browser, tenantId, annuaireId, julie.identifiant);
      const compte = await verifierAdmis(page, julie, tenantId);
      await page.context().close();

      expect((await kc.put(`users/${userId}/groups/${chefs.id}`)).status()).toBe(204);
      page = await parKeycloak(browser, tenantId, annuaireId, julie.identifiant);
      await verifierAdmis(page, { ...julie, attendu: { ...julie.attendu, role: "manager", menusVisibles: ["nav-ustemps"], menusAbsents: ["nav-admin"] } }, tenantId);
      expect((await (await page.request.get("/api/auth/me")).json()).id, "le même compte, reclassé — pas un second").toBe(compte);
      await page.context().close();

      expect((await kc.delete(`users/${userId}/groups/${chefs.id}`)).status()).toBe(204);
      page = await parKeycloak(browser, tenantId, annuaireId, julie.identifiant);
      await verifierAdmis(page, julie, tenantId);
      await page.context().close();

      // La désactivation locale est la mesure d'urgence : elle l'emporte sur ce que dit Keycloak.
      expect((await adm.patch(`${API}/api/auth/users/${compte}`, { data: { active: false } })).ok()).toBe(true);
      page = await parKeycloak(browser, tenantId, annuaireId, julie.identifiant);
      await page.waitForURL(/\/api\/auth\/sso\/annuaires\/callback/);
      await expect(page.locator("body")).toContainText(`le compte « ${julie.courriel} » est désactivé dans l'application`);
      await page.context().close();
    } finally {
      await kc.delete(`users/${userId}/groups/${chefs.id}`).catch(() => undefined);
      await kc.dispose();
      await adm.dispose();
    }
  });
});
