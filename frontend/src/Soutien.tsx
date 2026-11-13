/**
 * **Soutien** — le système de billets de l'AO 2026-PLAT-GA (exigence 30), en français (27), avec
 * les délais de résolution par niveau écrits à l'écran (28) et l'état du délai calculé par le
 * serveur. Chacun voit ses billets ; le soutien (superadmin) les traite depuis l'Exploitation.
 */
import { useCallback, useEffect, useState } from "react";
import { FormulaireModal } from "./Modal";
import { BadgeStatut, EnTeteEcran, EtatVide, Message, Onglets, type Onglet } from "./composants";
import { useOnglet } from "./navigation";

const VUES = ["billets", "niveaux"] as const;
type VueSoutien = (typeof VUES)[number];
const estVue = (s: string): s is VueSoutien => (VUES as readonly string[]).includes(s);
import { useAuth } from "./AuthContext";
import { ChampPieces, LIMITES_PAR_DEFAUT, ListePieces, Piece, PieceLocale, decrireNavigateur, LimitesPieces } from "./SoutienPieces";

export interface Politique {
  langue: string;
  niveaux: Array<{ code: string; description: string; delaiResolutionMinutes: number;
    delaiReponseEnHeuresMinutes?: number | null; delaiReponseHorsHeuresMinutes?: number | null }>;
  objectifDisponibilitePourcent: number;
  /** La politique de l'organisme : « standard », ou celle d'un contrat (« service-essentiel »). */
  code?: string; libelle?: string;
  heuresDeSoutien?: { debut: string; fin: string; fuseau: string } | null;
  preavisMaintenanceHeures: number;
  categories?: Array<{ code: string; libelle: string }>;
  impacts?: Array<{ code: string; libelle: string }>;
  pieces?: LimitesPieces;
}
export interface Ticket {
  id: string; numero: number | null; sujet: string; description: string; niveau: string; categorie: string; impact: string;
  ecran: string | null; navigateur: string | null; statut: string; ouvertLe: string; echeanceLe: string;
  resoluLe: string | null; auteurId: string; auteurNom: string; tenantId: string | null;
  satisfaction: number | null; satisfactionCommentaire: string | null; satisfactionLe: string | null;
  sla: { etat: string; ecartMinutes: number };
  /** Le délai de réponse (retour d'appel), quand la politique de l'organisme en promet un. */
  reponse?: SuiviReponse | null;
  messages: Array<{ id: string; auteurNom: string; texte: string; interne: boolean; le: string }>;
  pieces: Piece[];
}

export const LIBELLE_CATEGORIE: Record<string, string> = {
  incident: "Quelque chose ne fonctionne pas", question: "Question", demande: "Demande d'amélioration", acces: "Accès ou compte", donnees: "Donnée fausse ou manquante", autre: "Autre",
};
export const LIBELLE_IMPACT: Record<string, string> = { moi: "moi seulement", equipe: "mon équipe", etablissement: "tout l'organisme" };

/** Le titre d'un billet : son numéro d'abord, parce que c'est lui qu'on cite au téléphone. */
export const titreBillet = (t: Pick<Ticket, "numero" | "sujet">) => (t.numero ? `#${t.numero} — ${t.sujet}` : t.sujet);

/**
 * La satisfaction, une fois le billet résolu : cinq boutons radio, un mot, et c'est tout. Une
 * note déjà donnée se relit ; la donner à nouveau remplace la première.
 */
