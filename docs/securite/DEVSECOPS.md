# Pipeline DevSecOps de VINA GePS

> Pour qui maintient la chaîne d'intégration ou ajoute un environnement : ce que chaque porte vérifie, avec quel outil,
> comment les secrets circulent, et comment justifier une exemption sans affaiblir la porte.

## La carte

```
                 ┌─ secrets ─────┐  gitleaks (historique Git) + Trivy (arborescence)
 poussée / PR ──▶├─ sast ────────┤  Semgrep : OWASP Top 10, Java, TypeScript — bloque sur ERROR
                 ├─ dépendances ─┤  Trivy (pom.xml, package-lock) + npm audit du paquet livré
                 └─ iac ─────────┘  Trivy config : Terraform, Dockerfiles, compositions
                         │ toutes vertes
                         ▼
                      images ─────▶ Trivy image (système + bibliothèques) + SBOM CycloneDX
                         │
                         ▼
                     intrusion ───▶ pile éphémère : suite scriptée FX-AO (15 tests) + ZAP passif
                         │
                         ▼
                       bilan  ────▶ tableau des portes dans le résumé de l'exécution
```

| Fichier | Rôle |
|---|---|
| `.github/workflows/devsecops.yml` | **Orchestrateur** : à chaque poussée sur `develop`, `main`, `feature/**`, à chaque demande de fusion |
| `.github/workflows/_gabarit-secrets.yml` | Porte « secrets », réutilisable (`workflow_call`) |
| `.github/workflows/_gabarit-sast.yml` | Porte « analyse statique » |
| `.github/workflows/_gabarit-dependances.yml` | Porte « dépendances » |
| `.github/workflows/_gabarit-iac.yml` | Porte « infrastructure » |
| `.github/workflows/_gabarit-images.yml` | Porte « images » et liste des composants (SBOM) |
| `.github/workflows/intrusion.yml` | Tests d'intrusion : pile éphémère (défaut, hebdomadaire) ou environnement de démonstration |
| `.github/actions/trivy`, `.github/actions/gitleaks` | **Les outils, épinglés en un seul endroit** : version, options, dossiers ignorés |
| `.github/workflows/deployer-geps.yml` | Le déploiement réemploie les gabarits : secrets avant la construction, images analysées avant la livraison |

Deux niveaux de réemploi :
- Une **action composite** fixe un outil : son image épinglée (`aquasec/trivy:0.58.1`, `ghcr.io/gitleaks/gitleaks:v8.21.2`)
  et sa façon d'être appelé. Changer de version de Trivy se fait en un seul endroit.
- Un **gabarit** fixe une porte : ce qui est analysé, ce qui bloque, ce qui est conservé.

Le déploiement vers Azure appellera les mêmes gabarits : une porte ne dépend pas de l'hébergeur.

## Ce qui bloque, et ce qui informe

| Porte | Bloque sur | Conserve |
|---|---|---|
| Secrets | une fuite **nouvelle** dans l'historique (la référence fige les constats connus) ; un secret élevé ou critique dans l'arborescence | — |
| Analyse statique | un constat de gravité ERROR | SARIF complet (avertissements compris) |
| Dépendances | une vulnérabilité élevée ou critique **qui a un correctif** ; un avis élevé dans le paquet livré au navigateur | SARIF |
| Infrastructure | une mauvaise configuration élevée ou critique non justifiée | SARIF |
| Images | une vulnérabilité élevée ou critique **corrigeable**, dans le système ou une bibliothèque embarquée | SBOM CycloneDX par image, SARIF |
| Intrusion | un test scripté en échec | journal, JUnit, rapport ZAP HTML et JSON |

« Qui a un correctif » : une vulnérabilité sans correctif publié ne se corrige pas en relançant la chaîne. Elle reste
visible dans le SARIF ; la bloquer ne ferait qu'apprendre à ignorer la porte.

## Les secrets

| Règle | En pratique |
|---|---|
| **Aucun secret dans le dépôt** | `.env`, `.env.*`, `*.local*` ignorés ; gitleaks sur tout l'historique à chaque poussée |
| **Un secret par environnement** | Les secrets d'une application vivent dans l'**environnement GitHub** (`geps-demo` : `SUPERADMIN_PASSWORD`, `PERF_COMPTE_PASSWORD`) ; seuls les secrets de livraison propres à un hébergeur sont au niveau du dépôt (`DEPLOY_*` pour OVH) |
| **Privilège minimal** | `permissions: contents: read` par défaut, dans chaque workflow ; aucun jeton d'écriture n'est demandé |
| **Jamais affiché** | Un secret passe par `env:` à la commande qui l'emploie, jamais en argument (il s'afficherait dans `ps` et les journaux) ; les scripts de semis le lisent dans l'environnement |
| **Recopie sans affichage** | `ssh … 'grep …' \| tr -d '\n' \| gh secret set NOM --env ENV` : la valeur passe par un tuyau |
| **Clés de livraison dédiées** | Une clé SSH par pipeline (`github-actions-geps`), révocable seule ; empreintes de l'hôte comparées avant d'être posées (`DEPLOY_KNOWN_HOSTS`, `StrictHostKeyChecking yes`) |
| **Azure : pas de secret du tout** | Connexion par fédération OIDC (`azure/login` avec `client-id`, `tenant-id`, `subscription-id`, permission `id-token: write` sur le seul job qui déploie) ; secrets applicatifs dans Key Vault, lus par l'application avec son identité managée |
| **Rotation** | Un secret recopié dans GitHub se change aux deux endroits ; la procédure de chaque secret est dans `docs/DEPLOIEMENT-GEPS-OVH.md` § 3 |

