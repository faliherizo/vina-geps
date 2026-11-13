#!/usr/bin/env node
/**
 * **L'accord de niveau de service**, dans la forme des documents de `docs/SLA` (TI-31P) : pour chaque
 * opération — Objectifs (SLO), Indicateurs (SLI : où lire chaque valeur dans le rapport JMeter, et la valeur
 * lue), Accords (SLA), Notes. Les valeurs viennent du **rapport généré par JMeter** (`statistics.json` du
 * tableau de bord : Average, 95th pct, Error %, Transactions/s) et du JTL (End Time − Start Time), pour
 * deux contextes de données (typique, volumineux). Rend Markdown ; `build-docx.sh` en fait le Word et le PDF.
 *
 *   node perf/jmeter/accord.cjs --typique rapports/local/<horodatage> --volumineux rapports/local/<horodatage> --sortie docs/SLA/ACCORD-NIVEAU-SERVICE-PERFORMANCE.md
 */
const fs = require("fs");
const path = require("path");
const { lireJtl } = require("./analyser.cjs");

const ICI = __dirname;
const CATALOGUE = JSON.parse(fs.readFileSync(path.join(ICI, "operations.json"), "utf8"));

/** Les SLI d'une opération dans un dossier de rapports : ce que JMeter a écrit, sans recalcul. */
function lireSli(dossierRapports, nom) {
  const stats = path.join(dossierRapports, nom, "tableau-de-bord", "statistics.json");
  if (!fs.existsSync(stats)) return null;
  const s = JSON.parse(fs.readFileSync(stats, "utf8"))[nom];
  if (!s) return null;
  const jtl = path.join(ICI, "resultats", ...path.relative(path.join(ICI, "rapports"), dossierRapports).split(path.sep), `${nom}.jtl`);
  let debut = null; let fin = null;
  if (fs.existsSync(jtl)) {
    const obs = lireJtl(jtl).filter((o) => o.libelle === nom);
    debut = Math.min(...obs.map((o) => o.horodatage)); fin = Math.max(...obs.map((o) => o.horodatage + o.elapsed));
  }
  return { echantillons: s.sampleCount, moyen: s.meanResTime, p95: s.pct2ResTime, p99: s.pct3ResTime, erreursPct: s.errorPct, debit: s.throughput, debut, fin, dureeS: debut === null ? null : (fin - debut) / 1000 };
}

const ms = (v) => (v === null || v === undefined ? "—" : `${v.toFixed(0)} ms`);
const pct = (v) => (v === null || v === undefined ? "—" : `${v.toFixed(2)} %`);
const heure = (t) => (t === null ? "—" : new Date(t).toISOString().replace("T", " ").slice(0, 19) + " UTC");
const ok = (mesure, cible, plusPetit = true) => (mesure === null || mesure === undefined ? "—" : (plusPetit ? mesure <= cible : mesure >= cible) ? "✅" : "❌");
/** Le pire des deux contextes, ou rien si aucun n'est mesuré — un « ✅ » sans mesure serait un mensonge. */
const pire = (a, b) => (a === undefined && b === undefined ? undefined : Math.max(a ?? -Infinity, b ?? -Infinity));

/** Le tableau Métrique / Cible / Mesuré / Notes du format de référence, pour un jeu de cibles (SLO ou SLA). */
function tableau(op, cibles, typ, vol, ctx) {
  const l = [];
  l.push("| Métrique | Cible | Mesuré | Verdict | Notes |", "|---|---|---|:-:|---|");
  l.push(`| Temps réponse moyen (contexte typique) | ≤ ${cibles.moyen_typique} ms | ${ms(typ?.moyen)} | ${ok(typ?.moyen, cibles.moyen_typique)} | « Response Times (ms) – Average » |`);
  l.push(`| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ ${cibles.p95_typique} ms | ${ms(typ?.p95)} | ${ok(typ?.p95, cibles.p95_typique)} | 95 % des requêtes répondent en ≤ ${cibles.p95_typique} ms |`);
  l.push(`| Temps réponse moyen (contexte volumineux) | ≤ ${cibles.moyen_volumineux} ms | ${ms(vol?.moyen)} | ${ok(vol?.moyen, cibles.moyen_volumineux)} | établissement chargé (§ Profils) |`);
  l.push(`| Taux d'erreur | ≤ ${cibles.erreurs} % | ${pct(typ?.erreursPct)} / ${pct(vol?.erreursPct)} | ${ok(pire(typ?.erreursPct, vol?.erreursPct), cibles.erreurs)} | uniquement les statuts « Erreur » (5xx) — typique / volumineux |`);
  l.push(`| **Contexte** | | | | |`);
  l.push(`| Débit | ${ctx.debit} / seconde | ${typ ? typ.debit.toFixed(1) : "—"} / ${vol ? vol.debit.toFixed(1) : "—"} | ${ok(typ?.debit, ctx.debit * 0.95, false)} | requêtes / seconde — typique / volumineux |`);
  l.push(`| Durée de référence | ${ctx.duree} secondes | ${typ?.dureeS ? typ.dureeS.toFixed(0) : "—"} / ${vol?.dureeS ? vol.dureeS.toFixed(0) : "—"} s | ${ok(typ?.dureeS, ctx.duree * 0.98, false)} | End Time − Start Time |`);
  return l.join("\n");
}

