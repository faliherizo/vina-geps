# Tests d'intrusion

> **Dans ce dépôt, avec un pipeline** (depuis le 22 novembre 2026) : la suite de l'organisme FX-AO-20260810, qui s'exécute
> dans `securite/intrusion/` (pile éphémère, `lancer.sh`), dans `.github/workflows/intrusion.yml`, et dans l'orchestrateur
> `devsecops.yml`. Plan : [fx-ao/PLAN-TESTS-INTRUSION-FX-AO.md](fx-ao/PLAN-TESTS-INTRUSION-FX-AO.md) ; rapport :
> [fx-ao/RAPPORT-TESTS-INTRUSION-FX-AO-2026-11-22.md](fx-ao/RAPPORT-TESTS-INTRUSION-FX-AO-2026-11-22.md) ; portes de
> sécurité du pipeline : [DEVSECOPS.md](DEVSECOPS.md). La suite générale ci-dessous reste dans son projet local.

Les tests d'intrusion de VINA GePS / Urgences-santé sont dans un **projet distinct** :
`/Users/faliherizo/project/urgences-sante-pentest`, qui a son propre dépôt Git. Ils visent seulement la
pile Docker locale.

| Document | Contenu |
|---|---|
| `PLAN-TESTS-INTRUSION.md` | Portée, règles d'engagement, méthode (OWASP WSTG / ASVS L2 / API Top 10, PTES), outils, triage CVSS 3.1, CI |
| `RAPPORT-TESTS-INTRUSION-2026-11-22.md` | Exécution du 2026-11-22 : constats, preuves, recommandations |
| `tests/*.test.mjs` | 72 assertions `node:test`, dont le balayage de chaque route ; chacune échoue si la faille existe |
| `lancer-tout.sh` | Toutes les couches contre `CIBLE_WEB`, résultats dans `resultats/<date>/` |

```bash
# Secrets d'amorçage générés dans le projet de tests (ignorés par git) : la pile se monte comme en production.
set -a && . ../urgences-sante-pentest/secrets-essai/pile.env && set +a
US_WEB_PORT=8097 docker compose -p us-pentest up -d --build                         # 8095 et 8096 : pris par un autre projet
cd ../urgences-sante-pentest && CIBLE_WEB=http://localhost:8097 npm test          # 72 assertions (balayage compris), un fichier à la fois
CIBLE_WEB=http://localhost:8097 bash scripts/20-zap.sh                              # OWASP ZAP baseline + API
```

## Résultat du 2026-11-26 — balayage de chaque route, les deux avis

Rapport complet : `urgences-sante-pentest/RAPPORT-TESTS-INTRUSION-2026-11-26.md`. Le balayage lit la spécification
OpenAPI servie par le Java et joue **chacune des 627 routes** : refus sans session, rôles, isolation entre
établissements (identifiants de l'autre établissement compris), injection SQL classique et à l'aveugle dans chaque
paramètre de chemin, de requête et de corps, XSS réfléchi et stocké, traversée de chemin, SSRF, redirection ouverte,
jeton falsifié, attribution de masse. Une route qui ferme la session (déconnexion) ne peut plus masquer le reste :
le balayage se reconnecte, et échoue au-delà de 5 % de 401.

| Avis | Ce qui l'éprouve | Résultat |
|---|---|---|
| Urgences-santé (26-015-DO) | Balayage (effectifs, paie, communication, socle) · suite 01 à 09 · contrôle statique du frontend | 12/12 · 57/57 · 3/3 |
| FX-AO-20260810 | Balayage (alertes, communication, socle) · suite du dépôt `securite/intrusion/fx-ao` | 12/12 · 15/15 |
| Les deux | OWASP ZAP 2.16.1, base et API | 0 échec ; avertissements triés (503 de la limitation, faux positifs SQL vérifiés) |

| ID | Gravité | Constat | État |
|---|---|---|---|
| PT-10 | Moyen 5.3 | Activation du second facteur en 500 quand l'adresse `otpauth://` dépasse la capacité du QR (nom technique `login@@organisme`) : l'usager ne pouvait pas activer son second facteur | ✅ libellé tronqué jusqu'à tenir, secret intact (`Totp`, `CodeQrTest`) |
| PT-08 | Faible 3.1 | Pages de connexion SSO simulées sans `nosniff`, CSP ni interdiction d'encadrement (`add_header` coupant l'héritage nginx) | ✅ en-têtes redéclarés, CSP par empreinte (`nginx-conf.test.ts`) |
| PT-09 | Informatif | Authentification vérifiée par contrôleur : deux routes répondent 400 avant 401 à un anonyme, sans rien servir | Recommandation : filtre de refus par défaut sur `/api/**` |

