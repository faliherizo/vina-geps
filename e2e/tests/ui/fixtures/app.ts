/**
 * Harnais des tests **d'interface**.
 *
 * Ces tests ne valident que le comportement de l'interface web : rendu, navigation, états de
 * formulaire, accessibilité du balisage, réactions aux réponses de l'API. Le serveur n'est
 * jamais sollicité — toutes les requêtes `/api/**` sont interceptées et servies depuis des
 * doublures.
 *
 * C'est ce qui rend ces tests **parallélisables** : sans base de données partagée, deux tests
 * ne peuvent plus se marcher dessus. La suite e2e, elle, doit rester sérialisée parce qu'elle
 * partage un unique backend en mémoire.
 *
 * Ce qu'ils ne prouvent pas — et qui reste du ressort de la suite e2e : que le serveur répond
 * réellement cela. Une doublure qui diverge du serveur produit un test vert sur une interface
 * cassée ; les parcours critiques doivent donc rester couverts de bout en bout.
 */
import { test as base, expect, type Page, type Route } from "@playwright/test";

/** Réponse d'une doublure : une valeur JSON, ou une fonction qui la calcule. */
export type StubValue =
  | unknown
  | ((info: { url: URL; method: string; body: unknown }) => unknown | { status: number; body?: unknown });

/** Table de doublures : `"GET /api/auth/me"` → réponse. Le verbe est optionnel (GET par défaut). */
export type ApiStubs = Record<string, StubValue>;

export const DEFAULT_USER = {
  id: "usr-demo",
  username: "admin",
  role: "admin",
  tenantId: "tnt-demo",
  employeeId: null,
  active: true,
  mustChangePassword: false,
  securitySetupRequired: false,
  email: "admin@exemple.qc.ca",
};

/** Modules ouverts par défaut : tout le catalogue du produit, communication comprise. */
export const DEFAULT_MODULES = [
  "approvals", "notifications", "integrations", "confidentialite",
  "effectifs", "effectifs_regles", "effectifs_planification", "effectifs_disponibilites",
  "effectifs_absences", "effectifs_echanges", "effectifs_agph", "effectifs_presence",
  "effectifs_temps", "effectifs_communication", "effectifs_integrations", "alertes",
];

/** Doublures minimales pour que l'application démarre et affiche sa navigation. */
const BOOT_STUBS: ApiStubs = {
  "GET /api/auth/me": DEFAULT_USER,
  "GET /api/auth/profile": { profile: { jobTitle: "Administration" } },
  "GET /api/auth/tenants": [{ id: "tnt-demo", name: "Établissement de démonstration" }],
  "GET /api/auth/deploiement": { resolution: "selecteur", domaineRacine: null, hote: "selecteur", etablissement: null },
  "GET /api/saas/my-modules": { modules: DEFAULT_MODULES, planCode: "enterprise" },
};

const normalise = (method: string, path: string) => `${method.toUpperCase()} ${path}`;

/**
 * Installe l'interception. Les clés les plus **spécifiques** l'emportent : une entrée exacte
 * prime sur un préfixe, ce qui permet à un test de ne surcharger qu'un seul endpoint.
 */
export async function mockApi(page: Page, stubs: ApiStubs = {}) {
  const table = { ...BOOT_STUBS, ...stubs };
  const keys = Object.keys(table).sort((a, b) => b.length - a.length);

  await page.route("**/api/**", async (route: Route) => {
    const request = route.request();
    const url = new URL(request.url());
    const method = request.method();
    const exact = normalise(method, url.pathname);

    // Trois formes de correspondance, de la plus stricte à la plus lâche :
    //   1. égalité exacte              « DELETE /api/x/1 »
    //   2. joker de segment            « DELETE /api/x/* »  → un segment quelconque
    //   3. préfixe                     « GET /api/x »       → couvre /api/x/quoi/que/ce/soit
    // Les clés sont parcourues de la plus longue à la plus courte : une entrée précise
    // l'emporte donc toujours sur une entrée générale.
    const withVerb = (k: string) => (k.includes(" ") ? k : `GET ${k}`);
    const segmentsMatch = (pattern: string, target: string) => {
      const p = pattern.split("/");
      const t = target.split("/");
      return p.length === t.length && p.every((seg, i) => seg === "*" || seg === t[i]);
    };

    const key =
      keys.find((k) => withVerb(k) === exact) ??
      keys.find((k) => withVerb(k).includes("*") && segmentsMatch(withVerb(k), exact)) ??
      keys.find((k) => exact.startsWith(withVerb(k)));

    if (!key) {
      // Endpoint non doublé : on répond « rien », plutôt que de laisser fuir la requête vers
      // un vrai serveur — un test d'interface ne doit dépendre d'aucun backend.
      const empty = method === "GET" ? [] : { ok: true };
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(empty) });
      return;
    }

    const value = table[key];
    let body: unknown;
    let status = 200;
    if (typeof value === "function") {
      let parsed: unknown = null;
      try { parsed = request.postDataJSON(); } catch { parsed = null; }
      const produced = (value as (i: { url: URL; method: string; body: unknown }) => unknown)({ url, method, body: parsed });
      if (produced && typeof produced === "object" && "status" in (produced as Record<string, unknown>)) {
        status = (produced as { status: number }).status;
        body = (produced as { body?: unknown }).body ?? null;
      } else {
        body = produced;
      }
    } else {
      body = value;
    }

    await route.fulfill({
      status,
      contentType: "application/json",
      body: JSON.stringify(body ?? null),
    });
  });
}

/**
 * `test` enrichi : `mock(stubs)` installe les doublures **avant** toute navigation, puis
 * `page.goto` peut être appelé normalement.
 */
export const test = base.extend<{ mock: (stubs?: ApiStubs) => Promise<void> }>({
  mock: async ({ page }, use) => {
    await use((stubs: ApiStubs = {}) => mockApi(page, stubs));
  },
});

export { expect };
