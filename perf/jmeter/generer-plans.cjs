#!/usr/bin/env node
/**
 * **Génère les plans JMeter** — un plan par famille d'API couvrant **chaque endpoint** du backend
 * Java (la même table de routes que le Node : la parité est mesurée à 100 % par
 * `contract/outils/etat-migration.cjs`), les gabarits GET/POST/PUT/PATCH/DELETE à copier-coller,
 * et les scénarios de parcours. Rien n'est écrit à la main dans les `.jmx` : ce script est la
 * source, et il se relance quand une route naît.
 *
 *   node perf/jmeter/generer-plans.cjs            # plans/, gabarits/, scenarios/, plans/routes.json
 *   node perf/jmeter/generer-plans.cjs --sans-routeur   # ne relit pas le routeur : plans/routes.json fait foi
 *
 * Les corps des écritures viennent des **contrats DTO** de l'OpenAPI (ADR 0028) — l'exemple que
 * l'API accepte tel quel — ou d'un objet vide quand la route n'a pas de contrat : la requête mesure
 * alors le chemin de validation (400 nommé), jamais une erreur du serveur.
 */
const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");
const J = require("./jmx.cjs");

const ICI = __dirname;
const RACINE = path.resolve(ICI, "..", "..");
const SLO = JSON.parse(fs.readFileSync(path.join(ICI, "slo.json"), "utf8"));

/**
 * Les routes montées et les exemples de corps, lus de la **spécification OpenAPI que sert le Java**
 * (`/api/integrations/openapi.json`) : elle ne décrit que les routes réellement montées, ce qui en
 * fait l'inventaire du balayage. Le serveur visé est celui de la campagne (`PERF_API`, défaut
 * http://localhost:4020) ; on s'y connecte avec le premier compte de `donnees/comptes.csv`.
 */
function lireRouteur() {
  const base = process.env.PERF_API ?? "http://localhost:4020";
  const [entete, premier] = fs.readFileSync(path.join(ICI, "donnees", "comptes.csv"), "utf8").split(/\r?\n/).filter(Boolean);
  const compte = Object.fromEntries(entete.split(",").map((c, i) => [c, premier.split(",")[i]]));
  const script = `
    (async () => {
      const r = await fetch(${JSON.stringify(base)} + "/api/auth/login", { method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: ${JSON.stringify(compte.username)}, password: ${JSON.stringify(compte.password)} }) });
      const { token } = await r.json();
      const s = await fetch(${JSON.stringify(base)} + "/api/integrations/openapi.json", { headers: { Authorization: "Bearer " + token } });
      if (!s.ok) throw new Error("spécification OpenAPI : " + s.status);
      process.stdout.write(await s.text());
    })().catch((e) => { console.error(e.message); process.exit(1); });`;
  const spec = JSON.parse(execFileSync("node", ["-e", script], { maxBuffer: 64 * 1024 * 1024 }).toString("utf8"));
  const routes = [];
  const corps = {};
  for (const [chemin, ops] of Object.entries(spec.paths ?? {})) {
    if (/\/_[A-Za-z]/.test(chemin)) continue;
    for (const [methode, op] of Object.entries(ops)) {
      routes.push({ methode, chemin });
      const ex = op && op.requestBody && op.requestBody.content && op.requestBody.content["application/json"] && op.requestBody.content["application/json"].example;
      if (ex !== undefined && ex !== null && typeof ex === "object" && Object.keys(ex).length > 0) corps[methode + " " + chemin] = ex;
    }
  }
  return { routes, corps: avecCorpsDeBalayage(corps) };
}

/**
 * **Les corps de balayage** (`corps-balayage.json`) : pour une écriture dont le contrat n'a pas
 * d'exemple, un corps que la validation accepte, écrit avec les variables du CSV (`${uniteCode}`…)
 * et des fonctions JMeter pour l'unicité (`${__UUID()}`). Sans lui, la requête mesure le refus de
 * validation — un 400 qui répond vite et ne dit rien du travail réel. L'exemple du contrat, quand il
 * existe, l'emporte : c'est le contrat qui fait foi.
 */
