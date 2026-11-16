/**
 * **L'organisme de démonstration, tel que les planches le montrent.**
 *
 * Le module était livré et vide : pour le montrer, il fallait cliquer vingt écrans de paramétrage
 * avant que le premier tableau n'affiche autre chose que son état vide. `scripts/semer-urgences-sante.cjs`
 * sème le contenu des douze planches — sites, quarts, personnes, règles — et cet essai **le joue**,
 * puis vérifie que les écrans montrent ce que les planches annoncent.
 *
 * Il sème **par le script lui-même**, et non en recopiant ses appels : un double finirait par
 * diverger, et c'est alors la démonstration livrée au client qui serait la version non éprouvée.
 */
import { execFileSync } from "node:child_process";
import path from "node:path";
import { test, expect } from "@playwright/test";
import { auditA11y } from "./helpers/a11y";
import { menuPret, ouvrirNav } from "./helpers/nav";
import donnees from "../../donnees/urgences-sante/demonstration.json";
import { raconter } from "./helpers/recit";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;
const SCRIPT = path.join(__dirname, "..", "..", "..", "scripts", "semer-urgences-sante.cjs");
const MDP = "Passe!Demo2026";

test.use({ storageState: { cookies: [], origins: [] } });

/** Les entrées de menu des planches (colonne « MENU PRINCIPAL » de s01 à s12). */
const MENU_DES_PLANCHES = [
  { testId: "nav-monhoraire", libelle: "Mon horaire" },
  { testId: "nav-mesdisponibilites", libelle: "Disponibilités" },
  { testId: "nav-mesabsences", libelle: "Absences" },
  { testId: "nav-monagph", libelle: "Confection AGPH" },
  { testId: "nav-uscomblement", libelle: "Gestion des quarts" },
  { testId: "nav-monpointage", libelle: "Pointage" },
  { testId: "nav-matemps", libelle: "Feuilles de temps" },
  { testId: "nav-usreferentiels", libelle: "Administration" },
];

