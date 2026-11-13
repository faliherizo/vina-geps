/**
 * Interface — textos et canaux de notification (grille 25, devis 2.6).
 *
 * La carte « Textos » de la page Notifications dit le fournisseur en service (rouge quand ce
 * n'est que le simulateur), le quota du mois, refuse un numéro ou un texte invalide avant
 * l'envoi, remet un texto valide et le fait apparaître au journal, redemande un statut. Sur
 * Mon compte, la personne règle ses canaux, et ne peut pas fermer les deux canaux écrits.
 *
 * Les doublures sont **avec état** : le journal reflète l'envoi et le rafraîchissement.
 */
import { test, expect, DEFAULT_USER } from "./fixtures/app";
import { ouvrirNav } from "../e2e/helpers/nav";

const texto = (id: number, statut: string, extra: Record<string, unknown> = {}) => ({
  id, numero: "••••••••1234", texte: "Bonjour", statut, fournisseur: "twilio", identifiantFournisseur: `SM${id}`,
  creeLe: "2026-09-14T12:00:00.000Z", envoyeLe: null, erreur: null, contexte: "sollicitation-1", expediteur: "+15145550100", ...extra,
});

test.describe("Notifications — textos", () => {
  test("simulateur en rouge, quota, refus à l'écran, envoi, rafraîchissement", async ({ page, mock }) => {
    const journal = [texto(7, "envoye"), texto(6, "echoue", { erreur: "Twilio : 400 (code 21211) numéro injoignable", identifiantFournisseur: null })];
    let utilises = 2;
    await mock({
      "GET /api/notifications/sms/etat": { fournisseur: "simule", simule: true, manquantes: ["TWILIO_ACCOUNT_SID"], rappel: null, appel: "simule" },
      "GET /api/notifications/sms/quota": () => ({ mois: "2026-09", limite: 10, utilises, restants: 10 - utilises, illimite: false, longueurMax: 480 }),
      "GET /api/notifications/sms/resume": () => ({ total: journal.length, parStatut: { en_attente: journal.filter((t) => t.statut === "en_attente").length, envoye: journal.filter((t) => t.statut === "envoye").length, livre: journal.filter((t) => t.statut === "livre").length, echoue: journal.filter((t) => t.statut === "echoue").length }, dernierLe: null }),
      "GET /api/notifications/sms": () => journal,
      "POST /api/notifications/sms": ({ body }) => {
        const b = body as { numero: string; texte: string };
        utilises += 1;
        journal.unshift(texto(8, "en_attente", { numero: `••••••••${b.numero.slice(-4)}`, texte: b.texte, contexte: null }));
        return { status: 201, body: journal[0] };
      },
      "POST /api/notifications/sms/8/rafraichir": () => { journal[0] = { ...journal[0], statut: "livre" }; return journal[0]; },
    });

    await page.goto("/");
    await ouvrirNav(page, "nav-notifications");
    const carte = page.getByTestId("textos-carte");
    await expect(carte).toBeVisible();
    await expect(page.getByTestId("textos-fournisseur")).toHaveText(/Simulateur/);
    await expect(page.getByTestId("textos-fournisseur")).toHaveClass(/badge-terminated/);
    await expect(page.getByTestId("textos-manquantes")).toContainText("TWILIO_ACCOUNT_SID");
    await expect(page.getByTestId("textos-quota")).toHaveText("2 sur 10 texto(s) en septembre 2026 — 8 restant(s).");
    await expect(page.getByTestId("textos-resume")).toHaveText("2 textos au journal — 1 envoyé, 1 échoué.");
    await expect(page.getByTestId("textos-ligne-6")).toContainText("21211");
    await expect(page.getByTestId("textos-statut-6")).toHaveText("Échoué");
    // L'établissement ne règle pas sa propre limite.
    await expect(page.getByTestId("textos-quota-form")).toHaveCount(0);

    // Refusé avant d'être envoyé.
    await page.getByTestId("textos-numero").fill("12345");
    await page.getByTestId("textos-texte").fill("Bonjour");
    await page.getByTestId("textos-envoyer").click();
    await expect(page.getByTestId("textos-refus")).toContainText("Numéro invalide");
    await expect(page.getByTestId("textos-ligne-8")).toHaveCount(0);

    await page.getByTestId("textos-numero").fill("+15145559876");
    await page.getByTestId("textos-envoyer").click();
    await expect(page.getByTestId("notifications-success")).toContainText("remis au fournisseur");
    await expect(page.getByTestId("textos-ligne-8")).toContainText("••••••••9876");
    await expect(page.getByTestId("textos-statut-8")).toHaveText("En attente");
    await expect(page.getByTestId("textos-quota")).toHaveText(/3 sur 10/);

    await page.getByTestId("textos-rafraichir-8").click();
    await expect(page.getByTestId("textos-statut-8")).toHaveText("Livré");
    await expect(page.getByTestId("notifications-success")).toContainText("Livré");
  });

  test("l'administration du système règle la limite mensuelle ; le quota atteint bloque l'envoi", async ({ page, mock }) => {
    let limite = 5;
    await mock({
      "GET /api/auth/me": { ...DEFAULT_USER, tenantId: null },
      "GET /api/auth/tenants": [{ id: "tnt-demo", name: "École de démonstration", status: "active" }],
      "GET /api/notifications/sms/etat": { fournisseur: "twilio", simule: false, manquantes: [], rappel: "https://api/x", appel: "twilio" },
      "GET /api/notifications/sms/quota": () => ({ mois: "2026-09", limite, utilises: 5, restants: limite - 5, illimite: false }),
      "PUT /api/notifications/sms/quota": ({ body }) => { limite = (body as { limite: number }).limite; return { mois: "2026-09", limite, utilises: 5, restants: limite - 5, illimite: limite === 0 }; },
    });
    // Un administrateur du système administre l'établissement qu'il a choisi (mémorisé par le navigateur).
    await page.addInitScript(() => localStorage.setItem("establishmentId", "tnt-demo"));
    await page.goto("/");
    await ouvrirNav(page, "nav-notifications");
    await expect(page.getByTestId("textos-fournisseur")).toHaveClass(/badge-active/);
    await expect(page.getByTestId("textos-quota")).toContainText("Quota mensuel atteint");
    await expect(page.getByTestId("textos-envoyer")).toBeDisabled();

    await page.getByTestId("textos-limite").fill("50");
    await page.getByTestId("textos-limite-enregistrer").click();
    await expect(page.getByTestId("notifications-success")).toContainText("Limite mensuelle enregistrée");
    await expect(page.getByTestId("textos-quota")).toHaveText("5 sur 50 texto(s) en septembre 2026 — 45 restant(s).");
    await expect(page.getByTestId("textos-envoyer")).toBeEnabled();
  });
});

