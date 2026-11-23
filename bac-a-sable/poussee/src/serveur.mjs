/**
 * L'adaptateur HTTP du bac à sable de poussée. Routes :
 *   POST   /--/api/v2/push/send          contrat Expo (jeton d'accès exigé si EXPO_JETON_ACCES est réglé)
 *   POST   /--/api/v2/push/getReceipts   contrat Expo
 *   PUT    /bac-a-sable/appareils/:jeton installer l'application simulée sur un appareil (profil)
 *   DELETE /bac-a-sable/appareils/:jeton la désinstaller
 *   GET    /bac-a-sable/appareils        les appareils
 *   GET    /bac-a-sable/notifications    ce que chaque téléphone a reçu et montré (?jeton=, ?titre=)
 *   DELETE /bac-a-sable/notifications    vider
 *   GET    /sante                        disponibilité
 *   GET    /                             console de lecture (HTML)
 * Les routes /bac-a-sable exigent `Authorization: Bearer <BAC_JETON_CONTROLE>` quand ce jeton est réglé : un bac à
 * sable déployé sur un serveur n'expose pas les notifications reçues à quiconque.
 */
import { createServer } from "node:http";
import { randomUUID } from "node:crypto";
import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";
import { ServicePoussee } from "./service.mjs";

/** Corps refusé au-delà : Expo plafonne la requête bien en deçà (100 messages de 4 Kio). */
const CORPS_MAX_OCTETS = 1024 * 1024;
const CONSOLE = readFileSync(new URL("./console.html", import.meta.url));

export function creerServeur({ service, jetonControle = "" }) {
  return createServer(async (req, res) => {
    try {
      const url = new URL(req.url, "http://bac-a-sable");
      const r = await router(service, jetonControle, req, url);
      repondre(res, r);
    } catch (e) {
      repondre(res, { statut: e.statut ?? 500, corps: { errors: [{ code: e.code ?? "INTERNAL_SERVER_ERROR", message: e.message }] } });
    }
  });
}

async function router(service, jetonControle, req, url) {
  const { pathname } = url;
  if (req.method === "GET" && pathname === "/sante") return { statut: 200, corps: { statut: "ok" } };
  if (req.method === "GET" && pathname === "/") return { statut: 200, html: CONSOLE };
  if (req.method === "POST" && pathname === "/--/api/v2/push/send") return service.envoyer(await lireJson(req), req.headers.authorization);
  if (req.method === "POST" && pathname === "/--/api/v2/push/getReceipts") return service.accuses(await lireJson(req));
  if (!pathname.startsWith("/bac-a-sable/")) return { statut: 404, corps: { errors: ["route inconnue"] } };
  if (jetonControle && req.headers.authorization !== `Bearer ${jetonControle}`) return { statut: 401, corps: { errors: ["jeton de contrôle exigé"] } };
  const appareil = /^\/bac-a-sable\/appareils\/([^/]+)$/.exec(pathname);
  if (appareil && req.method === "PUT") return service.inscrire(decodeURIComponent(appareil[1]), await lireJson(req));
  if (appareil && req.method === "DELETE") return service.desinscrire(decodeURIComponent(appareil[1]));
  if (pathname === "/bac-a-sable/appareils" && req.method === "GET") return service.appareils();
  if (pathname === "/bac-a-sable/notifications" && req.method === "GET") {
    return service.notifications({ jeton: url.searchParams.get("jeton"), titre: url.searchParams.get("titre") });
  }
  if (pathname === "/bac-a-sable/notifications" && req.method === "DELETE") return service.vider();
  return { statut: 405, corps: { errors: ["méthode non admise"] } };
}

function lireJson(req) {
  return new Promise((resolve, reject) => {
    const morceaux = [];
    let taille = 0;
    req.on("data", (m) => {
      taille += m.length;
      if (taille > CORPS_MAX_OCTETS) reject(Object.assign(new Error("request entity too large"), { statut: 413, code: "PAYLOAD_TOO_LARGE" }));
      else morceaux.push(m);
    });
    req.on("end", () => {
      try {
        resolve(JSON.parse(Buffer.concat(morceaux).toString("utf8") || "null"));
      } catch {
        reject(Object.assign(new Error("body is not valid JSON"), { statut: 400, code: "VALIDATION_ERROR" }));
      }
    });
    req.on("error", reject);
  });
}

function repondre(res, r) {
  if (r.html) {
    res.writeHead(r.statut, { "Content-Type": "text/html; charset=utf-8", "Content-Security-Policy": "default-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'" });
    res.end(r.html);
    return;
  }
  if (r.corps === null) {
    res.writeHead(r.statut);
    res.end();
    return;
  }
  res.writeHead(r.statut, { "Content-Type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(r.corps));
}

/** Démarre le bac à sable selon l'environnement — configuration lue en ce seul endroit. */
export function demarrer(env, auPret = () => {}) {
  const service = new ServicePoussee({ jetonAcces: env.EXPO_JETON_ACCES ?? "", identifiant: randomUUID });
  const serveur = creerServeur({ service, jetonControle: env.BAC_JETON_CONTROLE ?? "" });
  return serveur.listen(Number(env.PORT ?? 8080), env.HOTE ?? "0.0.0.0", () => auPret(serveur.address().port));
}

// Exclusion de couverture : seule la garde d'exécution directe (`node src/serveur.mjs`), que les essais n'empruntent pas.
/* node:coverage ignore next 3 */
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  demarrer(process.env, (port) => console.log(`bac à sable de poussée : port ${port}`));
}
