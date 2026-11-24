/**
 * Interface — l'icône « Messages non lus » de la barre du haut.
 *
 * Le panneau menait à « sqmessages », un écran retiré du produit : toute l'application devenait blanche, et seulement
 * quand un message attendait. Chaque essai part donc d'un message non lu et va jusqu'au fil ouvert.
 */
import { test, expect, DEFAULT_MODULES, DEFAULT_USER } from "./fixtures/app";
import { auditA11y } from "../e2e/helpers/a11y";

const fil = (id: string, sujet: string) => ({ id, sujet, type: "groupe", creeLe: "2026-11-20T12:00:00Z", archiveeLe: null,
  participants: [{ personId: "usr-demo", depuis: "2026-11-20T12:00:00Z", jusqua: null }, { personId: "julie.roy", depuis: "2026-11-20T12:00:00Z", jusqua: null }] });
const nonLu = (conversationId: string, sujet: string) => ({ conversationId, sujet, nonLus: 1,
  dernierMessageLe: "2026-11-23T12:00:00Z", dernierAuteurId: "julie.roy", dernierExtrait: `Extrait ${conversationId}` });
const message = (id: string, corps: string) => ({ id, auteurId: "julie.roy", corps, envoyeLe: "2026-11-23T12:00:00Z",
  priveA: null, repondAuMessageId: null, luParMoi: false, lecteurs: [], pieces: [], reactions: [] });
const DOUBLURES = {
  "GET /api/communication/conversations/non-lus": { total: 2, conversations: [nonLu("cv-1", "Quart de nuit"), nonLu("cv-2", "Véhicule 412")] },
  "GET /api/communication/conversations": [fil("cv-1", "Quart de nuit"), fil("cv-2", "Véhicule 412")],
  "GET /api/communication/conversations/cv-1/messages": [message("m-1", "Qui prend la nuit ?")],
  "GET /api/communication/conversations/cv-2/messages": [message("m-2", "Le 412 est en réparation.")],
};

for (const depart of ["dashboard", "usmessages", "account"]) {
  test(`depuis ${depart}, un message non lu ouvre la messagerie sur son fil — jamais une page blanche`, async ({ page, mock }) => {
    const erreurs: string[] = [];
    page.on("pageerror", (e) => erreurs.push(e.message));
    await mock(DOUBLURES);
    await page.goto(`/${depart}`);
    await expect(page.getByTestId("avis-messages-compte")).toHaveText("2");
    await page.getByTestId("avis-messages-bouton").click();
    expect((await auditA11y(page)).map((v) => v.id), "axe-core : panneau ouvert").toEqual([]);
    await page.getByTestId("avis-message-cv-2").click();

    await expect(page.locator("main h1")).toHaveText("Messagerie du personnel");
    await expect.poll(() => new URL(page.url()).pathname).toBe("/usmessages");
    await expect(page.getByTestId("sq-msg-fil")).toHaveValue("cv-2");
    await expect(page.getByTestId("sq-msg-table")).toContainText("Le 412 est en réparation.");

    // L'autre fil, la messagerie déjà ouverte : le fil change, sans rechargement de la page.
    await page.getByTestId("avis-messages-bouton").click();
    await page.getByTestId("avis-message-cv-1").click();
    await expect(page.getByTestId("sq-msg-table")).toContainText("Qui prend la nuit ?");
    expect(erreurs).toEqual([]);
  });
}

test("au clavier et sur un téléphone, le message mène au même fil", async ({ page, mock }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await mock(DOUBLURES);
  await page.goto("/dashboard");
  await page.getByTestId("avis-messages-bouton").focus();
  await page.keyboard.press("Enter");
  await page.getByTestId("avis-message-cv-1").focus();
  await page.keyboard.press("Enter");
  await expect(page.getByTestId("sq-msg-table")).toContainText("Qui prend la nuit ?");
});

test("sans écran de messagerie, aucune icône : elle ne mènerait nulle part", async ({ page, mock }) => {
  await mock({ ...DOUBLURES, "GET /api/auth/me": DEFAULT_USER,
    "GET /api/saas/my-modules": { modules: DEFAULT_MODULES.filter((m) => m !== "effectifs_communication"), planCode: "enterprise" } });
  await page.goto("/dashboard");
  await expect(page.locator("main h1").first()).toBeVisible();
  await expect(page.getByTestId("avis-messages")).toHaveCount(0);
});
