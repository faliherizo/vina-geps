/**
 * **« Premiers pas » : la mise en route** — ce qu'il faut configurer avant d'utiliser l'application, dans l'ordre,
 * avec l'écran de chaque étape et le passage de la vidéo qui la montre. Chaque fiche y renvoie depuis « Avant de commencer ».
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import PremiersPas, { duree } from "../src/aide/PremiersPas";
import { FICHES, resumePrealable } from "../src/aide/fichesEcrans";
import { MISE_EN_ROUTE, VIDEO_MISE_EN_ROUTE } from "../src/aide/miseEnRoute";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const CHAPITRES = Object.fromEntries(MISE_EN_ROUTE.map((e, i) => [e.id, 10 + i * 15]));

describe("aide — premiers pas : la mise en route", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  beforeEach(() => { conteneur = document.createElement("div"); document.body.appendChild(conteneur); racine = createRoot(conteneur); });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); vi.unstubAllGlobals(); });

  const rendre = async (chapitres: unknown, ouvert: (p: string) => boolean = () => true) => {
    vi.stubGlobal("fetch", vi.fn(async () => ({ ok: chapitres !== null, json: async () => chapitres })));
    await act(async () => { racine.render(<PremiersPas ecranOuvert={ouvert} />); });
  };
  const sel = (id: string) => conteneur.querySelector<HTMLElement>(`[data-testid="${id}"]`);

  it("les étapes, dans l'ordre, chacune avec son lien vers l'écran où elle se fait", async () => {
    await rendre(CHAPITRES);
    const etapes = [...conteneur.querySelectorAll('[data-testid="aide-mise-en-route"] > li')];
    expect(etapes.map((li) => li.querySelector("strong")?.textContent)).toEqual(MISE_EN_ROUTE.map((e) => e.titre));
    const ressources = MISE_EN_ROUTE.find((e) => e.id === "ressources")!;
    expect(sel("aide-mise-en-route-ressources-lien")?.getAttribute("href")).toMatch(/\/usressources$/);
    expect(sel("aide-mise-en-route-ressources")?.textContent).toContain("aucune ressource planifiable");
    expect(sel("aide-mise-en-route-ressources-lien")?.textContent).toContain(FICHES[ressources.ecran].titre);
    expect(sel("aide-mise-en-route-regles")?.textContent).toContain("(facultatif)");
  });

  it("la vidéo : sans téléchargement d'avance, avec sous-titres et description", async () => {
    await rendre(CHAPITRES);
    const video = sel("help-video-mise-en-route") as HTMLVideoElement;
    expect([video.getAttribute("src"), video.getAttribute("preload")]).toEqual([VIDEO_MISE_EN_ROUTE.video, "none"]);
    expect(video.querySelector("track")?.getAttribute("src")).toBe(VIDEO_MISE_EN_ROUTE.sousTitres);
    expect(document.getElementById(video.getAttribute("aria-describedby")!)?.textContent).toContain("Mise en route commentée");
  });

  it("« Voir dans la vidéo » porte au début de l'étape et lance la lecture", async () => {
    await rendre(CHAPITRES);
    const video = sel("help-video-mise-en-route") as HTMLVideoElement;
    const lire = vi.fn(async () => {});
    video.play = lire;
    const bouton = sel("aide-mise-en-route-ressources-voir")!;
    expect(bouton.textContent).toContain(`à ${duree(CHAPITRES.ressources)}`);
    act(() => bouton.click());
    expect(video.currentTime).toBe(CHAPITRES.ressources);
    expect(lire).toHaveBeenCalled();
  });

  it("sans chapitres (vidéo pas encore tournée, réponse inattendue), aucun bouton ; un écran fermé n'a pas de lien", async () => {
    await rendre(null, (p) => p !== "usressources");
    expect(conteneur.querySelectorAll('[data-testid$="-voir"]')).toHaveLength(0);
    expect(sel("aide-mise-en-route-ressources-lien")).toBeNull();
    expect(sel("aide-mise-en-route-referentiels-lien")).not.toBeNull();
    act(() => racine.unmount());
    racine = createRoot(conteneur);
    await rendre([1, 2]);
    expect(conteneur.querySelectorAll('[data-testid$="-voir"]')).toHaveLength(0);
  });

  it("la durée se lit en mots", () => {
    expect([duree(8), duree(83), duree(120.7)]).toEqual(["8 s", "1 min 23 s", "2 min 00 s"]);
  });

  it("un préalable se résume : son libellé court, sinon l'écran où il se fait, sinon sa première proposition", () => {
    expect(resumePrealable({ texte: "x", court: "Court" })).toBe("Court");
    expect(resumePrealable({ texte: "x", ecran: "usressources" })).toBe(FICHES.usressources.titre);
    expect(resumePrealable({ texte: "Le plan d'abonnement : un module hors abonnement reste fermé." })).toBe("Le plan d'abonnement");
    const long = resumePrealable({ texte: `${"mot ".repeat(40)}fin.` });
    expect(long.endsWith("…")).toBe(true);
    expect(long.length).toBeLessThanOrEqual(91);
    const tous = Object.values(FICHES).flatMap((f) => f.prealables.map(resumePrealable));
    expect(tous.filter((r) => !r || r.length > 91)).toEqual([]);
  });
});
