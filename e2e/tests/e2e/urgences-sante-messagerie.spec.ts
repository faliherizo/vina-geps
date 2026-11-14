/**
 * **Messagerie du personnel — deux personnes, de bout en bout, contre le Java.** AO 26-015-DO (P11).
 *
 * Julie et Marc sont deux paramédics du même organisme, chacun dans son navigateur :
 * - Julie choisit Marc **à l'écran** (« Écrire à cette personne »), écrit, et Marc lit ;
 * - Julie appelle depuis le fil : le bandeau « ça sonne » s'affiche **chez elle, aussitôt et à
 *   l'écran**, et la sonnerie arrive **chez Marc**, qui décroche ; l'un raccroche, l'appel finit
 *   chez les deux ;
 * - un fil de groupe s'ouvre, Julie appelle le groupe, Marc rejoint.
 *
 * C'est le parcours qui échouait le 14 novembre 2026 : la liste des membres rendait la clé technique
 * `login@@organisme`, le fil et l'appel partaient vers un identifiant que Marc ne porte pas, et rien
 * ne sonnait chez lui. L'appel par l'API, avec le bon nom tapé à la main, passait : seul un essai qui
 * **choisit la personne à l'écran** voit ce défaut.
 */
import { test, expect, type Browser, type Page } from "@playwright/test";
import { superadminContext } from "./helpers/auth";
import { createTenant, setTenantModules, uniq } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";
import { auditA11y } from "./helpers/a11y";
import { optionsFilmees } from "./helpers/film";
import { amorcerOrganisme, identifiantDe, modulesDeLOrganisme, seConnecterProfil, type Profil } from "./helpers/urgences-sante";
import jeu from "../../donnees/urgences-sante/profils.json";

test.use({ storageState: { cookies: [], origins: [] } });

const PROFILS = jeu.profils as Profil[];
const JULIE = PROFILS.find((p) => p.cle === "paramedic")!;
const MARC = PROFILS.find((p) => p.cle === "collegue")!;
/** La relève des appels a lieu toutes les quelques secondes : l'appelé l'apprend au plus tard à la suivante. */
const SONNERIE_MS = 20_000;
/** Un fil à deux se nomme, selon qui le lit, « Conversation à deux » ou par le nom de l'autre. */
const FIL_A_DEUX = new RegExp(`deux|${MARC.nom}|${JULIE.nom}`, "i");

async function ouvrirMessagerie(browser: Browser, tenantId: string, profil: Profil): Promise<Page> {
  const ctx = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] } }));
  const page = await ctx.newPage();
  await seConnecterProfil(page, tenantId, identifiantDe(profil, tenantId));
  await ouvrirNav(page, "nav-usmessages");
  // Le choix du fil n'existe qu'une fois une conversation ouverte : on attend le formulaire « Écrire à une personne ».
  await expect(page.getByTestId("sq-msg-directe")).toBeVisible({ timeout: 30_000 });
  return page;
}

/** Choisit le fil dont le libellé correspond ; la liste arrive après l'écran. */
async function choisirFil(page: Page, libelle: RegExp): Promise<void> {
  const option = page.getByTestId("sq-msg-fil").locator("option").filter({ hasText: libelle });
  await expect(option).toHaveCount(1, { timeout: 30_000 });
  await page.getByTestId("sq-msg-fil").selectOption((await option.getAttribute("value"))!);
}

async function sansViolation(page: Page, etat: string): Promise<void> {
  expect(await auditA11y(page), `axe-core : ${etat}`).toEqual([]);
}

