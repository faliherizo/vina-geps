/**
 * Interface — **la géométrie de chaque page de l'application, au repos.**
 *
 * Pourquoi un relevé de plus, à côté de `audit-wcag22.spec.ts` : celui-là conduit chaque écran
 * dans ses **états** (tiroir ouvert, formulaire déployé, dialogue) à partir de scripts écrits un
 * par un. C'est plus riche, et c'est aussi plus fragile — quand un script d'état ne passe plus,
 * l'écran est déclaré « jamais rendu » et sa géométrie n'est **pas** mesurée. Au 19 octobre 2026,
 * quarante-deux écrans sont dans ce cas, et ils n'ont donc aucune mesure.
 *
 * Ce relevé-ci ne conduit rien : il **ouvre chaque page par son adresse** et mesure ce qu'elle
 * montre au repos, à quatre largeurs. Il ne remplace pas l'autre — il garantit un plancher :
 * aucune page de l'application ne déborde, ne recouvre un contrôle, ni ne pose une cible
 * minuscule, même si son script d'état est cassé.
 *
 * Référence : `docs/sante-quebec/2027-005/guide_wcag_html_css_accessibilite.md` §6.
 */
import fs from "fs";
import path from "path";
import { test, expect, DEFAULT_MODULES, type ApiStubs } from "./fixtures/app";

/** Les pages de l'application, lues à la source — une page ajoutée est mesurée sans rien écrire. */
function pagesDeLApplication(): string[] {
  const app = fs.readFileSync(path.join(__dirname, "..", "..", "..", "frontend", "src", "App.tsx"), "utf-8");
  const union = /^(?:export )?type Page = (.+);$/m.exec(app);
  if (!union) throw new Error("l'union `type Page` d'App.tsx est introuvable");
  return [...union[1].matchAll(/"([^"]+)"/g)].map((m) => m[1]);
}

/**
 * Écrans écartés, et le motif de chacun — jamais une exclusion par motif large.
 *
 * `superadmin` n'appartient pas à un administrateur d'établissement : il a son propre parcours et
 * son propre compte, et c'est l'audit dédié qui le mesure.
 */
const ECARTES: Record<string, string> = {
  superadmin: "parcours et compte distincts (audit dédié)",
};

const MODULES: ApiStubs = {
  // Tout ouvert : une page masquée par un module fermé ne serait pas mesurée, et l'absence de
  // mesure est précisément ce que ce relevé veut éviter.
  "GET /api/saas/my-modules": {
    modules: [...DEFAULT_MODULES],
    planCode: "enterprise",
  },
};

/**
 * Relevé exécuté dans la page, borné au contenu principal.
 *
 * La barre latérale et les couches flottantes sont hors sujet : la première a son propre essai,
 * les secondes passent au-dessus de ce qui défile par construction. Ce qu'on mesure, c'est le
 * contenu — celui qui porte les formulaires, les tableaux et les boutons.
 */
const RELEVE = `() => {
  const main = document.querySelector("main");
  if (!main) return { absent: true, recouverts: [], petites: [], debordement: 0 };
  const visible = (el) => {
    const s = getComputedStyle(el);
    const r = el.getBoundingClientRect();
    if (s.display === "none" || s.visibility === "hidden" || s.opacity === "0") return false;
    if (r.width === 0 || r.height === 0) return false;
    /*
     * Un \`<details>\` **replié** garde la géométrie de son contenu : le navigateur le rend
     * « sauté » (content-visibility) plutôt que supprimé. Ses champs ont donc une boîte, un
     * display \`block\` et une visibilité \`visible\` — mais rien n'est peint, et
     * \`elementFromPoint\` à leur centre rend ce qui se trouve dessous. Mesurés tels quels, ils
     * produisaient quatre-vingt-seize « recouvrements » qui n'existent pas à l'écran.
     */
    if (el.closest("details:not([open])")) return false;
    if (typeof el.checkVisibility === "function"
        && !el.checkVisibility({ contentVisibilityAuto: true, opacityProperty: true, visibilityProperty: true })) {
      return false;
    }
    return true;
  };
  const flottant = (n) => {
    for (let x = n; x; x = x.parentElement) {
      const s = getComputedStyle(x);
      if (s.position === "fixed" || s.position === "sticky" || x.getAttribute("role") === "menu" || x.getAttribute("role") === "dialog") return true;
    }
    return false;
  };
  const decrire = (el) => el.getAttribute("data-testid") || (el.getAttribute("aria-label") || el.textContent || "").trim().slice(0, 40) || el.tagName.toLowerCase();
  const recouverts = [], petites = [];
  for (const el of main.querySelectorAll("a[href], button, select, input:not([type=hidden]), textarea")) {
    if (!visible(el)) continue;
    if (el.disabled) continue;
    const r = el.getBoundingClientRect();
    const type = (el.getAttribute("type") || "").toLowerCase();
    // Exceptions du critère 2.5.8 : un lien inséré dans un bloc de texte, et une case ou un
    // bouton radio, dont la cible réelle est le libellé qui l'accompagne.
    const enLigne = el.tagName === "A" && el.closest("p, li, caption, small, .muted, .hint, .sq-champ-aide");
    const parLibelle = (type === "checkbox" || type === "radio") && el.closest("label");
    if (!enLigne && !parLibelle && (Math.round(r.width) < 24 || Math.round(r.height) < 24)) {
      petites.push({ quoi: decrire(el), largeur: Math.round(r.width), hauteur: Math.round(r.height) });
    }
    const dedans = r.top >= 0 && r.left >= 0 && r.bottom <= innerHeight && r.right <= innerWidth;
    if (dedans && !flottant(el)) {
      const cible = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
      if (cible && !flottant(cible) && cible !== el && !el.contains(cible) && !cible.contains(el)) {
        recouverts.push({ quoi: decrire(el), par: decrire(cible) });
      }
    }
  }
  return {
    absent: false, recouverts, petites,
    debordement: document.documentElement.scrollWidth - document.documentElement.clientWidth,
  };
}`;

