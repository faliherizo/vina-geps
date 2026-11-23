#!/usr/bin/env node
/**
 * **Créer un organisme prêt à l'emploi** : l'organisme, ses modules, sa configuration de base et un compte par rôle,
 * chaque personne reliée à sa ressource — pour que chaque écran s'ouvre dès la première connexion, « Mes absences »
 * compris (sans ressource, il répond « votre compte n'est relié à aucune ressource planifiable »).
 *
 * Usage :
 *   SUPERADMIN_PASSWORD=… node scripts/creer-organisme-par-defaut.cjs [--api http://localhost:8095]
 *       [--nom "Organisme par défaut"] [--superadmin-user superadmin] [--sans-donnees-de-demo]
 *
 * - Uniquement l'API publique, en superadmin puis en administration de l'organisme : aucune écriture en base.
 * - Rejouable : chaque objet est retrouvé par son nom, son code, son matricule ou son nom de connexion avant d'être
 *   créé ; une seconde exécution ne double rien et complète ce qui manque.
 * - Les mots de passe sont tirés au hasard et écrits **seulement** dans `scripts/.organisme-par-defaut.<nom>.local.json`
 *   (ignoré par git, mode 600). La console n'affiche que les noms de connexion. Le mot de passe du superadmin se lit
 *   dans l'environnement, jamais sur la ligne de commande (il s'afficherait dans `ps` et l'historique).
 * - L'exécution se termine par un autocontrôle : chaque rôle ouvre ses écrans, et un employé reçoit 403 là où seule
 *   l'administration entre.
 *
 * Ce qui est créé, l'ordre du paramétrage et la remise à zéro : docs/ORGANISME-PAR-DEFAUT.md.
 */
const fs = require("node:fs");
const path = require("node:path");
const { clientApi } = require("./lib/organisme-par-defaut/client.cjs");
const D = require("./lib/organisme-par-defaut/donnees.cjs");
const I = require("./lib/organisme-par-defaut/idempotence.cjs");
const V = require("./lib/organisme-par-defaut/verification.cjs");

const argument = (nom, defaut) => {
  const i = process.argv.indexOf(`--${nom}`);
  return i > 0 && process.argv[i + 1] && !process.argv[i + 1].startsWith("--") ? process.argv[i + 1] : defaut;
};

const OPTIONS = {
  api: argument("api", process.env.API_URL || "http://localhost:8095").replace(/\/+$/, ""),
  nom: argument("nom", "Organisme par défaut"),
  superadmin: argument("superadmin-user", process.env.SUPERADMIN_USERNAME || "superadmin"),
  avecDemo: !process.argv.includes("--sans-donnees-de-demo"),
};
const ADMIN = "admin";
// Une démonstration publique (DEMO_PASSWORD) écrit dans son propre fichier : sous le même nom, semer la démonstration
// distante réécrivait les identifiants tirés au hasard de l'organisme local homonyme — perdus, puisqu'ils n'existent
// que là.
const FICHIER = path.join(__dirname, `.organisme-par-defaut.${I.slug(OPTIONS.nom)}${process.env.DEMO_PASSWORD ? ".demo" : ""}.local.json`);
const { appeler, essayer, connecter } = clientApi(OPTIONS.api);
const bilan = { crees: {}, existants: {}, avertissements: [] };

/** Le jour de Montréal : c'est le fuseau des quarts (PresenceEffectifs.FUSEAU), pas celui du poste. */
const aujourdhui = () => new Intl.DateTimeFormat("en-CA", { timeZone: "America/Montreal" }).format(new Date());

function compter(quoi, cree) {
  const cible = cree ? bilan.crees : bilan.existants;
  cible[quoi] = (cible[quoi] ?? 0) + 1;
}

// ── Les identifiants locaux ─────────────────────────────────────────────────────────────────────

function lireEtat() {
  if (!fs.existsSync(FICHIER)) return { comptes: [] };
  return JSON.parse(fs.readFileSync(FICHIER, "utf8"));
}

