#!/usr/bin/env node
/**
 * **Prépare les données d'une campagne** : sème, par l'API de l'environnement visé, l'organisme
 * d'effectifs (unité, corps d'emploi, milieu, ressource, poste, période publiée, quarts, feuille,
 * absence, pointage, échange) et une conversation de la communication, puis écrit leurs
 * identifiants dans `donnees/identifiants.csv` — la source des paramètres de chemin des plans.
 * Sans données, la mesure flatte : une liste vide répond toujours vite.
 *
 *   node perf/jmeter/preparer.cjs --env local            # lit env/local.properties (hôte, port, comptes, tenant)
 *   node perf/jmeter/preparer.cjs --env dev --lignes 5   # cinq jeux d'identifiants (les unités tournent dessus)
 *   node perf/jmeter/preparer.cjs --env local --profil volumineux   # le journal d'avis chargé de l'accord
 *
 * Interdit en production (le fichier prod.properties dit ecritures=false) : la sonde ne sème rien.
 */
const fs = require("fs");
const path = require("path");

const ICI = __dirname;

function proprietes(env) {
  const fichier = path.join(ICI, "env", `${env}.properties`);
  if (!fs.existsSync(fichier)) throw new Error(`environnement inconnu : ${env}`);
  return Object.fromEntries(fs.readFileSync(fichier, "utf8").split("\n").filter((l) => l && !l.startsWith("#") && l.includes("=")).map((l) => { const i = l.indexOf("="); return [l.slice(0, i).trim(), l.slice(i + 1).trim()]; }));
}

function lireCsv(fichier) {
  const [entete, ...lignes] = fs.readFileSync(fichier, "utf8").split(/\r?\n/).filter(Boolean);
  const cles = entete.split(",");
  return lignes.map((l) => Object.fromEntries(l.split(",").map((v, i) => [cles[i], v])));
}

/**
 * Pause entre deux appels (`PERF_PAUSE_MS`, défaut 0). Un frontal déployé limite le débit par adresse (20 requêtes/s
 * sur geps, réponse 503 au-delà) : semer sans pause s'y heurtait au milieu d'un jeu, et la campagne partait sur des
 * identifiants à moitié créés. En local, sur le Java nu, aucune limite : aucune pause.
 */
const PAUSE_MS = Number(process.env.PERF_PAUSE_MS ?? 0);
const attendre = (ms) => new Promise((ok) => setTimeout(ok, ms));

async function client(base, compte) {
  const r = await fetch(`${base}/api/auth/login`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ username: compte.username, password: compte.password, tenantId: compte.tenantId || undefined }) });
  if (!r.ok) throw new Error(`connexion refusée (${r.status}) pour ${compte.username}`);
  const { token } = await r.json();
  if (!token) throw new Error("connexion sans jeton (choix d'établissement ou seconde étape exigés) : utilisez un compte de campagne sans MFA");
  const appelant = async (methode, chemin, corps, tenant) => {
    if (PAUSE_MS > 0) await attendre(PAUSE_MS);
    const rep = await fetch(`${base}${chemin}`, { method: methode, headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}`, ...(tenant ? { "X-Tenant-Id": tenant } : {}) }, body: corps ? JSON.stringify(corps) : undefined });
    const texte = await rep.text();
    let json = null; try { json = JSON.parse(texte); } catch { json = null; }
    if (!rep.ok) throw new Error(`${methode} ${chemin} → ${rep.status} ${texte.slice(0, 200)}`);
    return json;
  };
  // La base, pour les appels qui ne passent pas par la session (jeton machine).
  return Object.assign(appelant, { base });
}

/** Un jeu d'identifiants : chaque entité créée par la route que les plans exercent ensuite. */
async function semer(appel, tenant, n) {
  const weekDate = new Date().toISOString().slice(0, 10);
  // Le mois de la consommation d'avis : celui du jour. Un mois codé en dur finit par ne plus rien
  // contenir, et l'agrégat répond alors vite pour la mauvaise raison.
  const moisCourant = weekDate.slice(0, 7);
  const effectifs = await semerEffectifs(appel, tenant, n);
  const communication = await semerCommunication(appel, tenant, n);
  const echanges = await semerApiEchanges(appel, tenant, effectifs, n);
  return { weekDate, date: weekDate, id: effectifs.ressourceId, moisCourant, ...effectifs, ...communication, ...echanges };
}

/**
 * **L'API d'échange de l'organisme** (26-015-DO, addenda 5 ; AD-06) : un client d'API (portées read et write), puis un
 * employé transmis **par l'API elle-même**, avec le jeton client_credentials — la préparation éprouve l'écriture machine
 * avant que la campagne ne la mesure. Le secret n'est écrit que dans le CSV local de la campagne (non versionné).
 */
async function semerApiEchanges(appel, tenant, effectifs, n) {
  const client = await appel("POST", "/api/integrations/api-clients", { name: `Charge API ${n}`, scopes: ["read", "write"] }, tenant);
  const jeton = await jetonMachine(appel.base, client.clientId, client.clientSecret);
  const matricule = `API${Date.now().toString(36).toUpperCase().slice(-5)}${n}`;
  const r = await fetch(`${appel.base}/api/echanges/v1/employes/${matricule}`, { method: "PUT",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${jeton}` },
    body: JSON.stringify({ nom: `Employé transmis ${n}`, uniteCode: effectifs.uniteCode, corpsEmploiCode: effectifs.corpsEmploiCode, anciennete: 3 }) });
  // Un serveur d'avant AD-06b n'a pas l'écriture des employés : la campagne de comparaison n'en mesure que les lectures.
  if (r.status === 404 || r.status === 405) {
    console.log(`⚠ écriture des employés absente de ce serveur (${r.status}) : TransmettreSoldesParApi ne se mesure pas ici`);
    gardeSecret(client.clientSecret);
    return { clientId: client.clientId, matricule: "" };
  }
  if (r.status !== 201) throw new Error(`PUT /api/echanges/v1/employes/${matricule} → ${r.status} ${(await r.text()).slice(0, 200)}`);
  gardeSecret(client.clientSecret);
  return { clientId: client.clientId, matricule };
}

