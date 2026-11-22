/**
 * Les règles de la campagne, vérifiées sans JMeter : la classe SLO d'une route, la substitution
 * des paramètres de chemin par les colonnes du CSV, la famille d'un plan ; les centiles au rang le
 * plus proche, les SLI d'un JTL, le verdict nommé ; et la forme des plans générés (un XML JMeter
 * valide, chaque endpoint présent, les écritures sous garde).
 *
 *   node --test perf/jmeter/jmeter.test.cjs
 */
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { classeDe, substituer, famille, planFamille, gabarits, scenarios, operations, COLONNES, FLUX, HORS_BALAYAGE, corpsJson, ordonnerEcritures, dureeBalayage } = require("./generer-plans.cjs");
const { decouper, centile, indicateurs, verdict, analyser, lireJtl, couvertureUtile } = require("./analyser.cjs");
const { recapituler } = require("./recapituler.cjs");
const { accord, lireSli } = require("./accord.cjs");
const J = require("./jmx.cjs");

test("la classe SLO d'une route : santé, connexion, rapport avant écriture, écriture avant lecture", () => {
  assert.equal(classeDe("GET", "/api/health"), "sante");
  assert.equal(classeDe("POST", "/api/auth/login"), "connexion");
  assert.equal(classeDe("GET", "/api/payroll/projects/{id}/export.{format}"), "rapport");
  assert.equal(classeDe("POST", "/api/payroll/reports/summary"), "rapport");
  assert.equal(classeDe("POST", "/api/payroll/employees"), "ecriture");
  assert.equal(classeDe("DELETE", "/api/payroll/tasks/{id}"), "ecriture");
  assert.equal(classeDe("GET", "/api/payroll/employees"), "lecture");
});

test("les paramètres de chemin prennent la colonne de leur entité, sinon un jeton neutre", () => {
  assert.equal(substituer("/api/communication/conversations/{id}/messages"), "/api/communication/conversations/${conversationId}/messages");
  assert.equal(substituer("/api/communication/listes-distribution/{id}"), "/api/communication/listes-distribution/${listeId}");
  assert.equal(substituer("/api/communication/messages/{id}/lu"), "/api/communication/messages/${messageId}/lu");
  assert.equal(substituer("/api/communication/groupes/{id}/membres"), "/api/communication/groupes/${uniteCode}/membres");
  assert.equal(substituer("/api/communication/conversations/{id}/participants/{personId}"), "/api/communication/conversations/${conversationId}/participants/x");
  assert.equal(substituer("/api/exploitation/export.{format}"), "/api/exploitation/export.csv");
  assert.equal(substituer("/api/saas/vues/{role}"), "/api/saas/vues/employee");
  assert.equal(substituer("/api/approvals/requests/{id}"), "/api/approvals/requests/${id}");
  assert.equal(substituer("/api/effectifs/journal/{dateDebut}"), "/api/effectifs/journal/${date}");
  assert.equal(famille("/api/effectifs/postes"), "effectifs");
  assert.equal(famille("/public/annuaire"), "public");
});

