/**
 * **Les appels entre collègues** — la bannière qui sonne, et les gestes qui la closent.
 *
 * Elle vit dans la coquille de l'application, et non dans l'écran de messagerie, pour une raison
 * de fond : **un appel qu'on ne voit que si l'on se trouve déjà sur le bon écran n'est pas un
 * appel.** Une personne en train de construire un horaire doit savoir qu'on la demande.
 *
 * ## Ce que cette couche transporte, et ce qu'elle ne transporte pas
 *
 * Elle tient la **session** : qui appelle qui, qui a décroché, depuis combien de temps. Elle ne
 * transporte **pas la voix** — le flux audio relève d'un transport (WebRTC entre navigateurs, ou
 * le système téléphonique de l'établissement) qui se branche sur cette session. Le dire est
 * nécessaire : un bandeau « en cours » au-dessus d'un silence serait un mensonge d'interface.
 * L'écran l'écrit donc à l'utilisateur, il ne le laisse pas découvrir.
 */
import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { useAuth } from "../AuthContext";
import { useCurrentTenant } from "../organismeCourant";
import Avatar from "../composants/Avatar";

export interface AppelServi {
  id: string;
  appelantId: string;
  appeleId: string;
  conversationId: string | null;
  etat: "sonne" | "en_cours" | "termine" | "refuse" | "manque" | "annule";
  lanceLe: string;
  decrocheLe: string | null;
  termineLe: string | null;
  termineParId: string | null;
  dureeSecondes: number;
  /** Vrai quand c'est **moi** qu'on appelle : c'est ce qui déclenche la sonnerie à l'écran. */
  entrant: boolean;
}

/** Un appel de groupe tel que servi (REFONTE-MESSAGERIE.md) : mon état d'invité, et combien sont présents. */
export interface AppelGroupeServi {
  id: string;
  conversationId: string;
  lanceParId: string;
  etat: "sonne" | "en_cours" | "termine" | "manque";
  lanceLe: string;
  debuteLe: string | null;
  presents: number;
  monEtat: "invite" | "present" | "parti" | "ignore" | null;
  /** Vrai quand on m'invite et que je n'y suis pas encore : c'est ce qui fait sonner. */
  entrant: boolean;
}

export const LIBELLE_ETAT_APPEL: Record<AppelServi["etat"], string> = {
  sonne: "sonne", en_cours: "en cours", termine: "terminé",
  refuse: "refusé", manque: "manqué", annule: "annulé",
};

/** `mm:ss` — une durée d'appel se lit en minutes, jamais en secondes cumulées. */
export function dureeLisible(secondes: number): string {
  const s = Math.max(0, Math.floor(secondes));
  return `${String(Math.floor(s / 60)).padStart(2, "0")}:${String(s % 60).padStart(2, "0")}`;
}

/**
 * Le rythme d'interrogation.
 *
 * Trois secondes : au-delà, une personne appelée décroche après que l'appelant a renoncé ; en
 * deçà, on interroge le serveur vingt fois par minute et par poste pour une question dont la
 * réponse est « non » la plupart du temps.
 */
const RYTHME_MS = 3000;

/**
 * Les appels en cours pour le compte connecté, rafraîchis seuls.
 *
 * `actif` permet de **ne pas interroger** quand le module n'est pas ouvert : un appel réseau
 * répété qui rend 403 toutes les trois secondes remplit la console et masque les vraies erreurs.
 */
