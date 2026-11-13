/**
 * **Gestion des effectifs — lot R5 (absences), de bout en bout.** AO 26-015-DO.
 *
 * Le planificateur déclare l'absence de Julie **dans le formulaire unique** d'Absences (ADR 0034),
 * nature « effectifs ». Les règles la refusent — le comblement tomberait à 0 % — et l'écran le dit,
 * avec la voie qui reste. Dans Planification › Absences et comblement, il voit l'impact (taux avant
 * et après, quarts touchés), accorde l'absence par dérogation motivée, et les deux quarts de Julie
 * deviennent vacants (E-GN-5, E-GN-6, E-CH-6, E-GR-3). Chaque état visité passe axe-core.
 */
import { test, expect, type APIRequestContext } from "@playwright/test";
import { superadminContext } from "./helpers/auth";
import { createTenant, selectTenant, setTenantModules, uniq } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";
import { expectNoA11yViolations } from "./helpers/a11y";

test.use({ storageState: { cookies: [], origins: [] } });

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;
const LUNDI = "2030-03-04";

async function poser(sa: APIRequestContext, tenantId: string, chemin: string, data: unknown) {
  const r = await sa.post(`${API}${chemin}`, { headers: { "X-Tenant-Id": tenantId }, data });
  if (!r.ok()) throw new Error(`amorce ${chemin} : ${r.status()} ${await r.text()}`);
  return r.json();
}

/** Julie tient les deux quarts du lundi et du mardi ; une règle refuse ce qui viderait le comblement. */
async function amorcer(tenantId: string): Promise<{ periodeId: string }> {
  const sa = await superadminContext();
  try {
    await setTenantModules(sa, tenantId, ["effectifs_absences"]);
    const ref = (type: string, data: Record<string, unknown>) => poser(sa, tenantId, `/api/effectifs/referentiels/${type}`, { effectifDepuis: "2026-01-01", ...data });
    await ref("unites", { code: "EST", libelleFr: "Opérations Est" });
    await ref("corps_emploi", { code: "PARA", libelleFr: "Paramédics" });
    await ref("natures_quart", { code: "REG", libelleFr: "Régulier", comportement: "REGULIER" });
    await ref("types_milieu", { code: "CASERNE", libelleFr: "Caserne", comportement: "CASERNE" });
    await ref("codes_absence", { code: "MAL", libelleFr: "Maladie", comportement: "PAYE", attributs: { duree: "PONCTUELLE" } });
    const milieu = await poser(sa, tenantId, "/api/effectifs/milieux", { code: "C12", libelle: "Caserne 12", typeCode: "CASERNE", uniteCode: "EST" });
    const julie = await poser(sa, tenantId, "/api/effectifs/planification/ressources", { matricule: "P1", nom: "Julie Roy", corpsEmploiCode: "PARA", uniteCode: "EST" });
    const periode = await poser(sa, tenantId, "/api/effectifs/planification/periodes", { nom: "Semaine 10", uniteCode: "EST", debut: LUNDI, fin: "2030-03-10" });
    await poser(sa, tenantId, `/api/effectifs/planification/periodes/${periode.id}/etat`, { etat: "CONFECTION" });
    for (const date of [LUNDI, "2030-03-05"]) {
      const e = await poser(sa, tenantId, "/api/effectifs/planification/equipages", {
        periodeId: periode.id, date, debut: 360, fin: 1080, natureCode: "REG", milieuId: milieu.id, modeAttribution: "MANUEL",
        composition: [{ corpsEmploiCode: "PARA", nombre: 1 }],
      });
      await poser(sa, tenantId, `/api/effectifs/planification/places/${e.places[0].id}/attribuer`, { ressourceId: julie.id });
    }
    await poser(sa, tenantId, "/api/effectifs/regles", {
      contexte: "ABSENCE", cle: "comblement-minimal", condition: { fait: "tauxComblementApres", op: "<", valeur: 60 }, action: "REFUSER",
      message: "Le comblement tomberait à {tauxComblementApres} %",
    });
    return { periodeId: periode.id };
  } finally { await sa.dispose(); }
}

