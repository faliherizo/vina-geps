/**
 * **Les gestes d'un essai piloté par des données** — un seul endroit.
 *
 * Deux essais jouent des scénarios décrits en JSON (`exigences-fonctionnelles.json`,
 * `profils.json`). Ils posaient les mêmes gestes avec deux copies du même code, et la copie qui
 * n'avait pas été corrigée a rejoué le défaut : lire les options d'un `<select>` **dès le rendu**
 * donne une liste vide, et l'essai accuse alors l'amorce de n'avoir rien créé — le diagnostic le
 * plus coûteux qui soit, parce qu'il envoie chercher la faute du côté où elle n'est pas.
 *
 * Une règle, un endroit.
 */
import { expect, type Page } from "@playwright/test";

/** Un geste : cliquer, déployer un repli, choisir dans une liste, remplir un champ, ou constater. */
export interface Geste {
  cliquer?: string;
  deployer?: string;
  choisir?: string;
  remplir?: string;
  visible?: string;
  /**
   * **Ce que la commande dit**, et non seulement qu'elle est là.
   *
   * Ajouté le 8 novembre 2026, pour les essais de **saisie**. Le parcours « l'employée ajoute un
   * temps » constatait `mft-segments` et `mft-totaux` *visibles* — or ces deux commandes étaient
   * déjà là avant le geste : l'essai aurait donc survécu à un ajout silencieusement perdu, tout en
   * portant dans son motif « une saisie qui ne change aucun total n'a pas été prise ». Prouver
   * qu'une donnée est arrivée demande de lire ce qui s'affiche.
   */
  texte?: string;
  /**
   * **Ce qui ne doit pas être là.** Une commande absente se prouve, elle ne se devine pas.
   *
   * Ajouté le 2 novembre 2026 : sur « Ma feuille de temps », un compte sans dossier d'effectif
   * voyait le bouton « Préparer ma feuille », qui ne pouvait qu'échouer. Une fois le défaut
   * corrigé, rien n'empêchait qu'il revienne — un essai qui ne constate que des présences ne voit
   * jamais reparaître un geste impossible.
   */
  absent?: string;
  valeur?: string;
  /**
   * Choisir une option **par son libellé** (un fragment du texte affiché).
   *
   * Les valeurs d'un `<select>` sont souvent des identifiants de semis (`epr-17…`), qu'un fichier
   * de données ne peut pas connaître ; sans ce verbe, l'essai prenait la première option et
   * mesurait la mauvaise période en croyant mesurer la bonne.
   */
  libelle?: string;
  pourquoi: string;
}

/**
 * Une commande attendue, éventuellement **au choix** : `a|b` passe si l'une des deux est là, et
 * `prefixe:x` si un identifiant commence par `x`.
 *
 * Le choix est nécessaire là où l'écran rend l'un *ou* l'autre selon la donnée — « aucune
 * anomalie » et « 3 anomalies » prouvent tous deux que la feuille affiche ses anomalies ; exiger
 * le second reviendrait à semer une anomalie pour satisfaire un essai.
 */
/**
 * La commande désignée, par son identifiant exact ou par son **préfixe**.
 *
 * `prefixe:` servait à constater une présence ; il sert aussi à **agir**, parce que certaines
 * commandes portent un identifiant engendré — `agm-rlt-<id>-manifester` ne se nomme pas d'avance
 * dans un fichier de données, et le recopier figerait l'essai sur un identifiant de semis.
 *
 * **Un joker pour ce qui est à la fois préfixé et suffixé** : `prefixe:agm-rlt-*-manifester`.
 * Sans lui, `prefixe:agm-rlt-` désignait d'abord la **ligne** du remplacement — dont l'identifiant
 * est un préfixe de celui de son bouton — et le clic tombait sur un `<li>`, donc ne faisait rien.
 * Un geste qui ne lève aucune erreur et n'agit pas est le pire des deux : l'essai accuse ensuite
 * le serveur de n'avoir rien enregistré.
 */
export function commande(page: Page, c: string) {
  if (!c.startsWith("prefixe:")) return page.getByTestId(c).first();
  const motif = c.slice("prefixe:".length);
  const etoile = motif.indexOf("*");
  if (etoile < 0) return page.locator(`[data-testid^="${motif}"]`).first();
  return page.locator(`[data-testid^="${motif.slice(0, etoile)}"][data-testid$="${motif.slice(etoile + 1)}"]`).first();
}

