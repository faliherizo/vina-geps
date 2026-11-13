/**
 * **Mes échanges** — la page du module `effectifs_echanges` côté employé (AO 26-015-DO, lot R6 —
 * E-GR-7, E-GR-12).
 *
 * On choisit un de ses quarts publiés, puis un quart d'une ou d'un collègue de la même unité ; les
 * règles jugent l'échange dans les deux sens. Un refus s'affiche **avec le nom de la personne
 * concernée** (« Pour Marc : repos de 4 h, il en faut 8 »). Les échanges reçus s'acceptent ou se
 * refusent ; ceux qu'on a proposés s'annulent tant qu'ils ne sont pas appliqués.
 */
import { useCallback, useEffect, useState } from "react";
import { BadgeStatut, EnTeteEcran, EtatVide, Message, Tableau, type Colonne } from "./composants";
import { heure, useApiEffectifs } from "./effectifs/api";
import Motifs from "./effectifs/disponibilites/Motifs";
import { LIBELLE_STATUT, TON_STATUT, type Echange, type Quart, type QuartEchangeable } from "./effectifs/echanges/types";
import type { Motif, Ressource } from "./effectifs/planification/types";
import "./effectifs/effectifs.css";
import PageMaquette from "./composants/PageMaquette";

const quart = (q: Quart) => `${q.date} · ${heure(q.debut)}–${heure(q.fin)}`;

const COLONNES: ReadonlyArray<Colonne<Echange>> = [
  { id: "sens", libelle: "Échange" },
  { id: "cede", libelle: "Quart cédé", tri: (x) => x.quartCede.date },
  { id: "voulu", libelle: "Quart reçu en retour", tri: (x) => x.quartVoulu.date },
  { id: "statut", libelle: "Statut", tri: (x) => x.statut },
  { id: "motifs", libelle: "Ce que les règles ont relevé" },
  { id: "actions", libelle: "Actions", actions: true },
];

