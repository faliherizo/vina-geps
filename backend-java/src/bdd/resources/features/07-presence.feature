# language: fr
Fonctionnalité: Déclaration de présence — pointage en temps réel et saisie a posteriori
  L'employé pointe son quart en temps réel, au milieu de travail où le quart l'attend ; la présence
  se prouve par les mécanismes que le milieu exige, une règle configurable tranche l'écart de lieu,
  et l'encadrement décide des pointages mis en attente. Un quart non pointé se déclare après coup,
  en segments justifiés, selon le profil de pointage de la personne.

  # Le quart est posé aujourd'hui, de minuit à 23 h 59 (un quart ne dure pas 24 h pile), dans le fuseau des quarts : le pointage en
  # temps réel cherche un quart publié autour de l'instant présent.
  Contexte:
    Soit un organisme d'Urgences-santé amorcé
    Quand admin envoie POST /api/effectifs/referentiels/natures_quart avec :
      """json
      { "effectifDepuis": "2026-01-01", "code": "PRES", "libelleFr": "Régulier avec présence préalable", "comportement": "REGULIER",
        "attributs": { "presencePrealableMinutes": 15 } }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/milieux avec :
      """json
      { "code": "C20", "libelle": "Caserne 20", "typeCode": "CASERNE", "uniteCode": "EST", "latitude": 45.5, "longitude": -73.6, "rayonMetres": 100 }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "c20"
    Quand admin envoie PUT /api/effectifs/milieux/{{c20}}/preuves avec :
      """json
      { "preuves": [ { "mecanisme": "GEOREPERAGE", "exige": true }, { "mecanisme": "CODE_QR_DE_LIEU", "exige": false } ] }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      { "nom": "Aujourd'hui", "uniteCode": "EST", "debut": "{{aujourdhui}}", "fin": "{{aujourdhui}}" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "jour"
    Quand admin envoie POST /api/effectifs/planification/periodes/{{jour}}/etat avec :
      """json
      { "etat": "CONFECTION" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{jour}}", "date": "{{aujourdhui}}", "debut": 0, "fin": 1439, "natureCode": "PRES", "milieuId": "{{c20}}",
        "modeAttribution": "MANUEL", "composition": [ { "corpsEmploiCode": "PARA", "nombre": 2 } ] }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "placeJulie"
    Et je retiens "places.1.id" sous le nom "placeMarc"
    Quand admin envoie POST /api/effectifs/planification/places/{{placeJulie}}/attribuer avec :
      """json
      { "ressourceId": "{{P1}}" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/places/{{placeMarc}}/attribuer avec :
      """json
      { "ressourceId": "{{P2}}" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/periodes/{{jour}}/etat avec :
      """json
      { "etat": "PUBLIEE" }
      """
    Alors la réponse a le statut 200

  @E-GP-1 @E-GP-2 @E-GP-9 @E-GP-11 @E-GP-12
  Scénario: Julie pointe à la caserne : la preuve exigée par le milieu est satisfaite, la présence préalable compte, puis pause et sortie
    Quand julie envoie POST /api/effectifs/presence/pointages avec :
      """json
      { "sens": "ENTREE", "latitude": 45.5, "longitude": -73.6, "precisionMetres": 8 }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "sens": "ENTREE", "resultat": "CONFORME", "statut": "ENREGISTRE", "placeId": "{{placeJulie}}", "origine": "APPAREIL",
        "distanceMetres": 0, "preuves": [ { "mecanisme": "CODE_QR_DE_LIEU", "fournie": false }, { "mecanisme": "GEOREPERAGE", "exigee": true, "satisfaite": true } ] }
      """
    # La séquence est gardée : après l'entrée, seulement la pause ou la sortie.
    Quand julie envoie POST /api/effectifs/presence/pointages avec :
      """json
      { "sens": "ENTREE", "latitude": 45.5, "longitude": -73.6 }
      """
    Alors la réponse a le statut 422
    Et la réponse contient :
      """json
      { "errors": ["après l'entrée, on ne peut pointer que le début de pause ou la sortie"] }
      """
    Quand julie envoie GET /api/effectifs/presence/moi
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "ressource": { "id": "{{P1}}" }, "quart": { "milieu": "Caserne 20", "presencePrealableMinutes": 15, "prochains": ["DEBUT_PAUSE", "SORTIE"] } }
      """
    Quand julie envoie POST /api/effectifs/presence/pointages avec :
      """json
      { "sens": "DEBUT_PAUSE", "latitude": 45.5, "longitude": -73.6 }
      """
    Alors la réponse a le statut 201
    Quand julie envoie POST /api/effectifs/presence/pointages avec :
      """json
      { "sens": "FIN_PAUSE", "latitude": 45.5, "longitude": -73.6 }
      """
    Alors la réponse a le statut 201
    Quand julie envoie POST /api/effectifs/presence/pointages avec :
      """json
      { "sens": "SORTIE", "latitude": 45.5, "longitude": -73.6 }
      """
    Alors la réponse a le statut 201
    Quand julie envoie GET /api/effectifs/presence/moi
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "quart": { "prochains": [] } }
      """
    # Le code QR du lieu s'affiche pour l'encadrement, jamais pour l'employé.
    Quand julie envoie GET /api/effectifs/presence/milieux/{{c20}}/code-qr
    Alors la réponse a le statut 403
    Quand admin envoie GET /api/effectifs/presence/milieux/{{c20}}/code-qr
    Alors la réponse a le statut 200
    Et la réponse correspond à "\"valideJusqua\""

  @E-GP-4 @E-GP-10 @E-GP-13 @E-GP-14 @P-GP-2 @E-GP-6
  Scénario: Un écart de lieu déclenche l'action configurée — blocage au-delà de 5 km, mise en attente sans preuve — et l'encadrement décide, motif à l'appui
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      { "contexte": "POINTAGE", "cle": "trop-loin", "condition": { "fait": "distanceMetres", "op": ">", "valeur": 5000 },
        "action": "BLOQUER", "message": "vous êtes à {distanceMetres} m de votre milieu" }
      """
    Alors la réponse a le statut 201
    # À 10 km de la caserne : bloqué, rien d'enregistré.
    Quand marc envoie POST /api/effectifs/presence/pointages avec :
      """json
      { "sens": "ENTREE", "latitude": 45.59, "longitude": -73.6 }
      """
    Alors la réponse a le statut 409
    Et la réponse contient :
      """json
      { "verdict": { "action": "BLOQUER" }, "errors": ["vous êtes à 10008 m de votre milieu"] }
      """
    Quand marc envoie GET /api/effectifs/presence/moi
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "pointages": [] }
      """
    # Sans position, la preuve exigée manque : le pointage est gardé, en attente de décision.
    Quand marc envoie POST /api/effectifs/presence/pointages avec :
      """json
      { "sens": "ENTREE" }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "resultat": "ECART", "ecart": "PREUVE_ABSENTE", "statut": "A_APPROUVER", "distanceMetres": null }
      """
    Et je retiens "id" sous le nom "pointageMarc"
    # Un employé ne décide pas ; le chef décide dans son périmètre.
    Quand julie envoie GET /api/effectifs/presence/pointages
    Alors la réponse a le statut 403
    Quand admin envoie POST /api/effectifs/perimetres avec :
      """json
      { "utilisateurId": "{{u_chef}}", "uniteCode": "EST", "etendue": "UNITE", "niveau": "GESTION", "depuis": "2026-01-01" }
      """
    Alors la réponse a le statut 201
    Quand chef envoie GET /api/effectifs/presence/pointages?statut=A_APPROUVER
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      [ { "id": "{{pointageMarc}}", "nom": "Marc" } ]
      """
    Quand chef envoie POST /api/effectifs/presence/pointages/{{pointageMarc}}/rejeter avec :
      """json
      {}
      """
    Alors la réponse a le statut 422
    Et la réponse contient :
      """json
      { "errors": ["« motif » : un rejet se motive, la personne le lira"] }
      """
    Quand chef envoie POST /api/effectifs/presence/pointages/{{pointageMarc}}/approuver avec :
      """json
      { "motif": "Téléphone sans localisation, présence confirmée par le répartiteur" }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      [ { "statut": "APPROUVE", "decidePar": "{{u_chef}}" } ]
      """
    Quand chef envoie POST /api/effectifs/presence/pointages/{{pointageMarc}}/approuver
    Alors la réponse a le statut 409
    # Les localisations s'auditent : la distance seule est conservée, jamais la position (Loi 25).
    Quand julie envoie GET /api/effectifs/presence/audit-localisations
    Alors la réponse a le statut 403
    Quand chef envoie GET /api/effectifs/presence/audit-localisations
    Alors la réponse a le statut 200
    Et la réponse correspond à "1 déclaration\(s\) de présence auditées"
    Et la réponse correspond à "Loi 25"
    Quand chef envoie GET /api/effectifs/presence/audit-localisations.csv
    Alors la réponse a le statut 200
    Et la réponse correspond à "^\"horodatage\";\"matriculeOuNom\";\"sens\";\"milieu\""
    Et la réponse ne correspond pas à "45\.59"

  @E-GP-7 @E-GP-14
  Scénario: Le site déclaré au pointage — un autre milieu que celui du quart — se lit dans la répartition de « ma présence »
    Quand admin envoie POST /api/effectifs/milieux avec :
      """json
      { "code": "C7", "libelle": "Caserne 7", "typeCode": "CASERNE", "uniteCode": "EST" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "c7"
    Quand julie envoie POST /api/effectifs/presence/pointages avec :
      """json
      { "sens": "ENTREE", "latitude": 45.5, "longitude": -73.6 }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "milieuId": "{{c20}}" }
      """
    Quand julie envoie POST /api/effectifs/presence/pointages avec :
      """json
      { "sens": "DEBUT_PAUSE", "latitude": 45.5, "longitude": -73.6 }
      """
    Alors la réponse a le statut 201
    Quand julie envoie POST /api/effectifs/presence/pointages avec :
      """json
      { "sens": "FIN_PAUSE", "milieuId": "{{c7}}" }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "milieuId": "{{c7}}" }
      """
    Quand julie envoie GET /api/effectifs/presence/moi
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "quart": { "milieuId": "{{c20}}", "deplacements": [ { "deLibelle": "Caserne 20", "versLibelle": "Caserne 7" } ] } }
      """

  @P-GP-1 @P-GP-3 @P-GP-4 @E-GP-15
  Scénario: Un quart non pointé se déclare après coup en plusieurs segments justifiés ; l'encadrement en saisit pour autrui ; le même temps ne se déclare pas deux fois
    # Le quart d'hier de Julie, publié et jamais pointé.
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      { "nom": "Hier", "uniteCode": "EST", "debut": "{{hier}}", "fin": "{{hier}}" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "veille"
    Quand admin envoie POST /api/effectifs/planification/periodes/{{veille}}/etat avec :
      """json
      { "etat": "CONFECTION" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{veille}}", "date": "{{hier}}", "debut": 0, "fin": 1439, "natureCode": "PRES", "milieuId": "{{c20}}",
        "modeAttribution": "MANUEL", "composition": [ { "corpsEmploiCode": "PARA", "nombre": 1 } ] }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "placeHier"
    Quand admin envoie POST /api/effectifs/planification/places/{{placeHier}}/attribuer avec :
      """json
      { "ressourceId": "{{P1}}" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/periodes/{{veille}}/etat avec :
      """json
      { "etat": "PUBLIEE" }
      """
    Alors la réponse a le statut 200
    Quand julie envoie POST /api/effectifs/presence/saisies avec :
      """json
      { "segments": [ { "debut": "{{hier}}T13:00:00.000Z", "fin": "{{hier}}T17:00:00.000Z", "type": "TRAVAIL" } ], "justification": "" }
      """
    Alors la réponse a le statut 422
    Et la réponse contient :
      """json
      { "errors": ["« justification » : dites en quelques mots pourquoi le quart n'a pas été pointé"] }
      """
    Quand julie envoie POST /api/effectifs/presence/saisies avec :
      """json
      { "segments": [ { "debut": "2099-01-01T11:00:00.000Z", "fin": "2099-01-01T12:00:00.000Z", "type": "TRAVAIL" } ], "justification": "Téléphone déchargé" }
      """
    Alors la réponse a le statut 422
    Et la réponse contient :
      """json
      { "errors": ["on ne déclare après coup que du temps passé"] }
      """
    Quand julie envoie POST /api/effectifs/presence/saisies avec :
      """json
      { "segments": [
          { "debut": "{{hier}}T13:00:00.000Z", "fin": "{{hier}}T17:00:00.000Z", "type": "TRAVAIL" },
          { "debut": "{{hier}}T17:00:00.000Z", "fin": "{{hier}}T17:30:00.000Z", "type": "PAUSE" },
          { "debut": "{{hier}}T17:30:00.000Z", "fin": "{{hier}}T21:00:00.000Z", "type": "TRAVAIL" } ],
        "justification": "Téléphone déchargé" }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      [ { "sens": "ENTREE", "origine": "SAISIE", "statut": "A_APPROUVER", "justification": "Téléphone déchargé", "placeId": "{{placeHier}}" },
        { "sens": "DEBUT_PAUSE" }, { "sens": "FIN_PAUSE" }, { "sens": "SORTIE" } ]
      """
    Et je retiens "2.id" sous le nom "saisie"
    # Sur le même quart, le même temps déclaré deux fois doublerait le total.
    Quand julie envoie POST /api/effectifs/presence/saisies avec :
      """json
      { "segments": [ { "debut": "{{hier}}T14:00:00.000Z", "fin": "{{hier}}T15:00:00.000Z", "type": "TRAVAIL" } ], "justification": "Oubli" }
      """
    Alors la réponse a le statut 422
    Et la réponse correspond à "chevauche du temps déjà déclaré"
    # Un complément qui ne chevauche rien se cumule au même quart.
    Quand julie envoie POST /api/effectifs/presence/saisies avec :
      """json
      { "segments": [ { "debut": "{{hier}}T21:00:00.000Z", "fin": "{{hier}}T22:00:00.000Z", "type": "TRAVAIL" } ], "justification": "Retour après l'appel" }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/perimetres avec :
      """json
      { "utilisateurId": "{{u_chef}}", "uniteCode": "EST", "etendue": "UNITE", "niveau": "GESTION", "depuis": "2026-01-01" }
      """
    Alors la réponse a le statut 201
    # Approuver un segment approuve la saisie entière.
    Quand chef envoie POST /api/effectifs/presence/pointages/{{saisie}}/approuver
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      [ { "statut": "APPROUVE" }, { "statut": "APPROUVE" }, { "statut": "APPROUVE" }, { "statut": "APPROUVE" } ]
      """
    # Un employé ne saisit pas pour un autre ; l'encadrement le peut, et sa saisie est approuvée à son nom.
    Quand julie envoie POST /api/effectifs/presence/saisies avec :
      """json
      { "ressourceId": "{{P2}}", "segments": [ { "debut": "{{hier}}T13:00:00.000Z", "fin": "{{hier}}T17:00:00.000Z", "type": "TRAVAIL" } ],
        "justification": "Pour Marc" }
      """
    Alors la réponse a le statut 403
    Quand chef envoie POST /api/effectifs/presence/saisies avec :
      """json
      { "ressourceId": "{{P2}}", "segments": [ { "debut": "{{hier}}T13:00:00.000Z", "fin": "{{hier}}T17:00:00.000Z", "type": "TRAVAIL" } ],
        "justification": "Borne en panne" }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      [ { "statut": "APPROUVE", "saisiPar": "{{u_chef}}" } ]
      """

  @E-GP-1 @E-GP-2 @P-GP-1
  Scénario: Le profil de pointage décide du mode et des pauses admises ; retiré, il rend le défaut
    Quand julie envoie GET /api/effectifs/presence/referentiels-profils
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "modes": ["TEMPS_REEL", "A_POSTERIORI", "MIXTE"], "naturesNonPayees": ["PAUSE", "REPAS", "REPOS"] }
      """
    Quand julie envoie PUT /api/effectifs/presence/profils avec :
      """json
      { "code": "APOST" }
      """
    Alors la réponse a le statut 403
    Quand admin envoie PUT /api/effectifs/presence/profils avec :
      """json
      { "code": "APOST", "libelle": "Après coup", "mode": "A_POSTERIORI", "porteeType": "UNITE", "porteeCode": "EST",
        "naturesNonPayees": ["REPAS"], "fenetreAPosterioriJours": 2 }
      """
    Alors la réponse a le statut 200
    Quand julie envoie GET /api/effectifs/presence/mon-profil
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "profil": { "code": "APOST", "mode": "A_POSTERIORI" } }
      """
    # À saisie après coup, le pointage en temps réel n'est pas admis.
    Quand julie envoie POST /api/effectifs/presence/pointages avec :
      """json
      { "sens": "ENTREE", "latitude": 45.5, "longitude": -73.6 }
      """
    Alors la réponse a le statut 409
    Et la réponse contient :
      """json
      { "errors": ["le profil « Après coup » est à saisie après coup : le pointage en temps réel n'y est pas admis"] }
      """
    # Une pause que le profil n'admet pas est refusée ; un repas passe.
    Quand julie envoie POST /api/effectifs/presence/saisies avec :
      """json
      { "segments": [
          { "debut": "{{hier}}T13:00:00.000Z", "fin": "{{hier}}T17:00:00.000Z", "type": "TRAVAIL" },
          { "debut": "{{hier}}T17:00:00.000Z", "fin": "{{hier}}T17:30:00.000Z", "type": "PAUSE", "nature": "PAUSE" },
          { "debut": "{{hier}}T17:30:00.000Z", "fin": "{{hier}}T21:00:00.000Z", "type": "TRAVAIL" } ],
        "justification": "Téléphone déchargé" }
      """
    Alors la réponse a le statut 409
    Et la réponse contient :
      """json
      { "errors": ["le profil « Après coup » n'admet pas la pause (admises : pause-repas)"] }
      """
    Quand julie envoie POST /api/effectifs/presence/saisies avec :
      """json
      { "segments": [
          { "debut": "{{hier}}T13:00:00.000Z", "fin": "{{hier}}T17:00:00.000Z", "type": "TRAVAIL" },
          { "debut": "{{hier}}T17:00:00.000Z", "fin": "{{hier}}T17:30:00.000Z", "type": "PAUSE", "nature": "REPAS" },
          { "debut": "{{hier}}T17:30:00.000Z", "fin": "{{hier}}T21:00:00.000Z", "type": "TRAVAIL" } ],
        "justification": "Téléphone déchargé" }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      [ { "sens": "ENTREE" }, { "sens": "DEBUT_PAUSE", "naturePause": "REPAS" }, { "sens": "FIN_PAUSE" }, { "sens": "SORTIE" } ]
      """
    Quand admin envoie DELETE /api/effectifs/presence/profils/APOST
    Alors la réponse a le statut 200
    Quand julie envoie GET /api/effectifs/presence/mon-profil
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "profil": { "code": "__defaut__" } }
      """

  # La corrélation complète — temps proposé par une source tierce, à confirmer, sources concordantes ou
  # divergentes — se joue horloge figée dans ControleurTempsFluxEffectifsHttpTest#recit.
  @E-RF-13
  Scénario: Les horodatages d'un logiciel tiers s'importent pour corroborer la présence, sans doublon au rejeu, lignes fautives nommées
    Quand admin envoie POST /api/effectifs/perimetres avec :
      """json
      { "utilisateurId": "{{u_chef}}", "uniteCode": "EST", "etendue": "UNITE", "niveau": "GESTION", "depuis": "2026-01-01" }
      """
    Alors la réponse a le statut 201
    Quand julie envoie POST /api/effectifs/presence/horodatages avec :
      """json
      { "source": "borne-C20", "lignes": [ { "cle": "k1", "matricule": "P1", "sens": "ENTREE", "horodatage": "{{hier}}T12:00:00.000Z" } ] }
      """
    Alors la réponse a le statut 403
    Quand chef envoie POST /api/effectifs/presence/horodatages avec :
      """json
      { "source": "borne-C20", "lignes": [
          { "cle": "k1", "matricule": "P1", "sens": "ENTREE", "horodatage": "{{hier}}T12:00:00.000Z" },
          { "cle": "k1", "matricule": "P1", "sens": "ENTREE", "horodatage": "{{hier}}T12:00:00.000Z" },
          { "cle": "k2", "matricule": "Z9", "sens": "ENTREE", "horodatage": "{{hier}}T12:00:00.000Z" },
          { "cle": "k3", "matricule": "P1", "sens": "SORTIE", "horodatage": "hier" } ] }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "importes": 1, "doublons": 1,
        "rejets": [ { "ligne": 3, "motif": "matricule « Z9 » inconnu" }, { "ligne": 4, "motif": "horodatage invalide (ISO 8601)" } ] }
      """
    # Rejouée, la même ligne ne s'importe pas deux fois.
    Quand chef envoie POST /api/effectifs/presence/horodatages avec :
      """json
      { "source": "borne-C20", "lignes": [ { "cle": "k1", "matricule": "P1", "sens": "ENTREE", "horodatage": "{{hier}}T12:00:00.000Z" } ] }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "importes": 0, "doublons": 1 }
      """
    Quand admin envoie GET /api/effectifs/journal?action=presence.import
    Alors la réponse a le statut 200
    Et la réponse correspond à "borne-C20"

  @E-GP-15
  Scénario: Une saisie après coup porte l'activité exercée ; une activité hors référentiel est refusée
    Quand admin envoie POST /api/effectifs/referentiels/activites_presence avec :
      """json
      { "effectifDepuis": "2026-01-01", "code": "INTERVENTION", "libelleFr": "Intervention", "comportement": "INTERVENTION" }
      """
    Alors la réponse a le statut 201
    Quand julie envoie POST /api/effectifs/presence/saisies avec :
      """json
      { "segments": [ { "debut": "{{hier}}T13:00:00.000Z", "fin": "{{hier}}T17:00:00.000Z", "type": "TRAVAIL", "activiteCode": "VOL" } ],
        "justification": "Appel hors caserne" }
      """
    Alors la réponse a le statut 422
    Et la réponse correspond à "« VOL » n'est pas en vigueur"
    Quand julie envoie POST /api/effectifs/presence/saisies avec :
      """json
      { "segments": [ { "debut": "{{hier}}T13:00:00.000Z", "fin": "{{hier}}T17:00:00.000Z", "type": "TRAVAIL", "activiteCode": "INTERVENTION" } ],
        "justification": "Appel hors caserne" }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      [ { "sens": "ENTREE", "activiteCode": "INTERVENTION", "origine": "SAISIE" }, { "sens": "SORTIE" } ]
      """

  @P-GP-4
  Scénario: Deux saisies après coup hors de tout quart ne se chevauchent pas : le même temps ne compte jamais deux fois
    # Hier, Julie n'avait aucun quart planifié : sa saisie n'est rattachée à aucune place.
    Quand julie envoie POST /api/effectifs/presence/saisies avec :
      """json
      { "segments": [ { "debut": "{{hier}}T13:00:00.000Z", "fin": "{{hier}}T17:00:00.000Z", "type": "TRAVAIL" } ], "justification": "Formation" }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      [ { "placeId": null }, { "placeId": null } ]
      """
    Quand julie envoie POST /api/effectifs/presence/saisies avec :
      """json
      { "segments": [ { "debut": "{{hier}}T14:00:00.000Z", "fin": "{{hier}}T15:00:00.000Z", "type": "TRAVAIL" } ], "justification": "Oubli" }
      """
    Alors la réponse a le statut 422
    Et la réponse correspond à "chevauche du temps déjà déclaré"
    # Un complément qui ne chevauche rien se cumule.
    Quand julie envoie POST /api/effectifs/presence/saisies avec :
      """json
      { "segments": [ { "debut": "{{hier}}T17:00:00.000Z", "fin": "{{hier}}T18:00:00.000Z", "type": "TRAVAIL" } ], "justification": "Rapport" }
      """
    Alors la réponse a le statut 201
