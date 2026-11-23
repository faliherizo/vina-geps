#!/usr/bin/env node
/**
 * **Construit `docs/SCENARIOS-E2E.md`** : pour chaque exigence des deux avis (Urgences-santé 26-015-DO, 144 ;
 * FX-AO-20260810, 115), les scénarios de bout en bout qui la jouent, avec leurs données JSON.
 *
 * Le document est **calculé**, jamais écrit à la main : il lit les jeux de données que les essais exécutent
 * (`e2e/donnees/…`), les features BDD (`backend-java/src/bdd`), les essais e2e qui nomment l'exigence et les essais
 * Java cités. Un scénario décrit ici est donc un scénario qui tourne.
 *
 *   node scripts/construire-scenarios-e2e.cjs            # écrit le document
 *   node scripts/construire-scenarios-e2e.cjs --verifier # échoue si une exigence fonctionnelle n'a aucun scénario e2e
 */
const fs = require("node:fs");
const path = require("node:path");

const RACINE = path.join(__dirname, "..");
const lire = (f) => JSON.parse(fs.readFileSync(path.join(RACINE, f), "utf8"));
const SORTIE = path.join(RACINE, "docs/SCENARIOS-E2E.md");

/** Tous les fichiers d'un dossier, récursivement, filtrés par extension. */
function fichiers(dossier, ext) {
  const abs = path.join(RACINE, dossier);
  if (!fs.existsSync(abs)) return [];
  return fs.readdirSync(abs, { withFileTypes: true }).flatMap((e) => {
    const rel = path.join(dossier, e.name);
    if (e.isDirectory()) return e.name === "node_modules" ? [] : fichiers(rel, ext);
    return e.name.endsWith(ext) ? [rel] : [];
  });
}

/** Les scénarios BDD par étiquette d'exigence : `@E-GN-1` → [« fichier — titre »]. */
function scenariosBdd() {
  const parExigence = {};
  for (const f of fichiers("backend-java/src/bdd/resources/features", ".feature")) {
    let etiquettes = [];
    for (const ligne of fs.readFileSync(path.join(RACINE, f), "utf8").split("\n")) {
      const t = ligne.trim();
      if (t.startsWith("@")) etiquettes = t.split(/\s+/).map((x) => x.slice(1));
      const m = /^Scénario( Outline)?:\s*(.*)$/.exec(t);
      if (m) {
        for (const e of etiquettes) (parExigence[e] ??= []).push(`${path.basename(f)} — ${m[2]}`);
        etiquettes = [];
      }
    }
  }
  return parExigence;
}

/** Les essais e2e dont le texte nomme l'exigence (identifiant entre délimiteurs). */
function essaisQuiNomment(ids, contenus) {
  const r = {};
  for (const id of ids) {
    const motif = new RegExp(`(^|[^A-Z0-9-])${id.replace(/-/g, "\\-")}([^0-9]|$)`);
    r[id] = contenus.filter(([, texte]) => motif.test(texte)).map(([f]) => f);
  }
  return r;
}

const bloc = (objet) => "```json\n" + JSON.stringify(objet, null, 2) + "\n```";
const details = (resume, contenu) => `<details><summary>${resume}</summary>\n\n${contenu}\n\n</details>`;