test("un plan de famille est un XML JMeter avec chaque endpoint, les écritures sous garde, le corps du contrat", () => {
  const routes = [{ methode: "get", chemin: "/api/x/liste" }, { methode: "post", chemin: "/api/x/liste" }, { methode: "delete", chemin: "/api/x/liste/{id}" }, { methode: "get", chemin: "/api/auth/sso/saml/login" }];
  const xml = planFamille("x", routes, { "post /api/x/liste": { nom: "a" } });
  assert.ok(!xml.includes("/api/auth/sso/saml/login"), "le départ d'une fédération ne se balaye pas");
  assert.equal(FLUX.size, 0, "ce produit ne sert aucun flux d'événements");
  assert.ok(HORS_BALAYAGE.has("POST /api/auth/login") && HORS_BALAYAGE.has("POST /api/auth/logout"), "la connexion et la déconnexion ne se balayent pas");
  assert.ok(xml.includes('name="ThreadGroup.on_sample_error">stoptest<'), "une connexion qui échoue arrête le plan");
  assert.ok(xml.startsWith('<?xml version="1.0" encoding="UTF-8"?>'));
  assert.ok(xml.includes('jmeter="5.6.3"'));
  assert.ok(xml.includes('testname="GET /api/x/liste"'));
  assert.ok(xml.includes('testname="POST /api/x/liste"'));
  assert.ok(xml.includes('testname="DELETE /api/x/liste/{id}"'));
  assert.ok(xml.includes("&quot;nom&quot;:&quot;a&quot;"), "le corps du contrat, échappé");
  assert.ok(xml.includes("IfController"), "les écritures sous garde");
  assert.ok(xml.indexOf("IfController") < xml.indexOf('testname="POST /api/x/liste"'));
  assert.ok(xml.includes("SetupThreadGroup"), "la connexion en setUp");
  assert.ok(xml.includes("${__P(hote,localhost)}"), "l'hôte vient des propriétés");
  const ouvertures = (xml.match(/<hashTree>/g) ?? []).length; const fermetures = (xml.match(/<\/hashTree>/g) ?? []).length;
  assert.equal(ouvertures, fermetures, "arbre équilibré");
  assert.equal((xml.match(/<hashTree\/>/g) ?? []).length, 0);
});

test("les opérations de l'accord : un plan par opération, débit constant partagé, unités et durée de l'accord déclarées dans l'environnement du plan", () => {
  const ops = operations();
  // **Une relation, pas un nombre écrit en dur.** Un compte figé devient faux à chaque opération
  // ajoutée au catalogue, et on le corrige alors sans regarder ce qu'il garde. Ce qu'on veut
  // savoir, c'est qu'AUCUNE opération du catalogue ne reste sans plan — une opération déclarée
  // à l'accord de niveau de service et jamais mesurée est la pire des deux situations.
  const catalogue = JSON.parse(fs.readFileSync(path.join(__dirname, "operations.json"), "utf8")).operations;
  assert.ok(catalogue.length >= 10, "le catalogue est lu");
  assert.deepEqual(
    catalogue.map((o) => `${o.nom}.jmx`).filter((f) => !ops[f]), [],
    "chaque opération du catalogue a son plan",
  );
  assert.equal(Object.keys(ops).length, catalogue.length);
  const xml = ops["LireMesModules.jmx"];
  assert.ok(xml.includes("ConstantThroughputTimer"));
  assert.ok(xml.includes('name="calcMode">2<'), "débit partagé par toutes les unités");
  assert.ok(xml.includes("${__jexl3(${debit} * 60)}"), "JMeter compte par minute");
  assert.ok(xml.includes('ThreadGroup.duration">${duree_operation}<'));
  // Une variable du groupe absente de l'environnement du plan vaut chaîne vide : « Invalid duration 0 » (trouvé au premier lancement).
  for (const v of ["debit", "unites_operation", "duree_operation"]) assert.ok(xml.includes(`\${__P(${v},`), `${v} déclarée`);
  assert.ok(xml.includes('testname="LireMesModules"'), "le libellé est le nom de l'opération");
  assert.ok(ops["Connexion.jmx"].includes("Comptes (CSV)") && !ops["Connexion.jmx"].includes("SetupThreadGroup"), "la connexion se mesure elle-même, sans setUp");
  assert.ok(ops["PointerUneEntree.jmx"].includes('name="Assertion.test_type">8<'), "une écriture exige son code");
});

test("gabarits et scénarios : un fragment par verbe, un plan vierge, trois parcours", () => {
  const g = gabarits();
  assert.deepEqual(Object.keys(g).sort(), ["DELETE.jmx", "GET.jmx", "PATCH.jmx", "POST.jmx", "PUT.jmx", "plan-vierge.jmx"]);
  assert.ok(g["POST.jmx"].includes("JSONPostProcessor"), "le POST extrait l'identifiant créé");
  assert.ok(g["GET.jmx"].includes("TestFragmentController"));
  assert.ok(g["plan-vierge.jmx"].includes("Mon scénario"));
  const s = scenarios();
  assert.deepEqual(Object.keys(s).sort(), ["parcours-employe.jmx", "parcours-gestionnaire.jmx", "sonde-lecture.jmx"]);
  assert.ok(s["parcours-employe.jmx"].includes("/api/effectifs/presence/pointages"));
  assert.ok(s["parcours-gestionnaire.jmx"].includes("${periodeId}"));
  assert.ok(!s["sonde-lecture.jmx"].includes("IfController"), "la sonde ne contient aucune écriture");
  assert.equal(J.P("x", "1"), "${__P(x,1)}");
});

