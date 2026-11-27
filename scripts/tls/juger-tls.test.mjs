/** Le verdict TLS : ce qui est refusé l'est en le nommant ; un rapport sain passe. `node --test scripts/tls/`. */
import { test } from "node:test";
import assert from "node:assert/strict";
import { bilan, constats } from "./juger-tls.mjs";

const dans = (jours) => new Date(Date.now() + jours * 86_400_000).toISOString().slice(0, 16).replace("T", " ");
const sain = () => [
  { id: "SSLv3", severity: "OK", finding: "not offered" }, { id: "TLS1", severity: "OK", finding: "not offered" },
  { id: "TLS1_1", severity: "OK", finding: "not offered" }, { id: "TLS1_2", severity: "OK", finding: "offered" },
  { id: "TLS1_3", severity: "OK", finding: "offered with final" }, { id: "cert_notAfter", severity: "OK", finding: dans(80) },
  { id: "cert_chain_of_trust", severity: "OK", finding: "passed." }, { id: "HSTS", severity: "OK", finding: "365 days=31536000 s" },
];

test("un service en TLS 1.2 et 1.3, certificat valide et HSTS, est conforme", () => {
  assert.deepEqual(constats(sain()), []);
  assert.equal(bilan([{ nom: "geps", rapport: sain() }]).conforme, true);
});

test("TLS 1.0 offert, une faille, un certificat qui expire et l'absence de HSTS sont nommés", () => {
  const r = sain().map((x) => x.id === "TLS1" ? { ...x, finding: "offered (deprecated)" }
    : x.id === "cert_notAfter" ? { ...x, finding: dans(3) } : x.id === "HSTS" ? { ...x, finding: "not offered" } : x);
  r.push({ id: "BREACH", severity: "MEDIUM", finding: "potentially VULNERABLE" });
  const c = constats(r);
  assert.ok(c.includes("TLS1 offert"));
  assert.ok(c.some((x) => x.startsWith("BREACH (MEDIUM)")));
  assert.ok(c.some((x) => /expire dans [0-3] jour/.test(x)));
  assert.ok(c.includes("HSTS absent"));
});

test("sans TLS moderne, chaîne rompue : refusé ; un rapport vide n'est jamais conforme", () => {
  const r = sain().map((x) => x.id.startsWith("TLS1_") ? { ...x, finding: "not offered" }
    : x.id === "cert_chain_of_trust" ? { ...x, finding: "failed (self signed)" } : x);
  const c = constats(r);
  assert.ok(c.includes("ni TLS 1.2 ni TLS 1.3 offert"));
  assert.ok(c.some((x) => x.startsWith("chaîne de confiance")));
  const vide = bilan([{ nom: "muet", rapport: [] }]);
  assert.equal(vide.conforme, false);
  assert.match(vide.markdown, /rapport vide/);
});
