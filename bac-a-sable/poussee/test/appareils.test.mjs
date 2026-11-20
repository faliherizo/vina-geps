import { test } from "node:test";
import assert from "node:assert/strict";
import { lireProfil, versApns, versFcm, presenter, CANAL_DE_REPLI } from "../src/appareils.mjs";

const JETON = "ExponentPushToken[appareil-1]";
const CRITIQUE = { to: JETON, title: "Code orange", body: "Rendez-vous", priority: "high", channelId: "critique",
  interruptionLevel: "critical", sound: { critical: true, name: "default", volume: 1 } };
const ORDINAIRE = { to: JETON, title: "Info", body: "x", sound: "default" };

function appareil(profil) {
  const lu = lireProfil(JETON, profil);
  assert.equal(lu.ecarts, undefined, JSON.stringify(lu.ecarts));
  return lu.appareil;
}
const SILENCE = { modeSilencieux: true, nePasDeranger: true };

test("profil : plateforme et canaux validés, valeurs par défaut prudentes", () => {
  assert.deepEqual(lireProfil(JETON, { plateforme: "web" }).ecarts, ["plateforme : l'une de ios, android"]);
  assert.match(lireProfil(JETON, { plateforme: "android", application: { canaux: [{ id: "x", importance: "fort" }] } }).ecarts[0], /canaux\[0\]/);
  assert.match(lireProfil(JETON, null).ecarts[0], /plateforme/);
  const a = appareil({ plateforme: "ios", nom: "iPhone de Julie" });
  assert.equal(a.nom, "iPhone de Julie");
  assert.deepEqual(a.utilisateur, { autoriseNotifications: true, autoriseAlertesCritiques: false, accesNePasDeranger: false, modeSilencieux: false, nePasDeranger: false });
  assert.equal(a.application.droitAlertesCritiques, false);
  assert.equal(appareil({ plateforme: "ios", nom: 3 }).nom, null);
  const k = appareil({ plateforme: "android", application: { canaux: [{ id: "c", importance: "max", usageAudio: "autre" }] } }).application.canaux[0];
  assert.deepEqual(k, { id: "c", importance: "max", contourneNePasDeranger: false, usageAudio: "notification" });
});

test("APNs : niveau d'interruption et son critique, priorité 10 sauf « normal »", () => {
  const a = versApns(CRITIQUE);
  assert.deepEqual(a.entetes, { "apns-priority": "10", "apns-push-type": "alert" });
  assert.equal(a.charge.aps["interruption-level"], "critical");
  assert.deepEqual(a.charge.aps.sound, { critical: 1, name: "default", volume: 1 });
  const b = versApns({ ...ORDINAIRE, priority: "normal", badge: 2, data: { url: "/x" } });
  assert.equal(b.entetes["apns-priority"], "5");
  assert.equal(b.charge.aps.sound, "default");
  assert.equal(b.charge.aps.badge, 2);
  assert.deepEqual(b.charge.body, { url: "/x" });
  assert.deepEqual(versApns({ to: JETON, sound: { critical: false } }).charge.aps.sound, { critical: 0, name: "default", volume: 1 });
  assert.equal(versApns({ to: JETON }).charge.aps.sound, undefined);
});

test("FCM : priorité haute et canal portés au message de données", () => {
  assert.deepEqual(versFcm(CRITIQUE).android, { priority: "HIGH" });
  assert.equal(versFcm(CRITIQUE).data.channelId, "critique");
  assert.deepEqual(versFcm({ to: JETON }), { android: { priority: "NORMAL" }, data: { title: null, message: null, channelId: null, sound: null, body: {} } });
});

test("iOS avec le droit Critical Alerts et l'autorisation : sonne malgré silencieux et Ne pas déranger", () => {
  const p = presenter(appareil({ plateforme: "ios", application: { droitAlertesCritiques: true }, utilisateur: { autoriseAlertesCritiques: true, ...SILENCE } }), CRITIQUE);
  assert.deepEqual(p, { affichee: true, sonore: true, contourneSilencieux: true, contourneNePasDeranger: true, niveau: "critical", volume: 1, motif: "alerte critique honorée" });
  const parSon = presenter(appareil({ plateforme: "ios", application: { droitAlertesCritiques: true }, utilisateur: { autoriseAlertesCritiques: true } }),
    { to: JETON, sound: { critical: true, volume: 0.4 } });
  assert.equal(parSon.volume, 0.4);
  const sansVolume = presenter(appareil({ plateforme: "ios", application: { droitAlertesCritiques: true }, utilisateur: { autoriseAlertesCritiques: true } }),
    { to: JETON, interruptionLevel: "critical", sound: "default" });
  assert.equal(sansVolume.volume, 1);
});

