/**
 * Ce que deviennent un message Expo chez Apple (APNs) et chez Google (FCM), puis sur le téléphone. Les règles
 * reproduites sont celles des systèmes, documentées :
 *
 *  - iOS : une alerte critique (`interruption-level: critical`, son `critical: 1`) ne sonne en mode silencieux et en
 *    « Ne pas déranger » que si l'application porte le droit `com.apple.developer.usernotifications.critical-alerts`
 *    accordé par Apple ET si l'utilisateur l'a autorisée. Sans l'un des deux, iOS la ramène au niveau `active` : elle
 *    s'affiche, et ne sonne que si le téléphone n'est pas en silencieux.
 *  - Android : la notification suit le canal nommé par `channelId`, que l'application doit avoir créé. Un canal absent
 *    renvoie au canal de repli (importance par défaut). Un canal qui contourne « Ne pas déranger » le fait seulement si
 *    l'utilisateur a accordé l'accès ; un son d'usage « alarme » sonne malgré le mode silencieux.
 */

export const PLATEFORMES = ["ios", "android"];
const IMPORTANCES = ["min", "low", "default", "high", "max"];
export const CANAL_DE_REPLI = Object.freeze({ id: "expo_notifications_fallback_notification_channel", importance: "default" });

/** Valide et normalise le profil d'un appareil simulé ; rend `{ appareil }` ou `{ ecarts }`. */
export function lireProfil(jeton, corps) {
  const c = corps && typeof corps === "object" ? corps : {};
  const ecarts = [];
  if (!PLATEFORMES.includes(c.plateforme)) ecarts.push(`plateforme : l'une de ${PLATEFORMES.join(", ")}`);
  const application = c.application ?? {};
  const canaux = Array.isArray(application.canaux) ? application.canaux : [];
  canaux.forEach((k, i) => {
    if (!k || typeof k.id !== "string" || !IMPORTANCES.includes(k.importance)) {
      ecarts.push(`application.canaux[${i}] : id (texte) et importance (${IMPORTANCES.join(", ")}) exigés`);
    }
  });
  if (ecarts.length) return { ecarts };
  const u = c.utilisateur ?? {};
  return {
    appareil: {
      jeton,
      plateforme: c.plateforme,
      nom: typeof c.nom === "string" ? c.nom : null,
      application: {
        droitAlertesCritiques: application.droitAlertesCritiques === true,
        canaux: canaux.map((k) => ({ id: k.id, importance: k.importance, contourneNePasDeranger: k.contourneNePasDeranger === true,
          usageAudio: k.usageAudio === "alarme" ? "alarme" : "notification" })),
      },
      utilisateur: {
        autoriseNotifications: u.autoriseNotifications !== false,
        autoriseAlertesCritiques: u.autoriseAlertesCritiques === true,
        accesNePasDeranger: u.accesNePasDeranger === true,
        modeSilencieux: u.modeSilencieux === true,
        nePasDeranger: u.nePasDeranger === true,
      },
    },
  };
}

/** La charge APNs qu'Expo produit pour iOS (en-tête de priorité compris). */
export function versApns(m) {
  const aps = { alert: { title: m.title ?? undefined, subtitle: m.subtitle ?? undefined, body: m.body ?? undefined } };
  if (typeof m.sound === "string") aps.sound = m.sound;
  if (m.sound && typeof m.sound === "object") {
    aps.sound = { critical: m.sound.critical ? 1 : 0, name: m.sound.name ?? "default", volume: m.sound.volume ?? 1 };
  }
  if (m.interruptionLevel) aps["interruption-level"] = m.interruptionLevel;
  if (m.badge !== undefined) aps.badge = m.badge;
  return { entetes: { "apns-priority": m.priority === "normal" ? "5" : "10", "apns-push-type": "alert" }, charge: { aps, body: m.data ?? {} } };
}

/** Le message FCM qu'Expo produit pour Android : un message de données que l'application Expo affiche sur son canal. */
export function versFcm(m) {
  return {
    android: { priority: m.priority === "high" ? "HIGH" : "NORMAL" },
    data: { title: m.title ?? null, message: m.body ?? null, channelId: m.channelId ?? null, sound: m.sound ?? null, body: m.data ?? {} },
  };
}

/** Ce que l'utilisateur constate sur l'appareil. */
export function presenter(appareil, m) {
  if (!appareil.utilisateur.autoriseNotifications) {
    return { affichee: false, sonore: false, contourneSilencieux: false, contourneNePasDeranger: false, motif: "notifications refusées par l'utilisateur" };
  }
  return appareil.plateforme === "ios" ? presenterIos(appareil, m) : presenterAndroid(appareil, m);
}

function presenterIos(a, m) {
  const demandeCritique = m.interruptionLevel === "critical" || (m.sound && typeof m.sound === "object" && m.sound.critical === true);
  const u = a.utilisateur;
  if (demandeCritique && a.application.droitAlertesCritiques && u.autoriseAlertesCritiques) {
    return { affichee: true, sonore: true, contourneSilencieux: true, contourneNePasDeranger: true, niveau: "critical",
      volume: m.sound && typeof m.sound === "object" ? (m.sound.volume ?? 1) : 1, motif: "alerte critique honorée" };
  }
  const niveau = m.interruptionLevel === "critical" || !m.interruptionLevel ? "active" : m.interruptionLevel;
  const contourneNePasDeranger = niveau === "time-sensitive";
  const sonore = Boolean(m.sound) && niveau !== "passive" && !u.modeSilencieux && (!u.nePasDeranger || contourneNePasDeranger);
  const motif = !demandeCritique ? "notification ordinaire"
    : !a.application.droitAlertesCritiques ? "critique demandée, droit Critical Alerts absent : ramenée au niveau active"
      : "critique demandée, refusée par l'utilisateur : ramenée au niveau active";
  return { affichee: true, sonore, contourneSilencieux: false, contourneNePasDeranger, niveau, motif };
}

function presenterAndroid(a, m) {
  const demande = m.channelId ?? null;
  const canal = (demande && a.application.canaux.find((k) => k.id === demande)) || CANAL_DE_REPLI;
  const u = a.utilisateur;
  const audible = ["default", "high", "max"].includes(canal.importance) && m.sound !== null;
  const contourneNePasDeranger = canal.contourneNePasDeranger === true && u.accesNePasDeranger;
  const contourneSilencieux = canal.usageAudio === "alarme";
  const sonore = audible && (!u.modeSilencieux || contourneSilencieux) && (!u.nePasDeranger || contourneNePasDeranger);
  const motif = demande && canal === CANAL_DE_REPLI ? `canal « ${demande} » non créé par l'application : canal de repli` : `canal « ${canal.id} »`;
  return { affichee: true, sonore, contourneSilencieux: sonore && contourneSilencieux,
    contourneNePasDeranger: sonore && contourneNePasDeranger, canal: canal.id,
    banniere: ["high", "max"].includes(canal.importance) && m.priority === "high", motif };
}