export default function MesEchanges() {
  const { lire, envoyer, envoyerBrut, erreur, setErreur } = useApiEffectifs();
  const [moi, setMoi] = useState<Ressource | null | undefined>(undefined);
  const [mesQuarts, setMesQuarts] = useState<QuartEchangeable[]>([]);
  const [demandables, setDemandables] = useState<QuartEchangeable[]>([]);
  const [cede, setCede] = useState("");
  const [voulu, setVoulu] = useState("");
  const [echanges, setEchanges] = useState<Echange[]>([]);
  const [refus, setRefus] = useState<Motif[]>([]);
  const [info, setInfo] = useState("");

  const charger = useCallback(async () => {
    const m = await lire<{ ressource: Ressource | null; quarts: QuartEchangeable[] }>("/api/effectifs/echanges/moi");
    setMoi(m?.ressource ?? null);
    setMesQuarts(m?.quarts ?? []);
    setEchanges((await lire<Echange[]>("/api/effectifs/echanges")) ?? []);
  }, [lire]);
  useEffect(() => { void charger(); }, [charger]);

  useEffect(() => {
    setVoulu(""); setDemandables([]);
    if (cede) void lire<QuartEchangeable[]>(`/api/effectifs/echanges/places/${cede}/demandables`).then((d) => setDemandables(d ?? []));
  }, [cede, lire]);

  const proposer = async () => {
    setInfo(""); setRefus([]);
    const r = await envoyerBrut("/api/effectifs/echanges", { placeCedeeId: cede, placeVoulueId: voulu });
    if (r.statut === 409 && r.corps.verdict) { setErreur(""); setRefus(r.corps.verdict.motifs ?? []); return; }
    if (!r.ok) return;
    const x = r.corps as Echange;
    setInfo(`Échange proposé à ${x.nomB}.${x.actionRegles === "EXIGER_APPROBATION" ? " Une fois accepté, il devra être validé par le chef." : ""}`);
    setCede(""); await charger();
  };
  const geste = async (x: Echange, g: "accepter" | "refuser" | "annuler") => {
    setInfo(""); setRefus([]);
    const r = await envoyer<Echange>(`/api/effectifs/echanges/${x.id}/${g}`, {});
    if (!r) return;
    setInfo(`Échange ${LIBELLE_STATUT[r.statut].toLowerCase()}.`);
    await charger();
  };

  if (moi === undefined) return null;
  const monId = moi?.id ?? null;

  return (
    <PageMaquette module="Urgences-santé" contexte="Mon espace • échanges de quarts">
      <EnTeteEcran titre="Mes échanges" description="Proposez l'échange d'un de vos quarts publiés, et répondez à ceux qu'on vous propose." testId="mec-titre" />
      {moi === null && <EtatVide titre="Votre compte n'est relié à aucune ressource planifiable" testId="mec-sans-ressource">Demandez à votre planificateur de relier votre compte à votre dossier d'effectif.</EtatVide>}
      {erreur && <Message variante="erreur" testId="mec-erreur">{erreur}</Message>}
      <Motifs motifs={refus} refus titre="Échange refusé par les règles : voici pourquoi" testId="mec-refus" />
      {info && <Message variante="succes" testId="mec-info">{info}</Message>}

      {moi !== null && (
        <section aria-labelledby="mec-proposer-titre">
          <h2 id="mec-proposer-titre">Proposer un échange</h2>
          {mesQuarts.length === 0 ? <p data-testid="mec-aucun-quart">Aucun quart publié à venir à échanger.</p> : (
            <div className="eff-barre">
              <p>
                <label htmlFor="mec-cede">Mon quart</label>
                <select id="mec-cede" data-testid="mec-cede" value={cede} onChange={(e) => setCede(e.target.value)}>
                  <option value="">— Choisir —</option>
                  {mesQuarts.map((q) => <option key={q.placeId} value={q.placeId}>{quart(q)}</option>)}
                </select>
              </p>
              <p>
                <label htmlFor="mec-voulu">Contre le quart de</label>
                <select id="mec-voulu" data-testid="mec-voulu" value={voulu} disabled={!cede} onChange={(e) => setVoulu(e.target.value)}>
                  <option value="">{cede && demandables.length === 0 ? "Aucun quart admissible" : "— Choisir —"}</option>
                  {demandables.map((q) => <option key={q.placeId} value={q.placeId}>{q.nom} — {quart(q)}</option>)}
                </select>
              </p>
              <p className="eff-actions">
                <button type="button" data-testid="mec-proposer" disabled={!cede || !voulu} onClick={() => void proposer()}>Proposer l'échange</button>
              </p>
            </div>
          )}
        </section>
      )}

      <section aria-labelledby="mec-liste-titre">
        <h2 id="mec-liste-titre">Échanges qui me concernent</h2>
        <Tableau colonnes={COLONNES} lignes={echanges} legende="Échanges qui me concernent" testId="mec-table"
          vide={<EtatVide titre="Aucun échange" compact testId="mec-vide">Vos propositions et celles qu'on vous fait apparaîtront ici.</EtatVide>}
          rendre={(x) => {
            const jeRecois = x.ressourceBId === monId;
            return (
              <tr key={x.id} data-testid={`mec-${x.id}`}>
                <td>{jeRecois ? `Proposé par ${x.nomA}` : `Proposé à ${x.nomB}`}</td>
                <td>{quart(x.quartCede)}</td>
                <td>{quart(x.quartVoulu)}</td>
                <td><BadgeStatut statut={TON_STATUT[x.statut]} testId={`mec-${x.id}-statut`}>{LIBELLE_STATUT[x.statut]}</BadgeStatut></td>
                <td>{x.motifsRegles.length ? x.motifsRegles.map((m) => m.message).join(" ; ") : "—"}</td>
                <td className="eff-actions">
                  {jeRecois && x.statut === "PROPOSE" && (
                    <>
                      <button type="button" data-testid={`mec-${x.id}-accepter`} onClick={() => void geste(x, "accepter")}>Accepter<span className="sr-only"> l'échange proposé par {x.nomA}</span></button>
                      <button type="button" className="secondary" data-testid={`mec-${x.id}-refuser`} onClick={() => void geste(x, "refuser")}>Refuser<span className="sr-only"> l'échange proposé par {x.nomA}</span></button>
                    </>
                  )}
                  {!jeRecois && (x.statut === "PROPOSE" || x.statut === "A_VALIDER") && (
                    <button type="button" className="secondary" data-testid={`mec-${x.id}-annuler`} onClick={() => void geste(x, "annuler")}>Annuler<span className="sr-only"> l'échange proposé à {x.nomB}</span></button>
                  )}
                </td>
              </tr>
            );
          }} />
      </section>
    </PageMaquette>
  );
}
