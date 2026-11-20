/**
 * Le contrat public du service de poussée d'Expo (https://docs.expo.dev/push-notifications/sending-notifications/),
 * reproduit tel quel : forme d'un message, validation, billets (« tickets ») et accusés (« receipts »), erreurs nommées.
 * Le serveur de l'application s'y branche comme au vrai service, par la seule adresse (EXPO_PUSH_URL).
 */

/** Expo refuse une requête de plus de 100 messages. */
export const MESSAGES_PAR_REQUETE = 100;
/** Charge utile au-delà de laquelle APNs et FCM refusent : Expo rend MessageTooBig. */
export const TAILLE_MAX_OCTETS = 4096;

const JETON = /^(ExponentPushToken|ExpoPushToken)\[[A-Za-z0-9_-]+\]$/;
const PRIORITES = ["default", "normal", "high"];
const NIVEAUX = ["active", "critical", "passive", "time-sensitive"];

export const ERREURS = Object.freeze({
  NON_INSCRIT: "DeviceNotRegistered",
  TROP_GROS: "MessageTooBig",
});

/** Un jeton bien formé n'est pas pour autant inscrit : c'est le registre des appareils qui le dit. */
export function estJetonExpo(jeton) {
  return typeof jeton === "string" && JETON.test(jeton);
}

/** Les écarts de forme d'un message, nommés par leur chemin comme le fait Expo (`"[0].priority" must be …`). */
export function ecartsDuMessage(m, i) {
  const ou = (champ) => `"[${i}].${champ}"`;
  if (!m || typeof m !== "object" || Array.isArray(m)) return [`"[${i}]" must be an object`];
  const ecarts = [];
  const destinataires = Array.isArray(m.to) ? m.to : [m.to];
  if (m.to === undefined || destinataires.length === 0 || destinataires.some((t) => typeof t !== "string")) {
    ecarts.push(`${ou("to")} is required and must be a string or an array of strings`);
  }
  for (const champ of ["title", "subtitle", "body", "channelId", "categoryId"]) {
    if (m[champ] !== undefined && m[champ] !== null && typeof m[champ] !== "string") ecarts.push(`${ou(champ)} must be a string`);
  }
  if (m.data !== undefined && (m.data === null || typeof m.data !== "object" || Array.isArray(m.data))) {
    ecarts.push(`${ou("data")} must be an object`);
  }
  if (m.priority !== undefined && !PRIORITES.includes(m.priority)) ecarts.push(`${ou("priority")} must be one of [${PRIORITES.join(", ")}]`);
  if (m.interruptionLevel !== undefined && !NIVEAUX.includes(m.interruptionLevel)) {
    ecarts.push(`${ou("interruptionLevel")} must be one of [${NIVEAUX.join(", ")}]`);
  }
  ecarts.push(...ecartsDuSon(m.sound, ou("sound")));
  return ecarts;
}

function ecartsDuSon(son, ou) {
  if (son === undefined || son === null || typeof son === "string") return [];
  if (typeof son !== "object" || Array.isArray(son)) return [`${ou} must be a string, null or an object`];
  const ecarts = [];
  if (son.critical !== undefined && typeof son.critical !== "boolean") ecarts.push(`${ou}.critical must be a boolean`);
  if (son.volume !== undefined && (typeof son.volume !== "number" || son.volume < 0 || son.volume > 1)) {
    ecarts.push(`${ou}.volume must be a number between 0 and 1`);
  }
  if (son.name !== undefined && son.name !== null && typeof son.name !== "string") ecarts.push(`${ou}.name must be a string`);
  return ecarts;
}

/** La requête entière : un objet ou un tableau d'au plus 100 messages ; rend les messages ou les écarts. */
export function lireRequete(corps) {
  const seul = !Array.isArray(corps);
  const messages = seul ? [corps] : corps;
  if (messages.length === 0) return { ecarts: ['"value" must contain at least 1 items'] };
  if (messages.length > MESSAGES_PAR_REQUETE) return { ecarts: [`"value" must contain less than or equal to ${MESSAGES_PAR_REQUETE} items`] };
  const ecarts = messages.flatMap((m, i) => ecartsDuMessage(m, i));
  return ecarts.length ? { ecarts } : { messages, seul };
}

/** Taille de la charge réellement transmise au fournisseur (titre, corps, données). */
export function tailleCharge(m) {
  return Buffer.byteLength(JSON.stringify({ title: m.title, subtitle: m.subtitle, body: m.body, data: m.data }), "utf8");
}

export function billetErreur(erreur, message) {
  return { status: "error", message, details: { error: erreur } };
}