## Exemptions : toujours justifiées, jamais en bloc

| Outil | Où | Forme |
|---|---|---|
| gitleaks | `.gitleaks.toml` | par **chemin** de fixtures d'essai, jamais par motif ; les constats historiques dans `securite/gitleaks-reference.json` (valeurs masquées) |
| Trivy (IaC) | au-dessus de la ressource | `# raison` puis `#trivy:ignore:<ID>` |
| Semgrep | sur la ligne | `// nosemgrep: <règle> — raison` |

Ajouter une exemption est une décision de sécurité. Elle se relit dans la demande de fusion comme du code.

## État au 22 novembre 2026 (branche `feature/FX-AO-20260810`)

| Porte | Avant | Après |
|---|---|---|
| Secrets | 33 constats dans l'historique : 15 fixtures d'essai, 14 jetons de session d'essai (`e2e/.auth/admin.json`, suivi à tort), 4 secrets de clients d'API de campagnes JMeter locales (`perf/jmeter/resultats/`, suivis à tort) | fixtures déclarées ; les deux familles retirées du suivi ; 18 constats historiques figés ; **0 nouveau** |
| Analyse statique | 0 ERROR, 5 avertissements | 1 corrigé (`FiltrePareFeu` : JSON sérialisé au lieu de concaténé) ; 4 justifiés ci-dessous |
| Dépendances | 1 élevée : `postgresql` 42.7.11 (CVE-2026-54291) | 42.7.13 ; **0** |
| Infrastructure | 4 : `frontend/Dockerfile` sans `USER` ; répartiteur exposé ; SNS sans clé client ; sortie réseau ouverte | `USER 101` explicite ; 3 justifiées en ligne ; **0 non justifiée** |
| Images | serveur : Tomcat 11.0.22 (5 critiques), Spring Web MVC 7.0.8 (2 critiques), Jackson 2.21.4 / 3.1.4 ; interface : Alpine 3.21.3 (OpenSSL, curl, libexpat, zlib, musl — 3 critiques) | Tomcat 11.0.25, Spring 7.0.9, Jackson 2.21.7 / 3.1.7, PostgreSQL 42.7.13 ; nginx 1.29.8 sur Alpine 3.23 + `apk upgrade` (nginx 1.28 publié embarquait 1.28.2, 5 critiques) ; **0** dans les deux images |
| Intrusion | — | 15/15 ; ZAP : 0 échec, 58 contrôles passés |

Avertissements Semgrep justifiés :
- `unencrypted-socket` sur `ServeurClamdDemo` : simulateur de clamd, lié à l'adresse de bouclage seulement.
- `unencrypted-socket` sur `AnalyseurClamd` : clamd ne parle pas TLS ; le service est sur le réseau privé de la pile.
- `dynamic-proxy-host` sur `deploy/geps/nginx-geps.conf` : la variable vaut une constante (`geps-web:8080`). Elle n'existe
  que pour que nginx résolve l'amont à chaque requête.
- `request-host-used` sur le même fichier : `Host` est transmis à un `server_name` exact ; aucun hôte arbitraire n'arrive
  jusque-là.

## Lancer une porte sur le poste

```bash
docker run --rm -v "$PWD:/repo:ro" ghcr.io/gitleaks/gitleaks:v8.21.2 detect --source /repo --config /repo/.gitleaks.toml \
  --baseline-path /repo/securite/gitleaks-reference.json --redact --no-banner
docker run --rm -v "$PWD:/src" -w /src aquasec/trivy:0.58.1 fs --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed \
  --skip-dirs node_modules,frontend/node_modules,e2e/node_modules,backend-java/target /src
docker run --rm -v "$PWD:/src" -w /src aquasec/trivy:0.58.1 config --severity HIGH,CRITICAL /src
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:0.58.1 image --severity HIGH,CRITICAL --ignore-unfixed <image>
docker run --rm -v "$PWD:/src" -w /src semgrep/semgrep:1.95.0 semgrep scan --config p/owasp-top-ten --config p/java \
  --config p/typescript --severity ERROR --error backend-java/src/main frontend/src scripts deploy
securite/intrusion/lancer.sh            # ZAP=1 pour le balayage passif
```

## Ajouter une porte

1. Un gabarit `_gabarit-<porte>.yml` en `workflow_call`, `permissions: contents: read`.
2. Si l'outil est nouveau, une action composite qui l'épingle (`.github/actions/<outil>`).
3. Un job dans `devsecops.yml`, et sa ligne dans le bilan.
4. Une ligne dans le tableau « Ce qui bloque » ci-dessus.
