/**
 * **Relevés statistiques paramétrés par l'organisation** (AO 26-015-DO, P-RF-1), de bout en bout.
 *
 * L'administratrice d'Urgences ouvre le constructeur de rapports, choisit la source d'effectifs
 * « comblement des quarts », la regroupe par unité racine et par unité, pose sa période, un filtre
 * sur la nature de quart et deux indicateurs — le **taux** de places pourvues (rapporté au nombre
 * de places) et le nombre d'équipages sous le minimum. Elle produit le relevé, l'**enregistre**
 * pour l'organisme, le reproduit depuis la liste des rapports enregistrés et l'exporte en Excel,
 * puis en PDF. Chaque état passe axe-core.
 */
import { test, expect, request as pwRequest, type APIRequestContext } from "@playwright/test";
import { superadminContext } from "./helpers/auth";
import { createTenant, selectTenant, setTenantModules, TENANT_ADMIN_PASS, TENANT_ADMIN_USER, uniq } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";
import { expectNoA11yViolations } from "./helpers/a11y";
import { optionsFilmees } from "./helpers/film";
import { raconter } from "./helpers/recit";

test.use({ storageState: { cookies: [], origins: [] } });

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;

async function poser(ctx: APIRequestContext, tenantId: string, chemin: string, data: unknown) {
  const r = await ctx.post(`${API}${chemin}`, { headers: { "X-Tenant-Id": tenantId }, data });
  if (!r.ok()) throw new Error(`amorce ${chemin} : ${r.status()} ${await r.text()}`);
  return r.json();
}

