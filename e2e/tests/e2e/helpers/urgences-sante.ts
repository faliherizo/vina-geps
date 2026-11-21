/**
 * **L'amorce d'un organisme d'Urgences-santé** (AO 26-015-DO), partagée par les essais du module
 * `effectifs`.
 *
 * Elle était d'abord écrite dans `urgences-sante-ecarts.spec.ts`. Deux essais en ont besoin, et un
 * organisme amorcé à moitié rend un écran vide : l'essai échoue alors sur l'état vide plutôt que
 * sur ce qu'il cherchait. Elle vit donc ici, en un seul endroit.
 *
 * `avecTempsEtCollecte` ajoute ce que les écrans **de l'employé** exigent pour montrer leurs
 * commandes : un compte relié à une ressource, une période en collecte, et une feuille de temps
 * préparée. Sans cela, `mesdisponibilites`, `monpointage` et `matemps` ne rendent que leur état
 * vide — vrai, mais muet sur l'exigence à prouver.
 */
import { request as pwRequest, expect, type APIRequestContext, type Page } from "@playwright/test";
import { TENANT_ADMIN_PASS, TENANT_ADMIN_USER } from "./organisme";
import { menuPret } from "./nav";
import donnees from "../../../donnees/urgences-sante/ecarts.json";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;

/**
 * Un jour en avant ou en arrière d'aujourd'hui **à Montréal**, au format `AAAA-MM-JJ` — le jour du serveur. Lu en UTC,
 * il passait au lendemain dès 19 h (20 h l'été), et une donnée « d'aujourd'hui » tombait dans le futur.
 */
export function jour(decalage: number): string {
  const d = new Date();
  d.setDate(d.getDate() + decalage);
  return new Intl.DateTimeFormat("en-CA", { timeZone: "America/Montreal" }).format(d);
}

export async function adminDeLOrganisme(tenantId: string): Promise<APIRequestContext> {
  // Deux contextes : mêler un témoin de session à un jeton Bearer déclenche un refus CSRF muet.
  const connexion = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
  const { token } = await (await connexion.post(`${API}/api/auth/login`, {
    data: { username: TENANT_ADMIN_USER, password: TENANT_ADMIN_PASS, tenantId },
  })).json();
  await connexion.dispose();
  return pwRequest.newContext({
    storageState: { cookies: [], origins: [] },
    extraHTTPHeaders: { Authorization: `Bearer ${token}`, "X-Tenant-Id": tenantId },
  });
}

export const modulesDeLOrganisme: string[] = [...donnees.modules];

