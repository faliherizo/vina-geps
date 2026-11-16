/**
 * **L'organisme FX-AO-20260810 ne voit que son avis.** Le semis (`scripts/semer-fx-ao.cjs`) masque toute vue du catalogue
 * qui n'est pas nommée dans `donnees/fx-ao/demonstration.json` › `vuesGardees`. Cet essai sème l'organisme, puis vérifie,
 * profil par profil, que **chaque** entrée affichée est une vue gardée — et que les écrans de la grille y sont.
 * Une rubrique gardée en entier laissait passer l'horaire, les règles des effectifs, les intégrations et les codes
 * d'absence : c'est ce que l'essai interdit désormais.
 *
 * **L'aide suit le menu** : le guide ne montre la fiche (et sa vidéo) que d'un écran gardé — l'aide d'une fonctionnalité
 * que l'organisme n'a pas ne lui servirait à rien.
 */
import { execFileSync } from "node:child_process";
import path from "node:path";
import { test, expect, type Page } from "@playwright/test";
import { menuPret } from "./helpers/nav";
import donnees from "../../donnees/fx-ao/demonstration.json";
import { raconter } from "./helpers/recit";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;
const SCRIPT = path.join(__dirname, "..", "..", "..", "scripts", "semer-fx-ao.cjs");
const MDP = "Passe!Demo2026";
const GARDEES = new Set(donnees.vuesGardees.cles);
/** Les pages des vues gardées (`access:octrois` est une vue de la page `access`) : l'aide d'un écran suit sa page. */
const PAGES_GARDEES = new Set(donnees.vuesGardees.cles.map((c) => c.split(":")[0]));

/** Ce que chaque profil doit au moins trouver : les écrans de la grille qui le concernent. */
const ATTENDUS: Record<string, string[]> = {
  admin: ["alnouvelle", "alsuivi", "mesalertes", "alparametres", "admin", "access", "usmessages", "help"],
  "coord.urgence": ["alnouvelle", "alsuivi", "mesalertes", "usdisponibilites"],
  "inf.roy": ["mesalertes", "usmessages", "mesdisponibilites"],
};
const JAMAIS = ["monhoraire", "usregles", "uscodesabsence", "usintegrations", "integrations", "apidocs", "paiecycles", "uscomblement"];

test.use({ storageState: { cookies: [], origins: [] } });

test.describe("FX-AO-20260810 — un menu réduit à l'avis", () => {
  let tenantId = "";

  test.beforeAll(() => {
    const sortie = execFileSync("node", [SCRIPT, "--api", API, "--mdp", MDP], { encoding: "utf-8", timeout: 240_000 });
    tenantId = (sortie.match(/^TENANT=(.+)$/m) ?? [])[1] ?? "";
    expect(tenantId, `le script n'a pas nommé son organisme :\n${sortie}`).toBeTruthy();
  });

  async function menu(page: Page, identifiant: string): Promise<string[]> {
    await page.goto("/");
    await expect(page.getByTestId("login-tenant").locator(`option[value="${tenantId}"]`)).toHaveCount(1, { timeout: 60_000 });
    await page.getByTestId("login-tenant").selectOption(tenantId);
    await page.getByTestId("login-user").fill(identifiant);
    await page.getByTestId("login-pass").fill(MDP);
    await page.getByTestId("login-submit").click();
    await expect(page.getByTestId("dashboard-page")).toBeVisible({ timeout: 60_000 });
    await menuPret(page);
    const ids = await page.locator('nav [data-testid^="nav-"]').evaluateAll((els) => els.map((e) => e.getAttribute("data-testid") ?? ""));
    // Les rubriques et les notions regroupent : seules les entrées sont des vues. `nav-access-octrois` est la vue `access:octrois`.
    return [...new Set(ids.filter((i) => !i.startsWith("nav-group-") && !i.startsWith("nav-concept-"))
      .map((i) => i.slice(4).replace(/^access-/, "access:")))];
  }

  for (const [identifiant, attendus] of Object.entries(ATTENDUS)) {
    test(`${identifiant} : chaque entrée est une vue gardée, et les écrans de la grille y sont`, async ({ page }) => {
      test.setTimeout(identifiant === "admin" ? 360_000 : 120_000);
      const vues = await menu(page, identifiant);
      await raconter(page, `Cet organisme n'a reçu que les alertes cliniques et la communication. ${({ admin: "L'administrateur", "coord.urgence": "Sophie Lalonde, coordonnatrice des mesures d'urgence,", "inf.roy": "Julie Roy, infirmière," } as Record<string, string>)[identifiant] ?? identifiant} se connecte. Chaque entrée de son menu doit être un écran accordé à l'organisme, et ses écrans de travail doivent y être : jamais l'horaire, la paie ni les intégrations.`);
      expect(vues.filter((v) => !GARDEES.has(v)), "entrées hors de l'avis FX-AO").toEqual([]);
      for (const a of attendus) expect(vues, `écran de la grille absent : ${a}`).toContain(a);
      for (const j of JAMAIS) expect(vues).not.toContain(j);

      await raconter(page, "Le guide suit le menu : il ne montre l'aide que des écrans gardés. Pas de section sur la paie, mais bien celle des alertes.");
      await page.goto("/admin/help");
      await expect(page.getByTestId("help-page")).toBeVisible({ timeout: 60_000 });
      const fiches = await page.locator('[data-testid^="help-fiche-"]').evaluateAll((els) =>
        els.map((e) => (e.getAttribute("data-testid") ?? "").slice("help-fiche-".length)));
      expect(fiches.filter((f) => !PAGES_GARDEES.has(f)), "fiches d'aide d'écrans hors de l'avis FX-AO").toEqual([]);
      expect(fiches).toContain("mesalertes");
      await expect(page.getByTestId("help-sommaire-paie")).toHaveCount(0);
      await expect(page.getByTestId("help-sommaire-alertes")).toHaveCount(1);
    });
  }
});
