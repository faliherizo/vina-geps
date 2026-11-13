/**
 * E2E : flux première connexion (changement mot de passe + configuration sécurité).
 */
import { test, expect } from "@playwright/test";
import { GLOBAL_ACCOUNT } from "./helpers/auth";
import { request as pwRequest } from "@playwright/test";
import * as crypto from "crypto";
import * as fs from "fs";
import * as path from "path";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;

// Pas de session admin par défaut : on veut tester l'employé qui se connecte.
test.use({ storageState: { cookies: [], origins: [] } });

async function adminApi() {
  const storagePath = path.join(__dirname, "..", "..", ".auth", "admin.json");
  const state = JSON.parse(fs.readFileSync(storagePath, "utf8"));
  const csrf = state.cookies?.find((c: any) => c.name === "csrf")?.value ?? "";
  return pwRequest.newContext({
    storageState: storagePath,
    extraHTTPHeaders: csrf ? { "X-CSRF-Token": csrf } : {},
  });
}

function base32Decode(s: string): Buffer {
  const alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  const clean = s.replace(/=+$/g, "").toUpperCase();
  let bits = "";
  for (const c of clean) {
    const v = alpha.indexOf(c); if (v < 0) continue;
    bits += v.toString(2).padStart(5, "0");
  }
  const out: number[] = [];
  for (let i = 0; i + 8 <= bits.length; i += 8) out.push(parseInt(bits.slice(i, i + 8), 2));
  return Buffer.from(out);
}
function totpCode(secret: string, atMs = Date.now()): string {
  const counter = Math.floor(atMs / 1000 / 30);
  const buf = Buffer.alloc(8);
  buf.writeBigUInt64BE(BigInt(counter));
  const h = crypto.createHmac("sha1", base32Decode(secret)).update(buf).digest();
  const offset = h[h.length - 1] & 0x0f;
  const bin = ((h[offset] & 0x7f) << 24) | ((h[offset + 1] & 0xff) << 16) | ((h[offset + 2] & 0xff) << 8) | (h[offset + 3] & 0xff);
  return (bin % 1_000_000).toString().padStart(6, "0");
}

test("First-login : change password puis question secrète", async ({ page, browser }) => {
  // 1. L'administration crée un compte avec un mot de passe provisoire
  const adminCtx = await adminApi();
  const unique = `sec${Date.now().toString().slice(-7)}`;
  const r = await adminCtx.post(`${API}/api/auth/users`, {
    data: { username: unique, password: "Temporaire-2026", role: "employee" },
  });
  expect(r.status()).toBe(201);
  const tempPwd = "Temporaire-2026";
  await adminCtx.dispose();

  // 2. L'employé se connecte via le formulaire UI → écran FirstLogin attendu
  await page.goto("/");
  await page.getByTestId("login-user").fill(unique);
  await page.getByTestId("login-pass").fill(tempPwd);
  await page.getByTestId("login-tenant").selectOption(GLOBAL_ACCOUNT);
  await page.getByTestId("login-submit").click();
  await expect(page.getByTestId("first-login")).toBeVisible({ timeout: 10000 });

  // 3. Change mot de passe
  await page.getByTestId("first-login-newpwd").fill("NouveauPwd!2025");
  await page.getByTestId("first-login-newpwd2").fill("NouveauPwd!2025");
  await page.getByTestId("first-login-pwd-submit").click();

  // 4. Étape sécurité : configurer au moins 3 questions secrètes distinctes (politique MIN_SECURITY_QUESTIONS).
  await page.getByTestId("sec-method-question").check();
  // Les 3 menus déroulants sont pré-remplis avec des questions distinctes ; on sélectionne
  // explicitement des options distinctes puis on saisit une réponse pour chacune.
  await page.getByTestId("sec-question-select").selectOption({ index: 1 });
  await page.getByTestId("sec-answer").fill("Rex");
  await page.getByTestId("sec-question-select-1").selectOption({ index: 2 });
  await page.getByTestId("sec-answer-1").fill("Tremblay");
  await page.getByTestId("sec-question-select-2").selectOption({ index: 3 });
  await page.getByTestId("sec-answer-2").fill("Montréal");
  await page.getByTestId("first-login-sec-submit").click();

  // 5. Application principale visible
  await expect(page.getByTestId("nav-dashboard")).toHaveCount(1, { timeout: 5000 });

  // 6. Déconnexion puis reconnexion avec une **mauvaise** réponse de sécurité :
  //    un message d'erreur explicite doit s'afficher (et non un écran vide).
  await page.getByTestId("logout").click();
  await expect(page.getByTestId("login-form")).toBeVisible({ timeout: 10000 });
  await page.getByTestId("login-user").fill(unique);
  await page.getByTestId("login-pass").fill("NouveauPwd!2025");
  await page.getByTestId("login-tenant").selectOption(GLOBAL_ACCOUNT);
  await page.getByTestId("login-submit").click();
  // L'écran demande les questions de sécurité (toutes requises). On répond faux aux trois.
  await expect(page.getByTestId("login-mfa-answer")).toBeVisible({ timeout: 10000 });
  await page.getByTestId("login-mfa-answer").fill("faux-1");
  await page.getByTestId("login-mfa-answer-1").fill("faux-2");
  await page.getByTestId("login-mfa-answer-2").fill("faux-3");
  await page.getByTestId("login-tenant").selectOption(GLOBAL_ACCOUNT);
  await page.getByTestId("login-submit").click();
  await expect(page.getByTestId("login-error")).toContainText(/recommencer/i);
});

test("First-login : change password puis MFA TOTP", async ({ page, browser }) => {
  const adminCtx = await adminApi();
  const unique = `mfa${Date.now().toString().slice(-7)}`;
  const r = await adminCtx.post(`${API}/api/auth/users`, {
    data: { username: unique, password: "Temporaire-2026", role: "employee" },
  });
  expect(r.status()).toBe(201);
  const tempPwd = "Temporaire-2026";
  await adminCtx.dispose();

  await page.goto("/");
  await page.getByTestId("login-user").fill(unique);
  await page.getByTestId("login-pass").fill(tempPwd);
  await page.getByTestId("login-tenant").selectOption(GLOBAL_ACCOUNT);
  await page.getByTestId("login-submit").click();
  await expect(page.getByTestId("first-login")).toBeVisible({ timeout: 10000 });
  await page.getByTestId("first-login-newpwd").fill("NouveauPwd!2025");
  await page.getByTestId("first-login-newpwd2").fill("NouveauPwd!2025");
  await page.getByTestId("first-login-pwd-submit").click();

  await page.getByTestId("sec-method-totp").check();
  await page.getByTestId("sec-totp-init").click();
  // Le secret est affiché ; on le récupère puis calcule le code via une requête backend dédiée test
  const secret = await page.getByTestId("sec-totp-secret").textContent();
  expect(secret && secret.length > 8).toBeTruthy();
  // Calcul du code via implémentation locale
  const totp = totpCode(secret!.trim());
  await page.getByTestId("sec-totp-code").fill(totp);
  await page.getByTestId("first-login-sec-submit").click();

  await expect(page.getByTestId("nav-dashboard")).toHaveCount(1, { timeout: 5000 });
});
