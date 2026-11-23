/**
 * Le service de poussée du bac à sable : il répond comme Expo (billets, accusés, erreurs nommées) et remet chaque
 * message aux appareils simulés inscrits, qui le présentent selon les règles d'iOS et d'Android.
 * Pur : l'horloge et la fabrique d'identifiants sont injectées ; l'adaptateur HTTP est dans serveur.mjs.
 */
import { lireRequete, estJetonExpo, tailleCharge, billetErreur, ERREURS, TAILLE_MAX_OCTETS } from "./expo.mjs";
import { lireProfil, versApns, versFcm, presenter } from "./appareils.mjs";

/** Les notifications gardées en mémoire : au-delà, les plus anciennes tombent (un bac à sable ne s'engorge pas). */
export const NOTIFICATIONS_GARDEES = 2000;

export class ServicePoussee {
  #appareils = new Map();
  #notifications = [];
  #accuses = new Map();

  constructor({ jetonAcces = "", horloge = () => new Date(), identifiant }) {
    this.jetonAcces = jetonAcces;
    this.horloge = horloge;
    this.identifiant = identifiant;
  }

  /** POST /--/api/v2/push/send. Rend `{ statut, corps }`, exactement les formes d'Expo. */
  envoyer(corps, autorisation) {
    if (this.jetonAcces && autorisation !== `Bearer ${this.jetonAcces}`) {
      return { statut: 401, corps: { errors: [{ code: "UNAUTHORIZED", message: "The bearer token is invalid." }] } };
    }
    const lu = lireRequete(corps);
    if (lu.ecarts) return { statut: 400, corps: { errors: lu.ecarts.map((message) => ({ code: "VALIDATION_ERROR", message })) } };
    const billets = lu.messages.flatMap((m) => (Array.isArray(m.to) ? m.to : [m.to]).map((to) => this.#remettre({ ...m, to })));
    return { statut: 200, corps: { data: lu.seul && billets.length === 1 ? billets[0] : billets } };
  }

  #remettre(m) {
    const appareil = this.#appareils.get(m.to);
    if (!estJetonExpo(m.to) || !appareil) {
      return billetErreur(ERREURS.NON_INSCRIT, `"${m.to}" is not a registered push notification recipient`);
    }
    if (tailleCharge(m) > TAILLE_MAX_OCTETS) {
      return billetErreur(ERREURS.TROP_GROS, `Message exceeds the ${TAILLE_MAX_OCTETS} byte limit`);
    }
    const id = this.identifiant();
    const fournisseur = appareil.plateforme === "ios" ? { nom: "apns", ...versApns(m) } : { nom: "fcm", message: versFcm(m) };
    this.#notifications.push({ id, recueLe: this.horloge().toISOString(), jeton: m.to, plateforme: appareil.plateforme,
      message: m, fournisseur, presentation: presenter(appareil, m) });
    if (this.#notifications.length > NOTIFICATIONS_GARDEES) this.#notifications.shift();
    this.#accuses.set(id, { status: "ok" });
    return { status: "ok", id };
  }

  /** POST /--/api/v2/push/getReceipts : les identifiants inconnus sont omis, comme chez Expo. */
  accuses(corps) {
    const ids = corps && Array.isArray(corps.ids) ? corps.ids : null;
    if (!ids) return { statut: 400, corps: { errors: [{ code: "VALIDATION_ERROR", message: '"ids" is required' }] } };
    return { statut: 200, corps: { data: Object.fromEntries(ids.filter((id) => this.#accuses.has(id)).map((id) => [id, this.#accuses.get(id)])) } };
  }

  /** L'application est installée (ou réglée) sur un appareil : son jeton devient joignable. */
  inscrire(jeton, profil) {
    if (!estJetonExpo(jeton)) return { statut: 400, corps: { errors: ["jeton : ExponentPushToken[…] attendu"] } };
    const lu = lireProfil(jeton, profil);
    if (lu.ecarts) return { statut: 400, corps: { errors: lu.ecarts } };
    this.#appareils.set(jeton, lu.appareil);
    return { statut: 200, corps: lu.appareil };
  }

  /** L'application est désinstallée : la prochaine poussée rend DeviceNotRegistered. */
  desinscrire(jeton) {
    return this.#appareils.delete(jeton) ? { statut: 204, corps: null } : { statut: 404, corps: { errors: ["appareil inconnu"] } };
  }

  appareils() {
    return { statut: 200, corps: [...this.#appareils.values()] };
  }

  /** Les notifications reçues, les plus récentes d'abord, filtrées par jeton et par titre (contient). */
  notifications({ jeton, titre } = {}) {
    const choisies = this.#notifications.filter((n) => (!jeton || n.jeton === jeton) && (!titre || String(n.message.title ?? "").includes(titre)));
    return { statut: 200, corps: choisies.reverse() };
  }

  vider() {
    this.#notifications = [];
    this.#accuses.clear();
    return { statut: 204, corps: null };
  }
}
