/**
 * **Les parcours de l'avis 26-015-DO, joués par les personnes qu'ils concernent.**
 *
 * L'essai des exigences (`urgences-sante-exigences.spec.ts`) prouve que chaque commande est à
 * l'écran. Il le fait depuis le compte d'**administration**, et c'est sa limite : un écran peut
 * être complet pour l'administratrice et inatteignable pour la paramédic à qui il est destiné —
 * un rôle manquant, une ressource non reliée, un module fermé, et personne ne le voit.
 *
 * Celui-ci crée les **comptes**, les relie à leur ressource, ouvre une session **au nom de chacun**
 * et joue le parcours. Tout est dans `e2e/donnees/urgences-sante/profils.json` : ajouter une
 * exigence à prouver ne demande pas de toucher à ce fichier-ci, et le fichier dit **qui** fait
 * **quoi**, avec le motif de chaque geste.
 *
 * Ce qu'il ne prouve pas : le calcul. Qu'une saisie apparaisse ne dit pas qu'elle est payée juste.
 * C'est le travail des essais d'intégration nommés par `essaiProfond` dans le jeu des exigences.
 */
import { test, expect, type Page } from "@playwright/test";
import { superadminContext } from "./helpers/auth";
import { createTenant, setTenantModules, uniq } from "./helpers/organisme";
import { auditA11y } from "./helpers/a11y";
import { commandePresente, poser, type Geste } from "./helpers/gestes";
import {
  amorcerOrganisme, identifiantDe, modulesDeLOrganisme, seConnecterProfil, type Profil,
} from "./helpers/urgences-sante";
import jeu from "../../donnees/urgences-sante/profils.json";

test.use({ storageState: { cookies: [], origins: [] } });

interface Attendu { visible: string; pourquoi: string }
interface Parcours {
  id: string; titre: string; profil: string; ecran: string;
  gestes?: Geste[]; attendu: Attendu[];
}

const PROFILS = jeu.profils as Profil[];
const PARCOURS = jeu.parcours as Parcours[];

test.describe("26-015-DO — les parcours, joués par les personnes qu'ils concernent", () => {
  let tenantId = "";

  test.beforeAll(async () => {
    const sa = await superadminContext();
    try {
      tenantId = await createTenant(sa, uniq("US-PARC"));
      await setTenantModules(sa, tenantId, modulesDeLOrganisme);
    } finally { await sa.dispose(); }
    await amorcerOrganisme(tenantId, { avecTempsEtCollecte: true, profils: PROFILS });
  });

  test("chaque parcours se joue au nom de son profil, et l'écran passe axe-core", async ({ page }) => {
    // **Son budget est celui de ce qu'il mesure** : une connexion et un écran par parcours.
    test.setTimeout(PARCOURS.length * 45_000 + 120_000);

    const echecs: string[] = [];
    const violations: string[] = [];
    let profilOuvert = "";

    for (const p of PARCOURS) {
      const profil = PROFILS.find((x) => x.cle === p.profil);
      expect(profil, `profil « ${p.profil} » absent du fichier de données (${p.id})`).toBeTruthy();

      // Une session par profil, gardée tant que les parcours suivants sont les siens : se
      // reconnecter huit fois pour trois personnes coûterait cinq connexions pour rien.
      if (profilOuvert !== p.profil) {
        await seConnecterProfil(page, tenantId, identifiantDe(profil!, tenantId));
        profilOuvert = p.profil;
      }

      // Une entrée de menu, une page : l'adresse est la clé de l'écran.
      await page.goto(`/${p.ecran}`);
      for (const g of p.gestes ?? []) {
        try { await poser(page, g); }
        catch (e) { echecs.push(`${p.id} (${profil!.nom}) — geste « ${g.pourquoi} » : ${(e as Error).message}`); }
      }
      for (const a of p.attendu) {
        if (!(await commandePresente(page, a.visible))) {
          echecs.push(`${p.id} (${profil!.nom}) — « ${a.visible} » absent : ${a.pourquoi}`);
        }
      }

      const fautes = await auditA11y(page);
      if (fautes.length) violations.push(`${p.id} : ${fautes.map((v) => v.id).join(", ")}`);
    }

    // Lister plutôt que s'arrêter au premier : l'échec doit nommer tout ce qu'il faut corriger.
    expect(echecs, "parcours qui ne se jouent pas").toEqual([]);
    expect(violations, "écrans en violation d'axe-core, vus du profil concerné").toEqual([]);
  });

  test("le fichier de données se tient : chaque parcours nomme un profil, un écran et une preuve", () => {
    const cles = new Set(PROFILS.map((p) => p.cle));
    expect(PARCOURS.filter((p) => !cles.has(p.profil)).map((p) => p.id)).toEqual([]);
    // Un parcours sans attendu passerait toujours : c'est le pire des essais, celui qui rassure.
    expect(PARCOURS.filter((p) => !p.attendu?.length).map((p) => p.id)).toEqual([]);
    // Chaque geste et chaque attendu dit **pourquoi** : sans motif, personne ne saura le corriger.
    const muets = PARCOURS.flatMap((p) => [...(p.gestes ?? []), ...p.attendu]
      .filter((g) => !g.pourquoi).map(() => p.id));
    expect(muets).toEqual([]);
    expect(PROFILS.filter((p) => !p.pourquoi).map((p) => p.cle)).toEqual([]);
  });
});