function avecCorpsDeBalayage(corps) {
  const fichier = path.join(ICI, "corps-balayage.json");
  if (!fs.existsSync(fichier)) return corps;
  const { corps: balayage } = JSON.parse(fs.readFileSync(fichier, "utf8"));
  return { ...balayage, ...corps };
}

/** La classe SLO d'une route (première règle qui la reconnaît), et sa variable de seuil. */
function classeDe(methode, chemin) {
  const cle = `${methode.toUpperCase()} ${chemin}`;
  const regle = SLO.classes.find((c) => new RegExp(c.motif).test(cle));
  return regle ? regle.classe : "lecture";
}
const SEUIL = { sante: "seuil_lecture", connexion: "seuil_ecriture", rapport: "seuil_rapport", ecriture: "seuil_ecriture", lecture: "seuil_lecture" };

/** Les colonnes du CSV des identifiants : un paramètre de chemin prend la colonne de son entité. */
// L'ordre est celui du CSV écrit par preparer.cjs : une colonne ajoutée se met à la fin, jamais au milieu.
/**
 * **L'ordre fait foi.** Le plan déclare les noms de variables du CSV dans cet ordre, et le semoir
 * écrit ses colonnes dans le même — c'est `COLONNES` qui les ordonne, pas l'ordre d'un objet
 * JavaScript. Une colonne présente dans le fichier et absente d'ici décale **toutes** les
 * suivantes : `periodeId` recevait « 2026-11 », et 78 routes répondaient 404 pour cette seule
 * raison. Le défaut dormait depuis que `moisCourant` était écrit sans être déclaré ; il était sans
 * effet tant que la colonne était la dernière.
 */
const COLONNES = ["weekDate", "date", "id", "moisCourant",
  // Le module `effectifs` (AO 26-015-DO) : la plupart de ses routes portent un paramètre de chemin.
  // Sans ces colonnes, elles seraient balayées avec le jeton neutre « x » — donc mesurées en 404,
  // et un 404 répond toujours vite.
  "periodeId", "placeId", "ressourceId", "segmentId", "uniteCode", "typeRef", "codeRef",
  "absenceEffectifId", "campagneId", "remplacementId", "echangeId", "milieuEffId", "posteEffId",
  "equipageId", "regleId", "feuilleId", "pointageId", "quotaId", "perimetreId", "messageEsbId",
  "connecteurCode", "relaisCode", "profilCode", "fluxRegleId",
  // La communication : une conversation, ses messages, une liste de diffusion.
  "conversationId", "messageId", "listeId",
  // Les codes du référentiel semé : ce que les corps des écritures (`corps-balayage.json`) nomment.
  "corpsEmploiCode", "statutEmploiCode", "natureCode", "plageCode", "typeMilieuCode",
  // Une fiche du répertoire du personnel.
  "employeId"];

/**
 * Les paramètres du module `effectifs`, reconnus au **préfixe exact** qui précède le paramètre.
 *
 * Pourquoi pas la règle de `ENTITES` : celle-ci retient le dernier segment d'entité *trouvé* dans
 * le préfixe, et `/effectifs/echanges` comme `/effectifs/echanges/ressources` commencent au même
 * caractère — le départage serait un hasard d'ordre de tri. Un préfixe ancré ne se trompe pas, et
 * se relit route par route.
 */
