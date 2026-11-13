/**
 * **Intégrations** — onglet du paramétrage des effectifs (AO 26-015-DO, lot R10 — E-TN-7, P-TN-7,
 * E-RF-12, E-RF-13).
 *
 * L'administration choisit, par connecteur, le simulateur, l'adaptateur HTTP (adresse https,
 * **nom** de la variable qui porte le jeton — jamais le jeton) ou l'échange de fichiers de lot par
 * SFTP (compte, hôte, répertoire, empreinte de l'hôte ; **nom** de la variable qui porte la clé). L'encadrement déclenche les envois
 * (lot de paie, équipages, calendriers, relais du journal) et les tirages (horodatages, anciennetés),
 * suit la boîte d'envoi et rejoue un message rejeté ou en échec.
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Badge, BadgeStatut, EtatVide, Message, Tableau, Tiroir, type Colonne } from "../composants";
import { useAuth } from "../AuthContext";

const LIBELLE_MODE: Record<string, string> = { SIMULATEUR: "simulateur", HTTP: "HTTP", SFTP: "SFTP" };
import { liste, objet } from "../formeRecue";
import { useApiEffectifs } from "./api";
import { useReferentiels } from "./planification/useReferentiels";

interface Connecteur { code: string; libelle: string; sens: "SORTANT" | "ENTRANT"; mode: "SIMULATEUR" | "HTTP" | "SFTP"; url: string | null; jetonRef: string | null; actif: boolean; enAttente: number; enEchec: number; curseur: string | null }
interface MessageSortant { id: string; connecteur: string; type: string; etat: "EN_ATTENTE" | "ENVOYE" | "REJETE" | "ECHEC"; tentatives: number; refExterne: string | null; motif: string | null; deposeLe: string }
interface Ressource { id: string; matricule: string; nom: string; actif: boolean }

const LIBELLE_ETAT: Record<MessageSortant["etat"], string> = { EN_ATTENTE: "En attente", ENVOYE: "Envoyé", REJETE: "Rejeté", ECHEC: "En échec" };
const TON_ETAT: Record<MessageSortant["etat"], string> = { EN_ATTENTE: "en_attente", ENVOYE: "approuvee", REJETE: "refusee", ECHEC: "echec" };
const quand = (iso: string) => new Date(iso).toLocaleString("fr-CA", { dateStyle: "short", timeStyle: "short" });

const COLONNES_CONNECTEURS: ReadonlyArray<Colonne<Connecteur>> = [
  { id: "libelle", libelle: "Connecteur", tri: (c) => c.code }, { id: "mode", libelle: "Mode", tri: (c) => c.mode },
  { id: "actif", libelle: "État" }, { id: "file", libelle: "Boîte d'envoi" }, { id: "actions", libelle: "Actions", actions: true },
];
const COLONNES_MESSAGES: ReadonlyArray<Colonne<MessageSortant>> = [
  { id: "depose", libelle: "Déposé", tri: (m) => m.deposeLe }, { id: "connecteur", libelle: "Connecteur", tri: (m) => m.connecteur },
  { id: "etat", libelle: "État", tri: (m) => m.etat }, { id: "detail", libelle: "Détail" }, { id: "actions", libelle: "Actions", actions: true },
];

export default function Integrations() {
  const { user } = useAuth();
  const administrateur = user?.role === "admin" || user?.role === "superadmin";
  const { lire, envoyer, erreur, setErreur } = useApiEffectifs();
  const { refs } = useReferentiels(["unites"]);
  const [connecteurs, setConnecteurs] = useState<Connecteur[]>([]);
  const [messages, setMessages] = useState<MessageSortant[]>([]);
  const [filtre, setFiltre] = useState<"" | MessageSortant["etat"]>("");
  const [ressources, setRessources] = useState<Ressource[]>([]);
  const [edition, setEdition] = useState<Connecteur | null>(null);
  const [config, setConfig] = useState({ mode: "SIMULATEUR", url: "", jetonRef: "", actif: false });
  const [geste, setGeste] = useState({ debut: "", date: "", uniteCode: "", ressourceId: "" });
  const [bilan, setBilan] = useState("");

  const charger = useCallback(async () => {
    setConnecteurs(liste<Connecteur>(await lire("/api/effectifs/integrations/connecteurs")));
    setMessages(liste<MessageSortant>(await lire(`/api/effectifs/integrations/messages${filtre ? `?etat=${filtre}` : ""}`)));
  }, [lire, filtre]);
  useEffect(() => { void charger(); }, [charger]);
  useEffect(() => { void lire("/api/effectifs/planification/ressources").then((r) => setRessources(liste<Ressource>(r).filter((x) => x.actif))); }, [lire]);

  const actif = (code: string) => connecteurs.find((c) => c.code === code)?.actif ?? false;

  const enregistrer = async (e: FormEvent) => {
    e.preventDefault();
    if (!edition) return;
    const r = objet<Connecteur>(await envoyer(`/api/effectifs/integrations/connecteurs/${edition.code}`,
      { mode: config.mode, url: config.url || null, jetonRef: config.jetonRef || null, actif: config.actif }, "PUT"));
    if (!r) return;
    setBilan(`${r.code} : ${r.actif ? "activé" : "désactivé"}, mode ${LIBELLE_MODE[r.mode] ?? r.mode}.`);
    setEdition(null);
    await charger();
  };

  /** Un geste d'envoi ou de tirage : le serveur dit ce qu'il a fait, l'écran le répète. */
  const agir = async (chemin: string, corps: Record<string, unknown>, dire: (r: Record<string, unknown>) => string) => {
    setBilan("");
    const r = objet<Record<string, unknown>>(await envoyer(chemin, corps));
    if (r) { setBilan(dire(r)); await charger(); }
  };

  /** Le journal relayé au bus (événements) ou à l'entrepôt (versements), à partir du curseur du connecteur. */
  const relayer = (code: "ESB" | "ENTREPOT") => void agir(`/api/effectifs/integrations/relais/${code}`, {},
    (r) => (code === "ESB" ? `Bus : ${r.deposes} événement(s) déposé(s).` : `Entrepôt : ${r.deposes} versement(s) déposé(s).`));

  const rejouer = async (m: MessageSortant) => {
    setBilan("");
    if (objet(await envoyer(`/api/effectifs/integrations/messages/${m.id}/rejouer`, {}))) { setBilan(`Message ${m.connecteur} remis en file de livraison.`); await charger(); }
  };

  const champ = (cle: keyof typeof geste) => ({ value: geste[cle], onChange: (e: { target: { value: string } }) => setGeste((g) => ({ ...g, [cle]: e.target.value })) });

  return (
    <div data-testid="int-ecran">
      <p>Un connecteur coupé ne reçoit ni n'envoie rien. En mode simulateur, les échanges restent dans l'application : c'est le mode d'essai et de démonstration.</p>
      {erreur && !edition && <Message variante="erreur" testId="int-erreur">{erreur}</Message>}
      {bilan && <Message variante="succes" testId="int-bilan">{bilan}</Message>}

      <Tableau colonnes={COLONNES_CONNECTEURS} lignes={connecteurs} legende="Connecteurs de l'organisme" testId="int-connecteurs"
        vide={<EtatVide titre="Aucun connecteur" compact testId="int-connecteurs-vide">Le module d'intégrations n'est pas disponible.</EtatVide>}
        rendre={(c) => (
          <tr key={c.code} data-testid={`int-c-${c.code}`}>
            <td>{c.libelle} <span className="muted">({c.sens === "SORTANT" ? "sortant" : "entrant"})</span></td>
            <td>{c.mode === "SIMULATEUR" ? "Simulateur" : `${LIBELLE_MODE[c.mode]} — ${c.url}`}</td>
            <td>{c.actif ? <Badge ton="succes">Activé</Badge> : <Badge ton="neutre">Coupé</Badge>}</td>
            <td>{c.sens === "SORTANT" ? `${c.enAttente} en attente, ${c.enEchec} à reprendre` : "—"}</td>
            <td>
              {administrateur && (
                <button type="button" className="secondary" data-testid={`int-c-${c.code}-configurer`} onClick={() => {
                  setErreur(""); setEdition(c); setConfig({ mode: c.mode, url: c.url ?? "", jetonRef: c.jetonRef ?? "", actif: c.actif });
                }}>Configurer<span className="sr-only"> {c.code}</span></button>
              )}
            </td>
          </tr>
        )} />

      <details className="eff-groupe" data-testid="int-gestes">
        <summary>Envois et tirages</summary>
        <div className="eff-barre">
          <p><label htmlFor="int-debut">Période de paie (début)</label><input id="int-debut" type="date" data-testid="int-debut" {...champ("debut")} /></p>
          <p><button type="button" data-testid="int-paie" disabled={!actif("PFT") || !geste.debut}
            onClick={() => void agir("/api/effectifs/integrations/paie", { debut: geste.debut }, () => "Lot de paie déposé pour le PFT.")}>Transmettre le lot de paie</button></p>
        </div>
        <div className="eff-barre">
          <p><label htmlFor="int-date">Date des équipages</label><input id="int-date" type="date" data-testid="int-date" {...champ("date")} /></p>
          <p>
            <label htmlFor="int-unite">Unité</label>
            <select id="int-unite" data-testid="int-unite" {...champ("uniteCode")}>
              <option value="">— choisir —</option>
              {(refs.unites ?? []).map((u) => <option key={u.code} value={u.code}>{u.code} — {u.libelleFr}</option>)}
            </select>
          </p>
          <p><button type="button" data-testid="int-equipages" disabled={!actif("SAAV") || !geste.date || !geste.uniteCode}
            onClick={() => void agir("/api/effectifs/integrations/equipages", { date: geste.date, uniteCode: geste.uniteCode }, () => "Équipages déposés pour le SAAV.")}>Publier les équipages</button></p>
          <p><button type="button" className="secondary" data-testid="int-anciennetes" disabled={!actif("BD_CORPO") || !geste.uniteCode}
            onClick={() => void agir(`/api/effectifs/integrations/anciennetes/${geste.uniteCode}`, {}, (r) => `BD Corpo : ${r.lus} dossier(s) lu(s), ${r.modifiees} ancienneté(s) mise(s) à jour, ${r.ignorees} ignorée(s).`)}>Synchroniser les anciennetés</button></p>
        </div>
        <div className="eff-barre">
          <p>
            <label htmlFor="int-ressource">Personne</label>
            <select id="int-ressource" data-testid="int-ressource" {...champ("ressourceId")}>
              <option value="">— choisir —</option>
              {ressources.map((r) => <option key={r.id} value={r.id}>{r.nom} ({r.matricule})</option>)}
            </select>
          </p>
          <p><button type="button" data-testid="int-calendrier" disabled={!actif("GRAPH") || !geste.ressourceId}
            onClick={() => void agir(`/api/effectifs/integrations/calendriers/${geste.ressourceId}`, {}, (r) => `${r.deposes} quart(s) déposé(s) pour le calendrier.`)}>Publier ses quarts au calendrier</button></p>
        </div>
        <p className="eff-actions">
          <button type="button" className="secondary" data-testid="int-relais-esb" disabled={!actif("ESB")}
            onClick={() => relayer("ESB")}>Relayer le journal au bus</button>
          <button type="button" className="secondary" data-testid="int-relais-entrepot" disabled={!actif("ENTREPOT")}
            onClick={() => relayer("ENTREPOT")}>Verser le journal à l'entrepôt</button>
          <button type="button" className="secondary" data-testid="int-rao" disabled={!actif("RAO")}
            onClick={() => void agir("/api/effectifs/integrations/horodatages/tirer", {}, (r) => `RAO : ${r.lus} horodatage(s) lu(s), ${r.importes} importé(s), ${r.rejets} rejeté(s).`)}>Tirer les horodatages du RAO</button>
        </p>
      </details>

      <div className="eff-barre">
        <p>
          <label htmlFor="int-filtre">État des messages</label>
          <select id="int-filtre" data-testid="int-filtre" value={filtre} onChange={(e) => setFiltre(e.target.value as "" | MessageSortant["etat"])}>
            <option value="">Tous</option>
            {(Object.keys(LIBELLE_ETAT) as Array<MessageSortant["etat"]>).map((x) => <option key={x} value={x}>{LIBELLE_ETAT[x]}</option>)}
          </select>
        </p>
      </div>
      <Tableau colonnes={COLONNES_MESSAGES} lignes={messages} legende="Boîte d'envoi" testId="int-messages"
        vide={<EtatVide titre="Boîte d'envoi vide" compact testId="int-messages-vide">Aucun message ne correspond à cet état.</EtatVide>}
        rendre={(m) => (
          <tr key={m.id} data-testid={`int-m-${m.id}`}>
            <td>{quand(m.deposeLe)}</td>
            <td>{m.connecteur} · {m.type}</td>
            <td><BadgeStatut statut={TON_ETAT[m.etat]}>{LIBELLE_ETAT[m.etat]}</BadgeStatut></td>
            <td>{m.refExterne ?? m.motif ?? "—"}{m.tentatives > 1 ? ` (${m.tentatives} essais)` : ""}</td>
            <td>
              {(m.etat === "REJETE" || m.etat === "ECHEC") && (
                <button type="button" className="secondary" data-testid={`int-m-${m.id}-rejouer`} onClick={() => void rejouer(m)}>Rejouer<span className="sr-only"> le message {m.connecteur}</span></button>
              )}
            </td>
          </tr>
        )} />

      <Tiroir ouvert={!!edition} onFermer={() => setEdition(null)} titre={edition ? `Configurer ${edition.code}` : "Configurer"} testId="int-configuration">
        {edition && (
          <form onSubmit={(e) => void enregistrer(e)}>
            <p>{edition.libelle}</p>
            <p>
              <label htmlFor="int-mode">Mode</label>
              <select id="int-mode" data-testid="int-mode" value={config.mode} onChange={(e) => setConfig((c) => ({ ...c, mode: e.target.value }))}>
                <option value="SIMULATEUR">Simulateur (essai, démonstration)</option>
                <option value="HTTP">HTTP (système réel)</option>
                <option value="SFTP">SFTP (fichiers de lot)</option>
              </select>
            </p>
            {config.mode !== "SIMULATEUR" && (
              <>
                <p>
                  <label htmlFor="int-url">{config.mode === "SFTP" ? "Adresse SFTP" : "Adresse (https)"}</label>
                  <input id="int-url" type={config.mode === "SFTP" ? "text" : "url"} required data-testid="int-url" aria-describedby={config.mode === "SFTP" ? "int-url-aide" : undefined}
                         value={config.url} onChange={(e) => setConfig((c) => ({ ...c, url: e.target.value }))} />
                  {config.mode === "SFTP" && (
                    <span id="int-url-aide" className="aide">sftp://compte@hôte/répertoire?empreinte=SHA256:… — l'empreinte de la clé de l'hôte, telle que ssh-keygen -lf l'écrit,
                      est exigée. Les lots sortants sont déposés dans un sous-répertoire par type ; les lots entrants se cueillent dans horodatages/ et dossiers/.</span>
                  )}
                </p>
                <p>
                  <label htmlFor="int-jeton">{config.mode === "SFTP" ? "Variable d'environnement de la clé privée" : "Variable d'environnement du jeton"}</label>
                  <input id="int-jeton" data-testid="int-jeton" aria-describedby="int-jeton-aide" required={config.mode === "SFTP"}
                         value={config.jetonRef} onChange={(e) => setConfig((c) => ({ ...c, jetonRef: e.target.value.toUpperCase() }))} />
                  <span id="int-jeton-aide" className="aide">Le NOM de la variable posée sur le serveur (ex. {config.mode === "SFTP" ? "ESB_CLE_SFTP" : "PFT_JETON"}), jamais le secret lui-même.</span>
                </p>
              </>
            )}
            <p className="eff-case">
              <input id="int-actif" type="checkbox" data-testid="int-actif" checked={config.actif} onChange={(e) => setConfig((c) => ({ ...c, actif: e.target.checked }))} />
              <label htmlFor="int-actif">Connecteur activé</label>
            </p>
            {erreur && <Message variante="erreur" testId="int-configuration-erreur">{erreur}</Message>}
            <button type="submit" data-testid="int-enregistrer">Enregistrer</button>
          </form>
        )}
      </Tiroir>
    </div>
  );
}
