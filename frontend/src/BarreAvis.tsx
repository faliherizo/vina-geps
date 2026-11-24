/**
 * **Ce qui m'attend, dans la barre du haut** (maquette P11 : « notification sur le menu d'en
 * haut — messagerie et autre »).
 *
 * Deux compteurs, et deux seulement : la **messagerie** et les **avis** de l'organisme. Ils
 * répondent à la seule question qu'on se pose en arrivant — « est-ce qu'on m'a écrit ? » — et
 * l'un ne peut pas répondre pour l'autre : un message est adressé à moi, un avis est publié pour
 * tous. Les confondre ferait sonner la barre pour une annonce de maintenance comme pour un
 * transfert de civière.
 *
 * ## Ce qui est dit, et comment
 *
 * - le compte est **écrit en chiffres**, jamais rendu par une pastille muette : un point coloré
 *   se devine, « 3 » se lit (WCAG 1.4.1) ;
 * - il vit dans une région `role="status"` : il change sans que l'on ait cliqué, et un
 *   changement silencieux n'existe pas pour qui n'a pas les yeux sur la barre. `polite`, jamais
 *   `assertive` — on ne coupe pas la parole à quelqu'un pour annoncer un courriel ;
 * - le panneau est un `<details>` : il s'ouvre au clavier sans une ligne de JavaScript, annonce
 *   son état, et se referme à `Échap`. Les menus reconstruits à la main sont ceux qui piègent le
 *   focus (WCAG 2.1.2), et personne ne s'en aperçoit avant un audit.
 *
 * ## Ce qu'elle ne fait pas
 *
 * Elle **n'interroge pas en boucle**. Le compte se rafraîchit à l'ouverture du panneau et à
 * chaque navigation : une messagerie qui sonne à la seconde près coûte une requête par seconde
 * et par usager connecté, pour une information qu'on regarde deux fois par heure.
 */
import { useCallback, useEffect, useRef, useState } from "react";
import type { Page } from "./App";
import { useAuth } from "./AuthContext";
import { demanderFil } from "./filDemande";

/** Ce que la route des non-lus rend pour chaque fil. */
interface FilNonLu {
  conversationId: string;
  sujet: string;
  nonLus: number;
  dernierExtrait: string | null;
}

/** Une annonce de l'organisme, telle que `/api/notifications/annonces` la rend. */
interface Annonce {
  id: string;
  titre: string;
  corps?: string | null;
  lue: boolean;
}

interface Props {
  /** La messagerie n'existe que si l'établissement a le module : sans lui, aucun compteur. */
  avecMessagerie: boolean;
  /** Les avis de l'organisme, de même : un établissement sans le module n'en reçoit pas. */
  avecAvis: boolean;
  /**
   * Ouvrir l'écran de la messagerie, ou celui des avis. `Page` et non `string` : la barre menait à
   * « sqmessages », un écran retiré du produit, et l'application entière devenait blanche.
   */
  aller: (page: Page) => void;
  /** Change à chaque navigation : c'est le signal de rafraîchissement, et il est gratuit. */
  jeton: unknown;
}

/** Le maximum affiché dans la pastille : au-delà, le chiffre exact n'aide plus à décider. */
const PLAFOND_AFFICHE = 99;