const PARAMS_EFFECTIFS = [
  [/\/effectifs\/absences\/$/, "absenceEffectifId"],
  [/\/effectifs\/agph\/campagnes\/$/, "campagneId"],
  [/\/effectifs\/agph\/remplacements\/$/, "remplacementId"],
  [/\/effectifs\/echanges\/ressources\/$/, "ressourceId"],
  [/\/effectifs\/echanges\/$/, "echangeId"],
  [/\/effectifs\/integrations\/connecteurs\/$/, "connecteurCode"],
  [/\/effectifs\/integrations\/relais\/$/, "relaisCode"],
  [/\/effectifs\/integrations\/messages\/$/, "messageEsbId"],
  [/\/effectifs\/milieux\/$/, "milieuEffId"],
  [/\/effectifs\/perimetres\/$/, "perimetreId"],
  [/\/effectifs\/planification\/periodes\/$/, "periodeId"],
  [/\/effectifs\/planification\/postes\/$/, "posteEffId"],
  [/\/effectifs\/planification\/ressources\/$/, "ressourceId"],
  [/\/effectifs\/planification\/places\/$/, "placeId"],
  [/\/effectifs\/planification\/quotas\/$/, "quotaId"],
  [/\/effectifs\/planification\/equipages\/$/, "equipageId"],
  [/\/effectifs\/presence\/milieux\/$/, "milieuEffId"],
  [/\/effectifs\/presence\/pointages\/$/, "pointageId"],
  [/\/effectifs\/presence\/ressources\/$/, "ressourceId"],
  [/\/effectifs\/presence\/profils\/$/, "profilCode"],
  [/\/effectifs\/referentiels\/[^/]+\/$/, "codeRef"],
  [/\/effectifs\/regles\/$/, "regleId"],
  [/\/effectifs\/temps\/feuilles\/$/, "feuilleId"],
  [/\/effectifs\/temps\/flux\/$/, "fluxRegleId"],
];
const ENTITES = [[/\/communication\/conversations\b/, "conversationId"], [/\/communication\/messages\b/, "messageId"],
  [/\/communication\/listes-distribution\b/, "listeId"], [/\/communication\/groupes\b/, "uniteCode"],
  [/\/personnel\/employes\b/, "employeId"]];

/** `{id}` → `${employeeId}` selon l'entité du chemin ; `{format}` → csv ; un nom de colonne → sa variable ; sinon un jeton neutre. */
function substituer(chemin) {
  return chemin.replace(/\{([A-Za-z0-9_]+)\}/g, (_, nom, position) => {
    const avant = chemin.slice(0, position);
    const effectifs = PARAMS_EFFECTIFS.find(([re]) => re.test(avant));
    if (effectifs && (nom === "id" || nom === "code")) return `\${${effectifs[1]}}`;
    if (nom === "type" && /\/effectifs\/referentiels\/$/.test(avant)) return "${typeRef}";
    if (nom === "id") {
      // L'entité de `{id}` est le dernier segment d'entité qui le précède : `/teachers/{id}/contracts/{contractId}` → teacherId.
      const e = ENTITES.map(([re, colonne]) => ({ colonne, rang: avant.search(re) })).filter((x) => x.rang >= 0).sort((a, b) => b.rang - a.rang)[0];
      return e ? `\${${e.colonne}}` : "${id}";
    }
    if (COLONNES.includes(nom)) return `\${${nom}}`;
    if (nom === "uniteId") return "${uniteCode}";
    if (nom === "format") return "csv";
    if (nom === "role") return "employee";
    if (nom === "lang" || nom === "langue") return "fr";
    if (/date/i.test(nom)) return "${date}";
    return "x";
  });
}

/**
 * Les flux (Server-Sent Events) ne se balayent pas : la réponse ne finit jamais, un thread y resterait
 * jusqu'à la fin de la campagne sans rien mesurer. Ils se mesurent à part (temps du premier événement).
 */
const FLUX = new Set();
/**
 * Hors balayage aussi : la connexion (mesurée par le setUp de chaque plan ; la rejouer en boucle déclenche le
 * pare-feu — 429 puis verrou du compte — et fait tomber les plans suivants) et la déconnexion (elle révoque la
 * session partagée par tous les groupes).
 */
const HORS_BALAYAGE = new Set([...FLUX, "POST /api/auth/login", "POST /api/auth/logout"]);
/** Le départ d'une fédération (SAML, Entra…) redirige vers un fournisseur d'identité externe : 503 sans lui, rien à mesurer. */
const FEDERATION = /^\/api\/auth\/sso\/[^/]+\/login$/;
const balayable = (r) => !HORS_BALAYAGE.has(`${r.methode.toUpperCase()} ${r.chemin}`) && !FEDERATION.test(r.chemin);

/** La famille d'une route : le second segment (`/api/payroll/...` → payroll ; `/public/...` → public). */
function famille(chemin) {
  const m = chemin.match(/^\/(api\/)?([^/]+)/);
  return m ? m[2].replace(/[^a-z0-9-]/gi, "") : "autres";
}

