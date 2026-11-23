// Confidentialité (critère 29 ; addenda 1, Q1 : aucun renseignement de santé) : les numéros ne sortent pas, et une note ne
// porte pas de numéro d'assurance maladie.
import { test } from "node:test";
import assert from "node:assert/strict";
import { preparer, fxao } from "../commun.mjs";

const NUMERO = "+1 450 555 0142";
const TRACE = /555.?0142|4505550142/;

test("IN-FX-11 — un numéro posé sur une fiche n'est rendu à aucun profil", async () => {
  const etat = await preparer();
  const admin = await fxao(etat, "admin");
  const cree = await admin("POST", "/api/personnel/employes", { corps: { firstName: "Témoin", lastName: "Numéro", phone: NUMERO, email: "temoin.numero@exemple.test" } });
  assert.equal(cree.statut, 201, cree.texte.slice(0, 200));
  for (const compte of ["admin", "coord.urgence", "exp.garde.hsj", "inf.roy"]) {
    const s = compte === "admin" ? admin : await fxao(etat, compte);
    for (const chemin of ["/api/personnel/employes", `/api/personnel/employes/${cree.json.id}`, "/api/personnel/employes/suggestions?q=Num"]) {
      const r = await s("GET", chemin);
      assert.ok(!TRACE.test(r.texte), `${compte} lit le numéro par ${chemin}`);
    }
  }
});

test("IN-FX-12 — une note au dossier refuse un numéro d'assurance maladie", async () => {
  const chef = await fxao(await preparer(), "chef.urgence.hsj");
  const r = await chef("PUT", "/api/alertes/notes/inf.roy", { corps: { texte: "NAM ROYJ 8512 2514 à vérifier" } });
  assert.equal(r.statut, 400, `note avec NAM : ${r.statut} ${r.texte.slice(0, 160)}`);
});