export function Satisfaction({ ticket, envoyer }: { ticket: Ticket; envoyer: (chemin: string, corps: unknown, quoi: string) => Promise<boolean> }) {
  const [note, setNote] = useState<number>(ticket.satisfaction ?? 0);
  const [commentaire, setCommentaire] = useState(ticket.satisfactionCommentaire ?? "");
  const id = `satisfaction-${ticket.id}`;
  return (
    <form className="satisfaction" data-testid={`soutien-satisfaction-${ticket.id}`} aria-labelledby={`${id}-titre`}
          onSubmit={(e) => { e.preventDefault(); void envoyer(`/api/soutien/tickets/${ticket.id}/satisfaction`, { note, commentaire }, "Merci, votre avis est noté"); }}>
      <p id={`${id}-titre`}><strong>Comment cela s'est-il passé ?</strong>{ticket.satisfaction ? <span className="muted"> (votre note : {ticket.satisfaction}/5)</span> : null}</p>
      <div role="radiogroup" aria-labelledby={`${id}-titre`} className="satisfaction-notes">
        {[1, 2, 3, 4, 5].map((n) => (
          <label key={n} className="satisfaction-note">
            <input type="radio" name={id} value={n} checked={note === n} onChange={() => setNote(n)} data-testid={`${id}-${n}`} />
            <span>{n}</span>
          </label>
        ))}
        <span className="muted">1 = insatisfait, 5 = très satisfait</span>
      </div>
      <label className="field"><span>Un mot (facultatif)</span>
        <input value={commentaire} maxLength={1000} onChange={(e) => setCommentaire(e.target.value)} data-testid={`${id}-commentaire`} /></label>
      <button type="submit" className="secondary" disabled={note === 0} data-testid={`${id}-envoyer`}>Envoyer mon avis</button>
    </form>
  );
}

export const LIBELLE_STATUT: Record<string, string> = { nouveau: "Nouveau", en_cours: "En cours", resolu: "Résolu", ferme: "Fermé" };
export const LIBELLE_SLA: Record<string, string> = {
  dans_les_delais: "dans les délais", en_retard: "en retard", resolu_a_temps: "résolu à temps", resolu_en_retard: "résolu en retard",
};

export interface SuiviReponse { echeanceLe: string; atteintLe: string | null; etat: string; ecartMinutes: number }

/** 30 min, 2 h, 48 h, 7 j : au-delà de 48 heures, le contrat compte en jours. */
export function formaterDelai(minutes: number): string {
  if (minutes > 2880 && minutes % 1440 === 0) return `${minutes / 1440} j`;
  return minutes % 60 === 0 ? `${minutes / 60} h` : `${minutes} min`;
}

/** Le délai de réponse, écrit : « rappel attendu, il reste 20 min », « rappel tardif (15 min) »… */
export function formaterReponse(r: SuiviReponse): string {
  const abs = Math.abs(r.ecartMinutes);
  if (r.etat === "dans_les_delais") return `rappel attendu, il reste ${abs} min`;
  if (r.etat === "en_retard") return `rappel en retard de ${abs} min`;
  if (r.etat === "tenu") return "rappel à temps";
  return `rappel tardif (${abs} min)`;
}

/** L'écart au délai, écrit : « il reste 45 min » ou « dépassé de 2 h ». */
export function formaterEcart(sla: { etat: string; ecartMinutes: number }): string {
  const abs = Math.abs(sla.ecartMinutes);
  const duree = abs >= 60 ? `${Math.floor(abs / 60)} h ${abs % 60 ? `${abs % 60} min` : ""}`.trim() : `${abs} min`;
  if (sla.etat === "dans_les_delais") return `il reste ${duree}`;
  if (sla.etat === "en_retard") return `dépassé de ${duree}`;
  return LIBELLE_SLA[sla.etat] ?? sla.etat;
}

export const dateLisible = (iso: string) => new Date(iso).toLocaleString("fr-CA", { dateStyle: "medium", timeStyle: "short" });