export function useAppels(actif: boolean) {
  const { apiFetch, user } = useAuth();
  const { tenantId } = useCurrentTenant();
  const [appels, setAppels] = useState<AppelServi[]>([]);
  const [groupes, setGroupes] = useState<AppelGroupeServi[]>([]);
  const [refus, setRefus] = useState("");
  // Une requête en vol au plus : sur un réseau lent, le rythme fixe empilerait les appels.
  const enVol = useRef(false);

  const appelerApi = useCallback(async (chemin: string, init: RequestInit = {}) => {
    const headers = new Headers(init.headers ?? {});
    if (tenantId) headers.set("X-Tenant-Id", tenantId);
    if (init.body) headers.set("Content-Type", "application/json");
    const r = await apiFetch(chemin, { ...init, headers });
    if (!r.ok) {
      const j = await r.json().catch(() => ({}));
      throw new Error(Array.isArray(j?.errors) ? j.errors.join(" · ") : `Erreur ${r.status}`);
    }
    return r.status === 204 ? null : await r.json().catch(() => null);
  }, [apiFetch, tenantId]);

  const relire = useCallback(async () => {
    if (enVol.current) return;
    enVol.current = true;
    try {
      const l = await appelerApi("/api/communication/appels/en-cours");
      setAppels(Array.isArray(l) ? l : []);
      // Les appels de groupe sont lus à part : un refus de l'un ne doit pas taire l'autre.
      const g = await appelerApi("/api/communication/appels-groupe/en-cours").catch(() => []);
      setGroupes(Array.isArray(g) ? g : []);
    } catch { /* un sondage muet : l'écran n'a pas à crier parce qu'une requête de fond a raté */ }
    finally { enVol.current = false; }
  }, [appelerApi]);

  useEffect(() => {
    if (!actif) { setAppels([]); setGroupes([]); return; }
    void relire();
    const minuterie = setInterval(() => { void relire(); }, RYTHME_MS);
    return () => clearInterval(minuterie);
  }, [actif, relire]);

  const geste = useCallback(async (appelId: string, g: "decrocher" | "refuser" | "raccrocher") => {
    try { await appelerApi(`/api/communication/appels/${encodeURIComponent(appelId)}/${g}`, { method: "POST" }); setRefus(""); }
    // Un refus du serveur, lui, **se dit** : c'est un geste de l'utilisateur, pas un sondage.
    catch (e) { setRefus((e as Error).message); }
    await relire();
  }, [appelerApi, relire]);

  const appeler = useCallback(async (appeleId: string, conversationId?: string | null) => {
    try {
      await appelerApi("/api/communication/appels", {
        method: "POST", body: JSON.stringify({ appeleId, conversationId: conversationId ?? null }),
      });
      setRefus("");
    } catch (e) { setRefus((e as Error).message); }
    await relire();
  }, [appelerApi, relire]);

  const gesteGroupe = useCallback(async (appelId: string, g: "rejoindre" | "quitter" | "ignorer") => {
    try { await appelerApi(`/api/communication/appels-groupe/${encodeURIComponent(appelId)}/${g}`, { method: "POST" }); setRefus(""); }
    catch (e) { setRefus((e as Error).message); }
    await relire();
  }, [appelerApi, relire]);

  return { appels, groupes, refus, moi: user?.username ?? "", appeler, geste, gesteGroupe, relire, appelerApi };
}

/** L'autre bout de la ligne, vu de moi. */
const autrui = (a: AppelServi) => (a.entrant ? a.appelantId : a.appeleId);

/**
 * **La bannière d'appel.**
 *
 * Elle est annoncée (`role="alert"`) parce qu'un appel est urgent, et elle **ne prend pas le
 * focus** : voler le curseur à quelqu'un qui saisit un horaire lui ferait perdre sa frappe
 * (WCAG 2.4.3, et la règle du dépôt sur les mises à jour asynchrones).
 */
