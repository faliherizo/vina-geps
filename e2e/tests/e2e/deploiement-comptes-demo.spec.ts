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
interface Deploiement { resolution?: string; domaineRacine?: string | null; etablissement?: { id: string; nom: string } | null }

/**
 * L'adresse où les comptes d'un organisme se connectent, et l'organisme visé. En déploiement par sous-domaine
 * (ADR 0033), c'est `https://<sousDomaine>.<racine>` : l'hôte désigne l'organisme, la liste anonyme n'existe plus.
 * Sinon (sélecteur), l'adresse commune et l'exemplaire le plus récent de la liste publique.
 */
async function cibleDe(playwright: { request: { newContext: (o: { baseURL: string }) => Promise<import("@playwright/test").APIRequestContext> } },
  organisme: (typeof comptesDemo.organismes)[number]) {
  const racine = await playwright.request.newContext({ baseURL: process.env.E2E_BASE_URL! });
  const d: Deploiement = await (await racine.get("/api/auth/deploiement")).json();
  if (d.resolution === "sous_domaine" && d.domaineRacine) {
    const u = new URL(process.env.E2E_BASE_URL!);
    u.hostname = `${organisme.sousDomaine}.${d.domaineRacine}`;
    const api = await playwright.request.newContext({ baseURL: u.origin });
    const r = await api.get("/api/auth/deploiement");
    const corps: Deploiement = r.ok() ? await r.json() : {};
    expect(corps.etablissement, `${u.host} ne désigne aucun organisme : lancer scripts/semer-demo-geps.cjs`).toBeTruthy();
    expect(corps.etablissement!.nom.startsWith(organisme.nom), `${u.host} désigne « ${corps.etablissement!.nom} »`).toBe(true);
    await racine.dispose();
    return { api, id: corps.etablissement!.id, nom: corps.etablissement!.nom };
  }
  const publics: OrganismePublic[] = await (await racine.get("/api/auth/tenants")).json();
  // Les semis horodatés se reconnaissent à leur préfixe ; le plus récent est celui que la démonstration montre.
  const trouves = publics.filter((t) => organisme.correspondance === "exact" ? t.name === organisme.nom : t.name.startsWith(organisme.nom));
  expect(trouves.length, `« ${organisme.nom} » absent : lancer scripts/semer-demo-geps.cjs`).toBeGreaterThan(0);
  const cible = trouves.sort((a, b) => b.name.localeCompare(a.name))[0];
  return { api: racine, id: cible.id, nom: cible.name };
}

test.use({ storageState: { cookies: [], origins: [] } });

test.describe("Déploiement — les comptes de démonstration publiés", () => {
  test.skip(!DEPLOYE || !SEMES, "vise une démonstration déployée et semée : poser E2E_BASE_URL et E2E_COMPTES_DEMO=1");

  for (const organisme of comptesDemo.organismes) {
    test(`${organisme.nom} : chaque compte publié ouvre une session`, async ({ playwright }) => {
      test.setTimeout(120_000);
      const cible = await cibleDe(playwright, organisme);
      const refuses: string[] = [];
      for (const compte of organisme.comptes) {
        const r = await cible.api.post("/api/auth/login", { data: { username: compte.username, password: MDP, tenantId: cible.id } });
        const corps = r.ok() ? await r.json() : null;
        if (!corps?.token) refuses.push(`${compte.username} (${r.status()})`);
      }
      expect(refuses, `comptes refusés dans « ${cible.nom} »`).toEqual([]);
      await cible.api.dispose();
    });
  }

  test("la racine n'ouvre que les comptes globaux : aucun sélecteur d'organisme", async ({ playwright }) => {
    const racine = await playwright.request.newContext({ baseURL: process.env.E2E_BASE_URL! });
    const d: Deploiement = await (await racine.get("/api/auth/deploiement")).json();
    test.skip(d.resolution !== "sous_domaine", "déploiement par sélecteur : la liste des organismes y est publique par conception");
    expect((await racine.get("/api/auth/tenants")).status(), "liste anonyme des organismes").toBe(404);
    // Le choix de la vitrine (/acceder, ADR 0094) propose chaque organisme de la démonstration, avec l'adresse de son hôte.
    const proposes: { nom: string; adresse: string }[] = await (await racine.get("/api/auth/organismes")).json();
    for (const o of comptesDemo.organismes) {
      const attendue = `https://${o.sousDomaine}.${d.domaineRacine}/admin/login`;
      expect(proposes.map((p) => p.adresse), `« ${o.nom} » absent du choix de la vitrine`).toContain(attendue);
    }
    expect(proposes.every((p) => !("id" in p)), "le choix public ne donne pas d'identifiant").toBe(true);
    // `admin` existe aussi hors organisme (compte global) : seul un compte propre à un organisme prouve le refus.
    const propre = comptesDemo.organismes[0].comptes.find((c) => c.username !== "admin")!;
    const refus = await racine.post("/api/auth/login", { data: { username: propre.username, password: MDP } });
    expect(refus.ok(), `${propre.username} ouvert sur la racine`).toBe(false);
    await racine.dispose();
  });

  /**
   * Le SSO envoie le navigateur sur l'hôte où l'on a cliqué, jamais sur l'adresse interne du serveur. Le 23 novembre
   * 2026, « Se connecter avec Microsoft » menait à http://localhost:4000/mock/… sur la racine comme sur Clincom : aucun
   * essai ne suivait la redirection. Lecture seule : la page du fournisseur s'ouvre, aucun compte n'est choisi.
   */
  test("Microsoft et SAML mènent à une page de connexion atteignable, sur l'hôte de la requête", async ({ playwright }) => {
    const racine = await playwright.request.newContext({ baseURL: process.env.E2E_BASE_URL! });
    const d: Deploiement = await (await racine.get("/api/auth/deploiement")).json();
    await racine.dispose();
    test.skip(d.resolution !== "sous_domaine", "déploiement par sélecteur : l'hôte de la requête n'est pas retenu");
    const hotes = [d.domaineRacine!, ...comptesDemo.organismes.map((o) => `${o.sousDomaine}.${d.domaineRacine}`)];
    const defauts: string[] = [];
    for (const hote of hotes) {
      const api = await playwright.request.newContext({ baseURL: `https://${hote}`, maxRedirects: 0 });
      for (const fournisseur of ["entra", "saml"]) {
        const statut = await (await api.get(`/api/auth/sso/${fournisseur}/status`)).json();
        if (!statut.enabled) continue;
        const r = await api.get(`/api/auth/sso/${fournisseur}/login`);
        const vers = r.headers()["location"] ?? "";
        if (r.status() !== 302 || !vers.startsWith(`https://${hote}/`)) { defauts.push(`${hote} ${fournisseur} : ${r.status()} → ${vers.slice(0, 90)}`); continue; }
        const page = await api.get(vers);
        if (page.status() !== 200) defauts.push(`${hote} ${fournisseur} : la page du fournisseur répond ${page.status()}`);
      }
      await api.dispose();
    }
    expect(defauts).toEqual([]);
  });
});
