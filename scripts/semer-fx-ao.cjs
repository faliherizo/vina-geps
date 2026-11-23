#!/usr/bin/env node
/**
 * **L'organisme de démonstration de l'avis FX-AO-20260810** — Santé Québec Laurentides, communications cliniques.
 *
 * Semé depuis `e2e/donnees/fx-ao/demonstration.json` : les six sites du devis, les comptes par profil, les groupes
 * de garde, de spécialité et de comité, une liste de distribution, les réponses et modèles d'alerte, une politique
 * d'escalade, la copie par courriel, un annuaire LDAPS déclaré (inactif) et trois alertes déjà parties — une
 * cascade armée, une garde prise en charge, un code bleu critique partiellement lu. Le menu est réduit à ce que la
 * grille demande.
 *
 * Usage :
 *   node scripts/semer-fx-ao.cjs [--api http://localhost:8095] [--mdp 'Passe!Demo2026']
 *
 * Le superadmin est pris dans SUPERADMIN_USER / SUPERADMIN_PASS (défaut : superadmin/superadmin). Relancé, le script
 * crée un nouvel organisme horodaté : une démonstration ne s'écrase pas pendant qu'on la montre.
 */
const donnees = require("../e2e/donnees/fx-ao/demonstration.json");
const { clientApi, argument, jour, SEMIS_EN_COURS, achever } = require("./lib/api-semis.cjs");

const API = argument("api", process.env.API_URL || "http://localhost:4000");
const MDP = argument("mdp", process.env.DEMO_PASSWORD || "Passe!Demo2026");
const SUPERADMIN = { username: process.env.SUPERADMIN_USER || "superadmin", password: process.env.SUPERADMIN_PASS || "superadmin" };
const { appeler, connecter } = clientApi(API);

/** L'organisme, ses modules, son administration — et un menu réduit à la grille. */
async function creerOrganisme() {
  const jetonSa = await connecter(SUPERADMIN);
  const horodatage = new Date().toISOString().slice(0, 16).replace("T", " ");
  const cree = await appeler("/api/superadmin/tenants", {
    methode: "POST", jeton: jetonSa,
    corps: { name: `${process.env.SEMIS_NOM_ORGANISME ?? donnees.organisme.nom} (${horodatage})${SEMIS_EN_COURS}`, type: "public_institution", country: "CA", region: "QC",
      planCode: donnees.organisme.planCode, adminUsername: "admin", adminPassword: MDP,
      // Le sous-domaine de l'organisme (ADR 0033) : ses comptes ne se connectent que là en déploiement par sous-domaine.
      slug: process.env.SEMIS_SOUS_DOMAINE || undefined },
  });
  if (cree.admin?.userId) {
    await appeler(`/api/auth/users/${cree.admin.userId}`, { methode: "PATCH", jeton: jetonSa, corps: { securitySetupRequired: false, mustChangePassword: false } });
  }
  const tenant = cree.tenant;
  await appeler(`/api/saas/tenants/${tenant.id}/modules`, { methode: "PUT", jeton: jetonSa, corps: { modules: donnees.modules } });
  await cloisonner(jetonSa, tenant.id);
  return { tenant, jetonSa };
}

/** Le menu réduit à l'avis : toute vue du catalogue qui n'est pas nommée dans `vuesGardees` est masquée. */
async function cloisonner(jetonSa, tenantId) {
  const { sections, cles } = donnees.vuesGardees;
  const catalogue = await appeler("/api/saas/vues", { jeton: jetonSa });
  const masquees = catalogue.vues.filter((v) => !sections.includes(v.section) && !cles.includes(v.cle)).map((v) => v.cle);
  await appeler(`/api/saas/tenants/${tenantId}/vues`, { methode: "PUT", jeton: jetonSa, corps: { masquees } });
  return { gardees: catalogue.vues.length - masquees.length, masquees: masquees.length };
}

