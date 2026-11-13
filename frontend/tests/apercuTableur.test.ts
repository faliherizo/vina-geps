import { describe, expect, it } from "vitest";
import ExcelJS from "exceljs";
import { apercuTableur, echapper, lignesCsv } from "../src/apercuTableur";

describe("aperçu d'un tableur — échappé, xlsx et csv", () => {
  it("échappe chaque cellule : une feuille hostile n'écrit pas de script dans la page", async () => {
    const wb = new ExcelJS.Workbook();
    const f = wb.addWorksheet("Feuille <1>");
    f.addRow(["Nom", "<script>alert(1)</script>", 42]);
    f.addRow([{ richText: [{ text: "gras" }, { text: " et plus" }] }, new Date("2026-09-12T00:00:00Z"), { formula: "A1", result: "calculé" }]);
    const html = await apercuTableur(new Uint8Array(await wb.xlsx.writeBuffer()), "essai.xlsx");
    expect(html).toContain("Feuille &lt;1&gt;");
    expect(html).not.toContain("<script>");
    expect(html).toContain("&lt;script&gt;alert(1)&lt;/script&gt;");
    expect(html).toContain("<td>42</td>");
    expect(html).toContain("<td>gras et plus</td>");
    expect(html).toContain("<td>2026-09-12</td>");
    expect(html).toContain("<td>calculé</td>");
  });

  it("un CSV se découpe sur la virgule ou le point-virgule, guillemets compris", () => {
    expect(lignesCsv('a,b,"c,d"\n1,"dit ""oui""",3\n')).toEqual([["a", "b", "c,d"], ["1", 'dit "oui"', "3"]]);
    expect(lignesCsv("x;y;z\n1;2;3")).toEqual([["x", "y", "z"], ["1", "2", "3"]]);
    expect(lignesCsv("")).toEqual([]);
  });

  it("le CSV passe par le même tableau échappé", async () => {
    const html = await apercuTableur(new TextEncoder().encode("nom,note\n<b>x</b>,5"), "notes.csv");
    expect(html).toContain("&lt;b&gt;x&lt;/b&gt;");
    expect(html).toContain("<caption class=\"sr-only\">notes.csv</caption>");
    expect(echapper(`'"&`)).toBe("&#39;&quot;&amp;");
  });
});
