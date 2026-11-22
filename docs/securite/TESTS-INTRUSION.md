# Tests d'intrusion

Les tests d'intrusion de VINA GePS / Urgences-santé sont dans un **projet distinct** :
`/Users/faliherizo/project/urgences-sante-pentest`, qui a son propre dépôt Git. Ils visent seulement la
pile Docker locale.

| Document | Contenu |
|---|---|
| `PLAN-TESTS-INTRUSION.md` | Portée, règles d'engagement, méthode (OWASP WSTG / ASVS L2 / API Top 10, PTES), outils, triage CVSS 3.1, CI |
| `RAPPORT-TESTS-INTRUSION-2026-11-22.md` | Exécution du 2026-11-22 : constats, preuves, recommandations |
| `tests/*.test.mjs` | 53 assertions `node:test` ; chacune échoue si la faille existe |
| `lancer-tout.sh` | Toutes les couches contre `CIBLE_WEB`, résultats dans `resultats/<date>/` |

```bash
# Secrets d'amorçage générés dans le projet de tests (ignorés par git) : la pile se monte comme en production.
set -a && . ../urgences-sante-pentest/secrets-essai/pile.env && set +a
US_WEB_PORT=8096 docker compose -p us-pentest up -d --build
cd ../urgences-sante-pentest && CIBLE_WEB=http://localhost:8096 npm test          # 54 assertions, un fichier à la fois
CIBLE_WEB=http://localhost:8096 bash scripts/20-zap.sh                              # OWASP ZAP baseline + API
```

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

Observations de durcissement : nginx maître en root, `npm install` sans lockfile dans
`frontend/Dockerfile` (✅ `npm ci`), avis `npm audit` (outillage de développement surtout ; `exceljs` livré),
base SQLite en 0644, verrou par compte exploitable pour bloquer un compte connu. Le détail est dans le
rapport.

Non couvert : TLS de production, SSO, MFA, téléversements, en-tête `Host` en mode sous-domaine, CVE
Java (dependency-check non exécuté).
