/**
 * **FX-AO-20260810, critères 20 et 23 — la prise en charge d'une alerte depuis le navigateur d'un téléphone.**
 *
 * Aucune application à installer, aucun appareil fourni : l'infirmière ouvre l'adresse de l'organisme dans le navigateur
 * de son téléphone, voit l'alerte qui la vise, et la prend en charge en deux gestes (l'ouvrir, toucher « J'arrive »).
 * L'expéditrice le lit aussitôt dans son suivi. Mesuré à 390 et 320 px : aucun défilement horizontal (WCAG 1.4.10), des
 * réponses de 44 px au moins (une population au doigt, souvent gantée), axe-core sans violation, et un délai borné.
 *
 * Données : `e2e/donnees/fx-ao/mobile.json` (acteurs, alerte, appareils, seuils) ; organisme semé par `scripts/semer-fx-ao.cjs`.
 */
import { execFileSync } from "node:child_process";
import path from "node:path";
import { test, expect, request, type APIRequestContext } from "@playwright/test";
import { auditA11y } from "./helpers/a11y";
import donnees from "../../donnees/fx-ao/mobile.json";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;
const SCRIPT = path.join(__dirname, "..", "..", "..", "scripts", "semer-fx-ao.cjs");
const MDP = "Passe!Demo2026";

test.use({ storageState: { cookies: [], origins: [] } });

async function session(tenantId: string, username: string): Promise<APIRequestContext> {
  const ctx = await request.newContext({ baseURL: API });
  const r = await ctx.post("/api/auth/login", { data: { username, password: MDP, tenantId } });
  expect(r.ok(), `connexion de ${username}`).toBeTruthy();
  const { token } = await r.json();
  await ctx.dispose();
  return request.newContext({ baseURL: API, extraHTTPHeaders: { Authorization: `Bearer ${token}`, "X-Tenant-Id": tenantId } });
}

test.describe("FX-AO-20260810 — une alerte prise en charge depuis un téléphone (critères 20, 23)", () => {
  let tenantId = "";

  test.beforeAll(() => {
    const sortie = execFileSync("node", [SCRIPT, "--api", API, "--mdp", MDP], { encoding: "utf-8", timeout: 240_000 });
    tenantId = (sortie.match(/^TENANT=(.+)$/m) ?? [])[1] ?? "";
    expect(tenantId, `le script n'a pas nommé son organisme :\n${sortie}`).toBeTruthy();
  });

  for (const appareil of donnees.appareils) {
    test(`${appareil.nom} (${appareil.largeur} px) : ouvrir, prendre en charge, et l'expéditrice le voit`, async ({ browser }) => {
      test.setTimeout(180_000);
      const expeditrice = await session(tenantId, donnees.expediteur);
      const reponses: { id: string; semantique: string }[] = await (await expeditrice.get("/api/alertes/reponses")).json();
      const priseEnCharge = reponses.find((r) => r.semantique === donnees.alerte.semantiqueAttendue);
      expect(priseEnCharge, "une réponse « prise en charge » est configurée").toBeTruthy();
      const envoi = await expeditrice.post("/api/alertes", { data: {
        titre: `${donnees.alerte.titre} (${appareil.largeur})`, corps: donnees.alerte.corps, priorite: donnees.alerte.priorite,
        personnes: [donnees.destinataire], reponseIds: reponses.map((r) => r.id) } });
      expect(envoi.status(), await envoi.text()).toBe(201);
      const alerteId: string = (await envoi.json()).id;

      const contexte = await browser.newContext({ viewport: { width: appareil.largeur, height: appareil.hauteur }, isMobile: true, hasTouch: true, locale: "fr-CA" });
      const page = await contexte.newPage();
      await page.goto("/admin/login");
      await expect(page.getByTestId("login-tenant").locator(`option[value="${tenantId}"]`)).toHaveCount(1, { timeout: 60_000 });
      await page.getByTestId("login-tenant").selectOption(tenantId);
      await page.getByTestId("login-user").fill(donnees.destinataire);
      await page.getByTestId("login-pass").fill(MDP);
      await page.getByTestId("login-submit").click();
      await expect(page.getByTestId("dashboard-page")).toBeVisible({ timeout: 60_000 });

      await page.goto("/admin/mesalertes");
      const carte = page.getByTestId(`mal-alerte-${alerteId}`);
      await expect(carte).toBeVisible({ timeout: 30_000 });
      const debordement = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(debordement, "aucun défilement horizontal").toBeLessThanOrEqual(0);

      // Les gestes de la personne, comptés depuis l'écran affiché : ouvrir l'alerte, puis toucher la réponse.
      const debut = Date.now();
      let gestes = 0;
      await page.getByTestId(`mal-alerte-${alerteId}-resume`).tap(); gestes++;
      const bouton = page.getByTestId(`mal-alerte-${alerteId}-reponse-${priseEnCharge!.id}`);
      await expect(bouton).toBeVisible();
      const boite = await bouton.boundingBox();
      expect(boite!.height, "réponse touchable d'un doigt").toBeGreaterThanOrEqual(donnees.seuils.cibleMinPx);
      expect(boite!.x + boite!.width, "réponse entièrement à l'écran").toBeLessThanOrEqual(appareil.largeur);
      await bouton.tap(); gestes++;
      await expect(page.getByTestId(`mal-alerte-${alerteId}-repondue`)).toBeVisible({ timeout: 15_000 });
      expect(gestes).toBeLessThanOrEqual(donnees.seuils.gestesMax);
      expect(Date.now() - debut, "prise en charge rapide").toBeLessThan(donnees.seuils.delaiMaxMs);
      expect((await auditA11y(page)).map((v) => v.id), "axe-core, état « répondue »").toEqual([]);

      // L'expéditrice le lit dans son suivi : la prise en charge vient du téléphone.
      const suivi = await (await expeditrice.get(`/api/alertes/envoyees/${alerteId}`)).json();
      expect(JSON.stringify(suivi)).toContain(priseEnCharge!.id);
      await expeditrice.dispose();
      await contexte.close();
    });
  }
});
