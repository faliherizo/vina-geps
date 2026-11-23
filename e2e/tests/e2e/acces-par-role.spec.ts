/**
 * E2E — **les accès, rôle par rôle**, sur le vrai serveur.
 *
 * Pour chacun des sept rôles livrés (superadmin, admin d'établissement, RH, gestionnaire,
 * direction, employé, comptable), trois preuves :
 *
 * 1. **Le menu** : exactement les entrées que le profil autorise — ni une de plus, ni une de moins.
 *    La liste est celle que l'écran affichait à la date du relevé, revue à la main contre la
 *    table `PAGES` de `App.tsx` ; une entrée qui apparaît ou disparaît fait échouer l'essai, et
 *    c'est voulu : un menu qui change est une décision d'accès qui change.
 * 2. **L'URL directe** : une page que le menu n'offre pas ne s'ouvre pas non plus en la tapant —
 *    l'écran revient au tableau de bord.
 * 3. **L'API** : les routes sensibles répondent 403 à qui n'y a pas droit, et 200 à qui y a droit.
 *    C'est la seule preuve qui compte : un menu masqué n'est pas une garde.
 *
 * Ce que cet essai a trouvé en l'écrivant : l'admin de l'école A pouvait modifier les modules de
 * l'école B (`requireRole("admin")` ne distinguait pas l'administrateur du système), et une page
 * interdite s'ouvrait par son URL.
 */
import { test, expect, request as pwRequest, type Page } from "@playwright/test";
import { superadminContext, GLOBAL_ACCOUNT } from "./helpers/auth";
import { createTenant, setTenantModules, uniq, MODULES_SOCLE, TENANT_ADMIN_USER, TENANT_ADMIN_PASS, MODULES_TRANSVERSAUX } from "./helpers/organisme";
import { menuPret } from "./helpers/nav";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;
const MDP = "Passe!Test2026";

test.use({ storageState: { cookies: [], origins: [] } });

type Role = "superadmin" | "admin" | "rh" | "manager" | "director" | "employee" | "accountant";

/** Les écrans d'Urgences-santé ouverts à tout compte planifiable, puis ceux de l'encadrement (`urgencessante/menu.ts`). */
const US_TOUS = ["monhoraire", "mesdisponibilites", "mesabsences", "monagph", "monpointage", "matemps", "mesechanges", "usmessages", "usappels", "usdiffusion"];
/** « Mon équipe » : l'encadrement et les RH (`menu.ts` : ENCADREMENT + rh). */
const US_EQUIPE = ["monequipe"];
const US_ENCADREMENT = ["usdisponibilites", "usabsences", "usagph", "uscomblement", "usequipages", "uspostes", "usperiodes", "usressources", "usquotas",
  "uspresence", "ustemps", "usechanges", "usregles", "usmilieux", "usperimetres", "usintegrations", "usretention", "usjournal"];

/** Ce que seule l'administration configure (`effectifs.configure`) : les référentiels et les codes d'absence. */
const US_CONFIGURATION = ["usreferentiels", "uscodesabsence"];

/** Le menu attendu, par rôle — les groupes ne sont pas listés, seules les pages le sont. Relevé contre `PAGES` d'`App.tsx`. */
const MENU: Record<Role, string[]> = {
  superadmin: ["superadmin"],
  admin: ["dashboard", "rapports", "account", "help", "soutien", "approvals", "access", "notifications", "admin", "apidocs", "integrations", ...US_TOUS, ...US_EQUIPE, ...US_ENCADREMENT,
    ...US_CONFIGURATION],
  rh: ["dashboard", "help", "soutien", "notifications", ...US_TOUS, ...US_EQUIPE],
  manager: ["dashboard", "rapports", "account", "help", "soutien", "approvals", "notifications", ...US_TOUS, ...US_EQUIPE, ...US_ENCADREMENT],
  director: ["dashboard", "help", "soutien", "approvals", "apidocs", ...US_TOUS, ...US_EQUIPE, ...US_ENCADREMENT],
  employee: ["dashboard", "account", "help", "soutien", ...US_TOUS],
  accountant: ["dashboard", "help", "soutien"],
};

/** Les **vues** du menu à deux niveaux (un onglet proposé sous sa notion : `nav-<page>-<onglet>`), par rôle. */
const VUES: Record<Role, string[]> = {
  superadmin: [], admin: ["access-octrois", "access-qui"], rh: [], manager: [], director: [], employee: [], accountant: [],
};

