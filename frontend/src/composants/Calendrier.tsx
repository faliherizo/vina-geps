/**
 * **Calendrier mensuel** (gabarit G4) — une seule grille pour le calendrier personnel, le
 * calendrier pédagogique et, demain, les autres vues par jour. La barre de navigation
 * (◀ mois, mois ▶, aujourd'hui), l'en-tête des jours (lundi → dimanche) et la grille
 * `role="grid"` sont ici ; **le contenu d'une case** reste à l'appelant (`rendreJour`), qui
 * garde ses `data-testid` (`cal-day-<date>`, `cal-add-<date>`, `cal-event-<id>`…).
 *
 * Deux natures de case : un conteneur (`gridcell` passif, l'action est un bouton dedans) ou,
 * avec `onChoisirJour`, une case qui est elle-même un bouton. Les jours hors du mois sont
 * rendus estompés quand `semainesCompletes` (six semaines) est demandé, vides sinon.
 */
import { ReactNode, useMemo } from "react";
import Icone from "./Icone";

export const JOURS_SEMAINE = ["Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"];

export const ymd = (d: Date): string => {
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
};

export interface JourCalendrier { date: string; dansLeMois: boolean; aujourdhui: boolean }

/** Les cases du mois, lundi en tête ; `completes` étend à six semaines avec les jours voisins. */
export function casesDuMois(mois: Date, completes: boolean, aujourdhui = ymd(new Date())): Array<JourCalendrier | null> {
  const annee = mois.getFullYear(), m = mois.getMonth();
  const premier = new Date(annee, m, 1);
  const decalage = (premier.getDay() + 6) % 7;   // getDay(): 0 = dimanche → lundi = 0
  const jours = new Date(annee, m + 1, 0).getDate();
  const cases: Array<JourCalendrier | null> = [];
  if (completes) {
    const depart = new Date(annee, m, 1 - decalage);
    for (let i = 0; i < 42; i++) {
      const d = new Date(depart.getFullYear(), depart.getMonth(), depart.getDate() + i);
      const date = ymd(d);
      cases.push({ date, dansLeMois: d.getMonth() === m, aujourdhui: date === aujourdhui });
    }
    return cases;
  }
  for (let i = 0; i < decalage; i++) cases.push(null);
  for (let j = 1; j <= jours; j++) { const date = ymd(new Date(annee, m, j)); cases.push({ date, dansLeMois: true, aujourdhui: date === aujourdhui }); }
  while (cases.length % 7 !== 0) cases.push(null);
  return cases;
}

interface Props {
  mois: Date;
  onMois: (mois: Date) => void;
  /** Ce que la grille représente, pour le lecteur d'écran (« Calendrier personnel »). */
  libelle: string;
  rendreJour: (jour: JourCalendrier) => ReactNode;
  /** La case entière devient un bouton qui choisit le jour. */
  onChoisirJour?: (date: string) => void;
  /** Nom accessible d'une case cliquable (« 2026-10-19, 2 événements »). */
  libelleJour?: (jour: JourCalendrier) => string;
  /** La case choisie, marquée visuellement et par `aria-selected`. */
  jourChoisi?: string | null;
  semainesCompletes?: boolean;
  /** Ce qui se place dans la barre après « Aujourd'hui » : filtres, légende, exports. */
  outils?: ReactNode;
  /** Préfixe des `data-testid` (`cal` → `cal-prev`, `cal-next`, `cal-today`, `cal-month-label`, `cal-grid`, `cal-day-<date>`). */
  testId?: string;
  aujourdhui?: string;
}

export default function Calendrier({ mois, onMois, libelle, rendreJour, onChoisirJour, libelleJour, jourChoisi, semainesCompletes, outils, testId = "cal", aujourdhui }: Props) {
  const cases = useMemo(() => casesDuMois(mois, !!semainesCompletes, aujourdhui), [mois, semainesCompletes, aujourdhui]);
  const libelleMois = mois.toLocaleDateString("fr-CA", { year: "numeric", month: "long" });
  const versMois = (delta: number) => onMois(new Date(mois.getFullYear(), mois.getMonth() + delta, 1));
  return (
    <div className="calendrier">
      <div className="cal-toolbar" data-testid={`${testId}-toolbar`}>
        <button type="button" data-testid={`${testId}-prev`} className="secondary" onClick={() => versMois(-1)} aria-label="Mois précédent"><Icone nom="chevron-gauche" /> Mois précédent</button>
        <strong data-testid={`${testId}-month-label`} className="cal-month-label" aria-live="polite">{libelleMois}</strong>
        <button type="button" data-testid={`${testId}-next`} className="secondary" onClick={() => versMois(1)} aria-label="Mois suivant">Mois suivant <Icone nom="chevron-droite" /></button>
        <button type="button" data-testid={`${testId}-today`} className="secondary" onClick={() => { const d = new Date(); onMois(new Date(d.getFullYear(), d.getMonth(), 1)); }}>Aujourd'hui</button>
        {outils}
      </div>
      <div className="cal-grid" data-testid={`${testId}-grid`} role="grid" aria-label={`${libelle} — ${libelleMois}`} tabIndex={0}>
        {JOURS_SEMAINE.map((j) => <div key={j} className="cal-dow" role="columnheader">{j}</div>)}
        {cases.map((jour, i) => {
          if (!jour) return <div key={`vide-${i}`} className="cal-cell-empty" aria-hidden="true" />;
          const classes = ["cal-cell", jour.dansLeMois ? "" : "cal-cell-out", jour.aujourdhui ? "cal-cell-today" : "", jourChoisi === jour.date ? "cal-cell-selected" : ""].filter(Boolean).join(" ");
          if (onChoisirJour) {
            return (
              <button key={jour.date} type="button" role="gridcell" data-testid={`${testId}-day-${jour.date}`} className={classes}
                      aria-selected={jourChoisi === jour.date || undefined} aria-label={libelleJour ? libelleJour(jour) : jour.date}
                      onClick={() => onChoisirJour(jour.date)}>
                {rendreJour(jour)}
              </button>
            );
          }
          return (
            <div key={jour.date} role="gridcell" data-testid={`${testId}-day-${jour.date}`} className={classes} aria-label={libelleJour ? libelleJour(jour) : undefined}>
              {rendreJour(jour)}
            </div>
          );
        })}
      </div>
    </div>
  );
}
