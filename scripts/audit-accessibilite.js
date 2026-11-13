#!/usr/bin/env node
/**
 * Audit statique d'accessibilité du balisage — SGQRI 008 3.0 / WCAG 2.2 niveau AA.
 *
 * Complète l'audit dynamique axe-core exécuté par Playwright (`helpers/a11y.ts`) : celui-ci
 * ne voit que les écrans effectivement parcourus par un test, alors que ce scanner passe sur
 * **tout** le balisage du dépôt, y compris les branches conditionnelles jamais rendues en
 * démonstration.
 *
 * Les deux sont nécessaires et ne se recouvrent pas : le scanner attrape les manquements
 * structurels (libellé absent, tableau sans en-tête), axe attrape ce qui dépend du rendu
 * (contraste calculé, ordre de focus réel).
 *
 * Usage :
 *   node scripts/audit-accessibilite.js            # rapport lisible
 *   node scripts/audit-accessibilite.js --json     # sortie machine
 *   node scripts/audit-accessibilite.js --strict   # code de sortie 1 si manquement
 */
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..");
const TARGETS = [
  { dir: path.join(ROOT, "frontend", "src"), exts: [".tsx"] },
  { dir: path.join(ROOT, "frontend"), exts: [".html"], shallow: true },
  { dir: path.join(ROOT, "frontend", "public"), exts: [".html"] },
];
const SKIP_DIRS = new Set(["node_modules", "dist", "build", ".git", "coverage"]);

/** Critères vérifiés, avec leur référence normative — affichée dans le rapport. */
const RULES = {
  "img-alt": { wcag: "1.1.1", level: "A", label: "Image sans alternative textuelle" },
  "control-label": { wcag: "1.3.1 / 4.1.2", level: "A", label: "Champ de formulaire sans libellé" },
  "button-name": { wcag: "4.1.2", level: "A", label: "Bouton sans nom accessible" },
  "link-name": { wcag: "2.4.4", level: "A", label: "Lien sans intitulé" },
  "table-caption": { wcag: "1.3.1", level: "A", label: "Tableau sans légende" },
  "th-scope": { wcag: "1.3.1", level: "A", label: "En-tête de tableau sans portée (scope)" },
  "positive-tabindex": { wcag: "2.4.3", level: "A", label: "tabIndex positif — ordre de focus imposé" },
  "aria-hidden-focusable": { wcag: "4.1.2", level: "A", label: "Élément masqué mais focusable" },
  "iframe-title": { wcag: "4.1.2", level: "A", label: "Cadre sans titre" },
  "html-lang": { wcag: "3.1.1", level: "A", label: "Langue du document non déclarée" },
  "placeholder-only": { wcag: "3.3.2", level: "A", label: "Champ libellé par le seul texte indicatif" },
  "select-label": { wcag: "1.3.1", level: "A", label: "Liste déroulante sans libellé" },
  "duplicate-id": { wcag: "4.1.1", level: "A", label: "Identifiant dupliqué dans le fichier" },
  "click-handler": { wcag: "2.1.1", level: "A", label: "Élément cliquable non atteignable au clavier" },
  "noninteractive-tabindex": { wcag: "2.4.3", level: "A", label: "Point de tabulation sur un élément non interactif" },
  "cellule-flex": { wcag: "1.3.1 / 1.4.10", level: "A", label: "Cellule de tableau sortie du flux (display flex/grid)" },
};

/** Éléments naturellement focusables : y placer tabIndex={0} est redondant mais sans effet. */
const INTERACTIVE = new Set(["a", "button", "input", "select", "textarea", "details", "summary", "audio", "video"]);

const findings = [];
const add = (rule, file, line, detail) =>
  findings.push({ rule, file: path.relative(ROOT, file), line, detail });

// ── Parcours des fichiers ───────────────────────────────────────────────────

function walk(dir, exts, shallow, out = []) {
  let entries;
  try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { return out; }
  for (const e of entries) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) {
      if (shallow || SKIP_DIRS.has(e.name)) continue;
      walk(full, exts, false, out);
    } else if (exts.some((x) => e.name.endsWith(x))) {
      out.push(full);
    }
  }
  return out;
}

const lineOf = (src, index) => src.slice(0, index).split("\n").length;

/**
 * Extrait les balises ouvrantes en respectant les accolades et les chaînes.
 *
 * Une expression régulière naïve s'arrête au premier `>` — or les gestionnaires JSX en
 * contiennent un dans chaque flèche `=>`. Tout attribut placé **après** un `onChange`
 * devenait alors invisible, produisant des dizaines de faux signalements.
 */