test("centiles au rang le plus proche, découpage CSV avec guillemets, SLI et verdict nommé", () => {
  assert.equal(centile([], 95), 0);
  assert.equal(centile([100, 200, 300, 400, 500, 600, 700, 800, 900, 1000], 95), 1000);
  assert.equal(centile([100, 200, 300, 400, 500, 600, 700, 800, 900, 1000], 50), 500);
  assert.equal(centile([100, 200, 300, 400, 500, 600, 700, 800, 900, 1000], 99), 1000);
  assert.deepEqual(decouper('1,2,"a, b","dit ""x""",true'), ["1", "2", "a, b", 'dit "x"', "true"]);
  const obs = [{ horodatage: 0, elapsed: 100, succes: true }, { horodatage: 500, elapsed: 300, succes: true }, { horodatage: 1000, elapsed: 900, succes: false }, { horodatage: 1500, elapsed: 200, succes: true }];
  const sli = indicateurs(obs);
  assert.equal(sli.nombre, 4); assert.equal(sli.erreurs, 1); assert.equal(sli.erreursPct, 25); assert.equal(sli.p50, 200); assert.equal(sli.p95, 900); assert.equal(sli.max, 900);
  assert.ok(sli.debit > 1 && sli.debit < 3);
  const classe = { classe: "lecture", p95_ms: 500, p99_ms: 1000, erreurs_pct: 0.1 };
  assert.deepEqual(verdict(sli, classe, { p95_ms: 2000 }), ["p95 900 ms > SLO 500 ms", "erreurs 25.00 % > SLO 0.1 %"]);
  assert.deepEqual(verdict({ p95: 100, p99: 200, erreursPct: 0 }, classe, { p95_ms: 2000 }), []);
  assert.deepEqual(verdict({ p95: 2500, p99: 2600, erreursPct: 0 }, { classe: "rapport", p95_ms: 3000, p99_ms: 4000, erreurs_pct: 1 }, { p95_ms: 2000 }), ["p95 2500 ms > SLA 2000 ms"]);
});

test("l'analyse d'un JTL écrit RESULTATS.md, nomme les manqués d'abord, et refuse un JTL sans colonnes", () => {
  const dossier = fs.mkdtempSync(path.join(os.tmpdir(), "jtl-"));
  const jtl = path.join(dossier, "r.jtl");
  const lignes = ["timeStamp,elapsed,label,responseCode,responseMessage,threadName,dataType,success,failureMessage,bytes,sentBytes,grpThreads,allThreads,URL,Latency,IdleTime,Connect"];
  for (let i = 0; i < 20; i++) lignes.push(`${1000 + i * 100},${50 + i},GET /api/health,200,OK,g 1-1,text,${i === 3 ? "false" : "true"},${i === 3 ? '"The operation lasted too long: It took 53 milliseconds, but should not have lasted longer than 50 milliseconds."' : ""},10,10,1,1,http://x,1,0,1`);
  for (let i = 0; i < 20; i++) lignes.push(`${1000 + i * 100},${800 + i * 10},POST /api/payroll/employees,${i === 0 ? 500 : 201},Created,g 1-1,text,${i === 0 ? "false" : "true"},${i === 0 ? '"réponse 5xx, erreur du serveur"' : ""},10,10,1,1,http://x,1,0,1`);
  fs.writeFileSync(jtl, lignes.join("\n") + "\n");
  assert.equal(lireJtl(jtl).length, 40);
  const r = analyser(jtl, { env: "local", sortie: dossier, date: "2026-09-16 04:00" });
  assert.equal(r.requetes, 2);
  assert.equal(r.manquesRequetes, 1, "la requête trop longue n'est pas une erreur : la santé reste ✅");
  const md = fs.readFileSync(r.fichierMd, "utf8");
  assert.ok(md.includes("# Résultats de performance — local — 2026-09-16 04:00"));
  assert.ok(md.includes("❌ 1 requête(s) hors objectif"));
  assert.ok(md.indexOf("`POST /api/payroll/employees`") < md.indexOf("`GET /api/health`"), "les manqués d'abord");
  assert.ok(md.includes("erreurs 5.00 % > SLO 0.5 %"));
  // Le récapitulatif lit le dossier parent : une ligne par sous-dossier de plan.
  const recap = recapituler(path.dirname(dossier));
  assert.ok(recap.includes("| Plan | Endpoints |"));
  assert.ok(recap.includes(`| ${path.basename(dossier)} | 2 | 40 | 1 (2.50 %) |`), recap);
  assert.ok(recap.includes("❌ `POST /api/payroll/employees`"));
  fs.writeFileSync(jtl, "a,b\n1,2\n");
  assert.throws(() => analyser(jtl, { sortie: dossier }), /JTL sans les colonnes/);
  fs.writeFileSync(jtl, "timeStamp,elapsed,label,responseCode,success\n");
  assert.throws(() => analyser(jtl, { sortie: dossier }), /aucune observation/);
});