/** Amorce l'organisme à partir du fichier de données. Un refus du serveur **nomme** son chemin. */
export async function amorcerOrganisme(
  tenantId: string,
  options: { avecTempsEtCollecte?: boolean; profils?: Profil[] } = {},
): Promise<void> {
  const o = donnees.organisme;
  const adm = await adminDeLOrganisme(tenantId);
  const poser = async (chemin: string, data: unknown) => {
    const r = await adm.post(`${API}${chemin}`, { data });
    if (!r.ok()) throw new Error(`amorce ${chemin} : ${r.status()} ${await r.text()}`);
    return r.json();
  };
  try {
    const ref = (type: string, data: Record<string, unknown>) =>
      poser(`/api/effectifs/referentiels/${type}`, { effectifDepuis: "2026-01-01", ...data });
    await ref("unites", o.unite);
    await ref("corps_emploi", o.corpsEmploi);
    for (const s of o.statutsEmploi) await ref("statuts_emploi", s);
    await ref("natures_quart", o.natureQuart);
    // Une nature **ad hoc** : sans elle, le formulaire du quart ponctuel n'a aucune option à offrir.
    await ref("natures_quart", { code: "ADH", libelleFr: "Ad hoc", comportement: "AD_HOC" });
    await ref("types_milieu", o.typeMilieu);
    await ref("plages_horaires", o.plageHoraire);

    const milieu = await poser("/api/effectifs/milieux",
      { ...o.milieu, typeCode: o.typeMilieu.code, uniteCode: o.unite.code });
    for (const r of o.ressources) {
      await poser("/api/effectifs/planification/ressources", {
        matricule: r.matricule, nom: r.nom, corpsEmploiCode: o.corpsEmploi.code,
        uniteCode: o.unite.code, statutEmploiCode: r.statutEmploiCode, anciennete: r.anciennete,
      });
    }
    const cycle = [["J", "J", "J", null, null, null, null]];
    const postes: string[] = [];
    for (const p of o.postes) {
      postes.push((await poser("/api/effectifs/planification/postes", {
        code: p.code, libelle: p.libelle, uniteCode: o.unite.code,
        statutEmploiCode: p.statutEmploiCode, corpsEmploiCode: o.corpsEmploi.code,
        milieuId: milieu.id, natureCode: o.natureQuart.code, cycle, debutCycle: "2031-01-06",
      })).id);
    }
    await poser("/api/effectifs/planification/postes/publier", { ids: postes });
    /**
     * **Les profils avant la campagne**, et c'est l'ordre qui compte.
     *
     * `ouvrir` construit les **rangs** depuis les ressources actives de l'unité **à cet instant**.
     * Les profils étaient semés plus bas : Julie n'existait pas encore, elle n'avait donc aucun
     * rang, et « Mon AGPH » n'affichait rien. L'attendu de l'essai acceptait cette absence
     * (`agm-info|agm-sans-campagne`) — il passait donc sans aucune donnée.
     *
     * Les profils doivent aussi précéder la préparation des feuilles, plus bas : `preparer` bâtit
     * une feuille par ressource de l'unité, et une ressource créée après n'en aurait pas.
     */
    if (options.profils?.length) {
      await amorcerProfils(tenantId, options.profils, o.unite.code, o.corpsEmploi.code, o.statutsEmploi[0].code);
    }

    const c = await poser("/api/effectifs/agph/campagnes", {
      uniteCode: o.unite.code, corpsEmploiCode: o.corpsEmploi.code,
      annee: o.campagne.annee, delaiChoixMinutes: o.campagne.delaiChoixMinutes,
    });
    await poser(`/api/effectifs/agph/campagnes/${c.id}/ouvrir`, {});

    if (options.avecTempsEtCollecte) {
      // Les types d'heures d'abord : sans un type « travaillée » en vigueur, préparer une feuille
      // est refusé — et le refus le dit, ce qui est la bonne façon d'échouer.
      await ref("types_heure", { code: "REG", libelleFr: "Régulier", comportement: "TRAVAILLEE", attributs: { codePaie: "100" } });
      await ref("types_heure", { code: "PNP", libelleFr: "Pause non payée", comportement: "PAUSE_NON_PAYEE" });
      await ref("types_heure", { code: "PREA", libelleFr: "Présence préalable", comportement: "PRESENCE_PREALABLE", attributs: { codePaie: "105" } });
      await ref("types_heure", { code: "HS", libelleFr: "Heures supplémentaires", comportement: "SUPPLEMENTAIRE", attributs: { codePaie: "150", majorationPourcent: 50 } });

      /**
       * Un compte de **planificateur**. L'écran des périmètres écarte volontairement les comptes
       * d'administration — un administrateur voit déjà toutes les unités, lui donner un périmètre
       * ne voudrait rien dire. Sans un compte non administrateur, la liste est vide et l'écran n'a
       * rien à montrer : c'est l'organisme qui était incomplet, pas l'écran qui manquait.
       */
      await poser("/api/auth/users", {
        username: `planif-${tenantId.slice(-6)}`, password: "Passe!Charge2026", role: "manager",
      });

      const moi = (await (await adm.get(`${API}/api/auth/whoami`)).json()).utilisateur.id as string;
      await poser("/api/effectifs/planification/ressources", {
        matricule: "ADM1", nom: "Administratrice de garde", utilisateurId: moi,
        corpsEmploiCode: o.corpsEmploi.code, uniteCode: o.unite.code,
        statutEmploiCode: o.statutsEmploi[0].code, anciennete: 1000,
      });

      /**
       * **Une règle de disponibilité**, pour que le panneau « Règles applicables » ait quelque chose
       * à annoncer. Sans elle, l'écran afficherait « aucune règle ne restreint votre dépôt » —
       * vrai, et muet sur ce que l'essai veut prouver : que la règle est **annoncée avant** le
       * dépôt et non découverte au refus (E-GN-16).
       *
       * Elle avertit sans bloquer : un essai de saisie doit pouvoir déposer. Et son message est
       * **sans jeton**, parce que c'est à cette condition qu'il s'affiche avant tout dépôt — un
       * « {quartsParSemaine} » à l'écran se lirait comme une panne.
       */
      await poser("/api/effectifs/regles", {
        contexte: "DISPONIBILITE", cle: "deux-quarts-par-semaine",
        condition: { fait: "quartsParSemaine", op: "<", valeur: 2 }, action: "AVERTIR",
        message: "La convention attend au moins deux quarts par semaine",
      });

      // Une période **en collecte** : c'est la seule qui ouvre le dépôt des disponibilités.
      const periode = await poser("/api/effectifs/planification/periodes",
        { nom: "Collecte en cours", uniteCode: o.unite.code, debut: jour(30), fin: jour(43) });
      await poser(`/api/effectifs/planification/periodes/${periode.id}/etat`,
        { etat: "COLLECTE", finCollecte: jour(7) });

      /**
       * **Une période publiée, et un quart attribué à chaque profil.**
       *
       * Sans cela, « Mon horaire » et « Mes échanges » n'avaient que leur état vide à montrer —
       * et les essais l'acceptaient (`mho-table|mho-vide|mho-sans-ressource`). Un attendu qui
       * accepte l'absence de donnée passe quoi qu'il arrive : il aurait survécu à la disparition
       * du tableau. L'amorce donne donc de quoi remplir le tableau, et l'attendu l'exige.
       *
       * La période couvre la semaine écoulée et le jour même : c'est la fenêtre que `preparer`
       * reprend ensuite pour bâtir les feuilles, de sorte qu'une feuille porte de vrais segments.
       */
      const publiee = await poser("/api/effectifs/planification/periodes",
        { nom: "Semaine publiée", uniteCode: o.unite.code, debut: jour(-6), fin: jour(7) });
      for (const etat of ["CONFECTION", "PUBLIEE"]) {
        await poser(`/api/effectifs/planification/periodes/${publiee.id}/etat`, { etat });
      }
      const toutes = await (await adm.get(`${API}/api/effectifs/planification/ressources`)).json();
      const plage = o.plageHoraire as { attributs?: { debut?: number; fin?: number } };
      const equipage = await poser("/api/effectifs/planification/equipages", {
        periodeId: publiee.id, date: jour(0), uniteCode: o.unite.code, milieuId: milieu.id,
        natureCode: "ADH", debut: plage.attributs?.debut ?? 360, fin: plage.attributs?.fin ?? 1080,
        minimumOperationnel: 1,
        composition: [{ corpsEmploiCode: o.corpsEmploi.code, nombre: Math.max(2, toutes.length) }],
      });
      for (let i = 0; i < equipage.places.length && i < toutes.length; i += 1) {
        await poser(`/api/effectifs/planification/places/${equipage.places[i].id}/attribuer`,
          { ressourceId: toutes[i].id, forcer: true, justification: "Attribution d'amorce (essai)" });
      }

      await poser("/api/effectifs/temps/feuilles/preparer",
        { uniteCode: o.unite.code, debut: jour(-6), fin: jour(0) });

      /**
       * **Un quart sous son minimum**, dans la semaine publiée : deux places, aucune pourvue.
       *
       * Sans lui, le tableau de comblement n'avait que « tous les quarts sont opérationnels » à
       * montrer — vrai, et muet sur le geste « Combler » ajouté le 8 novembre 2026 (planches s01 et
       * s05), qu'aucun essai n'aurait alors pu jouer. Il est **le surlendemain** : ni dans la
       * fenêtre des feuilles de temps préparées, ni attribué à personne, il ne change ni « Mon
       * horaire » ni les feuilles des profils.
       */
      await poser("/api/effectifs/planification/equipages", {
        periodeId: publiee.id, date: jour(2), uniteCode: o.unite.code, milieuId: milieu.id,
        natureCode: "ADH", debut: plage.attributs?.debut ?? 360, fin: plage.attributs?.fin ?? 1080,
        minimumOperationnel: 2, composition: [{ corpsEmploiCode: o.corpsEmploi.code, nombre: 2 }],
      });

      /**
       * **Le tour de rôle démarré, et un remplacement long terme ouvert.**
       *
       * La campagne était créée et ouverte, jamais démarrée : « Mon AGPH » n'avait donc aucun rang
       * à montrer, et l'attendu de l'essai acceptait cette absence (`agm-info|agm-sans-campagne`).
       * Un attendu qui accepte l'absence de donnée passe quoi qu'il arrive.
       *
       * Le remplacement, lui, est le seul geste de cet écran qui ne dépende pas du **rang** : se
       * manifester demande le même corps d'emploi, pas d'être à son tour. C'est donc lui qui rend
       * un essai de saisie déterministe, quelle que soit l'ancienneté des profils semés.
       */
      await poser(`/api/effectifs/agph/campagnes/${c.id}/demarrer`, {});
      await poser("/api/effectifs/agph/remplacements", {
        posteId: postes[0], origine: "POSTE_VACANT", debut: jour(20), fin: jour(40),
        // La fenêtre se ferme demain midi : dans le futur (exigé), et avant le premier jour remplacé.
        finManifestation: `${jour(1)}T12:00:00.000Z`,
      });
    }
  } finally { await adm.dispose(); }
}

