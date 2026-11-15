/**
 * **Paie (ADR 0076), de bout en bout contre le Java.** L'administration crée le dossier de rémunération de Julie, puis
 * tente de préparer une paie de 2031 : aucun barème n'est publié pour cette année, et le refus le dit en le nommant —
 * une paie ne se calcule jamais sur un barème deviné. Julie, elle, n'a encore aucun bulletin. Chaque état passe axe-core.
 *
 * Le second essai joue la **chaîne complète** sur la semaine écoulée, que le barème de l'année couvre : Julie déclare et
 * soumet sa feuille, l'administration l'approuve, prépare le cycle à l'écran, lit le bulletin, traite la paie, puis
 * lit les remises et le relevé ; Julie lit enfin son bulletin, du brut au net.
 */
import { test, expect, request as pwRequest, type APIRequestContext } from "@playwright/test";
import { superadminContext, uiLogin } from "./helpers/auth";
import { createTenant, selectTenant, setTenantModules, TENANT_ADMIN_PASS, TENANT_ADMIN_USER, uniq } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";
import { expectNoA11yViolations } from "./helpers/a11y";
import { jour } from "./helpers/urgences-sante";
import { optionsFilmees } from "./helpers/film";

test.use({ storageState: { cookies: [], origins: [] } });

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;
const MDP = "Passe!Test2026";

async function poser(ctx: APIRequestContext, tenantId: string, chemin: string, data: unknown, methode: "post" | "patch" | "put" = "post") {
  const r = await ctx[methode](`${API}${chemin}`, { headers: { "X-Tenant-Id": tenantId }, data });
  if (!r.ok()) throw new Error(`amorce ${chemin} : ${r.status()} ${await r.text()}`);
  return r.json();
}

