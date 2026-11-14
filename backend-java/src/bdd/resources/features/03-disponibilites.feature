# language: fr
Fonctionnalité: Disponibilités des employés
  Les employés déposent leurs disponibilités dans une période ouverte ; la conformité se juge au
  dépôt, et le motif d'une non-conformité est dit à l'employé comme au bassin de candidats.

  @E-GN-12 @E-GN-13 @E-GN-15 @E-GN-16 @E-GR-10 @E-GR-11 @C-04
  Scénario: Hors collecte, le dépôt de disponibilités est refusé en disant pourquoi ; en collecte, un dépôt trop maigre est bloqué avec son motif, un dépôt suffisant passe avec un avertissement
    Soit un organisme d'Urgences-santé amorcé
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      {
        "nom": "DISPO",
        "uniteCode": "EST",
        "debut": "2030-03-11",
        "fin": "2030-03-17"
      }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "dispo"
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      {
        "periodeId": "{{dispo}}",
        "date": "2030-03-11",
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
    Et je retiens "places.0.id" sous le nom "placeDispo"
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      {
        "contexte": "DISPONIBILITE",
        "cle": "minimum-quarts",
        "condition": {
          "fait": "quartsParSemaine",
          "op": "<",
          "valeur": 2
        },
        "action": "BLOQUER",
        "message": "Au moins 2 quarts par semaine ({quartsParSemaine} offert)",
        "effectifDepuis": "{{demain}}"
      }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      {
        "contexte": "DISPONIBILITE",
        "cle": "peu-d-heures",
        "condition": {
          "fait": "heuresParPeriode",
          "op": "<",
          "valeur": 30
        },
        "action": "AVERTIR",
        "message": "Seulement {heuresParPeriode} h offertes",
        "effectifDepuis": "{{demain}}"
      }
      """
    Alors la réponse a le statut 201
    # La période est en préparation : le dépôt n'existe que par période ouverte
    Quand julie envoie PUT /api/effectifs/disponibilites/moi/{{dispo}} avec :
      """json
      {
        "plages": [
          {
            "date": "2030-03-11",
            "debut": 360,
            "fin": 1080,
            "type": "DISPONIBLE"
          }
        ]
      }
      """
    Alors la réponse a le statut 422
    Et la réponse correspond à "collecte.*n'est pas ouverte"
    Quand admin envoie POST /api/effectifs/planification/periodes/{{dispo}}/etat avec :
      """json
      {
        "etat": "COLLECTE",
        "finCollecte": "2030-03-01"
      }
      """
    Alors la réponse a le statut 200
    Quand julie envoie PUT /api/effectifs/disponibilites/moi/{{dispo}} avec :
      """json
      {
        "plages": [
          {
            "date": "2030-03-11",
            "debut": 360,
            "fin": 1080,
            "type": "DISPONIBLE"
          }
        ]
      }
      """
    Alors la réponse a le statut 409
    Et la réponse correspond à "Au moins 2 quarts par semaine \(1 offert\)"
    Quand julie envoie PUT /api/effectifs/disponibilites/moi/{{dispo}} avec :
      """json
      {
        "plages": [
          {
            "date": "2030-03-11",
            "debut": 360,
            "fin": 1080,
            "type": "DISPONIBLE"
          },
          {
            "date": "2030-03-12",
            "debut": 360,
            "fin": 1080,
            "type": "DISPONIBLE"
          }
        ]
      }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      {
        "conformite": "AVERTIE"
      }
      """
    Et la réponse correspond à "Seulement 24 h offertes"

  @E-GN-14 @E-GN-16 @C-05
  Scénario: Le bassin de candidats d'une place exclut, motif à l'appui, la ressource qui n'a rien déposé
    Soit un organisme d'Urgences-santé amorcé
    # Le chef reçoit la gestion de l'unité : le bassin de candidats se lit dans son périmètre
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
        "nom": "DISPO",
        "uniteCode": "EST",
        "debut": "2030-03-11",
        "fin": "2030-03-17"
      }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "dispo"
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      {
        "periodeId": "{{dispo}}",
        "date": "2030-03-11",
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
    Et je retiens "places.0.id" sous le nom "placeDispo"
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      {
        "contexte": "DISPONIBILITE",
        "cle": "minimum-quarts",
        "condition": {
          "fait": "quartsParSemaine",
          "op": "<",
          "valeur": 2
        },
        "action": "BLOQUER",
        "message": "Au moins 2 quarts par semaine ({quartsParSemaine} offert)",
        "effectifDepuis": "{{demain}}"
      }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      {
        "contexte": "DISPONIBILITE",
        "cle": "peu-d-heures",
        "condition": {
          "fait": "heuresParPeriode",
          "op": "<",
          "valeur": 30
        },
        "action": "AVERTIR",
        "message": "Seulement {heuresParPeriode} h offertes",
        "effectifDepuis": "{{demain}}"
      }
      """
    Alors la réponse a le statut 201
    # La période est en préparation : le dépôt n'existe que par période ouverte
    Quand julie envoie PUT /api/effectifs/disponibilites/moi/{{dispo}} avec :
      """json
      {
        "plages": [
          {
            "date": "2030-03-11",
            "debut": 360,
            "fin": 1080,
            "type": "DISPONIBLE"
          }
        ]
      }
      """
    Alors la réponse a le statut 422
    Et la réponse correspond à "collecte.*n'est pas ouverte"
    Quand admin envoie POST /api/effectifs/planification/periodes/{{dispo}}/etat avec :
      """json
      {
        "etat": "COLLECTE",
        "finCollecte": "2030-03-01"
      }
      """
    Alors la réponse a le statut 200
    Quand julie envoie PUT /api/effectifs/disponibilites/moi/{{dispo}} avec :
      """json
      {
        "plages": [
          {
            "date": "2030-03-11",
            "debut": 360,
            "fin": 1080,
            "type": "DISPONIBLE"
          }
        ]
      }
      """
    Alors la réponse a le statut 409
    Et la réponse correspond à "Au moins 2 quarts par semaine \(1 offert\)"
    Quand julie envoie PUT /api/effectifs/disponibilites/moi/{{dispo}} avec :
      """json
      {
        "plages": [
          {
            "date": "2030-03-11",
            "debut": 360,
            "fin": 1080,
            "type": "DISPONIBLE"
          },
          {
            "date": "2030-03-12",
            "debut": 360,
            "fin": 1080,
            "type": "DISPONIBLE"
          }
        ]
      }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      {
        "conformite": "AVERTIE"
      }
      """
    Et la réponse correspond à "Seulement 24 h offertes"
    Quand chef envoie GET /api/effectifs/disponibilites/places/{{placeDispo}}/candidats
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      {
        "admissibles": [
          {
            "nom": "Julie"
          }
        ]
      }
      """
    Et la réponse correspond à "\"exclus\":\[[^\]]*\"nom\":\"Marc\"[^\]]*\"motif\":\"[^\"]+\""

  # L'échéance tombe au lendemain de la fin de collecte, à minuit UTC, et la fin de collecte ne peut
  # être passée : un scénario joué en temps réel ne peut pas l'atteindre. Le départ de la tâche à
  # l'échéance, sans geste de personne, est prouvé horloge figée par
  # ControleurDisponibilitesEffectifsHttpTest#finDeCollecteAutomatique ; on prouve ici qu'elle est
  # planifiée dès l'ouverture, et que l'attribution qu'elle déclenche donne le quart au déposant.
  @P-GH-4
  Scénario: Ouvrir la collecte planifie sa fin ; à la fin, les quarts vacants vont automatiquement à qui a déposé la disponibilité correspondante
    Soit un organisme d'Urgences-santé amorcé
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      { "nom": "Collecte", "uniteCode": "EST", "debut": "2030-03-11", "fin": "2030-03-17" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "dispo"
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{dispo}}", "date": "2030-03-11", "debut": 360, "fin": 1080, "natureCode": "REG", "milieuId": "{{c12}}",
        "modeAttribution": "AUTOMATISE", "composition": [ { "corpsEmploiCode": "PARA", "nombre": 1 } ] }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/planification/periodes/{{dispo}}/etat avec :
      """json
      { "etat": "COLLECTE", "finCollecte": "2030-03-01" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie GET /api/effectifs/disponibilites/periodes/{{dispo}}/attribution-automatique
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "periodeId": "{{dispo}}", "finCollecte": "2030-03-01", "echeanceLe": "2030-03-02T00:00:00.000Z", "statut": "en_attente", "derniere": null }
      """
    Quand julie envoie GET /api/effectifs/disponibilites/periodes/{{dispo}}/attribution-automatique
    Alors la réponse a le statut 403
    Quand julie envoie PUT /api/effectifs/disponibilites/moi/{{dispo}} avec :
      """json
      { "plages": [ { "date": "2030-03-11", "debut": 360, "fin": 1080, "type": "DISPONIBLE" } ] }
      """
    Alors la réponse a le statut 200
    # Tant que la collecte court, rien ne s'attribue.
    Quand admin envoie POST /api/effectifs/disponibilites/periodes/{{dispo}}/attribuer
    Alors la réponse a le statut 422
    Quand admin envoie POST /api/effectifs/planification/periodes/{{dispo}}/etat avec :
      """json
      { "etat": "CONFECTION" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/disponibilites/periodes/{{dispo}}/attribuer
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "attribuees": 1, "restees": 0 }
      """
    Quand admin envoie GET /api/effectifs/planification/periodes/{{dispo}}/equipages
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      [ { "places": [ { "ressourceNom": "Julie", "attribueePar": "MOTEUR" } ] } ]
      """

  @E-GN-7 @E-GN-12
  Scénario: Une règle de conformité vise une unité ou un statut d'emploi : elle ne s'applique qu'à ceux qu'elle vise
    Soit un organisme d'Urgences-santé amorcé
    Quand admin envoie POST /api/effectifs/referentiels/unites avec :
      """json
      { "effectifDepuis": "2026-01-01", "code": "OUEST", "libelleFr": "Opérations Ouest" }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      { "nom": "Collecte", "uniteCode": "EST", "debut": "2030-03-11", "fin": "2030-03-17" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "dispo"
    Quand admin envoie POST /api/effectifs/planification/periodes/{{dispo}}/etat avec :
      """json
      { "etat": "COLLECTE", "finCollecte": "2030-03-01" }
      """
    Alors la réponse a le statut 200
    # Une règle de l'unité Ouest ne vise pas Julie, qui est de l'Est.
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      { "contexte": "DISPONIBILITE", "cle": "ouest-deux-quarts", "porteeType": "UNITE", "porteeCode": "OUEST",
        "condition": { "fait": "quartsParSemaine", "op": "<", "valeur": 2 },
        "action": "BLOQUER", "message": "À l'Ouest, au moins 2 quarts par semaine", "effectifDepuis": "{{demain}}" }
      """
    Alors la réponse a le statut 201
    Quand julie envoie PUT /api/effectifs/disponibilites/moi/{{dispo}} avec :
      """json
      { "plages": [ { "date": "2030-03-11", "debut": 360, "fin": 1080, "type": "DISPONIBLE" } ] }
      """
    Alors la réponse a le statut 200
    Et la réponse ne correspond pas à "À l'Ouest"
    # Une règle des temps partiels réguliers la vise : Julie est TPR.
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      { "contexte": "DISPONIBILITE", "cle": "tpr-deux-quarts", "porteeType": "STATUT_EMPLOI", "porteeCode": "TPR",
        "condition": { "fait": "quartsParSemaine", "op": "<", "valeur": 2 },
        "action": "BLOQUER", "message": "Temps partiel régulier : au moins 2 quarts ({quartsParSemaine} offert)", "effectifDepuis": "{{demain}}" }
      """
    Alors la réponse a le statut 201
    Quand julie envoie PUT /api/effectifs/disponibilites/moi/{{dispo}} avec :
      """json
      { "plages": [ { "date": "2030-03-11", "debut": 360, "fin": 1080, "type": "DISPONIBLE" } ] }
      """
    Alors la réponse a le statut 409
    Et la réponse correspond à "Temps partiel régulier : au moins 2 quarts \(1 offert\)"
    Et la réponse ne correspond pas à "À l'Ouest"
