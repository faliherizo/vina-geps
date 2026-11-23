/**
 * **FX-AO-20260810, les gestes posés à l'écran sur la démonstration déployée** — déclarer une absence, déposer une
 * disponibilité, envoyer une alerte à un groupe et à une personne puis y répondre, écrire à une personne et à un groupe.
 *
 * `deploiement-fx-ao-fonctionnalites.spec.ts` lit ; celui-ci **agit**, avec les comptes de démonstration de Clincom,
 * chacun dans sa propre session, et vérifie que le geste de l'un arrive chez l'autre. Il écrit donc dans la
 * démonstration, et s'y tient propre : chaque titre et chaque message porte la marque « Exercice e2e » et l'heure,
 * l'absence est annulée à la fin, le dépôt de disponibilité se remplace, le fil de groupe est repris s'il existe.
 *
 * Toute la donnée est dans `donnees/fx-ao/parcours-demonstration.json`. Vise une pile déployée et semée
 * (`E2E_BASE_URL`, `E2E_COMPTES_DEMO=1`) ; ne fait rien sans `E2E_PARCOURS_DEMO=1`, puisqu'il écrit.
 */
import { test, expect, request, type Browser, type Page } from "@playwright/test";
import jeu from "../../donnees/fx-ao/parcours-demonstration.json";
import { DEPLOYE, SEMES, allerA, apiDe, hoteDe, organismeDemo, seConnecter } from "./helpers/demonstration-deployee";
import { auditA11y } from "./helpers/a11y";

const ECRIT = process.env.E2E_PARCOURS_DEMO === "1";
const ORGANISME = organismeDemo("fx-ao");
/** L'heure de l'essai, dans chaque texte écrit : un passage se distingue du précédent, à l'écran comme au suivi. */
const HEURE = new Date().toISOString().slice(0, 16).replace("T", " ");
const marque = (texte: string) => `${jeu.marque} — ${texte} (${HEURE})`;

/** Un jour compté depuis aujourd'hui, au format du champ date, à Montréal. */
function jour(decalage: number): string {
  const d = new Date(Date.now() + decalage * 86_400_000);
  return d.toLocaleDateString("en-CA", { timeZone: "America/Toronto" });
}

async function session(browser: Browser, hote: string, compte: string, ecran: string): Promise<Page> {
  const ctx = await browser.newContext({ storageState: { cookies: [], origins: [] } });
  const page = await ctx.newPage();
  await seConnecter(page, hote, compte);
  await allerA(page, `${hote}/admin/${ecran}`);
  await expect(page.locator("h1").first(), `${compte} /admin/${ecran}`).toBeVisible({ timeout: 30_000 });
  return page;
}

/** La messagerie s'ouvre d'office sur l'unité de la personne (`groupeParDefaut`) : celle des personnes du parcours. */
async function messagerie(browser: Browser, hote: string, compte: string): Promise<Page> {
  const page = await session(browser, hote, compte, "usmessages");
  await expect(page.getByTestId("sq-perimetre-groupe").locator("option:checked"), `${compte} : unité ouverte d'office`)
    .toContainText(jeu.messages.unite, { timeout: 30_000 });
  return page;
}

async function sansViolation(page: Page, etat: string) {
  expect(await auditA11y(page), `axe-core : ${etat}`).toEqual([]);
}

/** Choisir dans un `<select>` l'option dont le texte correspond ; la liste arrive après l'écran. */
async function choisir(page: Page, testId: string, texte: string | RegExp) {
  const option = page.getByTestId(testId).locator("option").filter({ hasText: texte });
  await expect(option.first(), `« ${texte} » dans ${testId}`).toBeAttached({ timeout: 30_000 });
  await page.getByTestId(testId).selectOption((await option.first().getAttribute("value"))!);
}

test.use({ storageState: { cookies: [], origins: [] } });
test.describe.configure({ mode: "serial", timeout: 300_000 });

