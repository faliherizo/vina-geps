import { request, FullConfig } from "@playwright/test";
import fs from "fs";
import path from "path";

/**
 * Global setup E2E :
 *  - Attend que le backend réponde sur /api/health.
 *  - Login admin via /api/auth/login : le contexte de requête mémorise les cookies
 *    `session` (httpOnly) et `csrf` posés par le backend.
 *  - Sauvegarde le storageState (cookies inclus) dans .auth/admin.json pour que les tests
 *    démarrent authentifiés via cookie uniquement. Aucun token n'est mis en localStorage.
 */
export default async function globalSetup(config: FullConfig) {
  // La suite « interface » double l'API et n'a besoin d'aucun serveur applicatif : attendre le
  // backend — et a fortiori refuser de démarrer s'il n'est pas le bon — la rendrait dépendante
  // de ce qu'elle est justement conçue pour ne pas exiger.
  //
  // Le signal est **explicite** (`E2E_NO_BACKEND=1`, posé par `npm run test:ui`) plutôt que
  // déduit de `config.projects` : celui-ci n'est pas filtré par `--project`, et une inférence
  // fausse ici désactiverait silencieusement le garde-fou de la suite e2e.
  if (process.env.E2E_NO_BACKEND === "1") return;

  /**
   * **Un environnement déjà déployé** (`E2E_BASE_URL`) : on ne sonde pas, on n'écrit pas.
   *
   * Le garde-fou ci-dessous refuse un backend qui n'est pas celui d'essai, pour empêcher la
   * campagne d'écrire dans une vraie base par accident. Viser un déploiement n'est pas un
   * accident : c'est une intention déclarée, par cette variable. On lève donc le garde — et,
   * avec lui, la remise à zéro et la session `admin/admin` mémorisée, qui écrivent toutes deux
   * et supposent un compte d'essai.
   *
   * La contrepartie, qui n'est pas négociable : **un essai qui vise un déploiement ne doit que
   * lire.** Il se connecte, il consulte, il n'écrit pas. `deploiement-fumee.spec.ts` s'y tient,
   * et tout essai ajouté ici doit s'y tenir aussi.
   */
  if (process.env.E2E_BASE_URL) return;
  void config;

  const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;
  for (let i = 0; i < 30; i++) {
    try {
      const ctx = await request.newContext();
      const r = await ctx.get(`${API}/api/health`).catch(() => null);
      await ctx.dispose();
      if (r && r.ok()) break;
    } catch { /* ignore */ }
    await new Promise((r) => setTimeout(r, 500));
  }

  // Garde-fou : le backend qui répond doit être le backend **éphémère** de test.
  //
  // `reuseExistingServer` réutilise tout serveur déjà présent sur le port. Si un backend
  // Docker (base **fichier**, hors mode test) occupe ce port, la suite s'exécute contre lui :
  // l'injection de tenant de test ne s'applique plus, des dizaines de requêtes retombent en
  // 400, et l'échec se disperse en symptômes sans rapport (« aucun employé », « aucun
  // projet »…). Pire, la suite écrit alors dans une vraie base.
  //
  // `_reset` n'existe qu'en environnement de test (403 ailleurs) : c'est le marqueur le plus
  // fiable pour distinguer les deux.
  // La sonde doit être **authentifiée** : sans session, la route répond 401 avant d'atteindre
  // le garde d'environnement, et le contrôle passerait à côté de son objet.
  // Captation chapitre par chapitre (`scripts/monter-demo.sh`) : chaque chapitre est une exécution
  // Playwright distincte contre **le même** backend éphémère, démarré par le script lui-même —
  // la remise à zéro effacerait l'établissement du chapitre précédent. Le script garantit
  // l'origine du serveur ; ici, on ne fait que ne pas le vider.
  if (process.env.E2E_CONSERVER_ETAT === "1") return;

  const probe = await request.newContext();
  const probeLogin = await probe.post(`${API}/api/auth/login`, {
    data: { username: "admin", password: "admin" },
  }).catch(() => null);
  const probeCsrf = (await probe.storageState()).cookies.find((c) => c.name === "csrf")?.value ?? "";
  const marker = probeLogin?.ok()
    ? await probe.post(`${API}/api/auth/_reset`, { headers: { "X-CSRF-Token": probeCsrf } }).catch(() => null)
    : null;
  await probe.dispose();
  // Le backend éphémère accepte `_reset` (2xx) : le tenant de test y est injecté et le garde
  // d'environnement le laisse passer. Tout autre code le disqualifie — 403 (garde de
  // production), 400 (tenant strict, donc hors mode test), 404 (binaire obsolète).
  if (!marker || !marker.ok()) {
    throw new Error(
      `Le backend sur ${API} n'est PAS le backend de test ` +
      `(POST /api/auth/_reset → ${marker ? marker.status() : "injoignable"}).\n` +
      "Un backend Docker occupe probablement ce port ; la suite s'exécuterait contre une vraie\n" +
      "base et échouerait en cascade sur des symptômes sans rapport. Arrêtez-le :\n" +
      "    docker compose stop backend\n" +
      "    docker update --restart=no freelance-backend   # il peut redémarrer seul\n" +
      "puis relancez la suite.",
    );
  }

  const ctx = await request.newContext();
  const r = await ctx.post(`${API}/api/auth/login`, { data: { username: "admin", password: "admin" } });
  if (!r.ok()) throw new Error(`Admin login failed: ${r.status()}`);

  const storageDir = path.join(__dirname, ".auth");
  fs.mkdirSync(storageDir, { recursive: true });
  // Sauvegarde l'état complet : les cookies `session` httpOnly + `csrf` seront restaurés.
  await ctx.storageState({ path: path.join(storageDir, "admin.json") });
  await ctx.dispose();
}