test("l'accord de niveau de service lit statistics.json de JMeter, rend SLO/SLI/SLA/Notes par opération, et ne conclut rien sans mesure", () => {
  const racine = fs.mkdtempSync(path.join(os.tmpdir(), "accord-"));
  const rapports = path.join(racine, "rapports", "local", "20260916-000000");
  fs.mkdirSync(path.join(rapports, "LireMesModules", "tableau-de-bord"), { recursive: true });
  fs.writeFileSync(path.join(rapports, "LireMesModules", "tableau-de-bord", "statistics.json"), JSON.stringify({
    LireMesModules: { transaction: "LireMesModules", sampleCount: 12000, errorCount: 0, errorPct: 0, meanResTime: 3.4, pct1ResTime: 4, pct2ResTime: 6, pct3ResTime: 9, throughput: 20.01 },
    Total: { transaction: "Total", sampleCount: 12001, errorPct: 0, meanResTime: 3.5, pct2ResTime: 6, pct3ResTime: 9, throughput: 19.9 },
  }));
  const sli = lireSli(rapports, "LireMesModules");
  assert.equal(sli.moyen, 3.4); assert.equal(sli.p95, 6); assert.equal(sli.echantillons, 12000); assert.equal(sli.dureeS, null, "sans JTL, pas de durée");
  assert.equal(lireSli(rapports, "Connexion"), null);
  const md = accord({ typique: rapports, volumineux: undefined, env: "local", date: "2026-09-16" });
  assert.ok(md.includes("## 3. LireMesModules"));
  assert.ok(md.includes("### Objectifs de niveau de service (SLO)") && md.includes("### Indicateurs de niveau de service (SLI)") && md.includes("### Accords de niveau de service (SLA)") && md.includes("### Notes"));
  assert.ok(md.includes("| Temps réponse moyen (contexte typique) | ≤ 20 ms | 3 ms | ✅ |"), "cible du catalogue : 20 ms en moyenne");
  assert.ok(md.includes("« Response Times (ms) – 95th pct » : **6 ms** (typique), **—** (volumineux)"));
  assert.ok(md.includes("| Débit | 20 / seconde | 20.0 / — |"));
  // Une opération non mesurée : « — » partout, jamais un ✅ gratuit.
  const connexion = md.slice(md.indexOf("## 1. Connexion"), md.indexOf("## 2. ObtenirCatalogueAvis"));
  assert.ok(!connexion.includes("✅"), connexion);
  assert.ok(md.includes("| Connexion | — | — | — | — | — | — |"));
});