/** Les sites (unités), les corps d'emploi, puis une personne = un compte + une ressource rattachée à son site. */
async function semerPersonnes(ctx) {
  const poser = (chemin, corps) => appeler(chemin, { ...ctx, methode: "POST", corps });
  for (const s of donnees.sites) await poser("/api/effectifs/referentiels/unites", { effectifDepuis: "2026-01-01", ...s });
  for (const c of donnees.corpsEmploi) await poser("/api/effectifs/referentiels/corps_emploi", { effectifDepuis: "2026-01-01", ...c });
  for (const c of donnees.codesAbsence) await poser("/api/effectifs/referentiels/codes_absence", { effectifDepuis: "2026-01-01", ...c });
  // Les rôles propres d'abord (ADR 0085) : un compte ne peut porter qu'un rôle qui existe déjà.
  for (const r of donnees.rolesPropres.roles) await poser("/api/auth/roles", r);
  const comptes = {};
  for (const p of donnees.personnes) {
    const compte = await poser("/api/auth/users", { username: p.username, password: MDP, role: p.role, mustChangePassword: false });
    await appeler(`/api/auth/users/${compte.id}`, { ...ctx, methode: "PATCH", corps: { securitySetupRequired: false } });
    await poser("/api/effectifs/planification/ressources", {
      matricule: p.matricule, nom: p.nom, utilisateurId: compte.id, corpsEmploiCode: p.corps, uniteCode: p.site,
    });
    comptes[p.username] = { ...p, id: compte.id };
  }
  return comptes;
}

/** Groupes transverses (garde, comité, spécialité) et une liste de distribution partagée. */
async function semerGroupes(ctx) {
  const groupes = {};
  for (const g of donnees.groupesTransverses) {
    const cree = await appeler("/api/communication/groupes-transverses", { ...ctx, methode: "POST", corps: { nom: g.nom, nature: g.nature } });
    for (const m of g.membres) {
      await appeler(`/api/communication/groupes-transverses/${cree.id}/membres/${m}`, {
        ...ctx, methode: "PUT", corps: { depuis: jour(-30), moderateur: g.moderateurs.includes(m) },
      });
    }
    groupes[g.nom] = cree.id;
  }
  const l = donnees.listeDistribution;
  await appeler("/api/communication/listes-distribution", {
    ...ctx, methode: "POST", corps: { nom: l.nom, partagee: l.partagee, membres: l.personnes.map((cible) => ({ nature: "personne", cible })) },
  });
  return groupes;
}

/** Réponses, modèles, politique d'escalade, copie par courriel, annuaire LDAPS. */
async function semerParametres(ctx, groupes) {
  const poser = (chemin, corps) => appeler(chemin, { ...ctx, methode: "POST", corps });
  const reponses = {};
  for (const r of donnees.reponses) reponses[r.cle] = (await poser("/api/alertes/reponses", { libelle: r.libelle, semantique: r.semantique })).id;
  const modeles = {};
  for (const m of donnees.modeles) {
    modeles[m.cle] = { ...m, reponseIds: m.reponses.map((c) => reponses[c]) };
    modeles[m.cle].id = (await poser("/api/alertes/modeles", { titre: m.titre, corps: m.corps, priorite: m.priorite, reponseIds: modeles[m.cle].reponseIds })).id;
  }
  const p = donnees.politique;
  const politique = (await poser("/api/alertes/politiques", {
    nom: p.nom, arret: p.arret,
    etapes: p.etapes.map(({ groupe, ...e }) => (groupe ? { ...e, cibleId: groupes[groupe] } : e)),
  })).id;
  await appeler("/api/alertes/reglages", { ...ctx, methode: "PUT", corps: { copieCourriel: donnees.reglages.copieCourriel } });
  const { pourquoi, ...annuaire } = donnees.annuaire;
  await poser("/api/auth/annuaires", annuaire);
  return { reponses, modeles, politique };
}

/** Les alertes de la démonstration, envoyées, lues et répondues par leurs acteurs ; puis la note. */
async function semerAlertes(tenantId, groupes, parametres) {
  const jetons = {};
  const acteur = async (u) => (jetons[u] ??= await connecter({ username: u, password: MDP, tenantId }));
  const comme = async (u, chemin, methode, corps) => appeler(chemin, { jeton: await acteur(u), tenantId, methode, corps });
  for (const a of donnees.alertes) {
    const m = parametres.modeles[a.modele];
    const alerte = await comme(a.par, "/api/alertes", "POST", {
      titre: a.titre, corps: m.corps, priorite: m.priorite, reponseIds: m.reponseIds, modeleId: m.id,
      groupes: [a.groupe, groupes[a.groupeTransverse]].filter(Boolean), personnes: a.personnes ?? [],
      politiqueId: a.politique ? parametres.politique : undefined,
    });
    for (const u of a.lectures ?? []) await comme(u, `/api/alertes/recues/${alerte.id}/lue`, "POST");
    if (a.refusPar) await comme(a.refusPar, `/api/alertes/recues/${alerte.id}/reponse`, "POST", { reponseId: parametres.reponses.indispo });
    if (a.prisePar) await comme(a.prisePar, `/api/alertes/recues/${alerte.id}/reponse`, "POST", { reponseId: parametres.reponses.arrive });
  }
  const n = donnees.note;
  await comme(n.par, `/api/alertes/notes/${n.personne}`, "PUT", { texte: n.texte });
}

