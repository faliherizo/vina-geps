import { test } from "node:test";
import assert from "node:assert/strict";
import { ServicePoussee, NOTIFICATIONS_GARDEES } from "../src/service.mjs";
import { lireRequete, ecartsDuMessage, TAILLE_MAX_OCTETS, MESSAGES_PAR_REQUETE } from "../src/expo.mjs";

const IOS = "ExponentPushToken[ios-julie]";
const ANDROID = "ExpoPushToken[android-marc]";

function service(options = {}) {
  let n = 0;
  const s = new ServicePoussee({ horloge: () => new Date("2026-11-20T12:00:00Z"), identifiant: () => `billet-${++n}`, ...options });
  s.inscrire(IOS, { plateforme: "ios", application: { droitAlertesCritiques: true }, utilisateur: { autoriseAlertesCritiques: true, modeSilencieux: true } });
  s.inscrire(ANDROID, { plateforme: "android" });
  return s;
}

test("un message seul rend un billet seul ; un tableau, un tableau — comme Expo", () => {
  const s = service();
  assert.deepEqual(s.envoyer({ to: IOS, title: "A" }), { statut: 200, corps: { data: { status: "ok", id: "billet-1" } } });
  const r = s.envoyer([{ to: IOS, title: "B" }, { to: [ANDROID, IOS], title: "C" }]);
  assert.deepEqual(r.corps.data.map((b) => b.id), ["billet-2", "billet-3", "billet-4"]);
  assert.equal(s.envoyer({ to: [IOS, ANDROID], title: "D" }).corps.data.length, 2, "un seul message à deux jetons rend deux billets");
});

test("jeton inconnu, mal formé ou désinstallé : DeviceNotRegistered dans le billet", () => {
  const s = service();
  for (const to of ["ExponentPushToken[inconnu]", "pas-un-jeton"]) {
    const b = s.envoyer({ to, title: "x" }).corps.data;
    assert.equal(b.status, "error");
    assert.equal(b.details.error, "DeviceNotRegistered");
  }
  assert.equal(s.desinscrire(ANDROID).statut, 204);
  assert.equal(s.desinscrire(ANDROID).statut, 404);
  assert.equal(s.envoyer({ to: ANDROID, title: "x" }).corps.data.details.error, "DeviceNotRegistered");
});

test("charge trop grosse : MessageTooBig ; requête mal formée : 400 VALIDATION_ERROR nommant le champ", () => {
  const s = service();
  assert.equal(s.envoyer({ to: IOS, body: "x".repeat(TAILLE_MAX_OCTETS) }).corps.data.details.error, "MessageTooBig");
  const r = s.envoyer({ to: IOS, priority: "urgente" });
  assert.equal(r.statut, 400);
  assert.deepEqual(r.corps.errors, [{ code: "VALIDATION_ERROR", message: '"[0].priority" must be one of [default, normal, high]' }]);
});

test("validation de forme d'un message Expo", () => {
  assert.deepEqual(ecartsDuMessage(null, 0), ['"[0]" must be an object']);
  assert.deepEqual(ecartsDuMessage([], 1), ['"[1]" must be an object']);
  assert.match(ecartsDuMessage({}, 0)[0], /"\[0\]\.to" is required/);
  assert.match(ecartsDuMessage({ to: [] }, 0)[0], /to/);
  assert.match(ecartsDuMessage({ to: [3] }, 0)[0], /to/);
  assert.deepEqual(ecartsDuMessage({ to: IOS, title: 3, data: [], interruptionLevel: "fort", sound: 4 }, 0), [
    '"[0].title" must be a string', '"[0].data" must be an object', '"[0].interruptionLevel" must be one of [active, critical, passive, time-sensitive]',
    '"[0].sound" must be a string, null or an object']);
  assert.deepEqual(ecartsDuMessage({ to: IOS, data: null, sound: { critical: "oui", volume: 2, name: 5 } }, 0), [
    '"[0].data" must be an object', '"[0].sound".critical must be a boolean', '"[0].sound".volume must be a number between 0 and 1', '"[0].sound".name must be a string']);
  assert.deepEqual(ecartsDuMessage({ to: IOS, sound: [] }, 0), ['"[0].sound" must be a string, null or an object']);
  assert.deepEqual(ecartsDuMessage({ to: IOS, title: null, sound: null, channelId: "c" }, 0), []);
  assert.deepEqual(lireRequete([]).ecarts, ['"value" must contain at least 1 items']);
  assert.match(lireRequete(Array.from({ length: MESSAGES_PAR_REQUETE + 1 }, () => ({ to: IOS }))).ecarts[0], /less than or equal to 100/);
});

