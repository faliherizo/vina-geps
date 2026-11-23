/**
 * **Produire la paie d'un personnel de la santé (ADR 0084), de bout en bout contre le Java**, joué depuis
 * `donnees/paie/production-paie.json`.
 *
 * - **Amorce (API)** : trois personnes déclarent et soumettent leur semaine, et l'administration l'approuve, comme le
 *   ferait un gestionnaire.
 * - **RH, à l'écran** : exige la validation des temps, saisit l'échelle salariale et les primes, puis donne à chacun son
 *   échelon.
 * - **Comptable, à l'écran** : il voit la paie, mais pas la configuration. Il valide les temps (une anomalie « sans
 *   taux » se lit), retourne une feuille avec un motif, prépare le cycle, lit les gains, télécharge le registre et une
 *   fiche en PDF, puis traite.
 * - **La personne payée** : elle télécharge sa fiche de paie.
 *
 * Chaque écran passe axe-core.
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

interface Segment { jour: number; debut: string; fin: string }
interface Personne { cle: string; matricule: string; nom: string; titreEmploi: string; segments: Segment[]; dossier: Record<string, string> }
interface Regle {
  type: string; code: string; libelle: string; valeur: string; titreEmploi?: string; echelon?: string; du: string;
  debut?: string; fin?: string; jours?: number[]; codes?: string;
}
interface Jeu {
  referentiels: Array<{ type: string; corps: Record<string, unknown> }>;
  comptes: Record<string, { role: string }>;
  personnes: Personne[];
  regles: Regle[];
  retour: { cle: string; motif: string };
  attendu: {
    menusRh: string[]; menusComptable: string[]; absentsComptable: string[]; anomalies: Record<string, string[]>; payee: string;
    gains: Record<string, string>; gainsPresents: string[]; sansTaux: string;
  };
}

const JEU = JSON.parse(readFileSync(join(__dirname, "../../donnees/paie/production-paie.json"), "utf8")) as Jeu;

async function poser(ctx: APIRequestContext, tenantId: string, chemin: string, data: unknown, methode: "post" | "patch" | "put" = "post") {
  const r = await ctx[methode](`${API}${chemin}`, { headers: { "X-Tenant-Id": tenantId }, data });
  if (!r.ok()) throw new Error(`amorce ${chemin} : ${r.status()} ${await r.text()}`);
  return r.json();
}

async function contexte(tenantId: string, u: string, p: string): Promise<APIRequestContext> {
  const ctx = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
  const { token } = await (await ctx.post(`${API}/api/auth/login`, { data: { username: u, password: p, tenantId } })).json();
  await ctx.dispose();
  return pwRequest.newContext({ storageState: { cookies: [], origins: [] }, extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
}

/** Les comptes, les ressources et les feuilles approuvées ; rend les identifiants des comptes, des ressources et des feuilles. */
async function amorcer(tenantId: string, suffixe: string) {
  const sa = await superadminContext();
  await setTenantModules(sa, tenantId, ["effectifs_temps", "paie"]);
  await sa.dispose();
  const adm = await contexte(tenantId, TENANT_ADMIN_USER, TENANT_ADMIN_PASS);
  const comptes: Record<string, string> = {};
  const ressources: Record<string, string> = {};
  const feuilles: Record<string, string> = {};
  try {
    for (const r of JEU.referentiels) await poser(adm, tenantId, `/api/effectifs/referentiels/${r.type}`, { effectifDepuis: "2026-01-01", ...r.corps });
    const creerCompte = async (cle: string, role: string) => {
      const u = `${cle}.${suffixe}`.toLowerCase();
      const c = await poser(adm, tenantId, "/api/auth/users", { username: u, password: MDP, role });
      await poser(adm, tenantId, `/api/auth/users/${c.id}`, { mustChangePassword: false, securitySetupRequired: false }, "patch");
      comptes[cle] = u;
      return c.id as string;
    };
    for (const [cle, c] of Object.entries(JEU.comptes)) await creerCompte(cle, c.role);
    for (const p of JEU.personnes) {
      const id = await creerCompte(p.cle, "employee");
      const r = await poser(adm, tenantId, "/api/effectifs/planification/ressources",
        { matricule: p.matricule, nom: p.nom, corpsEmploiCode: "SOINS", titreEmploiCode: p.titreEmploi, uniteCode: "URG", utilisateurId: id });
      ressources[p.cle] = r.id;
    }
    await poser(adm, tenantId, "/api/effectifs/temps/feuilles/preparer", { uniteCode: "URG", debut: jour(-6), fin: jour(0) });
    for (const p of JEU.personnes) {
      const moi = await contexte(tenantId, comptes[p.cle], MDP);
      try {
        const f = ((await (await moi.get(`${API}/api/effectifs/temps/moi`, { headers: { "X-Tenant-Id": tenantId } })).json()) as Array<{ id: string; debut: string }>)
          .find((x) => x.debut === jour(-6))!;
        for (const s of p.segments) {
          await poser(moi, tenantId, `/api/effectifs/temps/feuilles/${f.id}/segments`,
            { date: jour(s.jour), debut: `${jour(s.jour)}T${s.debut}:00Z`, fin: `${jour(s.jour)}T${s.fin}:00Z`, typeHeureCode: "REG" });
        }
        await poser(moi, tenantId, `/api/effectifs/temps/feuilles/${f.id}/soumettre`, {});
        feuilles[p.cle] = f.id;
      } finally { await moi.dispose(); }
      await poser(adm, tenantId, `/api/effectifs/temps/feuilles/${feuilles[p.cle]}/approuver`, {});
    }
  } finally { await adm.dispose(); }
  return { comptes, ressources, feuilles };
}