/** Une requête du balayage : nom = « MÉTHODE chemin » (le libellé que l'analyse regroupe), corps du contrat, assertions. */
/** Le corps sérialisé ; un nombre tiré par JMeter (`"${__Random(a,b)}"`) perd ses guillemets pour rester un nombre JSON. */
function corpsJson(corps) {
  return JSON.stringify(corps ?? {}).replace(/"(\$\{__Random\([^)]*\)\})"/g, "$1");
}

function requeteDeRoute(r, corps) {
  const methode = r.methode.toUpperCase();
  const classe = classeDe(methode, r.chemin);
  const enfants = [J.assertionPasDErreurServeur(), J.assertionDuree(SEUIL[classe])];
  const avecCorps = ["POST", "PUT", "PATCH"].includes(methode);
  return J.requete(`${methode} ${r.chemin}`, methode, substituer(r.chemin), { corps: avecCorps ? corpsJson(corps) : null, enfants });
}

const socle = () => [J.variablesEnvironnement(), J.defautsHttp(), J.enTetes(), J.temoins(), J.jeuCsv("Identifiants (CSV)", "${identifiants}", COLONNES.join(",")), J.connexion()];
const gardeEcritures = (enfants) => J.si('${__jexl3("${ecritures}" == "true")}', "Écritures — seulement si ecritures=true (jamais en production)", enfants);

/** Un plan par famille : les lectures, puis les écritures sous garde ; tout endpoint de la famille y est. */
/**
 * **Un geste destructeur** : il retire, ferme ou annule l'entité semée. Joué avant les autres, il
 * les condamnait — archiver la conversation semée faisait refuser l'envoi de message, la réponse et
 * le brouillon qui le suivaient ; supprimer la liste rendait 404 à sa modification.
 */
const DESTRUCTEUR = /\/(archiver|supprimer|fermer|clore|annuler|liberer|refuser|rejeter|retirer|desactiver|revoquer|debrancher|non-comble|raccrocher)$/;
function estDestructeur(r) {
  return r.methode === "delete" || DESTRUCTEUR.test(r.chemin);
}

/** Les écritures, les gestes destructeurs en dernier : l'ordre de l'inventaire est conservé dans chaque groupe. */
function ordonnerEcritures(routes) {
  return [...routes.filter((r) => !estDestructeur(r)), ...routes.filter(estDestructeur)];
}

/**
 * **La durée d'un balayage** : assez pour qu'un utilisateur parcoure tout le plan une fois — la
 * montée, une seconde de réflexion par requête, une marge. À 60 s, le plan des effectifs (64
 * lectures, puis 89 écritures) n'atteignait jamais ses écritures. Surcharge : `-Jduree_balayage=…`.
 */
function dureeBalayage(nombreDeRequetes) {
  const MONTEE_S = 10, REFLEXION_S = 1, MARGE_S = 20;
  return MONTEE_S + nombreDeRequetes * REFLEXION_S + MARGE_S;
}

function planFamille(nom, routes, corps) {
  const lectures = routes.filter(balayable).filter((r) => ["get"].includes(r.methode)).map((r) => requeteDeRoute(r, null));
  const ecritures = ordonnerEcritures(routes.filter(balayable).filter((r) => !["get"].includes(r.methode))).map((r) => requeteDeRoute(r, corps[`${r.methode} ${r.chemin}`]));
  const groupe = J.groupe(`Balayage /api/${nom} (${routes.length} endpoints)`, [
    J.reflexion(),
    ...(lectures.length ? [J.chapitre(`Lectures (${lectures.length})`, lectures)] : []),
    ...(ecritures.length ? [gardeEcritures([J.chapitre(`Écritures (${ecritures.length})`, ecritures)])] : []),
  ], { duree: `\${__P(duree_balayage,${dureeBalayage(lectures.length + ecritures.length)})}` });
  return J.document(`Nexu VINA — endpoints ${nom}`, [...socle(), groupe]);
}

