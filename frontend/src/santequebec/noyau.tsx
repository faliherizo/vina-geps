/**
 * **Noyau des pages Santé Québec** — une page par entrée de menu.
 *
 * Le module d'horaires cliniques existant tient dans un seul écran à vingt-et-un onglets. Ce
 * noyau sert la structure demandée par l'appel d'offres 2027-005 : *un menu ou sous-menu, une
 * page*. Chaque page est un fichier, décrit par un **descripteur** — son titre, son intention, le
 * périmètre dont elle a besoin, et ses sections.
 *
 * Pourquoi un descripteur plutôt que du JSX dans chaque page : les règles d'accessibilité
 * (`<caption>`, `scope`, régions `role="status"`, libellés liés, états vides nommés) sont écrites
 * **une fois**, ici. Trente-quatre pages qui les réécrivent chacune à leur façon, c'est
 * trente-quatre occasions d'en oublier une — et l'oubli ne se voit qu'à l'audit. Une page qui a
 * besoin d'autre chose garde la main : `rendu` lui rend le JSX.
 *
 * Références : `docs/sante-quebec/2027-005/guide_wcag_html_css_accessibilite.md` (structure,
 * formulaires, tableaux, messages d'état) et `…_analyse_besoins_maquettes_UI.md` (contenu des
 * écrans).
 */
import { useCallback, useEffect, useId, useMemo, useRef, useState, type ReactNode } from "react";
import { useAuth } from "../AuthContext";
import { useCurrentTenant } from "../organismeCourant";
import { allerAPage, lienVersPage, useOnglet } from "../navigation";
import Onglets from "../composants/Onglets";
import { useT } from "../i18n";
import { BarrePerimetre, EVENEMENT_PERIMETRE, type Perimetre, useePerimetre } from "./perimetre";
import "./styles.css";
import { BandeauMaquette } from "../composants/PageMaquette";

// ── Descripteur d'une page ───────────────────────────────────────────────────

/** Ce dont une page a besoin pour poser ses questions au serveur. */
export type BesoinPerimetre = "groupe";

export interface Colonne<L = any> {
  /** Entête de colonne, lue par les lecteurs d'écran avant chaque cellule (`scope="col"`). */
  entete: string;
  /** La valeur affichée. Rendre `null` ou `undefined` affiche « — », jamais une cellule vide muette. */
  valeur: (ligne: L) => ReactNode;
  /** Vrai pour la colonne qui identifie la ligne : elle devient `<th scope="row">`. */
  identifiante?: boolean;
}

export interface Tableau<L = any> {
  /** Le `<caption>` : ce que le tableau montre, pour qui ne voit pas la page. */
  legende: string;
  colonnes: Colonne<L>[];
  /** Ce qu'on dit quand il n'y a rien. « Aucune donnée » n'informe pas ; dire pourquoi, oui. */
  vide: string;
  /** Les lignes, extraites de la réponse — une réponse qui n'est pas une liste est une erreur. */
  lignes?: (donnees: unknown) => L[];
  /**
   * Les **gestes offerts sur chaque ligne** : décider, prendre, retirer.
   *
   * Posé ici parce que le défaut mesuré le 20 octobre était exactement là : vingt-quatre écrans
   * montraient des données sans permettre d'agir dessus, et chacun aurait dû réinventer sa colonne
   * de boutons. Une seule implémentation suffit, et elle porte l'accessibilité une fois pour
   * toutes : un intitulé qui **nomme la ligne**, sans quoi vingt boutons s'appellent « Accepter »
   * et un lecteur d'écran ne sait plus lequel il annonce (WCAG 2.4.9).
   */
  actions?: Array<Action<L>>;
}

export interface Action<L = any> {
  /** L'intitulé, construit depuis la ligne : « Accepter l'échange de Dre Roy ». */
  libelle: (ligne: L) => string;
  /** Identifiant d'essai, construit depuis la ligne pour rester unique dans le tableau. */
  testId: (ligne: L) => string;
  /** Ce que le geste fait. Il reçoit le contexte de la section : appeler, annoncer, recharger. */
  executer: (ligne: L, ctx: ContexteSection) => Promise<void>;
  /** Vrai quand le geste ne s'applique pas à cette ligne : le bouton n'est alors pas rendu. */
  masquer?: (ligne: L) => boolean;
}

