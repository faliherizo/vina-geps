/**
 * **AGPH** — onglet de la planification des effectifs (AO 26-015-DO, lot R7 — E-CH-1, E-CH-7 à
 * E-CH-10, P-CH-4 à P-CH-7, P-CH-9).
 *
 * Deux vues du même dossier : la campagne annuelle (créer, fixer le budget, figer les rangs, suivre
 * les disponibilités déposées, démarrer le tour de rôle, saisir un choix pour autrui, réviser les
 * attributions, clore — les postes restés libres passent à la gestion régulière) et les
 * remplacements long terme (ouvrir, voir les intéressés dans l'ordre où l'attribution les prendra,
 * fermer la fenêtre, ou laisser non comblé, motif à l'appui).
 */
import { useCallback, useEffect, useState } from "react";
import { objet } from "../../formeRecue";
import { BadgeStatut, EtatVide, Message, Tableau, Tiroir, type Colonne } from "../../composants";
import { useApiEffectifs } from "../api";
import type { Poste } from "../planification/types";
import { useReferentiels } from "../planification/useReferentiels";
import DepotsCampagnePanneau from "./DepotsCampagne";
import {
  instant, LIBELLE_CAMPAGNE, LIBELLE_MODE, LIBELLE_RANG, LIBELLE_REMPLACEMENT,
  type Campagne, type Interesse, type PostesDisponibles, type Rang, type Remplacement, type VueCampagne,
} from "./types";

const COLONNES_RANGS: ReadonlyArray<Colonne<Rang>> = [
  { id: "rang", libelle: "Rang", numerique: true, tri: (r) => r.rang }, { id: "nom", libelle: "Personne", tri: (r) => r.nom },
  { id: "anciennete", libelle: "Ancienneté figée", numerique: true }, { id: "etat", libelle: "Tour" }, { id: "choix", libelle: "Poste obtenu" },
  { id: "actions", libelle: "Actions", actions: true },
];
const COLONNES_RLT: ReadonlyArray<Colonne<Remplacement>> = [
  { id: "poste", libelle: "Poste", tri: (x) => x.posteCode }, { id: "periode", libelle: "Période", tri: (x) => x.debut },
  { id: "fenetre", libelle: "Manifestations jusqu'au" }, { id: "etat", libelle: "État" }, { id: "interesses", libelle: "Intéressés", numerique: true },
  { id: "actions", libelle: "Actions", actions: true },
];

/** Un jour en avant d'aujourd'hui, au format du champ `date`. */
function jourDecale(jours: number): string {
  const d = new Date();
  d.setDate(d.getDate() + jours);
  return d.toISOString().slice(0, 10);
}

