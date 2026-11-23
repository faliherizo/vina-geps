/**
 * **Codes d'absence dynamiques, puis « Mes absences »**, joués depuis `donnees/absences/codes-et-mes-absences.json`.
 *
 * L'administration de l'organisme tient ses codes dans la page « Codes d'absence » : elle en ajoute, en remplace un à
 * partir d'aujourd'hui (nouveau libellé) et en ferme un. Julie déclare ensuite ses absences : elle voit les codes en
 * vigueur, avec leur libellé du jour, et jamais le code fermé. Un code « jour même » déclaré d'avance est refusé en
 * disant quoi faire, et une absence acceptée s'annule. Chaque état passe axe-core.
 */
import { test, expect, request as pwRequest, type APIRequestContext, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { superadminContext, uiLogin } from "./helpers/auth";
import { createTenant, selectTenant, setTenantModules, TENANT_ADMIN_PASS, TENANT_ADMIN_USER, uniq } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";
import { expectNoA11yViolations } from "./helpers/a11y";
import { jour } from "./helpers/urgences-sante";
import { optionsFilmees } from "./helpers/film";
import { raconter } from "./helpers/recit";

test.use({ storageState: { cookies: [], origins: [] } });

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;
const MDP = "Passe!Test2026";

interface Ajout { code: string; libelleFr: string; comportement: string; duree: string; planifiable: boolean; date: string }
interface Jeu {
  codesInitiaux: Array<Record<string, unknown>>;
  ajouts: Ajout[];
  remplacement: { code: string; libelleFr: string; date: string };
  fermeture: { code: string; date: string };
  attendu: { offerts: Record<string, string>; absents: string[] };
  declarations: Array<{ code: string; du: string; au: string; statut?: string; erreur?: string }>;
  annuler: { du: string; au: string };
}
const JEU = JSON.parse(readFileSync(join(__dirname, "../../donnees/absences/codes-et-mes-absences.json"), "utf8")) as Jeu;
const date = (v: string) => (v === "aujourdhui" ? jour(0) : v);

async function poser(ctx: APIRequestContext, tenantId: string, chemin: string, data: unknown, methode: "post" | "patch" = "post") {
  const r = await ctx[methode](`${API}${chemin}`, { headers: { "X-Tenant-Id": tenantId }, data });
  if (!r.ok()) throw new Error(`amorce ${chemin} : ${r.status()} ${await r.text()}`);
  return r.json();
}

async function amorcer(tenantId: string, identifiant: string): Promise<void> {
  const sa = await superadminContext();
  await setTenantModules(sa, tenantId, ["effectifs", "effectifs_absences"]);
  await sa.dispose();
  const ctx = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
  const { token } = await (await ctx.post(`${API}/api/auth/login`, { data: { username: TENANT_ADMIN_USER, password: TENANT_ADMIN_PASS, tenantId } })).json();
  await ctx.dispose();
  const adm = await pwRequest.newContext({ storageState: { cookies: [], origins: [] }, extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
  try {
    const ref = (type: string, data: Record<string, unknown>) => poser(adm, tenantId, `/api/effectifs/referentiels/${type}`, { effectifDepuis: "2026-01-01", ...data });
    await ref("unites", { code: "EST", libelleFr: "Opérations Est" });
    await ref("corps_emploi", { code: "PARA", libelleFr: "Paramédics" });
    for (const c of JEU.codesInitiaux) await ref("codes_absence", c);
    const compte = await poser(adm, tenantId, "/api/auth/users", { username: identifiant, password: MDP, role: "employee" });
    await poser(adm, tenantId, `/api/auth/users/${compte.id}`, { mustChangePassword: false, securitySetupRequired: false }, "patch");
    await poser(adm, tenantId, "/api/effectifs/planification/ressources", { matricule: "P1", nom: "Julie Roy", corpsEmploiCode: "PARA", uniteCode: "EST", utilisateurId: compte.id });
  } finally { await adm.dispose(); }
}

async function ajouter(page: Page, a: Ajout): Promise<void> {
  await raconter(page, `L'administration ajoute le code ${a.code}, ${a.libelleFr}, ${a.planifiable ? "planifiable" : "non planifiable"}, en vigueur à partir d'aujourd'hui.`);
  await page.getByTestId("ref-ajouter").click();
  await page.getByTestId("ref-form-code").fill(a.code);
  await page.getByTestId("ref-form-libelleFr").fill(a.libelleFr);
  await page.getByTestId("ref-form-comportement").selectOption(a.comportement);
  await page.getByTestId("ref-form-attr-duree").selectOption(a.duree);
  if (a.planifiable) await page.getByTestId("ref-form-attr-planifiable").check();
  await page.getByTestId("ref-form-date").fill(date(a.date));
  await page.getByTestId("ref-form-enregistrer").click();
  await expect(page.getByTestId("ref-succes")).toBeVisible();
  await expect(page.getByTestId(`ref-ligne-${a.code}`)).toContainText(a.libelleFr);
}

test("l'administration tient les codes d'absence ; Julie déclare avec les codes du jour, jamais un code fermé", async ({ browser }) => {
  test.setTimeout(300_000);
  const identifiant = uniq("julie").toLowerCase();
  const sa = await superadminContext();
  const tenantId = await createTenant(sa, uniq("CodesAbs"));
  await sa.dispose();
  await amorcer(tenantId, identifiant);

  // ── L'administration : ajouter, remplacer, fermer ───────────────────────────────────────
  const ctxAdmin = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] } }));
  const admin = await ctxAdmin.newPage();
  await selectTenant(admin, tenantId);
  await ouvrirNav(admin, "nav-uscodesabsence");
  await expect(admin.getByTestId("uscodesabsence-titre")).toBeVisible();
  await expect(admin.getByTestId("ref-type")).toHaveCount(0);
  await raconter(admin, "Codes d'absence. L'administrateur de l'organisme ouvre la page des codes d'absence : Maladie et Congé sans solde y sont déjà.");
  for (const c of JEU.codesInitiaux) await expect(admin.getByTestId(`ref-ligne-${String(c.code)}`)).toBeVisible();
  await expectNoA11yViolations(admin);
  for (const a of JEU.ajouts) await ajouter(admin, a);

  const r = JEU.remplacement;
  await raconter(admin, `Il remplace ensuite le code ${r.code} à partir d'aujourd'hui : son nouveau libellé est ${r.libelleFr}. L'ancienne version reste dans l'historique.`);
  await admin.getByTestId(`ref-remplacer-${r.code}`).click();
  await admin.getByTestId("ref-form-libelleFr").fill(r.libelleFr);
  await admin.getByTestId("ref-form-date").fill(date(r.date));
  await expectNoA11yViolations(admin);
  await admin.getByTestId("ref-form-enregistrer").click();
  await expect(admin.getByTestId(`ref-ligne-${r.code}`)).toContainText(r.libelleFr);

  const f = JEU.fermeture;
  await raconter(admin, "Puis il ferme le Congé sans solde à partir d'aujourd'hui : le code disparaît de la liste en vigueur. L'historique du code Maladie montre ses deux versions.");
  await admin.getByTestId(`ref-fermer-${f.code}`).click();
  await admin.getByTestId("ref-fermeture-date").fill(date(f.date));
  await admin.getByTestId("ref-fermeture-confirmer").click();
  await expect(admin.getByTestId(`ref-ligne-${f.code}`)).toHaveCount(0);
  await admin.getByTestId(`ref-historique-${r.code}`).click();
  await expect(admin.getByTestId("ref-historique-versions").locator("li")).toHaveCount(2);
  await expectNoA11yViolations(admin);
  await ctxAdmin.close();

  // ── Julie : les codes du jour, puis ses déclarations ───────────────────────────────────
  const ctx = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] } }));
  const page = await ctx.newPage();
  await page.goto("/");
  await uiLogin(page, identifiant, MDP, tenantId);
  await ouvrirNav(page, "nav-mesabsences");
  await raconter(page, "Julie Roy ouvre Mes absences. Elle ne voit pas la page d'administration des codes, et la liste qu'on lui offre ne porte que les codes en vigueur aujourd'hui, avec leur libellé du jour : jamais le congé sans solde fermé.");
  await expect(page.getByTestId("nav-uscodesabsence")).toHaveCount(0);
  await page.getByTestId("mab-declarer").click();
  const liste = page.getByTestId("abs-eff-code");
  for (const [code, libelle] of Object.entries(JEU.attendu.offerts)) await expect(liste.locator(`option[value="${code}"]`)).toContainText(libelle);
  for (const code of JEU.attendu.absents) await expect(liste.locator(`option[value="${code}"]`)).toHaveCount(0);
  await expectNoA11yViolations(page);
  for (const d of JEU.declarations) {
    await raconter(page, `Julie déclare une absence, code ${d.code}, du ${d.du} au ${d.au}.${d.erreur ? " Ce code ne se planifie pas d'avance : la déclaration doit être refusée." : " Elle doit être acceptée."}`);
    if (!(await page.getByTestId("abs-eff-code").isVisible())) await page.getByTestId("mab-declarer").click();
    await page.getByTestId("abs-eff-code").selectOption(d.code);
    await page.getByTestId("abs-start").fill(d.du);
    await page.getByTestId("abs-end").fill(d.au);
    await page.getByTestId("abs-submit").click();
    if (d.erreur) {
      await expect(page.getByTestId("abs-form-error")).toContainText(d.erreur);
      await page.keyboard.press("Escape");
      continue;
    }
    await expect(page.getByTestId("mab-info")).toContainText("Absence déclarée.");
    await expect(page.getByTestId("mab-table")).toContainText(`Du ${d.du} au ${d.au}`);
  }
  await expect(page.getByTestId("mab-table").locator("tbody tr")).toHaveCount(JEU.declarations.filter((d) => !d.erreur).length);
  const a = JEU.annuler;
  await raconter(page, `Julie annule son absence du ${a.du}.`);
  await page.getByTestId("mab-table").locator("tr", { hasText: `Du ${a.du} au ${a.au}` }).locator('[data-testid$="-annuler"]').click();
  await expect(page.getByTestId("mab-info")).toContainText(`Absence du ${a.du} au ${a.au} annulée.`);
  await expectNoA11yViolations(page);
  await ctx.close();
});
