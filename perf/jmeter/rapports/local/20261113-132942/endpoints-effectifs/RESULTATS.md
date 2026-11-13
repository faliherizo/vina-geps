# Résultats de performance — local — 2026-11-13 18:32

- **Fichier** : `resultats/local/20261113-132942/endpoints-effectifs.jtl`
- **Environnement** : `local` (http://localhost:4020, 20 utilisateurs, montée 10 s, durée 60 s, réflexion 1000 ms, écritures true)
- **SLA** : p95 < 2000 ms pour 2500 utilisateurs (Devis Santé Québec 26-432315-1226, exigences 8.1–8.2 et annexe C 10.1.1 ; AO-5338 NF)

## Verdict : ✅ objectifs tenus

## Couverture utile

**79 des 154 requêtes (51 %) répondent 2xx.** Les autres sont mesurées sur un refus : un identifiant de chemin absent (404), un corps que la validation refuse (422) ou un conflit (409). Leur latence ne dit rien du travail réel.

| Requête mesurée sur un refus | Code dominant |
|---|---|
| `GET /api/effectifs/agph/remplacements/{id}` | 404 |
| `GET /api/effectifs/agph/remplacements/{id}/interesses` | 404 |
| `GET /api/effectifs/echanges/{id}` | 404 |
| `GET /api/effectifs/presence/milieux/{id}/code-qr` | 422 |
| `GET /api/effectifs/temps/export-paie` | 422 |
| `GET /api/effectifs/temps/rapports` | 422 |
| `POST /api/effectifs/absences` | 409 |
| `POST /api/effectifs/absences/{id}/decisions` | 422 |
| `POST /api/effectifs/absences/{id}/remplacement` | 422 |
| `POST /api/effectifs/agph/campagnes` | 422 |
| `POST /api/effectifs/agph/campagnes/{id}/choix` | 409 |
| `POST /api/effectifs/agph/campagnes/{id}/demarrer` | 422 |
| `PUT /api/effectifs/agph/campagnes/{id}/disponibilites/moi` | 409 |
| `PUT /api/effectifs/agph/campagnes/{id}/disponibilites/{ressourceId}` | 409 |
| `POST /api/effectifs/agph/campagnes/{id}/ouvrir` | 422 |
| `POST /api/effectifs/agph/campagnes/{id}/reviser` | 422 |
| `POST /api/effectifs/agph/campagnes/{id}/revision/choix` | 409 |
| `POST /api/effectifs/agph/remplacements` | 404 |
| `POST /api/effectifs/agph/remplacements/{id}/interet` | 405 |
| `POST /api/effectifs/configuration/paquet` | 422 |
| `PUT /api/effectifs/disponibilites/moi/{periodeId}` | 422 |
| `POST /api/effectifs/disponibilites/periodes/{periodeId}/attribuer` | 422 |
| `PUT /api/effectifs/disponibilites/periodes/{periodeId}/ressources/{ressourceId}` | 422 |
| `POST /api/effectifs/disponibilites/periodes/{periodeId}/ressources/{ressourceId}/approuver` | 404 |
| `POST /api/effectifs/echanges` | 422 |
| `POST /api/effectifs/echanges/{id}/accepter` | 405 |
| `POST /api/effectifs/echanges/{id}/valider` | 405 |
| `POST /api/effectifs/integrations/anciennetes/{uniteCode}` | 409 |
| `POST /api/effectifs/integrations/calendriers/{ressourceId}` | 409 |
| `POST /api/effectifs/integrations/equipages` | 422 |
| `POST /api/effectifs/integrations/horodatages/tirer` | 409 |
| `POST /api/effectifs/integrations/messages/{id}/rejouer` | 404 |
| `POST /api/effectifs/integrations/paie` | 422 |
| `POST /api/effectifs/integrations/relais/{code}` | 422 |
| `PUT /api/effectifs/milieux/{id}/preuves` | 422 |
| `POST /api/effectifs/perimetres` | 422 |
| `POST /api/effectifs/planification/equipages` | 404 |
| `POST /api/effectifs/planification/equipages/lot` | 422 |
| `PATCH /api/effectifs/planification/equipages/{id}` | 422 |
| `POST /api/effectifs/planification/periodes/{id}/etat` | 422 |
| `POST /api/effectifs/planification/postes/publier` | 422 |
| `POST /api/effectifs/planification/quotas` | 422 |
| `POST /api/effectifs/planification/quotas/{id}/surcharges` | 422 |
| `POST /api/effectifs/presence/horodatages` | 422 |
| `POST /api/effectifs/presence/pointages` | 404 |
| `POST /api/effectifs/presence/pointages/{id}/approuver` | 409 |
| `POST /api/effectifs/presence/saisies` | 404 |
| `POST /api/effectifs/referentiels/{type}/{code}/versions` | 422 |
| `POST /api/effectifs/regles/apercu` | 422 |
| `POST /api/effectifs/regles/simuler` | 422 |
| `POST /api/effectifs/regles/{id}/versions` | 422 |
| `PUT /api/effectifs/retention` | 422 |
| `POST /api/effectifs/temps/feuilles/{id}/approuver` | 409 |
| `POST /api/effectifs/temps/feuilles/{id}/retourner` | 422 |
| `POST /api/effectifs/temps/feuilles/{id}/segments` | 422 |
| `PUT /api/effectifs/temps/feuilles/{id}/segments/{segmentId}` | 422 |
| `POST /api/effectifs/temps/feuilles/{id}/segments/{segmentId}/confirmer` | 422 |
| `POST /api/effectifs/temps/feuilles/{id}/soumettre` | 422 |
| `POST /api/effectifs/temps/flux` | 422 |
| `POST /api/effectifs/absences/{id}/annuler` | 422 |
| `POST /api/effectifs/agph/campagnes/{id}/clore` | 422 |
| `POST /api/effectifs/agph/remplacements/{id}/fermer` | 405 |
| `DELETE /api/effectifs/agph/remplacements/{id}/interet` | 405 |
| `POST /api/effectifs/agph/remplacements/{id}/non-comble` | 405 |
| `POST /api/effectifs/echanges/{id}/annuler` | 405 |
| `POST /api/effectifs/echanges/{id}/refuser` | 405 |
| `POST /api/effectifs/echanges/{id}/rejeter` | 405 |
| `POST /api/effectifs/perimetres/{id}/fermer` | 422 |
| `POST /api/effectifs/planification/places/{id}/liberer` | 422 |
| `POST /api/effectifs/presence/pointages/{id}/rejeter` | 409 |
| `DELETE /api/effectifs/presence/profils/{code}` | 404 |
| `POST /api/effectifs/referentiels/{type}/{code}/fermer` | 422 |
| `POST /api/effectifs/regles/{id}/retirer` | 422 |
| `DELETE /api/effectifs/temps/feuilles/{id}/segments/{segmentId}` | 422 |
| `DELETE /api/effectifs/temps/flux/{id}` | 404 |

Pour les rendre utiles : semer l'entité qui manque (`preparer.cjs`) ou donner au plan un corps que la validation accepte (`openapiContrats.ts`).

## Total

| Requêtes | Erreurs | Débit | p50 | p95 | p99 | max |
|---:|---:|---:|---:|---:|---:|---:|
| 3517 | 0 (0.00 %) | 19.1 req/s | 3 ms | 7 ms | 13 ms | 63 ms |

## Par requête (SLI mesurés, objectif de la classe)

| Requête | Classe | N | Erreurs | p50 | p95 | p99 | SLO p95 | Verdict |
|---|---|---:|---:|---:|---:|---:|---:|---|
| `POST /api/auth/login` | connexion | 1 | 0.0 % | 63 ms | 63 ms | 63 ms | 800 ms | ✅ |
| `PATCH /api/effectifs/planification/equipages/{id}` | ecriture | 20 | 0.0 % | 3 ms | 15 ms | 15 ms | 1000 ms | ✅ |
| `POST /api/effectifs/absences/{id}/remplacement` | ecriture | 20 | 0.0 % | 4 ms | 14 ms | 21 ms | 1000 ms | ✅ |
| `POST /api/effectifs/temps/feuilles/{id}/approuver` | ecriture | 20 | 0.0 % | 3 ms | 13 ms | 32 ms | 1000 ms | ✅ |
| `POST /api/effectifs/temps/feuilles/{id}/retourner` | ecriture | 20 | 0.0 % | 3 ms | 13 ms | 15 ms | 1000 ms | ✅ |
| `POST /api/effectifs/temps/feuilles/{id}/soumettre` | ecriture | 20 | 0.0 % | 3 ms | 13 ms | 30 ms | 1000 ms | ✅ |
| `POST /api/effectifs/absences/{id}/annuler` | ecriture | 20 | 0.0 % | 3 ms | 13 ms | 27 ms | 1000 ms | ✅ |
| `PUT /api/effectifs/retention` | ecriture | 20 | 0.0 % | 3 ms | 11 ms | 18 ms | 1000 ms | ✅ |
| `GET /api/effectifs/liaisons/organisation` | lecture | 22 | 0.0 % | 2 ms | 9 ms | 9 ms | 500 ms | ✅ |
| `PUT /api/effectifs/integrations/connecteurs/{code}` | ecriture | 20 | 0.0 % | 4 ms | 9 ms | 13 ms | 1000 ms | ✅ |
| `PUT /api/effectifs/milieux/{id}` | ecriture | 20 | 0.0 % | 4 ms | 9 ms | 10 ms | 1000 ms | ✅ |
| `POST /api/effectifs/presence/pointages/{id}/approuver` | ecriture | 20 | 0.0 % | 3 ms | 9 ms | 12 ms | 1000 ms | ✅ |
| `POST /api/effectifs/temps/feuilles/{id}/regenerer` | ecriture | 20 | 0.0 % | 5 ms | 9 ms | 15 ms | 1000 ms | ✅ |
| `GET /api/effectifs/agph/campagnes/{id}` | lecture | 40 | 0.0 % | 3 ms | 8 ms | 11 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/remplacements/{id}/interesses` | lecture | 40 | 0.0 % | 3 ms | 8 ms | 9 ms | 500 ms | ✅ |
| `PUT /api/effectifs/agph/campagnes/{id}/disponibilites/{ressourceId}` | ecriture | 20 | 0.0 % | 3 ms | 8 ms | 8 ms | 1000 ms | ✅ |
| `POST /api/effectifs/integrations/anciennetes/{uniteCode}` | ecriture | 20 | 0.0 % | 3 ms | 8 ms | 8 ms | 1000 ms | ✅ |
| `POST /api/effectifs/milieux` | ecriture | 20 | 0.0 % | 4 ms | 8 ms | 24 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/equipages` | ecriture | 20 | 0.0 % | 3 ms | 8 ms | 10 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/quotas` | ecriture | 20 | 0.0 % | 3 ms | 8 ms | 17 ms | 1000 ms | ✅ |
| `POST /api/effectifs/referentiels/{type}/{code}/versions` | ecriture | 20 | 0.0 % | 4 ms | 8 ms | 9 ms | 1000 ms | ✅ |
| `POST /api/effectifs/regles` | ecriture | 20 | 0.0 % | 4 ms | 8 ms | 13 ms | 1000 ms | ✅ |
| `POST /api/effectifs/regles/{id}/versions` | ecriture | 20 | 0.0 % | 3 ms | 8 ms | 8 ms | 1000 ms | ✅ |
| `POST /api/effectifs/temps/feuilles/preparer` | ecriture | 20 | 0.0 % | 3 ms | 8 ms | 12 ms | 1000 ms | ✅ |
| `PUT /api/effectifs/temps/parametres` | ecriture | 20 | 0.0 % | 3 ms | 8 ms | 24 ms | 1000 ms | ✅ |
| `POST /api/effectifs/agph/campagnes/{id}/clore` | ecriture | 20 | 0.0 % | 3 ms | 8 ms | 25 ms | 1000 ms | ✅ |
| `POST /api/effectifs/agph/remplacements/{id}/fermer` | ecriture | 20 | 0.0 % | 3 ms | 8 ms | 8 ms | 1000 ms | ✅ |
| `DELETE /api/effectifs/temps/flux/{id}` | ecriture | 20 | 0.0 % | 3 ms | 8 ms | 9 ms | 1000 ms | ✅ |
| `GET /api/effectifs/absences/{id}/impact` | lecture | 40 | 0.0 % | 3 ms | 7 ms | 23 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/remplacements/{id}` | lecture | 40 | 0.0 % | 3 ms | 7 ms | 15 ms | 500 ms | ✅ |
| `GET /api/effectifs/disponibilites/periodes/{periodeId}` | lecture | 40 | 0.0 % | 3 ms | 7 ms | 9 ms | 500 ms | ✅ |
| `GET /api/effectifs/integrations/messages` | lecture | 27 | 0.0 % | 2 ms | 7 ms | 8 ms | 500 ms | ✅ |
| `GET /api/effectifs/journal` | lecture | 24 | 0.0 % | 4 ms | 7 ms | 8 ms | 500 ms | ✅ |
| `GET /api/effectifs/planification/periodes/{id}/comblement` | lecture | 20 | 0.0 % | 5 ms | 7 ms | 8 ms | 500 ms | ✅ |
| `GET /api/effectifs/planification/periodes/{id}/equipages` | lecture | 20 | 0.0 % | 4 ms | 7 ms | 8 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/pointages/{id}` | lecture | 20 | 0.0 % | 3 ms | 7 ms | 8 ms | 500 ms | ✅ |
| `POST /api/effectifs/agph/campagnes` | ecriture | 20 | 0.0 % | 4 ms | 7 ms | 7 ms | 1000 ms | ✅ |
| `POST /api/effectifs/agph/campagnes/{id}/choix` | ecriture | 20 | 0.0 % | 4 ms | 7 ms | 13 ms | 1000 ms | ✅ |
| `POST /api/effectifs/agph/campagnes/{id}/demarrer` | ecriture | 20 | 0.0 % | 3 ms | 7 ms | 7 ms | 1000 ms | ✅ |
| `POST /api/effectifs/agph/remplacements/{id}/interet` | ecriture | 20 | 0.0 % | 3 ms | 7 ms | 10 ms | 1000 ms | ✅ |
| `POST /api/effectifs/configuration/paquet` | ecriture | 20 | 0.0 % | 4 ms | 7 ms | 8 ms | 1000 ms | ✅ |
| `PUT /api/effectifs/disponibilites/periodes/{periodeId}/ressources/{ressourceId}` | ecriture | 20 | 0.0 % | 4 ms | 7 ms | 8 ms | 1000 ms | ✅ |
| `POST /api/effectifs/liaisons/organisation` | ecriture | 20 | 0.0 % | 3 ms | 7 ms | 7 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/periodes/{id}/etat` | ecriture | 20 | 0.0 % | 3 ms | 7 ms | 13 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/periodes/{id}/generer` | ecriture | 20 | 0.0 % | 4 ms | 7 ms | 9 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/postes` | ecriture | 20 | 0.0 % | 4 ms | 7 ms | 9 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/postes/lot` | ecriture | 20 | 0.0 % | 5 ms | 7 ms | 18 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/postes/publier` | ecriture | 20 | 0.0 % | 3 ms | 7 ms | 12 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/ressources` | ecriture | 20 | 0.0 % | 4 ms | 7 ms | 8 ms | 1000 ms | ✅ |
| `PUT /api/effectifs/planification/ressources/{id}` | ecriture | 20 | 0.0 % | 3 ms | 7 ms | 8 ms | 1000 ms | ✅ |
| `POST /api/effectifs/referentiels/{type}` | ecriture | 20 | 0.0 % | 4 ms | 7 ms | 9 ms | 1000 ms | ✅ |
| `POST /api/effectifs/temps/feuilles/{id}/segments` | ecriture | 20 | 0.0 % | 3 ms | 7 ms | 12 ms | 1000 ms | ✅ |
| `POST /api/effectifs/agph/remplacements/{id}/non-comble` | ecriture | 20 | 0.0 % | 3 ms | 7 ms | 7 ms | 1000 ms | ✅ |
| `POST /api/effectifs/echanges/{id}/annuler` | ecriture | 20 | 0.0 % | 3 ms | 7 ms | 7 ms | 1000 ms | ✅ |
| `POST /api/effectifs/echanges/{id}/refuser` | ecriture | 20 | 0.0 % | 3 ms | 7 ms | 13 ms | 1000 ms | ✅ |
| `DELETE /api/effectifs/presence/profils/{code}` | ecriture | 20 | 0.0 % | 3 ms | 7 ms | 13 ms | 1000 ms | ✅ |
| `POST /api/effectifs/referentiels/{type}/{code}/fermer` | ecriture | 20 | 0.0 % | 3 ms | 7 ms | 7 ms | 1000 ms | ✅ |
| `GET /api/effectifs/absences/{id}/comblement` | lecture | 40 | 0.0 % | 4 ms | 6 ms | 8 ms | 500 ms | ✅ |
| `GET /api/effectifs/configuration/paquet` | lecture | 40 | 0.0 % | 5 ms | 6 ms | 15 ms | 500 ms | ✅ |
| `GET /api/effectifs/disponibilites/periodes/{periodeId}/attribution-automatique` | lecture | 40 | 0.0 % | 2 ms | 6 ms | 7 ms | 500 ms | ✅ |
| `GET /api/effectifs/disponibilites/places/{placeId}/candidats` | lecture | 40 | 0.0 % | 3 ms | 6 ms | 9 ms | 500 ms | ✅ |
| `GET /api/effectifs/perimetres` | lecture | 20 | 0.0 % | 3 ms | 6 ms | 13 ms | 500 ms | ✅ |
| `GET /api/effectifs/temps/feuilles/{id}` | lecture | 20 | 0.0 % | 4 ms | 6 ms | 7 ms | 500 ms | ✅ |
| `POST /api/effectifs/absences` | ecriture | 20 | 0.0 % | 3 ms | 6 ms | 6 ms | 1000 ms | ✅ |
| `POST /api/effectifs/absences/{id}/decisions` | ecriture | 20 | 0.0 % | 3 ms | 6 ms | 8 ms | 1000 ms | ✅ |
| `PUT /api/effectifs/agph/campagnes/{id}/disponibilites/moi` | ecriture | 20 | 0.0 % | 3 ms | 6 ms | 8 ms | 1000 ms | ✅ |
| `PUT /api/effectifs/agph/campagnes/{id}/parametres` | ecriture | 20 | 0.0 % | 4 ms | 6 ms | 7 ms | 1000 ms | ✅ |
| `POST /api/effectifs/agph/remplacements` | ecriture | 20 | 0.0 % | 3 ms | 6 ms | 7 ms | 1000 ms | ✅ |
| `POST /api/effectifs/echanges/{id}/valider` | ecriture | 20 | 0.0 % | 3 ms | 6 ms | 6 ms | 1000 ms | ✅ |
| `POST /api/effectifs/integrations/calendriers/{ressourceId}` | ecriture | 20 | 0.0 % | 3 ms | 6 ms | 7 ms | 1000 ms | ✅ |
| `POST /api/effectifs/integrations/equipages` | ecriture | 20 | 0.0 % | 3 ms | 6 ms | 7 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/places/{id}/attribuer` | ecriture | 20 | 0.0 % | 5 ms | 6 ms | 8 ms | 1000 ms | ✅ |
| `PUT /api/effectifs/planification/postes/{id}` | ecriture | 20 | 0.0 % | 5 ms | 6 ms | 6 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/quotas/{id}/surcharges` | ecriture | 20 | 0.0 % | 3 ms | 6 ms | 8 ms | 1000 ms | ✅ |
| `POST /api/effectifs/presence/horodatages` | ecriture | 20 | 0.0 % | 3 ms | 6 ms | 7 ms | 1000 ms | ✅ |
| `POST /api/effectifs/presence/pointages` | ecriture | 20 | 0.0 % | 2 ms | 6 ms | 8 ms | 1000 ms | ✅ |
| `POST /api/effectifs/presence/saisies` | ecriture | 20 | 0.0 % | 3 ms | 6 ms | 7 ms | 1000 ms | ✅ |
| `POST /api/effectifs/regles/apercu` | rapport | 20 | 0.0 % | 3 ms | 6 ms | 6 ms | 2000 ms | ✅ |
| `POST /api/effectifs/retention/appliquer` | ecriture | 20 | 0.0 % | 3 ms | 6 ms | 26 ms | 1000 ms | ✅ |
| `POST /api/effectifs/temps/feuilles/{id}/segments/{segmentId}/confirmer` | ecriture | 20 | 0.0 % | 3 ms | 6 ms | 8 ms | 1000 ms | ✅ |
| `POST /api/effectifs/perimetres/{id}/fermer` | ecriture | 20 | 0.0 % | 3 ms | 6 ms | 6 ms | 1000 ms | ✅ |
| `GET /api/effectifs/absences` | lecture | 40 | 0.0 % | 3 ms | 5 ms | 7 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/campagnes` | lecture | 40 | 0.0 % | 3 ms | 5 ms | 5 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/campagnes/{id}/disponibilites` | lecture | 40 | 0.0 % | 3 ms | 5 ms | 22 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/campagnes/{id}/disponibilites/moi` | lecture | 40 | 0.0 % | 3 ms | 5 ms | 5 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/campagnes/{id}/postes-disponibles` | lecture | 40 | 0.0 % | 3 ms | 5 ms | 7 ms | 500 ms | ✅ |
| `GET /api/effectifs/disponibilites/moi` | lecture | 40 | 0.0 % | 2 ms | 5 ms | 8 ms | 500 ms | ✅ |
| `GET /api/effectifs/disponibilites/periodes/{periodeId}/ressources/{ressourceId}` | lecture | 40 | 0.0 % | 2 ms | 5 ms | 5 ms | 500 ms | ✅ |
| `GET /api/effectifs/echanges/places/{placeId}/demandables` | lecture | 35 | 0.0 % | 2 ms | 5 ms | 7 ms | 500 ms | ✅ |
| `GET /api/effectifs/echanges/ressources/{id}/quarts` | lecture | 33 | 0.0 % | 2 ms | 5 ms | 7 ms | 500 ms | ✅ |
| `GET /api/effectifs/echanges/{id}` | lecture | 31 | 0.0 % | 2 ms | 5 ms | 12 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/milieux/{id}/code-qr` | lecture | 20 | 0.0 % | 2 ms | 5 ms | 7 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/mon-profil` | lecture | 20 | 0.0 % | 2 ms | 5 ms | 7 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/pointages` | lecture | 20 | 0.0 % | 2 ms | 5 ms | 5 ms | 500 ms | ✅ |
| `GET /api/effectifs/temps/flux` | lecture | 20 | 0.0 % | 2 ms | 5 ms | 6 ms | 500 ms | ✅ |
| `POST /api/effectifs/agph/campagnes/{id}/reviser` | ecriture | 20 | 0.0 % | 3 ms | 5 ms | 7 ms | 1000 ms | ✅ |
| `POST /api/effectifs/agph/campagnes/{id}/revision/choix` | ecriture | 20 | 0.0 % | 3 ms | 5 ms | 7 ms | 1000 ms | ✅ |
| `PUT /api/effectifs/disponibilites/moi/{periodeId}` | ecriture | 20 | 0.0 % | 4 ms | 5 ms | 6 ms | 1000 ms | ✅ |
| `POST /api/effectifs/disponibilites/periodes/{periodeId}/ressources/{ressourceId}/approuver` | ecriture | 20 | 0.0 % | 3 ms | 5 ms | 5 ms | 1000 ms | ✅ |
| `POST /api/effectifs/integrations/horodatages/tirer` | ecriture | 20 | 0.0 % | 2 ms | 5 ms | 6 ms | 1000 ms | ✅ |
| `POST /api/effectifs/integrations/messages/{id}/rejouer` | ecriture | 20 | 0.0 % | 3 ms | 5 ms | 5 ms | 1000 ms | ✅ |
| `PUT /api/effectifs/milieux/{id}/preuves` | ecriture | 20 | 0.0 % | 3 ms | 5 ms | 6 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/periodes` | ecriture | 20 | 0.0 % | 4 ms | 5 ms | 9 ms | 1000 ms | ✅ |
| `PUT /api/effectifs/presence/profils` | ecriture | 20 | 0.0 % | 4 ms | 5 ms | 5 ms | 1000 ms | ✅ |
| `POST /api/effectifs/temps/flux` | ecriture | 20 | 0.0 % | 2 ms | 5 ms | 7 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/places/{id}/liberer` | ecriture | 20 | 0.0 % | 3 ms | 5 ms | 7 ms | 1000 ms | ✅ |
| `GET /api/effectifs/absences/{id}` | lecture | 40 | 0.0 % | 3 ms | 4 ms | 5 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/remplacements` | lecture | 40 | 0.0 % | 2 ms | 4 ms | 6 ms | 500 ms | ✅ |
| `GET /api/effectifs/echanges` | lecture | 39 | 0.0 % | 2 ms | 4 ms | 6 ms | 500 ms | ✅ |
| `GET /api/effectifs/echanges/moi` | lecture | 37 | 0.0 % | 2 ms | 4 ms | 7 ms | 500 ms | ✅ |
| `GET /api/effectifs/integrations/connecteurs` | lecture | 28 | 0.0 % | 3 ms | 4 ms | 7 ms | 500 ms | ✅ |
| `GET /api/effectifs/perimetres/moi` | lecture | 20 | 0.0 % | 3 ms | 4 ms | 6 ms | 500 ms | ✅ |
| `GET /api/effectifs/planification/quotas` | lecture | 20 | 0.0 % | 2 ms | 4 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/planification/ressources` | lecture | 20 | 0.0 % | 2 ms | 4 ms | 6 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/audit-localisations` | lecture | 20 | 0.0 % | 3 ms | 4 ms | 7 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/audit-localisations.csv` | rapport | 20 | 0.0 % | 3 ms | 4 ms | 6 ms | 2000 ms | ✅ |
| `GET /api/effectifs/presence/moi` | lecture | 20 | 0.0 % | 3 ms | 4 ms | 10 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/ressources/{id}/pointages` | lecture | 20 | 0.0 % | 2 ms | 4 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/temps/feuilles/{id}/segments/{segmentId}/historique` | lecture | 20 | 0.0 % | 2 ms | 4 ms | 5 ms | 500 ms | ✅ |
| `GET /api/effectifs/temps/rapports` | rapport | 20 | 0.0 % | 2 ms | 4 ms | 8 ms | 2000 ms | ✅ |
| `POST /api/effectifs/agph/campagnes/{id}/ouvrir` | ecriture | 20 | 0.0 % | 3 ms | 4 ms | 6 ms | 1000 ms | ✅ |
| `POST /api/effectifs/echanges` | ecriture | 20 | 0.0 % | 3 ms | 4 ms | 5 ms | 1000 ms | ✅ |
| `POST /api/effectifs/echanges/{id}/accepter` | ecriture | 20 | 0.0 % | 3 ms | 4 ms | 5 ms | 1000 ms | ✅ |
| `POST /api/effectifs/integrations/paie` | ecriture | 20 | 0.0 % | 2 ms | 4 ms | 7 ms | 1000 ms | ✅ |
| `POST /api/effectifs/perimetres` | ecriture | 20 | 0.0 % | 3 ms | 4 ms | 6 ms | 1000 ms | ✅ |
| `POST /api/effectifs/planification/equipages/lot` | ecriture | 20 | 0.0 % | 3 ms | 4 ms | 7 ms | 1000 ms | ✅ |
| `POST /api/effectifs/regles/simuler` | ecriture | 20 | 0.0 % | 2 ms | 4 ms | 7 ms | 1000 ms | ✅ |
| `PUT /api/effectifs/temps/feuilles/{id}/segments/{segmentId}` | ecriture | 20 | 0.0 % | 3 ms | 4 ms | 25 ms | 1000 ms | ✅ |
| `DELETE /api/effectifs/agph/remplacements/{id}/interet` | ecriture | 20 | 0.0 % | 3 ms | 4 ms | 5 ms | 1000 ms | ✅ |
| `POST /api/effectifs/echanges/{id}/rejeter` | ecriture | 20 | 0.0 % | 3 ms | 4 ms | 4 ms | 1000 ms | ✅ |
| `POST /api/effectifs/presence/pointages/{id}/rejeter` | ecriture | 20 | 0.0 % | 3 ms | 4 ms | 6 ms | 1000 ms | ✅ |
| `POST /api/effectifs/regles/{id}/retirer` | ecriture | 20 | 0.0 % | 3 ms | 4 ms | 15 ms | 1000 ms | ✅ |
| `DELETE /api/effectifs/temps/feuilles/{id}/segments/{segmentId}` | ecriture | 20 | 0.0 % | 3 ms | 4 ms | 5 ms | 1000 ms | ✅ |
| `GET /api/effectifs/ma-ressource` | lecture | 20 | 0.0 % | 2 ms | 3 ms | 3 ms | 500 ms | ✅ |
| `GET /api/effectifs/milieux` | lecture | 20 | 0.0 % | 2 ms | 3 ms | 3 ms | 500 ms | ✅ |
| `GET /api/effectifs/mon-horaire` | lecture | 20 | 0.0 % | 2 ms | 3 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/planification/periodes` | lecture | 20 | 0.0 % | 2 ms | 3 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/planification/postes` | lecture | 20 | 0.0 % | 2 ms | 3 ms | 6 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/profils` | lecture | 20 | 0.0 % | 2 ms | 3 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/referentiels/{type}` | lecture | 20 | 0.0 % | 2 ms | 3 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/referentiels/{type}/{code}/historique` | lecture | 20 | 0.0 % | 2 ms | 3 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/regles` | lecture | 20 | 0.0 % | 2 ms | 3 ms | 6 ms | 500 ms | ✅ |
| `GET /api/effectifs/regles/evaluations` | lecture | 20 | 0.0 % | 2 ms | 3 ms | 5 ms | 500 ms | ✅ |
| `GET /api/effectifs/regles/{id}` | lecture | 20 | 0.0 % | 2 ms | 3 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/retention` | lecture | 20 | 0.0 % | 2 ms | 3 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/temps/export-paie` | rapport | 20 | 0.0 % | 2 ms | 3 ms | 4 ms | 2000 ms | ✅ |
| `GET /api/effectifs/temps/feuilles` | lecture | 20 | 0.0 % | 2 ms | 3 ms | 3 ms | 500 ms | ✅ |
| `GET /api/effectifs/temps/moi` | lecture | 20 | 0.0 % | 2 ms | 3 ms | 3 ms | 500 ms | ✅ |
| `GET /api/effectifs/temps/parametres` | lecture | 20 | 0.0 % | 2 ms | 3 ms | 3 ms | 500 ms | ✅ |
| `POST /api/effectifs/disponibilites/periodes/{periodeId}/attribuer` | ecriture | 20 | 0.0 % | 3 ms | 3 ms | 4 ms | 1000 ms | ✅ |
| `POST /api/effectifs/integrations/relais/{code}` | ecriture | 20 | 0.0 % | 3 ms | 3 ms | 4 ms | 1000 ms | ✅ |
| `GET /api/effectifs/presence/referentiels-profils` | lecture | 20 | 0.0 % | 1 ms | 2 ms | 3 ms | 500 ms | ✅ |
| `GET /api/effectifs/referentiels` | lecture | 20 | 0.0 % | 2 ms | 2 ms | 3 ms | 500 ms | ✅ |
| `GET /api/effectifs/regles/faits` | lecture | 20 | 0.0 % | 1 ms | 2 ms | 2 ms | 500 ms | ✅ |

## Lecture

- **SLI** : ce qui est mesuré (centiles de latence, taux d'erreur, débit). **SLO** : l'objectif par classe d'endpoint (`slo.json`). **SLA** : l'engagement contractuel, p95 < 2 s à 2 500 utilisateurs.
- Les centiles sont calculés sur toutes les observations (rang le plus proche) ; aucune moyenne.
- Une requête en 4xx nommé (validation, droit) compte comme une réponse valide ; un 5xx, un code inattendu ou un transport rompu compte comme une erreur. Une requête trop longue n'est pas une erreur : elle pèse dans les centiles.
