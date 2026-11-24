/**
 * Essais des parties pures de `scripts/creer-organisme-par-defaut.cjs` (données, idempotence, autocontrôle) et de
 * son client HTTP, contre un serveur local jetable.
 *
 *   node --test --experimental-test-coverage --test-coverage-include='scripts/lib/organisme-par-defaut/*.cjs' \
 *     scripts/lib/organisme-par-defaut/organisme-par-defaut.test.cjs
 *
 * Le script entier se valide en l'exécutant contre un serveur Java (docs/ORGANISME-PAR-DEFAUT.md, § Vérifier).
 */
process.env.SEMIS_PAUSE_MS = "0";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const http = require("node:http");
const D = require("./donnees.cjs");
const I = require("./idempotence.cjs");
const V = require("./verification.cjs");
const { clientApi, ErreurApi } = require("./client.cjs");

// ── Données ──────────────────────────────────────────────────────────────────────────────────────

test("les référentiels passent dans l'ordre : une référence vise toujours une valeur déjà posée", () => {
  const liste = D.referentiels(2026);
  const poses = new Map();
  for (const { type, corps } of liste) {
    if (corps.attributs?.parent) assert.ok(poses.get("unites")?.has(corps.attributs.parent), `parent ${corps.attributs.parent}`);
    if (type === "titres_emploi") assert.ok(poses.get("corps_emploi").has(corps.attributs.corps), `corps de ${corps.code}`);
    // Addenda 8, M-04 : une compétence n'est exigée que de corps d'emploi déjà posés.
    if (type === "competences") for (const c of corps.attributs.corps) assert.ok(poses.get("corps_emploi").has(c), `corps exigeant ${corps.code}`);
    if (!poses.has(type)) poses.set(type, new Set());
    poses.get(type).add(corps.code);
    assert.equal(corps.effectifDepuis, D.EFFECTIF_DEPUIS);
  }
  assert.equal(liste.length, 49);
});

test("chaque code d'absence porte sa catégorie de durée ; ceux d'une banque portent leurs heures par jour", () => {
  for (const c of D.CODES_ABSENCE) {
    assert.ok(["PONCTUELLE", "MOYEN_TERME", "LONG_TERME"].includes(c.attributs.duree), c.code);
    if (c.attributs.banque) assert.ok(c.attributs.heuresParJour > 0, c.code);
  }
  assert.ok(D.CODES_ABSENCE.some((c) => c.attributs.seuilJoursLongTerme), "au moins un seuil de remplacement long terme");
});

test("les fériés ont pour code leur date", () => {
  for (const f of D.joursFeries(2027)) assert.match(f.code, /^2027-\d{2}-\d{2}$/);
});

test("un compte par rôle intégré, trois employés au moins, et chaque personne reliée à une ressource", () => {
  const roles = new Set(D.COMPTES.map((c) => c.role));
  for (const r of ["director", "manager", "rh", "accountant", "employee"]) assert.ok(roles.has(r), r);
  assert.ok(D.COMPTES.filter((c) => c.role === "employee").length >= 3);
  for (const c of D.COMPTES) assert.ok(c.ressource?.matricule, `${c.cle} sans ressource`);
  const matricules = D.COMPTES.map((c) => c.ressource.matricule);
  assert.equal(new Set(matricules).size, matricules.length, "matricules uniques");
});

test("un gestionnaire reçoit la gestion de son unité", () => {
  for (const c of D.COMPTES.filter((x) => x.role === "manager")) {
    assert.equal(c.perimetre.niveau, "GESTION");
    assert.equal(c.perimetre.uniteCode, c.ressource.uniteCode);
  }
});

test("chaque ressource vise des codes du référentiel", () => {
  const codes = (liste) => new Set(liste.map((v) => v.code));
  const [unites, corps, titres, statuts, conv] = [D.UNITES, D.CORPS_EMPLOI, D.TITRES_EMPLOI, D.STATUTS_EMPLOI, D.CONVENTIONS].map(codes);
  for (const { ressource: r } of D.COMPTES) {
    assert.ok(unites.has(r.uniteCode) && corps.has(r.corpsEmploiCode) && titres.has(r.titreEmploiCode));
    assert.ok(statuts.has(r.statutEmploiCode) && conv.has(r.conventionCode));
    assert.equal(D.TITRES_EMPLOI.find((t) => t.code === r.titreEmploiCode).attributs.corps, r.corpsEmploiCode);
  }
});

