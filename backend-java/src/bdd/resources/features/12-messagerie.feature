# language: fr
Fonctionnalité: Messagerie sécurisée du personnel — à deux, en groupe, en privé, en appel
  Le personnel se joint comme sur les messageries qu'il connaît : un fil par personne, des groupes,
  une réponse qui ne va qu'à l'émetteur, une réaction plutôt qu'un message de plus, un appel à deux ou
  à plusieurs. Le serveur reste seul juge : on ne lit, ne réagit et n'appelle que là où l'on participe
  (docs/urgences-sante/REFONTE-MESSAGERIE.md § 5). Chaque scénario se joue avec plusieurs comptes réels.

  Contexte:
    Soit un organisme d'Urgences-santé neuf
    # La messagerie désigne chacun par son nom de connexion : {{n_julie}}, {{n_marc}}…

  Scénario: Julie écrit à Marc : un seul fil à deux, que Marc lit ; le chef ne le voit pas
    Quand julie envoie POST /api/communication/conversations/directe avec :
      """json
      { "personId": "{{n_marc}}" }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "type": "individuelle" }
      """
    Et je retiens "id" sous le nom "fil"
    # Un second « Nouveau message » à Marc rouvre le même fil — et Marc, qui écrit à Julie, aussi.
    Quand julie envoie POST /api/communication/conversations/directe avec :
      """json
      { "personId": "{{n_marc}}" }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "id": "{{fil}}" }
      """
    Quand marc envoie POST /api/communication/conversations/directe avec :
      """json
      { "personId": "{{n_julie}}" }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "id": "{{fil}}" }
      """
    Quand julie envoie POST /api/communication/conversations/{{fil}}/messages avec :
      """json
      { "corps": "Peux-tu prendre mon quart de mardi ?" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "message"
    Quand marc envoie GET /api/communication/conversations/non-lus
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "total": 1 }
      """
    Quand marc envoie GET /api/communication/conversations/{{fil}}/messages
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      [ { "id": "{{message}}", "auteurId": "{{n_julie}}", "luParMoi": false } ]
      """
    Quand marc envoie POST /api/communication/conversations/{{fil}}/lu
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "marques": 1 }
      """
    # Julie voit que Marc l'a lu.
    Quand julie envoie GET /api/communication/messages/{{message}}/lectures
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      [ { "personId": "{{n_marc}}" } ]
      """
    # Le chef ne voit ni le fil ni son existence.
    Quand chef envoie GET /api/communication/conversations/{{fil}}
    Alors la réponse a le statut 404
    Quand chef envoie GET /api/communication/conversations/{{fil}}/messages
    Alors la réponse a le statut 404

  Scénario: Dans un groupe, Julie répond à Marc seulement — le chef, qui a lancé le fil, ne voit pas cette réponse
    Quand chef envoie POST /api/communication/conversations avec :
      """json
      { "sujet": "Garde Est, nuit", "participants": ["{{n_julie}}", "{{n_marc}}"] }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "groupe"
    Quand chef envoie POST /api/communication/conversations/{{groupe}}/messages avec :
      """json
      { "corps": "Briefing à 18 h 45, salle 2." }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "annonce"
    Quand julie envoie POST /api/communication/conversations/{{groupe}}/messages avec :
      """json
      { "corps": "Tu me remplaces mardi ?", "priveA": "{{n_marc}}", "repondAuMessageId": "{{annonce}}" }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "priveA": "{{n_marc}}", "repondAuMessageId": "{{annonce}}" }
      """
    Et je retiens "id" sous le nom "prive"
    Quand marc envoie GET /api/communication/conversations/{{groupe}}/messages
    Alors la réponse a le statut 200
    Et la réponse compte 2 éléments
    Quand chef envoie GET /api/communication/conversations/{{groupe}}/messages
    Alors la réponse a le statut 200
    Et la réponse compte 1 élément
    Et la réponse ne correspond pas à "remplaces"
    Quand chef envoie GET /api/communication/messages/recherche?q=remplaces
    Alors la réponse a le statut 200
    Et la réponse compte 0 élément
    # On n'écrit pas en privé à quelqu'un qui n'est pas du fil.
    Quand julie envoie POST /api/communication/conversations/{{groupe}}/messages avec :
      """json
      { "corps": "x", "priveA": "{{n_directeur}}" }
      """
    Alors la réponse a le statut 400
    Et la réponse contient :
      """json
      { "errors": ["« priveA » : cette personne ne participe pas à la conversation"] }
      """

  Scénario: Une réaction accuse réception sans message de plus ; recommencer la retire ; une réaction inconnue est refusée
    Quand chef envoie POST /api/communication/conversations avec :
      """json
      { "sujet": "Garde Est, nuit", "participants": ["{{n_julie}}", "{{n_marc}}"] }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "groupe"
    Quand chef envoie POST /api/communication/conversations/{{groupe}}/messages avec :
      """json
      { "corps": "Briefing à 18 h 45." }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "annonce"
    Quand marc envoie POST /api/communication/messages/{{annonce}}/reactions avec :
      """json
      { "reaction": "pouce" }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "pose": true, "reactions": [ { "reaction": "pouce", "nombre": 1, "parMoi": true } ] }
      """
    Quand julie envoie GET /api/communication/conversations/{{groupe}}/messages
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      [ { "reactions": [ { "reaction": "pouce", "nombre": 1, "parMoi": false, "mots": "bien reçu" } ] } ]
      """
    Quand marc envoie POST /api/communication/messages/{{annonce}}/reactions avec :
      """json
      { "reaction": "pouce" }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "pose": false, "reactions": [] }
      """
    Quand marc envoie POST /api/communication/messages/{{annonce}}/reactions avec :
      """json
      { "reaction": "bravo" }
      """
    Alors la réponse a le statut 400
    Et la réponse contient :
      """json
      { "errors": ["« reaction » : pouce, fait, merci, regarde"] }
      """
    Quand directeur envoie POST /api/communication/messages/{{annonce}}/reactions avec :
      """json
      { "reaction": "pouce" }
      """
    Alors la réponse a le statut 404

  Scénario: Julie appelle Marc ; Marc décroche, Julie raccroche
    Quand julie envoie POST /api/communication/appels avec :
      """json
      { "appeleId": "{{n_marc}}" }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "etat": "sonne", "appeleId": "{{n_marc}}", "entrant": false }
      """
    Et je retiens "id" sous le nom "appel"
    Quand marc envoie GET /api/communication/appels/en-cours
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      [ { "id": "{{appel}}", "entrant": true } ]
      """
    # Seule la personne appelée décroche.
    Quand julie envoie POST /api/communication/appels/{{appel}}/decrocher
    Alors la réponse a le statut 400
    Quand marc envoie POST /api/communication/appels/{{appel}}/decrocher
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "etat": "en_cours" }
      """
    Quand julie envoie POST /api/communication/appels/{{appel}}/raccrocher
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "etat": "termine" }
      """
    Et la réponse correspond à "\"dureeSecondes\":[0-9]+"

  Scénario: Le chef appelle le groupe depuis le fil ; Julie rejoint, Marc ignore ; le dernier qui part termine l'appel
    Quand chef envoie POST /api/communication/conversations avec :
      """json
      { "sujet": "Garde Est, nuit", "participants": ["{{n_julie}}", "{{n_marc}}"] }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "groupe"
    Quand chef envoie POST /api/communication/conversations/{{groupe}}/appel
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "etat": "sonne", "presents": 1, "monEtat": "present" }
      """
    Et je retiens "id" sous le nom "appel"
    # Un second appel pendant le premier : on rejoint plutôt.
    Quand julie envoie POST /api/communication/conversations/{{groupe}}/appel
    Alors la réponse a le statut 409
    Et la réponse contient :
      """json
      { "errors": ["un appel de groupe est déjà en cours dans ce fil : rejoignez-le"] }
      """
    Quand julie envoie GET /api/communication/appels-groupe/en-cours
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      [ { "id": "{{appel}}", "entrant": true, "monEtat": "invite" } ]
      """
    Quand julie envoie POST /api/communication/appels-groupe/{{appel}}/rejoindre
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "etat": "en_cours", "presents": 2 }
      """
    Quand marc envoie POST /api/communication/appels-groupe/{{appel}}/ignorer
    Alors la réponse a le statut 200
    Quand marc envoie GET /api/communication/appels-groupe/en-cours
    Alors la réponse a le statut 200
    Et la réponse compte 0 élément
    # Le directeur n'est pas du fil : l'appel n'existe pas pour lui.
    Quand directeur envoie POST /api/communication/appels-groupe/{{appel}}/rejoindre
    Alors la réponse a le statut 404
    Quand julie envoie POST /api/communication/appels-groupe/{{appel}}/quitter
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "etat": "en_cours", "presents": 1 }
      """
    Quand chef envoie POST /api/communication/appels-groupe/{{appel}}/quitter
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "etat": "termine", "presents": 0 }
      """

  Scénario: Une personne retirée du groupe ne voit plus la suite, ne réagit plus et n'est pas invitée à l'appel suivant
    Quand chef envoie POST /api/communication/conversations avec :
      """json
      { "sujet": "Garde Est, nuit", "participants": ["{{n_julie}}", "{{n_marc}}"] }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "groupe"
    Quand chef envoie POST /api/communication/conversations/{{groupe}}/messages avec :
      """json
      { "corps": "Avant le départ de Marc." }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "avant"
    Quand chef envoie DELETE /api/communication/conversations/{{groupe}}/participants/{{n_marc}}
    Alors la réponse a le statut 200
    Quand chef envoie POST /api/communication/conversations/{{groupe}}/messages avec :
      """json
      { "corps": "Après le départ de Marc." }
      """
    Alors la réponse a le statut 201
    Quand marc envoie GET /api/communication/conversations/{{groupe}}/messages
    Alors la réponse a le statut 200
    Et la réponse correspond à "Avant le départ"
    Et la réponse ne correspond pas à "Après le départ"
    Quand marc envoie POST /api/communication/messages/{{avant}}/reactions avec :
      """json
      { "reaction": "pouce" }
      """
    Alors la réponse a le statut 409
    Quand chef envoie POST /api/communication/conversations/{{groupe}}/appel
    Alors la réponse a le statut 201
    Et la réponse ne correspond pas à "{{n_marc}}"
    Quand marc envoie GET /api/communication/appels-groupe/en-cours
    Alors la réponse a le statut 200
    Et la réponse compte 0 élément

  Scénario: Le cycle de vie d'un message — envoyé, reçu, lu, modifié avec son historique, supprimé
    Quand chef envoie POST /api/communication/conversations avec :
      """json
      { "sujet": "Garde Est, nuit", "participants": ["{{n_julie}}", "{{n_marc}}"] }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "groupe"
    Quand julie envoie POST /api/communication/conversations/{{groupe}}/messages avec :
      """json
      { "corps": "Briefing à 18 h" }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "statut": { "destinataires": 2, "recus": 0, "lus": 0, "libelle": "Envoyé" } }
      """
    Et je retiens "id" sous le nom "message"
    # Marc relève ses avis : la notification lui est parvenue.
    Quand marc envoie GET /api/communication/conversations/non-lus
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "total": 1 }
      """
    Quand julie envoie GET /api/communication/conversations/{{groupe}}/messages
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      [ { "statut": { "recus": 1, "libelle": "Reçu par 1 sur 2" } } ]
      """
    Quand marc envoie POST /api/communication/messages/{{message}}/lu
    Alors la réponse a le statut 200
    Quand julie envoie GET /api/communication/conversations/{{groupe}}/messages
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      [ { "statut": { "lus": 1, "libelle": "Lu par 1 sur 2" } } ]
      """
    # L'acheminement n'est montré qu'à l'auteur.
    Quand marc envoie GET /api/communication/conversations/{{groupe}}/messages
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      [ { "statut": null, "luParMoi": true } ]
      """
    # Modifier : l'auteur seul ; la version remplacée rejoint l'historique.
    Quand marc envoie PUT /api/communication/messages/{{message}} avec :
      """json
      { "corps": "Briefing annulé" }
      """
    Alors la réponse a le statut 403
    Quand julie envoie PUT /api/communication/messages/{{message}} avec :
      """json
      { "corps": "Briefing à 18 h 45, salle 2" }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "modifie": true, "corps": "Briefing à 18 h 45, salle 2" }
      """
    Quand marc envoie GET /api/communication/messages/{{message}}/historique
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      [ { "version": 1, "corps": "Briefing à 18 h", "courante": false }, { "version": 2, "corps": "Briefing à 18 h 45, salle 2", "courante": true } ]
      """
    # Supprimer : le chef, modérateur du fil, le peut ; le texte ne se sert plus à personne.
    Quand marc envoie DELETE /api/communication/messages/{{message}}
    Alors la réponse a le statut 403
    Quand chef envoie DELETE /api/communication/messages/{{message}}
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "supprime": true, "corps": null, "supprimePar": "{{n_chef}}" }
      """
    Quand marc envoie GET /api/communication/conversations/{{groupe}}/messages
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      [ { "supprime": true, "corps": null } ]
      """
    Et la réponse ne correspond pas à "Briefing"
    Quand marc envoie GET /api/communication/messages/{{message}}/historique
    Alors la réponse a le statut 409

  Scénario: Une pièce jointe voyage avec son fichier, se télécharge à l'identique dans le fil, et passe l'antivirus
    Quand julie envoie POST /api/communication/conversations/directe avec :
      """json
      { "personId": "{{n_marc}}" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "fil"
    Quand julie envoie POST /api/communication/conversations/{{fil}}/messages avec :
      """json
      { "corps": "La consigne de la nuit", "piecesJointes": [ { "nom": "consigne.pdf", "typeMime": "application/pdf", "contenu": "JVBERi0xLjQgY29uc2lnbmUgZGUgZ2FyZGU=" } ] }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "piecesJointes": [ { "nom": "consigne.pdf", "typeMime": "application/pdf", "taille": 26 } ] }
      """
    Et je retiens "piecesJointes.0.id" sous le nom "piece"
    Quand marc envoie GET /api/communication/pieces-jointes/{{piece}}
    Alors la réponse a le statut 200
    Et la réponse correspond à "^%PDF-1.4 consigne de garde$"
    # Hors du fil, la pièce n'existe pas.
    Quand chef envoie GET /api/communication/pieces-jointes/{{piece}}
    Alors la réponse a le statut 404
    # Une pièce sans fichier ne transporterait rien ; une pièce infectée est refusée en la nommant.
    Quand julie envoie POST /api/communication/conversations/{{fil}}/messages avec :
      """json
      { "corps": "Sans fichier", "piecesJointes": [ { "nom": "vide.pdf", "typeMime": "application/pdf" } ] }
      """
    Alors la réponse a le statut 400
    Et la réponse contient :
      """json
      { "errors": ["pièce jointe 1 : contenu requis : le fichier, encodé en base64"] }
      """
    Quand julie envoie POST /api/communication/conversations/{{fil}}/messages avec :
      """json
      { "corps": "Attention", "piecesJointes": [ { "nom": "test.txt", "typeMime": "text/plain", "contenu": "WDVPIVAlQEFQWzRcUFpYNTQoUF4pN0NDKTd9JEVJQ0FSLVNUQU5EQVJELUFOVElWSVJVUy1URVNULUZJTEUhJEgrSCo=" } ] }
      """
    Alors la réponse a le statut 400
    Et la réponse contient :
      """json
      { "errors": ["fichier refusé par l'antivirus (Eicar-Test-Signature)"] }
      """
