/**
 * Interface — la page publique **« Relier mon compte »** (ADR 0093), dans chacun de ses états, aux quatre largeurs :
 * axe-core sans violation (WCAG 2.2 AA), aucun débordement horizontal, cibles ≥ 24 px, aucune commande recouverte ni
 * collée à une autre (< 4 px), un seul h1, ordre de tabulation qui suit la lecture.
 */
import AxeBuilder from "@axe-core/playwright";
import type { Page } from "@playwright/test";
import { test, expect } from "./fixtures/app";

const ETAT = { indice: "j***@urgences-sante.qc.ca", candidat: true, demandeEnAttente: false, essaisRestants: 5 };
const LARGEURS = [390, 768, 1280, 1920];

/** Débordement, cibles trop petites, commandes recouvertes ou collées : relevés dans la page. */
async function geometrie(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const fautes: string[] = [];
    if (document.documentElement.scrollWidth > innerWidth + 1) fautes.push(`débordement : ${document.documentElement.scrollWidth} > ${innerWidth}`);
    const commandes = [...document.querySelectorAll("main a[href], main button, main input, main select, main textarea")]
      .filter((e) => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; });
    for (const e of commandes) {
      const r = e.getBoundingClientRect();
      const nom = (e as HTMLElement).dataset.testid ?? e.tagName;
      // Un lien dans le fil d'un texte est exempté par 2.5.8 (« en ligne »).
      const enLigne = e.tagName === "A" && getComputedStyle(e).display === "inline";
      if (!enLigne && (r.width < 24 || r.height < 24)) fautes.push(`cible < 24 px : ${nom}`);
      const centre = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
      if (centre && centre !== e && !e.contains(centre) && !(e as HTMLElement).closest("label")?.contains(centre)) fautes.push(`recouvert : ${nom}`);
    }
    for (let i = 0; i < commandes.length; i++) {
      for (let j = i + 1; j < commandes.length; j++) {
        const a = commandes[i].getBoundingClientRect();
        const b = commandes[j].getBoundingClientRect();
        const ecartH = Math.max(b.left - a.right, a.left - b.right);
        const ecartV = Math.max(b.top - a.bottom, a.top - b.bottom);
        if (Math.max(ecartH, ecartV) < 4) fautes.push(`collé à : ${(commandes[i] as HTMLElement).dataset.testid} / ${(commandes[j] as HTMLElement).dataset.testid}`);
      }
    }
    return fautes;
  });
}

async function auditer(page: Page, etat: string) {
  for (const largeur of LARGEURS) {
    await page.setViewportSize({ width: largeur, height: 900 });
    const axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"]).analyze();
    expect(axe.violations.map((v) => `${v.id} (${v.nodes.length})`), `${etat} à ${largeur} px : axe-core`).toEqual([]);
    expect(await geometrie(page), `${etat} à ${largeur} px : géométrie`).toEqual([]);
    await expect(page.locator("h1")).toHaveCount(1);
  }
}

test("les trois preuves proposées : états initial, refus annoncé, demande transmise", async ({ page, mock }) => {
  await mock({
    "GET /api/auth/sso/liaison": ETAT,
    "POST /api/auth/sso/liaison/mot-de-passe": () => ({ status: 403, body: { errors: ["la preuve ne permet pas de relier ce compte"] } }),
    "POST /api/auth/sso/liaison/demande": () => ({ status: 202, body: { demandeId: "d1", message: "Votre demande est transmise à l'administration de votre organisme." } }),
  });
  await page.goto("/connexion/relier");
  await expect(page.getByTestId("relier-indice")).toContainText("j***@urgences-sante.qc.ca");
  await auditer(page, "initial");

  // Le clavier suit l'ordre de lecture : identifiant, mot de passe, bouton, invitation, bouton, demande.
  await page.getByTestId("relier-identifiant").focus();
  const ordre: string[] = [];
  for (let i = 0; i < 5; i++) {
    await page.keyboard.press("Tab");
    ordre.push(await page.evaluate(() => (document.activeElement as HTMLElement).dataset.testid ?? ""));
  }
  expect(ordre).toEqual(["relier-mdp", "relier-mdp-envoyer", "relier-invitation", "relier-invitation-envoyer", "relier-demande"]);

  await page.getByTestId("relier-mdp").fill("mauvais");
  await page.getByTestId("relier-mdp-envoyer").click();
  await expect(page.getByTestId("relier-erreur")).toHaveAttribute("role", "alert");
  await auditer(page, "refus");

  await page.getByTestId("relier-demande").click();
  await expect(page.getByTestId("relier-demande-envoyee")).toContainText("transmise");
  await auditer(page, "demande transmise");
});

test("sans compte au courriel, puis sans liaison en cours : le message est clair, la page conforme", async ({ page, mock }) => {
  await mock({ "GET /api/auth/sso/liaison": { ...ETAT, candidat: false } });
  await page.goto("/connexion/relier");
  await expect(page.getByTestId("relier-sans-compte")).toContainText("aucun n'a été créé");
  await expect(page.getByTestId("relier-demande")).toBeDisabled();
  await auditer(page, "sans compte");

  await mock({ "GET /api/auth/sso/liaison": () => ({ status: 404, body: { errors: ["aucune liaison en cours, ou elle a expiré : reconnectez-vous par votre annuaire"] } }) });
  await page.goto("/connexion/relier");
  await expect(page.getByTestId("relier-expiree")).toContainText("aucune liaison en cours");
  await auditer(page, "expirée");
});
