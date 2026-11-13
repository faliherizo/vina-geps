import { useCallback, useEffect, useRef, useState } from "react";
import { useHauteurBarre } from "./hauteurBarre";
import { objet } from "./formeRecue";
import { Icone } from "./composants";
import Dashboard from "./Dashboard";
import ConstructeurRapports from "./ConstructeurRapports";
import ApiDocs from "./ApiDocs";
import MaintenanceBanner from "./MaintenanceBanner";
import { EmpruntBandeau } from "./admin/EmpruntIdentite";
import Approvals from "./Approvals";
import Admin from "./Admin";
import InvitationActivation from "./InvitationActivation";
import { useCurrentTenant } from "./organismeCourant";
import { MenuProfil } from "./ProfilUtilisateur";
import BarreAvis from "./BarreAvis";
import { ModuleAccessProvider } from "./moduleAccess";
import Help from "./Help";
import Soutien from "./Soutien";
import Integrations from "./Integrations";
import Notifications from "./Notifications";
import AccessControl from "./AccessControl";
import { CLES_US, ENTREES_US, PageUrgencesSante } from "./urgencessante";
import { BandeauAppels } from "./santequebec/appels";
import FrontiereErreur from "./FrontiereErreur";
import { droitDeVoir } from "./droitsDeMenu";
import { AIDE_PAR_PAGE, IDS_SECTIONS, titreDeSection } from "./aideParEcran";
import { PAGE_MODULE } from "./modulesParPage";
import Superadmin from "./Superadmin";
import Account from "./Account";
import Login from "./Login";
import FirstLogin from "./FirstLogin";
import { NAVIGATION, OngletContext, cleDeNotion, estConcept, testIdDe, type Concept, type Vue } from "./navigation";
import FilAriane, { type Maillon } from "./composants/FilAriane";
import { AuthProvider, useAuth } from "./AuthContext";
import { I18nProvider, useT, LANGUAGES, Lang } from "./i18n";

/**
 * Les pages de l'application. Les clés préfixées `sq` appartiennent au **module Santé Québec**
 * (AO 2027-005) : elles sont déclarées par `santequebec/menu.ts` et rendues par son registre.
 * L'union tient sur une seule ligne — un garde-fou du dépôt la lit ainsi.
 */
export type Page = "dashboard" | "rapports" | "apidocs" | "integrations" | "help" | "soutien" | "approvals" | "admin" | "notifications" | "access" | "superadmin" | "account" | "uscomblement" | "usequipages" | "usdisponibilites" | "usabsences" | "usechanges" | "usagph" | "uspresence" | "ustemps" | "uspostes" | "usperiodes" | "usressources" | "usquotas" | "usreferentiels" | "usregles" | "usmilieux" | "usperimetres" | "usintegrations" | "usretention" | "usjournal" | "usmessages" | "usappels" | "usdiffusion" | "monhoraire" | "mesdisponibilites" | "mesechanges" | "monagph" | "monpointage" | "matemps" | "mesabsences";
type Role = "superadmin" | "admin" | "manager" | "employee" | "rh" | "accountant" | "director";

/**
 * Capacité(s) ouvrant chaque page — **source unique avec le serveur**.
 *
 * Une page est visible dès que le compte détient **l'une** d'entre elles. Ce qui n'y figure pas
 * reste gouverné par les rôles : le tableau de bord, l'aide ou l'espace personnel ne
 * correspondent à aucun droit particulier, et doivent rester ouverts à tous.
 *
 * L'intérêt n'est pas cosmétique : depuis que les établissements définissent leurs propres
 * rôles, une liste de rôles écrite ici ne peut plus être exhaustive.
 */
const PAGE_CAPABILITIES: Partial<Record<Page, string[]>> = {};

/**
 * **Pages dont le serveur calcule le droit en entier.**
 *
 * Une capacité ne connaît que le rôle. Approuver dépend aussi de l'**organigramme** : quiconque
 * encadre réellement quelqu'un approuve son travail, même si son compte est resté « employé » —
 * et le serveur lui adresse déjà ses feuilles. La règle complète vit donc à un seul endroit
 * (`payroll/scope.peutApprouver`), et le menu lit son résultat.
 *
 * C'est le même raisonnement que les capacités, poussé d'un cran : ne pas recopier la règle.
 */
const PAGE_DROIT_SERVEUR: Partial<Record<Page, "peutApprouver">> = {
  approvals: "peutApprouver",
};

const PAGES: { key: Page; i18nKey: string; icon: string; roles: Role[] }[] = [
  // Les pages d'Urgences-santé sont déclarées par `urgencessante/menu.ts` : ajouter un écran
  // là-bas ne demande pas de modifier l'ossature de l'application.
  ...ENTREES_US.map((e) => ({ key: e.cle as Page, i18nKey: `nav.${e.cle}`, icon: e.icone, roles: e.roles as Role[] })),
  { key: "dashboard", i18nKey: "nav.dashboard", icon: "📊", roles: ["admin", "manager", "employee", "rh", "director", "accountant"] },
  { key: "rapports", i18nKey: "nav.rapports", icon: "📑", roles: ["admin", "manager"] },
  { key: "account", i18nKey: "nav.account", icon: "🪪", roles: ["admin", "manager", "employee"] },
  { key: "help", i18nKey: "nav.help", icon: "❓", roles: ["admin", "manager", "employee", "rh", "director", "accountant"] },
  { key: "soutien", i18nKey: "nav.soutien", icon: "🛟", roles: ["admin", "manager", "employee", "rh", "director", "accountant"] },
  { key: "approvals", i18nKey: "nav.approvals", icon: "✅", roles: ["admin", "manager", "director"] },
  { key: "access", i18nKey: "nav.access", icon: "🔐", roles: ["admin"] },
  { key: "notifications", i18nKey: "nav.notifications", icon: "🔔", roles: ["admin", "manager", "rh"] },
  { key: "admin", i18nKey: "nav.admin", icon: "🛠️", roles: ["admin"] },
  { key: "apidocs", i18nKey: "nav.apidocs", icon: "🧩", roles: ["admin", "director"] },
  { key: "integrations", i18nKey: "nav.integrations", icon: "🔌", roles: ["admin"] },
  { key: "superadmin", i18nKey: "nav.superadmin", icon: "🛡️", roles: ["superadmin"] },
];