/** L'instant courant au format du champ `datetime-local` (sans fuseau, comme le champ l'attend). */
function instantLocal(d: Date): string {
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

/**
 * Remet les trois dates d'accord quand le début change.
 *
 * La fin ne précède jamais le début, et la limite des manifestations ne le dépasse jamais. Les
 * corriger **au changement** vaut mieux que de refuser à l'envoi : l'écran montre alors ce qu'il
 * accepte, au lieu d'expliquer après coup ce qu'il refusait.
 */
function ajusterRlt(r: { debut: string; fin: string; limite: string }): { debut: string; fin: string; limite: string } {
  const fin = r.fin && r.fin >= r.debut ? r.fin : r.debut;
  const limite = r.limite && r.limite.slice(0, 10) <= r.debut ? r.limite : `${r.debut}T17:00`;
  return { debut: r.debut, fin, limite };
}

export default function AgphPlanification() {
  const { lire, envoyer, erreur, setErreur } = useApiEffectifs();
  const { refs } = useReferentiels(["unites", "corps_emploi"]);
  const [campagnes, setCampagnes] = useState<Campagne[]>([]);
  const [campagneId, setCampagneId] = useState("");
  const [vue, setVue] = useState<VueCampagne | null>(null);
  const [remplacements, setRemplacements] = useState<Remplacement[]>([]);
  const [postes, setPostes] = useState<Poste[]>([]);
  const [info, setInfo] = useState("");
  const [creation, setCreation] = useState(false);
  const [saisie, setSaisie] = useState<{ rang: Rang; dispo: PostesDisponibles | null } | null>(null);
  const [ouvrirRlt, setOuvrirRlt] = useState(false);
  /**
   * Les trois dates du remplacement, tenues ensemble.
   *
   * Des valeurs par défaut **cohérentes** : un remplacement de deux semaines dans un mois, et une
   * fenêtre qui se ferme la veille. Un formulaire vide obligeait à deviner la combinaison que le
   * serveur accepte — et le premier essai était presque toujours refusé.
   */
  const [rlt, setRlt] = useState(() => ajusterRlt({
    debut: jourDecale(30), fin: jourDecale(44), limite: `${jourDecale(29)}T17:00`,
  }));
  const [interesses, setInteresses] = useState<{ x: Remplacement; liste: Interesse[] | null } | null>(null);
  const [revision, setRevision] = useState<{ rang: Rang; dispo: PostesDisponibles | null } | null>(null);
  const [nonComble, setNonComble] = useState<Remplacement | null>(null);
  const tiroirOuvert = !!saisie || creation || ouvrirRlt || !!revision || !!nonComble;

  const charger = useCallback(async () => {
    const c = (await lire<Campagne[]>("/api/effectifs/agph/campagnes")) ?? [];
    setCampagnes(c);
    setCampagneId((id) => (id && c.some((x) => x.id === id) ? id : c[0]?.id ?? ""));
    setRemplacements((await lire<Remplacement[]>("/api/effectifs/agph/remplacements")) ?? []);
    setPostes((await lire<Poste[]>("/api/effectifs/planification/postes")) ?? []);
  }, [lire]);
  useEffect(() => { void charger(); }, [charger]);
  useEffect(() => {
    if (!campagneId) { setVue(null); return; }
    void lire<VueCampagne>(`/api/effectifs/agph/campagnes/${campagneId}`).then(setVue);
  }, [campagneId, lire, campagnes]);

  const geste = async (chemin: string, message: string) => {
    setInfo("");
    if (await envoyer(chemin, {})) { setInfo(message); await charger(); }
  };
  const creer = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const c = await envoyer<Campagne>("/api/effectifs/agph/campagnes", {
      uniteCode: f.get("uniteCode"), corpsEmploiCode: f.get("corpsEmploiCode"), annee: Number(f.get("annee")), delaiChoixMinutes: Number(f.get("delai")),
      budgetHeuresSemaine: f.get("budget") ? Number(f.get("budget")) : null,
    });
    if (c) { setCreation(false); setCampagneId(c.id); setInfo(`Campagne « ${c.nom} » créée.`); await charger(); }
  };
  // Le tiroir s'ouvre d'abord, les postes le rejoignent (voir la note « tiroir ouvert après la réponse »).
  const ouvrirSaisie = async (rang: Rang) => {
    setErreur("");
    setSaisie({ rang, dispo: null });
    const dispo = await lire<PostesDisponibles>(`/api/effectifs/agph/campagnes/${campagneId}/postes-disponibles?ressourceId=${rang.ressourceId}`);
    setSaisie((s) => (s?.rang.ressourceId === rang.ressourceId ? { rang, dispo } : s));
  };
  const choisirPour = async (posteId: string, code: string) => {
    if (!saisie) return;
    if (await envoyer(`/api/effectifs/agph/campagnes/${campagneId}/choix`, { ressourceId: saisie.rang.ressourceId, posteId })) {
      setInfo(`Poste ${code} saisi pour ${saisie.rang.nom}.`); setSaisie(null); await charger();
    }
  };
  const ouvrirRemplacement = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const x = await envoyer<Remplacement>("/api/effectifs/agph/remplacements", {
      posteId: f.get("posteId"), origine: f.get("origine"), referenceId: f.get("referenceId") || undefined,
      debut: f.get("debut"), fin: f.get("fin"), finManifestation: new Date(String(f.get("finManifestation"))).toISOString(),
    });
    if (x) { setOuvrirRlt(false); setInfo(`Remplacement du poste ${x.posteCode} ouvert aux manifestations.`); await charger(); }
  };
  const voirInteresses = async (x: Remplacement) => {
    setInteresses({ x, liste: null });
    // Le remplacement relu avec ses intéressés : une fenêtre a pu se fermer depuis l'affichage de la liste.
    const [frais, liste] = await Promise.all([lire<Remplacement>(`/api/effectifs/agph/remplacements/${x.id}`), lire<Interesse[]>(`/api/effectifs/agph/remplacements/${x.id}/interesses`)]);
    setInteresses((s) => (s?.x.id === x.id ? { x: objet<Remplacement>(frais) ?? x, liste: liste ?? [] } : s));
  };

  const ouvrirRevision = async (rang: Rang) => {
    setErreur("");
    setRevision({ rang, dispo: null });
    const dispo = await lire<PostesDisponibles>(`/api/effectifs/agph/campagnes/${campagneId}/postes-disponibles?ressourceId=${rang.ressourceId}`);
    setRevision((s) => (s?.rang.ressourceId === rang.ressourceId ? { rang, dispo } : s));
  };
  const reviserChoix = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!revision) return;
    const f = new FormData(e.currentTarget);
    const posteId = String(f.get("posteId") ?? "");
    if (await envoyer(`/api/effectifs/agph/campagnes/${campagneId}/revision/choix`, { ressourceId: revision.rang.ressourceId, posteId: posteId || undefined, motif: f.get("motif") })) {
      setInfo(`Attribution de ${revision.rang.nom} révisée.`); setRevision(null); await charger();
    }
  };
  const laisserNonComble = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!nonComble) return;
    const x = await envoyer<Remplacement>(`/api/effectifs/agph/remplacements/${nonComble.id}/non-comble`, { motif: new FormData(e.currentTarget).get("motif") });
    if (x) {
      setNonComble(null);
      setInfo(`Remplacement du poste ${x.posteCode} laissé non comblé : ${x.quartsTransferes ?? 0} quart(s) transféré(s) à la gestion régulière.`);
      await charger();
    }
  };

  const choixDe = (r: Rang) => vue?.choix.find((c) => c.ressourceId === r.ressourceId);

  return (
    <div data-testid="pla-agph">
      {erreur && !tiroirOuvert && <Message variante="erreur" testId="pla-agph-erreur">{erreur}</Message>}
      {info && <Message variante="succes" testId="pla-agph-info">{info}</Message>}

      <h3>Campagne annuelle</h3>
      <div className="eff-barre">
        <p>
          <label htmlFor="pla-agph-campagne">Campagne</label>
          <select id="pla-agph-campagne" data-testid="pla-agph-campagne" value={campagneId} onChange={(e) => setCampagneId(e.target.value)}>
            {campagnes.length === 0 && <option value="">Aucune campagne</option>}
            {campagnes.map((c) => <option key={c.id} value={c.id}>{c.nom} — {c.uniteCode} ({c.corpsEmploiCode})</option>)}
          </select>
        </p>
        <p className="eff-actions">
          <button type="button" className="secondary" data-testid="pla-agph-creer" onClick={() => { setErreur(""); setCreation(true); }}>Nouvelle campagne</button>
        </p>
      </div>
      {vue && (
        <>
          <p data-testid="pla-agph-etat">
            <BadgeStatut statut={vue.etat === "CLOSE" ? "annulee" : "en_attente"}>{LIBELLE_CAMPAGNE[vue.etat]}</BadgeStatut> Délai de choix : {vue.delaiChoixMinutes} minutes par rang.
          </p>
          <p className="eff-actions">
            {vue.etat === "PREPARATION" && <button type="button" data-testid="pla-agph-ouvrir" onClick={() => void geste(`/api/effectifs/agph/campagnes/${vue.id}/ouvrir`, "Rangs figés par ancienneté ; postes offerts.")}>Figer les rangs et offrir les postes</button>}
            {vue.etat === "DEPOT" && <button type="button" data-testid="pla-agph-demarrer" onClick={() => void geste(`/api/effectifs/agph/campagnes/${vue.id}/demarrer`, "Tour de rôle démarré : le rang 1 a la main.")}>Démarrer le tour de rôle</button>}
            {vue.etat === "ATTRIBUTION" && <button type="button" className="secondary" data-testid="pla-agph-reviser" onClick={() => void geste(`/api/effectifs/agph/campagnes/${vue.id}/reviser`, "Révision ouverte : corrigez les attributions, puis closez.")}>Ouvrir la révision</button>}
            {(vue.etat === "ATTRIBUTION" || vue.etat === "REVISION") && <button type="button" data-testid="pla-agph-clore" onClick={() => void geste(`/api/effectifs/agph/campagnes/${vue.id}/clore`, "Campagne close : statuts d'emploi posés, postes restants transférés à la gestion régulière.")}>Clore et poser les statuts</button>}
          </p>
          {vue.etat === "CLOSE" && (vue.postesNonPourvus ?? []).length > 0 && (
            <Message variante="info" titre="Postes transférés à la gestion régulière" testId="pla-agph-transferes">
              <p>Restés libres à la clôture, ces postes sont vacants : leurs quarts se génèrent avec une place libre, que le comblement et l'attribution depuis les disponibilités prennent en charge.</p>
              <ul>{(vue.postesNonPourvus ?? []).map((p) => <li key={p.posteId} data-testid={`pla-agph-transfere-${p.posteCode}`}>{p.posteCode} — {p.etat === "VACANT" ? "vacant" : p.etat.toLowerCase()}</li>)}</ul>
            </Message>
          )}
          <DepotsCampagnePanneau campagne={vue} onParametree={charger} />
          <Tableau colonnes={COLONNES_RANGS} lignes={vue.rangs} legende="Rangs de la campagne" testId="pla-agph-rangs"
            vide={<EtatVide titre="Rangs non encore figés" compact>Les rangs se figent à l'ouverture de la campagne.</EtatVide>}
            rendre={(r) => {
              const c = choixDe(r);
              return (
                <tr key={r.ressourceId} data-testid={`pla-agph-rang-${r.rang}`}>
                  <td className="col-numerique">{r.rang}</td>
                  <td>{r.nom} ({r.matricule})</td>
                  <td className="col-numerique">{r.anciennete}</td>
                  <td>{LIBELLE_RANG[r.etat]}{r.etat === "A_SON_TOUR" ? ` — jusqu'au ${instant(r.tourFin)}` : ""}</td>
                  <td>{c ? `${c.posteCode} (${LIBELLE_MODE[c.mode] ?? c.mode})` : "—"}</td>
                  <td>
                    {r.etat === "A_SON_TOUR" && (
                      <button type="button" className="secondary" data-testid={`pla-agph-saisir-${r.rang}`} onClick={() => void ouvrirSaisie(r)}>
                        Saisir son choix<span className="sr-only"> — {r.nom}</span>
                      </button>
                    )}
                    {vue.etat === "REVISION" && (
                      <button type="button" className="secondary" data-testid={`pla-agph-reviser-${r.rang}`} onClick={() => void ouvrirRevision(r)}>
                        Réviser<span className="sr-only"> l'attribution de {r.nom}</span>
                      </button>
                    )}
                  </td>
                </tr>
              );
            }} />
        </>
      )}

      <h3>Remplacements long terme</h3>
      <p className="eff-actions">
        <button type="button" className="secondary" data-testid="pla-rlt-ouvrir" onClick={() => { setErreur(""); setOuvrirRlt(true); }}>Ouvrir un remplacement</button>
      </p>
      <Tableau colonnes={COLONNES_RLT} lignes={remplacements} legende="Remplacements long terme" testId="pla-rlt-table"
        vide={<EtatVide titre="Aucun remplacement long terme" compact testId="pla-rlt-vide">Ouvrez-en un pour une absence longue ou un poste vacant.</EtatVide>}
        rendre={(x) => (
          <tr key={x.id} data-testid={`pla-rlt-${x.id}`}>
            <td>{x.posteCode}</td>
            <td>{x.debut} → {x.fin}</td>
            <td>{instant(x.finManifestation)}</td>
            <td data-testid={`pla-rlt-${x.id}-etat`}>
              {LIBELLE_REMPLACEMENT[x.etat]}{x.attribueANom ? ` — ${x.attribueANom} (${x.placesAttribuees} quart(s))` : ""}
              {x.etat === "NON_COMBLE" && ` — ${x.quartsTransferes ?? 0} quart(s) transféré(s) à la gestion régulière le ${instant(x.transfereLe ?? null)}`}
              {x.motifNonComble && <><br />Motif : {x.motifNonComble}</>}
            </td>
            <td className="col-numerique">{x.interesses}</td>
            <td className="eff-actions">
              <button type="button" className="secondary" data-testid={`pla-rlt-${x.id}-interesses`} onClick={() => void voirInteresses(x)}>Intéressés<span className="sr-only"> — poste {x.posteCode}</span></button>
              {x.etat === "OUVERT" && (
                <button type="button" data-testid={`pla-rlt-${x.id}-fermer`} onClick={() => void geste(`/api/effectifs/agph/remplacements/${x.id}/fermer`, `Fenêtre fermée pour le poste ${x.posteCode} : attribution faite.`)}>
                  Fermer et attribuer<span className="sr-only"> — poste {x.posteCode}</span>
                </button>
              )}
              {x.etat === "OUVERT" && (
                <button type="button" className="secondary" data-testid={`pla-rlt-${x.id}-non-comble`} onClick={() => { setErreur(""); setNonComble(x); }}>
                  Laisser non comblé<span className="sr-only"> — poste {x.posteCode}</span>
                </button>
              )}
            </td>
          </tr>
        )} />

      <Tiroir ouvert={creation} onFermer={() => setCreation(false)} titre="Nouvelle campagne AGPH" testId="pla-agph-creation">
        <form onSubmit={(e) => void creer(e)}>
          <p>
            <label htmlFor="pla-agph-unite">Unité</label>
            <select id="pla-agph-unite" name="uniteCode" data-testid="pla-agph-unite" required>
              {(refs.unites ?? []).map((u) => <option key={u.code} value={u.code}>{u.libelleFr}</option>)}
            </select>
          </p>
          <p>
            <label htmlFor="pla-agph-corps">Corps d'emploi</label>
            <select id="pla-agph-corps" name="corpsEmploiCode" data-testid="pla-agph-corps" required>
              {(refs.corps_emploi ?? []).map((c) => <option key={c.code} value={c.code}>{c.libelleFr}</option>)}
            </select>
          </p>
          <p><label htmlFor="pla-agph-annee">Année</label><input id="pla-agph-annee" name="annee" type="number" data-testid="pla-agph-annee" required defaultValue={new Date().getFullYear() + 1} /></p>
          <p><label htmlFor="pla-agph-budget-creation">Budget d'heures par semaine (facultatif)</label><input id="pla-agph-budget-creation" name="budget" type="number" min={1} step="0.5" data-testid="pla-agph-budget-creation" /></p>
          <p><label htmlFor="pla-agph-delai">Délai de choix (minutes)</label><input id="pla-agph-delai" name="delai" type="number" min={5} max={10080} data-testid="pla-agph-delai" required defaultValue={30} /></p>
          {erreur && <Message variante="erreur" testId="pla-agph-creation-erreur">{erreur}</Message>}
          <p className="eff-actions"><button type="submit" data-testid="pla-agph-creer-envoyer">Créer</button></p>
        </form>
      </Tiroir>

      <Tiroir ouvert={!!saisie} onFermer={() => setSaisie(null)} titre={saisie ? `Choix de ${saisie.rang.nom} (rang ${saisie.rang.rang})` : "Choix"} testId="pla-agph-saisie">
        {saisie && !saisie.dispo && <p role="status">Chargement des postes…</p>}
        {saisie?.dispo && (
          <ul className="eff-places" aria-label="Postes encore offerts">
            {saisie.dispo.postes.map((p) => (
              <li key={p.id}>
                <strong>{p.code}</strong> ({p.statutEmploiCode}) — {p.admissible ? "admissible" : `non admissible : ${p.motifs.join(" ; ")}`}{" "}
                {p.admissible && <button type="button" data-testid={`pla-agph-saisie-${p.code}`} onClick={() => void choisirPour(p.id, p.code)}>Retenir<span className="sr-only"> le poste {p.code}</span></button>}
              </li>
            ))}
          </ul>
        )}
        {erreur && <Message variante="erreur" testId="pla-agph-saisie-erreur">{erreur}</Message>}
      </Tiroir>

      <Tiroir ouvert={!!revision} onFermer={() => setRevision(null)} titre={revision ? `Réviser l'attribution de ${revision.rang.nom}` : "Révision"} testId="pla-agph-revision">
        {revision && !revision.dispo && <p role="status">Chargement des postes…</p>}
        {revision?.dispo && (
          <form onSubmit={(e) => void reviserChoix(e)}>
            <p>Poste actuel : {choixDe(revision.rang)?.posteCode ?? "aucun"}.</p>
            <p>
              <label htmlFor="pla-agph-revision-poste">Nouvelle attribution</label>
              <select id="pla-agph-revision-poste" name="posteId" data-testid="pla-agph-revision-poste">
                <option value="">Retirer le poste obtenu</option>
                {revision.dispo.postes.map((p) => (
                  <option key={p.id} value={p.id} disabled={!p.admissible}>{p.code} ({p.statutEmploiCode}){p.admissible ? "" : ` — non admissible : ${p.motifs.join(" ; ")}`}</option>
                ))}
              </select>
            </p>
            <p>
              <label htmlFor="pla-agph-revision-motif">Motif de la révision</label>
              <textarea id="pla-agph-revision-motif" name="motif" data-testid="pla-agph-revision-motif" required maxLength={500} />
            </p>
            {erreur && <Message variante="erreur" testId="pla-agph-revision-erreur">{erreur}</Message>}
            <p className="eff-actions"><button type="submit" data-testid="pla-agph-revision-envoyer">Réviser</button></p>
          </form>
        )}
      </Tiroir>

      <Tiroir ouvert={!!nonComble} onFermer={() => setNonComble(null)} titre={nonComble ? `Laisser non comblé — poste ${nonComble.posteCode}` : "Laisser non comblé"} testId="pla-rlt-non-comble">
        {nonComble && (
          <form onSubmit={(e) => void laisserNonComble(e)}>
            <p>Le remplacement demeurera non comblé, même s'il a des intéressés ({nonComble.interesses}) ; ses quarts vacants passeront à la gestion régulière.</p>
            <p>
              <label htmlFor="pla-rlt-non-comble-motif">Motif</label>
              <textarea id="pla-rlt-non-comble-motif" name="motif" data-testid="pla-rlt-non-comble-motif" required maxLength={500} />
            </p>
            {erreur && <Message variante="erreur" testId="pla-rlt-non-comble-erreur">{erreur}</Message>}
            <p className="eff-actions"><button type="submit" data-testid="pla-rlt-non-comble-envoyer">Laisser non comblé</button></p>
          </form>
        )}
      </Tiroir>

      <Tiroir ouvert={ouvrirRlt} onFermer={() => setOuvrirRlt(false)} titre="Ouvrir un remplacement long terme" testId="pla-rlt-creation">
        <form onSubmit={(e) => void ouvrirRemplacement(e)}>
          <p>
            <label htmlFor="pla-rlt-poste">Poste</label>
            <select id="pla-rlt-poste" name="posteId" data-testid="pla-rlt-poste" required>
              {postes.filter((p) => p.etat !== "BROUILLON").map((p) => <option key={p.id} value={p.id}>{p.code} — {p.libelle}</option>)}
            </select>
          </p>
          <p>
            <label htmlFor="pla-rlt-origine">Origine</label>
            <select id="pla-rlt-origine" name="origine" data-testid="pla-rlt-origine"><option value="POSTE_VACANT">Poste vacant</option><option value="ABSENCE">Absence longue</option></select>
          </p>
          <p><label htmlFor="pla-rlt-reference">Absence remplacée (si absence)</label><input id="pla-rlt-reference" name="referenceId" data-testid="pla-rlt-reference" /></p>
          {/*
            **Les trois dates se tiennent l'une l'autre.**

            Le domaine pose deux règles, et elles se contredisent si on les laisse libres : la
            fenêtre de manifestation ne peut pas se fermer **dans le passé**, ni **après le premier
            jour du remplacement** — on ne se porte pas candidat à un remplacement commencé. Un
            formulaire à trois champs libres laissait donc composer l'impossible, et le refus
            n'arrivait qu'après l'envoi, sous la forme d'un message que l'on prenait pour un défaut.

            Les bornes sont donc posées **ici** : le début ne peut pas précéder demain (une fenêtre
            de zéro jour n'est pas une fenêtre), la fin suit le début, et la limite des
            manifestations vit entre maintenant et la veille du début. Le serveur reste juge ;
            l'écran cesse seulement de proposer ce qu'il refusera.
          */}
          <p>
            <label htmlFor="pla-rlt-debut">Du</label>
            <input id="pla-rlt-debut" name="debut" type="date" data-testid="pla-rlt-debut" required
                   min={jourDecale(1)} value={rlt.debut}
                   onChange={(e) => setRlt((r) => ajusterRlt({ ...r, debut: e.target.value }))} />
          </p>
          <p>
            <label htmlFor="pla-rlt-fin">Au</label>
            <input id="pla-rlt-fin" name="fin" type="date" data-testid="pla-rlt-fin" required
                   min={rlt.debut || jourDecale(1)} value={rlt.fin}
                   onChange={(e) => setRlt((r) => ({ ...r, fin: e.target.value }))} />
          </p>
          <p>
            <label htmlFor="pla-rlt-limite">Manifestations jusqu'au</label>
            <input id="pla-rlt-limite" name="finManifestation" type="datetime-local" data-testid="pla-rlt-limite"
                   required min={instantLocal(new Date())} max={`${rlt.debut || jourDecale(1)}T23:59`}
                   value={rlt.limite} onChange={(e) => setRlt((r) => ({ ...r, limite: e.target.value }))}
                   aria-describedby="pla-rlt-limite-aide" />
            <span className="muted" id="pla-rlt-limite-aide" style={{ display: "block", fontSize: 12.5 }}>
              Au plus tard le premier jour du remplacement ({rlt.debut || jourDecale(1)}) : on ne se
              porte pas candidat à un remplacement déjà commencé.
            </span>
          </p>
          {erreur && <Message variante="erreur" testId="pla-rlt-creation-erreur">{erreur}</Message>}
          <p className="eff-actions"><button type="submit" data-testid="pla-rlt-envoyer">Ouvrir</button></p>
        </form>
      </Tiroir>

      <Tiroir ouvert={!!interesses} onFermer={() => setInteresses(null)} titre={interesses ? `Intéressés — poste ${interesses.x.posteCode}` : "Intéressés"} testId="pla-rlt-interesses">
        {interesses && !interesses.liste && <p role="status">Chargement…</p>}
        {interesses?.liste && (interesses.liste.length === 0
          ? <p>Personne ne s'est encore manifesté.</p>
          : <ol aria-label="Intéressés, dans l'ordre d'attribution">{interesses.liste.map((i) => <li key={i.ressourceId}>{i.nom} ({i.matricule}) — {i.anciennete} an(s), depuis le {instant(i.deposeLe)}</li>)}</ol>)}
      </Tiroir>
    </div>
  );
}