test("les paramètres du module des effectifs prennent la colonne de leur route, sans toucher aux autres familles", () => {
  // Le préfixe exact départage `/effectifs/echanges` et `/effectifs/echanges/ressources`, qui
  // commencent au même caractère : la règle du « dernier segment d'entité trouvé » aurait tranché
  // au hasard de l'ordre de tri.
  assert.equal(substituer("/api/effectifs/planification/periodes/{id}/comblement"),
    "/api/effectifs/planification/periodes/${periodeId}/comblement");
  assert.equal(substituer("/api/effectifs/echanges/ressources/{id}/quarts"),
    "/api/effectifs/echanges/ressources/${ressourceId}/quarts");
  assert.equal(substituer("/api/effectifs/echanges/{id}/valider"),
    "/api/effectifs/echanges/${echangeId}/valider");
  assert.equal(substituer("/api/effectifs/referentiels/{type}/{code}/historique"),
    "/api/effectifs/referentiels/${typeRef}/${codeRef}/historique");
  assert.equal(substituer("/api/effectifs/presence/profils/{code}"),
    "/api/effectifs/presence/profils/${profilCode}");
  // Les autres familles ne bougent pas : elles gardent la colonne générique.
  assert.equal(substituer("/api/approvals/rules/{id}"), "/api/approvals/rules/${id}");
});

test("l'ordre des colonnes du CSV est celui que le plan déclare, moisCourant compris", () => {
  // Une colonne écrite sans être déclarée décale **toutes** les suivantes, sans erreur : c'est
  // ainsi que `periodeId` a reçu « 2026-11 » et que 78 routes ont répondu 404.
  assert.equal(COLONNES[3], "moisCourant");
  assert.equal(new Set(COLONNES).size, COLONNES.length, "aucune colonne en double");
  const plan = planFamille("effectifs", [{ methode: "get", chemin: "/api/effectifs/planification/periodes/{id}/comblement" }], {});
  assert.ok(plan.includes(COLONNES.join(",")), "le plan déclare les colonnes dans l'ordre de COLONNES");
});

test("la couverture utile distingue une campagne mesurée d'une campagne de 404", () => {
  const u = couvertureUtile(new Map([
    ["GET /a", [{ code: "200" }, { code: "200" }, { code: "404" }]],
    ["GET /b", [{ code: "404" }, { code: "404" }]],
    ["POST /c", [{ code: "201" }]],
    ["POST /d", [{ code: "422" }]],
  ]));
  // Le code **dominant** décide : une requête qui répond 200 deux fois sur trois a travaillé.
  assert.deepEqual(u.lignes.map((l) => [l.libelle, l.dominant, l.utile]), [
    ["GET /a", "200", true], ["GET /b", "404", false], ["POST /c", "201", true], ["POST /d", "422", false],
  ]);
  assert.deepEqual([u.utiles, u.total, u.pct], [2, 4, 50]);
});

