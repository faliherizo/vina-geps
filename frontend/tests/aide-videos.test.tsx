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
import { FICHES, resumePrealable } from "../src/aide/fichesEcrans";
import { MISE_EN_ROUTE, VIDEO_MISE_EN_ROUTE } from "../src/aide/miseEnRoute";

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

  it("chaque vidéo annonce les préalables actuels de sa fiche (sinon : node scripts/introduire-aides.mjs)", () => {
    const annonces = JSON.parse(readFileSync(resolve(PUBLIC, "aide", "videos", "introductions.json"), "utf8")) as Record<string, string[]>;
    const perimees = Object.entries(FICHES)
      .filter(([p, f]) => JSON.stringify(annonces[p]) !== JSON.stringify(f.prealables.map(resumePrealable)))
      .map(([p]) => p);
    expect({ videosAuxPrealablesPerimes: perimees }).toEqual({ videosAuxPrealablesPerimes: [] });
  });

  it("la vidéo de mise en route existe, avec ses sous-titres et un début pour chaque étape", () => {
    expect(statSync(fichier(VIDEO_MISE_EN_ROUTE.video)).size).toBeGreaterThan(500_000);
    expect(readFileSync(fichier(VIDEO_MISE_EN_ROUTE.sousTitres), "utf8")).toMatch(/^WEBVTT[\s\S]*Mise en route\./);
    const chapitres = JSON.parse(readFileSync(fichier(VIDEO_MISE_EN_ROUTE.chapitres), "utf8")) as Record<string, number>;
    expect(Object.keys(chapitres)).toEqual(MISE_EN_ROUTE.map((e) => e.id));
    const debuts = Object.values(chapitres);
    expect(debuts).toEqual([...debuts].sort((a, b) => a - b));
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