/** Une exigence rendue : son écran, ses scénarios API, ses scénarios BDD, ses essais. */
function rendreExigence(e) {
  const l = [`### ${e.id} — ${e.titre}`, "", `*${e.meta}*`, ""];
  if (e.ecran) {
    l.push(`**Scénario d'écran** (\`${e.ecran.spec}\`) : ouvrir \`/${e.ecran.page}\`${e.ecran.geste ? ` — ${e.ecran.geste}` : ""} ; constater les commandes, puis axe-core.`, "");
    l.push(details("Données JSON du scénario d'écran", bloc(e.ecran.donnees)), "");
  }
  for (const s of e.api) {
    l.push(`**Scénario par l'API ${s.id}** (\`${s.spec}\`) — ${s.titre}`, "");
    l.push(details(`Données JSON de ${s.id} (${s.etapes.length} étapes)`, bloc(s.etapes)), "");
  }
  if (e.bdd.length) l.push(`**BDD** (Cucumber, contre le Java) : ${e.bdd.map((b) => `« ${b} »`).join(" ; ")}`, "");
  if (e.essaisE2e.length) l.push(`**Essais e2e qui la nomment** : ${e.essaisE2e.map((f) => `\`${f}\``).join(", ")}`, "");
  if (e.java.length) l.push(`**Essais Java** : ${e.java.map((f) => `\`${path.basename(f)}\``).join(", ")}`, "");
  if (e.documents?.length) l.push(`**Preuve d'infrastructure ou d'exploitation** : ${e.documents.map((f) => `\`${f}\``).join(", ")}`, "");
  if (!e.ecran && !e.api.length && !e.essaisE2e.length) l.push(`> **Aucun scénario e2e** — ${e.sansE2e}`, "");
  return l.join("\n");
}

/** Urgences-santé 26-015-DO : avis, écrans, comportements, BDD, couverture. */
function exigences26015(contenus, bdd) {
  const avis = lire("docs/26-015-DO_AO/exigences-26-015-DO.json").exigences;
  const ecrans = Object.fromEntries(lire("e2e/donnees/urgences-sante/exigences-fonctionnelles.json").exigences.map((x) => [x.id, x]));
  const scenarios = [
    ...lire("e2e/donnees/urgences-sante/comportements.json").scenarios.map((x) => ({ ...x, spec: "urgences-sante-comportements.spec.ts" })),
    ...lire("e2e/donnees/urgences-sante/technologie.json").scenarios.map((x) => ({ ...x, spec: "urgences-sante-technologie.spec.ts" })),
  ];
  const verdicts = Object.fromEntries(lire("docs/26-015-DO_AO/couverture-26-015-DO.json").jugements.map((j) => [j.id, j]));
  const nomment = essaisQuiNomment(avis.map((a) => a.id), contenus);
  return avis.map((a) => {
    const ecran = ecrans[a.id];
    const v = verdicts[a.id] ?? {};
    return {
      id: a.id, titre: a.description, meta: `${a.type} · ${a.famille} · verdict : ${v.verdict ?? "—"}${v.lot ? ` · lot ${v.lot}` : ""}`,
      ecran: ecran && { spec: "urgences-sante-exigences.spec.ts", page: ecran.page, geste: ecran.geste,
        donnees: { page: ecran.page, prealable: ecran.prealable ?? [], preuve: ecran.preuve } },
      api: scenarios.filter((s) => s.exigences.includes(a.id)),
      bdd: bdd[a.id] ?? [], essaisE2e: nomment[a.id], java: ecran?.essaiProfond ?? [],
      sansE2e: a.id.includes("-TN-")
        ? `exigence technologique : ${v.ecart || v.preuves || "prouvée par les essais Java et la documentation d'exploitation"}`
        : "à couvrir",
      fonctionnelle: !a.id.includes("-TN-"),
    };
  });
}

/** FX-AO-20260810 : grille, écrans, comportements. */
function exigencesFxAo(contenus) {
  const grille = lire("e2e/donnees/fx-ao/exigences.json").exigences;
  const scenarios = lire("e2e/donnees/fx-ao/comportements.json").scenarios;
  const nomment = essaisQuiNomment([], contenus);
  return grille.map((c) => ({
    id: `FX-${c.id.padStart(3, "0")}`, titre: c.intitule, meta: `critère ${c.id} · ${c.bloc} · statut ${c.statut}`,
    ecran: c.page && { spec: "fx-ao-exigences.spec.ts", page: c.page, donnees: { page: c.page, prealable: c.prealable ?? [], preuve: c.preuve } },
    api: (c.scenarios ?? []).map((id) => ({ ...scenarios.find((s) => s.id === id), spec: "fx-ao-comportements.spec.ts" })),
    bdd: [], essaisE2e: nomment[c.id] ?? [], java: c.essaiProfond ?? [], documents: c.documents,
    sansE2e: c.statut === "📄" || c.statut === "🏢"
      ? "engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer"
      : (c.essaiProfond?.length || c.documents?.length ? "prouvé hors e2e (essais Java, infrastructure ou documentation cités)" : "à couvrir"),
    fonctionnelle: c.bloc === "fonctionnel",
  }));
}

function bilan(nom, liste) {
  const n = (f) => liste.filter(f).length;
  return `| ${nom} | ${liste.length} | ${n((e) => e.ecran)} | ${n((e) => e.api.length)} | ${n((e) => e.bdd.length)} | ${n((e) => e.ecran || e.api.length || e.essaisE2e.length)} | ${n((e) => !e.ecran && !e.api.length && !e.essaisE2e.length)} |`;
}

function construire() {
  const contenus = fichiers("e2e/tests", ".ts").map((f) => [f.replace(/^e2e\//, ""), fs.readFileSync(path.join(RACINE, f), "utf8")]);
  const us = exigences26015(contenus, scenariosBdd());
  const fx = exigencesFxAo(contenus);
  const doc = [
    "# Scénarios de bout en bout, exigence par exigence",
    "",
    "> Généré par `node scripts/construire-scenarios-e2e.cjs` — ne pas modifier à la main. Écrit pour l'équipe qui",
    "> démontre et l'évaluateur qui vérifie : chaque scénario ci-dessous est exécuté contre le serveur Java par",
    "> `backend-java/scripts/campagne-e2e.sh`, et ses données JSON sont celles que l'essai lit.",
    "",
    "## Comment les jouer",
    "",
    "```bash",
    "# Écrans, comportements par l'API, parcours — contre le Java, base vierge Liquibase",
    "E2E_API_PORT=4031 E2E_WEB_PORT=5331 backend-java/scripts/campagne-e2e.sh \\",
    "  tests/e2e/urgences-sante-exigences tests/e2e/urgences-sante-comportements tests/e2e/urgences-sante-technologie \\",
    "  tests/e2e/fx-ao-exigences tests/e2e/fx-ao-comportements tests/e2e/fx-ao-parcours",
    "# BDD des exigences 26-015-DO",
    "(cd backend-java && mvn -o test -Dtest=ExigencesBddTest -Djacoco.skip=true)",
    "```",
    "",
    "Trois formes de données JSON :",
    "- **Écran** (`page`, `prealable`, `preuve`) : la page ouverte, les gestes qui mènent à l'état utile, les commandes",
    "  (`data-testid`) qui doivent y être ; `a|b` accepte l'une ou l'autre, `prefixe:x` un identifiant qui commence par `x`.",
    "- **API** (`etapes`) : l'acteur, la méthode, le chemin, le corps, l'attendu (`statut`, `corps` en sous-ensemble,",
    "  `texte` en expression régulière) et les captures réutilisées par `{{nom}}` dans les étapes suivantes.",
    "- **BDD** : les scénarios Gherkin étiquetés par l'exigence, contre le serveur démarré dans le processus.",
    "",
    "## Bilan",
    "",
    "| Avis | Exigences | Scénario d'écran | Scénario par l'API | BDD | Au moins un e2e | Sans e2e |",
    "|---|---|---|---|---|---|---|",
    bilan("Urgences-santé 26-015-DO", us),
    bilan("FX-AO-20260810", fx),
    "",
    "« Sans e2e » : exigences technologiques, engagements contractuels ou responsabilités de l'établissement — chacune",
    "dit, à sa ligne, par quoi elle est prouvée ou pourquoi aucun comportement logiciel n'est à jouer.",
    "",
    "## 1. Urgences-santé — AO 26-015-DO (144 exigences)",
    "",
    ...us.map(rendreExigence),
    "## 2. Santé Québec Laurentides — FX-AO-20260810 (115 critères)",
    "",
    ...fx.map(rendreExigence),
  ].join("\n");
  return { doc, us, fx };
}

const { doc, us, fx } = construire();
if (process.argv.includes("--verifier")) {
  const orphelines = [...us, ...fx].filter((e) => e.fonctionnelle && !e.ecran && !e.api.length && !e.essaisE2e.length).map((e) => e.id);
  if (orphelines.length) {
    console.error(`✗ exigences fonctionnelles sans scénario e2e : ${orphelines.join(", ")}`);
    process.exit(1);
  }
  if (!fs.existsSync(SORTIE) || fs.readFileSync(SORTIE, "utf8") !== doc) {
    console.error("✗ docs/SCENARIOS-E2E.md n'est pas à jour : relancez node scripts/construire-scenarios-e2e.cjs");
    process.exit(1);
  }
  console.log("✓ chaque exigence fonctionnelle a son scénario e2e ; le document est à jour");
} else {
  fs.writeFileSync(SORTIE, doc);
  console.log(`✓ ${path.relative(RACINE, SORTIE)} — ${us.length} + ${fx.length} exigences`);
}
