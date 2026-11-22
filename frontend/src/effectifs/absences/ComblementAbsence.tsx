/**
 * **Le comblement d'une absence accordée** — dans la fiche d'Absences et comblement (AO 26-015-DO,
 * lot U5 : E-GN-6, P-GN-1, P-GH-3, P-CH-8).
 *
 * Ce que le mécanisme a fait de chaque place libérée, sans geste de personne : attribuée d'office au
 * prioritaire, offerte aux admissibles — avec les avis envoyés, canal par canal —, sans candidat, ou
 * réservée au remplacement long terme. L'encadrement attribue une place offerte à l'un des avisés,
 * et ouvre, pour une absence longue, le remplacement long terme **lié à cette absence**.
 */
import { useCallback, useEffect, useState } from "react";
import { objet } from "../../formeRecue";
import { BadgeStatut, Message } from "../../composants";
import { heure, useApiEffectifs } from "../api";
import OrdreOffre from "../offres/OrdreOffre";
import { offresRecues, type OffreQuart } from "../offres/types";

type Resultat = "ATTRIBUEE" | "OFFERTE" | "SANS_CANDIDAT" | "REMPLACEMENT_LONG_TERME";
interface Avis { ressourceId: string; nom: string; canal: string; statut: string; erreur: string | null }
interface PlaceComblee {
  id: string; placeId: string; date: string; debut: number; fin: number; mode: "AUTOMATISE" | "MANUEL"; resultat: Resultat;
  attribueeANom: string | null; occupeeParId: string | null; occupeeParNom: string | null; avis: Avis[];
}
interface Remplacement { id: string; posteCode: string; etat: string; debut: string; fin: string; finManifestation: string; attribueANom: string | null }
interface Comblement {
  places: PlaceComblee[]; remplacement: Remplacement | null; remplacementPossible: boolean; raisonRemplacement: string | null;
  postesProposes: Array<{ id: string; code: string; libelle: string; titulaire: boolean }>;
}

const RESULTATS: Record<Resultat, { libelle: string; ton: string }> = {
  ATTRIBUEE: { libelle: "Attribuée d'office", ton: "approuvee" },
  OFFERTE: { libelle: "Offerte aux admissibles", ton: "en_attente" },
  SANS_CANDIDAT: { libelle: "Sans candidat — gestion régulière", ton: "refusee" },
  REMPLACEMENT_LONG_TERME: { libelle: "Réservée au remplacement long terme", ton: "en_attente" },
};
const CANAUX: Record<string, string> = { courriel: "courriel", texto: "texto", poussee: "notification" };
const STATUTS_AVIS: Record<string, string> = { sent: "envoyé", failed: "échec", desactive: "fermé par la personne", pending: "en attente" };

/** Les avis d'une place, regroupés par personne : « Marc — courriel envoyé, texto échec (aucun numéro…) ». */
function avisParPersonne(avis: Avis[]): Array<{ ressourceId: string; nom: string; detail: string }> {
  const parId = new Map<string, { ressourceId: string; nom: string; parties: string[] }>();
  for (const v of avis) {
    const p = parId.get(v.ressourceId) ?? { ressourceId: v.ressourceId, nom: v.nom, parties: [] };
    p.parties.push(`${CANAUX[v.canal] ?? v.canal} ${STATUTS_AVIS[v.statut] ?? v.statut}${v.erreur ? ` (${v.erreur})` : ""}`);
    parId.set(v.ressourceId, p);
  }
  return [...parId.values()].map((p) => ({ ressourceId: p.ressourceId, nom: p.nom, detail: p.parties.join(", ") }));
}

