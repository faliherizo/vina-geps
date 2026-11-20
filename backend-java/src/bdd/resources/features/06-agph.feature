# language: fr
Fonctionnalité: AGPH — attribution annuelle des postes et remplacements long terme
  Une fois l'an, le service de planification ouvre une campagne : chacun dépose sa semaine type,
  puis choisit un poste à son tour, dans l'ordre d'ancienneté. Les règles de convention écartent les
  choix non admissibles ; la clôture donne à chacun son statut d'emploi et transfère à la gestion
  régulière les postes restés libres. Hors campagne, un remplacement long terme s'ouvre, reçoit les
  manifestations d'intérêt et s'attribue — ou demeure non comblé.

  # Le tour de rôle attend un délai d'au moins cinq minutes avant d'attribuer automatiquement le
  # choix d'un employé silencieux (P-CH-4) : un scénario joué en temps réel ne peut pas l'attendre.
  # Cette expiration est prouvée, horloge figée, par ControleurAgphEffectifsHttpTest#expiration.

  Contexte:
    Soit un organisme d'Urgences-santé amorcé
    Quand admin envoie POST /api/effectifs/referentiels/statuts_emploi avec :
      """json
      { "effectifDepuis": "2026-01-01", "code": "TP", "libelleFr": "Temps partiel", "comportement": "TEMPS_PARTIEL_OCCASIONNEL" }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/planification/ressources avec :
      """json
      { "matricule": "P3", "nom": "Cléo", "corpsEmploiCode": "PARA", "uniteCode": "EST", "statutEmploiCode": "TP", "anciennete": 1 }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "P3"
    Quand admin envoie POST /api/effectifs/planification/postes avec :
      """json
      { "code": "A-TC", "libelle": "Poste A-TC", "uniteCode": "EST", "statutEmploiCode": "TC", "corpsEmploiCode": "PARA", "milieuId": "{{c12}}",
        "natureCode": "REG", "cycle": [["J", "J", "J", null, null, null, null]], "debutCycle": "2031-01-06" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "aTc"
    Quand admin envoie POST /api/effectifs/planification/postes avec :
      """json
      { "code": "B-TPR", "libelle": "Poste B-TPR", "uniteCode": "EST", "statutEmploiCode": "TPR", "corpsEmploiCode": "PARA", "milieuId": "{{c12}}",
        "natureCode": "REG", "cycle": [["J", "J", "J", null, null, null, null]], "debutCycle": "2031-01-06" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "bTpr"
    Quand admin envoie POST /api/effectifs/planification/postes avec :
      """json
      { "code": "C-TC", "libelle": "Poste C-TC", "uniteCode": "EST", "statutEmploiCode": "TC", "corpsEmploiCode": "PARA", "milieuId": "{{c12}}",
        "natureCode": "REG", "cycle": [["J", "J", "J", null, null, null, null]], "debutCycle": "2031-01-06" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "cTc"
    Quand admin envoie POST /api/effectifs/planification/postes avec :
      """json
      { "code": "D-TPR", "libelle": "Poste D-TPR", "uniteCode": "EST", "statutEmploiCode": "TPR", "corpsEmploiCode": "PARA", "milieuId": "{{c12}}",
        "natureCode": "REG", "cycle": [["J", "J", "J", null, null, null, null]], "debutCycle": "2031-01-06" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "dTpr"
    Quand admin envoie POST /api/effectifs/planification/postes/publier avec :
      """json
      { "ids": ["{{aTc}}", "{{bTpr}}", "{{cTc}}", "{{dTpr}}"] }
      """
    Alors la réponse a le statut 200

  @E-CH-1 @P-CH-1 @P-CH-2 @P-CH-3 @P-CH-5 @P-CH-6 @P-CH-7 @E-CH-10 @E-GR-2
  Scénario: Une campagne AGPH complète — dépôt, tour de rôle, révision, clôture — puis la gestion régulière reprend les postes libres
    # Le temps complet est réservé aux deux premiers rangs.
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      { "contexte": "CHOIX_AGPH", "cle": "tc-deux-premiers",
        "condition": { "tous": [ { "fait": "statutOffert", "op": "=", "valeur": "TC" }, { "fait": "rang", "op": ">", "valeur": 2 } ] },
        "action": "BLOQUER", "message": "Le temps complet est réservé aux deux premiers rangs (rang {rang})" }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/agph/campagnes avec :
      """json
      { "uniteCode": "EST", "corpsEmploiCode": "PARA", "annee": 2031, "delaiChoixMinutes": 30 }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "etat": "PREPARATION", "nom": "AGPH 2031" }
      """
    Et je retiens "id" sous le nom "camp"
    # Une seule campagne par année, unité et corps d'emploi.
    Quand admin envoie POST /api/effectifs/agph/campagnes avec :
      """json
      { "uniteCode": "EST", "corpsEmploiCode": "PARA", "annee": 2031 }
      """
    Alors la réponse a le statut 409
    Et la réponse contient :
      """json
      { "errors": ["une campagne 2031 existe déjà pour EST (PARA)"] }
      """
    # L'ouverture fige les rangs par ancienneté : Marc (9 ans), Julie (3 ans), Cléo (1 an).
    Quand admin envoie POST /api/effectifs/agph/campagnes/{{camp}}/ouvrir
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "etat": "DEPOT" }
      """
    Quand admin envoie GET /api/effectifs/agph/campagnes/{{camp}}
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "rangs": [ { "nom": "Marc", "rang": 1 }, { "nom": "Julie", "rang": 2 }, { "nom": "Cléo", "rang": 3 } ] }
      """
    # Pendant le dépôt, la planification fixe le budget et chacun émet sa semaine type.
    Quand julie envoie PUT /api/effectifs/agph/campagnes/{{camp}}/parametres avec :
      """json
      { "budgetHeuresSemaine": 60 }
      """
    Alors la réponse a le statut 403
    Quand admin envoie PUT /api/effectifs/agph/campagnes/{{camp}}/parametres avec :
      """json
      { "budgetHeuresSemaine": 60 }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "budgetHeuresSemaine": 60 }
      """
    Quand julie envoie PUT /api/effectifs/agph/campagnes/{{camp}}/disponibilites/moi avec :
      """json
      { "plages": [ { "jour": 1, "debut": 360, "fin": 1080, "type": "DISPONIBLE" }, { "jour": 2, "debut": 360, "fin": 1080, "type": "PREFERENCE" } ] }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "conformite": "CONFORME", "heures": 24 }
      """
    # Le dépôt n'est pas le tour de rôle.
    Quand marc envoie POST /api/effectifs/agph/campagnes/{{camp}}/choix avec :
      """json
      { "posteId": "{{bTpr}}" }
      """
    Alors la réponse a le statut 409
    Et la réponse contient :
      """json
      { "errors": ["la campagne est en depot : le tour de rôle n'est pas en cours"] }
      """
    Quand admin envoie POST /api/effectifs/agph/campagnes/{{camp}}/demarrer
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "etat": "CHOIX" }
      """
    # À son tour, Marc voit les postes encore offerts ; Julie attend le sien.
    Quand marc envoie GET /api/effectifs/agph/campagnes/{{camp}}/postes-disponibles
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "peutChoisir": true, "monRang": { "rang": 1 },
        "postes": [ { "code": "A-TC", "admissible": true }, { "code": "B-TPR" }, { "code": "C-TC" }, { "code": "D-TPR" } ] }
      """
    Quand julie envoie POST /api/effectifs/agph/campagnes/{{camp}}/choix avec :
      """json
      { "posteId": "{{aTc}}" }
      """
    Alors la réponse a le statut 409
    Et la réponse contient :
      """json
      { "errors": ["ce n'est pas encore le tour de Julie (rang 2 ; tour en cours : rang 1)"] }
      """
    Quand marc envoie POST /api/effectifs/agph/campagnes/{{camp}}/choix avec :
      """json
      { "posteId": "{{bTpr}}" }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "choix": [ { "posteCode": "B-TPR", "mode": "EMPLOYE" } ], "rangs": [ { "etat": "A_CHOISI" }, { "etat": "A_SON_TOUR" } ] }
      """
    # Un seul poste par rang.
    Quand marc envoie POST /api/effectifs/agph/campagnes/{{camp}}/choix avec :
      """json
      { "posteId": "{{aTc}}" }
      """
    Alors la réponse a le statut 409
    Et la réponse contient :
      """json
      { "errors": ["Marc a déjà été servi (poste choisi) : un seul choix par rang"] }
      """
    Quand julie envoie POST /api/effectifs/agph/campagnes/{{camp}}/choix avec :
      """json
      { "posteId": "{{aTc}}" }
      """
    Alors la réponse a le statut 200
    # Cléo n'a pas de compte : la planification choisit pour elle — mais pas un temps complet au rang 3.
    Quand admin envoie POST /api/effectifs/agph/campagnes/{{camp}}/choix avec :
      """json
      { "ressourceId": "{{P3}}", "posteId": "{{cTc}}" }
      """
    Alors la réponse a le statut 409
    Et la réponse contient :
      """json
      { "verdict": { "action": "BLOQUER" }, "errors": ["Le temps complet est réservé aux deux premiers rangs (rang 3)"] }
      """
    Quand admin envoie POST /api/effectifs/agph/campagnes/{{camp}}/choix avec :
      """json
      { "ressourceId": "{{P3}}", "posteId": "{{dTpr}}" }
      """
    Alors la réponse a le statut 200
    # Tous servis : la campagne passe d'elle-même en attribution.
    Et la réponse contient :
      """json
      { "etat": "ATTRIBUTION" }
      """
    # La révision retire à Cléo son poste, motif à l'appui.
    Quand admin envoie POST /api/effectifs/agph/campagnes/{{camp}}/reviser
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "etat": "REVISION" }
      """
    Quand admin envoie POST /api/effectifs/agph/campagnes/{{camp}}/revision/choix avec :
      """json
      { "ressourceId": "{{P3}}" }
      """
    Alors la réponse a le statut 422
    Et la réponse contient :
      """json
      { "errors": ["« motif » est obligatoire"] }
      """
    Quand admin envoie POST /api/effectifs/agph/campagnes/{{camp}}/revision/choix avec :
      """json
      { "ressourceId": "{{P3}}", "motif": "Poste D-TPR réservé à un retour d'invalidité" }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "rangs": [ { "etat": "A_CHOISI" }, { "etat": "A_CHOISI" }, { "etat": "PASSE" } ] }
      """
    # La clôture donne à chacun son statut et déclare vacants les postes restés libres.
    Quand julie envoie POST /api/effectifs/agph/campagnes/{{camp}}/clore
    Alors la réponse a le statut 403
    Quand admin envoie POST /api/effectifs/agph/campagnes/{{camp}}/clore
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "etat": "CLOSE" }
      """
    Et la réponse correspond à "\"posteCode\":\"C-TC\",\"etat\":\"VACANT\""
    Et la réponse correspond à "\"posteCode\":\"D-TPR\",\"etat\":\"VACANT\""
    Quand admin envoie GET /api/effectifs/planification/ressources
    Alors la réponse a le statut 200
    Et la réponse correspond à "\"nom\":\"Marc\"[^}]*\"statutEmploiCode\":\"TPR\""
    Et la réponse correspond à "\"nom\":\"Julie\"[^}]*\"statutEmploiCode\":\"TC\""
    Et la réponse correspond à "\"nom\":\"Cléo\"[^}]*\"statutEmploiCode\":\"TP\""
    # Le transfert à la gestion régulière se lit au journal, au nom du mécanisme.
    Quand admin envoie GET /api/effectifs/journal?format=csv
    Alors la réponse a le statut 200
    Et la réponse correspond à "agph.postes.transferes"
    # La gestion régulière prend en charge les quarts des postes vacants : places sans titulaire, sous le minimum.
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      { "nom": "Mars 2031", "uniteCode": "EST", "debut": "2031-03-03", "fin": "2031-03-09" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "mars"
    Quand admin envoie POST /api/effectifs/planification/periodes/{{mars}}/generer
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "equipagesCrees": 3, "placesCreees": 12 }
      """
    Quand admin envoie GET /api/effectifs/planification/periodes/{{mars}}/comblement
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "places": 12, "pourvues": 6,
        "sousMinimum": [ { "date": "2031-03-03", "pourvues": 2 }, { "date": "2031-03-04" }, { "date": "2031-03-05" } ] }
      """

  @E-CH-7 @E-CH-8 @P-CH-9
  Scénario: Un remplacement long terme s'ouvre, reçoit les manifestations d'intérêt et s'attribue au plus ancien des intéressés
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      { "nom": "Juin 2031", "uniteCode": "EST", "debut": "2031-06-02", "fin": "2031-06-08" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "juin"
    Quand admin envoie POST /api/effectifs/planification/periodes/{{juin}}/generer
    Alors la réponse a le statut 200
    # Une fenêtre de manifestation qui se fermerait dans le passé n'a pas de sens.
    Quand admin envoie POST /api/effectifs/agph/remplacements avec :
      """json
      { "posteId": "{{cTc}}", "origine": "POSTE_VACANT", "debut": "2031-06-02", "fin": "2031-06-08", "finManifestation": "2020-01-01T00:00:00Z" }
      """
    Alors la réponse a le statut 422
    Et la réponse contient :
      """json
      { "errors": ["la fenêtre de manifestation se fermerait dans le passé"] }
      """
    Quand julie envoie POST /api/effectifs/agph/remplacements avec :
      """json
      { "posteId": "{{cTc}}", "origine": "POSTE_VACANT", "debut": "2031-06-02", "fin": "2031-06-08", "finManifestation": "{{demain}}T12:00:00Z" }
      """
    Alors la réponse a le statut 403
    Quand admin envoie POST /api/effectifs/agph/remplacements avec :
      """json
      { "posteId": "{{cTc}}", "origine": "POSTE_VACANT", "debut": "2031-06-02", "fin": "2031-06-08", "finManifestation": "{{demain}}T12:00:00Z" }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "etat": "OUVERT", "posteCode": "C-TC", "interesses": 0 }
      """
    Et je retiens "id" sous le nom "rlt"
    # Les employés admissibles se manifestent, et peuvent se retirer.
    Quand julie envoie POST /api/effectifs/agph/remplacements/{{rlt}}/interet
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "interesses": 1 }
      """
    Quand marc envoie POST /api/effectifs/agph/remplacements/{{rlt}}/interet
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "interesses": 2 }
      """
    Quand julie envoie DELETE /api/effectifs/agph/remplacements/{{rlt}}/interet
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "interesses": 1 }
      """
    Quand julie envoie POST /api/effectifs/agph/remplacements/{{rlt}}/interet
    Alors la réponse a le statut 200
    # La liste des intéressés est réservée à la planification.
    Quand julie envoie GET /api/effectifs/agph/remplacements/{{rlt}}/interesses
    Alors la réponse a le statut 403
    Quand admin envoie GET /api/effectifs/agph/remplacements/{{rlt}}/interesses
    Alors la réponse a le statut 200
    Et la réponse compte 2 éléments
    # À la fermeture, le plus ancien des intéressés — Marc — reçoit les quarts vacants du poste.
    Quand admin envoie POST /api/effectifs/agph/remplacements/{{rlt}}/fermer
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "etat": "ATTRIBUE", "attribueANom": "Marc", "placesAttribuees": 3 }
      """
    Quand julie envoie POST /api/effectifs/agph/remplacements/{{rlt}}/interet
    Alors la réponse a le statut 409
    Et la réponse contient :
      """json
      { "errors": ["la fenêtre de manifestation est fermée"] }
      """
    Quand admin envoie POST /api/effectifs/agph/remplacements/{{rlt}}/fermer
    Alors la réponse a le statut 409
    Et la réponse contient :
      """json
      { "errors": ["ce remplacement est déjà décidé"] }
      """

  @E-CH-9 @E-CH-10
  Scénario: L'encadrement laisse un remplacement non comblé, motif à l'appui : ses quarts passent à la gestion régulière
    Quand admin envoie POST /api/effectifs/planification/periodes avec :
      """json
      { "nom": "Mai 2031", "uniteCode": "EST", "debut": "2031-05-05", "fin": "2031-05-11" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "mai"
    Quand admin envoie POST /api/effectifs/planification/periodes/{{mai}}/generer
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/agph/remplacements avec :
      """json
      { "posteId": "{{cTc}}", "origine": "POSTE_VACANT", "debut": "2031-05-05", "fin": "2031-05-11", "finManifestation": "{{demain}}T12:00:00Z" }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "rlt"
    Quand marc envoie POST /api/effectifs/agph/remplacements/{{rlt}}/interet
    Alors la réponse a le statut 200
    Quand marc envoie POST /api/effectifs/agph/remplacements/{{rlt}}/non-comble avec :
      """json
      { "motif": "x" }
      """
    Alors la réponse a le statut 403
    Quand admin envoie POST /api/effectifs/agph/remplacements/{{rlt}}/non-comble avec :
      """json
      { "motif": " " }
      """
    Alors la réponse a le statut 422
    Et la réponse contient :
      """json
      { "errors": ["« motif » est obligatoire : dites pourquoi ce remplacement demeure non comblé"] }
      """
    Quand admin envoie POST /api/effectifs/agph/remplacements/{{rlt}}/non-comble avec :
      """json
      { "motif": "Budget de l'unité atteint : quarts laissés à la gestion régulière" }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "etat": "NON_COMBLE", "attribueA": null, "quartsTransferes": 3, "interesses": 1,
        "motifNonComble": "Budget de l'unité atteint : quarts laissés à la gestion régulière" }
      """
    Quand admin envoie GET /api/effectifs/journal?format=csv
    Alors la réponse a le statut 200
    Et la réponse correspond à "remplacement.transfere"

  @E-CH-1
  Scénario: Le dépôt AGPH est jugé par les règles de convention — nombre et durée des quarts — et par le budget fixé par la planification
    Quand admin envoie POST /api/effectifs/agph/campagnes avec :
      """json
      { "uniteCode": "EST", "corpsEmploiCode": "PARA", "annee": 2031 }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "camp"
    Quand admin envoie POST /api/effectifs/agph/campagnes/{{camp}}/ouvrir
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      { "contexte": "DEPOT_AGPH", "cle": "minimum-quarts", "condition": { "fait": "quartsParSemaine", "op": "<", "valeur": 2 },
        "action": "BLOQUER", "message": "Au moins 2 quarts par semaine ({quartsParSemaine} offert)" }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      { "contexte": "DEPOT_AGPH", "cle": "duree-quart", "condition": { "fait": "dureeMinutes", "op": "<", "valeur": 480 },
        "action": "BLOQUER", "message": "Un quart dure au moins 8 h" }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      { "contexte": "DEPOT_AGPH", "cle": "budget", "condition": { "fait": "tauxBudget", "op": ">", "valeur": 100 },
        "action": "AVERTIR", "message": "Les disponibilités offertes dépassent le budget ({tauxBudget} %)" }
      """
    Alors la réponse a le statut 201
    Quand admin envoie PUT /api/effectifs/agph/campagnes/{{camp}}/parametres avec :
      """json
      { "budgetHeuresSemaine": 60 }
      """
    Alors la réponse a le statut 200
    # Nombre de quarts : un seul ne suffit pas.
    Quand julie envoie PUT /api/effectifs/agph/campagnes/{{camp}}/disponibilites/moi avec :
      """json
      { "plages": [ { "jour": 1, "debut": 360, "fin": 1080, "type": "DISPONIBLE" } ] }
      """
    Alors la réponse a le statut 409
    Et la réponse correspond à "Au moins 2 quarts par semaine \(1 offert\)"
    # Durée des quarts : quatre heures ne font pas un quart.
    Quand julie envoie PUT /api/effectifs/agph/campagnes/{{camp}}/disponibilites/moi avec :
      """json
      { "plages": [ { "jour": 1, "debut": 360, "fin": 1080, "type": "DISPONIBLE" }, { "jour": 2, "debut": 360, "fin": 600, "type": "DISPONIBLE" } ] }
      """
    Alors la réponse a le statut 409
    Et la réponse correspond à "Un quart dure au moins 8 h"
    Quand julie envoie PUT /api/effectifs/agph/campagnes/{{camp}}/disponibilites/moi avec :
      """json
      { "plages": [ { "jour": 1, "debut": 360, "fin": 1080, "type": "DISPONIBLE" }, { "jour": 2, "debut": 360, "fin": 1080, "type": "DISPONIBLE" },
                    { "jour": 3, "debut": 0, "fin": 1440, "type": "DISPONIBLE" } ] }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "conformite": "CONFORME", "heures": 48 }
      """
    # Budget : Marc fait passer la campagne au-delà — averti, pas refusé ; la planification arbitre.
    Quand marc envoie PUT /api/effectifs/agph/campagnes/{{camp}}/disponibilites/moi avec :
      """json
      { "plages": [ { "jour": 4, "debut": 360, "fin": 1080, "type": "DISPONIBLE" }, { "jour": 5, "debut": 360, "fin": 1080, "type": "DISPONIBLE" } ] }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "conformite": "AVERTIE", "motifs": [ { "message": "Les disponibilités offertes dépassent le budget (120 %)" } ] }
      """
    Quand admin envoie GET /api/effectifs/agph/campagnes/{{camp}}/disponibilites
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "budgetHeuresSemaine": 60, "heuresCampagne": 72, "tauxBudget": 120 }
      """

  # AD-04 (addenda 7, Q11) : l'« optimisation des budgets » est un calcul proposé par la solution, en heures.
  @E-CH-1
  Scénario: La solution propose les postes qui couvrent le plus d'heures sans dépasser le budget, parmi ceux que les dépôts couvrent
    Quand admin envoie POST /api/effectifs/agph/campagnes avec :
      """json
      { "uniteCode": "EST", "corpsEmploiCode": "PARA", "annee": 2031 }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "camp"
    Quand admin envoie POST /api/effectifs/agph/campagnes/{{camp}}/ouvrir
    Alors la réponse a le statut 200
    # Sans budget, il n'y a rien à optimiser : la réponse le dit.
    Quand admin envoie GET /api/effectifs/agph/campagnes/{{camp}}/optimisation
    Alors la réponse a le statut 409
    Et la réponse correspond à "fixez d'abord le budget"
    Quand admin envoie PUT /api/effectifs/agph/campagnes/{{camp}}/parametres avec :
      """json
      { "budgetHeuresSemaine": 80 }
      """
    Alors la réponse a le statut 200
    # Avant tout dépôt, aucun poste n'est couvert : tous sont écartés, avec leur motif.
    Quand admin envoie GET /api/effectifs/agph/campagnes/{{camp}}/optimisation
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "budgetHeures": 80, "totalHeures": 0, "retenus": [],
        "ecartes": [ { "code": "A-TC", "motif": "AUCUNE_DISPONIBILITE", "motifLibelle": "aucune semaine type déposée ne couvre tous ses quarts" } ] }
      """
    # Julie couvre les quatre postes (lundi à mercredi, 6 h-18 h) ; Marc, le jeudi et le vendredi, aucun.
    Quand julie envoie PUT /api/effectifs/agph/campagnes/{{camp}}/disponibilites/moi avec :
      """json
      { "plages": [ { "jour": 1, "debut": 360, "fin": 1080, "type": "DISPONIBLE" }, { "jour": 2, "debut": 360, "fin": 1080, "type": "DISPONIBLE" },
                    { "jour": 3, "debut": 0, "fin": 1440, "type": "DISPONIBLE" } ] }
      """
    Alors la réponse a le statut 200
    Quand marc envoie PUT /api/effectifs/agph/campagnes/{{camp}}/disponibilites/moi avec :
      """json
      { "plages": [ { "jour": 4, "debut": 360, "fin": 1080, "type": "DISPONIBLE" }, { "jour": 5, "debut": 360, "fin": 1080, "type": "DISPONIBLE" } ] }
      """
    Alors la réponse a le statut 200
    # Quatre postes de 36 h pour 80 h de budget : deux tiennent (72 h), il reste 8 h ; les deux autres feraient dépasser.
    Quand admin envoie GET /api/effectifs/agph/campagnes/{{camp}}/optimisation
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "budgetHeures": 80, "totalHeures": 72, "resteHeures": 8,
        "retenus": [ { "code": "A-TC", "heuresSemaine": 36, "candidats": 1 }, { "code": "B-TPR", "heuresSemaine": 36, "candidats": 1 } ],
        "ecartes": [ { "code": "C-TC", "motif": "HORS_BUDGET" }, { "code": "D-TPR", "motif": "HORS_BUDGET" } ] }
      """
