#!/usr/bin/env node
/**
 * **Mesure les SLI et rend le verdict SLO/SLA** d'une campagne JMeter, à partir du fichier de
 * résultats (`.jtl`, CSV) : par requête (libellé « MÉTHODE chemin ») et au total — nombre,
 * erreurs, débit, p50, p95, p99, maximum — comparés aux objectifs de `slo.json` (classe de
 * l'endpoint) et à l'engagement contractuel (p95 < 2 s). Écrit un rapport Markdown (et Word par
 * pandoc s'il est installé) et **sort en échec** dès qu'un objectif est manqué : c'est une porte.
 *
 *   node perf/jmeter/analyser.cjs resultats/local/…/resultats.jtl --env local --sortie rapports/local/…
 *
 * Les centiles sont calculés au rang le plus proche (méthode « nearest rank ») sur toutes les
 * observations : aucune moyenne, une moyenne acceptable cache une queue inacceptable.
 */
const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");

const ICI = __dirname;

/** Le fichier JTL (CSV avec en-têtes) → lignes { libelle, elapsed, code, succes, horodatage }. */
function lireJtl(fichier) {
  const texte = fs.readFileSync(fichier, "utf8");
  const [entete, ...lignes] = texte.split(/\r?\n/).filter((l) => l.trim());
  const colonnes = entete.split(",");
  const i = (n) => colonnes.indexOf(n);
  const [iT, iE, iL, iC, iS, iF] = [i("timeStamp"), i("elapsed"), i("label"), i("responseCode"), i("success"), i("failureMessage")];
  if ([iT, iE, iL, iC, iS].some((x) => x < 0)) throw new Error("JTL sans les colonnes timeStamp, elapsed, label, responseCode, success (jmeter.save.saveservice.output_format=csv)");
  // Une requête trop longue n'est pas une erreur : sa latence est déjà dans les centiles, et la compter deux
  // fois ferait d'une pause du ramasse-miettes (5 sur 3 000) un dépassement du budget d'erreur. L'erreur, c'est
  // un 5xx, un code inattendu, un transport rompu.
  const tropLongue = (c) => iF >= 0 && /lasted too long/.test(c[iF] ?? "");
  return lignes.map(decouper).filter((c) => c.length > iS).map((c) => ({ horodatage: Number(c[iT]), elapsed: Number(c[iE]), libelle: c[iL], code: c[iC], succes: c[iS] === "true" || tropLongue(c) }));
}

/** Découpe une ligne CSV en respectant les guillemets (un message d'échec peut contenir des virgules). */
function decouper(ligne) {
  const out = []; let cur = ""; let dans = false;
  for (let k = 0; k < ligne.length; k++) {
    const ch = ligne[k];
    if (ch === '"') { if (dans && ligne[k + 1] === '"') { cur += '"'; k++; } else dans = !dans; }
    else if (ch === "," && !dans) { out.push(cur); cur = ""; }
    else cur += ch;
  }
  out.push(cur);
  return out;
}

function centile(valeursTriees, p) {
  if (valeursTriees.length === 0) return 0;
  const rang = Math.max(1, Math.ceil((p / 100) * valeursTriees.length));
  return valeursTriees[rang - 1];
}

/** Les SLI d'un ensemble d'observations. */
function indicateurs(obs) {
  const durees = obs.map((o) => o.elapsed).sort((a, b) => a - b);
  const erreurs = obs.filter((o) => !o.succes).length;
  const debut = Math.min(...obs.map((o) => o.horodatage)); const fin = Math.max(...obs.map((o) => o.horodatage + o.elapsed));
  const secondes = Math.max(1, (fin - debut) / 1000);
  return { nombre: obs.length, erreurs, erreursPct: (100 * erreurs) / obs.length, debit: obs.length / secondes, p50: centile(durees, 50), p95: centile(durees, 95), p99: centile(durees, 99), max: durees[durees.length - 1] ?? 0 };
}

/**
 * **La couverture utile** : pour chaque requête, le code de réponse dominant, et la part des
 * requêtes qui ont réellement travaillé (2xx).
 *
 * Pourquoi cette mesure n'existait pas, et pourquoi elle manquait : l'assertion d'un plan ne
 * refuse que les 5xx, et un 404 n'est pas une erreur — c'est une réponse valide. Un balayage dont
 * tous les identifiants de chemin sont faux répond donc 404 partout, en deux millisecondes, et le
 * rapport conclut « objectifs tenus ». C'est exactement ce qui est arrivé au module `effectifs` :
 * 75 de ses 141 requêtes étaient mesurées sur un refus, et le chiffre flattait la famille la moins
 * éprouvée du produit. Une latence ne vaut que si l'on sait **sur quoi** elle a été mesurée.
 */
function couvertureUtile(parLibelle) {
  const lignes = [...parLibelle.entries()].map(([libelle, obs]) => {
    const comptes = new Map();
    for (const o of obs) comptes.set(o.code, (comptes.get(o.code) ?? 0) + 1);
    const dominant = [...comptes.entries()].sort((a, b) => b[1] - a[1])[0][0];
    return { libelle, dominant, utile: /^2/.test(dominant) };
  });
  const utiles = lignes.filter((l) => l.utile).length;
  return { lignes, utiles, total: lignes.length, pct: (100 * utiles) / lignes.length };
}

