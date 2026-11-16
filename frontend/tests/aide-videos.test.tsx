/**
 * **Une démonstration commentée par fiche d'aide** : chaque écran de `fichesEcrans.ts` a sa vidéo avec voix et ses
 * sous-titres français (`public/aide/videos`, montés par `scripts/monter-recit.mjs`), et la page d'aide les montre.
 * Une fiche ajoutée sans sa vidéo fait échouer cet essai : jouer un essai e2e qui la raconte (`raconter`), ou `node scripts/tourner-aides.mjs --seulement <écran>`.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { existsSync, readFileSync, statSync } from "node:fs";
import { resolve } from "node:path";
import Help, { videoDeFiche } from "../src/Help";
import { FICHES } from "../src/aide/fichesEcrans";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const PUBLIC = resolve(__dirname, "..", "public");
const fichier = (chemin: string) => resolve(PUBLIC, chemin.replace(/^\//, ""));

describe("aide — une vidéo commentée et ses sous-titres par écran", () => {
  it("chaque fiche a sa vidéo (image et voix) et ses sous-titres WebVTT, non vides", () => {
    const manquantes = Object.keys(FICHES).filter((p) => {
      const { video, sousTitres } = videoDeFiche(p);
      return !existsSync(fichier(video)) || statSync(fichier(video)).size < 50_000 || !existsSync(fichier(sousTitres));
    });
    expect({ fichesSansVideo: manquantes }).toEqual({ fichesSansVideo: [] });
  });

  it("les sous-titres disent la fiche : ils commencent par son titre", () => {
    const decales = Object.entries(FICHES).filter(([p, f]) => {
      const texte = readFileSync(fichier(videoDeFiche(p).sousTitres), "utf8");
      return !texte.startsWith("WEBVTT") || !texte.includes(`${f.titre.replace(/[«»]/g, "").replace(/\s+/g, " ").trim()}.`);
    }).map(([p]) => p);
    expect({ sousTitresQuiNeDisentPasLaFiche: decales }).toEqual({ sousTitresQuiNeDisentPasLaFiche: [] });
  });

  describe("la page d'aide", () => {
    let conteneur: HTMLDivElement;
    let racine: Root;
    beforeEach(() => { conteneur = document.createElement("div"); document.body.appendChild(conteneur); racine = createRoot(conteneur); });
    afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

    it("chaque fiche montre sa vidéo, sans la télécharger d'avance, avec sa piste de sous-titres et sa description", () => {
      act(() => { racine.render(<Help section="fiche-dashboard" />); });
      const video = conteneur.querySelector<HTMLVideoElement>('[data-testid="help-video-dashboard"]')!;
      expect(video.getAttribute("src")).toBe("/aide/videos/dashboard.webm");
      expect(video.getAttribute("preload")).toBe("none");
      expect(video.hasAttribute("controls")).toBe(true);
      const piste = video.querySelector("track")!;
      expect([piste.getAttribute("kind"), piste.getAttribute("srclang"), piste.getAttribute("src")]).toEqual(["captions", "fr", "/aide/videos/dashboard.fr.vtt"]);
      const description = conteneur.ownerDocument.getElementById(video.getAttribute("aria-describedby")!);
      expect(description?.textContent).toContain("Démonstration commentée");
    });
  });
});