async function connecter(browser: import("@playwright/test").Browser, tenantId: string, u: string): Promise<Page> {
  const ctx = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] }, acceptDownloads: true }));
  const page = await ctx.newPage();
  await page.goto("/");
  await uiLogin(page, u, MDP, tenantId);
  return page;
}

async function saisirRegle(page: Page, r: Regle): Promise<void> {
  await raconter(page, `Elle ajoute la règle ${r.libelle} : ${({ "taux-de-base": `taux de base de ${r.valeur} dollars de l'heure`, "prime-horaire": `${r.valeur} dollars de plus par heure`, "prime-pourcentage": `${r.valeur} pour cent de plus`, forfait: `${r.valeur} dollars par paie` } as Record<string, string>)[r.type] ?? r.valeur}${r.titreEmploi ? `, pour le titre d'emploi ${r.titreEmploi}` : ""}${r.echelon ? `, échelon ${r.echelon}` : ""}.`);
  await page.getByTestId("prg-nouvelle-ouvrir").click();
  await page.getByTestId("prg-type").selectOption(r.type);
  await page.getByTestId("prg-code").fill(r.code);
  await page.getByTestId("prg-libelle").fill(r.libelle);
  await page.getByTestId("prg-valeur").fill(r.valeur);
  if (r.titreEmploi) await page.getByTestId("prg-titre-emploi").fill(r.titreEmploi);
  if (r.echelon) await page.getByTestId("prg-echelon").fill(r.echelon);
  if (r.debut) await page.getByTestId("prg-debut").fill(r.debut);
  if (r.fin) await page.getByTestId("prg-fin").fill(r.fin);
  for (const j of r.jours ?? []) await page.getByTestId(`prg-jour-${j}`).check();
  if (r.codes) await page.getByTestId("prg-codes").fill(r.codes);
  await page.getByTestId("prg-du").fill(r.du);
  await page.getByTestId("prg-enregistrer").click();
  await expect(page.getByTestId("prg-succes")).toHaveText(`Règle ${r.code} créée.`);
}