export default function Soutien() {
  const { apiFetch, user } = useAuth();
  const [politique, setPolitique] = useState<Politique | null>(null);
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [erreur, setErreur] = useState("");
  const [vue, setVue] = useOnglet<VueSoutien>("soutien", "billets", estVue);
  const [succes, setSucces] = useState("");
  const [ouvert, setOuvert] = useState<string | null>(null);
  const [reponse, setReponse] = useState("");
  const [piecesNouveau, setPiecesNouveau] = useState<PieceLocale[]>([]);
  const [piecesReponse, setPiecesReponse] = useState<PieceLocale[]>([]);
  const [filtre, setFiltre] = useState<"ouverts" | "tous">("ouverts");
  const limites = politique?.pieces ?? LIMITES_PAR_DEFAUT;
  // L'écran d'où l'on vient : la page ouverte avant d'arriver au soutien, pas le soutien lui-même.
  const [ecranOrigine] = useState(() => (typeof document !== "undefined" && document.referrer && !/\/soutien/.test(document.referrer) ? document.referrer : ""));

  const charger = useCallback(async () => {
    const [p, t] = await Promise.all([apiFetch("/api/soutien/politique"), apiFetch("/api/soutien/tickets")]);
    if (p.ok) setPolitique(await p.json());
    if (t.ok) setTickets(await t.json());
  }, [apiFetch]);
  useEffect(() => { charger().catch(() => setErreur("Impossible de charger le soutien.")); }, [charger]);

  async function envoyer(chemin: string, corps: unknown, quoi: string): Promise<boolean> {
    setErreur(""); setSucces("");
    const r = await apiFetch(chemin, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(corps) });
    if (!r.ok) { const d = await r.json().catch(() => ({})); setErreur(`${quoi} : ${(d.errors ?? ["refus du serveur"]).join(" ; ")}`); return false; }
    setSucces(`${quoi} : fait.`);
    await charger();
    return true;
  }

  const ouverts = tickets.filter((t) => t.statut !== "ferme").length;
  const onglets: Onglet<VueSoutien>[] = [
    { id: "billets", libelle: "Mes billets", testId: "soutien-tab-billets", icone: "document", compteur: ouverts },
    { id: "niveaux", libelle: "Niveaux et délais", testId: "soutien-tab-niveaux", icone: "horloge" },
  ];

  return (
    <div data-testid="soutien-page">
      <EnTeteEcran titre="Soutien" testId="soutien-title" icone="aide"
                   description={<>Le soutien est offert en français. Chaque billet reçoit un niveau et une échéance de résolution ; l'écran dit s'il est
                     dans les délais. Disponibilité visée : {(politique?.objectifDisponibilitePourcent ?? 99.5).toLocaleString("fr-CA")} % hors maintenance planifiée,
                     annoncée {politique?.preavisMaintenanceHeures ?? 72} h à l'avance.</>}
                   actions={<FormulaireModal libelle="Ouvrir un billet" testId="soutien-billet">{(fermer) => (
          <form className="grid-form" data-testid="soutien-formulaire" onSubmit={async (e) => {
          e.preventDefault();
          const f = e.currentTarget; const d = new FormData(f);
          const ok = await envoyer("/api/soutien/tickets", {
            sujet: String(d.get("sujet") ?? ""), description: String(d.get("description") ?? ""), niveau: String(d.get("niveau") ?? "N3"),
            categorie: String(d.get("categorie") ?? "incident"), impact: String(d.get("impact") ?? "moi"),
            ecran: String(d.get("ecran") ?? ""), navigateur: decrireNavigateur(),
            pieces: piecesNouveau.map((p) => ({ nom: p.nom, typeMime: p.typeMime, contenu: p.contenu })),
          }, "Billet ouvert");
          if (ok) { f.reset(); setPiecesNouveau([]); fermer(); }
        }}>
          <label className="field"><span>Sujet <span className="req-star" aria-hidden="true">*</span></span>
            <input name="sujet" required minLength={5} maxLength={200} data-testid="soutien-sujet" placeholder="En quelques mots" /></label>
          <label className="field"><span>De quoi s'agit-il ? <span className="req-star" aria-hidden="true">*</span></span>
            <select name="categorie" defaultValue="incident" data-testid="soutien-categorie">
              {(politique?.categories ?? Object.entries(LIBELLE_CATEGORIE).map(([code, libelle]) => ({ code, libelle }))).map((c) => <option key={c.code} value={c.code}>{c.libelle}</option>)}
            </select></label>
          <label className="field"><span>Niveau <span className="req-star" aria-hidden="true">*</span></span>
            <select name="niveau" defaultValue="N3" data-testid="soutien-niveau" aria-describedby="soutien-niveau-aide">
              {(politique?.niveaux ?? []).map((n) => <option key={n.code} value={n.code}>{n.code} — {n.description}</option>)}
            </select>
            <span className="aide" id="soutien-niveau-aide">Le niveau fixe le délai de résolution engagé ; le soutien peut le corriger.</span></label>
          <label className="field"><span>Qui est touché ? <span className="req-star" aria-hidden="true">*</span></span>
            <select name="impact" defaultValue="moi" data-testid="soutien-impact">
              {(politique?.impacts ?? Object.entries(LIBELLE_IMPACT).map(([code, libelle]) => ({ code, libelle }))).map((i) => <option key={i.code} value={i.code}>{i.libelle}</option>)}
            </select></label>
          <label className="field field-full"><span>Description <span className="req-star" aria-hidden="true">*</span></span>
            <textarea name="description" required minLength={10} maxLength={5000} rows={5} data-testid="soutien-description" aria-describedby="soutien-description-aide"
                      placeholder={"Ce que j'ai fait :\nCe qui s'est passé (message d'erreur, texte exact) :\nCe que j'attendais :\nDepuis quand :"} />
            <span className="aide" id="soutien-description-aide">Le message d'erreur exact et les étapes pour reproduire évitent un aller-retour.</span></label>
          <label className="field field-full"><span>Écran concerné</span>
            <input name="ecran" defaultValue={ecranOrigine} maxLength={500} data-testid="soutien-ecran" aria-describedby="soutien-ecran-aide" placeholder="Adresse ou nom de la page" />
            <span className="aide" id="soutien-ecran-aide">Rempli avec la page d'où vous venez ; le navigateur et l'appareil sont relevés d'office ({decrireNavigateur()}).</span></label>
          <ChampPieces pieces={piecesNouveau} onChange={setPiecesNouveau} limites={limites} testId="soutien-pieces" />
          <div className="field field-actions"><button type="submit" className="primary" data-testid="soutien-ouvrir">Ouvrir le billet</button></div>
        </form>
        )}</FormulaireModal>} />
      {erreur && <Message variante="erreur" testId="soutien-erreur" onFermer={() => setErreur("")}>{erreur}</Message>}
      {succes && <Message variante="succes" testId="soutien-succes" onFermer={() => setSucces("")}>{succes}</Message>}

      <Onglets onglets={onglets} actif={vue} onChange={setVue} libelle="Soutien">
      {vue === "niveaux" && (
      <section className="card" aria-labelledby="soutien-niveaux-titre">
        <h2 id="soutien-niveaux-titre">Niveaux et délais</h2>
        {politique?.heuresDeSoutien
          ? <p data-testid="soutien-heures">Heures de soutien : de {politique.heuresDeSoutien.debut} à {politique.heuresDeSoutien.fin}, 7 jours sur 7 (heure du Québec).
              Le délai de réponse court jusqu'au rappel d'un technicien ; le délai de résolution court ensuite, depuis ce rappel.</p>
          : <p className="aide">Le délai de résolution court depuis l'envoi du billet.</p>}
        <div className="table-wrap">
          <table data-testid="soutien-niveaux">
            <caption className="sr-only">Niveaux de billet et délais engagés</caption>
            <thead><tr><th scope="col">Niveau</th><th scope="col">Situation</th>
              {politique?.heuresDeSoutien && <th scope="col">Réponse (pendant / hors heures de soutien)</th>}
              <th scope="col">Résolution</th></tr></thead>
            <tbody>{(politique?.niveaux ?? []).map((n) => (
              <tr key={n.code}><th scope="row">{n.code}</th><td>{n.description}</td>
                {politique?.heuresDeSoutien && <td>{formaterDelai(n.delaiReponseEnHeuresMinutes ?? 0)} / {formaterDelai(n.delaiReponseHorsHeuresMinutes ?? 0)}</td>}
                <td>{formaterDelai(n.delaiResolutionMinutes)}</td></tr>
            ))}</tbody>
          </table>
        </div>
      </section>
      )}

      {vue === "billets" && (
      <section className="card" aria-labelledby="soutien-liste-titre">
        <h2 id="soutien-liste-titre">Mes billets</h2>
        <p className="aide">Un bon billet dit ce qui s'est passé, ce qui était attendu, et montre l'écran : le soutien commence sans avoir à vous rappeler.</p>
        <div className="tabs tabs-secondaires" role="group" aria-label="Filtrer mes billets">
          <button type="button" aria-pressed={filtre === "ouverts"} onClick={() => setFiltre("ouverts")} data-testid="soutien-filtre-ouverts">Ouverts ({ouverts})</button>
          <button type="button" aria-pressed={filtre === "tous"} onClick={() => setFiltre("tous")} data-testid="soutien-filtre-tous">Tous ({tickets.length})</button>
        </div>
        {tickets.length === 0 && <EtatVide titre="Aucun billet" testId="soutien-vide" icone="document" compact>Quand quelque chose ne va pas, « Ouvrir un billet » en haut de l'écran.</EtatVide>}
        {tickets.filter((t) => filtre === "tous" || t.statut !== "ferme").map((t) => (
          <article key={t.id} className="bloc" data-testid={`soutien-billet-${t.id}`}>
            <h3>{titreBillet(t)} — <BadgeStatut statut={t.statut === "ferme" ? "closed" : t.statut === "nouveau" ? "ouvert" : t.statut} testId={`soutien-statut-${t.id}`}>{LIBELLE_STATUT[t.statut] ?? t.statut}</BadgeStatut> · {t.niveau}</h3>
            {/* L'état du délai est écrit, jamais seulement coloré. */}
            <p className="muted">Ouvert le {dateLisible(t.ouvertLe)} · échéance {dateLisible(t.echeanceLe)} ·{" "}
              <span data-testid={`soutien-sla-${t.id}`}>{formaterEcart(t.sla)}</span>
              {t.reponse ? <> · <span data-testid={`soutien-rappel-${t.id}`}>{formaterReponse(t.reponse)}</span></> : null}
              {" · "}{LIBELLE_CATEGORIE[t.categorie] ?? t.categorie} · touche {LIBELLE_IMPACT[t.impact] ?? t.impact}
              {t.ecran ? <> · écran : <span data-testid={`soutien-ecran-${t.id}`}>{t.ecran}</span></> : null}</p>
            <p>{t.description}</p>
            <ListePieces ticketId={t.id} pieces={t.pieces ?? []} messageId={null} testId={`soutien-pieces-${t.id}`} signaler={setErreur} />
            <ul>{t.messages.map((m) => (
              <li key={m.id}><strong>{m.auteurNom}</strong> ({dateLisible(m.le)}) : {m.texte}
                <ListePieces ticketId={t.id} pieces={t.pieces ?? []} messageId={m.id} testId={`soutien-pieces-${m.id}`} signaler={setErreur} /></li>
            ))}</ul>
            {t.statut !== "ferme" && (
              <div className="row-form" style={{ gap: 8, alignItems: "flex-end", flexWrap: "wrap" }}>
                <label className="field" style={{ flex: 1, minWidth: 220 }}><span>Répondre</span>
                  <input value={ouvert === t.id ? reponse : ""} onChange={(e) => { if (ouvert !== t.id) setPiecesReponse([]); setOuvert(t.id); setReponse(e.target.value); }} data-testid={`soutien-reponse-${t.id}`} /></label>
                <button type="button" className="secondary" data-testid={`soutien-envoyer-${t.id}`}
                        onClick={async () => {
                          const pieces = ouvert === t.id ? piecesReponse : [];
                          if (await envoyer(`/api/soutien/tickets/${t.id}/messages`, { texte: ouvert === t.id ? reponse : "", pieces: pieces.map((p) => ({ nom: p.nom, typeMime: p.typeMime, contenu: p.contenu })) }, "Message envoyé")) { setReponse(""); setPiecesReponse([]); }
                        }}>
                  Envoyer
                </button>
                {t.statut === "resolu" && (
                  <button type="button" className="secondary" data-testid={`soutien-fermer-${t.id}`}
                          onClick={() => void envoyer(`/api/soutien/tickets/${t.id}/statut`, { statut: "ferme" }, "Billet fermé")}>
                    Confirmer et fermer
                  </button>
                )}
                {ouvert === t.id && <ChampPieces pieces={piecesReponse} onChange={setPiecesReponse} limites={limites} testId={`soutien-reponse-pieces-${t.id}`} />}
              </div>
            )}
            {(t.statut === "resolu" || t.statut === "ferme") && user && t.auteurId === user.id && (
              <Satisfaction key={`${t.id}-${t.satisfactionLe ?? ""}`} ticket={t} envoyer={envoyer} />
            )}
          </article>
        ))}
      </section>
      )}
      </Onglets>
    </div>
  );
}
