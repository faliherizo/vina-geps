/**
 * **Messagerie clinique** (BF-030, BF-034 à BF-039, BF-126, BF-127, BF-136).
 *
 * L'écran ne savait que **lire** : il listait les conversations et renvoyait, en toutes lettres,
 * vers « l'écran du module d'horaires cliniques » pour écrire. Un besoin déclaré acquis dont
 * l'écran ne peut rien faire est un besoin qu'on ne sait pas montrer — et les routes existaient
 * depuis le premier lot. C'est ce report qui est corrigé ici.
 *
 * ## Ce que l'écran tient, et pourquoi dans cet ordre
 *
 * Ouvrir une conversation, y écrire, répondre **à l'émetteur seulement**, ajouter ou retirer un
 * participant, archiver. La réponse privée est le geste le plus délicat du lot : l'écran **dit qui
 * la verra avant l'envoi**, parce qu'un message privé envoyé au fil entier ne se rattrape pas.
 *
 * Le **brouillon** s'enregistre à la demande et non à la frappe : un enregistrement automatique
 * aurait noyé le journal et surpris l'auteur, qui n'a pas demandé qu'on garde ce qu'il efface.
 */
import { useEffect, useMemo, useState } from "react";
import { Panneau, TableauDonnees, page, type ContexteSection, type Section } from "../noyau";
import { ChampMembre } from "../references";
import { ChampPieces, tailleLisible, type PieceLocale, type LimitesPieces } from "../../SoutienPieces";
import { downloadApiFile } from "../../FileViewerModal";
import { AppelsSq } from "../appels";
import Avatar from "../../composants/Avatar";
import { useAuth } from "../../AuthContext";

/**
 * **Les limites de la messagerie clinique**, dites à l'écran avant que le serveur ne refuse.
 *
 * Elles recopient `TYPES_PIECE_JOINTE` et `TAILLE_MAX_PIECE` du domaine (`messagerie.ts`) : le
 * serveur reste seul juge — un écran qui laisserait passer verrait son envoi refusé, pas accepté.
 * Les dire ici évite seulement d'apprendre le refus après avoir attendu le téléversement.
 */
const LIMITES_MESSAGERIE: LimitesPieces = {
  maxParEnvoi: 5,
  tailleMaxOctets: 25 * 1024 * 1024,
  typesAdmis: [
    "application/pdf", "image/png", "image/jpeg", "image/gif", "image/webp",
    "text/plain", "text/csv",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  ],
};

interface Participant { personId: string; depuis: string; jusqua: string | null }
interface Conversation {
  id: string; sujet: string; type: string; archiveeLe: string | null;
  participants?: Participant[]; createdAt?: string;
}
interface PieceJointeLue { id: string; nom: string; typeMime: string; taille: number }
interface MessageLu {
  id: string; auteurId: string; corps: string; envoyeLe: string; priveA: string | null;
  repondAuMessageId?: string | null;
  piecesJointes?: PieceJointeLue[];
  /** « L'ai-je lu ? » — rendu par le serveur, et vrai d'office pour ce que j'ai écrit. */
  luParMoi?: boolean;
}

/** L'heure d'un horodatage ISO, lisible sans décoder : « 2027-03-04 08:15 ». */
const quand = (iso: string | null | undefined) => String(iso ?? "").replace("T", " ").slice(0, 16);

// ── Ouvrir une conversation ──────────────────────────────────────────────────

