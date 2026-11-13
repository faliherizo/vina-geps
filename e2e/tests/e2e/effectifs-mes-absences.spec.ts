/**
 * **Gestion des effectifs — lot R12 (Mes absences), de bout en bout.** AO 26-015-DO.
 *
 * Julie déclare elle-même son absence d'effectifs depuis Mon espace › Mes absences, dans le
 * formulaire unique des absences (ADR 0034, décision D-12) ; sans règle, l'absence est acceptée.
 * Elle la retrouve dans sa liste et l'annule. Un code « jour même » déclaré d'avance est refusé avec
 * un message qui dit quoi faire (P-GH-1, lot U5). Chaque état passe axe-core.
 */
import { test, expect, request as pwRequest, type APIRequestContext } from "@playwright/test";
import { superadminContext, uiLogin } from "./helpers/auth";
import { createTenant, setTenantModules, TENANT_ADMIN_PASS, TENANT_ADMIN_USER, uniq } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";
import { expectNoA11yViolations } from "./helpers/a11y";

test.use({ storageState: { cookies: [], origins: [] } });

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;
const MDP = "Passe!Test2026";

async function poser(ctx: APIRequestContext, tenantId: string, chemin: string, data: unknown, methode: "post" | "patch" = "post") {
  const r = await ctx[methode](`${API}${chemin}`, { headers: { "X-Tenant-Id": tenantId }, data });
  if (!r.ok()) throw new Error(`amorce ${chemin} : ${r.status()} ${await r.text()}`);
  return r.json();
}

async function adminDeLOrganisme(tenantId: string): Promise<APIRequestContext> {
  const ctx = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
  const { token } = await (await ctx.post(`${API}/api/auth/login`, { data: { username: TENANT_ADMIN_USER, password: TENANT_ADMIN_PASS, tenantId } })).json();
  await ctx.dispose();
  return pwRequest.newContext({ storageState: { cookies: [], origins: [] }, extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
}

async function amorcer(tenantId: string, identifiant: string): Promise<void> {
  const adm = await adminDeLOrganisme(tenantId);
  try {
    const sa = await superadminContext();
    await setTenantModules(sa, tenantId, ["effectifs_absences"]);
    await sa.dispose();
    const ref = (type: string, data: Record<string, unknown>) => poser(adm, tenantId, `/api/effectifs/referentiels/${type}`, { effectifDepuis: "2026-01-01", ...data });
    await ref("unites", { code: "EST", libelleFr: "Opérations Est" });
    await ref("corps_emploi", { code: "PARA", libelleFr: "Paramédics" });
    await ref("codes_absence", { code: "FORM", libelleFr: "Formation", comportement: "PAYE", attributs: { duree: "PONCTUELLE", planifiable: true } });
    await ref("codes_absence", { code: "DEC", libelleFr: "Décès d'un proche", comportement: "PAYE", attributs: { duree: "PONCTUELLE", planifiable: false } });
    const compte = await poser(adm, tenantId, "/api/auth/users", { username: identifiant, password: MDP, role: "employee" });
    await poser(adm, tenantId, `/api/auth/users/${compte.id}`, { mustChangePassword: false, securitySetupRequired: false }, "patch");
    await poser(adm, tenantId, "/api/effectifs/planification/ressources", { matricule: "P1", nom: "Julie Roy", corpsEmploiCode: "PARA", uniteCode: "EST", utilisateurId: compte.id });
  } finally { await adm.dispose(); }
}

test("déclarer soi-même une absence d'effectifs, la retrouver et l'annuler", async ({ browser }) => {
  test.setTimeout(240_000);
  const identifiant = uniq("julie").toLowerCase();
  const sa = await superadminContext();
  const tenantId = await createTenant(sa, uniq("EffAbs"));
  await sa.dispose();
  await amorcer(tenantId, identifiant);

  const ctx = await browser.newContext({ storageState: { cookies: [], origins: [] } });
  const page = await ctx.newPage();
  await page.goto("/");
  await uiLogin(page, identifiant, MDP, tenantId);
  await ouvrirNav(page, "nav-mesabsences");
  await expect(page.getByTestId("mab-vide")).toBeVisible();
  await page.getByTestId("mab-declarer").click();
  // P-GH-1 : un code « jour même » ne se planifie pas d'avance — l'option le dit, le serveur le refuse en disant quoi faire.
  await expect(page.getByTestId("abs-eff-code").locator("option", { hasText: "Décès d'un proche (ponctuelle, jour même seulement)" })).toHaveCount(1);
  await page.getByTestId("abs-eff-code").selectOption("DEC");
  await expect(page.getByTestId("abs-eff-code-aide")).toContainText("ne se planifie pas d'avance");
  await page.getByTestId("abs-start").fill("2031-05-12");
  await page.getByTestId("abs-end").fill("2031-05-13");
  await page.getByTestId("abs-submit").click();
  await expect(page.getByTestId("abs-form-error")).toContainText("le code « DEC » ne se planifie pas d'avance : il se déclare à partir du premier jour d'absence (2031-05-12");
  await expect(page.getByTestId("abs-form-error")).toContainText("Choisissez un code planifiable, ou déclarez-la le jour venu");
  // Le pointeur reste sur le bouton cliqué : son survol, en transition, se mesurerait à mi-couleur.
  await page.mouse.move(0, 0);
  await page.waitForTimeout(400);
  await expectNoA11yViolations(page);
  await page.getByTestId("abs-eff-code").selectOption("FORM");
  await expectNoA11yViolations(page);
  await page.getByTestId("abs-submit").click();
  await expect(page.getByTestId("mab-info")).toContainText("Absence déclarée.");
  await expect(page.getByTestId("mab-table")).toContainText("Du 2031-05-12 au 2031-05-13");
  await expect(page.getByTestId("mab-table")).toContainText("Acceptée");
  await expectNoA11yViolations(page);
  await page.locator('[data-testid^="mab-"][data-testid$="-annuler"]').click();
  await expect(page.getByTestId("mab-info")).toContainText("Absence du 2031-05-12 au 2031-05-13 annulée.");
  await expect(page.getByTestId("mab-table")).toContainText("Annulée");
  await ctx.close();
});