function ecrireEtat(etat) {
  const contenu = { avertissement: "Identifiants générés — ne jamais versionner ni partager ce fichier.", ...etat, misAJour: new Date().toISOString() };
  fs.writeFileSync(FICHIER, `${JSON.stringify(contenu, null, 2)}\n`, { mode: 0o600 });
  fs.chmodSync(FICHIER, 0o600);
}

function consigner(etat, compte) {
  etat.comptes = I.fusionnerComptes(etat.comptes, [compte]);
  ecrireEtat(etat);
}

/**
 * **Démonstration publique** (`DEMO_PASSWORD`, docs/COMPTES-DEMO-GEPS.md) : chaque compte reçoit ce mot de passe, publié,
 * pour que quiconque reçoit le lien de la démonstration puisse s'y connecter. Sans la variable, le comportement reste
 * celui d'un organisme réel : un mot de passe tiré au hasard par compte, écrit seulement dans le fichier local.
 */
const MDP_DEMO = process.env.DEMO_PASSWORD || null;
const nouveauMotDePasse = () => MDP_DEMO ?? I.motDePasse();
const motDePasseConnu = (etat, username) => MDP_DEMO ?? I.trouver(etat.comptes, "username", username)?.password ?? null;

// ── 1. L'organisme, son administration, ses modules ──────────────────────────────────────────────

/**
 * Le sous-domaine de l'organisme (`SEMIS_SOUS_DOMAINE`, ADR 0033) : en déploiement par sous-domaine, ses comptes ne se
 * connectent que sur `https://<slug>.<racine>`. Un organisme repris le reçoit s'il ne l'a pas encore.
 */
const SOUS_DOMAINE = process.env.SEMIS_SOUS_DOMAINE || "";

async function assurerSousDomaine(sa, tenant) {
  if (!SOUS_DOMAINE || tenant.slug === SOUS_DOMAINE) return tenant;
  return appeler(`/api/superadmin/tenants/${tenant.id}`, { methode: "PATCH", jeton: sa, corps: { slug: SOUS_DOMAINE } });
}

async function organisme(sa, etat) {
  const existant = I.trouver(await appeler("/api/superadmin/tenants", { jeton: sa }), "name", OPTIONS.nom);
  if (existant) {
    compter("organisme", false);
    return { tenant: await assurerSousDomaine(sa, existant), adminId: null };
  }
  const password = nouveauMotDePasse();
  const cree = await appeler("/api/superadmin/tenants", { methode: "POST", jeton: sa, corps: {
    name: OPTIONS.nom, type: "public_institution", country: "CA", region: "QC", planCode: "enterprise", adminUsername: ADMIN, adminPassword: password,
    slug: SOUS_DOMAINE || undefined } });
  etat.organisme = { id: cree.tenant.id, nom: OPTIONS.nom };
  consigner(etat, { cle: ADMIN, username: ADMIN, role: "admin", password, userId: cree.admin?.userId });
  compter("organisme", true);
  return { tenant: cree.tenant, adminId: cree.admin?.userId };
}

/** L'administration : son mot de passe connu s'il ouvre encore une session, sinon un nouveau posé par le superadmin. */
async function administration(sa, tenant, adminId, etat) {
  // La liste des administrations rend le lien (`id`) et le compte (`userId`) : c'est le compte qu'on modifie.
  const id = adminId ?? I.trouver(await appeler(`/api/superadmin/tenants/${tenant.id}/admins`, { jeton: sa }), "username", ADMIN)?.userId;
  if (!id) throw new Error(`aucun compte « ${ADMIN} » dans l'organisme ${tenant.id}`);
  etat.organisme = { id: tenant.id, nom: OPTIONS.nom };
  await assurerCompte({ jeton: sa }, etat, { cle: ADMIN, username: ADMIN, role: "admin", userId: id }, tenant.id);
  return connecter({ username: ADMIN, password: motDePasseConnu(etat, ADMIN), tenantId: tenant.id });
}

