/**
 * **Le guide écran par écran suit le code.** `docs/urgences-sante/GUIDE-ECRANS.md` est tiré de
 * `src/aide/fichesEcrans.ts` : une fiche modifiée sans régénérer le document le ferait mentir.
 */
import { execFileSync } from "node:child_process";
import path from "node:path";
import { describe, expect, it } from "vitest";

describe("GUIDE-ECRANS.md", () => {
  it("est à jour de fichesEcrans.ts (sinon : npm run guide:ecrans)", () => {
    const sortie = execFileSync("node", [path.join(__dirname, "..", "scripts", "generer-guide-ecrans.mjs"), "--verifier"], { encoding: "utf-8" });
    expect(sortie).toContain("à jour");
  });
});