/** Un profil du fichier `profils.json` : un compte, son rôle, et la ressource qu'il incarne. */
export interface Profil {
  cle: string;
  nom: string;
  username: string;
  role: string;
  ressource?: { matricule: string; nom: string; anciennete: number };
  /**
   * **Le niveau du périmètre d'encadrement**, quand le profil encadre.
   *
   * Un gestionnaire sans périmètre n'a **aucune unité** à offrir : l'écran de préparation des
   * feuilles présente une liste vide, et l'on prend pour un défaut d'écran ce qui est un défaut
   * d'amorce. Mesuré le 2 novembre 2026 sur le parcours E-RF-4.
   */
  perimetre?: "PLANIFICATION" | "GESTION" | "LECTURE";
  pourquoi?: string;
}

/** Le mot de passe des comptes d'essai : fort, parce que la politique l'exige d'un mot permanent. */
export const MDP_PROFIL = "Passe!Profil2026";

/**
 * **Crée les comptes des profils, et les rend utilisables.**
 *
 * Un compte naît avec `mustChangePassword` et `securitySetupRequired` : c'est voulu, et c'est la
 * bonne politique ([[account-security-hardening]]). Mais un essai qui devrait franchir le mur de
 * configuration — mot de passe fort, second facteur, trois questions — à chaque connexion
 * mesurerait ce mur, et non l'exigence qu'il vise. Les deux drapeaux sont donc retirés **par
 * l'API d'administration**, comme un administrateur le ferait, et non par une écriture en base.
 *
 * La ressource est créée **reliée au compte** : sans ce lien, les écrans personnels disent à juste
 * titre qu'aucune ressource planifiable ne correspond, et l'essai mesurerait l'état vide.
 *
 * Rend l'identifiant de compte de chaque profil, par clé.
 */