test.describe("Déploiement — FX-AO-20260810, les gestes sur la démonstration", () => {
  test.skip(!DEPLOYE || !SEMES || !ECRIT,
    "écrit dans une démonstration déployée et semée : poser E2E_BASE_URL, E2E_COMPTES_DEMO=1 et E2E_PARCOURS_DEMO=1");

  let hote = "";
  test.beforeAll(async () => {
    const anonyme = await request.newContext();
    hote = await hoteDe(anonyme, ORGANISME);
    await anonyme.dispose();
  });

  test(`déclarer une absence — ${jeu.absence.pourquoi}`, async ({ browser }) => {
    const a = jeu.absence;
    const du = jour(a.du);
    const au = jour(a.au);
    // Un passage interrompu a pu laisser l'absence : l'annuler d'abord, sinon le serveur refuse le chevauchement (409).
    const moi = await apiDe(hote, a.compte);
    for (const x of await (await moi.get(`${hote}/api/effectifs/absences`)).json()) {
      if (x.du === du && ["ACCEPTEE", "A_APPROUVER"].includes(x.statut)) await moi.post(`${hote}/api/effectifs/absences/${x.id}/annuler`, { data: {} });
    }

    const page = await session(browser, hote, a.compte, "mesabsences");
    await page.getByTestId("mab-declarer").click();
    await page.getByTestId("abs-eff-code").selectOption(a.code);
    await page.getByTestId("abs-start").fill(du);
    await page.getByTestId("abs-end").fill(au);
    await page.getByTestId("abs-submit").click();
    await expect(page.getByTestId("mab-info")).toContainText("Absence déclarée.", { timeout: 30_000 });
    await expect(page.getByTestId("mab-table")).toContainText(`Du ${du} au ${au}`);
    await sansViolation(page, "Mes absences, absence déclarée");

    // L'administration la voit, au nom de la personne, et acceptée : elle compte au calendrier interne.
    const admin = await apiDe(hote, a.verificateur);
    const vues = await (await admin.get(`${hote}/api/effectifs/absences`)).json();
    // Les passages précédents ont laissé des absences annulées à la même date : seules celles en vigueur comptent.
    expect(vues.filter((x: { ressourceNom: string; du: string; statut: string }) => x.ressourceNom === a.nom && x.du === du && x.statut !== "ANNULEE")
      .map((x: { codeAbsence: string; statut: string }) => [x.codeAbsence, x.statut])).toEqual([[a.code, "ACCEPTEE"]]);

    // La démonstration n'en garde pas trace : la personne l'annule.
    await page.getByTestId("mab-table").locator("tr", { hasText: `Du ${du} au ${au}` }).locator('[data-testid$="-annuler"]').click();
    await expect(page.getByTestId("mab-info")).toContainText(`Absence du ${du} au ${au} annulée.`, { timeout: 30_000 });
    await page.context().close();
    await Promise.all([moi.dispose(), admin.dispose()]);
  });

  test(`déposer une disponibilité — ${jeu.disponibilite.pourquoi}`, async ({ browser }) => {
    const d = jeu.disponibilite;
    const page = await session(browser, hote, d.compte, "mesdisponibilites");
    await choisir(page, "mdi-periode", d.periode);
    await expect(page.getByTestId("mdi-etat")).toContainText(d.etat);
    // Une plage, toujours la même : le dépôt remplace le précédent, l'essai se rejoue.
    while (await page.locator('[data-testid^="mdi-plage-"][data-testid$="-retirer"]').count()) {
      await page.locator('[data-testid^="mdi-plage-"][data-testid$="-retirer"]').first().click();
    }
    await page.getByTestId("mdi-ajouter").click();
    await page.getByTestId("mdi-deposer").click();
    await expect(page.getByTestId("mdi-confirme")).toContainText("Dépôt enregistré", { timeout: 30_000 });
    await expect(page.getByTestId("mdi-refus")).toHaveCount(0);
    await expect(page.getByTestId("mdi-conformite")).not.toContainText("Aucun dépôt");
    await sansViolation(page, "Mes disponibilités, dépôt enregistré");
    await page.context().close();
  });

  test(`envoyer une alerte à un groupe et à une personne, y répondre — ${jeu.alerte.pourquoi}`, async ({ browser }) => {
    const a = jeu.alerte;
    const titre = marque(a.titre);

    const chef = await session(browser, hote, a.expediteur, "alnouvelle");
    await choisir(chef, "aln-modele", a.modele);
    await chef.getByTestId("aln-titre-champ").fill(titre);
    await chef.getByTestId("aln-corps").fill(a.corps);
    await chef.getByTestId(`aln-p-${a.priorite}`).check();
    for (const g of a.groupes) await chef.getByLabel(`Tout le groupe ${g}`, { exact: true }).check();
    for (const r of a.recherches) {
      await chef.getByTestId("aln-recherche").fill(r.texte);
      await chef.getByTestId(`aln-trouvee-${r.personId}`).check({ timeout: 30_000 });
    }
    await expect(chef.getByTestId("aln-compte")).toContainText(a.recherches.map((r) => r.nom).join(", "));
    await sansViolation(chef, "Nouvelle alerte, destinataires choisis");
    await chef.getByTestId("aln-envoyer").click();
    await expect(chef.getByTestId("aln-envoyee")).toContainText(`envoyée à ${a.destinataires} personne(s)`, { timeout: 30_000 });

    // La garde de cardiologie la reçoit : la Dre Tremblay prend en charge d'un geste.
    const p = a.priseEnCharge;
    const medecin = await session(browser, hote, p.compte, "mesalertes");
    const carte = medecin.locator('[data-testid^="mal-alerte-"]').filter({ hasText: titre }).first();
    await expect(carte).toBeVisible({ timeout: 30_000 });
    await carte.locator("summary").click();
    await carte.getByRole("button", { name: p.reponse, exact: true }).click();
    await expect(medecin.getByTestId("mal-succes")).toContainText(`Réponse « ${p.reponse} » envoyée pour « ${titre} »`, { timeout: 30_000 });
    await expect(carte.locator('[data-testid$="-repondue"]')).toContainText(p.reponse);
    await sansViolation(medecin, "Mes alertes, prise en charge");

    // Julie Roy, cherchée par son nom, la reçoit aussi et la lit.
    for (const compte of a.lecteurs) {
      const lecteur = await session(browser, hote, compte, "mesalertes");
      const sienne = lecteur.locator('[data-testid^="mal-alerte-"]').filter({ hasText: titre }).first();
      await expect(sienne).toBeVisible({ timeout: 30_000 });
      await sienne.locator("summary").click();
      await expect(sienne).toContainText(a.corps);
      await lecteur.context().close();
    }

    // Le suivi de l'expéditeur nomme qui a pris en charge.
    await allerA(chef, `${hote}/admin/alsuivi`);
    const ligne = chef.getByTestId("als-table").locator("tr", { hasText: titre });
    await expect(ligne).toBeVisible({ timeout: 30_000 });
    await expect(ligne).toContainText(p.nom);
    await sansViolation(chef, "Suivi, alerte prise en charge");
    await Promise.all([chef.context().close(), medecin.context().close()]);
  });

  test(`écrire à une personne — ${jeu.messages.direct.pourquoi}`, async ({ browser }) => {
    const m = jeu.messages.direct;
    const texte = marque(m.texte);
    const de = await messagerie(browser, hote, m.de);
    await choisir(de, "sq-msg-directe-personne", m.nomA);
    await de.getByTestId("sq-msg-directe").click();
    await expect(de.getByTestId("sq-msg-entete-fil")).toContainText(m.nomA, { timeout: 30_000 });
    await de.getByTestId("sq-msg-corps").fill(texte);
    await de.getByTestId("sq-msg-envoyer").click();
    await expect(de.locator("main").getByText(texte).first()).toBeVisible({ timeout: 30_000 });

    const a = await messagerie(browser, hote, m.a);
    // Un fil à deux se nomme « à deux » ou par l'autre personne, selon son libellé (urgences-sante-messagerie.spec.ts).
    await choisir(a, "sq-msg-fil", new RegExp(`deux|${m.nomDe}|${m.de}`, "i"));
    await expect(a.locator("main").getByText(texte).first()).toBeVisible({ timeout: 30_000 });
    await sansViolation(a, "Messagerie, message reçu dans le fil à deux");
    await Promise.all([de.context().close(), a.context().close()]);
  });

  test(`écrire à un groupe — ${jeu.messages.groupe.pourquoi}`, async ({ browser }) => {
    const g = jeu.messages.groupe;
    const texte = marque(g.texte);
    const de = await messagerie(browser, hote, g.de);
    // Le fil de groupe est repris s'il existe : un passage n'ajoute pas un fil à la démonstration.
    const existe = await de.getByTestId("sq-msg-fil").locator("option").filter({ hasText: g.sujet }).count()
      .catch(() => 0);
    if (!existe) {
      await de.getByTestId("sq-msg-sujet").fill(g.sujet);
      await choisir(de, "sq-msg-participant", g.participants[0].nom);
      await de.getByTestId("sq-msg-ouvrir").click();
    }
    await choisir(de, "sq-msg-fil", g.sujet);
    for (const p of g.participants) {
      if (await de.getByTestId("sq-msg-part-table").getByText(p.nom).count()) continue;
      await choisir(de, "sq-msg-part-personne", p.nom);
      await de.getByTestId("sq-msg-part-ajouter").click();
      await expect(de.getByTestId("sq-msg-part-table")).toContainText(p.nom, { timeout: 30_000 });
    }
    await de.getByTestId("sq-msg-corps").fill(texte);
    await de.getByTestId("sq-msg-envoyer").click();
    await expect(de.locator("main").getByText(texte).first()).toBeVisible({ timeout: 30_000 });

    const lecteur = await messagerie(browser, hote, g.lecteur);
    await choisir(lecteur, "sq-msg-fil", g.sujet);
    await expect(lecteur.locator("main").getByText(texte).first()).toBeVisible({ timeout: 30_000 });
    await sansViolation(lecteur, "Messagerie, message reçu dans le fil de groupe");
    await Promise.all([de.context().close(), lecteur.context().close()]);
  });
});
