# Urgences-santé — gestion des effectifs

Produit issu du module « Urgences-santé » de Nexu VINA (AO 26-015-DO, Corporation d'urgences-santé),
séparé pour vivre dans son propre dépôt : un serveur **Java seul** (Spring Boot 4, Java 25), une
interface React, une console superadmin réduite, et leur outillage d'essai.

## Démarrer

```bash
docker compose up -d --build                     # serveur Java + nginx → http://localhost:8095 (US_WEB_PORT pour un autre port)
```

La base SQLite est créée par Liquibase au premier démarrage
(`backend-java/src/main/resources/db/changelog`). Comptes amorcés : `admin` / `superadmin`
(mots de passe par `ADMIN_PASSWORD` et `SUPERADMIN_PASSWORD`).

## Vérifier

| Porte | Commande |
|---|---|
| Serveur : architecture, normes, essais, couverture ≥ 96 % par classe | `cd backend-java && mvn verify` |
| Interface : types, essais | `cd frontend && npx tsc --noEmit && npx vitest run` |
| Accessibilité statique | `node scripts/audit-accessibilite.js` |
| Écrans : WCAG 2.2 AA (axe-core), géométrie à quatre largeurs | `cd e2e && E2E_NO_BACKEND=1 npx playwright test --project=interface` |
| Parcours de bout en bout contre le serveur Java | `backend-java/scripts/campagne-e2e.sh` |
| Performance (JMeter) | voir [perf/jmeter/README.md](perf/jmeter/README.md) |

La CI (`.github/workflows/ci.yml`) joue les quatre premières portes et les parcours à chaque poussée.

## Documents

| Document | Contenu |
|---|---|
| [PLANIFICATION-SEPARATION.md](PLANIFICATION-SEPARATION.md) | Faisabilité, cible, coupures, phases, décisions, **état de la séparation et dettes** |
| [INVENTAIRE.md](INVENTAIRE.md) | Ce qui a été copié du dépôt d'origine |
| [docs/urgences-sante/](docs/urgences-sante/) | Architecture, guide d'administration, essais d'acceptation, sécurité, formation |
| [docs/26-015-DO_AO/](docs/26-015-DO_AO/) | Appel d'offres, grille, matrice de couverture, questions et addenda |

## Provenance

Copie du 13 novembre 2026 depuis `~/Projects/freelance-invoicing`, branche `features/urgence-sante`,
commit `52087a5` (« Finalisation: urgence sante et front end »), sans l'historique Git (décision D4).
Le dépôt d'origine n'a pas été modifié ; le sort du module là-bas (décision D5) reste à trancher.