function openingTags(src) {
  const out = [];
  const re = /<([a-zA-Z][a-zA-Z0-9]*)\b/g;
  let m;
  while ((m = re.exec(src))) {
    let depth = 0, quote = null, i = m.index + m[0].length;
    for (; i < src.length; i++) {
      const ch = src[i];
      if (quote) { if (ch === quote && src[i - 1] !== "\\") quote = null; continue; }
      // Une apostrophe française dans un texte JSX (« d'API », « l'école ») n'ouvre pas une chaîne :
      // la prendre pour un guillemet avalait la fin de la balise et prêtait à un composant les
      // attributs de ses enfants.
      if (ch === "'" && /[A-Za-zÀ-ÿ]/.test(src[i - 1] ?? "") && /[A-Za-zÀ-ÿ]/.test(src[i + 1] ?? "")) continue;
      if (ch === '"' || ch === "'" || ch === "`") { quote = ch; continue; }
      if (ch === "{") depth++;
      else if (ch === "}") depth--;
      else if (ch === ">" && depth === 0) break;
    }
    out.push({ tag: m[1], raw: src.slice(m.index, i + 1), index: m.index });
    re.lastIndex = i;
  }
  return out;
}

/**
 * Le contrôle est-il **enveloppé** par un `<label>` ?
 *
 * `<label>Nom <input/></label>` associe le libellé sans `for`/`id` — technique WCAG H44,
 * largement utilisée dans ce dépôt. L'ignorer produirait des centaines de faux signalements.
 */
function insideLabel(src, index) {
  const before = src.slice(0, index);
  const opens = (before.match(/<label\b/g) || []).length;
  const closes = (before.match(/<\/label>/g) || []).length;
  return opens > closes;
}

/** Attributs d'une balise ouvrante, y compris les valeurs JSX entre accolades. */
function attrs(tag) {
  const out = {};
  // Un attribut dont la valeur est du JSX (`rendre={(x) => <tr aria-hidden…>}`) n'apporte pas
  // ses attributs intérieurs à la balise : chaque groupe `{…}` contenant une balise est vidé.
  let plat = ""; let depth = 0; let debut = -1;
  for (let i = 0; i < tag.length; i++) {
    const ch = tag[i];
    if (ch === "{") { if (depth === 0) debut = i; depth++; continue; }
    if (ch === "}") { depth--; if (depth === 0) { const groupe = tag.slice(debut + 1, i); plat += groupe.includes("<") ? "{}" : `{${groupe}}`; debut = -1; } continue; }
    if (depth === 0) plat += ch;
  }
  tag = plat;
  const re = /([a-zA-Z-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|\{([^}]*)\})|(\b[a-zA-Z-]+\b)(?=[\s/>])/g;
  let m;
  while ((m = re.exec(tag))) {
    const name = m[1] ?? m[5];
    if (!name) continue;
    out[name] = m[2] ?? m[3] ?? m[4] ?? "";
  }
  return out;
}

const has = (a, ...names) => names.some((n) => Object.hasOwn(a, n) && String(a[n]).trim() !== "");

/** Le contrôle porte-t-il un nom accessible autrement que par un `<label for>` ? */
const selfLabelled = (a) =>
  has(a, "aria-label", "aria-labelledby", "title") || a.type === "hidden";

// ── Analyse d'un fichier ────────────────────────────────────────────────────

/** Neutralise commentaires de bloc, de ligne et commentaires HTML, en préservant les positions. */
function stripComments(raw) {
  return raw
    .replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, " "))
    .replace(/(^|[^:])\/\/[^\n]*/g, (m, p1) => p1 + " ".repeat(m.length - p1.length))
    .replace(/<!--[\s\S]*?-->/g, (m) => m.replace(/[^\n]/g, " "));
}

