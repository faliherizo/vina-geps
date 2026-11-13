/**
 * **Journal des effectifs** (AO 26-015-DO, écran E17 — E-GN-9, E-GN-10).
 *
 * Toute action et toute décision automatique, filtrables par acteur, origine, action, objet et
 * période, exportables en CSV, Excel, PDF ou JSON (P-TN-8). Une décision du moteur porte l'origine « Système » et le nom du
 * mécanisme : elle n'est jamais attribuée à l'utilisateur qui l'a déclenchée sans le savoir.
 */
import { useCallback, useEffect, useState } from "react";
import { EtatVide, Message, Tableau, type Colonne } from "../composants";
import { useApiEffectifs } from "./api";
import ExportResultats from "./ExportResultats";

interface Entree {
  id: string; horodatage: string; acteurId: string; origine: string; action: string;
  objetType: string; objetId: string; uniteCode: string | null; avant: unknown; apres: unknown;
}

const FILTRES = ["acteurId", "action", "objetType", "objetId"] as const;
const LIBELLES: Record<(typeof FILTRES)[number], string> = { acteurId: "Acteur", action: "Action", objetType: "Type d'objet", objetId: "Objet" };

export default function Journal() {
  const { lire, erreur } = useApiEffectifs();
  const [filtres, setFiltres] = useState<Record<string, string>>({});
  const [entrees, setEntrees] = useState<Entree[]>([]);
  const [ouverte, setOuverte] = useState<string | null>(null);

  const requete = useCallback(() => {
    const p = new URLSearchParams();
    for (const [k, v] of Object.entries(filtres)) if (v.trim()) p.set(k, v.trim());
    return p;
  }, [filtres]);

  const charger = useCallback(async () => {
    const e = await lire<Entree[]>(`/api/effectifs/journal?${requete()}`);
    if (e) setEntrees(e);
  }, [lire, requete]);

  useEffect(() => { void charger(); }, [charger]);

  const colonnes: ReadonlyArray<Colonne<Entree>> = [
    { id: "quand", libelle: "Horodatage", tri: (e) => e.horodatage },
    { id: "origine", libelle: "Origine", tri: (e) => e.origine },
    { id: "acteur", libelle: "Acteur", tri: (e) => e.acteurId },
    { id: "action", libelle: "Action", tri: (e) => e.action },
    { id: "objet", libelle: "Objet" },
    { id: "unite", libelle: "Unité" },
    { id: "detail", libelle: "Détail", actions: true },
  ];

  return (
    <div data-testid="jrn-ecran">
      {/* Un **filtre** du journal, et non une écriture : il reste en ligne, au-dessus de ce qu'il filtre.
          Le journal lui-même ne se modifie par aucune route — c'est la condition de sa preuve. */}
      <form className="eff-barre" data-formulaire="requete" aria-label="Filtrer le journal" onSubmit={(e) => { e.preventDefault(); void charger(); }}>
        {FILTRES.map((f) => (
          <p key={f}>
            <label htmlFor={`jrn-${f}`}>{LIBELLES[f]}</label>
            <input id={`jrn-${f}`} data-testid={`jrn-filtre-${f}`} value={filtres[f] ?? ""} onChange={(e) => setFiltres((x) => ({ ...x, [f]: e.target.value }))} />
          </p>
        ))}
        <p>
          <label htmlFor="jrn-origine">Origine</label>
          <select id="jrn-origine" data-testid="jrn-filtre-origine" value={filtres.origine ?? ""} onChange={(e) => setFiltres((x) => ({ ...x, origine: e.target.value }))}>
            <option value="">Toutes</option>
            <option value="UTILISATEUR">Utilisateur</option>
            <option value="SYSTEME">Système (décision automatique)</option>
          </select>
        </p>
        <p>
          <label htmlFor="jrn-du">Du</label>
          <input id="jrn-du" type="date" data-testid="jrn-filtre-du" value={filtres.du ?? ""} onChange={(e) => setFiltres((x) => ({ ...x, du: e.target.value }))} />
        </p>
        <p>
          <label htmlFor="jrn-au">Au (exclu)</label>
          <input id="jrn-au" type="date" data-testid="jrn-filtre-au" value={filtres.au ?? ""} onChange={(e) => setFiltres((x) => ({ ...x, au: e.target.value }))} />
        </p>
        <span className="eff-actions">
          <button type="submit" data-testid="jrn-filtrer">Filtrer</button>
        </span>
        <ExportResultats chemin="/api/effectifs/journal" parametres={requete()} nomFichier="journal-effectifs" prefixe="jrn" />
      </form>
      {erreur && <Message variante="erreur" testId="jrn-erreur">{erreur}</Message>}
      <Tableau
        colonnes={colonnes} lignes={entrees} legende="Journal des actions et des décisions" testId="jrn-tableau"
        vide={<EtatVide titre="Aucune entrée" compact testId="jrn-vide">Aucune action ne correspond à ces filtres.</EtatVide>}
        rendre={(e) => (
          <tr key={e.id} data-testid={`jrn-ligne-${e.id}`}>
            <td>{new Date(e.horodatage).toLocaleString("fr-CA")}</td>
            <td>{e.origine === "SYSTEME" ? "Système" : "Utilisateur"}</td>
            <td>{e.acteurId}</td>
            <td>{e.action}</td>
            <td>{e.objetType} · {e.objetId}</td>
            <td>{e.uniteCode ?? "—"}</td>
            <td className="col-menu">
              <button type="button" className="secondary" aria-expanded={ouverte === e.id} data-testid={`jrn-detail-${e.id}`} onClick={() => setOuverte(ouverte === e.id ? null : e.id)}>
                {ouverte === e.id ? "Masquer" : "Voir"}<span className="sr-only"> le détail</span>
              </button>
              {ouverte === e.id && (
                <pre className="muted" data-testid={`jrn-avant-apres-${e.id}`}>{JSON.stringify({ avant: e.avant, apres: e.apres }, null, 2)}</pre>
              )}
            </td>
          </tr>
        )}
      />
    </div>
  );
}
