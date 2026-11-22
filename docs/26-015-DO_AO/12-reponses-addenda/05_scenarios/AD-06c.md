# Scénarios — AD-06c Absence constatée aux opérations ; avis des quarts modifiés

> Conventions : voir [../05_scenarios.md](../05_scenarios.md). Décision : [ADR 0090](../../../adr/0090-absence-operations-et-avis-des-quarts.md).

**Fichier** : `11-technologie.feature`, scénario « Les opérations transmettent une absence sur un quart réel par l'API
d'échange ; le quart vacant est annoncé au webhook et paraît parmi les correctifs (AD-06c) ». Contexte : organisme amorcé,
Julie (matricule P1), application « rao » de portées `read write`, webhook abonné à `effectifs.quart.modifie`.

| Étape | Ce qui est posé | Attendu | Pourquoi l'attendu prouve |
|---|---|---|---|
| 1 | Période en confection, un quart attribué à Julie | 0 envoi au webhook | Un horaire en confection n'est pas opposable : il ne s'annonce pas (b) |
| 2 | Période publiée ; Julie, par sa session, transmet l'absence | 401 | Une session humaine n'entre pas dans l'API d'échange |
| 3 | « rao » transmet `{quartId, codeAbsence: MAL}` | 201, `ACCEPTEE`, date du quart, `placesLiberees: 1`, `declareePar: application:<client>` | L'absence aux opérations est un fait, signé par l'application (d) |
| 4 | « rao » renvoie la même absence | 200 | Un renvoi après une coupure ne double rien |
| 5 | `GET /quarts/modifications` | une entrée `places.vacantes` sur la place du quart | Le quart vidé par l'absence sort parmi les correctifs (a) |
| 6 | `GET /api/integrations/webhooks/{id}/envois` | 1 envoi `effectifs.quart.modifie` | L'organisme est avisé, une fois par quart et par geste (b) |

**Essais Java** :
- `ControleurAbsencesEffectifsHttpTest.absenceConstateeAuxOperations` :
  - les règles refusent (`REFUSER`), mais l'absence est acceptée et le verdict reste consigné (`actionRegles`) ;
  - portée `read` seule : 403 ; renvoi identique : 200, même identifiant, aucune place libérée de plus ;
  - autre code sur les mêmes dates : 409 ; quart que l'employé ne tient pas : 409 nommé ; quart inconnu : 404 ;
  - quart et `au` ensemble : 422 ; matricule inconnu : 404 ; corps vide : 422 ; une seule absence enregistrée.
- `ControleurAbsencesEffectifsHttpTest.placeVacanteAnnoncee` : deux jours, deux places vacantes. Les correctifs les rendent
  avec le quart vidé, et un avis part par quart (`lire`, `uniteCode`).
- `JournalAvecAvisDesQuartsTest` :
  - l'écriture d'abord, puis l'avis ; deux places d'un même quart font un seul avis ;
  - un équipage se lit sans passer par les places ; un quart en confection reste muet ;
  - une écriture étrangère aux quarts n'interroge rien.
- `LectureEchangesApiTest.plusieursPlaces` : une absence ou un échange rendent un correctif par place, sous le curseur de
  leur entrée, et la lecture reste groupée.
- `EchangesApiTest.cibles`, `.absenceOperations` ; `AbsencesEffectifsDomaineTest.origine`, `.laMeme`.

**OpenAPI** : `POST /api/echanges/v1/employes/{matricule}/absences`, et les routes d'AD-06b qui manquaient
(`PUT /employes/{matricule}`, `PUT`/`GET …/soldes`). L'événement est décrit dans `x-webhooks` : OpenAPI 3.0 n'a pas de
section `webhooks`.
