/**
 * Aperçu d'un classeur (xlsx) ou d'un fichier CSV en HTML, **échappé cellule par cellule**.
 *
 * Remplace `xlsx` (SheetJS 0.18, vulnérabilités élevées sans correctif sur le registre npm) par
 * `exceljs`, déjà utilisé côté serveur. Au passage, un contenu de cellule n'est plus injecté tel
 * quel dans la page : une feuille hostile ne peut plus y écrire un script.
 */
import ExcelJS from "exceljs";

export function echapper(s: unknown): string {
  return String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c] as string));
}

const texteDe = (v: ExcelJS.CellValue): string => {
  if (v === null || v === undefined) return "";
  if (v instanceof Date) return v.toISOString().slice(0, 10);
  if (typeof v === "object") {
    const o = v as { richText?: Array<{ text: string }>; result?: unknown; text?: string; hyperlink?: string };
    if (o.richText) return o.richText.map((r) => r.text).join("");
    if ("result" in o) return String(o.result ?? "");
    if (o.text) return o.text;
  }
  return String(v);
};

function tableau(titre: string, lignes: string[][]): string {
  const corps = lignes.map((l) => `<tr>${l.map((c) => `<td>${echapper(c)}</td>`).join("")}</tr>`).join("");
  return `<h4 style="margin:12px 0 4px">${echapper(titre)}</h4><table><caption class="sr-only">${echapper(titre)}</caption><tbody>${corps}</tbody></table>`;
}

/** Découpe un CSV simple (virgule ou point-virgule, guillemets doubles) sans dépendance. */
export function lignesCsv(texte: string): string[][] {
  const separateur = (texte.split("\n")[0]?.split(";").length ?? 0) > (texte.split("\n")[0]?.split(",").length ?? 0) ? ";" : ",";
  return texte.split(/\r?\n/).filter((l) => l.length > 0).map((l) => {
    const cellules: string[] = []; let courante = ""; let entreGuillemets = false;
    for (let i = 0; i < l.length; i++) {
      const c = l[i];
      if (c === '"') { if (entreGuillemets && l[i + 1] === '"') { courante += '"'; i++; } else entreGuillemets = !entreGuillemets; }
      else if (c === separateur && !entreGuillemets) { cellules.push(courante); courante = ""; }
      else courante += c;
    }
    cellules.push(courante);
    return cellules;
  });
}

export async function apercuTableur(octets: Uint8Array, nom: string): Promise<string> {
  if (/\.csv$/i.test(nom)) return tableau(nom, lignesCsv(new TextDecoder().decode(octets)));
  const classeur = new ExcelJS.Workbook();
  await classeur.xlsx.load(octets.buffer.slice(octets.byteOffset, octets.byteOffset + octets.byteLength) as ArrayBuffer);
  return classeur.worksheets.map((feuille) => {
    const lignes: string[][] = [];
    feuille.eachRow({ includeEmpty: false }, (ligne) => {
      const cellules: string[] = [];
      for (let i = 1; i <= ligne.cellCount; i++) cellules.push(texteDe(ligne.getCell(i).value));
      lignes.push(cellules);
    });
    return tableau(feuille.name, lignes);
  }).join("");
}