function sectionOperation(i, op, typ, vol, ctx, env) {
  const s = [];
  s.push(`## ${i}. ${op.nom}`, "", `*${op.libelle}* — \`${op.methode} ${op.chemin.replace(/\$\{[^}]+\}/g, "…")}\``, "");
  s.push("### Objectifs de niveau de service (SLO)", "", "Cette section identifie les cibles de performances visées par l'opération dans un contexte précis (débit fixe et une durée de référence).", "");
  s.push(tableau(op, op.slo, typ, vol, ctx), "");
  s.push("### Indicateurs de niveau de service (SLI)", "", "Cette section identifie comment obtenir les indicateurs de performances de l'exécution de l'opération.", "");
  s.push("**La requête pour les temps réponses** — obtenir la valeur du rapport de performance généré par JMeter (`tableau-de-bord/statistics.json`, transaction `" + op.nom + "`) :", "");
  s.push(`- « Response Times (ms) – Average » : **${ms(typ?.moyen)}** (typique), **${ms(vol?.moyen)}** (volumineux)`);
  s.push(`- « Response Times (ms) – 95th pct » : **${ms(typ?.p95)}** (typique), **${ms(vol?.p95)}** (volumineux)`, "");
  s.push("**La requête pour les taux d'erreurs** — obtenir la valeur du rapport de performance généré par JMeter :", "");
  s.push(`- « Executions – Error % » : **${pct(typ?.erreursPct)}** (typique), **${pct(vol?.erreursPct)}** (volumineux)`, "");
  s.push("**Contexte** — obtenir la valeur du rapport de performance généré par JMeter :", "");
  s.push(`- « Throughput – Transactions / s » : **${typ ? typ.debit.toFixed(2) : "—"}** (typique), **${vol ? vol.debit.toFixed(2) : "—"}** (volumineux)`);
  s.push(`- « End Time – Start Time » : **${typ?.dureeS ? typ.dureeS.toFixed(0) + " s" : "—"}** (typique : ${heure(typ?.debut ?? null)} → ${heure(typ?.fin ?? null)}), **${vol?.dureeS ? vol.dureeS.toFixed(0) + " s" : "—"}** (volumineux)`);
  s.push(`- Échantillons : ${typ?.echantillons ?? "—"} (typique), ${vol?.echantillons ?? "—"} (volumineux)`, "");
  s.push(`**Tableau de bord JMeter – Simulation** : \`perf/jmeter/rapports/${env}/<horodatage>/${op.nom}/tableau-de-bord/index.html\``, "", "**Tableau de bord – Production** : *à ajouter* (sonde de lecture `scenarios/sonde-lecture.jmx`, sans écriture).", "");
  s.push("### Accords de niveau de service (SLA)", "", "Cette section documente les performances minimales que l'opération doit respecter.", "");
  s.push(tableau(op, op.sla, typ, vol, ctx), "");
  s.push("### Notes", "", op.notes, "");
  return s.join("\n");
}

