/**
 * **Le vocabulaire JMX** — chaque élément d'un plan JMeter 5.6 écrit une fois, sans dépendance :
 * plan, variables, défauts HTTP, en-têtes, témoins, jeu de données CSV, groupes d'unités (normal,
 * setUp), requête HTTP, extracteur JSON, assertions (code, durée), contrôleur conditionnel, temps
 * de réflexion. Les plans (`generer-plans.cjs`), les gabarits et les scénarios s'écrivent avec ces
 * fonctions : un seul endroit sait à quoi ressemble un `HTTPSamplerProxy`.
 *
 * Tout ce qui dépend de l'environnement passe par `${__P(cle,defaut)}` : la même
 * `.jmx` se joue en local, dev, préprod, prod avec `-q env/<env>.properties`.
 */
const echapper = (v) => String(v ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
const prop = (nom, valeur, type = "stringProp") => `<${type} name="${nom}">${echapper(valeur)}</${type}>`;
const bool = (nom, v) => `<boolProp name="${nom}">${v ? "true" : "false"}</boolProp>`;
const P = (cle, defaut = "") => `\${__P(${cle},${defaut})}`;

/** Le document : un plan et son arbre ; `enfants` = éléments déjà rendus (chacun suivi de son `<hashTree/>`). */
function document(nom, enfants) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="${echapper(nom)}">
      ${bool("TestPlan.functional_mode", false)}
      ${bool("TestPlan.tearDown_on_shutdown", true)}
      ${bool("TestPlan.serialize_threadgroups", false)}
      <elementProp name="TestPlan.user_defined_variables" elementType="Arguments" guiclass="ArgumentsPanel" testclass="Arguments" testname="Variables du plan">
        <collectionProp name="Arguments.arguments"/>
      </elementProp>
      ${prop("TestPlan.user_define_classpath", "")}
    </TestPlan>
    <hashTree>
${enfants.join("\n")}
    </hashTree>
  </hashTree>
</jmeterTestPlan>
`;
}

/** Un élément et ses enfants, rendus ensemble : `<Element/>` puis `<hashTree>…</hashTree>`. */
const noeud = (xml, enfants = []) => `${xml}\n<hashTree>\n${enfants.join("\n")}\n</hashTree>`;

function argument(nom, valeur) {
  return `<elementProp name="${echapper(nom)}" elementType="Argument">${prop("Argument.name", nom)}${prop("Argument.value", valeur)}${prop("Argument.metadata", "=")}</elementProp>`;
}

/** Les variables de l'environnement, lues des propriétés (`-q env/<env>.properties`, `-Jcle=`). */
function variablesEnvironnement() {
  const cles = [["protocole", "http"], ["hote", "localhost"], ["port", "4020"], ["tenant", "default"], ["comptes", "donnees/comptes.csv"], ["identifiants", "donnees/identifiants.csv"],
    ["utilisateurs", "1"], ["montee", "1"], ["duree", "30"], ["reflexion", "0"], ["debit", "20"], ["unites_operation", "10"], ["duree_operation", "600"], ["ecritures", "true"], ["seuil_lecture", "500"], ["seuil_ecriture", "1000"], ["seuil_rapport", "2000"]];
  return noeud(`<Arguments guiclass="ArgumentsPanel" testclass="Arguments" testname="Environnement (propriétés -q env/*.properties)"><collectionProp name="Arguments.arguments">${cles.map(([k, d]) => argument(k, P(k, d))).join("")}</collectionProp></Arguments>`);
}

function defautsHttp() {
  return noeud(`<ConfigTestElement guiclass="HttpDefaultsGui" testclass="ConfigTestElement" testname="Défauts HTTP (hôte, port, protocole de l'environnement)">
      <elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments"><collectionProp name="Arguments.arguments"/></elementProp>
      ${prop("HTTPSampler.domain", "${hote}")}${prop("HTTPSampler.port", "${port}")}${prop("HTTPSampler.protocol", "${protocole}")}${prop("HTTPSampler.contentEncoding", "UTF-8")}
      ${prop("HTTPSampler.connect_timeout", "5000")}${prop("HTTPSampler.response_timeout", "30000")}${prop("HTTPSampler.implementation", "HttpClient4")}
    </ConfigTestElement>`);
}

/** Les en-têtes de chaque requête : JSON, le jeton obtenu par la connexion (propriété partagée), l'établissement. */
function enTetes(avecJeton = true) {
  const lignes = [["Content-Type", "application/json"], ["Accept", "application/json"]];
  if (avecJeton) lignes.push(["Authorization", "Bearer ${__P(jeton,)}"], ["X-Tenant-Id", "${tenant}"]);
  return noeud(`<HeaderManager guiclass="HeaderPanel" testclass="HeaderManager" testname="En-têtes (JSON, jeton, établissement)"><collectionProp name="HeaderManager.headers">${lignes.map(([n, v]) => `<elementProp name="" elementType="Header">${prop("Header.name", n)}${prop("Header.value", v)}</elementProp>`).join("")}</collectionProp></HeaderManager>`);
}

function temoins() {
  return noeud(`<CookieManager guiclass="CookiePanel" testclass="CookieManager" testname="Témoins (session)">${bool("CookieManager.clearEachIteration", false)}${bool("CookieManager.controlledByThreadGroup", false)}</CookieManager>`);
}

/** Un jeu de données CSV partagé par toutes les unités, rejoué en boucle, première ligne = en-têtes. */
function jeuCsv(nom, fichier, variables) {
  return noeud(`<CSVDataSet guiclass="TestBeanGUI" testclass="CSVDataSet" testname="${echapper(nom)}">${prop("filename", fichier)}${prop("fileEncoding", "UTF-8")}${prop("variableNames", variables)}${bool("ignoreFirstLine", true)}${prop("delimiter", ",")}${bool("quotedData", true)}${bool("recycle", true)}${bool("stopThread", false)}${prop("shareMode", "shareMode.all")}</CSVDataSet>`);
}

/** Un groupe d'unités : N utilisateurs, montée, durée ; `setUp` = joué une fois avant les autres. */
function groupe(nom, enfants, { setUp = false, unites = "${utilisateurs}", montee = "${montee}", duree = "${duree}" } = {}) {
  const classe = setUp ? "SetupThreadGroup" : "ThreadGroup";
  const gui = setUp ? "SetupThreadGroupGui" : "ThreadGroupGui";
  const boucles = setUp ? `${prop("LoopController.loops", "1")}${bool("LoopController.continue_forever", false)}` : `${prop("LoopController.loops", "-1")}${bool("LoopController.continue_forever", false)}`;
  return noeud(`<${classe} guiclass="${gui}" testclass="${classe}" testname="${echapper(nom)}">
      ${prop("ThreadGroup.on_sample_error", setUp ? "stoptest" : "continue")}
      <elementProp name="ThreadGroup.main_controller" elementType="LoopController" guiclass="LoopControlPanel" testclass="LoopController">${boucles}</elementProp>
      ${prop("ThreadGroup.num_threads", setUp ? "1" : unites)}${prop("ThreadGroup.ramp_time", setUp ? "1" : montee)}
      ${bool("ThreadGroup.scheduler", !setUp)}${prop("ThreadGroup.duration", setUp ? "" : duree)}${prop("ThreadGroup.delay", "")}
      ${bool("ThreadGroup.same_user_on_next_iteration", true)}
    </${classe}>`, enfants);
}

/** Une requête HTTP ; `corps` (JSON) part tel quel ; les paramètres de requête vont dans le chemin. */
/**
 * Une requête HTTP. `corps` : un corps brut (JSON) ; `formulaire` : des paramètres encodés en
 * `application/x-www-form-urlencoded` (RFC 6749), avec leur propre en-tête de type, qui l'emporte sur celui du plan.
 */
function requete(nom, methode, chemin, { corps = null, formulaire = null, enfants = [] } = {}) {
  if (formulaire !== null) {
    const parametres = Object.entries(formulaire).map(([n, v]) => `<elementProp name="${echapper(n)}" elementType="HTTPArgument">${bool("HTTPArgument.always_encode", true)}${prop("Argument.name", n)}${prop("Argument.value", v)}${bool("HTTPArgument.use_equals", true)}${prop("Argument.metadata", "=")}</elementProp>`).join("");
    const type = noeud(`<HeaderManager guiclass="HeaderPanel" testclass="HeaderManager" testname="En-tête du formulaire"><collectionProp name="HeaderManager.headers"><elementProp name="" elementType="Header">${prop("Header.name", "Content-Type")}${prop("Header.value", "application/x-www-form-urlencoded")}</elementProp></collectionProp></HeaderManager>`);
    return noeud(`<HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="${echapper(nom)}">
      <elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments"><collectionProp name="Arguments.arguments">${parametres}</collectionProp></elementProp>
      ${prop("HTTPSampler.path", chemin)}${prop("HTTPSampler.method", methode)}${bool("HTTPSampler.follow_redirects", false)}${bool("HTTPSampler.auto_redirects", false)}${bool("HTTPSampler.use_keepalive", true)}${bool("HTTPSampler.DO_MULTIPART_POST", false)}
    </HTTPSamplerProxy>`, [type, ...enfants]);
  }
  const args = corps === null
    ? `<elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments"><collectionProp name="Arguments.arguments"/></elementProp>`
    : `${bool("HTTPSampler.postBodyRaw", true)}<elementProp name="HTTPsampler.Arguments" elementType="Arguments"><collectionProp name="Arguments.arguments"><elementProp name="" elementType="HTTPArgument">${bool("HTTPArgument.always_encode", false)}${prop("Argument.value", corps)}${prop("Argument.metadata", "=")}</elementProp></collectionProp></elementProp>`;
  return noeud(`<HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="${echapper(nom)}">
      ${args}
      ${prop("HTTPSampler.path", chemin)}${prop("HTTPSampler.method", methode)}${bool("HTTPSampler.follow_redirects", false)}${bool("HTTPSampler.auto_redirects", false)}${bool("HTTPSampler.use_keepalive", true)}${bool("HTTPSampler.DO_MULTIPART_POST", false)}
    </HTTPSamplerProxy>`, enfants);
}