/** Une période en collecte par site nommé : « Mes disponibilités » a de quoi recevoir un dépôt (critère 25). */
async function semerPeriodes(ctx) {
  for (const p of donnees.periodesCollecte.liste) {
    const periode = await appeler("/api/effectifs/planification/periodes", { ...ctx, methode: "POST",
      corps: { nom: p.nom, uniteCode: p.uniteCode, debut: jour(p.debut), fin: jour(p.fin) } });
    await appeler(`/api/effectifs/planification/periodes/${periode.id}/etat`, { ...ctx, methode: "POST",
      corps: { etat: "COLLECTE", finCollecte: jour(p.finCollecte) } });
  }
}

/**
 * Le calendrier interne (critère 25) : chaque absence est déclarée **par la personne**, depuis « Mes absences » ; si une
 * règle la retient pour approbation, l'administration l'approuve. Une absence non acceptée ne rend personne indisponible.
 */
async function semerAbsences(ctx) {
  const jetons = {};
  for (const a of donnees.absences.liste) {
    jetons[a.personne] ??= await connecter({ username: a.personne, password: MDP, tenantId: ctx.tenantId });
    const r = await appeler("/api/effectifs/absences", { jeton: jetons[a.personne], tenantId: ctx.tenantId, methode: "POST",
      corps: { motif: a.motif, du: jour(a.du), au: jour(a.au), commentaire: a.commentaire } });
    if (r.statut === "A_APPROUVER") await appeler(`/api/effectifs/absences/${r.ids[0]}/decisions`, { ...ctx, methode: "POST", corps: { decision: "APPROUVER" } });
    else if (r.statut !== "ACCEPTEE") throw new Error(`absence de ${a.personne} : ${r.statut} (${(r.motifs ?? []).join(" ; ")})`);
  }
}

async function semer() {
  const { tenant, jetonSa } = await creerOrganisme();
  const ctx = { jeton: await connecter({ username: "admin", password: MDP, tenantId: tenant.id }), tenantId: tenant.id };
  const comptes = await semerPersonnes(ctx);
  const groupes = await semerGroupes(ctx);
  const parametres = await semerParametres(ctx, groupes);
  await semerAbsences(ctx);
  await semerPeriodes(ctx);
  await semerAlertes(tenant.id, groupes, parametres);
  return { tenant: await achever(appeler, jetonSa, tenant), comptes };
}

/**
 * `--cloisonner <organisme>` : n'appliquer que le menu réduit à l'avis, sur un organisme FX-AO déjà semé — sans rien
 * semer d'autre. Le menu se règle par vues masquées : l'appliquer de nouveau ne double rien.
 */
const ORGANISME_A_CLOISONNER = argument("cloisonner", "");
if (ORGANISME_A_CLOISONNER) {
  connecter(SUPERADMIN).then((jeton) => cloisonner(jeton, ORGANISME_A_CLOISONNER)).then(({ gardees, masquees }) => {
    console.log(`✅ Menu de ${ORGANISME_A_CLOISONNER} réduit à l'avis FX-AO-20260810 : ${gardees} vues gardées, ${masquees} masquées.`);
    process.exit(0);
  }).catch((e) => { console.error(`\n❌ ${e.message}\n`); process.exit(1); });
} else semer().then(({ tenant, comptes }) => {
  console.log(`TENANT=${tenant.id}`);
  console.log(`\n✅ Organisme « ${tenant.name} » semé — ${tenant.id}`);
  console.log(`   Sites : ${donnees.sites.map((s) => s.libelleFr).join(", ")}`);
  console.log("\n   Comptes (choisir cet organisme à la connexion) :");
  console.log(`     admin / ${MDP}  — administration de l'organisme (Paramètres des alertes, comptes, annuaires)`);
  for (const c of Object.values(comptes)) console.log(`     ${c.username} / ${MDP}  — ${c.nom} — ${c.profil}`);
  console.log("");
}).catch((e) => {
  console.error(`\n❌ ${e.message}\n`);
  process.exit(1);
});