/** Les gabarits : un fragment par verbe, à copier-coller ; et un plan vierge prêt à recevoir des requêtes. */
function gabarits() {
  const corps = { POST: '{"champ":"valeur"}', PUT: '{"champ":"valeur"}', PATCH: '{"champ":"valeur"}' };
  const out = {};
  for (const verbe of ["GET", "POST", "PUT", "PATCH", "DELETE"]) {
    const enfants = [J.assertionPasDErreurServeur(), J.assertionDuree(verbe === "GET" ? "seuil_lecture" : "seuil_ecriture")];
    if (verbe === "POST") enfants.push(J.extracteurJson("idCree", "$.id"));
    const req = J.requete(`${verbe} /api/<module>/<ressource>${verbe === "GET" || verbe === "POST" ? "" : "/${id}"}`, verbe, `/api/<module>/<ressource>${verbe === "GET" || verbe === "POST" ? "" : "/\${id}"}`, { corps: corps[verbe] ?? null, enfants });
    out[`${verbe}.jmx`] = J.document(`Gabarit ${verbe} — à copier-coller`, [J.fragment(`Gabarit ${verbe} : requête + assertions (code, durée SLO)${verbe === "POST" ? " + extraction de l'identifiant créé" : ""}`, [req])]);
  }
  out["plan-vierge.jmx"] = J.document("Gabarit de plan — environnement, défauts, en-têtes, connexion, un groupe vide", [...socle(), J.groupe("Mon scénario (remplacer par les requêtes des gabarits)", [J.reflexion()])]);
  return out;
}

/** Les scénarios : des parcours d'utilisateur, données du CSV, chaînés par les identifiants extraits. */
function scenarios() {
  const lire = (nom, chemin, seuil = "seuil_lecture") => J.requete(`GET ${nom}`, "GET", chemin, { enfants: [J.assertionPasDErreurServeur(), J.assertionDuree(seuil)] });
  const employe = J.document("Urgences-santé — parcours de l'employé", [
    ...socle(),
    J.groupe("Employé : son horaire, ses disponibilités, sa feuille, son pointage", [
      J.reflexion(),
      lire("/api/effectifs/mon-horaire", "/api/effectifs/mon-horaire"),
      lire("/api/effectifs/disponibilites/moi", "/api/effectifs/disponibilites/moi"),
      lire("/api/effectifs/temps/moi", "/api/effectifs/temps/moi"),
      lire("/api/effectifs/absences", "/api/effectifs/absences"),
      gardeEcritures([
        J.requete("POST /api/effectifs/presence/pointages", "POST", "/api/effectifs/presence/pointages",
          { corps: '{"placeId":"${placeId}","sens":"ENTREE","preuve":{"type":"AUCUNE"}}', enfants: [J.assertionPasDErreurServeur(), J.assertionDuree("seuil_ecriture")] }),
      ]),
    ]),
  ]);
  const gestionnaire = J.document("Urgences-santé — parcours du gestionnaire", [
    ...socle(),
    J.groupe("Gestionnaire : comblement, équipages, absences, feuilles de temps", [
      J.reflexion(),
      lire("/api/effectifs/planification/periodes/{id}/comblement", "/api/effectifs/planification/periodes/${periodeId}/comblement", "seuil_rapport"),
      lire("/api/effectifs/planification/periodes/{id}/equipages", "/api/effectifs/planification/periodes/${periodeId}/equipages"),
      lire("/api/effectifs/absences/{id}/impact", "/api/effectifs/absences/${absenceEffectifId}/impact"),
      lire("/api/effectifs/temps/feuilles/{id}", "/api/effectifs/temps/feuilles/${feuilleId}"),
      gardeEcritures([
        J.requete("POST /api/effectifs/temps/feuilles/preparer", "POST", "/api/effectifs/temps/feuilles/preparer",
          { corps: '{"uniteCode":"${uniteCode}","debut":"${weekDate}","fin":"${weekDate}"}', enfants: [J.assertionPasDErreurServeur(), J.assertionDuree("seuil_ecriture")] }),
      ]),
    ]),
  ]);
  const sonde = J.document("Urgences-santé — sonde de lecture (production)", [
    ...socle(),
    J.groupe("Sonde : lectures seules", [
      J.reflexion(),
      lire("/api/saas/my-modules", "/api/saas/my-modules"),
      lire("/api/notifications/evenements", "/api/notifications/evenements"),
      lire("/api/effectifs/referentiels/unites", "/api/effectifs/referentiels/unites"),
      lire("/api/communication/conversations", "/api/communication/conversations"),
    ]),
  ]);
  return { "parcours-employe.jmx": employe, "parcours-gestionnaire.jmx": gestionnaire, "sonde-lecture.jmx": sonde };
}