export function BandeauAppels({ actif }: { actif: boolean }) {
  const { appels, groupes, refus, geste, gesteGroupe } = useAppels(actif);
  /**
   * Un battement par seconde, **pour re-rendre** — et rien d'autre.
   *
   * La durée affichée se calcule de l'horodatage du décrochage, jamais d'un compteur local : un
   * compteur dérive dès que l'onglet passe en veille, et l'appel paraîtrait alors deux fois plus
   * court qu'il ne l'a été. Interroger le serveur chaque seconde pour la même raison serait vingt
   * fois le trafic pour un chiffre que le navigateur sait déjà calculer.
   */
  const [, retracer] = useState(0);
  useEffect(() => {
    if (!appels.some((a) => a.etat === "en_cours") && !groupes.some((g) => g.debuteLe)) return;
    const t = setInterval(() => retracer((n) => n + 1), 1000);
    return () => clearInterval(t);
  }, [appels, groupes]);

  if (!actif || (appels.length === 0 && groupes.length === 0)) return null;

  return (
    <div className="sq-appels-bandeau" data-testid="sq-appels-bandeau" role="alert" aria-live="assertive">
      {refus && <p className="sq-appels-refus" data-testid="sq-appels-refus">{refus}</p>}
      {appels.map((a) => {
        const qui = autrui(a);
        const ecoule = a.decrocheLe ? Math.floor((Date.now() - Date.parse(a.decrocheLe)) / 1000) : 0;
        return (
          <div key={a.id} className="sq-appel" data-testid={`sq-appel-${a.id}`}>
            <span className="sq-appel-texte">
              {a.etat === "sonne" && a.entrant && <><strong>{qui}</strong> vous appelle.</>}
              {a.etat === "sonne" && !a.entrant && <>Appel vers <strong>{qui}</strong> — ça sonne.</>}
              {a.etat === "en_cours" && (
                <>En communication avec <strong>{qui}</strong> — <span data-testid={`sq-appel-duree-${a.id}`}>{dureeLisible(ecoule)}</span></>
              )}
            </span>
            {a.etat === "sonne" && a.entrant && (
              <>
                <button type="button" className="sq-appel-decrocher" data-testid={`sq-appel-decrocher-${a.id}`}
                        onClick={() => void geste(a.id, "decrocher")}>
                  Décrocher<span className="sr-only"> l'appel de {qui}</span>
                </button>
                <button type="button" className="sq-appel-refuser" data-testid={`sq-appel-refuser-${a.id}`}
                        onClick={() => void geste(a.id, "refuser")}>
                  Refuser<span className="sr-only"> l'appel de {qui}</span>
                </button>
              </>
            )}
            {(a.etat === "en_cours" || (a.etat === "sonne" && !a.entrant)) && (
              <button type="button" className="sq-appel-refuser" data-testid={`sq-appel-raccrocher-${a.id}`}
                      onClick={() => void geste(a.id, "raccrocher")}>
                Raccrocher<span className="sr-only"> l'appel avec {qui}</span>
              </button>
            )}
            {a.etat === "en_cours" && (
              <span className="sq-appel-note">
                La ligne est ouverte dans l'application ; la voix passe par votre téléphone
                habituel.
              </span>
            )}
          </div>
        );
      })}
      {groupes.map((g) => {
        const ecoule = g.debuteLe ? Math.floor((Date.now() - Date.parse(g.debuteLe)) / 1000) : 0;
        const present = g.monEtat === "present";
        return (
          <div key={g.id} className="sq-appel" data-testid={`sq-appel-groupe-${g.id}`}>
            <span className="sq-appel-texte">
              {g.entrant
                ? <>Appel de groupe de <strong>{g.lanceParId}</strong> — {g.presents} présent(s).</>
                : <>Appel de groupe — {g.presents} présent(s){g.debuteLe
                    ? <> — <span data-testid={`sq-appel-groupe-duree-${g.id}`}>{dureeLisible(ecoule)}</span></>
                    : " — ça sonne chez les autres"}.</>}
            </span>
            {!present && (
              <button type="button" className="sq-appel-decrocher" data-testid={`sq-appel-groupe-rejoindre-${g.id}`}
                      onClick={() => void gesteGroupe(g.id, "rejoindre")}>
                Rejoindre<span className="sr-only"> l'appel de groupe</span>
              </button>
            )}
            {g.monEtat === "invite" && (
              <button type="button" className="sq-appel-refuser" data-testid={`sq-appel-groupe-ignorer-${g.id}`}
                      onClick={() => void gesteGroupe(g.id, "ignorer")}>
                Ignorer<span className="sr-only"> l'appel de groupe</span>
              </button>
            )}
            {present && (
              <button type="button" className="sq-appel-refuser" data-testid={`sq-appel-groupe-quitter-${g.id}`}
                      onClick={() => void gesteGroupe(g.id, "quitter")}>
                Quitter<span className="sr-only"> l'appel de groupe</span>
              </button>
            )}
          </div>
        );
      })}
    </div>
  );
}

// ── L'onglet « Appels » de la messagerie ─────────────────────────────────────

/** `2027-03-04 08:15` — un horodatage ISO, lisible sans le décoder. */
const quand = (iso: string | null | undefined) => String(iso ?? "").replace("T", " ").slice(0, 16);

