/**
 * **Éditeur des plages d'un dépôt** (AO 26-015-DO, lot R4 — E-GN-12, E-GN-13).
 *
 * Une ligne par plage : le jour (pris dans la période, jamais tapé), l'heure de début, l'heure de
 * fin, et ce que la plage dit — disponible, de préférence, indisponible. Une fin avant le début
 * passe minuit ; 00:00 à 00:00 couvre la journée entière. Aucun glisser-déposer : chaque geste a
 * son bouton (2.5.7). Désactivé, l'éditeur se relit sans rien proposer de modifier.
 */
import { heure, minutes } from "../api";
import { TYPES_DISPONIBILITE, type Plage, type TypeDisponibilite } from "./types";

/** Une ligne en cours de saisie. Les champs sont contrôlés : l'index suffit comme clé. */
export interface LignePlage { date: string; debut: string; fin: string; type: TypeDisponibilite }

export const nouvelleLigne = (date: string, type: TypeDisponibilite = "DISPONIBLE"): LignePlage =>
  ({ date, debut: "06:00", fin: "18:00", type });

export const lignesDe = (plages: readonly Plage[]): LignePlage[] =>
  plages.map((p) => ({ date: p.date, debut: heure(p.debut % 1440), fin: heure(p.fin % 1440), type: p.type }));

export const plagesDe = (lignes: readonly LignePlage[]): Plage[] =>
  lignes.map((l) => ({ date: l.date, debut: minutes(l.debut), fin: minutes(l.fin, true), type: l.type }));

/** Les jours d'une période, bornes comprises. */
export function joursDe(debut: string, fin: string): string[] {
  const jours: string[] = [];
  for (let t = Date.parse(`${debut}T00:00:00Z`); t <= Date.parse(`${fin}T00:00:00Z`); t += 86_400_000) jours.push(new Date(t).toISOString().slice(0, 10));
  return jours;
}

const JOURS_SEMAINE = ["dim.", "lun.", "mar.", "mer.", "jeu.", "ven.", "sam."];
const libelleJour = (date: string) => `${JOURS_SEMAINE[new Date(`${date}T00:00:00Z`).getUTCDay()]} ${date}`;

interface Props {
  prefixe: string;
  jours: readonly string[];
  lignes: readonly LignePlage[];
  onChange: (lignes: LignePlage[]) => void;
  desactive?: boolean;
  /** Le libellé d'un jour ; par défaut « lun. 2030-03-04 ». La semaine type de l'AGPH ne montre que le jour. */
  libelleDuJour?: (date: string) => string;
}

export default function EditeurPlages({ prefixe, jours, lignes, onChange, desactive, libelleDuJour = libelleJour }: Props) {
  const modifier = (rang: number, champ: Partial<LignePlage>) => onChange(lignes.map((l, i) => (i === rang ? { ...l, ...champ } : l)));
  const retirer = (rang: number) => onChange(lignes.filter((_, i) => i !== rang));
  const ajouter = () => {
    const pris = new Set(lignes.map((l) => l.date));
    onChange([...lignes, nouvelleLigne(jours.find((j) => !pris.has(j)) ?? jours[0])]);
  };

  return (
    <div data-testid={`${prefixe}-editeur`}>
      {lignes.length > 0 && (
        // Une liste de groupes plutôt qu'un tableau : chaque plage se replie sur la largeur d'un
        // téléphone sans défilement horizontal (1.4.10), et chaque champ garde son libellé visible.
        <ol className="eff-plages" data-testid={`${prefixe}-plages`} aria-label="Plages de disponibilité du dépôt">
          {lignes.map((l, i) => {
            const id = `${prefixe}-plage-${i}`;
            return (
              <li key={i} data-testid={`${prefixe}-plage-${i}`}>
                <fieldset className="eff-groupe">
                  <legend>Plage {i + 1}</legend>
                  <div className="eff-barre">
                    <p>
                      <label htmlFor={`${id}-date`}>Jour</label>
                      <select id={`${id}-date`} data-testid={`${prefixe}-plage-${i}-date`} value={l.date} disabled={desactive} onChange={(e) => modifier(i, { date: e.target.value })}>
                        {jours.map((j) => <option key={j} value={j}>{libelleDuJour(j)}</option>)}
                      </select>
                    </p>
                    <p>
                      <label htmlFor={`${id}-debut`}>Début</label>
                      <input id={`${id}-debut`} type="time" data-testid={`${prefixe}-plage-${i}-debut`} value={l.debut} disabled={desactive} required
                        onChange={(e) => modifier(i, { debut: e.target.value })} />
                    </p>
                    <p>
                      <label htmlFor={`${id}-fin`}>Fin (avant le début = passe minuit)</label>
                      <input id={`${id}-fin`} type="time" data-testid={`${prefixe}-plage-${i}-fin`} value={l.fin} disabled={desactive} required
                        onChange={(e) => modifier(i, { fin: e.target.value })} />
                    </p>
                    <p>
                      <label htmlFor={`${id}-type`}>Disponibilité</label>
                      <select id={`${id}-type`} data-testid={`${prefixe}-plage-${i}-type`} value={l.type} disabled={desactive}
                        onChange={(e) => modifier(i, { type: e.target.value as TypeDisponibilite })}>
                        {TYPES_DISPONIBILITE.map((t) => <option key={t.code} value={t.code}>{t.libelle}</option>)}
                      </select>
                    </p>
                    {!desactive && (
                      <p className="eff-actions">
                        <button type="button" className="secondary" data-testid={`${prefixe}-plage-${i}-retirer`} onClick={() => retirer(i)}>
                          Retirer<span className="sr-only"> la plage {i + 1}</span>
                        </button>
                      </p>
                    )}
                  </div>
                </fieldset>
              </li>
            );
          })}
        </ol>
      )}
      {!desactive && (
        <p className="eff-actions">
          <button type="button" className="secondary" data-testid={`${prefixe}-ajouter`} disabled={jours.length === 0} onClick={ajouter}>Ajouter une plage</button>
        </p>
      )}
    </div>
  );
}
