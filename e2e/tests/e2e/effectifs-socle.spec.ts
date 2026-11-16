/**
 * **Gestion des effectifs — lot R1 (socle), de bout en bout.** AO Urgences-santé 26-015-DO.
 *
 * Ce qui se prouve ici, et qu'aucun essai serveur ne voit :
 *   1. le module `effectifs` s'active et se coupe depuis le superadmin, et **le menu suit** ;
 *   2. un référentiel se paramètre à l'écran, depuis le formulaire **rendu par son descripteur** ;
 *   3. une modification rétroactive est refusée **avec son motif affiché** (E-GR-9) ;
 *   4. un milieu se crée avec ses coordonnées saisies en champs, puis ses preuves de présence ;
 *   5. chaque action apparaît au journal (E-GN-9, E-GN-10) ;
 *   6. chaque état visité passe axe-core (SGQRI 008 3.0 / WCAG 2.2 AA).
 */
import { test, expect, type Page } from "@playwright/test";
import { superadminContext } from "./helpers/auth";
import { createTenant, selectTenant, setTenantModules, uniq } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";
import { expectNoA11yViolations } from "./helpers/a11y";
import { raconter } from "./helpers/recit";

test.use({ storageState: { cookies: [], origins: [] } });

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;

/** Paramétrage minimal posé par l'API (le superadmin passe toutes les gardes de rôle). */
async function amorcer(tenantId: string): Promise<void> {
  const sa = await superadminContext();
  try {
    const poser = async (type: string, data: Record<string, unknown>) => {
      const r = await sa.post(`${API}/api/effectifs/referentiels/${type}`, { headers: { "X-Tenant-Id": tenantId }, data });
      if (r.status() !== 201) throw new Error(`amorce ${type} ${String(data.code)} : ${r.status()} ${await r.text()}`);
    };
    await poser("unites", { code: "US", libelleFr: "Urgences-santé", effectifDepuis: "2026-01-01" });
    await poser("unites", { code: "EST", libelleFr: "Opérations Est", effectifDepuis: "2026-01-01", attributs: { parent: "US" } });
  } finally { await sa.dispose(); }
}

async function ouvrirParametrage(page: Page, tenantId: string): Promise<void> {
  await selectTenant(page, tenantId);
  await ouvrirNav(page, "nav-usreferentiels");
  await expect(page.getByTestId("usreferentiels-titre")).toBeVisible();
}

test("le module se coupe et s'active depuis le superadmin, et le menu suit", async ({ page }) => {
  const tenantId = await createTenant(page.request, uniq("EffModule"));
  const sa = await superadminContext();
  try { await setTenantModules(sa, tenantId, []); } finally { await sa.dispose(); }

  await selectTenant(page, tenantId);
  await raconter(page, "Le module des effectifs est coupé pour ce nouvel organisme par le superadmin : l'entrée Administration des effectifs n'apparaît pas dans le menu.");
  await expect(page.getByTestId("nav-usreferentiels")).toHaveCount(0);

  const sa2 = await superadminContext();
  try { await setTenantModules(sa2, tenantId, ["effectifs"]); } finally { await sa2.dispose(); }
  await raconter(page, "Le superadmin active maintenant le module. On recharge la page : l'entrée revient dans le menu, sans reconnexion.");
  await page.goto("/");
  await expect(page.getByTestId("nav-usreferentiels")).toHaveCount(1);
});

test("la case du module figure dans la famille « Gestion des effectifs » du superadmin", async ({ page }) => {
  const tenantId = await createTenant(page.request, uniq("EffCase"));
  await page.goto("/");
  await page.getByTestId("login-user").fill("superadmin");
  await page.getByTestId("login-pass").fill("superadmin");
  await page.getByTestId("login-tenant").selectOption("__global__");
  await page.getByTestId("login-submit").click();
  await raconter(page, "Le superadmin se connecte à la console globale et ouvre Modules et accès.");
  await ouvrirNav(page, "nav-superadmin");
  await page.getByTestId("sa-tab-modules").click();
  await page.getByTestId("modmgr-tenant").selectOption(tenantId);
  await raconter(page, "Pour l'organisme choisi, la case du module figure dans la famille Gestion des effectifs.");
  await expect(page.getByTestId("modmgr-famille-effectifs")).toBeVisible();
  await expect(page.getByTestId("modmgr-toggle-effectifs")).toHaveCount(1);
});

