/**
 * **En-tête d'écran** — le titre (`h1` unique de la page), une phrase qui dit à quoi sert
 * l'écran, et **les actions de création** : une primaire, au plus deux secondaires. Tout autre
 * geste vit dans le menu `⋯` d'une ligne ou dans la fiche. Le titre reçoit `tabIndex={-1}` pour
 * qu'un écran puisse y poser le focus après une navigation (WCAG 2.4.3).
 */
import { ReactNode } from "react";
import Icone, { type NomIcone } from "./Icone";

interface Props {
  titre: ReactNode;
  /** Sous-titre en une phrase ; facultatif. */
  description?: ReactNode;
  /** Les boutons d'action, dans l'ordre : secondaires puis primaire. */
  actions?: ReactNode;
  icone?: NomIcone;
  /** `data-testid` porté par le `h1`, pour les essais qui visent le titre. */
  testId?: string;
  /** Niveau du titre : `h1` par défaut (une page) ; `h2` pour l'en-tête d'un onglet ou d'une section. */
  niveau?: 1 | 2;
  /** Ce qui se place sous le titre, pleine largeur : un sélecteur de période, un bandeau. */
  children?: ReactNode;
  className?: string;
}

export default function EnTeteEcran({ titre, description, actions, icone, testId, niveau = 1, children, className }: Props) {
  const Titre = niveau === 1 ? "h1" : "h2";
  return (
    <header className={["ecran-entete", className].filter(Boolean).join(" ")}>
      <div className="ecran-entete-ligne">
        <div className="ecran-entete-titres">
          <Titre className="ecran-entete-titre" data-testid={testId} tabIndex={-1}>
            {icone && <Icone nom={icone} taille={22} className="ecran-entete-icone" />}
            {titre}
          </Titre>
          {description && <p className="ecran-entete-description">{description}</p>}
        </div>
        {actions && <div className="ecran-entete-actions">{actions}</div>}
      </div>
      {children}
    </header>
  );
}