function Ouvrir({ ctx }: { ctx: ContexteSection }) {
  const [sujet, setSujet] = useState("");
  const [type, setType] = useState("groupe");
  const [participants, setParticipants] = useState("");

  return (
    <form onSubmit={async (e) => {
      e.preventDefault();
      const creee = await ctx.appeler("/api/communication/conversations", {
        method: "POST",
        body: JSON.stringify({
          sujet, type,
          // Le créateur est ajouté par le serveur : l'écran n'a pas à le nommer, et le nommer
          // ferait croire qu'on peut ouvrir un fil auquel on ne participerait pas.
          participants: participants.split(",").map((p) => p.trim()).filter(Boolean),
        }),
      });
      if (creee?.id) {
        ctx.annoncer(`Conversation « ${sujet} » ouverte. Vous en êtes participant.`);
        setSujet(""); setParticipants("");
        ctx.recharger();
      }
    }}>
      <div className="sq-rangee">
        <div className="sq-champ">
          <label htmlFor="sq-msg-sujet">Sujet <span className="req-star" aria-hidden="true">*</span><span className="sr-only"> (obligatoire)</span></label>
          <input id="sq-msg-sujet" required data-testid="sq-msg-sujet" value={sujet}
                 onChange={(e) => setSujet(e.target.value)} />
        </div>
        <div className="sq-champ">
          <label htmlFor="sq-msg-type">Portée</label>
          <select id="sq-msg-type" data-testid="sq-msg-type" value={type}
                  onChange={(e) => setType(e.target.value)}>
            <option value="groupe">De groupe</option>
            <option value="individuelle">Individuelle</option>
          </select>
        </div>
        <ChampMembre ctx={ctx} libelle="Premier participant" testId="sq-msg-participant"
          valeur={participants} onChange={setParticipants} />
      </div>
      <div className="sq-actions">
        <button type="submit" data-testid="sq-msg-ouvrir">Ouvrir la conversation</button>
      </div>
    </form>
  );
}

/**
 * **Le nom d'une personne, pas son identifiant.**
 *
 * Les fils portaient `auteurId` à l'écran — « 7f3a… » en tête de chaque message. Personne ne
 * reconnaît un identifiant, et c'est en les recopiant d'un écran à l'autre qu'on rattache une
 * ligne à la mauvaise personne. Les noms du groupe sont lus **une fois** et servent partout ;
 * l'identifiant reste le repli pour qui n'a pas de dossier — un externe de garde.
 */
function useNomsDuGroupe(ctx: ContexteSection): (personId: string) => string {
  const [noms, setNoms] = useState<Record<string, string>>({});
  const groupeId = ctx.perimetre.groupeId;

  useEffect(() => {
    if (!groupeId) { setNoms({}); return; }
    void (async () => {
      const r = await ctx.appeler(`/api/communication/groupes/${encodeURIComponent(groupeId)}/membres`);
      const liste = Array.isArray(r) ? (r as Array<{ personId: string; nom?: string }>) : [];
      setNoms(Object.fromEntries(liste.map((m) => [m.personId, m.nom || m.personId])));
    })();
  }, [groupeId]);   // eslint-disable-line react-hooks/exhaustive-deps

  const { user } = useAuth();
  /**
   * **Deux espaces d'identifiants se croisent ici**, et c'est antérieur à cet écran : l'auteur
   * d'un message est l'`id` du compte (`acteur(req)`), tandis qu'un participant choisi l'est par
   * le `personId` du membre du groupe — souvent son identifiant d'emploi. Le créateur d'un fil
   * est donc inscrit sous son `id` de compte, que l'annuaire du groupe ne connaît pas.
   *
   * Le repli ne sera donc pas toujours un nom. Au moins, **celui qui lit se reconnaît** : on ne
   * lui montre pas son propre identifiant technique, qui est le pire des noms. Le reste est une
   * question de modèle — deux espaces d'identifiants pour une même personne —, et elle se tranche
   * ailleurs qu'en habillant l'écran.
   */
  return useMemo(() => (personId: string) => (
    noms[personId] ?? (personId === user?.id ? "Moi" : personId)
  ), [noms, user?.id]);
}

/** Ce que la route des non-lus rend pour chaque fil. */
interface FilNonLu {
  conversationId: string; sujet: string; nonLus: number;
  dernierMessageLe: string | null; dernierAuteurId: string | null; dernierExtrait: string | null;
}

/**
 * **La liste des conversations** (maquette P11) : un visage, un sujet, le début du dernier
 * message, et ce qui attend.
 *
 * Elle ne remplace pas le sélecteur « Conversation » : elle le double. Le sélecteur reste
 * l'affordance courte — celle qu'on utilise au clavier et sur un téléphone, où la liste passe
 * sous le fil — et la liste donne ce qu'un sélecteur ne peut pas montrer : qui a parlé en
 * dernier, et où il reste à lire. Les deux écrivent le même état ; il n'y a qu'une vérité.
 */