/** Un organisme avec la paie, une unité, et Julie : son compte et sa ressource d'effectifs. */
async function amorcer(tenantId: string, identifiant: string): Promise<void> {
  const sa = await superadminContext();
  await setTenantModules(sa, tenantId, ["effectifs_temps", "paie"]);
  await sa.dispose();
  const ctx = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
  const { token } = await (await ctx.post(`${API}/api/auth/login`, { data: { username: TENANT_ADMIN_USER, password: TENANT_ADMIN_PASS, tenantId } })).json();
  await ctx.dispose();
  const adm = await pwRequest.newContext({ storageState: { cookies: [], origins: [] }, extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
  try {
    const ref = (type: string, data: Record<string, unknown>) => poser(adm, tenantId, `/api/effectifs/referentiels/${type}`, { effectifDepuis: "2026-01-01", ...data });
    await ref("unites", { code: "EST", libelleFr: "Opérations Est" });
    await ref("corps_emploi", { code: "PARA", libelleFr: "Paramédics" });
    const compte = await poser(adm, tenantId, "/api/auth/users", { username: identifiant, password: MDP, role: "employee" });
    await poser(adm, tenantId, `/api/auth/users/${compte.id}`, { mustChangePassword: false, securitySetupRequired: false }, "patch");
    await poser(adm, tenantId, "/api/effectifs/planification/ressources", { matricule: "P1", nom: "Julie Roy", corpsEmploiCode: "PARA", uniteCode: "EST", utilisateurId: compte.id });
  } finally { await adm.dispose(); }
}

test("tenir le dossier de rémunération ; une année sans barème est refusée en la nommant ; chacun lit ses bulletins", async ({ browser }) => {
  test.setTimeout(180_000);
  const identifiant = uniq("julie").toLowerCase();
  const sa = await superadminContext();
  const tenantId = await createTenant(sa, uniq("Paie"));
  await sa.dispose();
  await amorcer(tenantId, identifiant);

  // ── L'administration ──────────────────────────────────────────────────────────────────
  const ctxAdmin = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] } }));
  const admin = await ctxAdmin.newPage();
  await selectTenant(admin, tenantId);
  await ouvrirNav(admin, "nav-paiedossiers");
  const ligne = admin.locator('[data-testid^="pdo-"][data-testid$="-modifier"]');
  await expect(ligne).toHaveCount(1);
  await expect(admin.getByTestId("pdo-manquants")).toContainText("1 personne(s) sans dossier");
  await ligne.click();
  await admin.getByTestId("pdo-taux").fill("32.50");
  await expectNoA11yViolations(admin);
  await admin.getByTestId("pdo-enregistrer").click();
  await expect(admin.getByTestId("pdo-succes")).toContainText("Dossier de Julie Roy enregistré.");
  await expect(admin.getByTestId("pdo-manquants")).toHaveCount(0);

  await ouvrirNav(admin, "nav-paiecycles");
  await expect(admin.getByTestId("pcy-vide")).toBeVisible();
  await admin.getByTestId("pcy-preparer-ouvrir").click();
  await admin.getByTestId("pcy-debut").fill("2031-01-13");
  await admin.getByTestId("pcy-fin").fill("2031-01-19");
  await admin.getByTestId("pcy-date-paie").fill("2031-01-23");
  await admin.getByTestId("pcy-creer").click();
  // Le motif se lit dans le dialogue, là où l'on regarde — et non sous son voile.
  await expect(admin.getByTestId("pcy-preparer-erreur")).toContainText("aucun barème de paie publié pour 2031");
  await admin.keyboard.press("Escape");

  await ouvrirNav(admin, "nav-paieparametres");
  await expect(admin.getByTestId("ppa-bareme")).toBeVisible();
  await expect(admin.getByTestId("ppa-fss")).toHaveValue("4.26");
  await expectNoA11yViolations(admin);
  await ctxAdmin.close();

  // ── Julie ─────────────────────────────────────────────────────────────────────────────
  const ctxJulie = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] } }));
  const julie = await ctxJulie.newPage();
  await julie.goto("/");
  await uiLogin(julie, identifiant, MDP, tenantId);
  await ouvrirNav(julie, "nav-mesbulletins");
  await expect(julie.getByTestId("mbu-vide")).toBeVisible();
  await expect(julie.getByTestId("nav-paiecycles")).toHaveCount(0);
  await expectNoA11yViolations(julie);
  await ctxJulie.close();
});

