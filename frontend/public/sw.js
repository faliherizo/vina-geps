/*
 * Service worker de VINA GEPS — deux rôles, rien de plus :
 *  1. rendre l'application **installable** (devis 2.6 : notifications sans application native ;
 *     grille 10 : rien à installer — l'installation est proposée, jamais exigée) ;
 *  2. recevoir les **notifications poussées** (Web Push, RFC 8291) et les afficher, écran fermé.
 * Aucune mise en cache applicative : l'écran lit toujours le serveur, le contrat HTTP reste la
 * seule vérité — un cache qui servirait une vieille liste de besoins tromperait une secrétaire.
 */
self.addEventListener("install", () => { self.skipWaiting(); });
self.addEventListener("activate", (e) => { e.waitUntil(self.clients.claim()); });

self.addEventListener("push", (e) => {
  let charge = { titre: "VINA GEPS", corps: "", url: "/admin/notifications" };
  try { charge = { ...charge, ...e.data.json() }; } catch { charge.corps = e.data ? e.data.text() : ""; }
  e.waitUntil(self.registration.showNotification(charge.titre, {
    body: charge.corps, lang: "fr-CA", tag: charge.etiquette || "nexu-vina", renotify: true,
    icon: "/icones/icone-192.svg", badge: "/icones/icone-192.svg", data: { url: charge.url },
  }));
});

self.addEventListener("notificationclick", (e) => {
  e.notification.close();
  const url = (e.notification.data && e.notification.data.url) || "/admin/notifications";
  e.waitUntil(self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((fenetres) => {
    const ouverte = fenetres.find((f) => "focus" in f);
    if (ouverte) { ouverte.navigate(url); return ouverte.focus(); }
    return self.clients.openWindow(url);
  }));
});