test.describe("26-015-DO — messagerie et appels entre deux personnes", () => {
  let tenantId = "";

  test.beforeAll(async () => {
    const sa = await superadminContext();
    try {
      tenantId = await createTenant(sa, uniq("US-MSG"));
      await setTenantModules(sa, tenantId, modulesDeLOrganisme);
    } finally { await sa.dispose(); }
    await amorcerOrganisme(tenantId, { profils: PROFILS });
  });

  test("écrire à une personne choisie à l'écran, puis l'appeler : ça sonne chez elle, elle décroche, on raccroche", async ({ browser }) => {
    test.setTimeout(240_000);
    const julie = await ouvrirMessagerie(browser, tenantId, JULIE);
    const marc = await ouvrirMessagerie(browser, tenantId, MARC);
    const idMarc = identifiantDe(MARC, tenantId);

    // ── Écrire : Marc est choisi dans la liste, par son nom ────────────────────────────────
    const personne = julie.getByTestId("sq-msg-directe-personne");
    await expect(personne.locator("option").filter({ hasText: MARC.nom })).toHaveCount(1, { timeout: 30_000 });
    // La valeur de l'option est le nom de connexion — jamais la clé technique « login@@organisme ».
    const valeur = await personne.locator("option").filter({ hasText: MARC.nom }).getAttribute("value");
    expect(valeur).toBe(idMarc);
    await personne.selectOption(valeur!);
    await julie.getByTestId("sq-msg-directe").click();
    await expect(julie.getByTestId("sq-msg-entete-fil")).toContainText(MARC.nom, { timeout: 30_000 });
    const texte = `Peux-tu prendre mon quart de jeudi ? (${Date.now()})`;
    await julie.getByTestId("sq-msg-corps").fill(texte);
    await julie.getByTestId("sq-msg-envoyer").click();
    await expect(julie.locator("main").getByText(texte).first()).toBeVisible({ timeout: 30_000 });

    await marc.reload();
    await choisirFil(marc, FIL_A_DEUX);
    // Le texte paraît deux fois : dans le fil, et en aperçu dans la liste des conversations.
    await expect(marc.locator("main").getByText(texte).first()).toBeVisible({ timeout: 30_000 });
    await sansViolation(marc, "fil à deux, message reçu");

    // ── Appeler depuis le fil ──────────────────────────────────────────────────────────────
    await julie.getByTestId("sq-msg-appeler").scrollIntoViewIfNeeded();
    await expect(julie.getByTestId("sq-msg-appeler")).toContainText(MARC.nom);
    await julie.getByTestId("sq-msg-appeler").click();
    // L'appelant voit son appel **aussitôt**, et le bandeau est ramené à l'écran.
    const bandeauJulie = julie.getByTestId("sq-appels-bandeau");
    await expect(bandeauJulie).toContainText("ça sonne", { timeout: 5_000 });
    await expect(bandeauJulie).toBeInViewport();

    // La sonnerie arrive chez Marc, où qu'il soit dans l'application.
    await ouvrirNav(marc, "nav-dashboard");
    const decrocher = marc.locator('[data-testid^="sq-appel-decrocher-"]');
    await expect(decrocher).toBeVisible({ timeout: SONNERIE_MS });
    await expect(marc.getByTestId("sq-appels-bandeau")).toContainText("vous appelle");
    await sansViolation(marc, "appel entrant");
    await decrocher.click();
    await expect(marc.getByTestId("sq-appels-bandeau")).toContainText("En communication", { timeout: 10_000 });
    await expect(bandeauJulie).toContainText("En communication", { timeout: SONNERIE_MS });
    await sansViolation(julie, "appel en cours");

    // Raccrocher termine l'appel chez les deux.
    await julie.locator('[data-testid^="sq-appel-raccrocher-"]').click();
    await expect(bandeauJulie).toHaveCount(0, { timeout: 10_000 });
    await expect(marc.getByTestId("sq-appels-bandeau")).toHaveCount(0, { timeout: SONNERIE_MS });

    // L'historique des appels le garde, chez l'appelé.
    await ouvrirNav(marc, "nav-usappels");
    await expect(marc.getByTestId("sq-appels-historique")).toContainText("reçu", { timeout: 30_000 });

    await julie.context().close();
    await marc.context().close();
  });

  test("refuser un appel : il s'arrête chez l'appelant aussi", async ({ browser }) => {
    test.setTimeout(180_000);
    const julie = await ouvrirMessagerie(browser, tenantId, JULIE);
    const marc = await ouvrirMessagerie(browser, tenantId, MARC);
    await choisirFil(julie, FIL_A_DEUX);
    await julie.getByTestId("sq-msg-appeler").click();
    await expect(julie.getByTestId("sq-appels-bandeau")).toContainText("ça sonne", { timeout: 5_000 });
    const refuser = marc.locator('[data-testid^="sq-appel-refuser-"]');
    await expect(refuser).toBeVisible({ timeout: SONNERIE_MS });
    await refuser.click();
    await expect(marc.getByTestId("sq-appels-bandeau")).toHaveCount(0, { timeout: 10_000 });
    await expect(julie.getByTestId("sq-appels-bandeau")).toHaveCount(0, { timeout: SONNERIE_MS });
    await julie.context().close();
    await marc.context().close();
  });

  test("un fil de groupe, un appel de groupe : l'invité rejoint", async ({ browser }) => {
    test.setTimeout(240_000);
    const julie = await ouvrirMessagerie(browser, tenantId, JULIE);
    const marc = await ouvrirMessagerie(browser, tenantId, MARC);
    const sujet = `Garde de nuit ${Date.now()}`;

    await julie.getByTestId("sq-msg-sujet").fill(sujet);
    const premier = julie.getByTestId("sq-msg-participant");
    await expect(premier.locator("option").filter({ hasText: MARC.nom })).toHaveCount(1, { timeout: 30_000 });
    await premier.selectOption(identifiantDe(MARC, tenantId));
    await julie.getByTestId("sq-msg-ouvrir").click();
    await choisirFil(julie, new RegExp(sujet));
    await expect(julie.getByTestId("sq-msg-appeler")).toContainText("Appeler le groupe");
    await julie.getByTestId("sq-msg-appeler").click();

    await marc.reload();
    const rejoindre = marc.locator('[data-testid^="sq-appel-groupe-rejoindre-"]');
    await expect(rejoindre).toBeVisible({ timeout: SONNERIE_MS });
    await sansViolation(marc, "appel de groupe entrant");
    await rejoindre.click();
    await expect(marc.locator('[data-testid^="sq-appel-groupe-quitter-"]')).toBeVisible({ timeout: 10_000 });
    await marc.locator('[data-testid^="sq-appel-groupe-quitter-"]').click();
    await julie.context().close();
    await marc.context().close();
  });
});