test.describe("Mon compte — canaux de notification", () => {
  test("règle ses canaux, sans pouvoir fermer les deux canaux écrits", async ({ page, mock }) => {
    let canaux = { courriel: true, texto: true, poussee: true, updatedAt: "2026-09-14T00:00:00.000Z" };
    await mock({
      "GET /api/notifications/preferences": () => canaux,
      "PUT /api/notifications/preferences": ({ body }) => { canaux = { ...canaux, ...(body as object) }; return canaux; },
      "GET /api/auth/security/status": { securityMethod: "question", hasQuestions: true },
    });
    await page.goto("/");
    await ouvrirNav(page, "nav-account");
    // Mon compte est en onglets : les canaux de notification ont le leur.
    await page.getByTestId("account-tab-notifications").click();
    await expect(page.getByTestId("account-canaux")).toBeVisible();
    await expect(page.getByTestId("account-canal-texto")).toBeChecked();

    await page.getByTestId("account-canal-courriel").uncheck();
    await page.getByTestId("account-canal-texto").uncheck();
    await page.getByTestId("account-canaux-save").click();
    await expect(page.getByTestId("account-canaux-refus")).toContainText("Au moins un canal écrit");
    expect(canaux.courriel).toBe(true);

    await page.getByTestId("account-canal-courriel").check();
    await page.getByTestId("account-canaux-save").click();
    await expect(page.getByTestId("account-success")).toContainText("Vos notifications sont enregistrées.");
    expect(canaux).toMatchObject({ courriel: true, texto: false, poussee: true });
  });
});