function classeDe(slo, libelle) {
  return slo.classes.find((c) => new RegExp(c.motif).test(libelle)) ?? slo.classes[slo.classes.length - 1];
}

/** Le verdict d'une requête : chaque objectif manqué est nommé. */
function verdict(sli, classe, sla) {
  const manques = [];
  if (sli.p95 > classe.p95_ms) manques.push(`p95 ${sli.p95} ms > SLO ${classe.p95_ms} ms`);
  if (sli.p99 > classe.p99_ms) manques.push(`p99 ${sli.p99} ms > SLO ${classe.p99_ms} ms`);
  if (sli.erreursPct > classe.erreurs_pct) manques.push(`erreurs ${sli.erreursPct.toFixed(2)} % > SLO ${classe.erreurs_pct} %`);
  if (sli.p95 > sla.p95_ms) manques.push(`p95 ${sli.p95} ms > SLA ${sla.p95_ms} ms`);
  return manques;
}

const ms = (v) => `${Math.round(v)} ms`;

/** Le rapport : contexte, total, tableau par requête (les manqués d'abord), verdict. */
function rapport({ jtl, env, slo, parLibelle, total, manquesTotaux, date, proprietes, couverture }) {
  const lignes = [...parLibelle.entries()].map(([libelle, sli]) => ({ libelle, sli, classe: classeDe(slo, libelle), manques: verdict(sli, classeDe(slo, libelle), slo.sla) }))
    .sort((a, b) => (b.manques.length - a.manques.length) || (b.sli.p95 - a.sli.p95));
  const manques = lignes.filter((l) => l.manques.length);
  const md = [];
  md.push(`# Résultats de performance — ${env} — ${date}`, "");
  md.push(`- **Fichier** : \`${path.relative(ICI, jtl)}\``);
  md.push(`- **Environnement** : \`${env}\` (${proprietes.protocole ?? "?"}://${proprietes.hote ?? "?"}:${proprietes.port ?? "?"}, ${proprietes.utilisateurs ?? "?"} utilisateurs, montée ${proprietes.montee ?? "?"} s, durée ${proprietes.duree ?? "?"} s, réflexion ${proprietes.reflexion ?? "?"} ms, écritures ${proprietes.ecritures ?? "?"})`);
  md.push(`- **SLA** : p95 < ${slo.sla.p95_ms} ms pour ${slo.sla.utilisateurs_simultanes} utilisateurs (${slo.sla.source})`, "");
  md.push(`## Verdict : ${manquesTotaux.length === 0 && manques.length === 0 ? "✅ objectifs tenus" : `❌ ${manques.length} requête(s) hors objectif${manquesTotaux.length ? " ; total hors objectif" : ""}`}`, "");
  md.push("## Couverture utile", "",
    `**${couverture.utiles} des ${couverture.total} requêtes (${couverture.pct.toFixed(0)} %) répondent 2xx.** Les autres sont mesurées sur un refus : un identifiant de chemin absent (404), un corps que la validation refuse (422) ou un conflit (409). Leur latence ne dit rien du travail réel.`, "");
  const refus = couverture.lignes.filter((l) => !l.utile);
  if (refus.length > 0) {
    md.push("| Requête mesurée sur un refus | Code dominant |", "|---|---|");
    for (const l of refus) md.push(`| \`${l.libelle}\` | ${l.dominant} |`);
    md.push("", "Pour les rendre utiles : semer l'entité qui manque (`preparer.cjs`) ou donner au plan un corps que la validation accepte (`openapiContrats.ts`).", "");
  }
  md.push("## Total", "", "| Requêtes | Erreurs | Débit | p50 | p95 | p99 | max |", "|---:|---:|---:|---:|---:|---:|---:|");
  md.push(`| ${total.nombre} | ${total.erreurs} (${total.erreursPct.toFixed(2)} %) | ${total.debit.toFixed(1)} req/s | ${ms(total.p50)} | ${ms(total.p95)} | ${ms(total.p99)} | ${ms(total.max)} |`, "");
  if (manquesTotaux.length) md.push(`Manques au total : ${manquesTotaux.join(" ; ")}`, "");
  md.push("## Par requête (SLI mesurés, objectif de la classe)", "", "| Requête | Classe | N | Erreurs | p50 | p95 | p99 | SLO p95 | Verdict |", "|---|---|---:|---:|---:|---:|---:|---:|---|");
  for (const l of lignes) md.push(`| \`${l.libelle}\` | ${l.classe.classe} | ${l.sli.nombre} | ${l.sli.erreursPct.toFixed(1)} % | ${ms(l.sli.p50)} | ${ms(l.sli.p95)} | ${ms(l.sli.p99)} | ${l.classe.p95_ms} ms | ${l.manques.length ? "❌ " + l.manques.join(" ; ") : "✅"} |`);
  md.push("", "## Lecture", "", "- **SLI** : ce qui est mesuré (centiles de latence, taux d'erreur, débit). **SLO** : l'objectif par classe d'endpoint (`slo.json`). **SLA** : l'engagement contractuel, p95 < 2 s à 2 500 utilisateurs.",
    "- Les centiles sont calculés sur toutes les observations (rang le plus proche) ; aucune moyenne.", "- Une requête en 4xx nommé (validation, droit) compte comme une réponse valide ; un 5xx, un code inattendu ou un transport rompu compte comme une erreur. Une requête trop longue n'est pas une erreur : elle pèse dans les centiles.", "");
  return md.join("\n");
}

