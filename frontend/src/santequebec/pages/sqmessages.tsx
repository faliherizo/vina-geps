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
import { Fragment, useEffect, useMemo, useState } from "react";
import { Panneau, TableauDonnees, page, type ContexteSection, type Section } from "../noyau";
import { ChampMembre } from "../references";
import { ChampPieces, tailleLisible, type PieceLocale, type LimitesPieces } from "../../SoutienPieces";
import { downloadApiFile } from "../../FileViewerModal";
import { AppelsSq, signalerAppelLance } from "../appels";
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
  /** Les réactions posées, par type : le compte, et si l'une d'elles est la mienne. */
  reactions?: ReactionLue[];
  editeLe?: string | null;
  modifie?: boolean;
  supprime?: boolean;
  supprimeLe?: string | null;
  supprimePar?: string | null;
  /** L'acheminement, servi à l'auteur seul : envoyé, reçu par N, lu par N. */
  statut?: { destinataires: number; recus: number; lus: number; libelle: string } | null;
}
interface VersionLue { version: number; corps: string; ecritLe: string; remplaceLe: string | null; courante: boolean }
interface ReactionLue { reaction: CodeReaction; nombre: number; parMoi: boolean }

/**
 * **Les réactions, un ensemble fermé** (REFONTE-MESSAGERIE.md § 2).
 *
 * Fermé, parce qu'une réaction libre deviendrait un second canal de messages, non journalisé ; et
 * chaque réaction porte ses **mots**, que l'assistance technique lit à la place de l'émoji.
 */
type CodeReaction = "pouce" | "fait" | "merci" | "regarde";
const REACTIONS: ReadonlyArray<{ code: CodeReaction; emoji: string; mots: string }> = [
  { code: "pouce", emoji: "👍", mots: "bien reçu" },
  { code: "fait", emoji: "✅", mots: "fait" },
  { code: "merci", emoji: "❤️", mots: "merci" },
  { code: "regarde", emoji: "👀", mots: "je regarde" },
];

/** Le jour d'un horodatage, pour séparer le fil comme une conversation : « Aujourd'hui », « Hier », sinon la date. */
export function jourDe(iso: string, maintenant = new Date()): string {
  const jour = String(iso ?? "").slice(0, 10);
  const aujourdhui = maintenant.toISOString().slice(0, 10);
  const hier = new Date(maintenant.getTime() - 86_400_000).toISOString().slice(0, 10);
  if (jour === aujourdhui) return "Aujourd'hui";
  if (jour === hier) return "Hier";
  return jour;
}

/**
 * **Qui je suis pour la messagerie** : le nom de connexion, comme le serveur (`GardeCommunication.acteurDe`).
 * Comparer l'auteur d'un message à l'`id` du compte ne reconnaissait jamais mes propres messages.
 */
export function acteurDe(user: { id?: string; username?: string } | null | undefined): string {
  return user?.username ?? user?.id ?? "";
}

/** L'heure d'un horodatage ISO, lisible sans décoder : « 2027-03-04 08:15 ». */
const quand = (iso: string | null | undefined) => String(iso ?? "").replace("T", " ").slice(0, 16);

// ── Ouvrir une conversation ──────────────────────────────────────────────────

/**
 * **Un fil vient d'être ouvert : le montrer.** Les sections de l'écran sont indépendantes ; ce
 * signal porte l'identifiant du fil créé jusqu'à la section « Lire et écrire », qui l'ouvre dès
 * que la liste rechargée le contient. Sans lui, on lisait « choisissez-la dans la liste » — un
 * second geste pour un fil qu'on venait de demander (relevé du 14 novembre 2026).
 */
const FIL_OUVERT = "vina:fil-ouvert";
const signalerFilOuvert = (id: string) => window.dispatchEvent(new CustomEvent<string>(FIL_OUVERT, { detail: id }));

function Ouvrir({ ctx }: { ctx: ContexteSection }) {
  const { user } = useAuth();
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
        signalerFilOuvert(creee.id);
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
        <ChampMembre ctx={ctx} libelle="Premier participant" testId="sq-msg-participant" exclure={acteurDe(user)}
          valeur={participants} onChange={setParticipants} />
      </div>
      <div className="sq-actions">
        <button type="submit" data-testid="sq-msg-ouvrir">Ouvrir la conversation</button>
      </div>
    </form>
  );
}