/**
 * Le secret du client d'API ne va jamais dans `donnees/identifiants.csv`, qui est versionné : il est écrit à part, dans un
 * fichier que git ignore, et la campagne le passe à JMeter par `-JclientSecret`.
 */
function gardeSecret(secret) {
  const env = process.argv.includes("--env") ? process.argv[process.argv.indexOf("--env") + 1] : "local";
  fs.writeFileSync(path.join(ICI, "donnees", `secret-client-${env}.txt`), `${secret}\n`, { mode: 0o600 });
}

/**
 * Le jeton d'une application : POST /api/oauth/token en formulaire, comme le veut la RFC 6749 (§ 4.4). Un serveur
 * d'avant l'ADR 0088 ne connaît que le JSON (415) : la préparation s'y replie, pour qu'une campagne de comparaison
 * puisse viser l'ancienne version.
 */
async function jetonMachine(base, clientId, clientSecret) {
  const champs = { grant_type: "client_credentials", client_id: clientId, client_secret: clientSecret };
  let r = await fetch(`${base}/api/oauth/token`, { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: new URLSearchParams(champs) });
  if (r.status === 415) r = await fetch(`${base}/api/oauth/token`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(champs) });
  if (!r.ok) throw new Error(`POST /api/oauth/token → ${r.status} ${(await r.text()).slice(0, 200)}`);
  return (await r.json()).access_token;
}

/**
 * **Le volume des correctifs** (`--quarts S`) : S semaines de plus, générées puis publiées, et chaque place attribuée —
 * des centaines d'entrées au journal des quarts. Sans elles, la lecture des correctifs mesure une page de trois lignes
 * et ne dirait rien de son coût par entrée.
 */
async function semerVolumeQuarts(appel, tenant, ligne, semaines) {
  const debut = new Date(`${ligne.dateFuture}T12:00:00Z`);
  let entrees = 0;
  let releves = 0;
  const motifs = new Set();
  // La séparation des tâches refuse qu'on approuve ce qu'on a soumis : une directrice de l'organisme approuve.
  const approbatrice = await approbatriceDeCampagne(appel, tenant, ligne.uniteCode);
  for (let k = 1; k <= semaines; k++) {
    const jour = (d) => { const x = new Date(debut); x.setUTCDate(x.getUTCDate() + 7 * (k + 4) + d); return x.toISOString().slice(0, 10); };
    const periode = await appel("POST", "/api/effectifs/planification/periodes", { nom: `Volume ${k}`, uniteCode: ligne.uniteCode, debut: jour(0), fin: jour(6) }, tenant).catch(() => null);
    if (!periode) continue;
    await appel("POST", `/api/effectifs/planification/periodes/${periode.id}/etat`, { etat: "CONFECTION" }, tenant);
    await appel("POST", `/api/effectifs/planification/periodes/${periode.id}/generer`, {}, tenant);
    const equipages = await appel("GET", `/api/effectifs/planification/periodes/${periode.id}/equipages`, null, tenant).catch(() => []);
    for (const e of Array.isArray(equipages) ? equipages : []) {
      entrees += 1;
      for (const p of e.places ?? []) {
        const ok = await appel("POST", `/api/effectifs/planification/places/${p.id}/attribuer`, { ressourceId: ligne.ressourceId }, tenant).then(() => true).catch(() => false);
        if (ok) entrees += 1;
      }
    }
    await appel("POST", `/api/effectifs/planification/periodes/${periode.id}/etat`, { etat: "PUBLIEE" }, tenant);
    releves += await approuverFeuilles({ soumettre: appel, approuver: approbatrice }, tenant, { uniteCode: ligne.uniteCode, debut: jour(0), fin: jour(6) }, motifs);
  }
  for (const m of motifs) console.log(`⚠ feuilles non approuvées : ${m}`);
  return { entrees, releves };
}

/**
 * Les feuilles de la semaine, préparées, soumises puis approuvées jusqu'au dernier palier : ce sont elles que l'API rend
 * comme relevés validés. Sans elles, la lecture des relevés serait mesurée sur une page vide.
 */