test("les règles visent l'organisme, et les milieux un type et une unité connus", () => {
  for (const r of D.REGLES) assert.equal(r.porteeType, "ORGANISME");
  assert.ok(D.REGLES.some((r) => r.contexte === "ABSENCE") && D.REGLES.some((r) => r.contexte === "ATTRIBUTION"));
  for (const m of D.MILIEUX) {
    assert.ok(D.TYPES_MILIEU.some((t) => t.code === m.typeCode));
    assert.ok(D.UNITES.some((u) => u.code === m.uniteCode));
  }
});

test("les dates se calculent en UTC, sans dépendre du fuseau du poste", () => {
  assert.equal(D.decaler("2026-12-31", 1), "2027-01-01");
  assert.equal(D.decaler("2026-03-01", -1), "2026-02-28");
  assert.equal(D.lundiDe("2026-11-22"), "2026-11-16"); // dimanche
  assert.equal(D.lundiDe("2026-11-16"), "2026-11-16"); // lundi
});

test("la période courante couvre deux semaines depuis le lundi ; la suivante clôt sa collecte la veille", () => {
  const p = D.periodes("2026-11-18");
  assert.deepEqual([p.courante.debut, p.courante.fin], ["2026-11-16", "2026-11-29"]);
  assert.deepEqual([p.collecte.debut, p.collecte.fin, p.collecte.finCollecte], ["2026-11-30", "2026-12-13", "2026-11-29"]);
});

test("les équipages commencent aujourd'hui, un quart de jour et un de nuit par jour", () => {
  const p = D.periodes("2026-11-18").courante;
  const liste = D.equipages(p, "2026-11-18");
  assert.equal(liste.length, 12 * 2);
  assert.equal(liste[0].date, "2026-11-18");
  assert.deepEqual([liste[1].debut, liste[1].fin], [1380, 420]);
  assert.equal(D.equipages(p, "2026-11-01")[0].date, "2026-11-16", "avant la période : depuis son début");
});

test("chaque employé reçoit un quart de jour distinct ; l'absence de démonstration vise un code planifiable", () => {
  const p = D.periodes("2026-11-18").courante;
  const a = D.attributionsDeDemo(D.equipages(p, "2026-11-18"));
  assert.equal(a.length, 3);
  assert.equal(new Set(a.map((x) => x.equipage.date)).size, 3);
  assert.ok(a.every((x) => x.equipage.plageCode === "J"));
  assert.equal(D.attributionsDeDemo(D.equipages(p, "2026-11-29")).length, 1, "fin de période : on n'invente pas de quart");
  const abs = D.absenceDeDemo("2026-11-22");
  assert.equal(abs.corps.du, "2026-12-13");
  assert.ok(D.CODES_ABSENCE.find((c) => c.code === abs.corps.motif).attributs.planifiable);
});

test("le repli des modules ne contient aucune option d'hébergement", () => {
  assert.ok(!D.MODULES_DU_PRODUIT.includes("byok") && !D.MODULES_DU_PRODUIT.includes("dedicated"));
  assert.ok(D.FAMILLES_HORS_PRODUIT.has("infrastructure"));
});

// ── Idempotence ──────────────────────────────────────────────────────────────────────────────────

test("trouver compare par la clé métier, et tient une liste absente pour vide", () => {
  const liste = [{ code: "A", id: 1 }, null, { code: "B", id: 2 }];
  assert.equal(I.trouver(liste, "code", "B").id, 2);
  assert.equal(I.trouver(liste, "code", "Z"), null);
  assert.equal(I.trouver(undefined, "code", "A"), null);
});

