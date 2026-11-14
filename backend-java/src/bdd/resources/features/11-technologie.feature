# language: fr
Fonctionnalité: Exigences technologiques vérifiables par l'API
  Ce qui, des exigences technologiques, se constate en appelant le serveur : modules activés par
  organisme, profils d'utilisateurs distincts, séparation des données entre clients, interface
  OpenAPI 3, réponses en français, délai des transactions de base, bus et entrepôt de données.
  Le reste — hébergement, disponibilité, soutien, engagements d'entreprise — se prouve ailleurs
  (voir docs/26-015-DO_AO/11-EXIGENCES-BDD.md).

  @E-TN-4 @E-TN-3
  Scénario: L'organisme décide d'activer une fonctionnalité ; désactivée, elle se refuse en la nommant
    Soit un organisme d'Urgences-santé amorcé
    Quand admin envoie GET /api/effectifs/agph/campagnes
    Alors la réponse a le statut 200
    Quand superadmin envoie PUT /api/saas/tenants/{{tenant}}/modules avec :
      """json
      { "modules": ["effectifs", "effectifs_planification", "effectifs_regles"] }
      """
    Alors la réponse a le statut 200
    Quand admin envoie GET /api/effectifs/agph/campagnes
    Alors la réponse a le statut 403
    Et la réponse contient :
      """json
      { "errors": ["module 'effectifs_agph' non activé pour cet organisme"] }
      """
    # Un administrateur d'organisme ne s'active pas lui-même une fonctionnalité.
    Quand admin envoie PUT /api/saas/tenants/{{tenant}}/modules avec :
      """json
      { "modules": ["effectifs", "effectifs_agph"] }
      """
    Alors la réponse a le statut 403
    Quand superadmin envoie PUT /api/saas/tenants/{{tenant}}/modules avec :
      """json
      { "modules": ["effectifs", "effectifs_planification", "effectifs_regles", "effectifs_agph"] }
      """
    Alors la réponse a le statut 200
    Quand admin envoie GET /api/effectifs/agph/campagnes
    Alors la réponse a le statut 200

  @E-TN-5
  Scénario: Les profils d'utilisateurs sont distincts : l'employé, le gestionnaire et l'administration n'ont pas les mêmes gestes
    Soit un organisme d'Urgences-santé amorcé
    # L'employé ne paramètre pas les référentiels…
    Quand julie envoie POST /api/effectifs/referentiels/unites avec :
      """json
      { "effectifDepuis": "2026-01-01", "code": "NORD", "libelleFr": "Nord" }
      """
    Alors la réponse a le statut 403
    # … ni ne gère les campagnes ; le gestionnaire sans périmètre non plus.
    Quand julie envoie POST /api/effectifs/agph/campagnes avec :
      """json
      { "uniteCode": "EST", "corpsEmploiCode": "PARA", "annee": 2031 }
      """
    Alors la réponse a le statut 403
    Quand chef envoie GET /api/effectifs/presence/pointages
    Alors la réponse a le statut 200
    Et la réponse compte 0 élément
    # L'administration paramètre.
    Quand admin envoie POST /api/effectifs/referentiels/unites avec :
      """json
      { "effectifDepuis": "2026-01-01", "code": "NORD", "libelleFr": "Nord" }
      """
    Alors la réponse a le statut 201
    # L'anonyme ne voit rien.
    Quand anonyme envoie GET /api/effectifs/planification/ressources
    Alors la réponse a le statut 401

  @E-TN-6
  Scénario: Les données d'un organisme sont séparées de celles d'un autre : ni lues, ni modifiables, ni même nommées
    Soit un organisme d'Urgences-santé amorcé
    Et un second organisme d'Urgences-santé amorcé, dont les acteurs et les valeurs portent le suffixe B
    Quand admin envoie GET /api/effectifs/planification/ressources
    Alors la réponse a le statut 200
    Et la réponse compte 2 éléments
    Et la réponse correspond à "{{P1}}"
    Et la réponse ne correspond pas à "{{P1B}}"
    Quand adminB envoie GET /api/effectifs/planification/ressources
    Alors la réponse a le statut 200
    Et la réponse ne correspond pas à "{{P1}}"
    # Un identifiant de l'autre organisme est introuvable, pas interdit : son existence ne se divulgue pas.
    Quand adminB envoie PUT /api/effectifs/planification/ressources/{{P1}} avec :
      """json
      { "matricule": "P1", "nom": "Julie piratée", "corpsEmploiCode": "PARA", "uniteCode": "EST" }
      """
    Alors la réponse a le statut 404
    Quand admin envoie GET /api/effectifs/planification/ressources
    Alors la réponse a le statut 200
    Et la réponse ne correspond pas à "Julie piratée"
    Quand julieB envoie GET /api/effectifs/presence/moi
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "ressource": { "id": "{{P1B}}" } }
      """

  @E-TN-20 @P-TN-17
  Scénario: L'API se décrit en OpenAPI 3, pour un compte authentifié
    Soit un organisme d'Urgences-santé neuf
    Quand anonyme envoie GET /api/integrations/openapi.json
    Alors la réponse a le statut 401
    Quand admin envoie GET /api/integrations/openapi.json
    Alors la réponse a le statut 200
    Et la réponse correspond à "\"openapi\" *: *\"3\."
    Et la réponse correspond à "/api/effectifs/"

  @E-TN-21
  Scénario: Les réponses de l'API sont en français, y compris les refus
    Soit un organisme d'Urgences-santé amorcé
    Quand admin envoie POST /api/effectifs/agph/campagnes avec :
      """json
      {}
      """
    Alors la réponse a le statut 422
    Et la réponse contient :
      """json
      { "errors": ["« uniteCode » est obligatoire"] }
      """
    Quand admin envoie GET /api/effectifs/agph/campagnes/inconnue
    Alors la réponse a le statut 404
    Et la réponse contient :
      """json
      { "errors": ["campagne « inconnue » introuvable"] }
      """

  # La mesure contractuelle (p95 sous charge) relève de la campagne JMeter (perf/jmeter) ; ce scénario
  # garde un plancher : une transaction de base, à vide, ne prend jamais deux secondes.
  @E-TN-17
  Scénario: Les transactions de base s'exécutent en moins de deux secondes
    Soit un organisme d'Urgences-santé amorcé
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      { "nom": "Semaine 10", "uniteCode": "EST", "debut": "2030-03-04", "fin": "2030-03-10" }
      """
    Alors la réponse a le statut 201
    Et la réponse arrive en moins de 2000 millisecondes
    Et je retiens "id" sous le nom "s10"
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{s10}}", "date": "2030-03-05", "debut": 360, "fin": 1080, "natureCode": "REG", "milieuId": "{{c12}}",
        "modeAttribution": "MANUEL", "composition": [ { "corpsEmploiCode": "PARA", "nombre": 1 } ] }
      """
    Alors la réponse a le statut 201
    Et la réponse arrive en moins de 2000 millisecondes
    Et je retiens "places.0.id" sous le nom "place"
    Quand admin envoie POST /api/effectifs/planification/places/{{place}}/attribuer avec :
      """json
      { "ressourceId": "{{P1}}" }
      """
    Alors la réponse a le statut 200
    Et la réponse arrive en moins de 2000 millisecondes
    Quand julie envoie POST /api/effectifs/absences avec :
      """json
      { "personneId": "{{P1}}", "motif": "MAL", "du": "2030-03-05", "au": "2030-03-05" }
      """
    Alors la réponse arrive en moins de 2000 millisecondes
    Quand julie envoie GET /api/effectifs/presence/moi
    Alors la réponse a le statut 200
    Et la réponse arrive en moins de 2000 millisecondes

  @E-TN-7 @P-TN-7
  Scénario: Le journal part au bus d'entreprise en événements, et à l'entrepôt de données en versements, à partir d'un curseur
    Soit un organisme d'Urgences-santé amorcé
    Quand admin envoie POST /api/effectifs/integrations/relais/ESB
    Alors la réponse a le statut 409
    Et la réponse contient :
      """json
      { "errors": ["le connecteur ESB n'est pas activé pour cet organisme : activez-le dans Intégrations"] }
      """
    Quand admin envoie PUT /api/effectifs/integrations/connecteurs/ESB avec :
      """json
      { "actif": true }
      """
    Alors la réponse a le statut 200
    Quand admin envoie PUT /api/effectifs/integrations/connecteurs/ENTREPOT avec :
      """json
      { "actif": true }
      """
    Alors la réponse a le statut 200
    # Un geste qui publie un événement : une place attribuée.
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      { "nom": "Semaine 10", "uniteCode": "EST", "debut": "2030-03-04", "fin": "2030-03-10" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "s10"
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{s10}}", "date": "2030-03-05", "debut": 360, "fin": 1080, "natureCode": "REG", "milieuId": "{{c12}}",
        "modeAttribution": "MANUEL", "composition": [ { "corpsEmploiCode": "PARA", "nombre": 1 } ] }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "place"
    Quand admin envoie POST /api/effectifs/planification/places/{{place}}/attribuer avec :
      """json
      { "ressourceId": "{{P1}}" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/integrations/relais/ESB
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "deposes": 1 }
      """
    Et je retiens "curseur" sous le nom "curseur"
    Quand admin envoie GET /api/effectifs/integrations/messages?connecteur=ESB
    Alors la réponse a le statut 200
    Et la réponse correspond à "effectifs.place.attribuee"
    # Le curseur avance : relayer à nouveau ne republie rien.
    Quand admin envoie POST /api/effectifs/integrations/relais/ESB
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "deposes": 0 }
      """
    Quand admin envoie POST /api/effectifs/integrations/relais/ENTREPOT
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "deposes": 1 }
      """
    Quand julie envoie POST /api/effectifs/integrations/relais/ESB
    Alors la réponse a le statut 403