/**
 * Base d'URL de l'application (routage interne SPA). En production l'app est servie sous
 * `/admin` ; en dev/e2e (Vite) elle est à la racine. Les URLs internes deviennent alors
 * `${APP_BASE}/<page>` (ex. `/admin/finance`) au lieu de l'URL statique `/admin/login`.
 */
const APP_BASE = (typeof window !== "undefined" && window.location.pathname.startsWith("/admin")) ? "/admin" : "";
/** Déduit la page courante depuis l'URL (retourne null si l'URL ne cible pas une page connue). */
function pageFromUrl(): Page | null {
  if (typeof window === "undefined") return null;
  const rest = window.location.pathname.slice(APP_BASE.length).replace(/^\/+/, "");
  const key = rest.split("/")[0];
  return key && PAGES.some((p) => p.key === key) ? (key as Page) : null;
}
/** L'onglet demandé par l'URL (`?onglet=`), quand le menu cible une vue précise d'une page. */
function ongletFromUrl(): string | null {
  if (typeof window === "undefined") return null;
  const o = new URLSearchParams(window.location.search).get("onglet");
  return o && /^[a-z-]+$/.test(o) ? o : null;
}

/** La section du guide demandée par l'URL (`?section=`), pour un lien partagé ou un rechargement. */
function sectionFromUrl(): string | null {
  if (typeof window === "undefined") return null;
  const s = new URLSearchParams(window.location.search).get("section");
  return s && IDS_SECTIONS.has(s) ? s : null;
}

/**
 * La **charte de l'organisme** (AO 26-015-DO, lot R11, P-TN-9) : ses couleurs remplacent celles de
 * l'application — des couleurs que le serveur a vérifiées, contraste compris — et son nom et son
 * logo prennent la place de la marque. Sans charte, ou hors organisme, rien ne change.
 */
function useCharteOrganisme(tenantId: string | null, apiFetch: (c: string, i?: RequestInit) => Promise<Response>) {
  const [charte, setCharte] = useState<{ nomAffiche: string; logo: string | null; variables: Record<string, string> } | null>(null);
  useEffect(() => {
    if (!tenantId) { setCharte(null); return; }
    let actif = true;
    void apiFetch("/api/organisme/charte").then((r) => (r.ok ? r.json() : null)).then((c) => { if (actif) setCharte(objet(c)); }).catch(() => undefined);
    return () => { actif = false; };
  }, [tenantId, apiFetch]);
  useEffect(() => {
    const racine = document.documentElement.style;
    const variables = charte?.variables && typeof charte.variables === "object" ? charte.variables : {};
    for (const [k, v] of Object.entries(variables)) racine.setProperty(k, v);
    return () => { for (const k of Object.keys(variables)) racine.removeProperty(k); };
  }, [charte]);
  return charte;
}

