# ADR 0094 — La démonstration geps par sous-domaine : un hôte par organisme

- **Statut** : accepté, le 23 novembre 2026.
- **Décideurs** : équipe VINA, à la demande du responsable produit.
- **Applique** : ADR 0033 (le tenant vient de l'hôte ; écrit avant la séparation des dépôts, sa règle vit dans
  `partage/securite/ResolutionHote.java`) à la démonstration publique du VPS OVH.
- **Concerne** :
  - le déploiement : `deploy/geps/` (`env.geps.exemple`, `docker-compose.yml`, `nginx-geps.conf`), le certificat de la
    lignée `geps.vinaitsolution.com` ;
  - l'interface : `frontend/nginx.conf` (`VITRINE_HOTE`, `/acceder`), `frontend/public/vitrine/acceder.html`, le lien
    « Choisir votre organisme » de la connexion des comptes globaux ;
  - les semis : `scripts/lib/hote-etablissement.cjs`, `scripts/semer-demo-geps.cjs`, `e2e/donnees/geps/comptes-demo.json`
    (`sousDomaine`) ;
  - la recette : `e2e/tests/e2e/deploiement-comptes-demo.spec.ts` ; la performance : `perf/jmeter/preparer.cjs`.

## Contexte

La démonstration servait tous les organismes sur un seul hôte, `geps.vinaitsolution.com`, en mode `selecteur` : la
connexion présentait la liste de tous les organismes, à n'importe qui. Le responsable produit veut :

- `urgence-sante.geps.vinaitsolution.com` pour Urgences-santé et `clincom.geps.vinaitsolution.com` pour l'organisme de
  l'avis FX-AO-20260810 ;
- une page qui choisit l'organisme, derrière « Accéder à l'application » de la vitrine ;
- **plus aucun choix d'organisme** à la connexion de l'hôte commun, réservée à l'espace d'exploitation (superadmin).

Le serveur sait déjà le faire (ADR 0033, `TENANT_RESOLUTION=sous_domaine`). Il ne manquait que le déploiement, la page
de choix et les outils qui se connectaient en passant l'organisme dans le corps de la requête.

## Décision

1. **Le serveur passe en `sous_domaine`** avec `DOMAINE_RACINE=geps.vinaitsolution.com`. L'hôte désigne l'organisme ;
   la racine n'ouvre que les comptes globaux ; la liste anonyme des organismes répond 404.
2. **Le sous-domaine est le slug de l'organisme.** `comptes-demo.json` le déclare (`sousDomaine`) ; le semis de la
   démonstration le pose. Il va à l'exemplaire le plus récent d'un organisme horodaté ; l'ancien le cède
   (`<slug>-<début de son id>`), car un slug est unique.
3. **Le choix de l'organisme est une liste déroulante** de la vitrine (`/acceder`), alimentée par la route publique
   `GET /api/auth/organismes` (révision du 23 novembre, à la demande du responsable produit) : les organismes que le
   superadmin crée et gère, **ouverts** (actifs ou à l'essai) et **pourvus d'un sous-domaine**. La route ne rend que le
   nom, le slug et l'adresse de connexion — ni identifiant, ni statut, ni pays ; en mode sélecteur, elle répond 404.
   La page ne suit qu'une adresse rendue par le serveur.
4. **La racine d'un hôte d'organisme mène à sa connexion**, pas à la vitrine : `frontend/nginx.conf` compare l'hôte à
   `VITRINE_HOTE` (la racine du déploiement). `VITRINE_HOTE=*`, le défaut, garde la vitrine partout.
5. **Un compte d'organisme se connecte sur l'hôte de son organisme, même dans un script.** Les clients de semis
   (`hote-etablissement.cjs`) lisent le mode (`/api/auth/deploiement`) et le slug (liste du superadmin) ; un jeton
   obtenu ainsi reste valable sur la racine, seule la connexion change d'adresse.
6. **Un certificat par nom, dans la lignée geps.** Trois noms sont ajoutés au certificat `geps.vinaitsolution.com` par le
   défi HTTP. Le certificat partagé des autres sites n'est pas touché.

## Options écartées

- **Garder le mode sélecteur et cacher la liste dans l'interface.** L'hôte n'aurait rien prouvé : un compte de clincom
  aurait pu se connecter sur urgence-sante en posant `tenantId` dans le corps. Masquer n'est pas cloisonner.
- **Un certificat générique `*.geps.vinaitsolution.com`.** Il exige le défi DNS, donc des clés de l'API OVH sur le
  serveur partagé. Le nombre d'organismes de la démonstration est petit et choisi : trois noms suffisent.
- **Une page de choix écrite en dur** (première version du 23 novembre). Chaque organisme ajouté par le superadmin
  demandait une modification de la page et un déploiement. Le responsable produit a préféré la liste vivante, et
  accepte que les **noms** des organismes ouverts soient publics ; la liste complète (`/api/auth/tenants`, avec
  identifiants et statuts) reste fermée à l'anonyme.

## Conséquences

- Un organisme sans slug n'est joignable par personne, et n'apparaît pas dans la liste. Un organisme que le superadmin
  crée avec un slug y apparaît aussitôt ; **son adresse ne répond en HTTPS qu'une fois son nom ajouté au certificat et
  au `server_name` du Nginx central** (le certificat nomme chaque hôte). Un certificat générique `*.geps…` lèverait
  cette étape, au prix de clés de l'API OVH sur le serveur (défi DNS).
- La campagne de performance retrouve son organisme par la liste **authentifiée** du compte global. Le volume de quarts
  (`--quarts`) connecte une approbatrice d'organisme : sur la démonstration, il exigerait un slug à l'organisme de
  campagne ; le pipeline ne s'en sert pas.
- Les essais de recette joignent chaque organisme par son hôte, et vérifient que la racine refuse un compte d'organisme
  et ne publie plus la liste.
- Retour arrière : `TENANT_RESOLUTION=selecteur` dans `.env.geps`, puis redéploiement. Les slugs posés restent sans
  effet en mode sélecteur.