/** Le lendemain à 17 h, heure locale, au format d'un champ `datetime-local`. */
function demainDixSeptHeures(): string {
  const d = new Date(Date.now() + 86_400_000);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}T17:00`;
}

export default function ComblementAbsence({ absenceId }: { absenceId: string }) {
  const { lire, envoyer, erreur, setErreur, apiFetch } = useApiEffectifs();
  const [c, setC] = useState<Comblement | null>(null);
  const [offres, setOffres] = useState<OffreQuart[]>([]);
  const [choix, setChoix] = useState<Record<string, string>>({});
  const [poste, setPoste] = useState("");
  const [fenetre, setFenetre] = useState(demainDixSeptHeures);
  const [info, setInfo] = useState("");

  const charger = useCallback(async () => {
    const lu = objet<Comblement>(await lire<Comblement>(`/api/effectifs/absences/${absenceId}/comblement`));
    setC(lu);
    // AD-07 : en mode par ancienneté et à tour de rôle, chaque place offerte a son ordre d'offre.
    // Lue à part : sans offre (mode simultané) ou sans droit, la section reste muette, sans erreur au comblement.
    const r = await apiFetch(`/api/effectifs/offres?absenceId=${encodeURIComponent(absenceId)}`);
    setOffres(r.ok ? offresRecues(await r.json().catch(() => [])) : []);
    if (lu?.postesProposes.length) setPoste((p) => p || lu.postesProposes[0].id);
  }, [lire, apiFetch, absenceId]);
  useEffect(() => { void charger(); }, [charger]);

  const attribuer = async (p: PlaceComblee) => {
    const ressourceId = choix[p.id] ?? avisParPersonne(p.avis)[0]?.ressourceId;
    if (!ressourceId) return;
    setErreur(""); setInfo("");
    const r = await envoyer(`/api/effectifs/planification/places/${p.placeId}/attribuer`, { ressourceId });
    if (!r) return;
    setInfo(`Quart du ${p.date} attribué.`);
    await charger();
  };

  const ouvrirRemplacement = async () => {
    setErreur(""); setInfo("");
    const r = await envoyer<Remplacement>(`/api/effectifs/absences/${absenceId}/remplacement`, { posteId: poste, finManifestation: new Date(fenetre).toISOString() });
    if (!r) return;
    setInfo(`Remplacement long terme ouvert sur le poste ${r.posteCode} : les intéressés se manifestent jusqu'au ${r.finManifestation.slice(0, 10)}.`);
    await charger();
  };

  if (!c) return <p role="status" data-testid="pla-abs-comblement-chargement">Lecture du comblement…</p>;
  return (
    <section aria-labelledby="pla-abs-comblement-titre" data-testid="pla-abs-comblement">
      <h3 id="pla-abs-comblement-titre">Comblement des places libérées</h3>
      {info && <Message variante="succes" testId="pla-abs-comblement-info">{info}</Message>}
      {erreur && <Message variante="erreur" testId="pla-abs-comblement-erreur">{erreur}</Message>}
      {c.places.length === 0
        ? <p data-testid="pla-abs-comblement-vide">Aucune place libérée par cette absence.</p>
        : (
          <ul>
            {c.places.map((p) => {
              const avises = avisParPersonne(p.avis);
              const vacante = p.occupeeParId === null;
              return (
                <li key={p.id} data-testid={`pla-abs-comblement-${p.date}`}>
                  <p>
                    <strong>{p.date} · {heure(p.debut)}–{heure(p.fin)}</strong> ({p.mode === "AUTOMATISE" ? "attribution automatisée" : "attribution manuelle"}) —{" "}
                    <BadgeStatut statut={RESULTATS[p.resultat].ton}>{RESULTATS[p.resultat].libelle}</BadgeStatut>
                    {p.attribueeANom && <> à {p.attribueeANom}</>}
                    {" "}— {vacante ? "place vacante" : `occupée par ${p.occupeeParNom}`}
                  </p>
                  {avises.length > 0 && (
                    <>
                      <p>Avis envoyés :</p>
                      <ul data-testid={`pla-abs-avis-${p.date}`}>{avises.map((v) => <li key={v.ressourceId}>{v.nom} — {v.detail}</li>)}</ul>
                    </>
                  )}
                  {offres.filter((o) => o.quart.placeId === p.placeId).map((o) => <OrdreOffre key={o.id} offre={o} />)}
                  {p.resultat === "OFFERTE" && vacante && (
                    <p className="eff-actions">
                      <label htmlFor={`pla-abs-choix-${p.id}`}>Attribuer le quart du {p.date} à</label>
                      <select id={`pla-abs-choix-${p.id}`} data-testid={`pla-abs-choix-${p.date}`} value={choix[p.id] ?? avises[0]?.ressourceId ?? ""}
                              onChange={(e) => setChoix((x) => ({ ...x, [p.id]: e.target.value }))}>
                        {avises.map((v) => <option key={v.ressourceId} value={v.ressourceId}>{v.nom}</option>)}
                      </select>
                      <button type="button" data-testid={`pla-abs-attribuer-${p.date}`} onClick={() => void attribuer(p)}>
                        Attribuer<span className="sr-only"> le quart du {p.date}</span>
                      </button>
                    </p>
                  )}
                </li>
              );
            })}
          </ul>
        )}

      <h3>Remplacement long terme</h3>
      {c.remplacement && (
        <p data-testid="pla-abs-remplacement">
          Remplacement {c.remplacement.id} sur le poste {c.remplacement.posteCode}, lié à cette absence : {c.remplacement.etat.toLowerCase()}
          {c.remplacement.attribueANom ? `, attribué à ${c.remplacement.attribueANom}` : ""} — du {c.remplacement.debut} au {c.remplacement.fin}, manifestations jusqu'au {c.remplacement.finManifestation.slice(0, 10)}.
        </p>
      )}
      {!c.remplacement && c.raisonRemplacement && <p data-testid="pla-abs-remplacement-raison">Sans objet : {c.raisonRemplacement}.</p>}
      {c.remplacementPossible && (
        c.postesProposes.length === 0
          ? <p data-testid="pla-abs-remplacement-sans-poste">Aucun poste publié de l'unité ne peut porter ce remplacement.</p>
          : (
            <div data-testid="pla-abs-remplacement-form">
              <p>Cette absence est de moyen ou long terme : ses quarts sont réservés à un remplacement long terme, à ouvrir ici.</p>
              <p>
                <label htmlFor="pla-abs-remplacement-poste">Poste remplacé</label>
                <select id="pla-abs-remplacement-poste" data-testid="pla-abs-remplacement-poste" value={poste} onChange={(e) => setPoste(e.target.value)}>
                  {c.postesProposes.map((p) => <option key={p.id} value={p.id}>{p.code} — {p.libelle}{p.titulaire ? " (son poste)" : ""}</option>)}
                </select>
              </p>
              <p>
                <label htmlFor="pla-abs-remplacement-fenetre">Fin des manifestations d'intérêt</label>
                <input id="pla-abs-remplacement-fenetre" type="datetime-local" data-testid="pla-abs-remplacement-fenetre" value={fenetre} onChange={(e) => setFenetre(e.target.value)} />
              </p>
              <p className="eff-actions">
                <button type="button" data-testid="pla-abs-remplacement-ouvrir" onClick={() => void ouvrirRemplacement()}>Ouvrir le remplacement long terme</button>
              </p>
            </div>
          )
      )}
    </section>
  );
}
