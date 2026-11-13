/**
 * **Gestion des effectifs — lot R3 (planification), de bout en bout.** AO 26-015-DO.
 *
 * Le matin d'un planificateur : le comblement signale un quart sous son minimum ; il attribue la
 * place vacante, le quart devient opérationnel. Le soir, une règle de repos refuse un renfort : les
 * motifs s'affichent, il force l'attribution **avec une justification**, et la place porte la
 * marque « forcée ». Il crée ensuite un quart ad hoc, et une surcharge de quota datée d'hier est
 * refusée avec son motif. Chaque état visité passe axe-core.
 */
import { test, expect, type APIRequestContext } from "@playwright/test";
import { superadminContext } from "./helpers/auth";
import { createTenant, selectTenant, setTenantModules, uniq } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";
import { expectNoA11yViolations } from "./helpers/a11y";

test.use({ storageState: { cookies: [], origins: [] } });

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;
const DEBUT = "2030-03-04";

async function poser(sa: APIRequestContext, tenantId: string, chemin: string, data: unknown, methode: "post" | "put" = "post") {
  const r = await sa[methode](`${API}${chemin}`, { headers: { "X-Tenant-Id": tenantId }, data });
  if (!r.ok()) throw new Error(`amorce ${chemin} : ${r.status()} ${await r.text()}`);
  return r.json();
}

/** Une caserne, deux paramédics, deux postes de jour, une semaine générée : l'état d'un matin ordinaire. */
async function amorcer(tenantId: string): Promise<void> {
  const sa = await superadminContext();
  try {
    await setTenantModules(sa, tenantId, ["effectifs_planification"]);
    const ref = (type: string, data: Record<string, unknown>) => poser(sa, tenantId, `/api/effectifs/referentiels/${type}`, { effectifDepuis: "2026-01-01", ...data });
    await ref("unites", { code: "EST", libelleFr: "Opérations Est" });
    await ref("corps_emploi", { code: "PARA", libelleFr: "Paramédics" });
    await ref("statuts_emploi", { code: "TC", libelleFr: "Temps complet", comportement: "TEMPS_COMPLET" });
    await ref("natures_quart", { code: "REG", libelleFr: "Régulier", comportement: "REGULIER" });
    await ref("natures_quart", { code: "ADH", libelleFr: "Ad hoc", comportement: "AD_HOC" });
    await ref("plages_horaires", { code: "J", libelleFr: "Jour", attributs: { debut: 360, fin: 1080 } });
    await ref("types_milieu", { code: "CASERNE", libelleFr: "Caserne", comportement: "CASERNE" });
    const milieu = await poser(sa, tenantId, "/api/effectifs/milieux", { code: "C12", libelle: "Caserne 12", typeCode: "CASERNE", uniteCode: "EST" });
    for (const [matricule, nom] of [["P1", "Julie Roy"], ["P2", "Marc Dubé"]]) {
      await poser(sa, tenantId, "/api/effectifs/planification/ressources", { matricule, nom, corpsEmploiCode: "PARA", uniteCode: "EST", statutEmploiCode: "TC" });
    }
    const cycle = [["J", null, null, null, null, null, null]];
    const postes = [];
    for (const code of ["3412-A", "3412-B"]) {
      postes.push((await poser(sa, tenantId, "/api/effectifs/planification/postes", {
        code, libelle: `PSA ${code}`, uniteCode: "EST", statutEmploiCode: "TC", corpsEmploiCode: "PARA", milieuId: milieu.id, natureCode: "REG", cycle, debutCycle: DEBUT,
      })).id);
    }
    await poser(sa, tenantId, "/api/effectifs/planification/postes/publier", { ids: postes });
    const periode = await poser(sa, tenantId, "/api/effectifs/planification/periodes", { nom: "Semaine 10", uniteCode: "EST", debut: DEBUT, fin: "2030-03-10" });
    await poser(sa, tenantId, `/api/effectifs/planification/periodes/${periode.id}/etat`, { etat: "CONFECTION" });
    await poser(sa, tenantId, `/api/effectifs/planification/periodes/${periode.id}/generer`, {});
    await poser(sa, tenantId, "/api/effectifs/regles", {
      contexte: "ATTRIBUTION", cle: "repos-minimal", condition: { fait: "reposHeures", op: "<", valeur: 8 }, action: "BLOQUER",
      message: "Repos de {reposHeures} h : il en faut 8",
    });
  } finally { await sa.dispose(); }
}