/**
 * **Écrire à une personne** — le geste le plus fréquent, réduit à deux temps.
 *
 * Ouvrir un fil de groupe demande un sujet et une portée ; écrire à Marc ne demande que Marc. Le
 * serveur rend le fil à deux **existant** s'il y en a un : écrire deux fois à la même personne ne
 * disperse plus la conversation dans deux fils.
 */
function EcrireA({ ctx }: { ctx: ContexteSection }) {
  const [personne, setPersonne] = useState("");
  const nomDe = useNomsDuGroupe(ctx);
  const { user } = useAuth();

  return (
    <form onSubmit={async (e) => {
      e.preventDefault();
      const fil = await ctx.appeler("/api/communication/conversations/directe", {
        method: "POST", body: JSON.stringify({ personId: personne }),
      });
      if (fil?.id) {
        ctx.annoncer(`Conversation avec ${nomDe(personne)} ouverte : écrivez-lui dans « Lire et écrire ».`);
        setPersonne("");
        ctx.recharger();
        signalerFilOuvert(fil.id);
      }
    }}>
      <div className="sq-rangee">
        {/* On ne s'écrit pas à soi-même : la personne connectée n'est pas proposée. Sans `requis`,
            l'option « — Choisir — » reste, et le bouton attend un vrai choix plutôt que d'afficher
            le premier nom de la liste sans qu'il soit choisi. */}
        <ChampMembre ctx={ctx} libelle="Personne" testId="sq-msg-directe-personne" exclure={acteurDe(user)}
          valeur={personne} onChange={setPersonne} />
      </div>
      <div className="sq-actions">
        <button type="submit" data-testid="sq-msg-directe" disabled={!personne}>Écrire à cette personne</button>
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
    noms[personId] ?? (personId === acteurDe(user) ? "Moi" : personId)
  ), [noms, user]);
}