export default function BarreAvis({ avecMessagerie, avecAvis, aller, jeton }: Props) {
  const { apiFetch, user } = useAuth();
  const [messages, setMessages] = useState<FilNonLu[]>([]);
  const [annonces, setAnnonces] = useState<Annonce[]>([]);
  const [ouvert, setOuvert] = useState<"messages" | "avis" | null>(null);
  const boite = useRef<HTMLDivElement>(null);

  const relire = useCallback(async () => {
    if (!user) { setMessages([]); setAnnonces([]); return; }
    if (avecMessagerie) {
      const r = await apiFetch("/api/communication/conversations/non-lus")
        .then((x) => (x.ok ? x.json() : null)).catch(() => null);
      const fils = Array.isArray(r?.conversations) ? (r.conversations as FilNonLu[]) : [];
      setMessages(fils.filter((f) => f.nonLus > 0));
    } else {
      setMessages([]);
    }
    if (!avecAvis) { setAnnonces([]); return; }
    const a = await apiFetch("/api/notifications/annonces")
      .then((x) => (x.ok ? x.json() : null)).catch(() => null);
    const liste = Array.isArray(a) ? a : Array.isArray(a?.annonces) ? a.annonces : [];
    setAnnonces((liste as Annonce[]).filter((x) => !x.lue));
  }, [apiFetch, user, avecMessagerie, avecAvis]);

  useEffect(() => { void relire(); }, [relire, jeton]);

  // Refermer en cliquant ailleurs et à Échap : un panneau qui reste ouvert recouvre le contenu,
  // et c'est exactement le défaut de superposition que les essais de géométrie traquent.
  useEffect(() => {
    if (!ouvert) return;
    const dehors = (e: MouseEvent) => {
      if (boite.current && !boite.current.contains(e.target as Node)) setOuvert(null);
    };
    const echap = (e: KeyboardEvent) => { if (e.key === "Escape") setOuvert(null); };
    document.addEventListener("mousedown", dehors);
    document.addEventListener("keydown", echap);
    return () => {
      document.removeEventListener("mousedown", dehors);
      document.removeEventListener("keydown", echap);
    };
  }, [ouvert]);

  if (!user || (!avecMessagerie && !avecAvis)) return null;

  const comptes = {
    messages: messages.reduce((t, f) => t + f.nonLus, 0),
    avis: annonces.length,
  };

  const basculer = (lequel: "messages" | "avis") => {
    const suivant = ouvert === lequel ? null : lequel;
    setOuvert(suivant);
    if (suivant) void relire();
  };

  const pastille = (n: number) => (n > PLAFOND_AFFICHE ? `${PLAFOND_AFFICHE}+` : String(n));

  return (
    <div className="barre-avis" ref={boite} data-testid="barre-avis">
      {avecMessagerie && (
        <details className="avis-menu" open={ouvert === "messages"} data-testid="avis-messages">
          <summary
            data-testid="avis-messages-bouton"
            aria-label={comptes.messages === 0
              ? "Messagerie : aucun message non lu"
              : `Messagerie : ${comptes.messages} message(s) non lu(s)`}
            onClick={(e) => { e.preventDefault(); basculer("messages"); }}
          >
            <span aria-hidden="true">✉️</span>
            <span className="sr-only">Messagerie</span>
            {comptes.messages > 0 && (
              <span className="avis-pastille" role="status" data-testid="avis-messages-compte">
                {pastille(comptes.messages)}
              </span>
            )}
          </summary>
          <div className="avis-panneau">
            <p className="avis-titre">Messages non lus</p>
            {messages.length === 0
              ? <p className="avis-vide" data-testid="avis-messages-vide">Aucun message non lu.</p>
              : (
                <ul className="avis-liste">
                  {messages.map((f) => (
                    <li key={f.conversationId}>
                      <button type="button" data-testid={`avis-message-${f.conversationId}`}
                              onClick={() => { setOuvert(null); demanderFil(f.conversationId); aller("usmessages"); }}>
                        <span className="avis-sujet">{f.sujet}</span>
                        <span className="avis-extrait">{f.dernierExtrait ?? ""}</span>
                        <span className="avis-compte">{f.nonLus} non lu(s)</span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
          </div>
        </details>
      )}

      {avecAvis && (
      <details className="avis-menu" open={ouvert === "avis"} data-testid="avis-annonces">
        <summary
          data-testid="avis-annonces-bouton"
          aria-label={comptes.avis === 0
            ? "Avis de l'organisme : aucun avis nouveau"
            : `Avis de l'organisme : ${comptes.avis} avis nouveau(x)`}
          onClick={(e) => { e.preventDefault(); basculer("avis"); }}
        >
          <span aria-hidden="true">🔔</span>
          <span className="sr-only">Avis</span>
          {comptes.avis > 0 && (
            <span className="avis-pastille" role="status" data-testid="avis-annonces-compte">
              {pastille(comptes.avis)}
            </span>
          )}
        </summary>
        <div className="avis-panneau">
          <p className="avis-titre">Avis de l'organisme</p>
          {annonces.length === 0
            ? <p className="avis-vide" data-testid="avis-annonces-vide">Aucun avis nouveau.</p>
            : (
              <ul className="avis-liste">
                {annonces.map((a) => (
                  <li key={a.id}>
                    <button type="button" data-testid={`avis-annonce-${a.id}`}
                            onClick={async () => {
                              // Marquer lu **ici** : l'avis a été montré, et le laisser non lu
                              // ferait sonner la barre pour quelque chose qu'on vient de lire.
                              await apiFetch(`/api/notifications/annonces/${encodeURIComponent(a.id)}/lue`,
                                             { method: "POST" }).catch(() => null);
                              setOuvert(null);
                              await relire();
                              aller("notifications");
                            }}>
                      <span className="avis-sujet">{a.titre}</span>
                      <span className="avis-extrait">{a.corps ?? ""}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
        </div>
      </details>
      )}
    </div>
  );
}