/** Les pages **interdites** qu'on tente d'ouvrir par l'URL, et l'identifiant qui prouverait qu'elles se sont ouvertes. */
const INTERDITES: Record<Role, Array<[string, string]>> = {
  superadmin: [["admin", "admin-page"]],
  admin: [["superadmin", "superadmin-page"]],
  rh: [["superadmin", "superadmin-page"], ["admin", "admin-page"], ["uscomblement", "uscomblement-titre"]],
  manager: [["superadmin", "superadmin-page"], ["admin", "admin-page"], ["usreferentiels", "usreferentiels-titre"]],
  director: [["superadmin", "superadmin-page"], ["admin", "admin-page"], ["notifications", "notifications-page"], ["usreferentiels", "usreferentiels-titre"]],
  employee: [["superadmin", "superadmin-page"], ["admin", "admin-page"], ["uscomblement", "uscomblement-titre"]],
  accountant: [["superadmin", "superadmin-page"], ["admin", "admin-page"], ["monhoraire", "mho-titre"]],
};

/**
 * Ce que l'API doit répondre à chaque rôle sur des routes sensibles (`$T` = l'établissement d'essai).
 * Le superadmin administre tout le système : il est autorisé partout, et n'est donc pas listé —
 * il est ajouté d'office. Les lectures seulement : l'essai ne doit pas changer l'état qu'il mesure.
 */
const API_ATTENDU: Array<{ chemin: string; autorises: Role[]; libelle: string }> = [
  { chemin: "/api/superadmin/tenants", autorises: [], libelle: "lister les établissements (superadmin)" },
  { chemin: "/api/saas/tenants/$T", autorises: [], libelle: "lire un établissement (administration du système)" },
  { chemin: "/api/exploitation/sauvegardes", autorises: [], libelle: "voir les sauvegardes" },
  { chemin: "/api/personnel/employes", autorises: ["admin", "rh", "manager", "director", "employee", "accountant"], libelle: "répertoire du personnel" },
  { chemin: "/api/saas/my-modules", autorises: ["admin", "rh", "manager", "director", "employee", "accountant"], libelle: "mes modules" },
  // Balayage des accès du 22 novembre 2026 : le registre des référentiels et la carte des droits sont à l'administration.
  { chemin: "/api/effectifs/referentiels", autorises: ["admin"], libelle: "registre des référentiels (administration)" },
  { chemin: "/api/habilitations/octrois", autorises: ["admin"], libelle: "octrois de l'établissement (administration)" },
  { chemin: "/api/effectifs/referentiels/unites", autorises: ["admin", "rh", "manager", "director", "employee", "accountant"], libelle: "valeurs en vigueur d'un référentiel (formulaires)" },
];

async function seConnecter(page: Page, tenant: string, user: string, pass: string) {
  await page.goto("/");
  await page.context().clearCookies();
  await page.evaluate(() => localStorage.clear()).catch(() => {});
  await page.goto("/");
  await page.getByTestId("login-tenant").selectOption(tenant);
  await page.getByTestId("login-user").fill(user);
  await page.getByTestId("login-pass").fill(pass);
  await page.getByTestId("login-submit").click();
  await expect(page.getByTestId("logout")).toBeVisible({ timeout: 20_000 });
  // Le menu complet, pas celui du chargement : il porte `aria-busy` tant que les modules ne sont pas connus.
  await menuPret(page);
}

