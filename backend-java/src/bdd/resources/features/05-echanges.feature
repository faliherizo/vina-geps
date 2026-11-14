# language: fr
Fonctionnalité: Échanges de quarts entre employés
  Deux employés échangent leurs quarts publiés ; l'échange est jugé dans les deux sens — admissibilité,
  repos entre deux quarts — et un échange entre deux périodes suit la règle que l'organisme a configurée.

  Contexte:
    Soit un organisme d'Urgences-santé amorcé
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      { "nom": "Semaine 10", "uniteCode": "EST", "debut": "2030-03-04", "fin": "2030-03-10" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "s10"
    Quand admin envoie POST /api/effectifs/planification/periodes/{{s10}}/etat avec :
      """json
      { "etat": "CONFECTION" }
      """
    Alors la réponse a le statut 200
    # Le lundi de Julie
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{s10}}", "date": "2030-03-04", "debut": 360, "fin": 1080, "natureCode": "REG", "milieuId": "{{c12}}",
        "modeAttribution": "MANUEL", "composition": [ { "corpsEmploiCode": "PARA", "nombre": 1 } ] }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "julieLun"
    Quand admin envoie POST /api/effectifs/planification/places/{{julieLun}}/attribuer avec :
      """json
      { "ressourceId": "{{P1}}" }
      """
    Alors la réponse a le statut 200
    # Le mercredi de Marc
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{s10}}", "date": "2030-03-06", "debut": 360, "fin": 1080, "natureCode": "REG", "milieuId": "{{c12}}",
        "modeAttribution": "MANUEL", "composition": [ { "corpsEmploiCode": "PARA", "nombre": 1 } ] }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "marcMer"
    Quand admin envoie POST /api/effectifs/planification/places/{{marcMer}}/attribuer avec :
      """json
      { "ressourceId": "{{P2}}" }
      """
    Alors la réponse a le statut 200

  @E-GR-7
  Scénario: Un échange se propose, s'accepte par l'autre employé seulement, et s'applique aux deux horaires
    Quand admin envoie POST /api/effectifs/planification/periodes/{{s10}}/etat avec :
      """json
      { "etat": "PUBLIEE" }
      """
    Alors la réponse a le statut 200
    Quand julie envoie GET /api/effectifs/echanges/places/{{julieLun}}/demandables
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      [ { "placeId": "{{marcMer}}", "nom": "Marc" } ]
      """
    Quand julie envoie POST /api/effectifs/echanges avec :
      """json
      { "placeCedeeId": "{{julieLun}}", "placeVoulueId": "{{marcMer}}" }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "statut": "PROPOSE", "nomA": "Julie", "nomB": "Marc", "actionRegles": "ACCEPTER" }
      """
    Et je retiens "id" sous le nom "echange"
    # Celui qui propose n'accepte pas à la place de l'autre.
    Quand julie envoie POST /api/effectifs/echanges/{{echange}}/accepter
    Alors la réponse a le statut 403
    Quand marc envoie POST /api/effectifs/echanges/{{echange}}/accepter
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "statut": "APPLIQUE" }
      """
    Quand julie envoie GET /api/effectifs/echanges/moi
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "quarts": [ { "placeId": "{{marcMer}}" } ] }
      """

  @E-GR-7
  Scénario: Un échange qui priverait un employé de son repos est bloqué, motif à l'appui ; un quart non publié ne s'échange pas
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      { "contexte": "ECHANGE", "cle": "repos", "condition": { "fait": "reposHeures", "op": "<", "valeur": 8 },
        "action": "BLOQUER", "message": "repos de {reposHeures} h, il en faut 8", "effectifDepuis": "{{demain}}" }
      """
    Alors la réponse a le statut 201
    # Marc travaille déjà le lundi soir jusqu'à minuit…
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{s10}}", "date": "2030-03-04", "debut": 1080, "fin": 1440, "natureCode": "REG", "milieuId": "{{c12}}",
        "modeAttribution": "MANUEL", "composition": [ { "corpsEmploiCode": "PARA", "nombre": 1 } ] }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "marcLunSoir"
    Quand admin envoie POST /api/effectifs/planification/places/{{marcLunSoir}}/attribuer avec :
      """json
      { "ressourceId": "{{P2}}" }
      """
    Alors la réponse a le statut 200
    # … et Julie a la nuit qui suit, de minuit à six heures.
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{s10}}", "date": "2030-03-05", "debut": 0, "fin": 360, "natureCode": "REG", "milieuId": "{{c12}}",
        "modeAttribution": "MANUEL", "composition": [ { "corpsEmploiCode": "PARA", "nombre": 1 } ] }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "julieNuit"
    Quand admin envoie POST /api/effectifs/planification/places/{{julieNuit}}/attribuer avec :
      """json
      { "ressourceId": "{{P1}}" }
      """
    Alors la réponse a le statut 200
    # Tant que la période n'est pas publiée, rien ne s'échange.
    Quand julie envoie POST /api/effectifs/echanges avec :
      """json
      { "placeCedeeId": "{{julieNuit}}", "placeVoulueId": "{{marcMer}}" }
      """
    Alors la réponse a le statut 422
    Et la réponse correspond à "n'est pas publiée"
    Quand admin envoie POST /api/effectifs/planification/periodes/{{s10}}/etat avec :
      """json
      { "etat": "PUBLIEE" }
      """
    Alors la réponse a le statut 200
    # Marc prendrait la nuit de Julie juste après son lundi soir : aucune heure de repos.
    Quand julie envoie POST /api/effectifs/echanges avec :
      """json
      { "placeCedeeId": "{{julieNuit}}", "placeVoulueId": "{{marcMer}}" }
      """
    Alors la réponse a le statut 409
    Et la réponse contient :
      """json
      { "verdict": { "action": "BLOQUER" }, "errors": ["Pour Marc : repos de 0 h, il en faut 8"] }
      """
    # On n'échange pas un quart avec soi-même.
    Quand julie envoie POST /api/effectifs/echanges avec :
      """json
      { "placeCedeeId": "{{julieLun}}", "placeVoulueId": "{{julieLun}}" }
      """
    Alors la réponse a le statut 422
    Et la réponse contient :
      """json
      { "errors": ["on n'échange pas un quart avec soi-même"] }
      """

  @E-GR-12
  Scénario: Un échange entre deux périodes suit la règle configurée — ici, la validation par le chef
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      { "contexte": "ECHANGE", "cle": "entre-periodes", "condition": { "fait": "memePeriode", "op": "=", "valeur": false },
        "action": "EXIGER_APPROBATION", "message": "échange entre deux périodes : le chef valide", "effectifDepuis": "{{demain}}" }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      { "nom": "Semaine 11", "uniteCode": "EST", "debut": "2030-03-11", "fin": "2030-03-17" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "s11"
    Quand admin envoie POST /api/effectifs/planification/periodes/{{s11}}/etat avec :
      """json
      { "etat": "CONFECTION" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{s11}}", "date": "2030-03-11", "debut": 360, "fin": 1080, "natureCode": "REG", "milieuId": "{{c12}}",
        "modeAttribution": "MANUEL", "composition": [ { "corpsEmploiCode": "PARA", "nombre": 1 } ] }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "marcLun11"
    Quand admin envoie POST /api/effectifs/planification/places/{{marcLun11}}/attribuer avec :
      """json
      { "ressourceId": "{{P2}}" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/periodes/{{s10}}/etat avec :
      """json
      { "etat": "PUBLIEE" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/periodes/{{s11}}/etat avec :
      """json
      { "etat": "PUBLIEE" }
      """
    Alors la réponse a le statut 200
    Quand julie envoie POST /api/effectifs/echanges avec :
      """json
      { "placeCedeeId": "{{julieLun}}", "placeVoulueId": "{{marcLun11}}" }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "actionRegles": "EXIGER_APPROBATION" }
      """
    Et je retiens "id" sous le nom "echange"
    Quand marc envoie POST /api/effectifs/echanges/{{echange}}/accepter avec :
      """json
      {}
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "statut": "A_VALIDER" }
      """
    # Un employé ne valide pas ; le chef valide dans son périmètre.
    Quand julie envoie POST /api/effectifs/echanges/{{echange}}/valider
    Alors la réponse a le statut 403
    Quand admin envoie POST /api/effectifs/perimetres avec :
      """json
      { "utilisateurId": "{{u_chef}}", "uniteCode": "EST", "etendue": "UNITE", "niveau": "GESTION", "depuis": "2026-01-01" }
      """
    Alors la réponse a le statut 201
    Quand chef envoie POST /api/effectifs/echanges/{{echange}}/valider avec :
      """json
      { "motif": "Remplacement couvert" }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "statut": "APPLIQUE", "motifDecision": "Remplacement couvert" }
      """

  @E-GR-12
  Scénario: Sans règle, un échange entre deux périodes s'applique ; une règle peut aussi l'interdire
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      { "nom": "Semaine 11", "uniteCode": "EST", "debut": "2030-03-11", "fin": "2030-03-17" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "s11"
    Quand admin envoie POST /api/effectifs/planification/periodes/{{s11}}/etat avec :
      """json
      { "etat": "CONFECTION" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{s11}}", "date": "2030-03-11", "debut": 360, "fin": 1080, "natureCode": "REG", "milieuId": "{{c12}}",
        "modeAttribution": "MANUEL", "composition": [ { "corpsEmploiCode": "PARA", "nombre": 1 } ] }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "marcLun11"
    Quand admin envoie POST /api/effectifs/planification/places/{{marcLun11}}/attribuer avec :
      """json
      { "ressourceId": "{{P2}}" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/periodes/{{s10}}/etat avec :
      """json
      { "etat": "PUBLIEE" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/periodes/{{s11}}/etat avec :
      """json
      { "etat": "PUBLIEE" }
      """
    Alors la réponse a le statut 200
    # Autorisé : aucune règle ne parle des échanges entre périodes.
    Quand julie envoie POST /api/effectifs/echanges avec :
      """json
      { "placeCedeeId": "{{julieLun}}", "placeVoulueId": "{{marcLun11}}" }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "actionRegles": "ACCEPTER" }
      """
    Et je retiens "id" sous le nom "echange"
    Quand marc envoie POST /api/effectifs/echanges/{{echange}}/accepter
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "statut": "APPLIQUE" }
      """
    # Interdit : l'organisme bloque désormais tout échange entre deux périodes.
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      { "contexte": "ECHANGE", "cle": "entre-periodes-interdit", "condition": { "fait": "memePeriode", "op": "=", "valeur": false },
        "action": "BLOQUER", "message": "les échanges entre deux périodes sont interdits", "effectifDepuis": "{{demain}}" }
      """
    Alors la réponse a le statut 201
    # Le lundi de la semaine 11 est désormais à Julie : Marc ne peut plus le lui reprendre contre son mercredi.
    Quand marc envoie POST /api/effectifs/echanges avec :
      """json
      { "placeCedeeId": "{{marcMer}}", "placeVoulueId": "{{marcLun11}}" }
      """
    Alors la réponse a le statut 409
    Et la réponse contient :
      """json
      { "verdict": { "action": "BLOQUER" },
        "errors": ["Pour Julie : les échanges entre deux périodes sont interdits ; Pour Marc : les échanges entre deux périodes sont interdits"] }
      """