export interface Section {
  titre: string;
  /**
   * **L'onglet qui porte cette section** — son libellé, tel qu'il s'affiche.
   *
   * Un écran de onze sections se lit mal : on y défile pour trouver, et l'on ne sait jamais ce
   * qu'on n'a pas encore vu. Dès qu'une page déclare des onglets, le noyau n'en rend qu'un à la
   * fois, avec le motif ARIA « tabs » du dépôt (`composants/Onglets`) : flèches, `aria-selected`,
   * panneau lié. L'onglet actif vit dans l'URL (`?onglet=`), donc un lien se partage.
   *
   * Une page sans aucun `onglet` garde son flux unique : deux ou trois sections n'ont pas besoin
   * d'être rangées. La règle est éprouvée (`onglets-sante-quebec.test.ts`) : ou bien **aucune**
   * section n'en déclare, ou bien **toutes**. Une page à moitié rangée cache la moitié de son
   * contenu sans que rien ne le dise.
   */
  onglet?: string;
  /** L'intention de la section, en une ou deux phrases. */
  aide?: string;
  /** Chemin d'API ; `null` ⇒ le périmètre n'est pas encore choisi, la section le dit. */
  source?: (p: Perimetre) => string | null;
  tableau?: Tableau;
  /** Rendu libre, pour une section qui n'est pas un tableau (formulaire, cartes, actions). */
  rendu?: (ctx: ContexteSection) => ReactNode;
  /** Section à bâtir : ce qu'elle contiendra, et où la fonction vit aujourd'hui s'il y a un ailleurs. */
  avenir?: AVenir;
}

export interface AVenir {
  /** Les besoins de l'énoncé que la page servira (BF-xxx), pour que le lien soit vérifiable. */
  besoins: string[];
  /** Le contenu prévu, tel que la maquette le décrit. */
  contenu: string[];
  /** Ce qui manque côté serveur ou côté produit pour la câbler. */
  manque: string;
  /** Là où la fonction se fait aujourd'hui, s'il existe un chemin. */
  entretemps?: string;
}

/**
 * **Un indicateur de tête de page** (maquettes UI v1.0, bandeau de quatre cartes).
 *
 * Il résume ce que la page sert, en un nombre. `detail` porte la nuance — « 3 nécessitent une
 * décision » — qui distingue un chiffre qu'on regarde d'un chiffre sur lequel on agit.
 *
 * `valeur` accepte une fonction des données de la page : un indicateur figé dans le descripteur
 * serait un chiffre décoratif, et un chiffre décoratif en tête d'écran se prend pour une mesure.
 */
export interface Indicateur {
  libelle: string;
  valeur: (donnees: unknown) => string;
  detail?: (donnees: unknown) => string;
  /** La teinte de `detail` : elle **accompagne** le mot, elle ne le remplace pas (WCAG 1.4.1). */
  ton?: "neutre" | "succes" | "attente" | "refus" | "info";
  testId: string;
}

/** Une étape du parcours d'un objet — « Brouillon → Proposé → À approuver → Finalisé ». */
export interface EtapeParcours {
  titre: string;
  detail: string;
}

export interface DescripteurPage {
  cle: string;
  /**
   * **Le module qui montre cette page**, tel que le bandeau le nomme. « Santé Québec » par défaut.
   *
   * La messagerie du personnel a deux hôtes — les horaires cliniques et Urgences-santé — et un
   * seul foyer (voir `sqmessages.tsx`). Le bandeau nommait « Santé Québec » en dur : la même
   * messagerie ouverte depuis le menu d'Urgences-santé affichait donc le nom de l'autre module,
   * ce qui fait douter d'avoir changé d'application plutôt que de page.
   */
  module?: string;
  /** Titre de la page — le `<h1>`, un seul par page. */
  titre: string;
  /** Une phrase : à quoi sert cet écran, et pour qui. */
  intro: string;
  perimetre?: BesoinPerimetre[];
  /**
   * Le bandeau d'indicateurs, et d'où il tire ses nombres.
   *
   * `source` est lue **une fois pour la page**, indépendamment des sections : les maquettes
   * montrent des compteurs en tête, et les faire dépendre d'une section les ferait disparaître
   * quand celle-ci est vide.
   */
  indicateurs?: { source: (p: Perimetre) => string | null; cartes: Indicateur[] };
  /**
   * Le parcours d'un objet de cette page, rendu en bas : ce que chaque état veut dire, et dans
   * quel ordre ils se suivent. Les maquettes le posent sur les écrans où l'état se discute —
   * un échange, une publication, une demande.
   */
  parcours?: { titre: string; intro: string; etapes: EtapeParcours[] };
  sections: Section[];
}