function ListeConversations({
  fils, choisi, onChoisir, nomDe,
}: {
  fils: FilNonLu[];
  choisi: string;
  onChoisir: (id: string) => void;
  nomDe: (personId: string) => string;
}) {
  if (fils.length === 0) return null;
  return (
    <ul className="msg-liste" data-testid="sq-msg-liste">
      {fils.map((f) => {
        const auteur = f.dernierAuteurId ? nomDe(f.dernierAuteurId) : null;
        return (
          <li key={f.conversationId}>
            <button
              type="button"
              className={`msg-liste-ligne${f.conversationId === choisi ? " msg-liste-ligne-active" : ""}`}
              // `aria-current` dit « c'est ici que vous êtes » à l'assistance technique ; la
              // teinte seule ne le dirait qu'à ceux qui la voient (WCAG 1.4.1).
              aria-current={f.conversationId === choisi ? "true" : undefined}
              data-testid={`sq-msg-fil-${f.conversationId}`}
              onClick={() => onChoisir(f.conversationId)}
            >
              <Avatar personId={f.dernierAuteurId} nom={auteur ?? f.sujet} taille={40} />
              <span className="msg-liste-texte">
                <span className="msg-liste-sujet">{f.sujet}</span>
                <span className="msg-liste-apercu">
                  {auteur ? `${auteur} : ` : ""}{f.dernierExtrait ?? "Aucun message"}
                </span>
              </span>
              <span className="msg-liste-cote">
                <span className="msg-liste-heure">{quand(f.dernierMessageLe).slice(11) || "—"}</span>
                {f.nonLus > 0 && (
                  /* Le compte est écrit, jamais rendu par une pastille muette : « 3 » se lit,
                     un point coloré se devine. */
                  <span className="msg-liste-compte" data-testid={`sq-msg-non-lus-${f.conversationId}`}>
                    {f.nonLus}
                    <span className="sr-only"> message(s) non lu(s)</span>
                  </span>
                )}
              </span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}

/**
 * Les appels, avec les **noms** du groupe.
 *
 * `useNomsDuGroupe` est un crochet : il ne peut pas être appelé dans le `rendu` d'une section,
 * qui n'est pas un composant. Ce composant-ci en est un, et il n'existe que pour cela.
 */
function AppelsAvecNoms({ ctx }: { ctx: ContexteSection }) {
  const nomDe = useNomsDuGroupe(ctx);
  return (
    <AppelsSq
      nomDe={nomDe}
      champPersonne={(valeur, onChange) => (
        /*
          Pas de `requis` : le champ garde son option « — Choisir — », qui correspond à l'état
          vide. Sans elle, le navigateur afficherait le premier membre du groupe alors que rien
          n'est choisi — et le bouton resterait désactivé devant un nom affiché.
        */
        <ChampMembre ctx={ctx} libelle="Personne à appeler"
                     testId="sq-appel-personne" valeur={valeur} onChange={onChange} />
      )}
    />
  );
}

// ── Lire et écrire dans un fil ───────────────────────────────────────────────

/**
 * Le fil d'une conversation : ses messages, et les gestes qui s'y appliquent.
 *
 * `priveA` porte le destinataire d'une **réponse privée**. L'écran l'affiche sur chaque message
 * concerné — « privé à … » — parce qu'un fil où l'on ne distingue pas le privé du public conduit
 * à répondre en public à ce qui était privé.
 */
function Fil({ ctx }: { ctx: ContexteSection }) {
  const conversations = (Array.isArray(ctx.donnees) ? ctx.donnees : []) as Conversation[];
  const nomDe = useNomsDuGroupe(ctx);
  const { user } = useAuth();
  const moiId = user?.id ?? "";
  const [fils, setFils] = useState<FilNonLu[]>([]);

  /**
   * Ce qui m'attend, fil par fil. Relu après chaque geste qui change l'état de lecture — sinon
   * le compte affiché contredirait le fil ouvert juste à côté, et c'est le genre de désaccord
   * qui fait douter de tout le reste de l'écran.
   */
  const rechargerFils = async () => {
    const r = await ctx.appeler("/api/communication/conversations/non-lus");
    setFils(Array.isArray(r?.conversations) ? (r.conversations as FilNonLu[]) : []);
  };
  const [conversationId, setConversationId] = useState("");
  const [messages, setMessages] = useState<MessageLu[]>([]);
  const [corps, setCorps] = useState("");
  const [priveA, setPriveA] = useState("");
  const [repondA, setRepondA] = useState("");
  const [nouveauParticipant, setNouveauParticipant] = useState("");
  const [pieces, setPieces] = useState<PieceLocale[]>([]);

  const choisie = conversations.find((c) => c.id === conversationId);
  const actifs = (choisie?.participants ?? []).filter((p) => p.jusqua === null);

  const charger = async (id: string) => {
    if (!id) { setMessages([]); return; }
    const lus = await ctx.appeler(`/api/communication/conversations/${encodeURIComponent(id)}/messages`);
    setMessages(Array.isArray(lus) ? lus : []);
  };

  useEffect(() => { void charger(conversationId); }, [conversationId]);  // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { void rechargerFils(); }, [conversations.length]);     // eslint-disable-line react-hooks/exhaustive-deps

  if (conversations.length === 0) {
    return <p className="sq-vide">Aucune conversation. Ouvrez-en une ci-dessus.</p>;
  }

  const ouvrir = (id: string) => { setConversationId(id); setPriveA(""); setRepondA(""); };

  return (
    /*
      **Deux colonnes** (maquette P11) : les fils à gauche, celui qu'on lit à droite. Sous 900 px
      elles s'empilent — la liste d'abord, puis le fil : sur un téléphone, on choisit avant de
      lire, et l'ordre de lecture suit l'ordre du geste.
    */
    <div className="msg-ecran">
      <div className="msg-colonne-fils">
        <div className="sq-rangee">
          <div className="sq-champ">
            <label htmlFor="sq-msg-fil">Conversation <span className="req-star" aria-hidden="true">*</span><span className="sr-only"> (obligatoire)</span></label>
            <select id="sq-msg-fil" required data-testid="sq-msg-fil" value={conversationId}
                    onChange={(e) => ouvrir(e.target.value)}>
              <option value="">— Choisir —</option>
              {conversations.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.sujet}{c.archiveeLe ? " (archivée)" : ""}
                </option>
              ))}
            </select>
          </div>
        </div>
        <ListeConversations fils={fils} choisi={conversationId} onChoisir={ouvrir} nomDe={nomDe} />
      </div>

      <div className="msg-colonne-fil">
      {!conversationId && (
        <p className="sq-vide" data-testid="sq-msg-aucun-fil">
          Choisissez une conversation à gauche pour la lire et y répondre.
        </p>
      )}
      {conversationId && (
        <>
          {/*
            **Le fil, en bulles** (maquette P11) — et non plus un tableau.
            Un tableau classait le message dans une colonne « Message » : on lisait une grille là
            où l'on attend une conversation, et la portée d'une réponse privée se perdait dans une
            cinquième colonne. La bulle met l'essentiel au même endroit que l'œil le cherche, et
            chaque geste du message reste **dans** le message.

            `sq-msg-table` reste l'identifiant du fil : les essais le lisent déjà, et le renommer
            n'aurait appris à personne que l'écran avait changé.
          */}
          <p className="msg-gestes-fil">
            {/* Rattraper une garde ne doit pas demander quarante clics. */}
            <button type="button" className="sq-lien" data-testid="sq-msg-tout-lu"
                    onClick={async () => {
                      const r = await ctx.appeler(`/api/communication/conversations/${encodeURIComponent(conversationId)}/lu`,
                                                  { method: "POST" });
                      if (r) {
                        ctx.annoncer(r.marques > 0
                          ? `${r.marques} message(s) marqué(s) lu(s).`
                          : "Tout était déjà lu dans ce fil.");
                        await charger(conversationId);
                        await rechargerFils();
                      }
                    }}>
              Tout marquer lu
            </button>
          </p>
          <ul className="msg-fil" data-testid="sq-msg-table"
              aria-label={`Messages de « ${choisie?.sujet ?? ""} »`}>
            {messages.length === 0 && (
              <li className="msg-vide" data-testid="sq-msg-vide">Aucun message dans cette conversation.</li>
            )}
            {messages.map((m) => {
              const moi = m.auteurId === moiId;
              const auteur = nomDe(m.auteurId);
              return (
                <li key={m.id} className={`msg-bulle${moi ? " msg-bulle-moi" : ""}`}>
                  <Avatar personId={m.auteurId} nom={auteur} taille={36} />
                  <div className="msg-corps">
                    <p className="msg-entete">
                      <span className="msg-auteur">{auteur}</span>
                      <span className="msg-heure">{quand(m.envoyeLe)}</span>
                      {/* La portée est écrite, jamais rendue par une seule couleur (WCAG 1.4.1). */}
                      <span className="msg-portee">{m.priveA ? `privé à ${nomDe(m.priveA)}` : "tout le fil"}</span>
                      {/* L'état de lecture **en toutes lettres** : une pastille muette se devine
                          (WCAG 1.4.1), et un fil exporté perd les couleurs mais garde les mots. */}
                      {!moi && (
                        <span className={m.luParMoi ? "msg-lu" : "msg-non-lu"}
                              data-testid={`sq-msg-etat-${m.id}`}>
                          {m.luParMoi ? "Lu" : "Non lu"}
                        </span>
                      )}
                    </p>
                    <p className="msg-texte">{m.corps}</p>
                    {(m.piecesJointes ?? []).length > 0 && (
                      <ul className="msg-pieces">
                        {(m.piecesJointes ?? []).map((pj) => (
                          <li key={pj.id}>
                            {/*
                              Un **bouton**, et non un lien : la session est portée par un témoin,
                              donc un `<a href>` s'authentifierait — mais sortirait du `fetch`
                              instrumenté qui pose l'en-tête d'établissement, et un refus du
                              serveur s'afficherait comme une page d'erreur à la place de
                              l'application. Ici, il se dit à l'écran.
                            */}
                            <button type="button" className="msg-piece"
                                    data-testid={`sq-msg-piece-${pj.id}`}
                                    onClick={() => { void downloadApiFile(`/api/communication/pieces-jointes/${pj.id}`, pj.nom); }}>
                              <span aria-hidden="true">📎</span> {pj.nom}{" "}
                              <span className="sq-doux">({tailleLisible(pj.taille)})</span>
                            </button>
                          </li>
                        ))}
                      </ul>
                    )}
                    <p className="msg-gestes">
                      {!choisie?.archiveeLe && (
                        <button type="button" className="sq-lien" data-testid={`sq-msg-repondre-${m.id}`}
                                onClick={() => {
                                  setRepondA(m.id);
                                  setPriveA(m.auteurId);
                                  ctx.annoncer(`Réponse préparée à ${auteur} seulement : lui et vous la verrez, personne d'autre.`);
                                }}>
                          Répondre à {auteur} seulement
                        </button>
                      )}
                      <button type="button" className="sq-lien" data-testid={`sq-msg-lu-${m.id}`}
                              onClick={async () => {
                                const fait = await ctx.appeler(`/api/communication/messages/${m.id}/lu`, { method: "POST" });
                                if (fait) { ctx.annoncer(`Message de ${auteur} marqué lu.`); await rechargerFils(); }
                              }}>
                        Marquer lu
                      </button>
                    </p>
                  </div>
                </li>
              );
            })}
          </ul>

          <form onSubmit={async (e) => {
            e.preventDefault();
            const envoye = await ctx.appeler(`/api/communication/conversations/${encodeURIComponent(conversationId)}/messages`, {
              method: "POST",
              body: JSON.stringify({
                corps, priveA: priveA || null, repondAuMessageId: repondA || null,
                // Le nom, le type **et le fichier** : déclarer une pièce sans l'envoyer était
                // précisément le défaut du 1er novembre 2026.
                piecesJointes: pieces.map((p) => ({ nom: p.nom, typeMime: p.typeMime, contenu: p.contenu })),
              }),
            });
            if (envoye?.id) {
              const jointes = pieces.length;
              ctx.annoncer([
                priveA ? `Message envoyé à ${priveA} seulement.` : "Message envoyé à tous les participants du fil.",
                jointes ? `${jointes} pièce(s) jointe(s) transmise(s).` : "",
              ].filter(Boolean).join(" "));
              setCorps(""); setPriveA(""); setRepondA(""); setPieces([]);
              await charger(conversationId);
            }
          }}>
            <div className="sq-rangee">
              <div className="sq-champ">
                <label htmlFor="sq-msg-corps">Message <span className="req-star" aria-hidden="true">*</span><span className="sr-only"> (obligatoire)</span></label>
                <textarea id="sq-msg-corps" required rows={3} data-testid="sq-msg-corps"
                          aria-describedby="sq-msg-corps-aide" value={corps}
                          onChange={(e) => setCorps(e.target.value)} />
                <p className="sq-champ-aide" id="sq-msg-corps-aide">
                  {priveA
                    ? `Cette réponse n'ira qu'à ${priveA}. Les autres participants ne la verront pas.`
                    : "Ce message ira à tous les participants actifs du fil."}
                </p>
              </div>
            </div>
            <ChampPieces pieces={pieces} onChange={setPieces} limites={LIMITES_MESSAGERIE}
                         testId="sq-msg-pieces" />
            <div className="sq-actions">
              <button type="submit" data-testid="sq-msg-envoyer" disabled={Boolean(choisie?.archiveeLe)}>
                {priveA ? `Envoyer à ${priveA} seulement` : "Envoyer au fil"}
              </button>
              {priveA && (
                <button type="button" data-testid="sq-msg-annuler-prive"
                        onClick={() => { setPriveA(""); setRepondA(""); ctx.annoncer("Réponse privée annulée : le message ira au fil."); }}>
                  Revenir à une réponse publique
                </button>
              )}
              <button type="button" data-testid="sq-msg-brouillon"
                      onClick={async () => {
                        const garde = await ctx.appeler(`/api/communication/conversations/${encodeURIComponent(conversationId)}/brouillon`, {
                          method: "PUT", body: JSON.stringify({ corps }),
                        });
                        ctx.annoncer(garde
                          ? "Brouillon gardé. Il n'est lisible que de vous, et disparaît à l'envoi."
                          : "Brouillon effacé : le texte était vide.");
                        ctx.recharger();
                      }}>
                Garder comme brouillon
              </button>
            </div>
          </form>

          <Panneau titre="Participants du fil" testId="sq-msg-participants"
                   aide="Une personne retirée garde ce qu'elle a lu et ne voit pas la suite.">
            <TableauDonnees
              testId="sq-msg-part-table"
              lignes={actifs}
              ctx={ctx}
              tableau={{
                legende: "Participants actifs de la conversation",
                vide: "Aucun participant actif.",
                colonnes: [
                  {
                    entete: "Personne",
                    // Le visage puis le nom : un identifiant d'employé ne se reconnaît pas, et
                    // c'est en les recopiant qu'on écrit au mauvais collègue.
                    valeur: (l: Participant) => (
                      <span className="msg-personne">
                        <Avatar personId={l.personId} nom={nomDe(l.personId)} taille={28} />
                        {nomDe(l.personId)}
                      </span>
                    ),
                    identifiante: true,
                  },
                  { entete: "Depuis", valeur: (l: Participant) => quand(l.depuis) },
                ],
                actions: [{
                  libelle: (l: Participant) => `Retirer ${nomDe(l.personId)} de la conversation`,
                  testId: (l: Participant) => `sq-msg-retirer-${l.personId}`,
                  executer: async (l: Participant, c) => {
                    const fait = await c.appeler(
                      `/api/communication/conversations/${encodeURIComponent(conversationId)}/participants/${encodeURIComponent(l.personId)}`,
                      { method: "DELETE" });
                    if (fait) {
                      c.annoncer(`${l.personId} ne voit plus la suite du fil ; ce qu'il a lu lui reste.`);
                      c.recharger();
                    }
                  },
                }],
              }}
            />

            <form onSubmit={async (e) => {
              e.preventDefault();
              const ajoute = await ctx.appeler(
                `/api/communication/conversations/${encodeURIComponent(conversationId)}/participants`,
                { method: "POST", body: JSON.stringify({ personId: nouveauParticipant }) });
              if (ajoute) {
                ctx.annoncer(`${nouveauParticipant} participe désormais à la conversation.`);
                setNouveauParticipant("");
                ctx.recharger();
              }
            }}>
              <div className="sq-rangee">
                <ChampMembre ctx={ctx} libelle="Ajouter un participant" requis
                  testId="sq-msg-part-personne" valeur={nouveauParticipant}
                  onChange={setNouveauParticipant} />
              </div>
              <div className="sq-actions">
                <button type="submit" data-testid="sq-msg-part-ajouter">Ajouter au fil</button>
                <button type="button" data-testid="sq-msg-archiver"
                        disabled={Boolean(choisie?.archiveeLe)}
                        onClick={async () => {
                          const fait = await ctx.appeler(
                            `/api/communication/conversations/${encodeURIComponent(conversationId)}/archiver`,
                            { method: "POST" });
                          if (fait) {
                            ctx.annoncer("Conversation archivée : elle reste lisible et cherchable, on n'y écrit plus.");
                            ctx.recharger();
                          }
                        }}>
                  Archiver la conversation
                </button>
              </div>
            </form>
          </Panneau>
        </>
      )}
      </div>
    </div>
  );
}

// ── Chercher ─────────────────────────────────────────────────────────────────

function Chercher({ ctx }: { ctx: ContexteSection }) {
  const [terme, setTerme] = useState("");
  const [trouves, setTrouves] = useState<MessageLu[] | null>(null);

  return (
    <>
      <form onSubmit={async (e) => {
        e.preventDefault();
        const r = await ctx.appeler(`/api/communication/messages/recherche?q=${encodeURIComponent(terme)}`);
        setTrouves(Array.isArray(r) ? r : []);
        ctx.annoncer(`${Array.isArray(r) ? r.length : 0} message(s) trouvé(s) pour « ${terme} ».`);
      }}>
        <div className="sq-rangee">
          <div className="sq-champ">
            <label htmlFor="sq-msg-terme">Terme <span className="req-star" aria-hidden="true">*</span><span className="sr-only"> (obligatoire)</span></label>
            <input id="sq-msg-terme" required minLength={2} aria-describedby="sq-msg-terme-aide"
                   data-testid="sq-msg-terme" value={terme} onChange={(e) => setTerme(e.target.value)} />
            <p className="sq-champ-aide" id="sq-msg-terme-aide">
              La recherche ne porte que sur les conversations auxquelles vous participez.
            </p>
          </div>
        </div>
        <div className="sq-actions">
          <button type="submit" data-testid="sq-msg-chercher">Chercher dans mes messages</button>
        </div>
      </form>

      {trouves !== null && (
        <TableauDonnees
          testId="sq-msg-trouves"
          lignes={trouves}
          tableau={{
            legende: `Messages contenant « ${terme} »`,
            vide: "Aucun message ne contient ce terme dans vos conversations.",
            colonnes: [
              { entete: "Envoyé le", valeur: (l: MessageLu) => quand(l.envoyeLe), identifiante: true },
              { entete: "Auteur", valeur: (l: MessageLu) => l.auteurId },
              { entete: "Message", valeur: (l: MessageLu) => l.corps },
            ],
          }}
        />
      )}
    </>
  );
}

/**
 * **Les sections de la messagerie, nommées et exportées** — parce qu'elles ont deux hôtes.
 *
 * La messagerie du personnel se montre dans **deux** modules : les horaires cliniques (Santé
 * Québec) et Urgences-santé (AO 26-015-DO), où l'énoncé demande la communication entre le
 * personnel. Elle n'a pour autant qu'un seul foyer — celui-ci. Recopier les écrans aurait produit
 * deux messageries qui divergent au premier correctif, et c'est précisément ce que l'ADR 0034
 * interdit : une fonction a **un** lieu.
 *
 * Ce qui diffère entre les deux hôtes n'est pas le contenu, c'est la **mise en page** : ici les
 * sections sont rangées par onglets, parce que la page en porte quatre familles ; dans
 * Urgences-santé chaque famille est une page à son adresse (« un menu, une page »), et l'hôte
 * retire les onglets avec `sansOnglets`.
 */
export const SECTIONS_CONVERSATIONS: Section[] = [
    {
      titre: "Ouvrir une conversation",
      onglet: "Conversations",
      aide: "Vous en êtes participant d'office : un fil auquel son auteur n'aurait pas accès n'aurait aucun sens.",
      rendu: (ctx) => <Ouvrir ctx={ctx} />,
    },
    {
      titre: "Lire et écrire",
      onglet: "Conversations",
      source: () => "/api/communication/conversations",
      rendu: (ctx) => <Fil ctx={ctx} />,
    },
    {
      titre: "Mes conversations",
      onglet: "Conversations",
      source: () => "/api/communication/conversations",
      tableau: {
        legende: "Conversations auxquelles je participe",
        vide: "Aucune conversation. Une diffusion s'ouvre aussi depuis « Listes de distribution ».",
        colonnes: [
          { entete: "Sujet", valeur: (l: Conversation) => l.sujet, identifiante: true },
          { entete: "Type", valeur: (l: Conversation) => (l.type === "groupe" ? "de groupe" : "individuelle") },
          { entete: "Participants actifs",
            valeur: (l: Conversation) => String((l.participants ?? []).filter((p) => p.jusqua === null).length) },
          { entete: "Ouverte le", valeur: (l: Conversation) => quand(l.createdAt) },
          { entete: "Archivée", valeur: (l: Conversation) => (l.archiveeLe ? "oui" : "non") },
        ],
      },
    },
];

/** Appeler un collègue : l'autre famille de la communication, et une page à part dans Urgences-santé. */
export const SECTIONS_APPELS: Section[] = [
  {
      titre: "Appeler un collègue",
      onglet: "Appels",
      aide: "L'appel sonne chez la personne, qui décroche ou refuse. La bannière d'appel apparaît "
        + "sur toutes les pages : on n'a pas à rester sur cet écran pour être joignable.",
      rendu: (ctx) => <AppelsAvecNoms ctx={ctx} />,
    },
];

/** Retrouver un message, et reprendre un message commencé : deux gestes de la même page. */
export const SECTIONS_RECHERCHE: Section[] = [
  {
      titre: "Chercher dans mes messages",
      onglet: "Recherche",
      rendu: (ctx) => <Chercher ctx={ctx} />,
    },
  {
      titre: "Brouillons en attente de reprise",
      onglet: "Brouillons",
      aide: "Un message commencé puis laissé de côté. Il n'est lisible que de vous, et il disparaît dès l'envoi.",
      source: () => "/api/communication/brouillons",
      tableau: {
        legende: "Mes brouillons, par conversation",
        vide: "Aucun brouillon en attente.",
        colonnes: [
          { entete: "Conversation", valeur: (l: any) => l.conversationId, identifiante: true },
          { entete: "Début du texte", valeur: (l: any) => String(l.corps ?? "").slice(0, 80) },
          { entete: "Modifié le", valeur: (l: any) => quand(l.modifieLe) },
        ],
        actions: [{
          libelle: (l: any) => `Effacer le brouillon de la conversation ${l.conversationId}`,
          testId: (l: any) => `sq-msg-brouillon-effacer-${l.conversationId}`,
          executer: async (l: any, c) => {
            const fait = await c.appeler(`/api/communication/conversations/${l.conversationId}/brouillon`,
                                        { method: "DELETE" });
            if (fait !== undefined) { c.annoncer("Brouillon effacé."); c.recharger(); }
          },
        }],
      },
    },
];

export default page({
  cle: "sqmessages",
  titre: "Messagerie clinique",
  intro: "Les conversations auxquelles vous participez : écrire au fil, répondre à l'émetteur "
    + "seulement, ajouter ou retirer un participant, archiver. Une réponse privée dit qui la verra "
    + "avant l'envoi.",
  sections: [...SECTIONS_CONVERSATIONS, ...SECTIONS_APPELS, ...SECTIONS_RECHERCHE],
});
