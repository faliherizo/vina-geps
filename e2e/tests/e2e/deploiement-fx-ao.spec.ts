/**
 * **FX-AO-20260810 sur la démonstration déployée** — l'organisme Clincom, joint par son hôte, tel que l'administration le
 * voit. Vise une pile déployée et semée (`E2E_BASE_URL`, `E2E_COMPTES_DEMO=1`) ; lecture seule.
 *
 * Les essais `fx-ao-menu` et `fx-ao-exigences` sèment un organisme neuf sur une pile d'essai. Celui-ci répond à une
 * autre question : **l'organisme que l'on montre au client** a-t-il le menu de l'avis, et chaque critère fonctionnel
 * a-t-il son écran et ses commandes ? Une vue masquée de trop, un module retiré par le superadmin, un écran qui casse
 * en production : la démonstration le dirait au client avant de le dire à l'équipe.
 *
 * Piloté par `donnees/fx-ao/demonstration.json` (vues gardées) et `donnees/fx-ao/exigences.json` (critères).
 */
import { test, expect, type Page } from "@playwright/test";
import comptesDemo from "../../donnees/geps/comptes-demo.json";
import donnees from "../../donnees/fx-ao/demonstration.json";
import jeu from "../../donnees/fx-ao/exigences.json";
import { menuPret } from "./helpers/nav";
import { commandePresente, poser, type Geste } from "./helpers/gestes";
import { auditA11y } from "./helpers/a11y";

const DEPLOYE = !!process.env.E2E_BASE_URL;
const SEMES = process.env.E2E_COMPTES_DEMO === "1";
const MDP = process.env.E2E_DEMO_PASSWORD || comptesDemo.motDePasseParDefaut;
const ORGANISME = comptesDemo.organismes.find((o) => o.cle === "fx-ao")!;
const GARDEES = new Set(donnees.vuesGardees.cles);
/** Ce que l'administration doit trouver au menu : les écrans de la grille qu'elle opère (fx-ao-menu.spec.ts), et les ressources. */
const ATTENDUS_ADMIN = ["alnouvelle", "alsuivi", "mesalertes", "alparametres", "admin", "access", "usmessages", "usressources", "help"];
/** Laisse le seau de la limitation de débit se vider entre deux écrans (`E2E_PAUSE_MS`). */
const PAUSE_MS = Number(process.env.E2E_PAUSE_MS ?? 2500);
const MODULES = ["alertes", "effectifs", "effectifs_absences", "effectifs_communication", "effectifs_disponibilites", "effectifs_planification", "effectifs_regles"];

interface Critere { id: string; bloc: string; statut: string; page?: string; prealable?: Geste[]; preuve?: string[] }
const CRITERES = (jeu.exigences as Critere[]).filter((c) => c.bloc === "fonctionnel" && c.page);

test.use({ storageState: { cookies: [], origins: [] } });
test.describe.configure({ mode: "serial", timeout: 600_000 });

/** L'hôte de l'organisme : `https://clincom.<racine>` en déploiement par sous-domaine (ADR 0094). */
async function hoteDeLOrganisme(page: Page): Promise<string> {
  const d = await (await page.request.get(`${process.env.E2E_BASE_URL}/api/auth/deploiement`)).json();
  if (d.resolution !== "sous_domaine") return process.env.E2E_BASE_URL!;
  const u = new URL(process.env.E2E_BASE_URL!);
  u.hostname = `${ORGANISME.sousDomaine}.${d.domaineRacine}`;
  return u.origin;
}

async function seConnecter(page: Page, hote: string) {
  await page.goto(`${hote}/admin/login`);
  await expect(page.getByTestId("login-tenant-locked")).toBeVisible({ timeout: 30_000 });
  await page.getByTestId("login-user").fill("admin");
  await page.getByTestId("login-pass").fill(MDP);
  await page.getByTestId("login-submit").click();
  await expect(page.getByTestId("logout")).toBeVisible({ timeout: 60_000 });
  await menuPret(page);
}

