#!/usr/bin/env node
/**
 * **La pointe des pointages** (AO 26-015-DO, E-TN-17) : 500 paramédics pointent leur entrée dans la même minute, au
 * changement de garde. C'est la transaction qui dicte le dimensionnement, et la seule que la campagne de l'accord ne
 * mesurait pas fidèlement : rejouer ENTREE sur trois places mesurait le refus de la garde, pas l'écriture.
 *
 *   perf/jmeter/backend-java-local.sh &                         # le Java sur :4020, base neuve
 *   node perf/jmeter/pointe-pointages.cjs --personnes 500 --fenetre 60
 *
 * Ici, chaque personne a son compte, sa ressource et **sa** place sur un quart en cours ; elle est connectée d'avance
 * (la connexion n'est pas la transaction mesurée), puis les 500 pointages partent répartis sur la fenêtre (60 s par
 * défaut) — et une seconde vague les envoie tous d'un coup, par lots concurrents. Chaque pointage doit être accepté
 * (201) : un refus mesuré serait un pointage qui n'a pas eu lieu. Verdict : p95 ≤ 2 000 ms (E-TN-17), 0,5 % d'erreurs au
 * plus (accord). Rapport : perf/jmeter/rapports/<env>/<horodatage>/pointe-pointages/RESULTATS.md.
 */
const fs = require("fs");
const path = require("path");
const { proprietes, lireCsv, etablissementDeCampagne } = require("./preparer.cjs");

const ICI = __dirname;
const option = (nom, defaut) => { const i = process.argv.indexOf(`--${nom}`); return i > 0 ? process.argv[i + 1] : defaut; };
const ENV = option("env", "local");
const PERSONNES = Number(option("personnes", "500"));
const FENETRE_S = Number(option("fenetre", "60"));
const RAFALE = Number(option("rafale", "50"));
const PLACES_PAR_EQUIPAGE = 10;
const SEUIL_P95_MS = 2000;
const ERREURS_MAX = 0.005;
const MDP = "Passe!Pointe2026";

const attendre = (ms) => new Promise((ok) => setTimeout(ok, ms));

async function requete(base, methode, chemin, { jeton, tenant, corps } = {}) {
  const debut = performance.now();
  const r = await fetch(`${base}${chemin}`, { method: methode, headers: { "Content-Type": "application/json",
    ...(jeton ? { Authorization: `Bearer ${jeton}` } : {}), ...(tenant ? { "X-Tenant-Id": tenant } : {}) },
    body: corps ? JSON.stringify(corps) : undefined });
  const texte = await r.text();
  let json = null; try { json = JSON.parse(texte); } catch { json = null; }
  return { statut: r.status, json, texte, ms: performance.now() - debut };
}

async function exiger(base, methode, chemin, opts) {
  const r = await requete(base, methode, chemin, opts);
  if (r.statut >= 300) throw new Error(`${methode} ${chemin} → ${r.statut} ${r.texte.slice(0, 200)}`);
  return r.json;
}

/** Connexion d'une personne ; une limitation (429) attend et recommence. */
async function connecter(base, username, tenantId) {
  for (let essai = 0; essai < 20; essai++) {
    const r = await requete(base, "POST", "/api/auth/login", { corps: { username, password: MDP, tenantId } });
    if (r.statut === 200 && r.json?.token) return r.json.token;
    if (r.statut !== 429) throw new Error(`connexion de ${username} → ${r.statut} ${r.texte.slice(0, 160)}`);
    await attendre(2000);
  }
  throw new Error(`connexion de ${username} : limitée trop longtemps`);
}

