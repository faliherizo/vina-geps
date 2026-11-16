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
import { MODULE_US, NOTIONS_US, type NotionUs } from "./urgencessante/menu";

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

/** Une rubrique du menu (un titre, jamais replié) : des notions, ou des pages seules. */
export interface GroupeNav {
  key: string;
  label: string;
  icon: string;
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
 * **Une notion du module devient une entrée du menu** (AO 26-015-DO) — une page par écran.
 *
 * La liste des écrans vient de `urgencessante/menu.ts`, elle n'est pas recopiée ici. Chaque vue
 * porte le **socle** du module (`effectifs`) : sans lui, tous les écrans du module disparaissent,
 * alors que le tableau de bord, voisin dans la même rubrique, reste. Le sous-module de chaque page
 * (`effectifs_planification`…) est jugé à part, par `PAGE_MODULE`.
 *
 * Une notion d'une seule vue est un **lien direct**, pas un intertitre : un titre qui ne regroupe
 * rien n'aide pas à se repérer (et l'essai de navigation le refuse).
 */
function entreeDuModule(n: NotionUs): Concept | Vue {
  if (n.cle === null || n.entrees.length === 1) return { page: n.entrees[0].cle, module: MODULE_US };
  return { key: n.cle, i18nKey: `concept.${n.cle}`, icon: n.icone, vues: n.entrees.map((e) => ({ page: e.cle, module: MODULE_US })) };
}

/** La notion du module qui se range sous « Administration » : elle paramètre, elle ne s'utilise pas au quotidien. */
const NOTION_PARAMETRES_US = "usadministration";

/**
 * **Le menu de VINA GEPS** (planche s01, ADR 0073) — trois rubriques fixes, deux niveaux au plus.
 *
 * La rubrique est un **titre**, pas un accordéon : on atteignait un écran en trois clics (section,
 * notion, vue), et le tableau de bord était une section qui ne contenait… que le tableau de bord.
 * « Menu principal » suit la colonne des planches : le tableau de bord, puis les sujets du
 * travail de tous les jours, puis les rapports. Le paramétrage du module rejoint l'administration
 * de l'organisme : c'est le même public, et ce n'est pas le travail quotidien.
 */
export const NAVIGATION: GroupeNav[] = [
  { key: "urgencessante", label: "Menu principal", icon: "🧭", entrees: [
    { page: "dashboard" },
    ...NOTIONS_US.filter((n) => n.cle !== NOTION_PARAMETRES_US).map(entreeDuModule),
    // Communications cliniques (ADR 0074) : la vue de l'expéditeur et celle du destinataire, sous une notion.
    { key: "alertesnotion", i18nKey: "concept.alertesnotion", icon: "🚨", vues: [
      { page: "alnouvelle", module: "alertes" }, { page: "alsuivi", module: "alertes" }, { page: "mesalertes", module: "alertes" },
    ] },
    // Paie (ADR 0076) : la gestion de la paie et les bulletins de chacun, sous une notion.
    { key: "paienotion", i18nKey: "concept.paienotion", icon: "💵", vues: [
      { page: "paietemps", module: "paie" }, { page: "paiecycles", module: "paie" }, { page: "paiedossiers", module: "paie" },
      { page: "paiedeclarations", module: "paie" },
      { page: "mesbulletins", module: "paie" },
    ] },
    { page: "rapports" },
    { page: "approvals" },
  ] },
  { key: "admin", label: "Administration", icon: "🛠️", entrees: [
    ...NOTIONS_US.filter((n) => n.cle === NOTION_PARAMETRES_US).map(entreeDuModule),
    { page: "alparametres", module: "alertes" },
    { page: "paieparametres", module: "paie" },
    { page: "paieregles", module: "paie" },
    { page: "admin" },
    { key: "access", i18nKey: "concept.access", icon: "🔒", vues: [
      { page: "access", i18nKey: "vue.access.objets", principale: true },
      { page: "access", onglet: "octrois", i18nKey: "vue.access.octrois" },
      { page: "access", onglet: "qui", i18nKey: "vue.access.qui" },
    ] },
    { page: "notifications" }, { page: "integrations" }, { page: "apidocs" }, { page: "superadmin" },
  ] },
  { key: "space", label: "Aide et compte", icon: "🏠", entrees: [
    { page: "account" }, { page: "help" }, { page: "soutien" },
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
