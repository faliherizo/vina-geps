/**
 * Abonnement du navigateur aux **notifications poussées** (devis 2.6). Tout passe par des
 * fonctions injectables pour être éprouvé sans navigateur : `navigator.serviceWorker` et
 * `PushManager` sont des paramètres, jamais lus directement dans la logique.
 */
export interface EnvironnementPoussee {
  supporte: boolean;
  permission: () => NotificationPermission;
  demanderPermission: () => Promise<NotificationPermission>;
  enregistrer: () => Promise<{ abonnementActuel: () => Promise<PushSubscription | null>; abonner: (cle: Uint8Array) => Promise<PushSubscription> }>;
}

/** La clé VAPID base64url → octets, telle que `pushManager.subscribe` l'exige. */
export function cleEnOctets(cle: string): Uint8Array {
  const rembourrage = "=".repeat((4 - (cle.length % 4)) % 4);
  const b64 = (cle + rembourrage).replace(/-/g, "+").replace(/_/g, "/");
  const brut = atob(b64);
  return Uint8Array.from(brut, (c) => c.charCodeAt(0));
}

export function environnementNavigateur(): EnvironnementPoussee {
  const supporte = typeof window !== "undefined" && "serviceWorker" in navigator && "PushManager" in window && "Notification" in window;
  return {
    supporte,
    permission: () => (supporte ? Notification.permission : "denied"),
    demanderPermission: () => (supporte ? Notification.requestPermission() : Promise.resolve("denied" as NotificationPermission)),
    enregistrer: async () => {
      const reg = await navigator.serviceWorker.register("/sw.js", { scope: "/" });
      await navigator.serviceWorker.ready;
      return {
        abonnementActuel: () => reg.pushManager.getSubscription(),
        abonner: (cle) => reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: cle as BufferSource }),
      };
    },
  };
}

export type EtatPoussee = "non_supporte" | "refuse" | "inactif" | "actif";

export interface ClientPoussee { call: (chemin: string, init?: RequestInit) => Promise<Response> }

/** Ce que l'écran affiche au chargement : supporté ? permis ? abonné sur cet appareil ? */
export async function etatActuel(env: EnvironnementPoussee): Promise<EtatPoussee> {
  if (!env.supporte) return "non_supporte";
  if (env.permission() === "denied") return "refuse";
  try {
    const sw = await env.enregistrer();
    return (await sw.abonnementActuel()) ? "actif" : "inactif";
  } catch { return "inactif"; }
}

/** Abonne cet appareil : permission, clé publique du serveur, abonnement, enregistrement côté serveur. */
export async function activer(env: EnvironnementPoussee, client: ClientPoussee): Promise<{ etat: EtatPoussee; erreur: string | null }> {
  if (!env.supporte) return { etat: "non_supporte", erreur: "Ce navigateur ne prend pas en charge les notifications poussées." };
  if ((await env.demanderPermission()) !== "granted") return { etat: "refuse", erreur: "Permission refusée : les notifications resteront dans l'application, par courriel et par texto." };
  const r = await client.call("/api/notifications/push/cle-publique");
  if (!r.ok) return { etat: "inactif", erreur: "Clé du serveur indisponible." };
  const { cle } = await r.json() as { cle: string };
  const sw = await env.enregistrer();
  const abonnement = (await sw.abonnementActuel()) ?? (await sw.abonner(cleEnOctets(cle)));
  const e = await client.call("/api/notifications/push/abonnements", { method: "POST", body: JSON.stringify({ abonnement: abonnement.toJSON(), navigateur: navigator.userAgent.slice(0, 120) }) });
  if (!e.ok) { const d = await e.json().catch(() => ({})); return { etat: "inactif", erreur: (d.errors ?? ["abonnement refusé"]).join(" ; ") }; }
  return { etat: "actif", erreur: null };
}

export async function desactiver(env: EnvironnementPoussee, client: ClientPoussee): Promise<EtatPoussee> {
  if (!env.supporte) return "non_supporte";
  const sw = await env.enregistrer();
  const abonnement = await sw.abonnementActuel();
  if (abonnement) {
    await client.call("/api/notifications/push/abonnements", { method: "DELETE", body: JSON.stringify({ endpoint: abonnement.endpoint }) });
    await abonnement.unsubscribe();
  }
  return "inactif";
}
