/**
 * **Gestion des effectifs — lot R10 (intégrations), de bout en bout.** AO 26-015-DO.
 *
 * L'administrateur active le SAAV et le bus (ESB) en mode simulateur, publie les équipages du jour et
 * relaie le journal au bus ; les messages déposés apparaissent dans la boîte d'envoi. Il passe le PFT
 * en HTTP : une adresse non chiffrée est refusée avec son motif (E-TN-7, P-TN-7). Chaque état passe
 * axe-core.
 */
import { test, expect, request as pwRequest, type APIRequestContext } from "@playwright/test";
import { superadminContext } from "./helpers/auth";
import { createTenant, selectTenant, setTenantModules, TENANT_ADMIN_PASS, TENANT_ADMIN_USER, uniq } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";
import { expectNoA11yViolations } from "./helpers/a11y";
import { optionsFilmees } from "./helpers/film";

test.use({ storageState: { cookies: [], origins: [] } });

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;

async function poser(ctx: APIRequestContext, tenantId: string, chemin: string, data: unknown, methode: "post" | "patch" = "post") {
  const r = await ctx[methode](`${API}${chemin}`, { headers: { "X-Tenant-Id": tenantId }, data });
  if (!r.ok()) throw new Error(`amorce ${chemin} : ${r.status()} ${await r.text()}`);
  return r.json();
}

async function adminDeLOrganisme(tenantId: string): Promise<APIRequestContext> {
  const ctx = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
  const { token } = await (await ctx.post(`${API}/api/auth/login`, { data: { username: TENANT_ADMIN_USER, password: TENANT_ADMIN_PASS, tenantId } })).json();
  await ctx.dispose();
  return pwRequest.newContext({ storageState: { cookies: [], origins: [] }, extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
}

/** Un équipage publié le 15 janvier 2031, tenu par Julie. */
async function amorcer(tenantId: string): Promise<void> {
  const adm = await adminDeLOrganisme(tenantId);
  try {
    const sa = await superadminContext();
    await setTenantModules(sa, tenantId, ["effectifs_integrations"]);
    await sa.dispose();
    const ref = (type: string, data: Record<string, unknown>) => poser(adm, tenantId, `/api/effectifs/referentiels/${type}`, { effectifDepuis: "2026-01-01", ...data });
    await ref("unites", { code: "EST", libelleFr: "Opérations Est" });
    await ref("corps_emploi", { code: "PARA", libelleFr: "Paramédics" });
    await ref("natures_quart", { code: "REG", libelleFr: "Régulier", comportement: "REGULIER" });
    await ref("types_milieu", { code: "CASERNE", libelleFr: "Caserne", comportement: "CASERNE" });
    const milieu = await poser(adm, tenantId, "/api/effectifs/milieux", { code: "C12", libelle: "Caserne 12", typeCode: "CASERNE", uniteCode: "EST" });
    const julie = await poser(adm, tenantId, "/api/effectifs/planification/ressources", { matricule: "P1", nom: "Julie Roy", corpsEmploiCode: "PARA", uniteCode: "EST" });
    const p = await poser(adm, tenantId, "/api/effectifs/planification/periodes", { nom: "Semaine 3", uniteCode: "EST", debut: "2031-01-13", fin: "2031-01-19" });
    await poser(adm, tenantId, `/api/effectifs/planification/periodes/${p.id}/etat`, { etat: "CONFECTION" });
    const e = await poser(adm, tenantId, "/api/effectifs/planification/equipages", {
      periodeId: p.id, date: "2031-01-15", debut: 360, fin: 1080, natureCode: "REG", milieuId: milieu.id, modeAttribution: "MANUEL", composition: [{ corpsEmploiCode: "PARA", nombre: 1 }],
    });
    await poser(adm, tenantId, `/api/effectifs/planification/places/${e.places[0].id}/attribuer`, { ressourceId: julie.id });
    await poser(adm, tenantId, `/api/effectifs/planification/periodes/${p.id}/etat`, { etat: "PUBLIEE" });
  } finally { await adm.dispose(); }
}

test("configurer les connecteurs, publier les équipages et relayer le journal", async ({ browser }) => {
  test.setTimeout(240_000);
  const sa = await superadminContext();
  const tenantId = await createTenant(sa, uniq("EffInt"));
  await sa.dispose();
  await amorcer(tenantId);

  const ctx = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] } }));
  const page = await ctx.newPage();
  await selectTenant(page, tenantId);
  await ouvrirNav(page, "nav-usintegrations");
  await expect(page.getByTestId("int-c-SAAV")).toContainText("Coupé");
  await expectNoA11yViolations(page);

  const activer = async (code: string) => {
    await page.getByTestId(`int-c-${code}-configurer`).click();
    await page.getByTestId("int-actif").check();
    await page.getByTestId("int-enregistrer").click();
    await expect(page.getByTestId("int-bilan")).toContainText(`${code} : activé, mode simulateur.`);
  };
  await activer("SAAV");
  await activer("ESB");

  await page.getByTestId("int-gestes").locator("summary").click();
  await page.getByTestId("int-date").fill("2031-01-15");
  await page.getByTestId("int-unite").selectOption("EST");
  await page.getByTestId("int-equipages").click();
  await expect(page.getByTestId("int-bilan")).toContainText("Équipages déposés pour le SAAV.");
  await page.getByTestId("int-relais-esb").click();
  await expect(page.getByTestId("int-bilan")).toContainText("Bus : 1 événement(s) déposé(s).");
  await expect(page.getByTestId("int-messages")).toContainText("SAAV · EQUIPAGE");
  await expect(page.getByTestId("int-messages")).toContainText("ESB · EVENEMENT");
  await expectNoA11yViolations(page);

  await page.getByTestId("int-c-PFT-configurer").click();
  await page.getByTestId("int-mode").selectOption("HTTP");
  await page.getByTestId("int-url").fill("http://paie.exemple.ca");
  await page.getByTestId("int-jeton").fill("pft_jeton");
  await expect(page.getByTestId("int-jeton")).toHaveValue("PFT_JETON");
  await page.getByTestId("int-enregistrer").click();
  await expect(page.getByTestId("int-configuration-erreur")).toContainText("un protocole sécurisé (https) est exigé");
  await expectNoA11yViolations(page);

  // Les lots par SFTP (addenda 06, réponse 3) : sans empreinte de l'hôte, refusé ; avec, enregistré.
  await page.getByTestId("int-mode").selectOption("SFTP");
  await expect(page.getByTestId("int-url")).toHaveAttribute("aria-describedby", "int-url-aide");
  await page.getByTestId("int-url").fill("sftp://nexu@sftp.exemple.ca/depot");
  await page.getByTestId("int-jeton").fill("pft_cle_sftp");
  await page.getByTestId("int-enregistrer").click();
  await expect(page.getByTestId("int-configuration-erreur")).toContainText("l'empreinte de la clé d'hôte est exigée");
  await expectNoA11yViolations(page);
  await page.getByTestId("int-url").fill(`sftp://nexu@sftp.exemple.ca/depot?empreinte=SHA256:${"A".repeat(43)}`);
  await page.getByTestId("int-enregistrer").click();
  await expect(page.getByTestId("int-bilan")).toContainText("PFT : désactivé, mode SFTP.");
  await expect(page.getByTestId("int-c-PFT")).toContainText("SFTP — sftp://nexu@sftp.exemple.ca/depot");
  await ctx.close();
});