export async function commandePresente(page: Page, commande_: string, delai = 15_000): Promise<boolean> {
  // Une règle, un endroit : ce localisateur avait sa copie ici, et c'est la copie non corrigée qui
  // rejoue les défauts — l'avertissement que porte l'en-tête de ce fichier.
  const resultats = await Promise.all(commande_.split("|").map((c) => commande(page, c)
    .waitFor({ state: "attached", timeout: delai }).then(() => true).catch(() => false)));
  return resultats.some(Boolean);
}

export async function poser(page: Page, g: Geste): Promise<void> {
  if (g.cliquer) return commande(page, g.cliquer).click({ timeout: 30_000 });
  if (g.absent) {
    // L'écran est d'abord **rendu** : constater une absence sur une page encore vide passerait
    // toujours. Le titre de l'écran sert de témoin — il arrive avec le premier rendu.
    await expect(page.locator("h1").first(), "l'écran doit être rendu avant de constater une absence")
      .toBeVisible({ timeout: 30_000 });
    await expect(commande(page, g.absent), g.pourquoi).toHaveCount(0);
    return;
  }
  if (g.remplir) return page.getByTestId(g.remplir).first().fill(g.valeur ?? "", { timeout: 30_000 });
  if (g.visible) {
    // `commande` et non `getByTestId` : `prefixe:` doit valoir pour **constater** comme pour agir.
    // Branché d'abord sur `cliquer` seulement, il faisait chercher un identifiant littéral
    // « prefixe:agm-rlt- » — absent, évidemment, et l'échec accusait l'écran.
    await expect(commande(page, g.visible), g.pourquoi).toBeVisible({ timeout: 30_000 });
    return;
  }
  if (g.texte) {
    // `toContainText` attend que le texte arrive : une saisie part au serveur, revient, puis se
    // rend. Lire tout de suite ferait accuser l'écriture d'avoir échoué alors qu'elle est en vol.
    await expect(commande(page, g.texte), g.pourquoi)
      .toContainText(g.valeur ?? "", { timeout: 30_000 });
    return;
  }
  if (g.deployer) {
    // Un `<details>` s'ouvre par son résumé ; le piloter par l'état évite de dépendre de l'endroit
    // exact où le clic tombe quand le repli est encore fermé.
    const repli = page.getByTestId(g.deployer).first();
    await repli.waitFor({ state: "attached", timeout: 30_000 });
    await repli.evaluate((el) => { (el as HTMLDetailsElement).open = true; });
    return;
  }
  if (g.choisir) {
    const liste = page.getByTestId(g.choisir).first();
    /*
      **Attendre une option, et non lire tout de suite.**

      Le `<select>` existe avant que sa liste n'arrive : l'écran se rend, puis la requête répond.
      Lire les options dès le premier rendu donne une liste vide, et l'essai accuse alors l'amorce
      d'être incomplète. On attend donc la **première option réelle**.
    */
    await expect(liste.locator("option[value]:not([value=''])").first(), g.pourquoi)
      .toBeAttached({ timeout: 30_000 });
    const options = await liste.locator("option").evaluateAll(
      (opts) => opts.map((o) => ({ valeur: (o as HTMLOptionElement).value, texte: o.textContent ?? "" })).filter((o) => o.valeur));
    if (options.length === 0) throw new Error(`« ${g.choisir} » n'offre aucune valeur : l'amorce n'a rien créé`);
    if (g.libelle) {
      // Par libellé, **et sans repli** : prendre la première option faute de trouver la bonne
      // ferait passer l'essai sur la mauvaise donnée — l'échec doit nommer ce qui manque.
      const trouvee = options.find((o) => o.texte.includes(g.libelle!));
      if (!trouvee) throw new Error(`« ${g.choisir} » n'offre aucune option « ${g.libelle} » (offertes : ${options.map((o) => o.texte.trim()).join(" | ")})`);
      await liste.selectOption(trouvee.valeur);
      return;
    }
    const valeurs = options.map((o) => o.valeur);
    await liste.selectOption(g.valeur && valeurs.includes(g.valeur) ? g.valeur : valeurs[0]);
    return;
  }
  throw new Error("geste sans verbe : attendu cliquer, deployer, choisir, remplir, texte ou visible");
}
