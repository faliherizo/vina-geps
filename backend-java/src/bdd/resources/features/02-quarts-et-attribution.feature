# language: fr
Fonctionnalité: Quarts ad hoc, attribution manuelle et postes publiés
  Un quart se crée hors de tout poste pour un besoin ponctuel ; une attribution se décide par les
  règles, et seul un utilisateur autorisé peut passer outre — en le justifiant et en laissant une trace.

  @E-GN-2 @E-GN-3 @E-GN-4 @E-GR-6 @E-GR-13 @C-01
  Scénario: Un quart ad hoc se crée hors de tout poste ; hors période, sans composition, à minimum impossible ou sur une plage inconnue, il est refusé
    Soit un organisme d'Urgences-santé amorcé
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      {
        "nom": "PLAN",
        "uniteCode": "EST",
        "debut": "2030-03-18",
        "fin": "2030-03-24"
      }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "plan"
    # Hors de la période : un quart n'existe que dans une période
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      {
        "periodeId": "{{plan}}",
        "date": "2030-04-01",
        "debut": 600,
        "fin": 660,
        "natureCode": "ADH",
        "milieuId": "{{c12}}",
        "composition": [
          {
            "corpsEmploiCode": "PARA",
            "nombre": 2
          }
        ]
      }
      """
    Alors la réponse a le statut 422
    # Sans composition : un quart sans place n'est pas un quart
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      {
        "periodeId": "{{plan}}",
        "date": "2030-03-18",
        "debut": 600,
        "fin": 660,
        "natureCode": "ADH",
        "milieuId": "{{c12}}",
        "composition": []
      }
      """
    Alors la réponse a le statut 422
    # Un minimum opérationnel de 3 sur 2 places ne serait jamais atteint
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      {
        "periodeId": "{{plan}}",
        "date": "2030-03-18",
        "debut": 600,
        "fin": 660,
        "natureCode": "ADH",
        "milieuId": "{{c12}}",
        "minimumOperationnel": 3,
        "composition": [
          {
            "corpsEmploiCode": "PARA",
            "nombre": 2
          }
        ]
      }
      """
    Alors la réponse a le statut 422
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      {
        "periodeId": "{{plan}}",
        "date": "2030-03-18",
        "debut": 600,
        "fin": 660,
        "natureCode": "ADH",
        "milieuId": "{{c12}}",
        "plageCode": "ZZ",
        "composition": [
          {
            "corpsEmploiCode": "PARA",
            "nombre": 2
          }
        ]
      }
      """
    Alors la réponse a le statut 422
    # Le mode d'attribution se choisit par quart ; le minimum se déclare
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      {
        "periodeId": "{{plan}}",
        "date": "2030-03-18",
        "debut": 600,
        "fin": 660,
        "natureCode": "ADH",
        "milieuId": "{{c12}}",
        "modeAttribution": "AUTOMATISE",
        "minimumOperationnel": 1,
        "composition": [
          {
            "corpsEmploiCode": "PARA",
            "nombre": 2
          }
        ]
      }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      {
        "modeAttribution": "AUTOMATISE",
        "minimumOperationnel": 1
      }
      """
    Et je retiens "id" sous le nom "adhoc"
    Et je retiens "places.0.id" sous le nom "placeAdhoc"

  @E-GN-11 @E-GN-8 @E-GN-9 @C-02
  Scénario: Une règle de repos bloque l'attribution en nommant son motif ; forcer exige le niveau GESTION et une justification, et le forçage est tracé
    Soit un organisme d'Urgences-santé amorcé
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      {
        "nom": "PLAN",
        "uniteCode": "EST",
        "debut": "2030-03-18",
        "fin": "2030-03-24"
      }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "plan"
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      {
        "contexte": "ATTRIBUTION",
        "cle": "repos-minimal",
        "condition": {
          "fait": "reposHeures",
          "op": "<",
          "valeur": 8
        },
        "action": "BLOQUER",
        "message": "Repos de {reposHeures} h : il en faut 8",
        "effectifDepuis": "{{demain}}"
      }
      """
    Alors la réponse a le statut 201
    # Marc tient le quart de jour, qui finit à 18 h
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      {
        "periodeId": "{{plan}}",
        "date": "2030-03-18",
        "debut": 360,
        "fin": 1080,
        "natureCode": "REG",
        "milieuId": "{{c12}}",
        "composition": [
          {
            "corpsEmploiCode": "PARA",
            "nombre": 1
          }
        ]
      }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "placeJour"
    Quand admin envoie POST /api/effectifs/planification/places/{{placeJour}}/attribuer avec :
      """json
      {
        "ressourceId": "{{P2}}"
      }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      {
        "periodeId": "{{plan}}",
        "date": "2030-03-18",
        "debut": 1080,
        "fin": 1320,
        "natureCode": "ADH",
        "milieuId": "{{c12}}",
        "composition": [
          {
            "corpsEmploiCode": "PARA",
            "nombre": 1
          }
        ],
        "note": "Renfort de soirée"
      }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      {
        "modeAttribution": "MANUEL",
        "note": "Renfort de soirée"
      }
      """
    Et je retiens "places.0.id" sous le nom "placeSoir"
    # Sans repos entre deux quarts, la règle refuse et dit pourquoi
    Quand admin envoie POST /api/effectifs/planification/places/{{placeSoir}}/attribuer avec :
      """json
      {
        "ressourceId": "{{P2}}"
      }
      """
    Alors la réponse a le statut 409
    Et la réponse correspond à "Repos de 0 h : il en faut 8"
    # Un gestionnaire sans périmètre n'a aucun droit : refus par défaut
    Quand chef envoie POST /api/effectifs/planification/places/{{placeSoir}}/attribuer avec :
      """json
      {
        "ressourceId": "{{P2}}",
        "forcer": true,
        "justification": "Aucun autre PSA disponible, appel massif"
      }
      """
    Alors la réponse a le statut 403
    Quand admin envoie POST /api/effectifs/perimetres avec :
      """json
      {
        "utilisateurId": "{{u_chef}}",
        "uniteCode": "EST",
        "etendue": "UNITE",
        "niveau": "GESTION",
        "depuis": "2026-01-01"
      }
      """
    Alors la réponse a le statut 201
    # Une justification de trois mots n'en est pas une
    Quand chef envoie POST /api/effectifs/planification/places/{{placeSoir}}/attribuer avec :
      """json
      {
        "ressourceId": "{{P2}}",
        "forcer": true,
        "justification": "court"
      }
      """
    Alors la réponse a le statut 422
    Quand chef envoie POST /api/effectifs/planification/places/{{placeSoir}}/attribuer avec :
      """json
      {
        "ressourceId": "{{P2}}",
        "forcer": true,
        "justification": "Aucun autre PSA disponible, appel massif"
      }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      {
        "force": true
      }
      """
    Et la réponse correspond à "Aucun autre PSA disponible, appel massif"
    # La trace dit quelle règle a été écartée
    Quand admin envoie GET /api/effectifs/journal?action=place.attribution.forcee
    Alors la réponse a le statut 200
    Et la réponse correspond à "repos-minimal"

  @E-CH-2 @E-CH-4 @E-CH-5 @C-03
  Scénario: Un poste se publie une fois ; une liste vide ou un poste déjà publié est refusé
    Soit un organisme d'Urgences-santé amorcé
    # Un paramètre de poste se déclare avant usage : le refus nomme le référentiel à compléter
    Quand admin envoie POST /api/effectifs/planification/postes avec :
      """json
      {
        "code": "3412-J0",
        "libelle": "PSA jour",
        "uniteCode": "EST",
        "statutEmploiCode": "TC",
        "corpsEmploiCode": "PARA",
        "milieuId": "{{c12}}",
        "natureCode": "REG",
        "cycle": [
          [
            "J",
            "J",
            "J",
            null,
            null,
            null,
            null
          ]
        ],
        "debutCycle": "2030-03-18",
        "parametres": {
          "VEHICULE": "3412"
        }
      }
      """
    Alors la réponse a le statut 422
    Et la réponse correspond à "paramètre de poste inconnu : « VEHICULE »"
    Quand admin envoie POST /api/effectifs/referentiels/parametres_poste avec :
      """json
      {
        "effectifDepuis": "2026-01-01",
        "code": "VEHICULE",
        "libelleFr": "Véhicule",
        "attributs": {
          "typeValeur": "TEXTE"
        }
      }
      """
    Alors la réponse a le statut 201
    # Le type déclaré se vérifie : un nombre n'est pas un texte
    Quand admin envoie POST /api/effectifs/planification/postes avec :
      """json
      {
        "code": "3412-J0",
        "libelle": "PSA jour",
        "uniteCode": "EST",
        "statutEmploiCode": "TC",
        "corpsEmploiCode": "PARA",
        "milieuId": "{{c12}}",
        "natureCode": "REG",
        "cycle": [
          [
            "J",
            "J",
            "J",
            null,
            null,
            null,
            null
          ]
        ],
        "debutCycle": "2030-03-18",
        "parametres": {
          "VEHICULE": 12
        }
      }
      """
    Alors la réponse a le statut 422
    Quand admin envoie POST /api/effectifs/planification/postes avec :
      """json
      {
        "code": "3412-J1",
        "libelle": "PSA jour",
        "uniteCode": "EST",
        "statutEmploiCode": "TC",
        "corpsEmploiCode": "PARA",
        "milieuId": "{{c12}}",
        "natureCode": "REG",
        "cycle": [
          [
            "J",
            "J",
            "J",
            null,
            null,
            null,
            null
          ]
        ],
        "debutCycle": "2030-03-18",
        "parametres": {
          "VEHICULE": "3412"
        }
      }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      {
        "etat": "BROUILLON",
        "parametres": {
          "VEHICULE": "3412"
        }
      }
      """
    Et je retiens "id" sous le nom "poste"
    Quand admin envoie POST /api/effectifs/planification/postes/publier avec :
      """json
      {
        "ids": []
      }
      """
    Alors la réponse a le statut 422
    Quand admin envoie POST /api/effectifs/planification/postes/publier avec :
      """json
      {
        "ids": [
          "{{poste}}"
        ]
      }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      [
        {
          "etat": "PUBLIE"
        }
      ]
      """
    Quand admin envoie POST /api/effectifs/planification/postes/publier avec :
      """json
      {
        "ids": [
          "{{poste}}"
        ]
      }
      """
    Alors la réponse a le statut 422
