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

  Scénario: Une politique de relance arme la cascade ; la prise en charge l'arrête ; le chef la reprend (ST-G04)
    # Critères 15, 17, 19, 32. Les étapes à échéance (relance, escalade, prochain disponible : 15, 16, 25) sont
    # éprouvées à horloge réglée dans ControleurCascadesHttpTest, redémarrage et concurrence compris.
    Quand admin envoie POST /api/alertes/politiques avec :
      """json
      { "nom": "Code bleu", "arret": "prise_en_charge",
        "etapes": [ { "cible": "initiaux", "canaux": ["poussee"], "delaiMinutes": 5 },
                    { "cible": "personne", "cibleId": "{{n_marc}}", "canaux": ["texto"], "delaiMinutes": 10 } ] }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "nom": "Code bleu", "version": 1, "actif": true }
      """
    Et je retiens "id" sous le nom "politique"
    Quand chef envoie POST /api/alertes/politiques avec :
      """json
      { "nom": "X", "arret": "toutes_lues", "etapes": [] }
      """
    Alors la réponse a le statut 403
    Quand chef envoie GET /api/alertes/politiques
    Alors la réponse a le statut 200
    Et la réponse compte 1 élément
    Quand chef envoie POST /api/alertes avec :
      """json
      { "titre": "Code bleu 512", "corps": "5e étage", "priorite": "urgente", "reponseIds": ["{{arrive}}"],
        "personnes": ["{{n_julie}}"], "politiqueId": "{{politique}}" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "alerte"
    Quand chef envoie GET /api/alertes/envoyees/{{alerte}}/cascade
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "cascade": { "etat": "armee", "etape": 1, "etapes": 2 } }
      """
    Quand julie envoie POST /api/alertes/recues/{{alerte}}/reponse avec :
      """json
      { "reponseId": "{{arrive}}" }
      """
    Alors la réponse a le statut 200
    Quand chef envoie GET /api/alertes/envoyees/{{alerte}}/cascade
    Alors la réponse contient :
      """json
      { "cascade": { "etat": "arretee" } }
      """
    Et la réponse correspond à "evenement.{3}arretee"
    Quand chef envoie POST /api/alertes/envoyees/{{alerte}}/cascade/reprendre avec :
      """json
      { "etape": 2 }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "cascade": { "etat": "armee", "etape": 2 } }
      """
    Quand chef envoie GET /api/alertes/envoyees/{{alerte}}/cascade
    Alors la réponse correspond à "evenement.{3}reprise"
    Quand julie envoie POST /api/alertes/envoyees/{{alerte}}/cascade/reprendre avec :
      """json
      { "etape": 1 }
      """
    Alors la réponse a le statut 403

  Scénario: Un texto entrant sans signature Twilio valide est refusé avant toute lecture (ST-G05)
    # Critères 4, 9 : la réponse par texto. Signature vraie, « 1 », « NON », mot inconnu, rejeu et numéro inconnu sont
    # éprouvés dans ControleurWebhooksAlertesHttpTest ; ici, la porte publique ne s'ouvre pas sans la signature.
    Quand anonyme envoie POST /api/alertes/webhooks/texto avec :
      """json
      { "MessageSid": "SM1", "From": "+15145550101", "Body": "1" }
      """
    Alors la réponse a le statut 403