async function approuverFeuilles({ soumettre: appel, approuver }, tenant, { uniteCode, debut, fin }, motifs = new Set()) {
  // Un refus n'est pas avalé : son motif est gardé, et la préparation le dit — un semis qui n'approuve rien
  // ferait mesurer la lecture des relevés sur une page vide.
  const noter = (e) => { motifs.add(e.message.slice(0, 160)); return null; };
  const feuilles = await appel("POST", "/api/effectifs/temps/feuilles/preparer", { uniteCode, debut, fin }, tenant).catch(noter);
  let approuvees = 0;
  for (const id of [...(feuilles?.creees ?? []), ...(feuilles?.existantes ?? [])]) {
    await appel("POST", `/api/effectifs/temps/feuilles/${id}/soumettre`, {}, tenant).catch(noter);
    for (let palier = 0; palier < PALIERS_MAX; palier++) {
      const f = await approuver("POST", `/api/effectifs/temps/feuilles/${id}/approuver`, {}, tenant).catch(noter);
      if (!f || f.statut === "APPROUVEE") { approuvees += f ? 1 : 0; break; }
    }
  }
  return approuvees;
}

/**
 * Une directrice de l'organisme, créée par l'administration, sans le mur de première connexion (comme les essais e2e),
 * avec un périmètre de gestion sur l'unité ; son mot de passe est tiré au hasard et ne quitte pas ce processus.
 */
async function approbatriceDeCampagne(appel, tenant, uniteCode) {
  const username = `approbatrice-${Date.now().toString(36)}`;
  const password = `Ap-${require("crypto").randomBytes(12).toString("base64url")}9!`;
  const cree = await appel("POST", "/api/auth/users", { username, password, role: "director", mustChangePassword: false }, tenant);
  await appel("PATCH", `/api/auth/users/${cree.id}`, { securitySetupRequired: false }, tenant);
  // Sans périmètre sur l'unité, les feuilles lui sont invisibles (404) : elle gère l'unité et ses sous-unités.
  await appel("POST", "/api/effectifs/perimetres",
    { utilisateurId: cree.id, uniteCode, etendue: "SOUS_ARBRE", niveau: "GESTION", depuis: new Date().toISOString().slice(0, 10) }, tenant);
  return client(appel.base, { username, password, tenantId: tenant });
}

/** Les paliers d'approbation d'une feuille vont de 1 à 3 (paramètres du temps) : au-delà, la boucle s'arrête. */
const PALIERS_MAX = 3;

/**
 * **Une conversation et une liste de diffusion** : sans elles, les routes de la communication qui
 * portent un identifiant seraient balayées en 404. La conversation réunit le compte de campagne et
 * les comptes du système (sans doublon : le serveur refuse un participant répété), nommés par leur **nom de connexion** — l'identité de la messagerie.
 */
async function semerCommunication(appel, tenant, n) {
  const vide = { conversationId: "", messageId: "", listeId: "" };
  const moi = await appel("GET", "/api/auth/me", null, tenant).catch(() => null);
  const nom = moi?.username;
  if (!nom) return vide;
  const conversation = await appel("POST", "/api/communication/conversations",
    { sujet: `Charge ${n}`, type: "groupe", participants: [...new Set([nom, "admin", "superadmin"])] }, tenant).catch(() => null);
  const message = conversation
    ? await appel("POST", `/api/communication/conversations/${conversation.id}/messages`, { corps: `Message de charge ${n}` }, tenant).catch(() => null)
    : null;
  const liste = await appel("POST", "/api/communication/listes-distribution",
    { nom: `Liste de charge ${n}`, membres: [{ nature: "personne", cible: "admin" }] }, tenant).catch(() => null);
  return { conversationId: conversation?.id ?? "", messageId: message?.id ?? "", listeId: liste?.id ?? "" };
}

/**
 * **L'organisme d'effectifs de l'avis 26-015-DO** : unité, corps d'emploi, milieu, ressources,
 * postes publiés, période générée, place attribuée, absence décidée, pointage, feuille de temps,
 * règle, campagne annuelle, échange, profil de pointage.
 *
 * Pourquoi tout cela : **84 des 148 routes du module portent un paramètre de chemin**. Sans ces
 * identifiants, les plans les balayaient avec le jeton neutre « x » — donc en 404. Un 404 répond
 * en deux millisecondes : la campagne donnait le module de l'avis pour le plus rapide du produit,
 * alors qu'il n'était pas mesuré du tout.
 *
 * Chaque création est tolérante (`catch`) et rend une chaîne vide : une route dont l'identifiant
 * manque reste balayée en 404, et l'analyse le montre — mieux qu'une campagne qui s'arrête.
 */
