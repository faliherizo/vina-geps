/**
 * Interface — bouton **SAML 2.0** de la page de connexion (grille scolaire, exigence 41).
 *
 * Le bouton dépend de `GET /api/auth/sso/saml/status` : présent quand un IdP est déclaré,
 * absent sinon. Un bouton menant à un 503 serait pire que pas de bouton — c'est ce que le
 * second cas verrouille.
 */
import { test, expect } from "./fixtures/app";

const DECONNECTE = { "GET /api/auth/me": () => ({ status: 401, body: { errors: ["non authentifié"] } }) };

test("l'IdP déclaré : le bouton SAML mène au démarrage de la connexion", async ({ page, mock }) => {
  await mock({ ...DECONNECTE, "GET /api/auth/sso/saml/status": { enabled: true, provider: "SAML 2.0", entityId: "nexu", acsUrl: "/api/auth/sso/saml/acs" } });
  await page.goto("/");
  await expect(page.getByTestId("login-form")).toBeVisible();
  const saml = page.getByTestId("login-sso-saml");
  await expect(saml).toBeVisible();
  await expect(saml).toHaveAttribute("href", "/api/auth/sso/saml/login");
  await expect(saml).toHaveAccessibleName(/SAML/);
});

test("aucun IdP : ni bouton SAML, ni promesse", async ({ page, mock }) => {
  await mock({ ...DECONNECTE, "GET /api/auth/sso/saml/status": { enabled: false, provider: "SAML 2.0", entityId: "", acsUrl: "" } });
  await page.goto("/");
  await expect(page.getByTestId("login-form")).toBeVisible();
  await expect(page.getByTestId("login-sso-entra")).toBeVisible();
  await expect(page.getByTestId("login-sso-saml")).toHaveCount(0);
});

test("Entra réel : le bouton Microsoft envoie le navigateur chez l'IdP, il ne poste pas une session", async ({ page, mock }) => {
  await mock({
    ...DECONNECTE,
    "GET /api/auth/sso/entra/status": { enabled: true, mock: false, provider: "Microsoft Entra ID" },
    "GET /api/auth/sso/saml/status": { enabled: false, provider: "SAML 2.0", entityId: "", acsUrl: "" },
    // Le départ du flux est une **navigation** vers le serveur, qui redirige chez l'IdP.
    "GET /api/auth/sso/entra/login": { redirection: "idp" },
  });
  await page.goto("/");
  await expect(page.getByTestId("login-form")).toBeVisible();
  const postes: string[] = [];
  page.on("request", (r) => { if (r.method() === "POST") postes.push(new URL(r.url()).pathname); });
  await page.getByTestId("login-sso-entra").click();
  await page.waitForURL(/\/api\/auth\/sso\/entra\/login$/);
  expect(postes).not.toContain("/api/auth/sso/entra/session");
});
