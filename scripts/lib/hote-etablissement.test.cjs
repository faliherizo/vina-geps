/**
 * L'hôte où se connecte un compte d'organisme (ADR 0033) : la racine en mode sélecteur, `https://<slug>.<racine>` en
 * déploiement par sous-domaine, et une erreur qui le dit quand l'organisme n'a pas de sous-domaine.
 */
const { test, beforeEach, afterEach } = require("node:test");
const assert = require("node:assert/strict");
const { resolveurHote } = require("./hote-etablissement.cjs");

const API = "https://geps.vinaitsolution.com";
let appels;
let reponses;
const fetchOrigine = global.fetch;

beforeEach(() => {
  appels = [];
  reponses = {};
  global.fetch = async (url, options = {}) => {
    appels.push({ url, autorisation: options.headers?.Authorization });
    const corps = reponses[url];
    return { ok: corps !== undefined, json: async () => corps };
  };
});
afterEach(() => { global.fetch = fetchOrigine; });

test("un compte global se connecte sur l'adresse donnée, sans rien demander au serveur", async () => {
  assert.equal(await resolveurHote(API)(undefined, "jeton"), API);
  assert.deepEqual(appels, []);
});

test("mode sélecteur : un compte d'organisme se connecte aussi sur l'adresse donnée", async () => {
  reponses[`${API}/api/auth/deploiement`] = { resolution: "selecteur", domaineRacine: null };
  assert.equal(await resolveurHote(API)("t-1", "jeton"), API);
});

test("sous-domaine : le slug lu dans la liste du superadmin fait l'hôte, le schéma et le port sont gardés", async () => {
  const api = "http://geps.local:8095";
  reponses[`${api}/api/auth/deploiement`] = { resolution: "sous_domaine", domaineRacine: "geps.local" };
  reponses[`${api}/api/superadmin/tenants`] = [{ id: "t-1", slug: "clincom" }, { id: "t-2", slug: null }];
  const base = resolveurHote(api);
  assert.equal(await base("t-1", "jeton-sa"), "http://clincom.geps.local:8095");
  assert.equal(appels.find((a) => a.url.endsWith("/tenants")).autorisation, "Bearer jeton-sa");
  // Le mode et les slugs se lisent une fois.
  await base("t-1", "jeton-sa");
  assert.equal(appels.filter((a) => a.url.endsWith("/deploiement")).length, 1);
});

test("sous-domaine : un organisme sans slug est refusé, avec une erreur qui le nomme", async () => {
  reponses[`${API}/api/auth/deploiement`] = { resolution: "sous_domaine", domaineRacine: "geps.vinaitsolution.com" };
  reponses[`${API}/api/superadmin/tenants`] = [{ id: "t-2", slug: null }];
  await assert.rejects(resolveurHote(API)("t-2", "jeton-sa"), /t-2 n'a pas de sous-domaine/);
});

test("sous-domaine sans jeton global : la liste n'est pas demandée, et l'organisme inconnu est refusé", async () => {
  reponses[`${API}/api/auth/deploiement`] = { resolution: "sous_domaine", domaineRacine: "geps.vinaitsolution.com" };
  await assert.rejects(resolveurHote(API)("t-1", undefined), /pas de sous-domaine/);
  assert.equal(appels.some((a) => a.url.endsWith("/tenants")), false);
});

test("un serveur qui ne dit rien de son déploiement (ancienne version) : l'adresse donnée", async () => {
  assert.equal(await resolveurHote(API)("t-1", "jeton"), API);
});
