/**
 * **Les parcours en vidéo de l'aide, joués sur Clincom (FX-AO-20260810)** — configurer, alertes, absences,
 * disponibilités, quarts supplémentaires.
 *
 * Chaque essai est un parcours de l'aide : il dit à voix haute ce qu'il va montrer (`raconter`, actif au tournage
 * seulement), le fait par le menu, comme une personne, et **affirme** ce que l'écran doit montrer avec les données semées.
 * `scripts/monter-parcours.mjs` monte ensuite une vidéo par essai : une vidéo d'aide ne montre donc jamais un écran que
 * l'essai n'a pas vérifié.
 *
 * Tourner : `scripts/tourner-parcours-fx-ao.sh` (voir docs/VIDEOS-AIDE.md). Les gestes qui écrivent (absence, dépôt,
 * alerte) ne s'exécutent qu'avec `E2E_PARCOURS_DEMO=1`, marqués « Exercice e2e » ; l'absence est annulée à la fin, le
 * dépôt se remplace. La configuration ouvre ses formulaires et les referme sans enregistrer.
 */
import { test, expect, request, type Browser, type Page } from "@playwright/test";
import jeu from "../../donnees/fx-ao/videos-aide.json";
import demo from "../../donnees/fx-ao/parcours-demonstration.json";
import { DEPLOYE, SEMES, apiDe, hoteDe, organismeDemo, seConnecter } from "./helpers/demonstration-deployee";
import { auditA11y } from "./helpers/a11y";
import { optionsFilmees } from "./helpers/film";
import { ouvrirNav } from "./helpers/nav";
import { raconter } from "./helpers/recit";

const ECRIT = process.env.E2E_PARCOURS_DEMO === "1";
const ORGANISME = organismeDemo("fx-ao");
const HEURE = new Date().toISOString().slice(0, 16).replace("T", " ");
const SANS_SESSION = { cookies: [], origins: [] };
const essai = (id: string) => jeu.parcours.find((p) => p.id === id)!.essai;

test.use({ storageState: SANS_SESSION });
test.describe.configure({ mode: "serial", timeout: 600_000 });

/** Un jour compté depuis aujourd'hui, au format du champ date, à Montréal. */
function jour(decalage: number): string {
  return new Date(Date.now() + decalage * 86_400_000).toLocaleDateString("en-CA", { timeZone: "America/Toronto" });
}

/** Une session filmée au nom d'un compte, ouverte sur le tableau de bord. */
async function session(browser: Browser, hote: string, compte: string): Promise<Page> {
  const page = await (await browser.newContext(optionsFilmees({ storageState: SANS_SESSION }))).newPage();
  await seConnecter(page, hote, compte);
  return page;
}

/** Un écran ouvert **par le menu** : la vidéo montre où il se trouve. */
async function parLeMenu(page: Page, ecran: string) {
  await ouvrirNav(page, `nav-${ecran}`);
  await expect(page.locator("main h1").first(), `/admin/${ecran}`).toBeVisible({ timeout: 30_000 });
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
}

async function sansViolation(page: Page, etat: string) {
  const v = await auditA11y(page);
  expect(v.map((x) => `${x.id} → ${x.targets.join(" | ")}`), `axe-core : ${etat}`).toEqual([]);
}

/** Choisir dans un `<select>` l'option dont le texte correspond ; la liste arrive après l'écran. */
async function choisir(page: Page, testId: string, texte: string | RegExp) {
  const option = page.getByTestId(testId).locator("option").filter({ hasText: texte });
  await expect(option.first(), `« ${texte} » dans ${testId}`).toBeAttached({ timeout: 30_000 });
  await page.getByTestId(testId).selectOption((await option.first().getAttribute("value"))!);
}

/** Amener une zone à l'image avant d'en parler. */
async function montrer(page: Page, testId: string) {
  const zone = page.getByTestId(testId);
  await expect(zone).toBeVisible({ timeout: 30_000 });
  await zone.scrollIntoViewIfNeeded();
  return zone;
}

