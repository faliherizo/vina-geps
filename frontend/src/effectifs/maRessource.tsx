/**
 * **Mon dossier d'effectif, ou son absence** — ce que chaque écran personnel doit savoir avant
 * de proposer un geste.
 *
 * Un compte sans dossier d'effectif n'est pas planifiable : il n'a ni horaire, ni feuille de
 * temps, ni campagne AGPH. Faute de le savoir, « Ma feuille de temps » affichait « Aucune
 * feuille de temps · Préparez la feuille d'une période » et offrait le bouton — qui échouait sur
 * la question d'un planificateur, « pour qui préparer les feuilles ? ». Un état vide qui invite
 * au geste impossible coûte plus qu'un écran muet : il fait croire à une panne.
 *
 * Trois écrans savaient déjà répondre, parce que leur lecture rend la ressource à côté des
 * données (`/mon-horaire`, `/disponibilites/moi`, `/echanges/moi`). Les autres lisent une liste,
 * et **une liste vide ne distingue pas** « rien à montrer » de « vous n'êtes pas planifiable ».
 * Ce crochet pose la question au seul endroit qui y répond sans rien supposer du module ouvert.
 */
import { useEffect, useState } from "react";
import { EtatVide } from "../composants";
import { objet } from "../formeRecue";
import { useApiEffectifs } from "./api";
import type { Page } from "../App";
import LienPrealables from "../aide/LienPrealables";
import type { Ressource } from "./planification/types";

/** `undefined` tant que la réponse n'est pas là ; `null` quand le compte n'a pas de dossier. */
export function useMaRessource(): Ressource | null | undefined {
  const { lire } = useApiEffectifs();
  const [moi, setMoi] = useState<Ressource | null | undefined>(undefined);
  useEffect(() => {
    void lire<{ ressource: Ressource | null }>("/api/effectifs/ma-ressource")
      // Une réponse d'une autre forme — un refus traduit en tableau, un corps vide — ne doit pas
      // laisser l'écran croire qu'il a un dossier : sans objet lisible, il n'y en a pas.
      .then((r) => setMoi(objet<{ ressource: Ressource | null }>(r)?.ressource ?? null));
  }, [lire]);
  return moi;
}

/**
 * L'état vide commun aux écrans personnels : ce qui manque, et à qui le demander.
 *
 * Le même texte que « Mon horaire », « Mes disponibilités » et « Mes échanges » posaient déjà
 * chacun de leur côté : la phrase est la même parce que la situation est la même, et la relire
 * ailleurs sous d'autres mots ferait douter qu'il s'agisse du même manque.
 */
export function SansDossierEffectif({ testId, page }: { testId: string; page: Page }) {
  return (
    <EtatVide titre="Votre compte n'est relié à aucune ressource planifiable" testId={testId}
      action={<LienPrealables page={page} testId={`${testId}-prealables`} />}>
      Demandez à votre planificateur de relier votre compte à votre dossier d'effectif.
    </EtatVide>
  );
}