test("une règle se retrouve par clé, contexte et portée", () => {
  const existantes = [{ cle: "r", contexte: "ABSENCE", porteeType: "ORGANISME", porteeCode: null }, { cle: "u", contexte: "ABSENCE" }];
  assert.ok(I.regleExistante(existantes, { cle: "r", contexte: "ABSENCE", porteeType: "ORGANISME" }));
  assert.ok(I.regleExistante(existantes, { cle: "u", contexte: "ABSENCE", porteeType: "ORGANISME" }), "portée absente = organisme");
  assert.equal(I.regleExistante(existantes, { cle: "r", contexte: "ATTRIBUTION", porteeType: "ORGANISME" }), null);
  assert.equal(I.regleExistante(existantes, { cle: "r", contexte: "ABSENCE", porteeType: "UNITE", porteeCode: "OPS" }), null);
  assert.equal(I.regleExistante(null, { cle: "r" }), null);
});

test("un périmètre fermé ne compte plus", () => {
  const liste = [{ utilisateurId: "u", uniteCode: "OPS", jusqua: "2026-01-01" }, { utilisateurId: "v", uniteCode: "OPS", jusqua: null }];
  assert.equal(I.perimetreExistant(liste, "u", "OPS"), null);
  assert.ok(I.perimetreExistant(liste, "v", "OPS"));
  assert.equal(I.perimetreExistant(undefined, "v", "OPS"), null);
});

test("une période n'avance que vers l'avant", () => {
  assert.deepEqual(I.etatsAFranchir(undefined, "CONFECTION"), ["CONFECTION"]);
  assert.deepEqual(I.etatsAFranchir("CONFECTION", "PUBLIEE"), ["PUBLIEE"]);
  assert.deepEqual(I.etatsAFranchir("PUBLIEE", "CONFECTION"), [], "une période publiée ne recule pas");
  assert.deepEqual(I.etatsAFranchir("COLLECTE", "COLLECTE"), []);
  assert.throws(() => I.etatsAFranchir("PREPARATION", "INCONNU"), /inconnu/);
});

test("un 409 se reconnaît au statut", () => {
  assert.ok(I.estConflit({ statut: 409 }));
  assert.ok(!I.estConflit({ statut: 422 }));
  assert.ok(!I.estConflit(undefined));
});

test("le nom de fichier d'un organisme est sans accent ni espace", () => {
  assert.equal(I.slug("Organisme par défaut"), "organisme-par-defaut");
  assert.equal(I.slug("  Équipe — Nord! "), "equipe-nord");
  assert.equal(I.slug("—"), "organisme");
});

test("un mot de passe tiré dépasse la politique (12 caractères) et mêle les quatre classes", () => {
  const fixe = I.motDePasse((n) => Buffer.alloc(n, 7));
  assert.equal(fixe, I.motDePasse((n) => Buffer.alloc(n, 7)), "déterministe à tirage égal");
  for (const mdp of [fixe, I.motDePasse()]) {
    assert.ok(mdp.length >= 24);
    for (const classe of [/[a-z]/, /[A-Z]/, /\d/, /[^A-Za-z0-9]/]) assert.match(mdp, classe);
  }
  assert.notEqual(I.motDePasse(), I.motDePasse());
});

test("la fusion des comptes garde l'ancien mot de passe sauf remplacement explicite", () => {
  const anciens = [{ username: "a", password: "1", userId: "u1" }, { username: "b", password: "2" }];
  const f = I.fusionnerComptes(anciens, [{ username: "a", ressourceId: "r1" }, { username: "b", password: "3" }, { username: "c", password: "4" }]);
  assert.deepEqual(f.find((c) => c.username === "a"), { username: "a", password: "1", userId: "u1", ressourceId: "r1" });
  assert.equal(f.find((c) => c.username === "b").password, "3");
  assert.equal(f.length, 3);
  assert.equal(I.fusionnerComptes(undefined, [{ username: "x" }]).length, 1);
});

// ── Autocontrôle ─────────────────────────────────────────────────────────────────────────────────

