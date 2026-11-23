# Déployer la démonstration VINA GePS sur le VPS OVH

> Pour qui exploite la démonstration publique <https://geps.vinaitsolution.com> : comment elle est installée, comment
> le pipeline la met à jour, comment la remettre en état, la semer, la mesurer et revenir en arrière.
> Les comptes de démonstration et leur mot de passe : [COMPTES-DEMO-GEPS.md](COMPTES-DEMO-GEPS.md).

## 1. Ce qui tourne, et où

Le VPS OVH (`deploy@51.222.86.37`) héberge une douzaine d'applications derrière **un seul Nginx**, `vina_nginx`, qui
termine TLS pour tous les sous-domaines (guide du serveur : `vina solution/docs/DEPLOYMENT-OVH-VPS.md`, §15). VINA GePS
s'y ajoute sans publier aucun port :

```
Internet :443 ──▶ vina_nginx (TLS, certificat geps.vinaitsolution.com)
                     │  réseau Docker partagé « web »
                     ▼
                  geps-web :8080  (nginx de l'interface, relaie /api et /public)
                     │  réseau privé « vina-geps_interne »
                     ▼
                  geps-backend :4000  (Spring Boot 4, Java 25) ── volume vina-geps_donnees
                                                                  (SQLite, fichiers, sauvegardes)
```

| Élément | Emplacement |
|---|---|
| Composition, scripts serveur, bloc Nginx | [`deploy/geps/`](../deploy/geps/) |
| Clone du dépôt sur le serveur | `~/projets/vina-geps` (clé de déploiement `github-geps`, `~/.ssh/config`) |
| Secrets de la démonstration | `~/projets/vina-geps/deploy/geps/.env.geps` (mode 600, jamais versionné) |
| Version en service, version précédente | `deploy/geps/.version-courante`, `.version-precedente` |
| Sauvegardes avant chaque déploiement | `~/sauvegardes/geps/donnees-*.tar.gz` (les 10 dernières) |
| Bloc Nginx | ajouté à `~/vina-cms/nginx/prod.conf` ; copie de référence : [`deploy/geps/nginx-geps.conf`](../deploy/geps/nginx-geps.conf) |
| Certificat | lignée Let's Encrypt `geps.vinaitsolution.com`, distincte du certificat partagé, renouvelée par `vina_certbot` |

Trois choix tiennent au partage du serveur :

- **Les images ne se construisent pas sur le VPS.** GitHub Actions les construit et les charge par SSH
  (`docker save | ssh … docker load`). La compilation Maven ne prend donc ni mémoire ni processeur aux autres
  applications, et aucun registre d'images n'est à ouvrir.
- **Les conteneurs s'appellent `geps-*`.** Sur le réseau `web`, un service `backend` existe déjà
  (freelance-invoicing) : un nom partagé y résoudrait vers l'autre application. Pour la même raison, `/public/` suit
  désormais `API_UPSTREAM` dans `frontend/nginx.conf`.
- **La mémoire est plafonnée** (1 Go pour le Java, 128 Mo pour nginx). Sans plafond, la JVM dimensionne son tas sur
  les 11 Go de l'hôte.

### Le mode démonstration

`.env.geps` pose `DEMONSTRATION_INTEGRATIONS=entra,saml,sms,antivirus`. Les tiers embarqués parlent les vrais
protocoles, sans compte réel : **aucun texto ni courriel ne part**. `api` et `webhooks` se branchent à l'écran
(Admin › Intégrations) : les nommer dans la variable arrête le démarrage. Le mode d'essai `NEXU_ESSAI` n'est **pas**
activé : il ouvre des routes de remise à zéro qui n'ont rien à faire sur une adresse publique.

### Un hôte par organisme (ADR 0094, depuis le 23 novembre 2026)

