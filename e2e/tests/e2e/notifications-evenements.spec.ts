/**
 * E2E — **avis d'événements et préférences par événement** (ADR 0029, demande du 16 sept. 2026) :
 * l'employée règle ses notifications dans Mon compte (matrice événement × canal, refus nommé d'un
 * essentiel sans canal écrit) ; l'administration l'avise par son nom depuis la page Notifications
 * et lit le compte rendu par canal ; le journal porte l'envoi avec son statut ; le courriel est
 * parti, identité et réglage compris.
 */
import { test, expect, type Browser } from "@playwright/test";
import { authedContext, uiLogin } from "./helpers/auth";
import { expectNoA11yViolations } from "./helpers/a11y";
import { ouvrirNav } from "./helpers/nav";
import { createTenant, selectTenant, setTenantModules, uniq, MODULES_SOCLE } from "./helpers/organisme";
import { choisirPersonne } from "./helpers/personne";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;

test("l'employée règle ses notifications ; l'administration l'avise par son nom ; le courriel en témoigne", async ({ browser }: { browser: Browser }) => {
  test.setTimeout(180_000);
  // Un établissement à lui : l'employée, son compte, et le module des notifications ; semé par l'administration système.
  const systeme = await authedContext();
  const tenantId = await createTenant(systeme, uniq("Avis"), "enterprise");
  // Les avis du module : sans les effectifs, le catalogue ne propose que le message de l'encadrement.
  await setTenantModules(systeme, tenantId, [...MODULES_SOCLE, "effectifs", "effectifs_regles", "effectifs_planification"]);
  const t = { "X-Tenant-Id": tenantId };
  const suffixe = Date.now().toString().slice(-6);
  // Sa fiche au répertoire du personnel, puis son compte rattaché à la fiche : c'est par la fiche qu'on l'avise par son nom.
  const emp = await (await systeme.post(`${API}/api/personnel/employes`, { headers: t, data: { firstName: "Léa", lastName: `Tremblay${suffixe}`, email: `lea-${suffixe}@css.qc`, phone: "418 555-0101" } })).json();
  expect(emp.id).toBeTruthy();
  const compte = await (await systeme.post(`${API}/api/auth/users`, { headers: t, data: { username: `lea-${suffixe}`, password: "pass1234", role: "employee", employeeId: emp.id } })).json();
  // Le compte est celui de l'établissement : le correctif des drapeaux de premier accès se fait sous le même établissement.
  await systeme.patch(`${API}/api/auth/users/${compte.id}`, { headers: t, data: { mustChangePassword: false, securitySetupRequired: false } });

  // 1) Mon compte › Mes notifications : la matrice, un refus nommé, puis un réglage enregistré.
  const ctxLea = await browser.newContext({ storageState: { cookies: [], origins: [] } });
  const lea = await ctxLea.newPage();
  await lea.goto("/");
  await uiLogin(lea, `lea-${suffixe}`, "pass1234", tenantId);
  await ouvrirNav(lea, "nav-account");
  await lea.getByTestId("account-tab-notifications").click();
  await expect(lea.getByTestId("account-canaux-matrice")).toBeVisible();
  await expect(lea.getByTestId("account-avis-message.direct-essentiel")).toBeVisible();
  await lea.getByTestId("account-avis-message.direct-courriel").uncheck();
  await lea.getByTestId("account-avis-message.direct-texto").uncheck();
  await lea.getByTestId("account-canaux-save").click();
  await expect(lea.getByTestId("account-canaux-refus")).toContainText("Message de l'encadrement");
  await expectNoA11yViolations(lea);
  await lea.getByTestId("account-avis-message.direct-courriel").check();
  await lea.getByTestId("account-avis-quart.offert-poussee").uncheck();
  await lea.getByTestId("account-canaux-save").click();
  await expect(lea.getByTestId("account-canaux-refus")).toHaveCount(0);
  await expect(lea.getByTestId("account-success")).toContainText("enregistrées");
  const prefs = await (await ctxLea.request.get(`${API}/api/notifications/preferences`)).json();
  expect(prefs.evenements["message.direct"]).toEqual({ courriel: true, texto: false, poussee: true });
  expect(prefs.evenements["quart.offert"]).toEqual({ courriel: true, texto: true, poussee: false });
  await ctxLea.close();

  // 2) Notifications › Notifier une personne (l'administration de l'établissement) : par le nom, tous les canaux demandés — le texto est « désactivé ».
  const ctxAdmin = await browser.newContext({ storageState: { cookies: [], origins: [] } });
  const page = await ctxAdmin.newPage();
  await selectTenant(page, tenantId);
  await ouvrirNav(page, "nav-notifications");
  await expect(page.getByTestId("notif-personne")).toBeVisible();
  await choisirPersonne(page, "notif-personne-cible", `Tremblay${suffixe}`);
  await page.getByTestId("notif-personne-sujet").fill("Réunion d'équipe jeudi");
  await page.getByTestId("notif-personne-corps").fill("Bonjour {{prenom}}, réunion d'équipe jeudi à 9 h.");
  await page.getByTestId("notif-personne-envoyer").click();
  const resultat = page.getByTestId("notif-personne-resultat");
  await expect(resultat).toContainText(`Léa Tremblay${suffixe} — courriel envoyé`);
  await expect(resultat).toContainText("texto désactivé (fermé par la personne)");
  await expectNoA11yViolations(page);
  // Le courriel est parti à l'adresse de la fiche, le prénom substitué.
  const outbox = await (await systeme.get(`${API}/api/auth/_outbox`)).json();
  const mail = [...outbox].reverse().find((m: { to: string }) => m.to === `lea-${suffixe}@css.qc`);
  expect(mail, "courriel envoyé à l'employée").toBeTruthy();
  expect(mail.text).toContain("Bonjour Léa, réunion d'équipe jeudi à 9 h.");
  expect(mail.subject).toMatch(/Réunion d'équipe jeudi$/);
  await ctxAdmin.close();

  await systeme.dispose();
});