test("un compte avec ressource contrôle ses quatre écrans personnels et sa ressource", () => {
  const ctl = V.controlesDe({ role: "employee", aRessource: true }, { du: "2026-11-22", au: "2026-12-05" });
  for (const chemin of ["/api/effectifs/absences", "/api/effectifs/disponibilites/moi", "/api/effectifs/temps/moi", "/api/effectifs/ma-ressource"]) {
    assert.ok(ctl.some((c) => c.chemin === chemin), chemin);
  }
  assert.ok(ctl.some((c) => c.chemin.startsWith("/api/effectifs/mon-horaire?du=2026-11-22&au=2026-12-05")));
  assert.ok(ctl.some((c) => c.chemin === "/api/auth/users" && c.attendu === 403), "l'employé est refusé à l'administration");
});

test("l'administration n'a pas d'écrans personnels ; un rôle inconnu n'a aucun contrôle", () => {
  assert.ok(V.controlesDe({ role: "admin", aRessource: false }, {}).every((c) => c.attendu === 200));
  assert.deepEqual(V.controlesDe({ role: "inconnu", aRessource: false }, {}), []);
  for (const role of ["director", "manager", "rh", "accountant"]) assert.ok(V.PAR_ROLE[role].length >= 2, role);
});

test("le verdict refuse le mauvais statut, la phrase « aucune ressource planifiable », la ressource absente et la liste trop courte", () => {
  const simple = { attendu: 200 };
  assert.equal(V.verdict(simple, 200, []), null);
  assert.match(V.verdict(simple, 404, {}), /statut 404, attendu 200/);
  assert.match(V.verdict({ attendu: 404 }, 404, { errors: ["votre compte n'est relié à aucune ressource planifiable"] }), /aucune ressource planifiable/);
  assert.match(V.verdict(simple, 200, "aucune ressource planifiable"), /aucune ressource/);
  assert.match(V.verdict({ attendu: 200, ressource: true }, 200, { ressource: null }), /aucune ressource reliée/);
  assert.equal(V.verdict({ attendu: 200, ressource: true }, 200, { ressource: { id: "r" } }), null);
  assert.match(V.verdict({ attendu: 200, auMoins: 3 }, 200, [1]), /1 élément/);
  assert.match(V.verdict({ attendu: 200, auMoins: 3 }, 200, {}), /0 élément/);
  assert.equal(V.verdict({ attendu: 200, auMoins: 1 }, 200, [1]), null);
  assert.equal(V.verdict({ attendu: 403 }, 403, null), null);
});

// ── Client HTTP ──────────────────────────────────────────────────────────────────────────────────

async function serveur(repondre) {
  const s = http.createServer((req, res) => {
    let corps = "";
    req.on("data", (d) => { corps += d; });
    req.on("end", () => repondre(req, corps ? JSON.parse(corps) : null, res));
  });
  await new Promise((ok) => s.listen(0, "127.0.0.1", ok));
  return { s, api: `http://127.0.0.1:${s.address().port}` };
}

test("le client envoie jeton et organisme, lit le JSON, et lève une ErreurApi qui porte le statut sans le corps envoyé", async () => {
  const vus = [];
  const { s, api } = await serveur((req, corps, res) => {
    vus.push({ url: req.url, auth: req.headers.authorization, tenant: req.headers["x-tenant-id"], corps });
    if (req.url === "/api/auth/login") {
      res.writeHead(corps.password === "bon" ? 200 : 401, { "Content-Type": "application/json" });
      return res.end(JSON.stringify(corps.password === "bon" ? { token: "t" } : { errors: ["invalid credentials"] }));
    }
    if (req.url === "/texte") { res.writeHead(200); return res.end("pas du json"); }
    if (req.url === "/vide") { res.writeHead(204); return res.end(); }
    res.writeHead(409, { "Content-Type": "application/json" });
    return res.end(JSON.stringify({ errors: ["existe déjà"] }));
  });
  try {
    const c = clientApi(api);
    assert.equal(await c.connecter({ username: "u", password: "bon", tenantId: "T" }), "t");
    assert.equal(await c.connecter({ username: "u", password: "mauvais" }), null);
    assert.equal(await c.appeler("/texte", { jeton: "t", tenantId: "T" }), "pas du json");
    assert.equal(vus.at(-1).auth, "Bearer t");
    assert.equal(vus.at(-1).tenant, "T");
    assert.equal(await c.appeler("/vide"), null);
    const e = await c.appeler("/conflit?x=1", { methode: "POST", corps: { password: "secret" } }).catch((x) => x);
    assert.ok(e instanceof ErreurApi);
    assert.equal(e.statut, 409);
    assert.ok(I.estConflit(e));
    assert.match(e.message, /^POST \/conflit → 409/);
    assert.ok(!e.message.includes("secret"), "le corps envoyé n'apparaît jamais dans l'erreur");
    assert.deepEqual(await c.essayer("/conflit"), { statut: 409, corps: { errors: ["existe déjà"] } });
  } finally {
    s.close();
  }
});

