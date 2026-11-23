// Isolation entre organismes (BOLA, OWASP API1:2023) : FX-AO et l'organisme témoin ne se lisent jamais.
import { test } from "node:test";
import assert from "node:assert/strict";
import { preparer, fxao, temoin } from "../commun.mjs";

test("IN-FX-06 — une alerte du témoin est introuvable depuis FX-AO, même par son identifiant", async () => {
  const etat = await preparer();
  const t = await temoin(etat);
  const envoi = await t("POST", "/api/alertes", { corps: { titre: "Alerte du témoin", corps: "secret", priorite: "information", personnes: [etat.destinataireTemoin] } });
  assert.equal(envoi.statut, 201, envoi.texte.slice(0, 200));
  const id = envoi.json.id;
  const coord = await fxao(etat, "coord.urgence");
  const lue = await coord("GET", `/api/alertes/envoyees/${id}`);
  assert.ok([403, 404].includes(lue.statut), `lecture croisée : ${lue.statut}`);
  assert.ok(!lue.texte.includes("secret"));
  const viaEntete = await coord("GET", `/api/alertes/envoyees/${id}`, { tenant: etat.temoin });
  assert.equal(viaEntete.statut, 403, "un X-Tenant-Id étranger est une discordance");
  const reponse = await (await fxao(etat, "inf.roy"))("POST", `/api/alertes/recues/${id}/reponse`, { corps: { reponseId: "x" } });
  assert.ok([400, 403, 404].includes(reponse.statut), `réponse croisée : ${reponse.statut}`);
});

test("IN-FX-07 — l'administrateur FX-AO ne voit aucun compte du témoin", async () => {
  const etat = await preparer();
  const admin = await fxao(etat, "admin");
  const r = await admin("GET", "/api/auth/users", { tenant: etat.temoin });
  if (r.statut === 200) assert.deepEqual(r.json.filter((u) => u.tenantId && u.tenantId !== etat.fx), [], "comptes d'un autre organisme rendus");
  else assert.equal(r.statut, 403);
});
