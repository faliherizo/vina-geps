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
import { test, expect } from "@playwright/test";
import donnees from "../../donnees/fx-ao/demonstration.json";
import jeu from "../../donnees/fx-ao/exigences.json";
import { commandePresente, poser, type Geste } from "./helpers/gestes";
import { auditA11y } from "./helpers/a11y";
import { DEPLOYE, SEMES, allerA, entreesDuMenu, hoteDe, organismeDemo, seConnecter } from "./helpers/demonstration-deployee";

const ORGANISME = organismeDemo("fx-ao");
const GARDEES = new Set(donnees.vuesGardees.cles);
/** Ce que l'administration doit trouver au menu : les écrans de la grille qu'elle opère (fx-ao-menu.spec.ts), et les ressources. */
const ATTENDUS_ADMIN = ["alnouvelle", "alsuivi", "mesalertes", "alparametres", "admin", "access", "usmessages", "usressources", "help"];
const MODULES = ["alertes", "effectifs", "effectifs_absences", "effectifs_communication", "effectifs_disponibilites", "effectifs_planification", "effectifs_regles"];

interface Critere { id: string; bloc: string; statut: string; page?: string; prealable?: Geste[]; preuve?: string[] }
const CRITERES = (jeu.exigences as Critere[]).filter((c) => c.bloc === "fonctionnel" && c.page);

test.use({ storageState: { cookies: [], origins: [] } });
test.describe.configure({ mode: "serial", timeout: 600_000 });

test.describe("Déploiement — FX-AO-20260810 sur l'organisme montré au client", () => {
  test.skip(!DEPLOYE || !SEMES, "vise une démonstration déployée et semée : poser E2E_BASE_URL et E2E_COMPTES_DEMO=1");

  test("l'administration a exactement les modules et le menu de l'avis", async ({ page }) => {
    const hote = await hoteDe(page.request, ORGANISME);
    await seConnecter(page, hote);
    const modules = (await (await page.request.get(`${hote}/api/saas/my-modules`)).json()).modules as string[];
    expect([...modules].sort(), "modules accordés à l'organisme").toEqual([...MODULES].sort());
    const vues = await entreesDuMenu(page);
    expect(vues.filter((v) => !GARDEES.has(v)), "entrées hors de l'avis FX-AO").toEqual([]);
    for (const a of ATTENDUS_ADMIN) expect(vues, `écran de la grille absent du menu : ${a}`).toContain(a);
  });

  test("chaque écran du menu s'ouvre, sans erreur ni violation axe-core", async ({ page }) => {
    const hote = await hoteDe(page.request, ORGANISME);
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
    const hote = await hoteDe(page.request, ORGANISME);
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