/** Extrait `$.chemin` de la réponse JSON dans une variable (vide si absent). */
function extracteurJson(variable, cheminJson, defaut = "") {
  return noeud(`<JSONPostProcessor guiclass="JSONPostProcessorGui" testclass="JSONPostProcessor" testname="Extraire ${echapper(variable)}">${prop("JSONPostProcessor.referenceNames", variable)}${prop("JSONPostProcessor.jsonPathExprs", cheminJson)}${prop("JSONPostProcessor.match_numbers", "1")}${prop("JSONPostProcessor.defaultValues", defaut)}</JSONPostProcessor>`);
}

/** Pose une propriété JMeter (visible de tous les groupes) à partir d'une variable : le jeton de la connexion. */
function publierPropriete(propriete, variable) {
  return noeud(`<JSR223PostProcessor guiclass="TestBeanGUI" testclass="JSR223PostProcessor" testname="Publier ${echapper(propriete)}">${prop("scriptLanguage", "groovy")}${prop("script", `props.put("${propriete}", vars.get("${variable}") ?: "")`)}${prop("parameters", "")}${prop("filename", "")}${prop("cacheKey", "true")}</JSR223PostProcessor>`);
}

/**
 * L'assertion de code : la réponse n'est pas une erreur du serveur (5xx) — un 4xx nommé (validation, droit,
 * conflit) reste une réponse valide du balayage. « Ignorer le statut » : sans cela JMeter compte tout
 * 4xx comme un échec avant même de lire l'assertion.
 */