test.describe.serial("Accès par rôle — menu, URL directe, API", () => {
  let tenantId = "";
  const jetons: Partial<Record<Role, string>> = {};

  test.beforeAll(async () => {
    const ctx = await superadminContext();
    try {
      tenantId = await createTenant(ctx, uniq("ROLES"));
      await setTenantModules(ctx, tenantId, [...MODULES_SOCLE, ...MODULES_TRANSVERSAUX, "effectifs", "effectifs_regles", "effectifs_planification",
        "effectifs_disponibilites", "effectifs_absences", "effectifs_echanges", "effectifs_agph", "effectifs_presence", "effectifs_temps",
        "effectifs_integrations", "effectifs_communication"]);
    } finally { await ctx.dispose(); }
    const anonyme = await pwRequest.newContext();
    const login = async (username: string, password: string, tenant?: string) =>
      (await (await anonyme.post(`${API}/api/auth/login`, { data: { username, password, tenantId: tenant } })).json()).token as string;
    jetons.superadmin = await login("superadmin", "superadmin");
    jetons.admin = await login(TENANT_ADMIN_USER, TENANT_ADMIN_PASS, tenantId);
    const api = await pwRequest.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${jetons.admin}`, "X-Tenant-Id": tenantId } });
    try {
      for (const role of ["rh", "manager", "director", "employee", "accountant"] as Role[]) {
        const u = await (await api.post(`${API}/api/auth/users`, { data: { username: `r-${role}`, password: MDP, role } })).json();
        await api.patch(`${API}/api/auth/users/${u.id}`, { data: { securitySetupRequired: false, mustChangePassword: false } });
        jetons[role] = await login(`r-${role}`, MDP, tenantId);
      }
    } finally { await api.dispose(); await anonyme.dispose(); }
  });

  for (const role of Object.keys(MENU) as Role[]) {
    test(`${role} — voit exactement son menu, et une page interdite ne s'ouvre pas par l'URL`, async ({ page }) => {
      test.setTimeout(150_000);
      const [user, pass, tenant] = role === "superadmin" ? ["superadmin", "superadmin", GLOBAL_ACCOUNT]
        : role === "admin" ? [TENANT_ADMIN_USER, TENANT_ADMIN_PASS, tenantId] : [`r-${role}`, MDP, tenantId];
      await seConnecter(page, tenant, user, pass);
      for (const s of await page.locator(".nav-group summary").all()) await s.click({ timeout: 2_000 }).catch(() => {});   // un sommaire masqué (section vide pour ce compte) ne se clique pas : on passe
      const navs = (await page.locator('[data-testid^="nav-"]').evaluateAll((els) => els.map((e) => e.getAttribute("data-testid")!)))
        .filter((n) => !n.startsWith("nav-group-") && !n.startsWith("nav-concept-")).map((n) => n.replace(/^nav-/, ""));
      expect(navs.sort()).toEqual([...MENU[role], ...VUES[role]].sort());

      for (const [pageInterdite, temoin] of INTERDITES[role]) {
        await page.goto(`/admin/${pageInterdite}`);
        await page.waitForTimeout(800);
        await expect(page.getByTestId(temoin), `${role} a ouvert « ${pageInterdite} » par l'URL`).toHaveCount(0);
        expect(new URL(page.url()).pathname, `${role} reste sur /admin/${pageInterdite}`).not.toBe(`/admin/${pageInterdite}`);
      }
    });
  }

  test("**l'API répond 403 à qui n'a pas le droit, et 200 à qui l'a — pour chaque rôle et chaque route sensible**", async () => {
    const ecarts: string[] = [];
    for (const regle of API_ATTENDU) {
      for (const role of Object.keys(MENU) as Role[]) {
        const ctx = await pwRequest.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${jetons[role]}`, "X-Tenant-Id": tenantId } });
        try {
          const r = await ctx.get(`${API}${regle.chemin.replace("$T", tenantId)}`);
          const attendu = role === "superadmin" || regle.autorises.includes(role) ? "autorisé" : "refusé";
          const obtenu = r.status() === 403 ? "refusé" : r.status() < 400 ? "autorisé" : `erreur ${r.status()}`;
          if (attendu !== obtenu) ecarts.push(`${regle.libelle} — ${role} : attendu ${attendu}, obtenu ${obtenu}`);
        } finally { await ctx.dispose(); }
      }
    }
    expect(ecarts, ecarts.join("\n")).toEqual([]);
  });

  test("**l'admin d'un établissement ne touche pas à un autre établissement**", async () => {
    const sa = await superadminContext();
    const autre = await createTenant(sa, uniq("AUTRE"));
    await sa.dispose();
    const ctx = await pwRequest.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${jetons.admin}`, "X-Tenant-Id": tenantId } });
    try {
      expect((await ctx.get(`${API}/api/saas/tenants/${autre}`)).status()).toBe(403);
      expect((await ctx.put(`${API}/api/saas/tenants/${autre}/modules`, { data: { modules: ["effectifs"] } })).status()).toBe(403);
      expect((await ctx.post(`${API}/api/saas/tenants/${autre}/suspend`)).status()).toBe(403);
      expect((await ctx.get(`${API}/api/saas/tenants`)).status()).toBe(403);
      // Et les données d'un autre établissement restent invisibles, même en nommant son identifiant.
      const croise = await pwRequest.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${jetons.admin}`, "X-Tenant-Id": autre } });
      try { expect((await croise.get(`${API}/api/personnel/employes`)).status()).toBe(403); } finally { await croise.dispose(); }
    } finally { await ctx.dispose(); }
  });
});
