/**
 * La marque d'un semis inachevé : un organisme naît marqué et ne perd la marque qu'au dernier geste du semis. Un
 * semis interrompu reste donc reconnaissable, et l'orchestrateur ne le montre pas comme une démonstration.
 */
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { SEMIS_EN_COURS, semisInacheve, achever } = require("./api-semis.cjs");

test("un nom marqué est inachevé ; le nom définitif ne l'est pas", () => {
  assert.equal(semisInacheve(`Clincom (2026-11-23 02:13)${SEMIS_EN_COURS}`), true);
  assert.equal(semisInacheve("Clincom (2026-11-23 02:13)"), false);
  // La marque se lit à la fin du nom seulement : un nom qui la contient ailleurs n'est pas un semis interrompu.
  assert.equal(semisInacheve(`${SEMIS_EN_COURS} Clincom`), false);
});

test("achever retire la marque par le superadmin et rend l'organisme sous son nom définitif", async () => {
  const appels = [];
  const appeler = async (chemin, options) => { appels.push({ chemin, ...options }); return null; };
  const tenant = { id: "t-1", name: `Clincom (2026-11-23 02:13)${SEMIS_EN_COURS}` };
  const fini = await achever(appeler, "jeton-sa", tenant);
  assert.deepEqual(fini, { id: "t-1", name: "Clincom (2026-11-23 02:13)" });
  assert.deepEqual(appels, [{ chemin: "/api/superadmin/tenants/t-1", methode: "PATCH", jeton: "jeton-sa", corps: { name: "Clincom (2026-11-23 02:13)" } }]);
});

test("achever un organisme sans marque garde son nom", async () => {
  const appeler = async () => null;
  assert.equal((await achever(appeler, "j", { id: "t-2", name: "Urgences-santé" })).name, "Urgences-santé");
});