/** Tous les modules du produit, lus au catalogue du serveur (repli : la liste connue), hors options d'hébergement. */
async function modules(sa, tenantId) {
  const catalogue = await appeler("/api/saas/modules", { jeton: sa }).catch(() => null);
  const codes = catalogue?.modules
    ? catalogue.modules.filter((m) => !D.FAMILLES_HORS_PRODUIT.has(m.famille)).map((m) => m.code)
    : D.MODULES_DU_PRODUIT;
  await appeler(`/api/saas/tenants/${tenantId}/modules`, { methode: "PUT", jeton: sa, corps: { modules: codes } });
  return codes;
}

// ── Les comptes ─────────────────────────────────────────────────────────────────────────────────

/**
 * Un compte existant garde son mot de passe s'il ouvre encore une session ; sinon (fichier local perdu, mot de passe
 * changé), on lui en pose un nouveau. Le mur de première connexion est levé : les comptes sont prêts à l'emploi.
 */
async function assurerCompte(ctx, etat, compte, tenantId) {
  const connu = motDePasseConnu(etat, compte.username);
  const valide = connu && await connecter({ username: compte.username, password: connu, tenantId });
  const password = valide ? connu : nouveauMotDePasse();
  const corps = { securitySetupRequired: false, mustChangePassword: false, ...(valide ? {} : { password }) };
  await appeler(`/api/auth/users/${compte.userId}`, { ...ctx, methode: "PATCH", corps });
  consigner(etat, { ...compte, password });
  if (!valide) {
    bilan.avertissements.push(`mot de passe de « ${compte.username} » ${connu ? "réinitialisé (l'ancien était refusé)" : "posé (aucun dans le fichier local)"}`);
  }
}

async function compteDe(ctx, etat, c, existants) {
  const existant = I.trouver(existants, "username", c.cle);
  if (existant) {
    compter("comptes", false);
    await assurerCompte(ctx, etat, { cle: c.cle, username: c.cle, role: c.role, userId: existant.id }, ctx.tenantId);
    return existant.id;
  }
  const password = nouveauMotDePasse();
  const cree = await appeler("/api/auth/users", { ...ctx, methode: "POST", corps: { username: c.cle, password, role: c.role, mustChangePassword: false } });
  consigner(etat, { cle: c.cle, username: c.cle, role: c.role, password, userId: cree.id });
  await appeler(`/api/auth/users/${cree.id}`, { ...ctx, methode: "PATCH", corps: { securitySetupRequired: false } });
  compter("comptes", true);
  return cree.id;
}

// ── 2. La configuration ─────────────────────────────────────────────────────────────────────────

/** Créer, et tenir un 409 (créé entre-temps) pour « existe déjà ». */
async function creerOuTrouver(quoi, creer) {
  try {
    const r = await creer();
    compter(quoi, true);
    return r;
  } catch (e) {
    if (!I.estConflit(e)) throw e;
    compter(quoi, false);
    return null;
  }
}

async function referentiels(ctx) {
  const parType = new Map();
  for (const { type, corps } of D.referentiels(Number(aujourdhui().slice(0, 4)))) {
    if (!parType.has(type)) parType.set(type, await appeler(`/api/effectifs/referentiels/${type}`, ctx));
    if (I.trouver(parType.get(type), "code", corps.code)) { compter("référentiels", false); continue; }
    await creerOuTrouver("référentiels", () => appeler(`/api/effectifs/referentiels/${type}`, { ...ctx, methode: "POST", corps }));
  }
}

async function milieux(ctx) {
  const existants = await appeler("/api/effectifs/milieux", ctx);
  const ids = {};
  for (const m of D.MILIEUX) {
    const trouve = I.trouver(existants, "code", m.code);
    compter("milieux", !trouve);
    ids[m.code] = trouve ? trouve.id : (await appeler("/api/effectifs/milieux", { ...ctx, methode: "POST", corps: m })).id;
  }
  return ids;
}