test("déclarer une absence d'effectifs, voir le refus motivé, mesurer l'impact et accorder par dérogation", async ({ page }) => {
  test.setTimeout(240_000);
  const tenantId = await createTenant(page.request, uniq("EffAbs"));
  const { periodeId } = await amorcer(tenantId);
  await selectTenant(page, tenantId);

  // ── Absences et comblement › Déclarer pour une ressource du périmètre ───────────────────
  await ouvrirNav(page, "nav-usabsences");
  await page.getByTestId("pla-abs-declarer").click();
  await expect(page.getByTestId("abs-hours")).toHaveCount(0);
  await page.getByTestId("abs-eff-ressource").selectOption({ label: "Julie Roy (P1)" });
  await page.getByTestId("abs-eff-code").selectOption("MAL");
  await page.getByTestId("abs-start").fill(LUNDI);
  await page.getByTestId("abs-end").fill("2030-03-05");
  await page.getByTestId("abs-eff-commentaire").fill("Grippe");
  await expectNoA11yViolations(page);
  await page.getByTestId("abs-submit").click();
  await expect(page.getByTestId("pla-abs-bilan")).toContainText("refusée par les règles de l'organisme. Motifs : Le comblement tomberait à 0 %.");
  await expect(page.getByTestId("pla-abs-bilan")).toContainText("dérogation motivée");

  // ── Absences et comblement : impact, puis dérogation ─────────────────────────────────────
  await page.getByTestId("pla-abs-filtre").selectOption("REFUSEE_AUTO");
  const ligne = page.locator('[data-testid^="pla-abs-"][data-testid$="-ouvrir"]').first();
  await ligne.click();
  await expect(page.getByTestId("pla-abs-impact-taux")).toContainText("100 % avant, 0 % après");
  await expect(page.getByTestId("pla-abs-impact")).toContainText("2030-03-04 · 06:00–18:00");
  await expectNoA11yViolations(page);
  await page.getByTestId("pla-abs-motif").fill("court");
  await page.getByTestId("pla-abs-deroger").click();
  await expect(page.getByTestId("pla-abs-fiche-erreur")).toContainText("10 caractères");
  await page.getByTestId("pla-abs-motif").fill("Hospitalisation confirmée par billet médical");
  await page.getByTestId("pla-abs-deroger").click();
  await expect(page.getByTestId("pla-abs-bilan")).toContainText("Julie Roy : absence acceptée — 2 place(s) rendue(s) vacante(s)");
  await expectNoA11yViolations(page);

  // Les deux quarts de Julie sont vacants : le comblement le montre.
  const sa = await superadminContext();
  const eq = await (await sa.get(`${API}/api/effectifs/planification/periodes/${periodeId}/equipages`, { headers: { "X-Tenant-Id": tenantId } })).json();
  await sa.dispose();
  expect(eq.map((e: { places: Array<{ ressourceId: string | null }> }) => e.places[0].ressourceId)).toEqual([null, null]);
});

/**
 * **Lot U5 — le comblement s'enclenche de lui-même après l'autorisation manuelle** (E-GN-6, P-GN-1,
 * P-GH-3, P-CH-8). La période est publiée ; Julie tient le lundi (attribution automatisée) et le
 * mardi (attribution manuelle). La dérogation accordée, le lundi va d'office à Marc — Léa, plus
 * ancienne, est sautée par la règle de repos —, le mardi est offert à Léa et Marc, avisés ; le
 * planificateur le donne à Léa depuis la fiche. L'absence de long terme de Théo réserve ses quarts
 * et ouvre, depuis la même fiche, un remplacement long terme lié à elle.
 */
