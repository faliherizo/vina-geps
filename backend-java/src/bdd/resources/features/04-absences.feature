# language: fr
Fonctionnalité: Absences, dérogation et comblement
  Une absence se juge par les règles : acceptée d'office quand tout est conforme, refusée d'office
  quand elle ferait tomber le comblement — et seul un utilisateur autorisé peut y déroger.

  @P-GN-2 @P-GH-2 @E-GN-6 @E-GR-3 @C-06
  Scénario: Une absence qui ferait tomber le comblement est refusée d'office, et le refus est conservé avec son motif
    Soit un organisme d'Urgences-santé amorcé
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      {
        "nom": "ABS",
        "uniteCode": "EST",
        "debut": "2030-03-04",
        "fin": "2030-03-10"
      }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "abs"
    Quand admin envoie POST /api/effectifs/planification/periodes/{{abs}}/etat avec :
      """json
      {
        "etat": "CONFECTION"
      }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      {
        "periodeId": "{{abs}}",
        "date": "2030-03-04",
        "debut": 360,
        "fin": 1080,
        "natureCode": "REG",
        "milieuId": "{{c12}}",
        "modeAttribution": "AUTOMATISE",
        "composition": [
          {
            "corpsEmploiCode": "PARA",
            "nombre": 1
          }
        ]
      }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "placeLundi"
    Quand admin envoie POST /api/effectifs/planification/places/{{placeLundi}}/attribuer avec :
      """json
      {
        "ressourceId": "{{P1}}"
      }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      {
        "periodeId": "{{abs}}",
        "date": "2030-03-05",
        "debut": 360,
        "fin": 1080,
        "natureCode": "REG",
        "milieuId": "{{c12}}",
        "modeAttribution": "AUTOMATISE",
        "composition": [
          {
            "corpsEmploiCode": "PARA",
            "nombre": 1
          }
        ]
      }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "placeMardi"
    Quand admin envoie POST /api/effectifs/planification/places/{{placeMardi}}/attribuer avec :
      """json
      {
        "ressourceId": "{{P1}}"
      }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      {
        "contexte": "ABSENCE",
        "cle": "comblement-minimal",
        "condition": {
          "fait": "tauxComblementApres",
          "op": "<",
          "valeur": 60
        },
        "action": "REFUSER",
        "message": "Le comblement tomberait à {tauxComblementApres} %",
        "effectifDepuis": "{{demain}}"
      }
      """
    Alors la réponse a le statut 201
    Quand julie envoie POST /api/effectifs/absences avec :
      """json
      {
        "motif": "MAL",
        "du": "2030-03-04",
        "au": "2030-03-05",
        "commentaire": "Grippe"
      }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      {
        "statut": "REFUSEE_AUTO",
        "placesLiberees": 0
      }
      """
    Et la réponse correspond à "Le comblement tomberait à 0 %"
    Et je retiens "ids.0" sous le nom "refusee"
    # L'impact se lit avant de décider : le taux avant et après
    Quand admin envoie GET /api/effectifs/absences/{{refusee}}/impact
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      {
        "tauxAvant": 100,
        "tauxApres": 0
      }
      """

  @E-GN-5 @E-GN-6 @E-CH-6 @E-GN-9 @C-07
  Scénario: La dérogation exige un droit, un motif et un code ; elle accorde l'absence et rend les places vacantes par un mécanisme journalisé
    Soit un organisme d'Urgences-santé amorcé
    # Le chef reçoit la gestion de l'unité : sans ce périmètre, l'absence lui est introuvable
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
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      {
        "nom": "ABS",
        "uniteCode": "EST",
        "debut": "2030-03-04",
        "fin": "2030-03-10"
      }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "abs"
    Quand admin envoie POST /api/effectifs/planification/periodes/{{abs}}/etat avec :
      """json
      {
        "etat": "CONFECTION"
      }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      {
        "periodeId": "{{abs}}",
        "date": "2030-03-04",
        "debut": 360,
        "fin": 1080,
        "natureCode": "REG",
        "milieuId": "{{c12}}",
        "modeAttribution": "AUTOMATISE",
        "composition": [
          {
            "corpsEmploiCode": "PARA",
            "nombre": 1
          }
        ]
      }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "placeLundi"
    Quand admin envoie POST /api/effectifs/planification/places/{{placeLundi}}/attribuer avec :
      """json
      {
        "ressourceId": "{{P1}}"
      }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      {
        "periodeId": "{{abs}}",
        "date": "2030-03-05",
        "debut": 360,
        "fin": 1080,
        "natureCode": "REG",
        "milieuId": "{{c12}}",
        "modeAttribution": "AUTOMATISE",
        "composition": [
          {
            "corpsEmploiCode": "PARA",
            "nombre": 1
          }
        ]
      }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "placeMardi"
    Quand admin envoie POST /api/effectifs/planification/places/{{placeMardi}}/attribuer avec :
      """json
      {
        "ressourceId": "{{P1}}"
      }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      {
        "contexte": "ABSENCE",
        "cle": "comblement-minimal",
        "condition": {
          "fait": "tauxComblementApres",
          "op": "<",
          "valeur": 60
        },
        "action": "REFUSER",
        "message": "Le comblement tomberait à {tauxComblementApres} %",
        "effectifDepuis": "{{demain}}"
      }
      """
    Alors la réponse a le statut 201
    Quand julie envoie POST /api/effectifs/absences avec :
      """json
      {
        "motif": "MAL",
        "du": "2030-03-04",
        "au": "2030-03-05",
        "commentaire": "Grippe"
      }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      {
        "statut": "REFUSEE_AUTO",
        "placesLiberees": 0
      }
      """
    Et la réponse correspond à "Le comblement tomberait à 0 %"
    Et je retiens "ids.0" sous le nom "refusee"
    # L'impact se lit avant de décider : le taux avant et après
    Quand admin envoie GET /api/effectifs/absences/{{refusee}}/impact
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      {
        "tauxAvant": 100,
        "tauxApres": 0
      }
      """
    Quand julie envoie POST /api/effectifs/absences/{{refusee}}/decisions avec :
      """json
      {
        "decision": "DEROGER",
        "motif": "Hospitalisation confirmée par billet médical",
        "codeAbsence": "MAL"
      }
      """
    Alors la réponse a le statut 403
    Quand chef envoie POST /api/effectifs/absences/{{refusee}}/decisions avec :
      """json
      {
        "decision": "DEROGER",
        "motif": "court",
        "codeAbsence": "MAL"
      }
      """
    Alors la réponse a le statut 422
    Quand chef envoie POST /api/effectifs/absences/{{refusee}}/decisions avec :
      """json
      {
        "decision": "DEROGER",
        "motif": "Hospitalisation confirmée par billet médical",
        "codeAbsence": "MAL"
      }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      {
        "statut": "ACCEPTEE",
        "derogationCode": "MAL",
        "placesLiberees": 2,
        "decideePar": "{{u_chef}}"
      }
      """
    Quand admin envoie GET /api/effectifs/journal?action=places.vacantes
    Alors la réponse a le statut 200
    Et la réponse correspond à "places\.vacantes"

  @P-GN-3 @P-GH-1 @C-08
  Scénario: Une absence conforme est acceptée d'office ; un code réservé à un titre d'emploi est refusé à qui ne le porte pas
    Soit un organisme d'Urgences-santé amorcé
    Quand marc envoie POST /api/effectifs/absences avec :
      """json
      {
        "motif": "MAL",
        "du": "2030-03-08"
      }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      {
        "statut": "ACCEPTEE"
      }
      """
    Quand marc envoie POST /api/effectifs/absences avec :
      """json
      {
        "motif": "FORM",
        "du": "2030-04-01"
      }
      """
    Alors la réponse a le statut 422

  @E-GR-4 @P-GN-1 @P-GH-3
  Scénario: Après une dérogation, le quart automatisé va au prioritaire disponible et le quart manuel est offert aux admissibles, avisés
    Soit un organisme d'Urgences-santé amorcé
    Quand admin envoie POST /api/effectifs/perimetres avec :
      """json
      { "utilisateurId": "{{u_chef}}", "uniteCode": "EST", "etendue": "UNITE", "niveau": "GESTION", "depuis": "2026-01-01" }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      { "nom": "ABS", "uniteCode": "EST", "debut": "2030-03-04", "fin": "2030-03-10" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "abs"
    # Marc dépose ses disponibilités pendant la collecte : il sera candidat.
    Quand admin envoie POST /api/effectifs/planification/periodes/{{abs}}/etat avec :
      """json
      { "etat": "COLLECTE", "finCollecte": "2030-02-28" }
      """
    Alors la réponse a le statut 200
    Quand marc envoie PUT /api/effectifs/disponibilites/moi/{{abs}} avec :
      """json
      { "plages": [ { "date": "2030-03-04", "debut": 0, "fin": 1440, "type": "DISPONIBLE" },
                    { "date": "2030-03-05", "debut": 0, "fin": 1440, "type": "DISPONIBLE" } ] }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/periodes/{{abs}}/etat avec :
      """json
      { "etat": "CONFECTION" }
      """
    Alors la réponse a le statut 200
    # Le lundi de Julie se comble automatiquement ; son mardi, manuellement.
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{abs}}", "date": "2030-03-04", "debut": 360, "fin": 1080, "natureCode": "REG", "milieuId": "{{c12}}",
        "modeAttribution": "AUTOMATISE", "composition": [ { "corpsEmploiCode": "PARA", "nombre": 1 } ] }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "placeLundi"
    Quand admin envoie POST /api/effectifs/planification/places/{{placeLundi}}/attribuer avec :
      """json
      { "ressourceId": "{{P1}}" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{abs}}", "date": "2030-03-05", "debut": 360, "fin": 1080, "natureCode": "REG", "milieuId": "{{c12}}",
        "modeAttribution": "MANUEL", "composition": [ { "corpsEmploiCode": "PARA", "nombre": 1 } ] }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "placeMardi"
    Quand admin envoie POST /api/effectifs/planification/places/{{placeMardi}}/attribuer avec :
      """json
      { "ressourceId": "{{P1}}" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      { "contexte": "ABSENCE", "cle": "comblement-minimal", "condition": { "fait": "tauxComblementApres", "op": "<", "valeur": 60 },
        "action": "REFUSER", "message": "Le comblement tomberait à {tauxComblementApres} %", "effectifDepuis": "{{demain}}" }
      """
    Alors la réponse a le statut 201
    Quand julie envoie POST /api/effectifs/absences avec :
      """json
      { "motif": "MAL", "du": "2030-03-04", "au": "2030-03-05" }
      """
    Alors la réponse a le statut 201
    Et je retiens "ids.0" sous le nom "absence"
    Quand chef envoie POST /api/effectifs/absences/{{absence}}/decisions avec :
      """json
      { "decision": "DEROGER", "motif": "Hospitalisation confirmée par billet médical", "codeAbsence": "MAL" }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "placesLiberees": 2, "comblement": { "attribuees": 1, "offertes": 1, "sansCandidat": 0 } }
      """
    # Le bilan se lit place par place : à qui le quart est allé, qui a été avisé.
    Quand chef envoie GET /api/effectifs/absences/{{absence}}/comblement
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "places": [ { "resultat": "ATTRIBUEE", "mode": "AUTOMATISE", "attribueeANom": "Marc" },
                    { "resultat": "OFFERTE", "avis": [ { "nom": "Marc" } ] } ] }
      """
    # Une employée ne lit pas le bilan de comblement.
    Quand julie envoie GET /api/effectifs/absences/{{absence}}/comblement
    Alors la réponse a le statut 403

  @P-CH-8
  Scénario: Une absence de long terme réserve ses places et ouvre un remplacement long terme qui lui est lié
    Soit un organisme d'Urgences-santé amorcé
    Quand admin envoie POST /api/effectifs/perimetres avec :
      """json
      { "utilisateurId": "{{u_chef}}", "uniteCode": "EST", "etendue": "UNITE", "niveau": "GESTION", "depuis": "2026-01-01" }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/referentiels/codes_absence avec :
      """json
      { "effectifDepuis": "2026-01-01", "code": "CLD", "libelleFr": "Congé de longue durée", "comportement": "NON_PAYE",
        "attributs": { "duree": "LONG_TERME", "planifiable": true } }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/planification/postes avec :
      """json
      { "code": "EST-1", "libelle": "Poste EST-1", "uniteCode": "EST", "statutEmploiCode": "TC", "corpsEmploiCode": "PARA",
        "milieuId": "{{c12}}", "natureCode": "REG", "cycle": [["J", null, null, null, null, null, null]], "debutCycle": "2030-03-04" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "poste"
    Quand admin envoie POST /api/effectifs/planification/postes/publier avec :
      """json
      { "ids": ["{{poste}}"] }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      { "nom": "LONG", "uniteCode": "EST", "debut": "2030-03-04", "fin": "2030-03-10" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "periode"
    Quand admin envoie POST /api/effectifs/planification/periodes/{{periode}}/etat avec :
      """json
      { "etat": "CONFECTION" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{periode}}", "date": "2030-03-06", "debut": 360, "fin": 1080, "natureCode": "REG", "milieuId": "{{c12}}",
        "modeAttribution": "AUTOMATISE", "composition": [ { "corpsEmploiCode": "PARA", "nombre": 1 } ] }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "place"
    Quand admin envoie POST /api/effectifs/planification/places/{{place}}/attribuer avec :
      """json
      { "ressourceId": "{{P2}}" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/absences avec :
      """json
      { "personneId": "{{P2}}", "motif": "CLD", "du": "2030-03-06", "au": "2030-05-31" }
      """
    Alors la réponse a le statut 201
    Et je retiens "ids.0" sous le nom "absence"
    Quand chef envoie GET /api/effectifs/absences/{{absence}}/comblement
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "places": [ { "resultat": "REMPLACEMENT_LONG_TERME" } ], "remplacementPossible": true }
      """
    Quand chef envoie POST /api/effectifs/absences/{{absence}}/remplacement avec :
      """json
      { "posteId": "{{poste}}", "finManifestation": "2030-01-20T17:00:00.000Z" }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "origine": "ABSENCE", "referenceId": "{{absence}}", "debut": "2030-03-06", "fin": "2030-05-31", "etat": "OUVERT" }
      """
    # Un second remplacement pour la même absence est un conflit.
    Quand chef envoie POST /api/effectifs/absences/{{absence}}/remplacement avec :
      """json
      { "posteId": "{{poste}}", "finManifestation": "2030-01-20T17:00:00.000Z" }
      """
    Alors la réponse a le statut 409

  # AD-02 (addenda 7, Q7) : un seuil par code d'absence choisit le processus ; une période en cours se comble en gestion régulière.
  @E-CH-6 @E-CH-7 @P-CH-8 @E-GR-4
  Scénario: Le seuil du code décide du remplacement long terme, sauf pendant une période horaire en cours
    Soit un organisme d'Urgences-santé amorcé
    Quand admin envoie POST /api/effectifs/referentiels/codes_absence avec :
      """json
      { "effectifDepuis": "2026-01-01", "code": "MALS", "libelleFr": "Maladie (seuil de 14 jours)", "comportement": "PAYE",
        "attributs": { "duree": "PONCTUELLE", "planifiable": true, "seuilJoursLongTerme": 14 } }
      """
    Alors la réponse a le statut 201
    # Un seuil hors bornes est refusé : il se règle de 1 à 365 jours.
    Quand admin envoie POST /api/effectifs/referentiels/codes_absence avec :
      """json
      { "effectifDepuis": "2026-01-01", "code": "MALX", "libelleFr": "Seuil impossible", "comportement": "PAYE",
        "attributs": { "duree": "PONCTUELLE", "seuilJoursLongTerme": 0 } }
      """
    Alors la réponse a le statut 422
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      { "nom": "AVENIR", "uniteCode": "EST", "debut": "2030-03-04", "fin": "2030-03-31" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "avenir"
    Quand admin envoie POST /api/effectifs/planification/periodes/{{avenir}}/etat avec :
      """json
      { "etat": "CONFECTION" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{avenir}}", "date": "2030-03-06", "debut": 360, "fin": 1080, "natureCode": "REG", "milieuId": "{{c12}}",
        "modeAttribution": "MANUEL", "composition": [ { "corpsEmploiCode": "PARA", "nombre": 2 } ] }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "placeMarc"
    Et je retiens "places.1.id" sous le nom "placeJulie"
    Quand admin envoie POST /api/effectifs/planification/places/{{placeMarc}}/attribuer avec :
      """json
      { "ressourceId": "{{P2}}" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/places/{{placeJulie}}/attribuer avec :
      """json
      { "ressourceId": "{{P1}}" }
      """
    Alors la réponse a le statut 200
    # Vingt jours d'absence, au-delà du seuil de 14 : la place est réservée au remplacement long terme.
    Quand admin envoie POST /api/effectifs/absences avec :
      """json
      { "personneId": "{{P2}}", "motif": "MALS", "du": "2030-03-06", "au": "2030-03-25" }
      """
    Alors la réponse a le statut 201
    Et je retiens "ids.0" sous le nom "longue"
    Quand admin envoie GET /api/effectifs/absences/{{longue}}/comblement
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "places": [ { "resultat": "REMPLACEMENT_LONG_TERME" } ], "remplacementPossible": true }
      """
    # Trois jours, sous le seuil, même code : la place se comble quart par quart.
    Quand admin envoie POST /api/effectifs/absences avec :
      """json
      { "personneId": "{{P1}}", "motif": "MALS", "du": "2030-03-06", "au": "2030-03-08" }
      """
    Alors la réponse a le statut 201
    Et je retiens "ids.0" sous le nom "courte"
    Quand admin envoie GET /api/effectifs/absences/{{courte}}/comblement
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "remplacementPossible": false }
      """
    Et la réponse ne correspond pas à "REMPLACEMENT_LONG_TERME"
    # Une période publiée et commencée aujourd'hui est « en cours » : la même absence longue s'y comble en gestion régulière.
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      { "nom": "EN COURS", "uniteCode": "EST", "debut": "{{aujourdhui}}", "fin": "{{demain}}" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "encours"
    Quand admin envoie POST /api/effectifs/planification/periodes/{{encours}}/etat avec :
      """json
      { "etat": "CONFECTION" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{encours}}", "date": "{{demain}}", "debut": 360, "fin": 1080, "natureCode": "REG", "milieuId": "{{c12}}",
        "modeAttribution": "MANUEL", "composition": [ { "corpsEmploiCode": "PARA", "nombre": 1 } ] }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "placeEnCours"
    Quand admin envoie POST /api/effectifs/planification/places/{{placeEnCours}}/attribuer avec :
      """json
      { "ressourceId": "{{P2}}" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/periodes/{{encours}}/etat avec :
      """json
      { "etat": "PUBLIEE" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/absences avec :
      """json
      { "personneId": "{{P2}}", "motif": "MALS", "du": "{{demain}}", "au": "{{dans30jours}}" }
      """
    Alors la réponse a le statut 201
    Et je retiens "ids.0" sous le nom "pendant"
    Quand admin envoie GET /api/effectifs/absences/{{pendant}}/comblement
    Alors la réponse a le statut 200
    Et la réponse correspond à "\"placeId\":\"{{placeEnCours}}\""
    Et la réponse ne correspond pas à "REMPLACEMENT_LONG_TERME"
