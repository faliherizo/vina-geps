# Pipelines de recette, de semis et de performance : un environnement de plus (Azure)

> Pour qui ajoute un environnement déployé (recette Azure, préproduction, production) et veut y lancer les essais
> e2e, le semis de démonstration ou la campagne de performance, sans toucher aux workflows.

## Le principe : le workflow ne sait pas où il va

Trois workflows sont **séparés du déploiement** et ne connaissent aucun hébergeur :

| Workflow | Fichier | Rôle |
|---|---|---|
| Recette e2e (environnement déployé) | `.github/workflows/recette-e2e.yml` | essais Playwright **en lecture seule** contre l'environnement |
| Semer la démonstration | `.github/workflows/semer-demo.yml` | organismes de démonstration aux comptes publiés, **démonstrations seulement** |
| Performance (JMeter) | `.github/workflows/perf.yml` | campagne JMeter, verdict SLO ; jamais automatique |

Chacun prend une entrée `environnement`, qui est le nom d'un **environnement GitHub** (Settings › Environments). Tout
ce qui diffère d'un hébergement à l'autre vit dans cet environnement : l'adresse, les secrets, le profil de charge.
Changer d'hébergeur, ou en ajouter un, revient donc à **créer un environnement**. Aucun workflow ne change.

Les deux premiers sont aussi **réutilisables** (`workflow_call`). Un workflow de déploiement les appelle après sa
livraison, comme le fait `deployer-geps.yml` pour OVH :

```yaml
  recette:
    needs: livrer
    uses: ./.github/workflows/recette-e2e.yml
    with: { environnement: azure-recette }
    secrets: inherit
```

La livraison, elle, est propre à l'hébergeur : OVH (`deployer-geps.yml`, SSH et `docker load`) ou Azure (Terraform
`infra/terraform/aws` aujourd'hui, l'équivalent Azure à venir). C'est le seul fichier à écrire pour Azure.

## Les valeurs d'un environnement

| Nom | Type | Lu par | Rôle |
|---|---|---|---|
| `APP_URL` | variable | les trois | L'adresse publique, sans `/` final : `https://geps.vinaitsolution.com`, `https://<app>.azurewebsites.net`, `https://<domaine>` |
| `SUPERADMIN_PASSWORD` | secret | recette, semis | Le superadmin **de cet environnement** |
| `COMPTES_DEMO` | variable | recette, semis | `1` seulement pour une démonstration. Sans elle, l'essai des comptes publiés se saute et le semis **refuse** de s'exécuter |
| `DEMO_PASSWORD` | secret | recette, semis | Facultatif : le mot de passe des comptes publiés, s'il diffère de `Passe!Demo2026` |
| `PERF_PROFIL` | variable | performance | Le profil `perf/jmeter/env/<profil>.properties` : charge, seuils, écritures permises ou non |
| `PERF_COMPTE` | variable | performance | Le compte système de la campagne (défaut `admin`) |
| `PERF_COMPTE_PASSWORD` | secret | performance | Son mot de passe |

Le **profil** dit *combien* charger. L'**environnement** dit *où* : `perf.yml` substitue `APP_URL` à l'hôte du profil,
pour JMeter (`-Jprotocole`, `-Jhote`, `-Jport`) comme pour `preparer.cjs` (`PERF_URL`). Un même profil peut donc
servir deux hébergements, et un profil en lecture seule (`ecritures=false`, comme `prod`) ne sème jamais rien.

## Ajouter l'environnement de recette Azure

```bash
R=faliherizo/vina-geps
gh api -X PUT repos/$R/environments/azure-recette
gh variable set APP_URL      --env azure-recette -R $R -b "https://<application>.azurewebsites.net"
gh variable set PERF_PROFIL  --env azure-recette -R $R -b "preprod"        # ou un profil créé pour Azure
gh variable set PERF_COMPTE  --env azure-recette -R $R -b "admin"
# Les secrets se lisent dans Key Vault et passent par un tuyau : ils ne s'affichent jamais.
az keyvault secret show --vault-name <coffre> -n superadmin-password --query value -o tsv \
  | tr -d '\n' | gh secret set SUPERADMIN_PASSWORD --env azure-recette -R $R
az keyvault secret show --vault-name <coffre> -n admin-password --query value -o tsv \
  | tr -d '\n' | gh secret set PERF_COMPTE_PASSWORD --env azure-recette -R $R
# COMPTES_DEMO : ne pas le poser, sauf si cet environnement est lui-même une démonstration publique.
```

Puis lancer la recette et, si besoin, la performance :

```bash
gh workflow run recette-e2e.yml -f environnement=azure-recette
gh workflow run perf.yml -f environnement=azure-recette -f plan=scenarios
```

Points à vérifier sur Azure avant la première campagne :

- **Débit par adresse.** Le frontal nginx plafonne à 20 requêtes par seconde par adresse. Derrière Application Gateway
  ou Front Door, il doit lire l'adresse réelle : `API_PROXIES_CONFIANCE` doit couvrir la plage du mandataire.
  Sinon, tous les utilisateurs partagent une seule limite, et une campagne mesure des 429.
- **Protections réseau.** Les exécuteurs GitHub hébergés n'ont pas d'adresse fixe. Si l'environnement est fermé par
  une liste d'adresses, utiliser un exécuteur auto-hébergé dans le réseau virtuel, en remplaçant
  `runs-on: ubuntu-latest` par une entrée `runner`, ou ouvrir temporairement la règle.
- **Charge réelle.** L'engagement de l'appel d'offres (2 500 utilisateurs, p95 < 2 s) ne se mesure pas depuis un
  seul exécuteur. Il faut des injecteurs répartis : voir `perf/jmeter/README.md`.

## Protéger un environnement

Settings › Environments › *nom* :

- **Required reviewers** : un relecteur approuve chaque exécution. À poser au moins sur la production, et sur tout
  environnement où `perf.yml` pourrait gêner des utilisateurs réels.
- **Deployment branches** : limiter `geps-demo` à `develop`, la production à `main`.

## Essais qui visent un environnement

Seuls les essais qui **lisent** peuvent viser un environnement déployé. Ils se sautent sans `E2E_BASE_URL`
(`e2e/global-setup.ts`). Aujourd'hui :

- `tests/e2e/deploiement-fumee.spec.ts` : connexion du superadmin, espace superadmin, routes de l'API ;
- `tests/e2e/deploiement-comptes-demo.spec.ts` : chaque compte publié ouvre une session (`COMPTES_DEMO=1` seulement).

La campagne complète remet la base à zéro entre deux essais. Elle ne vise **jamais** un environnement déployé : elle
tourne dans `ci.yml`, contre un serveur éphémère.
