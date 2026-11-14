/**
 * **Communications cliniques — le socle, de bout en bout, contre le Java.** ADR 0074 ; FX-AO-20260810.
 *
 * L'administration crée une réponse « J'arrive » (prise en charge) et un modèle « Code bleu » ; la
 * planificatrice envoie l'alerte à Julie, choisie par son nom dans son groupe ; Julie la lit et la prend en
 * charge d'un geste ; la planificatrice voit, au suivi, qui a lu et qui a pris en charge. Chaque écran
 * visité passe axe-core.
 */
import { test, expect, type Browser, type Page } from "@playwright/test";
import { superadminContext } from "./helpers/auth";
import { createTenant, setTenantModules, uniq } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";
import { auditA11y } from "./helpers/a11y";
import { optionsFilmees } from "./helpers/film";
import { amorcerOrganisme, identifiantDe, modulesDeLOrganisme, seConnecterProfil, type Profil } from "./helpers/urgences-sante";
import jeu from "../../donnees/urgences-sante/profils.json";

test.use({ storageState: { cookies: [], origins: [] } });

const PROFILS = jeu.profils as Profil[];
const profil = (cle: string) => PROFILS.find((p) => p.cle === cle)!;

async function ouvrir(browser: Browser, tenantId: string, cle: string, nav: string, temoin: string): Promise<Page> {
  const ctx = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] } }));
  const page = await ctx.newPage();
  await seConnecterProfil(page, tenantId, identifiantDe(profil(cle), tenantId));
  await ouvrirNav(page, nav);
  await expect(page.getByTestId(temoin)).toBeVisible({ timeout: 30_000 });
  return page;
}

async function sansViolation(page: Page, etat: string): Promise<void> {
  expect(await auditA11y(page), `axe-core : ${etat}`).toEqual([]);
}

test.describe("FX-AO-20260810 — alertes : paramétrer, envoyer, répondre, suivre", () => {
  let tenantId = "";

  test.beforeAll(async () => {
    const sa = await superadminContext();
    try {
      tenantId = await createTenant(sa, uniq("US-ALR"));
      await setTenantModules(sa, tenantId, [...modulesDeLOrganisme, "alertes"]);
    } finally { await sa.dispose(); }
    await amorcerOrganisme(tenantId, { profils: PROFILS });
  });

  test("l'administration paramètre ; la planificatrice envoie ; Julie prend en charge ; le suivi le dit", async ({ browser }) => {
    test.setTimeout(240_000);
    const julie = profil("paramedic");

    // ── L'administration : une réponse, puis un modèle qui la propose ─────────────────────
    const admin = await ouvrir(browser, tenantId, "administration", "nav-alparametres", "alp-titre");
    await admin.getByTestId("alp-reponse-ouvrir").click();
    await admin.getByTestId("alp-r-libelle").fill("J'arrive");
    await admin.getByTestId("alp-r-semantique").selectOption("prise_en_charge");
    await admin.getByTestId("alp-r-creer").click();
    await expect(admin.getByTestId("alp-succes")).toContainText("Réponse « J'arrive » créée");
    await admin.getByTestId("alp-modele-ouvrir").click();
    await admin.getByTestId("alp-m-titre").fill("Code bleu");
    await admin.getByTestId("alp-m-corps").fill("Chambre 512, 5e étage : répondez à la prise en charge");
    await admin.getByTestId("alp-m-priorite").selectOption("critique");
    await admin.locator('[data-testid^="alp-m-r-"]').first().check();
    await admin.getByTestId("alp-m-creer").click();
    await expect(admin.getByTestId("alp-modeles")).toContainText("Code bleu");
    await sansViolation(admin, "paramètres, réponse et modèle créés");
    await admin.context().close();

    // ── La planificatrice envoie, à Julie choisie par son nom ─────────────────────────────
    const chef = await ouvrir(browser, tenantId, "planificateur", "nav-alnouvelle", "aln-titre");
    await expect(chef.getByTestId("aln-modele").locator("option", { hasText: "Code bleu" })).toHaveCount(1, { timeout: 30_000 });
    await chef.getByTestId("aln-modele").selectOption({ label: "Code bleu" });
    await expect(chef.getByTestId("aln-critique")).toBeVisible();
    await chef.getByTestId("aln-groupe-personnes").selectOption({ index: 1 });
    const caseJulie = chef.getByTestId(`aln-pers-${identifiantDe(julie, tenantId)}`);
    await expect(caseJulie).toBeVisible({ timeout: 30_000 });
    await caseJulie.check();
    await expect(chef.getByTestId("aln-compte")).toContainText(julie.nom);
    await sansViolation(chef, "console d'envoi remplie");
    await chef.getByTestId("aln-envoyer").click();
    await expect(chef.getByTestId("aln-envoyee")).toContainText("envoyée à 1 personne(s)");

    // ── Julie lit et prend en charge ──────────────────────────────────────────────────────
    const page = await ouvrir(browser, tenantId, "paramedic", "nav-mesalertes", "mal-titre");
    const alerte = page.locator('[data-testid^="mal-alerte-"][data-testid$="-resume"]').first();
    await expect(alerte).toContainText("Code bleu");
    await expect(alerte).toContainText("Non lue");
    await alerte.click();
    const arrive = page.locator('[data-testid^="mal-alerte-"][data-testid*="-reponse-"]').first();
    await expect(arrive).toHaveText("J'arrive");
    await sansViolation(page, "alerte dépliée");
    await arrive.click();
    await expect(page.getByTestId("mal-succes")).toContainText("« J'arrive » envoyée");
    await page.context().close();

    // ── Le suivi : lue, répondue, prise en charge par Julie ──────────────────────────────
    await ouvrirNav(chef, "nav-alsuivi");
    const ligne = chef.locator('[data-testid^="als-alerte-"]').filter({ hasText: "Code bleu" }).first();
    await expect(ligne).toContainText("1 / 1");
    await expect(ligne).toContainText(julie.nom);
    await ligne.locator('[data-testid$="-detail"]').click();
    await expect(chef.getByTestId("als-fiche")).toContainText("Répondu : J'arrive");
    await sansViolation(chef, "suivi, détail ouvert");
    await chef.context().close();
  });
});
