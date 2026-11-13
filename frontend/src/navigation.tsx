/**
 * **Navigation à deux niveaux** — un concept, une entrée.
 *
 * Le menu latéral listait des pages, et chaque page cachait des onglets : « Absences » dans
 * Temps & présences, « Absences & suppléance » dans un onglet du Module Enseignement, « Déclarer
 * une absence » dans un onglet des Remplacements scolaires. Trois entrées pour une notion, à trois
 * endroits, sous trois noms. Le menu dit désormais **la notion** (Absences, Affectations, Contrats,
 * Paie, Personnel, Temps…) et, sous elle, **les vues** qui la servent — qu'elles soient une page ou
 * un onglet d'une page. Un onglet devient adressable par l'URL (`?onglet=`), donc atteignable depuis
 * le menu comme une page.
 *
 * Ce fichier ne connaît que des identifiants : les libellés viennent de la traduction, les pages
 * de `App.tsx`. Il ne décide rien de ce qui est visible (rôles, capacités, modules) : c'est la page
 * ciblée qui décide, et une vue dont la page est masquée disparaît avec elle.
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { MODULE_US, NOTIONS_US } from "./urgencessante/menu";

/** Une vue : une page, éventuellement ouverte sur un onglet précis. */
export interface Vue {
  page: string;
  onglet?: string;
  /** Clé de traduction du libellé ; sinon le libellé de la page. */
  i18nKey?: string;
  /**
   * L'entrée **principale** d'une page à onglets : celle qui porte `nav-<page>` bien qu'elle ouvre
   * un onglet précis — l'onglet par défaut de la page, celui que les essais attendent.
   */
  principale?: boolean;
  /** Rôles qui voient l'entrée ; sans indication, ceux qui voient la page. La page reste juge. */
  roles?: readonly string[];
  /** Module qui régit l'entrée elle-même (une vue d'enseignement hébergée par une page d'une autre notion). */
  module?: string;
  /**
   * La vue de **repli** quand la page hôte est masquée : les vues du module Enseignement vivent dans
   * la page de leur notion (Absences › Enseignement) mais restent joignables au foyer du module
   * (`mifi`) si l'établissement a fermé le menu générique de cette notion.
   */
  repli?: Vue;
}

/** Une notion du menu : un libellé et ses vues, dans l'ordre où l'on travaille. */
export interface Concept {
  key: string;
  i18nKey: string;
  icon: string;
  vues: Vue[];
}

/** Un groupe du menu (section repliable) : des notions, ou des pages seules. */
export interface GroupeNav {
  key: string;
  label: string;
  icon: string;
  /** Module qui régit **toute** la section : désactivé, elle disparaît, en-tête compris. */
  module?: string;
  entrees: Array<Concept | Vue>;
}

export function estConcept(e: Concept | Vue): e is Concept {
  return (e as Concept).vues !== undefined;
}

/** L'identifiant d'essai d'une vue : `nav-<page>` pour une page, `nav-<page>-<onglet>` pour un onglet. */
export function testIdDe(v: Vue): string {
  return v.onglet && !v.principale ? `nav-${v.page}-${v.onglet}` : `nav-${v.page}`;
}

/**
 * **Le module Urgences-santé** (AO 26-015-DO) : neuf notions, **une page par entrée**.
 *
 * Le groupe est construit depuis `urgencessante/menu.ts` — la liste des écrans n'est pas recopiée
 * ici. Les douze onglets de l'ancienne « Planification des effectifs » et les sept de l'ancien
 * « Paramétrage » sont devenus dix-neuf pages : un lien se partage, le retour arrière revient à
 * l'écran précédent, et un écran se charge seul.
 */
const GROUPE_URGENCES_SANTE: GroupeNav = {
  key: "urgencessante",
  label: "Urgences-santé",
  icon: "🚑",
  module: MODULE_US,
  // Une notion d'une seule vue est un **lien direct**, pas un intertitre : un titre qui ne
  // regroupe rien n'aide pas à se repérer (et l'essai de navigation le refuse).
  entrees: NOTIONS_US.map((n) => (n.cle === null || n.entrees.length === 1
    ? { page: n.entrees[0].cle, module: n.entrees[0].module }
    : {
      key: n.cle!,
      i18nKey: `concept.${n.cle}`,
      icon: n.icone,
      vues: n.entrees.map((e) => ({ page: e.cle, module: e.module })),
    })),
};