test.describe("Aide en vidéo — les parcours FX-AO sur la démonstration", () => {
  test.skip(!DEPLOYE || !SEMES, "vise une démonstration déployée et semée : poser E2E_BASE_URL et E2E_COMPTES_DEMO=1");

  let hote = "";
  test.beforeAll(async () => {
    const anonyme = await request.newContext();
    hote = await hoteDe(anonyme, ORGANISME);
    await anonyme.dispose();
  });

  test(essai("configurer"), async ({ browser }) => {
    const c = jeu.configuration;
    const page = await session(browser, hote, c.compte);
    await raconter(page, "L'administration de l'organisme prépare les communications cliniques. On commence par les sites, tenus au référentiel des unités.");
    await parLeMenu(page, "usreferentiels");
    await expect(page.locator("main")).toContainText(c.site, { timeout: 30_000 });
    await raconter(page, `Les six sites du territoire y figurent, dont l'${c.site}. Chaque personne est rattachée à son site.`);

    await parLeMenu(page, "admin");
    await montrer(page, "admin-users-list");
    await raconter(page, "La console d'administration crée les comptes, un par personne, avec son profil et son site. Un compte se désactive sans être supprimé.");
    await parLeMenu(page, "access");
    await montrer(page, "access-tabs");
    await raconter(page, "Les profils d'accès disent qui envoie, qui reçoit, qui suit et qui administre. Un rôle propre, comme l'expéditeur, se compose ici.");

    await parLeMenu(page, "alparametres");
    const reponses = await montrer(page, "alp-reponses");
    for (const r of c.reponses) await expect(reponses).toContainText(r);
    await raconter(page, `Les paramètres des alertes. D'abord les réponses prédéfinies : ${c.reponses.join(" et ")}. Chacune dit si elle prend en charge ou si elle décline.`);
    await page.getByTestId("alp-reponse-ouvrir").click();
    await page.getByTestId("alp-r-libelle").fill(c.exempleReponse.libelle);
    await sansViolation(page, "Nouvelle réponse prédéfinie");
    await raconter(page, `Une nouvelle réponse se crée par ce bouton : un libellé court, « ${c.exempleReponse.libelle} », et son sens. Ici, on referme sans enregistrer.`);
    await page.keyboard.press("Escape");

    const modeles = await montrer(page, "alp-modeles");
    for (const m of c.modeles) await expect(modeles).toContainText(m);
    await raconter(page, `Les modèles préparent les alertes fréquentes, comme ${c.modeles.join(" ou ")} : le titre, le texte, la priorité et les réponses offertes. Chaque modification garde la version précédente.`);
    await page.getByTestId("alp-modele-ouvrir").click();
    await page.getByTestId("alp-m-titre").fill(c.exempleModele.titre);
    await page.getByTestId("alp-m-corps").fill(c.exempleModele.corps);
    await raconter(page, "Un nouveau modèle : son titre, son texte, sa priorité, puis les réponses que le destinataire pourra choisir. Aucun renseignement de santé n'y est écrit.");
    await page.keyboard.press("Escape");

    const groupes = await montrer(page, "alp-gt-table");
    for (const g of c.groupes) await expect(groupes).toContainText(g);
    await raconter(page, `Les groupes transverses réunissent des personnes de plusieurs sites : la ${c.groupes[0]}, le ${c.groupes[1]}. Leurs membres sont datés, et un modérateur les tient à jour.`);
    await expect(await montrer(page, "alp-politiques-table")).toContainText(c.politique);
    await raconter(page, `Les politiques de relance règlent la cascade : « ${c.politique} » relance d'abord par poussée et par texto, puis vise le prochain disponible de la garde, puis appelle. Une personne absente n'est jamais visée.`);
    await montrer(page, "alp-reglages");
    await raconter(page, "Enfin, les réglages de l'organisme : la copie des alertes par courriel, et le décompte des utilisateurs actifs du mois.");
    await sansViolation(page, "Paramètres des alertes");
    await page.context().close();
  });

  test(essai("alertes"), async ({ browser }) => {
    test.skip(!ECRIT, "écrit dans la démonstration : poser E2E_PARCOURS_DEMO=1");
    const a = demo.alerte;
    const titre = `${jeu.marque} — ${a.titre} (${HEURE})`;
    const chef = await session(browser, hote, a.expediteur);
    await raconter(chef, "Martin Gagnon, chef de l'urgence de Saint-Jérôme, ouvre Nouvelle alerte.");
    await parLeMenu(chef, "alnouvelle");
    await choisir(chef, "aln-modele", a.modele);
    await chef.getByTestId("aln-titre-champ").fill(titre);
    await chef.getByTestId("aln-corps").fill(a.corps);
    await raconter(chef, `Il part du modèle « ${a.modele} » : le titre et le texte sont prêts, il les ajuste.`);
    await chef.getByTestId(`aln-p-${a.priorite}`).check();
    for (const g of a.groupes) await chef.getByLabel(`Tout le groupe ${g}`, { exact: true }).check();
    await raconter(chef, `Il vise tout le groupe « ${a.groupes[0]} » d'un seul geste.`);
    for (const r of a.recherches) {
      await chef.getByTestId("aln-recherche").fill(r.texte);
      await chef.getByTestId(`aln-trouvee-${r.personId}`).check({ timeout: 30_000 });
    }
    await expect(chef.getByTestId("aln-compte")).toContainText(a.recherches.map((r) => r.nom).join(", "));
    await raconter(chef, `Et il ajoute ${a.recherches[0].nom}, cherchée par son nom dès trois lettres. Ses coordonnées ne s'affichent jamais.`);
    await chef.getByTestId("aln-envoyer").click();
    await expect(chef.getByTestId("aln-envoyee")).toContainText(`envoyée à ${a.destinataires} personne(s)`, { timeout: 30_000 });
    await raconter(chef, `L'alerte part à ${a.destinataires} personnes, par poussée et par texto.`);

    const p = a.priseEnCharge;
    const medecin = await session(browser, hote, p.compte);
    await parLeMenu(medecin, "mesalertes");
    const carte = medecin.locator('[data-testid^="mal-alerte-"]').filter({ hasText: titre }).first();
    await expect(carte).toBeVisible({ timeout: 30_000 });
    await carte.locator("summary").click();
    await raconter(medecin, `${p.nom}, de garde, la reçoit dans Mes alertes et répond d'un geste.`);
    await carte.getByRole("button", { name: p.reponse, exact: true }).click();
    await expect(medecin.getByTestId("mal-succes")).toContainText(`Réponse « ${p.reponse} » envoyée`, { timeout: 30_000 });
    await raconter(medecin, `« ${p.reponse} » : elle prend l'alerte en charge.`);
    await sansViolation(medecin, "Mes alertes, prise en charge");

    await parLeMenu(chef, "alsuivi");
    const ligne = chef.getByTestId("als-table").locator("tr", { hasText: titre });
    await expect(ligne).toContainText(p.nom, { timeout: 30_000 });
    await ligne.scrollIntoViewIfNeeded();
    await raconter(chef, `De retour au suivi, Martin Gagnon voit qui a pris en charge : ${p.nom}. Les relances de la cascade s'arrêtent d'elles-mêmes.`);
    await Promise.all([chef.context().close(), medecin.context().close()]);
  });

  test(essai("absences"), async ({ browser }) => {
    test.skip(!ECRIT, "écrit dans la démonstration : poser E2E_PARCOURS_DEMO=1");
    const a = jeu.absences;
    const du = jour(a.du);
    const au = jour(a.au);
    // Un passage interrompu a pu laisser l'absence : l'annuler d'abord, sinon le serveur refuse le chevauchement.
    const moi = await apiDe(hote, a.compte);
    for (const x of await (await moi.get(`${hote}/api/effectifs/absences`)).json()) {
      if (x.du === du && ["ACCEPTEE", "A_APPROUVER"].includes(x.statut)) await moi.post(`${hote}/api/effectifs/absences/${x.id}/annuler`, { data: {} });
    }
    await moi.dispose();

    const page = await session(browser, hote, a.compte);
    await raconter(page, `${a.nom}, infirmier, sera en congé. Il le déclare lui-même, dans Mes absences.`);
    await parLeMenu(page, "mesabsences");
    await page.getByTestId("mab-declarer").click();
    await page.getByTestId("abs-eff-code").selectOption(a.code);
    await page.getByTestId("abs-start").fill(du);
    await page.getByTestId("abs-end").fill(au);
    await raconter(page, `Le motif, puis les dates : du ${du} au ${au}.`);
    await page.getByTestId("abs-submit").click();
    await expect(page.getByTestId("mab-info")).toContainText("Absence déclarée.", { timeout: 30_000 });
    await expect(page.getByTestId("mab-table")).toContainText(`Du ${du} au ${au}`);
    await raconter(page, "L'absence est enregistrée et acceptée : pendant ces jours, il n'est visé par aucune cascade d'alerte.");

    const admin = await session(browser, hote, a.verificateur);
    await parLeMenu(admin, "usabsences");
    await admin.getByTestId("pla-abs-filtre").selectOption("ACCEPTEE");
    await expect(admin.locator("main")).toContainText(a.nom, { timeout: 30_000 });
    await expect(admin.locator("main")).toContainText(a.dejaAuCalendrier);
    await raconter(admin, `L'administration la voit au calendrier interne, avec celle du ${a.dejaAuCalendrier}. C'est ce calendrier qui dit aux alertes qui est absent.`);
    await sansViolation(admin, "Absences et comblement, absences acceptées");

    await page.getByTestId("mab-table").locator("tr", { hasText: `Du ${du} au ${au}` }).locator('[data-testid$="-annuler"]').click();
    await expect(page.getByTestId("mab-info")).toContainText(`Absence du ${du} au ${au} annulée.`, { timeout: 30_000 });
    await raconter(page, "Les plans changent : il annule son absence d'un clic, et redevient joignable.");
    await Promise.all([page.context().close(), admin.context().close()]);
  });

  test(essai("disponibilites"), async ({ browser }) => {
    test.skip(!ECRIT, "écrit dans la démonstration : poser E2E_PARCOURS_DEMO=1");
    const d = jeu.disponibilites;
    const page = await session(browser, hote, d.compte);
    await raconter(page, `${d.personne}, infirmière à Saint-Jérôme, ouvre Mes disponibilités.`);
    await parLeMenu(page, "mesdisponibilites");
    await choisir(page, "mdi-periode", d.periode);
    await expect(page.getByTestId("mdi-etat")).toContainText(d.etat);
    await raconter(page, `La période de son site est en collecte : « ${d.periode} ». Elle a jusqu'à la date affichée pour déposer.`);
    while (await page.locator('[data-testid^="mdi-plage-"][data-testid$="-retirer"]').count()) {
      await page.locator('[data-testid^="mdi-plage-"][data-testid$="-retirer"]').first().click();
    }
    await page.getByTestId("mdi-ajouter").click();
    await raconter(page, "Elle ajoute une plage : le jour, l'heure de début et de fin du quart, ici le quart de jour, de 6 h à 18 h, et si elle est disponible ou de préférence.");
    await page.getByTestId("mdi-deposer").click();
    await expect(page.getByTestId("mdi-confirme")).toContainText("Dépôt enregistré", { timeout: 30_000 });
    await expect(page.getByTestId("mdi-refus")).toHaveCount(0);
    await raconter(page, "Le dépôt est enregistré. Les règles de l'organisme le jugent aussitôt : un dépôt incomplet serait refusé avec son motif.");
    await sansViolation(page, "Mes disponibilités, dépôt enregistré");

    const admin = await session(browser, hote, d.gestionnaire);
    await parLeMenu(admin, "usdisponibilites");
    await choisir(admin, "pla-periode", /Saint-Jérôme|Collecte/);
    await expect(await montrer(admin, "pla-dispo-resume")).toBeVisible();
    await raconter(admin, "Côté gestion, Disponibilités de la période montre les dépôts reçus et leur conformité, avant la confection de l'horaire.");
    await sansViolation(admin, "Disponibilités de la période");
    await Promise.all([page.context().close(), admin.context().close()]);
  });

  test(essai("quarts"), async ({ browser }) => {
    const q = jeu.quarts;
    const page = await session(browser, hote, q.compte);
    await raconter(page, `${q.inscrite} veut des quarts supplémentaires. Elle passe par Mes demandes.`);
    await parLeMenu(page, "mesdemandes");
    await expect(page.getByTestId("dem-type").locator("option", { hasText: q.type })).toHaveCount(1);
    await expect(page.getByTestId("dem-table")).toContainText(q.type, { timeout: 30_000 });
    await page.getByTestId("dem-table").scrollIntoViewIfNeeded();
    await raconter(page, `Son inscription à la « ${q.type} » est là, acceptée, pour les soirs de semaine. Une nouvelle demande se fait par le même formulaire : le type, les dates, un commentaire.`);
    await sansViolation(page, "Mes demandes");

    const chef = await session(browser, hote, q.gestionnaire);
    await parLeMenu(chef, "usdemandes");
    await expect(chef.getByTestId("dem-gestion-table")).toContainText(q.enAttente, { timeout: 30_000 });
    await raconter(chef, `Le gestionnaire ouvre Demandes opérationnelles : la demande d'${q.enAttente} attend sa décision, qu'il accepte ou refuse avec un motif.`);
    await chef.getByTestId("dem-ts-date").fill(jour(q.dansJours));
    await expect(chef.getByTestId("dem-ts-table")).toContainText(q.inscrite, { timeout: 30_000 });
    await chef.getByTestId("dem-ts-table").scrollIntoViewIfNeeded();
    await raconter(chef, `Pour combler un quart, il choisit le jour : la liste de temps supplémentaire nomme les personnes inscrites, dont ${q.inscrite}.`);
    await sansViolation(chef, "Demandes opérationnelles, liste de temps supplémentaire");
    await Promise.all([page.context().close(), chef.context().close()]);
  });
});