export interface ContexteSection {
  perimetre: Perimetre;
  /** Les données de `source`, quand la section en a une. */
  donnees: unknown;
  /** Relance le chargement de la section — après une action qui change l'état. */
  recharger: () => void;
  /** Un appel d'API qui rend le corps, ou `null` en cas de refus (le motif est affiché). */
  appeler: (chemin: string, init?: RequestInit) => Promise<any | null>;
  /** Annonce un succès dans la région `role="status"` de la page. */
  annoncer: (message: string) => void;
  /** Annonce un échec dans la région `role="alert"` de la page. */
  signaler: (message: string) => void;
}

// ── Accès à l'API ────────────────────────────────────────────────────────────

/**
 * Appels d'API du module, avec l'établissement courant.
 *
 * Même convention que le reste de l'application : l'en-tête `X-Tenant-Id` accompagne chaque
 * requête, et un refus rend `null` après avoir livré son motif — un `catch` muet ferait croire à
 * une page vide là où le serveur a dit pourquoi.
 */
export function useApiSq() {
  const { apiFetch } = useAuth();
  const { tenantId } = useCurrentTenant();

  return useCallback(async (chemin: string, init: RequestInit = {}) => {
    const headers = new Headers(init.headers ?? {});
    if (tenantId) headers.set("X-Tenant-Id", tenantId);
    if (init.body) headers.set("Content-Type", "application/json");
    const r = await apiFetch(chemin, { ...init, headers });
    if (r.ok) {
      /**
       * Un geste qui touche aux **groupes** ou aux **périodes** change le périmètre de travail de
       * tout le module : l'annoncer ici évite que chaque page y pense. Créer un groupe laissait
       * sinon le sélecteur sur « — Choisir — » jusqu'au rechargement de la page, et la nouvelle
       * période restait introuvable.
       */
      const methode = (init.method ?? "GET").toUpperCase();
      if (methode !== "GET" && /\/api\/communication\/groupes/.test(chemin)) {
        window.dispatchEvent(new Event(EVENEMENT_PERIMETRE));
      }
      return r.status === 204 ? null : await r.json().catch(() => null);
    }
    const j = await r.json().catch(() => ({}));
    const motif = Array.isArray(j?.errors) ? j.errors.join(" · ") : `Erreur ${r.status}`;
    throw new Error(motif);
  }, [apiFetch, tenantId]);
}

/**
 * Le même appel, **brut**.
 *
 * `useApiSq` rend le corps déjà lu et lève sur un refus : commode pour une page, inutilisable pour
 * un composant qui doit lire `r.ok` lui-même — l'autocomplétion, qui ne doit pas faire éclater un
 * formulaire parce qu'une suggestion a échoué. L'en-tête d'établissement reste écrit une seule
 * fois : c'est la raison d'être de ce voisinage.
 */
export function useApiSqBrut() {
  const { apiFetch } = useAuth();
  const { tenantId } = useCurrentTenant();

  return useCallback((chemin: string, init: RequestInit = {}) => {
    const headers = new Headers(init.headers ?? {});
    if (tenantId) headers.set("X-Tenant-Id", tenantId);
    if (init.body) headers.set("Content-Type", "application/json");
    return apiFetch(chemin, { ...init, headers });
  }, [apiFetch, tenantId]);
}

// ── Briques des maquettes (pack UI v1.0) ─────────────────────────────────────

/*
 * Il n'y a **pas** de fil d'Ariane ici, et c'est délibéré.
 *
 * L'application en rend un (`composants/FilAriane`, Section › Notion › Vue), avec des liens qui
 * remontent. Celui que ce module rendait en doublait le premier niveau juste en dessous, sans
 * lien, et son `<ol>` n'était pas remis à zéro : la page des échanges affichait « 1. » et « 2. »
 * l'un sous l'autre — visible sur la capture du 25 octobre 2026, et absent de la maquette P17.
 * Deux fils d'Ariane pour une page, c'est un de trop, quel que soit le mieux balisé.
 */

const TONS_PASTILLE: Record<string, string> = {
  succes: "sq-pastille-succes", attente: "sq-pastille-attente",
  refus: "sq-pastille-refus", info: "sq-pastille-info", neutre: "",
};