export const NAVIGATION: GroupeNav[] = [
  /**
   * **Le tableau de bord est une section à lui seul, et la première** : l'écran sur lequel on
   * arrive à la connexion se retrouve en un clic. Une section d'une seule page est une **vue**
   * du menu, pas une notion : elle s'affiche comme un lien direct, sans intertitre à déplier.
   */
  { key: "pilotage", label: "Tableau de bord", icon: "📊", entrees: [{ page: "dashboard" }, { page: "rapports" }] },
  GROUPE_URGENCES_SANTE,
  { key: "space", label: "Mon espace", icon: "🏠", entrees: [
    { page: "account" }, { page: "approvals" }, { page: "help" }, { page: "soutien" },
  ] },
  { key: "admin", label: "Administration", icon: "🛠️", entrees: [
    { page: "admin" },
    { key: "access", i18nKey: "concept.access", icon: "🔒", vues: [
      { page: "access", i18nKey: "vue.access.objets", principale: true },
      { page: "access", onglet: "octrois", i18nKey: "vue.access.octrois" },
      { page: "access", onglet: "qui", i18nKey: "vue.access.qui" },
    ] },
    { page: "notifications" }, { page: "apidocs" }, { page: "integrations" }, { page: "superadmin" },
  ] },
];

/** L'onglet demandé par le menu ou l'URL, que la page hôte consomme à l'ouverture et à chaque changement. */
export interface OngletDemande {
  page: string | null;
  onglet: string | null;
  /** Change à chaque demande, même identique : cliquer deux fois la même entrée ramène à l'onglet. */
  jeton: number;
  /** La notion du menu où l'on est (`section/notion`), pour ne montrer que ses onglets ; `null` hors menu. */
  notion: string | null;
}

export const OngletContext = createContext<OngletDemande>({ page: null, onglet: null, jeton: 0, notion: null });

/** La clé d'une notion du menu : la section, puis la notion (vide pour une entrée directement sous la section). */
export function cleDeNotion(groupe: string, concept: string | null): string {
  return `${groupe}/${concept ?? ""}`;
}

/**
 * **Un onglet appartient à une seule notion du menu** — celle de l'entrée qui le cite ; l'entrée
 * sans onglet (la page « telle quelle ») possède tous ceux qu'aucune autre entrée ne réclame. Une
 * page hôte de plusieurs notions (remplacements scolaires, foyer de l'enseignement) ne montre donc,
 * sous une notion, que les onglets de cette notion : un onglet de « Affectations » ne s'affiche pas
 * sous « Absences ». Les replis comptent pour la notion qu'ils remplacent.
 */
export function proprietairesDesOnglets(page: string): { parOnglet: Record<string, string>; parDefaut: string | null } {
  const parOnglet: Record<string, string> = {};
  let parDefaut: string | null = null;
  for (const g of NAVIGATION) {
    for (const e of g.entrees) {
      const notion = cleDeNotion(g.key, estConcept(e) ? e.key : null);
      for (const v of (estConcept(e) ? e.vues : [e]).flatMap((x) => (x.repli ? [x, x.repli] : [x]))) {
        if (v.page !== page) continue;
        if (v.onglet) parOnglet[v.onglet] ??= notion;
        else parDefaut ??= notion;
      }
    }
  }
  return { parOnglet, parDefaut };
}

/** Un onglet est admis sous la notion courante s'il lui appartient — ou s'il n'appartient à aucune entrée du menu. */
export function ongletAdmis(page: string, onglet: string, notion: string | null): boolean {
  if (notion === null) return true;
  const p = proprietairesDesOnglets(page);
  const proprietaire = p.parOnglet[onglet] ?? p.parDefaut;
  return proprietaire === null || proprietaire === notion;
}

/** Les onglets d'une page que la notion courante du menu montre (voir {@link proprietairesDesOnglets}). */
export function useOngletsDuMenu<O extends { id: string }>(page: string, onglets: ReadonlyArray<O>): O[] {
  const { notion } = useContext(OngletContext);
  return useMemo(() => onglets.filter((o) => ongletAdmis(page, o.id, notion)), [page, onglets, notion]);
}

