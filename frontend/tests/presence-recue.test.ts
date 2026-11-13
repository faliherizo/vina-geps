/**
 * **La présence reçue, rendue sûre avant d'entrer dans l'arbre React** (`presenceRecue`).
 *
 * Le backend Java ne rend pas `parMilieu`, `parActivite` ni `deplacements` (le multi-site et les
 * activités n'y sont pas portés — mesuré le 8 novembre 2026). « Mon pointage » lisait leur
 * `.length` sans vérifier : sur la pile Java, l'écran s'effondrait dès qu'un quart était en cours.
 */
import { describe, expect, it } from "vitest";
import { presenceRecue } from "../src/MonPointage";

const QUART_JAVA = {
  placeId: "pl-1", milieuId: "m-1", milieu: "Caserne 12", uniteCode: "EST", natureCode: "REG", date: "2031-01-15", debut: 360, fin: 1080,
  debutPrevu: "2031-01-15T11:00:00.000Z", finPrevue: "2031-01-15T23:00:00.000Z", presencePrealableMinutes: 15, prochains: ["FIN_PAUSE"],
  minutesPayees: 310, segments: [{ debut: "2031-01-15T11:00:00.000Z", fin: null, type: "TRAVAIL", minutes: 295 }],
};

describe("Présence reçue", () => {
  it("**la forme du backend Java** — sans répartition ni déplacements — devient des listes vides, jamais un effondrement", () => {
    const p = presenceRecue({ ressource: { id: "rs-1", nom: "Julie" }, quart: QUART_JAVA, pointages: [] })!;
    expect(p.quart!.parMilieu).toEqual([]);
    expect(p.quart!.parActivite).toEqual([]);
    expect(p.quart!.deplacements).toEqual([]);
    // Ce qui était reçu est conservé tel quel : la normalisation comble, elle ne réécrit pas.
    expect(p.quart!.segments).toHaveLength(1);
    expect(p.quart!.prochains).toEqual(["FIN_PAUSE"]);
  });

  it("la forme du Node passe intacte", () => {
    const parMilieu = [{ milieuId: "m-1", milieu: "Caserne 12", minutes: 200 }];
    const p = presenceRecue({ ressource: null, quart: { ...QUART_JAVA, parMilieu, parActivite: [], deplacements: [] }, pointages: [] })!;
    expect(p.quart!.parMilieu).toEqual(parMilieu);
  });

  it("aucun quart en cours reste « aucun quart », et une réponse illisible n'est pas une présence", () => {
    expect(presenceRecue({ ressource: { id: "rs-1", nom: "Julie" }, quart: null, pointages: [] })!.quart).toBeNull();
    // Le harnais d'essai répond `[]` à une route qu'il ne double pas : ce n'est pas un objet.
    expect(presenceRecue([])).toBeNull();
    expect(presenceRecue(null)).toBeNull();
    // Des pointages absents deviennent une liste vide, et une ressource absente « aucune ».
    const p = presenceRecue({ quart: null })!;
    expect([p.pointages, p.ressource]).toEqual([[], null]);
  });
});