async function semerEffectifs(appel, tenant, n) {
  const suffixe = `${Date.now().toString(36)}-${n}`.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(-6);
  const vide = {
    periodeId: "", placeId: "", ressourceId: "", segmentId: "", uniteCode: "", typeRef: "unites", codeRef: "",
    absenceEffectifId: "", campagneId: "", remplacementId: "", echangeId: "", milieuEffId: "", posteEffId: "",
    equipageId: "", regleId: "", feuilleId: "", pointageId: "", quotaId: "", perimetreId: "", messageEsbId: "",
    connecteurCode: "ESB", relaisCode: "ESB", profilCode: "", fluxRegleId: "",
  };
  const MODULES = ["effectifs", "effectifs_planification", "effectifs_regles", "effectifs_agph",
    "effectifs_absences", "effectifs_echanges", "effectifs_disponibilites", "effectifs_presence",
    "effectifs_temps", "effectifs_integrations", "effectifs_communication"];
  const ouverts = await appel("GET", "/api/saas/my-modules", null, tenant).catch(() => null);
  if (ouverts && !(ouverts.modules ?? []).includes("effectifs_planification")) {
    const cible = tenant || ouverts.tenantId;
    const voulus = [...new Set([...(ouverts.modules ?? []), ...MODULES])];
    await appel("PUT", `/api/saas/tenants/${cible}/modules`, { modules: voulus }, tenant).catch(() => null);
  }
  const ref = (type, corps) => appel("POST", `/api/effectifs/referentiels/${type}`,
    { effectifDepuis: "2026-01-01", ...corps }, tenant).catch(() => null);
  const unite = `U${suffixe}`;
  if (!(await ref("unites", { code: unite, libelleFr: `Unité de charge ${n}` }))) return vide;
  const corpsEmploi = `C${suffixe}`;
  await ref("corps_emploi", { code: corpsEmploi, libelleFr: "Paramédics de charge" });
  await ref("statuts_emploi", { code: `TC${suffixe}`, libelleFr: "Temps complet", comportement: "TEMPS_COMPLET" });
  await ref("natures_quart", { code: `R${suffixe}`, libelleFr: "Régulier", comportement: "REGULIER" });
  await ref("natures_quart", { code: `A${suffixe}`, libelleFr: "Ad hoc", comportement: "AD_HOC" });
  await ref("types_milieu", { code: `CA${suffixe}`, libelleFr: "Caserne", comportement: "CASERNE" });
  await ref("plages_horaires", { code: `J${suffixe}`, libelleFr: "Jour", attributs: { debut: 360, fin: 1080 } });
  for (const [code, libelleFr, comportement] of [["REG", "Régulier", "TRAVAILLEE"], ["PNP", "Pause", "PAUSE_NON_PAYEE"],
    ["PREA", "Préalable", "PRESENCE_PREALABLE"], ["HS", "Supplémentaires", "SUPPLEMENTAIRE"]]) {
    await ref("types_heure", { code, libelleFr, comportement, attributs: { codePaie: code } });
  }
  await ref("codes_absence", { code: "MAL", libelleFr: "Maladie", comportement: "PAYE",
    attributs: { duree: "PONCTUELLE", planifiable: false } });
  await ref("codes_absence", { code: "LONG", libelleFr: "Invalidité", comportement: "PAYE",
    attributs: { duree: "LONG_TERME", planifiable: true } });
  const poser = (chemin, corps) => appel("POST", chemin, corps, tenant).catch(() => null);
  const milieu = await poser("/api/effectifs/milieux",
    { code: `M${suffixe}`, libelle: `Caserne de charge ${n}`, typeCode: `CA${suffixe}`, uniteCode: unite });
  const moi = await appel("GET", "/api/auth/whoami", null, tenant).catch(() => null);
  const ressource = await poser("/api/effectifs/planification/ressources", {
    matricule: `P${suffixe}`, nom: `Ressource de charge ${n}`, utilisateurId: moi?.utilisateur?.id,
    corpsEmploiCode: corpsEmploi, uniteCode: unite, statutEmploiCode: `TC${suffixe}`, anciennete: 100,
  });
  // Le cycle part d'un lundi passé : la période couvre aujourd'hui, donc les quarts générés
  // existent et le comblement mesure un vrai calcul.
  const lundi = new Date();
  lundi.setDate(lundi.getDate() - ((lundi.getDay() + 6) % 7) - 7);
  const jour = (d) => { const x = new Date(lundi); x.setDate(x.getDate() + d); return x.toISOString().slice(0, 10); };
  const poste = await poser("/api/effectifs/planification/postes", {
    code: `PO${suffixe}`, libelle: `Poste de charge ${n}`, uniteCode: unite, statutEmploiCode: `TC${suffixe}`,
    corpsEmploiCode: corpsEmploi, milieuId: milieu?.id, natureCode: `R${suffixe}`,
    cycle: [[`J${suffixe}`, `J${suffixe}`, `J${suffixe}`, `J${suffixe}`, `J${suffixe}`, null, null]],
    debutCycle: jour(0),
  });
  if (poste) await poser("/api/effectifs/planification/postes/publier", { ids: [poste.id] });
  const periode = await poser("/api/effectifs/planification/periodes",
    { nom: `Période de charge ${n}`, uniteCode: unite, debut: jour(0), fin: jour(13) });
  if (periode) {
    await poser(`/api/effectifs/planification/periodes/${periode.id}/etat`, { etat: "CONFECTION" });
    await poser(`/api/effectifs/planification/periodes/${periode.id}/generer`, {});
  }
  const equipages = periode
    ? await appel("GET", `/api/effectifs/planification/periodes/${periode.id}/equipages`, null, tenant).catch(() => null)
    : null;
  const equipage = Array.isArray(equipages) ? equipages[0] : null;
  const place = equipage?.places?.[0] ?? null;
  if (place && ressource) await poser(`/api/effectifs/planification/places/${place.id}/attribuer`, { ressourceId: ressource.id });
  // Une publication de l'horaire : sans elle, la feuille de temps n'a aucun segment, et les
  // routes de segment seraient mesurées en 404.
  if (periode) await poser(`/api/effectifs/planification/periodes/${periode.id}/etat`, { etat: "PUBLIEE" });
  const aujourdHui = new Date().toISOString().slice(0, 10);
  const quota = await poser("/api/effectifs/planification/quotas",
    { uniteCode: unite, plageCode: `J${suffixe}`, natureCode: `R${suffixe}`, quota: 1, depuis: aujourdHui });
  // Un profil de pointage se **pose** (PUT, idempotent), il ne se crée pas : son code en est la clé.
  const profil = await appel("PUT", "/api/effectifs/presence/profils",
    { code: `PF${suffixe}`, libelle: "Profil de charge", mode: "MIXTE", porteeType: "ORGANISME" }, tenant).catch(() => null);
  const feuilles = await poser("/api/effectifs/temps/feuilles/preparer", { uniteCode: unite, debut: jour(0), fin: jour(6) });
  const feuilleId = feuilles?.creees?.[0] ?? feuilles?.existantes?.[0] ?? "";
  const regenere = feuilleId
    ? await poser(`/api/effectifs/temps/feuilles/${feuilleId}/regenerer`, {})
    : null;
  const detail = regenere ?? (feuilleId
    ? await appel("GET", `/api/effectifs/temps/feuilles/${feuilleId}`, null, tenant).catch(() => null)
    : null);
  const campagne = await poser("/api/effectifs/agph/campagnes",
    { uniteCode: unite, corpsEmploiCode: corpsEmploi, annee: new Date().getFullYear() + 1, delaiChoixMinutes: 60 });
  const regle = await poser("/api/effectifs/regles",
    { contexte: "DISPONIBILITE", cle: "minimum-quarts", action: "AVERTIR",
      condition: { fait: "quartsParSemaine", op: "<", valeur: 3 },
      message: "Au moins 3 quarts par semaine ({quartsParSemaine} déclarés)", effectifDepuis: aujourdHui });
  // Un compte de planificateur et son périmètre : l'écran des périmètres écarte les comptes
  // d'administration, qui voient déjà tout.
  const planif = await poser("/api/auth/users",
    { username: `planif-${suffixe.toLowerCase()}`, password: "Passe!Charge2026", role: "manager" });
  const perimetre = planif ? await poser("/api/effectifs/perimetres",
    { utilisateurId: planif.id, uniteCode: unite, etendue: "SOUS_ARBRE", niveau: "GESTION", depuis: aujourdHui }) : null;
  // Une absence déclarée par le compte relié à la ressource : jugée par les règles, enregistrée même refusée.
  const absence = await poser("/api/effectifs/absences", { motif: "MAL", du: aujourdHui });
  const absenceId = absence?.ids?.[0] ?? "";
  // Un pointage et un échange proposé : sans eux, deux familles de routes restent en 404.
  /**
   * Un quart **ad hoc couvrant l'heure courante**, attribué à la ressource du compte : sans lui, le
   * pointage est refusé (« aucun quart publié ne vous attend en ce moment ») et les quatre routes
   * qui portent un identifiant de pointage restent mesurées en 404.
   */
  const minutes = new Date().getHours() * 60 + new Date().getMinutes();
  const maintenant = await poser("/api/effectifs/planification/equipages", {
    periodeId: periode?.id, date: aujourdHui, debut: Math.max(0, minutes - 60),
    fin: Math.min(1439, minutes + 240), natureCode: `A${suffixe}`, milieuId: milieu?.id,
    composition: [{ corpsEmploiCode: corpsEmploi, nombre: 1 }], minimumOperationnel: 1,
  });
  const placeDuJour = maintenant?.places?.[0] ?? null;
  if (placeDuJour && ressource) {
    await poser(`/api/effectifs/planification/places/${placeDuJour.id}/attribuer`, { ressourceId: ressource.id });
  }
  const pointage = placeDuJour
    ? await poser("/api/effectifs/presence/pointages", { placeId: placeDuJour.id, sens: "ENTREE", preuve: { type: "AUCUNE" } })
    : null;
  const complements = await semerComplements(appel, tenant, {
    poser, unite, corpsEmploi, suffixe, jour, periode, equipages, ressource, poste, campagne, aujourdHui,
  });
  const echange = complements.echange;
  // Une fiche au répertoire du personnel : sans elle, la lecture et la correction d'une fiche restent en 404.
  const employe = await poser("/api/personnel/employes", { firstName: "Fiche", lastName: `De charge ${n}`, email: `charge-${n}@exemple.qc.ca` });
  return {
    ...vide, uniteCode: unite, typeRef: "unites", codeRef: unite, employeId: employe?.id ?? "",
    milieuEffId: milieu?.id ?? "", ressourceId: ressource?.id ?? "", posteEffId: poste?.id ?? "",
    periodeId: periode?.id ?? "", equipageId: equipage?.id ?? "",
    // `placeId` est la place **en cours**, celle que le pointage peut atteindre ; à défaut, la première.
    placeId: placeDuJour?.id ?? place?.id ?? "",
    quotaId: quota?.versions?.[0]?.id ?? "", profilCode: profil ? `PF${suffixe}` : "",
    feuilleId, segmentId: detail?.segments?.[0]?.id ?? "",
    campagneId: campagne?.id ?? "", regleId: regle?.id ?? "",
    perimetreId: perimetre?.id ?? "", absenceEffectifId: absenceId,
    pointageId: pointage?.id ?? "", echangeId: echange?.id ?? "",
    corpsEmploiCode: corpsEmploi, statutEmploiCode: `TC${suffixe}`, natureCode: `R${suffixe}`,
    plageCode: `J${suffixe}`, typeMilieuCode: `CA${suffixe}`,
    ...complements.colonnes,
  };
}

