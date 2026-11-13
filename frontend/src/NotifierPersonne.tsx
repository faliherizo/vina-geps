/**
 * **Notifier une personne** (ADR 0029) : l'encadrement choisit une ou plusieurs personnes par leur
 * **nom** (l'identifiant est transmis, jamais tapé), les canaux, un sujet, un message — et lit,
 * par personne et par canal, ce qui est parti et ce qui a été refusé : « désactivé » quand la
 * personne a fermé le canal dans Mon compte, « échoué » sans adresse ou numéro. Le serveur ne
 * laisse un gestionnaire aviser que son équipe.
 */
import { useState } from "react";
import { Message } from "./composants";
import ChampPersonne from "./ChampPersonne";
import { Canaux } from "./reglesTextos";

interface Props { call: (chemin: string, init?: RequestInit) => Promise<Response>; signaler: (erreur: string) => void; apresEnvoi: () => void }

export interface EnvoiAvis { employeeId: string; canal: keyof Canaux; statut: "sent" | "failed" | "desactive"; erreur: string | null }

const CANAUX: Array<{ cle: keyof Canaux; libelle: string }> = [{ cle: "courriel", libelle: "Courriel" }, { cle: "texto", libelle: "Texto" }, { cle: "poussee", libelle: "Poussée" }];
const LIBELLE_STATUT: Record<EnvoiAvis["statut"], string> = { sent: "envoyé", failed: "échoué", desactive: "désactivé (fermé par la personne)" };

/** Le compte rendu d'un envoi, une phrase par personne : « emp-1 — courriel envoyé, texto désactivé (fermé par la personne) ». */
export function resumerEnvois(envois: EnvoiAvis[], nomDe: (id: string) => string): string[] {
  const parPersonne = new Map<string, EnvoiAvis[]>();
  for (const e of envois) parPersonne.set(e.employeeId, [...(parPersonne.get(e.employeeId) ?? []), e]);
  return [...parPersonne.entries()].map(([id, liste]) => `${nomDe(id)} — ${liste.map((e) => `${CANAUX.find((c) => c.cle === e.canal)?.libelle.toLowerCase() ?? e.canal} ${LIBELLE_STATUT[e.statut]}${e.statut === "failed" && e.erreur ? ` (${e.erreur})` : ""}`).join(", ")}`);
}

export default function NotifierPersonne({ call, signaler, apresEnvoi }: Props) {
  const [personnes, setPersonnes] = useState("");
  const [canaux, setCanaux] = useState<Canaux>({ courriel: true, texto: true, poussee: true });
  const [sujet, setSujet] = useState("");
  const [corps, setCorps] = useState("");
  const [resultat, setResultat] = useState<string[]>([]);
  const [refus, setRefus] = useState("");
  const [envoi, setEnvoi] = useState(false);

  const ids = personnes.split(",").map((s) => s.trim()).filter(Boolean);
  const choisis = CANAUX.filter((c) => canaux[c.cle]).map((c) => c.cle);

  async function envoyer(ev: React.FormEvent) {
    ev.preventDefault();
    const manque = ids.length === 0 ? "Choisissez au moins une personne." : choisis.length === 0 ? "Choisissez au moins un canal." : !sujet.trim() ? "Le sujet est requis." : !corps.trim() ? "Le message est requis." : "";
    setRefus(manque);
    if (manque) return;
    setEnvoi(true);
    try {
      const r = await call("/api/notifications/envoyer", { method: "POST", body: JSON.stringify({ employeeIds: ids, sujet: sujet.trim(), corps: corps.trim(), canaux: choisis }) });
      const j = await r.json().catch(() => ({}));
      if (!r.ok) { signaler(j.errors?.join(", ") || `Erreur ${r.status}`); return; }
      const envois: EnvoiAvis[] = Array.isArray(j.envois) ? j.envois : [];
      // Le compte rendu nomme les personnes, pas leurs identifiants : le dossier de chacune est relu.
      const noms = new Map<string, string>();
      await Promise.all([...new Set(envois.map((e) => e.employeeId))].map(async (id) => {
        const e = await call(`/api/personnel/employes/${encodeURIComponent(id)}`).then((x) => (x.ok ? x.json() : null)).catch(() => null);
        if (e && typeof e.nom === "string") noms.set(id, e.nom);
      }));
      setResultat(resumerEnvois(envois, (id) => noms.get(id) ?? id));
      setPersonnes(""); setSujet(""); setCorps("");
      apresEnvoi();
    } finally { setEnvoi(false); }
  }

  return (
    <section className="card" aria-labelledby="notif-personne-title" data-testid="notif-personne">
      <h2 id="notif-personne-title">Notifier une personne</h2>
      <p className="muted">Le message part par les canaux que la personne a laissés ouverts (Mon compte › Mes notifications) ; un canal fermé apparaît « désactivé ». Variables : <code>{"{{prenom}}"}</code>, <code>{"{{nom}}"}</code>.</p>
      <form onSubmit={envoyer} data-testid="notif-personne-form">
        <div className="grid-form">
          <div style={{ gridColumn: "1 / -1" }}>
            <ChampPersonne libelle="Personne(s)" testId="notif-personne-cible" appel={call} valeur={personnes} onChoisir={setPersonnes} multiple requis placeholder="Trois lettres du nom…" />
          </div>
          <fieldset className="field" style={{ gridColumn: "1 / -1" }}>
            <legend>Canaux</legend>
            {CANAUX.map((c) => (
              <label key={c.cle} className="checkbox-line">
                <input type="checkbox" data-testid={`notif-personne-canal-${c.cle}`} checked={canaux[c.cle]} onChange={(e) => setCanaux({ ...canaux, [c.cle]: e.target.checked })} />
                <span>{c.libelle}</span>
              </label>
            ))}
          </fieldset>
          <label className="field"><span>Sujet</span><input data-testid="notif-personne-sujet" value={sujet} maxLength={200} onChange={(e) => setSujet(e.target.value)} required /></label>
          <label className="field" style={{ gridColumn: "1 / -1" }}><span>Message</span><textarea data-testid="notif-personne-corps" value={corps} maxLength={2000} onChange={(e) => setCorps(e.target.value)} required /></label>
        </div>
        {refus && <Message variante="erreur" testId="notif-personne-refus">{refus}</Message>}
        <div className="field field-actions">
          <button type="submit" className="primary" data-testid="notif-personne-envoyer" disabled={envoi}>{ids.length > 1 ? `Envoyer à ${ids.length} personnes` : "Envoyer"}</button>
        </div>
        {resultat.length > 0 && (
          <Message variante="succes" titre="Envoyé" testId="notif-personne-resultat">
            <ul style={{ margin: "4px 0 0", paddingLeft: 18 }}>{resultat.map((l) => <li key={l}>{l}</li>)}</ul>
          </Message>
        )}
      </form>
    </section>
  );
}
