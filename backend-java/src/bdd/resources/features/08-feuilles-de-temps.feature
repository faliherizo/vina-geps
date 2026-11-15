# language: fr
Fonctionnalité: Feuilles de temps — préparation, saisie en versions, approbation, paie et relevés
  La feuille de temps se prépare d'elle-même depuis l'horaire publié ; elle se complète et se corrige
  en versions, chaque saisie jugée en temps réel par les règles de l'organisme ; les anomalies et les
  heures supplémentaires y sont calculées et affichées ; elle s'approuve par paliers, selon un flux
  configurable, puis part à la paie dans un lot structuré, et nourrit les relevés.

  Contexte:
    Soit un organisme d'Urgences-santé amorcé
    Quand admin envoie POST /api/effectifs/referentiels/types_heure avec :
      """json
      { "effectifDepuis": "2026-01-01", "code": "REG", "libelleFr": "Régulier", "comportement": "TRAVAILLEE", "attributs": { "codePaie": "100" } }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/referentiels/types_heure avec :
      """json
      { "effectifDepuis": "2026-01-01", "code": "HS", "libelleFr": "Heures supplémentaires", "comportement": "SUPPLEMENTAIRE",
        "attributs": { "codePaie": "150", "majorationPourcent": 50 } }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/referentiels/jours_feries avec :
      """json
      { "effectifDepuis": "2026-01-01", "code": "2030-03-06", "libelleFr": "Férié d'essai" }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      { "contexte": "FEUILLE_DE_TEMPS", "cle": "journee-max", "condition": { "fait": "heuresJour", "op": ">", "valeur": 16 },
        "action": "BLOQUER", "message": "plus de 16 h déclarées le même jour ({heuresJour} h)", "effectifDepuis": "{{demain}}" }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      { "contexte": "HEURES_SUPPLEMENTAIRES", "cle": "ferie", "condition": { "fait": "jourFerie", "op": "=", "valeur": true },
        "action": "AVERTIR", "message": "travail un jour férié : majoration à vérifier", "effectifDepuis": "{{demain}}" }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/perimetres avec :
      """json
      { "utilisateurId": "{{u_chef}}", "uniteCode": "EST", "etendue": "UNITE", "niveau": "GESTION", "depuis": "2026-01-01" }
      """
    Alors la réponse a le statut 201
    # L'horaire publié : Julie le mardi et le mercredi (férié), Marc le mardi — douze heures chacun.
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
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{s10}}", "date": "2030-03-05", "debut": 360, "fin": 1080, "natureCode": "REG", "milieuId": "{{c12}}",
        "modeAttribution": "MANUEL", "composition": [ { "corpsEmploiCode": "PARA", "nombre": 2 } ] }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "julieMar"
    Et je retiens "places.1.id" sous le nom "marcMar"
    Quand admin envoie POST /api/effectifs/planification/equipages avec :
      """json
      { "periodeId": "{{s10}}", "date": "2030-03-06", "debut": 360, "fin": 1080, "natureCode": "REG", "milieuId": "{{c12}}",
        "modeAttribution": "MANUEL", "composition": [ { "corpsEmploiCode": "PARA", "nombre": 1 } ] }
      """
    Alors la réponse a le statut 201
    Et je retiens "places.0.id" sous le nom "julieMer"
    Quand admin envoie POST /api/effectifs/planification/places/{{julieMar}}/attribuer avec :
      """json
      { "ressourceId": "{{P1}}" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/places/{{julieMer}}/attribuer avec :
      """json
      { "ressourceId": "{{P1}}" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/places/{{marcMar}}/attribuer avec :
      """json
      { "ressourceId": "{{P2}}" }
      """
    Alors la réponse a le statut 200
    Quand admin envoie POST /api/effectifs/planification/periodes/{{s10}}/etat avec :
      """json
      { "etat": "PUBLIEE" }
      """
    Alors la réponse a le statut 200
    Quand julie envoie POST /api/effectifs/temps/feuilles/preparer avec :
      """json
      { "uniteCode": "EST", "debut": "2030-03-04", "fin": "2030-03-10" }
      """
    Alors la réponse a le statut 403
    Quand admin envoie POST /api/effectifs/temps/feuilles/preparer avec :
      """json
      { "uniteCode": "EST", "debut": "2030-03-04", "fin": "2030-03-10" }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "existantes": [] }
      """
    Quand julie envoie GET /api/effectifs/temps/moi
    Alors la réponse a le statut 200
    Et la réponse compte 1 élément
    Et je retiens "0.id" sous le nom "fJulie"
    Quand marc envoie GET /api/effectifs/temps/moi
    Alors la réponse a le statut 200
    Et je retiens "0.id" sous le nom "fMarc"

  @E-RF-1 @E-RF-5 @E-RF-6 @E-RF-9 @E-RF-10 @E-RF-11 @E-GP-8
  Scénario: La feuille se prépare depuis l'horaire : heures par type, heures supplémentaires, écart planifié-déclaré et anomalies affichées
    # Préparer à nouveau ne double rien.
    Quand admin envoie POST /api/effectifs/temps/feuilles/preparer avec :
      """json
      { "uniteCode": "EST", "debut": "2030-03-04", "fin": "2030-03-10" }
      """
    Alors la réponse a le statut 201
    Et la réponse contient :
      """json
      { "creees": [] }
      """
    Quand julie envoie GET /api/effectifs/temps/feuilles/{{fJulie}}
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "nom": "Julie", "matricule": "P1", "statut": "BROUILLON",
        "segments": [ { "date": "2030-03-05", "typeHeureCode": "REG", "source": "GENERE" }, { "date": "2030-03-06", "typeHeureCode": "REG", "source": "GENERE" } ],
        "totaux": { "REG": 1440 },
        "heuresSupplementaires": { "parJour": { "2030-03-05": 240, "2030-03-06": 240 }, "total": 480 },
        "ecarts": [ { "planifie": 720, "declare": 720, "ecart": 0 }, { "planifie": 720, "declare": 720, "ecart": 0 } ] }
      """
    Et la réponse correspond à "\"code\":\"REGLE\""
    Et la réponse correspond à "travail un jour férié : majoration à vérifier"
    Et la réponse correspond à "\"code\":\"SANS_POINTAGE\""
    # La feuille d'autrui ne se lit pas.
    Quand marc envoie GET /api/effectifs/temps/feuilles/{{fJulie}}
    Alors la réponse a le statut 404

  @E-RF-2 @E-RF-3 @E-RF-14 @E-GP-5 @E-GP-3
  Scénario: La saisie est jugée en temps réel ; une correction crée une version motivée ; les règles restreignent qui modifie quoi
    Quand julie envoie POST /api/effectifs/temps/feuilles/{{fJulie}}/segments avec :
      """json
      { "date": "2030-03-07", "debut": "2030-03-07T13:00:00Z", "fin": "2030-03-07T17:00:00Z", "typeHeureCode": "REG" }
      """
    Alors la réponse a le statut 201
    Et la réponse correspond à "\"date\":\"2030-03-07\""
    # Plus de 16 heures le même jour : l'anomalie bloque la saisie, motif à l'appui.
    Quand julie envoie POST /api/effectifs/temps/feuilles/{{fJulie}}/segments avec :
      """json
      { "date": "2030-03-07", "debut": "2030-03-07T17:00:00Z", "fin": "2030-03-08T06:00:00Z", "typeHeureCode": "REG" }
      """
    Alors la réponse a le statut 409
    Et la réponse contient :
      """json
      { "verdict": { "action": "BLOQUER" }, "errors": ["plus de 16 h déclarées le même jour (17 h)"] }
      """
    Quand julie envoie POST /api/effectifs/temps/feuilles/{{fJulie}}/segments avec :
      """json
      { "date": "2030-03-11", "debut": "2030-03-11T13:00:00Z", "fin": "2030-03-11T14:00:00Z", "typeHeureCode": "REG" }
      """
    Alors la réponse a le statut 422
    Et la réponse contient :
      """json
      { "errors": ["le 2030-03-11 est hors de la période de la feuille (2030-03-04 au 2030-03-10)"] }
      """
    Quand julie envoie POST /api/effectifs/temps/feuilles/{{fJulie}}/segments avec :
      """json
      { "date": "2030-03-07", "debut": "2030-03-07T18:00:00Z", "fin": "2030-03-07T19:00:00Z", "typeHeureCode": "XX" }
      """
    Alors la réponse a le statut 422
    Et la réponse contient :
      """json
      { "errors": ["type d'heure « XX » inconnu ou hors vigueur"] }
      """
    # Corriger le temps d'autrui se motive ; la correction est une nouvelle version, l'ancienne demeure.
    Quand julie envoie GET /api/effectifs/temps/feuilles/{{fJulie}}
    Alors la réponse a le statut 200
    Et je retiens "segments.0.id" sous le nom "mardi"
    Quand chef envoie PUT /api/effectifs/temps/feuilles/{{fJulie}}/segments/{{mardi}} avec :
      """json
      { "fin": "2030-03-05T22:00:00Z" }
      """
    Alors la réponse a le statut 422
    Et la réponse contient :
      """json
      { "errors": ["« motif » : une correction du temps d'autrui ou d'un temps pointé se motive"] }
      """
    Quand chef envoie PUT /api/effectifs/temps/feuilles/{{fJulie}}/segments/{{mardi}} avec :
      """json
      { "fin": "2030-03-05T22:00:00Z", "motif": "Départ anticipé, autorisé par le répartiteur" }
      """
    Alors la réponse a le statut 200
    Et la réponse correspond à "\"remplaceId\":\"{{mardi}}\""
    Et la réponse correspond à "\"version\":2"
    Quand chef envoie PUT /api/effectifs/temps/feuilles/{{fJulie}}/segments/{{mardi}} avec :
      """json
      { "fin": "2030-03-05T21:00:00Z", "motif": "encore" }
      """
    Alors la réponse a le statut 409
    Et la réponse contient :
      """json
      { "errors": ["ce segment a déjà été remplacé : corrigez sa dernière version"] }
      """
    # L'historique de la première version la garde, désactivée : rien ne s'efface.
    Quand julie envoie GET /api/effectifs/temps/feuilles/{{fJulie}}/segments/{{mardi}}/historique
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      [ { "id": "{{mardi}}", "version": 1, "actif": 0, "fin": "2030-03-05T23:00:00.000Z" } ]
      """
    # E-GP-3 : seul le niveau GESTION retire un segment, quoi qu'en dise la propriété de la feuille.
    Quand admin envoie POST /api/effectifs/regles avec :
      """json
      { "contexte": "MODIFICATION_HEURES", "cle": "retrait-gestion",
        "condition": { "tous": [ { "fait": "operation", "op": "=", "valeur": "RETRAIT" },
                                 { "fait": "niveauActeur", "op": "hors", "valeur": ["GESTION", "ETABLISSEMENT"] } ] },
        "action": "BLOQUER", "message": "seul le niveau GESTION retire un segment" }
      """
    Alors la réponse a le statut 201
    Quand julie envoie GET /api/effectifs/temps/feuilles/{{fJulie}}
    Alors la réponse a le statut 200
    Et je retiens "segments.2.id" sous le nom "jeudi"
    Quand julie envoie DELETE /api/effectifs/temps/feuilles/{{fJulie}}/segments/{{jeudi}}
    Alors la réponse a le statut 409
    Et la réponse contient :
      """json
      { "errors": ["modification des heures refusée : seul le niveau GESTION retire un segment"] }
      """
    Quand chef envoie DELETE /api/effectifs/temps/feuilles/{{fJulie}}/segments/{{jeudi}} avec :
      """json
      { "motif": "Saisi par erreur" }
      """
    Alors la réponse a le statut 200
    Et la réponse ne correspond pas à "\"id\":\"{{jeudi}}\""

  @E-RF-4 @E-GP-6 @E-RF-12
  Scénario: La feuille soumise se verrouille et s'approuve par paliers, deux personnes ; approuvée, elle part à la paie dans un lot structuré
    Quand chef envoie PUT /api/effectifs/temps/parametres avec :
      """json
      { "paliersApprobation": 2 }
      """
    Alors la réponse a le statut 403
    Quand admin envoie PUT /api/effectifs/temps/parametres avec :
      """json
      { "paliersApprobation": 2 }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "paliersApprobation": 2, "seuilJourHeures": 8 }
      """
    Quand julie envoie POST /api/effectifs/temps/feuilles/{{fJulie}}/soumettre
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "statut": "SOUMISE", "palier": 1, "paliersRequis": 2 }
      """
    Quand julie envoie POST /api/effectifs/temps/feuilles/{{fJulie}}/segments avec :
      """json
      { "date": "2030-03-07", "debut": "2030-03-07T13:00:00Z", "fin": "2030-03-07T14:00:00Z", "typeHeureCode": "REG" }
      """
    Alors la réponse correspond à "feuille soumise : elle attend une décision ; rappelez-la ou faites-la retourner pour la corriger"
    Quand julie envoie POST /api/effectifs/temps/feuilles/{{fJulie}}/approuver
    Alors la réponse a le statut 403
    Quand chef envoie POST /api/effectifs/temps/feuilles/{{fJulie}}/approuver
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "statut": "SOUMISE", "palier": 2 }
      """
    Quand chef envoie POST /api/effectifs/temps/feuilles/{{fJulie}}/approuver
    Alors la réponse a le statut 409
    Et la réponse contient :
      """json
      { "errors": ["vous avez déjà approuvé un palier de cette feuille : le palier suivant revient à une autre personne"] }
      """
    Quand admin envoie POST /api/effectifs/perimetres avec :
      """json
      { "utilisateurId": "{{u_directeur}}", "uniteCode": "EST", "etendue": "UNITE", "niveau": "GESTION", "depuis": "2026-01-01" }
      """
    Alors la réponse a le statut 201
    Quand directeur envoie POST /api/effectifs/temps/feuilles/{{fJulie}}/approuver
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "statut": "APPROUVEE", "decisions": [ { "palier": 1, "decision": "APPROUVER" }, { "palier": 2, "decision": "APPROUVER" } ] }
      """
    # Une feuille retournée l'est avec un motif.
    Quand marc envoie POST /api/effectifs/temps/feuilles/{{fMarc}}/soumettre
    Alors la réponse a le statut 200
    Quand chef envoie POST /api/effectifs/temps/feuilles/{{fMarc}}/retourner
    Alors la réponse contient :
      """json
      { "errors": ["« motif » : dites ce qu'il faut corriger"] }
      """
    Quand chef envoie POST /api/effectifs/temps/feuilles/{{fMarc}}/retourner avec :
      """json
      { "motif": "Ajoutez la formation du jeudi" }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "statut": "RETOURNEE", "palier": 0 }
      """
    # Le lot de paie : seules les feuilles approuvées, par code de paie, heures supplémentaires reclassées, empreinte.
    Quand julie envoie GET /api/effectifs/temps/export-paie?debut=2030-03-04
    Alors la réponse a le statut 403
    Quand chef envoie GET /api/effectifs/temps/export-paie?debut=2030-03-04
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "debut": "2030-03-04", "feuilles": ["{{fJulie}}"],
        "lignes": [ { "matricule": "P1", "date": "2030-03-05", "codePaie": "100", "heures": 8 },
                    { "matricule": "P1", "date": "2030-03-05", "codePaie": "150", "heures": 4 },
                    { "matricule": "P1", "date": "2030-03-06", "codePaie": "100", "heures": 8 },
                    { "matricule": "P1", "date": "2030-03-06", "codePaie": "150", "heures": 4 } ] }
      """
    Et la réponse correspond à "\"empreinte\":\"[0-9a-f]{64}\""

  @E-GP-6
  Scénario: Le flux d'approbation se configure : un palier de plus quand les heures supplémentaires dépassent un seuil
    Quand chef envoie POST /api/effectifs/temps/flux avec :
      """json
      { "objet": "FEUILLE", "palier": 1, "champ": "toujours", "operateur": "eq", "valeur": 1, "approbateur": { "type": "PERIMETRE", "valeur": "GESTION" } }
      """
    Alors la réponse a le statut 403
    Quand admin envoie POST /api/effectifs/temps/flux avec :
      """json
      { "objet": "FEUILLE", "palier": 1, "champ": "couleur", "operateur": "eq", "valeur": 1, "approbateur": { "type": "PERIMETRE", "valeur": "GESTION" } }
      """
    Alors la réponse a le statut 422
    Quand admin envoie POST /api/effectifs/temps/flux avec :
      """json
      { "objet": "FEUILLE", "palier": 1, "champ": "toujours", "operateur": "eq", "valeur": 1, "approbateur": { "type": "PERIMETRE", "valeur": "GESTION" } }
      """
    Alors la réponse a le statut 201
    Quand admin envoie POST /api/effectifs/temps/flux avec :
      """json
      { "objet": "FEUILLE", "palier": 2, "champ": "heuresSupplementaires", "operateur": "gte", "valeur": 5, "approbateur": { "type": "ROLE", "valeur": "director" } }
      """
    Alors la réponse a le statut 201
    # Julie compte 8 h supplémentaires : deux paliers ; Marc, 4 h : un seul.
    Quand julie envoie POST /api/effectifs/temps/feuilles/{{fJulie}}/soumettre
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "paliersRequis": 2 }
      """
    Quand marc envoie POST /api/effectifs/temps/feuilles/{{fMarc}}/soumettre
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "paliersRequis": 1 }
      """
    Quand chef envoie POST /api/effectifs/temps/feuilles/{{fMarc}}/approuver
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "statut": "APPROUVEE" }
      """

  @E-RF-7 @P-RF-1 @E-RF-8 @P-TN-8
  Scénario: Les relevés se regroupent selon le paramètre choisi et s'exportent dans les formats standards
    Quand julie envoie GET /api/effectifs/temps/rapports?debut=2030-03-04
    Alors la réponse a le statut 403
    Quand chef envoie GET /api/effectifs/temps/rapports?debut=2030-03-04&regroupement=unite
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      [ { "groupe": "EST", "feuilles": 2, "planifie": 2160, "declare": 2160 } ]
      """
    Quand chef envoie GET /api/effectifs/temps/rapports?debut=2030-03-04&regroupement=ressource
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      [ { "groupe": "P1 — Julie", "declare": 1440 }, { "groupe": "P2 — Marc", "declare": 720 } ]
      """
    Quand chef envoie GET /api/effectifs/temps/rapports?debut=2030-03-04&regroupement=typeHeure
    Alors la réponse a le statut 200
    Et la réponse correspond à "\"groupe\":\"REG\""
    Quand chef envoie GET /api/effectifs/temps/rapports?debut=2030-03-04&regroupement=semaine
    Alors la réponse a le statut 200
    Et la réponse correspond à "\"groupe\":\"2030-03-04\""
    Quand chef envoie GET /api/effectifs/temps/rapports?debut=2030-03-04&regroupement=mois
    Alors la réponse a le statut 422
    Quand chef envoie GET /api/effectifs/temps/rapports?debut=2030-03-04&regroupement=unite&format=csv
    Alors la réponse a le statut 200
    Et la réponse correspond à "^Groupe,Feuilles,Planifié \(h\),Déclaré \(h\),Supplémentaires \(h\),Anomalies$"
    Et la réponse correspond à "^EST,2,36.0,36.0,"
    Quand chef envoie GET /api/effectifs/temps/rapports?debut=2030-03-04&regroupement=unite&format=xlsx
    Alors la réponse a le statut 200
    Et la réponse correspond à "^PK"
    Quand chef envoie GET /api/effectifs/temps/rapports?debut=2030-03-04&regroupement=unite&format=pdf
    Alors la réponse a le statut 200
    Et la réponse correspond à "^%PDF-"
    Quand chef envoie GET /api/effectifs/temps/rapports?debut=2030-03-04&regroupement=unite&format=docx
    Alors la réponse a le statut 422
    Et la réponse contient :
      """json
      { "errors": ["format inconnu : « docx » (attendu : json, csv, xlsx, pdf)"] }
      """

  @P-RF-1 @E-RF-7
  Scénario: L'organisme définit son propre relevé statistique — source, mesures, regroupement, période — l'enregistre et le reproduit
    Quand chef envoie GET /api/rapports/sources
    Alors la réponse a le statut 200
    Et la réponse correspond à "\"effectifs_places\""
    Quand chef envoie POST /api/rapports/executer avec :
      """json
      { "source": "effectifs_places",
        "colonnes": [ { "champ": "jour" }, { "champ": "places", "agregat": "somme" }, { "champ": "pourvues", "agregat": "somme" } ],
        "groupes": ["jour"], "filtres": [ { "champ": "jour", "operateur": "entre", "valeurs": ["2030-03-01", "2030-03-31"] } ],
        "tri": { "champ": "jour" } }
      """
    Alors la réponse a le statut 200
    Et la réponse contient :
      """json
      { "lignes": [ { "jour": "2030-03-05", "places": 2, "pourvues": 2 }, { "jour": "2030-03-06", "places": 1, "pourvues": 1 } ], "tronque": false }
      """
    # Un champ inconnu de la source est nommé.
    Quand chef envoie POST /api/rapports/valider avec :
      """json
      { "source": "effectifs_places", "colonnes": [ { "champ": "couleur" } ] }
      """
    Alors la réponse a le statut 400
    Et la réponse correspond à "couleur"
    Quand julie envoie POST /api/rapports/enregistres avec :
      """json
      { "nom": "Comblement de mars", "definition": { "source": "effectifs_places", "colonnes": [ { "champ": "jour" } ] } }
      """
    Alors la réponse a le statut 403
    Quand chef envoie POST /api/rapports/enregistres avec :
      """json
      { "nom": "Comblement de mars", "description": "Places et places pourvues par jour",
        "definition": { "source": "effectifs_places",
          "colonnes": [ { "champ": "jour" }, { "champ": "places", "agregat": "somme" }, { "champ": "pourvues", "agregat": "somme" } ],
          "groupes": ["jour"], "filtres": [ { "champ": "jour", "operateur": "entre", "valeurs": ["2030-03-01", "2030-03-31"] } ] } }
      """
    Alors la réponse a le statut 201
    Et je retiens "id" sous le nom "releve"
    Quand chef envoie POST /api/rapports/enregistres/{{releve}}/executer
    Alors la réponse a le statut 200
    Et la réponse correspond à "\"jour\":\"2030-03-05\""
    Quand chef envoie POST /api/rapports/enregistres/{{releve}}/exporter avec :
      """json
      { "format": "csv" }
      """
    Alors la réponse a le statut 200
    Et la réponse correspond à "Jour"