/**
 * Des envois dans les journaux (`sms_message`, `courriel_message`).
 *
 * **Sans eux, la consommation d'avis mesure une table vide** et répond en quelques millisecondes
 * — un chiffre rassurant qui ne dit rien. Or c'est la lecture qui croît le plus vite du produit :
 * l'historique des envois ne se purge pas, et l'écran somme les segments de tous les organismes
 * pour le mois. C'est précisément ce volume qu'il faut mesurer.
 *
 * Les textos partent par l'API, donc par le **simulateur** : aucun envoi réel, aucun coût. Un
 * environnement où le fournisseur serait configuré enverrait pour de vrai — d'où le refus
 * explicite ci-dessous plutôt qu'une surprise sur la facture.
 */
/**
 * **Ce qui manquait aux balayages** : chaque bloc sème l'état que l'essai HTTP Java correspondant
 * monte avant d'appeler la route (`backend-java/src/test/java/.../effectifs/infrastructure/web/`).
 * Sans lui, la route est mesurée sur un refus — un 404 ou un 409 qui répond vite et ne mesure rien.
 */
async function semerComplements(appel, tenant, c) {
  const { poser, unite, corpsEmploi, suffixe, jour, periode, equipages, ressource, poste, campagne, aujourdHui } = c;
  const demain = new Date(Date.now() + 86_400_000).toISOString();
  // Échange (`ControleurEchangesEffectifsHttpTest`) : deux quarts **à venir** d'une période publiée,
  // l'un à la ressource du compte, l'autre à une partenaire de la même unité ; le compte propose, la
  // cible vient de « demandables ». La période semée finit cette semaine : une semaine suivante, à part,
  // porte l'échange (le cycle du poste ne travaille que du lundi au vendredi).
  const partenaire = await poser("/api/effectifs/planification/ressources", {
    matricule: `Q${suffixe}`, nom: `Partenaire de charge`, corpsEmploiCode: corpsEmploi, uniteCode: unite,
    statutEmploiCode: `TC${suffixe}`, anciennete: 50,
  });
  const suivante = await poser("/api/effectifs/planification/periodes", { nom: `Semaine d'échange`, uniteCode: unite, debut: jour(14), fin: jour(20) });
  if (suivante) {
    await poser(`/api/effectifs/planification/periodes/${suivante.id}/etat`, { etat: "CONFECTION" });
    await poser(`/api/effectifs/planification/periodes/${suivante.id}/generer`, {});
  }
  const futurs = suivante ? await appel("GET", `/api/effectifs/planification/periodes/${suivante.id}/equipages`, null, tenant).catch(() => null) : null;
  const aVenir = (Array.isArray(futurs) ? futurs : []).filter((e) => e.date > aujourdHui && e.places?.length);
  const cedee = aVenir[0]?.places?.[0] ?? null;
  const voulue = aVenir[1]?.places?.[0] ?? null;
  if (cedee && ressource) await poser(`/api/effectifs/planification/places/${cedee.id}/attribuer`, { ressourceId: ressource.id });
  if (voulue && partenaire) await poser(`/api/effectifs/planification/places/${voulue.id}/attribuer`, { ressourceId: partenaire.id });
  if (suivante) await poser(`/api/effectifs/planification/periodes/${suivante.id}/etat`, { etat: "PUBLIEE" });
  const demandables = cedee ? await appel("GET", `/api/effectifs/echanges/places/${cedee.id}/demandables`, null, tenant).catch(() => null) : null;
  const cible = Array.isArray(demandables) ? demandables[0]?.placeId : null;
  const echange = cedee && cible ? await poser("/api/effectifs/echanges", { placeCedeeId: cedee.id, placeVoulueId: cible }) : null;
  // Remplacement long terme sur un poste vacant (`ControleurAgphEffectifsHttpTest`, l. 508 et 606).
  const remplacement = poste ? await poser("/api/effectifs/agph/remplacements",
    { posteId: poste.id, origine: "POSTE_VACANT", debut: jour(21), fin: jour(27), finManifestation: demain }) : null;
  // Intégrations (`ControleurIntegrationsEffectifsHttpTest`) : un connecteur inactif répond 409 à tout geste.
  const connecteur = (code, extra = {}) => appel("PUT", `/api/effectifs/integrations/connecteurs/${code}`, { actif: true, ...extra }, tenant).catch(() => null);
  for (const code of ["PFT", "SAAV", "GRAPH", "ESB", "ENTREPOT"]) await connecteur(code);
  await connecteur("RAO", { jeuEssai: [{ cle: `r${suffixe}`, matricule: `P${suffixe}`, sens: "ENTREE", horodatage: new Date().toISOString() }] });
  await connecteur("BD_CORPO", { jeuEssai: [{ matricule: `P${suffixe}`, anciennete: 12.5 }] });
  const dateEquipage = (Array.isArray(equipages) ? equipages : [])[0]?.date ?? jour(0);
  const message = await poser("/api/effectifs/integrations/equipages", { date: dateEquipage, uniteCode: unite });
  // Règle de flux (`ControleurTempsFluxEffectifsHttpTest`) : une condition qui ne s'applique jamais,
  // pour que l'approbation des feuilles ne change pas pendant la mesure.
  const flux = await poser("/api/effectifs/temps/flux", { objet: "POINTAGE", palier: 9, champ: "uniteCode", operateur: "eq",
    valeur: "AUCUNE", approbateur: { type: "ROLE", valeur: "director" } });
  // Disponibilités (`ControleurDisponibilitesEffectifsHttpTest`) : une période en collecte, après la période publiée.
  const collecte = await poser("/api/effectifs/planification/periodes", { nom: `Collecte de charge`, uniteCode: unite, debut: jour(21), fin: jour(27) });
  if (collecte) await poser(`/api/effectifs/planification/periodes/${collecte.id}/etat`, { etat: "COLLECTE", finCollecte: jour(20) });
  // AGPH : la campagne ouverte au dépôt reçoit les semaines types et ses paramètres.
  if (campagne) await poser(`/api/effectifs/agph/campagnes/${campagne.id}/ouvrir`, {});
  return {
    echange,
    colonnes: {
      remplacementId: remplacement?.id ?? "", messageEsbId: message?.id ?? "", fluxRegleId: flux?.id ?? "",
      debutPeriode: periode ? jour(0) : "", dateEquipage, periodeCollecteId: collecte?.id ?? "", dateCollecte: jour(21),
      dateFuture: jour(21), finManifestation: demain, placeEchangeId: cedee?.id ?? "", placePartenaireId: cible ?? "",
    },
  };
}

