/**
 * **Addenda 8 de 26-015-DO, à l'écran, sur les deux organismes déployés** — Urgences-santé et Clincom (FX-AO).
 *
 * Chaque réponse livrée (M-02 congés annuels, M-04 compétences, M-08 demandes et liste de temps supplémentaire,
 * M-09 rattachements, M-10 journal des consultations, M-18 sources de rapport) est constatée **avec les données que le
 * semis a posées** : un écran vide passerait un essai de présence, il échoue ici en nommant la donnée absente.
 *
 * Toute la connaissance est dans `donnees/addenda-08/demonstration.json`. La lecture ne modifie rien ; les gestes
 * (soumettre, annuler, refuser une demande) écrivent et ne s'exécutent qu'avec `E2E_PARCOURS_DEMO=1`, marqués
 * « Exercice e2e ». Vise une pile déployée et semée (`E2E_BASE_URL`, `E2E_COMPTES_DEMO=1`).
 */
import { test, expect, request, type Page } from "@playwright/test";
import jeu from "../../donnees/addenda-08/demonstration.json";
import { auditA11y } from "./helpers/a11y";
import { DEPLOYE, SEMES, allerA, apiDe, hoteDe, organismeDemo, seConnecter } from "./helpers/demonstration-deployee";

const ECRIT = process.env.E2E_PARCOURS_DEMO === "1";
type Cas = (typeof jeu.organismes)[number];

/** Le jour de Montréal décalé de `n` jours : la liste de temps supplémentaire se lit à une date couverte par le semis. */
function jourDans(n: number): string {
  const d = new Date(Date.now() + n * 86_400_000);
  return d.toLocaleDateString("en-CA", { timeZone: "America/Toronto" });
}

async function hote(cas: Cas): Promise<string> {
  const api = await request.newContext();
  try { return await hoteDe(api, organismeDemo(cas.cle)); } finally { await api.dispose(); }
}

async function ouvrir(page: Page, h: string, ecran: string) {
  await allerA(page, `${h}/admin/${ecran}`);
  await expect(page.locator("h1").first(), `/admin/${ecran}`).toBeVisible({ timeout: 30_000 });
}

/** axe-core sur l'état affiché, données réelles comprises : la cible est nommée, pas seulement la règle. */
async function accessible(page: Page, etat: string) {
  const violations = await auditA11y(page);
  expect(violations.map((v) => `${v.id} — ${v.help} → ${v.targets.join(" | ")}`), `axe-core : ${etat}`).toEqual([]);
}

