/**
 * **Ce que nginx sert à la racine du site** : l'application — et, à leur place, les fichiers qui
 * doivent y vivre. Le travailleur de service des notifications (`/sw.js`, portée `/`), le manifeste
 * d'installation et les icônes doivent être servis avec leur type : en `text/html`, le navigateur
 * refuse d'enregistrer le travailleur et l'abonnement aux notifications échoue
 * (« unsupported MIME type »). Cet essai lit la configuration et les fichiers publics : chaque
 * fichier de l'application à la racine a sa `location`, servie depuis la racine de l'application
 * avec le bon type, et le travailleur comme le manifeste renvoient vers l'application (`/admin`).
 */
import { describe, expect, it } from "vitest";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const RACINE = join(__dirname, "..");
const conf = readFileSync(join(RACINE, "nginx.conf"), "utf8");

const bloc = (location: string) => {
  const debut = conf.indexOf(`location ${location} {`);
  expect(debut, `location ${location}`).toBeGreaterThan(-1);
  return conf.slice(debut, conf.indexOf("}", debut));
};

describe("nginx — fichiers de l'application à la racine du site", () => {
  it("le travailleur de service est servi depuis l'application, en JavaScript, jamais mis en cache", () => {
    const b = bloc("= /sw.js");
    expect(b).toContain("root /usr/share/nginx/html;");
    expect(b).toContain("default_type application/javascript;");
    expect(b).toContain("no-cache");
    expect(existsSync(join(RACINE, "public", "sw.js"))).toBe(true);
  });

  it("le manifeste d'installation est servi en application/manifest+json et démarre dans l'application", () => {
    const b = bloc("= /manifest.webmanifest");
    expect(b).toContain("root /usr/share/nginx/html;");
    expect(b).toContain("default_type application/manifest+json;");
    const manifeste = JSON.parse(readFileSync(join(RACINE, "public", "manifest.webmanifest"), "utf8"));
    expect(manifeste.start_url).toMatch(/^\/admin\//);
    expect(manifeste.scope).toBe("/");
  });

  it("les icônes de l'application ont leur location, et chaque icône du manifeste existe", () => {
    expect(bloc("/icones/")).toContain("root /usr/share/nginx/html;");
    const manifeste = JSON.parse(readFileSync(join(RACINE, "public", "manifest.webmanifest"), "utf8"));
    for (const icone of manifeste.icons as Array<{ src: string }>) expect(existsSync(join(RACINE, "public", icone.src)), icone.src).toBe(true);
  });

  it("une notification cliquée ouvre l'application (/admin), pas un chemin inconnu", () => {
    const sw = readFileSync(join(RACINE, "public", "sw.js"), "utf8");
    expect(sw).not.toMatch(/["']\/app\//);
    expect(sw).toContain('"/admin/notifications"');
    expect(sw).toContain('self.registration.showNotification');
  });

  it("la racine sert la vitrine, qui mène à l'application ; ces locations précèdent le repli `location /`", () => {
    const racine = conf.indexOf("location / {");
    for (const l of ["= /sw.js", "= /manifest.webmanifest", "/icones/", "/vitrine/"]) expect(conf.indexOf(`location ${l} {`)).toBeLessThan(racine);
    // La vitrine de VINA GePS (14 novembre 2026) : une page statique, et non une redirection vers /admin.
    expect(bloc("= /")).toContain("try_files /vitrine/index.html =404;");
    expect(bloc("= /")).not.toContain("return 302");
    expect(bloc("/vitrine/")).toContain("try_files $uri =404;");
    expect(bloc("~ ^/invitation/")).toContain("try_files $uri @spa_index;");
  });

  it("chaque proxy vers l'API transmet l'hôte d'origine : le backend en déduit l'établissement (ADR 0033)", () => {
    // Ces locations contiennent des gabarits `${…}` : on lit jusqu'à l'accolade fermante de la location, pas la première.
    const location = (l: string) => { const d = conf.indexOf(`location ${l} {`); expect(d, l).toBeGreaterThan(-1); return conf.slice(d, conf.indexOf("\n  }", d)); };
    for (const l of ["/api/", "/public/"]) {
      const b = location(l);
      expect(b, l).toContain("proxy_set_header Host $host;");
      expect(b, l).toContain("proxy_set_header X-Forwarded-Host $host;");
    }
    // Un seul serveur pour tous les hôtes : aucun `server_name` qui exclurait un sous-domaine d'établissement.
    expect(conf.match(/server_name\s+([^;]+);/)?.[1].trim()).toBe("_");
  });
});