/**
 * **Les identifiants vides d'une ligne prennent la valeur d'une autre ligne.** Les trois jeux sont
 * semés dans le même organisme : une absence le même jour, une règle de même clé sont refusées au
 * second jeu, et la colonne restait vide — le chemin devenait `/absences//annuler`, mesuré en 405.
 */
function completerLesVides(lignes) {
  for (const colonne of new Set(lignes.flatMap((l) => Object.keys(l)))) {
    const valeur = lignes.map((l) => l[colonne]).find((v) => v);
    if (valeur) for (const l of lignes) if (!l[colonne]) l[colonne] = valeur;
  }
  return lignes;
}

async function semerJournauxDAvis(appel, tenant, { textos = 2000 } = {}) {
  const etat = await appel("GET", "/api/notifications/sms/etat", null, tenant).catch(() => null);
  if (etat && etat.simule === false) {
    throw new Error(`le fournisseur de textos est « ${etat.fournisseur} » : semer 2 000 textos enverrait pour de vrai. Poser SMS_FOURNISSEUR= (simulateur) avant de semer.`);
  }
  // Le quota par défaut refuserait après mille segments : on l'ouvre le temps de la mesure.
  await appel("PUT", "/api/notifications/sms/quota", { limite: 0 }, tenant).catch(() => null);
  let envoyes = 0;
  for (let n = 0; n < textos; n++) {
    // Un message sur trois bascule en UCS-2 (« ça ») : le journal porte alors des segments
    // multiples, comme en production, et la somme mesurée n'est pas une simple numération.
    const texte = n % 3 === 0 ? `ça commence à 8 h — avis ${n}` : `Avis de charge ${n}`;
    const r = await appel("POST", "/api/notifications/sms", { numero: "+15145550101", texte }, tenant).catch(() => null);
    if (r) envoyes++;
    if ((n + 1) % 500 === 0) console.log(`  ${n + 1} textos…`);
  }
  return { textos: envoyes };
}