interface Releve {
  absent: boolean;
  recouverts: Array<{ quoi: string; par: string }>;
  petites: Array<{ quoi: string; largeur: number; hauteur: number }>;
  debordement: number;
}

const LARGEURS = [320, 390, 768, 1280];
const PAGES = pagesDeLApplication().filter((p) => !(p in ECARTES));

test.describe("Géométrie — toutes les pages, au repos", () => {
  test("la liste mesurée vient de l'application, et les écarts sont nommés un par un", () => {
    expect(PAGES.length, "pages mesurées").toBeGreaterThan(35);
    for (const [page, motif] of Object.entries(ECARTES)) expect(motif.length, page).toBeGreaterThan(10);
  });

  for (const largeur of LARGEURS) {
    test(`à ${largeur} px : aucun débordement, rien de recouvert, aucune cible sous 24 px`, async ({ page, mock }) => {
      // Cent trente pages à mesurer depuis le module Santé Québec : 5 min 15 s à 1280 px, machine
      // seule, 14 min 24 s à 320 px en parallèle d'autres essais (mesuré le 12 nov. 2026). L'ancien
      // budget de 5 min expirait avant la fin ; 25 min laissent la marge d'une machine partagée.
      test.setTimeout(1_500_000);
      await mock(MODULES);
      await page.setViewportSize({ width: largeur, height: 900 });

      const manquements: string[] = [];
      const jamaisRendues: string[] = [];
      /** La dernière erreur de rendu par page : c'est elle qui explique une page blanche. */
      const erreursConsole = new Map<string, string>();
      let courante = "";
      page.on("pageerror", (e) => { if (courante) erreursConsole.set(courante, e.message.slice(0, 160)); });

      for (const cle of PAGES) {
        courante = cle;
        await page.goto(`/${cle}`);
        // Le contenu principal doit paraître : une page qui ne se monte pas ne se mesure pas, et
        // un écran sauté en silence rendrait ce relevé décoratif. On **patiente** : l'application
        // démarre en demandant son compte et ses modules, et un `isVisible()` immédiat conclut
        // « jamais rendue » sur toutes les pages — ce qu'il a fait au premier passage.
        const prete = await page.locator("main").first()
          .waitFor({ state: "visible", timeout: 15_000 }).then(() => true).catch(() => false);
        if (!prete) {
          // Dire **pourquoi** : « ne se monte pas » n'est pas exploitable, « plantage au rendu
          // avec une réponse vide » l'est. On relève le texte visible et la dernière erreur.
          const indice = await page.evaluate(() => (document.body.innerText || "").trim().slice(0, 160)).catch(() => "");
          jamaisRendues.push(`${cle} (${erreursConsole.get(cle) ?? indice ?? "sans indice"})`);
          continue;
        }
        await page.waitForTimeout(150);

        const r = await page.evaluate(`(${RELEVE})()`) as Releve;
        if (r.absent) { jamaisRendues.push(cle); continue; }
        if (r.debordement > 2) manquements.push(`${cle} — débordement horizontal de ${r.debordement} px`);
        for (const x of r.recouverts) manquements.push(`${cle} — « ${x.quoi} » est recouvert par « ${x.par} »`);
        for (const x of r.petites) manquements.push(`${cle} — cible « ${x.quoi} » de ${x.largeur}×${x.hauteur} px`);
      }

      expect(jamaisRendues, `pages qui ne se montent pas : ${jamaisRendues.join(", ")}`).toEqual([]);
      expect(manquements, manquements.join("\n")).toEqual([]);
    });
  }
});
