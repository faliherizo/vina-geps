import { Page, BrowserContext, request as pwRequest, APIRequestContext } from "@playwright/test";
import fs from "fs";
import path from "path";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;

/**
 * Login via cookie : exécute /api/auth/login dans le contexte de la page (ou du browser context),
 * ce qui pose les cookies `session` httpOnly + `csrf` directement sur la session navigateur.
 * Aucun token n'est jamais stocké en localStorage côté web.
 */
export async function loginAs(page: Page, username = "admin", password = "admin") {
  const r = await page.request.post(`${API}/api/auth/login`, { data: { username, password } });
  if (!r.ok()) throw new Error(`Login failed for ${username}: ${r.status()}`);
  return await r.json();
}

/**
 * Lève les exigences de **première connexion** d'un compte fraîchement créé.
 *
 * Tout compte naît avec `mustChangePassword` et `securitySetupRequired` : c'est voulu, et
 * l'onboarding est couvert par ses propres tests. Un test qui vise autre chose — l'écran
 * d'approbation, une liste, une modale — se retrouverait sinon bloqué sur cet accueil.
 */
export async function clearFirstLoginFlags(ctx: APIRequestContext, userId: string): Promise<void> {
  await ctx.patch(`${API}/api/auth/users/${userId}`, {
    data: { mustChangePassword: false, securitySetupRequired: false },
  });
}

/** Valeur du sélecteur d'établissement pour un compte global (admin système, superadmin). */
export const GLOBAL_ACCOUNT = "__global__";

/**
 * Login **par le formulaire**, sélecteur d'établissement compris.
 *
 * `loginAs` court-circuite l'écran de connexion en appelant l'API : c'est le bon outil quand
 * le test porte sur autre chose. Dès qu'un test passe réellement par le formulaire, il doit
 * désigner un établissement — le champ est **obligatoire** (« dans la vraie vie, les tenants
 * ont leur propre URL »), et le soumettre vide laisse l'utilisateur sur la page de connexion.
 *
 * Par défaut on choisit le compte global, qui correspond à `admin`/`superadmin`.
 */
export async function uiLogin(
  page: Page,
  username = "admin",
  password = "admin",
  tenantId: string = GLOBAL_ACCOUNT,
) {
  await page.getByTestId("login-user").fill(username);
  await page.getByTestId("login-pass").fill(password);
  // L'établissement peut être déjà imposé (URL dédiée) : le sélecteur est alors absent.
  const tenant = page.getByTestId("login-tenant");
  if (await tenant.count()) await tenant.selectOption(tenantId);
  await page.getByTestId("login-submit").click();
}

/** Variante : pose les cookies sur un contexte navigateur (utile avant page.goto). */
export async function loginContext(context: BrowserContext, username = "admin", password = "admin") {
  const r = await context.request.post(`${API}/api/auth/login`, { data: { username, password } });
  if (!r.ok()) throw new Error(`Login failed for ${username}: ${r.status()}`);
  return await r.json();
}

/** Login API only — retourne le token JWT (utile pour APIs ne supportant que Bearer, ex: scripts CLI). */
export async function apiLogin(username = "admin", password = "admin"): Promise<string> {
  const ctx = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
  const r = await ctx.post(`${API}/api/auth/login`, { data: { username, password } });
  const j = await r.json();
  await ctx.dispose();
  return j.token;
}

/**
 * Contexte API **superadmin** (administration système : création de tenants + admins).
 * Utilise un token Bearer → pas de cookie session, donc pas de double-submit CSRF requis.
 */
export async function superadminContext(): Promise<APIRequestContext> {
  const token = await apiLogin("superadmin", "superadmin");
  // Cookie jar **vide** explicite : sinon le storageState global (admin.json) injecte un cookie
  // csrf → la protection CSRF rejette les mutations (Bearer seul, sans cookie, la contourne).
  return pwRequest.newContext({ storageState: { cookies: [], origins: [] }, extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
}

/**
 * Crée un APIRequestContext anonyme (sans cookies) — utile pour exécuter des requêtes
 * en bypass CSRF (le middleware ne déclenche pas si pas de cookie session).
 */
export async function anonymousContext(): Promise<APIRequestContext> {
  return pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
}

/**
 * Crée un APIRequestContext authentifié via cookie (admin), avec X-CSRF-Token automatiquement
 * injecté en extraHTTPHeaders pour les mutations. Utilise le storageState global déjà capturé.
 */
export async function authedContext(): Promise<APIRequestContext> {
  const storagePath = path.join(__dirname, "..", "..", "..", ".auth", "admin.json");
  let csrf = "";
  try {
    const state = JSON.parse(fs.readFileSync(storagePath, "utf8"));
    csrf = state.cookies?.find((c: any) => c.name === "csrf")?.value ?? "";
  } catch { /* ignore */ }
  return pwRequest.newContext({ extraHTTPHeaders: csrf ? { "X-CSRF-Token": csrf } : {} });
}

export async function resetAndProvision() {
  const ctx = await anonymousContext();
  await ctx.post(`${API}/api/auth/_reset`);
  await ctx.dispose();
}

/**
 * Force le **français** dans le navigateur d'essai : Chromium se présente en `en-US`, et les
 * écrans traduits (FR/EN/MG) suivraient cette langue — les essais qui lisent un texte français
 * doivent le dire. Posé avant toute navigation, rejoué à chaque chargement de page.
 */
export async function forcerFrancais(page: import("@playwright/test").Page): Promise<void> {
  await page.addInitScript(() => { try { localStorage.setItem("lang", "fr"); } catch { /* stockage indisponible : la langue du navigateur s'applique */ } });
}
