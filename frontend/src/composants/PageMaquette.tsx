/**
 * **L'enveloppe visuelle des écrans de santé** (AO 26-015-DO, ADR 0069).
 *
 * Les douze planches de `docs/urgences-sante/maquettes/svg/` dessinent un chrome constant :
 * bandeau marine en tête, fond de page gris-bleu, cartes blanches bordées, ruban de repères,
 * bandeau de quatre chiffres, grande carte de données, bouton sarcelle. Posé **ici**, ce chrome
 * vaut pour tous les écrans des deux modules : un écran n'a rien à faire pour ressembler aux maquettes,
 * et ne peut pas s'en écarter par omission — c'est la leçon du module Santé Québec, où le chrome
 * posé écran par écran avait fini par diverger d'un écran à l'autre.
 *
 * Le bandeau **n'est pas la barre d'application de la maquette** : celle-ci est partagée avec
 * tous les modules et réglée sur d'autres planches. Deux chromes empilés, ou un chrome qui change
 * selon l'écran ouvert, se lisent comme une panne. Le marine revient donc en bandeau de page.
 */
import { ReactNode } from "react";
import "./maquette-sante.css";

interface Props {
  /** Le nom du module, en tête du bandeau : « Urgences-santé », « Santé Québec ». */
  module: string;
  /**
   * Ce qui situe l'écran, à droite du nom du module — « Vue opérationnelle • Aujourd'hui » dans
   * la planche s01. Absent, le bandeau ne porte que le nom : une phrase inventée pour remplir la
   * place ne renseigne personne.
   */
  contexte?: ReactNode;
  children: ReactNode;
}

/**
 * **Le bandeau seul** — pour un écran qui a déjà son enveloppe.
 *
 * Le module Santé Québec a la sienne (`.sq-page` : largeur de lecture bornée, gouttière), et la
 * remplacer casserait sa mise en page pour n'y gagner qu'un `<section>`. Il prend donc le bandeau
 * et les jetons, pas l'enveloppe.
 *
 * Le bandeau est du texte, pas une image : il se lit au lecteur d'écran dans l'ordre où il
 * s'affiche, avant le titre de la page. Il ne porte aucun titre de section — un `h2` ici
 * s'intercalerait avant le `h1` de l'écran et casserait la hiérarchie des titres.
 */
export function BandeauMaquette({ module, contexte }: { module: string; contexte?: ReactNode }) {
  return (
    <div className="maquette-bandeau" data-testid="maquette-bandeau">
      <p className="maquette-bandeau-nom">{module}</p>
      {contexte && <p className="maquette-bandeau-contexte" data-testid="maquette-bandeau-contexte">{contexte}</p>}
    </div>
  );
}

export default function PageMaquette({ module, contexte, children }: Props) {
  return (
    <section className="maquette-page">
      <BandeauMaquette module={module} contexte={contexte} />
      {children}
    </section>
  );
}
