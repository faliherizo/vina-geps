/**
 * **Flux d'approbation des heures** — paramétrage par l'organisme (AO 26-015-DO, E-GP-6).
 *
 * Chaque palier a un approbateur (niveau de périmètre de l'unité, rôle ou personne désignée) et
 * une condition de routage : « toujours », ou « heures supplémentaires ≥ 4 », « anomalies ≥ 1 »…
 * Un palier dont la condition est fausse n'existe pas pour ce document. Les feuilles de temps et
 * les pointages en attente ont chacun leur flux ; sans règle, le flux par défaut s'applique (les
 * paliers du paramètre « Paliers d'approbation », au niveau GESTION de l'unité).
 *
 * Les règles vivent dans le moteur d'approbation générique de l'application ; cet écran ne fait
 * que les composer dans les mots des effectifs.
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { useAuth, useCapability } from "../../AuthContext";
import { liste, objet } from "../../formeRecue";
import { Message } from "../../composants";
import { useApiEffectifs } from "../api";

type ObjetFlux = "FEUILLE" | "POINTAGE";
type TypeApprobateur = "PERIMETRE" | "ROLE" | "PERSONNE";
interface Champ { code: string; libelle: string; type: "nombre" | "texte" }
interface RegleFlux {
  id: string; objet: ObjetFlux; palier: number; champ: string; operateur: string; valeur: number | string;
  approbateur: { type: TypeApprobateur; valeur: string };
}
interface Lecture { regles: RegleFlux[]; champs: Record<ObjetFlux, Champ[]>; parDefaut: Record<ObjetFlux, boolean> }
interface Compte { id: string; username: string; loginName?: string | null; role: string }

const OBJETS: Record<ObjetFlux, string> = { FEUILLE: "Feuilles de temps", POINTAGE: "Pointages en attente" };
const OPERATEURS: Record<string, string> = { gte: "≥", lte: "≤", eq: "=", neq: "≠" };
const TYPES: Record<TypeApprobateur, string> = { PERIMETRE: "Niveau de périmètre de l'unité", ROLE: "Rôle", PERSONNE: "Personne désignée" };
const NIVEAUX = ["GESTION", "PLANIFICATION", "LECTURE"] as const;
const VIDE = { objet: "FEUILLE" as ObjetFlux, palier: "1", champ: "toujours", operateur: "eq", valeur: "1", type: "PERIMETRE" as TypeApprobateur, approbateur: "GESTION" };

export default function FluxApprobation() {
  const { lire, envoyer, supprimer, erreur } = useApiEffectifs();
  const { apiFetch } = useAuth();
  const peutConfigurer = useCapability("effectifs.configure");
  const [flux, setFlux] = useState<Lecture | null>(null);
  const [comptes, setComptes] = useState<Compte[]>([]);
  const [saisie, setSaisie] = useState(VIDE);
  const [info, setInfo] = useState("");

  const charger = useCallback(async () => { setFlux(objet<Lecture>(await lire<Lecture>("/api/effectifs/temps/flux"))); }, [lire]);
  useEffect(() => { void charger(); }, [charger]);
  useEffect(() => {
    if (!peutConfigurer) return;
    void apiFetch("/api/auth/users").then(async (r) => (r.ok ? setComptes(liste<Compte>(await r.json())) : undefined));
  }, [apiFetch, peutConfigurer]);

  const nom = (id: string) => { const c = comptes.find((x) => x.id === id); return c ? (c.loginName ?? c.username) : id; };
  const approbateurEnMots = (r: RegleFlux) => r.approbateur.type === "PERIMETRE" ? `niveau ${r.approbateur.valeur} de l'unité`
    : r.approbateur.type === "ROLE" ? `rôle « ${r.approbateur.valeur} »` : `${nom(r.approbateur.valeur)} (personne désignée)`;
  const champDe = (o: ObjetFlux, code: string) => flux?.champs[o]?.find((c) => c.code === code);
  const conditionEnMots = (r: RegleFlux) => r.champ === "toujours" ? "Toujours" : `${champDe(r.objet, r.champ)?.libelle ?? r.champ} ${OPERATEURS[r.operateur] ?? r.operateur} ${r.valeur}`;

  const ajouter = async (e: FormEvent) => {
    e.preventDefault();
    setInfo("");
    const champ = champDe(saisie.objet, saisie.champ);
    const r = await envoyer<RegleFlux>("/api/effectifs/temps/flux", {
      objet: saisie.objet, palier: Number(saisie.palier), champ: saisie.champ, operateur: saisie.operateur,
      valeur: champ?.type === "texte" ? saisie.valeur : Number(saisie.valeur), approbateur: { type: saisie.type, valeur: saisie.approbateur },
    });
    if (!r) return;
    setInfo(`Palier ${r.palier} ajouté au flux des ${OBJETS[r.objet].toLowerCase()} : il vaut pour les soumissions à partir de maintenant.`);
    setSaisie(VIDE);
    await charger();
  };

  const retirer = async (r: RegleFlux) => {
    setInfo("");
    if (await supprimer(`/api/effectifs/temps/flux/${r.id}`)) { setInfo(`Palier ${r.palier} retiré du flux des ${OBJETS[r.objet].toLowerCase()}.`); await charger(); }
  };

  const changer = (cle: keyof typeof VIDE, v: string) => setSaisie((s) => {
    const n = { ...s, [cle]: v };
    if (cle === "type") n.approbateur = v === "PERIMETRE" ? "GESTION" : "";
    if (cle === "objet") { n.champ = "toujours"; n.operateur = "eq"; n.valeur = "1"; }
    return n;
  });

  if (!flux) return erreur ? <Message variante="erreur" testId="pla-tps-flux-erreur">{erreur}</Message> : null;
  const champs = flux.champs[saisie.objet] ?? [];
  return (
    <section data-testid="pla-tps-flux" aria-labelledby="pla-tps-flux-titre">
      <h3 id="pla-tps-flux-titre">Flux d'approbation</h3>
      <p>Chaque palier se décide dans l'ordre, par une personne différente. Un palier dont la condition est fausse ne s'applique pas au document.</p>
      {(Object.keys(OBJETS) as ObjetFlux[]).map((o) => {
        const regles = flux.regles.filter((r) => r.objet === o).sort((a, b) => a.palier - b.palier);
        return (
          <table key={o} className="eff-table-compacte" data-testid={`pla-tps-flux-${o}`}>
            <caption>{OBJETS[o]}{flux.parDefaut[o] ? " — flux par défaut : niveau GESTION de l'unité" : ""}</caption>
            <thead><tr><th scope="col">Palier</th><th scope="col">Condition</th><th scope="col">Approbateur</th>{peutConfigurer && <th scope="col">Actions</th>}</tr></thead>
            <tbody>
              {regles.map((r) => (
                <tr key={r.id} data-testid={`pla-tps-flux-regle-${r.id}`}>
                  <th scope="row">{r.palier}</th><td>{conditionEnMots(r)}</td><td>{approbateurEnMots(r)}</td>
                  {peutConfigurer && (
                    <td>
                      <button type="button" className="secondary" data-testid={`pla-tps-flux-retirer-${r.id}`} onClick={() => void retirer(r)}>
                        Retirer<span className="sr-only"> le palier {r.palier} ({conditionEnMots(r)})</span>
                      </button>
                    </td>
                  )}
                </tr>
              ))}
              {regles.length === 0 && <tr><td colSpan={peutConfigurer ? 4 : 3}>Aucune règle configurée.</td></tr>}
            </tbody>
          </table>
        );
      })}
      {peutConfigurer && (
        <form onSubmit={(e) => void ajouter(e)} aria-label="Ajouter un palier au flux">
          <div className="eff-barre">
            <p>
              <label htmlFor="pla-tps-flux-objet">Flux</label>
              <select id="pla-tps-flux-objet" data-testid="pla-tps-flux-objet" value={saisie.objet} onChange={(e) => changer("objet", e.target.value)}>
                {(Object.keys(OBJETS) as ObjetFlux[]).map((o) => <option key={o} value={o}>{OBJETS[o]}</option>)}
              </select>
            </p>
            <p><label htmlFor="pla-tps-flux-palier">Palier</label>
              <input id="pla-tps-flux-palier" type="number" min={1} max={10} step={1} required data-testid="pla-tps-flux-palier" value={saisie.palier} onChange={(e) => changer("palier", e.target.value)} /></p>
            <p>
              <label htmlFor="pla-tps-flux-champ">Condition : champ</label>
              <select id="pla-tps-flux-champ" data-testid="pla-tps-flux-champ" value={saisie.champ} onChange={(e) => changer("champ", e.target.value)}>
                {champs.map((c) => <option key={c.code} value={c.code}>{c.libelle}</option>)}
              </select>
            </p>
            <p>
              <label htmlFor="pla-tps-flux-operateur">Comparaison</label>
              <select id="pla-tps-flux-operateur" data-testid="pla-tps-flux-operateur" value={saisie.operateur} onChange={(e) => changer("operateur", e.target.value)}>
                {Object.entries(OPERATEURS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
            </p>
            <p><label htmlFor="pla-tps-flux-valeur">Valeur</label>
              <input id="pla-tps-flux-valeur" required data-testid="pla-tps-flux-valeur" value={saisie.valeur} onChange={(e) => changer("valeur", e.target.value)} /></p>
            <p>
              <label htmlFor="pla-tps-flux-type">Approbateur</label>
              <select id="pla-tps-flux-type" data-testid="pla-tps-flux-type" value={saisie.type} onChange={(e) => changer("type", e.target.value)}>
                {(Object.keys(TYPES) as TypeApprobateur[]).map((t) => <option key={t} value={t}>{TYPES[t]}</option>)}
              </select>
            </p>
            <p>
              {saisie.type === "ROLE"
                ? (
                  <>
                    <label htmlFor="pla-tps-flux-role">Code du rôle</label>
                    <input id="pla-tps-flux-role" data-testid="pla-tps-flux-approbateur" required value={saisie.approbateur} onChange={(e) => changer("approbateur", e.target.value)} />
                  </>
                )
                : (
                  <>
                    <label htmlFor="pla-tps-flux-approbateur">{saisie.type === "PERIMETRE" ? "Niveau" : "Personne"}</label>
                    <select id="pla-tps-flux-approbateur" data-testid="pla-tps-flux-approbateur" required value={saisie.approbateur} onChange={(e) => changer("approbateur", e.target.value)}>
                      {saisie.type === "PERIMETRE"
                        ? NIVEAUX.map((n) => <option key={n} value={n}>{n}</option>)
                        : [<option key="" value="">— choisir —</option>, ...comptes.map((c) => <option key={c.id} value={c.id}>{c.loginName ?? c.username} ({c.role})</option>)]}
                    </select>
                  </>
                )}
            </p>
            <p><button type="submit" data-testid="pla-tps-flux-ajouter">Ajouter le palier</button></p>
          </div>
        </form>
      )}
      {erreur && <Message variante="erreur" testId="pla-tps-flux-erreur">{erreur}</Message>}
      {info && <p role="status" data-testid="pla-tps-flux-info">{info}</p>}
    </section>
  );
}