/**
 * Les opérations de l'accord de niveau de service (`operations.json`) : un plan par opération, une seule requête,
 * jouée à **débit fixe** (`debit` requêtes/s, timer à débit constant partagé) par `unites_operation` unités pendant
 * `duree_operation` secondes — le contexte que l'accord nomme (débit, durée de référence). Le libellé de la requête est
 * le nom de l'opération : c'est lui que le rapport JMeter (`statistics.json`) et `accord.cjs` lisent.
 */
function operations() {
  const catalogue = JSON.parse(fs.readFileSync(path.join(ICI, "operations.json"), "utf8"));
  const out = {};
  for (const op of catalogue.operations) {
    const enfants = [J.assertionPasDErreurServeur()];
    if (op.code) enfants.unshift(J.assertionCode(op.code));
    const requete = J.requete(op.nom, op.methode, op.chemin, { corps: op.corps ?? null, enfants });
    const socleOperation = op.sansJeton
      ? [J.variablesEnvironnement(), J.defautsHttp(), J.enTetes(false), J.jeuCsv("Comptes (CSV)", "${comptes}", "username,password,tenantId,role")]
      : socle();
    const groupe = J.groupe(`Opération ${op.nom} — débit fixe`, [J.debitConstant(), requete], { unites: "${unites_operation}", montee: "1", duree: "${duree_operation}" });
    out[`${op.nom}.jmx`] = J.document(`Accord de niveau de service — ${op.nom}`, [...socleOperation, groupe]);
  }
  return out;
}

function main() {
  const sansRouteur = process.argv.includes("--sans-routeur");
  const fichierRoutes = path.join(ICI, "plans", "routes.json");
  let inventaire;
  if (sansRouteur && fs.existsSync(fichierRoutes)) inventaire = JSON.parse(fs.readFileSync(fichierRoutes, "utf8"));
  else { inventaire = lireRouteur(); fs.mkdirSync(path.dirname(fichierRoutes), { recursive: true }); fs.writeFileSync(fichierRoutes, JSON.stringify(inventaire, null, 1) + "\n"); }
  const { routes, corps } = inventaire;
  const familles = new Map();
  // `/mock/*` double des tiers (Entra, GRICS, SAGIR) pour les démonstrations : ce n'est pas l'API du produit, on ne la mesure pas.
  for (const r of routes.filter((x) => famille(x.chemin) !== "mock")) familles.set(famille(r.chemin), [...(familles.get(famille(r.chemin)) ?? []), r]);
  for (const f of fs.readdirSync(path.join(ICI, "plans"))) if (f.startsWith("endpoints-")) fs.unlinkSync(path.join(ICI, "plans", f));
  for (const [nom, liste] of familles) fs.writeFileSync(path.join(ICI, "plans", `endpoints-${nom}.jmx`), planFamille(nom, liste, corps));
  for (const [nom, xml] of Object.entries(gabarits())) fs.writeFileSync(path.join(ICI, "gabarits", nom), xml);
  for (const [nom, xml] of Object.entries(scenarios())) fs.writeFileSync(path.join(ICI, "scenarios", nom), xml);
  fs.mkdirSync(path.join(ICI, "operations"), { recursive: true });
  const ops = operations();
  for (const [nom, xml] of Object.entries(ops)) fs.writeFileSync(path.join(ICI, "operations", nom), xml);
  console.log(`plans : ${familles.size} familles, ${routes.length} endpoints (${Object.keys(corps).length} corps de contrat) ; gabarits : 6 ; scénarios : 3 ; opérations : ${Object.keys(ops).length}`);
}

if (require.main === module) main();
module.exports = { classeDe, substituer, famille, planFamille, gabarits, scenarios, operations, COLONNES, FLUX, HORS_BALAYAGE, corpsJson, ordonnerEcritures, dureeBalayage };
