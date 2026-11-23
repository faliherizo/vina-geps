/**
 * **Communications cliniques — le socle, de bout en bout, contre le Java.** ADR 0074 ; FX-AO-20260810.
 *
 * L'administration crée une réponse « J'arrive » (prise en charge), un modèle « Code bleu » et un groupe
 * transverse « Garde de nuit » où elle inscrit Julie par son nom (ST-G09). La planificatrice, gestionnaire,
 * voit la priorité critique refusée (ST-G01) et envoie l'alerte au groupe de garde ; Julie la lit et la prend en
 * charge d'un geste ; la planificatrice voit, au suivi filtré, qui a lu et qui a pris en charge. Chaque écran
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
import { raconter } from "./helpers/recit";

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
  // Le second test emprunte la réponse, le modèle et la garde que le premier a créés.
  test.describe.configure({ mode: "serial" });
  let tenantId = "";

  test.beforeAll(async () => {
    const sa = await superadminContext();
    try {
      tenantId = await createTenant(sa, uniq("Demo-Alertes"));
      await setTenantModules(sa, tenantId, [...modulesDeLOrganisme, "alertes"]);
    } finally { await sa.dispose(); }
    await amorcerOrganisme(tenantId, { profils: PROFILS });
  });

  test("l'administration paramètre ; la planificatrice envoie ; Julie prend en charge ; le suivi le dit", async ({ browser }) => {
    test.setTimeout(240_000);
    const julie = profil("paramedic");

    // ── L'administration : une réponse, puis un modèle qui la propose ─────────────────────
    const admin = await ouvrir(browser, tenantId, "administration", "nav-alparametres", "alp-titre");
    await raconter(admin, "Communications cliniques. L'administration paramètre les alertes. Elle crée une réponse, J'arrive, qui vaut prise en charge.");
    await admin.getByTestId("alp-reponse-ouvrir").click();
    await admin.getByTestId("alp-r-libelle").fill("J'arrive");
    await admin.getByTestId("alp-r-semantique").selectOption("prise_en_charge");
    await admin.getByTestId("alp-r-creer").click();
    await expect(admin.getByTestId("alp-succes")).toContainText("Réponse « J'arrive » créée");
    await raconter(admin, "Puis un modèle d'alerte, Code bleu : chambre 512, cinquième étage, priorité urgente, avec la réponse J'arrive.");
    await admin.getByTestId("alp-modele-ouvrir").click();
    await admin.getByTestId("alp-m-titre").fill("Code bleu");
    await admin.getByTestId("alp-m-corps").fill("Chambre 512, 5e étage : répondez à la prise en charge");
    await admin.getByTestId("alp-m-priorite").selectOption("urgente");
    await admin.locator('[data-testid^="alp-m-r-"]').first().check();
    await admin.getByTestId("alp-m-creer").click();
    await expect(admin.getByTestId("alp-modeles")).toContainText("Code bleu");
    await sansViolation(admin, "paramètres, réponse et modèle créés");
    // ST-G09 : une garde transverse, Julie inscrite par son nom.
    await raconter(admin, "Elle crée le groupe Garde de nuit, et y inscrit Julie Roy en cherchant son nom.");
    await admin.getByTestId("alp-gt-nouveau-ouvrir").click();
    await admin.getByTestId("alp-gt-nom").fill("Garde de nuit");
    await admin.getByTestId("alp-gt-nature").selectOption("garde");
    await admin.getByTestId("alp-gt-creer").click();
    await expect(admin.getByTestId("alp-gt-info")).toContainText("Groupe « Garde de nuit » créé.");
    await admin.locator('[data-testid^="alp-gt-gt-"][data-testid$="-gerer"]').first().click();
    await admin.getByTestId("alp-gt-personne").fill(julie.nom.slice(0, 4));
    await admin.locator(`[data-testid="alp-gt-personne-option-${identifiantDe(julie, tenantId)}"]`).click();
    await admin.getByTestId("alp-gt-inscrire").click();
    await expect(admin.locator(`[data-testid="alp-gt-membre-${identifiantDe(julie, tenantId)}"]`)).toContainText(julie.nom);
    await sansViolation(admin, "groupe transverse, Julie inscrite");
    await admin.context().close();

    // ── La planificatrice envoie, à Julie choisie par son nom ─────────────────────────────
    const chef = await ouvrir(browser, tenantId, "planificateur", "nav-alnouvelle", "aln-titre");
    await raconter(chef, "La planificatrice ouvre Nouvelle alerte et choisit le modèle Code bleu. La priorité critique ne lui est pas permise : l'écran le dit. Elle vise tout le groupe Garde de nuit et envoie l'alerte.");
    await expect(chef.getByTestId("aln-modele").locator("option", { hasText: "Code bleu" })).toHaveCount(1, { timeout: 30_000 });
    await chef.getByTestId("aln-modele").selectOption({ label: "Code bleu" });
    // ST-G01 : la priorité critique est réservée ; la gestionnaire la voit, inactive, avec la raison.
    await expect(chef.getByTestId("aln-p-critique")).toBeDisabled();
    await expect(chef.getByTestId("aln-critique-refusee")).toBeVisible();
    await chef.getByLabel("Tout le groupe Garde de nuit").check();
    await expect(chef.getByTestId("aln-compte")).toContainText("1 groupe(s)");
    await sansViolation(chef, "console d'envoi remplie");
    await chef.getByTestId("aln-envoyer").click();
    await expect(chef.getByTestId("aln-envoyee")).toContainText("envoyée à 1 personne(s)");

    // ── Julie lit et prend en charge ──────────────────────────────────────────────────────
    const page = await ouvrir(browser, tenantId, "paramedic", "nav-mesalertes", "mal-titre");
    await raconter(page, "Julie Roy ouvre Mes alertes : le Code bleu est là, non lu. Elle le déplie et répond J'arrive.");
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

    // ── Le suivi : filtré sur les prises en charge ; lue, répondue, prise en charge par Julie ──
    await raconter(chef, "Au suivi des alertes, filtré sur les prises en charge, la planificatrice voit le Code bleu pris en charge, une personne sur une, par Julie, avec sa réponse.");
    await ouvrirNav(chef, "nav-alsuivi");
    await chef.getByTestId("als-etat").selectOption("prise_en_charge");
    await chef.getByTestId("als-filtrer").click();
    const ligne = chef.locator('[data-testid^="als-alerte-"]').filter({ hasText: "Code bleu" }).first();
    await expect(ligne).toContainText("1 / 1");
    await expect(ligne).toContainText(julie.nom);
    await ligne.locator('[data-testid$="-detail"]').click();
    await expect(chef.getByTestId("als-fiche")).toContainText("Répondu : J'arrive");
    await sansViolation(chef, "suivi, détail ouvert");
    await chef.context().close();
  });

  test("ST-G04 : une politique de relance arme la cascade ; la reprise la fait partir par l'ordonnanceur réel", async ({ browser }) => {
    test.setTimeout(240_000);
    const admin = await ouvrir(browser, tenantId, "administration", "nav-alparametres", "alp-titre");
    await raconter(admin, "Deuxième cas, les relances. L'administration crée une politique de relance, Garde, relance, dont la première étape part après trente secondes.");
    await admin.getByTestId("alp-politique-ouvrir").click();
    await admin.getByTestId("alp-pol-nom").fill("Garde — relance");
    await admin.getByTestId("alp-pol-e0-delai").fill("30");
    await sansViolation(admin, "dialogue de politique ouvert");
    await admin.getByTestId("alp-pol-enregistrer").click();
    await expect(admin.getByTestId("alp-pol-succes")).toContainText("Politique « Garde — relance » créée : version 1.");
    await admin.context().close();

    const chef = await ouvrir(browser, tenantId, "planificateur", "nav-alnouvelle", "aln-titre");
    await raconter(chef, "La planificatrice envoie une alerte de garde avec cette politique : la cascade de relances est armée dès l'envoi.");
    await expect(chef.getByTestId("aln-politique").locator("option", { hasText: "Garde — relance" })).toHaveCount(1, { timeout: 30_000 });
    await chef.getByTestId("aln-modele").selectOption({ label: "Code bleu" });
    await chef.getByTestId("aln-titre-champ").fill("Relance de garde");
    await chef.getByLabel("Tout le groupe Garde de nuit").check();
    await chef.getByTestId("aln-politique").selectOption({ label: "Garde — relance" });
    await chef.getByTestId("aln-envoyer").click();
    await expect(chef.getByTestId("aln-envoyee")).toContainText("envoyée à 1 personne(s)");

    await ouvrirNav(chef, "nav-alsuivi");
    const ligne = chef.locator('[data-testid^="als-alerte-"]').filter({ hasText: "Relance de garde" }).first();
    const detail = ligne.locator('[data-testid$="-detail"]');
    await detail.click();
    await expect(chef.getByTestId("als-cascade-etat")).toContainText("En cours");
    await expect(chef.getByTestId("als-cascade-trace")).toContainText("Armée à l'envoi");
    await sansViolation(chef, "suivi, cascade armée");
    // La reprise met l'étape à maintenant ; l'ordonnanceur de la campagne (tic de 3 s) l'exécute.
    await raconter(chef, "Elle reprend la cascade à la main, et la reprise est tracée. L'ordonnanceur exécute l'étape, et toutes les étapes sont parties.");
    await chef.getByTestId("als-cascade-reprendre").click();
    await expect(chef.getByTestId("als-cascade-succes")).toContainText("Cascade reprise à l'étape 1");
    await expect.poll(async () => {
      await detail.click();
      await detail.click();
      return (await chef.getByTestId("als-cascade-trace").textContent()) ?? "";
    }, { timeout: 60_000, intervals: [3_000] }).toContain("Étape exécutée");
    await expect(chef.getByTestId("als-cascade-etat")).toContainText("Toutes les étapes sont parties");
    await chef.context().close();
  });
});
