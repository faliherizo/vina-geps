#!/usr/bin/env node
/**
 * **Un Markdown en PDF** sans LaTeX ni LibreOffice : pandoc rend le HTML (avec une feuille de style sobre,
 * tableaux lisibles), puis le Chromium de Playwright (déjà installé pour les essais e2e) l'imprime en PDF.
 *
 *   node perf/jmeter/pdf.cjs docs/SLA/ACCORD-NIVEAU-SERVICE-PERFORMANCE.md [sortie.pdf]
 */
const fs = require("fs");
const os = require("os");
const path = require("path");
const { execFileSync } = require("child_process");

const STYLE = `
  body { font-family: -apple-system, "Segoe UI", Helvetica, Arial, sans-serif; font-size: 10.5pt; color: #111; margin: 0; }
  h1 { color: #1f5fa8; font-size: 20pt; border-bottom: 2px solid #1f5fa8; padding-bottom: 4px; margin-top: 28px; }
  h2 { color: #1f5fa8; font-size: 15pt; border-bottom: 1px solid #9bb8dc; padding-bottom: 3px; margin-top: 26px; page-break-before: auto; }
  h3 { color: #2b6cb0; font-size: 12pt; margin-top: 18px; }
  table { border-collapse: collapse; width: 100%; margin: 8px 0 14px; font-size: 9.5pt; page-break-inside: avoid; }
  th, td { border: 1px solid #999; padding: 4px 6px; text-align: left; vertical-align: top; }
  th { background: #e8eef7; }
  code { font-family: Menlo, Consolas, monospace; font-size: 9pt; background: #f3f4f6; padding: 0 3px; }
  pre { background: #f3f4f6; padding: 8px; font-size: 8.5pt; white-space: pre-wrap; }
  li { margin: 2px 0; }
`;

async function md2pdf(md, pdf) {
  const html = path.join(os.tmpdir(), `pandoc-${process.pid}.html`);
  const css = path.join(os.tmpdir(), `pandoc-${process.pid}.css`);
  fs.writeFileSync(css, STYLE);
  execFileSync("pandoc", [md, "--from", "gfm", "--to", "html5", "--standalone", "--css", css, "--embed-resources", "--metadata", `title=${path.basename(md, ".md")}`, "-o", html], { stdio: "inherit" });
  const { chromium } = require(path.join(__dirname, "..", "..", "node_modules", "playwright"));
  const navigateur = await chromium.launch();
  try {
    const page = await navigateur.newPage();
    await page.goto(`file://${html}`);
    await page.pdf({ path: pdf, format: "Letter", printBackground: true, margin: { top: "18mm", bottom: "18mm", left: "16mm", right: "16mm" },
      displayHeaderFooter: true, headerTemplate: "<span></span>", footerTemplate: '<div style="font-size:8px;width:100%;text-align:center;color:#666;">Nexu VINA — accord de niveau de service · page <span class="pageNumber"></span> / <span class="totalPages"></span></div>' });
  } finally { await navigateur.close(); fs.unlinkSync(html); fs.unlinkSync(css); }
}

if (require.main === module) {
  const [md, sortie] = process.argv.slice(2);
  if (!md) { console.error("usage : pdf.cjs <document.md> [sortie.pdf]"); process.exit(2); }
  const pdf = sortie ?? md.replace(/\.md$/, ".pdf");
  md2pdf(md, pdf).then(() => console.log(`→ ${pdf}`)).catch((e) => { console.error(`✗ ${e.message}`); process.exit(1); });
}
module.exports = { md2pdf };