test("la chaîne complète : feuille approuvée, cycle préparé et traité à l'écran, remises, bulletin lu par Julie", async ({ browser }) => {
  test.setTimeout(240_000);
  const identifiant = uniq("julie").toLowerCase();
  const sa = await superadminContext();
  const tenantId = await createTenant(sa, uniq("PaieComplete"));
  await sa.dispose();
  await amorcer(tenantId, identifiant);

  // ── La semaine de Julie : déclarée, soumise, approuvée ; son dossier de rémunération ─────
  const ctx = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
  const jeton = async (u: string, p: string) => (await (await ctx.post(`${API}/api/auth/login`, { data: { username: u, password: p, tenantId } })).json()).token as string;
  const adm = await pwRequest.newContext({ storageState: { cookies: [], origins: [] }, extraHTTPHeaders: { Authorization: `Bearer ${await jeton(TENANT_ADMIN_USER, TENANT_ADMIN_PASS)}` } });
  const jul = await pwRequest.newContext({ storageState: { cookies: [], origins: [] }, extraHTTPHeaders: { Authorization: `Bearer ${await jeton(identifiant, MDP)}` } });
  let ressourceId = "";
  try {
    // Les types d'heures que la paie exige : régulier, et supplémentaire (majoré de 50 %).
    await poser(adm, tenantId, "/api/effectifs/referentiels/types_heure",
      { effectifDepuis: "2026-01-01", code: "REG", libelleFr: "Régulier", comportement: "TRAVAILLEE", attributs: { codePaie: "100" } });
    await poser(adm, tenantId, "/api/effectifs/referentiels/types_heure",
      { effectifDepuis: "2026-01-01", code: "HS", libelleFr: "Heures supplémentaires", comportement: "SUPPLEMENTAIRE", attributs: { codePaie: "150", majorationPourcent: 50 } });
    await poser(adm, tenantId, "/api/effectifs/temps/feuilles/preparer", { uniteCode: "EST", debut: jour(-6), fin: jour(0) });
    const feuille = ((await (await jul.get(`${API}/api/effectifs/temps/moi`, { headers: { "X-Tenant-Id": tenantId } })).json()) as Array<{ id: string; debut: string }>)
      .find((f) => f.debut === jour(-6))!;
    for (const d of [-6, -5, -4]) {
      await poser(jul, tenantId, `/api/effectifs/temps/feuilles/${feuille.id}/segments`,
        { date: jour(d), debut: `${jour(d)}T12:00:00Z`, fin: `${jour(d)}T20:00:00Z`, typeHeureCode: "REG" });
    }
    await poser(jul, tenantId, `/api/effectifs/temps/feuilles/${feuille.id}/soumettre`, {});
    await poser(adm, tenantId, `/api/effectifs/temps/feuilles/${feuille.id}/approuver`, {});
    ressourceId = ((await (await adm.get(`${API}/api/effectifs/planification/ressources`, { headers: { "X-Tenant-Id": tenantId } })).json()) as Array<{ id: string }>)[0].id;
    await poser(adm, tenantId, `/api/paie/dossiers/${ressourceId}`, { mode: "horaire", frequence: "hebdomadaire", tauxHoraire: "34" }, "put");
  } finally { await Promise.all([ctx.dispose(), adm.dispose(), jul.dispose()]); }

  // ── L'administration prépare le cycle, lit le bulletin, traite la paie ──────────────────
  const ctxAdmin = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] } }));
  const admin = await ctxAdmin.newPage();
  await selectTenant(admin, tenantId);
  await ouvrirNav(admin, "nav-paiecycles");
  await admin.getByTestId("pcy-preparer-ouvrir").click();
  await admin.getByTestId("pcy-debut").fill(jour(-6));
  await admin.getByTestId("pcy-fin").fill(jour(0));
  await admin.getByTestId("pcy-date-paie").fill(jour(4));
  await admin.getByTestId("pcy-creer").click();
  const bulletin = admin.getByTestId(`pcy-bulletin-${ressourceId}`);
  await expect(bulletin).toContainText("Julie Roy");
  await expect(bulletin).toContainText("24");
  await admin.getByTestId(`pcy-bulletin-${ressourceId}-voir`).click();
  await expect(admin.getByTestId("pcy-b-brut")).toContainText("816");
  await expectNoA11yViolations(admin);
  await admin.keyboard.press("Escape");
  await admin.getByTestId("pcy-traiter").click();
  await admin.getByTestId("pcy-confirmer-traiter-confirm").click();
  await expect(admin.getByTestId("pcy-succes")).toBeVisible();
  await ouvrirNav(admin, "nav-paiedeclarations");
  await expect(admin.getByTestId(`pde-remise-${jour(4).slice(0, 7)}`)).toBeVisible();
  await expect(admin.getByTestId(`pde-releve-${ressourceId}`)).toContainText("Julie Roy");
  await expectNoA11yViolations(admin);
  await ctxAdmin.close();

  // ── Julie lit son bulletin ───────────────────────────────────────────────────────────────
  const ctxJulie = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] } }));
  const julie = await ctxJulie.newPage();
  await julie.goto("/");
  await uiLogin(julie, identifiant, MDP, tenantId);
  await ouvrirNav(julie, "nav-mesbulletins");
  const ligne = julie.locator('[data-testid^="mbu-"][data-testid$="-voir"]').first();
  await ligne.click();
  await expect(julie.getByTestId("mbu-b-brut")).toContainText("816");
  await expect(julie.getByTestId("mbu-b-net")).toBeVisible();
  await expectNoA11yViolations(julie);
  await ctxJulie.close();
});