export async function amorcerProfils(
  tenantId: string, profils: Profil[], unite: string, corpsEmploi: string, statutEmploi: string,
): Promise<Record<string, string>> {
  const adm = await adminDeLOrganisme(tenantId);
  const comptes: Record<string, string> = {};
  try {
    const appeler = async (chemin: string, data: unknown, methode: "post" | "patch" = "post") => {
      const r = methode === "post" ? await adm.post(`${API}${chemin}`, { data })
                                   : await adm.patch(`${API}${chemin}`, { data });
      if (!r.ok()) throw new Error(`profil ${chemin} : ${r.status()} ${await r.text()}`);
      return r.json();
    };
    for (const p of profils) {
      const compte = await appeler("/api/auth/users",
        { username: `${p.username}-${tenantId.slice(-6)}`, password: MDP_PROFIL, role: p.role, mustChangePassword: false });
      await appeler(`/api/auth/users/${compte.id}`, { securitySetupRequired: false }, "patch");
      comptes[p.cle] = compte.id;
      if (p.ressource) {
        await appeler("/api/effectifs/planification/ressources", {
          matricule: p.ressource.matricule, nom: p.ressource.nom, utilisateurId: compte.id,
          corpsEmploiCode: corpsEmploi, uniteCode: unite, statutEmploiCode: statutEmploi,
          anciennete: p.ressource.anciennete,
        });
      }
      // Le périmètre d'encadrement : sans lui, ses écrans de console s'ouvrent sur des listes vides.
      if (p.perimetre) {
        await appeler("/api/effectifs/perimetres", {
          utilisateurId: compte.id, uniteCode: unite, etendue: "SOUS_ARBRE", niveau: p.perimetre,
        });
      }
    }

    /*
      Les groupes de la communication sont les **unités en vigueur** des référentiels, et leurs
      membres les ressources actives de l'unité liées à un compte : les ressources créées ci-dessus
      suffisent, aucun groupe n'est à déclarer à part.
    */
  } finally { await adm.dispose(); }
  return comptes;
}

