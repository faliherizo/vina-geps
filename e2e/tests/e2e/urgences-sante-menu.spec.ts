/**
 * E2E — **« je ne vois aucun écran d'Urgences-santé »**, et pourquoi c'était vrai.
 *
 * L'organisme possédait les dix modules de l'AO 26-015-DO, aucune vue n'était masquée, et les
 * dix-neuf écrans répondaient à leur URL. Ils étaient néanmoins introuvables :
 *
 *  - au **menu**, ils vivaient sous « Temps & présences » (la planification) et sous
 *    « Administration » (le paramétrage). Rien ne nommait l'avis ni le client ;
 *  - à l'**espace superadmin**, la famille s'appelait « Gestion des effectifs (préhospitalier) » :
 *    le filtre de l'écran cherche dans le libellé et le code, donc taper « Urgences-santé » —
 *    le seul mot qu'on ait envie de taper — ne ramenait rien.
 *
 * Un essai d'intégration ne voit pas ce défaut : il n'y a pas de route pour « trouvable ». Celui-ci
 * ouvre le menu comme on l'ouvre, et cherche dans l'écran du superadmin comme on y cherche.
 */
import { test, expect, type APIRequestContext } from "@playwright/test";
import { superadminContext, GLOBAL_ACCOUNT } from "./helpers/auth";
import { createTenant, setTenantModules, uniq } from "./helpers/organisme";
import { auditA11y } from "./helpers/a11y";
import { ouvrirNav } from "./helpers/nav";
import { amorcerOrganisme, modulesDeLOrganisme, seConnecterOrganisme } from "./helpers/urgences-sante";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;

test.use({ storageState: { cookies: [], origins: [] } });

/**
 * Les neuf écrans du module : les deux de pilotage et les sept de l'espace personnel. Le compte
 * fait partie de la preuve — un écran ajouté sans son enveloppe ne serait pas vu d'ici.
 */
const ECRANS_DU_MODULE = [
  "uscomblement", "usequipages", "usagph", "uspresence", "ustemps", "usreferentiels", "usjournal",
  "monhoraire", "mesdisponibilites", "mesechanges", "monagph", "monpointage", "matemps", "mesabsences",
  // La communication du personnel : les pages viennent du module Santé Québec, et leur bandeau
  // doit nommer **Urgences-santé** — il nommait l'autre module en dur, et changer de page faisait
  // croire qu'on avait changé d'application.
  "usmessages", "usappels", "usdiffusion",
];

/** Les onze codes de la famille : le compte fait partie de la preuve — en oublier un la viderait. */
const MODULES_US = [
  "effectifs", "effectifs_regles", "effectifs_planification", "effectifs_disponibilites",
  "effectifs_absences", "effectifs_echanges", "effectifs_agph", "effectifs_presence",
  "effectifs_temps", "effectifs_integrations",
  // La communication du personnel, qui ne requiert que le socle du module.
  "effectifs_communication",
];

/** Les modules réellement enregistrés pour un établissement — le quota, pas l'écran. */
async function modulesDe(ctx: APIRequestContext, tenantId: string): Promise<string[]> {
  const r = await ctx.get(`${API}/api/saas/tenants/${tenantId}`);
  if (!r.ok()) throw new Error(`lecture de l'établissement : ${r.status()} ${await r.text()}`);
  const corps = await r.json();
  return (corps.quota?.modules ?? []) as string[];
}