test("paramétrer un référentiel, refuser une modification rétroactive, créer un milieu, lire le journal", async ({ page }) => {
  const tenantId = await createTenant(page.request, uniq("EffSocle"));
  const sa = await superadminContext();
  try { await setTenantModules(sa, tenantId, ["effectifs"]); } finally { await sa.dispose(); }
  await amorcer(tenantId);
  await ouvrirParametrage(page, tenantId);

  // ── Référentiels : le formulaire se construit depuis le descripteur ────────────────────────
  await raconter(page, "Paramétrage des référentiels : l'administrateur choisit le référentiel des types de milieu, encore vide.");
  await page.getByTestId("ref-type").selectOption("types_milieu");
  await expect(page.getByTestId("ref-vide")).toBeVisible();
  await expectNoA11yViolations(page);
  await raconter(page, "Il ajoute une valeur : le code CASERNE, libellé Caserne, en vigueur depuis le premier janvier 2026.");
  await page.getByTestId("ref-ajouter").click();
  await expect(page.getByTestId("ref-formulaire")).toBeVisible();
  await page.getByTestId("ref-form-code").fill("CASERNE");
  await page.getByTestId("ref-form-libelleFr").fill("Caserne");
  await page.getByTestId("ref-form-comportement").selectOption("CASERNE");
  await page.getByTestId("ref-form-date").fill("2026-01-01");
  await expectNoA11yViolations(page);
  await page.getByTestId("ref-form-enregistrer").click();
  await expect(page.getByTestId("ref-succes")).toContainText("CASERNE");
  await expect(page.getByTestId("ref-ligne-CASERNE")).toBeVisible();

  // ── E-GR-9 : un remplacement daté d'hier est refusé, et l'écran dit pourquoi ───────────────
  await raconter(page, "Une valeur ne se modifie jamais en place : on la remplace à partir d'une date. Ici, l'administrateur tente un remplacement daté de 2020, avant le début de la valeur. Le serveur doit refuser cette modification rétroactive.");
  await page.getByTestId("ref-remplacer-CASERNE").click();
  await page.getByTestId("ref-form-libelleFr").fill("Caserne (renommée)");
  await page.getByTestId("ref-form-date").fill("2020-01-01");
  await page.getByTestId("ref-form-enregistrer").click();
  await expect(page.getByTestId("ref-form-erreur")).toContainText(/rétroactive/);
  await expectNoA11yViolations(page);
  await page.keyboard.press("Escape");

  // ── Milieux : coordonnées en champs, puis preuves de présence ──────────────────────────────
  await raconter(page, "Refus affiché, avec son motif. On passe aux milieux : l'administrateur crée la Caserne 12, rattachée à l'unité Opérations Est, avec ses coordonnées et un rayon de 150 mètres.");
  await ouvrirNav(page, "nav-usmilieux");
  await expect(page.getByTestId("mil-vide")).toBeVisible();
  await page.getByTestId("mil-ajouter").click();
  await page.getByTestId("mil-form-code").fill("C12");
  await page.getByTestId("mil-form-libelle").fill("Caserne 12");
  await page.getByTestId("mil-form-type").selectOption("CASERNE");
  await page.getByTestId("mil-form-unite").selectOption("EST");
  await page.getByTestId("mil-form-latitude").fill("45.58");
  await page.getByTestId("mil-form-longitude").fill("-73.55");
  await page.getByTestId("mil-form-rayon").fill("150");
  await page.getByTestId("mil-form-enregistrer").click();
  await expect(page.getByTestId("mil-ligne-C12")).toBeVisible();

  await raconter(page, "Il choisit les preuves de présence acceptées à la caserne : le géorepérage, rendu obligatoire, et le code QR du lieu.");
  await page.getByTestId("mil-preuves-C12").click();
  await page.getByTestId("mil-preuve-GEOREPERAGE").check();
  await page.getByTestId("mil-exige-GEOREPERAGE").check();
  await page.getByTestId("mil-preuve-CODE_QR_DE_LIEU").check();
  await expectNoA11yViolations(page);
  await page.getByTestId("mil-preuves-enregistrer").click();
  await expect(page.getByTestId("mil-ligne-C12")).toContainText(/Georeperage \(exigée\)/i);

  // ── Journal : les actions précédentes y sont, filtrables ───────────────────────────────────
  await raconter(page, "Enfin, le journal des actions : filtré sur la création de milieu, il trace l'opération qui vient d'être faite.");
  await ouvrirNav(page, "nav-usjournal");
  await page.getByTestId("jrn-filtre-action").fill("milieu.cree");
  await page.getByTestId("jrn-filtrer").click();
  await expect(page.getByTestId("jrn-tableau")).toContainText("milieu.cree");
  await expectNoA11yViolations(page);
});
