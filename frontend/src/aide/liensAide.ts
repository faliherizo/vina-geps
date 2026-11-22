/**
 * **Les adresses de l'aide** : l'écran d'une fiche, et la fiche d'un écran.
 *
 * L'application est servie sous `/admin` en production et à la racine en développement (voir
 * `APP_BASE` dans `App.tsx`) ; un lien écrit en dur vers `/admin/…` mènerait, en développement, à une
 * page que le serveur de Vite ne connaît pas. La base se lit donc à l'adresse courante, comme `App.tsx`.
 */
import type { Page } from "../App";
import { idFiche } from "./fichesEcrans";

const base = (): string => (typeof window !== "undefined" && window.location.pathname.startsWith("/admin") ? "/admin" : "");

/** L'adresse d'un écran ; `App.tsx` l'ouvre au chargement (`pageFromUrl`). */
export const cheminEcran = (page: Page): string => `${base()}/${page}`;

/** L'adresse de la fiche d'aide d'un écran : le guide l'ouvre et y porte le focus (`?section=`). */
export const cheminFiche = (page: Page): string => `${base()}/help?section=${idFiche(page)}`;
