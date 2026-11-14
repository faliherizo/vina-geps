# language: fr
Fonctionnalité: Journal des actions
  Toute action qui touche les horaires, les quarts, les absences ou les paramètres est consignée,
  horodatée, avec son auteur — et se consulte ou s'exporte a posteriori.

  @E-GN-10 @E-RF-8 @C-09
  Scénario: Le journal s'exporte en CSV, en-tête fixe : chaque geste humain ou automatique y est retrouvable
    Soit un organisme d'Urgences-santé amorcé
    Quand admin envoie GET /api/effectifs/journal?format=csv
    Alors la réponse a le statut 200
    Et la réponse correspond à "^horodatage,origine,acteurId,action,objetType,objetId,uniteCode,avant,apres"

  @E-GN-9 @E-GN-10
  Scénario: Les gestes de paramétrage et d'horaire se retrouvent au journal, horodatés, avec leur auteur, et se filtrent a posteriori
    Soit un organisme d'Urgences-santé amorcé
    # Un geste de paramétrage…
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      { "contexte": "DISPONIBILITE", "cle": "au-moins-un", "condition": { "fait": "quartsParSemaine", "op": "<", "valeur": 1 },
        "action": "BLOQUER", "message": "Au moins un quart", "effectifDepuis": "{{demain}}" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "regle"
    # … et un geste d'horaire.
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      { "nom": "Semaine 10", "uniteCode": "EST", "debut": "2030-03-04", "fin": "2030-03-10" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "periode"
    Quand admin envoie GET /api/effectifs/journal?action=regle.creee
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      [ { "action": "regle.creee", "objetId": "{{regle}}", "origine": "UTILISATEUR" } ]
      """
    Et la réponse correspond à "\"horodatage\":\"20[0-9]{2}-[0-9]{2}-[0-9]{2}T"
    Et la réponse correspond à "\"acteurId\":\"[^\"]+\""
    Quand admin envoie GET /api/effectifs/journal?action=periode.creee
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      [ { "action": "periode.creee", "objetId": "{{periode}}" } ]
      """
    # L'amorce elle-même — les référentiels — est tracée.
    Quand admin envoie GET /api/effectifs/journal?action=referentiel.cree
    Alors la réponse a le statut 200
    Et la réponse correspond à "\"action\":\"referentiel.cree\""
    # Un employé ne lit pas le journal de l'organisme.
    Quand julie envoie GET /api/effectifs/journal
    Alors la réponse a le statut 403