test.describe("26-015-DO — la section Urgences-santé et son réglage", () => {
  test("la section est au menu de l'organisme, et ses entrées ouvrent leur page", async ({ page }) => {
    // **Son budget est celui de ce qu'il mesure** : une connexion, deux navigations de menu et
    // un passage axe-core. Les trente secondes d'un essai ordinaire ne couvraient que la
    // connexion, et l'essai expirait sur une page parfaitement fonctionnelle.
    test.setTimeout(120_000);
    const sa = await superadminContext();
    let tenantId = "";
    try {
      tenantId = await createTenant(sa, uniq("US-MENU"));
      await setTenantModules(sa, tenantId, modulesDeLOrganisme);
    } finally { await sa.dispose(); }

    await seConnecterOrganisme(page, tenantId);

    // La rubrique elle-même (planche s01) : « Menu principal », le tableau de bord en tête. Le
    // client se nomme dans la barre du haut, à côté de la marque VINA GePS.
    const section = page.getByTestId("nav-group-urgencessante");
    await expect(section).toHaveCount(1);
    await expect(section).toContainText("Menu principal");
    await expect(page.getByTestId("marque-produit")).toContainText("VINA GePS");

    // Une entrée de menu, une page : « Comblement » ouvre son écran, et non un onglet.
    await ouvrirNav(page, "nav-uscomblement");
    await expect(page.getByTestId("uscomblement-titre")).toBeVisible({ timeout: 30_000 });

    // L'administration du module, devenue une notion de pages.
    await ouvrirNav(page, "nav-usreferentiels");
    await expect(page.getByTestId("usreferentiels-titre")).toBeVisible({ timeout: 30_000 });

    // Menu déployé : un état non audité est un état non conforme jusqu'à preuve du contraire.
    const violations = await auditA11y(page);
    expect(violations, "axe-core sur le menu déployé").toEqual([]);
  });

  /**
   * **Les neuf écrans portent le chrome des maquettes** (ADR 0069).
   *
   * Le bandeau marine est posé une fois, dans `PageUrgencesSante` : si un écran l'a perdu, c'est
   * qu'on a oublié de l'envelopper, et il s'écartera des planches sans que personne le remarque —
   * c'est la dérive écran par écran qu'a connue le module Santé Québec.
   */
  test("chaque écran du module porte le bandeau des maquettes, et passe axe-core", async ({ page }) => {
    // **Son budget est celui de ce qu'il mesure**, pas celui d'un essai ordinaire : neuf écrans
    // ouverts et passés à axe-core. Un budget fixe le ferait expirer au neuvième le jour où l'on
    // ajoute un écran — c'est-à-dire précisément quand il devient utile.
    test.setTimeout(ECRANS_DU_MODULE.length * 20_000 + 60_000);
    const sa = await superadminContext();
    let tenantId = "";
    try {
      tenantId = await createTenant(sa, uniq("US-MAQ"));
      await setTenantModules(sa, tenantId, modulesDeLOrganisme);
    } finally { await sa.dispose(); }

    await seConnecterOrganisme(page, tenantId);

    const violations: string[] = [];
    for (const ecran of ECRANS_DU_MODULE) {
      await page.goto(`/${ecran}`);
      await expect(page.getByTestId("maquette-bandeau"), `bandeau absent de ${ecran}`).toBeVisible({ timeout: 30_000 });
      await expect(page.getByTestId("maquette-bandeau")).toContainText("Urgences-santé");
      const fautes = await auditA11y(page);
      if (fautes.length) violations.push(`${ecran} : ${fautes.map((v) => v.id).join(", ")}`);
    }
    expect(violations, "écrans du module en violation d'axe-core").toEqual([]);
  });

  /**
   * **Mon horaire** (maquette s03) — la seule planche qui n'avait pas d'hôte.
   *
   * La donnée existait : l'horaire publié d'une personne sert déjà à proposer un échange. Ce qui
   * manquait était de pouvoir le **lire comme un horaire**, sans dérouler un sélecteur dans un
   * écran d'échanges.
   */
  test("« Mon horaire » existe, et dit quand, où et à quel titre", async ({ page }) => {
    const sa = await superadminContext();
    let tenantId = "";
    try {
      tenantId = await createTenant(sa, uniq("US-HOR"));
      await setTenantModules(sa, tenantId, modulesDeLOrganisme);
    } finally { await sa.dispose(); }
    // L'horaire n'a de sens que pour un compte **relié à une ressource** : sans elle, l'écran
    // dit à juste titre qu'il n'y a personne à planifier, et l'essai mesurerait l'état vide.
    await amorcerOrganisme(tenantId, { avecTempsEtCollecte: true });

    await seConnecterOrganisme(page, tenantId);
    await ouvrirNav(page, "nav-monhoraire");
    await expect(page.getByTestId("mho-titre")).toBeVisible({ timeout: 30_000 });
    // Le calendrier du mois est la vue par défaut : la table est celle de la liste de la semaine.
    await page.getByTestId("mho-vue-liste").click();
    // Les cinq colonnes de la planche : jour, quart, site, poste, statut.
    const entetes = page.getByTestId("mho-table").locator("thead th");
    await expect(entetes).toHaveText([/Jour/, /Quart/, /Site/, /Poste/, /Statut/]);
  });

  test("le superadmin trouve le module en tapant « Urgences-santé », et l'accorde d'un geste", async ({ page }) => {
    const sa = await superadminContext();
    let tenantId = "";
    try {
      // Le forfait accorde le module à la création ; on part d'un organisme sans aucun module.
      tenantId = await createTenant(sa, uniq("US-SUPER"), "professional");
      await setTenantModules(sa, tenantId, []);
      // ⚠️ **Pas** `/api/varpay/modules/status` : cette route ancienne énumère neuf codes en dur et
      // rend `undefined` pour tout module ajouté depuis — un essai écrit contre elle passerait au
      // vert sans rien mesurer. Le quota de l'établissement est la source.
      const avant = await modulesDe(sa, tenantId);
      expect(avant, "l'organisme part sans module").not.toContain("effectifs");
    } finally { await sa.dispose(); }

    await page.goto("/");
    await page.getByTestId("login-user").fill("superadmin");
    await page.getByTestId("login-pass").fill("superadmin");
    await page.getByTestId("login-tenant").selectOption(GLOBAL_ACCOUNT);
    await page.getByTestId("login-submit").click();

    await ouvrirNav(page, "nav-group-admin").catch(() => {});
    await ouvrirNav(page, "nav-superadmin");
    await page.getByTestId("sa-tab-modules").click();
    await expect(page.getByTestId("tenant-modules-manager")).toBeVisible();
    await page.getByTestId("modmgr-tenant").selectOption(tenantId);

    // **Le défaut constaté** : c'est ce mot-là que l'on tape, et il ne ramenait rien.
    await page.getByTestId("modmgr-filtre").fill("Urgences-santé");
    await expect(page.getByTestId("modmgr-aucun")).toHaveCount(0);
    const famille = page.getByTestId("modmgr-famille-effectifs");
    await expect(famille).toBeVisible();
    for (const code of MODULES_US) {
      await expect(page.getByTestId(`modmgr-toggle-${code}`), `module ${code} absent du filtre`).toHaveCount(1);
    }

    // Le geste réel n'est pas dix clics : « donne-lui tout Urgences-santé ».
    await page.getByTestId("modmgr-famille-tout-effectifs").click();
    await expect(page.getByTestId("sa-success")).toBeVisible();
    for (const code of MODULES_US) {
      await expect(page.getByTestId(`modmgr-toggle-${code}`), `module ${code} non accordé`).toBeChecked();
    }

    // La garde serveur suit le clic : sans cela on n'aurait réglé qu'un menu.
    const sa2 = await superadminContext();
    try {
      const apres = await modulesDe(sa2, tenantId);
      expect(MODULES_US.filter((c) => !apres.includes(c)), "codes non enregistrés côté serveur").toEqual([]);
      // Et la communication n'exige plus que le socle du module : aucun code d'un autre produit.
      expect(apres.filter((c) => !c.startsWith("effectifs")), "rien d'autre que le module").toEqual([]);
    } finally { await sa2.dispose(); }
  });
});