/**
 * **Une pastille d'état** : un mot, et une teinte qui l'accompagne.
 *
 * La couleur ne porte jamais le sens seule (WCAG 1.4.1). C'est aussi ce qui rend l'état lisible
 * dans un export, où la teinte n'existe pas — et c'est le cas le plus fréquent en appel d'offres.
 */
export function Pastille(
  { texte, ton = "neutre", testId }:
  { texte: string; ton?: keyof typeof TONS_PASTILLE; testId?: string },
) {
  return (
    <span className={["sq-pastille", TONS_PASTILLE[ton]].filter(Boolean).join(" ")}
          data-testid={testId}>
      {texte}
    </span>
  );
}

/**
 * **Le bandeau d'indicateurs** de tête de page.
 *
 * Une liste, et non une rangée de `div` : quatre cartes sont quatre éléments de même nature, et
 * l'annoncer permet d'en connaître le nombre sans les parcourir. Tant que la source n'a pas
 * répondu, la valeur est **« — »** et non zéro : un zéro inventé se lit comme une mesure.
 */
function CartesIndicateurs(
  { cartes, donnees, pret }:
  { cartes: Indicateur[]; donnees: unknown; pret: boolean },
) {
  const TONS_TEXTE: Record<string, string> = {
    succes: "var(--sq-succes-texte)", attente: "var(--sq-attente-texte)",
    refus: "var(--sq-refus-texte)", info: "var(--sq-info-texte)", neutre: "var(--sq-doux)",
  };
  return (
    <ul className="sq-indicateurs" aria-label="Indicateurs de l'écran">
      {cartes.map((c) => (
        <li key={c.testId} className="sq-indicateur" data-testid={c.testId}>
          <p className="sq-indicateur-libelle">{c.libelle}</p>
          <p className="sq-indicateur-valeur">{pret ? c.valeur(donnees) : "—"}</p>
          {c.detail && (
            <p className="sq-indicateur-detail" style={{ color: TONS_TEXTE[c.ton ?? "neutre"] }}>
              {pret ? c.detail(donnees) : "en cours de lecture"}
            </p>
          )}
        </li>
      ))}
    </ul>
  );
}

/**
 * **Le parcours d'un objet** : ses états, dans l'ordre.
 *
 * Une liste **ordonnée** — l'ordre est l'information. Les numéros sont dessinés par la feuille de
 * style à partir d'un compteur, et non écrits dans le balisage : les écrire ferait lire « 1 un »
 * à chaque étape.
 */
function Parcours({ titre, intro, etapes }: { titre: string; intro: string; etapes: EtapeParcours[] }) {
  // `Panneau` plutôt qu'une `<section>` de plus : une seule façon de rendre une section titrée,
  // donc un seul endroit où tenir la liaison entre le titre et la région qu'il nomme.
  return (
    <Panneau titre={titre} aide={intro} testId="sq-parcours">
      <ol className="sq-parcours">
        {etapes.map((e) => (
          <li key={e.titre}>
            <span className="sq-parcours-titre">{e.titre}</span>
            <span className="sq-parcours-detail">{e.detail}</span>
          </li>
        ))}
      </ol>
    </Panneau>
  );
}

// ── Briques accessibles ──────────────────────────────────────────────────────

/** Un panneau : une `<section>` nommée par son titre — la relation est portée par le balisage. */
export function Panneau(
  { titre, aide, testId, children }:
  { titre: string; aide?: string; testId?: string; children: ReactNode },
) {
  const id = useId();
  return (
    <section className="sq-panneau" aria-labelledby={id} data-testid={testId}>
      <h2 id={id}>{titre}</h2>
      {aide && <p className="sq-aide">{aide}</p>}
      {children}
    </section>
  );
}

/**
 * Le cadre d'un tableau large : il **défile** plutôt que d'écraser ses colonnes.
 *
 * Un cadre qui défile et qu'on ne peut pas atteindre au clavier est une zone de contenu
 * inaccessible (2.1.1). On lui donne donc un rôle de région, un nom, et un arrêt de tabulation —
 * mais **seulement quand il déborde réellement**. Le poser d'office ajouterait, sur chaque
 * tableau de chaque écran, un arrêt clavier qui ne mène nulle part.
 */
