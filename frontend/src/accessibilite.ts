/**
 * **Navigation clavier des onglets** — installée une fois pour toute l'application.
 *
 * Nos barres d'onglets suivent le motif ARIA « tabs » avec un *tabindex tournant* : seul l'onglet
 * actif est atteignable par Tab. Ce motif n'est correct que si les flèches déplacent le focus
 * d'un onglet à l'autre ; sans cela, un utilisateur au clavier ne peut **jamais** ouvrir un autre
 * onglet — treize écrans étaient dans ce cas, chacun ayant réimplémenté (ou oublié) la même
 * chose. Un seul délégué sur le document couvre tous les `role="tablist"` présents et à venir,
 * sans qu'un écran ait à y penser.
 *
 * Comportement (WAI-ARIA APG, activation automatique) : ← / → passent à l'onglet voisin en
 * bouclant, Début / Fin vont aux extrémités ; l'onglet reçoit le focus **et** est activé.
 */
const TOUCHES: Record<string, (i: number, n: number) => number> = {
  ArrowRight: (i, n) => (i + 1) % n,
  ArrowLeft: (i, n) => (i - 1 + n) % n,
  ArrowDown: (i, n) => (i + 1) % n,
  ArrowUp: (i, n) => (i - 1 + n) % n,
  Home: () => 0,
  End: (_i, n) => n - 1,
};

export function deplacerOnglet(e: KeyboardEvent): void {
  const suivant = TOUCHES[e.key];
  const cible = e.target as HTMLElement | null;
  if (!suivant || !cible || cible.getAttribute("role") !== "tab") return;
  const liste = cible.closest('[role="tablist"]');
  if (!liste) return;
  const onglets = Array.from(liste.querySelectorAll<HTMLElement>('[role="tab"]')).filter((o) => !o.hasAttribute("disabled"));
  const i = onglets.indexOf(cible);
  if (i < 0 || onglets.length < 2) return;
  e.preventDefault();
  const prochain = onglets[suivant(i, onglets.length)];
  prochain.focus();
  prochain.click();
}

let installee = false;

export function installerNavigationOnglets(): void {
  if (installee || typeof document === "undefined") return;
  document.addEventListener("keydown", deplacerOnglet);
  installee = true;
}
