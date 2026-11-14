# language: fr
Fonctionnalité: Planification des quarts, des postes et des quotas
  Pour confectionner et ajuster les horaires d'Urgences-santé,
  le service de planification découpe le travail en unités, en postes et en quarts,
  rattache chaque quart à un milieu physique et règle des quotas de comblement.

  Contexte:
    Soit un organisme d'Urgences-santé amorcé
    Quand admin envoie POST /api/effectifs/referentiels/plages_horaires avec :
      """json
      { "effectifDepuis": "2026-01-01", "code": "N", "libelleFr": "Nuit", "attributs": { "debut": 1080, "fin": 1440 } }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      { "nom": "Semaine 10", "uniteCode": "EST", "debut": "2030-03-04", "fin": "2030-03-10" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "periode"

  @E-GN-1 @E-GN-17
  Scénario: Un quart réunit plusieurs places de corps d'emploi choisis, rattaché à un milieu physique
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      {
        "periodeId": "{{periode}}", "date": "2030-03-04", "debut": 600, "fin": 660, "natureCode": "ADH",
        "milieuId": "{{c12}}",
        "composition": [ { "corpsEmploiCode": "PARA", "nombre": 2 } ]
      }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "milieuId": "{{c12}}", "natureCode": "ADH", "places": [ { "corpsEmploiCode": "PARA" }, { "corpsEmploiCode": "PARA" } ] }
      """
    # Un quart sans milieu ne se situe nulle part : il est refusé, en nommant le milieu introuvable.
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      {
        "periodeId": "{{periode}}", "date": "2030-03-04", "debut": 600, "fin": 660, "natureCode": "ADH",
        "composition": [ { "corpsEmploiCode": "PARA", "nombre": 1 } ]
      }
      """
    Alors la réponse a le statut 404
    Et la réponse correspond à "milieu .* introuvable"

  @E-GN-7
  Scénario: Chaque unité est un groupe organisationnel distinct qui porte ses propres périodes
    Quand admin envoie POST /api/effectifs/referentiels/unites avec :
      """json
      { "effectifDepuis": "2026-01-01", "code": "OUEST", "libelleFr": "Opérations Ouest" }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      { "nom": "Semaine 10 Ouest", "uniteCode": "OUEST", "debut": "2030-03-04", "fin": "2030-03-10" }
      """
    Alors la réponse a le statut 201
    Quand admin envoie GET /api/effectifs/planification/periodes
    Alors la réponse a le statut 200
    Et la réponse correspond à "\"uniteCode\":\"EST\""
    Et la réponse correspond à "\"uniteCode\":\"OUEST\""

  @E-CH-3 @E-CH-5 @E-GR-1
  Scénario: Un poste porte ses paramètres déclarés, se modifie, et la période se génère hors de tout processus annuel
    Quand admin envoie POST /api/effectifs/referentiels/parametres_poste avec :
      """json
      { "effectifDepuis": "2026-01-01", "code": "VEHICULE", "libelleFr": "Véhicule", "attributs": { "typeValeur": "TEXTE" } }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/referentiels/parametres_poste avec :
      """json
      { "effectifDepuis": "2026-01-01", "code": "ALTERNANCE", "libelleFr": "Alternance", "attributs": { "typeValeur": "BOOLEEN" } }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/planification/postes avec :
      """json
      {
        "code": "3412-J1", "libelle": "PSA jour", "uniteCode": "EST", "statutEmploiCode": "TC", "corpsEmploiCode": "PARA",
        "milieuId": "{{c12}}", "natureCode": "REG", "cycle": [["J", "J", "J", null, null, null, null]], "debutCycle": "2030-03-04",
        "parametres": { "VEHICULE": "3412" }
      }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "etat": "BROUILLON", "parametres": { "VEHICULE": "3412" } }
      """
    Et je retiens "id" sous le nom "poste"
    # Un paramètre fonctionnel inconnu du catalogue est refusé nommément.
    Quand admin envoie POST /api/effectifs/planification/postes avec :
      """json
      {
        "code": "X1", "libelle": "PSA jour", "uniteCode": "EST", "statutEmploiCode": "TC", "corpsEmploiCode": "PARA",
        "milieuId": "{{c12}}", "natureCode": "REG", "cycle": [["J", null, null, null, null, null, null]], "debutCycle": "2030-03-04",
        "parametres": { "COULEUR": "rouge" }
      }
      """
    Alors la réponse a le statut 422
    Quand admin envoie PUT /api/effectifs/planification/postes/{{poste}} avec :
      """json
      {
        "code": "3412-J1", "libelle": "PSA jour — 3412", "uniteCode": "EST", "statutEmploiCode": "TC", "corpsEmploiCode": "PARA",
        "milieuId": "{{c12}}", "natureCode": "REG", "cycle": [["J", "J", "J", null, null, null, null]], "debutCycle": "2030-03-04",
        "parametres": { "VEHICULE": "3412", "ALTERNANCE": true }
      }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "libelle": "PSA jour — 3412" }
      """
    Quand admin envoie POST /api/effectifs/planification/postes/publier avec :
      """json
      { "ids": ["{{poste}}"] }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/periodes/{{periode}}/etat avec :
      """json
      { "etat": "CONFECTION" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/periodes/{{periode}}/generer
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "equipagesCrees": 3 }
      """
    # Générer deux fois ne double rien.
    Quand admin envoie POST /api/effectifs/planification/periodes/{{periode}}/generer
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "equipagesCrees": 0, "placesCreees": 0 }
      """

  @E-GR-5
  Scénario: Un quart du jour s'ajuste — heures et minimum — et un ajustement vide ou impossible est refusé
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      {
        "periodeId": "{{periode}}", "date": "2030-03-04", "debut": 360, "fin": 1080, "natureCode": "ADH",
        "milieuId": "{{c12}}", "composition": [ { "corpsEmploiCode": "PARA", "nombre": 2 } ]
      }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "quart"
    Quand admin envoie PATCH /api/effectifs/planification/equipages/{{quart}} avec :
      """json
      { "debut": 420, "fin": 1080, "minimumOperationnel": 1 }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "debut": 420, "minimumOperationnel": 1 }
      """
    Quand admin envoie PATCH /api/effectifs/planification/equipages/{{quart}} avec :
      """json
      {}
      """
    Alors la réponse a le statut 422
    Et la réponse contient :
      """json
      { "errors": ["aucun changement demandé"] }
      """
    # Un minimum plus grand que le nombre de places ne serait jamais atteint.
    Quand admin envoie PATCH /api/effectifs/planification/equipages/{{quart}} avec :
      """json
      { "minimumOperationnel": 5 }
      """
    Alors la réponse a le statut 422

  @E-GR-8
  Scénario: Les quotas de comblement se règlent par unité, par plage et par nature de quart
    Quand admin envoie POST /api/effectifs/planification/quotas avec :
      """json
      { "uniteCode": "EST", "plageCode": "J", "natureCode": "REG", "quota": 1, "depuis": "2030-01-01" }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/planification/quotas avec :
      """json
      { "uniteCode": "EST", "plageCode": "N", "natureCode": "REG", "quota": 2, "depuis": "2030-01-01" }
      """
    Alors la réponse a le statut 201
    # Une plage inconnue n'est pas une plage.
    Quand admin envoie POST /api/effectifs/planification/quotas avec :
      """json
      { "uniteCode": "EST", "plageCode": "ZZ", "quota": 1 }
      """
    Alors la réponse a le statut 422
    Quand admin envoie GET /api/effectifs/planification/quotas
    Alors la réponse a le statut 200
    Et la réponse correspond à "EST\\|J\\|REG"
    Et la réponse correspond à "EST\\|N\\|REG"

  @E-GR-9
  Scénario: Un quota s'ajuste temporairement, avec un motif, et jamais dans le passé
    Quand admin envoie POST /api/effectifs/planification/quotas avec :
      """json
      { "uniteCode": "EST", "plageCode": "J", "natureCode": "REG", "quota": 1, "depuis": "2030-01-01" }
      """
    Alors la réponse a le statut 201
    Et je retiens "versions.0.id" sous le nom "quota"
    Quand admin envoie POST /api/effectifs/planification/quotas/{{quota}}/surcharges avec :
      """json
      { "valeur": 3, "depuis": "2030-03-05T00:00:00.000Z", "jusqua": "2030-03-06T00:00:00.000Z", "motif": "Événement majeur" }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "surcharges": [ { "motif": "Événement majeur" } ] }
      """
    # Sans effet rétroactif : une surcharge dans le passé est refusée.
    Quand admin envoie POST /api/effectifs/planification/quotas/{{quota}}/surcharges avec :
      """json
      { "valeur": 3, "depuis": "2020-01-01T00:00:00.000Z", "jusqua": "2020-01-02T00:00:00.000Z", "motif": "x" }
      """
    Alors la réponse a le statut 422
    # Un ajustement sans motif ne se justifie pas.
    Quand admin envoie POST /api/effectifs/planification/quotas/{{quota}}/surcharges avec :
      """json
      { "valeur": 3, "depuis": "2030-03-06T00:00:00.000Z", "jusqua": "2030-03-07T00:00:00.000Z", "motif": "" }
      """
    Alors la réponse a le statut 422

  @E-GR-10
  Scénario: Une période fermée ne se modifie plus : la fermeture est contrôlée et définitive
    Quand admin envoie POST /api/effectifs/planification/periodes/{{periode}}/etat avec :
      """json
      { "etat": "CONFECTION" }
      """
    Alors la réponse a le statut 200
    # On ne ferme pas une période qui n'a pas été publiée.
    Quand admin envoie POST /api/effectifs/planification/periodes/{{periode}}/etat avec :
      """json
      { "etat": "CLOSE" }
      """
    Alors la réponse a le statut 422
    Quand admin envoie POST /api/effectifs/planification/periodes/{{periode}}/etat avec :
      """json
      { "etat": "PUBLIEE" }
      """
    Alors la réponse a le statut 200
    Quand julie envoie POST /api/effectifs/planification/periodes/{{periode}}/etat avec :
      """json
      { "etat": "CLOSE" }
      """
    Alors la réponse a le statut 403
    Quand admin envoie POST /api/effectifs/planification/periodes/{{periode}}/etat avec :
      """json
      { "etat": "CLOSE" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{periode}}", "date": "2030-03-04", "debut": 360, "fin": 1080, "natureCode": "ADH", "milieuId": "{{c12}}",
        "composition": [ { "corpsEmploiCode": "PARA", "nombre": 1 } ] }
      """
    Alors la réponse a le statut 422
    Et la réponse contient :
      """json
      { "errors": ["la période « Semaine 10 » est close : elle ne se modifie plus"] }
      """
    # Close, elle ne se rouvre pas.
    Quand admin envoie POST /api/effectifs/planification/periodes/{{periode}}/etat avec :
      """json
      { "etat": "PUBLIEE" }
      """
    Alors la réponse a le statut 422
