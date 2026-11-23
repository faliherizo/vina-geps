/**
 * **Les comptes publiés de la démonstration ouvrent une session** — vise une pile déployée (`E2E_BASE_URL`).
 *
 * docs/COMPTES-DEMO-GEPS.md promet, à qui reçoit le lien de la démonstration, un organisme, un nom de connexion et un mot
 * de passe. Une promesse de ce genre se périme sans bruit : un semis qui échoue à moitié, une base recréée, un mot de passe
 * changé à l'écran — et la personne à qui on montre le produit lit « identifiants invalides ». Cet essai rejoue chaque
 * ligne du tableau, depuis la source même du document (`e2e/donnees/geps/comptes-demo.json`).
 *
 * Lecture seule, comme toute la fumée d'un déploiement : il ouvre une session et lit le profil, rien d'autre.
 */
import { test, expect } from "@playwright/test";
import comptesDemo from "../../donnees/geps/comptes-demo.json";

const DEPLOYE = !!process.env.E2E_BASE_URL;
/** Un environnement réel (recette Azure, production) n'a pas ces comptes : l'essai ne vaut que là où ils sont semés. */
const SEMES = process.env.E2E_COMPTES_DEMO === "1";
const MDP = process.env.E2E_DEMO_PASSWORD || comptesDemo.motDePasseParDefaut;

interface OrganismePublic { id: string; name: string }

test.use({ storageState: { cookies: [], origins: [] } });

test.describe("Déploiement — les comptes de démonstration publiés", () => {
  test.skip(!DEPLOYE || !SEMES, "vise une démonstration déployée et semée : poser E2E_BASE_URL et E2E_COMPTES_DEMO=1");

  for (const organisme of comptesDemo.organismes) {
    test(`${organisme.nom} : chaque compte publié ouvre une session`, async ({ playwright }) => {
      test.setTimeout(120_000);
      const api = await playwright.request.newContext({ baseURL: process.env.E2E_BASE_URL });
      const publics: OrganismePublic[] = await (await api.get("/api/auth/tenants")).json();
      // Les semis horodatés se reconnaissent à leur préfixe ; le plus récent est celui que la démonstration montre.
      const trouves = publics.filter((t) => organisme.correspondance === "exact" ? t.name === organisme.nom : t.name.startsWith(organisme.nom));
      expect(trouves.length, `« ${organisme.nom} » absent : lancer scripts/semer-demo-geps.cjs`).toBeGreaterThan(0);
      const cible = trouves.sort((a, b) => b.name.localeCompare(a.name))[0];

      const refuses: string[] = [];
      for (const compte of organisme.comptes) {
        const r = await api.post("/api/auth/login", { data: { username: compte.username, password: MDP, tenantId: cible.id } });
        const corps = r.ok() ? await r.json() : null;
        if (!corps?.token) refuses.push(`${compte.username} (${r.status()})`);
      }
      expect(refuses, `comptes refusés dans « ${cible.name} »`).toEqual([]);
      await api.dispose();
    });
  }
});
