# language: fr
Fonctionnalité: Communications cliniques — alertes priorisées, réponses prédéfinies, suivi partagé
  Le socle du module « alertes » (ADR 0074 ; FX-AO-20260810, critères 1, 5, 6, 9, 10, 11, 12, 24, 31, 32).
  L'administration prépare des réponses ; un expéditeur autorisé envoie une alerte à des personnes ;
  chaque destinataire lit et répond d'un geste ; le suivi, partagé entre expéditeurs, dit qui a lu et
  qui prend en charge. Les règles R1 à R10 sont celles de
  docs/FX-AO-20260810_Dossier/07_validation_vina_geps/07_analyse_metier_socle_alertes.md.

  Contexte:
    Soit un organisme d'Urgences-santé neuf
    Quand admin envoie POST /api/alertes/reponses avec :
      """json
      { "libelle": "J'arrive", "semantique": "prise_en_charge" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "arrive"
    Quand admin envoie POST /api/alertes/reponses avec :
      """json
      { "libelle": "Indisponible", "semantique": "refus" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "indispo"

  Scénario: Le chef envoie une alerte ; Julie la lit et la prend en charge ; le directeur suit
    Quand chef envoie POST /api/alertes avec :
      """json
      { "titre": "Code bleu chambre 512", "corps": "5e étage, répondez à la prise en charge",
        "priorite": "urgente", "reponseIds": ["{{arrive}}", "{{indispo}}"],
        "personnes": ["{{n_julie}}", "{{n_marc}}", "{{n_chef}}"] }
      """
    Alors la réponse a le statut 201
    # L'expéditeur n'est pas destinataire de sa propre alerte (R2).
    Et la réponse contient :
      """json
      { "priorite": "urgente", "bilan": { "destinataires": 2, "lues": 0, "repondues": 0 } }
      """
    Et je retiens "id" sous le nom "alerte"
    Quand julie envoie GET /api/alertes/recues
    Alors la réponse a le statut 200
    Et la réponse compte 1 élément
    Et la réponse correspond à "Code bleu chambre 512"
    # Le destinataire ne voit que sa remise, jamais les autres (R6).
    Et la réponse ne correspond pas à "{{n_marc}}"
    Quand julie envoie POST /api/alertes/recues/{{alerte}}/lue
    Alors la réponse a le statut 200
    Quand julie envoie POST /api/alertes/recues/{{alerte}}/reponse avec :
      """json
      { "reponseId": "{{arrive}}" }
      """
    Alors la réponse a le statut 200
    Quand julie envoie POST /api/alertes/recues/{{alerte}}/reponse avec :
      """json
      { "reponseId": "{{indispo}}" }
      """
    Alors la réponse a le statut 409
    Quand directeur envoie GET /api/alertes/envoyees/{{alerte}}
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "bilan": { "destinataires": 2, "lues": 1, "repondues": 1 } }
      """
    Et la réponse correspond à "\"priseEnChargePar\":\"[^\"]+\""

  Scénario: Une personne qui n'est pas destinataire ne trouve pas l'alerte ; un employé n'en envoie pas
    Quand chef envoie POST /api/alertes avec :
      """json
      { "titre": "Remplacement inhalothérapie", "corps": "Qui peut prendre la garde de nuit ?",
        "priorite": "urgente", "reponseIds": ["{{arrive}}"], "personnes": ["{{n_julie}}"] }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "alerte"
    Quand marc envoie POST /api/alertes/recues/{{alerte}}/lue
    Alors la réponse a le statut 404
    Quand marc envoie GET /api/alertes/recues
    Alors la réponse compte 0 élément
    Quand julie envoie POST /api/alertes avec :
      """json
      { "titre": "x", "corps": "x", "priorite": "information", "personnes": ["{{n_marc}}"] }
      """
    Alors la réponse a le statut 403
    Quand julie envoie GET /api/alertes/envoyees
    Alors la réponse a le statut 403

  Scénario: L'administration tient les modèles ; une réponse désactivée n'est plus proposée à l'envoi
    Quand admin envoie POST /api/alertes/modeles avec :
      """json
      { "titre": "Code bleu", "corps": "Chambre à préciser", "priorite": "critique", "reponseIds": ["{{arrive}}"] }
      """
    Alors la réponse a le statut 201
    Quand chef envoie POST /api/alertes/modeles avec :
      """json
      { "titre": "Interdit", "corps": "x", "priorite": "urgente" }
      """
    Alors la réponse a le statut 403
    Quand chef envoie GET /api/alertes/modeles
    Alors la réponse compte 1 élément
    Quand admin envoie PATCH /api/alertes/reponses/{{indispo}} avec :
      """json
      { "active": false }
      """
    Alors la réponse a le statut 200
    Quand chef envoie POST /api/alertes avec :
      """json
      { "titre": "Info", "corps": "x", "priorite": "information", "reponseIds": ["{{indispo}}"], "personnes": ["{{n_julie}}"] }
      """
    Alors la réponse a le statut 400
    Et la réponse correspond à "n'existe pas ou n'est plus active"

  Scénario: La priorité critique se réserve à qui en a le droit (ST-G01, critère 7)
    Quand chef envoie POST /api/alertes avec :
      """json
      { "titre": "Code bleu chambre 512", "corps": "5e étage", "priorite": "critique", "personnes": ["{{n_julie}}"] }
      """
    Alors la réponse a le statut 403
    Quand directeur envoie POST /api/alertes avec :
      """json
      { "titre": "Code bleu chambre 512", "corps": "5e étage", "priorite": "critique", "personnes": ["{{n_julie}}"] }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "priorite": "critique", "bilan": { "destinataires": 1 } }
      """