function analyse(file) {
  const raw = fs.readFileSync(file, "utf8");
  // Les décalages sont conservés : les numéros de ligne restent exacts.
  const src = stripComments(raw);
  const isHtml = file.endsWith(".html");

  // Identifiants déclarés par un `<label htmlFor>` / `<label for>` : servent à valider
  // l'association des champs.
  const labelled = new Set();
  for (const m of src.matchAll(/<label[^>]*\b(?:htmlFor|for)\s*=\s*(?:"([^"]*)"|'([^']*)'|\{`?([^}`]*)`?\})/g)) {
    labelled.add((m[1] ?? m[2] ?? m[3] ?? "").trim());
  }

  const seenIds = new Map();

  for (const m of openingTags(src)) {
    const full = m.raw;
    const tag = m.tag.toLowerCase();
    const a = attrs(full.slice(m.tag.length + 1));
    const line = lineOf(src, m.index);

    // Identifiants dupliqués — uniquement les valeurs littérales (les gabarits sont dynamiques).
    if (a.id && !a.id.includes("$") && !a.id.includes("{")) {
      if (seenIds.has(a.id)) {
        add("duplicate-id", file, line, `id="${a.id}" déjà déclaré ligne ${seenIds.get(a.id)}`);
      } else seenIds.set(a.id, line);
    }

    // tabIndex={0} sur un élément non interactif ajoute un arrêt de tabulation qui ne fait
    // rien : sur un écran dense, cela transforme la navigation clavier en parcours d'obstacles.
    const tabIndexValue = String(a.tabIndex ?? a.tabindex ?? "").trim();
    if (tabIndexValue === "0" && !INTERACTIVE.has(tag) && !has(a, "role") && !has(a, "onClick")) {
      add("noninteractive-tabindex", file, line, `<${tag} tabIndex={0}>`);
    }
    if (a.tabIndex && /^\d+$/.test(a.tabIndex.trim()) && Number(a.tabIndex) > 0) {
      add("positive-tabindex", file, line, `<${tag} tabIndex={${a.tabIndex}}>`);
    }
    if (a.tabindex && /^\d+$/.test(a.tabindex.trim()) && Number(a.tabindex) > 0) {
      add("positive-tabindex", file, line, `<${tag} tabindex="${a.tabindex}">`);
    }

    // Un élément masqué aux technologies d'assistance ne doit pas rester atteignable.
    if (has(a, "aria-hidden") && (has(a, "tabIndex") || has(a, "tabindex"))) {
      const v = String(a.tabIndex ?? a.tabindex).trim();
      if (v !== "-1") add("aria-hidden-focusable", file, line, `<${tag} aria-hidden tabIndex=${v}>`);
    }

    switch (tag) {
      case "img":
        if (!Object.hasOwn(a, "alt")) add("img-alt", file, line, full.slice(0, 90));
        break;

      case "iframe":
        if (!has(a, "title")) add("iframe-title", file, line, full.slice(0, 90));
        break;

      case "html":
        if (isHtml && !has(a, "lang")) add("html-lang", file, line, "<html> sans attribut lang");
        break;

      case "input": {
        const type = (a.type || "text").replace(/[{}"']/g, "");
        if (["hidden", "submit", "button", "reset", "image"].includes(type)) break;
        const associated = a.id && (labelled.has(a.id) || [...labelled].some((l) => a.id.includes(l)));
        if (!associated && !selfLabelled(a) && !insideLabel(src, m.index)) {
          const rule = has(a, "placeholder") ? "placeholder-only" : "control-label";
          add(rule, file, line, full.slice(0, 100));
        }
        break;
      }

      case "select":
      case "textarea": {
        const associated = a.id && (labelled.has(a.id) || [...labelled].some((l) => a.id.includes(l)));
        if (!associated && !selfLabelled(a) && !insideLabel(src, m.index)) {
          add(tag === "select" ? "select-label" : "control-label", file, line, full.slice(0, 100));
        }
        break;
      }

      case "th":
        if (!has(a, "scope")) add("th-scope", file, line, full.slice(0, 80));
        break;

      case "div":
      case "span":
      case "li":
      case "td":
        // Un gestionnaire de clic sur un élément non interactif doit être atteignable au
        // clavier : rôle explicite **et** point d'entrée au focus.
        if (has(a, "onClick") && !has(a, "role") && !has(a, "aria-hidden")
            && !has(a, "onKeyDown", "onKeyUp", "onKeyPress")) {
          add("click-handler", file, line, `<${tag} onClick> sans role ni gestion clavier`);
        }
        break;
      default:
        break;
    }
  }

  // Tableaux sans légende : on inspecte chaque `<table>` et son contenu jusqu'à la fermeture.
  for (const m of src.matchAll(/<table\b[^>]*>([\s\S]*?)<\/table>/g)) {
    if (!/<caption\b/.test(m[1]) && !/aria-label/.test(m[0].slice(0, m[0].indexOf(">")))) {
      add("table-caption", file, lineOf(src, m.index), "<table> sans <caption> ni aria-label");
    }
  }

  // Boutons et liens sans intitulé perceptible : contenu vide, ou uniquement une icône.
  for (const m of src.matchAll(/<(button|a)\b([^>]*)>([\s\S]{0,200}?)<\/\1>/g)) {
    const [, tag, rawAttrs, inner] = m;
    const a = attrs(rawAttrs);
    if (selfLabelled(a)) continue;
    // Retire les éléments décoratifs et le balisage pour ne garder que le texte perceptible.
    // Contenu dynamique (`{t("…")}`, `{variable}`) : indécidable statiquement, donc non signalé.
    // L'audit dynamique axe-core, lui, le verra au rendu.
    if (/\{[^}]*\}/.test(inner.replace(/<[^>]*aria-hidden[^>]*>[\s\S]*?<\/[a-zA-Z]+>/g, ""))) continue;
    const text = inner
      .replace(/<[^>]*aria-hidden[^>]*>[\s\S]*?<\/[a-zA-Z]+>/g, "")
      .replace(/<[^>]+>/g, "")
      .replace(/[\p{Emoji_Presentation}\p{Extended_Pictographic}\s]/gu, "");
    if (!text.trim()) {
      add(tag === "button" ? "button-name" : "link-name", file, lineOf(src, m.index),
          `<${tag}> sans intitulé perceptible`);
    }
  }

  // ── WCAG 2.2 ───────────────────────────────────────────────────────────────
  //
  // 3.3.8 « Authentification accessible (minimum) » : un champ d'authentification ne doit pas
  // exiger un test de fonction cognitive. Bloquer le collage force à retaper de mémoire un mot
  // de passe issu d'un gestionnaire — exactement ce que le critère interdit.
  for (const m of openingTags(src)) {
    const a = attrs(m.raw);
    if (m.tag !== "input") continue;
    const type = (a.type || "").toLowerCase();
    if (type !== "password") continue;
    if (/onPaste\s*=/.test(m.raw)) {
      add("auth-paste-bloque", file, lineOf(src, m.index),
          "champ de mot de passe interceptant le collage (WCAG 2.2 — 3.3.8)");
    }
  }

  // 1.3.1 / 1.4.10 : une cellule `<td>`/`<th>` mise en `display: flex` (ou `grid`) quitte le
  // rendu tabulaire — sa hauteur ne compte plus dans la rangée, et ses boutons se superposent aux
  // lignes suivantes. La disposition en ligne se fait dans un `<div class="actions-row">` **dans** la cellule.
  for (const m of openingTags(src)) {
    if (m.tag !== "td" && m.tag !== "th") continue;
    if (/\bdisplay\s*:\s*["']?(?:flex|grid|inline-flex)\b/.test(m.raw) || /className\s*=\s*["'][^"']*\b(?:actions|actions-row)\b/.test(m.raw)) {
      add("cellule-flex", file, lineOf(src, m.index), `<${m.tag}> disposée en flex ou grid : envelopper le contenu dans un <div class="actions-row">`);
    }
  }

  // 2.5.7 « Mouvements de glissement » : toute action réalisable par glisser-déposer doit
  // disposer d'une alternative à pointeur unique (bouton, menu, champ de saisie).
  for (const m of openingTags(src)) {
    if (!/\bdraggable\s*=\s*(?:"true"|\{true\})/.test(m.raw) && !/onDragStart\s*=/.test(m.raw)) continue;
    // Une alternative déclarée sur le même élément lève le signalement.
    if (/data-drag-alternative/.test(m.raw)) continue;
    add("glisser-sans-alternative", file, lineOf(src, m.index),
        "élément glissable sans alternative à pointeur unique (WCAG 2.2 — 2.5.7)");
  }
}

// ── Exécution ───────────────────────────────────────────────────────────────

const files = TARGETS.flatMap((t) => walk(t.dir, t.exts, t.shallow));
files.forEach(analyse);

if (process.argv.includes("--json")) {
  console.log(JSON.stringify({ files: files.length, findings }, null, 2));
  process.exit(findings.length && process.argv.includes("--strict") ? 1 : 0);
}

console.log(`Audit d'accessibilité — SGQRI 008 3.0 / WCAG 2.2 AA`);
console.log(`${files.length} fichiers analysés\n`);

if (!findings.length) {
  console.log("✅ Aucun manquement structurel détecté.");
  process.exit(0);
}

const byRule = new Map();
for (const f of findings) {
  if (!byRule.has(f.rule)) byRule.set(f.rule, []);
  byRule.get(f.rule).push(f);
}

const sorted = [...byRule.entries()].sort((a, b) => b[1].length - a[1].length);
for (const [rule, items] of sorted) {
  const meta = RULES[rule] ?? { wcag: "?", level: "?", label: rule };
  console.log(`\n▸ ${meta.label}  —  WCAG ${meta.wcag} (niveau ${meta.level})  ·  ${items.length} occurrence(s)`);
  const byFile = new Map();
  for (const it of items) {
    if (!byFile.has(it.file)) byFile.set(it.file, []);
    byFile.get(it.file).push(it);
  }
  for (const [file, list] of [...byFile.entries()].sort((a, b) => b[1].length - a[1].length)) {
    console.log(`    ${file}  (${list.length})`);
    for (const it of list.slice(0, 3)) console.log(`      ligne ${it.line} : ${it.detail}`);
    if (list.length > 3) console.log(`      … et ${list.length - 3} autre(s)`);
  }
}

console.log(`\nTotal : ${findings.length} manquement(s) sur ${byRule.size} critère(s).`);
process.exit(process.argv.includes("--strict") ? 1 : 0);
