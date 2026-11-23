/**
 * Interface — connexion en **déploiement par sous-domaine** (ADR 0033). Le serveur dit ce que
 * l'hôte désigne (`GET /api/auth/deploiement`) ; l'écran n'a plus de sélecteur : l'établissement
 * est verrouillé, la console des comptes globaux se nomme, un hôte inconnu ne propose rien —
 * et aucun `tenantId` ne part au serveur. Chaque état passe à axe.
 */
import { test, expect } from "./fixtures/app";
import { expectNoA11yViolations } from "../e2e/helpers/a11y";

const DECONNECTE = { "GET /api/auth/me": () => ({ status: 401, body: { errors: ["non authentifié"] } }) };
const RACINE = "nexus-vinaitsolution.com";

test("cssdc. : l'établissement est verrouillé, la liste n'est pas demandée, le corps de la connexion ne porte aucun tenant", async ({ page, mock }) => {
  const corps: unknown[] = [];
  let listeDemandee = false;
  await mock({
    ...DECONNECTE,
    "GET /api/auth/deploiement": { resolution: "sous_domaine", domaineRacine: RACINE, hote: "etablissement", etablissement: { id: "tnt-cssdc", nom: "CSS des Chics-Chocs", slug: "cssdc" } },
    "GET /api/auth/tenants": () => { listeDemandee = true; return { status: 404, body: { errors: ["aucun sélecteur"] } }; },
    "POST /api/auth/login": ({ body }) => { corps.push(body); return { status: 401, body: { errors: ["invalid credentials"] } }; },
  });
  await page.goto("/");
  await expect(page.getByTestId("login-tenant-locked")).toBeVisible();
  await expect(page.getByTestId("login-tenant-locked").locator("input")).toHaveValue("CSS des Chics-Chocs");
  await expect(page.getByTestId("login-tenant")).toHaveCount(0);
  await expectNoA11yViolations(page);
  await page.getByTestId("login-user").fill("admin");
  await page.getByTestId("login-pass").fill("secret");
  await page.getByTestId("login-submit").click();
  await expect(page.getByTestId("login-error")).toBeVisible();
  expect(corps).toHaveLength(1);
  expect((corps[0] as Record<string, unknown>).tenantId).toBeUndefined();
  expect(listeDemandee).toBe(false);
});

test("admin. : la console des comptes globaux, sans sélecteur", async ({ page, mock }) => {
  await mock({ ...DECONNECTE, "GET /api/auth/deploiement": { resolution: "sous_domaine", domaineRacine: RACINE, hote: "global", etablissement: null } });
  await page.goto("/");
  await expect(page.getByTestId("login-comptes-globaux")).toBeVisible();   // le libellé suit la langue du navigateur
  await expect(page.getByTestId("login-tenant")).toHaveCount(0);
  // Le compte d'un organisme ne s'ouvre pas ici : l'écran mène au choix de l'organisme, où chacun a son adresse.
  await expect(page.getByTestId("login-vers-organismes")).toHaveAttribute("href", "/acceder");
  await expect(page.getByTestId("login-submit")).toBeEnabled();
  await expectNoA11yViolations(page);
});

test("un sous-domaine inconnu : l'écran le dit, le bouton est inerte", async ({ page, mock }) => {
  await mock({ ...DECONNECTE, "GET /api/auth/deploiement": () => ({ status: 404, body: { resolution: "sous_domaine", domaineRacine: RACINE, hote: "inconnu", etablissement: null, errors: ["organisme inconnu ou fermé"] } }) });
  await page.goto("/");
  await expect(page.getByTestId("login-tenant-inconnu")).toBeVisible();
  await expect(page.getByTestId("login-tenant-inconnu")).toHaveAttribute("role", "alert");
  await expect(page.getByTestId("login-submit")).toBeDisabled();
  await expectNoA11yViolations(page);
});

test("mode sélecteur (défaut) : la liste est demandée et l'établissement choisi part avec la connexion", async ({ page, mock }) => {
  const corps: unknown[] = [];
  await mock({ ...DECONNECTE, "POST /api/auth/login": ({ body }) => { corps.push(body); return { status: 401, body: { errors: ["invalid credentials"] } }; } });
  await page.goto("/");
  await page.getByTestId("login-tenant").selectOption("tnt-demo");
  await page.getByTestId("login-user").fill("admin");
  await page.getByTestId("login-pass").fill("secret");
  await page.getByTestId("login-submit").click();
  await expect(page.getByTestId("login-error")).toBeVisible();
  expect((corps[0] as Record<string, unknown>).tenantId).toBe("tnt-demo");
});
