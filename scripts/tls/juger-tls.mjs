#!/usr/bin/env node
/**
 * **Le verdict d'un balayage TLS** (AO 26-015-DO, E-TN-8) : lit les rapports JSON de testssl.sh et dit, hôte par hôte,
 * si la transmission navigateur → service est conforme.
 *
 *   node scripts/tls/juger-tls.mjs rapports/*.json [--sortie bilan.md]
 *
 * Refusé : un protocole antérieur à TLS 1.2 offert, TLS 1.2 absent, une vulnérabilité ou un constat de sévérité
 * MEDIUM et plus, un certificat expiré, non approuvé ou qui ne nomme pas l'hôte, un certificat qui expire dans moins
 * de 14 jours, l'absence de HSTS. Code de sortie 1 au premier refus ; le bilan Markdown nomme chaque constat.
 */
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { basename } from "node:path";

const SEVERITES_REFUSEES = new Set(["MEDIUM", "HIGH", "CRITICAL"]);
const PROTOCOLES_INTERDITS = ["SSLv2", "SSLv3", "TLS1", "TLS1_1"];
const JOURS_AVANT_EXPIRATION = 14;

/** Les constats refusés d'un rapport testssl (tableau d'objets `{ id, severity, finding, ip }`). */
export function constats(rapport) {
  const refus = [];
  const parId = (id) => rapport.filter((x) => x.id === id || x.id.startsWith(`${id} `));
  for (const p of PROTOCOLES_INTERDITS) {
    for (const x of parId(p)) if (/^offered/i.test(x.finding)) refus.push(`${p} offert`);
  }
  if (!parId("TLS1_2").some((x) => /^offered/i.test(x.finding)) && !parId("TLS1_3").some((x) => /^offered/i.test(x.finding))) {
    refus.push("ni TLS 1.2 ni TLS 1.3 offert");
  }
  for (const x of rapport) {
    if (SEVERITES_REFUSEES.has(x.severity)) refus.push(`${x.id} (${x.severity}) : ${x.finding}`);
  }
  for (const x of parId("cert_notAfter")) {
    const jours = (Date.parse(x.finding) - Date.now()) / 86_400_000;
    if (!Number.isNaN(jours) && jours < JOURS_AVANT_EXPIRATION) refus.push(`certificat : expire dans ${Math.floor(jours)} jour(s)`);
  }
  for (const x of parId("cert_chain_of_trust")) if (!/^passed/i.test(x.finding)) refus.push(`chaîne de confiance : ${x.finding}`);
  for (const x of parId("cert_subjectAltName")) if (/no SAN|missing/i.test(x.finding)) refus.push(`certificat : ${x.finding}`);
  const hsts = parId("HSTS");
  if (hsts.length && hsts.every((x) => /not offered|^--$/i.test(x.finding))) refus.push("HSTS absent");
  return [...new Set(refus)];
}

/** Le bilan Markdown de plusieurs rapports, et s'ils sont tous conformes. */
export function bilan(rapports) {
  const lignes = ["| Hôte | Verdict | Constats |", "|---|---|---|"];
  let conforme = true;
  for (const { nom, rapport } of rapports) {
    const refus = constats(rapport);
    if (rapport.length === 0) refus.push("rapport vide : le balayage n'a rien mesuré");
    conforme &&= refus.length === 0;
    lignes.push(`| ${nom} | ${refus.length ? "⛔ refusé" : "✅ conforme"} | ${refus.join(" ; ").replace(/\|/g, "/") || "—"} |`);
  }
  return { conforme, markdown: lignes.join("\n") + "\n" };
}

export function principal(args) {
  const i = args.indexOf("--sortie");
  const sortie = i >= 0 ? args[i + 1] : null;
  const fichiers = args.filter((a, k) => a !== "--sortie" && !(i >= 0 && k === i + 1));
  if (fichiers.length === 0) throw new Error("aucun rapport testssl à juger");
  const absents = fichiers.filter((f) => !existsSync(f));
  if (absents.length) throw new Error(`rapport introuvable : ${absents.join(", ")} — testssl a-t-il pu écrire ?`);
  const { conforme, markdown } = bilan(fichiers.map((f) => {
    const brut = JSON.parse(readFileSync(f, "utf8"));
    return { nom: basename(f, ".json"), rapport: Array.isArray(brut) ? brut : brut.scanResult ?? [] };
  }));
  if (sortie) writeFileSync(sortie, markdown);
  process.stdout.write(markdown);
  return conforme ? 0 : 1;
}

if (import.meta.url === `file://${process.argv[1]}`) process.exit(principal(process.argv.slice(2)));
