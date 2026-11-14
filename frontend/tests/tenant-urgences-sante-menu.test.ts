/**
 * **Ce qu'un organisme d'Urgences-santé voit vraiment dans son menu.**
 *
 * Question posée le 2 novembre 2026 : « pourquoi les modules Comptabilité et Paie & RH sont-ils
 * présents dans le locataire Urgences-santé ? » — alors que son abonnement ne porte que les
 * onze codes `effectifs_*` et le socle des horaires cliniques.
 *
 * La réponse n'est pas un module mal accordé, et c'est pourquoi elle se mesure ici plutôt que de
 * se raconter : **ces deux sections ne portent aucun code de module**, et chacune héberge encore
 * des écrans du **socle** — ceux que tout compte paie, énumérés avec leur motif dans
 * `menus-gouvernes-par-module.test.ts`. Une section s'affiche dès qu'une de ses entrées est
 * visible : il suffit donc d'`orgchart` pour faire apparaître « Paie & RH », et de `rapports`
 * pour « Comptabilité ».
 *
 * Deux conséquences, et cet essai les fixe toutes les deux :
 *
 * 1. **la liste des fuites est nommée** — un écran du socle ajouté demain sous une section
 *    sectorielle fait échouer l'essai, au lieu d'apparaître en silence chez un client ;
 * 2. **le masquage par organisme les referme** : c'est le réglage prévu pour cela
 *    (Superadmin › Modules et accès), et le semoir de la démonstration le pose.
 */
import { describe, expect, it } from "vitest";
import { NAVIGATION, estConcept, type Vue } from "../src/navigation";
import { PAGE_MODULE } from "../src/modulesParPage";

/** L'abonnement de l'organisme de démonstration, tel que le semoir l'accorde. */
const MODULES_URGENCES_SANTE = new Set([
  "effectifs", "effectifs_regles", "effectifs_planification", "effectifs_disponibilites",
  "effectifs_absences", "effectifs_echanges", "effectifs_agph", "effectifs_presence",
  "effectifs_temps", "effectifs_integrations",
  // La communication du personnel ne dépend plus que du socle du module.
  "effectifs_communication",
]);

/** La même règle qu'`App.tsx` : un code absent de l'abonnement ferme l'entrée. */
const accorde = (code?: string | null) => !code || MODULES_URGENCES_SANTE.has(code);

/** Les vues visibles d'une section, section et entrée et page gardées comme dans l'application. */
function vuesVisibles(groupe: (typeof NAVIGATION)[number]): Vue[] {
  return groupe.entrees
    .flatMap((e) => (estConcept(e) ? e.vues : [e]))
    .filter((v) => accorde(v.module) && accorde(PAGE_MODULE[v.page as keyof typeof PAGE_MODULE]));
}

const sectionsVisibles = () => NAVIGATION.filter((g) => vuesVisibles(g).length > 0).map((g) => g.key);

describe("le menu d'un organisme d'Urgences-santé", () => {
  it("les rubriques visibles sont le menu principal, l'administration, l'aide et le compte — et rien d'autre", () => {
    expect(sectionsVisibles()).toEqual(["urgencessante", "admin", "space"]);
  });

  it("chaque écran du module porte son code, et un code retiré retire l'écran", () => {
    const us = NAVIGATION.find((g) => g.key === "urgencessante")!;
    // Les écrans du module : ceux qui portent son socle. Le tableau de bord et les rapports n'en sont pas.
    const pages = us.entrees.flatMap((e) => (estConcept(e) ? e.vues : [e])).filter((v) => v.module === "effectifs").map((v) => v.page);
    expect(pages.length).toBeGreaterThan(20);
    for (const page of pages) expect(PAGE_MODULE[page as keyof typeof PAGE_MODULE], page).toBeDefined();
    MODULES_URGENCES_SANTE.delete("effectifs_communication");
    try {
      const visibles = vuesVisibles(us).map((v) => v.page);
      expect(visibles).not.toContain("usmessages");
      expect(visibles).toContain("uscomblement");
    } finally {
      MODULES_URGENCES_SANTE.add("effectifs_communication");
    }
  });

  it("sans le socle du module, tous ses écrans disparaissent — le tableau de bord et les rapports restent", () => {
    MODULES_URGENCES_SANTE.delete("effectifs");
    try {
      const principal = vuesVisibles(NAVIGATION.find((g) => g.key === "urgencessante")!).map((v) => v.page);
      // « Approbations » a son propre module, que cet abonnement ne contient pas : seuls restent le
      // tableau de bord et les rapports, qui n'appartiennent à aucun module d'effectifs.
      expect(principal).toEqual(["dashboard", "rapports"]);
      const admin = vuesVisibles(NAVIGATION.find((g) => g.key === "admin")!).map((v) => v.page);
      expect(admin.filter((p) => p.startsWith("us"))).toEqual([]);
    } finally {
      MODULES_URGENCES_SANTE.add("effectifs");
    }
  });
});