| Hôte | Ce qu'il sert |
|---|---|
| `geps.vinaitsolution.com` | La vitrine, le choix de l'organisme (`/acceder`, liste déroulante des organismes du superadmin) et la connexion des **seuls comptes globaux** |
| `urgence-sante.geps.vinaitsolution.com` | L'organisme Urgences-santé ; sa racine mène à sa connexion |
| `clincom.geps.vinaitsolution.com` | L'organisme de l'avis FX-AO-20260810 |
| `urgence-sante-demo.geps.vinaitsolution.com` | Urgences-santé — démonstration (maquettes de l'avis) |

`.env.geps` pose `TENANT_RESOLUTION=sous_domaine` et `DOMAINE_RACINE=geps.vinaitsolution.com` ; la composition en tire
`VITRINE_HOTE` pour `geps-web`. Le DNS a une entrée `*.geps.vinaitsolution.com` vers le VPS, mais le certificat nomme
chaque hôte (défi HTTP, sans clés de l'API OVH sur le serveur). **Ajouter un organisme** :

1. son slug, posé par le superadmin (Espace d'exploitation › organisme › « Slug d'URL ») ou, pour un organisme semé,
   par `sousDomaine` dans `e2e/donnees/geps/comptes-demo.json`. Il apparaît aussitôt dans la liste de `/acceder`
   (`GET /api/auth/organismes` : organismes ouverts qui ont un slug) — mais son adresse ne répond qu'après les deux
   étapes suivantes ;
2. son nom au `server_name` des deux blocs geps de `~/vina-cms/nginx/prod.conf` (bloc `listen 80` et bloc 443), édité
   **sans `sed -i`** (voir l'encadré ⚠️ du § 2), puis `docker exec vina_nginx nginx -t` et `nginx -s reload` ;
3. le certificat étendu, puis un nouveau rechargement :

```bash
docker run --rm -v ~/vina-cms/certbot/conf:/etc/letsencrypt -v ~/vina-cms/certbot/www:/var/www/certbot \
  certbot/certbot certonly --webroot -w /var/www/certbot --cert-name geps.vinaitsolution.com --expand \
  -d geps.vinaitsolution.com -d urgence-sante.geps.vinaitsolution.com -d urgence-sante-demo.geps.vinaitsolution.com \
  -d clincom.geps.vinaitsolution.com -d <nouveau>.geps.vinaitsolution.com \
  --email admin@vinaitsolution.com --agree-tos --no-eff-email --non-interactive
```

Retour arrière : `TENANT_RESOLUTION=selecteur` dans `.env.geps`, puis `deploy/geps/deployer.sh $(cat .version-courante)`.

## 2. Première installation (déjà faite le 22 novembre 2026)

À refaire seulement pour réinstaller sur un autre serveur. Chaque étape est rejouable.

```bash
ssh deploy@51.222.86.37
cd ~/projets && git clone git@github-geps:faliherizo/vina-geps.git    # clé de déploiement en lecture
cd vina-geps && git checkout develop
deploy/geps/preparer-serveur.sh       # .env.geps : secrets tirés au hasard, plage du réseau web, mode 600
```

**Nginx central, étape 1 : le défi ACME.** Ajouter `geps.vinaitsolution.com` au `server_name` du bloc `listen 80` de
`~/vina-cms/nginx/prod.conf`, puis lancer `docker exec vina_nginx nginx -t` et `docker exec vina_nginx nginx -s reload`.

> ⚠️ **Éditer `prod.conf` sans changer son inode** (`nano`, `vi`, `cat >>`), **jamais avec `sed -i`.** Le fichier
> est monté seul dans `vina_nginx`. `sed -i` écrit un nouveau fichier : le conteneur continue de lire l'ancien, et
> `nginx -t` puis `reload` réussissent… sur l'ancienne configuration. Le symptôme est que le nouveau site reçoit le
> certificat d'un autre. Si cela arrive, valider la nouvelle configuration dans le conteneur (copie dans `/tmp`,
> `nginx -t -c`), puis lancer `docker restart vina_nginx`, soit une à deux secondes de coupure pour tous les sites.

**Nginx central, étape 2 : le certificat**, dans sa propre lignée. Le certificat partagé n'est pas touché ; un
`--expand` mal dérivé en retirerait d'autres sites.

```bash
docker run --rm -v ~/vina-cms/certbot/conf:/etc/letsencrypt -v ~/vina-cms/certbot/www:/var/www/certbot \
  certbot/certbot certonly --webroot -w /var/www/certbot --cert-name geps.vinaitsolution.com \
  -d geps.vinaitsolution.com --email admin@vinaitsolution.com --agree-tos --no-eff-email --non-interactive
```

**Nginx central, étape 3 : le bloc HTTPS.** Ajouter [`deploy/geps/nginx-geps.conf`](../deploy/geps/nginx-geps.conf)
à la fin de `prod.conf`, puis `nginx -t` et `reload`. L'amont est désigné par une variable : si `geps-web` est arrêté,
le site répond 502 au lieu d'empêcher Nginx de démarrer, ce qui couperait tous les autres sites.

Contrôle : chaque site du serveur répond toujours, et `geps` présente son certificat.

```bash
for n in vinaitsolution.com nexus.vinaitsolution.com contrat.vinaitsolution.com fleet.vinaitsolution.com geps.vinaitsolution.com; do
  printf "%-32s %s\n" $n "$(curl -s -o /dev/null -w '%{http_code}' https://$n/)"; done
```

## 3. Le pipeline

Un seul workflow est propre à OVH : la livraison. Les trois autres sont **séparés, paramétrés par un environnement
GitHub** (`geps-demo` ici) et ne connaissent ni OVH ni Azure. Ils se lancent seuls, à tout moment, contre n'importe
quel environnement déployé. Les ajouter pour Azure : [PIPELINES-ENVIRONNEMENTS.md](PIPELINES-ENVIRONNEMENTS.md).

| Workflow | Déclenchement | Ce qu'il fait |
|---|---|---|
| **Déployer geps (démonstration)**, `deployer-geps.yml` | chaque poussée sur **`develop`**, ou à la main | construit les deux images, les livre au VPS, déploie, puis appelle les deux pipelines suivants |
| **Semer la démonstration**, `semer-demo.yml` | à la main (environnement au choix), ou appelé par le déploiement | (re)sème les organismes de démonstration ; refuse un environnement sans `COMPTES_DEMO=1` |
| **Recette e2e (environnement déployé)**, `recette-e2e.yml` | à la main, ou appelé par le déploiement | essais Playwright en lecture seule contre `APP_URL` |
| **Performance (JMeter)**, `perf.yml` | à la main seulement | campagne JMeter au profil `PERF_PROFIL`, verdict SLO |

### Déployer : les trois jobs

1. **Construire, livrer, déployer.**
   - Construit `vina-geps/backend:<sha12>` et `vina-geps/web:<sha12>` avec le cache de GitHub Actions.
   - Les charge sur le serveur par SSH.
   - Sur le serveur, place `~/projets/vina-geps` sur le commit poussé, pour la composition et les scripts.
   - Lance [`deploy/geps/deployer.sh <sha12>`](../deploy/geps/deployer.sh), qui :
     1. arrête le serveur Java et sauvegarde le volume (`~/sauvegardes/geps`) ;
     2. démarre la nouvelle version ;
     3. attend que le Java **et** le relais nginx répondent à `/api/health` (240 s au plus) ;
     4. en cas d'échec, revient à la version précédente et fait échouer le job ;
     5. ne garde que les images courante et précédente.
2. **Semer** (`semer-demo.yml`, environnement `geps-demo`). Lance `scripts/semer-demo-geps.cjs`, qui est idempotent (§5).
3. **Recette** (`recette-e2e.yml`, environnement `geps-demo`), en lecture seule, contre l'adresse publique :
   - `deploiement-fumee.spec.ts` : le superadmin se connecte, son espace se rend, l'API sert le catalogue ;
   - `deploiement-comptes-demo.spec.ts` : chaque compte publié ouvre une session.
   - `deploiement-fx-ao.spec.ts` : sur `clincom.`, l'administration a exactement les modules et le menu de l'avis
     FX-AO-20260810, chaque écran du menu s'ouvre sans erreur ni violation axe-core, et chaque critère fonctionnel a son
     écran et ses commandes. Il ouvre les écrans au rythme d'une personne (`E2E_PAUSE_MS`, 2,5 s) : la limitation de
     débit du frontal répond 503 à des rechargements enchaînés.
   - `deploiement-fx-ao-fonctionnalites.spec.ts` : sur `clincom.`, chaque fonctionnalité de l'avis jugée sur ses
     valeurs, par l'API et à l'écran, compte par compte (`donnees/fx-ao/fonctionnalites.json`) : le menu et les droits
     des six profils, les groupes de garde et leur modératrice, le congé du Dr Lavoie que la cascade respecte, la prise
     en charge nommée au suivi, la recherche d'un destinataire par son nom. Un organisme semé à moitié y échoue en
     nommant ce qui manque ; l'essai de présence ci-dessus le laissait passer.

La porte retenue est « dès la construction » : le déploiement n'attend pas la CI complète (`ci.yml`, sur `main`).
Une démonstration de `develop` peut donc montrer un défaut que la CI aurait refusé. C'est le prix d'une démonstration
à jour en quelques minutes. Pour attendre la CI, remplacer `on: push` par
`on: workflow_run: { workflows: [CI], types: [completed], branches: [develop] }`, et ajouter `develop` aux branches de
`ci.yml`.

Lancer à la main, ou suivre une exécution :

```bash
gh workflow run "Déployer geps (démonstration)" --ref develop -f semer=true
gh run watch $(gh run list --workflow deployer-geps.yml -L1 --json databaseId -q '.[0].databaseId')
```

### Secrets et variables

Posés par `gh secret set` et `gh variable set` le 22 novembre 2026. Aucune valeur n'est dans le dépôt.

**Environnement GitHub `geps-demo`** (Settings › Environments). C'est ce que les pipelines séparés lisent :

| Nom | Type | Valeur |
|---|---|---|
| `APP_URL` | variable | `https://geps.vinaitsolution.com` |
| `COMPTES_DEMO` | variable | `1` : démonstration aux comptes publiés (semis permis, essai des comptes actif) |
| `PERF_PROFIL` | variable | `geps` → `perf/jmeter/env/geps.properties` |
| `PERF_COMPTE` | variable | `admin` (compte système du premier démarrage) |
| `SUPERADMIN_PASSWORD` | secret | `SUPERADMIN_PASSWORD` de `.env.geps` |
| `PERF_COMPTE_PASSWORD` | secret | `ADMIN_PASSWORD` de `.env.geps` |
| `DEMO_PASSWORD` | secret, facultatif | le mot de passe des comptes publiés, s'il diffère de `Passe!Demo2026` |

**Secrets du dépôt**, pour la livraison au VPS seulement :

| Secret | Contenu | Remplacer |
|---|---|---|
| `DEPLOY_HOST` | `51.222.86.37` | — |
| `DEPLOY_USER` | `deploy` | — |
| `DEPLOY_SSH_KEY` | clé ed25519 privée **dédiée** (`github-actions-geps`), dont la publique est dans `~deploy/.ssh/authorized_keys` | voir ci-dessous |
| `DEPLOY_KNOWN_HOSTS` | clés d'hôte du VPS, comparées à celles d'un poste qui le connaissait | `ssh-keyscan 51.222.86.37 \| gh secret set DEPLOY_KNOWN_HOSTS`, **après** comparaison des empreintes |

Pour changer la clé du pipeline : en générer une, ajouter la publique à `authorized_keys`, poser la privée avec
`gh secret set DEPLOY_SSH_KEY < cle`, puis **retirer l'ancienne ligne** `github-actions-geps` du fichier.

Pour recopier un mot de passe du serveur vers un secret, sans qu'il s'affiche :

```bash
ssh deploy@51.222.86.37 'grep ^SUPERADMIN_PASSWORD= ~/projets/vina-geps/deploy/geps/.env.geps | cut -d= -f2-' \
  | tr -d '\n' | gh secret set SUPERADMIN_PASSWORD --env geps-demo -R faliherizo/vina-geps
```

> Changer `SUPERADMIN_PASSWORD` dans `.env.geps` **ne change pas** le mot de passe d'un superadmin déjà créé : le
> serveur ne l'écrase jamais. Changer le mot de passe à l'écran (ou en base), puis mettre à jour `.env.geps` et le
> secret.

## 4. Exploiter

```bash
ssh deploy@51.222.86.37
cd ~/projets/vina-geps/deploy/geps
cat .version-courante                                 # la version en service (sha du commit)
docker ps --filter name=geps-                         # état et santé
docker logs --tail 100 -f geps-backend                # journal JSON du serveur Java
docker stats --no-stream geps-backend geps-web        # mémoire, sous le plafond de la composition
./deployer.sh --retour-arriere                        # revenir à la version précédente (sauvegarde comprise)
```

**Restaurer une sauvegarde** : arrêter la pile, vider le volume, y décompresser l'archive, puis redémarrer.

```bash
cd ~/projets/vina-geps/deploy/geps
V=$(cat .version-courante)
GEPS_VERSION=$V docker compose -p vina-geps --env-file .env.geps -f docker-compose.yml stop
docker run --rm -v vina-geps_donnees:/donnees -v ~/sauvegardes/geps:/s alpine \
  sh -c 'rm -rf /donnees/* && tar xzf /s/<archive>.tar.gz -C /donnees && chown -R 100:101 /donnees'
./deployer.sh "$V"
```

Le serveur tient aussi ses propres sauvegardes, dans le volume (`SAUVEGARDES_INTERVALLE_MIN=60`). Elles sont visibles
à l'écran Superadmin › Exploitation.

## 5. Les données de démonstration

[`scripts/semer-demo-geps.cjs`](../scripts/semer-demo-geps.cjs) sème trois organismes, chacun par son script
d'origine, en passant uniquement par l'API publique :

| Organisme | Script | Rejouer |
|---|---|---|
| `Urgences-santé` : un compte par rôle, chaque personne reliée à sa ressource | `creer-organisme-par-defaut.cjs` | **repris** : il complète ce qui manque et remet chaque mot de passe à celui de la démonstration |
| `Urgences-santé — démonstration (date)` : le contenu des maquettes 26-015-DO | `semer-urgences-sante.cjs` | sauté s'il existe ; `--forcer` en sème un nouvel exemplaire |
| `Santé Québec Laurentides — démonstration FX-AO (date)` : alertes, six sites | `semer-fx-ao.cjs` | idem |

Tous les comptes reçoivent `DEMO_PASSWORD`, par défaut `Passe!Demo2026`. Ce mot de passe est publié dans
[COMPTES-DEMO-GEPS.md](COMPTES-DEMO-GEPS.md). Sans `DEMO_PASSWORD`, `creer-organisme-par-defaut.cjs` garde son
comportement d'organisme réel : un mot de passe tiré au hasard par compte.

```bash
# Depuis le pipeline : Actions › Semer la démonstration › Run (environnement, organisme, forcer)
# Depuis un poste (Node 22) :
SUPERADMIN_PASSWORD='…' node scripts/semer-demo-geps.cjs --api https://geps.vinaitsolution.com
SUPERADMIN_PASSWORD='…' node scripts/semer-demo-geps.cjs --api https://geps.vinaitsolution.com --seulement fx-ao --forcer
```

Le serveur n'a pas Node : on sème depuis le pipeline ou depuis un poste. Les scripts respectent la limite de débit du
frontal, avec une pause entre deux appels (`SEMIS_PAUSE_MS`, 70 ms par défaut).

**Ajouter des données** : enrichir le jeu de données du semis concerné (`e2e/donnees/urgences-sante/demonstration.json`,
`e2e/donnees/fx-ao/demonstration.json`, `scripts/lib/organisme-par-defaut/donnees.cjs`). Si l'ajout crée un compte,
l'ajouter aussi à [`e2e/donnees/geps/comptes-demo.json`](../e2e/donnees/geps/comptes-demo.json) et au tableau de
COMPTES-DEMO-GEPS.md. Lancer ensuite **Semer la démonstration** avec `forcer` pour les deux organismes horodatés.

## 6. Essais de bout en bout contre la démonstration

Un essai qui vise un déploiement **ne fait que lire** : se connecter, consulter, jamais écrire
(`e2e/global-setup.ts`). Trois essais existent pour cela :

```bash
# Le pipeline : Actions › Recette e2e (environnement déployé) › Run (environnement, essais)
gh workflow run recette-e2e.yml -f environnement=geps-demo
# Depuis un poste :
cd e2e
E2E_BASE_URL=https://geps.vinaitsolution.com E2E_SUPERADMIN_PASSWORD='…' E2E_COMPTES_DEMO=1 \
  npx playwright test --project=e2e tests/e2e/deploiement-fumee.spec.ts tests/e2e/deploiement-comptes-demo.spec.ts \
  tests/e2e/deploiement-fx-ao.spec.ts tests/e2e/deploiement-fx-ao-fonctionnalites.spec.ts
```

Ajouter un essai à la recette : il doit **seulement lire** et se sauter sans `E2E_BASE_URL`. On le passe ensuite dans
l'entrée `essais` du workflow, ou on l'ajoute à sa valeur par défaut.

La campagne complète (`backend-java/scripts/campagne-e2e.sh`, 67 fichiers) **ne vise pas** la démonstration : elle
remet la base à zéro entre deux essais, ce qui exige le mode `NEXU_ESSAI` et une base jetable. Elle tourne dans
`ci.yml`, contre un serveur Java éphémère.

## 7. Performance

`perf.yml` (Actions › **Performance (JMeter)** › Run, environnement `geps-demo`) :

1. installe JMeter 5.6.3 ;
2. écrit le compte de campagne depuis `PERF_COMPTE` / `PERF_COMPTE_PASSWORD` ;
3. sème ses données dans l'établissement « Charge … », réutilisé d'une campagne à l'autre (`PERF_TENANT`) ;
4. joue le plan choisi (`scenarios`, `endpoints`, `operations`, ou un fichier `.jmx`), au profil `PERF_PROFIL`, contre
   `APP_URL` ;
5. rend le verdict de `perf/jmeter/analyser.cjs`. Le job échoue si un objectif est manqué.

Le tableau de bord HTML et `RESULTATS.md` sont dans l'artefact `perf-geps-demo`.

```bash
gh workflow run perf.yml -f environnement=geps-demo -f plan=scenarios -f utilisateurs=10 -f duree=180
```

À la main, depuis un poste qui a JMeter et zsh :

```bash
printf 'username,password,tenantId,role\nadmin,%s,,admin\n' "$MDP_ADMIN" > perf/jmeter/donnees/comptes-geps.local.csv
node perf/jmeter/preparer.cjs --env geps --lignes 3
perf/jmeter/lancer.sh geps scenarios -Jtenant=$(cat perf/jmeter/donnees/tenant-geps.txt)
```

**Ce que la mesure vaut.**

- Un injecteur unique n'a qu'une adresse, et le frontal la plafonne à 20 requêtes par seconde (rafale de 40). Au-delà,
  la campagne mesure des 429, pas l'application. `perf/jmeter/env/geps.properties` reste en deçà : 10 utilisateurs,
  1 s de réflexion.
- Le VPS est partagé : une latence y dépend aussi des voisins.
- Ces chiffres décrivent la **démonstration**. L'engagement de l'appel d'offres (2 500 utilisateurs, p95 < 2 s) se
  mesure sur l'infrastructure cible (`perf/jmeter/README.md`).
- Lire la **couverture utile** (part de 2xx) avant toute latence : un 404 répond vite.
- Première campagne (22 novembre 2026, sonde de lecture, 5 utilisateurs, 60 s) : 252 requêtes, 0 % d'erreur, 100 % en
  2xx, p95 de 45 ms. Le verdict reste rouge sur **une seule** connexion à 1 434 ms (objectif 800 ms) : la connexion
  est mesurée une fois par utilisateur, à froid (BCrypt, JIT). Allonger la campagne avant de conclure, plutôt que
  desserrer le seuil.

## 8. Dépannage

| Symptôme | Cause probable | Geste |
|---|---|---|
| `geps` reçoit le certificat `vinaitsolution.com` | `prod.conf` édité avec `sed -i` : le conteneur lit l'ancien fichier | §2, l'encadré ⚠️ |
| 502 sur geps | `geps-web` arrêté ou en cours de redémarrage | `docker ps --filter name=geps-`, `deployer.sh $(cat .version-courante)` |
| Le déploiement échoue sur « image absente » | livraison interrompue | relancer le workflow |
| « ne répond pas après 240 s » puis retour arrière | le Java ne démarre pas (variable refusée, migration) | le job affiche les 80 dernières lignes du journal ; corriger `.env.geps` ou le code |
| `git fetch` échoue sur le serveur | clé de déploiement `github-geps` retirée de GitHub | `ssh -T github-geps` sur le serveur |
| Un compte de démonstration est refusé | mot de passe changé à l'écran, base recréée | Actions › Semer la démonstration |
| 429 pendant une campagne | limite de débit par adresse | baisser `utilisateurs` ou monter `reflexion` |
| Les visiteurs partagent tous la même limite de débit | `GEPS_PROXIES_CONFIANCE` ne couvre plus le réseau `web` | `docker network inspect web`, corriger `.env.geps`, redéployer |
