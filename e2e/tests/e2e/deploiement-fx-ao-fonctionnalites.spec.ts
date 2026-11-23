/**
 * **FX-AO-20260810, fonctionnalité par fonctionnalité, sur la démonstration déployée** — groupes et garde, disponibilité,
 * cascades, suivi, réception, notes, profils : jugés sur des valeurs, par l'API et à l'écran, compte par compte.
 *
 * `deploiement-fx-ao.spec.ts` constate que chaque critère a son écran et ses commandes. Celui-ci répond à la question
 * suivante : **la fonctionnalité est-elle là, avec ses données, pour le profil qui s'en sert ?** — la garde de
 * cardiologie et sa modératrice, le congé du Dr Lavoie que la cascade respecte, la prise en charge nommée au suivi,
 * le menu de l'expéditeur. Un organisme semé à moitié (constaté le 23 novembre 2026 : ni modèles, ni réponses, ni
 * cascade) passait l'essai de présence ; il échoue ici, en nommant ce qui manque.
 *
 * Toute la connaissance est dans `donnees/fx-ao/fonctionnalites.json` ; lecture seule — aucune requête n'écrit, et la
 * démonstration reste telle qu'on la montre. Vise une pile déployée et semée (`E2E_BASE_URL`, `E2E_COMPTES_DEMO=1`).
 */
import { test, expect, type APIRequestContext, type Page } from "@playwright/test";
import jeu from "../../donnees/fx-ao/fonctionnalites.json";
import { rendre } from "./helpers/comportements";
import { commandePresente, poser, type Geste } from "./helpers/gestes";
import { DEPLOYE, SEMES, allerA, apiDe, entreesDuMenu, hoteDe, organismeDemo, seConnecter } from "./helpers/demonstration-deployee";

interface Lecture {
  acteur: string; chemin: string; statut?: number; pourquoi?: string;
  corps?: unknown; contient?: unknown[]; longueur?: number; texte?: string; absent?: string;
  capturer?: Record<string, { element: unknown; champ: string }>;
}
interface Ecran { acteur: string; page: string; largeur?: number; preuve?: string[]; textes?: string[]; gestes?: Geste[]; pourquoi?: string }
interface Capacite { id: string; titre: string; exigences: string[]; criteres: string[]; api: Lecture[]; ecrans: Ecran[] }
interface Profil { compte: string; profil: string; droits: Record<string, boolean>; menu: { present: string[]; absent: string[] } }

const ORGANISME = organismeDemo("fx-ao");
const PROFILS = jeu.profils as Profil[];
const CAPACITES = jeu.capacites as Capacite[];
/** Une lecture à la fois, espacées : la limitation de débit vaut aussi pour l'API. */
const PAUSE_API_MS = 300;

/**
 * **Correspondre**, et non être égal : un objet attendu est un sous-ensemble du reçu ; chaque élément d'un tableau
 * attendu se retrouve dans le tableau reçu, dans n'importe quel ordre. La démonstration est vivante — on y lit, on y
 * répond pendant qu'on la montre — : l'essai juge ce que le semis a posé, pas l'état exact d'une liste.
 */
function ecarts(attendu: unknown, recu: unknown, chemin = "$"): string[] {
  if (Array.isArray(attendu)) {
    if (!Array.isArray(recu)) return [`${chemin} : une liste était attendue, reçu ${JSON.stringify(recu)?.slice(0, 200)}`];
    return attendu.flatMap((a) => recu.some((r) => ecarts(a, r).length === 0) ? []
      : [`${chemin} : aucun élément ne correspond à ${JSON.stringify(a)}`]);
  }
  if (attendu && typeof attendu === "object") {
    if (!recu || typeof recu !== "object") return [`${chemin} : un objet était attendu, reçu ${JSON.stringify(recu)?.slice(0, 200)}`];
    return Object.entries(attendu).flatMap(([k, a]) => ecarts(a, (recu as Record<string, unknown>)[k], `${chemin}.${k}`));
  }
  return Object.is(attendu, recu) ? [] : [`${chemin} : attendu ${JSON.stringify(attendu)}, reçu ${JSON.stringify(recu)}`];
}

/** Les contextes d'API des comptes, ouverts une fois : une connexion par compte, pas une par lecture. */
const contextes = new Map<string, APIRequestContext>();
async function api(hote: string, compte: string) {
  if (!contextes.has(compte)) contextes.set(compte, await apiDe(hote, compte));
  return contextes.get(compte)!;
}

