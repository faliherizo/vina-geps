#!/usr/bin/env node
/**
 * **La convention SP-CSN des paramédics, paramétrée par les gestes de l'application** (26-015-DO, story AD-08).
 *
 * L'addenda 4 (Q1) a joint les articles 14 à 16 de la convention ; l'addenda 6 (Q2) demande de **démontrer** le
 * paramétrage. Ce script le pose par l'API, comme une administratrice le ferait à l'écran : référentiels, règles à
 * portée de convention, seuils d'heures supplémentaires. Les valeurs et leurs articles sont dans
 * `e2e/donnees/urgences-sante/convention-sp-csn.json` ; la traduction en appels, dans `lib/parametrage-convention.cjs`.
 * Aucune ligne du serveur ne connaît cette convention.
 *
 * Usage :
 *   node scripts/semer-sp-csn.cjs [--api http://localhost:4000] [--mdp 'Passe!Demo2026']
 *       Crée un organisme de démonstration horodaté, ses comptes, et y pose le paramétrage.
 *   SEMIS_MDP=<mot de passe de l'administration> node scripts/semer-sp-csn.cjs --organisme <id> [--admin admin]
 *       Pose le seul paramétrage sur un organisme existant (c'est ainsi que l'essai de bout en bout l'éprouve).
 *
 * Le superadmin est pris dans SUPERADMIN_USER / SUPERADMIN_PASS (défaut : superadmin/superadmin).
 */
const donnees = require("../e2e/donnees/urgences-sante/convention-sp-csn.json");
const { clientApi, argument } = require("./lib/api-semis.cjs");
const { etapesDuParametrage, EFFECTIF_DEPUIS } = require("./lib/parametrage-convention.cjs");

const API = argument("api", process.env.API_URL || "http://localhost:4000");
// Le mot de passe se lit aussi dans l'environnement : passé en argument, il s'affiche dans tout rapport d'échec.
const MDP = argument("mdp", process.env.SEMIS_MDP || process.env.DEMO_PASSWORD || "Passe!Demo2026");
const ORGANISME = argument("organisme", "");
const ADMIN = argument("admin", "admin");
const SUPERADMIN = { username: process.env.SUPERADMIN_USER || "superadmin", password: process.env.SUPERADMIN_PASS || "superadmin" };

/** Les modules du paramétrage : les effectifs, dont règles, absences, échanges et temps. */
const MODULES = [
  "effectifs", "effectifs_regles", "effectifs_planification", "effectifs_disponibilites",
  "effectifs_absences", "effectifs_echanges", "effectifs_agph", "effectifs_presence", "effectifs_temps",
];
/** Le menu de la démonstration : celui d'Urgences-santé (voir semer-urgences-sante.cjs, SECTIONS_GARDEES). */
const SECTIONS_GARDEES = ["pilotage", "space", "admin", "urgencessante"];

const { appeler, connecter } = clientApi(API);

/** Joue le paramétrage ; un refus nomme l'appel et l'article qu'il portait. */
async function parametrer(ctx) {
  const etapes = etapesDuParametrage(donnees);
  for (const e of etapes) {
    await appeler(e.chemin, { ...ctx, methode: e.methode, corps: e.corps })
      .catch((err) => { throw new Error(`${e.quoi} : ${err.message}`); });
  }
  return etapes.length;
}

/** L'organisme, ses modules, son menu, son administration. */
async function creerOrganisme() {
  const jetonSa = await connecter(SUPERADMIN);
  const horodatage = new Date().toISOString().slice(0, 16).replace("T", " ");
  const s = donnees.support.organisme;
  const cree = await appeler("/api/superadmin/tenants", {
    methode: "POST", jeton: jetonSa,
    corps: { name: `${s.nom} (${horodatage})`, type: "public_institution", country: "CA", region: "QC", planCode: s.planCode,
      adminUsername: ADMIN, adminPassword: MDP },
  });
  if (cree.admin?.userId) {
    await appeler(`/api/auth/users/${cree.admin.userId}`, { methode: "PATCH", jeton: jetonSa, corps: { securitySetupRequired: false, mustChangePassword: false } });
  }
  await appeler(`/api/saas/tenants/${cree.tenant.id}/modules`, { methode: "PUT", jeton: jetonSa, corps: { modules: MODULES } });
  const catalogue = await appeler("/api/saas/vues", { jeton: jetonSa });
  const masquees = catalogue.vues.filter((v) => !SECTIONS_GARDEES.includes(v.section)).map((v) => v.cle);
  await appeler(`/api/saas/tenants/${cree.tenant.id}/vues`, { methode: "PUT", jeton: jetonSa, corps: { masquees } });
  return cree.tenant;
}

