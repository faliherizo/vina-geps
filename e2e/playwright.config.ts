import { defineConfig } from "@playwright/test";

/**
 * Campagnes Playwright d'Urgences-santé, portables par variables d'environnement :
 *   - `E2E_WEB_PORT` / `E2E_API_PORT` : ports du front Vite et du serveur Java (défaut 5299 / 4020) ;
 *   - `E2E_BASE_URL` : un environnement **déjà déployé** — aucun serveur local n'est lancé ;
 *   - `E2E_VIDEO` : `on` | `retain-on-failure` (défaut) | `off`.
 *
 * Le serveur Java n'est **pas** lancé ici : `backend-java/scripts/campagne-e2e.sh` le démarre sur
 * une base vierge que Liquibase bâtit, en environnement d'essai (`NEXU_ESSAI=1`). Playwright ne
 * lance que le front, dont le mandataire vise ce serveur.
 *
 * Rapports : HTML (`playwright-report/`), JSON (`test-results/report.json`), JUnit
 * (`test-results/junit.xml`).
 */
const WEB_PORT = process.env.E2E_WEB_PORT ?? "5299";
const API_PORT = process.env.E2E_API_PORT ?? "4020";
const BASE_URL = process.env.E2E_BASE_URL ?? `http://localhost:${WEB_PORT}`;
const START_LOCAL = !process.env.E2E_BASE_URL;
const WORKERS = Number(process.env.PW_WORKERS ?? 1);

export default defineConfig({
  testDir: "./tests",
  // Le tournage du film (`E2E_RALENTI`) ralentit chaque geste : le budget par défaut suit, sinon un essai
  // parfaitement vert expire au ralenti.
  timeout: process.env.E2E_RALENTI ? 120_000 : 30_000,
  fullyParallel: false,
  workers: WORKERS,
  reporter: [
    ["list"],
    ["html", { outputFolder: "playwright-report", open: "never" }],
    ["json", { outputFile: "test-results/report.json" }],
    ["junit", { outputFile: "test-results/junit.xml" }],
  ],
  globalSetup: require.resolve("./global-setup"),
  use: {
    baseURL: BASE_URL,
    trace: "on-first-retry",
    // Sans taille donnée, Playwright réduit le film à 800 px de large : illisible à la vitrine.
    video: process.env.E2E_VIDEO_LARGEUR
      ? { mode: "on", size: { width: Number(process.env.E2E_VIDEO_LARGEUR), height: Math.round(Number(process.env.E2E_VIDEO_LARGEUR) * 9 / 16) } }
      : (process.env.E2E_VIDEO as "on" | "off" | "retain-on-failure") ?? "retain-on-failure",
    // **Le film de la vitrine** (`scripts/monter-video-vitrine.sh`) : une image lisible demande un
    // geste lisible. `E2E_RALENTI` (ms) ralentit chaque action, et `E2E_VIDEO_LARGEUR` fixe la taille.
    ...(process.env.E2E_RALENTI ? { launchOptions: { slowMo: Number(process.env.E2E_RALENTI) } } : {}),
    ...(process.env.E2E_VIDEO_LARGEUR ? {
      viewport: { width: Number(process.env.E2E_VIDEO_LARGEUR), height: Math.round(Number(process.env.E2E_VIDEO_LARGEUR) * 9 / 16) },
      locale: "fr-CA", timezoneId: "America/Toronto",
    } : {}),
    screenshot: "only-on-failure",
    storageState: "./.auth/admin.json",
  },
  projects: [
    /**
     * `interface` — essais d'écran, API doublée (`tests/ui/fixtures/app.ts`) : aucun état partagé,
     * donc parallélisables. Le budget de 90 s couvre l'audit géométrique à quatre largeurs.
     */
    {
      name: "interface",
      testDir: "./tests/ui",
      fullyParallel: true,
      timeout: 90_000,
      use: { browserName: "chromium", storageState: { cookies: [], origins: [] }, actionTimeout: 5_000 },
    },
    /** `e2e` — parcours complets contre le vrai serveur Java : une base partagée, donc sérialisés. */
    { name: "e2e", testDir: "./tests/e2e", use: { browserName: "chromium" } },
  ],
  ...(START_LOCAL
    ? {
        webServer: [
          {
            command: `npm --prefix ../frontend run dev -- --port ${WEB_PORT} --strictPort`,
            port: Number(WEB_PORT),
            reuseExistingServer: !process.env.CI,
            timeout: 60_000,
            // Le mandataire Vite vise le serveur de la campagne, pas un :4000 occupé par ailleurs.
            env: { API_PORT: API_PORT },
          },
        ],
      }
    : {}),
});
