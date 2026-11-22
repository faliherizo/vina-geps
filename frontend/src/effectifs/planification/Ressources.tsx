/**
 * **Ressources planifiables** : chaque personne avec son unité, son corps et son titre d'emploi, son
 * statut, sa convention et son ancienneté — ce que les règles et l'AGPH doivent savoir d'elle.
 * Toutes les listes viennent des référentiels de l'organisme.
 */
import { useCallback, useEffect, useState } from "react";
import { Badge, EtatVide, Message, Tableau, Tiroir, type Colonne } from "../../composants";
import { useApiEffectifs } from "../api";
import { liste as listeRecue } from "../../formeRecue";
import type { Valeur } from "../Referentiels";
import type { Ressource } from "./types";
import { libelleDe, useReferentiels } from "./useReferentiels";

const API = "/api/effectifs/planification/ressources";

/** Un compte de l'organisme, tel que l'administration le liste. */
interface Compte { id: string; username: string; role: string }

/**
 * **Les comptes à relier.** Une ressource sans compte ne trouve pas son propriétaire : ses écrans
 * personnels répondent « votre compte n'est relié à aucune ressource planifiable ». La liste n'est
 * servie qu'à l'administration (`/api/auth/users`) ; pour un autre rôle, `null` — le lien existant est
 * gardé tel quel, et un refus de lecture n'est pas une erreur de l'écran.
 */
function useComptes(apiFetch: (chemin: string) => Promise<Response>): Compte[] | null {
  const [comptes, setComptes] = useState<Compte[] | null>(null);
  useEffect(() => {
    void apiFetch("/api/auth/users")
      .then(async (r) => setComptes(r.ok ? listeRecue<Compte>(await r.json()).filter((c) => c.role !== "superadmin") : null))
      .catch(() => setComptes(null));
  }, [apiFetch]);
  return comptes;
}

export default function Ressources() {
  const { lire, envoyer, erreur, setErreur, succes, setSucces, apiFetch } = useApiEffectifs();
  const comptes = useComptes(apiFetch);
  const { refs } = useReferentiels(["unites", "corps_emploi", "titres_emploi", "statuts_emploi", "conventions"]);
  const [ressources, setRessources] = useState<Ressource[]>([]);
  const [fiche, setFiche] = useState<Ressource | "nouvelle" | null>(null);
  const charger = useCallback(async () => { const r = await lire<Ressource[]>(API); if (r) setRessources(r); }, [lire]);
  useEffect(() => { void charger(); }, [charger]);

  const colonnes: ReadonlyArray<Colonne<Ressource>> = [
    { id: "nom", libelle: "Personne", tri: (r) => r.nom },
    { id: "unite", libelle: "Unité", tri: (r) => r.uniteCode },
    { id: "corps", libelle: "Corps d'emploi" },
    { id: "statut", libelle: "Statut" },
    { id: "anciennete", libelle: "Ancienneté", numerique: true, tri: (r) => r.anciennete },
    { id: "actions", libelle: "Actions", actions: true },
  ];

  return (
    <div data-testid="pla-ressources">
      <div className="eff-barre"><button type="button" data-testid="pla-ressource-nouvelle" onClick={() => { setErreur(""); setFiche("nouvelle"); }}>Nouvelle ressource</button></div>
      {erreur && !fiche && <Message variante="erreur" testId="pla-ressources-erreur">{erreur}</Message>}
      {succes && <Message variante="succes" testId="pla-ressources-succes">{succes}</Message>}
      <Tableau colonnes={colonnes} lignes={ressources} legende="Ressources planifiables de votre périmètre" testId="pla-ressources-tableau"
        vide={<EtatVide titre="Aucune ressource" compact testId="pla-ressources-vide">Aucune personne planifiable dans votre périmètre.</EtatVide>}
        rendre={(r) => (
          <tr key={r.id} data-testid={`pla-ressource-${r.matricule}`}>
            <td>{r.nom} <span className="muted">({r.matricule})</span> {!r.actif && <Badge ton="neutre">Inactive</Badge>}</td>
            <td>{r.uniteCode}</td>
            <td>{libelleDe(refs.corps_emploi, r.corpsEmploiCode)}</td>
            <td>{libelleDe(refs.statuts_emploi, r.statutEmploiCode)}</td>
            <td className="col-numerique">{r.anciennete.toLocaleString("fr-CA")}</td>
            <td className="col-menu">
              <button type="button" className="secondary" data-testid={`pla-ressource-modifier-${r.matricule}`} onClick={() => { setErreur(""); setFiche(r); }}>
                Modifier<span className="sr-only"> {r.nom}</span>
              </button>
            </td>
          </tr>
        )} />
      {fiche && (
        <FicheRessource ressource={fiche === "nouvelle" ? null : fiche} refs={refs} comptes={comptes} erreur={erreur} onFermer={() => setFiche(null)} onEnregistrer={async (c) => {
          const r = fiche === "nouvelle" ? await envoyer<Ressource>(API, c) : await envoyer<Ressource>(`${API}/${fiche.id}`, c, "PUT");
          if (r) { setFiche(null); setSucces(`« ${r.nom} » enregistrée.`); await charger(); }
        }} />
      )}
    </div>
  );
}