/** Le décor : une unité, un corps d'emploi, un milieu, une période publiée qui couvre aujourd'hui. */
async function decor(base, jeton, tenant, suffixe) {
  const o = { jeton, tenant };
  const ref = (type, corps) => exiger(base, "POST", `/api/effectifs/referentiels/${type}`, { ...o, corps: { effectifDepuis: "2026-01-01", ...corps } });
  const unite = `PT${suffixe}`;
  await ref("unites", { code: unite, libelleFr: `Pointe ${suffixe}` });
  await ref("corps_emploi", { code: `PC${suffixe}`, libelleFr: "Paramédics (pointe)" });
  await ref("natures_quart", { code: `PA${suffixe}`, libelleFr: "Ad hoc", comportement: "AD_HOC" });
  await ref("types_milieu", { code: `PM${suffixe}`, libelleFr: "Caserne", comportement: "CASERNE" });
  const milieu = await exiger(base, "POST", "/api/effectifs/milieux", { ...o, corps: { code: `PL${suffixe}`, libelle: "Caserne de la pointe", typeCode: `PM${suffixe}`, uniteCode: unite } });
  const aujourdHui = new Date().toISOString().slice(0, 10);
  const fin = new Date(Date.now() + 13 * 86_400_000).toISOString().slice(0, 10);
  const periode = await exiger(base, "POST", "/api/effectifs/planification/periodes", { ...o, corps: { nom: `Pointe ${suffixe}`, uniteCode: unite, debut: aujourdHui, fin } });
  await exiger(base, "POST", `/api/effectifs/planification/periodes/${periode.id}/etat`, { ...o, corps: { etat: "CONFECTION" } });
  await exiger(base, "POST", `/api/effectifs/planification/periodes/${periode.id}/etat`, { ...o, corps: { etat: "PUBLIEE" } });
  return { unite, corps: `PC${suffixe}`, nature: `PA${suffixe}`, milieu: milieu.id, periode: periode.id, aujourdHui };
}

/** Les personnes, et une place chacune sur un quart ad hoc qui couvre l'heure courante. */
async function personnes(base, jeton, tenant, d, suffixe) {
  const o = { jeton, tenant };
  const minutes = new Date().getHours() * 60 + new Date().getMinutes();
  const liste = [];
  for (let i = 0; i < PERSONNES; i++) {
    const username = `pointe-${suffixe.toLowerCase()}-${i}`;
    const compte = await exiger(base, "POST", "/api/auth/users", { ...o, corps: { username, password: MDP, role: "employee", mustChangePassword: false } });
    await exiger(base, "PATCH", `/api/auth/users/${compte.id}`, { ...o, corps: { securitySetupRequired: false } });
    const ressource = await exiger(base, "POST", "/api/effectifs/planification/ressources", { ...o, corps: {
      matricule: `PP${suffixe}${i}`, nom: `Paramédic ${i}`, utilisateurId: compte.id, corpsEmploiCode: d.corps, uniteCode: d.unite } });
    liste.push({ username, ressourceId: ressource.id });
    if ((i + 1) % 100 === 0) console.log(`  ${i + 1} personnes`);
  }
  for (let e = 0; e < liste.length; e += PLACES_PAR_EQUIPAGE) {
    const lot = liste.slice(e, e + PLACES_PAR_EQUIPAGE);
    const equipage = await exiger(base, "POST", "/api/effectifs/planification/equipages", { ...o, corps: {
      periodeId: d.periode, date: d.aujourdHui, debut: Math.max(0, minutes - 30), fin: Math.min(1439, minutes + 240),
      natureCode: d.nature, milieuId: d.milieu, composition: [{ corpsEmploiCode: d.corps, nombre: lot.length }], minimumOperationnel: 1 } });
    for (const [k, p] of lot.entries()) {
      p.placeId = equipage.places[k].id;
      await exiger(base, "POST", `/api/effectifs/planification/places/${p.placeId}/attribuer`, { ...o, corps: { ressourceId: p.ressourceId } });
    }
  }
  return liste;
}

const centile = (v, q) => { const t = [...v].sort((a, b) => a - b); return t.length ? t[Math.min(t.length - 1, Math.ceil(q * t.length) - 1)] : NaN; };
function resume(nom, mesures) {
  const ms = mesures.map((m) => m.ms);
  const erreurs = mesures.filter((m) => m.statut !== 201).length;
  return { nom, n: mesures.length, erreurs, taux: erreurs / Math.max(1, mesures.length),
    p50: centile(ms, 0.5), p95: centile(ms, 0.95), p99: centile(ms, 0.99), max: Math.max(...ms),
    codes: Object.entries(mesures.reduce((a, m) => ({ ...a, [m.statut]: (a[m.statut] ?? 0) + 1 }), {})).map(([c, n]) => `${c} × ${n}`).join(", ") };
}