/** Ce que le paramétrage vise : l'unité, le corps, le titre, le milieu — hors convention. */
async function semerSupport(ctx) {
  const s = donnees.support;
  const ref = (type, corps) => appeler(`/api/effectifs/referentiels/${type}`, { ...ctx, methode: "POST", corps: { effectifDepuis: EFFECTIF_DEPUIS, ...corps } });
  await ref("unites", s.unite);
  await ref("corps_emploi", s.corpsEmploi);
  await ref("titres_emploi", { ...s.titreEmploi, attributs: { corps: s.corpsEmploi.code } });
  await ref("types_milieu", s.typeMilieu);
  await appeler("/api/effectifs/milieux", { ...ctx, methode: "POST",
    corps: { code: s.milieu.code, libelle: s.milieu.libelle, typeCode: s.typeMilieu.code, uniteCode: s.unite.code } });
}

/** Les personnes de la convention : un compte chacune ; les paramédics ont aussi leur dossier, rattaché à SP-CSN. */
async function semerPersonnes(ctx) {
  const s = donnees.support;
  for (const p of s.personnes) {
    const compte = await appeler("/api/auth/users", { ...ctx, methode: "POST", corps: { username: p.username, password: MDP, role: p.role, mustChangePassword: false } });
    await appeler(`/api/auth/users/${compte.id}`, { ...ctx, methode: "PATCH", corps: { securitySetupRequired: false } });
    if (p.matricule) {
      await appeler("/api/effectifs/planification/ressources", { ...ctx, methode: "POST", corps: {
        matricule: p.matricule, nom: p.nom, utilisateurId: compte.id, corpsEmploiCode: s.corpsEmploi.code, titreEmploiCode: s.titreEmploi.code,
        uniteCode: s.unite.code, statutEmploiCode: p.statutEmploiCode, anciennete: p.anciennete, conventionCode: donnees.convention.code } });
    }
    if (p.perimetre) {
      await appeler("/api/effectifs/perimetres", { ...ctx, methode: "POST",
        corps: { utilisateurId: compte.id, uniteCode: s.unite.code, etendue: "SOUS_ARBRE", niveau: p.perimetre } });
    }
  }
}

async function surOrganismeExistant() {
  const jeton = await connecter({ username: ADMIN, password: MDP, tenantId: ORGANISME });
  const n = await parametrer({ jeton, tenantId: ORGANISME });
  console.log(`PARAMETRAGE=${n}`);
  console.log(`✅ Convention ${donnees.convention.code} paramétrée sur ${ORGANISME} : ${n} appels, ${donnees.regles.length} règles, ${donnees.ecarts.length} écarts documentés.`);
}

async function demonstration() {
  const tenant = await creerOrganisme();
  const ctx = { jeton: await connecter({ username: ADMIN, password: MDP, tenantId: tenant.id }), tenantId: tenant.id };
  await semerSupport(ctx);
  const n = await parametrer(ctx);
  await semerPersonnes(ctx);
  console.log(`TENANT=${tenant.id}`);
  console.log(`\n✅ Organisme « ${tenant.name} » — convention ${donnees.convention.code} paramétrée (${n} appels, ${donnees.regles.length} règles).`);
  console.log(`   Écarts que le moteur n'exprime pas : ${donnees.ecarts.map((e) => `${e.id} (art. ${e.article})`).join(", ")}`);
  console.log("\n   Comptes (choisir cet organisme à la connexion) :");
  console.log(`     ${ADMIN} / ${MDP}  — administration de l'organisme`);
  for (const p of donnees.support.personnes) console.log(`     ${p.username} / ${MDP}  — ${p.nom}${p.statutEmploiCode ? ` (${p.statutEmploiCode})` : ""}`);
}

(ORGANISME ? surOrganismeExistant() : demonstration()).catch((e) => {
  console.error(`\n❌ ${e.message}\n`);
  process.exit(1);
});