async function regles(ctx) {
  const existantes = await appeler("/api/effectifs/regles", ctx);
  for (const r of D.REGLES) {
    if (I.regleExistante(existantes, r)) { compter("règles", false); continue; }
    await creerOuTrouver("règles", () => appeler("/api/effectifs/regles", { ...ctx, methode: "POST", corps: r }));
  }
}

/** Les paramètres du temps et les seuils par convention : des PUT, rejouables tels quels. */
async function parametresDuTemps(ctx) {
  await appeler("/api/effectifs/temps/parametres", { ...ctx, methode: "PUT", corps: D.PARAMETRES_TEMPS });
  for (const s of D.SEUILS_CONVENTIONS) {
    await appeler(`/api/effectifs/temps/seuils-conventions/${s.code}`, { ...ctx, methode: "PUT", corps: s.corps });
  }
}

// ── 3. Les personnes : compte, ressource, périmètre ──────────────────────────────────────────────

/** La ressource d'un compte, retrouvée par matricule ; une ressource existante non reliée (ou mal reliée) est reliée. */
async function ressourceDe(ctx, c, utilisateurId, existantes) {
  const corps = { ...c.ressource, nom: c.nom, utilisateurId };
  const trouvee = I.trouver(existantes, "matricule", c.ressource.matricule);
  if (!trouvee) {
    compter("ressources", true);
    return (await appeler("/api/effectifs/planification/ressources", { ...ctx, methode: "POST", corps })).id;
  }
  compter("ressources", false);
  if (trouvee.utilisateurId !== utilisateurId) {
    const { matricule, ...modif } = corps;
    await appeler(`/api/effectifs/planification/ressources/${trouvee.id}`, { ...ctx, methode: "PUT", corps: modif });
    bilan.avertissements.push(`ressource ${matricule} reliée de nouveau au compte « ${c.cle} »`);
  }
  return trouvee.id;
}

/** Le périmètre d'encadrement : la liste se lit par compte (`GET /perimetres` sans compte rend une liste vide). */
async function perimetreDe(ctx, c, utilisateurId) {
  if (!c.perimetre) return;
  const existants = await appeler(`/api/effectifs/perimetres?utilisateurId=${encodeURIComponent(utilisateurId)}`, ctx);
  if (I.perimetreExistant(existants, utilisateurId, c.perimetre.uniteCode)) { compter("périmètres", false); return; }
  await appeler("/api/effectifs/perimetres", { ...ctx, methode: "POST", corps: { utilisateurId, ...c.perimetre } });
  compter("périmètres", true);
}

async function personnes(ctx, etat) {
  const comptes = await appeler("/api/auth/users", ctx);
  const ressources = await appeler("/api/effectifs/planification/ressources", ctx);
  const ids = {};
  for (const c of D.COMPTES) {
    const userId = await compteDe(ctx, etat, c, comptes);
    const ressourceId = await ressourceDe(ctx, c, userId, ressources);
    await perimetreDe(ctx, c, userId);
    consigner(etat, { username: c.cle, ressourceId });
    ids[c.cle] = { userId, ressourceId };
  }
  return ids;
}

// ── 4. Les périodes et les équipages ─────────────────────────────────────────────────────────────

/** La période nommée, retrouvée par son nom ou créée. */
async function periode(ctx, existantes, p) {
  const trouvee = I.trouver(existantes, "nom", p.nom);
  compter("périodes", !trouvee);
  const { finCollecte, ...corps } = p;
  return trouvee ?? appeler("/api/effectifs/planification/periodes", { ...ctx, methode: "POST", corps });
}

/** Faire avancer une période jusqu'à un état, sans jamais la faire reculer (une période publiée le reste). */
async function avancer(ctx, per, cible, finCollecte) {
  for (const etat of I.etatsAFranchir(per.etat, cible)) {
    await appeler(`/api/effectifs/planification/periodes/${per.id}/etat`, { ...ctx, methode: "POST", corps: { etat, ...(finCollecte ? { finCollecte } : {}) } })
      .then((r) => { per.etat = r?.etat ?? etat; })
      .catch((e) => bilan.avertissements.push(`période « ${per.nom} » : passage à ${etat} refusé (${e.message})`));
  }
}