test("combler un quart, forcer une attribution justifiée, créer un quart ad hoc", async ({ page }) => {
  const tenantId = await createTenant(page.request, uniq("EffPlan"));
  await amorcer(tenantId);
  await selectTenant(page, tenantId);
  // « Planification des effectifs » était une page à douze onglets ; depuis le 2 novembre 2026,
  // chaque onglet est une page à son adresse (« un menu, une page »).
  await ouvrirNav(page, "nav-uscomblement");
  await expect(page.getByTestId("uscomblement-titre")).toBeVisible();

  // ── Le comblement signale le quart du lundi sous son minimum ───────────────────────────
  await expect(page.getByTestId("pla-tuile-sous-minimum")).toContainText("1 quart(s)");
  await expectNoA11yViolations(page);

  // ── Attribuer les deux places vacantes du lundi ────────────────────────────────────────
  await ouvrirNav(page, "nav-usequipages");
  const quart = page.locator('[data-testid^="pla-eq-etat-"]').first();
  await expect(quart).toContainText(/Sous le minimum/);
  for (const libelle of ["Julie Roy (P1)", "Marc Dubé (P2)"]) {
    await page.locator('[data-testid^="pla-place-attribuer-"]').first().click();
    await page.getByTestId("pla-att-ressource").selectOption({ label: libelle });
    await page.getByTestId("pla-att-attribuer").click();
    await expect(page.getByTestId("pla-eq-succes")).toContainText("Place attribuée");
  }
  await expect(quart).toContainText(/Opérationnel \(2\/2\)/);

  // ── Un renfort de soirée collé au quart de jour : la règle de repos s'y oppose ─────────
  await page.getByTestId("pla-eq-adhoc").click();
  await page.getByTestId("pla-ah-date").fill(DEBUT);
  await page.getByTestId("pla-ah-debut").fill("18:00");
  await page.getByTestId("pla-ah-fin").fill("22:00");
  await page.getByTestId("pla-ah-nature").selectOption("ADH");
  await page.getByTestId("pla-ah-milieu").selectOption({ index: 1 });
  await page.getByTestId("pla-ah-corps-0").selectOption("PARA");
  await expectNoA11yViolations(page);
  await page.getByTestId("pla-ah-creer").click();
  await expect(page.getByTestId("pla-eq-succes")).toContainText("Quart ad hoc");

  await page.locator('[data-testid^="pla-place-attribuer-"]').first().click();
  await page.getByTestId("pla-att-ressource").selectOption({ index: 1 });
  await page.getByTestId("pla-att-attribuer").click();
  await expect(page.getByTestId("pla-att-refus")).toBeVisible();
  await expect(page.getByTestId("pla-att-motif-0")).toContainText("Repos de 0 h : il en faut 8");
  await expectNoA11yViolations(page);

  // ── Forcer, avec justification (E-GN-11) ────────────────────────────────────────────────
  await page.getByTestId("pla-att-justification").fill("Aucun autre paramédic disponible, appel massif");
  await page.getByTestId("pla-att-forcer").click();
  await expect(page.getByTestId("pla-eq-succes")).toContainText("Attribution forcée");
  await expect(page.getByTestId("pla-eq-tableau")).toContainText("forcée");
});

test("une surcharge de quota datée d'hier est refusée, et l'écran dit pourquoi", async ({ page }) => {
  const tenantId = await createTenant(page.request, uniq("EffQuota"));
  await amorcer(tenantId);
  await selectTenant(page, tenantId);
  await ouvrirNav(page, "nav-usquotas");
  await page.getByTestId("pla-quota-definir").click();
  await page.getByTestId("pla-quo-uniteCode").selectOption("EST");
  await page.getByTestId("pla-quo-quota").fill("2");
  await page.getByTestId("pla-quo-enregistrer").click();
  await expect(page.getByTestId("pla-quotas-succes")).toBeVisible();

  await page.getByTestId("pla-quota-surcharger-EST|*|*").click();
  const hier = new Date(Date.now() - 86_400_000).toISOString().slice(0, 16);
  const demain = new Date(Date.now() + 86_400_000).toISOString().slice(0, 16);
  await page.getByTestId("pla-sur-depuis").fill(hier);
  await page.getByTestId("pla-sur-jusqua").fill(demain);
  await page.getByTestId("pla-sur-motif").fill("Événement");
  await page.getByTestId("pla-sur-enregistrer").click();
  await expect(page.getByTestId("pla-sur-erreur")).toContainText(/rétroactive/);
  await expectNoA11yViolations(page);
});

