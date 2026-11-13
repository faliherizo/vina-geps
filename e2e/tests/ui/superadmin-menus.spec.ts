/**
 * Interface — regroupement de l'espace superadmin en **menus**.
 *
 * Ce que le test vérifie : chaque onglet n'affiche **que** ses propres sections. C'est de la
 * logique d'affichage pure — le contenu des sections, lui, vient du serveur et relève de la
 * suite e2e.
 *
 * L'isolation entre onglets mérite un test dédié : sans elle, l'espace superadmin redevient la
 * page unique et interminable qu'il était avant son découpage.
 */
import { test, expect } from "./fixtures/app";
import { ouvrirNav } from "../e2e/helpers/nav";

const SUPERADMIN = {
  id: "usr-sa",
  username: "superadmin",
  role: "superadmin",
  tenantId: null,
  employeeId: null,
  active: true,
  mustChangePassword: false,
  securitySetupRequired: false,
  email: "superadmin@exemple.qc.ca",
};

test.describe("Espace superadmin — menus", () => {
  test("chaque menu n'affiche que ses propres sections", async ({ page, mock }) => {
    await mock({
      "GET /api/auth/me": SUPERADMIN,
      "GET /api/superadmin/tenants": [],
      "GET /api/vitrine/content": { hero: {}, sections: [] },
      "GET /api/saas/maintenance": { active: false, message: "" },
      "GET /api/soutien/politique": { langue: "fr", niveaux: [{ code: "N1", description: "Service interrompu", delaiResolutionMinutes: 60 }], objectifDisponibilitePourcent: 99.5, preavisMaintenanceHeures: 72 },
      "GET /api/soutien/disponibilite": { mois: "2026-09", observeDepuis: "2026-09-01T00:00:00.000Z", observeJusqua: "2026-09-12T08:00:00.000Z", minutesObservees: 16320, minutesEnMaintenance: 120, minutesIndisponibles: 40, pourcentage: 99.75, objectifPourcent: 99.5, objectifAtteint: true },
      "GET /api/exploitation/securite": { version: { application: "1.0.0", git: "abc1234567890", node: "v22", migrations: "0067" },
        residence: { region: "canadaeast", hebergeur: "Azure canadaeast", auQuebec: true, auCanada: true, ia: "Ollama local" },
        pareFeu: { mode: "bloquer", reglages: { requetesParMinute: 20000, echecsConnexionAvantVerrou: 10, verrouMinutes: 15 }, blocages24h: { injection_sql: 2 }, verrousActifs: 0 },
        enTetes: ["X-Frame-Options"], balayage: { faitLe: "2026-09-12T18:00:00.000Z", outil: "npm audit", vulnerabilites: { critique: 0, elevee: 0, moderee: 0, faible: 0, total: 0 } },
        capacite: { demarreLe: "2026-09-12T00:00:00.000Z", dureeSecondes: 90000, cpus: 8, chargeMoyenne1Min: 1.2, memoireRssMo: 180, memoireTasMo: 60, baseOctets: 5_242_880, requetesDerniereMinute: 42, latenceP95Ms: 12, latenceP50Ms: 4, enVol: 1 } },
      "GET /api/auth/audit/integrite": { entrees: 120, chainees: 120, nonChainees: 0, intacte: true, premiereRupture: null, derniereEmpreinte: "ab".repeat(32), revue: { derniere: null, prochaineAvant: "2026-12-01T00:00:00.000Z", enRetard: false } },
      "GET /api/exploitation/sauvegardes": {
        dossier: "/srv/nexu/sauvegardes",
        retention: { incrementale: 96, complete: 30 },
        intervalleMinutes: { incrementale: 15, complete: 1440 },
        sauvegardes: [{ fichier: "2026-09-12T08-00-00-000Z-complete.sqlite", type: "complete", faiteLe: "2026-09-12T08:00:00.000Z", octets: 2_400_000 }],
      },
      "GET /api/countries": [{ code: "CA", name: "Canada", currency: "CAD" }],
      "GET /api/saas/consommation": { mois: "2026-09", organismes: [],
        totaux: { textosSegments: 0, textosMessages: 0, courriels: 0, depassementTextos: 0, depassementCourriels: 0 } },
      "GET /api/soutien/tickets/resume": { ouverts: 3, nouveaux: 1, enRetard: 0, dernierOuvertLe: "2026-09-14T08:00:00.000Z" },
      "GET /api/soutien/notification": { destinataires: ["superadmin@exemple.qc.ca"], courrielDuCompte: "superadmin@exemple.qc.ca", boitePartagee: null, transport: { mode: "smtp", hote: "smtp.exemple.qc", expediteur: "soutien@exemple.qc.ca" } },
      // La section « Conformité » monte le tableau des exigences : il lui faut la forme
      // attendue, sinon le composant échoue au rendu et la section entière disparaît.
      "GET /api/varpay/requirements": {
        reference: "AIN20260525 §3.4.3",
        summary: { total: 1, implemented: 1, pivot: 0, infra: 0 },
        categories: ["Générales"],
        requirements: [{
          id: 1, category: "Générales", status: "implemented",
          text: "Logiciel-service clé en main de type infonuagique SaaS.",
          response: "Architecture multi-tenant.",
        }],
      },
    });

    await page.goto("/");
    await ouvrirNav(page, "nav-group-admin").catch(() => {});
    await ouvrirNav(page, "nav-superadmin");
    await expect(page.getByTestId("superadmin-page")).toBeVisible();

    // Les liens vers la documentation d'API restent dans l'en-tête, quel que soit l'onglet.
    await expect(page.getByTestId("sa-api-links")).toBeVisible();
    await expect(page.getByTestId("sa-swagger")).toHaveAttribute("href", "/api/integrations/swagger");
    await expect(page.getByTestId("sa-openapi")).toHaveAttribute("href", "/api/integrations/openapi.json");

    // Onglet par défaut : Établissements.
    await expect(page.getByTestId("sa-tabs")).toBeVisible();
    await expect(page.getByTestId("sa-create")).toBeVisible();

    // Ni vitrine, ni abonnements, ni facturation dans ce produit (D3) : ces onglets n'existent pas.
    await expect(page.getByTestId("sa-tab-vitrine")).toHaveCount(0);
    await expect(page.getByTestId("sa-tab-billing")).toHaveCount(0);

    // Consommation d'avis — et réciproquement, plus d'établissements.
    await page.getByTestId("sa-tab-consommation").click();
    await expect(page.getByTestId("sa-conso-table")).toBeVisible();
    await expect(page.getByTestId("sa-create")).toHaveCount(0);

    // Conformité : le message de maintenance.
    await page.getByTestId("sa-tab-compliance").click();
    await expect(page.getByTestId("sa-maintenance")).toBeVisible();

    // Exploitation : la politique de sauvegarde lue du serveur, et la copie listée avec sa taille lisible.
    await page.getByTestId("sa-tab-exploitation").click();
    await expect(page.getByTestId("sa-sauvegardes")).toBeVisible();
    await expect(page.getByTestId("sa-maintenance")).toHaveCount(0);
    await expect(page.getByTestId("sa-sauvegardes-politique")).toContainText("toutes les 24 h, 30 copies conservées");
    await expect(page.getByTestId("sa-sauvegarde-2026-09-12T08-00-00-000Z-complete.sqlite")).toContainText("2.3 Mo");
    await expect(page.getByTestId("sa-saml-metadata")).toHaveAttribute("href", "/api/auth/sso/saml/metadata");
    // Sécurité et registres : les chiffres du serveur sont écrits tels quels.
    await expect(page.getByTestId("sa-securite-residence")).toContainText("au Québec");
    await expect(page.getByTestId("sa-securite-blocages")).toContainText("injection SQL : 2");
    await expect(page.getByTestId("sa-securite-balayage")).toContainText("0 vulnérabilité(s)");
    await expect(page.getByTestId("audit-integrite-etat")).toContainText("intacte : 120 entrées scellées");

    // Soutien : son propre onglet, avec le compte des billets ouverts ; la disponibilité et le dispositif d'avis y sont.
    await expect(page.getByTestId("sa-soutien-badge")).toHaveText("3");
    await page.getByTestId("sa-tab-soutien").click();
    await expect(page.getByTestId("sa-soutien")).toBeVisible();
    await expect(page.getByTestId("sa-sauvegardes")).toHaveCount(0);
    await expect(page.getByTestId("sa-dispo-resume")).toContainText("99,75 % — objectif 99.5 % : atteint");
    await expect(page.getByTestId("sa-avis-transport-smtp")).toContainText("smtp.exemple.qc");
    await expect(page.getByTestId("sa-avis-destinataires")).toContainText("superadmin@exemple.qc.ca");
  });

  test("sans organisme choisi, le superadmin atterrit sur sa console, et rien d'autre ne paraît au menu", async ({ page, mock }) => {
    await mock({
      "GET /api/auth/me": { ...SUPERADMIN, tenantId: null },
      "GET /api/saas/my-modules": { modules: [], planCode: null },
      "GET /api/superadmin/tenants": [],
    });
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto("/");
    await expect(page.getByTestId("superadmin-page")).toBeVisible();
    const entrees = await page.locator('[data-testid^="nav-"]').evaluateAll((els) => els.map((e) => e.getAttribute("data-testid")!)
      .filter((n) => !n.startsWith("nav-group-") && !n.startsWith("nav-concept-")));
    expect(entrees).toEqual(["nav-superadmin"]);
    // Une adresse d'écran du module, tapée à la main, ramène à la console : rien à lire sans organisme.
    await page.goto("/uscomblement");
    await expect(page.getByTestId("superadmin-page")).toBeVisible();
    await expect(page.getByTestId("uscomblement-titre")).toHaveCount(0);
  });
});