async function amorcerComblement(tenantId: string): Promise<void> {
  const sa = await superadminContext();
  const P = (chemin: string, data: unknown) => poser(sa, tenantId, chemin, data);
  try {
    await setTenantModules(sa, tenantId, ["effectifs_absences", "effectifs_disponibilites", "effectifs_agph"]);
    const ref = (type: string, data: Record<string, unknown>) => P(`/api/effectifs/referentiels/${type}`, { effectifDepuis: "2026-01-01", ...data });
    await ref("unites", { code: "EST", libelleFr: "Opérations Est" });
    await ref("corps_emploi", { code: "PARA", libelleFr: "Paramédics" });
    await ref("statuts_emploi", { code: "TC", libelleFr: "Temps complet", comportement: "TEMPS_COMPLET" });
    await ref("natures_quart", { code: "REG", libelleFr: "Régulier", comportement: "REGULIER" });
    await ref("types_milieu", { code: "CASERNE", libelleFr: "Caserne", comportement: "CASERNE" });
    await ref("plages_horaires", { code: "J", libelleFr: "Jour", attributs: { debut: 360, fin: 1080 } });
    await ref("codes_absence", { code: "MAL", libelleFr: "Maladie", comportement: "PAYE", attributs: { duree: "PONCTUELLE", planifiable: true } });
    await ref("codes_absence", { code: "CLD", libelleFr: "Congé de longue durée", comportement: "NON_PAYE", attributs: { duree: "LONG_TERME", planifiable: true } });
    const milieu = await P("/api/effectifs/milieux", { code: "C12", libelle: "Caserne 12", typeCode: "CASERNE", uniteCode: "EST" });
    const res: Record<string, string> = {};
    for (const [mat, nom, anc] of [["P1", "Julie Roy", 3], ["P2", "Marc Gagnon", 9], ["P3", "Léa Tremblay", 15], ["P4", "Théo Paquin", 6]] as const) {
      const compte = await P("/api/auth/users", { username: uniq(mat).toLowerCase(), password: "Passe!Test2026", role: "employee", tenantId, employeeId: uniq(`emp-${mat}`) });
      res[mat] = (await P("/api/effectifs/planification/ressources", { matricule: mat, nom, corpsEmploiCode: "PARA", uniteCode: "EST", anciennete: anc, utilisateurId: compte.id })).id;
    }
    const periode = await P("/api/effectifs/planification/periodes", { nom: "Semaine 10", uniteCode: "EST", debut: LUNDI, fin: "2030-03-10" });
    await P(`/api/effectifs/planification/periodes/${periode.id}/etat`, { etat: "COLLECTE", finCollecte: "2030-02-28" });
    const quart = async (date: string, debut: number, fin: number, mode: string, titulaire: string) => {
      const e = await P("/api/effectifs/planification/equipages", {
        periodeId: periode.id, date, debut, fin, natureCode: "REG", milieuId: milieu.id, modeAttribution: mode, composition: [{ corpsEmploiCode: "PARA", nombre: 1 }],
      });
      await P(`/api/effectifs/planification/places/${e.places[0].id}/attribuer`, { ressourceId: res[titulaire] });
      return e.places[0].id as string;
    };
    await quart(LUNDI, 360, 1080, "AUTOMATISE", "P1");
    await quart("2030-03-05", 360, 1080, "MANUEL", "P1");
    await quart(LUNDI, 0, 300, "MANUEL", "P3");
    const mercredi = await quart("2030-03-06", 360, 1080, "AUTOMATISE", "P4");
    for (const mat of ["P1", "P2", "P3"]) {
      const r = await sa.put(`${API}/api/effectifs/disponibilites/periodes/${periode.id}/ressources/${res[mat]}`, { headers: { "X-Tenant-Id": tenantId },
        data: { plages: [LUNDI, "2030-03-05", "2030-03-06"].map((date) => ({ date, debut: 0, fin: 1440, type: "DISPONIBLE" })) } });
      if (!r.ok()) throw new Error(`dépôt ${mat} : ${r.status()} ${await r.text()}`);
    }
    for (const etat of ["CONFECTION", "PUBLIEE"]) await P(`/api/effectifs/planification/periodes/${periode.id}/etat`, { etat });
    const poste = await P("/api/effectifs/planification/postes", {
      code: "EST-1", libelle: "Poste EST-1", uniteCode: "EST", statutEmploiCode: "TC", corpsEmploiCode: "PARA", milieuId: milieu.id, natureCode: "REG",
      cycle: [["J", "J", "J", null, null, null, null]], debutCycle: LUNDI,
    });
    await P("/api/effectifs/planification/postes/publier", { ids: [poste.id] });
    expect(mercredi).toBeTruthy();
    await P("/api/effectifs/regles", { contexte: "ATTRIBUTION", cle: "repos-minimal", condition: { fait: "reposHeures", op: "<", valeur: 8 }, action: "BLOQUER",
      message: "Repos de {reposHeures} h seulement" });
    await P("/api/effectifs/regles", { contexte: "ABSENCE", cle: "comblement-minimal",
      condition: { tous: [{ fait: "tauxComblementApres", op: "<", valeur: 70 }, { fait: "dureeAbsence", op: "=", valeur: "PONCTUELLE" }] }, action: "REFUSER",
      message: "Le comblement tomberait à {tauxComblementApres} %" });
    const julie = await P("/api/effectifs/absences", { personneId: res.P1, motif: "MAL", du: LUNDI, au: "2030-03-05" });
    expect(julie.statut).toBe("REFUSEE_AUTO");
    const theo = await P("/api/effectifs/absences", { personneId: res.P4, motif: "CLD", du: "2030-03-06", au: "2030-05-31" });
    expect([theo.statut, theo.comblement.reservees]).toEqual(["ACCEPTEE", 1]);
  } finally { await sa.dispose(); }
}

