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
  // Jusqu'à l'accolade fermante de la location (indentée de deux espaces), pas la première : un `if (…) { … }` en a une.
  return conf.slice(debut, conf.indexOf("\n  }", debut));
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
    // La vitrine de VINA GePS (14 novembre 2026) : une page statique, et non une redirection vers /admin — sauf sur l'hôte
    // d'un organisme (ADR 0033), où la racine mène à sa connexion.
    expect(bloc("= /")).toContain("try_files /vitrine/index.html =404;");
    expect(bloc("= /")).toContain("if ($racine_vers_connexion) { return 302 /admin/login; }");
    expect(bloc("= /acceder")).toContain("try_files /vitrine/acceder.html =404;");
    expect(conf.indexOf("location = /acceder {")).toBeLessThan(racine);
    expect(bloc("/vitrine/")).toContain("try_files $uri =404;");
    expect(bloc("~ ^/invitation/")).toContain("try_files $uri @spa_index;");
  });

  it("la vitrine reste sur l'hôte commun : par défaut (« * ») partout, sinon la racine d'un organisme mène à sa connexion", () => {
    const map = conf.slice(conf.indexOf('map "$host:${VITRINE_HOTE}" $racine_vers_connexion {'), conf.indexOf("server {"));
    const motifs = [...map.matchAll(/"~([^"]+)"\s+0;/g)].map((m) => new RegExp(m[1]));
    const versConnexion = (hote: string, vitrine: string) => !motifs.some((m) => m.test(`${hote}:${vitrine}`));
    expect(versConnexion("clincom.geps.vinaitsolution.com", "*")).toBe(false);
    expect(versConnexion("geps.vinaitsolution.com", "geps.vinaitsolution.com")).toBe(false);
    expect(versConnexion("clincom.geps.vinaitsolution.com", "geps.vinaitsolution.com")).toBe(true);
    expect(versConnexion("urgence-sante.geps.vinaitsolution.com", "geps.vinaitsolution.com")).toBe(true);
    expect(map).toMatch(/default\s+1;/);
    expect(readFileSync(join(RACINE, "Dockerfile"), "utf8")).toContain('VITRINE_HOTE="*" NGINX_ENVSUBST_FILTER="^(API|VITRINE)_"');
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

/** Le bloc entier d'une location : jusqu'à son accolade fermante (indentée de deux espaces), au-delà de `${…}` et `types { … }`. */
const blocEntier = (location: string) => {
  const debut = conf.indexOf(`location ${location} {`);
  expect(debut, `location ${location}`).toBeGreaterThan(-1);
  return conf.slice(debut, conf.indexOf("\n  }", debut));
};

describe("nginx — durcissement issu des tests d'intrusion du 22 novembre 2026", () => {
  it("PT-04 : chaque page HTML porte la politique de sécurité du contenu, sans script en ligne", () => {
    const politique = conf.slice(conf.indexOf("map $request_method $politique_contenu"), conf.indexOf("server {"));
    expect(politique).toContain("script-src 'self';");
    expect(politique).not.toMatch(/script-src[^;]*unsafe-(inline|eval)/);
    expect(politique).toContain("object-src 'none'");
    expect(politique).toContain("frame-ancestors 'self'");
    for (const location of ["@spa_index", "= /", "/vitrine/"]) {
      expect(blocEntier(location), location).toContain("add_header Content-Security-Policy $politique_contenu always;");
    }
    // ZAP (10063) : la vitrine, à la racine, redéclare ses en-têtes ; la politique des permissions n'y manque plus.
    for (const location of ["@spa_index", "= /"]) {
      expect(blocEntier(location), location).toContain("add_header Permissions-Policy");
    }
    const index = readFileSync(join(RACINE, "index.html"), "utf8");
    expect(index.match(/<script(?![^>]*(src=|application\/ld\+json))[^>]*>/g) ?? []).toEqual([]);
    expect(existsSync(join(RACINE, "public", "avertissement-ie.js"))).toBe(true);
  });

  it("PT-05 : l'API garde ses propres en-têtes ; nginx n'y ajoute que HSTS, une seule fois", () => {
    for (const location of ["/api/", "/public/"]) {
      const b = blocEntier(location);
      expect(b, location).toContain("proxy_hide_header Strict-Transport-Security;");
      expect(b.match(/^\s*add_header /gm), location).toHaveLength(1);
    }
  });
});

describe("nginx — durcissements IN-01 et IN-05", () => {
  const dockerfile = readFileSync(join(RACINE, "Dockerfile"), "utf8");

  it("IN-01 : l'image tourne sans privilège et écoute sur un port non privilégié", () => {
    expect(dockerfile).toMatch(/^FROM nginxinc\/nginx-unprivileged:[\d.]+-alpine AS runtime$/m);
    expect(dockerfile).toContain("EXPOSE 8080");
    expect(conf).toMatch(/^\s*listen 8080;$/m);
    expect(conf).not.toMatch(/^\s*listen 80;$/m);
  });

  it("IN-05 : X-Forwarded-For n'est cru que des mandataires déclarés, puis réécrit vers l'API", () => {
    expect(conf).toContain("set_real_ip_from ${API_PROXIES_CONFIANCE};");
    expect(conf).toContain("real_ip_recursive on;");
    expect(conf).not.toContain("$proxy_add_x_forwarded_for");
    for (const location of ["/api/", "/public/"]) {
      expect(blocEntier(location), location).toContain("proxy_set_header X-Forwarded-For $remote_addr;");
    }
    expect(dockerfile).toContain("API_PROXIES_CONFIANCE=127.0.0.1/32");
  });

  it("isolation de l'origine (ZAP 90004) : CORP partout où nosniff est posé, COOP partout où la CSP l'est — jamais sur l'API", () => {
    const compter = (motif: RegExp) => (conf.match(motif) ?? []).length;
    expect(compter(/add_header X-Content-Type-Options "nosniff" always;/g)).toBe(compter(/add_header Cross-Origin-Resource-Policy "same-origin" always;/g));
    expect(compter(/add_header Content-Security-Policy \$politique_contenu always;/g)).toBe(compter(/add_header Cross-Origin-Opener-Policy "same-origin" always;/g));
    for (const location of ["/api/", "/public/"]) expect(blocEntier(location), location).not.toContain("Cross-Origin-");
  });

  it("SSO de démonstration : seules les pages de connexion des fournisseurs embarqués passent, vers l'API, avec l'hôte d'origine", () => {
    const motif = conf.match(/location ~ (\^\/mock\/[^ ]+) \{/);
    expect(motif, "location des pages de connexion /mock").not.toBeNull();
    const re = new RegExp(motif![1]);
    for (const ouvert of ["/mock/entra/d3d3d3d3-0000-4000-8000-c55dc55dc55d/oauth2/v2.0/authorize", "/mock/entra/t-1/login",
      "/mock/entra/t-1/oauth2/v2.0/logout", "/mock/saml/sso"]) expect(ouvert, ouvert).toMatch(re);
    // Le jeton, les clés, la découverte et les autres simulateurs restent internes.
    for (const ferme of ["/mock/entra/t-1/oauth2/v2.0/token", "/mock/entra/t-1/discovery/v2.0/keys",
      "/mock/entra/t-1/v2.0/.well-known/openid-configuration", "/mock/saml/metadata", "/mock/twilio/x", "/mock/webhooks/recepteur/journal.html",
      "/mock/entra/../login"]) expect(ferme, ferme).not.toMatch(re);
    const b = bloc(`~ ${motif![1]}`);
    expect(b).toMatch(/set \$\w+ \$\{API_UPSTREAM\};/);
    expect(b).toContain("proxy_set_header X-Forwarded-Host $host;");
  });

  it("co-hébergement : /public/ vise le même amont configuré que /api/, jamais un nom d'hôte en dur", () => {
    for (const location of ["/api/", "/public/"]) {
      const b = blocEntier(location);
      expect(b, location).toMatch(/set \$\w+ \$\{API_UPSTREAM\};/);
      expect(b, location).toMatch(/proxy_pass http:\/\/\$\w+:\$\{API_PORT\};/);
    }
  });
});
