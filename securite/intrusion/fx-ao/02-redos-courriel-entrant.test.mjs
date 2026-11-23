// Déni de service par expression régulière (CWE-1333, OWASP API4:2023) : le routage des courriels entrants compilait un
// motif fourni par l'appelant et l'appliquait à un sujet fourni par lui.
import { test } from "node:test";
import assert from "node:assert/strict";
import { preparer, temoin } from "../commun.mjs";

/** Au-delà, un fil du serveur est occupé : avant correctif, ce cas dépassait la minute. */
const DELAI_MAX_MS = 5_000;

test("IN-FX-01 — un motif catastrophique ne monopolise pas le serveur", async () => {
  const admin = await temoin(await preparer());
  const r = await admin("POST", "/api/integrations/inbound-email", { corps: {
    email: { from: "a@b.c", subject: `${"a".repeat(64)}!` }, routes: [{ subjectPattern: "(a+)+$", action: "piege" }, { subjectPattern: "!", action: "repli" }] } });
  assert.equal(r.statut, 200, r.texte.slice(0, 200));
  assert.ok(r.duree < DELAI_MAX_MS, `réponse en ${r.duree} ms`);
  assert.equal(r.json.action, "repli", "le motif coûteux est abandonné, la route suivante est essayée");
});

test("IN-FX-01b — un employé ne déclenche pas le routage (réservé à l'administration)", async () => {
  const etat = await preparer();
  const admin = await temoin(etat);
  const u = `employe-${Date.now()}`;
  const cree = await admin("POST", "/api/auth/users", { corps: { username: u, password: "Intrusion!Essai2026", role: "employee", mustChangePassword: false } });
  assert.ok([200, 201].includes(cree.statut), cree.texte.slice(0, 200));
  await admin("PATCH", `/api/auth/users/${cree.json.id}`, { corps: { securitySetupRequired: false } });
  const { session } = await import("../commun.mjs");
  const employe = await session({ username: u, password: "Intrusion!Essai2026", tenantId: etat.temoin });
  assert.equal((await employe("POST", "/api/integrations/inbound-email", { corps: { email: { subject: "x" }, routes: [] } })).statut, 403);
});