test("RH configure, le comptable valide les temps et produit la paie, la personne télécharge sa fiche", async ({ browser }) => {
  test.setTimeout(420_000);
  const sa = await superadminContext();
  const tenantId = await createTenant(sa, uniq("PaieProduction"));
  await sa.dispose();
  const { comptes, ressources, feuilles } = await amorcer(tenantId, uniq("e2e").slice(-6));
  const A = JEU.attendu;

  // ── RH : réglages, règles, dossiers ──────────────────────────────────────────────────
  const rh = await connecter(browser, tenantId, comptes.rh);
  await raconter(rh, "Produire la paie d'un personnel de la santé. La responsable des ressources humaines se connecte : elle a les six écrans de la paie, dont les règles de paie variables.");
  for (const m of A.menusRh) {
    await ouvrirNav(rh, m);
    await expect(rh.locator("main h1")).toBeVisible();
  }
  await raconter(rh, "Dans les paramètres, elle exige la validation des temps avant toute paie : une feuille non validée ne sera pas payée.");
  await ouvrirNav(rh, "nav-paieparametres");
  await rh.getByTestId("ppa-validation").check();
  await rh.getByTestId("ppa-enregistrer").click();
  await expect(rh.getByTestId("ppa-succes")).toBeVisible();
  await raconter(rh, "Elle ouvre les règles de paie variables. L'échelle salariale est vide : elle la construit, puis ajoute les primes.");
  await ouvrirNav(rh, "nav-paieregles");
  await expect(rh.getByTestId("prg-echelle-vide")).toBeVisible();
  await expectNoA11yViolations(rh);
  for (const r of JEU.regles) await saisirRegle(rh, r);
  await expect(rh.locator('[data-testid="prg-echelle"] tbody tr')).toHaveCount(JEU.regles.filter((r) => r.type === "taux-de-base").length);
  await rh.getByTestId("prg-nouvelle-ouvrir").click();
  await expectNoA11yViolations(rh);
  await rh.keyboard.press("Escape");
  await raconter(rh, "Dans les dossiers de rémunération, elle place chaque personne à son échelon : Julie Roy et Léa Côté à l'échelon 3, Marc Dubé à l'échelon 2, qui n'existe pas encore dans l'échelle.");
  await ouvrirNav(rh, "nav-paiedossiers");
  for (const p of JEU.personnes) {
    await rh.getByTestId(`pdo-${ressources[p.cle]}-modifier`).click();
    await rh.getByTestId("pdo-frequence").selectOption(p.dossier.frequence);
    await rh.getByTestId("pdo-echelon").fill(p.dossier.echelon);
    await rh.getByTestId("pdo-enregistrer").click();
    await expect(rh.getByTestId("pdo-succes")).toContainText(p.nom);
    await expect(rh.getByTestId(`pdo-${ressources[p.cle]}`)).toContainText(`échelon ${p.dossier.echelon}`);
  }
  await expectNoA11yViolations(rh);
  await rh.context().close();

  // ── Comptable : ce qu'il voit, valider les temps, produire la paie ──────────────────────
  const cpt = await connecter(browser, tenantId, comptes.comptable);
  await raconter(cpt, "Le comptable se connecte. Il ne voit ni les règles, ni les dossiers, ni les paramètres : la séparation des tâches est respectée. Il ouvre la validation des temps de la semaine.");
  for (const m of A.absentsComptable) await expect(cpt.getByTestId(m)).toHaveCount(0);
  await ouvrirNav(cpt, "nav-paietemps");
  await cpt.getByTestId("pvt-debut").fill(jour(-6));
  await cpt.getByTestId("pvt-fin").fill(jour(0));
  await cpt.getByTestId("pvt-afficher").click();
  for (const [cle, attendues] of Object.entries(A.anomalies)) {
    const ligne = cpt.getByTestId(`pvt-${feuilles[cle]}`);
    await expect(ligne).toBeVisible();
    for (const a of attendues) await expect(ligne.getByTestId(`pvt-anomalie-${a}`)).toBeVisible();
    if (attendues.length === 0) await expect(ligne).toContainText("Aucune");
  }
  await expectNoA11yViolations(cpt);
  await raconter(cpt, "Le contrôle signale une anomalie : Marc Dubé n'a pas de taux, son échelon manque dans l'échelle. Le comptable retourne la feuille de Léa Côté avec son motif : le quart du lundi doit être codé en formation.");
  const retour = JEU.retour;
  await cpt.getByTestId(`pvt-${feuilles[retour.cle]}-retourner`).click();
  await cpt.getByTestId("pvt-motif").fill(retour.motif);
  await expectNoA11yViolations(cpt);
  await cpt.getByTestId("pvt-retour-confirmer").click();
  await expect(cpt.getByTestId("pvt-succes")).toContainText("retournée");
  await expect(cpt.getByTestId(`pvt-${feuilles[retour.cle]}`)).toContainText("Retournée");
  await raconter(cpt, "Il valide d'un geste toutes les feuilles sans anomalie, puis celle de Marc, une à une. Chaque validation est signée de son nom.");
  await cpt.getByTestId("pvt-valider-lot").click();
  await expect(cpt.getByTestId("pvt-succes")).toContainText("sans anomalie validée");
  await cpt.getByTestId(`pvt-${feuilles.marc}-valider`).click();
  await expect(cpt.getByTestId(`pvt-${feuilles.marc}-validation`)).toContainText("Validée par");
  await expect(cpt.getByTestId(`pvt-${feuilles[A.payee]}-validation`)).toContainText("Validée par");

  await raconter(cpt, "Il prépare le cycle de paie. L'écran avertit que Marc Dubé n'a pas de taux : il n'est pas payé tant que la RH n'a pas complété l'échelle. Seule Julie a un bulletin : 816 dollars d'heures régulières, la prime de soins critiques, la prime de soir et l'allocation de rétention.");
  await ouvrirNav(cpt, "nav-paiecycles");
  await cpt.getByTestId("pcy-preparer-ouvrir").click();
  await cpt.getByTestId("pcy-debut").fill(jour(-6));
  await cpt.getByTestId("pcy-fin").fill(jour(0));
  await cpt.getByTestId("pcy-date-paie").fill(jour(4));
  await cpt.getByTestId("pcy-creer").click();
  await expect(cpt.getByTestId("pcy-sans-taux")).toContainText(A.sansTaux);
  const payee = ressources[A.payee];
  await expect(cpt.getByTestId(`pcy-bulletin-${payee}`)).toBeVisible();
  await expect(cpt.locator('[data-testid^="pcy-bulletin-"][data-testid$="-voir"]')).toHaveCount(1);
  await cpt.getByTestId(`pcy-bulletin-${payee}-voir`).click();
  for (const [code, montant] of Object.entries(A.gains)) await expect(cpt.getByTestId(`pcy-b-gain-${code}`)).toContainText(montant);
  for (const code of A.gainsPresents) await expect(cpt.getByTestId(`pcy-b-gain-${code}`)).toBeVisible();
  await expectNoA11yViolations(cpt);
  await cpt.keyboard.press("Escape");
  await raconter(cpt, "Il télécharge le registre de paie en PDF, et la fiche de paie de Julie. Puis il traite le cycle : les relevés sont produits.");
  const [registre] = await Promise.all([cpt.waitForEvent("download"), cpt.getByTestId("pcy-registre").click()]);
  expect(registre.suggestedFilename()).toBe(`registre-paie-${jour(4)}.pdf`);
  const [fiche] = await Promise.all([cpt.waitForEvent("download"), cpt.getByTestId(`pcy-bulletin-${payee}-pdf`).click()]);
  expect(fiche.suggestedFilename()).toMatch(/^fiche-paie-INF-001-.*\.pdf$/);
  await cpt.getByTestId("pcy-traiter").click();
  await cpt.getByTestId("pcy-confirmer-traiter-confirm").click();
  await expect(cpt.getByTestId("pcy-succes")).toContainText("traitée");
  await ouvrirNav(cpt, "nav-paiedeclarations");
  await expect(cpt.getByTestId(`pde-releve-${payee}`)).toBeVisible();
  await expectNoA11yViolations(cpt);
  await cpt.context().close();

  // ── La personne payée télécharge sa fiche ──────────────────────────────────────────────
  const moi = await connecter(browser, tenantId, comptes[A.payee]);
  await raconter(moi, "Julie Roy ouvre Mes bulletins. Elle n'a pas accès à la validation des temps. Elle télécharge sa fiche de paie en PDF, et relit son allocation de rétention.");
  await ouvrirNav(moi, "nav-mesbulletins");
  await expect(moi.getByTestId("nav-paietemps")).toHaveCount(0);
  const pdf = moi.locator('[data-testid^="mbu-"][data-testid$="-pdf"]');
  await expect(pdf).toHaveCount(1);
  const [sienne] = await Promise.all([moi.waitForEvent("download"), pdf.click()]);
  expect(sienne.suggestedFilename()).toMatch(/^fiche-paie-INF-001-.*\.pdf$/);
  await moi.locator('[data-testid^="mbu-"][data-testid$="-voir"]').click();
  await expect(moi.getByTestId("mbu-b-gain-RETENTION")).toContainText(A.gains.RETENTION);
  await expectNoA11yViolations(moi);
  await moi.context().close();
});
