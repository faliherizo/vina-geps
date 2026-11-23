import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { creerServeur, demarrer } from "../src/serveur.mjs";
import { ServicePoussee } from "../src/service.mjs";

const JETON = "ExponentPushToken[http-1]";
let serveur;
let base;

before(async () => {
  let n = 0;
  serveur = creerServeur({ service: new ServicePoussee({ identifiant: () => `b${++n}` }), jetonControle: "controle" });
  await new Promise((ok) => serveur.listen(0, "127.0.0.1", ok));
  base = `http://127.0.0.1:${serveur.address().port}`;
});
after(() => serveur.close());

const CONTROLE = { Authorization: "Bearer controle", "Content-Type": "application/json" };

async function appel(chemin, { methode = "GET", corps, entetes = {} } = {}) {
  const r = await fetch(base + chemin, { method: methode, headers: { "Content-Type": "application/json", ...entetes }, body: corps === undefined ? undefined : typeof corps === "string" ? corps : JSON.stringify(corps) });
  const texte = await r.text();
  let json = null;
  try { json = JSON.parse(texte); } catch { /* HTML ou vide */ }
  return { statut: r.status, json, texte, type: r.headers.get("content-type") };
}

test("santé et console HTML", async () => {
  assert.deepEqual((await appel("/sante")).json, { statut: "ok" });
  const c = await appel("/");
  assert.equal(c.statut, 200);
  assert.match(c.type, /text\/html/);
  assert.match(c.texte, /<html lang="fr-CA">/);
});

test("parcours complet : installer l'application, pousser, lire ce que le téléphone a montré, accusé", async () => {
  assert.equal((await appel(`/bac-a-sable/appareils/${encodeURIComponent(JETON)}`, { methode: "PUT", corps: { plateforme: "ios" }, entetes: CONTROLE })).statut, 200);
  const envoi = await appel("/--/api/v2/push/send", { methode: "POST", corps: { to: JETON, title: "Code bleu", sound: "default" } });
  assert.equal(envoi.json.data.status, "ok");
  const recu = await appel(`/bac-a-sable/notifications?jeton=${encodeURIComponent(JETON)}&titre=Code`, { entetes: CONTROLE });
  assert.equal(recu.json[0].presentation.sonore, true);
  const accuses = await appel("/--/api/v2/push/getReceipts", { methode: "POST", corps: { ids: [envoi.json.data.id] } });
  assert.deepEqual(accuses.json.data[envoi.json.data.id], { status: "ok" });
  assert.equal((await appel("/bac-a-sable/appareils", { entetes: CONTROLE })).json.length, 1);
  assert.equal((await appel("/bac-a-sable/notifications", { methode: "DELETE", entetes: CONTROLE })).statut, 204);
  assert.equal((await appel(`/bac-a-sable/appareils/${encodeURIComponent(JETON)}`, { methode: "DELETE", entetes: CONTROLE })).statut, 204);
});

test("contrôle protégé par son jeton ; routes et méthodes inconnues ; corps illisible ou trop gros", async () => {
  assert.equal((await appel("/bac-a-sable/notifications")).statut, 401);
  assert.equal((await appel("/ailleurs")).statut, 404);
  assert.equal((await appel("/bac-a-sable/notifications", { methode: "POST", entetes: CONTROLE })).statut, 405);
  const illisible = await appel("/--/api/v2/push/send", { methode: "POST", corps: "{pas du json" });
  assert.deepEqual([illisible.statut, illisible.json.errors[0].code], [400, "VALIDATION_ERROR"]);
  const vide = await appel("/--/api/v2/push/getReceipts", { methode: "POST", corps: "" });
  assert.equal(vide.statut, 400);
  const gros = await appel("/--/api/v2/push/send", { methode: "POST", corps: "x".repeat(1024 * 1024 + 10) }).catch(() => ({ statut: 413 }));
  assert.equal(gros.statut, 413);
  assert.equal((await appel("/sante")).statut, 200, "le serveur survit au corps refusé");
});

test("démarrage par l'environnement : jeton d'accès Expo et jeton de contrôle appliqués", async () => {
  let s;
  const port = await new Promise((ok) => { s = demarrer({ PORT: "0", HOTE: "127.0.0.1", EXPO_JETON_ACCES: "acces", BAC_JETON_CONTROLE: "c" }, ok); });
  try {
    const r = await fetch(`http://127.0.0.1:${port}/--/api/v2/push/send`, { method: "POST", body: JSON.stringify({ to: JETON }) });
    assert.equal(r.status, 401);
    assert.equal((await fetch(`http://127.0.0.1:${port}/bac-a-sable/appareils`)).status, 401);
  } finally { s.close(); }
  let d;
  const defaut = await new Promise((ok) => { d = demarrer({ PORT: "0", HOTE: "127.0.0.1" }, ok); });
  try {
    assert.equal((await fetch(`http://127.0.0.1:${defaut}/bac-a-sable/appareils`)).status, 200);
  } finally { d.close(); }
});
