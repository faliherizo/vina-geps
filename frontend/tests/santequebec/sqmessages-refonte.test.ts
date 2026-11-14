import { describe, expect, it } from "vitest";
import { acteurDe, jourDe } from "../../src/santequebec/pages/sqmessages";

describe("messagerie — règles pures de la refonte", () => {
  it("je suis, pour la messagerie, mon nom de connexion — comme le serveur", () => {
    expect(acteurDe({ id: "usr-1", username: "julie-x" })).toBe("julie-x");
    expect(acteurDe({ id: "usr-1" })).toBe("usr-1");
    expect(acteurDe(null)).toBe("");
  });

  it("le fil se sépare par jour : aujourd'hui, hier, sinon la date", () => {
    const maintenant = new Date("2026-11-13T15:00:00Z");
    expect(jourDe("2026-11-13T08:00:00.000Z", maintenant)).toBe("Aujourd'hui");
    expect(jourDe("2026-11-12T23:00:00.000Z", maintenant)).toBe("Hier");
    expect(jourDe("2026-11-01T08:00:00.000Z", maintenant)).toBe("2026-11-01");
  });
});
