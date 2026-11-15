/**
 * Interface — bouton **SAML 2.0** de la page de connexion (grille scolaire, exigence 41).
 *
 * Le bouton dépend de `GET /api/auth/sso/saml/status` : présent quand un IdP est déclaré,
 * absent sinon. Un bouton menant à un 503 serait pire que pas de bouton — c'est ce que le
 * second cas verrouille.
 */
import AxeBuilder from "@axe-core/playwright";
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

test("annuaire LDAPS (critère 82) : le bouton envoie l'identifiant et le mot de passe saisis, sans redirection ; sans eux, il le dit", async ({ page, mock }) => {
  const envois: unknown[] = [];
  await mock({
    ...DECONNECTE,
    "GET /api/auth/deploiement": { resolution: "sous_domaine", domaineRacine: "geps.ca", hote: "etablissement", etablissement: { id: "T1", nom: "Hôpital de Saint-Jérôme", slug: "stj" } },
    "GET /api/auth/sso/annuaires": { annuaires: [{ id: "ann-ad", nom: "AD local", fournisseur: "ldaps" }, { id: "ann-k", nom: "Keycloak", fournisseur: "oidc" }] },
    "POST /api/auth/sso/annuaires/ann-ad/connexion": ({ body }: { body: unknown }) => {
      envois.push(body);
      return { status: 403, body: { errors: ["identifiant ou mot de passe refusé par l'annuaire « AD local »"] } };
    },
  });
  // WCAG 2.2 AA dans chaque état : la page et ses annuaires, puis le refus affiché.
  const axe = async () => (await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"]).analyze()).violations
    .map((v) => `${v.id} : ${v.nodes.map((n) => n.target.join(" ")).join(", ")}`);
  await page.goto("/");
  const ad = page.getByTestId("login-annuaire-ann-ad");
  await expect(ad).toBeVisible();
  expect(await axe()).toEqual([]);
  await expect(ad).toHaveAccessibleName(/annuaire AD local avec l'identifiant et le mot de passe/);
  await expect(page.getByTestId("login-annuaire-ann-k")).toHaveAttribute("href", "/api/auth/sso/annuaires/ann-k/login");
  await ad.click();
  await expect(page.getByText("Saisissez d'abord votre identifiant et votre mot de passe de l'annuaire.")).toBeVisible();
  expect(envois).toHaveLength(0);
  await page.getByTestId("login-user").fill("mroy");
  await page.getByTestId("login-pass").fill("mauvais");
  await ad.click();
  await expect(page.getByText("identifiant ou mot de passe refusé par l'annuaire « AD local »")).toBeVisible();
  expect(envois).toEqual([{ identifiant: "mroy", motDePasse: "mauvais" }]);
  expect(await axe()).toEqual([]);
});