function assertionPasDErreurServeur() {
  return noeud(`<ResponseAssertion guiclass="AssertionGui" testclass="ResponseAssertion" testname="Aucune erreur du serveur (pas de 5xx)"><collectionProp name="Asserion.test_strings"><stringProp name="0">^[1-4]\\d\\d$</stringProp></collectionProp>${prop("Assertion.custom_message", "réponse 5xx : erreur du serveur")}${prop("Assertion.test_field", "Assertion.response_code")}${bool("Assertion.assume_success", true)}<intProp name="Assertion.test_type">1</intProp></ResponseAssertion>`);
}

/** L'assertion de code exact (la connexion doit rendre 200). */
function assertionCode(code) {
  return noeud(`<ResponseAssertion guiclass="AssertionGui" testclass="ResponseAssertion" testname="Code ${code}"><collectionProp name="Asserion.test_strings"><stringProp name="0">${code}</stringProp></collectionProp>${prop("Assertion.custom_message", `code ${code} attendu`)}${prop("Assertion.test_field", "Assertion.response_code")}${bool("Assertion.assume_success", false)}<intProp name="Assertion.test_type">8</intProp></ResponseAssertion>`);
}

/** L'assertion de durée : le SLO de la classe (lecture, écriture, rapport) devient un échec visible. */
function assertionDuree(variableSeuil) {
  return noeud(`<DurationAssertion guiclass="DurationAssertionGui" testclass="DurationAssertion" testname="Durée ≤ SLO (${echapper(variableSeuil)})">${prop("DurationAssertion.duration", `\${${variableSeuil}}`)}</DurationAssertion>`);
}