function AppShell() {
  const { user, loading, logout, apiFetch, establishmentId } = useAuth();
  const charte = useCharteOrganisme(user?.tenantId ?? null, apiFetch);
  const { t, lang, setLang } = useT();
  const [isTeacherProfile, setIsTeacherProfile] = useState(false);
  // Encadre-t-il des personnes ? Renseigné par `/api/auth/profile` d'après la hiérarchie.
  const [managesTeam, setManagesTeam] = useState(false);

  useEffect(() => {
    let cancelled = false;
    if (!user) {
      setIsTeacherProfile(false);
      setManagesTeam(false);
      return;
    }
    // Le profil est désormais chargé pour **tout** utilisateur : il porte aussi le nombre de
    // personnes encadrées, dont dépend l'accès à « Mon équipe ».
    apiFetch("/api/auth/profile")
      .then((r) => (r.ok ? r.json() : null))
      .then((j) => {
        if (cancelled) return;
        const title = String(j?.profile?.jobTitle || "").toLowerCase();
        setIsTeacherProfile(user.role === "employee" && /enseign|prof|teacher/.test(title));
        setManagesTeam(Number(j?.manages ?? 0) > 0);
      })
      .catch(() => { if (!cancelled) { setIsTeacherProfile(false); setManagesTeam(false); } });
    return () => { cancelled = true; };
  }, [user, apiFetch]);

  // Modules activés pour le tenant courant (pilotent l'affichage des menus). `null` = pas encore
  // chargé ou utilisateur global (superadmin) → aucun gating par module.
  const [enabledModules, setEnabledModules] = useState<Set<string> | null>(null);
  /**
   * Les vues de menu **masquées** pour cet établissement (Superadmin › Modules et accès).
   *
   * ⚠️ Ce n'est pas un contrôle d'accès : les routes d'API de la page restent ouvertes. C'est un
   * dégarnissage de menu — l'entrée disparaît, **et** l'URL directe ne l'ouvre plus, donc
   * personne n'y tombe par un signet. La frontière de sécurité reste le module.
   */
  const [vuesMasquees, setVuesMasquees] = useState<Set<string>>(new Set());
  const refreshModules = useCallback(async () => {
    if (!user) { setEnabledModules(null); setVuesMasquees(new Set()); return; }
    try {
      // `no-store` : on ne veut jamais une copie mise en cache — une désactivation par le
      // superadmin doit se refléter dès le prochain chargement/retour d'onglet.
      const r = await apiFetch("/api/saas/my-modules", { cache: "no-store" });
      const j = r.ok ? await r.json() : null;
      /*
       * **Sans établissement, aucun menu de module.**
       *
       * L'ancienne règle lisait `planCode` : un compte global le recevait `null`, et l'on en
       * concluait « aucun filtrage », c'est-à-dire **tous** les menus — y compris Santé Québec,
       * dont les pages s'ouvraient alors sans locataire et échouaient à chaque appel. Un
       * établissement sans plan tarifaire tombait dans le même trou.
       *
       * Le serveur dit maintenant quel établissement il a retenu. Aucun ⇒ ensemble vide ⇒ seuls
       * les menus du cœur (tableau de bord, mon compte, aide, administration, superadmin), qui
       * ne sont rattachés à aucun module, restent visibles.
       */
      setEnabledModules(j && Array.isArray(j.modules) ? new Set<string>(j.modules) : new Set<string>());
      // Le masquage suit les modules : même appel, même fraîcheur. Un superadmin qui masque une
      // entrée doit la voir disparaître au prochain chargement, pas le lendemain.
      setVuesMasquees(new Set<string>(Array.isArray(j?.vuesMasquees) ? j.vuesMasquees : []));
    } catch { setEnabledModules(null); setVuesMasquees(new Set()); }
  }, [user, apiFetch]);
  useEffect(() => { refreshModules(); }, [refreshModules]);
  // Rafraîchit les modules quand l'onglet reprend le focus (le superadmin a pu en désactiver
  // dans un autre onglet/poste) — le menu se met alors à jour sans reconnexion.
  useEffect(() => {
    const onFocus = () => { if (document.visibilityState === "visible") refreshModules(); };
    window.addEventListener("focus", onFocus);
    document.addEventListener("visibilitychange", onFocus);
    return () => { window.removeEventListener("focus", onFocus); document.removeEventListener("visibilitychange", onFocus); };
  }, [refreshModules]);

  /** Un code de module est autorisé s'il n'est pas géré, si aucun gating (utilisateur global), ou s'il est activé. */
  const moduleCodeAllowed = (mod?: string): boolean => !mod || enabledModules === null || enabledModules.has(mod);
  /** Un module est autorisé si non géré (menu cœur) ou présent dans les modules activés du tenant. */
  const moduleAllowed = (key: Page): boolean => moduleCodeAllowed(PAGE_MODULE[key]);
  /** La clé d'une vue au catalogue du serveur : `page` pour une page, `page:onglet` pour un onglet. */
  const cleDeVue = (v: { page: string; onglet?: string }) => (v.onglet ? `${v.page}:${v.onglet}` : v.page);
  const vueMasquee = (v: { page: string; onglet?: string }) => vuesMasquees.has(cleDeVue(v));

  const visible = user
    ? PAGES.filter((p) => {

      // La règle du droit vit dans `droitsDeMenu` : capacité, droit calculé par le serveur, ou
      // rôle en dernier recours. Ici on ne fait que la lire — et les cas particuliers de page.
      const droitDelegue = PAGE_DROIT_SERVEUR[p.key];
      if (!droitDeVoir({
        roles: p.roles,
        capacitesRequises: PAGE_CAPABILITIES[p.key],
        droitDelegue: droitDelegue !== undefined,
        droitServeur: droitDelegue ? user[droitDelegue] : undefined,
      }, { role: user.role, capabilities: user.capabilities })) return false;

      return moduleAllowed(p.key);
    })
    : [];

  /**
   * **La page mémorisée appartient au compte, pas au navigateur.**
   *
   * Elle était rangée sous la seule clé `page` : sur un poste partagé — un poste de garde, un
   * ordinateur de bureau d'unité — le second à se connecter arrivait sur l'écran que le premier
   * avait quitté, et non sur son tableau de bord. Pire : une page que son rôle ne lui ouvre pas,
   * d'où un aller-retour silencieux vers le tableau de bord qui ressemble à une panne.
   *
   * La clé porte donc l'identifiant du compte. Une **première** connexion n'a rien de mémorisé :
   * elle arrive sur le tableau de bord, qui est bien le point de départ voulu.
   */
  const clePageMemorisee = user?.id ? `page:${user.id}` : null;
  const [page, setPage] = useState<Page>(() => {
    const fromUrl = pageFromUrl();
    if (fromUrl) return fromUrl;
    if (typeof window === "undefined" || !clePageMemorisee) return "dashboard";
    const saved = localStorage.getItem(clePageMemorisee) as Page | null;
    return saved && PAGES.some((p) => p.key === saved) ? saved : "dashboard";
  });
  // La vue demandée dans la page : l'onglet ciblé par le menu ou l'URL, que la page à onglets
  // consomme (voir `useOnglet`). Le jeton change à chaque demande pour qu'une même entrée,
  // cliquée à nouveau, ramène à son onglet.
  const [demande, setDemande] = useState<{ onglet: string | null; jeton: number }>(() => ({ onglet: ongletFromUrl(), jeton: 0 }));
  const [open, setOpen] = useState(false);
  // La barre d'en-tête publie sa hauteur **mesurée** : le menu collant s'y aligne, et non sur une
  // constante qui ignorait le menu du profil (52 px supposés pour 68 réels).
  const barre = useHauteurBarre();
  // Le menu est un **accordéon** : une section ouverte à la fois, et dans la section une notion
  // à la fois. Tout ouvert, le menu faisait quatre écrans de haut ; ce qu'on n'a pas ouvert
  // reste replié. La section et la notion de la page courante s'ouvrent seules à l'arrivée.
  const [deplie, setDeplie] = useState<{ groupe: string | null; concept: string | null }>({ groupe: null, concept: null });
  // Accessibilité (WCAG 2.1 AA / SGQRI 008) : après une navigation, on déplace le focus dans le
  // contenu principal pour que l'utilisateur clavier/lecteur d'écran « entre » dans les données.
  const mainRef = useRef<HTMLElement>(null);
  const firstRender = useRef(true);

  useEffect(() => {
    if (clePageMemorisee) localStorage.setItem(clePageMemorisee, page);
    // L'ancienne clé commune est effacée au passage : la laisser ferait revenir le défaut d'un
    // poste partagé dès qu'une version antérieure a tourné sur ce navigateur.
    localStorage.removeItem("page");
  }, [clePageMemorisee, page]);

  /** Toutes les vues que le menu déclare, replis compris — la référence des gardes de masquage. */
  const toutesLesVuesDuMenu = NAVIGATION.flatMap((g) => g.entrees)
    .flatMap((e) => (estConcept(e) ? e.vues : [e])).flatMap((v) => (v.repli ? [v, v.repli] : [v]));

  /**
   * **Le repli d'une garde : la première page que ce compte peut ouvrir.** Le tableau de bord n'est
   * pas ouvert à tous — un compte global n'a que sa console : y renvoyer ferait atterrir sur une page
   * elle-même interdite.
   */
  const repli: Page = visible.some((p) => p.key === "dashboard") ? "dashboard" : (visible[0]?.key ?? "dashboard");

  // Garde d'accès : si la page courante cible un module (page ou section) désactivé pour le tenant
  // (accès direct par URL ou après désactivation), on redirige vers le tableau de bord.
  useEffect(() => {
    if (!user || enabledModules === null) return;
    if (!moduleAllowed(page)) setPage(repli);
  }, [enabledModules, page, user]); // eslint-disable-line react-hooks/exhaustive-deps

  // Garde d'accès par **rôle et capacité** : une page que le menu ne propose pas à ce compte ne
  // s'ouvre pas non plus par son URL. Le serveur refuse déjà les données (403) ; sans cette garde,
  // l'écran s'affichait quand même, vide ou en erreur — ce que l'essai « accès par rôle » a montré.
  useEffect(() => {
    if (!user) return;
    if (!visible.some((p) => p.key === page)) setPage(repli);
  }, [page, user, visible.length]); // eslint-disable-line react-hooks/exhaustive-deps

  /**
   * Garde du **masquage** : une page dont le menu ne propose plus aucune vue ne s'ouvre pas par
   * son URL. Sans elle, masquer « Organigramme » laisserait le signet fonctionner, et le réglage
   * ne serait qu'une politesse.
   *
   * Une page qui n'apparaît dans **aucune** vue du menu (atteinte depuis la recherche, un lien de
   * courriel) n'est pas concernée : rien n'a été masqué la concernant.
   */
  useEffect(() => {
    if (!user || vuesMasquees.size === 0) return;
    const sesVues = toutesLesVuesDuMenu.filter((v) => v.page === page);
    if (sesVues.length > 0 && sesVues.every((v) => vueMasquee(v))) setPage("dashboard");
  }, [page, user, vuesMasquees]); // eslint-disable-line react-hooks/exhaustive-deps

  /**
   * Un **onglet masqué** demandé par l'URL (`?onglet=reprise`) est refusé : la page s'ouvre sur
   * son onglet par défaut. La page garde ses propres onglets — ce réglage ne les connaît pas —
   * mais le lien direct ne mène plus à celui qu'on a retiré du menu.
   */
  useEffect(() => {
    if (!user || !demande.onglet) return;
    if (vueMasquee({ page, onglet: demande.onglet })) setDemande((d) => ({ onglet: null, jeton: d.jeton + 1 }));
  }, [page, demande.onglet, user, vuesMasquees]); // eslint-disable-line react-hooks/exhaustive-deps

  // Déplace le focus sur <main> à chaque changement de page (sauf au tout premier rendu).
  useEffect(() => {
    if (firstRender.current) { firstRender.current = false; return; }
    mainRef.current?.focus();
  }, [page]);

  /** La section du guide à atteindre, posée par le bouton d'aide ou lue dans l'URL. */
  const [sectionAide, setSectionAide] = useState<string | null>(() => sectionFromUrl());

  // Routage interne : synchronise l'URL avec la page courante (`${APP_BASE}/<page>`) une fois
  // l'utilisateur dans l'application — évite d'afficher l'URL statique `/admin/login`.
  useEffect(() => {
    if (!user || typeof window === "undefined") return;
    // Sur la même page, les autres paramètres (`employeeId`, `from`… posés par un écran) sont
    // conservés : seul `onglet` est le nôtre. Changer de page repart d'une URL propre.
    const chemin = `${APP_BASE}/${page}`;
    const params = new URLSearchParams(window.location.pathname === chemin ? window.location.search : "");
    if (demande.onglet) params.set("onglet", demande.onglet); else params.delete("onglet");
    // L'aide porte sa section : un lien partagé ou un rechargement retombent au bon endroit.
    if (page === "help" && sectionAide) params.set("section", sectionAide); else params.delete("section");
    const recherche = params.toString();
    const target = `${chemin}${recherche ? `?${recherche}` : ""}`;
    if (window.location.pathname + window.location.search !== target) {
      window.history.pushState({ page, onglet: demande.onglet }, "", target);
    }
  }, [page, demande.onglet, sectionAide, user]);

  // Boutons Précédent/Suivant du navigateur : resynchronise la page depuis l'URL.
  useEffect(() => {
    const onPop = () => {
      const p = pageFromUrl();
      if (p) { setPage(p); setDemande((d) => ({ onglet: ongletFromUrl(), jeton: d.jeton + 1 })); }
    };
    window.addEventListener("popstate", onPop);
    return () => window.removeEventListener("popstate", onPop);
  }, []);
  useEffect(() => {
    if (user && !visible.some((p) => p.key === page)) {
      setPage(visible[0]?.key || "time");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  // Les onglets de la page courante que le menu propose : sans correspondance exacte, c'est
  // l'entrée principale de la page qui est marquée courante (arrivée par URL ou depuis la recherche).
  const ongletsProposes = new Set(NAVIGATION.flatMap((g) => g.entrees).flatMap((e) => (estConcept(e) ? e.vues : [e])).flatMap((v) => (v.repli ? [v, v.repli] : [v]))
    .filter((v) => v.page === page && v.onglet).map((v) => v.onglet));
  const estCourante = (vue: Vue) => vue.page === page
    && ((vue.onglet ?? null) === demande.onglet || (!ongletsProposes.has(demande.onglet ?? undefined) && (vue.principale || !vue.onglet)));
  const emplacement = (() => {
    for (const g of NAVIGATION) {
      for (const e of g.entrees) {
        const vues = (estConcept(e) ? e.vues : [e]).flatMap((v) => (v.repli ? [v, v.repli] : [v]));
        if (vues.some(estCourante)) return { groupe: g.key, concept: estConcept(e) ? e.key : null };
      }
    }
    return null;
  })();
  const emplacementCle = emplacement ? `${emplacement.groupe}/${emplacement.concept}` : "";
  const toutesLesVues = NAVIGATION.flatMap((g) => g.entrees).flatMap((e) => (estConcept(e) ? e.vues : [e])).flatMap((v) => (v.repli ? [v, { ...v.repli, i18nKey: v.i18nKey }] : [v]));
  const vueCourante = toutesLesVues.find(estCourante) ?? null;
  useEffect(() => {
    if (emplacement) setDeplie(emplacement);
  }, [emplacementCle]); // eslint-disable-line react-hooks/exhaustive-deps

  if (loading) return <div style={{ padding: 40 }}>{t("common.loading")}</div>;
  if (!user) return <Login />;
  if (user.mustChangePassword || user.securitySetupRequired) return <FirstLogin />;
  // Tenant obligatoire : un compte rattaché à aucun établissement (ex. admin global)
  // doit en choisir un avant d'accéder aux modules (qui sont multi-tenant stricts).
  // En prod, le tenant vient du sous-domaine → user.tenantId est déjà défini ici.
  // Le superadmin (gestion des tenants) en est exempté. Désactivé en `vite dev`
  // (import.meta.env.DEV) où le backend injecte un tenant `default` pour les tests e2e.
  if (!(import.meta as any).env?.DEV && user.role !== "superadmin" && !user.tenantId && !establishmentId) {
    return <EstablishmentGate />;
  }

  function go(p: Page, onglet?: string) {
    setPage(p);
    setDemande((d) => ({ onglet: onglet ?? null, jeton: d.jeton + 1 }));
    setOpen(false);
  }

  /** La page d'une vue du menu, si ce compte la voit ; l'entrée disparaît avec la page (et avec son module). */
  function pageDe(vue: Vue) {
    const p = visible.find((x) => x.key === vue.page);
    if (!p || !moduleCodeAllowed(vue.module)) return null;
    return vue.roles && !vue.roles.includes(user!.role) ? null : p;
  }
  /**
   * La vue à rendre : celle du menu, ou son repli quand sa page hôte est masquée (module, rôle).
   *
   * Une vue **masquée par l'établissement** disparaît **sans repli** : le repli sert la même
   * notion, et le proposer rendrait le masquage sans effet — l'entrée réapparaîtrait sous un
   * autre nom, ce qui est pire que de ne pas l'avoir masquée.
   */
  function vueEffective(vue: Vue): Vue | null {
    if (vueMasquee(vue)) return null;
    if (pageDe(vue)) return vue;
    // Le repli remplace une **page** masquée, jamais un rôle exclu : la vue garde ses rôles.
    if (vue.roles && !vue.roles.includes(user!.role)) return null;
    return vue.repli && pageDe(vue.repli) ? vue.repli : null;
  }
  /**
   * Le fil d'Ariane est le menu à plat : Section › Notion › Vue. La notion mène à sa première
   * vue visible ; la section ouvre sa branche du menu. Une page ne peut plus « être ailleurs »
   * que là où le menu la range (refonte visuelle, B.1).
   */
  const filAriane = (): Maillon[] => {
    if (!emplacement || !vueCourante) return [];
    const groupe = NAVIGATION.find((g) => g.key === emplacement.groupe)!;
    const maillons: Maillon[] = [{ libelle: groupe.label, testId: `fil-${groupe.key}`, onClick: () => setDeplie({ groupe: groupe.key, concept: null }) }];
    const concept = emplacement.concept ? (groupe.entrees.find((e) => estConcept(e) && e.key === emplacement.concept) as Concept | undefined) : undefined;
    if (concept) {
      const premiere = concept.vues.find((v) => pageDe(v));
      maillons.push({ libelle: t(concept.i18nKey), testId: `fil-${concept.key}`, onClick: premiere ? () => go(pageDe(premiere)!.key, premiere.onglet) : undefined });
    }
    const hote = PAGES.find((p) => p.key === vueCourante.page)!;
    maillons.push({ libelle: t(vueCourante.i18nKey ?? hote.i18nKey), testId: `fil-${testIdDe(vueCourante).slice(4)}` });
    return maillons;
  };

  const basculerGroupe = (key: string) => setDeplie((d) => (d.groupe === key ? { groupe: null, concept: null } : { groupe: key, concept: null }));
  const basculerConcept = (key: string) => setDeplie((d) => ({ groupe: d.groupe, concept: d.concept === key ? null : key }));

  const entreeMenu = (demandee: Vue) => {
    const vue = vueEffective(demandee);
    const p = vue && pageDe(vue);
    if (!vue || !p) return null;
    const courante = estCourante(vue);
    return (
      <li key={testIdDe(vue)}>
        <button type="button" data-testid={testIdDe(vue)} className={courante ? "active" : ""}
                aria-current={courante ? "page" : undefined} onClick={() => go(p.key, vue.onglet)}>
          <span aria-hidden="true">{p.icon}</span> {t(demandee.i18nKey ?? vue.i18nKey ?? p.i18nKey)}
        </button>
      </li>
    );
  };

  /**
   * Grille 12 : **chaque écran a une entrée d'aide**. Le bouton de la barre ouvre le guide à la
   * section qui parle de l'écran courant — jamais une page vide d'aide générique.
   */
  /**
   * **Ouvrir l'aide de cet écran.**
   *
   * Le défilement était tenté dans un `setTimeout(…, 0)` juste après la navigation : React
   * n'avait pas encore monté la page d'aide, `getElementById` rendait `null`, et **rien ne se
   * passait, en silence** — on atterrissait en haut du sommaire quel que soit l'écran de départ.
   * Un `setTimeout` plus long n'aurait pas corrigé la cause, seulement déplacé la course.
   *
   * La section est donc **passée à la page d'aide**, qui met le focus dessus une fois montée, et
   * elle est inscrite dans l'URL (`?section=`) pour qu'un rechargement ou un lien partagé
   * retombent au bon endroit.
   */
  function ouvrirAide() {
    setSectionAide(AIDE_PAR_PAGE[page]);
    go("help");
  }

  return (
    <div className="app">
      <a href="#main-content" className="skip-link">{t("common.skipToContent")}</a>
      <div className="topbar" ref={barre}>
        <button className="menu-btn" data-testid="menu-toggle" aria-label={t("common.menu")} onClick={() => setOpen(true)}>☰ {t("common.menu")}</button>
        <span className="brand">{charte?.nomAffiche ?? "Nexu VINA"}</span>
        <EstablishmentPicker />
        <EstablishmentBadge />
        <button type="button" className="aide-contextuelle" data-testid="aide-contextuelle-mobile" onClick={ouvrirAide}
                aria-label={`Aide sur cet écran (guide, section « ${titreDeSection(AIDE_PAR_PAGE[page])} »)`} title="Aide sur cet écran">
          <span aria-hidden="true">?</span> Aide
        </button>
        {/*
          * **Le menu « Profil »** de la maquette P04, en bout de barre.
          *
          * Il porte l'accès à son propre compte, qui passait jusqu'ici par la navigation
          * latérale — le même chemin que les écrans de travail, alors que « mon compte » n'en
          * est pas un et ne dépend d'aucun module.
          */}
        {/*
          * **Ce qui m'attend** — messagerie et avis de l'organisme, avant le menu « Profil ».
          *
          * Placé là et non dans la colonne de gauche : la barre du haut est visible sur tous les
          * écrans, y compris ceux où la colonne est repliée. Un compteur que l'on ne voit qu'en
          * ouvrant le menu ne prévient personne.
          */}
        {/*
            **La messagerie se compte dès qu'un module la sert.**

            Le compteur ne regardait que `sq_communication` : un organisme qui achète la
            communication du personnel dans Urgences-santé (`effectifs_communication`) avait donc
            les écrans et pas le compteur — ses messages non lus n'étaient annoncés nulle part, et
            c'est précisément ce que la barre du haut sert à dire.
        */}
        <BarreAvis avecMessagerie={moduleCodeAllowed("sq_communication") || moduleCodeAllowed("effectifs_communication")}
                   avecAvis={moduleCodeAllowed("notifications")}
                   aller={(p) => go(p as Page)} jeton={page} />
        {user && (
          <MenuProfil nom={user.username} role={user.role}
                      versProfil={() => go("account")} versAide={ouvrirAide} />
        )}
      </div>

      {/* Décoratif : la barre latérale se referme aussi par navigation ou par le bouton de menu. */}
      {open && <div className="backdrop" aria-hidden="true" onClick={() => setOpen(false)} />}
      <aside className={`sidebar ${open ? "open" : ""}`} data-testid="sidebar">
        <div className="brand">{charte?.logo && <img src={charte.logo} alt="" className="brand-logo" />}{charte?.nomAffiche ?? "Nexu VINA"}</div>
        <nav aria-label={t("common.menu")} className="nav-groups">
          {NAVIGATION.map((g) => {
            if (!moduleCodeAllowed(g.module)) return null;
            // Une notion sans vue visible n'apparaît pas ; une section sans entrée non plus.
            const rendu = g.entrees.map((e) => {
              if (!estConcept(e)) return entreeMenu(e);
              const vues = e.vues.map(entreeMenu).filter(Boolean);
              if (!vues.length) return null;
              // Les <details> sont pilotés par l'état : le clic sur l'intitulé ne bascule pas
              // l'élément lui-même (preventDefault), il choisit ce qui est déplié.
              return (
                <li key={e.key}>
                  <details className="nav-concept" data-testid={`nav-concept-${e.key}`} open={deplie.concept === e.key}>
                    <summary className="nav-concept-title" onClick={(ev) => { ev.preventDefault(); basculerConcept(e.key); }}>
                      <span aria-hidden="true">{e.icon}</span> {t(e.i18nKey)}
                    </summary>
                    <ul>{vues}</ul>
                  </details>
                </li>
              );
            }).filter(Boolean);
            if (!rendu.length) return null;
            /**
             * **Une section d'une seule page est un lien, pas un accordéon.**
             *
             * C'est la règle déjà appliquée aux notions (`navigation.tsx`), étendue aux sections :
             * un intertitre qui ne regroupe rien n'aide pas à se repérer. Sans cela, « Tableau de
             * bord » demandait deux clics — déplier la section, puis cliquer l'entrée — et
             * affichait son libellé deux fois de suite.
             */
            if (g.entrees.length === 1 && !estConcept(g.entrees[0])) {
              return <ul key={g.key} className="nav-group-seul" data-testid={`nav-group-${g.key}`}>{rendu}</ul>;
            }
            return (
              <details key={g.key} className="nav-group" data-testid={`nav-group-${g.key}`} open={deplie.groupe === g.key}>
                <summary className="nav-group-title" onClick={(ev) => { ev.preventDefault(); basculerGroupe(g.key); }}>
                  <span aria-hidden="true">{g.icon}</span> {g.label}
                </summary>
                <ul>{rendu}</ul>
              </details>
            );
          })}
        </nav>
        <div className="footer">
          <button type="button" className="secondary aide-contextuelle-sidebar" data-testid="aide-contextuelle" onClick={ouvrirAide}
                  aria-label={`Aide sur cet écran (guide, section « ${titreDeSection(AIDE_PAR_PAGE[page])} »)`}>
            <span aria-hidden="true">?</span> Aide sur cet écran
          </button>
          <div style={{ marginBottom: 8 }} data-testid="auth-user">
            {user.username} <span style={{ opacity: 0.7 }}>({user.role})</span>
          </div>
          <TenantDevBadge />
          <div style={{ marginBottom: 8 }}>
            <div style={{ fontSize: 12, opacity: 0.8, marginBottom: 4 }}>
              🌐 {t("common.language")}
            </div>
            <div data-testid="lang-select" role="group" aria-label={t("common.language")} style={{ display: "flex", gap: 4 }} tabIndex={0}>
              {LANGUAGES.map((l) => (
                <button
                  key={l.code}
                  type="button"
                  data-testid={`lang-${l.code}`}
                  className={lang === l.code ? "primary" : "secondary"}
                  onClick={() => setLang(l.code as Lang)}
                  // WCAG 2.2 — 2.5.8 « Taille de cible (minimum) » : au moins 24 × 24 px.
                  // Le remplissage précédent produisait des boutons de 23 px de haut.
                  style={{ flex: 1, padding: "6px 8px", fontSize: 11, minHeight: 24 }}
                  aria-pressed={lang === l.code}
                >
                  {l.flag} {l.code.toUpperCase()}
                </button>
              ))}
            </div>
          </div>
          <button className="secondary" data-testid="logout" onClick={logout} style={{ width: "100%" }}><Icone nom="sortie" /> {t("common.logout")}
          </button>
          <div style={{ marginTop: 8, fontSize: 11, opacity: 0.7 }}>v2.0 — Phase 5</div>
        </div>
      </aside>

      <OngletContext.Provider value={{ page, onglet: demande.onglet, jeton: demande.jeton, notion: emplacement ? cleDeNotion(emplacement.groupe, emplacement.concept) : null }}>
      <main className="main" id="main-content" tabIndex={-1} ref={mainRef} aria-label={t("common.mainContent")}>
        <ModuleAccessProvider value={enabledModules}>
        <EmpruntBandeau />
        {/*
          **Un appel se voit où qu'on soit.** La bannière est au-dessus du contenu, dans le flux,
          sur toutes les pages — pas seulement sur l'écran de messagerie : une personne en train
          de construire un horaire doit savoir qu'on la demande. Elle n'interroge le serveur que
          si l'établissement a le module (ADR 0063).
        */}
        <BandeauAppels actif={moduleCodeAllowed("effectifs_communication")} />
        <FilAriane maillons={filAriane()} />
        <MaintenanceBanner />
        <FrontiereErreur cle={page}>
        {page === "dashboard" && <Dashboard />}
        {page === "rapports" && <ConstructeurRapports />}
        {page === "apidocs" && <ApiDocs />}
        {page === "integrations" && <Integrations />}
        {page === "notifications" && <Notifications />}
        {page === "access" && <AccessControl />}
        {page === "superadmin" && <Superadmin />}
        {page === "account" && <Account />}
        {CLES_US.includes(page) && <PageUrgencesSante cle={page} />}
        {page === "help" && <Help section={sectionAide} />}
        {page === "soutien" && <Soutien />}
        {page === "approvals" && <Approvals />}
        {page === "admin" && <Admin />}
        </FrontiereErreur>
        </ModuleAccessProvider>
      </main>
      </OngletContext.Provider>
      {/* Hors de <main> (ce n'est pas le contenu de la page), mais sous les mêmes modules : l'IA en est un (lot R11). */}
    </div>
  );
}

/**
 * Indicateur global d'établissement (tenant) du compte connecté, affiché dans la barre du
 * haut sur **toutes** les pages — l'application étant multi-tenant de bout en bout.
 */
/** Affiché si on tourne en dev / localhost (validation), jamais sur un domaine de prod. */
const TENANT_BADGE_VISIBLE =
  (import.meta as any).env?.DEV === true ||
  /^(?:localhost|127\.0\.0\.1|0\.0\.0\.0)$|\.local$/.test(window.location.hostname);

/**
 * Badge de **validation (Dev)** : nom + identifiant du tenant dans lequel l'utilisateur
 * est connecté. Rendu dans la barre latérale → présent sur **toutes les vues**. Permet de
 * vérifier d'un coup d'œil l'isolation par tenant. Masqué sur un domaine de production.
 *
 * ## Pourquoi ses couleurs sont écrites ici, et pas héritées
 *
 * Son fond translucide (`rgba(255,255,255,0.12)`) **éclaircit localement** la colonne : le bleu
 * marine `#073f70` y devient `#255681`. Le texte, qui héritait la couleur de la colonne
 * (`#a8bace`, 5,41:1 sur le marine), n'y tenait plus que **3,87:1** — sous le seuil de 4,5.
 * Mesuré par axe-core le 1er novembre 2026, et invisible à la relecture du diff : rien dans la
 * règle du texte n'annonce qu'une couche semi-transparente passera dessous.
 *
 * Les `opacity` d'origine aggravaient la même chose sans jamais être signalées — axe rend
 * « incomplet » plutôt que « violation » quand il ne sait pas composer la couleur. Chaque ligne
 * porte donc sa couleur **calculée** sur `#255681`, et aucune n'est transparente.
 */
/** 5,00:1 sur le fond composé du badge — vérifié par calcul, pas à l'œil. */
const SECONDAIRE_BADGE = "#c2d2e4";

function TenantDevBadge() {
  const { tenantId, tenantName } = useCurrentTenant();
  if (!TENANT_BADGE_VISIBLE) return null;
  return (
    <div data-testid="tenant-dev-badge" title="Tenant courant (validation Dev)"
      style={{ marginBottom: 8, padding: "6px 8px", borderRadius: 6,
        background: "rgba(255,255,255,0.12)", border: "1px dashed rgba(255,255,255,0.45)" }}>
      <div style={{ fontSize: 10, letterSpacing: 0.5, color: SECONDAIRE_BADGE }}>DEV · TENANT</div>
      <div style={{ fontWeight: 700, fontSize: 12, color: "#dce6f2" }} data-testid="tenant-dev-name">
        {/* 6,10:1 sur le fond composé #255681. L'émoji décore : il ne porte aucune information. */}
        <span aria-hidden="true">🏫</span> {tenantId ? (tenantName ?? "…") : "Aucun organisme"}
      </div>
      {tenantId && <div style={{ fontSize: 10, wordBreak: "break-all", color: SECONDAIRE_BADGE }} data-testid="tenant-dev-id">{tenantId}</div>}
    </div>
  );
}

/**
 * Écran bloquant : un compte sans établissement (ex. admin global en dev, ou compte
 * système sans sous-domaine en prod) doit choisir un établissement avant d'utiliser
 * l'application — les modules sont multi-tenant stricts. Une fois choisi, l'établissement
 * pilote l'en-tête X-Tenant-Id de toutes les requêtes.
 */
function EstablishmentGate() {
  const { setEstablishment, logout, user } = useAuth();
  const [tenants, setTenants] = useState<Array<{ id: string; name: string }>>([]);
  const [choice, setChoice] = useState("");
  useEffect(() => {
    fetch("/api/auth/tenants").then((r) => (r.ok ? r.json() : [])).then(setTenants).catch(() => setTenants([]));
  }, []);
  return (
    <div style={{ minHeight: "100vh", display: "grid", placeItems: "center", background: "var(--bg)", padding: 20 }}>
      <div className="card" style={{ width: "100%", maxWidth: 440 }} data-testid="establishment-gate">
        <h1 style={{ marginBottom: 4 }}>🏫 Choisir un organisme</h1>
        <p className="subtitle">
          Connecté en tant que <strong>{user?.username}</strong>. Ce compte n'est rattaché à aucun
          organisme. Sélectionnez celui que vous souhaitez administrer pour continuer.
        </p>
        <label htmlFor="f-establishment-gate-select">Organisme</label>
        <select id="f-establishment-gate-select" data-testid="establishment-gate-select" value={choice} onChange={(e) => setChoice(e.target.value)}>
          <option value="">— Choisir —</option>
          {tenants.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
        </select>
        <div style={{ display: "flex", gap: 8, marginTop: 16 }}>
          <button className="primary" data-testid="establishment-gate-confirm" disabled={!choice}
            onClick={() => setEstablishment(choice)} style={{ flex: 1 }}>
            Continuer
          </button>
          <button className="secondary" data-testid="establishment-gate-logout" onClick={logout}>
            Se déconnecter
          </button>
        </div>
        {tenants.length === 0 && (
          <p className="muted" style={{ marginTop: 12, fontSize: 13 }}>
            Aucun organisme disponible. Le superadmin doit d'abord en créer un.
          </p>
        )}
      </div>
    </div>
  );
}

function EstablishmentBadge() {
  const { tenantId, tenantName } = useCurrentTenant();
  if (!tenantId) return null;
  return (
    <span className="badge badge-active" data-testid="topbar-tenant" title="Organisme courant"
      style={{ marginLeft: "auto" }}>
      🏫 {tenantName ?? "…"}
    </span>
  );
}

/**
 * Sélecteur d'établissement pour un **admin système** (compte global sans tenant propre).
 * Les modules de base (paie, projets…) sont multi-tenant stricts : sans établissement,
 * les appels `/api/payroll/*` renvoient 400. Ce sélecteur fixe le tenant courant
 * (injecté en X-Tenant-Id par le patch fetch). Un admin rattaché à un tenant ne le voit pas.
 */
function EstablishmentPicker() {
  const { needsEstablishment, establishmentId, setEstablishment } = useAuth();
  const [tenants, setTenants] = useState<Array<{ id: string; name: string }>>([]);
  useEffect(() => {
    if (!needsEstablishment) return;
    fetch("/api/auth/tenants").then((r) => (r.ok ? r.json() : [])).then(setTenants).catch(() => setTenants([]));
  }, [needsEstablishment]);
  if (!needsEstablishment) return null;
  return (
    <label style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: 6, fontSize: 13 }}>
      <span aria-hidden="true">🏫</span>
      <span className="sr-only">Organisme</span>
      <select data-testid="topbar-establishment" value={establishmentId ?? ""}
        onChange={(e) => setEstablishment(e.target.value || null)}
        title="Choisir l'organisme à administrer">
        <option value="">— Choisir un organisme —</option>
        {tenants.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
      </select>
    </label>
  );
}

export default function App() {
  // Page **publique** d'activation d'un compte par invitation (BF-101) : la personne n'a pas
  // encore de compte — c'est ce qu'elle vient chercher. Le jeton est l'autorisation.
  const invitMatch = typeof window !== "undefined"
    && window.location.pathname.match(/\/invitation\/([A-Za-z0-9_-]{16,})/);
  if (invitMatch) {
    return (
      <I18nProvider>
        <InvitationActivation jeton={invitMatch[1]} />
      </I18nProvider>
    );
  }
  return (
    <I18nProvider>
      <AuthProvider>
        <AppShell />
      </AuthProvider>
    </I18nProvider>
  );
}