test.describe("26-015-DO — l'organisme de démonstration", () => {
  let tenantId = "";

  test.beforeAll(() => {
    // Le script parle au même serveur que l'essai : c'est la seule chose qu'on lui dit.
    const sortie = execFileSync("node", [SCRIPT, "--api", API, "--mdp", MDP],
      { encoding: "utf-8", timeout: 240_000 });
    tenantId = (sortie.match(/^TENANT=(.+)$/m) ?? [])[1] ?? "";
    expect(tenantId, `le script n'a pas nommé son organisme :\n${sortie}`).toBeTruthy();
  });

  const connecter = async (page: import("@playwright/test").Page, identifiant: string, mdp: string) => {
    await page.goto("/");
    await expect(page.getByTestId("login-tenant").locator(`option[value="${tenantId}"]`))
      .toHaveCount(1, { timeout: 60_000 });
    await page.getByTestId("login-tenant").selectOption(tenantId);
    await page.getByTestId("login-user").fill(identifiant);
    await page.getByTestId("login-pass").fill(mdp);
    await page.getByTestId("login-submit").click();
    await expect(page.getByTestId("dashboard-page")).toBeVisible({ timeout: 60_000 });
    await menuPret(page);
  };

  test("le menu porte les entrées des planches, et chaque écran porte leur chrome", async ({ page }) => {
    /*
      **Le budget du premier essai porte aussi l'amorce.** Playwright compte le `beforeAll` sur
      lui, et cette amorce sème tout l'organisme — référentiels, sites, sept dossiers d'employé et
      leurs comptes, postes, deux périodes, quarts attribués, règles, feuilles de temps, un groupe
      et un fil de messagerie. Le budget d'un essai ordinaire ne couvrait que le semis, et le
      premier essai expirait sur une application parfaitement fonctionnelle.
    */
    test.setTimeout(MENU_DES_PLANCHES.length * 20_000 + 300_000);
    await raconter(page, "Premier cas : l'administrateur de l'organisme de démonstration se connecte. Le menu principal porte chacune des fonctionnalités : horaire, disponibilités, absences, confection annuelle, gestion des quarts, pointage, feuilles de temps et administration.");
    await connecter(page, "admin", MDP);

    const section = page.getByTestId("nav-group-urgencessante");
    await expect(section).toHaveCount(1);
    // Les rubriques ont des titres fixes (« Menu principal », « Administration », « Aide et compte ») : le module
    // ne nomme plus la rubrique, il la remplit.
    await expect(section).toContainText("Menu principal");

    /*
      **La présence, et non le dépliage.** Le contenu d'un `<details>` replié reste dans le
      document : `toHaveCount(1)` le voit, alors qu'attendre sa visibilité ferait dépendre l'essai
      de l'ordre dans lequel les sections s'ouvrent — un détail d'interface, pas une exigence.
    */
    const manquantes: string[] = [];
    for (const entree of MENU_DES_PLANCHES) {
      /*
        **Attendre, et non compter tout de suite.** Le menu se rend une première fois avant que la
        liste des modules de l'établissement ne soit connue, puis se recompose : lire le compte à
        cet instant-là mesure l'avancement du chargement, pas la présence de l'entrée. C'est ce
        qui a fait manquer les cinq dernières entrées à la première écriture de cet essai.
      */
      const presente = await page.getByTestId(entree.testId).first()
        .waitFor({ state: "attached", timeout: 20_000 }).then(() => true).catch(() => false);
      if (!presente) manquantes.push(`${entree.libelle} (${entree.testId})`);
    }
    expect(manquantes, "entrées des planches absentes du menu").toEqual([]);

    // Et chaque écran porte le chrome des planches : il est posé une fois, pour tous.
    await raconter(page, "Toutes les entrées sont présentes. On ouvre maintenant chaque écran du menu, l'un après l'autre : chacun doit s'ouvrir avec son en-tête.");
    const sansChrome: string[] = [];
    for (const entree of MENU_DES_PLANCHES) {
      const ecran = entree.testId.replace("nav-", "");
      await page.goto(`/${ecran}`);
      const porte = await page.getByTestId("maquette-bandeau")
        .waitFor({ state: "visible", timeout: 30_000 }).then(() => true).catch(() => false);
      if (!porte) sansChrome.push(ecran);
    }
    expect(sansChrome, "écrans sans le bandeau des planches").toEqual([]);
  });

  test("les sites, les quarts et les personnes sont ceux des planches", async ({ page }) => {
    test.setTimeout(120_000);
    await connecter(page, "admin", MDP);

    // Les trois sites de la planche s01, à l'écran des milieux.
    await raconter(page, "Deuxième cas : les données de démonstration. Voici les sites, ou milieux, de l'organisme.");
    await page.goto("/usmilieux");
    await expect(page.getByTestId("mil-ecran")).toBeVisible({ timeout: 30_000 });
    // Le premier site attendu sert de **signal de chargement** : la liste arrive après le rendu,
    // et lire son texte tout de suite mesurerait la vitesse du réseau, pas le contenu semé.
    await expect(page.locator("body")).toContainText(donnees.milieux[0].libelle, { timeout: 30_000 });
    for (const m of donnees.milieux) await expect(page.locator("body")).toContainText(m.libelle);

    // Les personnes des planches s02 et s07, à l'écran des ressources.
    await raconter(page, "Puis les ressources planifiables : chaque personne qui travaille sur le terrain a sa fiche. Les ressources humaines, qui ne font pas de quarts, n'y figurent pas.");
    await page.goto("/usressources");
    await expect(page.getByTestId("usressources-titre")).toBeVisible({ timeout: 30_000 });
    const planifiables = donnees.personnes.filter((p) => p.matricule);
    await expect(page.locator("body")).toContainText(planifiables[0].nom, { timeout: 30_000 });
    for (const p of planifiables) await expect(page.locator("body")).toContainText(p.nom);

    /*
      **Et celle qui n'est pas planifiable n'y est pas.** Les ressources humaines ont un compte et
      un espace personnel, pas de quarts : leur créer une ressource les ferait apparaître dans les
      bassins de candidats, et c'est ainsi qu'on finit par planifier quelqu'un qui n'est pas sur le
      terrain. L'absence est donc une preuve, pas un oubli.
    */
    for (const p of donnees.personnes.filter((x) => !x.matricule)) {
      await expect(page.locator("body"), `${p.nom} n'est pas planifiable`).not.toContainText(p.nom);
    }
  });

  /**
   * **Le menu ne porte que ce que cet organisme fait** — la question du 2 novembre 2026.
   *
   * « Pourquoi les modules Comptabilité et Paie & RH sont-ils présents ? » Parce que ces deux
   * sections ne portent aucun code de module et hébergent encore des écrans du **socle** :
   * `rapports`, `countrymodules` et `accountant` pour l'une, `orgchart` pour l'autre. Une section
   * paraît dès qu'une de ses entrées est visible — il suffit donc d'un écran du socle. Aucun
   * écran de comptabilité ni de paie n'était ouvert : c'était un défaut de rangement, pas
   * d'abonnement (mesuré par `frontend/tests/tenant-urgences-sante-menu.test.ts`).
   *
   * Le semoir pose donc le **masquage par organisme** — le réglage prévu pour cela — et cet essai
   * en constate l'effet là où il compte : dans le menu que l'évaluateur voit.
   */
  test("le menu ne montre que le cœur et Urgences-santé : les sections d'un autre métier sont masquées", async ({ page }) => {
    test.setTimeout(120_000);
    await connecter(page, "admin", MDP);

    /*
      **Attendre la disparition, et non compter tout de suite.**

      Le menu porte `aria-busy` tant que les modules et les vues masquées ne sont pas connus (`connecter` attend
      sa fin) ; l'attente de détachement reste, par prudence, pour une section qui partirait au rendu suivant.
    */
    await raconter(page, "Troisième cas : le menu ne montre que ce que fait cet organisme. Les sections d'un autre métier, comme la comptabilité ou la facturation, sont masquées.");
    await expect(page.getByTestId("nav-group-urgencessante")).toHaveCount(1, { timeout: 30_000 });

    const absentes: string[] = [];
    for (const [cle, libelle] of [["accounting", "Comptabilité"], ["hr", "Paie & RH"],
                                  ["santequebec", "Santé Québec"], ["sales", "Ventes & facturation"],
                                  ["time", "Temps & présences"]] as const) {
      // Un en-tête de section **absent du document**, et non simplement replié : le masquage
      // retire l'entrée, il ne la cache pas en CSS.
      const partie = await page.getByTestId(`nav-group-${cle}`)
        .waitFor({ state: "detached", timeout: 30_000 }).then(() => true).catch(() => false);
      if (!partie) absentes.push(`${libelle} (${cle})`);
    }
    expect(absentes, "sections qui n'ont rien à faire chez un organisme préhospitalier").toEqual([]);

    // Et ce qui reste est utilisable : la porte d'entrée, le module, mon espace, l'administration.
    for (const cle of ["urgencessante", "space", "admin"]) {
      await expect(page.getByTestId(`nav-group-${cle}`), `section ${cle}`).toHaveCount(1);
    }

    /*
      Le masquage **ferme aussi l'URL directe** : sans cela, un signet ramènerait l'écran, et le
      réglage ne serait qu'un rangement de menu. (Ce n'est pas pour autant un contrôle d'accès :
      la frontière de sécurité reste le module, gardé au serveur en refus par défaut.)
    */
    await raconter(page, "Et une vue masquée ne s'ouvre pas non plus par son adresse directe : on revient au tableau de bord.");
    await page.goto("/orgchart");
    await expect(page.getByTestId("dashboard-page"), "une vue masquée ne s'ouvre pas par son adresse")
      .toBeVisible({ timeout: 30_000 });
  });

  /**
   * **La communication du personnel** (planche P11), servie dans Urgences-santé.
   *
   * La fonction vient du module Santé Québec et n'y est pas recopiée : si quelqu'un la dupliquait,
   * les deux messageries divergeraient au premier correctif. Ce qui se prouve ici est qu'elle est
   * **atteignable depuis ce module**, qu'elle porte son nom, et qu'elle montre le fil semé.
   */
  test("Julie Roy écrit à ses collègues depuis Urgences-santé, et le fil porte des noms", async ({ page }) => {
    // Une connexion, deux navigations et un passage axe-core : le défaut de 30 s ne couvrait que
    // la connexion, et l'essai expirait sur un écran qui affichait tout ce qu'on lui demandait.
    test.setTimeout(120_000);
    await connecter(page, "julie.roy", MDP);
    await raconter(page, "Quatrième cas : Julie Roy, paramédic, ouvre la messagerie du personnel depuis le menu. Elle retrouve la conversation de son équipe, avec le nom de chacun.");
    await ouvrirNav(page, "nav-usmessages");
    await expect(page.getByTestId("sq-titre-usmessages")).toBeVisible({ timeout: 30_000 });
    // Le bandeau nomme **ce** module : il nommait « Santé Québec » en dur, et changer de page
    // faisait croire qu'on avait changé d'application.
    await expect(page.getByTestId("maquette-bandeau")).toContainText("Urgences-santé");

    // Le fil semé, par son sujet — et non un fil quelconque.
    await expect(page.locator("body")).toContainText(donnees.communication.conversation.sujet, { timeout: 30_000 });

    const violations = await auditA11y(page);
    expect(violations, "axe-core sur la messagerie du personnel").toEqual([]);
  });

  test("Julie Roy voit son horaire, et c'est le sien", async ({ page }) => {
    test.setTimeout(120_000);
    await connecter(page, "julie.roy", MDP);
    await raconter(page, "Dernier cas de ce chapitre : Julie ouvre Mon horaire. Elle voit ses propres quarts, par jour, avec le site, le poste et le statut.");
    await ouvrirNav(page, "nav-monhoraire");
    await expect(page.getByTestId("mho-titre")).toBeVisible({ timeout: 30_000 });
    // Le calendrier du mois est la vue par défaut : la table est celle de la liste de la semaine.
    await page.getByTestId("mho-vue-liste").click();
    // Les colonnes de la planche s03 : jour, quart, site, poste, statut.
    await expect(page.getByTestId("mho-table").locator("thead th"))
      .toHaveText([/Jour/, /Quart/, /Site/, /Poste/, /Statut/]);

    const violations = await auditA11y(page);
    expect(violations, "axe-core sur l'horaire de l'employée").toEqual([]);
  });
});
