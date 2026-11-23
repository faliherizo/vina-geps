// Élévation de privilège verticale et affectation de masse (OWASP API3:2023, API5:2023) : les profils de l'addenda 2
// (Q20) — destinataire, expéditeur sans administration (rôle propre), gestionnaire, administration.
import { test } from "node:test";
import assert from "node:assert/strict";
import { preparer, fxao } from "../commun.mjs";

test("IN-FX-08 — une destinataire n'envoie pas d'alerte, critique ou non", async () => {
  const roy = await fxao(await preparer(), "inf.roy");
  for (const priorite of ["information", "critique"]) {
    const r = await roy("POST", "/api/alertes", { corps: { titre: "x", corps: "x", priorite, personnes: ["inf.cote"] } });
    assert.equal(r.statut, 403, `${priorite} : ${r.statut}`);
  }
});

test("IN-FX-09 — l'expéditeur de garde envoie, mais n'administre rien et n'envoie pas en critique", async () => {
  const exp = await fxao(await preparer(), "exp.garde.hsj");
  const ok = await exp("POST", "/api/alertes", { corps: { titre: "Garde", corps: "Rappel", priorite: "urgente", personnes: ["inf.roy"] } });
  assert.equal(ok.statut, 201, `son métier : ${ok.texte.slice(0, 200)}`);
  assert.equal((await exp("POST", "/api/alertes", { corps: { titre: "x", corps: "x", priorite: "critique", personnes: ["inf.roy"] } })).statut, 403);
  for (const [m, c, corps] of [["GET", "/api/auth/users"], ["POST", "/api/alertes/reponses", { libelle: "x", semantique: "information" }],
    ["PUT", "/api/alertes/reglages", { copieCourriel: true }], ["GET", "/api/auth/journal-actions"]]) {
    assert.equal((await exp(m, c, { corps })).statut, 403, `${m} ${c}`);
  }
});

test("IN-FX-10 — affectation de masse : un compte ne s'attribue ni rôle ni organisme", async () => {
  const etat = await preparer();
  const exp = await fxao(etat, "exp.garde.hsj");
  const moi = await exp("GET", "/api/auth/me");
  const id = moi.json?.id ?? moi.json?.utilisateur?.id;
  const r = await exp("PATCH", `/api/auth/users/${id}`, { corps: { role: "admin", tenantId: etat.temoin } });
  assert.ok([401, 403].includes(r.statut), `auto-promotion : ${r.statut}`);
  const profil = await exp("PUT", "/api/auth/profile", { corps: { role: "admin", tenantId: etat.temoin, capabilities: ["*"] } });
  const apres = await exp("GET", "/api/auth/me");
  assert.ok(!JSON.stringify(apres.json).includes('"role":"admin"'), `le rôle a changé (profil ${profil.statut})`);
});