/** L'URL d'une page de l'application, sous le préfixe où elle est servie (`/admin/absences`, `/admin/scolaire?onglet=besoins`). */
export function lienVersPage(page: string, onglet?: string | null): string {
  const base = typeof window !== "undefined" && window.location.pathname.startsWith("/admin") ? "/admin" : "";
  return `${base}/${page}${onglet ? `?onglet=${encodeURIComponent(onglet)}` : ""}`;
}

/** Navigation interne d'une page vers une autre (identique à App.tsx) : pushState + popstate, sans rechargement. */
export function naviguerVers(page: string, onglet?: string | null): void {
  window.history.pushState({ page, onglet: onglet ?? null }, "", lienVersPage(page, onglet));
  window.dispatchEvent(new PopStateEvent("popstate"));
}

/**
 * Changer d'onglet **sur place** : l'URL suit (`?onglet=`, sans nouvelle entrée d'historique) et
 * l'application est prévenue (popstate) — le menu, le fil d'Ariane et la notion courante restent
 * ceux de l'onglet affiché. Les autres paramètres de l'URL (`employeeId`…) sont conservés.
 */
export function changerDOnglet(page: string, onglet: string): void {
  if (typeof window === "undefined") return;
  const chemin = lienVersPage(page);
  const params = new URLSearchParams(window.location.pathname === chemin ? window.location.search : "");
  params.set("onglet", onglet);
  window.history.replaceState({ page, onglet }, "", `${chemin}?${params.toString()}`);
  window.dispatchEvent(new PopStateEvent("popstate"));
}

/**
 * **Aller à une page** sans recharger l'application.
 *
 * `App.tsx` déduit la page de l'URL et écoute `popstate` : pousser l'URL puis émettre
 * l'événement suffit donc à naviguer. Sans cela, un lien interne rechargerait toute
 * l'application — plusieurs secondes, et le périmètre de travail perdu en route.
 */
export function allerAPage(page: string): void {
  if (typeof window === "undefined") return;
  const cible = lienVersPage(page);
  if (window.location.pathname !== cible) {
    window.history.pushState({ page }, "", cible);
  }
  window.dispatchEvent(new PopStateEvent("popstate"));
}

/**
 * L'onglet initial d'une page à onglets, et son suivi : la page suit le menu quand celui-ci demande
 * une vue précise, et chaque changement d'onglet de la page remonte à l'URL et au menu
 * ({@link changerDOnglet}) — un onglet affiché est toujours celui que le menu désigne.
 *
 * Une navigation vers la page **sans** onglet (son entrée principale au menu, un retour vers
 * `/page`) ramène l'onglet par défaut : sans cela, l'écran gardait l'onglet courant alors que
 * l'adresse et le menu désignaient la vue principale (relevé le 12 nov. 2026). Le jeton change à
 * chaque navigation : recliquer la même entrée suffit à revenir.
 */
export function useOnglet<T extends string>(page: string, defaut: T, valide: (s: string) => s is T): [T, (t: T) => void] {
  const demande = useContext(OngletContext);
  const demandePourMoi = demande.page === page && demande.onglet && valide(demande.onglet) ? demande.onglet : null;
  const versLaVuePrincipale = demande.page === page && !demande.onglet;
  const [onglet, setOnglet] = useState<T>(demandePourMoi ?? defaut);
  // Le défaut est lu au moment de la navigation, sans en être une dépendance : un défaut qui change
  // de lui-même (rôle chargé après coup) ne doit pas arracher l'utilisateur à l'onglet qu'il lit.
  const defautCourant = useRef(defaut);
  defautCourant.current = defaut;
  useEffect(() => {
    if (demandePourMoi) setOnglet(demandePourMoi);
    else if (versLaVuePrincipale) setOnglet(defautCourant.current);
  }, [demandePourMoi, versLaVuePrincipale, demande.jeton]);
  const changer = useCallback((t: T) => {
    setOnglet(t);
    if (demande.page === page && t !== demande.onglet) changerDOnglet(page, t);
  }, [page, demande.page, demande.onglet]);
  return [onglet, changer];
}