## Résultat du 2026-11-22

Les contrôles principaux tiennent (47 assertions) : isolation par `X-Tenant-Id`, frontière
machine/session, OAuth sans oracle, RBAC, CSRF, CORS, injection SQL et XSS, absence de 500 et de trace
de pile, verrou contre la force brute, rien de sensible servi par nginx, backend non-root, aucun
secret dans les images.

Premier passage : 0 critique, 0 élevé, 3 moyens, 3 faibles. **Tous corrigés et re-testés le jour même**
([ADR 0091](../adr/0091-correctifs-tests-intrusion-2026-11-22.md)) : suite assertive **54/54** contre la pile
reconstruite, OWASP ZAP 2.16.1 **sans échec** (avertissements triés dans le rapport, § 4.1 : 503 de la limitation de
débit et faux positifs d'injection SQL). ZAP a ajouté PT-07 (`Permissions-Policy` absente de la vitrine), corrigé.

| ID | Gravité | Constat (état : ✅ corrigé, re-testé) | Correctif |
|---|---|---|---|
| PT-01 | Moyen 5.3 | `/api/_migration/routes` publie 601 routes sans session (la Javadoc dit « désactivé par défaut », mais le défaut est `true`) | ✅ défaut `${EXPOSER_ROUTES:false}` |
| PT-02 | Moyen 4.8 | Après déconnexion, le JWT (Bearer et témoin) reste valide 8 h ; pas de `jti` ni de révocation | ✅ `jti` par jeton, liste `auth_sessions_revoquees` (changeset 0025) consultée par `FiltreSession` |
| PT-03 | Moyen 6.5* | `admin/admin` et `superadmin/superadmin` acceptés, `mustChangePassword=false` (*si déployé sans secrets) | ✅ changement imposé hors mode d'essai |
| PT-04 | Faible 3.1 | Pas de Content-Security-Policy sur les pages HTML | ✅ CSP unique (`map` nginx), aucun script en ligne |
| PT-05 | Faible 2.6 | `X-Frame-Options`, `Referrer-Policy` et `Permissions-Policy` en double et contradictoires sur `/api/` | ✅ en-têtes de l'API seuls sur `/api/`, HSTS par nginx |
| PT-06 | Faible 3.8 | Un superadmin qui crée un compte avec `X-Tenant-Id` sans `tenantId` dans le corps crée un compte global | ✅ établissement de l'en-tête à défaut du corps |

Observations de durcissement : **toutes traitées** ([ADR 0092](../adr/0092-adresse-reelle-client-et-durcissements.md)),
re-test **57/57** :
- ✅ IN-01 : nginx sans privilège (`nginxinc/nginx-unprivileged`, port 8080, Compose et Terraform alignés) ;
- ✅ IN-02 : `npm ci` sur le verrou ;
- ✅ IN-03 : `npm audit` 15 → 0 (vite 8, vitest 5, Capacitor 8, `uuid` forcé sous `exceljs`), `audit:prod` en CI ;
- ✅ IN-04 : base SQLite en 600, dossier en 700 ;
- ✅ IN-05 : le serveur voit désormais l'**adresse réelle** du client (il voyait celle de nginx : verrou devenu par compte,
  limitation de débit commune, plage réseau du pointage jugée sur nginx), sans qu'un `X-Forwarded-For` forgé soit cru ;
  seuil par adresse contre l'aspersion de mots de passe.

Non couvert : TLS de production, SSO, MFA, téléversements, en-tête `Host` en mode sous-domaine, CVE
Java (dependency-check non exécuté).