/**
 * **L'établissement de la campagne.** Sur une base neuve, l'établissement nommé par la propriété
 * `tenant` n'existe pas : les routes gardées par un module répondent « tenant not found », et le
 * semoir repartait à vide sans le dire. On le crée donc, avec les modules de l'avis, et on écrit
 * son identifiant à côté des données — la campagne se lance ensuite avec `-Jtenant=<id>`.
 *
 * Si l'établissement existe déjà (pile Docker, environnement partagé), rien n'est créé : on ne
 * touche pas à un environnement qu'on n'a pas fabriqué.
 */
const MODULES_AVIS = ["approvals", "notifications", "effectifs", "effectifs_planification", "effectifs_regles",
  "effectifs_agph", "effectifs_absences", "effectifs_echanges", "effectifs_disponibilites",
  "effectifs_presence", "effectifs_temps", "effectifs_integrations", "effectifs_communication"];

async function etablissementDeCampagne(appel, env, tenantVoulu) {
  // L'existence se lit dans la liste publique des organismes : `my-modules` ne dit rien d'un organisme pour un compte
  // système (il ignore X-Tenant-Id et rend une liste vide), et chaque campagne en créait donc un de plus.
  const publics = await fetch(`${appel.base}/api/auth/tenants`).then((r) => (r.ok ? r.json() : [])).catch(() => []);
  const existe = !!tenantVoulu && Array.isArray(publics) && publics.some((t) => t.id === tenantVoulu);
  let tenant = tenantVoulu;
  if (!existe) {
    const cree = await appel("POST", "/api/saas/tenants",
      { name: `Charge ${new Date().toISOString().slice(0, 16)}`, type: "public_institution", country: "CA", region: "QC", planCode: "enterprise" }, "");
    tenant = cree.id;
    console.log(`établissement créé pour la campagne : ${tenant}`);
  }
  const ouverts = (await appel("GET", "/api/saas/my-modules", null, tenant).catch(() => null))?.modules ?? [];
  const voulus = [...new Set([...ouverts, ...MODULES_AVIS])];
  if (voulus.length !== ouverts.length) {
    await appel("PUT", `/api/saas/tenants/${tenant}/modules`, { modules: voulus }, tenant);
  }
  fs.writeFileSync(path.join(ICI, "donnees", `tenant-${env}.txt`), `${tenant}\n`);
  return tenant;
}

