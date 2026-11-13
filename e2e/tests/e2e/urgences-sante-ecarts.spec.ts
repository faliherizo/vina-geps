/**
 * **Les douze écarts du document 06, éprouvés à l'écran** — AO 26-015-DO.
 *
 * Le document `docs/26-015-DO_AO/06-MAQUETTES-ET-ECARTS-RESTANTS.md` listait douze exigences
 * « restant à traiter », reprises de jugements portés le **30 septembre 2026**. En les rouvrant le
 * 1er novembre, chacune s'est révélée servie par du code **et** par des essais : la matrice n'avait
 * pas suivi le produit.
 *
 * Un `grep` qui trouve un service ne prouve pourtant rien d'utile : il ne dit pas que le geste est
 * **atteignable**. Cet essai ouvre donc chaque écran concerné et vérifie que les commandes qui
 * servent l'exigence y sont — c'est précisément ce que trois des douze jugements réclamaient
 * (« aucun essai ne montre … **dans les écrans** »).
 *
 * **Le parcours est piloté par `e2e/donnees/urgences-sante/ecarts.json`** : l'amorce, les écrans et
 * les commandes attendues y sont décrits. Couvrir une exigence de plus se fait en ajoutant une
 * entrée au fichier, sans toucher à ce code — et le fichier se relit sans lire le test.
 */
import { test, expect } from "@playwright/test";
import { superadminContext } from "./helpers/auth";
import { createTenant, setTenantModules, uniq } from "./helpers/organisme";
import { auditA11y } from "./helpers/a11y";
import { amorcerOrganisme, seConnecterOrganisme } from "./helpers/urgences-sante";
import donnees from "../../donnees/urgences-sante/ecarts.json";

test.use({ storageState: { cookies: [], origins: [] } });

/**
 * Deux postes ouverts par cas, et une amorce complète : le budget d'un essai ordinaire n'y suffit
 * pas, et un budget fixe pour un travail proportionnel échoue sur la charge de la machine plutôt
 * que sur le défaut qu'il cherche.
 */
test.describe.configure({ timeout: 240_000 });

test.describe.serial("26-015-DO — les douze écarts, à l'écran", () => {
  let tenantId = "";

  test.beforeAll(async () => {
    const sa = await superadminContext();
    try {
      tenantId = await createTenant(sa, uniq("US-ECARTS"));
      await setTenantModules(sa, tenantId, donnees.modules);
    } finally { await sa.dispose(); }
    await amorcerOrganisme(tenantId);
  });

  test("chaque exigence a ses commandes à l'écran, et l'écran passe axe-core", async ({ page }) => {
    await seConnecterOrganisme(page, tenantId);

    const manquants: Array<{ exigence: string; ecran: string; commande: string }> = [];
    const auditees = new Set<string>();

    for (const e of donnees.exigences) {
      const adresse = `/${e.page}`;
      await page.goto(adresse);
      /*
        **Aucune vérification d'écran ici, et c'est le gain du découpage.**

        Quand ces vues étaient des onglets, il fallait s'assurer que l'onglet demandé était bien
        celui qui s'ouvrait — sans quoi l'on éprouvait l'onglet par défaut en croyant mesurer
        l'autre. Une page à son adresse ne peut pas se tromper d'écran : si elle ne s'est pas
        ouverte, les commandes attendues manqueront, et l'échec le dira.
      */

      /**
       * **Attendre la commande, pas la compter.**
       *
       * Un `count()` posé juste après l'ouverture de l'onglet lit l'écran **avant** que son
       * panneau ait reçu ses données : il rendait absentes onze commandes qui arrivaient une
       * fraction de seconde plus tard. L'attente d'un élément *attaché* lève la course sans
       * masquer une vraie absence — celle-ci devient un dépassement de délai, récolté dans la
       * liste plutôt que levé, pour que l'échec les nomme **toutes** d'un coup.
       */
      /**
       * Les gestes à poser pour atteindre l'état où la commande existe — un tiroir à ouvrir, par
       * exemple. Ils sont **dans le fichier de données** : le test n'a pas à connaître la forme
       * de chaque écran, et un écran qui change se rattrape sans toucher au code.
       */
      for (const geste of (e as { prealable?: Array<{ cliquer: string }> }).prealable ?? []) {
        await page.getByTestId(geste.cliquer).first().click();
      }

      for (const commande of e.preuve) {
        await page.getByTestId(commande).first().waitFor({ state: "attached", timeout: 15_000 })
          .catch(() => { manquants.push({ exigence: e.id, ecran: adresse, commande }); });
      }

      // Un écran n'est audité qu'une fois : six exigences partagent trois onglets.
      if (!auditees.has(adresse)) {
        auditees.add(adresse);
        const violations = await auditA11y(page);
        // **Nommer la cible**, pas seulement la règle : « color-contrast » ne se corrige pas,
        // « le bouton .pla-x à 3,1:1 » se corrige.
        expect(violations.map((v) => `${v.id} — ${v.help} → ${v.targets.join(" | ")}`),
          `axe-core sur ${adresse}`).toEqual([]);
      }
    }

    // Une liste, et non un compte : l'échec nomme l'exigence, l'écran et la commande absente.
    expect(manquants).toEqual([]);
  });

  test("le jeu de données couvre les douze exigences du document 06, sans doublon", () => {
    // La porte du fichier lui-même : une entrée retirée par mégarde rendrait l'essai vert pour
    // une raison fausse — il prouverait onze exigences en disant les prouver toutes.
    const ids = donnees.exigences.map((e) => e.id);
    expect(ids).toHaveLength(12);
    expect(new Set(ids).size).toBe(12);
    for (const e of donnees.exigences) {
      expect({ id: e.id, a: Boolean(e.jugeManquantLe30Septembre), p: e.preuve.length > 0 })
        .toEqual({ id: e.id, a: true, p: true });
    }
  });
});
