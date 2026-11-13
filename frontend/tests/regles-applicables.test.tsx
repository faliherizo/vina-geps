/**
 * **Les règles annoncées avant le dépôt** (E-GN-16, panneau « Règles applicables » de la planche s02).
 *
 * L'employée ne connaissait les règles de son dépôt qu'au **refus** : elle déposait, lisait le
 * motif, corrigeait. Ce panneau dit la même chose dans l'autre sens. Trois décisions d'affichage
 * s'y éprouvent, et chacune est un choix qu'on pourrait « simplifier » à tort :
 *
 * - la **conséquence est en mots** : « refuse le dépôt » et « demande une approbation » ne se
 *   distinguent pas d'un rouge et d'un bleu quand on ne voit pas les couleurs (WCAG 1.4.1), et la
 *   différence est tout l'enjeu ;
 * - un **message à jetons ne s'affiche pas** : « {min} » se lit comme une panne ;
 * - la **portée est dite** quand elle n'est pas celle de l'organisme, sinon une règle qui ne vise
 *   qu'un statut d'emploi a l'air d'une règle maison.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import ReglesApplicables, { type RegleApplicable } from "../src/effectifs/disponibilites/ReglesApplicables";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const BLOQUANTE: RegleApplicable = {
  cle: "minimum-quarts", action: "BLOQUER", message: null, porteeType: "ORGANISME", porteeCode: null,
};
const APPROBATION: RegleApplicable = {
  cle: "fins-de-semaine", action: "EXIGER_APPROBATION",
  message: "Une fin de semaine offerte demande l'accord du chef", porteeType: "STATUT_EMPLOI", porteeCode: "TPR",
};

describe("Règles applicables à un dépôt", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  beforeEach(() => {
    conteneur = document.createElement("div"); document.body.appendChild(conteneur);
    racine = createRoot(conteneur);
  });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

  const rendre = (regles: RegleApplicable[]) => act(() => { racine.render(<ReglesApplicables regles={regles} testId="mdi-regles" />); });

  it("aucune règle : l'écran le dit, plutôt que de laisser un panneau vide", () => {
    rendre([]);
    expect(conteneur.querySelector('[data-testid="mdi-regles"]')).toBeNull();
    expect(conteneur.querySelector('[data-testid="mdi-regles-aucune"]')!.textContent)
      .toContain("Aucune règle ne restreint votre dépôt");
  });

  it("**la conséquence est en mots**, jamais une couleur seule", () => {
    rendre([BLOQUANTE, APPROBATION]);
    expect(conteneur.querySelector('[data-testid="mdi-regles-minimum-quarts"]')!.textContent).toContain("refuse le dépôt");
    expect(conteneur.querySelector('[data-testid="mdi-regles-fins-de-semaine"]')!.textContent)
      .toContain("demande l'approbation de votre gestionnaire");
  });

  it("un message entier s'affiche ; un message absent ne laisse aucun gabarit à l'écran", () => {
    rendre([BLOQUANTE, APPROBATION]);
    expect(conteneur.querySelector('[data-testid="mdi-regles-fins-de-semaine"]')!.textContent)
      .toContain("Une fin de semaine offerte demande l'accord du chef");
    // La règle bloquante n'a pas de message affichable : sa ligne dit la clé et la conséquence,
    // et rien qui ressemble à « {quartsParSemaine} ».
    expect(conteneur.querySelector('[data-testid="mdi-regles-minimum-quarts"]')!.textContent).not.toContain("{");
  });

  it("la portée est dite quand ce n'est pas celle de l'organisme — et elle nomme son code", () => {
    rendre([BLOQUANTE, APPROBATION]);
    const statut = conteneur.querySelector('[data-testid="mdi-regles-fins-de-semaine"]')!.textContent!;
    expect(statut).toContain("selon votre statut d'emploi : TPR");
    // Une règle d'organisme vise tout le monde : l'expliquer ajouterait du bruit à chaque ligne.
    expect(conteneur.querySelector('[data-testid="mdi-regles-minimum-quarts"]')!.textContent).not.toContain("selon");
  });

  it("le panneau est replié et se compte : il annonce, il n'encombre pas la saisie", () => {
    rendre([BLOQUANTE, APPROBATION]);
    const panneau = conteneur.querySelector<HTMLDetailsElement>('[data-testid="mdi-regles"]')!;
    expect(panneau.tagName).toBe("DETAILS");
    expect(panneau.open).toBe(false);
    expect(panneau.querySelector("summary")!.textContent).toContain("(2)");
    // La liste est nommée : au clavier, une liste sans nom ne dit pas de quoi elle parle.
    expect(panneau.querySelector("ul")!.getAttribute("aria-label")).toBe("Règles applicables à votre dépôt");
  });

  it("une action inconnue s'affiche telle quelle, plutôt que de disparaître", () => {
    // Un code ajouté au moteur et pas encore traduit ici doit rester **visible** : une ligne muette
    // ferait croire que la règle n'a aucun effet.
    rendre([{ ...BLOQUANTE, cle: "neuve", action: "FUTURE_ACTION" }]);
    expect(conteneur.querySelector('[data-testid="mdi-regles-neuve"]')!.textContent).toContain("FUTURE_ACTION");
  });
});