export function CadreDefilant({ legende, children }: { legende: string; children: ReactNode }) {
  const cadre = useRef<HTMLDivElement | null>(null);
  const [defile, setDefile] = useState(false);

  useEffect(() => {
    const el = cadre.current;
    if (!el) return;
    const mesurer = () => setDefile(el.scrollWidth - el.clientWidth > 2);
    mesurer();
    // La largeur change avec la fenêtre **et** avec les données : on observe les deux.
    const observateur = new ResizeObserver(mesurer);
    observateur.observe(el);
    return () => observateur.disconnect();
  }, [children]);

  return (
    <>
      <div ref={cadre} className="sq-tableau-cadre"
           {...(defile ? { role: "region", "aria-label": `${legende} — tableau défilant`, tabIndex: 0 } : {})}>
        {children}
      </div>
      {defile && (
        <p className="sq-champ-aide" data-testid="sq-tableau-defile">
          ↔ <span aria-hidden="true"> </span>Ce tableau est plus large que l'écran : il défile
          horizontalement. Au clavier, il se parcourt avec les flèches une fois sélectionné.
        </p>
      )}
    </>
  );
}

/** « — » plutôt qu'une cellule vide : l'absence de valeur doit se lire, pas se deviner. */
function cellule(valeur: ReactNode): ReactNode {
  return valeur === null || valeur === undefined || valeur === "" ? "—" : valeur;
}

