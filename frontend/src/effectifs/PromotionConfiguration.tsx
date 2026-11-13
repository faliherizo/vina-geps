/**
 * **Promouvoir la configuration** — AO 26-015-DO P-TN-1 (configurer les modules dans les
 * environnements de formation, de tests et de production).
 *
 * Dans l'environnement où la configuration a été montée et éprouvée : « Exporter » donne le
 * paquet (référentiels en vigueur, règles et leur version courante). Dans l'environnement suivant :
 * choisir le fichier, « Simuler » montre ce qui serait créé, remplacé ou laissé, puis « Appliquer »
 * le rejoue — validé, versionné et journalisé comme une saisie.
 */
import { useState, type ChangeEvent } from "react";
import { Message, Tiroir } from "../composants";
import { objet } from "../formeRecue";
import { aujourdhui, useApiEffectifs } from "./api";

const CHEMIN = "/api/effectifs/configuration/paquet";

interface Ligne { objet: string; cle: string; action: "creer" | "remplacer" | "inchange" }
export interface Bilan { applique: boolean; priseDEffet: string; creer: number; remplacer: number; inchange: number; lignes: Ligne[] }

const LIBELLE_ACTION: Record<Ligne["action"], string> = { creer: "À créer", remplacer: "À remplacer", inchange: "Inchangé" };

/** Le lendemain d'une date AAAA-MM-JJ : la prise d'effet proposée par défaut, comme au serveur. */
export function lendemain(date: string): string {
  const d = new Date(`${date}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + 1);
  return d.toISOString().slice(0, 10);
}

export default function PromotionConfiguration({ onApplique }: { onApplique: (message: string) => void }) {
  const { lire, envoyer, erreur, setErreur } = useApiEffectifs();
  const [ouvert, setOuvert] = useState(false);
  const [paquet, setPaquet] = useState<unknown>(null);
  const [nomFichier, setNomFichier] = useState("");
  const [depuis, setDepuis] = useState(lendemain(aujourdhui()));
  const [bilan, setBilan] = useState<Bilan | null>(null);

  const exporter = async () => {
    const p = await lire<Record<string, unknown>>(CHEMIN);
    if (!p) return;
    const url = URL.createObjectURL(new Blob([JSON.stringify(p, null, 1)], { type: "application/json" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = `configuration-effectifs-${aujourdhui()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const choisir = async (e: ChangeEvent<HTMLInputElement>) => {
    setBilan(null); setErreur(""); setPaquet(null);
    const f = e.target.files?.[0];
    if (!f) return;
    setNomFichier(f.name);
    try { setPaquet(JSON.parse(await f.text())); } catch { setErreur(`« ${f.name} » n'est pas un fichier JSON lisible.`); }
  };

  const importer = async (appliquer: boolean) => {
    const b = objet<Bilan>(await envoyer(`${CHEMIN}?appliquer=${appliquer}&depuis=${encodeURIComponent(depuis)}`, paquet));
    if (!b) return;
    setBilan(b);
    if (b.applique) onApplique(`Configuration appliquée : ${b.creer} création(s), ${b.remplacer} remplacement(s) à partir du ${b.priseDEffet}.`);
  };

  return (
    <>
      <button type="button" className="secondary" data-testid="ref-promotion" onClick={() => { setOuvert(true); setBilan(null); }}>
        Promouvoir la configuration
      </button>
      <Tiroir ouvert={ouvert} onFermer={() => setOuvert(false)} titre="Promouvoir la configuration d'un environnement à l'autre" testId="ref-promotion-tiroir">
        <h3>Exporter depuis cet environnement</h3>
        <p>Les référentiels en vigueur aujourd'hui et les règles avec leur version courante, en un fichier.</p>
        <button type="button" data-testid="ref-promotion-exporter" onClick={() => void exporter()}>Exporter le paquet</button>

        <h3>Importer dans cet environnement</h3>
        <p>
          <label htmlFor="ref-promotion-fichier">Paquet de configuration (JSON)</label>
          <input id="ref-promotion-fichier" type="file" accept="application/json,.json" data-testid="ref-promotion-fichier" onChange={(e) => void choisir(e)} />
        </p>
        <p>
          <label htmlFor="ref-promotion-depuis">Les remplacements prennent effet le</label>
          <input id="ref-promotion-depuis" type="date" min={aujourdhui()} value={depuis} data-testid="ref-promotion-depuis" onChange={(e) => setDepuis(e.target.value)} />
        </p>
        {erreur && <Message variante="erreur" testId="ref-promotion-erreur">{erreur}</Message>}
        <button type="button" data-testid="ref-promotion-simuler" disabled={!paquet} onClick={() => void importer(false)}>
          Simuler{nomFichier ? <span className="sr-only"> {nomFichier}</span> : null}
        </button>

        {bilan && (
          <div data-testid="ref-promotion-bilan" role="status">
            <p>
              {bilan.applique ? "Appliqué" : "Simulation"} : {bilan.creer} à créer, {bilan.remplacer} à remplacer (à partir du {bilan.priseDEffet}),{" "}
              {bilan.inchange} inchangé(s).
            </p>
            {bilan.lignes.length > 0 && (
              <table data-testid="ref-promotion-lignes">
                <caption className="sr-only">Détail du paquet</caption>
                <thead><tr><th scope="col">Objet</th><th scope="col">Clé</th><th scope="col">Action</th></tr></thead>
                <tbody>
                  {bilan.lignes.map((l) => (
                    <tr key={`${l.objet}|${l.cle}`}><td>{l.objet}</td><th scope="row">{l.cle}</th><td>{LIBELLE_ACTION[l.action]}</td></tr>
                  ))}
                </tbody>
              </table>
            )}
            {!bilan.applique && bilan.creer + bilan.remplacer > 0 && (
              <button type="button" data-testid="ref-promotion-appliquer" onClick={() => void importer(true)}>Appliquer</button>
            )}
          </div>
        )}
      </Tiroir>
    </>
  );
}