function lireProprietes(env) {
  const fichier = path.join(ICI, "env", `${env}.properties`);
  if (!fs.existsSync(fichier)) return {};
  return Object.fromEntries(fs.readFileSync(fichier, "utf8").split("\n").filter((l) => l && !l.startsWith("#") && l.includes("=")).map((l) => { const i = l.indexOf("="); return [l.slice(0, i).trim(), l.slice(i + 1).trim()]; }));
}

function analyser(jtl, { env = "local", sortie = path.dirname(jtl), date = new Date().toISOString().slice(0, 16).replace("T", " ") } = {}) {
  const slo = JSON.parse(fs.readFileSync(path.join(ICI, "slo.json"), "utf8"));
  const obs = lireJtl(jtl);
  if (obs.length === 0) throw new Error("aucune observation dans le JTL");
  const parLibelle = new Map();
  for (const o of obs) parLibelle.set(o.libelle, [...(parLibelle.get(o.libelle) ?? []), o]);
  const sliParLibelle = new Map([...parLibelle.entries()].map(([l, liste]) => [l, indicateurs(liste)]));
  const total = indicateurs(obs);
  const manquesTotaux = [];
  if (total.p95 > slo.sla.p95_ms) manquesTotaux.push(`p95 ${total.p95} ms > SLA ${slo.sla.p95_ms} ms`);
  if (total.erreursPct > 100 - slo.budget_erreur.slo_disponibilite_pct) manquesTotaux.push(`erreurs ${total.erreursPct.toFixed(2)} % > budget ${(100 - slo.budget_erreur.slo_disponibilite_pct).toFixed(1)} %`);
  const couverture = couvertureUtile(parLibelle);
  const md = rapport({ jtl, env, slo, parLibelle: sliParLibelle, total, manquesTotaux, date, proprietes: lireProprietes(env), couverture });
  fs.mkdirSync(sortie, { recursive: true });
  const fichierMd = path.join(sortie, "RESULTATS.md");
  fs.writeFileSync(fichierMd, md);
  let docx = null;
  try { docx = path.join(sortie, "RESULTATS.docx"); execFileSync("pandoc", [fichierMd, "-o", docx], { stdio: "ignore" }); } catch { docx = null; }
  const manquesRequetes = [...sliParLibelle.entries()].filter(([l, sli]) => verdict(sli, classeDe(slo, l), slo.sla).length).length;
  return { fichierMd, docx, total, manquesTotaux, manquesRequetes, requetes: sliParLibelle.size, couverture };
}

if (require.main === module) {
  const args = process.argv.slice(2);
  const jtl = args.find((a) => !a.startsWith("--"));
  const option = (nom) => { const i = args.indexOf(`--${nom}`); return i >= 0 ? args[i + 1] : undefined; };
  if (!jtl) { console.error("usage : analyser.cjs <resultats.jtl> [--env local] [--sortie dossier] [--min-2xx 80]"); process.exit(2); }
  const r = analyser(jtl, { env: option("env"), sortie: option("sortie") });
  console.log(`${r.requetes} requêtes distinctes ; total p95 ${r.total.p95} ms, erreurs ${r.total.erreursPct.toFixed(2)} %, débit ${r.total.debit.toFixed(1)} req/s → ${r.fichierMd}${r.docx ? ` (+ ${path.basename(r.docx)})` : ""}`);
  console.log(`couverture utile : ${r.couverture.utiles}/${r.couverture.total} requêtes en 2xx (${r.couverture.pct.toFixed(0)} %)`);
  // `--min-2xx` : une campagne dont la plupart des requêtes répondent 404 n'est pas une campagne
  // tenue, c'est une campagne vide. Le seuil est explicite pour que personne ne l'hérite par
  // surprise d'une famille mal semée.
  const min2xx = Number(option("min-2xx") ?? 0);
  if (r.couverture.pct < min2xx) { console.error(`❌ couverture utile ${r.couverture.pct.toFixed(0)} % < ${min2xx} % exigé : les identifiants ou les corps manquent, la mesure ne porte sur rien`); process.exit(1); }
  if (r.manquesTotaux.length || r.manquesRequetes) { console.error(`❌ objectifs manqués : ${r.manquesRequetes} requête(s)${r.manquesTotaux.length ? ", " + r.manquesTotaux.join(" ; ") : ""}`); process.exit(1); }
  console.log("✅ objectifs tenus");
}

module.exports = { lireJtl, decouper, centile, indicateurs, verdict, analyser, classeDe, couvertureUtile };
