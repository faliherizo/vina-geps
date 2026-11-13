/**
 * **Proposer un échange pour une personne** — le planificateur agit pour une personne de son
 * périmètre (AO 26-015-DO, E-GR-7 : « la demandeuse ou son planificateur » ; lot R12). Mêmes
 * contrôles que l'échange proposé par la personne : l'échange est jugé dans les deux sens, et la
 * destinataire l'accepte ensuite elle-même.
 */
import { useEffect, useState, type FormEvent } from "react";
import { Message } from "../../composants";
import { liste, objet } from "../../formeRecue";
import { heure, useApiEffectifs } from "../api";
import type { Echange, QuartEchangeable } from "./types";

interface Ressource { id: string; matricule: string; nom: string; actif: boolean }
const libelle = (q: QuartEchangeable) => `${q.date} · ${heure(q.debut)}–${heure(q.fin)}`;

export default function ProposerPourAutrui({ onPropose }: { onPropose: (e: Echange) => void }) {
  const { lire, envoyer, erreur } = useApiEffectifs();
  const [ressources, setRessources] = useState<Ressource[]>([]);
  const [ressourceId, setRessourceId] = useState("");
  const [siens, setSiens] = useState<QuartEchangeable[]>([]);
  const [cede, setCede] = useState("");
  const [demandables, setDemandables] = useState<QuartEchangeable[]>([]);
  const [voulu, setVoulu] = useState("");

  useEffect(() => { void lire("/api/effectifs/planification/ressources").then((r) => setRessources(liste<Ressource>(r).filter((x) => x.actif))); }, [lire]);
  useEffect(() => {
    setSiens([]); setCede("");
    if (ressourceId) void lire(`/api/effectifs/echanges/ressources/${ressourceId}/quarts`).then((q) => setSiens(liste<QuartEchangeable>(q)));
  }, [lire, ressourceId]);
  useEffect(() => {
    setDemandables([]); setVoulu("");
    if (cede) void lire(`/api/effectifs/echanges/places/${cede}/demandables`).then((q) => setDemandables(liste<QuartEchangeable>(q)));
  }, [lire, cede]);

  const proposer = async (e: FormEvent) => {
    e.preventDefault();
    const x = objet<Echange>(await envoyer("/api/effectifs/echanges", { placeCedeeId: cede, placeVoulueId: voulu }));
    if (x) { setRessourceId(""); onPropose(x); }
  };

  return (
    <form onSubmit={(e) => void proposer(e)} data-testid="pla-ech-pour">
      <div className="eff-barre">
        <p>
          <label htmlFor="pla-ech-pour-ressource">Personne</label>
          <select id="pla-ech-pour-ressource" data-testid="pla-ech-pour-ressource" value={ressourceId} onChange={(e) => setRessourceId(e.target.value)}>
            <option value="">— choisir —</option>
            {ressources.map((r) => <option key={r.id} value={r.id}>{r.nom} ({r.matricule})</option>)}
          </select>
        </p>
        <p>
          <label htmlFor="pla-ech-pour-cede">Quart qu'elle cède</label>
          <select id="pla-ech-pour-cede" data-testid="pla-ech-pour-cede" value={cede} disabled={!siens.length} onChange={(e) => setCede(e.target.value)}>
            <option value="">{ressourceId && !siens.length ? "aucun quart publié à venir" : "— choisir —"}</option>
            {siens.map((q) => <option key={q.placeId} value={q.placeId}>{libelle(q)}</option>)}
          </select>
        </p>
        <p>
          <label htmlFor="pla-ech-pour-voulu">Quart demandé en retour</label>
          <select id="pla-ech-pour-voulu" data-testid="pla-ech-pour-voulu" value={voulu} disabled={!demandables.length} onChange={(e) => setVoulu(e.target.value)}>
            <option value="">{cede && !demandables.length ? "aucun quart demandable" : "— choisir —"}</option>
            {demandables.map((q) => <option key={q.placeId} value={q.placeId}>{q.nom} — {libelle(q)}</option>)}
          </select>
        </p>
        <p><button type="submit" data-testid="pla-ech-pour-proposer" disabled={!cede || !voulu}>Proposer l'échange</button></p>
      </div>
      {erreur && <Message variante="erreur" testId="pla-ech-pour-erreur">{erreur}</Message>}
    </form>
  );
}
