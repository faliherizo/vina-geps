/**
 * **Jouer un essai contre la démonstration déployée** — l'hôte d'un organisme, la connexion d'un compte, le rythme des
 * écrans et la lecture du menu. Un seul endroit pour les essais `deploiement-*` qui visent un organisme semé.
 */
import { expect, request, type APIRequestContext, type Page } from "@playwright/test";
import comptesDemo from "../../../donnees/geps/comptes-demo.json";
import { menuPret } from "./nav";

export const DEPLOYE = !!process.env.E2E_BASE_URL;
export const SEMES = process.env.E2E_COMPTES_DEMO === "1";
export const MDP = process.env.E2E_DEMO_PASSWORD || comptesDemo.motDePasseParDefaut;
/** Laisse le seau de la limitation de débit se vider entre deux écrans (`E2E_PAUSE_MS`). */
export const PAUSE_MS = Number(process.env.E2E_PAUSE_MS ?? 2500);

export type OrganismeDemo = (typeof comptesDemo.organismes)[number];

export function organismeDemo(cle: string): OrganismeDemo {
  const o = comptesDemo.organismes.find((x) => x.cle === cle);
  if (!o) throw new Error(`organisme de démonstration inconnu : ${cle}`);
  return o;
}

/** L'hôte de l'organisme : `https://<sous-domaine>.<racine>` en déploiement par sous-domaine (ADR 0094), sinon l'adresse visée. */
export async function hoteDe(api: APIRequestContext, organisme: OrganismeDemo): Promise<string> {
  const d = await (await api.get(`${process.env.E2E_BASE_URL}/api/auth/deploiement`)).json();
  if (d.resolution !== "sous_domaine") return process.env.E2E_BASE_URL!;
  const u = new URL(process.env.E2E_BASE_URL!);
  u.hostname = `${organisme.sousDomaine}.${d.domaineRacine}`;
  return u.origin;
}

/** Se connecter par l'écran de connexion de l'hôte, qui affiche l'organisme sans le laisser choisir. */
export async function seConnecter(page: Page, hote: string, compte = "admin") {
  await page.goto(`${hote}/admin/login`);
  await expect(page.getByTestId("login-tenant-locked")).toBeVisible({ timeout: 30_000 });
  await page.getByTestId("login-user").fill(compte);
  await page.getByTestId("login-pass").fill(MDP);
  await page.getByTestId("login-submit").click();
  await expect(page.getByTestId("logout"), `connexion de ${compte}`).toBeVisible({ timeout: 60_000 });
  await menuPret(page);
}

/** Un contexte d'API au nom d'un compte de l'organisme, par son jeton : la lecture se juge sans passer par l'écran. */
export async function apiDe(hote: string, compte: string): Promise<APIRequestContext> {
  const anonyme = await request.newContext();
  const r = await anonyme.post(`${hote}/api/auth/login`, { data: { username: compte, password: MDP } });
  const { token } = await r.json();
  await anonyme.dispose();
  if (!token) throw new Error(`connexion de ${compte} refusée sur ${hote} (${r.status()})`);
  return request.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
}

/**
 * Ouvrir un écran au rythme d'une personne. Le frontal limite le débit par adresse (20 requêtes/s, rafale de 40 —
 * `frontend/nginx.conf`, MIFI #94) : un écran rechargé en appelle une vingtaine, et enchaîner les rechargements sans
 * pause faisait répondre 503 à la moitié des écrans. L'essai respecte ce contrôle de sécurité au lieu de le contourner.
 */
export async function allerA(page: Page, url: string) {
  await page.goto(url);
  await page.waitForLoadState("networkidle", { timeout: 30_000 }).catch(() => {});
  await page.waitForTimeout(PAUSE_MS);
}

/** Les vues du menu : les entrées, sans les rubriques ni les notions (`nav-access-octrois` est la vue `access:octrois`). */
export async function entreesDuMenu(page: Page): Promise<string[]> {
  const ids = await page.locator('nav [data-testid^="nav-"]').evaluateAll((els) => els.map((e) => e.getAttribute("data-testid") ?? ""));
  return [...new Set(ids.filter((i) => !i.startsWith("nav-group-") && !i.startsWith("nav-concept-"))
    .map((i) => i.slice(4).replace(/^access-/, "access:")))];
}