function FicheRessource({ ressource, refs, comptes, erreur, onFermer, onEnregistrer }: {
  ressource: Ressource | null; refs: Record<string, Valeur[]>; comptes: Compte[] | null; erreur: string; onFermer: () => void; onEnregistrer: (c: Record<string, unknown>) => Promise<void>;
}) {
  const [v, setV] = useState<Record<string, string | boolean>>({
    matricule: ressource?.matricule ?? "", nom: ressource?.nom ?? "", uniteCode: ressource?.uniteCode ?? "", corpsEmploiCode: ressource?.corpsEmploiCode ?? "",
    titreEmploiCode: ressource?.titreEmploiCode ?? "", statutEmploiCode: ressource?.statutEmploiCode ?? "", conventionCode: ressource?.conventionCode ?? "",
    anciennete: String(ressource?.anciennete ?? 0), actif: ressource?.actif ?? true,
    // Renvoyé à chaque enregistrement : la modification remplace la ressource entière, et un lien omis
    // y était remis à nul — la personne perdait ses écrans personnels à la première correction de son nom.
    utilisateurId: ressource?.utilisateurId ?? "",
  });
  const poser = (k: string, x: string | boolean) => setV((o) => ({ ...o, [k]: x }));
  const liste = (cle: string, libelle: string, type: string, requis: boolean) => (
    <p>
      <label htmlFor={`pla-res-${cle}`}>{libelle}{requis ? "" : " (facultatif)"}</label>
      <select id={`pla-res-${cle}`} data-testid={`pla-res-${cle}`} required={requis} value={String(v[cle])} onChange={(e) => poser(cle, e.target.value)}>
        <option value="">{requis ? "Choisir…" : "Aucun"}</option>
        {(refs[type] ?? []).map((o) => <option key={o.code} value={o.code}>{o.libelleFr}</option>)}
      </select>
    </p>
  );
  return (
    <Tiroir ouvert titre={ressource ? ressource.nom : "Nouvelle ressource"} onFermer={onFermer} testId="pla-ressource-fiche">
      <form onSubmit={(e) => { e.preventDefault(); void onEnregistrer({ ...v, anciennete: Number(v.anciennete) }); }}>
        {!ressource && <p><label htmlFor="pla-res-matricule">Matricule</label><input id="pla-res-matricule" data-testid="pla-res-matricule" required value={String(v.matricule)} onChange={(e) => poser("matricule", e.target.value)} /></p>}
        <p><label htmlFor="pla-res-nom">Nom</label><input id="pla-res-nom" data-testid="pla-res-nom" required value={String(v.nom)} onChange={(e) => poser("nom", e.target.value)} /></p>
        {liste("uniteCode", "Unité", "unites", true)}
        {liste("corpsEmploiCode", "Corps d'emploi", "corps_emploi", true)}
        {liste("titreEmploiCode", "Titre d'emploi", "titres_emploi", false)}
        {liste("statutEmploiCode", "Statut d'emploi", "statuts_emploi", false)}
        {liste("conventionCode", "Convention collective", "conventions", false)}
        <ChampCompte comptes={comptes} valeur={String(v.utilisateurId)} onChange={(x) => poser("utilisateurId", x)} />
        <p><label htmlFor="pla-res-anciennete">Ancienneté (selon la convention)</label>
          <input id="pla-res-anciennete" type="number" min={0} step="any" data-testid="pla-res-anciennete" value={String(v.anciennete)} onChange={(e) => poser("anciennete", e.target.value)} /></p>
        {ressource && (
          <p className="eff-case"><input id="pla-res-actif" type="checkbox" data-testid="pla-res-actif" checked={v.actif === true} onChange={(e) => poser("actif", e.target.checked)} /><label htmlFor="pla-res-actif">Active</label></p>
        )}
        {erreur && <Message variante="erreur" testId="pla-res-erreur">{erreur}</Message>}
        <button type="submit" data-testid="pla-res-enregistrer">Enregistrer</button>
      </form>
    </Tiroir>
  );
}

/** Le compte de connexion relié à la ressource : choisi par l'administration, lu par les autres rôles. */
function ChampCompte({ comptes, valeur, onChange }: { comptes: Compte[] | null; valeur: string; onChange: (x: string) => void }) {
  if (!comptes) {
    return (
      <p className="muted" data-testid="pla-res-utilisateur-lecture">
        Compte utilisateur : {valeur ? "relié" : "aucun — l'administration de l'organisme le relie"}.
      </p>
    );
  }
  const inconnu = valeur !== "" && !comptes.some((c) => c.id === valeur);
  return (
    <p>
      <label htmlFor="pla-res-utilisateur">Compte utilisateur (facultatif)</label>
      <select id="pla-res-utilisateur" data-testid="pla-res-utilisateur" aria-describedby="pla-res-utilisateur-aide" value={valeur} onChange={(e) => onChange(e.target.value)}>
        <option value="">Aucun</option>
        {inconnu && <option value={valeur}>{valeur}</option>}
        {comptes.map((c) => <option key={c.id} value={c.id}>{c.username}</option>)}
      </select>
      <span className="aide" id="pla-res-utilisateur-aide">
        Le compte de connexion de la personne : sans lui, son horaire, ses absences et sa feuille de temps lui répondent qu'elle n'est reliée à aucune ressource.
      </span>
    </p>
  );
}