test("le rapport nomme les requêtes mesurées sur un refus, et leur code", () => {
  const dossier = fs.mkdtempSync(path.join(os.tmpdir(), "jmeter-utile-"));
  const jtl = path.join(dossier, "r.jtl");
  fs.writeFileSync(jtl, [
    "timeStamp,elapsed,label,responseCode,success,failureMessage",
    "1700000000000,3,GET /api/effectifs/x,200,true,",
    "1700000000100,2,GET /api/effectifs/y/{id},404,true,",
  ].join("\n"));
  const r = analyser(jtl, { env: "local-node", sortie: dossier, date: "2026-11-01 14:00" });
  const md = fs.readFileSync(r.fichierMd, "utf8");
  assert.deepEqual([r.couverture.utiles, r.couverture.total], [1, 2]);
  assert.match(md, /## Couverture utile/);
  assert.match(md, /1 des 2 requêtes \(50 %\) répondent 2xx/);
  // Nommer la requête et son code : « la couverture a baissé » n'est pas exploitable.
  assert.match(md, /GET \/api\/effectifs\/y\/\{id\}` \| 404/);
  fs.rmSync(dossier, { recursive: true, force: true });
});

test("les corps de balayage : un nombre tiré par JMeter reste un nombre, une variable reste une chaîne", () => {
  assert.equal(corpsJson({ annee: "${__Random(2030,2999)}", unite: "${uniteCode}" }), '{"annee":${__Random(2030,2999)},"unite":"${uniteCode}"}');
  assert.equal(corpsJson(undefined), "{}");
  // Chaque corps de balayage est un objet, et chaque variable qu'il nomme est une colonne du CSV ou une fonction JMeter.
  const { corps } = JSON.parse(fs.readFileSync(path.join(__dirname, "corps-balayage.json"), "utf8"));
  for (const [route, c] of Object.entries(corps)) {
    assert.equal(typeof c, "object", route);
    for (const [, nom] of JSON.stringify(c).matchAll(/\$\{([A-Za-z_]+)/g)) {
      assert.ok(nom.startsWith("__") || COLONNES.includes(nom), `${route} : variable « ${nom} » absente du CSV`);
    }
  }
});

test("un identifiant d'unité ou de fiche prend la colonne semée", () => {
  assert.equal(substituer("/api/communication/acheminement/unites/{uniteId}"), "/api/communication/acheminement/unites/${uniteCode}");
  assert.equal(substituer("/api/personnel/employes/{id}"), "/api/personnel/employes/${employeId}");
});

test("un geste destructeur se joue après les autres, et un balayage dure assez pour atteindre ses écritures", () => {
  const r = (methode, chemin) => ({ methode, chemin });
  const ordre = ordonnerEcritures([r("post", "/api/x/{id}/archiver"), r("delete", "/api/x/{id}"), r("post", "/api/x/{id}/messages"), r("put", "/api/x/{id}")]);
  assert.deepEqual(ordre.map((x) => `${x.methode} ${x.chemin}`), ["post /api/x/{id}/messages", "put /api/x/{id}", "post /api/x/{id}/archiver", "delete /api/x/{id}"]);
  assert.equal(dureeBalayage(153), 183);
  assert.match(planFamille("x", [r("get", "/api/x"), r("post", "/api/x")], {}), /\$\{__P\(duree_balayage,32\)\}/);
});

test("un identifiant vide d'un jeu semé prend la valeur d'un autre jeu", () => {
  const { completerLesVides } = require("./preparer.cjs");
  assert.deepEqual(completerLesVides([{ a: "1", b: "" }, { a: "", b: "" }, { a: "3", b: "x" }]), [{ a: "1", b: "x" }, { a: "1", b: "x" }, { a: "3", b: "x" }]);
});

test("les disponibilités se déposent sur la période en collecte, pas sur la période publiée", () => {
  assert.equal(substituer("/api/effectifs/disponibilites/moi/{periodeId}"), "/api/effectifs/disponibilites/moi/${periodeCollecteId}");
  assert.equal(substituer("/api/effectifs/planification/periodes/{id}/etat"), "/api/effectifs/planification/periodes/${periodeId}/etat");
});

test("l'API d'échange (AD-06) : une opération machine obtient son jeton client_credentials, sans connexion de compte", () => {
  const ops = operations();
  const plan = ops["LireCorrectifsParApi.jmx"];
  assert.ok(plan, "plan de l'opération machine généré");
  assert.match(plan, /\/api\/oauth\/token/);
  assert.match(plan, /grant_type&quot;:&quot;client_credentials/);
  assert.match(plan, /\$\.access_token/);
  assert.doesNotMatch(plan, /\/api\/auth\/login/, "aucune session humaine sur une route machine");
  assert.match(ops["LireQuartsParApi.jmx"], /du=\$\{debutPeriode\}&amp;au=\$\{dateFuture\}/);
  for (const c of ["clientId", "matricule"]) assert.ok(COLONNES.includes(c), `colonne ${c} déclarée`);
  // Le CSV est versionné : le secret du client n'y entre jamais, il passe en propriété.
  assert.ok(!COLONNES.includes("clientSecret"));
  assert.match(plan, /__P\(clientSecret,\)/);
  // Les écritures en dernier : une écriture rejouée 600 s contaminerait les lectures qui la suivraient.
  const catalogue = require("./operations.json").operations.map((o) => o.nom);
  assert.equal(catalogue.at(-1), "TransmettreSoldesParApi");
  assert.ok(catalogue.indexOf("LireCorrectifsParApi") < catalogue.indexOf("PointerUneEntree"));
  // Le formulaire (RFC 6749) reste disponible aux plans : il porte son propre en-tête de type.
  const formulaire = J.document("d", [J.requete("t", "POST", "/x", { formulaire: { grant_type: "client_credentials" } })]);
  assert.match(formulaire, /application\/x-www-form-urlencoded/);
  assert.match(formulaire, /Argument\.name">grant_type</);
});