/**
 * Ouvrir un écran au rythme d'une personne. Le frontal limite le débit par adresse (20 requêtes/s, rafale de 40 —
 * `frontend/nginx.conf`, MIFI #94) : un écran rechargé en appelle une vingtaine, et enchaîner les rechargements sans
 * pause faisait répondre 503 à la moitié des écrans. L'essai respecte ce contrôle de sécurité au lieu de le contourner.
 */
async function allerA(page: Page, url: string) {
  await page.goto(url);
  await page.waitForLoadState("networkidle", { timeout: 30_000 }).catch(() => {});
  await page.waitForTimeout(PAUSE_MS);
}

/** Les vues du menu : les entrées, sans les rubriques ni les notions (`nav-access-octrois` est la vue `access:octrois`). */
async function entreesDuMenu(page: Page): Promise<string[]> {
  const ids = await page.locator('nav [data-testid^="nav-"]').evaluateAll((els) => els.map((e) => e.getAttribute("data-testid") ?? ""));
  return [...new Set(ids.filter((i) => !i.startsWith("nav-group-") && !i.startsWith("nav-concept-"))
    .map((i) => i.slice(4).replace(/^access-/, "access:")))];
}

test.describe("Déploiement — FX-AO-20260810 sur l'organisme montré au client", () => {
  test.skip(!DEPLOYE || !SEMES, "vise une démonstration déployée et semée : poser E2E_BASE_URL et E2E_COMPTES_DEMO=1");

  test("l'administration a exactement les modules et le menu de l'avis", async ({ page }) => {
    const hote = await hoteDeLOrganisme(page);
    await seConnecter(page, hote);
    const modules = (await (await page.request.get(`${hote}/api/saas/my-modules`)).json()).modules as string[];
    expect([...modules].sort(), "modules accordés à l'organisme").toEqual([...MODULES].sort());
    const vues = await entreesDuMenu(page);
    expect(vues.filter((v) => !GARDEES.has(v)), "entrées hors de l'avis FX-AO").toEqual([]);
    for (const a of ATTENDUS_ADMIN) expect(vues, `écran de la grille absent du menu : ${a}`).toContain(a);
  });

  test("chaque écran du menu s'ouvre, sans erreur ni violation axe-core", async ({ page }) => {
    const hote = await hoteDeLOrganisme(page);
    await seConnecter(page, hote);
    const pages = [...new Set((await entreesDuMenu(page)).map((v) => v.split(":")[0]))];
    const defauts: string[] = [];
    for (const p of pages) {
      await allerA(page, `${hote}/admin/${p}`);
      const rendu = await page.locator("h1").first().waitFor({ timeout: 30_000 }).then(() => true).catch(() => false);
      if (!rendu) defauts.push(`/admin/${p} : aucun titre rendu`);
      if (await page.getByTestId("frontiere-erreur").count()) defauts.push(`/admin/${p} : ${await page.getByTestId("frontiere-erreur-motif").textContent()}`);
      for (const v of await auditA11y(page)) defauts.push(`/admin/${p} — ${v.id} : ${v.targets.join(" | ")}`);
    }
    expect(defauts).toEqual([]);
  });

  test("chaque critère fonctionnel a son écran et ses commandes", async ({ page }) => {
    const hote = await hoteDeLOrganisme(page);
    await seConnecter(page, hote);
    const manquants: string[] = [];
    const refus: string[] = [];
    page.on("response", (r) => { if (r.url().includes("/api/") && r.status() >= 400) refus.push(`${r.status()} ${new URL(r.url()).pathname}`); });
    for (const c of CRITERES) {
      await allerA(page, `${hote}/admin/${c.page}`);
      for (const geste of c.prealable ?? []) await poser(page, geste);
      for (const commande of c.preuve ?? []) {
        if (!(await commandePresente(page, commande))) manquants.push(`critère ${c.id} — /admin/${c.page} : ${commande}`);
      }
    }
    expect(manquants, `réponses de l'API en erreur : ${[...new Set(refus)].join(", ")}`).toEqual([]);
  });
});