/**
 * **Appeler un collègue, et relire ses appels.**
 *
 * Le geste d'appel vit ici plutôt que dans la bannière : la bannière sert ce qui *arrive*, cet
 * onglet sert ce qu'on *déclenche*. Les mêler ferait un panneau qui change de rôle selon l'heure.
 */
export function AppelsSq(
  { champPersonne, nomDe = (x) => x }:
  {
    champPersonne: (valeur: string, onChange: (v: string) => void) => ReactNode;
    /** Résout un identifiant en nom ; sans lui, l'identifiant s'affiche tel quel. */
    nomDe?: (personId: string) => string;
  },
) {
  /**
   * **`false` : ce panneau ne sonde pas.**
   *
   * La bannière de l'application sonde déjà toutes les trois secondes, et elle est rendue sur
   * toutes les pages — celle-ci comprise. Un second sondage ici doublerait le trafic de chaque
   * poste ouvert sur cet onglet pour afficher exactement la même chose, deux fois. Le panneau se
   * contente donc de **lancer** un appel et de relire son historique après coup.
   */
  const { refus, appeler, appelerApi } = useAppels(false);
  const [qui, setQui] = useState("");
  const [historique, setHistorique] = useState<AppelServi[]>([]);

  const relireHistorique = useCallback(async () => {
    try {
      const l = await appelerApi("/api/communication/appels?limite=50");
      setHistorique(Array.isArray(l) ? l : []);
    } catch { setHistorique([]); }
  }, [appelerApi]);

  useEffect(() => { void relireHistorique(); }, [relireHistorique]);

  return (
    <>
      <form onSubmit={(e) => { e.preventDefault(); void appeler(qui).then(relireHistorique); }}>
        <div className="sq-rangee sq-rangee-personne">
          {champPersonne(qui, setQui)}
          {/*
            **Le visage de celle ou celui qu'on va appeler** (maquette P11).

            On compose un appel dans un service où plusieurs personnes portent le même prénom.
            Voir le portrait avant de lancer la sonnerie est le seul contrôle qui tienne à ce
            moment-là : l'identifiant ne se reconnaît pas, et le nom seul se confond.
          */}
          {qui.trim() && (
            <span className="msg-personne" data-testid="sq-appel-destinataire">
              <Avatar personId={qui} nom={nomDe(qui)} taille={32} />
              {nomDe(qui)}
            </span>
          )}
        </div>
        <div className="sq-actions">
          <button type="submit" data-testid="sq-appel-lancer" disabled={!qui.trim()}>
            Appeler cette personne
          </button>
        </div>
        <p className="sq-champ-aide">
          L'appel <strong>sonne</strong> chez la personne : elle décroche ou refuse. Tant qu'elle
          n'a pas répondu, vous pouvez raccrocher depuis la bannière en haut de l'écran.
        </p>
      </form>
      {refus && <p className="sq-erreur" role="alert" data-testid="sq-appel-refus">{refus}</p>}

      <table className="sq-table" data-testid="sq-appels-historique">
        <caption>Mes appels — reçus, passés, manqués</caption>
        <thead>
          <tr>
            <th scope="col">Lancé le</th><th scope="col">Sens</th><th scope="col">Avec</th>
            <th scope="col">État</th><th scope="col">Durée</th>
          </tr>
        </thead>
        <tbody>
          {historique.map((a) => (
            <tr key={a.id} data-testid={`sq-appel-ligne-${a.id}`}>
              <th scope="row">{quand(a.lanceLe)}</th>
              {/* Le sens est écrit : une flèche seule ne se lit pas à l'oreille (WCAG 1.4.1). */}
              <td>{a.entrant ? "reçu" : "passé"}</td>
              <td>{autrui(a)}</td>
              <td>{LIBELLE_ETAT_APPEL[a.etat]}</td>
              <td>{a.etat === "termine" ? dureeLisible(a.dureeSecondes) : "—"}</td>
            </tr>
          ))}
          {historique.length === 0 && (
            <tr><td colSpan={5}>Aucun appel. Appelez un collègue ci-dessus.</td></tr>
          )}
        </tbody>
      </table>
    </>
  );
}
