#!/usr/bin/env node
/**
 * **Semer la démonstration publique** (geps.vinaitsolution.com) : les trois organismes de `e2e/donnees/geps/comptes-demo.json`,
 * chacun par son propre script de semis, tous avec le mot de passe publié de la démonstration.
 *
 *   SUPERADMIN_PASSWORD=… node scripts/semer-demo-geps.cjs --api https://geps.vinaitsolution.com [--forcer] [--seulement fx-ao]
 *
 * - **Rejouable** : un organisme déjà présent (nom exact, ou préfixe pour les semis horodatés) n'est pas semé une seconde
 *   fois — sauf `--forcer`, qui en sème un nouvel exemplaire horodaté sans toucher l'ancien. L'organisme par défaut, lui,
 *   est toujours repris : son script complète ce qui manque et remet chaque mot de passe à celui de la démonstration.
 * - Le mot de passe du superadmin se lit dans l'environnement seulement (`SUPERADMIN_PASSWORD`) ; celui des comptes de
 *   démonstration dans `DEMO_PASSWORD` (défaut : celui du fichier, publié dans docs/COMPTES-DEMO-GEPS.md).
 * - Sortie 0 si chaque semis réussit ; sinon 1, avec le nom de l'organisme en échec.
 */
const { execFileSync } = require("node:child_process");
const path = require("node:path");
const comptes = require("../e2e/donnees/geps/comptes-demo.json");
const { clientApi, argument } = require("./lib/api-semis.cjs");

const API = argument("api", process.env.API_URL || "http://localhost:8095").replace(/\/+$/, "");
const FORCER = process.argv.includes("--forcer");
const SEULEMENT = argument("seulement", "");
const SUPERADMIN = { username: process.env.SUPERADMIN_USERNAME || "superadmin", password: process.env.SUPERADMIN_PASSWORD || "" };
const MDP = process.env.DEMO_PASSWORD || comptes.motDePasseParDefaut;

/** Les arguments de chaque script de semis : l'organisme par défaut se nomme, les autres s'horodatent. */
const ARGUMENTS = {
  "urgences-sante": (o) => ["--api", API, "--nom", o.nom],
  "urgences-sante-demonstration": () => ["--api", API],
  "fx-ao": () => ["--api", API],
};

const present = (organisme, existants) => existants.some((t) =>
  organisme.correspondance === "exact" ? t.name === organisme.nom : t.name.startsWith(organisme.nom));

function semer(organisme) {
  const script = path.join(__dirname, "..", organisme.semis);
  // Les scripts historiques lisent SUPERADMIN_USER/PASS, l'organisme par défaut SUPERADMIN_USERNAME/PASSWORD : les deux.
  const env = { ...process.env, DEMO_PASSWORD: MDP, SUPERADMIN_USER: SUPERADMIN.username, SUPERADMIN_PASS: SUPERADMIN.password,
    SUPERADMIN_USERNAME: SUPERADMIN.username, SUPERADMIN_PASSWORD: SUPERADMIN.password };
  execFileSync(process.execPath, [script, ...ARGUMENTS[organisme.cle](organisme)], { env, stdio: "inherit" });
}

async function principal() {
  if (!SUPERADMIN.password) throw new Error("SUPERADMIN_PASSWORD absent de l'environnement");
  const { appeler, connecter } = clientApi(API);
  const jeton = await connecter(SUPERADMIN);
  const existants = await appeler("/api/superadmin/tenants", { jeton });
  let echecs = 0;
  for (const organisme of comptes.organismes) {
    if (SEULEMENT && organisme.cle !== SEULEMENT) continue;
    if (present(organisme, existants) && organisme.correspondance !== "exact" && !FORCER) {
      console.log(`= ${organisme.nom} — déjà semé (--forcer pour un nouvel exemplaire)`);
      continue;
    }
    console.log(`▶ ${organisme.nom}`);
    try { semer(organisme); } catch { echecs++; console.error(`❌ ${organisme.nom} — semis en échec`); }
  }
  process.exit(echecs ? 1 : 0);
}

principal().catch((e) => { console.error(`❌ ${e.message}`); process.exit(1); });