test.describe("Addenda 8 — les réponses à l'écran, avec leurs données", () => {
  test.skip(!DEPLOYE || !SEMES, "vise une démonstration déployée et semée : poser E2E_BASE_URL et E2E_COMPTES_DEMO=1");
  test.describe.configure({ mode: "serial" });

  for (const cas of jeu.organismes) {
    test.describe(cas.cle, () => {
      test(`M-04 et M-09 : compétences, échéances et autres unités (${cas.cle})`, async ({ page }) => {
        const h = await hote(cas);
        await seConnecter(page, h, cas.admin);
        await ouvrir(page, h, "usressources");

        await page.getByTestId(`pla-ressource-competences-${cas.competence.matricule}`).click();
        const fiche = page.getByTestId("pla-competences");
        await expect(fiche).toBeVisible();
        for (const libelle of cas.competence.attendus) await expect(fiche, `compétence « ${libelle} »`).toContainText(libelle);
        await accessible(page, "compétences d'une personne");
        await page.keyboard.press("Escape");
        await expect(fiche).toBeHidden();

        await expect(page.getByTestId("pla-echeances-tableau"), "relevé des échéances").toContainText(cas.competence.echeance);

        await page.getByTestId(`pla-ressource-unites-${cas.rattachement.matricule}`).click();
        const unites = page.getByTestId("pla-rattachements");
        await expect(unites.getByTestId("pla-rattachements-tableau"), "rattachement semé").toContainText(cas.rattachement.unite);
        await accessible(page, "autres unités d'une personne");
        await page.keyboard.press("Escape");
      });

      test(`M-08 : demandes à traiter et liste de temps supplémentaire (${cas.cle})`, async ({ page }) => {
        const h = await hote(cas);
        await seConnecter(page, h, cas.decideur);
        await ouvrir(page, h, "usdemandes");
        await expect(page.getByTestId("dem-gestion-table"), "demande en attente semée").toContainText(cas.demandes.enAttente);
        await page.getByTestId("dem-ts-date").fill(jourDans(cas.demandes.dansJours));
        await expect(page.getByTestId("dem-ts-table"), "inscrit à la liste de temps supplémentaire").toContainText(cas.demandes.inscritTs);
        await accessible(page, "demandes opérationnelles");
      });

      test(`M-02 : campagne de congés annuels et ses résultats (${cas.cle})`, async ({ page }) => {
        const h = await hote(cas);
        await seConnecter(page, h, cas.admin);
        await ouvrir(page, h, "uscongesannuels");
        const ligne = page.getByTestId("cng-table").locator("tr", { hasText: cas.conges.campagne });
        await expect(ligne, `campagne « ${cas.conges.campagne} »`).toHaveCount(1);
        await ligne.locator('[data-testid^="cng-resultats-"]').click();
        await expect(page.getByTestId("cng-resultats"), "choix semés").toContainText(cas.conges.resultat);
        await accessible(page, "campagne et résultats");
      });

      test(`M-02 et M-08 : l'espace de la personne (${cas.cle})`, async ({ page }) => {
        const h = await hote(cas);
        await seConnecter(page, h, cas.employe);
        await ouvrir(page, h, "mescongesannuels");
        await expect(page.getByTestId("cng-miennes"), "campagne ouverte à la personne").toContainText(cas.conges.campagne);
        await accessible(page, "mes congés annuels");
        await ouvrir(page, h, "mesdemandes");
        await expect(page.getByTestId("dem-formulaire")).toBeVisible();
        await expect(page.getByTestId("dem-type").locator("option", { hasText: "Liste de temps supplémentaire" })).toHaveCount(1);
        await accessible(page, "mes demandes");
      });

      test(`M-10 et M-18 : journal des consultations et sources de rapport (${cas.cle})`, async ({ page }) => {
        const h = await hote(cas);
        await seConnecter(page, h, cas.admin);
        await ouvrir(page, h, "usjournal");
        await expect(page.getByTestId("jrn-consultations-actif"), "journal des consultations activé au semis").toBeChecked();
        await accessible(page, "journal des décisions");

        const api = await apiDe(h, cas.admin);
        try {
          const codes = ((await (await api.get(`${h}/api/rapports/sources`)).json()) as Array<{ code: string }>).map((s) => s.code);
          for (const code of cas.rapports) expect(codes, `source de rapport ${code}`).toContain(code);
        } finally { await api.dispose(); }
      });

      test(`M-08 : soumettre, annuler, refuser — gestes réels (${cas.cle})`, async ({ browser }) => {
        test.skip(!ECRIT, "écrit dans la démonstration : poser E2E_PARCOURS_DEMO=1");
        const h = await hote(cas);
        const employe = await (await browser.newContext()).newPage();
        await seConnecter(employe, h, cas.employe);
        await ouvrir(employe, h, "mesdemandes");
        const soumettre = async (type: string, texte: string) => {
          await employe.getByTestId("dem-type").selectOption(type);
          await employe.getByTestId("dem-du").fill(jourDans(40));
          await employe.getByTestId("dem-au").fill(jourDans(41));
          await employe.getByTestId("dem-commentaire").fill(texte);
          await employe.getByTestId("dem-envoyer").click();
          await expect(employe.getByTestId("dem-succes")).toBeVisible();
        };
        const aAnnuler = `${jeu.marque} — annulée ${Date.now()}`;
        await soumettre(cas.demandes.typeGeste, aAnnuler);
        const ligne = employe.getByTestId("dem-table").locator("tr", { hasText: aAnnuler });
        await ligne.locator('[data-testid$="-annuler"]').click();
        await expect(ligne, "demande annulée").toContainText(/Annulée/i);

        const aRefuser = `${jeu.marque} — refusée ${Date.now()}`;
        await soumettre(cas.demandes.typeRefus, aRefuser);

        const decideur = await (await browser.newContext()).newPage();
        await seConnecter(decideur, h, cas.decideur);
        await ouvrir(decideur, h, "usdemandes");
        const aTraiter = decideur.getByTestId("dem-gestion-table").locator("tr", { hasText: aRefuser });
        await expect(aTraiter).toHaveCount(1);
        await aTraiter.locator('[data-testid^="dem-motif-"]').fill(`${jeu.marque} : période déjà couverte`);
        await aTraiter.locator('[data-testid^="dem-refuser-"]').click();
        await expect(decideur.getByTestId("dem-gestion-succes")).toBeVisible();

        await ouvrir(employe, h, "mesdemandes");
        await expect(employe.getByTestId("dem-table").locator("tr", { hasText: aRefuser }), "refus visible de la personne").toContainText(/Refusée/i);
      });
    });
  }
});