/** Les filtres de la liste des fils (maquette : Tous · Non lus · Groupes · Archivés). */
type Filtre = "tous" | "non-lus" | "groupes" | "archives";
const FILTRES: ReadonlyArray<{ code: Filtre; libelle: string }> = [
  { code: "tous", libelle: "Tous" }, { code: "non-lus", libelle: "Non lus" },
  { code: "groupes", libelle: "Groupes" }, { code: "archives", libelle: "Archivés" },
];

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
  const moiId = acteurDe(user);
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
  const [filtre, setFiltre] = useState<Filtre>("tous");
  /** Le message en cours de modification, et son texte. */
  const [edition, setEdition] = useState<{ id: string; corps: string } | null>(null);
  /** Le message dont la suppression attend confirmation : un clic de trop ne supprime rien. */
  const [aSupprimer, setASupprimer] = useState("");
  /** L'historique déplié, par message. */
  const [historiques, setHistoriques] = useState<Record<string, VersionLue[]>>({});
  const [recherche, setRecherche] = useState("");

  const choisie = conversations.find((c) => c.id === conversationId);
  const actifs = (choisie?.participants ?? []).filter((p) => p.jusqua === null);

  const charger = async (id: string) => {
    if (!id) { setMessages([]); return; }
    const lus = await ctx.appeler(`/api/communication/conversations/${encodeURIComponent(id)}/messages`);
    setMessages(Array.isArray(lus) ? lus : []);
  };

  useEffect(() => { void charger(conversationId); }, [conversationId]);  // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { void rechargerFils(); }, [conversations.length]);     // eslint-disable-line react-hooks/exhaustive-deps
  // Un fil tout juste ouvert ailleurs dans l'écran s'ouvre ici, dès que la liste le contient.
  const [aOuvrir, setAOuvrir] = useState("");
  useEffect(() => {
    const noter = (e: Event) => setAOuvrir((e as CustomEvent<string>).detail);
    window.addEventListener(FIL_OUVERT, noter);
    return () => window.removeEventListener(FIL_OUVERT, noter);
  }, []);
  useEffect(() => {
    if (!aOuvrir || !conversations.some((c) => c.id === aOuvrir)) return;
    setConversationId(aOuvrir); setPriveA(""); setRepondA(""); setAOuvrir("");
  }, [aOuvrir, conversations]);

  if (conversations.length === 0) {
    return <p className="sq-vide">Aucune conversation. Ouvrez-en une ci-dessus.</p>;
  }

  const ouvrir = (id: string) => { setConversationId(id); setPriveA(""); setRepondA(""); };
  const parId = new Map(conversations.map((c) => [c.id, c]));
  const filsVisibles = fils.filter((f) => {
    const c = parId.get(f.conversationId);
    if (filtre === "non-lus" && f.nonLus === 0) return false;
    if (filtre === "groupes" && c?.type !== "groupe") return false;
    if (filtre === "archives" ? !c?.archiveeLe : Boolean(c?.archiveeLe)) return false;
    const t = recherche.trim().toLocaleLowerCase("fr");
    return !t || `${f.sujet} ${f.dernierExtrait ?? ""}`.toLocaleLowerCase("fr").includes(t);
  });
  const autre = choisie?.type === "individuelle" ? actifs.find((p) => p.personId !== moiId) : undefined;

  const enregistrerModification = async () => {
    if (!edition) return;
    const fait = await ctx.appeler(`/api/communication/messages/${encodeURIComponent(edition.id)}`, {
      method: "PUT", body: JSON.stringify({ corps: edition.corps }),
    });
    if (fait) {
      ctx.annoncer("Message modifié : la version précédente reste dans son historique.");
      setEdition(null);
      setHistoriques((h) => { const n = { ...h }; delete n[fait.id]; return n; });
      await charger(conversationId);
    }
  };

  const supprimer = async (id: string) => {
    const fait = await ctx.appeler(`/api/communication/messages/${encodeURIComponent(id)}`, { method: "DELETE" });
    setASupprimer("");
    if (fait) {
      ctx.annoncer("Message supprimé : il garde sa place dans le fil, son texte ne se lit plus.");
      await charger(conversationId);
    }
  };

  const basculerHistorique = async (id: string) => {
    if (historiques[id]) {
      setHistoriques((h) => { const n = { ...h }; delete n[id]; return n; });
      return;
    }
    const versions = await ctx.appeler(`/api/communication/messages/${encodeURIComponent(id)}/historique`);
    if (Array.isArray(versions)) setHistoriques((h) => ({ ...h, [id]: versions as VersionLue[] }));
  };

  /** Réagir, ou retirer sa réaction : le serveur bascule, l'écran relit le fil. */
  const reagir = async (m: MessageLu, r: (typeof REACTIONS)[number]) => {
    const fait = await ctx.appeler(`/api/communication/messages/${encodeURIComponent(m.id)}/reactions`, {
      method: "POST", body: JSON.stringify({ reaction: r.code }),
    });
    if (fait) {
      ctx.annoncer(fait.pose ? `Réaction « ${r.mots} » posée.` : `Réaction « ${r.mots} » retirée.`);
      await charger(conversationId);
    }
  };

  /** Appeler depuis le fil : la personne d'un fil à deux, ou tout le groupe. */
  const appelerDepuisLeFil = async () => {
    if (!choisie) return;
    const lance = autre
      ? await ctx.appeler("/api/communication/appels", {
          method: "POST", body: JSON.stringify({ appeleId: autre.personId, conversationId: choisie.id }),
        })
      : await ctx.appeler(`/api/communication/conversations/${encodeURIComponent(choisie.id)}/appel`, { method: "POST" });
    if (lance) {
      signalerAppelLance();
      ctx.annoncer(autre
        ? `Appel lancé : ça sonne chez ${nomDe(autre.personId)}.`
        : `Appel de groupe lancé : ${actifs.length - 1} participant(s) invité(s).`);
    }
  };

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
        <div className="sq-champ">
          <label htmlFor="sq-msg-filtre-texte">Chercher un fil</label>
          <input id="sq-msg-filtre-texte" type="search" data-testid="sq-msg-filtre-texte" value={recherche}
                 onChange={(e) => setRecherche(e.target.value)} />
        </div>
        {/* Des boutons à bascule : l'état choisi se dit par `aria-pressed`, pas par la seule couleur. */}
        <div className="msg-filtres" role="group" aria-label="Filtrer les conversations">
          {FILTRES.map((f) => (
            <button key={f.code} type="button" aria-pressed={filtre === f.code}
                    className={`msg-filtre${filtre === f.code ? " msg-filtre-actif" : ""}`}
                    data-testid={`sq-msg-filtre-${f.code}`} onClick={() => setFiltre(f.code)}>
              {f.libelle}
            </button>
          ))}
        </div>
        <ListeConversations fils={filsVisibles} choisi={conversationId} onChoisir={ouvrir} nomDe={nomDe} />
        {filsVisibles.length === 0 && fils.length > 0 && (
          <p className="sq-vide" data-testid="sq-msg-filtre-vide">Aucun fil ne correspond à ce filtre.</p>
        )}
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
          <div className="msg-entete-fil" data-testid="sq-msg-entete-fil">
            <Avatar personId={autre?.personId ?? choisie?.id ?? ""} nom={autre ? nomDe(autre.personId) : (choisie?.sujet ?? "")} taille={40} />
            <div className="msg-entete-fil-texte">
              <h3 className="msg-entete-fil-titre">{autre ? nomDe(autre.personId) : choisie?.sujet}</h3>
              <p className="msg-entete-fil-sous">
                {choisie?.type === "groupe"
                  ? `${actifs.length} participant(s) : ${actifs.slice(0, 3).map((p) => nomDe(p.personId)).join(", ")}${actifs.length > 3 ? "…" : ""}`
                  : "Conversation à deux"}
                {choisie?.archiveeLe ? " · archivée" : ""}
              </p>
            </div>
            {!choisie?.archiveeLe && (
              <button type="button" className="msg-appeler" data-testid="sq-msg-appeler" onClick={() => { void appelerDepuisLeFil(); }}>
                <span aria-hidden="true">📞</span> {autre ? `Appeler ${nomDe(autre.personId)}` : "Appeler le groupe"}
              </button>
            )}
          </div>
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
            {messages.map((m, i) => {
              const moi = m.auteurId === moiId;
              const auteur = nomDe(m.auteurId);
              const jour = jourDe(m.envoyeLe);
              const nouveauJour = i === 0 || jourDe(messages[i - 1].envoyeLe) !== jour;
              return (
                <Fragment key={m.id}>
                {nouveauJour && (
                  <li className="msg-jour" data-testid={`sq-msg-jour-${String(m.envoyeLe).slice(0, 10)}`}>
                    <span>{jour}</span>
                  </li>
                )}
                <li className={`msg-bulle${moi ? " msg-bulle-moi" : ""}${m.priveA ? " msg-bulle-privee" : ""}`}>
                  <Avatar personId={m.auteurId} nom={auteur} taille={36} />
                  <div className="msg-corps">
                    <p className="msg-entete">
                      <span className="msg-auteur">{auteur}</span>
                      <span className="msg-heure">{quand(m.envoyeLe)}</span>
                      {/* La portée est écrite, jamais rendue par une seule couleur (WCAG 1.4.1). */}
                      <span className="msg-portee">{m.priveA ? <><span aria-hidden="true">🔒 </span>Privé à {nomDe(m.priveA)}</> : "tout le fil"}</span>
                      {/* L'état de lecture **en toutes lettres** : une pastille muette se devine
                          (WCAG 1.4.1), et un fil exporté perd les couleurs mais garde les mots. */}
                      {!moi && (
                        <span className={m.luParMoi ? "msg-lu" : "msg-non-lu"}
                              data-testid={`sq-msg-etat-${m.id}`}>
                          {m.luParMoi ? "Lu" : "Non lu"}
                        </span>
                      )}
                    </p>
                    {m.supprime ? (
                      <p className="msg-texte msg-supprime" data-testid={`sq-msg-supprime-${m.id}`}>
                        Message supprimé par {nomDe(m.supprimePar ?? "")} le {quand(m.supprimeLe)}.
                      </p>
                    ) : edition?.id === m.id ? (
                      <form className="msg-edition" onSubmit={(e) => { e.preventDefault(); void enregistrerModification(); }}>
                        <label htmlFor={`sq-msg-edition-${m.id}`}>Modifier le message</label>
                        <textarea id={`sq-msg-edition-${m.id}`} data-testid={`sq-msg-edition-${m.id}`} rows={3} required
                                  value={edition.corps} onChange={(e) => setEdition({ id: m.id, corps: e.target.value })} />
                        <span className="msg-edition-gestes">
                          <button type="submit" data-testid={`sq-msg-edition-enregistrer-${m.id}`}>Enregistrer la modification</button>
                          <button type="button" data-testid={`sq-msg-edition-annuler-${m.id}`} onClick={() => setEdition(null)}>Annuler</button>
                        </span>
                      </form>
                    ) : (
                      <p className="msg-texte">{m.corps}</p>
                    )}
                    {/* L'état d'un message, **en mots** : envoyé, reçu, lu — pour l'auteur seul (WCAG 1.4.1). */}
                    {!m.supprime && (m.statut || m.modifie) && (
                      <p className="msg-etat">
                        {m.statut && <span data-testid={`sq-msg-statut-${m.id}`}>{m.statut.libelle}</span>}
                        {m.modifie && (
                          <button type="button" className="sq-lien" aria-expanded={Boolean(historiques[m.id])}
                                  data-testid={`sq-msg-historique-${m.id}`} onClick={() => { void basculerHistorique(m.id); }}>
                            Modifié le {quand(m.editeLe)} · {historiques[m.id] ? "masquer l'historique" : "voir l'historique"}
                          </button>
                        )}
                      </p>
                    )}
                    {historiques[m.id] && (
                      <ol className="msg-historique" data-testid={`sq-msg-versions-${m.id}`} aria-label="Versions du message">
                        {historiques[m.id].map((v) => (
                          <li key={v.version}>
                            <span className="msg-heure">Version {v.version} · {quand(v.ecritLe)}{v.courante ? " (actuelle)" : ""}</span>
                            <span className="msg-historique-texte">{v.corps}</span>
                          </li>
                        ))}
                      </ol>
                    )}
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
                    {!choisie?.archiveeLe && !m.supprime && (
                      <p className="msg-reactions" data-testid={`sq-msg-reactions-${m.id}`}>
                        {REACTIONS.map((r) => {
                          const lue = (m.reactions ?? []).find((x) => x.reaction === r.code);
                          const nombre = lue?.nombre ?? 0;
                          return (
                            <button key={r.code} type="button" className="msg-reaction" aria-pressed={Boolean(lue?.parMoi)}
                                    data-testid={`sq-msg-reagir-${r.code}-${m.id}`}
                                    aria-label={`${r.mots} : ${nombre} personne(s)${lue?.parMoi ? ", dont vous" : ""}`}
                                    onClick={() => { void reagir(m, r); }}>
                              <span aria-hidden="true">{r.emoji}</span>
                              {nombre > 0 && <span aria-hidden="true"> {nombre}</span>}
                            </button>
                          );
                        })}
                      </p>
                    )}
                    {!m.supprime && <p className="msg-gestes">
                      {moi && !choisie?.archiveeLe && edition?.id !== m.id && (
                        <button type="button" className="sq-lien" data-testid={`sq-msg-modifier-${m.id}`}
                                onClick={() => { setEdition({ id: m.id, corps: m.corps }); setASupprimer(""); }}>
                          Modifier<span className="sr-only"> votre message de {quand(m.envoyeLe)}</span>
                        </button>
                      )}
                      {moi && !choisie?.archiveeLe && aSupprimer !== m.id && (
                        <button type="button" className="sq-lien" data-testid={`sq-msg-supprimer-${m.id}`} onClick={() => setASupprimer(m.id)}>
                          Supprimer<span className="sr-only"> votre message de {quand(m.envoyeLe)}</span>
                        </button>
                      )}
                      {aSupprimer === m.id && (
                        <span role="group" aria-label="Confirmer la suppression">
                          <button type="button" className="sq-lien msg-danger" data-testid={`sq-msg-supprimer-confirmer-${m.id}`}
                                  onClick={() => { void supprimer(m.id); }}>
                            Confirmer la suppression
                          </button>
                          <button type="button" className="sq-lien" data-testid={`sq-msg-supprimer-annuler-${m.id}`} onClick={() => setASupprimer("")}>
                            Garder le message
                          </button>
                        </span>
                      )}
                      {!moi && !choisie?.archiveeLe && (
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
                    </p>}
                  </div>
                </li>
                </Fragment>
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
      titre: "Écrire à une personne",
      onglet: "Conversations",
      aide: "Si vous avez déjà un fil avec cette personne, c'est lui qui s'ouvre : jamais deux fils à deux.",
      rendu: (ctx) => <EcrireA ctx={ctx} />,
    },
    {
      titre: "Ouvrir une conversation de groupe",
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
