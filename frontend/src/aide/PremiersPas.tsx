/**
 * **« Premiers pas » : la mise en route, filmée et décrite** — ce qu'il faut configurer, dans l'ordre, avant qu'une
 * personne puisse se servir de l'application. Chaque étape mène à son écran (quand il est ouvert à l'organisme) et au
 * passage de la vidéo qui la montre ; la liste écrite est la transcription de la vidéo (WCAG 1.2.1).
 */
import { useEffect, useRef, useState } from "react";
import type { Page } from "../App";
import { FICHES } from "./fichesEcrans";
import { cheminEcran } from "./liensAide";
import { LIBELLE_ACTEUR, MISE_EN_ROUTE, VIDEO_MISE_EN_ROUTE, type ChapitresMiseEnRoute, type EtapeMiseEnRoute } from "./miseEnRoute";

const SECONDES_PAR_MINUTE = 60;

/** 83 → « 1 min 23 s » : lu tel quel par un lecteur d'écran, contrairement à « 1:23 ». */
export const duree = (secondes: number): string => {
  const s = Math.floor(secondes);
  const min = Math.floor(s / SECONDES_PAR_MINUTE);
  return min ? `${min} min ${String(s % SECONDES_PAR_MINUTE).padStart(2, "0")} s` : `${s} s`;
};

/** Les débuts de chapitre écrits par le tournage ; absents (vidéo pas encore tournée), les boutons ne s'affichent pas. */
function useChapitres(): ChapitresMiseEnRoute {
  const [chapitres, setChapitres] = useState<ChapitresMiseEnRoute>({});
  useEffect(() => {
    let actif = true;
    fetch(VIDEO_MISE_EN_ROUTE.chapitres)
      .then((r) => (r.ok ? r.json() : {}))
      .then((c: unknown) => { if (actif && c && typeof c === "object" && !Array.isArray(c)) setChapitres(c as ChapitresMiseEnRoute); })
      .catch(() => {});
    return () => { actif = false; };
  }, []);
  return chapitres;
}

export default function PremiersPas({ ecranOuvert }: { ecranOuvert: (p: Page) => boolean }) {
  const video = useRef<HTMLVideoElement>(null);
  const chapitres = useChapitres();
  const voir = (debut: number) => {
    const v = video.current;
    if (!v) return;
    v.currentTime = debut;
    v.scrollIntoView?.({ block: "nearest" });
    v.play?.()?.catch(() => {});
  };
  return (
    <>
      <p>
        Avant qu'une personne puisse déclarer une absence, lire son horaire ou pointer, ces étapes doivent être faites,
        dans cet ordre : chacune prépare la suivante. La vidéo les montre une à une, avec le compte qui les fait.
      </p>
      <figure className="fiche-video">
        <video ref={video} controls preload="none" src={VIDEO_MISE_EN_ROUTE.video} data-testid="help-video-mise-en-route"
               aria-describedby="mise-en-route-video">
          <track kind="captions" srcLang="fr" label="Français (facultatif)" src={VIDEO_MISE_EN_ROUTE.sousTitres} />
          <a href={VIDEO_MISE_EN_ROUTE.video}>Télécharger la vidéo de mise en route</a>
        </video>
        <figcaption id="mise-en-route-video" className="muted">
          Mise en route commentée : de la création de l'organisme jusqu'à la première absence déclarée. Sous-titres
          français facultatifs ; la liste ci-dessous décrit les mêmes étapes.
        </figcaption>
      </figure>
      <ol className="mise-en-route" data-testid="aide-mise-en-route">
        {MISE_EN_ROUTE.map((e) => (
          <EtapeAide key={e.id} etape={e} debut={chapitres[e.id]} ecranOuvert={ecranOuvert} voir={voir} />
        ))}
      </ol>
    </>
  );
}

function EtapeAide({ etape, debut, ecranOuvert, voir }: {
  etape: EtapeMiseEnRoute; debut: number | undefined; ecranOuvert: (p: Page) => boolean; voir: (debut: number) => void;
}) {
  const testId = `aide-mise-en-route-${etape.id}`;
  return (
    <li id={`mise-en-route-${etape.id}`} data-testid={testId}>
      <strong>{etape.titre}</strong>{etape.facultative && " (facultatif)"}
      <p className="muted">{LIBELLE_ACTEUR[etape.acteur]}</p>
      <p>{etape.gestes.join(" ")}</p>
      <div className="mise-en-route-actions">
        {ecranOuvert(etape.ecran) && (
          <a href={cheminEcran(etape.ecran)} data-testid={`${testId}-lien`}>Ouvrir « {FICHES[etape.ecran].titre} »</a>
        )}
        {debut !== undefined && (
          <button type="button" className="btn secondary" data-testid={`${testId}-voir`} onClick={() => voir(debut)}>
            Voir dans la vidéo<span className="sr-only"> l'étape « {etape.titre} »</span>, à {duree(debut)}
          </button>
        )}
      </div>
    </li>
  );
}