async function lire(hote: string, l: Lecture, vars: Record<string, unknown>): Promise<string[]> {
  const chemin = String(rendre(l.chemin, vars));
  const ou = `${l.acteur} GET ${chemin}`;
  await new Promise((ok) => setTimeout(ok, PAUSE_API_MS));
  const r = await (await api(hote, l.acteur)).get(`${hote}${chemin}`);
  const texte = await r.text();
  const attendu = l.statut ?? 200;
  if (r.status() !== attendu) return [`${ou} : statut ${r.status()} au lieu de ${attendu}${l.pourquoi ? ` (${l.pourquoi})` : ""} — ${texte.slice(0, 200)}`];
  if (attendu !== 200) return [];
  const json = JSON.parse(texte);
  const constats = [
    ...(l.corps === undefined ? [] : ecarts(l.corps, json)),
    ...(l.contient === undefined ? [] : ecarts(l.contient, json)),
    ...(l.longueur === undefined || (Array.isArray(json) && json.length === l.longueur) ? [] : [`longueur ${Array.isArray(json) ? json.length : "?"} au lieu de ${l.longueur}`]),
    ...(l.texte === undefined || new RegExp(l.texte).test(texte) ? [] : [`le corps ne contient pas /${l.texte}/`]),
    ...(l.absent === undefined || !new RegExp(l.absent).test(texte) ? [] : [`le corps contient /${l.absent}/, qui ne devait pas y être${l.pourquoi ? ` (${l.pourquoi})` : ""}`]),
  ];
  for (const [nom, { element, champ }] of Object.entries(l.capturer ?? {})) {
    const trouve = (json as Record<string, unknown>[]).find((x) => ecarts(element, x).length === 0);
    if (trouve) vars[nom] = trouve[champ];
    else constats.push(`capture « ${nom} » : aucun élément ne correspond à ${JSON.stringify(element)}`);
  }
  return constats.map((c) => `${ou} : ${c}`);
}

async function constaterEcran(page: Page, hote: string, e: Ecran): Promise<string[]> {
  await page.setViewportSize({ width: e.largeur ?? 1280, height: 900 });
  await allerA(page, `${hote}/admin/${e.page}`);
  const ou = `${e.acteur} /admin/${e.page}${e.largeur ? ` à ${e.largeur} px` : ""}`;
  const rendu = await page.locator("h1").first().waitFor({ timeout: 30_000 }).then(() => true).catch(() => false);
  if (!rendu) return [`${ou} : aucun titre rendu${e.pourquoi ? ` (${e.pourquoi})` : ""}`];
  if (await page.getByTestId("frontiere-erreur").count()) return [`${ou} : ${await page.getByTestId("frontiere-erreur-motif").textContent()}`];
  const constats: string[] = [];
  // Les gestes d'abord : un filtre à choisir, un texte à chercher — ce que l'écran montre ensuite en dépend.
  for (const g of e.gestes ?? []) {
    await poser(page, g).catch((err: Error) => constats.push(`${ou} : ${g.pourquoi} — ${err.message.split("\n")[0]}`));
  }
  for (const c of e.preuve ?? []) if (!(await commandePresente(page, c))) constats.push(`${ou} : commande absente ${c}`);
  for (const t of e.textes ?? []) {
    const vu = await expect(page.locator("main")).toContainText(new RegExp(t), { timeout: 15_000 }).then(() => true).catch(() => false);
    if (!vu) constats.push(`${ou} : texte absent /${t}/`);
  }
  return constats;
}

/** Les écrans d'une capacité, compte par compte : une connexion par compte, dans un contexte neuf. */
async function constaterEcrans(page: Page, hote: string, ecrans: Ecran[]): Promise<string[]> {
  const constats: string[] = [];
  for (const acteur of [...new Set(ecrans.map((e) => e.acteur))]) {
    const contexte = await page.context().browser()!.newContext({ storageState: { cookies: [], origins: [] } });
    const p = await contexte.newPage();
    try {
      await seConnecter(p, hote, acteur);
      for (const e of ecrans.filter((x) => x.acteur === acteur)) constats.push(...await constaterEcran(p, hote, e));
    } catch (err) {
      constats.push(`${acteur} : ${(err as Error).message.split("\n")[0]}`);
    } finally {
      await contexte.close();
    }
  }
  return constats;
}

test.use({ storageState: { cookies: [], origins: [] } });
test.describe.configure({ mode: "serial", timeout: 600_000 });

test.describe("Déploiement — FX-AO-20260810, fonctionnalités jugées sur la démonstration", () => {
  test.skip(!DEPLOYE || !SEMES, "vise une démonstration déployée et semée : poser E2E_BASE_URL et E2E_COMPTES_DEMO=1");

  test.afterAll(async () => {
    for (const c of contextes.values()) await c.dispose();
    contextes.clear();
  });

  for (const p of PROFILS) {
    test(`profil ${p.compte} — ${p.profil}`, async ({ page }) => {
      const hote = await hoteDe(page.request, ORGANISME);
      await seConnecter(page, hote, p.compte);
      const vues = await entreesDuMenu(page);
      const droits = await (await page.request.get(`${hote}/api/alertes/droits`)).json();
      const constats = [
        ...p.menu.present.filter((v) => !vues.includes(v)).map((v) => `absente du menu : ${v}`),
        ...p.menu.absent.filter((v) => vues.includes(v)).map((v) => `présente au menu sans droit : ${v}`),
        ...ecarts(p.droits, droits, "droits"),
      ];
      expect(constats, `menu vu : ${vues.join(", ")}`).toEqual([]);
    });
  }

  for (const c of CAPACITES) {
    test(`${c.id} ${c.titre} (${c.exigences.join(", ")} ; critères ${c.criteres.join(", ")})`, async ({ page }) => {
      const hote = await hoteDe(page.request, ORGANISME);
      const vars: Record<string, unknown> = {};
      const constats: string[] = [];
      for (const l of c.api) {
        // Une capture manquée rend la lecture suivante impossible : le constat le dit, et l'essai continue.
        constats.push(...await lire(hote, l, vars).catch((err: Error) => [`${l.acteur} GET ${l.chemin} : ${err.message}`]));
      }
      constats.push(...await constaterEcrans(page, hote, c.ecrans));
      expect(constats).toEqual([]);
    });
  }
});