async function equipages(ctx, per, idsMilieux) {
  const existants = await appeler(`/api/effectifs/planification/periodes/${per.id}/equipages`, ctx);
  for (const e of D.equipages(per, aujourdhui())) {
    const deja = (existants ?? []).some((x) => x.date === e.date && x.debut === e.debut && x.milieuId === idsMilieux[e.milieuCode]);
    if (deja) { compter("équipages", false); continue; }
    const { milieuCode, ...corps } = e;
    await appeler("/api/effectifs/planification/equipages", { ...ctx, methode: "POST", corps: { ...corps, periodeId: per.id, milieuId: idsMilieux[milieuCode] } });
    compter("équipages", true);
  }
  return appeler(`/api/effectifs/planification/periodes/${per.id}/equipages`, ctx);
}

async function planification(ctx, idsMilieux) {
  const p = D.periodes(aujourdhui());
  const existantes = await appeler("/api/effectifs/planification/periodes", ctx);
  const courante = await periode(ctx, existantes, p.courante);
  await avancer(ctx, courante, "CONFECTION");
  const liste = await equipages(ctx, courante, idsMilieux);
  await avancer(ctx, courante, "PUBLIEE");
  await avancer(ctx, await periode(ctx, existantes, p.collecte), "COLLECTE", p.collecte.finCollecte);
  return { courante, equipages: liste };
}

// ── 5. Les données de démonstration ──────────────────────────────────────────────────────────────

async function attribuer(ctx, plan, ids) {
  for (const a of D.attributionsDeDemo(D.equipages(plan.courante, aujourdhui()))) {
    const eq = plan.equipages.find((x) => x.date === a.equipage.date && x.debut === a.equipage.debut);
    const ressourceId = ids[a.cle].ressourceId;
    if (!eq || eq.places.some((pl) => pl.ressourceId === ressourceId)) { compter("attributions", false); continue; }
    const libre = eq.places.find((pl) => !pl.ressourceId);
    if (!libre) continue;
    await appeler(`/api/effectifs/planification/places/${libre.id}/attribuer`, { ...ctx, methode: "POST", corps: { ressourceId } })
      .then(() => compter("attributions", true))
      .catch((e) => bilan.avertissements.push(`attribution de ${a.cle} le ${a.equipage.date} refusée : ${e.message}`));
  }
}

/** La déclaration d'absence se fait **en tant qu'employée**, par la route de « Mes absences ». */
async function absence(tenantId, etat) {
  const demo = D.absenceDeDemo(aujourdhui());
  const jeton = await connecter({ username: demo.cle, password: motDePasseConnu(etat, demo.cle), tenantId });
  const moi = { jeton, tenantId };
  const miennes = await appeler("/api/effectifs/absences", moi);
  // Les dates suivent le jour du semis (J+21) : celle d'un semis antérieur chevauche presque toujours la nouvelle, et le
  // serveur refuse un chevauchement (409). Toute absence qui chevauche la période compte donc comme déjà semée.
  if ((miennes ?? []).some((x) => D.chevauche(x, demo.corps))) { compter("absences", false); return; }
  await appeler("/api/effectifs/absences", { ...moi, methode: "POST", corps: demo.corps });
  compter("absences", true);
}

async function demonstration(ctx, plan, ids, etat) {
  await attribuer(ctx, plan, ids);
  await absence(ctx.tenantId, etat);
  const lundi = D.lundiDe(aujourdhui());
  await appeler("/api/effectifs/temps/feuilles/preparer", { ...ctx, methode: "POST", corps: { uniteCode: "OPS", debut: lundi, fin: D.decaler(lundi, 6) } })
    .catch((e) => bilan.avertissements.push(`feuilles de temps non préparées : ${e.message}`));
}