test("jeton d'accès Expo exigé quand il est réglé (sécurité renforcée) : 401 UNAUTHORIZED", () => {
  const s = service({ jetonAcces: "secret" });
  assert.equal(s.envoyer({ to: IOS }, undefined).statut, 401);
  assert.equal(s.envoyer({ to: IOS }, "Bearer autre").corps.errors[0].code, "UNAUTHORIZED");
  assert.equal(s.envoyer({ to: IOS }, "Bearer secret").statut, 200);
});

test("accusés : ok pour un billet émis, identifiants inconnus omis ; ids exigés", () => {
  const s = service();
  s.envoyer({ to: IOS, title: "x" });
  assert.deepEqual(s.accuses({ ids: ["billet-1", "nulle-part"] }), { statut: 200, corps: { data: { "billet-1": { status: "ok" } } } });
  assert.equal(s.accuses({}).statut, 400);
  assert.equal(s.accuses(null).statut, 400);
});

test("ce que le téléphone a reçu : traduction fournisseur, présentation, filtres, vidage", () => {
  const s = service();
  s.envoyer({ to: IOS, title: "Évacuation", interruptionLevel: "critical", sound: { critical: true } });
  s.envoyer({ to: ANDROID, title: "Info" });
  const ios = s.notifications({ jeton: IOS }).corps;
  assert.equal(ios.length, 1);
  assert.equal(ios[0].fournisseur.nom, "apns");
  assert.equal(ios[0].recueLe, "2026-11-20T12:00:00.000Z");
  assert.equal(ios[0].presentation.contourneSilencieux, true);
  assert.equal(s.notifications({ titre: "Info" }).corps[0].fournisseur.nom, "fcm");
  assert.deepEqual(s.notifications().corps.map((n) => n.message.title), ["Info", "Évacuation"], "les plus récentes d'abord");
  assert.equal(s.notifications({ titre: "absent" }).corps.length, 0);
  s.envoyer({ to: ANDROID });
  assert.equal(s.notifications({ titre: "Info" }).corps.length, 1, "un message sans titre ne répond pas au filtre");
  assert.equal(s.vider().statut, 204);
  assert.equal(s.notifications().corps.length, 0);
  assert.deepEqual(s.accuses({ ids: ["billet-1"] }).corps.data, {});
});

test("le bac à sable ne s'engorge pas : les plus anciennes notifications tombent", () => {
  const s = service();
  for (let i = 0; i <= NOTIFICATIONS_GARDEES; i++) s.envoyer({ to: ANDROID, title: `n${i}` });
  const toutes = s.notifications().corps;
  assert.equal(toutes.length, NOTIFICATIONS_GARDEES);
  assert.equal(toutes.at(-1).message.title, "n1");
});

test("inscription : jeton et profil validés ; liste des appareils", () => {
  const s = service();
  assert.equal(s.inscrire("pas-un-jeton", { plateforme: "ios" }).statut, 400);
  assert.equal(s.inscrire("ExponentPushToken[x]", { plateforme: "web" }).statut, 400);
  assert.deepEqual(s.appareils().corps.map((a) => a.plateforme), ["ios", "android"]);
});

test("horloge par défaut : l'instant présent", () => {
  const s = new ServicePoussee({ identifiant: () => "b" });
  s.inscrire(IOS, { plateforme: "ios" });
  s.envoyer({ to: IOS });
  assert.ok(Date.parse(s.notifications().corps[0].recueLe) > Date.parse("2026-01-01"));
});