/** Le nom d'utilisateur réel d'un profil : il porte le suffixe de l'organisme, pour ne pas se heurter. */
export function identifiantDe(p: Profil, tenantId: string): string {
  return `${p.username}-${tenantId.slice(-6)}`;
}

/** Ouvre une session **au nom d'un profil**, et non du compte d'administration. */
export async function seConnecterProfil(page: Page, tenantId: string, identifiant: string): Promise<void> {
  /*
    **Fermer la session d'abord.** Un essai qui enchaîne deux profils sur la même page retombe
    sinon sur le tableau de bord du premier : le formulaire de connexion n'existe pas, et l'attente
    du sélecteur d'établissement expire sur une page parfaitement fonctionnelle — l'échec le moins
    lisible qui soit.
  */
  await page.context().clearCookies();
  await page.goto("/");
  await expect(page.getByTestId("login-tenant").locator(`option[value="${tenantId}"]`))
    .toHaveCount(1, { timeout: 60_000 });
  await page.getByTestId("login-tenant").selectOption(tenantId);
  await page.getByTestId("login-user").fill(identifiant);
  await page.getByTestId("login-pass").fill(MDP_PROFIL);
  await page.getByTestId("login-submit").click();
  await expect(page.getByTestId("dashboard-page")).toBeVisible({ timeout: 60_000 });
  await menuPret(page);
}

export async function seConnecterOrganisme(page: Page, tenantId: string): Promise<void> {
  await page.goto("/");
  // Attendre l'**option**, et non le menu : le `<select>` existe avant que la liste n'arrive.
  await expect(page.getByTestId("login-tenant").locator(`option[value="${tenantId}"]`))
    .toHaveCount(1, { timeout: 60_000 });
  await page.getByTestId("login-tenant").selectOption(tenantId);
  await page.getByTestId("login-user").fill(TENANT_ADMIN_USER);
  await page.getByTestId("login-pass").fill(TENANT_ADMIN_PASS);
  await page.getByTestId("login-submit").click();
  await expect(page.getByTestId("dashboard-page")).toBeVisible({ timeout: 60_000 });
  await menuPret(page);
}