test("après la dérogation, le comblement attribue au prioritaire, offre et avise ; une absence longue ouvre son remplacement long terme", async ({ page }) => {
  test.setTimeout(240_000);
  const tenantId = await createTenant(page.request, uniq("EffCbl"));
  await amorcerComblement(tenantId);
  await selectTenant(page, tenantId);

  await ouvrirNav(page, "nav-usabsences");
  await page.getByTestId("pla-abs-filtre").selectOption("REFUSEE_AUTO");
  await page.locator('[data-testid^="pla-abs-"][data-testid$="-ouvrir"]').first().click();
  await page.getByTestId("pla-abs-motif").fill("Hospitalisation confirmée par billet médical");
  await page.getByTestId("pla-abs-deroger").click();
  await expect(page.getByTestId("pla-abs-bilan")).toContainText(
    "Julie Roy : absence acceptée — 2 place(s) rendue(s) vacante(s) ; comblement automatique : 1 attribuée(s) d'office au prioritaire, 1 offerte(s) aux admissibles avisés.");

  // La fiche de l'absence accordée montre ce que le mécanisme a fait, et les avis envoyés.
  await page.getByTestId("pla-abs-filtre").selectOption("ACCEPTEE");
  await page.getByRole("button", { name: /Impact et décision — Julie Roy/ }).click();
  const lundi = page.getByTestId("pla-abs-comblement-2030-03-04");
  await expect(lundi).toContainText("Attribuée d'office");
  await expect(lundi).toContainText("à Marc Gagnon");
  await expect(lundi).toContainText("occupée par Marc Gagnon");
  await expect(page.getByTestId("pla-abs-avis-2030-03-04")).toContainText("Marc Gagnon — courriel");
  const mardi = page.getByTestId("pla-abs-comblement-2030-03-05");
  await expect(mardi).toContainText("Offerte aux admissibles");
  await expect(page.getByTestId("pla-abs-avis-2030-03-05")).toContainText("Léa Tremblay — ");
  await expect(page.getByTestId("pla-abs-avis-2030-03-05")).toContainText("Marc Gagnon — ");
  await expect(page.getByTestId("pla-abs-avis-2030-03-05")).not.toContainText("Julie Roy");
  await expect(page.getByTestId("pla-abs-remplacement-raison")).toContainText("le code « MAL » est ponctuel");
  await expectNoA11yViolations(page);
  await page.getByTestId("pla-abs-choix-2030-03-05").selectOption({ label: "Léa Tremblay" });
  await page.getByTestId("pla-abs-attribuer-2030-03-05").click();
  await expect(page.getByTestId("pla-abs-comblement-info")).toContainText("Quart du 2030-03-05 attribué.");
  await expect(mardi).toContainText("occupée par Léa Tremblay");
  await page.getByTestId("pla-abs-fiche-close").click();

  // L'absence de long terme de Théo : ses quarts sont réservés ; le remplacement s'ouvre d'ici, lié à elle.
  await page.getByRole("button", { name: /Impact et décision — Théo Paquin/ }).click();
  await expect(page.getByTestId("pla-abs-comblement-2030-03-06")).toContainText("Réservée au remplacement long terme");
  await expect(page.getByTestId("pla-abs-remplacement-poste")).toHaveValue(/.+/);
  await expectNoA11yViolations(page);
  await page.getByTestId("pla-abs-remplacement-ouvrir").click();
  await expect(page.getByTestId("pla-abs-comblement-info")).toContainText("Remplacement long terme ouvert sur le poste EST-1");
  await expect(page.getByTestId("pla-abs-remplacement")).toContainText("sur le poste EST-1, lié à cette absence : ouvert — du 2030-03-06 au 2030-05-31");
  await expect(page.getByTestId("pla-abs-remplacement-form")).toHaveCount(0);
  await expectNoA11yViolations(page);
});