async function adminDeLOrganisme(tenantId: string): Promise<APIRequestContext> {
  const ctx = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
  const { token } = await (await ctx.post(`${API}/api/auth/login`, { data: { username: TENANT_ADMIN_USER, password: TENANT_ADMIN_PASS, tenantId } })).json();
  await ctx.dispose();
  return pwRequest.newContext({ storageState: { cookies: [], origins: [] }, extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
}

/**
 * L'unité EST, sous la racine US, et sa semaine publiée : le 15, un équipage d'une place pourvue ;
 * le 16, un équipage de deux places au minimum de deux, une seule pourvue — sous le minimum.
 */
async function amorcer(tenantId: string): Promise<void> {
  const sa = await superadminContext();
  await setTenantModules(sa, tenantId, ["effectifs_planification"]);
  await sa.dispose();
  const adm = await adminDeLOrganisme(tenantId);
  try {
    const ref = (type: string, data: Record<string, unknown>) => poser(adm, tenantId, `/api/effectifs/referentiels/${type}`, { effectifDepuis: "2026-01-01", ...data });
    await ref("unites", { code: "US", libelleFr: "Urgences-santé" });
    await ref("unites", { code: "EST", libelleFr: "Opérations Est", attributs: { parent: "US" } });
    await ref("corps_emploi", { code: "PARA", libelleFr: "Paramédics" });
    await ref("natures_quart", { code: "REG", libelleFr: "Régulier", comportement: "REGULIER" });
    await ref("types_milieu", { code: "CASERNE", libelleFr: "Caserne", comportement: "CASERNE" });
    const milieu = await poser(adm, tenantId, "/api/effectifs/milieux", { code: "C12", libelle: "Caserne 12", typeCode: "CASERNE", uniteCode: "EST" });
    const julie = await poser(adm, tenantId, "/api/effectifs/planification/ressources", { matricule: "P1", nom: "Julie Roy", corpsEmploiCode: "PARA", uniteCode: "EST" });
    const p = await poser(adm, tenantId, "/api/effectifs/planification/periodes", { nom: "Semaine 3", uniteCode: "EST", debut: "2031-01-13", fin: "2031-01-19" });
    await poser(adm, tenantId, `/api/effectifs/planification/periodes/${p.id}/etat`, { etat: "CONFECTION" });
    for (const [date, nombre] of [["2031-01-15", 1], ["2031-01-16", 2]] as const) {
      const e = await poser(adm, tenantId, "/api/effectifs/planification/equipages", {
        periodeId: p.id, date, debut: 360, fin: 1080, natureCode: "REG", milieuId: milieu.id, modeAttribution: "MANUEL",
        minimumOperationnel: nombre, composition: [{ corpsEmploiCode: "PARA", nombre }],
      });
      await poser(adm, tenantId, `/api/effectifs/planification/places/${e.places[0].id}/attribuer`, { ressourceId: julie.id });
    }
    await poser(adm, tenantId, `/api/effectifs/planification/periodes/${p.id}/etat`, { etat: "PUBLIEE" });
  } finally { await adm.dispose(); }
}

test("définir, produire, enregistrer, reproduire et exporter un relevé de comblement paramétré", async ({ browser }) => {
  test.setTimeout(240_000);
  const sa = await superadminContext();
  const tenantId = await createTenant(sa, uniq("EffRel"));
  await sa.dispose();
  await amorcer(tenantId);

  const ctx = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] }, acceptDownloads: true }));
  const page = await ctx.newPage();
  await selectTenant(page, tenantId);
  await ouvrirNav(page, "nav-rapports");
  await expect(page.getByTestId("rap-enr-vide")).toBeVisible();

  // ── Définir : source, regroupements, colonnes, indicateurs, période, filtre ──────────────────
  await raconter(page, "Relevés et rapports. Le gestionnaire construit un relevé de comblement : source, les places des effectifs ; regroupé par unité ; le taux de places pourvues et le nombre d'équipages sous le minimum ; pour la semaine du 13 janvier, quarts réguliers seulement.");
  await page.getByTestId("rap-source").selectOption("effectifs_places");
  await expect(page.getByTestId("rap-periode")).toBeVisible();
  for (const axe of ["uniteNiveau1", "unite"]) {
    await page.getByTestId(`rap-grp-${axe}`).check();
    await page.getByTestId(`rap-col-${axe}`).check();
  }
  await page.getByTestId("rap-col-pourvues").check();
  await page.getByTestId("rap-agr-pourvues").selectOption("taux");
  await page.getByTestId("rap-den-pourvues").selectOption("places");
  await page.getByTestId("rap-col-equipagesSousMinimum").check();
  await page.getByTestId("rap-agr-equipagesSousMinimum").selectOption("somme");
  await page.getByTestId("rap-periode-du").fill("2031-01-13");
  await page.getByTestId("rap-periode-au").fill("2031-01-19");
  await page.getByTestId("rap-filtre-ajouter").click();
  await page.getByTestId("rap-filtre-champ-0").selectOption("natureQuart");
  await page.getByTestId("rap-filtre-val-0").fill("REG");
  await expectNoA11yViolations(page);

  // ── Produire ─────────────────────────────────────────────────────────────────────────────
  await raconter(page, "Il produit le relevé : l'unité Opérations Est est pourvue à 66,67 pour cent, avec un équipage sous le minimum.");
  await page.getByTestId("rap-executer").click();
  await expect(page.getByTestId("rap-resume")).toContainText("1 ligne(s).");
  await expect(page.getByTestId("rap-resultat")).toContainText("Places pourvues / Places (%)");
  // Trois places, deux pourvues : 66,67 % ; l'équipage du 16 est sous son minimum.
  await expect(page.getByTestId("rap-ligne-0")).toHaveText(/US\s*EST\s*66\.67\s*1/);
  await expectNoA11yViolations(page);

  // Une période oubliée est refusée nommément, sans exécuter.
  await raconter(page, "Sans date de début, le relevé est refusé, et le message dit le format attendu.");
  await page.getByTestId("rap-periode-du").fill("");
  await page.getByTestId("rap-executer").click();
  await expect(page.getByTestId("rap-erreur")).toContainText("deux dates AAAA-MM-JJ");
  await expectNoA11yViolations(page);
  await page.getByTestId("rap-periode-du").fill("2031-01-13");

  // ── Enregistrer pour l'organisme ─────────────────────────────────────────────────────────
  await raconter(page, "Il enregistre le relevé sous le nom Comblement hebdomadaire.");
  await page.getByTestId("rap-enr-nom").fill("Comblement hebdomadaire");
  await page.getByTestId("rap-enr-description").fill("Taux de places pourvues et équipages sous le minimum");
  await page.getByTestId("rap-enr-creer").click();
  await expect(page.getByTestId("rap-annonce")).toHaveText("Rapport « Comblement hebdomadaire » enregistré.");
  await expect(page.getByTestId("rap-enr-0")).toContainText("Comblement hebdomadaire");
  await expectNoA11yViolations(page);

  // ── Reproduire depuis la liste, après être reparti de zéro ───────────────────────────────
  await raconter(page, "Après rechargement, il le reproduit d'un clic : mêmes paramètres, même résultat. Puis il l'exporte, en Excel et en PDF.");
  await page.reload();
  await ouvrirNav(page, "nav-rapports");
  await page.getByTestId("rap-enr-produire-0").click();
  await expect(page.getByTestId("rap-ligne-0")).toHaveText(/US\s*EST\s*66\.67\s*1/);
  await expect(page.getByTestId("rap-source")).toHaveValue("effectifs_places");
  await expect(page.getByTestId("rap-periode-du")).toHaveValue("2031-01-13");
  await expect(page.getByTestId("rap-den-pourvues")).toHaveValue("places");
  await expect(page.getByTestId("rap-enr-maj")).toBeVisible();

  // ── Exporter en Excel et en PDF ─────────────────────────────────────────────────────────
  for (const format of ["xlsx", "pdf"]) {
    await page.getByTestId("rap-format").selectOption(format);
    const [telechargement] = await Promise.all([page.waitForEvent("download"), page.getByTestId("rap-enr-exporter-0").click()]);
    expect(telechargement.suggestedFilename()).toBe(`rapport-effectifs_places.${format}`);
    await expect(page.getByTestId("rap-annonce")).toHaveText(`Fichier rapport-effectifs_places.${format} produit.`);
  }

  // ── Modifier puis retirer ───────────────────────────────────────────────────────────────
  await raconter(page, "Il le renomme, Comblement par semaine, puis le retire de la liste.");
  await page.getByTestId("rap-enr-nom").fill("Comblement par semaine");
  await page.getByTestId("rap-enr-maj").click();
  await expect(page.getByTestId("rap-annonce")).toHaveText("Rapport « Comblement par semaine » mis à jour.");
  await page.getByTestId("rap-enr-retirer-0").click();
  await expect(page.getByTestId("rap-annonce")).toHaveText("Rapport « Comblement par semaine » retiré.");
  await expect(page.getByTestId("rap-enr-vide")).toBeVisible();
  await expectNoA11yViolations(page);
  await ctx.close();
});