/** Un contrôleur conditionnel : ses enfants ne jouent que si la condition est vraie (les écritures, hors production). */
function si(condition, nom, enfants) {
  return noeud(`<IfController guiclass="IfControllerPanel" testclass="IfController" testname="${echapper(nom)}">${prop("IfController.condition", condition)}${bool("IfController.evaluateAll", false)}${bool("IfController.useExpression", true)}</IfController>`, enfants);
}

/** Le temps de réflexion entre deux requêtes d'un même utilisateur (ms, propriété `reflexion`). */
function reflexion() {
  return noeud(`<ConstantTimer guiclass="ConstantTimerGui" testclass="ConstantTimer" testname="Temps de réflexion">${prop("ConstantTimer.delay", "${reflexion}")}</ConstantTimer>`);
}

/**
 * Un débit constant, partagé par toutes les unités du groupe : `debit` requêtes par seconde (JMeter compte par
 * minute), quel que soit le nombre d'unités — c'est le « débit fixe » d'un accord de niveau de service.
 */
function debitConstant() {
  return noeud(`<ConstantThroughputTimer guiclass="TestBeanGUI" testclass="ConstantThroughputTimer" testname="Débit fixe (\${debit} requêtes/s, toutes unités)"><intProp name="calcMode">2</intProp>${prop("throughput", "${__jexl3(${debit} * 60)}")}</ConstantThroughputTimer>`);
}

/** Un contrôleur simple : un chapitre nommé du plan. */
function chapitre(nom, enfants) {
  return noeud(`<GenericController guiclass="LogicControllerGui" testclass="GenericController" testname="${echapper(nom)}"/>`, enfants);
}

/** Un fragment (gabarit) : s'ouvre dans JMeter, se copie-colle dans un plan. */
function fragment(nom, enfants) {
  return noeud(`<TestFragmentController guiclass="TestFragmentControllerGui" testclass="TestFragmentController" testname="${echapper(nom)}"/>`, enfants);
}

/** La connexion : POST /api/auth/login avec le compte du CSV, jeton extrait puis publié à tous les groupes. */
function connexion() {
  return groupe("Connexion (setUp : un jeton pour tous)", [
    jeuCsv("Comptes (CSV)", "${comptes}", "username,password,tenantId,role"),
    enTetes(false),
    requete("POST /api/auth/login", "POST", "/api/auth/login", {
      corps: '{"username":"${username}","password":"${password}","tenantId":"${tenantId}"}',
      enfants: [assertionCode(200), extracteurJson("jeton", "$.token"), publierPropriete("jeton", "jeton")],
    }),
  ], { setUp: true });
}

/**
 * La connexion d'une **application** (API d'échange, AD-06) : POST /api/oauth/token avec le client semé par preparer.cjs ;
 * le jeton machine est publié à tous les groupes, comme celui d'une session. Le corps est en JSON, forme que toutes les
 * versions du serveur acceptent : une campagne doit pouvoir comparer une version à la précédente sur le même plan. La
 * forme standard (formulaire, RFC 6749) est éprouvée par `ControleurOAuthHttpTest` et par `preparer.cjs`.
 */
function connexionMachine(colonnes) {
  return groupe("Jeton d'application (setUp : client_credentials)", [
    jeuCsv("Identifiants (CSV)", "${identifiants}", colonnes),
    requete("POST /api/oauth/token", "POST", "/api/oauth/token", {
      corps: '{"grant_type":"client_credentials","client_id":"${clientId}","client_secret":"${__P(clientSecret,)}"}',
      enfants: [assertionCode(200), extracteurJson("jeton", "$.access_token"), publierPropriete("jeton", "jeton")],
    }),
  ], { setUp: true });
}

module.exports = { connexionMachine, document, noeud, variablesEnvironnement, defautsHttp, enTetes, temoins, jeuCsv, groupe, requete, extracteurJson, publierPropriete, assertionPasDErreurServeur, assertionCode, assertionDuree, si, reflexion, debitConstant, chapitre, fragment, connexion, P };