test("modifier deux postes d'un seul geste : leurs quarts publiés suivent ; puis ajuster un quart de la période publiée (E-CH-3, E-GR-5)", async ({ page }) => {
  const tenantId = await createTenant(page.request, uniq("EffLot"));
  await amorcer(tenantId);
  const sa = await superadminContext();
  try {
    await poser(sa, tenantId, "/api/effectifs/referentiels/plages_horaires", { effectifDepuis: "2026-01-01", code: "N", libelleFr: "Nuit", attributs: { debut: 1080, fin: 360 } });
    const periodes = await (await sa.get(`${API}/api/effectifs/planification/periodes`, { headers: { "X-Tenant-Id": tenantId } })).json();
    await poser(sa, tenantId, `/api/effectifs/planification/periodes/${periodes[0].id}/etat`, { etat: "PUBLIEE" });
  } finally { await sa.dispose(); }
  await selectTenant(page, tenantId);
  await ouvrirNav(page, "nav-uspostes");

  // ── Sélection multiple : les deux postes, un seul tiroir, tout ou rien ──────────────────────
  await page.getByTestId("pla-poste-choix-3412-A").check();
  await page.getByTestId("pla-poste-choix-3412-B").check();
  await page.getByTestId("pla-postes-lot").click();
  await expect(page.getByTestId("pla-lot-fiche")).toBeVisible();
  await page.getByTestId("pla-lot-enregistrer").click();
  await expect(page.getByTestId("pla-lot-vide")).toContainText("Choisissez au moins un changement");
  await page.getByTestId("pla-lot-remplacer-cycle").check();
  await page.getByTestId("pla-lot-cycle-0").selectOption("N");
  await expectNoA11yViolations(page);
  await page.getByTestId("pla-lot-enregistrer").click();
  await expect(page.getByTestId("pla-postes-succes")).toContainText("2 poste(s) modifié(s) d'un seul geste");
  await expect(page.getByTestId("pla-postes-succes")).toContainText("2 déplacé(s)");
  await expectNoA11yViolations(page);

  // ── Le quart du lundi est devenu un quart de nuit ; on l'ajuste dans la période publiée ─────
  await ouvrirNav(page, "nav-usequipages");
  const ajuster = page.locator('[data-testid^="pla-eq-ajuster-"]').first();
  await expect(page.getByTestId("pla-eq-tableau")).toContainText("18:00");
  await ajuster.click();
  await page.getByTestId("pla-aj-debut").fill("19:00");
  await page.getByTestId("pla-aj-minimum").fill("1");
  await page.getByTestId("pla-aj-note").fill("Arrivée retardée d'une heure");
  await expectNoA11yViolations(page);
  await page.getByTestId("pla-aj-enregistrer").click();
  await expect(page.getByTestId("pla-eq-succes")).toContainText("Quart ajusté");
  await expect(page.getByTestId("pla-eq-tableau")).toContainText("19:00");

  // ── Tout est consigné, avec son auteur et son heure (E-GN-9) ─────────────────────────────────
  const sa2 = await superadminContext();
  try {
    const journal = await (await sa2.get(`${API}/api/effectifs/journal?action=equipage.modifie`, { headers: { "X-Tenant-Id": tenantId } })).json();
    expect(journal[0]).toMatchObject({ origine: "UTILISATEUR", apres: { debut: 1140, minimumOperationnel: 1, note: "Arrivée retardée d'une heure" } });
    const lot = await (await sa2.get(`${API}/api/effectifs/journal?action=poste.modifie.lot`, { headers: { "X-Tenant-Id": tenantId } })).json();
    expect(lot).toHaveLength(2);
  } finally { await sa2.dispose(); }
});