function accord({ typique, volumineux, env = "local", date = new Date().toISOString().slice(0, 10) }) {
  const ctx = CATALOGUE.contexte;
  const md = [];
  md.push("# Accord de niveau de service — performance des opérations du backend Java (Nexu VINA)", "");
  md.push(`- **Date** : ${date} · **Environnement** : \`${env}\` · **Forme** : TI-31P « Accord de niveau de service », une section par opération (SLO, SLI, SLA, Notes)`);
  md.push(`- **Contexte de référence** : débit fixe de **${ctx.debit} requêtes / seconde** (timer à débit constant partagé), **${ctx.duree} secondes** par opération, ${ctx.unites} unités — une opération à la fois, jamais deux en même temps`);
  md.push(`- **Campagnes** : typique \`${typique ? path.relative(path.join(ICI, ".."), typique) : "—"}\` · volumineux \`${volumineux ? path.relative(path.join(ICI, ".."), volumineux) : "—"}\``);
  md.push("- **Où lire les valeurs** : le rapport HTML généré par JMeter (`tableau-de-bord/index.html`, tableau *Statistics* : Executions – Error %, Response Times (ms) – Average / 95th pct, Throughput – Transactions/s) ; les mêmes chiffres sont dans `statistics.json`, c'est là que ce document les lit, sans recalcul", "");
  md.push("## Définitions", "", "- **SLI** (indicateur) : ce que l'on mesure et où on le lit dans le rapport JMeter.", "- **SLO** (objectif) : la cible visée par l'équipe, dans le contexte de référence.", "- **SLA** (accord) : la performance minimale que l'opération doit respecter ; au-delà, c'est un manquement.", "- **Verdict** : ✅ la mesure tient la cible, ❌ elle la dépasse, — non mesuré.", "");
  md.push("## Profils de données", "", `- **typique** : ${CATALOGUE.profils.typique}.`, `- **volumineux** : ${CATALOGUE.profils.volumineux}.`, "", "Les identifiants utilisés par les opérations (employé, projet, semaine…) sont ceux de `perf/jmeter/donnees/identifiants.csv`, écrits par `preparer.cjs` ; comme dans le format de référence, ils doivent désigner des dossiers d'un **volume typique** (un employé qui a des saisies, un projet qui a des tâches), sans quoi la mesure flatte.", "");
  md.push("# Opérations", "");
  const bilan = [];
  CATALOGUE.operations.forEach((op, i) => {
    const typ = typique ? lireSli(typique, op.nom) : null;
    const vol = volumineux ? lireSli(volumineux, op.nom) : null;
    md.push(sectionOperation(i + 1, op, typ, vol, ctx, env));
    const verdictSlo = [ok(typ?.moyen, op.slo.moyen_typique), ok(typ?.p95, op.slo.p95_typique), ok(vol?.moyen, op.slo.moyen_volumineux), ok(pire(typ?.erreursPct, vol?.erreursPct), op.slo.erreurs)];
    const verdictSla = [ok(typ?.moyen, op.sla.moyen_typique), ok(typ?.p95, op.sla.p95_typique), ok(vol?.moyen, op.sla.moyen_volumineux), ok(pire(typ?.erreursPct, vol?.erreursPct), op.sla.erreurs)];
    const resume = (v) => (v.includes("❌") ? "❌" : v.includes("—") ? "—" : "✅");
    bilan.push(`| ${op.nom} | ${ms(typ?.moyen)} | ${ms(typ?.p95)} | ${ms(vol?.moyen)} | ${pct(typ?.erreursPct)} | ${resume(verdictSlo)} | ${resume(verdictSla)} |`);
  });
  md.push("# Bilan", "", "| Opération | Moyen (typique) | 95ᵉ pct (typique) | Moyen (volumineux) | Erreurs | SLO | SLA |", "|---|---:|---:|---:|---:|:-:|:-:|", ...bilan, "");
  return md.join("\n");
}

if (require.main === module) {
  const args = process.argv.slice(2);
  const option = (nom) => { const i = args.indexOf(`--${nom}`); return i >= 0 ? args[i + 1] : undefined; };
  const typique = option("typique") ? path.resolve(option("typique")) : undefined;
  const volumineux = option("volumineux") ? path.resolve(option("volumineux")) : undefined;
  const sortie = option("sortie") ?? path.join(ICI, "..", "..", "docs", "SLA", "ACCORD-NIVEAU-SERVICE-PERFORMANCE.md");
  const md = accord({ typique, volumineux, env: option("env") ?? "local" });
  fs.mkdirSync(path.dirname(sortie), { recursive: true });
  fs.writeFileSync(sortie, md);
  // On compte les verdicts, pas la légende des définitions (qui cite les trois symboles).
  const verdicts = md.slice(md.indexOf("# Opérations"));
  console.log(`→ ${sortie} (${(verdicts.match(/❌/g) ?? []).length} ❌, ${(verdicts.match(/✅/g) ?? []).length} ✅)`);
}

module.exports = { lireSli, accord, tableau };