const pointer = (base, tenant, p, sens) => requete(base, "POST", "/api/effectifs/presence/pointages",
  { jeton: p.jeton, tenant, corps: { placeId: p.placeId, sens, preuve: { type: "AUCUNE" } } });

async function main() {
  const p = proprietes(ENV);
  const base = (process.env.PERF_URL || `${p.protocole}://${p.hote}:${p.port}`).replace(/\/+$/, "");
  const compte = lireCsv(path.join(ICI, p.comptes))[0];
  const admin = await exiger(base, "POST", "/api/auth/login", { corps: { username: compte.username, password: compte.password } });
  const appel = async (m, c, corps, t) => exiger(base, m, c, { jeton: admin.token, tenant: t, corps: corps ?? undefined });
  const tenant = await etablissementDeCampagne(appel, ENV, process.env.PERF_TENANT || "");
  const suffixe = Date.now().toString(36).toUpperCase().slice(-5);
  console.log(`▶ décor et ${PERSONNES} personnes (organisme ${tenant})`);
  const d = await decor(base, admin.token, tenant, suffixe);
  const liste = await personnes(base, admin.token, tenant, d, suffixe);
  console.log("▶ connexions préalables (hors mesure)");
  for (const pers of liste) pers.jeton = await connecter(base, pers.username, tenant);

  console.log(`▶ vague 1 : ${PERSONNES} entrées réparties sur ${FENETRE_S} s`);
  const ecart = (FENETRE_S * 1000) / PERSONNES;
  const t0 = Date.now();
  const vague1 = await Promise.all(liste.map(async (pers, i) => {
    await attendre(Math.max(0, t0 + i * ecart - Date.now()));
    return pointer(base, tenant, pers, "ENTREE");
  }));
  console.log(`▶ vague 2 : ${PERSONNES} sorties d'un coup, ${RAFALE} à la fois`);
  const vague2 = [];
  for (let i = 0; i < liste.length; i += RAFALE) {
    vague2.push(...await Promise.all(liste.slice(i, i + RAFALE).map((pers) => pointer(base, tenant, pers, "SORTIE"))));
  }

  const r1 = resume(`${PERSONNES} entrées sur ${FENETRE_S} s (la pointe de l'accord)`, vague1);
  const r2 = resume(`${PERSONNES} sorties, ${RAFALE} simultanées (au-delà de la pointe)`, vague2);
  const tenu = (r) => r.p95 <= SEUIL_P95_MS && r.taux <= ERREURS_MAX;
  const lignes = [r1, r2].map((r) => `| ${r.nom} | ${r.n} | ${r.codes} | ${Math.round(r.p50)} | ${Math.round(r.p95)} | ${Math.round(r.p99)} | ${Math.round(r.max)} | ${tenu(r) ? "✅" : "⛔"} |`);
  const horodatage = new Date().toISOString().replace(/[-:]/g, "").slice(0, 15).replace("T", "-");
  const dossier = path.join(ICI, "rapports", ENV, horodatage, "pointe-pointages");
  fs.mkdirSync(dossier, { recursive: true });
  const md = [`# Pointe des pointages — ${new Date().toISOString().slice(0, 16).replace("T", " ")}`, "",
    `Serveur : ${base}. Chaque personne a son compte et sa place sur un quart en cours ; connexions faites avant la mesure.`,
    `Objectif E-TN-17 : p95 ≤ ${SEUIL_P95_MS} ms ; accord : erreurs ≤ ${ERREURS_MAX * 100} %. Toute réponse autre que 201 compte comme erreur.`, "",
    "| Vague | N | Codes | p50 (ms) | p95 (ms) | p99 (ms) | max (ms) | Verdict |", "|---|---:|---|---:|---:|---:|---:|---|", ...lignes, "",
    `## Verdict : ${tenu(r1) && tenu(r2) ? "✅ objectifs tenus" : "⛔ objectif manqué"}`, "",
    "La machine de mesure est celle du développement, partagée : un temps mesuré ici est un plafond, pas une référence de production."].join("\n");
  fs.writeFileSync(path.join(dossier, "RESULTATS.md"), md + "\n");
  console.log(md);
  process.exit(tenu(r1) && tenu(r2) ? 0 : 1);
}

main().catch((e) => { console.error(`❌ ${e.message}`); process.exit(2); });