test("ErreurApi résume un corps textuel ou absent", () => {
  assert.match(new ErreurApi("GET", "/a", 500, "panne").message, /GET \/a → 500 panne/);
  assert.match(new ErreurApi("GET", "/a", 502, undefined).message, /→ 502 ""/);
});

test("absence de démonstration : celle d'un semis antérieur (dates décalées) compte comme déjà semée", () => {
  const voulue = { du: "2026-12-14", au: "2026-12-15" };
  assert.equal(D.chevauche({ du: "2026-12-13", au: "2026-12-14", statut: "ACCEPTEE" }, voulue), true);
  assert.equal(D.chevauche({ debut: "2026-12-15T00:00:00Z", fin: "2026-12-16", statut: "A_APPROUVER" }, voulue), true);
  assert.equal(D.chevauche({ du: "2026-12-10", au: "2026-12-13", statut: "ACCEPTEE" }, voulue), false);
  // Annulée ou refusée, elle libère ses dates : le serveur accepte alors la nouvelle.
  assert.equal(D.chevauche({ du: "2026-12-14", au: "2026-12-15", statut: "ANNULEE" }, voulue), false);
  assert.equal(D.chevauche({ au: "2026-12-15" }, voulue), false);
});

test("addenda 8, M-04 : les compétences de démonstration laissent passer les attributions et peuplent le relevé des échéances", () => {
  const c = D.competencesDeDemo("2026-11-23");
  const exigees = D.COMPETENCES.filter((x) => x.attributs.corps.includes("PARA")).map((x) => x.code);
  // Chaque employé paramédic détient chaque compétence exigée, valide au moins jusqu'à la fin de la période de démonstration.
  for (const cle of D.COMPTES.filter((x) => x.role === "employee").map((x) => x.cle)) {
    for (const code of exigees) {
      const d = c.find((x) => x.cle === cle && x.code === code);
      assert.ok(d && d.corps.echeance >= "2026-12-07", `${cle} ${code}`);
    }
  }
  assert.ok(c.some((x) => x.corps.echeance < "2026-11-23"), "une compétence échue au relevé");
  assert.ok(c.every((x) => x.corps.obtenueLe <= x.corps.echeance));
});

test("addenda 8 : les données de démonstration visent des types, des unités et des semaines qui existent", () => {
  const a = D.addenda8DeDemo("2026-11-23");
  const types = D.TYPES_DEMANDE.map((t) => t.code);
  for (const d of a.demandes) assert.ok(types.includes(d.corps.typeCode), d.corps.typeCode);
  assert.ok(D.UNITES.some((u) => u.code === a.rattachement.corps.uniteCode));
  assert.equal(a.campagne.annee, 2027);
  // Les semaines choisies sont des lundis de la saison.
  for (const s of a.choix.semaines) {
    assert.equal(new Date(`${s}T12:00:00`).getDay(), 1, s);
    assert.ok(s >= a.campagne.du && s <= a.campagne.au, s);
  }
  assert.ok(D.CODES_ABSENCE.some((c) => c.code === a.campagne.codeAbsence));
});