async function main() {
  const args = process.argv.slice(2);
  const option = (nom, defaut) => { const i = args.indexOf(`--${nom}`); return i >= 0 ? args[i + 1] : defaut; };
  const env = option("env", "local");
  const p = proprietes(env);
  if (p.ecritures === "false") throw new Error(`${env} : ecritures=false — cet environnement ne se sème pas`);
  // PERF_URL : l'adresse de l'environnement GitHub visé (variable APP_URL) l'emporte sur l'hôte du profil de charge —
  // le profil dit COMBIEN charger, l'environnement dit OÙ ; ainsi un même profil sert sur OVH comme sur Azure.
  const base = (process.env.PERF_URL || `${p.protocole}://${p.hote}:${p.port}`).replace(/\/+$/, "");
  const compte = lireCsv(path.join(ICI, p.comptes))[0];
  const appel = await client(base, compte);
  // PERF_TENANT : l'établissement d'une campagne précédente, que le pipeline retrouve par son nom (« Charge … ») — sur
  // un environnement partagé, une campagne par jour ne doit pas laisser un établissement de plus à chaque fois.
  const tenant = compte.tenantId ? "" : await etablissementDeCampagne(appel, env, process.env.PERF_TENANT || p.tenant || "");
  if (option("profil", "typique") === "volumineux") {
    const v = await semerJournauxDAvis(appel, tenant);
    console.log(`profil volumineux semé : ${v.textos} textos au journal d'avis (${base})`);
    return;
  }
  const lignes = [];
  for (let n = 1; n <= Number(option("lignes", "3")); n++) lignes.push(await semer(appel, tenant, n));
  // L'ordre des colonnes est celui que le plan déclare, et non celui des clés de l'objet : une
  // colonne de plus au mauvais endroit décale toutes les suivantes, sans erreur et sans bruit.
  const { COLONNES } = require("./generer-plans.cjs");
  completerLesVides(lignes);
  const semaines = Number(option("quarts", "0"));
  if (semaines > 0) {
    const v = await semerVolumeQuarts(appel, tenant, lignes[0], semaines);
    console.log(`volume de l'API d'échange : ${v.entrees} entrées de quarts, ${v.releves} relevés approuvés (${semaines} semaines)`);
  }
  const inattendues = Object.keys(lignes[0]).filter((c) => !COLONNES.includes(c));
  if (inattendues.length > 0) throw new Error(`colonne(s) semée(s) mais non déclarée(s) dans COLONNES de generer-plans.cjs : ${inattendues.join(", ")} — le CSV décalerait les variables du plan`);
  const colonnes = COLONNES.filter((c) => c in lignes[0]);
  fs.writeFileSync(path.join(ICI, p.identifiants), [colonnes.join(","), ...lignes.map((l) => colonnes.map((c) => l[c]).join(","))].join("\n") + "\n");
  console.log(`${lignes.length} jeu(x) d'identifiants → ${p.identifiants} (${base})`);
  // Les identifiants d'effectifs comptent : sans eux, 84 des 148 routes du module seraient
  // balayées en 404. Les nommer ici évite une campagne verte pour la mauvaise raison.
  const manquants = COLONNES.filter((c) => !lignes[0][c] && c !== "id");
  if (manquants.length > 0) {
    // Dire **combien de routes** sont concernées, et non seulement lesquelles : « 3 colonnes
    // vides » ne se juge pas, « 11 routes mesurées sur un 404 » se juge.
    const routes = require("./plans/routes.json").routes
      .filter((r) => manquants.some((c) => require("./generer-plans.cjs").substituer(r.chemin).includes(`\${${c}}`)));
    console.log(`⚠ ${manquants.length} identifiant(s) sans valeur : ${manquants.join(", ")}`);
    console.log(`  → ${routes.length} route(s) seront mesurées sur un 404 ; le rapport doit le dire.`);
  }
  if (tenant) console.log(`établissement de la campagne : -Jtenant=${tenant}  (aussi dans donnees/tenant-${env}.txt)`);
}

if (require.main === module) main().catch((e) => { console.error(`✗ ${e.message}`); process.exit(1); });
module.exports = { proprietes, lireCsv, semer, semerEffectifs, semerCommunication, semerJournauxDAvis, etablissementDeCampagne, completerLesVides, semerApiEchanges, jetonMachine, semerVolumeQuarts };
