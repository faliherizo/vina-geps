// Cloisonnement par module (OWASP API5:2023, fonction non autorisée) : l'organisme FX-AO n'a que l'avis. Une vue masquée
// au menu reste une route : c'est le serveur qui doit refuser.
import { test } from "node:test";
import assert from "node:assert/strict";
import { preparer, fxao } from "../commun.mjs";

const HORS_AVIS = ["/api/paie/dossiers", "/api/effectifs/temps/feuilles", "/api/effectifs/presence/pointages", "/api/effectifs/echanges",
  "/api/effectifs/agph/campagnes", "/api/effectifs/integrations/connecteurs", "/api/integrations/webhooks", "/api/securite/incidents"];

test("IN-FX-02 — l'administrateur FX-AO ne peut ni lister ni émettre de clé d'API (module integrations non accordé)", async () => {
  const admin = await fxao(await preparer(), "admin");
  assert.equal((await admin("GET", "/api/integrations/api-clients")).statut, 403);
  const cree = await admin("POST", "/api/integrations/api-clients", { corps: { name: "exfiltration", scopes: ["read", "write"] } });
  assert.equal(cree.statut, 403, `émettre un secret machine doit être refusé : ${cree.texte.slice(0, 200)}`);
  assert.ok(!cree.texte.includes("clientSecret"), "aucun secret ne doit être rendu");
});

test("IN-FX-04 — chaque fonctionnalité hors de l'avis répond 403 à l'administrateur FX-AO", async () => {
  const admin = await fxao(await preparer(), "admin");
  const ouvertes = [];
  for (const chemin of HORS_AVIS) {
    const r = await admin("GET", chemin);
    if (r.statut !== 403) ouvertes.push(`${chemin} → ${r.statut}`);
  }
  assert.deepEqual(ouvertes, []);
});

test("IN-FX-05 — le courriel entrant et les utilitaires d'intégration sont fermés sans le module", async () => {
  const admin = await fxao(await preparer(), "admin");
  for (const [chemin, corps] of [["/api/integrations/inbound-email", { email: { subject: "x" }, routes: [] }],
    ["/api/integrations/export.xml", { records: [] }], ["/api/integrations/sso/map-role", { mapping: { claim: "roles" } }]]) {
    assert.equal((await admin("POST", chemin, { corps })).statut, 403, chemin);
  }
});