export function TableauDonnees<L>(
  { tableau, lignes, testId, ctx }:
  { tableau: Tableau<L>; lignes: L[]; testId?: string; ctx?: ContexteSection },
) {
  if (lignes.length === 0) {
    return <p className="sq-vide" data-testid={testId ? `${testId}-vide` : undefined}>{tableau.vide}</p>;
  }
  // Les actions n'ont de sens qu'avec un contexte : sans lui, rien à appeler ni à recharger.
  const actions = ctx ? (tableau.actions ?? []) : [];
  return (
    <CadreDefilant legende={tableau.legende}>
      <table data-testid={testId}>
        <caption className="sr-only">{tableau.legende}</caption>
        <thead>
          <tr>
            {tableau.colonnes.map((c) => <th key={c.entete} scope="col">{c.entete}</th>)}
            {actions.length > 0 && <th scope="col">Gestes</th>}
          </tr>
        </thead>
        <tbody>
          {lignes.map((l, i) => (
            <tr key={i}>
              {tableau.colonnes.map((c) => (c.identifiante
                ? <th key={c.entete} scope="row">{cellule(c.valeur(l))}</th>
                : <td key={c.entete}>{cellule(c.valeur(l))}</td>))}
              {actions.length > 0 && (
                <td>
                  <div className="sq-gestes">
                    {actions.filter((a) => !a.masquer?.(l)).map((a) => (
                      <button key={a.testId(l)} type="button" data-testid={a.testId(l)}
                              onClick={() => { void a.executer(l, ctx!); }}>
                        {a.libelle(l)}
                      </button>
                    ))}
                  </div>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </CadreDefilant>
  );
}

/** Un champ de formulaire : libellé **lié**, aide rattachée par `aria-describedby`. */
export function Champ(
  { label, aide, pour, obligatoire, children }:
  { label: string; aide?: string; /** L'identifiant du contrôle, que le libellé désigne. */ pour: string;
    obligatoire?: boolean;
    children: (props: { "aria-describedby"?: string; required?: boolean }) => ReactNode },
) {
  const idAide = `${pour}-aide`;
  return (
    <div className="sq-champ">
      <label htmlFor={pour}>
        {label}
        {obligatoire && <> <span aria-hidden="true">*</span><span className="sr-only"> (obligatoire)</span></>}
      </label>
      {/* Le contrôle écrit son `id` lui-même : le lien libellé ↔ champ se relit sans suivre
          d'indirection, et le contrôle statique du dépôt le voit comme un relecteur le verrait. */}
      {children({ "aria-describedby": aide ? idAide : undefined, required: obligatoire })}
      {aide && <p className="sq-champ-aide" id={idAide}>{aide}</p>}
    </div>
  );
}

/**
 * Un lien vers une autre page du module.
 *
 * C'est un **vrai lien** : il porte son adresse, s'ouvre dans un nouvel onglet avec
 * <kbd>Ctrl</kbd> ou le clic du milieu, et se copie. Le clic ordinaire est intercepté pour
 * naviguer sans recharger l'application — mais la navigation du navigateur reste la référence,
 * jamais un `<div>` qui écoute un clic.
 */
export function LienPage(
  { vers, children, testId }: { vers: string; children: ReactNode; testId?: string },
) {
  return (
    <a href={lienVersPage(vers)} data-testid={testId}
       onClick={(e) => {
         if (e.metaKey || e.ctrlKey || e.shiftKey || e.altKey || e.button !== 0) return;
         e.preventDefault();
         allerAPage(vers);
       }}>
      {children}
    </a>
  );
}

/**
 * Une page encore à bâtir.
 *
 * Elle ne montre **aucun contrôle inerte** : un filtre qui ne filtre pas et un bouton qui ne fait
 * rien coûtent plus de confiance qu'une page qui dit franchement ce qu'elle attend. Elle nomme les
 * besoins de l'énoncé qu'elle servira, le contenu prévu, ce qui manque pour la câbler, et le
 * chemin qui rend le service aujourd'hui s'il en existe un.
 */
export function SectionAVenir({ avenir }: { avenir: AVenir }) {
  return (
    <>
      <p className="sq-etat" role="note">
        <strong>Écran créé, fonctions à venir.</strong> La structure, la navigation et
        l'accessibilité sont en place ; les données seront branchées au lot correspondant.
      </p>
      <h3>Ce que l'écran contiendra</h3>
      <ul className="sq-liste-nue">
        {avenir.contenu.map((c) => <li key={c}>{c}</li>)}
      </ul>
      <h3>Besoins servis</h3>
      <p>{avenir.besoins.join(" · ")}</p>
      <h3>Ce qui manque</h3>
      <p>{avenir.manque}</p>
      {avenir.entretemps && (
        <>
          <h3>En attendant</h3>
          <p>{avenir.entretemps}</p>
        </>
      )}
    </>
  );
}

// ── Rendu d'une section à source ─────────────────────────────────────────────

function SectionRendue(
  { section, perimetre, annoncer, signaler, jetonPage, rechargerPage }:
  { section: Section; perimetre: Perimetre; annoncer: (m: string) => void; signaler: (m: string) => void;
    jetonPage: number; rechargerPage: () => void },
) {
  const appeler = useApiSq();
  const [donnees, setDonnees] = useState<unknown>(null);
  const [erreur, setErreur] = useState("");
  const [chargement, setChargement] = useState(false);
  const chemin = section.source ? section.source(perimetre) : null;

  useEffect(() => {
    if (!section.source) return;
    if (!chemin) { setDonnees(null); setErreur(""); return; }
    let vivant = true;
    setChargement(true);
    appeler(chemin)
      .then((d) => { if (vivant) { setDonnees(d); setErreur(""); } })
      .catch((e: Error) => { if (vivant) { setDonnees(null); setErreur(e.message); } })
      .finally(() => { if (vivant) setChargement(false); });
    return () => { vivant = false; };
    /*
     * `jetonPage` et non un jeton local : un geste change souvent ce que les **autres** sections
     * montrent. Décrire une tâche doit la faire paraître dans la section d'à côté qui règle ses
     * cases horaires — sinon l'écran dit « décrivez d'abord une tâche » juste après qu'on l'a
     * décrite, et l'utilisateur recharge la page pour s'en sortir.
     */
  }, [chemin, jetonPage, appeler, section.source]);

  const recharger = rechargerPage;
  const ctx: ContexteSection = { perimetre, donnees, recharger, appeler: async (c, i) => {
    try { return await appeler(c, i); } catch (e: any) { signaler(e.message); return null; }
  }, annoncer, signaler };

  const corps = () => {
    if (section.avenir) return <SectionAVenir avenir={section.avenir} />;
    if (section.source && !chemin) {
      return <p className="sq-vide">Choisissez un groupe et une période ci-dessus pour afficher cette section.</p>;
    }
    if (erreur) {
      // Le motif du serveur est rendu tel quel : « Erreur 403 » n'apprend rien, son motif si.
      return <p className="sq-erreur" role="alert"><strong>Refus du serveur :</strong> {erreur}</p>;
    }
    if (chargement && donnees === null) return <p className="sq-etat" role="status">Chargement…</p>;
    if (section.tableau) {
      const extraire = section.tableau.lignes ?? ((d: unknown) => (Array.isArray(d) ? d : []));
      const lignes = extraire(donnees);
      if (!Array.isArray(lignes)) {
        return (
          <p className="sq-erreur" role="alert">
            <strong>Réponse inattendue :</strong> cette section attend une liste.
          </p>
        );
      }
      return (
        <>
          <TableauDonnees tableau={section.tableau} lignes={lignes} ctx={ctx}
                          testId={`sq-tableau-${section.titre.toLowerCase().replace(/[^a-z0-9]+/g, "-")}`} />
          {section.rendu?.(ctx)}
        </>
      );
    }
    return section.rendu ? section.rendu(ctx) : null;
  };

  return <Panneau titre={section.titre} aide={section.aide}>{corps()}</Panneau>;
}

// ── La page ──────────────────────────────────────────────────────────────────

/**
 * Rend une page décrite.
 *
 * La page porte **un seul `<h1>`**, son intention, le périmètre qu'elle réclame, puis ses
 * sections. Les deux régions d'annonce sont posées une fois pour toutes : un succès parle en
 * `role="status"` (sans voler le focus), un échec en `role="alert"`.
 */
/**
 * **L'identifiant d'un onglet**, dérivé de son libellé : il part dans l'URL (`?onglet=…`) et
 * sert d'identifiant d'essai. Les accents sont dépliés et la ponctuation tombe, pour qu'une
 * adresse reste lisible et qu'un libellé retouché n'invalide pas un lien partagé pour une
 * virgule.
 */
export function cleDOnglet(libelle: string): string {
  return libelle.normalize("NFD").replace(/[\u0300-\u036f]/g, "")
    .toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

export function PageDecrite({ d }: { d: DescripteurPage }) {
  const perimetre = useePerimetre();
  /**
   * Les onglets de la page, dans l'ordre de déclaration des sections.
   *
   * L'ordre vient du descripteur et non d'un tri : il suit le métier — on paramètre, puis on
   * agit, puis on contrôle. Un tri alphabétique mettrait « Archives » avant « Créer ».
   */
  const onglets = useMemo(() => {
    const vus = new Map<string, string>();
    for (const sec of d.sections) if (sec.onglet && !vus.has(cleDOnglet(sec.onglet))) vus.set(cleDOnglet(sec.onglet), sec.onglet);
    return [...vus].map(([id, libelle]) => ({ id, libelle }));
  }, [d.sections]);
  const aDesOnglets = onglets.length >= 2;
  const estOnglet = useCallback((x: string): x is string => onglets.some((o) => o.id === x), [onglets]);
  const [ongletActif, changerDOnglet] = useOnglet<string>(d.cle, onglets[0]?.id ?? "", estOnglet);
  /**
   * Les sections à rendre. Hors onglets, toutes ; sinon celles de l'onglet actif — **rendues et
   * non masquées** : une section cachée en CSS reste dans l'ordre de tabulation et dans l'arbre
   * d'accessibilité, et un lecteur d'écran annoncerait onze sections quand l'œil en voit deux.
   */
  const sectionsVisibles = useMemo(
    () => (aDesOnglets ? d.sections.filter((sec) => cleDOnglet(sec.onglet ?? "") === ongletActif) : d.sections),
    [d.sections, aDesOnglets, ongletActif]);
  const { t } = useT();
  const appeler = useApiSq();
  const [succes, setSucces] = useState("");
  const [echec, setEchec] = useState("");
  const [faits, setFaits] = useState<{ donnees: unknown; pret: boolean }>({ donnees: null, pret: false });
  /** Jeton de relecture **de la page** : un geste rafraîchit toutes ses sections. */
  const [jeton, setJeton] = useState(0);
  const recharger = useCallback(() => setJeton((j) => j + 1), []);
  const annoncer = useCallback((m: string) => { setEchec(""); setSucces(m); }, []);
  const signaler = useCallback((m: string) => { setSucces(""); setEchec(m); }, []);
  const besoins = useMemo(() => d.perimetre ?? [], [d.perimetre]);

  // Les indicateurs sont lus **une fois pour la page** : les accrocher à une section les ferait
  // disparaître quand celle-ci est vide, alors qu'ils résument justement ce que la page contient.
  const cheminIndicateurs = d.indicateurs ? d.indicateurs.source(perimetre) : null;
  useEffect(() => {
    if (!cheminIndicateurs) { setFaits({ donnees: null, pret: false }); return; }
    let annule = false;
    void (async () => {
      try {
        const r = await appeler(cheminIndicateurs);
        if (!annule) setFaits({ donnees: r, pret: true });
      } catch {
        // Un indicateur illisible reste « — » : afficher zéro inventerait une mesure.
        if (!annule) setFaits({ donnees: null, pret: false });
      }
    })();
    return () => { annule = true; };
  }, [cheminIndicateurs, appeler, jeton]);

  return (
    /*
      `maquette-page` en plus de `sq-page` : les deux modules de santé s'adressent au même métier
      et se montrent au même client, et deux chartes pour un même domaine se remarquent surtout
      quand on passe de l'une à l'autre. `sq-page` garde ce qui lui est propre — largeur de lecture
      bornée, gouttière —, `maquette-page` apporte les teintes et le chrome des planches.
    */
    <div className="sq-page maquette-page" data-testid={`sq-page-${d.cle}`}>
      <BandeauMaquette module={d.module ?? "Santé Québec"} />
      <header>
        <div className="sq-titre-rangee">
          <div>
            <h1 data-testid={`sq-titre-${d.cle}`}>{d.titre}</h1>
            <p className="sq-intro">{d.intro}</p>
          </div>
        </div>
        {besoins.length > 0 && <BarrePerimetre besoins={besoins} />}
        {d.indicateurs && (
          <CartesIndicateurs cartes={d.indicateurs.cartes} donnees={faits.donnees} pret={faits.pret} />
        )}
      </header>

      {/* Les deux régions vivent **hors** des sections : un message ne doit pas disparaître
          parce que la section qui l'a produit s'est rechargée. */}
      <p className="sq-etat" role="status" data-testid={`sq-succes-${d.cle}`} hidden={!succes}>{succes}</p>
      <p className="sq-erreur" role="alert" data-testid={`sq-erreur-${d.cle}`} hidden={!echec}>{echec}</p>

      {(() => {
        const rendues = sectionsVisibles.map((sec) => (
          <SectionRendue key={sec.titre} section={sec} perimetre={perimetre}
                         annoncer={annoncer} signaler={signaler}
                         jetonPage={jeton} rechargerPage={recharger} />
        ));
        if (!aDesOnglets) return rendues;
        return (
          <Onglets
            onglets={onglets.map((o) => ({ id: o.id, libelle: o.libelle, testId: `sq-onglet-${d.cle}-${o.id}` }))}
            actif={ongletActif} onChange={changerDOnglet}
            // Le nom de la barre dit **de quoi** ce sont les vues : un lecteur d'écran annonce
            // « Vues de Gabarits et récurrences », et non « navigation par onglets ».
            libelle={`Vues de ${d.titre}`}
            testId={`sq-onglets-${d.cle}`} prefixeIds={`sq-${d.cle}`}
          >
            {rendues}
          </Onglets>
        );
      })()}

      {d.parcours && <Parcours {...d.parcours} />}

      {/*
        * La fraîcheur de la donnée, que les maquettes posent en pied de chaque écran. Elle n'est
        * pas décorative : une console de garde qui a vingt minutes de retard se lit exactement
        * comme une console à jour, et c'est ce que cette ligne empêche.
        */}
      <p className="sq-pied" data-testid={`sq-pied-${d.cle}`}>
        Dernière lecture de cet écran : {new Date().toLocaleTimeString("fr-CA", { hour: "2-digit", minute: "2-digit" })}
        <span aria-hidden="true"> · </span>
        Les données affichées sont celles de votre périmètre.
      </p>
    </div>
  );
}

/**
 * **Les mêmes sections, sans leurs onglets** — pour un hôte qui met une page par entrée de menu.
 *
 * Urgences-santé suit la règle « un menu, une page » : ses sections s'empilent, et une barre
 * d'onglets dans une page qui n'en a qu'une famille serait un rangement sans rien à ranger. La
 * règle du dépôt est d'ailleurs stricte — ou **toutes** les sections déclarent un onglet, ou
 * **aucune** — donc reprendre un sous-ensemble d'une page à onglets exige de les retirer.
 */
export function sansOnglets(sections: Section[]): Section[] {
  return sections.map(({ onglet, ...reste }) => reste);  // eslint-disable-line @typescript-eslint/no-unused-vars
}

/** Raccourci d'écriture d'une page : `export default page(descripteur)`. */
export function page(d: DescripteurPage) {
  const Page = () => <PageDecrite d={d} />;
  Page.displayName = `PageSQ(${d.cle})`;
  return Page;
}
