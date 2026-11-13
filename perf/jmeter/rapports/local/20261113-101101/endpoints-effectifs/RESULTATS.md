# Résultats de performance — local — 2026-11-13 15:13

- **Fichier** : `resultats/local/20261113-101101/endpoints-effectifs.jtl`
- **Environnement** : `local` (http://localhost:4020, 20 utilisateurs, montée 10 s, durée 60 s, réflexion 1000 ms, écritures true)
- **SLA** : p95 < 2000 ms pour 2500 utilisateurs (Devis Santé Québec 26-432315-1226, exigences 8.1–8.2 et annexe C 10.1.1 ; AO-5338 NF)

## Verdict : ✅ objectifs tenus

## Couverture utile

**60 des 154 requêtes (39 %) répondent 2xx.** Les autres sont mesurées sur un refus : un identifiant de chemin absent (404), un corps que la validation refuse (422) ou un conflit (409). Leur latence ne dit rien du travail réel.

| Requête mesurée sur un refus | Code dominant |
|---|---|
| `GET /api/effectifs/absences/{id}` | 404 |
| `GET /api/effectifs/absences/{id}/comblement` | 404 |
| `GET /api/effectifs/absences/{id}/impact` | 404 |
| `GET /api/effectifs/agph/remplacements/{id}` | 404 |
| `GET /api/effectifs/agph/remplacements/{id}/interesses` | 404 |
| `GET /api/effectifs/echanges/{id}` | 404 |
| `GET /api/effectifs/presence/milieux/{id}/code-qr` | 422 |
| `GET /api/effectifs/presence/pointages/{id}` | 404 |
| `GET /api/effectifs/regles/{id}` | 404 |
| `GET /api/effectifs/temps/export-paie` | 422 |
| `GET /api/effectifs/temps/rapports` | 422 |
| `POST /api/effectifs/absences` | 422 |
| `POST /api/effectifs/absences/{id}/annuler` | 405 |
| `POST /api/effectifs/absences/{id}/decisions` | 405 |
| `POST /api/effectifs/absences/{id}/remplacement` | 405 |
| `POST /api/effectifs/agph/campagnes` | 422 |
| `POST /api/effectifs/agph/campagnes/{id}/choix` | 422 |
| `POST /api/effectifs/agph/campagnes/{id}/clore` | 422 |
| `POST /api/effectifs/agph/campagnes/{id}/demarrer` | 422 |
| `PUT /api/effectifs/agph/campagnes/{id}/disponibilites/moi` | 409 |
| `PUT /api/effectifs/agph/campagnes/{id}/disponibilites/{ressourceId}` | 409 |
| `POST /api/effectifs/agph/campagnes/{id}/ouvrir` | 422 |
| `POST /api/effectifs/agph/campagnes/{id}/reviser` | 422 |
| `POST /api/effectifs/agph/campagnes/{id}/revision/choix` | 409 |
| `POST /api/effectifs/agph/remplacements` | 404 |
| `POST /api/effectifs/agph/remplacements/{id}/fermer` | 405 |
| `DELETE /api/effectifs/agph/remplacements/{id}/interet` | 405 |
| `POST /api/effectifs/agph/remplacements/{id}/interet` | 405 |
| `POST /api/effectifs/agph/remplacements/{id}/non-comble` | 405 |
| `POST /api/effectifs/configuration/paquet` | 422 |
| `PUT /api/effectifs/disponibilites/moi/{periodeId}` | 422 |
| `POST /api/effectifs/disponibilites/periodes/{periodeId}/attribuer` | 422 |
| `PUT /api/effectifs/disponibilites/periodes/{periodeId}/ressources/{ressourceId}` | 422 |
| `POST /api/effectifs/disponibilites/periodes/{periodeId}/ressources/{ressourceId}/approuver` | 404 |
| `POST /api/effectifs/echanges` | 404 |
| `POST /api/effectifs/echanges/{id}/accepter` | 405 |
| `POST /api/effectifs/echanges/{id}/annuler` | 405 |
| `POST /api/effectifs/echanges/{id}/refuser` | 405 |
| `POST /api/effectifs/echanges/{id}/rejeter` | 405 |
| `POST /api/effectifs/echanges/{id}/valider` | 405 |
| `POST /api/effectifs/integrations/anciennetes/{uniteCode}` | 409 |
| `POST /api/effectifs/integrations/calendriers/{ressourceId}` | 409 |
| `POST /api/effectifs/integrations/equipages` | 422 |
| `POST /api/effectifs/integrations/horodatages/tirer` | 409 |
| `POST /api/effectifs/integrations/messages/{id}/rejouer` | 404 |
| `POST /api/effectifs/integrations/paie` | 422 |
| `POST /api/effectifs/integrations/relais/{code}` | 422 |
| `POST /api/effectifs/milieux` | 422 |
| `PUT /api/effectifs/milieux/{id}` | 422 |
| `PUT /api/effectifs/milieux/{id}/preuves` | 422 |
| `POST /api/effectifs/perimetres` | 422 |
| `POST /api/effectifs/perimetres/{id}/fermer` | 422 |
| `POST /api/effectifs/planification/equipages` | 404 |
| `POST /api/effectifs/planification/equipages/lot` | 422 |
| `PATCH /api/effectifs/planification/equipages/{id}` | 422 |
| `POST /api/effectifs/planification/periodes` | 422 |
| `POST /api/effectifs/planification/periodes/{id}/etat` | 422 |
| `POST /api/effectifs/planification/places/{id}/attribuer` | 404 |
| `POST /api/effectifs/planification/places/{id}/liberer` | 422 |
| `POST /api/effectifs/planification/postes` | 422 |
| `POST /api/effectifs/planification/postes/lot` | 422 |
| `POST /api/effectifs/planification/postes/publier` | 422 |
| `PUT /api/effectifs/planification/postes/{id}` | 422 |
| `POST /api/effectifs/planification/quotas` | 422 |
| `POST /api/effectifs/planification/quotas/{id}/surcharges` | 422 |
| `POST /api/effectifs/planification/ressources` | 422 |
| `PUT /api/effectifs/planification/ressources/{id}` | 422 |
| `POST /api/effectifs/presence/horodatages` | 422 |
| `POST /api/effectifs/presence/pointages` | 422 |
| `POST /api/effectifs/presence/pointages/{id}/approuver` | 405 |
| `POST /api/effectifs/presence/pointages/{id}/rejeter` | 405 |
| `PUT /api/effectifs/presence/profils` | 422 |
| `DELETE /api/effectifs/presence/profils/{code}` | 404 |
| `POST /api/effectifs/presence/saisies` | 422 |
| `POST /api/effectifs/referentiels/{type}` | 422 |
| `POST /api/effectifs/referentiels/{type}/{code}/fermer` | 422 |
| `POST /api/effectifs/referentiels/{type}/{code}/versions` | 422 |
| `POST /api/effectifs/regles` | 422 |
| `POST /api/effectifs/regles/apercu` | 422 |
| `POST /api/effectifs/regles/simuler` | 422 |
| `POST /api/effectifs/regles/{id}/retirer` | 405 |
| `POST /api/effectifs/regles/{id}/versions` | 405 |
| `PUT /api/effectifs/retention` | 422 |
| `POST /api/effectifs/temps/feuilles/preparer` | 422 |
| `POST /api/effectifs/temps/feuilles/{id}/approuver` | 409 |
| `POST /api/effectifs/temps/feuilles/{id}/regenerer` | 422 |
| `POST /api/effectifs/temps/feuilles/{id}/retourner` | 409 |
| `POST /api/effectifs/temps/feuilles/{id}/segments` | 422 |
| `DELETE /api/effectifs/temps/feuilles/{id}/segments/{segmentId}` | 422 |
| `PUT /api/effectifs/temps/feuilles/{id}/segments/{segmentId}` | 422 |
| `POST /api/effectifs/temps/feuilles/{id}/segments/{segmentId}/confirmer` | 422 |
| `POST /api/effectifs/temps/feuilles/{id}/soumettre` | 422 |
| `POST /api/effectifs/temps/flux` | 422 |
| `DELETE /api/effectifs/temps/flux/{id}` | 404 |

Pour les rendre utiles : semer l'entité qui manque (`preparer.cjs`) ou donner au plan un corps que la validation accepte (`openapiContrats.ts`).

## Total

| Requêtes | Erreurs | Débit | p50 | p95 | p99 | max |
|---:|---:|---:|---:|---:|---:|---:|
| 8999 | 0 (0.00 %) | 74.5 req/s | 2 ms | 4 ms | 8 ms | 136 ms |

## Par requête (SLI mesurés, objectif de la classe)

| Requête | Classe | N | Erreurs | p50 | p95 | p99 | SLO p95 | Verdict |
|---|---|---:|---:|---:|---:|---:|---:|---|
| `POST /api/auth/login` | connexion | 1 | 0.0 % | 68 ms | 68 ms | 68 ms | 800 ms | ✅ |
| `PUT /api/effectifs/integrations/connecteurs/{code}` | ecriture | 59 | 0.0 % | 3 ms | 9 ms | 60 ms | 1000 ms | ✅ |
| `POST /api/effectifs/integrations/equipages` | ecriture | 59 | 0.0 % | 2 ms | 9 ms | 17 ms | 1000 ms | ✅ |
| `POST /api/effectifs/perimetres` | ecriture | 59 | 0.0 % | 2 ms | 8 ms | 23 ms | 1000 ms | ✅ |
| `POST /api/effectifs/temps/feuilles/{id}/soumettre` | ecriture | 57 | 0.0 % | 2 ms | 8 ms | 37 ms | 1000 ms | ✅ |
| `GET /api/effectifs/journal` | lecture | 59 | 0.0 % | 4 ms | 7 ms | 18 ms | 500 ms | ✅ |
| `PUT /api/effectifs/agph/campagnes/{id}/parametres` | ecriture | 59 | 0.0 % | 3 ms | 7 ms | 16 ms | 1000 ms | ✅ |
| `POST /api/effectifs/echanges/{id}/refuser` | ecriture | 59 | 0.0 % | 2 ms | 7 ms | 13 ms | 1000 ms | ✅ |
| `POST /api/effectifs/echanges/{id}/rejeter` | ecriture | 59 | 0.0 % | 2 ms | 7 ms | 14 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/equipages` | ecriture | 59 | 0.0 % | 2 ms | 7 ms | 13 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/periodes/{id}/generer` | ecriture | 59 | 0.0 % | 4 ms | 7 ms | 8 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/quotas/{id}/surcharges` | ecriture | 59 | 0.0 % | 2 ms | 7 ms | 41 ms | 1000 ms | ✅ |
| `POST /api/effectifs/presence/saisies` | ecriture | 59 | 0.0 % | 2 ms | 7 ms | 72 ms | 1000 ms | ✅ |
| `POST /api/effectifs/temps/feuilles/{id}/retourner` | ecriture | 57 | 0.0 % | 3 ms | 7 ms | 14 ms | 1000 ms | ✅ |
| `GET /api/effectifs/agph/campagnes/{id}/postes-disponibles` | lecture | 59 | 0.0 % | 3 ms | 6 ms | 16 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/audit-localisations.csv` | rapport | 59 | 0.0 % | 2 ms | 6 ms | 8 ms | 2000 ms | ✅ |
| `DELETE /api/effectifs/agph/remplacements/{id}/interet` | ecriture | 59 | 0.0 % | 2 ms | 6 ms | 31 ms | 1000 ms | ✅ |
| `POST /api/effectifs/echanges/{id}/valider` | ecriture | 59 | 0.0 % | 2 ms | 6 ms | 42 ms | 1000 ms | ✅ |
| `POST /api/effectifs/integrations/horodatages/tirer` | ecriture | 59 | 0.0 % | 2 ms | 6 ms | 13 ms | 1000 ms | ✅ |
| `POST /api/effectifs/perimetres/{id}/fermer` | ecriture | 59 | 0.0 % | 2 ms | 6 ms | 15 ms | 1000 ms | ✅ |
| `PUT /api/effectifs/planification/postes/{id}` | ecriture | 59 | 0.0 % | 2 ms | 6 ms | 102 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/quotas` | ecriture | 59 | 0.0 % | 2 ms | 6 ms | 9 ms | 1000 ms | ✅ |
| `PUT /api/effectifs/planification/ressources/{id}` | ecriture | 59 | 0.0 % | 2 ms | 6 ms | 8 ms | 1000 ms | ✅ |
| `POST /api/effectifs/presence/horodatages` | ecriture | 59 | 0.0 % | 2 ms | 6 ms | 24 ms | 1000 ms | ✅ |
| `POST /api/effectifs/presence/pointages/{id}/rejeter` | ecriture | 59 | 0.0 % | 3 ms | 6 ms | 7 ms | 1000 ms | ✅ |
| `POST /api/effectifs/referentiels/{type}` | ecriture | 59 | 0.0 % | 2 ms | 6 ms | 136 ms | 1000 ms | ✅ |
| `POST /api/effectifs/referentiels/{type}/{code}/fermer` | ecriture | 59 | 0.0 % | 2 ms | 6 ms | 10 ms | 1000 ms | ✅ |
| `POST /api/effectifs/temps/feuilles/{id}/regenerer` | ecriture | 58 | 0.0 % | 3 ms | 6 ms | 7 ms | 1000 ms | ✅ |
| `PUT /api/effectifs/temps/parametres` | ecriture | 57 | 0.0 % | 3 ms | 6 ms | 14 ms | 1000 ms | ✅ |
| `GET /api/effectifs/planification/periodes/{id}/comblement` | lecture | 59 | 0.0 % | 3 ms | 5 ms | 12 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/referentiels-profils` | lecture | 59 | 0.0 % | 1 ms | 5 ms | 17 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/ressources/{id}/pointages` | lecture | 59 | 0.0 % | 2 ms | 5 ms | 7 ms | 500 ms | ✅ |
| `GET /api/effectifs/temps/feuilles/{id}` | lecture | 59 | 0.0 % | 4 ms | 5 ms | 6 ms | 500 ms | ✅ |
| `POST /api/effectifs/agph/campagnes/{id}/choix` | ecriture | 59 | 0.0 % | 3 ms | 5 ms | 19 ms | 1000 ms | ✅ |
| `PUT /api/effectifs/disponibilites/periodes/{periodeId}/ressources/{ressourceId}` | ecriture | 59 | 0.0 % | 2 ms | 5 ms | 14 ms | 1000 ms | ✅ |
| `POST /api/effectifs/disponibilites/periodes/{periodeId}/ressources/{ressourceId}/approuver` | ecriture | 59 | 0.0 % | 2 ms | 5 ms | 47 ms | 1000 ms | ✅ |
| `POST /api/effectifs/echanges/{id}/accepter` | ecriture | 59 | 0.0 % | 2 ms | 5 ms | 21 ms | 1000 ms | ✅ |
| `POST /api/effectifs/integrations/messages/{id}/rejouer` | ecriture | 59 | 0.0 % | 3 ms | 5 ms | 6 ms | 1000 ms | ✅ |
| `POST /api/effectifs/liaisons/organisation` | ecriture | 59 | 0.0 % | 2 ms | 5 ms | 29 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/equipages/lot` | ecriture | 59 | 0.0 % | 2 ms | 5 ms | 13 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/postes` | ecriture | 59 | 0.0 % | 2 ms | 5 ms | 6 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/postes/publier` | ecriture | 59 | 0.0 % | 2 ms | 5 ms | 6 ms | 1000 ms | ✅ |
| `POST /api/effectifs/presence/pointages` | ecriture | 59 | 0.0 % | 2 ms | 5 ms | 60 ms | 1000 ms | ✅ |
| `POST /api/effectifs/presence/pointages/{id}/approuver` | ecriture | 59 | 0.0 % | 2 ms | 5 ms | 7 ms | 1000 ms | ✅ |
| `DELETE /api/effectifs/presence/profils/{code}` | ecriture | 59 | 0.0 % | 2 ms | 5 ms | 13 ms | 1000 ms | ✅ |
| `POST /api/effectifs/temps/feuilles/{id}/approuver` | ecriture | 58 | 0.0 % | 3 ms | 5 ms | 11 ms | 1000 ms | ✅ |
| `POST /api/effectifs/temps/feuilles/{id}/segments` | ecriture | 57 | 0.0 % | 2 ms | 5 ms | 7 ms | 1000 ms | ✅ |
| `GET /api/effectifs/absences/{id}` | lecture | 59 | 0.0 % | 2 ms | 4 ms | 96 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/campagnes/{id}` | lecture | 59 | 0.0 % | 2 ms | 4 ms | 8 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/remplacements/{id}/interesses` | lecture | 59 | 0.0 % | 2 ms | 4 ms | 6 ms | 500 ms | ✅ |
| `GET /api/effectifs/configuration/paquet` | lecture | 59 | 0.0 % | 3 ms | 4 ms | 48 ms | 500 ms | ✅ |
| `GET /api/effectifs/echanges/{id}` | lecture | 59 | 0.0 % | 2 ms | 4 ms | 6 ms | 500 ms | ✅ |
| `GET /api/effectifs/integrations/connecteurs` | lecture | 59 | 0.0 % | 2 ms | 4 ms | 5 ms | 500 ms | ✅ |
| `GET /api/effectifs/milieux` | lecture | 59 | 0.0 % | 2 ms | 4 ms | 23 ms | 500 ms | ✅ |
| `GET /api/effectifs/planification/periodes/{id}/equipages` | lecture | 59 | 0.0 % | 2 ms | 4 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/audit-localisations` | lecture | 59 | 0.0 % | 2 ms | 4 ms | 28 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/moi` | lecture | 59 | 0.0 % | 3 ms | 4 ms | 10 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/mon-profil` | lecture | 59 | 0.0 % | 1 ms | 4 ms | 8 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/pointages/{id}` | lecture | 59 | 0.0 % | 2 ms | 4 ms | 9 ms | 500 ms | ✅ |
| `GET /api/effectifs/regles` | lecture | 59 | 0.0 % | 2 ms | 4 ms | 21 ms | 500 ms | ✅ |
| `GET /api/effectifs/regles/evaluations` | lecture | 59 | 0.0 % | 2 ms | 4 ms | 8 ms | 500 ms | ✅ |
| `POST /api/effectifs/absences` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 6 ms | 1000 ms | ✅ |
| `POST /api/effectifs/absences/{id}/annuler` | ecriture | 59 | 0.0 % | 3 ms | 4 ms | 7 ms | 1000 ms | ✅ |
| `POST /api/effectifs/absences/{id}/decisions` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 5 ms | 1000 ms | ✅ |
| `POST /api/effectifs/absences/{id}/remplacement` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 4 ms | 1000 ms | ✅ |
| `POST /api/effectifs/agph/campagnes` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 4 ms | 1000 ms | ✅ |
| `POST /api/effectifs/agph/campagnes/{id}/clore` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 16 ms | 1000 ms | ✅ |
| `POST /api/effectifs/agph/campagnes/{id}/demarrer` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 5 ms | 1000 ms | ✅ |
| `POST /api/effectifs/agph/campagnes/{id}/ouvrir` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 13 ms | 1000 ms | ✅ |
| `POST /api/effectifs/agph/campagnes/{id}/reviser` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 6 ms | 1000 ms | ✅ |
| `POST /api/effectifs/agph/campagnes/{id}/revision/choix` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 25 ms | 1000 ms | ✅ |
| `POST /api/effectifs/agph/remplacements` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 7 ms | 1000 ms | ✅ |
| `POST /api/effectifs/agph/remplacements/{id}/fermer` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 7 ms | 1000 ms | ✅ |
| `POST /api/effectifs/agph/remplacements/{id}/interet` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 20 ms | 1000 ms | ✅ |
| `POST /api/effectifs/agph/remplacements/{id}/non-comble` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 18 ms | 1000 ms | ✅ |
| `PUT /api/effectifs/disponibilites/moi/{periodeId}` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 9 ms | 1000 ms | ✅ |
| `POST /api/effectifs/disponibilites/periodes/{periodeId}/attribuer` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 4 ms | 1000 ms | ✅ |
| `POST /api/effectifs/echanges` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 14 ms | 1000 ms | ✅ |
| `POST /api/effectifs/integrations/calendriers/{ressourceId}` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 17 ms | 1000 ms | ✅ |
| `POST /api/effectifs/integrations/paie` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 45 ms | 1000 ms | ✅ |
| `POST /api/effectifs/integrations/relais/{code}` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 12 ms | 1000 ms | ✅ |
| `POST /api/effectifs/milieux` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 7 ms | 1000 ms | ✅ |
| `PUT /api/effectifs/milieux/{id}` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 5 ms | 1000 ms | ✅ |
| `PUT /api/effectifs/milieux/{id}/preuves` | ecriture | 59 | 0.0 % | 3 ms | 4 ms | 11 ms | 1000 ms | ✅ |
| `PATCH /api/effectifs/planification/equipages/{id}` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 7 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/periodes/{id}/etat` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 10 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/places/{id}/attribuer` | ecriture | 59 | 0.0 % | 3 ms | 4 ms | 55 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/places/{id}/liberer` | ecriture | 59 | 0.0 % | 3 ms | 4 ms | 8 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/postes/lot` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 5 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/ressources` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 15 ms | 1000 ms | ✅ |
| `PUT /api/effectifs/presence/profils` | ecriture | 59 | 0.0 % | 2 ms | 4 ms | 6 ms | 1000 ms | ✅ |
| `POST /api/effectifs/referentiels/{type}/{code}/versions` | ecriture | 58 | 0.0 % | 2 ms | 4 ms | 4 ms | 1000 ms | ✅ |
| `POST /api/effectifs/regles/{id}/retirer` | ecriture | 58 | 0.0 % | 2 ms | 4 ms | 29 ms | 1000 ms | ✅ |
| `POST /api/effectifs/regles/{id}/versions` | ecriture | 58 | 0.0 % | 2 ms | 4 ms | 6 ms | 1000 ms | ✅ |
| `POST /api/effectifs/retention/appliquer` | ecriture | 58 | 0.0 % | 2 ms | 4 ms | 4 ms | 1000 ms | ✅ |
| `DELETE /api/effectifs/temps/feuilles/{id}/segments/{segmentId}` | ecriture | 57 | 0.0 % | 2 ms | 4 ms | 8 ms | 1000 ms | ✅ |
| `PUT /api/effectifs/temps/feuilles/{id}/segments/{segmentId}` | ecriture | 57 | 0.0 % | 2 ms | 4 ms | 4 ms | 1000 ms | ✅ |
| `POST /api/effectifs/temps/feuilles/{id}/segments/{segmentId}/confirmer` | ecriture | 57 | 0.0 % | 2 ms | 4 ms | 6 ms | 1000 ms | ✅ |
| `POST /api/effectifs/temps/flux` | ecriture | 57 | 0.0 % | 2 ms | 4 ms | 25 ms | 1000 ms | ✅ |
| `DELETE /api/effectifs/temps/flux/{id}` | ecriture | 57 | 0.0 % | 3 ms | 4 ms | 33 ms | 1000 ms | ✅ |
| `GET /api/effectifs/absences` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 51 ms | 500 ms | ✅ |
| `GET /api/effectifs/absences/{id}/comblement` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 6 ms | 500 ms | ✅ |
| `GET /api/effectifs/absences/{id}/impact` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 3 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/campagnes` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 6 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/campagnes/{id}/disponibilites` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 6 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/campagnes/{id}/disponibilites/moi` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 12 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/remplacements` | lecture | 59 | 0.0 % | 1 ms | 3 ms | 12 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/remplacements/{id}` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 3 ms | 500 ms | ✅ |
| `GET /api/effectifs/disponibilites/moi` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 5 ms | 500 ms | ✅ |
| `GET /api/effectifs/disponibilites/periodes/{periodeId}` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/disponibilites/periodes/{periodeId}/attribution-automatique` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 21 ms | 500 ms | ✅ |
| `GET /api/effectifs/disponibilites/periodes/{periodeId}/ressources/{ressourceId}` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 5 ms | 500 ms | ✅ |
| `GET /api/effectifs/disponibilites/places/{placeId}/candidats` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 5 ms | 500 ms | ✅ |
| `GET /api/effectifs/echanges` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 12 ms | 500 ms | ✅ |
| `GET /api/effectifs/echanges/moi` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 3 ms | 500 ms | ✅ |
| `GET /api/effectifs/echanges/places/{placeId}/demandables` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 6 ms | 500 ms | ✅ |
| `GET /api/effectifs/integrations/messages` | lecture | 59 | 0.0 % | 1 ms | 3 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/liaisons/organisation` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 5 ms | 500 ms | ✅ |
| `GET /api/effectifs/perimetres/moi` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/planification/postes` | lecture | 59 | 0.0 % | 1 ms | 3 ms | 3 ms | 500 ms | ✅ |
| `GET /api/effectifs/planification/quotas` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 8 ms | 500 ms | ✅ |
| `GET /api/effectifs/planification/ressources` | lecture | 59 | 0.0 % | 1 ms | 3 ms | 7 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/milieux/{id}/code-qr` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 6 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/pointages` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 5 ms | 500 ms | ✅ |
| `GET /api/effectifs/referentiels/{type}` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/referentiels/{type}/{code}/historique` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 8 ms | 500 ms | ✅ |
| `GET /api/effectifs/regles/faits` | lecture | 59 | 0.0 % | 1 ms | 3 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/regles/{id}` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 5 ms | 500 ms | ✅ |
| `GET /api/effectifs/retention` | lecture | 59 | 0.0 % | 1 ms | 3 ms | 8 ms | 500 ms | ✅ |
| `GET /api/effectifs/temps/feuilles/{id}/segments/{segmentId}/historique` | lecture | 59 | 0.0 % | 2 ms | 3 ms | 5 ms | 500 ms | ✅ |
| `GET /api/effectifs/temps/moi` | lecture | 59 | 0.0 % | 1 ms | 3 ms | 3 ms | 500 ms | ✅ |
| `GET /api/effectifs/temps/parametres` | lecture | 59 | 0.0 % | 1 ms | 3 ms | 5 ms | 500 ms | ✅ |
| `GET /api/effectifs/temps/rapports` | rapport | 59 | 0.0 % | 1 ms | 3 ms | 6 ms | 2000 ms | ✅ |
| `PUT /api/effectifs/agph/campagnes/{id}/disponibilites/moi` | ecriture | 59 | 0.0 % | 2 ms | 3 ms | 7 ms | 1000 ms | ✅ |
| `PUT /api/effectifs/agph/campagnes/{id}/disponibilites/{ressourceId}` | ecriture | 59 | 0.0 % | 2 ms | 3 ms | 14 ms | 1000 ms | ✅ |
| `POST /api/effectifs/configuration/paquet` | ecriture | 59 | 0.0 % | 2 ms | 3 ms | 5 ms | 1000 ms | ✅ |
| `POST /api/effectifs/echanges/{id}/annuler` | ecriture | 59 | 0.0 % | 2 ms | 3 ms | 5 ms | 1000 ms | ✅ |
| `POST /api/effectifs/integrations/anciennetes/{uniteCode}` | ecriture | 59 | 0.0 % | 2 ms | 3 ms | 9 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/periodes` | ecriture | 59 | 0.0 % | 2 ms | 3 ms | 8 ms | 1000 ms | ✅ |
| `POST /api/effectifs/regles` | ecriture | 58 | 0.0 % | 2 ms | 3 ms | 8 ms | 1000 ms | ✅ |
| `POST /api/effectifs/regles/apercu` | rapport | 58 | 0.0 % | 2 ms | 3 ms | 4 ms | 2000 ms | ✅ |
| `POST /api/effectifs/regles/simuler` | ecriture | 58 | 0.0 % | 2 ms | 3 ms | 4 ms | 1000 ms | ✅ |
| `PUT /api/effectifs/retention` | ecriture | 58 | 0.0 % | 2 ms | 3 ms | 5 ms | 1000 ms | ✅ |
| `POST /api/effectifs/temps/feuilles/preparer` | ecriture | 58 | 0.0 % | 2 ms | 3 ms | 23 ms | 1000 ms | ✅ |
| `GET /api/effectifs/echanges/ressources/{id}/quarts` | lecture | 59 | 0.0 % | 2 ms | 2 ms | 3 ms | 500 ms | ✅ |
| `GET /api/effectifs/ma-ressource` | lecture | 59 | 0.0 % | 1 ms | 2 ms | 2 ms | 500 ms | ✅ |
| `GET /api/effectifs/mon-horaire` | lecture | 59 | 0.0 % | 2 ms | 2 ms | 3 ms | 500 ms | ✅ |
| `GET /api/effectifs/perimetres` | lecture | 59 | 0.0 % | 1 ms | 2 ms | 3 ms | 500 ms | ✅ |
| `GET /api/effectifs/planification/periodes` | lecture | 59 | 0.0 % | 1 ms | 2 ms | 3 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/profils` | lecture | 59 | 0.0 % | 1 ms | 2 ms | 3 ms | 500 ms | ✅ |
| `GET /api/effectifs/referentiels` | lecture | 59 | 0.0 % | 1 ms | 2 ms | 7 ms | 500 ms | ✅ |
| `GET /api/effectifs/temps/export-paie` | rapport | 59 | 0.0 % | 1 ms | 2 ms | 4 ms | 2000 ms | ✅ |
| `GET /api/effectifs/temps/feuilles` | lecture | 59 | 0.0 % | 1 ms | 2 ms | 8 ms | 500 ms | ✅ |
| `GET /api/effectifs/temps/flux` | lecture | 59 | 0.0 % | 1 ms | 2 ms | 7 ms | 500 ms | ✅ |

## Lecture

- **SLI** : ce qui est mesuré (centiles de latence, taux d'erreur, débit). **SLO** : l'objectif par classe d'endpoint (`slo.json`). **SLA** : l'engagement contractuel, p95 < 2 s à 2 500 utilisateurs.
- Les centiles sont calculés sur toutes les observations (rang le plus proche) ; aucune moyenne.
- Une requête en 4xx nommé (validation, droit) compte comme une réponse valide ; un 5xx, un code inattendu ou un transport rompu compte comme une erreur. Une requête trop longue n'est pas une erreur : elle pèse dans les centiles.
