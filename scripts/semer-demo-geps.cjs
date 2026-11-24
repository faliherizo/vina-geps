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
 * - **Un sous-domaine par organisme** (`sousDomaine`, ADR 0033) : `urgence-sante.geps…`, `clincom.geps…`. Il va à
 *   l'exemplaire le plus récent ; un exemplaire plus ancien le cède (`<slug>-<début de son id>`), car un slug est unique.
 * - Sortie 0 si chaque semis réussit ; sinon 1, avec le nom de l'organisme en échec.
 */
const { execFileSync } = require("node:child_process");
const path = require("node:path");
const comptes = require("../e2e/donnees/geps/comptes-demo.json");
const { clientApi, argument, semisInacheve } = require("./lib/api-semis.cjs");

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

/** Un exemplaire de l'organisme dont le semis est allé au bout ; un semis interrompu garde sa marque et ne compte pas. */
const exemplaire = (organisme) => (t) => organisme.correspondance === "exact"
  ? t.name === organisme.nom : t.name.startsWith(organisme.nom) && !semisInacheve(t.name);

const present = (organisme, existants) => existants.some(exemplaire(organisme));

const instances = (organisme, existants) => existants.filter(exemplaire(organisme)).sort((a, b) => b.name.localeCompare(a.name));

function semer(organisme) {
  const script = path.join(__dirname, "..", organisme.semis);
  // Les scripts historiques lisent SUPERADMIN_USER/PASS, l'organisme par défaut SUPERADMIN_USERNAME/PASSWORD : les deux.
  const env = { ...process.env, DEMO_PASSWORD: MDP, SUPERADMIN_USER: SUPERADMIN.username, SUPERADMIN_PASS: SUPERADMIN.password,
    SUPERADMIN_USERNAME: SUPERADMIN.username, SUPERADMIN_PASSWORD: SUPERADMIN.password, SEMIS_SOUS_DOMAINE: organisme.sousDomaine };
  execFileSync(process.execPath, [script, ...ARGUMENTS[organisme.cle](organisme)], { env, stdio: "inherit" });
}

/** Les semis qui savent mettre à niveau un exemplaire existant, sans en créer un nouveau. */
const COMPLETER = { "fx-ao": true };

function completer(organisme, tenantId) {
  const script = path.join(__dirname, "..", organisme.semis);
  const env = { ...process.env, DEMO_PASSWORD: MDP, SUPERADMIN_USER: SUPERADMIN.username, SUPERADMIN_PASS: SUPERADMIN.password };
  execFileSync(process.execPath, [script, "--api", API, "--completer", tenantId], { env, stdio: "inherit" });
}

/** Le sous-domaine libéré par tout organisme autre que `garde` qui le tient : un slug est unique. */
async function liberer(ctx, slug, garde) {
  for (const t of ctx.existants.filter((x) => x.slug === slug && x.id !== garde)) {
    await ctx.appeler(`/api/superadmin/tenants/${t.id}`, { methode: "PATCH", jeton: ctx.jeton, corps: { slug: `${slug}-${t.id.slice(0, 8)}` } });
    console.log(`  ↪ « ${t.name} » cède ${slug} (devient ${slug}-${t.id.slice(0, 8)})`);
  }
}

/** L'exemplaire le plus récent reçoit le sous-domaine de l'organisme (une démonstration semée avant ADR 0033 n'en a pas). */
async function attribuer(ctx, organisme) {
  const recent = instances(organisme, ctx.existants)[0];
  if (!recent || recent.slug === organisme.sousDomaine) return;
  await liberer(ctx, organisme.sousDomaine, recent.id);
  await ctx.appeler(`/api/superadmin/tenants/${recent.id}`, { methode: "PATCH", jeton: ctx.jeton, corps: { slug: organisme.sousDomaine } });
  console.log(`  ↪ « ${recent.name} » → ${organisme.sousDomaine}`);
}

async function principal() {
  if (!SUPERADMIN.password) throw new Error("SUPERADMIN_PASSWORD absent de l'environnement");
  const { appeler, connecter } = clientApi(API);
  const jeton = await connecter(SUPERADMIN);
  const ctx = { appeler, jeton, existants: await appeler("/api/superadmin/tenants", { jeton }) };
  let echecs = 0;
  for (const organisme of comptes.organismes) {
    if (SEULEMENT && organisme.cle !== SEULEMENT) continue;
    try {
      if (present(organisme, ctx.existants) && organisme.correspondance !== "exact" && !FORCER) {
        await attribuer(ctx, organisme);
        // Un exemplaire déjà semé se met à niveau quand son semis sait le faire (FX-AO : menu de l'avis, addenda 8).
        if (COMPLETER[organisme.cle]) completer(organisme, instances(organisme, ctx.existants)[0].id);
        console.log(`= ${organisme.nom} — déjà semé (--forcer pour un nouvel exemplaire)`);
        continue;
      }
      console.log(`▶ ${organisme.nom}`);
      // Un nouvel exemplaire naît avec le sous-domaine : l'ancien le cède d'abord. L'exemplaire exact le reçoit à la reprise.
      if (organisme.correspondance !== "exact") await liberer(ctx, organisme.sousDomaine, null);
      semer(organisme);
    } catch (e) { echecs++; console.error(`❌ ${organisme.nom} — semis en échec${e?.message ? ` : ${e.message}` : ""}`); }
  }
  process.exit(echecs ? 1 : 0);
}

principal().catch((e) => { console.error(`❌ ${e.message}`); process.exit(1); });