// ── 6. L'autocontrôle ────────────────────────────────────────────────────────────────────────────

async function autocontrole(tenantId, etat) {
  const fenetre = { du: aujourdhui(), au: D.decaler(aujourdhui(), 13) };
  const comptes = [{ cle: ADMIN, role: "admin", aRessource: false }, ...D.COMPTES.map((c) => ({ cle: c.cle, role: c.role, aRessource: true }))];
  const lignes = [];
  for (const c of comptes) {
    const jeton = await connecter({ username: c.cle, password: motDePasseConnu(etat, c.cle), tenantId });
    if (!jeton) { lignes.push({ compte: c.cle, quoi: "connexion", motif: "refusée" }); continue; }
    for (const ctl of V.controlesDe(c, fenetre)) {
      const { statut, corps } = await essayer(ctl.chemin, { jeton, tenantId });
      lignes.push({ compte: c.cle, quoi: ctl.quoi, statut, motif: V.verdict(ctl, statut, corps) });
    }
  }
  return lignes;
}

// ── L'exécution ──────────────────────────────────────────────────────────────────────────────────

function afficher(tenant, codesModules, controles) {
  console.log(`TENANT=${tenant.id}`);
  console.log(`\nOrganisme « ${OPTIONS.nom} » prêt — ${tenant.id} (${OPTIONS.api})`);
  console.log(`  Modules (${codesModules.length}) : ${codesModules.join(", ")}`);
  console.log(`  Créés : ${JSON.stringify(bilan.crees)}`);
  console.log(`  Déjà présents : ${JSON.stringify(bilan.existants)}`);
  for (const a of bilan.avertissements) console.log(`  ⚠ ${a}`);
  console.log("\n  Comptes (choisir cet organisme à la connexion) — mots de passe dans le fichier local :");
  console.log(`    ${ADMIN.padEnd(10)} admin`);
  for (const c of D.COMPTES) console.log(`    ${c.cle.padEnd(10)} ${c.role.padEnd(10)} ${c.nom}`);
  console.log(`  Fichier : ${path.relative(process.cwd(), FICHIER)} (ignoré par git, mode 600)`);
  const echecs = controles.filter((l) => l.motif);
  console.log(`\n  Autocontrôle : ${controles.length - echecs.length}/${controles.length} contrôles conformes`);
  for (const l of echecs) console.log(`    ✗ ${l.compte} — ${l.quoi} : ${l.motif}`);
  return echecs.length;
}

async function principal() {
  const mdpSuperadmin = process.env.SUPERADMIN_PASSWORD;
  if (!mdpSuperadmin) throw new Error("SUPERADMIN_PASSWORD absent de l'environnement (jamais en argument : il s'afficherait dans ps)");
  const sa = await connecter({ username: OPTIONS.superadmin, password: mdpSuperadmin });
  if (!sa) throw new Error(`connexion du superadmin « ${OPTIONS.superadmin} » refusée`);
  const etat = lireEtat();
  etat.api = OPTIONS.api;
  const { tenant, adminId } = await organisme(sa, etat);
  const codesModules = await modules(sa, tenant.id);
  const jeton = await administration(sa, tenant, adminId, etat);
  if (!jeton) throw new Error("connexion de l'administration de l'organisme refusée après réinitialisation");
  const ctx = { jeton, tenantId: tenant.id };
  await referentiels(ctx);
  const idsMilieux = await milieux(ctx);
  await regles(ctx);
  await parametresDuTemps(ctx);
  const ids = await personnes(ctx, etat);
  const plan = await planification(ctx, idsMilieux);
  if (OPTIONS.avecDemo) await demonstration(ctx, plan, ids, etat);
  return afficher(tenant, codesModules, await autocontrole(tenant.id, etat));
}

if (require.main === module) {
  principal().then((echecs) => process.exit(echecs ? 2 : 0)).catch((e) => {
    console.error(`\n❌ ${e.message}\n`);
    process.exit(1);
  });
}