test("iOS sans le droit d'Apple (Q-ALC en attente) : ramenée au niveau active, muette en silencieux", () => {
  const p = presenter(appareil({ plateforme: "ios", utilisateur: { autoriseAlertesCritiques: true, ...SILENCE } }), CRITIQUE);
  assert.equal(p.niveau, "active");
  assert.equal(p.sonore, false);
  assert.match(p.motif, /droit Critical Alerts absent/);
  const refusee = presenter(appareil({ plateforme: "ios", application: { droitAlertesCritiques: true } }), CRITIQUE);
  assert.match(refusee.motif, /refusée par l'utilisateur/);
  assert.equal(refusee.sonore, true, "téléphone non silencieux : elle sonne comme une autre");
});

test("iOS ordinaire : silencieux, passive, time-sensitive", () => {
  assert.equal(presenter(appareil({ plateforme: "ios" }), ORDINAIRE).sonore, true);
  assert.equal(presenter(appareil({ plateforme: "ios" }), ORDINAIRE).motif, "notification ordinaire");
  assert.equal(presenter(appareil({ plateforme: "ios", utilisateur: { modeSilencieux: true } }), ORDINAIRE).sonore, false);
  assert.equal(presenter(appareil({ plateforme: "ios" }), { ...ORDINAIRE, interruptionLevel: "passive" }).sonore, false);
  const sensible = presenter(appareil({ plateforme: "ios", utilisateur: { nePasDeranger: true } }), { ...ORDINAIRE, interruptionLevel: "time-sensitive" });
  assert.deepEqual([sensible.sonore, sensible.contourneNePasDeranger], [true, true]);
  assert.equal(presenter(appareil({ plateforme: "ios", utilisateur: { nePasDeranger: true } }), ORDINAIRE).sonore, false);
  assert.equal(presenter(appareil({ plateforme: "ios" }), { to: JETON, title: "muet" }).sonore, false);
});

test("Android, canal « critique » d'alarme qui contourne Ne pas déranger : sonne en silencieux", () => {
  const a = appareil({ plateforme: "android", application: { canaux: [{ id: "critique", importance: "max", contourneNePasDeranger: true, usageAudio: "alarme" }] },
    utilisateur: { accesNePasDeranger: true, ...SILENCE } });
  assert.deepEqual(presenter(a, CRITIQUE), { affichee: true, sonore: true, contourneSilencieux: true, contourneNePasDeranger: true, canal: "critique", banniere: true, motif: "canal « critique »" });
  const sansAcces = appareil({ plateforme: "android", application: { canaux: [{ id: "critique", importance: "max", contourneNePasDeranger: true, usageAudio: "alarme" }] },
    utilisateur: SILENCE });
  assert.equal(presenter(sansAcces, CRITIQUE).sonore, false, "sans l'accès Ne pas déranger, le canal ne passe pas");
});

test("Android sans le canal « critique » : canal de repli, muette en silencieux", () => {
  const p = presenter(appareil({ plateforme: "android", utilisateur: { modeSilencieux: true } }), CRITIQUE);
  assert.equal(p.canal, CANAL_DE_REPLI.id);
  assert.equal(p.sonore, false);
  assert.match(p.motif, /non créé par l'application/);
  const ordinaire = presenter(appareil({ plateforme: "android" }), { to: JETON, title: "x", sound: "default" });
  assert.deepEqual([ordinaire.sonore, ordinaire.banniere, ordinaire.motif], [true, false, `canal « ${CANAL_DE_REPLI.id} »`]);
  const basse = appareil({ plateforme: "android", application: { canaux: [{ id: "bas", importance: "low" }] } });
  assert.equal(presenter(basse, { to: JETON, channelId: "bas", sound: "default" }).sonore, false);
  assert.equal(presenter(appareil({ plateforme: "android" }), { to: JETON, sound: null }).sonore, false);
});

test("notifications refusées par l'utilisateur : rien ne s'affiche", () => {
  for (const plateforme of ["ios", "android"]) {
    const p = presenter(appareil({ plateforme, utilisateur: { autoriseNotifications: false } }), CRITIQUE);
    assert.deepEqual([p.affichee, p.sonore], [false, false]);
  }
});
