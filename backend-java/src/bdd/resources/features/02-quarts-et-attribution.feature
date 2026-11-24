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

  # AD-08 (addenda 4 Q1, addenda 6 Q2) : la convention SP-CSN se paramètre par des règles à portée de convention.
  # Le paramétrage complet, ses articles et ses écarts : e2e/donnees/urgences-sante/convention-sp-csn.json.
  @E-GR-7
  Scénario: Une règle à portée de convention ne vise que ses personnes ; le travail en continuité n'est pas un repos (14.06) mais compte dans les 16 h sur 24 h (16.02)
    Soit un organisme d'Urgences-santé amorcé
    Quand admin envoie POST /api/effectifs/referentiels/conventions avec :
      """json
      { "effectifDepuis": "2026-01-01", "code": "SPCSN", "libelleFr": "Convention SP-CSN — paramédics" }
      """
    Alors la réponse a le statut 201
    Quand admin envoie PUT /api/effectifs/planification/ressources/{{P2}} avec :
      """json
      { "nom": "Marc", "corpsEmploiCode": "PARA", "uniteCode": "EST", "statutEmploiCode": "TPR", "anciennete": 9,
        "utilisateurId": "{{u_marc}}", "conventionCode": "SPCSN" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      { "contexte": "ATTRIBUTION", "cle": "sp-csn-repos", "porteeType": "CONVENTION", "porteeCode": "SPCSN",
        "condition": { "tous": [ { "fait": "reposHeures", "op": ">", "valeur": 0 }, { "fait": "reposHeures", "op": "<", "valeur": 10 } ] },
        "action": "BLOQUER", "message": "Repos de {reposHeures} h entre deux quarts : il en faut au moins 10 (art. 14.06)" }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      { "contexte": "ATTRIBUTION", "cle": "sp-csn-seize-heures", "porteeType": "CONVENTION", "porteeCode": "SPCSN",
        "condition": { "fait": "heures24h", "op": ">", "valeur": 16 },
        "action": "BLOQUER", "message": "{heures24h} h sur 24 h : au plus 16 (art. 16.02, 16.07)" }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      { "nom": "SP-CSN", "uniteCode": "EST", "debut": "2030-04-01", "fin": "2030-04-07" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "p"
    # Lundi 7 h – 18 h 15 (un quart de 11,25 h, art. 14.01 c), Marc et Julie.
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{p}}", "date": "2030-04-01", "debut": 420, "fin": 1095, "natureCode": "REG", "milieuId": "{{c12}}",
        "composition": [ { "corpsEmploiCode": "PARA", "nombre": 2 } ] }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "lunMarc"
    Et je retiens "places.1.id" sous le nom "lunJulie"
    Quand admin envoie POST /api/effectifs/planification/places/{{lunMarc}}/attribuer avec :
      """json
      { "ressourceId": "{{P2}}" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/places/{{lunJulie}}/attribuer avec :
      """json
      { "ressourceId": "{{P1}}" }
      """
    Alors la réponse a le statut 200
    # Mardi 3 h : 8,75 h de repos. Refusé pour Marc (SP-CSN) ; Julie n'est d'aucune convention.
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{p}}", "date": "2030-04-02", "debut": 180, "fin": 645, "natureCode": "REG", "milieuId": "{{c12}}",
        "composition": [ { "corpsEmploiCode": "PARA", "nombre": 2 } ] }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "tot1"
    Et je retiens "places.1.id" sous le nom "tot2"
    Quand admin envoie POST /api/effectifs/planification/places/{{tot1}}/attribuer avec :
      """json
      { "ressourceId": "{{P2}}" }
      """
    Alors la réponse a le statut 409
    Et la réponse correspond à "Repos de 8[.,]75 h entre deux quarts : il en faut au moins 10 [(]art[.] 14[.]06[)]"
    Quand admin envoie POST /api/effectifs/planification/places/{{tot2}}/attribuer avec :
      """json
      { "ressourceId": "{{P1}}" }
      """
    Alors la réponse a le statut 200
    # Mardi 4 h 15 – 12 h : dix heures de repos pile, admis.
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{p}}", "date": "2030-04-02", "debut": 255, "fin": 720, "natureCode": "REG", "milieuId": "{{c12}}",
        "composition": [ { "corpsEmploiCode": "PARA", "nombre": 1 } ] }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "matin"
    Quand admin envoie POST /api/effectifs/planification/places/{{matin}}/attribuer avec :
      """json
      { "ressourceId": "{{P2}}" }
      """
    Alors la réponse a le statut 200
    # Prolonger le matin jusqu'à 20 h 30 : 16,25 h sur 24 h. La continuité n'est pas un repos : seule 16.02 refuse.
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{p}}", "date": "2030-04-02", "debut": 720, "fin": 1230, "natureCode": "ADH", "milieuId": "{{c12}}",
        "composition": [ { "corpsEmploiCode": "PARA", "nombre": 1 } ] }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "suite1"
    Quand admin envoie POST /api/effectifs/planification/places/{{suite1}}/attribuer avec :
      """json
      { "ressourceId": "{{P2}}" }
      """
    Alors la réponse a le statut 409
    Et la réponse correspond à "16[.,]25 h sur 24 h : au plus 16 [(]art[.] 16[.]02, 16[.]07[)]"
    Et la réponse ne correspond pas à "14[.]06"
    # Jusqu'à 20 h 15 : 16 h pile, admis.
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{p}}", "date": "2030-04-02", "debut": 720, "fin": 1215, "natureCode": "ADH", "milieuId": "{{c12}}",
        "composition": [ { "corpsEmploiCode": "PARA", "nombre": 1 } ] }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "suite2"
    Quand admin envoie POST /api/effectifs/planification/places/{{suite2}}/attribuer avec :
      """json
      { "ressourceId": "{{P2}}" }
      """
    Alors la réponse a le statut 200

  @E-GN-11 @E-GN-16 @M-04
  Scénario: Une compétence exigée qui manque ou est échue au jour du quart rend inadmissible selon la règle ; renouvelée, elle débloque ; le relevé nomme ce qui échoit
    Soit un organisme d'Urgences-santé amorcé
    Quand admin envoie POST /api/effectifs/referentiels/competences avec :
      """json
      { "code": "RCR", "libelleFr": "Réanimation cardiorespiratoire", "effectifDepuis": "2026-01-01", "attributs": { "corps": ["PARA"] } }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      {
        "contexte": "ATTRIBUTION",
        "cle": "competence-exigee",
        "condition": { "fait": "competencesManquantes", "op": ">", "valeur": 0 },
        "action": "BLOQUER",
        "message": "{competencesManquantes} compétence(s) exigée(s) manquante(s) ou échue(s)",
        "effectifDepuis": "{{demain}}"
      }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      { "nom": "CMP", "uniteCode": "EST", "debut": "2030-04-01", "fin": "2030-04-07" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "cmp"
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{cmp}}", "date": "2030-04-01", "debut": 360, "fin": 1080, "natureCode": "REG", "milieuId": "{{c12}}",
        "composition": [ { "corpsEmploiCode": "PARA", "nombre": 1 } ] }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "placeCmp"
    # Sans la compétence : la règle bloque, et le dit
    Quand admin envoie POST /api/effectifs/planification/places/{{placeCmp}}/attribuer avec :
      """json
      { "ressourceId": "{{P1}}" }
      """
    Alors la réponse a le statut 409
    Et la réponse correspond à "1 compétence\(s\) exigée\(s\) manquante\(s\) ou échue\(s\)"
    # Échue la veille du quart : toujours inadmissible
    Quand admin envoie PUT /api/effectifs/planification/ressources/{{P1}}/competences/RCR avec :
      """json
      { "obtenueLe": "2026-01-10", "echeance": "2030-03-31" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/places/{{placeCmp}}/attribuer avec :
      """json
      { "ressourceId": "{{P1}}" }
      """
    Alors la réponse a le statut 409
    # Renouvelée : l'attribution passe
    Quand admin envoie PUT /api/effectifs/planification/ressources/{{P1}}/competences/RCR avec :
      """json
      { "obtenueLe": "2026-01-10", "echeance": "2032-01-10" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/places/{{placeCmp}}/attribuer avec :
      """json
      { "ressourceId": "{{P1}}" }
      """
    Alors la réponse a le statut 200
    Et la réponse correspond à "\"action\":\"ACCEPTER\""
    # Le relevé : une compétence échue figure, la plus pressante d'abord
    Quand admin envoie PUT /api/effectifs/planification/ressources/{{P2}}/competences/RCR avec :
      """json
      { "obtenueLe": "2020-01-10", "echeance": "2021-01-10" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie GET /api/effectifs/planification/competences/echeances
    Alors la réponse a le statut 200
    Et la réponse correspond à "\"code\":\"RCR\""
    Et la réponse correspond à "\"etat\":\"echue\""
