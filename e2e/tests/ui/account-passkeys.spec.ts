/**
 * Interface — Mon compte : gestion des clés d'accès FIDO2 / passkeys (exigence MIFI #93).
 *
 * Ce test porte sur le **comportement de l'écran** : la carte s'affiche, l'ajout fait apparaître
 * une clé et un message de succès, le retrait la fait disparaître. La persistance réelle et la
 * cérémonie WebAuthn du navigateur relèvent de la suite e2e et du déploiement.
 *
 * La doublure est **avec état** : la liste des clés reflète les ajouts et retraits, sinon
 * l'écran ne pourrait pas être exercé au-delà du premier clic.
 */
import { test, expect } from "./fixtures/app";
import { ouvrirNav } from "../e2e/helpers/nav";

test.describe("Mon compte — clés d'accès", () => {
  test("ajoute puis retire une clé d'accès", async ({ page, mock }) => {
    const cles: Array<{ id: string; label: string; createdAt: string }> = [];

    await mock({
      "GET /api/auth/webauthn/credentials": () => cles,
      "POST /api/auth/webauthn/register/begin": { challenge: "chal-demo" },
      "POST /api/auth/webauthn/register/finish": () => {
        cles.push({ id: `cred-${cles.length + 1}`, label: "Cet appareil", createdAt: "2026-08-05" });
        return { ok: true };
      },
      "DELETE /api/auth/webauthn/credentials/*": ({ url }) => {
        const id = url.pathname.split("/").pop();
        const i = cles.findIndex((c) => c.id === id);
        if (i >= 0) cles.splice(i, 1);
        return { ok: true };
      },
      "GET /api/auth/security/status": { securityMethod: "question", hasQuestions: true },
    });

    await page.goto("/");
    await ouvrirNav(page, "nav-account");
    // Mon compte est en onglets : les clés d'accès ont le leur.
    await page.getByTestId("account-tab-passkeys").click();

    const carte = page.getByTestId("account-passkeys");
    await expect(carte).toBeVisible();

    await page.getByTestId("account-passkey-add").click();
    await expect(page.getByTestId("account-passkey-item").first()).toBeVisible();
    await expect(page.getByTestId("account-success")).toContainText(/passkey enregistrée/i);

    await page.locator('[data-testid^="account-passkey-remove-"]').first().click();
    await expect(page.getByTestId("account-success")).toContainText(/passkey retirée/i);
    await expect(page.getByTestId("account-passkey-item")).toHaveCount(0);
  });
});
