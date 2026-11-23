/**
 * **FX-AO-20260810 — les 32 critères fonctionnels, constatés à l'écran ; les 115, chacun avec sa preuve.**
 *
 * Pour chaque critère fonctionnel de la grille, l'essai ouvre l'écran annoncé et vérifie que les commandes qui
 * le servent y sont, puis passe axe-core sur chaque écran. Le second essai garde le jeu honnête : les 115
 * critères y sont, une seule fois ; un scénario cité existe dans `comportements.json` ; un essai Java ou un
 * document cité existe dans le dépôt. Le comportement (ce que la règle décide) est prouvé par
 * `fx-ao-comportements.spec.ts` et par les essais Java nommés.
 *
 * Piloté par `e2e/donnees/fx-ao/exigences.json`.
 */
import { existsSync } from "node:fs";
import path from "node:path";
import { test, expect } from "@playwright/test";
import { superadminContext } from "./helpers/auth";
import { createTenant, setTenantModules, uniq } from "./helpers/organisme";
import { auditA11y } from "./helpers/a11y";
import { commandePresente, poser, type Geste } from "./helpers/gestes";
import { amorcerOrganisme, modulesDeLOrganisme, seConnecterOrganisme } from "./helpers/urgences-sante";
import jeu from "../../donnees/fx-ao/exigences.json";
import comportements from "../../donnees/fx-ao/comportements.json";
import { raconter } from "./helpers/recit";

test.use({ storageState: { cookies: [], origins: [] } });
test.describe.configure({ timeout: 32 * 8_000 + 240_000 });

interface Critere {
  id: string; intitule: string; statut: string; bloc: string;
  page?: string; prealable?: Geste[]; preuve?: string[];
  scenarios?: string[]; essaiProfond?: string[]; documents?: string[];
}
const CRITERES = jeu.exigences as Critere[];
const RACINE = path.join(__dirname, "../../..");

test.describe.serial("FX-AO-20260810 — la grille de conformité, à l'écran", () => {
  let tenantId = "";

  test.beforeAll(async () => {
    const sa = await superadminContext();
    try {
      tenantId = await createTenant(sa, uniq("Demo-Alertes"));
      await setTenantModules(sa, tenantId, [...modulesDeLOrganisme, "alertes"]);
    } finally { await sa.dispose(); }
    await amorcerOrganisme(tenantId);
  });

  test("chaque critère fonctionnel a ses commandes à l'écran, et chaque écran passe axe-core", async ({ page }) => {
    await seConnecterOrganisme(page, tenantId);
    const manquants: string[] = [];
    const violations: string[] = [];
    const auditees = new Set<string>();
    for (const c of CRITERES.filter((x) => x.page)) {
      await page.goto(`/${c.page}`);
      await raconter(page, `${c.intitule}. Ses commandes doivent être présentes à l'écran.`);
      for (const geste of c.prealable ?? []) await poser(page, geste);
      for (const commande of c.preuve ?? []) {
        if (!(await commandePresente(page, commande))) manquants.push(`critère ${c.id} — /${c.page} : ${commande}`);
      }
      if (!auditees.has(c.page!)) {
        auditees.add(c.page!);
        for (const v of await auditA11y(page)) violations.push(`/${c.page} — ${v.id} : ${v.help} → ${v.targets.join(" | ")}`);
      }
    }
    expect(manquants).toEqual([]);
    expect(violations).toEqual([]);
  });

  test("les 115 critères sont décrits une fois, et chaque preuve citée existe", () => {
    expect(CRITERES.map((c) => Number(c.id))).toEqual(Array.from({ length: 115 }, (_, i) => i + 1));
    const fonctionnels = CRITERES.filter((c) => c.bloc === "fonctionnel");
    // Un critère fonctionnel acquis se constate à l'écran : sans page ni commande, il n'est que déclaré.
    expect(fonctionnels.filter((c) => c.statut === "✅" && (!c.page || !(c.preuve ?? []).length)).map((c) => c.id)).toEqual([]);
    // Et il se prouve par un comportement : un scénario joué contre l'API, ou un essai qui agit. La présence d'un écran
    // ne dit pas que la règle tient — c'est ainsi que 20, 21, 22, 26 et 29 étaient « acquis » sans preuve (22 nov. 2026).
    expect(fonctionnels.filter((c) => c.statut !== "📄" && !(c.scenarios ?? []).length && !(c.essaiProfond ?? []).length)
      .map((c) => c.id), "critères fonctionnels sans preuve de comportement").toEqual([]);
    const connus = new Set(comportements.scenarios.map((s) => s.id));
    const inconnus = CRITERES.flatMap((c) => (c.scenarios ?? []).filter((s) => !connus.has(s)).map((s) => `${c.id} → ${s}`));
    expect(inconnus).toEqual([]);
    const absents = CRITERES.flatMap((c) => [...(c.essaiProfond ?? []), ...(c.documents ?? [])]
      .filter((f) => !existsSync(path.join(RACINE, f))).map((f) => `${c.id} → ${f}`));
    expect(absents).toEqual([]);
  });
});
