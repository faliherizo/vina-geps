/**
 * Le groupe sur lequel s'ouvre la messagerie : le choix de la session, sinon le sien, sinon le premier. Le premier
 * seul ouvrait la messagerie d'une infirmière de Saint-Jérôme sur Argenteuil, où elle ne connaît personne.
 */
import { describe, expect, it } from "vitest";
import { groupeParDefaut, type GroupeSq } from "../src/santequebec/perimetre";

const ARG: GroupeSq = { id: "ARG", code: "ARG", nom: "CMSSS d'Argenteuil" };
const HSJ: GroupeSq = { id: "HSJ", code: "HSJ", nom: "Hôpital de Saint-Jérôme" };
const GARDE: GroupeSq = { id: "gt-1", code: "gt-1", nom: "Garde de cardiologie" };

describe("groupe ouvert d'office dans la messagerie", () => {
  it("ouvre sur le groupe de la personne, pas sur le premier de la liste", () => {
    expect(groupeParDefaut([ARG, HSJ, GARDE], ["HSJ", "gt-1"], "")).toBe("HSJ");
  });

  it("garde le choix fait dans la session, même hors de ses groupes", () => {
    expect(groupeParDefaut([ARG, HSJ], ["HSJ"], "ARG")).toBe("ARG");
  });

  it("un choix retenu qui a disparu retombe sur le sien", () => {
    expect(groupeParDefaut([ARG, HSJ], ["HSJ"], "parti")).toBe("HSJ");
  });

  it("sans groupe à soi, le premier ; sans groupe du tout, rien", () => {
    expect(groupeParDefaut([ARG, HSJ], [], "")).toBe("ARG");
    expect(groupeParDefaut([], ["HSJ"], "")).toBe("");
  });
});
