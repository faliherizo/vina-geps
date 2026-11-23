/**
 * **Les 90 exigences fonctionnelles du 26-015-DO, vérifiées à l'écran.**
 *
 * La matrice de couverture de l'avis a été fausse deux fois, et toujours dans le même sens :
 * des exigences données « partielles » alors que le code et les essais les servaient depuis des
 * semaines. La cause n'est pas l'inattention, c'est le dispositif — un jugement écrit en prose ne
 * se vérifie pas, et rien ne le relit.
 *
 * Cet essai est le dispositif qui manquait. Pour **chaque** exigence fonctionnelle de l'avis, il
 * ouvre l'écran annoncé, pose les gestes qui mènent à l'état utile, et vérifie que les commandes
 * qui servent l'exigence y sont. Il échoue si une exigence de `exigences-26-015-DO.json` n'a pas
 * son entrée dans le jeu de données : une exigence ne peut donc plus être déclarée servie sans que
 * quelqu'un ait dit **où** et **comment** le constater.
 *
 * Ce qu'il ne prouve pas, et qu'il ne prétend pas prouver : le **comportement**. Qu'un bouton
 * existe ne dit pas qu'il décide juste. Le comportement est prouvé par les essais nommés dans
 * `essaiProfond`, relevés du dépôt par `docs/26-015-DO_AO/relever-essais.cjs`. Les deux niveaux
 * sont nécessaires : un essai d'intégration vert sur un écran inatteignable est une conformité de
 * papier, et un écran peuplé de boutons sans règle derrière en est une autre.
 *
 * Piloté par `e2e/donnees/urgences-sante/exigences-fonctionnelles.json`.
 */
import { readFileSync } from "node:fs";
import path from "node:path";
import { test, expect, type Page } from "@playwright/test";
import { superadminContext } from "./helpers/auth";
import { createTenant, setTenantModules, uniq } from "./helpers/organisme";
import { auditA11y } from "./helpers/a11y";
import { commandePresente, poser, type Geste } from "./helpers/gestes";
import { amorcerOrganisme, modulesDeLOrganisme, seConnecterOrganisme } from "./helpers/urgences-sante";
import jeu from "../../donnees/urgences-sante/exigences-fonctionnelles.json";
import { raconter } from "./helpers/recit";

test.use({ storageState: { cookies: [], origins: [] } });

/** Quatre-vingt-dix exigences, 22 écrans et 31 états : un budget proportionnel, jamais fixe. */
test.describe.configure({ timeout: 90 * 6_000 + 240_000 });

interface Exigence {
  id: string; page: string; geste: string;
  prealable?: Geste[];
  preuve: string[]; essaiProfond?: string[];
}
const EXIGENCES = jeu.exigences as Exigence[];

/** Les exigences **fonctionnelles** de l'avis : les E-TN/P-TN ne se vérifient par aucun geste. */
function exigencesDeLAvis(): string[] {
  const chemin = path.join(__dirname, "../../../docs/26-015-DO_AO/exigences-26-015-DO.json");
  const avis = JSON.parse(readFileSync(chemin, "utf8")) as { exigences: Array<{ id: string }> };
  return avis.exigences.map((e) => e.id).filter((id) => !id.includes("-TN-"));
}

/** Une entrée de menu, une page : l'adresse est la clé de la page, sans paramètre d'onglet. */
const adresseDe = (e: Exigence): string => `/${e.page}`;
test.describe.serial("26-015-DO — les 90 exigences fonctionnelles, à l'écran", () => {
  let tenantId = "";

  test.beforeAll(async () => {
    const sa = await superadminContext();
    try {
      tenantId = await createTenant(sa, uniq("Demo-Ecrans"));
      await setTenantModules(sa, tenantId, modulesDeLOrganisme);
    } finally { await sa.dispose(); }
    await amorcerOrganisme(tenantId, { avecTempsEtCollecte: true });
  });

  test("chaque exigence fonctionnelle a ses commandes à l'écran, et chaque écran passe axe-core", async ({ page }) => {
    await seConnecterOrganisme(page, tenantId);

    const manquants: Array<{ exigence: string; ecran: string; commande: string }> = [];
    const auditees = new Set<string>();
    const violationsParEcran: string[] = [];

    for (const e of EXIGENCES) {
      const adresse = adresseDe(e);
      await page.goto(adresse);
      /*
        **Aucune vérification d'écran ici, et c'est le gain du découpage.**

        Quand ces vues étaient des onglets, il fallait s'assurer que l'onglet demandé était
        bien celui qui s'ouvrait — sans quoi l'on éprouvait l'onglet par défaut en croyant
        mesurer l'autre. Une page à son adresse ne peut pas se tromper d'écran : si elle ne
        s'est pas ouverte, les commandes attendues manqueront, et l'échec le dira.
      */

      // Les gestes qui mènent à l'état utile — un tiroir à ouvrir, une vue à choisir. Ils sont
      // **dans le fichier de données** : un écran qui change se rattrape sans toucher à ce code.
      await raconter(page, e.geste);
      for (const geste of e.prealable ?? []) await poser(page, geste);

      for (const commande of e.preuve) {
        if (!(await commandePresente(page, commande))) {
          manquants.push({ exigence: e.id, ecran: adresse, commande });
        }
      }

      // Un écran n'est audité qu'une fois : plusieurs exigences partagent le même onglet. L'audit
      // a lieu **après** les gestes, donc tiroir ouvert : un état non audité est un état non
      // conforme jusqu'à preuve du contraire.
      const clePrealable = `${adresse}#${(e.prealable ?? []).map((g) => g.cliquer ?? g.choisir ?? g.remplir).join(",")}`;
      if (!auditees.has(clePrealable)) {
        auditees.add(clePrealable);
        const violations = await auditA11y(page);
        // **Nommer la cible**, pas seulement la règle : « color-contrast » ne se corrige pas,
        // « le bouton .pla-x à 3,1:1 » se corrige.
        for (const v of violations) violationsParEcran.push(`${clePrealable} — ${v.id} : ${v.help} → ${v.targets.join(" | ")}`);
      }
    }

    // Des listes, et non des comptes : l'échec nomme l'exigence, l'écran et la commande absente.
    expect(manquants).toEqual([]);
    expect(violationsParEcran).toEqual([]);
  });

  test("le jeu de données couvre toutes les exigences fonctionnelles de l'avis, sans doublon ni invention", () => {
    const avis = exigencesDeLAvis();
    const declarees = EXIGENCES.map((e) => e.id);
    // Deux sens, et non un seul : une exigence oubliée rendrait l'essai vert pour une raison
    // fausse, une exigence inventée ferait croire à une couverture que l'avis ne demande pas.
    expect([...declarees].sort()).toEqual([...avis].sort());
    expect(new Set(declarees).size).toBe(declarees.length);

    // Chaque entrée dit **où**, **comment**, et par quoi le comportement est prouvé.
    const incompletes = EXIGENCES.filter((e) => !e.page || !e.geste || e.preuve.length === 0
      || (e.essaiProfond ?? []).length === 0).map((e) => e.id);
    expect(incompletes).toEqual([]);
  });
});
