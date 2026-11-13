/**
 * **Les saisies de l'avis 26-015-DO : écrire, et prouver que c'est arrivé.**
 *
 * Trois essais pilotés par données existaient déjà pour ce module, et ils mesurent trois choses
 * différentes : `urgences-sante-exigences` prouve que chaque commande est **à l'écran**,
 * `urgences-sante-parcours` qu'elle est **atteignable par la personne concernée**, et
 * `urgences-sante-ecarts` que les douze écarts du document 06 ont leur écran.
 *
 * Aucun des trois ne prouve qu'on peut **écrire**. C'était visible dans le parcours « l'employée
 * ajoute un temps » : il remplissait le formulaire, puis constatait que `mft-segments` et
 * `mft-totaux` étaient *visibles* — deux commandes déjà présentes avant le geste. Il aurait donc
 * passé sur un ajout perdu en route, tout en affirmant dans son motif qu'« une saisie qui ne
 * change aucun total n'a pas été prise ». Un essai qui mesure autre chose que ce qu'il annonce
 * est plus dangereux qu'un essai absent : il rassure.
 *
 * Celui-ci joue les scénarios de `saisies.json` : chacun **écrit** une donnée, puis la relit par
 * son **contenu** — le message que le serveur a renvoyé, la durée recalculée, la ligne qui porte
 * l'heure saisie.
 *
 * Ce qu'il ne prouve pas : la justesse du calcul de paie. C'est le travail des essais
 * d'intégration, et le jeu des exigences les nomme.
 */
import { test, expect } from "@playwright/test";
import { superadminContext } from "./helpers/auth";
import { createTenant, setTenantModules, uniq } from "./helpers/organisme";
import { auditA11y } from "./helpers/a11y";
import { poser, type Geste } from "./helpers/gestes";
import {
  amorcerOrganisme, identifiantDe, jour, modulesDeLOrganisme, seConnecterProfil, type Profil,
} from "./helpers/urgences-sante";
import profils from "../../donnees/urgences-sante/profils.json";
import jeu from "../../donnees/urgences-sante/saisies.json";

test.use({ storageState: { cookies: [], origins: [] } });

interface Saisie {
  id: string; titre: string; profil: string; ecran: string;
  exigences: string[]; gestes: Geste[]; attendu: Geste[];
}

const PROFILS = profils.profils as Profil[];
const SAISIES = jeu.saisies as Saisie[];

/**
 * `{jour:N}` → la date de dans N jours.
 *
 * Un fichier de données ne calcule pas, et une date figée ferait échouer l'essai le lendemain de
 * son écriture — l'échec le plus déroutant qui soit, puisque rien n'a changé dans le code.
 */
const JOUR_RELATIF = /^\{jour:(-?\d+)\}$/;

export function valeurRendue(valeur: string | undefined): string | undefined {
  const m = valeur === undefined ? null : JOUR_RELATIF.exec(valeur);
  return m ? jour(Number(m[1])) : valeur;
}

test.describe("26-015-DO — les saisies : écrire dans les écrans, et le prouver", () => {
  let tenantId = "";

  test.beforeAll(async () => {
    const sa = await superadminContext();
    try {
      tenantId = await createTenant(sa, uniq("US-SAIS"));
      await setTenantModules(sa, tenantId, modulesDeLOrganisme);
    } finally { await sa.dispose(); }
    await amorcerOrganisme(tenantId, { avecTempsEtCollecte: true, profils: PROFILS });
  });

  test("chaque saisie s'écrit, se relit par son contenu, et l'écran passe axe-core", async ({ page }) => {
    // Son budget est celui de ce qu'il mesure : une connexion, un écran et un aller-retour serveur
    // par scénario — plus l'amorce, qui sème l'organisme entier une seule fois.
    test.setTimeout(SAISIES.length * 60_000 + 240_000);

    const echecs: string[] = [];
    const violations: string[] = [];
    let profilOuvert = "";

    for (const s of SAISIES) {
      const profil = PROFILS.find((x) => x.cle === s.profil);
      expect(profil, `profil « ${s.profil} » absent du fichier de données (${s.id})`).toBeTruthy();

      if (profilOuvert !== s.profil) {
        await seConnecterProfil(page, tenantId, identifiantDe(profil!, tenantId));
        profilOuvert = s.profil;
      }
      await page.goto(`/${s.ecran}`);

      // Gestes et attendus passent par le **même** verbe : un attendu de contenu est un constat,
      // et le distinguer par un second mécanisme aurait fait deux copies à corriger.
      for (const g of [...s.gestes, ...s.attendu]) {
        try { await poser(page, { ...g, valeur: valeurRendue(g.valeur) }); }
        catch (e) { echecs.push(`${s.id} (${profil!.nom}) — « ${g.pourquoi} » : ${(e as Error).message.split("\n")[0]}`); }
      }

      const fautes = await auditA11y(page);
      if (fautes.length) violations.push(`${s.id} : ${fautes.map((v) => v.id).join(", ")}`);
    }

    // Lister plutôt que s'arrêter au premier : l'échec doit nommer tout ce qu'il faut corriger.
    expect(echecs, "saisies qui ne s'écrivent pas, ou qui ne se relisent pas").toEqual([]);
    expect(violations, "écrans en violation d'axe-core après la saisie").toEqual([]);
  });

  test("le fichier de données se tient : chaque saisie écrit, puis relit un contenu", () => {
    const cles = new Set(PROFILS.map((p) => p.cle));
    expect(SAISIES.filter((s) => !cles.has(s.profil)).map((s) => s.id)).toEqual([]);

    // **Un scénario sans écriture n'est pas une saisie** : il ferait doublon avec les parcours, et
    // le nom du fichier mentirait sur ce qu'il mesure.
    const ecrit = (g: Geste) => !!(g.cliquer || g.remplir || g.choisir);
    expect(SAISIES.filter((s) => !s.gestes.some(ecrit)).map((s) => s.id)).toEqual([]);

    /*
      **Et un attendu qui ne lit aucun contenu ne prouve pas l'écriture.**

      C'est la faute exacte que cet essai répare : constater qu'un tableau est visible après avoir
      rempli un formulaire ne dit rien, puisque le tableau était là avant. Chaque scénario doit
      donc porter au moins un attendu `texte`.
    */
    expect(SAISIES.filter((s) => !s.attendu.some((a) => a.texte)).map((s) => s.id)).toEqual([]);

    // Chaque geste dit **pourquoi**, et chaque scénario nomme les exigences qu'il sert.
    expect(SAISIES.flatMap((s) => [...s.gestes, ...s.attendu].filter((g) => !g.pourquoi).map(() => s.id))).toEqual([]);
    expect(SAISIES.filter((s) => !s.exigences?.length).map((s) => s.id)).toEqual([]);
    expect(SAISIES.filter((s) => new Set(SAISIES.map((x) => x.id)).size !== SAISIES.length).map((s) => s.id)).toEqual([]);
  });

  test("`{jour:N}` se rend en date, et le reste passe tel quel", () => {
    expect(valeurRendue("{jour:0}")).toBe(jour(0));
    expect(valeurRendue("{jour:-3}")).toBe(jour(-3));
    expect(valeurRendue("08:00")).toBe("08:00");
    expect(valeurRendue(undefined)).toBeUndefined();
    // Un gabarit mal écrit passe tel quel plutôt que de devenir une date surprise : il échouera
    // à la saisie, là où le message nomme le champ.
    expect(valeurRendue("{jour:}")).toBe("{jour:}");
  });
});
