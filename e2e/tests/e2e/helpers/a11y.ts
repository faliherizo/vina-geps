import AxeBuilder from "@axe-core/playwright";
import { expect, type Page } from "@playwright/test";

/**
 * Audit d'accessibilité automatisé, adossé à axe-core.
 *
 * Le devis R-0640-27 impose la conformité au **SGQRI-008 3.0** (RNF-014), le standard
 * québécois qui transpose **WCAG 2.1 niveau AA**. Les règles activées ci-dessous couvrent
 * donc `wcag2a`, `wcag2aa`, `wcag21a` et `wcag21aa`.
 *
 * Un audit automatisé ne remplace pas un audit manuel — il détecte environ un tiers des
 * critères WCAG. Ce qu'il attrape de façon fiable, et qui régresse le plus souvent, c'est
 * précisément ce qu'on vérifie ici : libellés de formulaires manquants, contrastes
 * insuffisants, tableaux sans en-têtes, ordre de titres incohérent, attributs ARIA invalides.
 */
export const WCAG_AA_TAGS = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"] as const;

export interface A11yViolation {
  id: string;
  impact: string | null | undefined;
  help: string;
  nodes: number;
  targets: string[];
}

/** Analyse la page (ou un sous-arbre) et retourne les manquements, sans assertion. */
export async function auditA11y(page: Page, selector?: string): Promise<A11yViolation[]> {
  // L'état mesuré est l'état stable : un tiroir qui entre en fondu (opacité 0 → 1) mesuré à
  // mi-parcours rend des couleurs mélangées, et un contraste qui n'existe pas à l'écran.
  await page.evaluate(() => Promise.all(document.getAnimations()
    .filter((a) => a.effect?.getComputedTiming().iterations !== Infinity)
    .map((a) => a.finished.catch(() => undefined)))).catch(() => undefined);
  let builder = new AxeBuilder({ page }).withTags([...WCAG_AA_TAGS]);
  if (selector) builder = builder.include(selector);
  const results = await builder.analyze();
  return results.violations.map((v) => ({
    id: v.id,
    impact: v.impact,
    help: v.help,
    nodes: v.nodes.length,
    // Pour un contraste, les couleurs mesurées : sans elles, un état transitoire (fondu, voile) est indevinable.
    targets: v.nodes.slice(0, 3).map((n) => n.target.join(" ") + (v.id === "color-contrast" ? " " + JSON.stringify(n.any[0]?.data) : "")),
  }));
}

/**
 * Échoue si la page présente un manquement WCAG 2.1 AA.
 *
 * Le message d'erreur liste la règle, son impact et les sélecteurs fautifs : sans cela un
 * échec d'accessibilité est illisible en CI et finit par être ignoré.
 */
export async function expectNoA11yViolations(page: Page, selector?: string): Promise<void> {
  const violations = await auditA11y(page, selector);
  const report = violations
    .map((v) => `  • [${v.impact ?? "n/a"}] ${v.id} — ${v.help}\n    ${v.nodes} élément(s) : ${v.targets.join(", ")}`)
    .join("\n");
  expect(violations, `Manquements WCAG 2.1 AA (SGQRI-008 3.0) :\n${report}`).toEqual([]);
}
