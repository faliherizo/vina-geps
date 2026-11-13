/**
 * La bibliothèque de composants de la refonte : ce que chaque composant **promet** au lecteur
 * d'écran et au clavier — rôles, états, annonces — et rien d'autre. Un écran migré hérite de
 * ces garanties ; c'est ici qu'elles sont tenues, une fois pour toutes.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  Badge, BadgeStatut, BarreFiltres, Calendrier, EnTeteEcran, EtatVide, Etapes, FilAriane, Icone, MenuActions, Message, Onglets, Tableau, Tiroir, Tuile,
  casesDuMois, dateIso, libelleResultats, libelleTendance, tonDuStatut,
} from "../src/composants";
import { NOMS_ICONES } from "../src/composants/Icone";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let conteneur: HTMLDivElement;
let racine: Root;
beforeEach(() => { conteneur = document.createElement("div"); document.body.appendChild(conteneur); racine = createRoot(conteneur); });
afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });
const rendre = (ui: React.ReactElement) => act(() => racine.render(ui));
const cliquer = (el: Element | null) => act(() => { (el as HTMLElement).click(); });
const touche = (key: string, cible: Element | Document = document) => act(() => { cible.dispatchEvent(new KeyboardEvent("keydown", { key, bubbles: true })); });
const $ = (sel: string) => conteneur.querySelector(sel);
const $$ = (sel: string) => Array.from(conteneur.querySelectorAll(sel));

describe("Icone", () => {
  it("est décorative et rend un tracé pour chaque nom", () => {
    for (const nom of NOMS_ICONES) {
      rendre(<Icone nom={nom} />);
      const svg = $("svg")!;
      expect(svg.getAttribute("aria-hidden")).toBe("true");
      expect(svg.querySelectorAll("path").length).toBeGreaterThan(0);
    }
  });
});

describe("Message", () => {
  it("une erreur est un alert, le reste un status, et chaque variante dit son nom au lecteur d'écran", () => {
    rendre(<Message variante="erreur" testId="m">Impossible</Message>);
    expect($('[data-testid="m"]')!.getAttribute("role")).toBe("alert");
    expect($(".message-icone")!.getAttribute("aria-label")).toBe("Erreur");
    expect($('[data-testid="m"]')!.textContent).toBe("Impossible");   // rien d'ajouté au texte
    rendre(<Message variante="succes" testId="m">Fait</Message>);
    expect($('[data-testid="m"]')!.getAttribute("role")).toBe("status");
    expect($(".message-icone")!.getAttribute("aria-label")).toBe("Succès");
  });
  it("le bouton de fermeture est étiqueté et appelle onFermer ; le rôle explicite l'emporte", () => {
    const fermer = vi.fn();
    rendre(<Message variante="info" titre="Titre" role="alert" onFermer={fermer} testId="m">Texte</Message>);
    expect($('[data-testid="m"]')!.getAttribute("role")).toBe("alert");
    expect($(".message-titre")!.textContent).toBe("Titre");
    const bouton = $('[data-testid="m-fermer"]')!;
    expect(bouton.getAttribute("aria-label")).toBe("Fermer le message");
    cliquer(bouton);
    expect(fermer).toHaveBeenCalledTimes(1);
  });
});

describe("Badge", () => {
  it("porte une forme en plus de la couleur, cachée au lecteur d'écran", () => {
    rendre(<Badge ton="succes" forme="plein" testId="b">Actif</Badge>);
    const b = $('[data-testid="b"]')!;
    expect(b.className).toContain("badge-ton-succes");
    expect(b.querySelector(".badge-forme")!.getAttribute("aria-hidden")).toBe("true");
    expect(b.textContent).toContain("Actif");
  });
  it("déduit le ton d'un statut métier ; un statut inconnu reste neutre et sans forme", () => {
    expect(tonDuStatut("paid")).toEqual(["succes", "plein"]);
    expect(tonDuStatut("DRAFT")).toEqual(["neutre", "vide"]);
    expect(tonDuStatut("en_cours")).toEqual(["avertissement", "demi"]);
    expect(tonDuStatut("cancelled")).toEqual(["erreur", "plein"]);
    expect(tonDuStatut("inconnu")).toEqual(["neutre", "aucune"]);
    expect(tonDuStatut(null)).toEqual(["neutre", "aucune"]);
    rendre(<BadgeStatut statut="sent" testId="s" />);
    expect($('[data-testid="s"]')!.textContent).toContain("sent");
    rendre(<BadgeStatut statut={undefined} testId="s">Envoyée</BadgeStatut>);
    expect($('[data-testid="s"]')!.textContent).toBe("Envoyée");
  });
});

describe("EtatVide", () => {
  it("nomme ce qui manque et propose l'action, en variante compacte aussi", () => {
    rendre(<EtatVide titre="Aucun contrat" action={<button>Créer</button>} testId="v">Rien pour l'instant.</EtatVide>);
    expect($(".etat-vide-titre")!.textContent).toBe("Aucun contrat");
    expect($(".etat-vide-texte")!.textContent).toBe("Rien pour l'instant.");
    expect($(".etat-vide-action button")!.textContent).toBe("Créer");
    rendre(<EtatVide titre="Vide" compact testId="v" />);
    expect($('[data-testid="v"]')!.className).toContain("etat-vide-compact");
    expect($(".etat-vide-texte")).toBeNull();
  });
});

describe("EnTeteEcran", () => {
  it("rend un h1 focalisable avec ses actions, ou un h2 pour une section", () => {
    rendre(<EnTeteEcran titre="Absences" description="Les demandes." icone="calendrier" testId="t" actions={<button>Déclarer</button>}><p>Sous</p></EnTeteEcran>);
    const h1 = $("h1")!;
    expect(h1.getAttribute("data-testid")).toBe("t");
    expect(h1.getAttribute("tabindex")).toBe("-1");
    expect(h1.textContent).toBe("Absences");
    expect($(".ecran-entete-description")!.textContent).toBe("Les demandes.");
    expect($(".ecran-entete-actions button")!.textContent).toBe("Déclarer");
    expect($(".ecran-entete > p")!.textContent).toBe("Sous");
    rendre(<EnTeteEcran titre="Section" niveau={2} />);
    expect($("h2")).not.toBeNull();
    expect($("h1")).toBeNull();
  });
});

describe("Onglets", () => {
  const onglets = [{ id: "a", libelle: "A", testId: "tab-a" }, { id: "b", libelle: "B", testId: "tab-b", compteur: 3 }, { id: "c", libelle: "C", desactive: true, raison: "module absent" }] as const;
  it("suit le motif ARIA tabs : aria-selected, tabindex tournant, panneau lié, compteur annoncé", () => {
    const changer = vi.fn();
    rendre(<Onglets onglets={onglets} actif="a" onChange={changer} libelle="Vues" testId="tl"><p>Panneau A</p></Onglets>);
    const liste = $('[role="tablist"]')!;
    expect(liste.getAttribute("aria-label")).toBe("Vues");
    const a = $('[data-testid="tab-a"]')!, b = $('[data-testid="tab-b"]')!;
    expect(a.getAttribute("aria-selected")).toBe("true");
    expect(a.getAttribute("tabindex")).toBe("0");
    expect(b.getAttribute("aria-selected")).toBe("false");
    expect(b.getAttribute("tabindex")).toBe("-1");
    expect(b.querySelector(".onglet-compteur")!.getAttribute("aria-label")).toBe("3 à traiter");
    const panneau = $('[role="tabpanel"]')!;
    expect(panneau.getAttribute("aria-labelledby")).toBe(a.id);
    expect(a.getAttribute("aria-controls")).toBe(panneau.id);
    expect(b.hasAttribute("aria-controls")).toBe(false);  // son panneau n'est pas dans le document : pas de référence vide
    const c = $$('[role="tab"]')[2];
    expect(c.hasAttribute("disabled")).toBe(true);
    expect(c.textContent).toContain("module absent");
    cliquer(b);
    expect(changer).toHaveBeenCalledWith("b");
  });
  it("sans enfants, ne rend pas de panneau ; en secondaire, porte la classe", () => {
    rendre(<Onglets onglets={onglets} actif="b" onChange={() => undefined} libelle="Vues" secondaires />);
    expect($('[role="tabpanel"]')).toBeNull();
    expect($('[data-testid="tab-b"]')!.hasAttribute("aria-controls")).toBe(false);  // aucun panneau promis : aucune référence
    expect($('[role="tablist"]')!.className).toContain("tabs-secondaires");
    rendre(<Onglets onglets={onglets} actif="b" onChange={() => undefined} libelle="Vues" prefixeIds="x" />);
    expect($('[data-testid="tab-b"]')!.getAttribute("aria-controls")).toBe("x-tabpanel-b");
    expect($('[data-testid="tab-b"]')!.id).toBe("x-tabbtn-b");
  });
});

describe("BarreFiltres", () => {
  it("annonce le décompte et n'offre « Réinitialiser » que si un filtre est posé", () => {
    expect(libelleResultats(1)).toBe("1 résultat");
    expect(libelleResultats(12, 42)).toBe("12 résultats sur 42");
    expect(libelleResultats(42, 42)).toBe("42 résultats");
    const reinit = vi.fn();
    rendre(<BarreFiltres total={2} totalSansFiltre={5} onReinitialiser={reinit} filtresActifs testId="f"><div><label htmlFor="x">Statut</label><select id="x" /></div></BarreFiltres>);
    expect($('[data-testid="f"]')!.getAttribute("role")).toBe("group");
    expect($('[data-testid="f-total"]')!.getAttribute("role")).toBe("status");
    expect($('[data-testid="f-total"]')!.textContent).toBe("2 résultats sur 5");
    cliquer($('[data-testid="f-reinitialiser"]'));
    expect(reinit).toHaveBeenCalled();
    rendre(<BarreFiltres onReinitialiser={reinit} filtresActifs={false} testId="f"><span /></BarreFiltres>);
    expect($('[data-testid="f-reinitialiser"]')).toBeNull();
    expect($('[data-testid="f-total"]')).toBeNull();
  });
});

describe("Tuile", () => {
  it("dit la tendance en mots, pas seulement par la couleur ; cliquable, c'est un bouton", () => {
    expect(libelleTendance(8, "sur un mois")).toBe("en hausse de 8 % sur un mois");
    expect(libelleTendance(-3.25)).toBe("en baisse de 3,3 %");
    expect(libelleTendance(0)).toBe("stable,");
    const ouvrir = vi.fn();
    rendre(<Tuile valeur="12 480 $" libelle="Revenus" periode="ce mois" tendance={8} alerte="2 impayées" onOuvrir={ouvrir} testId="t" />);
    const t = $('[data-testid="t"]')!;
    expect(t.tagName).toBe("BUTTON");
    expect(t.querySelector(".tuile-tendance")!.textContent).toContain("en hausse de 8 %");
    expect(t.querySelector(".tuile-alerte")!.textContent).toContain("2 impayées");
    cliquer(t);
    expect(ouvrir).toHaveBeenCalled();
    rendre(<Tuile valeur="0" libelle="Rien" tendance={0} testId="t" />);
    expect($('[data-testid="t"]')!.tagName).toBe("DIV");
    expect($(".tuile-tendance-stable")).not.toBeNull();
  });
});

describe("Etapes", () => {
  const etapes = [{ id: "1", libelle: "Identité", testId: "e1" }, { id: "2", libelle: "Emploi", testId: "e2" }, { id: "3", libelle: "Compte", testId: "e3" }];
  it("marque l'étape courante, dit « terminée » pour les faites et permet d'y revenir", () => {
    const aller = vi.fn();
    rendre(<Etapes etapes={etapes} courante="2" onAller={aller} testId="et" />);
    expect($('[data-testid="e2"]')!.getAttribute("aria-current")).toBe("step");
    expect($('[data-testid="e1"]')!.textContent).toContain("terminée");
    expect($('[data-testid="e3"]')!.textContent).not.toContain("terminée");
    cliquer($('[data-testid="e1"] button'));
    expect(aller).toHaveBeenCalledWith("1");
    expect($('[data-testid="e3"] button')).toBeNull();
    rendre(<Etapes etapes={etapes} courante="2" />);
    expect($('[data-testid="e1"] button')).toBeNull();
    rendre(<Etapes etapes={etapes} courante="2" onAller={aller} libre />);
    cliquer($('[data-testid="e3"] button'));   // en mode libre, une étape à venir se rejoint directement
    expect(aller).toHaveBeenLastCalledWith("3");
    expect($('[data-testid="e2"] button')).toBeNull();
  });
});

describe("MenuActions", () => {
  const actions = [
    { id: "ouvrir", libelle: "Ouvrir", onClick: vi.fn(), testId: "a-ouvrir" },
    { id: "supprimer", libelle: "Supprimer", onClick: vi.fn(), danger: true, testId: "a-supprimer" },
    { id: "pdf", libelle: "PDF", onClick: vi.fn(), desactive: true, raison: "brouillon", testId: "a-pdf" },
  ];
  it("est un bouton de menu étiqueté ; ouvert, il pose le focus, les flèches circulent, Échap ferme et rend le focus", () => {
    rendre(<MenuActions actions={actions} libelle="Actions sur PAY-001" testId="m" />);
    const bouton = $('[data-testid="m"]')!;
    expect(bouton.getAttribute("aria-haspopup")).toBe("menu");
    expect(bouton.getAttribute("aria-expanded")).toBe("false");
    expect(bouton.getAttribute("aria-label")).toBe("Actions sur PAY-001");
    cliquer(bouton);
    expect(bouton.getAttribute("aria-expanded")).toBe("true");
    const menu = $('[role="menu"]')!;
    expect(menu.getAttribute("aria-label")).toBe("Actions sur PAY-001");
    expect(document.activeElement).toBe($('[data-testid="a-ouvrir"]'));
    touche("ArrowDown");
    expect(document.activeElement).toBe($('[data-testid="a-supprimer"]'));
    touche("ArrowDown");
    expect(document.activeElement).toBe($('[data-testid="a-ouvrir"]'));  // le désactivé est sauté, on boucle
    touche("End");
    expect(document.activeElement).toBe($('[data-testid="a-supprimer"]'));
    touche("ArrowUp"); touche("Home");
    expect($('[data-testid="a-supprimer"]')!.textContent).toContain("irréversible");
    expect($('[data-testid="a-pdf"]')!.textContent).toContain("brouillon");
    touche("Escape");
    expect($('[role="menu"]')).toBeNull();
    expect(document.activeElement).toBe(bouton);
  });
  it("choisir une action ferme le menu et l'exécute ; un clic hors du menu le ferme ; Tab aussi", () => {
    rendre(<MenuActions actions={actions} libelle="Actions" texte="Actions" testId="m" />);
    expect($('[data-testid="m"]')!.textContent).toContain("Actions");
    cliquer($('[data-testid="m"]'));
    cliquer($('[data-testid="a-ouvrir"]'));
    expect(actions[0].onClick).toHaveBeenCalled();
    expect($('[role="menu"]')).toBeNull();
    cliquer($('[data-testid="m"]'));
    act(() => { document.body.dispatchEvent(new MouseEvent("mousedown", { bubbles: true })); });
    expect($('[role="menu"]')).toBeNull();
    cliquer($('[data-testid="m"]'));
    touche("Tab");
    expect($('[role="menu"]')).toBeNull();
  });
});

describe("Tableau", () => {
  type L = { id: string; nom: string; montant: number | null };
  const lignes: L[] = [{ id: "1", nom: "Zoé", montant: 10 }, { id: "2", nom: "Ana", montant: null }, { id: "3", nom: "Émile", montant: 5 }];
  const colonnes = [
    { id: "nom", libelle: "Nom", tri: (l: L) => l.nom },
    { id: "montant", libelle: "Montant", tri: (l: L) => l.montant, numerique: true },
    { id: "actions", libelle: "Actions", actions: true },
  ];
  const rendreLigne = (l: L) => <tr key={l.id} data-testid={`l-${l.id}`}><td>{l.nom}</td><td className="col-numerique">{l.montant ?? "—"}</td><td /></tr>;
  const noms = () => $$("tbody tr").map((tr) => tr.querySelector("td")!.textContent);
  it("a une légende, des en-têtes scope=col, et un tri annoncé par aria-sort qui place les vides en dernier", () => {
    rendre(<Tableau colonnes={colonnes} lignes={lignes} rendre={rendreLigne} legende="Factures" testId="tb" tbodyTestId="corps" />);
    expect($("caption")!.textContent).toBe("Factures");
    expect($("caption")!.className).toBe("sr-only");
    expect($$("th").every((th) => th.getAttribute("scope") === "col")).toBe(true);
    expect($('[data-testid="corps"]')).not.toBeNull();
    expect($$("th")[0].getAttribute("aria-sort")).toBe("none");
    expect($$("th")[2].getAttribute("aria-sort")).toBeNull();
    expect(noms()).toEqual(["Zoé", "Ana", "Émile"]);
    cliquer($('[data-testid="tb-tri-nom"]'));
    expect($$("th")[0].getAttribute("aria-sort")).toBe("ascending");
    expect(noms()).toEqual(["Ana", "Émile", "Zoé"]);
    cliquer($('[data-testid="tb-tri-nom"]'));
    expect($$("th")[0].getAttribute("aria-sort")).toBe("descending");
    expect(noms()).toEqual(["Zoé", "Émile", "Ana"]);
    cliquer($('[data-testid="tb-tri-montant"]'));
    expect(noms()).toEqual(["Émile", "Zoé", "Ana"]);
    cliquer($('[data-testid="tb-tri-montant"]'));
    expect(noms()).toEqual(["Ana", "Zoé", "Émile"]);
  });
  it("une liste vide montre l'état vide ; un tri initial s'applique ; une légende peut être visible", () => {
    rendre(<Tableau colonnes={colonnes} lignes={[]} rendre={rendreLigne} legende="Factures" legendeVisible vide={<tr><td colSpan={3}>Aucune facture</td></tr>} />);
    expect($("tbody")!.textContent).toBe("Aucune facture");
    expect($("caption")!.className).toBe("");
    rendre(<Tableau key="tri-initial" colonnes={colonnes} lignes={lignes} rendre={rendreLigne} legende="F" triInitial={{ colonne: "nom", sens: "desc" }} />);
    expect(noms()).toEqual(["Zoé", "Émile", "Ana"]);
    rendre(<Tableau colonnes={colonnes} lignes={[]} rendre={rendreLigne} legende="F" />);
    expect($(".empty-cell")!.textContent).toBe("Aucun élément.");
  });
  it("**un état vide donné en phrase devient une ligne du tableau**, et non un texte hors du tableau", () => {
    rendre(<Tableau colonnes={colonnes} lignes={[]} rendre={rendreLigne} legende="F" vide="Aucun ordre." />);
    // Un nœud de texte nu dans un `<tbody>` est remonté hors du tableau par le navigateur :
    // la phrase s'afficherait alors au-dessus de l'en-tête, orpheline de la liste qu'elle décrit.
    const cellule = $("tbody .empty-cell") as HTMLTableCellElement;
    expect(cellule.textContent).toBe("Aucun ordre.");
    expect(cellule.colSpan).toBe(colonnes.length);
    expect([...$("tbody")!.childNodes].every((n) => n.nodeType === Node.ELEMENT_NODE)).toBe(true);
  });
  it("**un état vide donné en composant devient aussi une ligne** : un `<div>` nu quitterait le tableau", () => {
    // La forme la plus répandue du dépôt : `vide={<EtatVide …>}`, c'est-à-dire un `<div>`. Placé
    // nu dans un `<tbody>`, le navigateur le remonte hors du tableau, au-dessus de l'en-tête.
    rendre(<Tableau colonnes={colonnes} lignes={[]} rendre={rendreLigne} legende="F"
      vide={<EtatVide titre="Aucune facture">Créez-en une.</EtatVide>} />);
    const cellule = $("tbody .empty-cell") as HTMLTableCellElement;
    expect(cellule.textContent).toContain("Aucune facture");
    expect(cellule.colSpan).toBe(colonnes.length);
    expect([...$("tbody")!.children].map((n) => n.tagName)).toEqual(["TR"]);
  });
  it("un état vide **déjà donné en `<tr>`** passe tel quel, sans double enveloppe", () => {
    rendre(<Tableau colonnes={colonnes} lignes={[]} rendre={rendreLigne} legende="F"
      vide={<tr data-testid="tb-vide-propre"><td colSpan={3}>Rien</td></tr>} />);
    expect($('[data-testid="tb-vide-propre"]')).not.toBeNull();
    expect($("tbody")!.querySelectorAll("tr").length).toBe(1);
  });
});

describe("FilAriane", () => {
  it("est une navigation étiquetée dont le dernier maillon est la page courante", () => {
    const aller = vi.fn();
    rendre(<FilAriane maillons={[{ libelle: "Paie & RH" }, { libelle: "Absences", onClick: aller, testId: "fa-absences" }, { libelle: "Demandes", testId: "fa-demandes" }]} />);
    expect($("nav")!.getAttribute("aria-label")).toBe("Fil d'Ariane");
    expect($('[data-testid="fa-demandes"]')!.getAttribute("aria-current")).toBe("page");
    expect($(".fil-ariane-repere")!.textContent).toBe("Paie & RH");
    cliquer($('[data-testid="fa-absences"]'));
    expect(aller).toHaveBeenCalled();
    rendre(<FilAriane maillons={[]} />);
    expect($("nav")).toBeNull();
  });
});

describe("Tiroir", () => {
  it("est un dialogue titré, ancré à droite, avec ses actions et une fermeture", () => {
    const fermer = vi.fn();
    rendre(<Tiroir ouvert onFermer={fermer} titre="Facture PAY-001" testId="t" actions={<button>PDF</button>}><p>Corps</p></Tiroir>);
    const d = $('[role="dialog"]')!;
    expect(d.className).toContain("tiroir");
    expect(d.getAttribute("aria-modal")).toBe("true");
    expect(document.getElementById(d.getAttribute("aria-labelledby")!)!.textContent).toBe("Facture PAY-001");
    expect($(".modal-entete-actions button")!.textContent).toBe("PDF");
    cliquer($('[data-testid="t-close"]'));
    expect(fermer).toHaveBeenCalled();
    rendre(<Tiroir ouvert={false} onFermer={fermer} titre="X"><p /></Tiroir>);
    expect($('[role="dialog"]')).toBeNull();
  });
});

describe("Calendrier", () => {
  it("place le 1er sous le bon jour (lundi en tête) et complète à six semaines sur demande", () => {
    const cases = casesDuMois(new Date(2026, 9, 1), false, "2026-10-19");   // octobre 2026 commence un jeudi
    expect(cases.slice(0, 3)).toEqual([null, null, null]);
    expect(cases[3]).toEqual({ date: "2026-10-01", dansLeMois: true, aujourdhui: false });
    expect(cases.length % 7).toBe(0);
    expect(cases.find((c) => c?.date === "2026-10-19")?.aujourdhui).toBe(true);
    const completes = casesDuMois(new Date(2026, 9, 1), true);
    expect(completes.length).toBe(42);
    expect(completes[0]).toMatchObject({ date: "2026-09-28", dansLeMois: false });
    expect(dateIso(new Date(2026, 0, 5))).toBe("2026-01-05");
  });
  it("est une grille étiquetée ; la barre change de mois ; une case cliquable est un bouton sélectionnable", () => {
    const onMois = vi.fn(); const choisir = vi.fn();
    rendre(<Calendrier mois={new Date(2026, 9, 1)} onMois={onMois} libelle="Calendrier d'essai" rendreJour={(j) => <span>{j.date.slice(-2)}</span>} testId="c" />);
    const grille = $('[data-testid="c-grid"]')!;
    expect(grille.getAttribute("role")).toBe("grid");
    expect(grille.getAttribute("aria-label")).toMatch(/Calendrier d'essai — octobre 2026/);
    expect($$('[role="columnheader"]').length).toBe(7);
    expect($('[data-testid="c-day-2026-10-01"]')!.getAttribute("role")).toBe("gridcell");
    expect($('[data-testid="c-month-label"]')!.textContent).toMatch(/octobre 2026/);
    cliquer($('[data-testid="c-next"]'));
    expect(onMois.mock.calls[0][0].getMonth()).toBe(10);
    cliquer($('[data-testid="c-prev"]'));
    expect(onMois.mock.calls[1][0].getMonth()).toBe(8);
    cliquer($('[data-testid="c-today"]'));
    expect(onMois.mock.calls[2][0].getDate()).toBe(1);
    rendre(<Calendrier mois={new Date(2026, 9, 1)} onMois={onMois} libelle="Essai" semainesCompletes jourChoisi="2026-10-02" onChoisirJour={choisir}
                       libelleJour={(j) => `${j.date}, 0 événement`} rendreJour={() => null} testId="c" />);
    const case2 = $('[data-testid="c-day-2026-10-02"]')!;
    expect(case2.tagName).toBe("BUTTON");
    expect(case2.getAttribute("aria-selected")).toBe("true");
    expect(case2.getAttribute("aria-label")).toBe("2026-10-02, 0 événement");
    cliquer(case2);
    expect(choisir).toHaveBeenCalledWith("2026-10-02");
    expect($('[data-testid="c-day-2026-09-28"]')!.className).toContain("cal-cell-out");
  });
});
