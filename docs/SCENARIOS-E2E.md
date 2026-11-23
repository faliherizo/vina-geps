# Scénarios de bout en bout, exigence par exigence

> Généré par `node scripts/construire-scenarios-e2e.cjs` — ne pas modifier à la main. Écrit pour l'équipe qui
> démontre et l'évaluateur qui vérifie : chaque scénario ci-dessous est exécuté contre le serveur Java par
> `backend-java/scripts/campagne-e2e.sh`, et ses données JSON sont celles que l'essai lit.

## Comment les jouer

```bash
# Écrans, comportements par l'API, parcours — contre le Java, base vierge Liquibase
E2E_API_PORT=4031 E2E_WEB_PORT=5331 backend-java/scripts/campagne-e2e.sh \
  tests/e2e/urgences-sante-exigences tests/e2e/urgences-sante-comportements tests/e2e/urgences-sante-technologie \
  tests/e2e/fx-ao-exigences tests/e2e/fx-ao-comportements tests/e2e/fx-ao-parcours
# BDD des exigences 26-015-DO
(cd backend-java && mvn -o test -Dtest=ExigencesBddTest -Djacoco.skip=true)
```

Trois formes de données JSON :
- **Écran** (`page`, `prealable`, `preuve`) : la page ouverte, les gestes qui mènent à l'état utile, les commandes
  (`data-testid`) qui doivent y être ; `a|b` accepte l'une ou l'autre, `prefixe:x` un identifiant qui commence par `x`.
- **API** (`etapes`) : l'acteur, la méthode, le chemin, le corps, l'attendu (`statut`, `corps` en sous-ensemble,
  `texte` en expression régulière) et les captures réutilisées par `{{nom}}` dans les étapes suivantes.
- **BDD** : les scénarios Gherkin étiquetés par l'exigence, contre le serveur démarré dans le processus.

## Bilan

| Avis | Exigences | Scénario d'écran | Scénario par l'API | BDD | Au moins un e2e | Sans e2e |
|---|---|---|---|---|---|---|
| Urgences-santé 26-015-DO | 144 | 90 | 38 | 101 | 102 | 42 |
| FX-AO-20260810 | 115 | 32 | 43 | 0 | 44 | 71 |

« Sans e2e » : exigences technologiques, engagements contractuels ou responsabilités de l'établissement — chacune
dit, à sa ligne, par quoi elle est prouvée ou pourquoi aucun comportement logiciel n'est à jouer.

## 1. Urgences-santé — AO 26-015-DO (144 exigences)

### E-GN-1 — La solution doit permettre de configurer des quarts de travail nécessitant une ou plusieurs ressources selon le corps d’emploi ou le contexte.

*essentiel · Généralités · verdict : ACQUIS · lot U2*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usequipages` — Choisir la période, « Quart ad hoc », puis « Ajouter un corps d'emploi » : un quart reçoit une ou plusieurs places, chacune avec son corps d'emploi. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usequipages",
  "prealable": [
    {
      "cliquer": "pla-eq-adhoc",
      "pourquoi": "La composition se définit dans le tiroir de création ; la liste seule ne la montre pas."
    }
  ],
  "preuve": [
    "pla-adhoc",
    "pla-ah-ajouter-corps",
    "pla-ah-nature"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 01-planification.feature — Un quart réunit plusieurs places de corps d'emploi choisis, rattaché à un milieu physique »

**Essais Java** : `PlanificationDomaineTest.java`

### E-GN-2 — La solution doit permettre de définir le nombre minimal de ressources requises pour qu’un quart de travail soit considéré opérationnel.

*essentiel · Généralités · verdict : ACQUIS · lot U2*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usequipages` — Dans le tiroir du quart ad hoc, renseigner « Minimum opérationnel » : sous ce nombre, le comblement classe le quart en sous-minimum. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usequipages",
  "prealable": [
    {
      "cliquer": "pla-eq-adhoc",
      "pourquoi": "Le minimum est un champ de la création du quart."
    }
  ],
  "preuve": [
    "pla-adhoc",
    "pla-ah-minimum"
  ]
}
```

</details>

**Scénario par l'API C-01** (`urgences-sante-comportements.spec.ts`) — Un quart ad hoc se crée hors de tout poste ; hors période, sans composition, à minimum impossible ou sur une plage inconnue, il est refusé

<details><summary>Données JSON de C-01 (6 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes",
    "corps": {
      "nom": "PLAN",
      "uniteCode": "EST",
      "debut": "2030-03-18",
      "fin": "2030-03-24"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "plan": "id"
    }
  },
  {
    "pourquoi": "Hors de la période : un quart n'existe que dans une période",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-04-01",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 2
        }
      ]
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "pourquoi": "Sans composition : un quart sans place n'est pas un quart",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "composition": []
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "pourquoi": "Un minimum opérationnel de 3 sur 2 places ne serait jamais atteint",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "minimumOperationnel": 3,
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 2
        }
      ]
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "plageCode": "ZZ",
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 2
        }
      ]
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "pourquoi": "Le mode d'attribution se choisit par quart ; le minimum se déclare",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "modeAttribution": "AUTOMATISE",
      "minimumOperationnel": 1,
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 2
        }
      ]
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "modeAttribution": "AUTOMATISE",
        "minimumOperationnel": 1
      }
    },
    "capturer": {
      "adhoc": "id",
      "placeAdhoc": "places.0.id"
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 02-quarts-et-attribution.feature — Un quart ad hoc se crée hors de tout poste ; hors période, sans composition, à minimum impossible ou sur une plage inconnue, il est refusé »

**Essais Java** : `PlanificationDomaineTest.java`, `comportements.json`

### E-GN-3 — La solution doit permettre la création de quarts de travail ponctuels ou ad hoc à des fins opérationnelles particulières.

*essentiel · Généralités · verdict : ACQUIS · lot U2*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usequipages` — « Quart ad hoc » : date, plage et milieu libres, hors cycle de poste. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usequipages",
  "prealable": [
    {
      "cliquer": "pla-eq-adhoc",
      "pourquoi": "Le quart ponctuel se crée dans ce tiroir."
    }
  ],
  "preuve": [
    "pla-adhoc",
    "pla-ah-date",
    "pla-ah-plage",
    "pla-ah-milieu"
  ]
}
```

</details>

**Scénario par l'API C-01** (`urgences-sante-comportements.spec.ts`) — Un quart ad hoc se crée hors de tout poste ; hors période, sans composition, à minimum impossible ou sur une plage inconnue, il est refusé

<details><summary>Données JSON de C-01 (6 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes",
    "corps": {
      "nom": "PLAN",
      "uniteCode": "EST",
      "debut": "2030-03-18",
      "fin": "2030-03-24"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "plan": "id"
    }
  },
  {
    "pourquoi": "Hors de la période : un quart n'existe que dans une période",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-04-01",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 2
        }
      ]
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "pourquoi": "Sans composition : un quart sans place n'est pas un quart",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "composition": []
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "pourquoi": "Un minimum opérationnel de 3 sur 2 places ne serait jamais atteint",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "minimumOperationnel": 3,
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 2
        }
      ]
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "plageCode": "ZZ",
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 2
        }
      ]
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "pourquoi": "Le mode d'attribution se choisit par quart ; le minimum se déclare",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "modeAttribution": "AUTOMATISE",
      "minimumOperationnel": 1,
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 2
        }
      ]
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "modeAttribution": "AUTOMATISE",
        "minimumOperationnel": 1
      }
    },
    "capturer": {
      "adhoc": "id",
      "placeAdhoc": "places.0.id"
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 02-quarts-et-attribution.feature — Un quart ad hoc se crée hors de tout poste ; hors période, sans composition, à minimum impossible ou sur une plage inconnue, il est refusé »

**Essais Java** : `ControleurPlanificationEffectifsHttpTest.java`, `comportements.json`

### E-GN-4 — La solution doit permettre de choisir, pour un quart de travail, un mode d’attribution automatisé ou manuel par un utilisateur autorisé.

*essentiel · Généralités · verdict : ACQUIS · lot U2*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usequipages` — Champ « Mode d'attribution » du quart : automatisé ou manuel ; « Modifier en lot » le change pour plusieurs quarts. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usequipages",
  "prealable": [
    {
      "cliquer": "pla-eq-adhoc",
      "pourquoi": "Le mode est un champ du quart."
    }
  ],
  "preuve": [
    "pla-adhoc",
    "pla-ah-mode"
  ]
}
```

</details>

**Scénario par l'API C-01** (`urgences-sante-comportements.spec.ts`) — Un quart ad hoc se crée hors de tout poste ; hors période, sans composition, à minimum impossible ou sur une plage inconnue, il est refusé

<details><summary>Données JSON de C-01 (6 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes",
    "corps": {
      "nom": "PLAN",
      "uniteCode": "EST",
      "debut": "2030-03-18",
      "fin": "2030-03-24"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "plan": "id"
    }
  },
  {
    "pourquoi": "Hors de la période : un quart n'existe que dans une période",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-04-01",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 2
        }
      ]
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "pourquoi": "Sans composition : un quart sans place n'est pas un quart",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "composition": []
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "pourquoi": "Un minimum opérationnel de 3 sur 2 places ne serait jamais atteint",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "minimumOperationnel": 3,
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 2
        }
      ]
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "plageCode": "ZZ",
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 2
        }
      ]
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "pourquoi": "Le mode d'attribution se choisit par quart ; le minimum se déclare",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "modeAttribution": "AUTOMATISE",
      "minimumOperationnel": 1,
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 2
        }
      ]
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "modeAttribution": "AUTOMATISE",
        "minimumOperationnel": 1
      }
    },
    "capturer": {
      "adhoc": "id",
      "placeAdhoc": "places.0.id"
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 02-quarts-et-attribution.feature — Un quart ad hoc se crée hors de tout poste ; hors période, sans composition, à minimum impossible ou sur une plage inconnue, il est refusé »

**Essais Java** : `PlanificationDomaineTest.java`, `comportements.json`

### E-GN-5 — La solution doit permettre l’autorisation manuelle d’un congé ayant été refusé par une automatisation avec attribution du code d’absence applicable.

*essentiel · Généralités · verdict : ACQUIS · lot U5*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usabsences` — Ouvrir une absence refusée par l'automatisation, puis « Déroger » : un motif et un code d'absence sont exigés. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usabsences",
  "prealable": [],
  "preuve": [
    "pla-abs",
    "pla-abs-filtre",
    "pla-abs-table"
  ]
}
```

</details>

**Scénario par l'API C-07** (`urgences-sante-comportements.spec.ts`) — La dérogation exige un droit, un motif et un code ; elle accorde l'absence et rend les places vacantes par un mécanisme journalisé

<details><summary>Données JSON de C-07 (4 étapes)</summary>

```json
[
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/effectifs/absences/{{refusee}}/decisions",
    "corps": {
      "decision": "DEROGER",
      "motif": "Hospitalisation confirmée par billet médical",
      "codeAbsence": "MAL"
    },
    "attendu": {
      "statut": 403
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/effectifs/absences/{{refusee}}/decisions",
    "corps": {
      "decision": "DEROGER",
      "motif": "court",
      "codeAbsence": "MAL"
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/effectifs/absences/{{refusee}}/decisions",
    "corps": {
      "decision": "DEROGER",
      "motif": "Hospitalisation confirmée par billet médical",
      "codeAbsence": "MAL"
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "statut": "ACCEPTEE",
        "derogationCode": "MAL",
        "placesLiberees": 2,
        "decideePar": "{{u_chef}}"
      }
    }
  },
  {
    "methode": "GET",
    "chemin": "/api/effectifs/journal?action=places.vacantes",
    "attendu": {
      "statut": 200,
      "texte": "places\\.vacantes"
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 04-absences.feature — La dérogation exige un droit, un motif et un code ; elle accorde l'absence et rend les places vacantes par un mécanisme journalisé »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-absences.spec.ts`

**Essais Java** : `AbsencesEffectifsDomaineTest.java`, `ControleurAbsencesEffectifsHttpTest.java`, `comportements.json`, `effectifs-absences.spec.ts`

### E-GN-6 — La solution doit appliquer automatiquement les règles de comblement à la suite d’une autorisation manuelle.

*essentiel · Généralités · verdict : ACQUIS · lot U5*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usabsences` — Après la dérogation, l'onglet montre le comblement des places libérées : qui a été retenu, qui a été avisé. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usabsences",
  "prealable": [],
  "preuve": [
    "pla-abs",
    "pla-abs-filtre"
  ]
}
```

</details>

**Scénario par l'API C-06** (`urgences-sante-comportements.spec.ts`) — Une absence qui ferait tomber le comblement est refusée d'office, et le refus est conservé avec son motif

<details><summary>Données JSON de C-06 (9 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes",
    "corps": {
      "nom": "ABS",
      "uniteCode": "EST",
      "debut": "2030-03-04",
      "fin": "2030-03-10"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "abs": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes/{{abs}}/etat",
    "corps": {
      "etat": "CONFECTION"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
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
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "placeLundi": "places.0.id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/places/{{placeLundi}}/attribuer",
    "corps": {
      "ressourceId": "{{P1}}"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
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
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "placeMardi": "places.0.id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/places/{{placeMardi}}/attribuer",
    "corps": {
      "ressourceId": "{{P1}}"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/regles",
    "corps": {
      "contexte": "ABSENCE",
      "cle": "comblement-minimal",
      "condition": {
        "fait": "tauxComblementApres",
        "op": "<",
        "valeur": 60
      },
      "action": "REFUSER",
      "message": "Le comblement tomberait à {tauxComblementApres} %",
      "effectifDepuis": "2027-01-01"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/effectifs/absences",
    "corps": {
      "motif": "MAL",
      "du": "2030-03-04",
      "au": "2030-03-05",
      "commentaire": "Grippe"
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "statut": "REFUSEE_AUTO",
        "placesLiberees": 0
      },
      "texte": "Le comblement tomberait à 0 %"
    },
    "capturer": {
      "refusee": "ids.0"
    }
  },
  {
    "pourquoi": "L'impact se lit avant de décider : le taux avant et après",
    "methode": "GET",
    "chemin": "/api/effectifs/absences/{{refusee}}/impact",
    "attendu": {
      "statut": 200,
      "corps": {
        "tauxAvant": 100,
        "tauxApres": 0
      }
    }
  }
]
```

</details>

**Scénario par l'API C-07** (`urgences-sante-comportements.spec.ts`) — La dérogation exige un droit, un motif et un code ; elle accorde l'absence et rend les places vacantes par un mécanisme journalisé

<details><summary>Données JSON de C-07 (4 étapes)</summary>

```json
[
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/effectifs/absences/{{refusee}}/decisions",
    "corps": {
      "decision": "DEROGER",
      "motif": "Hospitalisation confirmée par billet médical",
      "codeAbsence": "MAL"
    },
    "attendu": {
      "statut": 403
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/effectifs/absences/{{refusee}}/decisions",
    "corps": {
      "decision": "DEROGER",
      "motif": "court",
      "codeAbsence": "MAL"
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/effectifs/absences/{{refusee}}/decisions",
    "corps": {
      "decision": "DEROGER",
      "motif": "Hospitalisation confirmée par billet médical",
      "codeAbsence": "MAL"
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "statut": "ACCEPTEE",
        "derogationCode": "MAL",
        "placesLiberees": 2,
        "decideePar": "{{u_chef}}"
      }
    }
  },
  {
    "methode": "GET",
    "chemin": "/api/effectifs/journal?action=places.vacantes",
    "attendu": {
      "statut": 200,
      "texte": "places\\.vacantes"
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 04-absences.feature — Une absence qui ferait tomber le comblement est refusée d'office, et le refus est conservé avec son motif » ; « 04-absences.feature — La dérogation exige un droit, un motif et un code ; elle accorde l'absence et rend les places vacantes par un mécanisme journalisé »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-absences.spec.ts`

**Essais Java** : `ControleurAbsencesEffectifsHttpTest.java`, `comportements.json`, `effectifs-absences.spec.ts`

### E-GN-7 — La solution doit permettre la segmentation complète de la planification et des règles par groupes organisationnels distincts.

*essentiel · Généralités · verdict : ACQUIS · lot U1*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usreferentiels` — Choisir le type « Unités » : chaque unité est un groupe organisationnel, et postes, règles et quotas s'y rattachent. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usreferentiels",
  "prealable": [],
  "preuve": [
    "ref-ecran",
    "ref-type",
    "ref-tableau"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 01-planification.feature — Chaque unité est un groupe organisationnel distinct qui porte ses propres périodes » ; « 03-disponibilites.feature — Une règle de conformité vise une unité ou un statut d'emploi : elle ne s'applique qu'à ceux qu'elle vise »

**Essais Java** : `SocleEffectifsDomaineTest.java`

### E-GN-8 — La solution doit permettre de restreindre l’accès et les opérations d’un utilisateur à des groupes organisationnels précis.

*essentiel · Généralités · verdict : ACQUIS · lot U1*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usperimetres` — Choisir un compte, puis lui donner une unité, un niveau et une étendue : hors de son périmètre, le serveur refuse. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usperimetres",
  "prealable": [
    {
      "choisir": "per-utilisateur",
      "pourquoi": "Le formulaire d'accès n'apparaît qu'une fois le compte choisi : sans lui, on ne saurait pas à qui on donne l'accès."
    },
    {
      "cliquer": "per-ajout-ouvrir",
      "pourquoi": "Le formulaire vit dans un dialogue depuis le 8 novembre 2026 : ses champs n'existent qu'ouvert."
    }
  ],
  "preuve": [
    "per-ecran",
    "per-utilisateur",
    "per-ajout-ouvrir",
    "per-unite",
    "per-niveau",
    "per-ajouter"
  ]
}
```

</details>

**Scénario par l'API C-02** (`urgences-sante-comportements.spec.ts`) — Une règle de repos bloque l'attribution en nommant son motif ; forcer exige le niveau GESTION et une justification, et le forçage est tracé

<details><summary>Données JSON de C-02 (10 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/effectifs/regles",
    "corps": {
      "contexte": "ATTRIBUTION",
      "cle": "repos-minimal",
      "condition": {
        "fait": "reposHeures",
        "op": "<",
        "valeur": 8
      },
      "action": "BLOQUER",
      "message": "Repos de {reposHeures} h : il en faut 8",
      "effectifDepuis": "2027-01-01"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "pourquoi": "Marc tient le quart de jour, qui finit à 18 h",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 360,
      "fin": 1080,
      "natureCode": "REG",
      "milieuId": "{{c12}}",
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 1
        }
      ]
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "placeJour": "places.0.id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/places/{{placeJour}}/attribuer",
    "corps": {
      "ressourceId": "{{P2}}"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 1080,
      "fin": 1320,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 1
        }
      ],
      "note": "Renfort de soirée"
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "modeAttribution": "MANUEL",
        "note": "Renfort de soirée"
      }
    },
    "capturer": {
      "placeSoir": "places.0.id"
    }
  },
  {
    "pourquoi": "Sans repos entre deux quarts, la règle refuse et dit pourquoi",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/places/{{placeSoir}}/attribuer",
    "corps": {
      "ressourceId": "{{P2}}"
    },
    "attendu": {
      "statut": 409,
      "texte": "Repos de 0 h : il en faut 8"
    }
  },
  {
    "pourquoi": "Un gestionnaire sans périmètre n'a aucun droit : refus par défaut",
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/places/{{placeSoir}}/attribuer",
    "corps": {
      "ressourceId": "{{P2}}",
      "forcer": true,
      "justification": "Aucun autre PSA disponible, appel massif"
    },
    "attendu": {
      "statut": 403
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/perimetres",
    "corps": {
      "utilisateurId": "{{u_chef}}",
      "uniteCode": "EST",
      "etendue": "UNITE",
      "niveau": "GESTION",
      "depuis": "2026-01-01"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "pourquoi": "Une justification de trois mots n'en est pas une",
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/places/{{placeSoir}}/attribuer",
    "corps": {
      "ressourceId": "{{P2}}",
      "forcer": true,
      "justification": "court"
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/places/{{placeSoir}}/attribuer",
    "corps": {
      "ressourceId": "{{P2}}",
      "forcer": true,
      "justification": "Aucun autre PSA disponible, appel massif"
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "force": true
      },
      "texte": "Aucun autre PSA disponible, appel massif"
    }
  },
  {
    "pourquoi": "La trace dit quelle règle a été écartée",
    "methode": "GET",
    "chemin": "/api/effectifs/journal?action=place.attribution.forcee",
    "attendu": {
      "statut": 200,
      "texte": "repos-minimal"
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 02-quarts-et-attribution.feature — Une règle de repos bloque l'attribution en nommant son motif ; forcer exige le niveau GESTION et une justification, et le forçage est tracé »

**Essais Java** : `SocleEffectifsDomaineTest.java`, `comportements.json`

### E-GN-9 — La solution doit consigner de manière horodatée toute interaction, action, modification ou décision ayant un impact sur les horaires, les quarts, les absences, les paramètres ou les données de temps, incluant l’identification de l’utilisateur ou du mécanisme automatisé à l’origine de l’action.

*essentiel · Généralités · verdict : ACQUIS · lot U1*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usjournal` — Le journal : chaque action horodatée avec son auteur ou le mécanisme qui l'a posée ; filtrable par période et par origine. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usjournal",
  "prealable": [],
  "preuve": [
    "jrn-ecran",
    "jrn-filtre-du",
    "jrn-filtre-origine",
    "jrn-filtrer"
  ]
}
```

</details>

**Scénario par l'API C-02** (`urgences-sante-comportements.spec.ts`) — Une règle de repos bloque l'attribution en nommant son motif ; forcer exige le niveau GESTION et une justification, et le forçage est tracé

<details><summary>Données JSON de C-02 (10 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/effectifs/regles",
    "corps": {
      "contexte": "ATTRIBUTION",
      "cle": "repos-minimal",
      "condition": {
        "fait": "reposHeures",
        "op": "<",
        "valeur": 8
      },
      "action": "BLOQUER",
      "message": "Repos de {reposHeures} h : il en faut 8",
      "effectifDepuis": "2027-01-01"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "pourquoi": "Marc tient le quart de jour, qui finit à 18 h",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 360,
      "fin": 1080,
      "natureCode": "REG",
      "milieuId": "{{c12}}",
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 1
        }
      ]
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "placeJour": "places.0.id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/places/{{placeJour}}/attribuer",
    "corps": {
      "ressourceId": "{{P2}}"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 1080,
      "fin": 1320,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 1
        }
      ],
      "note": "Renfort de soirée"
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "modeAttribution": "MANUEL",
        "note": "Renfort de soirée"
      }
    },
    "capturer": {
      "placeSoir": "places.0.id"
    }
  },
  {
    "pourquoi": "Sans repos entre deux quarts, la règle refuse et dit pourquoi",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/places/{{placeSoir}}/attribuer",
    "corps": {
      "ressourceId": "{{P2}}"
    },
    "attendu": {
      "statut": 409,
      "texte": "Repos de 0 h : il en faut 8"
    }
  },
  {
    "pourquoi": "Un gestionnaire sans périmètre n'a aucun droit : refus par défaut",
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/places/{{placeSoir}}/attribuer",
    "corps": {
      "ressourceId": "{{P2}}",
      "forcer": true,
      "justification": "Aucun autre PSA disponible, appel massif"
    },
    "attendu": {
      "statut": 403
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/perimetres",
    "corps": {
      "utilisateurId": "{{u_chef}}",
      "uniteCode": "EST",
      "etendue": "UNITE",
      "niveau": "GESTION",
      "depuis": "2026-01-01"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "pourquoi": "Une justification de trois mots n'en est pas une",
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/places/{{placeSoir}}/attribuer",
    "corps": {
      "ressourceId": "{{P2}}",
      "forcer": true,
      "justification": "court"
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/places/{{placeSoir}}/attribuer",
    "corps": {
      "ressourceId": "{{P2}}",
      "forcer": true,
      "justification": "Aucun autre PSA disponible, appel massif"
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "force": true
      },
      "texte": "Aucun autre PSA disponible, appel massif"
    }
  },
  {
    "pourquoi": "La trace dit quelle règle a été écartée",
    "methode": "GET",
    "chemin": "/api/effectifs/journal?action=place.attribution.forcee",
    "attendu": {
      "statut": 200,
      "texte": "repos-minimal"
    }
  }
]
```

</details>

**Scénario par l'API C-07** (`urgences-sante-comportements.spec.ts`) — La dérogation exige un droit, un motif et un code ; elle accorde l'absence et rend les places vacantes par un mécanisme journalisé

<details><summary>Données JSON de C-07 (4 étapes)</summary>

```json
[
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/effectifs/absences/{{refusee}}/decisions",
    "corps": {
      "decision": "DEROGER",
      "motif": "Hospitalisation confirmée par billet médical",
      "codeAbsence": "MAL"
    },
    "attendu": {
      "statut": 403
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/effectifs/absences/{{refusee}}/decisions",
    "corps": {
      "decision": "DEROGER",
      "motif": "court",
      "codeAbsence": "MAL"
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/effectifs/absences/{{refusee}}/decisions",
    "corps": {
      "decision": "DEROGER",
      "motif": "Hospitalisation confirmée par billet médical",
      "codeAbsence": "MAL"
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "statut": "ACCEPTEE",
        "derogationCode": "MAL",
        "placesLiberees": 2,
        "decideePar": "{{u_chef}}"
      }
    }
  },
  {
    "methode": "GET",
    "chemin": "/api/effectifs/journal?action=places.vacantes",
    "attendu": {
      "statut": 200,
      "texte": "places\\.vacantes"
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 02-quarts-et-attribution.feature — Une règle de repos bloque l'attribution en nommant son motif ; forcer exige le niveau GESTION et une justification, et le forçage est tracé » ; « 04-absences.feature — La dérogation exige un droit, un motif et un code ; elle accorde l'absence et rend les places vacantes par un mécanisme journalisé » ; « 09-journal.feature — Les gestes de paramétrage et d'horaire se retrouvent au journal, horodatés, avec leur auteur, et se filtrent a posteriori »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-planification.spec.ts`, `tests/e2e/effectifs-regles.spec.ts`, `tests/e2e/effectifs-socle.spec.ts`

**Essais Java** : `SocleEffectifsDomaineTest.java`, `ControleurIntegrationsEffectifsHttpTest.java`, `ControleurPresenceEffectifsHttpTest.java`, `comportements.json`, `effectifs-planification.spec.ts`, `effectifs-regles.spec.ts`, `effectifs-socle.spec.ts`

### E-GN-10 — La solution doit permettre l’audit, l’analyse et la consultation a posteriori de l’ensemble des traces d’exécution et de paramétrage.

*essentiel · Généralités · verdict : ACQUIS · lot U1*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usjournal` — Filtrer le journal sur une période passée, puis « Exporter » : l'audit a posteriori se lit à l'écran et s'emporte en CSV. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usjournal",
  "prealable": [],
  "preuve": [
    "jrn-ecran",
    "jrn-tableau",
    "jrn-exporter"
  ]
}
```

</details>

**Scénario par l'API C-09** (`urgences-sante-comportements.spec.ts`) — Le journal s'exporte en CSV, en-tête fixe : chaque geste humain ou automatique y est retrouvable

<details><summary>Données JSON de C-09 (1 étapes)</summary>

```json
[
  {
    "methode": "GET",
    "chemin": "/api/effectifs/journal?format=csv",
    "attendu": {
      "statut": 200,
      "texte": "^horodatage,origine,acteurId,action,objetType,objetId,uniteCode,avant,apres"
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 09-journal.feature — Le journal s'exporte en CSV, en-tête fixe : chaque geste humain ou automatique y est retrouvable » ; « 09-journal.feature — Les gestes de paramétrage et d'horaire se retrouvent au journal, horodatés, avec leur auteur, et se filtrent a posteriori »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-socle.spec.ts`

**Essais Java** : `SocleEffectifsDomaineTest.java`, `comportements.json`, `effectifs-socle.spec.ts`

### E-GN-11 — Lorsqu’un quart est attribué manuellement par un utilisateur autorisé dans un contexte d’exception opérationnelle, la solution ne doit pas empêcher l’attribution même si certaines règles de priorisation automatisées ne sont pas respectées, tout en assurant la traçabilité complète de la décision.

*essentiel · Généralités · verdict : ACQUIS · lot U5*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usequipages` — Attribuer une ressource qu'une règle écarte : l'écran liste les avertissements et exige une justification avant de forcer. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usequipages",
  "prealable": [],
  "preuve": [
    "pla-equipages",
    "pla-eq-tableau",
    "pla-eq-lot"
  ]
}
```

</details>

**Scénario par l'API C-02** (`urgences-sante-comportements.spec.ts`) — Une règle de repos bloque l'attribution en nommant son motif ; forcer exige le niveau GESTION et une justification, et le forçage est tracé

<details><summary>Données JSON de C-02 (10 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/effectifs/regles",
    "corps": {
      "contexte": "ATTRIBUTION",
      "cle": "repos-minimal",
      "condition": {
        "fait": "reposHeures",
        "op": "<",
        "valeur": 8
      },
      "action": "BLOQUER",
      "message": "Repos de {reposHeures} h : il en faut 8",
      "effectifDepuis": "2027-01-01"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "pourquoi": "Marc tient le quart de jour, qui finit à 18 h",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 360,
      "fin": 1080,
      "natureCode": "REG",
      "milieuId": "{{c12}}",
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 1
        }
      ]
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "placeJour": "places.0.id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/places/{{placeJour}}/attribuer",
    "corps": {
      "ressourceId": "{{P2}}"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 1080,
      "fin": 1320,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 1
        }
      ],
      "note": "Renfort de soirée"
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "modeAttribution": "MANUEL",
        "note": "Renfort de soirée"
      }
    },
    "capturer": {
      "placeSoir": "places.0.id"
    }
  },
  {
    "pourquoi": "Sans repos entre deux quarts, la règle refuse et dit pourquoi",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/places/{{placeSoir}}/attribuer",
    "corps": {
      "ressourceId": "{{P2}}"
    },
    "attendu": {
      "statut": 409,
      "texte": "Repos de 0 h : il en faut 8"
    }
  },
  {
    "pourquoi": "Un gestionnaire sans périmètre n'a aucun droit : refus par défaut",
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/places/{{placeSoir}}/attribuer",
    "corps": {
      "ressourceId": "{{P2}}",
      "forcer": true,
      "justification": "Aucun autre PSA disponible, appel massif"
    },
    "attendu": {
      "statut": 403
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/perimetres",
    "corps": {
      "utilisateurId": "{{u_chef}}",
      "uniteCode": "EST",
      "etendue": "UNITE",
      "niveau": "GESTION",
      "depuis": "2026-01-01"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "pourquoi": "Une justification de trois mots n'en est pas une",
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/places/{{placeSoir}}/attribuer",
    "corps": {
      "ressourceId": "{{P2}}",
      "forcer": true,
      "justification": "court"
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/places/{{placeSoir}}/attribuer",
    "corps": {
      "ressourceId": "{{P2}}",
      "forcer": true,
      "justification": "Aucun autre PSA disponible, appel massif"
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "force": true
      },
      "texte": "Aucun autre PSA disponible, appel massif"
    }
  },
  {
    "pourquoi": "La trace dit quelle règle a été écartée",
    "methode": "GET",
    "chemin": "/api/effectifs/journal?action=place.attribution.forcee",
    "attendu": {
      "statut": 200,
      "texte": "repos-minimal"
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 02-quarts-et-attribution.feature — Une règle de repos bloque l'attribution en nommant son motif ; forcer exige le niveau GESTION et une justification, et le forçage est tracé »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-planification.spec.ts`

**Essais Java** : `PlanificationDomaineTest.java`, `comportements.json`, `effectifs-planification.spec.ts`

### E-GN-12 — La solution doit permettre de définir des règles de conformité des disponibilités selon les conventions collectives, le statut d’emploi, le type de poste ou le contexte opérationnel.

*essentiel · Généralités · verdict : ACQUIS · lot U3*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usregles` — Contexte « Disponibilités », « Nouvelle règle » : la portée (unité, corps, statut) et le fait choisis forment la règle de conformité. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usregles",
  "prealable": [
    {
      "cliquer": "rgl-nouvelle",
      "pourquoi": "La portée et le fait se choisissent dans le formulaire, pas dans la liste."
    }
  ],
  "preuve": [
    "rgl-ecran",
    "rgl-formulaire",
    "rgl-form-portee",
    "rgl-form-cle"
  ]
}
```

</details>

**Scénario par l'API C-04** (`urgences-sante-comportements.spec.ts`) — Hors collecte, le dépôt de disponibilités est refusé en disant pourquoi ; en collecte, un dépôt trop maigre est bloqué avec son motif, un dépôt suffisant passe avec un avertissement

<details><summary>Données JSON de C-04 (8 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes",
    "corps": {
      "nom": "DISPO",
      "uniteCode": "EST",
      "debut": "2030-03-11",
      "fin": "2030-03-17"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "dispo": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
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
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "placeDispo": "places.0.id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/regles",
    "corps": {
      "contexte": "DISPONIBILITE",
      "cle": "minimum-quarts",
      "condition": {
        "fait": "quartsParSemaine",
        "op": "<",
        "valeur": 2
      },
      "action": "BLOQUER",
      "message": "Au moins 2 quarts par semaine ({quartsParSemaine} offert)",
      "effectifDepuis": "2027-01-01"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/regles",
    "corps": {
      "contexte": "DISPONIBILITE",
      "cle": "peu-d-heures",
      "condition": {
        "fait": "heuresParPeriode",
        "op": "<",
        "valeur": 30
      },
      "action": "AVERTIR",
      "message": "Seulement {heuresParPeriode} h offertes",
      "effectifDepuis": "2027-01-01"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "pourquoi": "La période est en préparation : le dépôt n'existe que par période ouverte",
    "acteur": "julie",
    "methode": "PUT",
    "chemin": "/api/effectifs/disponibilites/moi/{{dispo}}",
    "corps": {
      "plages": [
        {
          "date": "2030-03-11",
          "debut": 360,
          "fin": 1080,
          "type": "DISPONIBLE"
        }
      ]
    },
    "attendu": {
      "statut": 422,
      "texte": "collecte.*n'est pas ouverte"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes/{{dispo}}/etat",
    "corps": {
      "etat": "COLLECTE",
      "finCollecte": "2030-03-01"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "acteur": "julie",
    "methode": "PUT",
    "chemin": "/api/effectifs/disponibilites/moi/{{dispo}}",
    "corps": {
      "plages": [
        {
          "date": "2030-03-11",
          "debut": 360,
          "fin": 1080,
          "type": "DISPONIBLE"
        }
      ]
    },
    "attendu": {
      "statut": 409,
      "texte": "Au moins 2 quarts par semaine \\(1 offert\\)"
    }
  },
  {
    "acteur": "julie",
    "methode": "PUT",
    "chemin": "/api/effectifs/disponibilites/moi/{{dispo}}",
    "corps": {
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
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "conformite": "AVERTIE"
      },
      "texte": "Seulement 24 h offertes"
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 03-disponibilites.feature — Hors collecte, le dépôt de disponibilités est refusé en disant pourquoi ; en collecte, un dépôt trop maigre est bloqué avec son motif, un dépôt suffisant passe avec un avertissement » ; « 03-disponibilites.feature — Une règle de conformité vise une unité ou un statut d'emploi : elle ne s'applique qu'à ceux qu'elle vise »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-regles.spec.ts`, `tests/e2e/urgences-sante-sp-csn.spec.ts`

**Essais Java** : `ReglesEffectifsDomaineTest.java`, `comportements.json`, `effectifs-regles.spec.ts`

### E-GN-13 — La solution doit permettre aux employés d'inscrire leurs disponibilités pour une période donnée.

*essentiel · Généralités · verdict : ACQUIS · lot U3*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/mesdisponibilites` — L'employé choisit la période et dépose ses plages par jour. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "mesdisponibilites",
  "prealable": [],
  "preuve": [
    "mdi-titre",
    "mdi-periode",
    "mdi-deposer"
  ]
}
```

</details>

**Scénario par l'API C-04** (`urgences-sante-comportements.spec.ts`) — Hors collecte, le dépôt de disponibilités est refusé en disant pourquoi ; en collecte, un dépôt trop maigre est bloqué avec son motif, un dépôt suffisant passe avec un avertissement

<details><summary>Données JSON de C-04 (8 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes",
    "corps": {
      "nom": "DISPO",
      "uniteCode": "EST",
      "debut": "2030-03-11",
      "fin": "2030-03-17"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "dispo": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
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
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "placeDispo": "places.0.id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/regles",
    "corps": {
      "contexte": "DISPONIBILITE",
      "cle": "minimum-quarts",
      "condition": {
        "fait": "quartsParSemaine",
        "op": "<",
        "valeur": 2
      },
      "action": "BLOQUER",
      "message": "Au moins 2 quarts par semaine ({quartsParSemaine} offert)",
      "effectifDepuis": "2027-01-01"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/regles",
    "corps": {
      "contexte": "DISPONIBILITE",
      "cle": "peu-d-heures",
      "condition": {
        "fait": "heuresParPeriode",
        "op": "<",
        "valeur": 30
      },
      "action": "AVERTIR",
      "message": "Seulement {heuresParPeriode} h offertes",
      "effectifDepuis": "2027-01-01"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "pourquoi": "La période est en préparation : le dépôt n'existe que par période ouverte",
    "acteur": "julie",
    "methode": "PUT",
    "chemin": "/api/effectifs/disponibilites/moi/{{dispo}}",
    "corps": {
      "plages": [
        {
          "date": "2030-03-11",
          "debut": 360,
          "fin": 1080,
          "type": "DISPONIBLE"
        }
      ]
    },
    "attendu": {
      "statut": 422,
      "texte": "collecte.*n'est pas ouverte"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes/{{dispo}}/etat",
    "corps": {
      "etat": "COLLECTE",
      "finCollecte": "2030-03-01"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "acteur": "julie",
    "methode": "PUT",
    "chemin": "/api/effectifs/disponibilites/moi/{{dispo}}",
    "corps": {
      "plages": [
        {
          "date": "2030-03-11",
          "debut": 360,
          "fin": 1080,
          "type": "DISPONIBLE"
        }
      ]
    },
    "attendu": {
      "statut": 409,
      "texte": "Au moins 2 quarts par semaine \\(1 offert\\)"
    }
  },
  {
    "acteur": "julie",
    "methode": "PUT",
    "chemin": "/api/effectifs/disponibilites/moi/{{dispo}}",
    "corps": {
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
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "conformite": "AVERTIE"
      },
      "texte": "Seulement 24 h offertes"
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 03-disponibilites.feature — Hors collecte, le dépôt de disponibilités est refusé en disant pourquoi ; en collecte, un dépôt trop maigre est bloqué avec son motif, un dépôt suffisant passe avec un avertissement »

**Essais Java** : `ControleurDisponibilitesEffectifsHttpTest.java`, `comportements.json`

### E-GN-14 — La solution doit exclure automatiquement une ressource du bassin de candidats à un quart lorsque ses disponibilités ne respectent pas les règles de conformité définies.

*essentiel · Généralités · verdict : ACQUIS · lot U3*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usdisponibilites` — Sur une place, « Bassin » : les candidats retenus et, à part, les exclus avec le motif de leur exclusion. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usdisponibilites",
  "prealable": [],
  "preuve": [
    "pla-dispo",
    "pla-dispo-depots"
  ]
}
```

</details>

**Scénario par l'API C-05** (`urgences-sante-comportements.spec.ts`) — Le bassin de candidats d'une place exclut, motif à l'appui, la ressource qui n'a rien déposé

<details><summary>Données JSON de C-05 (1 étapes)</summary>

```json
[
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/effectifs/disponibilites/places/{{placeDispo}}/candidats",
    "attendu": {
      "statut": 200,
      "corps": {
        "admissibles": [
          {
            "nom": "Julie"
          }
        ]
      },
      "texte": "\"exclus\":\\[[^\\]]*\"nom\":\"Marc\"[^\\]]*\"motif\":\"[^\"]+\""
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 03-disponibilites.feature — Le bassin de candidats d'une place exclut, motif à l'appui, la ressource qui n'a rien déposé »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-disponibilites.spec.ts`

**Essais Java** : `ControleurDisponibilitesEffectifsHttpTest.java`, `comportements.json`, `effectifs-disponibilites.spec.ts`

### E-GN-15 — La solution doit valider la conformité des disponibilités au moment de leur saisie et empêcher ou signaler toute non-conformité selon des paramètres configurables.

*essentiel · Généralités · verdict : ACQUIS · lot U3*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/mesdisponibilites` — Déposer une plage non conforme : le dépôt est refusé ou signalé selon le réglage de la règle, à la saisie. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "mesdisponibilites",
  "prealable": [],
  "preuve": [
    "mdi-titre",
    "mdi-conformite",
    "mdi-deposer"
  ]
}
```

</details>

**Scénario par l'API C-04** (`urgences-sante-comportements.spec.ts`) — Hors collecte, le dépôt de disponibilités est refusé en disant pourquoi ; en collecte, un dépôt trop maigre est bloqué avec son motif, un dépôt suffisant passe avec un avertissement

<details><summary>Données JSON de C-04 (8 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes",
    "corps": {
      "nom": "DISPO",
      "uniteCode": "EST",
      "debut": "2030-03-11",
      "fin": "2030-03-17"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "dispo": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
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
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "placeDispo": "places.0.id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/regles",
    "corps": {
      "contexte": "DISPONIBILITE",
      "cle": "minimum-quarts",
      "condition": {
        "fait": "quartsParSemaine",
        "op": "<",
        "valeur": 2
      },
      "action": "BLOQUER",
      "message": "Au moins 2 quarts par semaine ({quartsParSemaine} offert)",
      "effectifDepuis": "2027-01-01"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/regles",
    "corps": {
      "contexte": "DISPONIBILITE",
      "cle": "peu-d-heures",
      "condition": {
        "fait": "heuresParPeriode",
        "op": "<",
        "valeur": 30
      },
      "action": "AVERTIR",
      "message": "Seulement {heuresParPeriode} h offertes",
      "effectifDepuis": "2027-01-01"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "pourquoi": "La période est en préparation : le dépôt n'existe que par période ouverte",
    "acteur": "julie",
    "methode": "PUT",
    "chemin": "/api/effectifs/disponibilites/moi/{{dispo}}",
    "corps": {
      "plages": [
        {
          "date": "2030-03-11",
          "debut": 360,
          "fin": 1080,
          "type": "DISPONIBLE"
        }
      ]
    },
    "attendu": {
      "statut": 422,
      "texte": "collecte.*n'est pas ouverte"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes/{{dispo}}/etat",
    "corps": {
      "etat": "COLLECTE",
      "finCollecte": "2030-03-01"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "acteur": "julie",
    "methode": "PUT",
    "chemin": "/api/effectifs/disponibilites/moi/{{dispo}}",
    "corps": {
      "plages": [
        {
          "date": "2030-03-11",
          "debut": 360,
          "fin": 1080,
          "type": "DISPONIBLE"
        }
      ]
    },
    "attendu": {
      "statut": 409,
      "texte": "Au moins 2 quarts par semaine \\(1 offert\\)"
    }
  },
  {
    "acteur": "julie",
    "methode": "PUT",
    "chemin": "/api/effectifs/disponibilites/moi/{{dispo}}",
    "corps": {
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
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "conformite": "AVERTIE"
      },
      "texte": "Seulement 24 h offertes"
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 03-disponibilites.feature — Hors collecte, le dépôt de disponibilités est refusé en disant pourquoi ; en collecte, un dépôt trop maigre est bloqué avec son motif, un dépôt suffisant passe avec un avertissement »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-disponibilites.spec.ts`, `tests/e2e/effectifs-regles.spec.ts`

**Essais Java** : `ControleurDisponibilitesEffectifsHttpTest.java`, `comportements.json`, `effectifs-disponibilites.spec.ts`, `effectifs-regles.spec.ts`

### E-GN-16 — La solution doit informer l’utilisateur des motifs d’inadmissibilité liés à la non-conformité de ses disponibilités.

*essentiel · Généralités · verdict : ACQUIS · lot U3*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/mesdisponibilites` — Le refus nomme la règle et ce qu'elle exige : l'employé lit le motif de son inadmissibilité, pas un code. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "mesdisponibilites",
  "prealable": [],
  "preuve": [
    "mdi-titre",
    "mdi-conformite",
    "mdi-etat"
  ]
}
```

</details>

**Scénario par l'API C-04** (`urgences-sante-comportements.spec.ts`) — Hors collecte, le dépôt de disponibilités est refusé en disant pourquoi ; en collecte, un dépôt trop maigre est bloqué avec son motif, un dépôt suffisant passe avec un avertissement

<details><summary>Données JSON de C-04 (8 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes",
    "corps": {
      "nom": "DISPO",
      "uniteCode": "EST",
      "debut": "2030-03-11",
      "fin": "2030-03-17"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "dispo": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
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
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "placeDispo": "places.0.id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/regles",
    "corps": {
      "contexte": "DISPONIBILITE",
      "cle": "minimum-quarts",
      "condition": {
        "fait": "quartsParSemaine",
        "op": "<",
        "valeur": 2
      },
      "action": "BLOQUER",
      "message": "Au moins 2 quarts par semaine ({quartsParSemaine} offert)",
      "effectifDepuis": "2027-01-01"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/regles",
    "corps": {
      "contexte": "DISPONIBILITE",
      "cle": "peu-d-heures",
      "condition": {
        "fait": "heuresParPeriode",
        "op": "<",
        "valeur": 30
      },
      "action": "AVERTIR",
      "message": "Seulement {heuresParPeriode} h offertes",
      "effectifDepuis": "2027-01-01"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "pourquoi": "La période est en préparation : le dépôt n'existe que par période ouverte",
    "acteur": "julie",
    "methode": "PUT",
    "chemin": "/api/effectifs/disponibilites/moi/{{dispo}}",
    "corps": {
      "plages": [
        {
          "date": "2030-03-11",
          "debut": 360,
          "fin": 1080,
          "type": "DISPONIBLE"
        }
      ]
    },
    "attendu": {
      "statut": 422,
      "texte": "collecte.*n'est pas ouverte"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes/{{dispo}}/etat",
    "corps": {
      "etat": "COLLECTE",
      "finCollecte": "2030-03-01"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "acteur": "julie",
    "methode": "PUT",
    "chemin": "/api/effectifs/disponibilites/moi/{{dispo}}",
    "corps": {
      "plages": [
        {
          "date": "2030-03-11",
          "debut": 360,
          "fin": 1080,
          "type": "DISPONIBLE"
        }
      ]
    },
    "attendu": {
      "statut": 409,
      "texte": "Au moins 2 quarts par semaine \\(1 offert\\)"
    }
  },
  {
    "acteur": "julie",
    "methode": "PUT",
    "chemin": "/api/effectifs/disponibilites/moi/{{dispo}}",
    "corps": {
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
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "conformite": "AVERTIE"
      },
      "texte": "Seulement 24 h offertes"
    }
  }
]
```

</details>

**Scénario par l'API C-05** (`urgences-sante-comportements.spec.ts`) — Le bassin de candidats d'une place exclut, motif à l'appui, la ressource qui n'a rien déposé

<details><summary>Données JSON de C-05 (1 étapes)</summary>

```json
[
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/effectifs/disponibilites/places/{{placeDispo}}/candidats",
    "attendu": {
      "statut": 200,
      "corps": {
        "admissibles": [
          {
            "nom": "Julie"
          }
        ]
      },
      "texte": "\"exclus\":\\[[^\\]]*\"nom\":\"Marc\"[^\\]]*\"motif\":\"[^\"]+\""
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 03-disponibilites.feature — Hors collecte, le dépôt de disponibilités est refusé en disant pourquoi ; en collecte, un dépôt trop maigre est bloqué avec son motif, un dépôt suffisant passe avec un avertissement » ; « 03-disponibilites.feature — Le bassin de candidats d'une place exclut, motif à l'appui, la ressource qui n'a rien déposé »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-disponibilites.spec.ts`, `tests/e2e/effectifs-regles.spec.ts`, `tests/e2e/helpers/urgences-sante.ts`, `tests/ui/audit-wcag22.spec.ts`

**Essais Java** : `ReglesEffectifsDomaineTest.java`, `ControleurDisponibilitesEffectifsHttpTest.java`, `comportements.json`, `effectifs-disponibilites.spec.ts`, `effectifs-regles.spec.ts`, `urgences-sante.ts`, `audit-wcag22.spec.ts`

### E-GN-17 — La solution doit permettre l'identification du milieu physique lié au quart de travail planifié.

*essentiel · Généralités · verdict : ACQUIS · lot U1*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usmilieux` — « Ajouter un milieu » : type, unité, adresse et coordonnées ; le quart planifié s'y rattache. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usmilieux",
  "prealable": [
    {
      "cliquer": "mil-ajouter",
      "pourquoi": "La fiche du milieu est un tiroir."
    }
  ],
  "preuve": [
    "mil-ecran",
    "mil-fiche",
    "mil-form-code",
    "mil-form-type",
    "mil-form-unite"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 01-planification.feature — Un quart réunit plusieurs places de corps d'emploi choisis, rattaché à un milieu physique »

**Essais Java** : `SocleEffectifsDomaineTest.java`

### E-CH-1 — La solution doit permettre la réalisation d’un processus annuel structuré de planification et de révision des horaires (AGPH) pour l'attribution des postes. La solution permet donc aux ressources d'émettre leurs disponibilités (journée/horaire), en respectant les règles prédéfinies de la Convention collective (nombre, durée des quarts et optimisation des budgets) et Conditions particulières ; et paramétrables, selon les besoins, par les personnes identifiées au sein du service de planification des effectifs.

*essentiel · Confection des horaires · verdict : ACQUIS · lot U4*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usagph` — Campagne annuelle : ouvrir la collecte, fixer le budget d'heures, suivre les dépôts de disponibilités rang par rang. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usagph",
  "prealable": [
    {
      "cliquer": "pla-agph-budget-edition-ouvrir",
      "pourquoi": "Le formulaire vit dans un dialogue depuis le 8 novembre 2026 : ses champs n'existent qu'ouvert."
    }
  ],
  "preuve": [
    "pla-agph-dispo",
    "pla-agph-budget-edition-ouvrir",
    "pla-agph-budget"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 06-agph.feature — Une campagne AGPH complète — dépôt, tour de rôle, révision, clôture — puis la gestion régulière reprend les postes libres » ; « 06-agph.feature — Le dépôt AGPH est jugé par les règles de convention — nombre et durée des quarts — et par le budget fixé par la planification » ; « 06-agph.feature — La solution propose les postes qui couvrent le plus d'heures sans dépasser le budget, parmi ceux que les dépôts couvrent »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-agph.spec.ts`, `tests/ui/audit-wcag22.spec.ts`

**Essais Java** : `AgphDomaineTest.java`, `ControleurAgphEffectifsHttpTest.java`, `effectifs-agph.spec.ts`, `audit-wcag22.spec.ts`

### E-CH-2 — La solution doit permettre la création de quarts de travail et de postes liés aux quarts de travail

*essentiel · Confection des horaires · verdict : ACQUIS · lot U2*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/uspostes` — « Nouveau poste » : code, libellé, cycle de quarts et début de cycle. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "uspostes",
  "prealable": [
    {
      "cliquer": "pla-poste-nouveau",
      "pourquoi": "Le poste se crée dans un tiroir."
    }
  ],
  "preuve": [
    "pla-postes",
    "pla-poste-fiche",
    "pla-pos-code",
    "pla-pos-cycle-ajouter"
  ]
}
```

</details>

**Scénario par l'API C-03** (`urgences-sante-comportements.spec.ts`) — Un poste se publie une fois ; une liste vide ou un poste déjà publié est refusé

<details><summary>Données JSON de C-03 (7 étapes)</summary>

```json
[
  {
    "pourquoi": "Un paramètre de poste se déclare avant usage : le refus nomme le référentiel à compléter",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/postes",
    "corps": {
      "code": "3412-J0",
      "libelle": "PSA jour",
      "uniteCode": "EST",
      "statutEmploiCode": "TC",
      "corpsEmploiCode": "PARA",
      "milieuId": "{{c12}}",
      "natureCode": "REG",
      "cycle": [
        [
          "J",
          "J",
          "J",
          null,
          null,
          null,
          null
        ]
      ],
      "debutCycle": "2030-03-18",
      "parametres": {
        "VEHICULE": "3412"
      }
    },
    "attendu": {
      "statut": 422,
      "texte": "paramètre de poste inconnu : « VEHICULE »"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/referentiels/parametres_poste",
    "corps": {
      "effectifDepuis": "2026-01-01",
      "code": "VEHICULE",
      "libelleFr": "Véhicule",
      "attributs": {
        "typeValeur": "TEXTE"
      }
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "pourquoi": "Le type déclaré se vérifie : un nombre n'est pas un texte",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/postes",
    "corps": {
      "code": "3412-J0",
      "libelle": "PSA jour",
      "uniteCode": "EST",
      "statutEmploiCode": "TC",
      "corpsEmploiCode": "PARA",
      "milieuId": "{{c12}}",
      "natureCode": "REG",
      "cycle": [
        [
          "J",
          "J",
          "J",
          null,
          null,
          null,
          null
        ]
      ],
      "debutCycle": "2030-03-18",
      "parametres": {
        "VEHICULE": 12
      }
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/postes",
    "corps": {
      "code": "3412-J1",
      "libelle": "PSA jour",
      "uniteCode": "EST",
      "statutEmploiCode": "TC",
      "corpsEmploiCode": "PARA",
      "milieuId": "{{c12}}",
      "natureCode": "REG",
      "cycle": [
        [
          "J",
          "J",
          "J",
          null,
          null,
          null,
          null
        ]
      ],
      "debutCycle": "2030-03-18",
      "parametres": {
        "VEHICULE": "3412"
      }
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "etat": "BROUILLON",
        "parametres": {
          "VEHICULE": "3412"
        }
      }
    },
    "capturer": {
      "poste": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/postes/publier",
    "corps": {
      "ids": []
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/postes/publier",
    "corps": {
      "ids": [
        "{{poste}}"
      ]
    },
    "attendu": {
      "statut": 200,
      "corps": [
        {
          "etat": "PUBLIE"
        }
      ]
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/postes/publier",
    "corps": {
      "ids": [
        "{{poste}}"
      ]
    },
    "attendu": {
      "statut": 422
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 02-quarts-et-attribution.feature — Un poste se publie une fois ; une liste vide ou un poste déjà publié est refusé »

**Essais Java** : `PlanificationDomaineTest.java`, `comportements.json`

### E-CH-3 — La solution doit permettre la modification de quarts de travail et de postes liés aux quarts de travail (1 ou plusieurs postes)

*essentiel · Confection des horaires · verdict : ACQUIS · lot U2*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/uspostes` — Sélectionner des postes, « Modifier en lot » : le cycle est remplacé pour tous, les conflits sont listés avant d'écrire. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "uspostes",
  "prealable": [],
  "preuve": [
    "pla-postes-lot",
    "pla-postes-publier"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 01-planification.feature — Un poste porte ses paramètres déclarés, se modifie, et la période se génère hors de tout processus annuel »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-planification.spec.ts`, `tests/ui/audit-wcag22.spec.ts`

**Essais Java** : `RepercussionPosteDomaineTest.java`, `ControleurPlanificationEffectifsHttpTest.java`, `effectifs-planification.spec.ts`, `audit-wcag22.spec.ts`

### E-CH-4 — La solution doit permettre la publication de quarts de travail et de postes liés aux quarts de travail

*essentiel · Confection des horaires · verdict : ACQUIS · lot U2*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/uspostes` — « Publier » : les postes passent de brouillon à publié, et leurs quarts entrent dans la planification. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "uspostes",
  "prealable": [],
  "preuve": [
    "pla-postes",
    "pla-postes-publier",
    "pla-postes-tableau"
  ]
}
```

</details>

**Scénario par l'API C-03** (`urgences-sante-comportements.spec.ts`) — Un poste se publie une fois ; une liste vide ou un poste déjà publié est refusé

<details><summary>Données JSON de C-03 (7 étapes)</summary>

```json
[
  {
    "pourquoi": "Un paramètre de poste se déclare avant usage : le refus nomme le référentiel à compléter",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/postes",
    "corps": {
      "code": "3412-J0",
      "libelle": "PSA jour",
      "uniteCode": "EST",
      "statutEmploiCode": "TC",
      "corpsEmploiCode": "PARA",
      "milieuId": "{{c12}}",
      "natureCode": "REG",
      "cycle": [
        [
          "J",
          "J",
          "J",
          null,
          null,
          null,
          null
        ]
      ],
      "debutCycle": "2030-03-18",
      "parametres": {
        "VEHICULE": "3412"
      }
    },
    "attendu": {
      "statut": 422,
      "texte": "paramètre de poste inconnu : « VEHICULE »"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/referentiels/parametres_poste",
    "corps": {
      "effectifDepuis": "2026-01-01",
      "code": "VEHICULE",
      "libelleFr": "Véhicule",
      "attributs": {
        "typeValeur": "TEXTE"
      }
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "pourquoi": "Le type déclaré se vérifie : un nombre n'est pas un texte",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/postes",
    "corps": {
      "code": "3412-J0",
      "libelle": "PSA jour",
      "uniteCode": "EST",
      "statutEmploiCode": "TC",
      "corpsEmploiCode": "PARA",
      "milieuId": "{{c12}}",
      "natureCode": "REG",
      "cycle": [
        [
          "J",
          "J",
          "J",
          null,
          null,
          null,
          null
        ]
      ],
      "debutCycle": "2030-03-18",
      "parametres": {
        "VEHICULE": 12
      }
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/postes",
    "corps": {
      "code": "3412-J1",
      "libelle": "PSA jour",
      "uniteCode": "EST",
      "statutEmploiCode": "TC",
      "corpsEmploiCode": "PARA",
      "milieuId": "{{c12}}",
      "natureCode": "REG",
      "cycle": [
        [
          "J",
          "J",
          "J",
          null,
          null,
          null,
          null
        ]
      ],
      "debutCycle": "2030-03-18",
      "parametres": {
        "VEHICULE": "3412"
      }
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "etat": "BROUILLON",
        "parametres": {
          "VEHICULE": "3412"
        }
      }
    },
    "capturer": {
      "poste": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/postes/publier",
    "corps": {
      "ids": []
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/postes/publier",
    "corps": {
      "ids": [
        "{{poste}}"
      ]
    },
    "attendu": {
      "statut": 200,
      "corps": [
        {
          "etat": "PUBLIE"
        }
      ]
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/postes/publier",
    "corps": {
      "ids": [
        "{{poste}}"
      ]
    },
    "attendu": {
      "statut": 422
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 02-quarts-et-attribution.feature — Un poste se publie une fois ; une liste vide ou un poste déjà publié est refusé »

**Essais Java** : `ControleurPlanificationEffectifsHttpTest.java`, `comportements.json`

### E-CH-5 — La solution doit permettre d’associer à chaque poste ses paramètres fonctionnels.

*essentiel · Confection des horaires · verdict : ACQUIS · lot U2*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/uspostes` — Fiche du poste : statut d'emploi, corps, milieu, nature et cycle — les paramètres fonctionnels du poste. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "uspostes",
  "prealable": [
    {
      "cliquer": "pla-poste-nouveau",
      "pourquoi": "Les paramètres sont les champs de la fiche."
    }
  ],
  "preuve": [
    "pla-poste-fiche",
    "pla-pos-libelle",
    "pla-pos-debut"
  ]
}
```

</details>

**Scénario par l'API C-03** (`urgences-sante-comportements.spec.ts`) — Un poste se publie une fois ; une liste vide ou un poste déjà publié est refusé

<details><summary>Données JSON de C-03 (7 étapes)</summary>

```json
[
  {
    "pourquoi": "Un paramètre de poste se déclare avant usage : le refus nomme le référentiel à compléter",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/postes",
    "corps": {
      "code": "3412-J0",
      "libelle": "PSA jour",
      "uniteCode": "EST",
      "statutEmploiCode": "TC",
      "corpsEmploiCode": "PARA",
      "milieuId": "{{c12}}",
      "natureCode": "REG",
      "cycle": [
        [
          "J",
          "J",
          "J",
          null,
          null,
          null,
          null
        ]
      ],
      "debutCycle": "2030-03-18",
      "parametres": {
        "VEHICULE": "3412"
      }
    },
    "attendu": {
      "statut": 422,
      "texte": "paramètre de poste inconnu : « VEHICULE »"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/referentiels/parametres_poste",
    "corps": {
      "effectifDepuis": "2026-01-01",
      "code": "VEHICULE",
      "libelleFr": "Véhicule",
      "attributs": {
        "typeValeur": "TEXTE"
      }
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "pourquoi": "Le type déclaré se vérifie : un nombre n'est pas un texte",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/postes",
    "corps": {
      "code": "3412-J0",
      "libelle": "PSA jour",
      "uniteCode": "EST",
      "statutEmploiCode": "TC",
      "corpsEmploiCode": "PARA",
      "milieuId": "{{c12}}",
      "natureCode": "REG",
      "cycle": [
        [
          "J",
          "J",
          "J",
          null,
          null,
          null,
          null
        ]
      ],
      "debutCycle": "2030-03-18",
      "parametres": {
        "VEHICULE": 12
      }
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/postes",
    "corps": {
      "code": "3412-J1",
      "libelle": "PSA jour",
      "uniteCode": "EST",
      "statutEmploiCode": "TC",
      "corpsEmploiCode": "PARA",
      "milieuId": "{{c12}}",
      "natureCode": "REG",
      "cycle": [
        [
          "J",
          "J",
          "J",
          null,
          null,
          null,
          null
        ]
      ],
      "debutCycle": "2030-03-18",
      "parametres": {
        "VEHICULE": "3412"
      }
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "etat": "BROUILLON",
        "parametres": {
          "VEHICULE": "3412"
        }
      }
    },
    "capturer": {
      "poste": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/postes/publier",
    "corps": {
      "ids": []
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/postes/publier",
    "corps": {
      "ids": [
        "{{poste}}"
      ]
    },
    "attendu": {
      "statut": 200,
      "corps": [
        {
          "etat": "PUBLIE"
        }
      ]
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/postes/publier",
    "corps": {
      "ids": [
        "{{poste}}"
      ]
    },
    "attendu": {
      "statut": 422
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 01-planification.feature — Un poste porte ses paramètres déclarés, se modifie, et la période se génère hors de tout processus annuel » ; « 02-quarts-et-attribution.feature — Un poste se publie une fois ; une liste vide ou un poste déjà publié est refusé »

**Essais Java** : `PlanificationDomaineTest.java`, `comportements.json`

### E-CH-6 — La solution doit identifier automatiquement les postes vacants causés par des absences planifiées.

*essentiel · Confection des horaires · verdict : ACQUIS · lot U5*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usabsences` — Une absence accordée réserve ses places : l'onglet les montre comme vacantes, sans geste de planificateur. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usabsences",
  "prealable": [],
  "preuve": [
    "pla-abs",
    "pla-abs-table"
  ]
}
```

</details>

**Scénario par l'API C-07** (`urgences-sante-comportements.spec.ts`) — La dérogation exige un droit, un motif et un code ; elle accorde l'absence et rend les places vacantes par un mécanisme journalisé

<details><summary>Données JSON de C-07 (4 étapes)</summary>

```json
[
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/effectifs/absences/{{refusee}}/decisions",
    "corps": {
      "decision": "DEROGER",
      "motif": "Hospitalisation confirmée par billet médical",
      "codeAbsence": "MAL"
    },
    "attendu": {
      "statut": 403
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/effectifs/absences/{{refusee}}/decisions",
    "corps": {
      "decision": "DEROGER",
      "motif": "court",
      "codeAbsence": "MAL"
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/effectifs/absences/{{refusee}}/decisions",
    "corps": {
      "decision": "DEROGER",
      "motif": "Hospitalisation confirmée par billet médical",
      "codeAbsence": "MAL"
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "statut": "ACCEPTEE",
        "derogationCode": "MAL",
        "placesLiberees": 2,
        "decideePar": "{{u_chef}}"
      }
    }
  },
  {
    "methode": "GET",
    "chemin": "/api/effectifs/journal?action=places.vacantes",
    "attendu": {
      "statut": 200,
      "texte": "places\\.vacantes"
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 04-absences.feature — La dérogation exige un droit, un motif et un code ; elle accorde l'absence et rend les places vacantes par un mécanisme journalisé » ; « 04-absences.feature — Le seuil du code décide du remplacement long terme, sauf pendant une période horaire en cours »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-absences.spec.ts`

**Essais Java** : `ControleurAbsencesEffectifsHttpTest.java`, `comportements.json`, `effectifs-absences.spec.ts`

### E-CH-7 — La solution doit permettre l’ouverture d’un processus de remplacements long terme.

*essentiel · Confection des horaires · verdict : ACQUIS · lot U4*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usagph` — « Ouvrir un remplacement long terme » : poste, fenêtre et date limite pour se manifester. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usagph",
  "prealable": [],
  "preuve": [
    "pla-rlt-table",
    "pla-rlt-ouvrir"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 04-absences.feature — Le seuil du code décide du remplacement long terme, sauf pendant une période horaire en cours » ; « 06-agph.feature — Un remplacement long terme s'ouvre, reçoit les manifestations d'intérêt et s'attribue au plus ancien des intéressés »

**Essais Java** : `ControleurAgphEffectifsHttpTest.java`

### E-CH-8 — La solution doit permettre aux employés admissibles de se manifester pour des remplacements long terme.

*essentiel · Confection des horaires · verdict : ACQUIS · lot U4*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/monagph` — L'employé admissible voit le remplacement ouvert et se manifeste pour lui. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "monagph",
  "prealable": [],
  "preuve": [
    "agm-titre",
    "agm-info|agm-sans-remplacement|prefixe:agm-rlt-"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 06-agph.feature — Un remplacement long terme s'ouvre, reçoit les manifestations d'intérêt et s'attribue au plus ancien des intéressés »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-agph.spec.ts`

**Essais Java** : `ControleurAgphEffectifsHttpTest.java`, `effectifs-agph.spec.ts`

### E-CH-9 — La solution doit permettre que certains remplacements demeurent non comblés.

*essentiel · Confection des horaires · verdict : ACQUIS · lot U4*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usagph` — Clore un remplacement « non comblé » avec son motif : l'état le dit, et il n'est pas forcé à un titulaire. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usagph",
  "prealable": [],
  "preuve": [
    "pla-agph-etat"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 06-agph.feature — L'encadrement laisse un remplacement non comblé, motif à l'appui : ses quarts passent à la gestion régulière »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-agph.spec.ts`, `tests/ui/audit-wcag22.spec.ts`

**Essais Java** : `ControleurAgphEffectifsHttpTest.java`, `effectifs-agph.spec.ts`, `audit-wcag22.spec.ts`

### E-CH-10 — La solution doit permettre le transfert automatique des quarts avec postes non couverts vers la gestion régulière.

*essentiel · Confection des horaires · verdict : ACQUIS · lot U4*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/uscomblement` — Clore la campagne : les quarts restés sans titulaire apparaissent au tableau de comblement de la gestion régulière. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "uscomblement",
  "prealable": [],
  "preuve": [
    "uscomblement-titre",
    "pla-comblement"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 06-agph.feature — Une campagne AGPH complète — dépôt, tour de rôle, révision, clôture — puis la gestion régulière reprend les postes libres » ; « 06-agph.feature — L'encadrement laisse un remplacement non comblé, motif à l'appui : ses quarts passent à la gestion régulière »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-agph.spec.ts`

**Essais Java** : `ControleurAgphEffectifsHttpTest.java`, `effectifs-agph.spec.ts`

### E-GR-1 — La solution doit permettre la gestion continue des horaires en dehors du processus AGPH.

*essentiel · Gestion régulière des horaires · verdict : ACQUIS · lot U4*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/uscomblement` — Le tableau du matin, hors campagne annuelle : taux de comblement, quarts sous minimum, quotas manqués. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "uscomblement",
  "prealable": [],
  "preuve": [
    "pla-comblement",
    "pla-tuile-taux",
    "pla-tuile-sous-minimum"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 01-planification.feature — Un poste porte ses paramètres déclarés, se modifie, et la période se génère hors de tout processus annuel »

**Essais Java** : `ControleurPlanificationEffectifsHttpTest.java`

### E-GR-2 — La solution doit prendre en charge les quarts non couverts à l’issue de l’AGPH.

*essentiel · Gestion régulière des horaires · verdict : ACQUIS · lot U4*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/uscomblement` — Les quarts non couverts à la clôture de la campagne sont pris en charge ici, et non laissés dans l'AGPH. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "uscomblement",
  "prealable": [],
  "preuve": [
    "uscomblement-titre",
    "pla-sous-minimum"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 06-agph.feature — Une campagne AGPH complète — dépôt, tour de rôle, révision, clôture — puis la gestion régulière reprend les postes libres »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-agph.spec.ts`

**Essais Java** : `ControleurAgphEffectifsHttpTest.java`, `effectifs-agph.spec.ts`

### E-GR-3 — La solution doit évaluer l’impact d’une absence sur le taux de comblement.

*essentiel · Gestion régulière des horaires · verdict : ACQUIS · lot U5*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usabsences` — Ouvrir une demande d'absence de la liste : sa fiche calcule le taux de comblement qu'elle laisserait, avant toute décision. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usabsences",
  "prealable": [],
  "preuve": [
    "pla-abs",
    "pla-abs-filtre",
    "pla-abs-table"
  ]
}
```

</details>

**Scénario par l'API C-06** (`urgences-sante-comportements.spec.ts`) — Une absence qui ferait tomber le comblement est refusée d'office, et le refus est conservé avec son motif

<details><summary>Données JSON de C-06 (9 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes",
    "corps": {
      "nom": "ABS",
      "uniteCode": "EST",
      "debut": "2030-03-04",
      "fin": "2030-03-10"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "abs": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes/{{abs}}/etat",
    "corps": {
      "etat": "CONFECTION"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
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
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "placeLundi": "places.0.id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/places/{{placeLundi}}/attribuer",
    "corps": {
      "ressourceId": "{{P1}}"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
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
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "placeMardi": "places.0.id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/places/{{placeMardi}}/attribuer",
    "corps": {
      "ressourceId": "{{P1}}"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/regles",
    "corps": {
      "contexte": "ABSENCE",
      "cle": "comblement-minimal",
      "condition": {
        "fait": "tauxComblementApres",
        "op": "<",
        "valeur": 60
      },
      "action": "REFUSER",
      "message": "Le comblement tomberait à {tauxComblementApres} %",
      "effectifDepuis": "2027-01-01"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/effectifs/absences",
    "corps": {
      "motif": "MAL",
      "du": "2030-03-04",
      "au": "2030-03-05",
      "commentaire": "Grippe"
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "statut": "REFUSEE_AUTO",
        "placesLiberees": 0
      },
      "texte": "Le comblement tomberait à 0 %"
    },
    "capturer": {
      "refusee": "ids.0"
    }
  },
  {
    "pourquoi": "L'impact se lit avant de décider : le taux avant et après",
    "methode": "GET",
    "chemin": "/api/effectifs/absences/{{refusee}}/impact",
    "attendu": {
      "statut": 200,
      "corps": {
        "tauxAvant": 100,
        "tauxApres": 0
      }
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 04-absences.feature — Une absence qui ferait tomber le comblement est refusée d'office, et le refus est conservé avec son motif »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-absences.spec.ts`

**Essais Java** : `ControleurAbsencesEffectifsHttpTest.java`, `comportements.json`, `effectifs-absences.spec.ts`

### E-GR-4 — La solution doit identifier automatiquement les quarts à offrir pour comblement.

*essentiel · Gestion régulière des horaires · verdict : ACQUIS · lot U5*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usabsences` — Les places libérées par l'absence accordée sont offertes d'elles-mêmes au comblement, sans les désigner une à une. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usabsences",
  "prealable": [],
  "preuve": [
    "pla-abs",
    "pla-abs-table"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 04-absences.feature — Après une dérogation, le quart automatisé va au prioritaire disponible et le quart manuel est offert aux admissibles, avisés » ; « 04-absences.feature — Le seuil du code décide du remplacement long terme, sauf pendant une période horaire en cours »

**Essais Java** : `ControleurAbsencesEffectifsHttpTest.java`

### E-GR-5 — La solution doit permettre la gestion d’ajustements journaliers.

*essentiel · Gestion régulière des horaires · verdict : ACQUIS · lot U2*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usperiodes` — Ajuster une journée : « Nouvelle période » et l'ajustement d'un quart existant portent les corrections du jour. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usperiodes",
  "prealable": [],
  "preuve": [
    "pla-periodes",
    "pla-periode-nouvelle"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 01-planification.feature — Un quart du jour s'ajuste — heures et minimum — et un ajustement vide ou impossible est refusé »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-planification.spec.ts`

**Essais Java** : `ControleurPlanificationEffectifsHttpTest.java`, `effectifs-planification.spec.ts`

### E-GR-6 — La solution doit permettre la création et la gestion de quarts ad hoc.

*essentiel · Gestion régulière des horaires · verdict : ACQUIS · lot U2*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usequipages` — « Quart ad hoc » crée et gère un quart hors cycle ; « Libérer » le retire avec son motif. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usequipages",
  "prealable": [],
  "preuve": [
    "pla-equipages",
    "pla-eq-adhoc"
  ]
}
```

</details>

**Scénario par l'API C-01** (`urgences-sante-comportements.spec.ts`) — Un quart ad hoc se crée hors de tout poste ; hors période, sans composition, à minimum impossible ou sur une plage inconnue, il est refusé

<details><summary>Données JSON de C-01 (6 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes",
    "corps": {
      "nom": "PLAN",
      "uniteCode": "EST",
      "debut": "2030-03-18",
      "fin": "2030-03-24"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "plan": "id"
    }
  },
  {
    "pourquoi": "Hors de la période : un quart n'existe que dans une période",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-04-01",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 2
        }
      ]
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "pourquoi": "Sans composition : un quart sans place n'est pas un quart",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "composition": []
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "pourquoi": "Un minimum opérationnel de 3 sur 2 places ne serait jamais atteint",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "minimumOperationnel": 3,
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 2
        }
      ]
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "plageCode": "ZZ",
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 2
        }
      ]
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "pourquoi": "Le mode d'attribution se choisit par quart ; le minimum se déclare",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "modeAttribution": "AUTOMATISE",
      "minimumOperationnel": 1,
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 2
        }
      ]
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "modeAttribution": "AUTOMATISE",
        "minimumOperationnel": 1
      }
    },
    "capturer": {
      "adhoc": "id",
      "placeAdhoc": "places.0.id"
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 02-quarts-et-attribution.feature — Un quart ad hoc se crée hors de tout poste ; hors période, sans composition, à minimum impossible ou sur une plage inconnue, il est refusé »

**Essais Java** : `ControleurPlanificationEffectifsHttpTest.java`, `comportements.json`

### E-GR-7 — La solution doit permettre la gestion des échanges de quarts incluant les validations d’admissibilité et de contiguïté.

*essentiel · Gestion régulière des horaires · verdict : ACQUIS · lot U3*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usechanges` — Valider un échange : l'écran montre l'admissibilité et la contiguïté des deux quarts, et le motif d'un rejet. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usechanges",
  "prealable": [],
  "preuve": [
    "pla-ech",
    "pla-ech-filtre",
    "pla-ech-table"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 02-quarts-et-attribution.feature — Une règle à portée de convention ne vise que ses personnes ; le travail en continuité n'est pas un repos (14.06) mais compte dans les 16 h sur 24 h (16.02) » ; « 05-echanges.feature — Un échange se propose, s'accepte par l'autre employé seulement, et s'applique aux deux horaires » ; « 05-echanges.feature — Un échange qui priverait un employé de son repos est bloqué, motif à l'appui ; un quart non publié ne s'échange pas » ; « 05-echanges.feature — Un échange qui ferait travailler plus de 16 heures sur 24 heures est bloqué, pour la seule personne concernée »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-echanges.spec.ts`

**Essais Java** : `EchangeEffectifsDomaineTest.java`, `ControleurEchangesEffectifsHttpTest.java`, `effectifs-echanges.spec.ts`

### E-GR-8 — La solution doit permettre la définition de quotas de comblement distincts par période, plage horaire ou type de quart.

*essentiel · Gestion régulière des horaires · verdict : ACQUIS · lot U3*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usquotas` — « Définir un quota » : clé (période, plage ou type de quart) et valeur, distincts les uns des autres. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usquotas",
  "prealable": [
    {
      "cliquer": "pla-quota-definir",
      "pourquoi": "Le quota se définit dans un tiroir."
    }
  ],
  "preuve": [
    "pla-quotas",
    "pla-quota-tiroir",
    "pla-quo-quota",
    "pla-quo-depuis"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 01-planification.feature — Les quotas de comblement se règlent par unité, par plage et par nature de quart »

**Essais Java** : `PlanificationDomaineTest.java`

### E-GR-9 — La solution doit permettre l’ajustement temporaire de paramètres opérationnels incluant les quotas en cours de période sans effet rétroactif.

*essentiel · Gestion régulière des horaires · verdict : ACQUIS · lot U3*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usquotas` — Surcharger un quota en cours de période : la surcharge est datée de « à partir de », donc sans effet rétroactif. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usquotas",
  "prealable": [],
  "preuve": [
    "pla-quotas",
    "pla-quotas-tableau",
    "pla-quota-definir"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 01-planification.feature — Un quota s'ajuste temporairement, avec un motif, et jamais dans le passé »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-socle.spec.ts`

**Essais Java** : `PlanificationDomaineTest.java`, `SocleEffectifsDomaineTest.java`, `effectifs-socle.spec.ts`

### E-GR-10 — La solution doit permettre la gestion des horaires par périodes de planification incluant l’ouverture et la fermeture contrôlées.

*essentiel · Gestion régulière des horaires · verdict : ACQUIS · lot U3*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usperiodes` — Créer une période, ouvrir sa collecte avec sa date de fin, puis la clore : l'ouverture et la fermeture sont des gestes distincts et tracés. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usperiodes",
  "prealable": [
    {
      "cliquer": "pla-periode-nouvelle",
      "pourquoi": "La période se crée dans un tiroir."
    }
  ],
  "preuve": [
    "pla-periodes",
    "pla-periode-tiroir",
    "pla-per-debut",
    "pla-per-fin"
  ]
}
```

</details>

**Scénario par l'API C-04** (`urgences-sante-comportements.spec.ts`) — Hors collecte, le dépôt de disponibilités est refusé en disant pourquoi ; en collecte, un dépôt trop maigre est bloqué avec son motif, un dépôt suffisant passe avec un avertissement

<details><summary>Données JSON de C-04 (8 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes",
    "corps": {
      "nom": "DISPO",
      "uniteCode": "EST",
      "debut": "2030-03-11",
      "fin": "2030-03-17"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "dispo": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
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
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "placeDispo": "places.0.id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/regles",
    "corps": {
      "contexte": "DISPONIBILITE",
      "cle": "minimum-quarts",
      "condition": {
        "fait": "quartsParSemaine",
        "op": "<",
        "valeur": 2
      },
      "action": "BLOQUER",
      "message": "Au moins 2 quarts par semaine ({quartsParSemaine} offert)",
      "effectifDepuis": "2027-01-01"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/regles",
    "corps": {
      "contexte": "DISPONIBILITE",
      "cle": "peu-d-heures",
      "condition": {
        "fait": "heuresParPeriode",
        "op": "<",
        "valeur": 30
      },
      "action": "AVERTIR",
      "message": "Seulement {heuresParPeriode} h offertes",
      "effectifDepuis": "2027-01-01"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "pourquoi": "La période est en préparation : le dépôt n'existe que par période ouverte",
    "acteur": "julie",
    "methode": "PUT",
    "chemin": "/api/effectifs/disponibilites/moi/{{dispo}}",
    "corps": {
      "plages": [
        {
          "date": "2030-03-11",
          "debut": 360,
          "fin": 1080,
          "type": "DISPONIBLE"
        }
      ]
    },
    "attendu": {
      "statut": 422,
      "texte": "collecte.*n'est pas ouverte"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes/{{dispo}}/etat",
    "corps": {
      "etat": "COLLECTE",
      "finCollecte": "2030-03-01"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "acteur": "julie",
    "methode": "PUT",
    "chemin": "/api/effectifs/disponibilites/moi/{{dispo}}",
    "corps": {
      "plages": [
        {
          "date": "2030-03-11",
          "debut": 360,
          "fin": 1080,
          "type": "DISPONIBLE"
        }
      ]
    },
    "attendu": {
      "statut": 409,
      "texte": "Au moins 2 quarts par semaine \\(1 offert\\)"
    }
  },
  {
    "acteur": "julie",
    "methode": "PUT",
    "chemin": "/api/effectifs/disponibilites/moi/{{dispo}}",
    "corps": {
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
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "conformite": "AVERTIE"
      },
      "texte": "Seulement 24 h offertes"
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 01-planification.feature — Une période fermée ne se modifie plus : la fermeture est contrôlée et définitive » ; « 03-disponibilites.feature — Hors collecte, le dépôt de disponibilités est refusé en disant pourquoi ; en collecte, un dépôt trop maigre est bloqué avec son motif, un dépôt suffisant passe avec un avertissement »

**Essais Java** : `PlanificationDomaineTest.java`, `comportements.json`

### E-GR-11 — La solution doit permettre le dépôt et l’ajustement des disponibilités par période ouverte.

*essentiel · Gestion régulière des horaires · verdict : ACQUIS · lot U3*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/mesdisponibilites` — Tant que la collecte de la période est ouverte, l'employé dépose et ajuste ses disponibilités ; fermée, l'écran passe en lecture seule. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "mesdisponibilites",
  "prealable": [],
  "preuve": [
    "mdi-titre",
    "mdi-periode",
    "mdi-etat",
    "mdi-deposer"
  ]
}
```

</details>

**Scénario par l'API C-04** (`urgences-sante-comportements.spec.ts`) — Hors collecte, le dépôt de disponibilités est refusé en disant pourquoi ; en collecte, un dépôt trop maigre est bloqué avec son motif, un dépôt suffisant passe avec un avertissement

<details><summary>Données JSON de C-04 (8 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes",
    "corps": {
      "nom": "DISPO",
      "uniteCode": "EST",
      "debut": "2030-03-11",
      "fin": "2030-03-17"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "dispo": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
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
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "placeDispo": "places.0.id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/regles",
    "corps": {
      "contexte": "DISPONIBILITE",
      "cle": "minimum-quarts",
      "condition": {
        "fait": "quartsParSemaine",
        "op": "<",
        "valeur": 2
      },
      "action": "BLOQUER",
      "message": "Au moins 2 quarts par semaine ({quartsParSemaine} offert)",
      "effectifDepuis": "2027-01-01"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/regles",
    "corps": {
      "contexte": "DISPONIBILITE",
      "cle": "peu-d-heures",
      "condition": {
        "fait": "heuresParPeriode",
        "op": "<",
        "valeur": 30
      },
      "action": "AVERTIR",
      "message": "Seulement {heuresParPeriode} h offertes",
      "effectifDepuis": "2027-01-01"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "pourquoi": "La période est en préparation : le dépôt n'existe que par période ouverte",
    "acteur": "julie",
    "methode": "PUT",
    "chemin": "/api/effectifs/disponibilites/moi/{{dispo}}",
    "corps": {
      "plages": [
        {
          "date": "2030-03-11",
          "debut": 360,
          "fin": 1080,
          "type": "DISPONIBLE"
        }
      ]
    },
    "attendu": {
      "statut": 422,
      "texte": "collecte.*n'est pas ouverte"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes/{{dispo}}/etat",
    "corps": {
      "etat": "COLLECTE",
      "finCollecte": "2030-03-01"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "acteur": "julie",
    "methode": "PUT",
    "chemin": "/api/effectifs/disponibilites/moi/{{dispo}}",
    "corps": {
      "plages": [
        {
          "date": "2030-03-11",
          "debut": 360,
          "fin": 1080,
          "type": "DISPONIBLE"
        }
      ]
    },
    "attendu": {
      "statut": 409,
      "texte": "Au moins 2 quarts par semaine \\(1 offert\\)"
    }
  },
  {
    "acteur": "julie",
    "methode": "PUT",
    "chemin": "/api/effectifs/disponibilites/moi/{{dispo}}",
    "corps": {
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
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "conformite": "AVERTIE"
      },
      "texte": "Seulement 24 h offertes"
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 03-disponibilites.feature — Hors collecte, le dépôt de disponibilités est refusé en disant pourquoi ; en collecte, un dépôt trop maigre est bloqué avec son motif, un dépôt suffisant passe avec un avertissement »

**Essais Java** : `ControleurDisponibilitesEffectifsHttpTest.java`, `comportements.json`

### E-GR-12 — La solution doit permettre de configurer si les échanges de quarts entre différentes périodes de planification sont autorisés, restreints ou interdits.

*essentiel · Gestion régulière des horaires · verdict : ACQUIS · lot U3*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usechanges` — Un échange entre deux périodes de planification est accepté, restreint ou refusé selon la règle en vigueur, qui est nommée dans le refus. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usechanges",
  "prealable": [],
  "preuve": [
    "pla-ech",
    "pla-ech-table"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 05-echanges.feature — Un échange entre deux périodes suit la règle configurée — ici, la validation par le chef » ; « 05-echanges.feature — Sans règle, un échange entre deux périodes s'applique ; une règle peut aussi l'interdire »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-echanges.spec.ts`

**Essais Java** : `ControleurEchangesEffectifsHttpTest.java`, `effectifs-echanges.spec.ts`

### E-GR-13 — La solution doit permettre de configurer des quarts de travail non lié à des postes (Quarts ad hoc).

*essentiel · Gestion régulière des horaires · verdict : ACQUIS · lot U2*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usequipages` — Le quart ad hoc n'exige aucun poste : sa nature et sa plage suffisent. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usequipages",
  "prealable": [
    {
      "cliquer": "pla-eq-adhoc",
      "pourquoi": "L'absence de poste se constate sur le formulaire de création."
    }
  ],
  "preuve": [
    "pla-adhoc",
    "pla-ah-nature",
    "pla-ah-debut",
    "pla-ah-fin"
  ]
}
```

</details>

**Scénario par l'API C-01** (`urgences-sante-comportements.spec.ts`) — Un quart ad hoc se crée hors de tout poste ; hors période, sans composition, à minimum impossible ou sur une plage inconnue, il est refusé

<details><summary>Données JSON de C-01 (6 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes",
    "corps": {
      "nom": "PLAN",
      "uniteCode": "EST",
      "debut": "2030-03-18",
      "fin": "2030-03-24"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "plan": "id"
    }
  },
  {
    "pourquoi": "Hors de la période : un quart n'existe que dans une période",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-04-01",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 2
        }
      ]
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "pourquoi": "Sans composition : un quart sans place n'est pas un quart",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "composition": []
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "pourquoi": "Un minimum opérationnel de 3 sur 2 places ne serait jamais atteint",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "minimumOperationnel": 3,
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 2
        }
      ]
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "plageCode": "ZZ",
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 2
        }
      ]
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "pourquoi": "Le mode d'attribution se choisit par quart ; le minimum se déclare",
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
      "periodeId": "{{plan}}",
      "date": "2030-03-18",
      "debut": 600,
      "fin": 660,
      "natureCode": "ADH",
      "milieuId": "{{c12}}",
      "modeAttribution": "AUTOMATISE",
      "minimumOperationnel": 1,
      "composition": [
        {
          "corpsEmploiCode": "PARA",
          "nombre": 2
        }
      ]
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "modeAttribution": "AUTOMATISE",
        "minimumOperationnel": 1
      }
    },
    "capturer": {
      "adhoc": "id",
      "placeAdhoc": "places.0.id"
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 02-quarts-et-attribution.feature — Un quart ad hoc se crée hors de tout poste ; hors période, sans composition, à minimum impossible ou sur une plage inconnue, il est refusé »

**Essais Java** : `ControleurPlanificationEffectifsHttpTest.java`, `comportements.json`

### E-GP-1 — La solution doit permettre aux employés la déclaration des heures de travail en temps réel selon des profils configurables

*essentiel · Gestion du "punch" · verdict : ACQUIS · lot U6*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/monpointage` — « Déclarer » : l'employé pointe son entrée et sa sortie en temps réel, selon le profil de pointage qui lui est posé. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "monpointage",
  "prealable": [],
  "preuve": [
    "mpo-titre",
    "mpo-declarer-ouvrir",
    "mpo-table"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 07-presence.feature — Julie pointe à la caserne : la preuve exigée par le milieu est satisfaite, la présence préalable compte, puis pause et sortie » ; « 07-presence.feature — Le profil de pointage décide du mode et des pauses admises ; retiré, il rend le défaut »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-presence.spec.ts`

**Essais Java** : `ProfilsPointageTest.java`, `ControleurPresenceEffectifsHttpTest.java`, `effectifs-presence.spec.ts`

### E-GP-2 — La solution doit permettre la saisie manuelle des pauses, repas et périodes de repos non payé selon des profils configurables.

*essentiel · Gestion du "punch" · verdict : ACQUIS · lot U6*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/uspresence` — Profils de pointage : le profil dit quelles natures de temps non payé (pause, repas, repos) sont admises à la saisie. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "uspresence",
  "prealable": [],
  "preuve": [
    "pla-pre",
    "pfl-panneau",
    "pfl-pauses"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 07-presence.feature — Julie pointe à la caserne : la preuve exigée par le milieu est satisfaite, la présence préalable compte, puis pause et sortie » ; « 07-presence.feature — Le profil de pointage décide du mode et des pauses admises ; retiré, il rend le défaut »

**Essais Java** : `PresenceQualificationDomaineTest.java`, `ProfilsPointageTest.java`, `ControleurPresenceEffectifsHttpTest.java`

### E-GP-3 — La solution doit restreindre la modification des heures selon des règles configurables.

*essentiel · Gestion du "punch" · verdict : ACQUIS · lot U7*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/ustemps` — Une feuille soumise ou approuvée n'est plus modifiable par l'employé : la règle de modification restreint selon le statut et le délai. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "ustemps",
  "prealable": [],
  "preuve": [
    "pla-tps",
    "pla-tps-filtre"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 08-feuilles-de-temps.feature — La saisie est jugée en temps réel ; une correction crée une version motivée ; les règles restreignent qui modifie quoi »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-temps-flux.spec.ts`

**Essais Java** : `FluxApprobationDomaineTest.java`, `ControleurTempsEffectifsHttpTest.java`, `ControleurTempsFluxEffectifsHttpTest.java`, `FluxEssai.java`, `effectifs-temps-flux.spec.ts`

### E-GP-4 — La solution doit valider les heures saisies selon les règles d’affaires applicables au quart de travail

*essentiel · Gestion du "punch" · verdict : ACQUIS · lot U6*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/uspresence` — La fiche d'un pointage montre les motifs retenus par les règles du quart, et l'écart au planifié. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "uspresence",
  "prealable": [],
  "preuve": [
    "pla-pre",
    "pla-pre-filtre",
    "pla-pre-table"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 07-presence.feature — Un écart de lieu déclenche l'action configurée — blocage au-delà de 5 km, mise en attente sans preuve — et l'encadrement décide, motif à l'appui »

**Essais Java** : `ControleurPresenceEffectifsHttpTest.java`

### E-GP-5 — La solution doit aviser l'utilisateur de la présence d'une anomalie lors de la saisie en cas de non respect d'un ou pluseiurs paramètres identifiés dans les règles d'affaire.

*essentiel · Gestion du "punch" · verdict : ACQUIS · lot U7*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/matemps` — Une anomalie est nommée sur la feuille au moment de la saisie, avec ce qu'il faut corriger ; une anomalie bloquante empêche la soumission. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "matemps",
  "prealable": [],
  "preuve": [
    "mft-titre",
    "mft-preparation-ouvrir",
    "mft-anomalies|mft-sans-anomalie"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 08-feuilles-de-temps.feature — La saisie est jugée en temps réel ; une correction crée une version motivée ; les règles restreignent qui modifie quoi »

**Essais Java** : `ControleurTempsEffectifsHttpTest.java`

### E-GP-6 — La solution doit permettre l’approbation des heures saisies selon des flux configurables.

*essentiel · Gestion du "punch" · verdict : ACQUIS · lot U7*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/ustemps` — « Paramètres des feuilles » → flux d'approbation : paliers, approbateur et condition de routage se configurent. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "ustemps",
  "prealable": [
    {
      "cliquer": "feuilles-approbations-ouvrir",
      "pourquoi": "Le formulaire vit dans un dialogue depuis le 8 novembre 2026 : ses champs n'existent qu'ouvert."
    }
  ],
  "preuve": [
    "feuilles-approbations-ouvrir",
    "pla-tps-flux",
    "pla-tps-flux-palier",
    "pla-tps-flux-approbateur",
    "pla-tps-flux-champ",
    "pla-tps-flux-operateur"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 07-presence.feature — Un écart de lieu déclenche l'action configurée — blocage au-delà de 5 km, mise en attente sans preuve — et l'encadrement décide, motif à l'appui » ; « 08-feuilles-de-temps.feature — La feuille soumise se verrouille et s'approuve par paliers, deux personnes ; approuvée, elle part à la paie dans un lot structuré » ; « 08-feuilles-de-temps.feature — Le flux d'approbation se configure : un palier de plus quand les heures supplémentaires dépassent un seuil »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-temps-flux.spec.ts`, `tests/ui/audit-wcag22.spec.ts`

**Essais Java** : `FluxApprobationDomaineTest.java`, `ControleurPresenceEffectifsHttpTest.java`, `ControleurTempsEffectifsHttpTest.java`, `ControleurTempsFluxEffectifsHttpTest.java`, `FluxEssai.java`, `effectifs-temps-flux.spec.ts`, `audit-wcag22.spec.ts`

### E-GP-7 — La solution doit permettre la gestion multi-site de la déclaration de présence.

*essentiel · Gestion du "punch" · verdict : ACQUIS · lot U6*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/uspresence` — Filtrer les pointages par milieu : la déclaration de présence est rattachée au site, et plusieurs sites coexistent. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "uspresence",
  "prealable": [],
  "preuve": [
    "pla-pre",
    "pla-pre-milieu",
    "pla-pre-site"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 07-presence.feature — Le site déclaré au pointage — un autre milieu que celui du quart — se lit dans la répartition de « ma présence » »

**Essais e2e qui la nomment** : `tests/ui/audit-wcag22.spec.ts`

**Essais Java** : `PresenceQualificationDomaineTest.java`, `RepartitionPresenceTest.java`, `ControleurPresenceEffectifsHttpTest.java`, `audit-wcag22.spec.ts`

### E-GP-8 — La solution doit permettre la comparaison entre heures planifiées et heures déclarées.

*essentiel · Gestion du "punch" · verdict : ACQUIS · lot U7*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/matemps` — Sur la feuille : le tableau « Planifié et déclaré, quart par quart » et son écart. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "matemps",
  "prealable": [],
  "preuve": [
    "mft-titre",
    "mft-feuille-detail",
    "mft-totaux"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 08-feuilles-de-temps.feature — La feuille se prépare depuis l'horaire : heures par type, heures supplémentaires, écart planifié-déclaré et anomalies affichées »

**Essais Java** : `ControleurTempsEffectifsHttpTest.java`

### E-GP-9 — La solution doit permettre de définir des exigences de présence préalable rémunérée avant un quart de travail

*essentiel · Gestion du "punch" · verdict : ACQUIS · lot U6*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/uspresence` — Profil de pointage : « Présence préalable rémunérée (minutes) » — le temps exigé avant le quart, et payé. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "uspresence",
  "prealable": [],
  "preuve": [
    "pla-pre",
    "pfl-panneau",
    "pfl-prealable"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 07-presence.feature — Julie pointe à la caserne : la preuve exigée par le milieu est satisfaite, la présence préalable compte, puis pause et sortie »

**Essais Java** : `ControleurPresenceEffectifsHttpTest.java`

### E-GP-10 — La solution doit permettre de valider que la déclaration de présence est effectuée à partir d’un emplacement conforme au milieu de travail associé au quart.

*essentiel · Gestion du "punch" · verdict : ACQUIS · lot U6*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usmilieux` — Coordonnées et rayon du milieu : un pointage hors du rayon est un écart d'emplacement. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usmilieux",
  "prealable": [
    {
      "cliquer": "mil-ajouter",
      "pourquoi": "Le rayon est un champ de la fiche du milieu."
    }
  ],
  "preuve": [
    "mil-fiche",
    "mil-form-latitude",
    "mil-form-longitude",
    "mil-form-rayon"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 07-presence.feature — Un écart de lieu déclenche l'action configurée — blocage au-delà de 5 km, mise en attente sans preuve — et l'encadrement décide, motif à l'appui »

**Essais Java** : `ControleurPresenceEffectifsHttpTest.java`

### E-GP-11 — La solution doit supporter un ou plusieurs mécanismes de validation de la présence physique à un milieu de travail identifié au quart de travail.

*essentiel · Gestion du "punch" · verdict : ACQUIS · lot U6*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usmilieux` — « Preuves » d'un milieu : code sur place, code QR, position ou plage réseau — un ou plusieurs mécanismes, par plage horaire. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usmilieux",
  "prealable": [
    {
      "cliquer": "mil-preuves-C12",
      "pourquoi": "Les preuves acceptées sont un tiroir ouvert depuis la ligne du milieu."
    }
  ],
  "preuve": [
    "mil-ecran",
    "mil-preuves",
    "mil-preuves-enregistrer"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 07-presence.feature — Julie pointe à la caserne : la preuve exigée par le milieu est satisfaite, la présence préalable compte, puis pause et sortie »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-presence.spec.ts`

**Essais Java** : `SocleEffectifsDomaineTest.java`, `ControleurPresenceEffectifsHttpTest.java`, `effectifs-presence.spec.ts`

### E-GP-12 — La solution doit permettre de configurer les exigences de validation de présence.

*essentiel · Gestion du "punch" · verdict : ACQUIS · lot U6*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usmilieux` — Les exigences de validation se règlent milieu par milieu et s'enregistrent — aucun code à toucher. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usmilieux",
  "prealable": [
    {
      "cliquer": "mil-preuves-C12",
      "pourquoi": "Le réglage vit dans le tiroir des preuves du milieu."
    }
  ],
  "preuve": [
    "mil-preuves",
    "mil-preuves-enregistrer"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 07-presence.feature — Julie pointe à la caserne : la preuve exigée par le milieu est satisfaite, la présence préalable compte, puis pause et sortie »

**Essais Java** : `ControleurSocleEffectifsHttpTest.java`

### E-GP-13 — La solution doit valider la cohérence entre le lieu de déclaration de présence et le milieu de travail associé au quart, et déclencher une action configurable en cas d’écart.

*essentiel · Gestion du "punch" · verdict : ACQUIS · lot U6*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/uspresence` — Un écart entre le lieu déclaré et le milieu du quart déclenche l'action configurée : ignorer, signaler ou bloquer. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "uspresence",
  "prealable": [],
  "preuve": [
    "pla-pre",
    "pla-pre-table"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 07-presence.feature — Un écart de lieu déclenche l'action configurée — blocage au-delà de 5 km, mise en attente sans preuve — et l'encadrement décide, motif à l'appui »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-presence.spec.ts`

**Essais Java** : `ControleurPresenceEffectifsHttpTest.java`, `effectifs-presence.spec.ts`

### E-GP-14 — La solution doit consigner les informations de localisation associées aux déclarations de présence et permettre leur audit

*essentiel · Gestion du "punch" · verdict : ACQUIS · lot U6*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/uspresence` — « Audit des pointages » : la localisation consignée se lit à l'écran et s'exporte en CSV. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "uspresence",
  "prealable": [],
  "preuve": [
    "pla-pre",
    "pla-pre-audit",
    "pla-pre-audit-charger"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 07-presence.feature — Un écart de lieu déclenche l'action configurée — blocage au-delà de 5 km, mise en attente sans preuve — et l'encadrement décide, motif à l'appui » ; « 07-presence.feature — Le site déclaré au pointage — un autre milieu que celui du quart — se lit dans la répartition de « ma présence » »

**Essais Java** : `ControleurPresenceEffectifsHttpTest.java`

### E-GP-15 — La solution doit permettre a un utilisateur autorisé de saisir des heures et des activitée à postérior

*essentiel · Gestion du "punch" · verdict : ACQUIS · lot U6*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/monpointage` — Saisie a posteriori : date, entrée, sortie, activité et justification, sous le profil qui l'autorise. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "monpointage",
  "prealable": [
    {
      "cliquer": "mpo-declarer-ouvrir",
      "pourquoi": "Le formulaire vit dans un dialogue depuis le 8 novembre 2026 : ses champs n'existent qu'ouvert."
    }
  ],
  "preuve": [
    "mpo-titre",
    "mpo-s-date",
    "mpo-s-entree",
    "mpo-s-sortie",
    "mpo-s-justification"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 07-presence.feature — Un quart non pointé se déclare après coup en plusieurs segments justifiés ; l'encadrement en saisit pour autrui ; le même temps ne se déclare pas deux fois » ; « 07-presence.feature — Une saisie après coup porte l'activité exercée ; une activité hors référentiel est refusée »

**Essais e2e qui la nomment** : `tests/ui/audit-wcag22.spec.ts`

**Essais Java** : `PresenceQualificationDomaineTest.java`, `RepartitionPresenceTest.java`, `ControleurPresenceEffectifsHttpTest.java`, `audit-wcag22.spec.ts`

### E-RF-1 — La solution doit préparer automatiquement la feuille de temps à partir des horaires et affectations.

*essentiel · Rapports et formulaires · verdict : ACQUIS · lot U7*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/ustemps` — « Préparer les feuilles » : la feuille de la période est bâtie depuis les quarts attribués, sans saisie. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "ustemps",
  "prealable": [],
  "preuve": [
    "pla-tps",
    "pla-tps-debut",
    "pla-tps-fin",
    "pla-tps-preparer"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 08-feuilles-de-temps.feature — La feuille se prépare depuis l'horaire : heures par type, heures supplémentaires, écart planifié-déclaré et anomalies affichées »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-temps.spec.ts`

**Essais Java** : `ControleurTempsEffectifsHttpTest.java`, `effectifs-temps.spec.ts`

### E-RF-2 — La solution doit permettre la saisie manuelle ou automatisée des données de temps dans la feuille de temps

*essentiel · Rapports et formulaires · verdict : ACQUIS · lot U7*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/matemps` — « Ajouter » un temps : l'employé saisit ce que le pointage n'a pas rempli ; le reste vient des quarts. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "matemps",
  "prealable": [],
  "preuve": [
    "mft-titre",
    "mft-feuille-detail",
    "mft-ajouter-ouvrir"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 08-feuilles-de-temps.feature — La saisie est jugée en temps réel ; une correction crée une version motivée ; les règles restreignent qui modifie quoi »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-temps.spec.ts`

**Essais Java** : `ControleurTempsEffectifsHttpTest.java`, `effectifs-temps.spec.ts`

### E-RF-3 — La solution doit permettre la validation en temps réel des données de feuille de temps.

*essentiel · Rapports et formulaires · verdict : ACQUIS · lot U7*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/matemps` — À l'enregistrement d'un segment, les anomalies et les totaux sont recalculés et réaffichés aussitôt. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "matemps",
  "prealable": [],
  "preuve": [
    "mft-titre",
    "mft-feuille-detail",
    "mft-segments"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 08-feuilles-de-temps.feature — La saisie est jugée en temps réel ; une correction crée une version motivée ; les règles restreignent qui modifie quoi »

**Essais Java** : `ControleurTempsEffectifsHttpTest.java`

### E-RF-4 — La solution doit permettre l’approbation des feuilles de temps selon des profils autorisés.

*essentiel · Rapports et formulaires · verdict : ACQUIS · lot U7*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/ustemps` — Ouvrir une feuille soumise et « Approuver le palier » — ou la retourner avec un motif. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "ustemps",
  "prealable": [],
  "preuve": [
    "pla-tps",
    "pla-tps-table",
    "pla-tps-filtre"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 08-feuilles-de-temps.feature — La feuille soumise se verrouille et s'approuve par paliers, deux personnes ; approuvée, elle part à la paie dans un lot structuré »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-temps.spec.ts`, `tests/e2e/helpers/urgences-sante.ts`

**Essais Java** : `ControleurTempsEffectifsHttpTest.java`, `effectifs-temps.spec.ts`, `urgences-sante.ts`

### E-RF-5 — La solution doit permettre l’évaluation automatique des heures supplémentaires.

*essentiel · Rapports et formulaires · verdict : ACQUIS · lot U7*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/ustemps` — Les seuils d'heures supplémentaires (jour, semaine) se règlent dans « Paramètres des feuilles » ; le total est reclassé d'office. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "ustemps",
  "prealable": [
    {
      "cliquer": "feuilles-parametres-ouvrir",
      "pourquoi": "Le formulaire vit dans un dialogue depuis le 8 novembre 2026 : ses champs n'existent qu'ouvert."
    }
  ],
  "preuve": [
    "feuilles-parametres-ouvrir",
    "pla-tps-p-seuilJourHeures",
    "pla-tps-p-seuilSemaineHeures"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 08-feuilles-de-temps.feature — La feuille se prépare depuis l'horaire : heures par type, heures supplémentaires, écart planifié-déclaré et anomalies affichées » ; « 08-feuilles-de-temps.feature — Une convention porte ses propres seuils, dont celui de la période de paie, et seule la feuille de ses personnes en suit le calcul »

**Essais Java** : `ControleurTempsEffectifsHttpTest.java`

### E-RF-6 — La solution doit permettre la distinction des types d’heures.

*essentiel · Rapports et formulaires · verdict : ACQUIS · lot U7*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usreferentiels` — Type « Types d'heures » : chaque type porte son comportement et son code de paie ; les totaux de la feuille sont ventilés par type. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usreferentiels",
  "prealable": [],
  "preuve": [
    "ref-ecran",
    "ref-type",
    "ref-tableau"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 08-feuilles-de-temps.feature — La feuille se prépare depuis l'horaire : heures par type, heures supplémentaires, écart planifié-déclaré et anomalies affichées »

**Essais Java** : `TempsEffectifsDomaineTest.java`

### E-RF-7 — La solution doit permettre la production de rapports opérationnels.

*essentiel · Rapports et formulaires · verdict : ACQUIS · lot U7*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/ustemps` — « Relevés » : choisir le regroupement et afficher le relevé opérationnel de la période. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "ustemps",
  "prealable": [],
  "preuve": [
    "pla-tps-rapports",
    "pla-tps-regroupement",
    "pla-tps-lire-rapport"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 08-feuilles-de-temps.feature — Les relevés se regroupent selon le paramètre choisi et s'exportent dans les formats standards » ; « 08-feuilles-de-temps.feature — L'organisme définit son propre relevé statistique — source, mesures, regroupement, période — l'enregistre et le reproduit »

**Essais Java** : `ControleurTempsEffectifsHttpTest.java`

### E-RF-8 — La solution doit permettre l’exportation des données dans des formats standards.

*essentiel · Rapports et formulaires · verdict : ACQUIS · lot U7*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/rapports` — Constructeur de rapports : choisir la source, les colonnes, le format (CSV, Excel, PDF, JSON) et exporter. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "rapports",
  "prealable": [],
  "preuve": [
    "rap-titre",
    "rap-source",
    "rap-format",
    "rap-exporter"
  ]
}
```

</details>

**Scénario par l'API C-09** (`urgences-sante-comportements.spec.ts`) — Le journal s'exporte en CSV, en-tête fixe : chaque geste humain ou automatique y est retrouvable

<details><summary>Données JSON de C-09 (1 étapes)</summary>

```json
[
  {
    "methode": "GET",
    "chemin": "/api/effectifs/journal?format=csv",
    "attendu": {
      "statut": 200,
      "texte": "^horodatage,origine,acteurId,action,objetType,objetId,uniteCode,avant,apres"
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 08-feuilles-de-temps.feature — Les relevés se regroupent selon le paramètre choisi et s'exportent dans les formats standards » ; « 09-journal.feature — Le journal s'exporte en CSV, en-tête fixe : chaque geste humain ou automatique y est retrouvable »

**Essais Java** : `ControleurSocleEffectifsHttpTest.java`, `comportements.json`

### E-RF-9 — La solution doit détecter automatiquement des anomalies de temps et de présence.

*essentiel · Rapports et formulaires · verdict : ACQUIS · lot U7*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/ustemps` — Les anomalies de temps et de présence sont détectées par règle au contexte « Feuille de temps » ; la fiche de la feuille les liste. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "ustemps",
  "prealable": [],
  "preuve": [
    "pla-tps",
    "pla-tps-table"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 08-feuilles-de-temps.feature — La feuille se prépare depuis l'horaire : heures par type, heures supplémentaires, écart planifié-déclaré et anomalies affichées »

**Essais Java** : `ControleurTempsEffectifsHttpTest.java`

### E-RF-10 — La solution doit permettre la configuration de règles de contrôle temporel.

*essentiel · Rapports et formulaires · verdict : ACQUIS · lot U3*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usregles` — Contexte « Feuille de temps » : les règles de contrôle temporel se créent, se simulent et se publient datées. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usregles",
  "prealable": [],
  "preuve": [
    "rgl-ecran",
    "rgl-contexte",
    "rgl-tableau"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 08-feuilles-de-temps.feature — La feuille se prépare depuis l'horaire : heures par type, heures supplémentaires, écart planifié-déclaré et anomalies affichées »

**Essais Java** : `FluxApprobationDomaineTest.java`

### E-RF-11 — La solution doit afficher clairement les anomalies dans la feuille de temps.

*essentiel · Rapports et formulaires · verdict : ACQUIS · lot U7*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/matemps` — Les anomalies sont affichées en tête de feuille, nommées, et dites bloquantes en mots — jamais par la seule couleur. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "matemps",
  "prealable": [],
  "preuve": [
    "mft-titre",
    "mft-feuille-detail",
    "mft-anomalies|mft-sans-anomalie"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 08-feuilles-de-temps.feature — La feuille se prépare depuis l'horaire : heures par type, heures supplémentaires, écart planifié-déclaré et anomalies affichées »

**Essais Java** : `ControleurTempsEffectifsHttpTest.java`

### E-RF-12 — La solution doit structurer les données de temps afin de permettre leur traitement adéquat par les systèmes de paie

*essentiel · Rapports et formulaires · verdict : ACQUIS · lot U8*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/ustemps` — « Lot de paie » : produire le lot des feuilles approuvées, codé par type d'heure, avec son empreinte SHA-256. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "ustemps",
  "prealable": [],
  "preuve": [
    "pla-tps-paie",
    "pla-tps-produire"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 08-feuilles-de-temps.feature — La feuille soumise se verrouille et s'approuve par paliers, deux personnes ; approuvée, elle part à la paie dans un lot structuré »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-temps.spec.ts`

**Essais Java** : `ControleurIntegrationsEffectifsHttpTest.java`, `ControleurTempsEffectifsHttpTest.java`, `effectifs-temps.spec.ts`

### E-RF-13 — La solution doit permettre de corréler des horodatages provenant de sources multiples (provenance de logiciels tiers) pour la validation de la présence et du temps.

*essentiel · Rapports et formulaires · verdict : ACQUIS · lot U6*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/ustemps` — Paramètres : ce que la feuille fait d'un pointage qu'aucune source tierce ne confirme, et de deux sources en désaccord. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "ustemps",
  "prealable": [
    {
      "cliquer": "feuilles-parametres-ouvrir",
      "pourquoi": "Le formulaire vit dans un dialogue depuis le 8 novembre 2026 : ses champs n'existent qu'ouvert."
    }
  ],
  "preuve": [
    "pla-tps",
    "pla-tps-p-actionNonCorrobore",
    "pla-tps-p-actionSourcesDivergentes"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 07-presence.feature — Les horodatages d'un logiciel tiers s'importent pour corroborer la présence, sans doublon au rejeu, lignes fautives nommées »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-temps-flux.spec.ts`, `tests/ui/audit-wcag22.spec.ts`

**Essais Java** : `CorroborationTempsDomaineTest.java`, `TempsEffectifsDomaineTest.java`, `ControleurPresenceEffectifsHttpTest.java`, `ControleurTempsFluxEffectifsHttpTest.java`, `effectifs-temps-flux.spec.ts`, `audit-wcag22.spec.ts`

### E-RF-14 — La solution doit permettre d'effectuer des modifications aux entrées déjà effectuées selon les profils autorisés.

*essentiel · Rapports et formulaires · verdict : ACQUIS · lot U7*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/matemps` — Modifier une entrée déjà saisie : le motif est exigé et l'historique des versions du segment reste lisible. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "matemps",
  "prealable": [],
  "preuve": [
    "mft-titre",
    "mft-feuille-detail",
    "mft-segments"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 08-feuilles-de-temps.feature — La saisie est jugée en temps réel ; une correction crée une version motivée ; les règles restreignent qui modifie quoi »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-temps.spec.ts`

**Essais Java** : `ControleurTempsEffectifsHttpTest.java`, `effectifs-temps.spec.ts`

### E-TN-1 — La solution est un logiciel-service de type infonuagique SaaS (Software as a Service) conforme à la définition SaaS suivante : La solution SaaS doit être composée d’un seul ensemble de lignes de codes partagées par tous les clients et ne doit nécessiter aucune programmation ni installation sur serveur du client.

*essentiel · Technologie · verdict : ACQUIS · lot U0*

> **Aucun scénario e2e** — exigence technologique : Une base de code multi-établissements : résolution de l'établissement (`ResolutionTenantTest`, `ResolveurTenantHoteTest`, ADR 0033), jeton de session lié à l'établissement (`PariteJetonTest`) ; image unique (ci-cd.yml)

### E-TN-2 — La solution authorise (authentification) les utilisateurs via la plateforme Microsoft Entra Id.

*essentiel · Technologie · verdict : ACQUIS · lot U0*

**Scénario par l'API T-08** (`urgences-sante-technologie.spec.ts`) — L'authentification Microsoft Entra ID est offerte par la page de connexion

<details><summary>Données JSON de T-08 (1 étapes)</summary>

```json
[
  {
    "methode": "GET",
    "chemin": "/api/auth/sso/entra/status",
    "attendu": {
      "statut": 200,
      "texte": "Entra"
    }
  }
]
```

</details>

### E-TN-3 — La solution SaaS doit doit bénéficier d’une paramétrisation supportant des mises à jour périodiques des fonctionnalités proposées par le fournisseur et rendue disponibles à tous les clients en même temps selon un calendrier d’évolution. Après un processus de validation, les correctifs et l’augmentation du niveau de stabilité et de sécurité peuvent être déployés rapidement.

*essentiel · Technologie · verdict : ACQUIS · lot U0*

**BDD** (Cucumber, contre le Java) : « 11-technologie.feature — L'organisme décide d'activer une fonctionnalité ; désactivée, elle se refuse en la nommant »

> **Aucun scénario e2e** — exigence technologique : Une image par version, pour tous les organismes (ci-cd.yml) ; fonctions nouvelles désactivées jusqu'à l'activation par organisme (`CatalogueModulesTest`) ; calendrier d'évolution, circuit de validation et notes de version (docs/urgences-sante/CALENDRIER-EVOLUTION-ET-MISES-A-JOUR.md, docs/notes-de-version/) ; déploiement sur le service ECS du Terraform AWS proposé (travail deploy-aws, rôle OIDC infra/terraform/aws/deploiement-ci.tf), dans la plage d'entretien ; fenêtres annoncées 72 h (`SoutienTest`)

### E-TN-4 — Le client peut décider d’activer des fonctionnalités nouvelles et des améliorations fonctionnelles.

*essentiel · Technologie · verdict : ACQUIS · lot U0*

**Scénario par l'API T-09** (`urgences-sante-technologie.spec.ts`) — Les fonctionnalités activées pour l'organisme se lisent au serveur, qui les garde en refus par défaut

<details><summary>Données JSON de T-09 (2 étapes)</summary>

```json
[
  {
    "methode": "GET",
    "chemin": "/api/saas/my-modules",
    "attendu": {
      "statut": 200,
      "texte": "effectifs_planification"
    }
  },
  {
    "pourquoi": "La paie n'est pas activée pour cet organisme : sa route est fermée",
    "acteur": "julie",
    "methode": "GET",
    "chemin": "/api/paie/mes-bulletins",
    "attendu": {
      "statut": 403
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 11-technologie.feature — L'organisme décide d'activer une fonctionnalité ; désactivée, elle se refuse en la nommant »

### E-TN-5 — La solution supporte des profils d'utilisateurs distincts.

*essentiel · Technologie · verdict : ACQUIS · lot U0*

**Scénario par l'API T-01** (`urgences-sante-technologie.spec.ts`) — Des profils distincts : l'administration lit les comptes, un employé et une gestionnaire non

<details><summary>Données JSON de T-01 (4 étapes)</summary>

```json
[
  {
    "methode": "GET",
    "chemin": "/api/auth/users",
    "attendu": {
      "statut": 200,
      "texte": "julie-{{suffixe}}"
    }
  },
  {
    "acteur": "julie",
    "methode": "GET",
    "chemin": "/api/auth/users",
    "attendu": {
      "statut": 403
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/droits",
    "attendu": {
      "statut": 200,
      "corps": {
        "envoyer": true,
        "configurer": false
      }
    }
  },
  {
    "acteur": "julie",
    "methode": "GET",
    "chemin": "/api/alertes/droits",
    "attendu": {
      "statut": 200,
      "corps": {
        "envoyer": false
      }
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 11-technologie.feature — Les profils d'utilisateurs sont distincts : l'employé, le gestionnaire et l'administration n'ont pas les mêmes gestes »

### E-TN-6 — Les données opérationnelles, sauvegardées et archivées sont séparées de celles des autres clients.

*essentiel · Technologie · verdict : ACQUIS · lot U0*

**BDD** (Cucumber, contre le Java) : « 11-technologie.feature — Les données d'un organisme sont séparées de celles d'un autre : ni lues, ni modifiables, ni même nommées »

> **Aucun scénario e2e** — exigence technologique : Données vivantes filtrées par organisme (`PariteJetonTest`, `ControleurSocleEffectifsHttpTest`) ; chaque sauvegarde complète donne une copie par organisme qui ne contient que ses lignes, téléchargeable par son seul administrateur (`CopiesOrganismesTest`, `ControleurCopiesOrganismeHttpTest`, écran Administration › Organisme, frontend/tests/copies-organisme.test.tsx) ; copies du volume chiffrées (infra/terraform/aws/stockage-applicatif.tf) ; destruction par organisme (`DestructionTest`). Les tables filles sans colonne d'organisme (23 sur 537, dont les tables globales) ne sont pas dans la copie : elle ne mêle jamais deux organismes, mais n'est pas une restitution exhaustive

### E-TN-7 — La solution s'intègre à d'autres applications utilisant un "Entreprise Service Bus" (ESB).

*essentiel · Technologie · verdict : ACQUIS · lot U8*

**BDD** (Cucumber, contre le Java) : « 11-technologie.feature — Le journal part au bus d'entreprise en événements, et à l'entrepôt de données en versements, à partir d'un curseur » ; « 11-technologie.feature — Les opérations transmettent une absence sur un quart réel par l'API d'échange ; le quart vacant est annoncé au webhook et paraît parmi les correctifs (AD-06c) »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-integrations.spec.ts`

### E-TN-8 — La solution garantit la confidentialité, liaison et disponibilité pour chaque type de transmission (exemples: HTTPS sur Internet, IPSec VPN , MPLS, extension LAN).

*essentiel · Technologie · verdict : PARTIEL · lot U0*

> **Aucun scénario e2e** — exigence technologique : VPN décrit mais jamais monté avec un équipement de l'organisme ; répartiteur → tâches en HTTP dans le VPC ; STARTTLS SMTP opportuniste ; MPLS et SFTP absents ; aucun balayage TLS d'un environnement en service.

### E-TN-9 — L’adjudicataire devra assurer la haute disponibilité et la fiabilité de la Solution pour supporter une exploitation sur une base de 20 heures / 7 jours / 365 jours.

*essentiel · Technologie · verdict : PARTIEL · lot U0*

> **Aucun scénario e2e** — exigence technologique : Addenda 06 : 24 h / 7 j / 365 j à 99,7 %, 2 h 10 min d'indisponibilité par mois hors maintenance annoncée. SQLite n'admet qu'un écrivain : une tâche unique, et la disponibilité repose sur son remplacement (minutes), pas sur deux tâches actives ; jamais déployé ni éprouvé par un essai de bascule ; NAT unique.

### E-TN-10 — Le support technique pour la solution doit être disponible en français.

*essentiel · Technologie · verdict : ACQUIS · lot U0*

> **Aucun scénario e2e** — exigence technologique : Module soutien en français (docs/SOUTIEN-BILLETS.md) : `ControleurSoutienHttpTest`, `ControleurSoutienPiecesHttpTest`

### E-TN-11 — Le fournisseur doit être en mesure d'assurer le support technique de la solution durant les heures ouvrables de l'Organisation, soit de 6h00 le matin à 20h00 le soir, du lundi au vendredi selon le fuseau horaire UTC-05:00.

*essentiel · Technologie · verdict : HORS_CODE*

> **Aucun scénario e2e** — exigence technologique : Équipe de soutien de 5 h à 23 h, 7 j/7, 365 j, qui tient les délais de 5.02.02 : dotation, pas logiciel.

### E-TN-12 — L’adjudicataire s’engage à fournir les procédures de tests d'acceptation pour démontrer que les spécifications de performance et les fonctionnalités décrites dans ce devis sont remplies et respectées.

*essentiel · Technologie · verdict : ACQUIS · lot U0*

> **Aucun scénario e2e** — exigence technologique : Procédure d'essais d'acceptation rédigée pour Urgences-santé et cahier de 90 cas généré depuis la recette, chacun avec l'essai automatisé qui le couvre (docs/urgences-sante/PROCEDURE-ESSAIS-ACCEPTATION.md, construire-procedure-acceptation.py) ; cas de performance renvoyant aux plans JMeter et aux SLO (perf/jmeter) ; 13 suites e2e jouées contre le Java (backend-java/scripts/campagne-e2e.sh)

### E-TN-13 — Une documentation du métamodèle de données est fournie avec la solution.

*essentiel · Technologie · verdict : ACQUIS · lot U0*

> **Aucun scénario e2e** — exigence technologique : Dictionnaire de données et métamodèle générés depuis le schéma réel (base sur laquelle le Java a démarré) : docs/donnees/DICTIONNAIRE-DONNEES.md et docs/donnees/metamodele.json, par backend-java/scripts/dictionnaire-donnees.py (mode --verifier pour la CI) ; migrations versionnées ; DDL identique Node/Java (R13)

### E-TN-14 — La solution doit être mise à jour sur une base fréquente et régulière par le fournisseur pour se conformer aux exigences législatives et de cybersécurité.

*essentiel · Technologie · verdict : ACQUIS · lot U0*

> **Aucun scénario e2e** — exigence technologique : Mises à jour de dépendances proposées chaque semaine (.github/dependabot.yml) ; balayage hebdomadaire des vulnérabilités, porte npm audit, DAST OWASP ZAP (ci-cd.yml) ; calendrier des mises à jour législatives (revue trimestrielle) et de cybersécurité (docs/urgences-sante/CALENDRIER-EVOLUTION-ET-MISES-A-JOUR.md) ; registre d'incidents (ADR 0020)

### E-TN-15 — Le fournisseur doit fournir l'information relative aux sujets des copies de sécurité des données de la solution et de la période de conservation de ces copies de sécurité.

*essentiel · Technologie · verdict : ACQUIS · lot U0*

> **Aucun scénario e2e** — exigence technologique : Information alignée sur l'hébergement AWS proposé : sujets, fréquences, conservation (quotidiennes 35 j, mensuelles 6 ans = année courante + 5 ans), lieu, protection et essais (docs/urgences-sante/COPIES-DE-SECURITE.md) ; les mêmes chiffres dans le code et vérifiés au plan (aws/stockage-applicatif.tf, objectifs-de-reprise.tf) ; sauvegardes applicatives aux 15 minutes (`SauvegardesTest`)

### E-TN-16 — La solution offre des mécanismes de retour en arrière (Rollback). Un maximum de 24 heures est requis.

*essentiel · Technologie · verdict : ACQUIS · lot U0*

> **Aucun scénario e2e** — exigence technologique : Retour à une version antérieure par son empreinte (entrée « version » de ci-cd.yml, travail deploy-aws) ; disjoncteur de déploiement ECS qui reprend seul la révision précédente (aws/calcul.tf) ; données sur volume persistant, restaurables depuis AWS Backup ou les sauvegardes applicatives (exercice de reprise : `ControleurExploitationHttpTest`) ; procédure : docs/urgences-sante/CALENDRIER-EVOLUTION-ET-MISES-A-JOUR.md § 5

### E-TN-17 — Les fonctionnalités transactionnelles de base doivent s'exécuter à l'intérieur de 2 secondes.

*essentiel · Technologie · verdict : PARTIEL · lot U0*

**BDD** (Cucumber, contre le Java) : « 11-technologie.feature — Les transactions de base s'exécutent en moins de deux secondes »

> **Aucun scénario e2e** — exigence technologique : Plans JMeter des effectifs générés (148 routes) et six transactions de base au catalogue de l'accord (doc 09), mais mesurés sur le Node, pas sur le Java ; écritures non mesurées (45 corps refusés en 422) ; pointage non mesuré (machine à états rejouée sur la même place) ; charge contractuelle (1 100 usagers, pointe de 500 pointages) non rejouée sur une machine au repos.

### E-TN-18 — Le fournisseur doit prévoir un environnement de formation pour les utilisateurs de l'Organisation.

*essentiel · Technologie · verdict : ACQUIS · lot U0*

> **Aucun scénario e2e** — exigence technologique : Environnement de tests et formation d'Urgences-santé (Contrat 2.05 a) décrit par le même code que la production : aws/urgences-sante-essai.tfvars, client paramétré (aws/variables.tf, `terraform validate`) ; organisme et comptes de formation semés par l'API (docs/urgences-sante/COMPTES-DEMONSTRATION.md) ; simulateurs des connecteurs

### E-TN-19 — La solution doit provenir d'un fournisseur hébergé au QUÉBEC

*essentiel · Technologie · verdict : ACQUIS · lot U0*

> **Aucun scénario e2e** — exigence technologique : Addenda 06 : l'exigence vise désormais le CANADA, et AWS ca-central-1 y répond. Reste une décision d'entreprise : passer l'ADR 0043 de « proposé » à « accepté ».

### E-TN-20 — La solution offre des interfaces de communication Open-API version 3 ou supérieure afin de partager les données qu'elle contient avec le reste du parc applicatif d’Urgences-santé.

*essentiel · Technologie · verdict : ACQUIS · lot U0*

**Scénario par l'API T-02** (`urgences-sante-technologie.spec.ts`) — L'interface de communication est décrite en OpenAPI 3, et réservée aux comptes authentifiés

<details><summary>Données JSON de T-02 (1 étapes)</summary>

```json
[
  {
    "methode": "GET",
    "chemin": "/api/integrations/openapi.json",
    "attendu": {
      "statut": 200,
      "texte": "\"openapi\"\\s*:\\s*\"3\\."
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 11-technologie.feature — L'API se décrit en OpenAPI 3, pour un compte authentifié »

### E-TN-21 — La solution doit avoir une interface en français.

*essentiel · Technologie · verdict : ACQUIS · lot U0*

**Scénario par l'API T-03** (`urgences-sante-technologie.spec.ts`) — Les refus parlent français et disent quoi corriger

<details><summary>Données JSON de T-03 (2 étapes)</summary>

```json
[
  {
    "methode": "PUT",
    "chemin": "/api/organisme/charte",
    "attendu": {
      "statut": 422,
      "texte": "« nomAffiche » : de 1 à 60 caractères"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/organisme/alertes",
    "corps": {
      "evenement": "TOUT",
      "destinataires": [
        "a@b.ca"
      ]
    },
    "attendu": {
      "statut": 422
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 11-technologie.feature — Les réponses de l'API sont en français, y compris les refus »

### E-TN-22 — La solution prend en charge un rendu adaptatif sur les ordinateurs de bureau, les ordinateurs portables et les appareils mobiles suivants ainsi que sur les dernières versions des principaux navigateurs web Google Chrome et Microsoft Edge.

*essentiel · Technologie · verdict : ACQUIS · lot U0*

> **Aucun scénario e2e** — exigence technologique : e2e/tests/ui/audit-wcag22.spec.ts : 390/768/1280/1920 px, écrans effectifs inclus ; moteur Chromium (Chrome/Edge) en CI ; projet msedge (E2E_EDGE=1) ; application mobile Expo

### E-TN-23 — La solution offre une protection contre le déni de service (DDoS).

*essentiel · Technologie · verdict : PARTIEL · lot U0*

> **Aucun scénario e2e** — exigence technologique : Shield Advanced non souscrit par défaut (3 000 USD par mois, engagement d'un an : décision commerciale) ; Bot Control désactivé ; règles WAF et alarme non éprouvées par un essai de charge contre un déploiement réel.

### E-TN-24 — La solution fournit une détection d'intrusion.

*essentiel · Technologie · verdict : ACQUIS · lot U0*

> **Aucun scénario e2e** — exigence technologique : GuardDuty (journaux de flux, DNS, appels à l'API) et règle de découverte de gravité ≥ 7 vers le sujet d'alertes (aws/detection.tf, `terraform validate`) ; signatures WAF (injection, traversée) et verrou de connexion : `PareFeuTest`, `FiltrePareFeuTest` ; console de sécurité (`ControleurSecuriteHttpTest`) et export SIEM (`SiemTest`)

### E-TN-25 — Les services professionnels sont disponibles en français.

*essentiel · Technologie · verdict : HORS_CODE*

> **Aucun scénario e2e** — exigence technologique : Services professionnels en français : engagement de l'entreprise.

### E-TN-26 — La documentation et les contenus de formation sont disponibles en français.

*essentiel · Technologie · verdict : ACQUIS · lot U0*

> **Aucun scénario e2e** — exigence technologique : Trousse de formation en neuf modules et capsules, éditable (docs/urgences-sante/TROUSSE-FORMATION.md) ; aide en ligne de tous les écrans d'effectifs, ouverte par « Aide sur cet écran » (frontend/src/Help.tsx, aideParEcran.ts) ; interface et documentation en français

### E-TN-27 — La documentation destinée aux administrateurs utilisant la console d'administration, des flux d'affaires, des gabarits de messages et des rapports est disponible.

*essentiel · Technologie · verdict : ACQUIS · lot U0*

> **Aucun scénario e2e** — exigence technologique : Guide d'administration consolidé des effectifs : rôles, modules, paramétrage, flux d'approbation, avis et gabarits, journal et exports, rapports, tâches planifiées, index réglage → menu → route (docs/urgences-sante/GUIDE-ADMINISTRATION-EFFECTIFS.md) ; routes citées vérifiées contre les contrôleurs Java

### E-TN-28 — L’intégrateur est en mesure de présenter au moins trois (3) projets de référence en configuration de la solution (remplir les 3 fiches de référence) terminés en date de la publication de cet appel d’offre.

*essentiel · Technologie · verdict : HORS_CODE*

> **Aucun scénario e2e** — exigence technologique : Trois projets de référence terminés de configuration de la solution : fait commercial, éliminatoire s'il manque.

### E-TN-29 — L'intégrateur est en mesure de présenter les coordonnées (nom, prénom, numéro de téléphone, courriel, nom de l'entreprise) d'au moins trois (3) clients informés et autorisant une prise de référence par l'équipe d'approvisionnement du client.

*essentiel · Technologie · verdict : HORS_CODE*

> **Aucun scénario e2e** — exigence technologique : Trois clients acceptant d'être contactés : fait commercial, éliminatoire s'il manque.

### E-TN-30 — L'intégrateur propose un service de soutien aux mises à jour SaaS inclus dans la proposition pour la durée du contrat.

*essentiel · Technologie · verdict : HORS_CODE*

> **Aucun scénario e2e** — exigence technologique : Service de soutien aux mises à jour inclus : engagement contractuel.

### E-TN-31 — Le fournisseur est responsable du paramétrage de la solution et ce paramétrage sera conforme aux exigences du ministère de la cybersécurité et du numérique documenté dans la Liste de vérification de la conformité de la zone d’accueil du nuage externe avant le déploiement des charges de travail, et ce, de concert avec l'équipe de l'organisation. Le paramétrage de la solution consiste à configurer la solution, selon les besoins, les requis, les exigences fonctionnelles, de sécurité applicative et technologique. Le paramétrage comprend aussi les accès sécurisés à la solution, aux rapports de sécurité ainsi que la sécurité des journaux transmis, sauvegardés et gérés par la solution.

*essentiel · Technologie · verdict : PARTIEL · lot U0*

> **Aucun scénario e2e** — exigence technologique : La liste de vérification de la zone d'accueil du MCN n'est pas jointe à l'avis : sa correspondance ligne à ligne reste à faire avec l'organisation avant le déploiement.

### P-GN-1 — La solution doit permettre l’attribution automatique d’un quart à la ressource prioritaire selon les règles applicables.

*préférentiel · Généralités · verdict : ACQUIS · lot U5*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usabsences` — Sur un quart au mode automatisé, le comblement attribue d'office à la ressource prioritaire que les règles acceptent. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usabsences",
  "prealable": [],
  "preuve": [
    "pla-abs",
    "pla-abs-table"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 04-absences.feature — Après une dérogation, le quart automatisé va au prioritaire disponible et le quart manuel est offert aux admissibles, avisés » ; « 04-absences.feature — En mode par ancienneté et à tour de rôle, le quart libéré est offert à un rang à la fois ; un refus passe au suivant, qui l'accepte (AD-07) »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-absences.spec.ts`

**Essais Java** : `ControleurAbsencesEffectifsHttpTest.java`, `effectifs-absences.spec.ts`

### P-GN-2 — La solution doit valider automatiquement l’admissibilité des absences et les restrictions applicables.

*préférentiel · Généralités · verdict : ACQUIS · lot U5*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usregles` — Vue « Simulateur » : évaluer une situation d'absence et lire le verdict des règles avant d'agir. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usregles",
  "prealable": [
    {
      "cliquer": "rgl-vue-simulateur",
      "pourquoi": "Le simulateur est une vue secondaire de l'écran des règles."
    }
  ],
  "preuve": [
    "rgl-simulateur",
    "rgl-sim-unite",
    "rgl-sim-date",
    "rgl-sim-evaluer"
  ]
}
```

</details>

**Scénario par l'API C-06** (`urgences-sante-comportements.spec.ts`) — Une absence qui ferait tomber le comblement est refusée d'office, et le refus est conservé avec son motif

<details><summary>Données JSON de C-06 (9 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes",
    "corps": {
      "nom": "ABS",
      "uniteCode": "EST",
      "debut": "2030-03-04",
      "fin": "2030-03-10"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "abs": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes/{{abs}}/etat",
    "corps": {
      "etat": "CONFECTION"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
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
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "placeLundi": "places.0.id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/places/{{placeLundi}}/attribuer",
    "corps": {
      "ressourceId": "{{P1}}"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
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
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "placeMardi": "places.0.id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/places/{{placeMardi}}/attribuer",
    "corps": {
      "ressourceId": "{{P1}}"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/regles",
    "corps": {
      "contexte": "ABSENCE",
      "cle": "comblement-minimal",
      "condition": {
        "fait": "tauxComblementApres",
        "op": "<",
        "valeur": 60
      },
      "action": "REFUSER",
      "message": "Le comblement tomberait à {tauxComblementApres} %",
      "effectifDepuis": "2027-01-01"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/effectifs/absences",
    "corps": {
      "motif": "MAL",
      "du": "2030-03-04",
      "au": "2030-03-05",
      "commentaire": "Grippe"
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "statut": "REFUSEE_AUTO",
        "placesLiberees": 0
      },
      "texte": "Le comblement tomberait à 0 %"
    },
    "capturer": {
      "refusee": "ids.0"
    }
  },
  {
    "pourquoi": "L'impact se lit avant de décider : le taux avant et après",
    "methode": "GET",
    "chemin": "/api/effectifs/absences/{{refusee}}/impact",
    "attendu": {
      "statut": 200,
      "corps": {
        "tauxAvant": 100,
        "tauxApres": 0
      }
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 04-absences.feature — Une absence qui ferait tomber le comblement est refusée d'office, et le refus est conservé avec son motif »

**Essais Java** : `ControleurAbsencesEffectifsHttpTest.java`, `comportements.json`

### P-GN-3 — La solution doit accepter automatiquement une absence lorsque l’ensemble des règles applicables sont respectées.

*préférentiel · Généralités · verdict : ACQUIS · lot U5*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/mesabsences` — Déclarer une absence qui respecte toutes les règles : elle est accordée sans décision humaine, et l'écran le dit. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "mesabsences",
  "prealable": [],
  "preuve": [
    "mab-titre",
    "mab-declarer",
    "mab-table"
  ]
}
```

</details>

**Scénario par l'API C-08** (`urgences-sante-comportements.spec.ts`) — Une absence conforme est acceptée d'office ; un code réservé à un titre d'emploi est refusé à qui ne le porte pas

<details><summary>Données JSON de C-08 (2 étapes)</summary>

```json
[
  {
    "acteur": "marc",
    "methode": "POST",
    "chemin": "/api/effectifs/absences",
    "corps": {
      "motif": "MAL",
      "du": "2030-03-08"
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "statut": "ACCEPTEE"
      }
    }
  },
  {
    "acteur": "marc",
    "methode": "POST",
    "chemin": "/api/effectifs/absences",
    "corps": {
      "motif": "FORM",
      "du": "2030-04-01"
    },
    "attendu": {
      "statut": 422
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 04-absences.feature — Une absence conforme est acceptée d'office ; un code réservé à un titre d'emploi est refusé à qui ne le porte pas »

**Essais Java** : `AbsencesEffectifsDomaineTest.java`, `comportements.json`

### P-GH-1 — La solution doit permettre aux employés la déclaration d’absences ponctuelles ou planifiées paramétrable selon le titre d'emploi

*préférentiel · Gestion des horaires · verdict : ACQUIS · lot U5*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/mesabsences` — Un code « jour même seulement » n'est pas proposé à l'avance ; déclaré d'avance, le refus dit quel code choisir. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "mesabsences",
  "prealable": [],
  "preuve": [
    "mab-titre",
    "mab-declarer"
  ]
}
```

</details>

**Scénario par l'API C-08** (`urgences-sante-comportements.spec.ts`) — Une absence conforme est acceptée d'office ; un code réservé à un titre d'emploi est refusé à qui ne le porte pas

<details><summary>Données JSON de C-08 (2 étapes)</summary>

```json
[
  {
    "acteur": "marc",
    "methode": "POST",
    "chemin": "/api/effectifs/absences",
    "corps": {
      "motif": "MAL",
      "du": "2030-03-08"
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "statut": "ACCEPTEE"
      }
    }
  },
  {
    "acteur": "marc",
    "methode": "POST",
    "chemin": "/api/effectifs/absences",
    "corps": {
      "motif": "FORM",
      "du": "2030-04-01"
    },
    "attendu": {
      "statut": 422
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 04-absences.feature — Une absence conforme est acceptée d'office ; un code réservé à un titre d'emploi est refusé à qui ne le porte pas »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-mes-absences.spec.ts`

**Essais Java** : `ControleurAbsencesEffectifsHttpTest.java`, `comportements.json`, `effectifs-mes-absences.spec.ts`

### P-GH-2 — La solution doit valider automatiquement la conformité des absences déclarées.

*préférentiel · Gestion des horaires · verdict : ACQUIS · lot U5*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/mesabsences` — La déclaration est jugée à la saisie : préavis, absences simultanées et taux de comblement sont évalués sans geste d'encadrement. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "mesabsences",
  "prealable": [],
  "preuve": [
    "mab-titre",
    "mab-table"
  ]
}
```

</details>

**Scénario par l'API C-06** (`urgences-sante-comportements.spec.ts`) — Une absence qui ferait tomber le comblement est refusée d'office, et le refus est conservé avec son motif

<details><summary>Données JSON de C-06 (9 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes",
    "corps": {
      "nom": "ABS",
      "uniteCode": "EST",
      "debut": "2030-03-04",
      "fin": "2030-03-10"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "abs": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/periodes/{{abs}}/etat",
    "corps": {
      "etat": "CONFECTION"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
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
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "placeLundi": "places.0.id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/places/{{placeLundi}}/attribuer",
    "corps": {
      "ressourceId": "{{P1}}"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/equipages",
    "corps": {
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
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "placeMardi": "places.0.id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/planification/places/{{placeMardi}}/attribuer",
    "corps": {
      "ressourceId": "{{P1}}"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/effectifs/regles",
    "corps": {
      "contexte": "ABSENCE",
      "cle": "comblement-minimal",
      "condition": {
        "fait": "tauxComblementApres",
        "op": "<",
        "valeur": 60
      },
      "action": "REFUSER",
      "message": "Le comblement tomberait à {tauxComblementApres} %",
      "effectifDepuis": "2027-01-01"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/effectifs/absences",
    "corps": {
      "motif": "MAL",
      "du": "2030-03-04",
      "au": "2030-03-05",
      "commentaire": "Grippe"
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "statut": "REFUSEE_AUTO",
        "placesLiberees": 0
      },
      "texte": "Le comblement tomberait à 0 %"
    },
    "capturer": {
      "refusee": "ids.0"
    }
  },
  {
    "pourquoi": "L'impact se lit avant de décider : le taux avant et après",
    "methode": "GET",
    "chemin": "/api/effectifs/absences/{{refusee}}/impact",
    "attendu": {
      "statut": 200,
      "corps": {
        "tauxAvant": 100,
        "tauxApres": 0
      }
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 04-absences.feature — Une absence qui ferait tomber le comblement est refusée d'office, et le refus est conservé avec son motif »

**Essais Java** : `ControleurAbsencesEffectifsHttpTest.java`, `comportements.json`

### P-GH-3 — La solution doit notifier les ressources admissibles à un quart disponible.

*préférentiel · Gestion des horaires · verdict : ACQUIS · lot U5*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usabsences` — Une place offerte au comblement avise les ressources admissibles ; l'écran dit à qui, par quel canal, et ce que le canal a rendu. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usabsences",
  "prealable": [],
  "preuve": [
    "pla-abs",
    "pla-abs-table"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 04-absences.feature — Après une dérogation, le quart automatisé va au prioritaire disponible et le quart manuel est offert aux admissibles, avisés » ; « 04-absences.feature — En mode par ancienneté et à tour de rôle, le quart libéré est offert à un rang à la fois ; un refus passe au suivant, qui l'accepte (AD-07) »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-absences.spec.ts`

**Essais Java** : `ControleurAbsencesEffectifsHttpTest.java`, `effectifs-absences.spec.ts`

### P-GH-4 — La solution doit attribuer automatiquement un quart de travail disponible à un employé ayant émis une disponibilité correspondante au quart de travail à la fin d'une période de planification et de récupéraiton des disponibilitées déterminée

*préférentiel · Gestion des horaires · verdict : ACQUIS · lot U3*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usdisponibilites` — À la fin de la collecte, l'attribution depuis les disponibilités part d'elle-même ; avant la confection, l'écran dit qu'elle attend. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usdisponibilites",
  "prealable": [],
  "preuve": [
    "pla-dispo",
    "pla-dispo-depots"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 03-disponibilites.feature — Ouvrir la collecte planifie sa fin ; à la fin, les quarts vacants vont automatiquement à qui a déposé la disponibilité correspondante »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-agph.spec.ts`, `tests/e2e/effectifs-disponibilites.spec.ts`, `tests/ui/audit-wcag22.spec.ts`

**Essais Java** : `DisponibilitesDomaineTest.java`, `ControleurAgphEffectifsHttpTest.java`, `ControleurDisponibilitesEffectifsHttpTest.java`, `effectifs-agph.spec.ts`, `effectifs-disponibilites.spec.ts`, `audit-wcag22.spec.ts`

### P-CH-1 — La solution doit permettre à un employé admissible de visualiser les postes encore disponibles à son tour de rôle déterminé par les règles de convention collective.

*préférentiel · Confection des horaires · verdict : ACQUIS · lot U4*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/monagph` — À son tour de rôle, l'employé admissible voit les postes encore libres de sa campagne. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "monagph",
  "prealable": [],
  "preuve": [
    "agm-titre",
    "prefixe:agm-campagne-"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 06-agph.feature — Une campagne AGPH complète — dépôt, tour de rôle, révision, clôture — puis la gestion régulière reprend les postes libres »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-agph.spec.ts`

**Essais Java** : `ControleurAgphEffectifsHttpTest.java`, `effectifs-agph.spec.ts`

### P-CH-2 — La solution doit permettre à un employé de sélectionner un seul poste dans un délai défini lors de son tour de rôle.

*préférentiel · Confection des horaires · verdict : ACQUIS · lot U4*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/monagph` — Un seul poste se choisit, dans le délai du tour de rôle ; passé le délai, le tour avance. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "monagph",
  "prealable": [],
  "preuve": [
    "agm-titre",
    "prefixe:agm-campagne-"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 06-agph.feature — Une campagne AGPH complète — dépôt, tour de rôle, révision, clôture — puis la gestion régulière reprend les postes libres » ; « 06-agph.feature — À son tour, un employé renonce à tout poste ; le tour passe, et le traitement du délai échu se règle par campagne »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-agph.spec.ts`

**Essais Java** : `ControleurAgphEffectifsHttpTest.java`, `effectifs-agph.spec.ts`

### P-CH-3 — La solution doit empêcher toute sélection de poste non admissible selon les règles applicables.

*préférentiel · Confection des horaires · verdict : ACQUIS · lot U4*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/monagph` — Un poste que les règles écartent n'est pas sélectionnable, et l'écran dit pourquoi. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "monagph",
  "prealable": [],
  "preuve": [
    "agm-titre",
    "prefixe:agm-campagne-"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 06-agph.feature — Une campagne AGPH complète — dépôt, tour de rôle, révision, clôture — puis la gestion régulière reprend les postes libres »

**Essais Java** : `ControleurAgphEffectifsHttpTest.java`

### P-CH-4 — La solution doit permettre l’attribution automatisée des postes selon le tour de rôle.

*préférentiel · Confection des horaires · verdict : ACQUIS · lot U4*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usagph` — « Démarrer » la campagne : les postes s'attribuent d'office selon le tour de rôle, rang par rang. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usagph",
  "prealable": [],
  "preuve": [
    "pla-agph",
    "pla-agph-rangs"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 06-agph.feature — À son tour, un employé renonce à tout poste ; le tour passe, et le traitement du délai échu se règle par campagne »

**Essais Java** : `ControleurAgphEffectifsHttpTest.java`

### P-CH-5 — La solution doit attribuer le statut temps complet (TC) aux employés ayant obtenu un poste de type temps complet (TC)

*préférentiel · Confection des horaires · verdict : ACQUIS · lot U4*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usagph` — Un employé qui obtient un poste à temps complet prend le statut TC ; le tableau de la campagne le montre. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usagph",
  "prealable": [],
  "preuve": [
    "pla-agph",
    "pla-agph-etat"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 06-agph.feature — Une campagne AGPH complète — dépôt, tour de rôle, révision, clôture — puis la gestion régulière reprend les postes libres »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-agph.spec.ts`

**Essais Java** : `AgphDomaineTest.java`, `ControleurAgphEffectifsHttpTest.java`, `effectifs-agph.spec.ts`

### P-CH-6 — La solution doit attribuer le statut temps partiel régulier (TPR) aux employés ayant obtenu un poste de type temps partiel régulier (TPR)

*préférentiel · Confection des horaires · verdict : ACQUIS · lot U4*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usagph` — Créer la campagne pour une unité et un corps d'emploi : le statut posé à la clôture suit le type du poste obtenu. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usagph",
  "prealable": [
    {
      "cliquer": "pla-agph-creer",
      "pourquoi": "L'unité et le corps d'emploi de la campagne se choisissent dans le tiroir de création."
    }
  ],
  "preuve": [
    "pla-agph-creation",
    "pla-agph-unite",
    "pla-agph-corps"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 06-agph.feature — Une campagne AGPH complète — dépôt, tour de rôle, révision, clôture — puis la gestion régulière reprend les postes libres »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-agph.spec.ts`

**Essais Java** : `ControleurAgphEffectifsHttpTest.java`, `effectifs-agph.spec.ts`

### P-CH-7 — La solution doit attribuer le statut temps partiel aux employés n’ayant pas obtenu de poste.

*préférentiel · Confection des horaires · verdict : ACQUIS · lot U4*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usagph` — « Clore et poser les statuts », une fois l'attribution faite : les employés sans poste gardent le temps partiel occasionnel. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usagph",
  "prealable": [],
  "preuve": [
    "pla-agph",
    "pla-agph-campagne",
    "pla-agph-etat"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 06-agph.feature — Une campagne AGPH complète — dépôt, tour de rôle, révision, clôture — puis la gestion régulière reprend les postes libres » ; « 06-agph.feature — À son tour, un employé renonce à tout poste ; le tour passe, et le traitement du délai échu se règle par campagne »

**Essais Java** : `AgphDomaineTest.java`, `ControleurAgphEffectifsHttpTest.java`

### P-CH-8 — La solution doit permettre l’application de codes d’absence planifiés de moyen ou long terme.

*préférentiel · Confection des horaires · verdict : ACQUIS · lot U5*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usabsences` — Une absence de moyen ou long terme réserve ses places et ouvre un remplacement long terme qui leur est lié. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usabsences",
  "prealable": [],
  "preuve": [
    "pla-abs",
    "pla-abs-table"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 04-absences.feature — Une absence de long terme réserve ses places et ouvre un remplacement long terme qui lui est lié » ; « 04-absences.feature — Le seuil du code décide du remplacement long terme, sauf pendant une période horaire en cours »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-absences.spec.ts`

**Essais Java** : `ControleurAbsencesEffectifsHttpTest.java`, `effectifs-absences.spec.ts`

### P-CH-9 — La solution doit attribuer automatiquement les remplacements long terme selon les règles définies.

*préférentiel · Confection des horaires · verdict : ACQUIS · lot U4*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/usagph` — « Ouvrir un remplacement » puis, à la date limite, l'attribution aux intéressés selon les règles. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usagph",
  "prealable": [],
  "preuve": [
    "pla-rlt-table",
    "pla-rlt-ouvrir"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 06-agph.feature — Un remplacement long terme s'ouvre, reçoit les manifestations d'intérêt et s'attribue au plus ancien des intéressés »

**Essais Java** : `AgphDomaineTest.java`, `ControleurAgphEffectifsHttpTest.java`

### P-GP-1 — La solution doit permettre aux employés la déclaration des heures de travail de manière asynchrone (postériorie) selon des profils configurables

*préférentiel · Gestion du "punch" · verdict : ACQUIS · lot U6*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/monpointage` — « Déclarer un temps » : l'employé saisit après coup, dans la fenêtre que son profil autorise. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "monpointage",
  "prealable": [
    {
      "cliquer": "mpo-declarer-ouvrir",
      "pourquoi": "Le formulaire vit dans un dialogue depuis le 8 novembre 2026 : ses champs n'existent qu'ouvert."
    }
  ],
  "preuve": [
    "mpo-titre",
    "mpo-declarer-ouvrir",
    "mpo-s-envoyer"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 07-presence.feature — Un quart non pointé se déclare après coup en plusieurs segments justifiés ; l'encadrement en saisit pour autrui ; le même temps ne se déclare pas deux fois » ; « 07-presence.feature — Le profil de pointage décide du mode et des pauses admises ; retiré, il rend le défaut »

**Essais Java** : `ProfilsPointageTest.java`, `ControleurPresenceEffectifsHttpTest.java`

### P-GP-2 — La solution doit valider les heures saisies selon les règles d’affaires applicables au quart de travail

*préférentiel · Gestion du "punch" · verdict : ACQUIS · lot U6*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/uspresence` — Les heures saisies sont jugées par les règles du quart : la fiche du pointage liste les motifs retenus. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "uspresence",
  "prealable": [],
  "preuve": [
    "pla-pre",
    "pla-pre-table"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 07-presence.feature — Un écart de lieu déclenche l'action configurée — blocage au-delà de 5 km, mise en attente sans preuve — et l'encadrement décide, motif à l'appui »

**Essais Java** : `ControleurPresenceEffectifsHttpTest.java`

### P-GP-3 — La solution doit permettre la saisie manuelle de plusieurs segments de temps pour un quart exceptionnel.

*préférentiel · Gestion du "punch" · verdict : ACQUIS · lot U6*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/monpointage` — Déclarer un temps avec une pause : la nature de la période non payée est exigée dès que la pause est bornée — un segment, puis un autre, sur le même quart. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "monpointage",
  "prealable": [
    {
      "cliquer": "mpo-declarer-ouvrir",
      "pourquoi": "Le formulaire vit dans un dialogue depuis le 8 novembre 2026 : ses champs n'existent qu'ouvert."
    },
    {
      "remplir": "mpo-s-pause-debut",
      "valeur": "12:00",
      "pourquoi": "La nature du temps non payé n'est demandée qu'une fois la pause bornée."
    },
    {
      "remplir": "mpo-s-pause-fin",
      "valeur": "12:30",
      "pourquoi": "Idem : les deux bornes font apparaître le champ."
    }
  ],
  "preuve": [
    "mpo-declarer-ouvrir",
    "mpo-s-date",
    "mpo-s-entree",
    "mpo-s-sortie",
    "mpo-s-nature"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 07-presence.feature — Un quart non pointé se déclare après coup en plusieurs segments justifiés ; l'encadrement en saisit pour autrui ; le même temps ne se déclare pas deux fois »

**Essais Java** : `ControleurPresenceEffectifsHttpTest.java`

### P-GP-4 — La solution doit permettre le cumul cohérent de plusieurs saisies associées à un même quart.

*préférentiel · Gestion du "punch" · verdict : ACQUIS · lot U6*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/matemps` — Sur la feuille : les totaux cumulent les saisies du même quart, et isolent les heures supplémentaires. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "matemps",
  "prealable": [],
  "preuve": [
    "mft-titre",
    "mft-feuille-detail",
    "mft-totaux",
    "mft-hs"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 07-presence.feature — Un quart non pointé se déclare après coup en plusieurs segments justifiés ; l'encadrement en saisit pour autrui ; le même temps ne se déclare pas deux fois » ; « 07-presence.feature — Deux saisies après coup hors de tout quart ne se chevauchent pas : le même temps ne compte jamais deux fois »

**Essais Java** : `RepartitionPresenceTest.java`, `ControleurPresenceEffectifsHttpTest.java`

### P-RF-1 — La solution doit produire des relevés statistiques selon différents paramètres définis par l'organisation

*préférentiel · Rapports et formulaires · verdict : ACQUIS · lot U7*

**Scénario d'écran** (`urgences-sante-exigences.spec.ts`) : ouvrir `/rapports` — Constructeur de rapports : la source, les colonnes, les filtres, les regroupements et le tri sont définis par l'organisation, puis enregistrés comme relevé réutilisable. ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "rapports",
  "prealable": [],
  "preuve": [
    "rap-titre",
    "rap-source",
    "rap-colonnes",
    "rap-groupes",
    "rap-filtres",
    "rap-enregistrer"
  ]
}
```

</details>

**BDD** (Cucumber, contre le Java) : « 08-feuilles-de-temps.feature — Les relevés se regroupent selon le paramètre choisi et s'exportent dans les formats standards » ; « 08-feuilles-de-temps.feature — L'organisme définit son propre relevé statistique — source, mesures, regroupement, période — l'enregistre et le reproduit »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-releves.spec.ts`, `tests/ui/audit-wcag22.spec.ts`

**Essais Java** : `RelevesEffectifsDomaineTest.java`, `FaitsRelevesEffectifsHttpTest.java`, `ControleurTempsEffectifsHttpTest.java`, `RelevesStatistiquesTest.java`, `ControleurRapportsHttpTest.java`, `effectifs-releves.spec.ts`, `audit-wcag22.spec.ts`

### P-TN-1 — Le fournisseur doit configurer les modules de la solution dans les environnements de formation, de tests et de production.

*préférentiel · Technologie · verdict : ACQUIS · lot U0*

> **Aucun scénario e2e** — exigence technologique : Environnements de tests-formation et de production par le même code (aws/urgences-sante-essai.tfvars, aws/urgences-sante.tfvars) ; modules activables par organisme (`CatalogueModulesTest`) ; promotion outillée de la configuration fonctionnelle : paquet exporté (référentiels, règles), simulé puis appliqué ailleurs par les mêmes validations, versions datées et journal (`PaquetConfigurationTest`, `ControleurConfigurationEffectifsHttpTest`, écran Référentiels › Promouvoir la configuration, frontend/tests/promotion-configuration.test.tsx)

### P-TN-2 — La solution permet l'intégration à plusieurs annuaires actifs (Tel que: Active Directories).

*préférentiel · Technologie · verdict : ACQUIS · lot U0*

**Scénario par l'API T-07** (`urgences-sante-technologie.spec.ts`) — Plusieurs annuaires actifs par organisme : un fournisseur OIDC et un Active Directory en LDAPS

<details><summary>Données JSON de T-07 (4 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/auth/annuaires",
    "corps": {
      "nom": "Keycloak",
      "fournisseur": "oidc",
      "emetteur": "https://sso.urgences-sante.qc.ca/realms/geps",
      "clientId": "vina-geps",
      "variableSecret": "ANNUAIRE_KEYCLOAK",
      "domaines": [
        "urgences-sante.qc.ca"
      ],
      "roleParDefaut": "employee"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/auth/annuaires",
    "corps": {
      "nom": "AD du siège",
      "fournisseur": "ldaps",
      "emetteur": "ldaps://ad.urgences-sante.local:636/OU=Personnel,DC=us,DC=local",
      "clientId": "CN=svc-geps,DC=us,DC=local",
      "variableSecret": "ANNUAIRE_AD",
      "domaines": [
        "urgences-sante.qc.ca"
      ],
      "roleParDefaut": "employee"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "methode": "GET",
    "chemin": "/api/auth/annuaires",
    "attendu": {
      "statut": 200,
      "texte": "AD du siège"
    }
  },
  {
    "acteur": "julie",
    "methode": "GET",
    "chemin": "/api/auth/annuaires",
    "attendu": {
      "statut": 403
    }
  }
]
```

</details>

### P-TN-3 — La solution effectue des tests de reprise après sinistre (Disaster Recovery) avec révision des plans au besoin, a minima une fois par année.

*préférentiel · Technologie · verdict : PARTIEL · lot U0*

> **Aucun scénario e2e** — exigence technologique : Jamais exécuté contre un déploiement réel : aucun rapport d'exercice de reprise ni révision de plan consignée à ce jour.

### P-TN-4 — Les environnements de reprise après sinistre offrent le même niveau de service qu'en temps normal.

*préférentiel · Technologie · verdict : PARTIEL · lot U0*

> **Aucun scénario e2e** — exigence technologique : Copie hors région désactivée faute d'autorisation écrite (art. 17.07.04) ; aucun site de relève actif ni essai de bascule : le même niveau de service n'est démontré ni pour une zone ni pour une région.

### P-TN-5 — La solution garantie la récupération des processus transactionnels incomplets au moment où une panne ou une défaillance survient.

*préférentiel · Technologie · verdict : ACQUIS · lot U0*

> **Aucun scénario e2e** — exigence technologique : Ordonnanceur : un verrou abandonné se reprend (`BoucleOrdonnanceurTest`, `ControleurOrdonnanceurHttpTest`) ; boîte d'envoi idempotente, panne retentée, rejeu (`ControleurIntegrationsEffectifsHttpTest`)

### P-TN-6 — La solution utilisera les plages d’entretien pour les mises en place de correctifs hors de la période de disponibilité.

*préférentiel · Technologie · verdict : ACQUIS · lot U0*

> **Aucun scénario e2e** — exigence technologique : Porte de plage d'entretien avant tout déploiement (scripts/plage-entretien.sh, éprouvée sous macOS et Linux aux bornes et en heure d'été) ; déploiement planifié dans la plage (cron du dimanche 8 h UTC) ou manuel, une urgence exigeant un motif écrit (.github/workflows/ci-cd.yml) ; fenêtre annoncée 72 h, ≤ 12 h, exclue de la disponibilité (`SoutienTest`) ; bannière : e2e maintenance-banner.spec.ts

### P-TN-7 — La solution permet de verser ses données dans un entrepôt de données (Data Warehouse) à des fins d'analyse via un protocole sécurisé.

*préférentiel · Technologie · verdict : ACQUIS · lot U8*

**BDD** (Cucumber, contre le Java) : « 11-technologie.feature — Le journal part au bus d'entreprise en événements, et à l'entrepôt de données en versements, à partir d'un curseur »

**Essais e2e qui la nomment** : `tests/e2e/effectifs-integrations.spec.ts`

### P-TN-8 — La solution doit permettre d'exporter les résultats de recherche sur demande dans ces différents formats : Excel, PDF, CSV, JSON...

*préférentiel · Technologie · verdict : ACQUIS · lot U7*

**Scénario par l'API T-06** (`urgences-sante-technologie.spec.ts`) — Les résultats s'exportent en CSV, Excel, PDF et JSON

<details><summary>Données JSON de T-06 (5 étapes)</summary>

```json
[
  {
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/export?format=csv",
    "attendu": {
      "statut": 200,
      "texte": "Suivi des communications"
    }
  },
  {
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/export?format=xlsx",
    "attendu": {
      "statut": 200,
      "texte": "^PK"
    }
  },
  {
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/export?format=pdf",
    "attendu": {
      "statut": 200,
      "texte": "^%PDF-"
    }
  },
  {
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/export?format=json",
    "attendu": {
      "statut": 200
    }
  },
  {
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/export?format=doc",
    "attendu": {
      "statut": 400
    }
  }
]
```

</details>

**BDD** (Cucumber, contre le Java) : « 08-feuilles-de-temps.feature — Les relevés se regroupent selon le paramètre choisi et s'exportent dans les formats standards »

### P-TN-9 — La solution doit permettre de personnaliser l'apparence générale de la solution, en intégrant l'image graphique (logo et couleurs officielles) de l'organisation.

*préférentiel · Technologie · verdict : ACQUIS · lot U0*

**Scénario par l'API T-04** (`urgences-sante-technologie.spec.ts`) — L'organisme personnalise l'apparence : nom affiché et couleurs

<details><summary>Données JSON de T-04 (3 étapes)</summary>

```json
[
  {
    "methode": "PUT",
    "chemin": "/api/organisme/charte",
    "corps": {
      "nomAffiche": "Urgences-santé",
      "couleurPrincipale": "#005a9c",
      "couleurLaterale": "#001a2e"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "methode": "GET",
    "chemin": "/api/organisme/charte",
    "attendu": {
      "statut": 200,
      "corps": {
        "nomAffiche": "Urgences-santé",
        "couleurPrincipale": "#005a9c"
      }
    }
  },
  {
    "acteur": "julie",
    "methode": "PUT",
    "chemin": "/api/organisme/charte",
    "corps": {
      "nomAffiche": "X",
      "couleurPrincipale": "#000000",
      "couleurLaterale": "#000000"
    },
    "attendu": {
      "statut": 403
    }
  }
]
```

</details>

### P-TN-10 — La solution permet de définir des règles d'alertage par courriel lors de certaines opérations par les utilisateurs (ex: téléchargement de données).

*préférentiel · Technologie · verdict : ACQUIS · lot U0*

**Scénario par l'API T-05** (`urgences-sante-technologie.spec.ts`) — Une règle d'alerte par courriel se pose sur une opération sensible ; une adresse invalide est refusée

<details><summary>Données JSON de T-05 (3 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/organisme/alertes",
    "corps": {
      "evenement": "EXPORT_TELECHARGE",
      "destinataires": [
        "securite@urgences-sante.ca"
      ]
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/organisme/alertes",
    "corps": {
      "evenement": "EXPORT_TELECHARGE",
      "destinataires": [
        "pas-une-adresse"
      ]
    },
    "attendu": {
      "statut": 422
    }
  },
  {
    "methode": "GET",
    "chemin": "/api/organisme/alertes",
    "attendu": {
      "statut": 200,
      "texte": "securite@urgences-sante.ca"
    }
  }
]
```

</details>

### P-TN-11 — La solution permet de restreindre les authentifications des utilisateurs en provenance des seuls pays qu'elle autorisera.

*préférentiel · Technologie · verdict : ACQUIS · lot U0*

**Scénario par l'API T-10** (`urgences-sante-technologie.spec.ts`) — La restriction des connexions par pays se lit et se règle par l'administration

<details><summary>Données JSON de T-10 (3 étapes)</summary>

```json
[
  {
    "methode": "GET",
    "chemin": "/api/organisme/pays",
    "attendu": {
      "statut": 200,
      "texte": "enteteConfiguree"
    }
  },
  {
    "methode": "PUT",
    "chemin": "/api/organisme/pays",
    "corps": {
      "pays": []
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "acteur": "julie",
    "methode": "PUT",
    "chemin": "/api/organisme/pays",
    "corps": {
      "pays": []
    },
    "attendu": {
      "statut": 403
    }
  }
]
```

</details>

### P-TN-12 — La solution devra avoir une procédure de destruction sécuritaire des données dont la vie utile est terminée.

*préférentiel · Technologie · verdict : ACQUIS · lot U0*

> **Aucun scénario e2e** — exigence technologique : Destruction sécuritaire avec attestation (`DestructionTest`) ; rétention par classe des effectifs : la purge ne touche que l'ancien (`ControleurRetentionEffectifsHttpTest`, `RetentionEffectifsDomaineTest`)

### P-TN-13 — Le prestataire de services détient une expérience pertinente dans le domaine spécifique au contrat à adjuger, soit trois (3) années, et dans des projets de nature et d'importance similaire réalisés.

*préférentiel · Technologie · verdict : HORS_CODE*

> **Aucun scénario e2e** — exigence technologique : Trois ans d'expérience dans le domaine et projets similaires : fait de l'entreprise.

### P-TN-14 — Le prestataire de services dispose d'une équipe projet disponible au Québec pour accompagner l'organisation au moment du démarrage du projet et soutenir le projet avec vélocité et une continuité de service.

*préférentiel · Technologie · verdict : HORS_CODE*

> **Aucun scénario e2e** — exigence technologique : Équipe projet disponible au Québec : dotation.

### P-TN-15 — Le prestatairte de services fournit un plan projet indiquant les meilleures pratiques déployées préalablement dans les projets de référence et pouvant aider l'organisation à se préparer, à se mobiliser et à s'organiser pour respecter les échéances du projet.

*préférentiel · Technologie · verdict : HORS_CODE*

> **Aucun scénario e2e** — exigence technologique : Plan de projet et bonnes pratiques à rédiger pour ce dossier.

### P-TN-16 — Le prestatairte de services indique dans son plan de projet le recours à des méthodes de transfert de connaissances de l'équipe d'intégrateur vers les administrateurs et les usagers formateurs.

*préférentiel · Technologie · verdict : HORS_CODE*

> **Aucun scénario e2e** — exigence technologique : Méthode de transfert de connaissances à rédiger.

### P-TN-17 — Le prestatairte de services inclus dans son plan de projet le développement d'interface de programmation d'applications (IPA) ou (API).

*préférentiel · Technologie · verdict : HORS_CODE*

**BDD** (Cucumber, contre le Java) : « 11-technologie.feature — L'API se décrit en OpenAPI 3, pour un compte authentifié » ; « 11-technologie.feature — Les opérations transmettent une absence sur un quart réel par l'API d'échange ; le quart vacant est annoncé au webhook et paraît parmi les correctifs (AD-06c) »

> **Aucun scénario e2e** — exigence technologique : Développement d'API à inscrire au plan de projet.

### P-TN-18 — La formation des administrateurs de la plateforme lors de la mise en production est incluse dans la proposition.

*préférentiel · Technologie · verdict : HORS_CODE*

> **Aucun scénario e2e** — exigence technologique : Formation des administrateurs à inclure dans l'offre.

### P-TN-19 — Une documentation de formation en français et éditable par le client (guide usagers, aide à la tâche, autre) est incluse dans le plan de projet au moment de la mise en production.

*préférentiel · Technologie · verdict : HORS_CODE*

> **Aucun scénario e2e** — exigence technologique : Documentation de formation éditable en français à produire.

### P-TN-20 — Le prestatairte de services indique une date à partir de laquelle l'équipe projet peut se mobiliser pour débuter le projet.

*préférentiel · Technologie · verdict : HORS_CODE*

> **Aucun scénario e2e** — exigence technologique : Date de mobilisation à déclarer.

### P-TN-21 — Le prestatairte de services donne un estimé du nombre d'ateliers compte tenu du contexte de l'organisation décrit dans les documents de l'appel d'offre décrivant le contexte organisationnel et les besoins fonctionnels.

*préférentiel · Technologie · verdict : HORS_CODE*

> **Aucun scénario e2e** — exigence technologique : Estimation du nombre d'ateliers à déclarer.

### P-TN-22 — Le prestatairte de services indique dans son plan de projet les outils de gestion de projet, de communication et de collaboration qui pourront être utilisés par l'équipe d'intégration avec l'équipe projet (exemple: Portail de communauté) dans le respect des règles de sécurité et de confidentialité de l'organisation.

*préférentiel · Technologie · verdict : HORS_CODE*

> **Aucun scénario e2e** — exigence technologique : Outils de gestion de projet et de collaboration à déclarer.

### P-TN-23 — Le prestatairte de services fournit dans son plan de projet un estimé de la durée totale en mois pour atteindre la mise en production.

*préférentiel · Technologie · verdict : HORS_CODE*

> **Aucun scénario e2e** — exigence technologique : Durée totale jusqu'à la mise en production à déclarer (≤ 16 semaines après démarrage, contrat 2.03).

## 2. Santé Québec Laurentides — FX-AO-20260810 (115 critères)

### FX-001 — Console web d'envoi centralisée ; alerte par application mobile ou texto selon la préférence du destinataire

*critère 1 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/alnouvelle` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "alnouvelle",
  "prealable": [],
  "preuve": [
    "aln-formulaire",
    "aln-destinataires",
    "aln-envoyer"
  ]
}
```

</details>

**Scénario par l'API FX-04** (`fx-ao-comportements.spec.ts`) — La console envoie à un groupe ou à des personnes nommées ; critique réservée ; destinataire inconnu refusé

<details><summary>Données JSON de FX-04 (7 étapes)</summary>

```json
[
  {
    "pourquoi": "Recherche du destinataire par son nom dès trois caractères (critère 3)",
    "methode": "GET",
    "chemin": "/api/communication/groupes-transverses/personnes?q=jul",
    "attendu": {
      "statut": 200,
      "texte": "{{p_julie}}"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/groupes",
    "attendu": {
      "statut": 200,
      "texte": "Garde de nuit"
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Code bleu",
      "corps": "Chambre 512",
      "priorite": "urgente",
      "reponseIds": [
        "{{arrive}}",
        "{{indispo}}"
      ],
      "groupes": [
        "{{garde}}"
      ],
      "modeleId": "{{modele}}"
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "bilan": {
          "destinataires": 2,
          "lues": 0,
          "repondues": 0
        }
      }
    },
    "capturer": {
      "alerte": "id"
    }
  },
  {
    "pourquoi": "La priorité critique exige la capacité alertes.critique",
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Critique",
      "corps": "x",
      "priorite": "critique",
      "personnes": [
        "{{p_julie}}"
      ]
    },
    "attendu": {
      "statut": 403
    }
  },
  {
    "acteur": "dir",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Évacuation aile B",
      "corps": "Rendez-vous au poste",
      "priorite": "critique",
      "personnes": [
        "{{p_julie}}",
        "{{p_marc}}"
      ]
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "priorite": "critique",
        "bilan": {
          "destinataires": 2
        }
      }
    },
    "capturer": {
      "critique": "id"
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Info",
      "corps": "x",
      "priorite": "information",
      "personnes": [
        "inconnu-{{suffixe}}"
      ]
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "pourquoi": "Un destinataire n'envoie pas d'alerte",
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "x",
      "corps": "x",
      "priorite": "information",
      "personnes": [
        "{{p_marc}}"
      ]
    },
    "attendu": {
      "statut": 403
    }
  }
]
```

</details>

**Essais Java** : `ControleurAlertesHttpTest.java`

### FX-002 — Insertion d'une note au dossier des destinataires

*critère 2 · fonctionnel · statut 🟡*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/alsuivi` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "alsuivi",
  "prealable": [],
  "preuve": [
    "als-ecran",
    "als-table|als-vide"
  ]
}
```

</details>

**Scénario par l'API FX-09** (`fx-ao-comportements.spec.ts`) — Une note courte et datée sur le destinataire, lue par les expéditeurs

<details><summary>Données JSON de FX-09 (3 étapes)</summary>

```json
[
  {
    "acteur": "chef",
    "methode": "PUT",
    "chemin": "/api/alertes/notes/{{p_julie}}",
    "corps": {
      "texte": "Joindre sur le cellulaire de garde"
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "texte": "Joindre sur le cellulaire de garde"
      }
    }
  },
  {
    "acteur": "dir",
    "methode": "GET",
    "chemin": "/api/alertes/notes?personnes={{p_julie}}",
    "attendu": {
      "statut": 200,
      "texte": "cellulaire de garde"
    }
  },
  {
    "acteur": "marc",
    "methode": "PUT",
    "chemin": "/api/alertes/notes/{{p_julie}}",
    "corps": {
      "texte": "x"
    },
    "attendu": {
      "statut": 403
    }
  }
]
```

</details>

**Essais Java** : `ControleurNotesHttpTest.java`

### FX-003 — Recherche de destinataire par nom, prénom

*critère 3 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/alnouvelle` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "alnouvelle",
  "prealable": [],
  "preuve": [
    "aln-groupe-personnes"
  ]
}
```

</details>

**Scénario par l'API FX-04** (`fx-ao-comportements.spec.ts`) — La console envoie à un groupe ou à des personnes nommées ; critique réservée ; destinataire inconnu refusé

<details><summary>Données JSON de FX-04 (7 étapes)</summary>

```json
[
  {
    "pourquoi": "Recherche du destinataire par son nom dès trois caractères (critère 3)",
    "methode": "GET",
    "chemin": "/api/communication/groupes-transverses/personnes?q=jul",
    "attendu": {
      "statut": 200,
      "texte": "{{p_julie}}"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/groupes",
    "attendu": {
      "statut": 200,
      "texte": "Garde de nuit"
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Code bleu",
      "corps": "Chambre 512",
      "priorite": "urgente",
      "reponseIds": [
        "{{arrive}}",
        "{{indispo}}"
      ],
      "groupes": [
        "{{garde}}"
      ],
      "modeleId": "{{modele}}"
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "bilan": {
          "destinataires": 2,
          "lues": 0,
          "repondues": 0
        }
      }
    },
    "capturer": {
      "alerte": "id"
    }
  },
  {
    "pourquoi": "La priorité critique exige la capacité alertes.critique",
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Critique",
      "corps": "x",
      "priorite": "critique",
      "personnes": [
        "{{p_julie}}"
      ]
    },
    "attendu": {
      "statut": 403
    }
  },
  {
    "acteur": "dir",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Évacuation aile B",
      "corps": "Rendez-vous au poste",
      "priorite": "critique",
      "personnes": [
        "{{p_julie}}",
        "{{p_marc}}"
      ]
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "priorite": "critique",
        "bilan": {
          "destinataires": 2
        }
      }
    },
    "capturer": {
      "critique": "id"
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Info",
      "corps": "x",
      "priorite": "information",
      "personnes": [
        "inconnu-{{suffixe}}"
      ]
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "pourquoi": "Un destinataire n'envoie pas d'alerte",
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "x",
      "corps": "x",
      "priorite": "information",
      "personnes": [
        "{{p_marc}}"
      ]
    },
    "attendu": {
      "statut": 403
    }
  }
]
```

</details>

### FX-004 — Messages texte bidirectionnels entre équipes, médecins, utilisateurs autorisés

*critère 4 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/usmessages` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usmessages",
  "prealable": [],
  "preuve": [
    "sq-msg-ouvrir|sq-msg-directe"
  ]
}
```

</details>

**Scénario par l'API FX-05** (`fx-ao-comportements.spec.ts`) — Le destinataire lit, puis prend en charge d'un geste ; une seconde réponse est refusée

<details><summary>Données JSON de FX-05 (5 étapes)</summary>

```json
[
  {
    "acteur": "julie",
    "methode": "GET",
    "chemin": "/api/alertes/recues",
    "attendu": {
      "statut": 200,
      "texte": "Code bleu"
    }
  },
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes/recues/{{alerte}}/lue",
    "attendu": {
      "statut": 200
    }
  },
  {
    "acteur": "marc",
    "methode": "POST",
    "chemin": "/api/alertes/recues/{{alerte}}/reponse",
    "corps": {
      "reponseId": "{{indispo}}"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes/recues/{{alerte}}/reponse",
    "corps": {
      "reponseId": "{{arrive}}"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "pourquoi": "Une réponse donnée ne se réécrit pas",
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes/recues/{{alerte}}/reponse",
    "corps": {
      "reponseId": "{{indispo}}"
    },
    "attendu": {
      "statut": 409
    }
  }
]
```

</details>

**Essais Java** : `ControleurWebhooksAlertesHttpTest.java`

### FX-005 — Envoi à un utilisateur, plusieurs, ou un groupe configurable

*critère 5 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/alnouvelle` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "alnouvelle",
  "prealable": [],
  "preuve": [
    "aln-destinataires",
    "aln-groupe-personnes"
  ]
}
```

</details>

**Scénario par l'API FX-04** (`fx-ao-comportements.spec.ts`) — La console envoie à un groupe ou à des personnes nommées ; critique réservée ; destinataire inconnu refusé

<details><summary>Données JSON de FX-04 (7 étapes)</summary>

```json
[
  {
    "pourquoi": "Recherche du destinataire par son nom dès trois caractères (critère 3)",
    "methode": "GET",
    "chemin": "/api/communication/groupes-transverses/personnes?q=jul",
    "attendu": {
      "statut": 200,
      "texte": "{{p_julie}}"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/groupes",
    "attendu": {
      "statut": 200,
      "texte": "Garde de nuit"
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Code bleu",
      "corps": "Chambre 512",
      "priorite": "urgente",
      "reponseIds": [
        "{{arrive}}",
        "{{indispo}}"
      ],
      "groupes": [
        "{{garde}}"
      ],
      "modeleId": "{{modele}}"
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "bilan": {
          "destinataires": 2,
          "lues": 0,
          "repondues": 0
        }
      }
    },
    "capturer": {
      "alerte": "id"
    }
  },
  {
    "pourquoi": "La priorité critique exige la capacité alertes.critique",
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Critique",
      "corps": "x",
      "priorite": "critique",
      "personnes": [
        "{{p_julie}}"
      ]
    },
    "attendu": {
      "statut": 403
    }
  },
  {
    "acteur": "dir",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Évacuation aile B",
      "corps": "Rendez-vous au poste",
      "priorite": "critique",
      "personnes": [
        "{{p_julie}}",
        "{{p_marc}}"
      ]
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "priorite": "critique",
        "bilan": {
          "destinataires": 2
        }
      }
    },
    "capturer": {
      "critique": "id"
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Info",
      "corps": "x",
      "priorite": "information",
      "personnes": [
        "inconnu-{{suffixe}}"
      ]
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "pourquoi": "Un destinataire n'envoie pas d'alerte",
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "x",
      "corps": "x",
      "priorite": "information",
      "personnes": [
        "{{p_marc}}"
      ]
    },
    "attendu": {
      "statut": 403
    }
  }
]
```

</details>

### FX-006 — Messages prédéfinis pour les expéditeurs

*critère 6 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/alparametres` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "alparametres",
  "prealable": [],
  "preuve": [
    "alp-modeles|alp-modeles-vide",
    "alp-modele-ouvrir"
  ]
}
```

</details>

**Scénario par l'API FX-02** (`fx-ao-comportements.spec.ts`) — Un modèle d'alerte propose ses réponses et se versionne à chaque modification

<details><summary>Données JSON de FX-02 (5 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/alertes/modeles",
    "corps": {
      "titre": "Code bleu",
      "corps": "Chambre 512, 5e étage",
      "priorite": "urgente",
      "reponseIds": [
        "{{arrive}}",
        "{{indispo}}"
      ]
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "version": 1,
        "actif": true
      }
    },
    "capturer": {
      "modele": "id"
    }
  },
  {
    "methode": "PUT",
    "chemin": "/api/alertes/modeles/{{modele}}",
    "corps": {
      "titre": "Code bleu — 5e",
      "corps": "Chambre 512, 5e étage, aile B",
      "priorite": "urgente",
      "reponseIds": [
        "{{arrive}}",
        "{{indispo}}"
      ]
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "version": 2
      }
    }
  },
  {
    "methode": "GET",
    "chemin": "/api/alertes/modeles/{{modele}}/versions",
    "attendu": {
      "statut": 200,
      "corps": [
        {
          "version": 2
        },
        {
          "version": 1
        }
      ]
    }
  },
  {
    "pourquoi": "La priorité est un ensemble fermé",
    "methode": "POST",
    "chemin": "/api/alertes/modeles",
    "corps": {
      "titre": "X",
      "corps": "Y",
      "priorite": "haute"
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "pourquoi": "La gestionnaire voit le modèle actif",
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/modeles",
    "attendu": {
      "statut": 200,
      "texte": "Code bleu — 5e"
    }
  }
]
```

</details>

### FX-007 — Diffusion d'alertes en temps réel

*critère 7 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/alnouvelle` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "alnouvelle",
  "prealable": [],
  "preuve": [
    "aln-envoyer"
  ]
}
```

</details>

**Scénario par l'API FX-04** (`fx-ao-comportements.spec.ts`) — La console envoie à un groupe ou à des personnes nommées ; critique réservée ; destinataire inconnu refusé

<details><summary>Données JSON de FX-04 (7 étapes)</summary>

```json
[
  {
    "pourquoi": "Recherche du destinataire par son nom dès trois caractères (critère 3)",
    "methode": "GET",
    "chemin": "/api/communication/groupes-transverses/personnes?q=jul",
    "attendu": {
      "statut": 200,
      "texte": "{{p_julie}}"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/groupes",
    "attendu": {
      "statut": 200,
      "texte": "Garde de nuit"
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Code bleu",
      "corps": "Chambre 512",
      "priorite": "urgente",
      "reponseIds": [
        "{{arrive}}",
        "{{indispo}}"
      ],
      "groupes": [
        "{{garde}}"
      ],
      "modeleId": "{{modele}}"
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "bilan": {
          "destinataires": 2,
          "lues": 0,
          "repondues": 0
        }
      }
    },
    "capturer": {
      "alerte": "id"
    }
  },
  {
    "pourquoi": "La priorité critique exige la capacité alertes.critique",
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Critique",
      "corps": "x",
      "priorite": "critique",
      "personnes": [
        "{{p_julie}}"
      ]
    },
    "attendu": {
      "statut": 403
    }
  },
  {
    "acteur": "dir",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Évacuation aile B",
      "corps": "Rendez-vous au poste",
      "priorite": "critique",
      "personnes": [
        "{{p_julie}}",
        "{{p_marc}}"
      ]
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "priorite": "critique",
        "bilan": {
          "destinataires": 2
        }
      }
    },
    "capturer": {
      "critique": "id"
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Info",
      "corps": "x",
      "priorite": "information",
      "personnes": [
        "inconnu-{{suffixe}}"
      ]
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "pourquoi": "Un destinataire n'envoie pas d'alerte",
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "x",
      "corps": "x",
      "priorite": "information",
      "personnes": [
        "{{p_marc}}"
      ]
    },
    "attendu": {
      "statut": 403
    }
  }
]
```

</details>

### FX-008 — Copie courriel d'un message, si configurée par l'organisme

*critère 8 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/alparametres` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "alparametres",
  "prealable": [],
  "preuve": [
    "alp-copie"
  ]
}
```

</details>

**Scénario par l'API FX-07** (`fx-ao-comportements.spec.ts`) — La copie par courriel se règle par organisme

<details><summary>Données JSON de FX-07 (3 étapes)</summary>

```json
[
  {
    "methode": "PUT",
    "chemin": "/api/alertes/reglages",
    "corps": {
      "copieCourriel": true
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "copieCourriel": true
      }
    }
  },
  {
    "methode": "GET",
    "chemin": "/api/alertes/reglages",
    "attendu": {
      "statut": 200,
      "corps": {
        "copieCourriel": true
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "PUT",
    "chemin": "/api/alertes/reglages",
    "corps": {
      "copieCourriel": false
    },
    "attendu": {
      "statut": 403
    }
  }
]
```

</details>

**Essais Java** : `ControleurAlertesHttpTest.java`

### FX-009 — Confirmation de réception, lecture ou prise en charge, simple et automatisée

*critère 9 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/mesalertes` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "mesalertes",
  "prealable": [],
  "preuve": [
    "mal-titre"
  ]
}
```

</details>

**Scénario par l'API FX-05** (`fx-ao-comportements.spec.ts`) — Le destinataire lit, puis prend en charge d'un geste ; une seconde réponse est refusée

<details><summary>Données JSON de FX-05 (5 étapes)</summary>

```json
[
  {
    "acteur": "julie",
    "methode": "GET",
    "chemin": "/api/alertes/recues",
    "attendu": {
      "statut": 200,
      "texte": "Code bleu"
    }
  },
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes/recues/{{alerte}}/lue",
    "attendu": {
      "statut": 200
    }
  },
  {
    "acteur": "marc",
    "methode": "POST",
    "chemin": "/api/alertes/recues/{{alerte}}/reponse",
    "corps": {
      "reponseId": "{{indispo}}"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes/recues/{{alerte}}/reponse",
    "corps": {
      "reponseId": "{{arrive}}"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "pourquoi": "Une réponse donnée ne se réécrit pas",
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes/recues/{{alerte}}/reponse",
    "corps": {
      "reponseId": "{{indispo}}"
    },
    "attendu": {
      "statut": 409
    }
  }
]
```

</details>

### FX-010 — Réponses prédéfinies, configurées par les administrateurs

*critère 10 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/alparametres` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "alparametres",
  "prealable": [],
  "preuve": [
    "alp-reponses|alp-reponses-vide",
    "alp-reponse-ouvrir"
  ]
}
```

</details>

**Scénario par l'API FX-01** (`fx-ao-comportements.spec.ts`) — Les réponses prédéfinies ont une sémantique fermée ; seule l'administration les tient

<details><summary>Données JSON de FX-01 (4 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/alertes/reponses",
    "corps": {
      "libelle": "J'arrive",
      "semantique": "prise_en_charge"
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "libelle": "J'arrive",
        "semantique": "prise_en_charge",
        "active": true
      }
    },
    "capturer": {
      "arrive": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/alertes/reponses",
    "corps": {
      "libelle": "Indisponible",
      "semantique": "refus"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "indispo": "id"
    }
  },
  {
    "pourquoi": "Une sémantique hors de l'ensemble fermé est refusée",
    "methode": "POST",
    "chemin": "/api/alertes/reponses",
    "corps": {
      "libelle": "Peut-être",
      "semantique": "oui"
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "pourquoi": "La gestionnaire envoie, elle ne paramètre pas",
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes/reponses",
    "corps": {
      "libelle": "X",
      "semantique": "refus"
    },
    "attendu": {
      "statut": 403
    }
  }
]
```

</details>

### FX-011 — Statut d'envoi, réception, lecture, réponse et relance, vu par l'expéditeur

*critère 11 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/alsuivi` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "alsuivi",
  "prealable": [],
  "preuve": [
    "als-filtres",
    "als-etat"
  ]
}
```

</details>

**Scénario par l'API FX-06** (`fx-ao-comportements.spec.ts`) — Le suivi est partagé entre expéditeurs, filtrable et exportable ; il dit qui a lu, répondu, pris en charge

<details><summary>Données JSON de FX-06 (6 étapes)</summary>

```json
[
  {
    "pourquoi": "La directrice voit l'alerte de la gestionnaire (critère 12) ; répondre vaut lecture",
    "acteur": "dir",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/{{alerte}}",
    "attendu": {
      "statut": 200,
      "corps": {
        "bilan": {
          "destinataires": 2,
          "lues": 2,
          "repondues": 2,
          "relances": 0
        }
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees?etat=prise_en_charge",
    "attendu": {
      "statut": 200,
      "texte": "Code bleu"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees?etat=sans_prise_en_charge",
    "attendu": {
      "statut": 200,
      "texte": "Évacuation aile B"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees?etat=perdue",
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/export?format=csv",
    "attendu": {
      "statut": 200,
      "texte": "relances"
    }
  },
  {
    "pourquoi": "L'export PDF est un PDF (PDF/A-2b, critère 39 ; veraPDF en CI)",
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/export?format=pdf",
    "attendu": {
      "statut": 200,
      "texte": "^%PDF-"
    }
  }
]
```

</details>

### FX-012 — Historique des échanges et statuts partagés entre expéditeurs

*critère 12 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/alsuivi` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "alsuivi",
  "prealable": [],
  "preuve": [
    "als-expediteur",
    "prefixe:als-export"
  ]
}
```

</details>

**Scénario par l'API FX-06** (`fx-ao-comportements.spec.ts`) — Le suivi est partagé entre expéditeurs, filtrable et exportable ; il dit qui a lu, répondu, pris en charge

<details><summary>Données JSON de FX-06 (6 étapes)</summary>

```json
[
  {
    "pourquoi": "La directrice voit l'alerte de la gestionnaire (critère 12) ; répondre vaut lecture",
    "acteur": "dir",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/{{alerte}}",
    "attendu": {
      "statut": 200,
      "corps": {
        "bilan": {
          "destinataires": 2,
          "lues": 2,
          "repondues": 2,
          "relances": 0
        }
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees?etat=prise_en_charge",
    "attendu": {
      "statut": 200,
      "texte": "Code bleu"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees?etat=sans_prise_en_charge",
    "attendu": {
      "statut": 200,
      "texte": "Évacuation aile B"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees?etat=perdue",
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/export?format=csv",
    "attendu": {
      "statut": 200,
      "texte": "relances"
    }
  },
  {
    "pourquoi": "L'export PDF est un PDF (PDF/A-2b, critère 39 ; veraPDF en CI)",
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/export?format=pdf",
    "attendu": {
      "statut": 200,
      "texte": "^%PDF-"
    }
  }
]
```

</details>

### FX-013 — Confirmer la réception d'un retour d'appel

*critère 13 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/mesalertes` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "mesalertes",
  "prealable": [],
  "preuve": [
    "mal-titre"
  ]
}
```

</details>

**Scénario par l'API FX-05** (`fx-ao-comportements.spec.ts`) — Le destinataire lit, puis prend en charge d'un geste ; une seconde réponse est refusée

<details><summary>Données JSON de FX-05 (5 étapes)</summary>

```json
[
  {
    "acteur": "julie",
    "methode": "GET",
    "chemin": "/api/alertes/recues",
    "attendu": {
      "statut": 200,
      "texte": "Code bleu"
    }
  },
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes/recues/{{alerte}}/lue",
    "attendu": {
      "statut": 200
    }
  },
  {
    "acteur": "marc",
    "methode": "POST",
    "chemin": "/api/alertes/recues/{{alerte}}/reponse",
    "corps": {
      "reponseId": "{{indispo}}"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes/recues/{{alerte}}/reponse",
    "corps": {
      "reponseId": "{{arrive}}"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "pourquoi": "Une réponse donnée ne se réécrit pas",
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes/recues/{{alerte}}/reponse",
    "corps": {
      "reponseId": "{{indispo}}"
    },
    "attendu": {
      "statut": 409
    }
  }
]
```

</details>

**Essais Java** : `AppelsAlertesTest.java`

### FX-014 — Appel téléphonique automatique en cas de non-réponse, délai programmable

*critère 14 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/alparametres` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "alparametres",
  "prealable": [],
  "preuve": [
    "alp-politiques",
    "alp-politique-ouvrir"
  ]
}
```

</details>

**Scénario par l'API FX-08** (`fx-ao-comportements.spec.ts`) — Une politique de relance arme une cascade ; l'expéditeur la reprend à l'étape choisie, et la trace le dit

<details><summary>Données JSON de FX-08 (8 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/alertes/politiques",
    "corps": {
      "nom": "Code bleu — relance",
      "arret": "prise_en_charge",
      "etapes": [
        {
          "cible": "initiaux",
          "canaux": [
            "poussee"
          ],
          "delaiMinutes": 5
        },
        {
          "cible": "prochain_disponible",
          "cibleId": "{{garde}}",
          "canaux": [
            "texto"
          ],
          "delaiMinutes": 10
        },
        {
          "cible": "initiaux",
          "canaux": [
            "appel"
          ],
          "delaiMinutes": 15
        }
      ]
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "version": 1
      }
    },
    "capturer": {
      "politique": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/alertes/politiques",
    "corps": {
      "nom": "X",
      "arret": "jamais",
      "etapes": []
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Relance de garde",
      "corps": "Répondez",
      "priorite": "urgente",
      "reponseIds": [
        "{{arrive}}"
      ],
      "personnes": [
        "{{p_julie}}"
      ],
      "politiqueId": "{{politique}}"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "relancee": "id"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade",
    "attendu": {
      "statut": 200,
      "corps": {
        "cascade": {
          "etat": "armee",
          "etape": 1,
          "etapes": 3
        },
        "traces": [
          {
            "evenement": "armee"
          }
        ]
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 2
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "cascade": {
          "etat": "armee",
          "etape": 2
        }
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 9
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 1
    },
    "attendu": {
      "statut": 403
    }
  },
  {
    "acteur": "dir",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade",
    "attendu": {
      "statut": 200,
      "texte": "reprise"
    }
  }
]
```

</details>

**Essais Java** : `AppelsAlertesTest.java`

### FX-015 — Relances automatisées configurables

*critère 15 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/alparametres` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "alparametres",
  "prealable": [],
  "preuve": [
    "alp-politiques"
  ]
}
```

</details>

**Scénario par l'API FX-08** (`fx-ao-comportements.spec.ts`) — Une politique de relance arme une cascade ; l'expéditeur la reprend à l'étape choisie, et la trace le dit

<details><summary>Données JSON de FX-08 (8 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/alertes/politiques",
    "corps": {
      "nom": "Code bleu — relance",
      "arret": "prise_en_charge",
      "etapes": [
        {
          "cible": "initiaux",
          "canaux": [
            "poussee"
          ],
          "delaiMinutes": 5
        },
        {
          "cible": "prochain_disponible",
          "cibleId": "{{garde}}",
          "canaux": [
            "texto"
          ],
          "delaiMinutes": 10
        },
        {
          "cible": "initiaux",
          "canaux": [
            "appel"
          ],
          "delaiMinutes": 15
        }
      ]
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "version": 1
      }
    },
    "capturer": {
      "politique": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/alertes/politiques",
    "corps": {
      "nom": "X",
      "arret": "jamais",
      "etapes": []
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Relance de garde",
      "corps": "Répondez",
      "priorite": "urgente",
      "reponseIds": [
        "{{arrive}}"
      ],
      "personnes": [
        "{{p_julie}}"
      ],
      "politiqueId": "{{politique}}"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "relancee": "id"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade",
    "attendu": {
      "statut": 200,
      "corps": {
        "cascade": {
          "etat": "armee",
          "etape": 1,
          "etapes": 3
        },
        "traces": [
          {
            "evenement": "armee"
          }
        ]
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 2
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "cascade": {
          "etat": "armee",
          "etape": 2
        }
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 9
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 1
    },
    "attendu": {
      "statut": 403
    }
  },
  {
    "acteur": "dir",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade",
    "attendu": {
      "statut": 200,
      "texte": "reprise"
    }
  }
]
```

</details>

**Essais Java** : `ControleurCascadesHttpTest.java`

### FX-016 — Cascades entre utilisateurs d'un groupe ou de groupes distincts

*critère 16 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/alparametres` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "alparametres",
  "prealable": [],
  "preuve": [
    "alp-politiques",
    "prefixe:alp-gt"
  ]
}
```

</details>

**Scénario par l'API FX-08** (`fx-ao-comportements.spec.ts`) — Une politique de relance arme une cascade ; l'expéditeur la reprend à l'étape choisie, et la trace le dit

<details><summary>Données JSON de FX-08 (8 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/alertes/politiques",
    "corps": {
      "nom": "Code bleu — relance",
      "arret": "prise_en_charge",
      "etapes": [
        {
          "cible": "initiaux",
          "canaux": [
            "poussee"
          ],
          "delaiMinutes": 5
        },
        {
          "cible": "prochain_disponible",
          "cibleId": "{{garde}}",
          "canaux": [
            "texto"
          ],
          "delaiMinutes": 10
        },
        {
          "cible": "initiaux",
          "canaux": [
            "appel"
          ],
          "delaiMinutes": 15
        }
      ]
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "version": 1
      }
    },
    "capturer": {
      "politique": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/alertes/politiques",
    "corps": {
      "nom": "X",
      "arret": "jamais",
      "etapes": []
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Relance de garde",
      "corps": "Répondez",
      "priorite": "urgente",
      "reponseIds": [
        "{{arrive}}"
      ],
      "personnes": [
        "{{p_julie}}"
      ],
      "politiqueId": "{{politique}}"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "relancee": "id"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade",
    "attendu": {
      "statut": 200,
      "corps": {
        "cascade": {
          "etat": "armee",
          "etape": 1,
          "etapes": 3
        },
        "traces": [
          {
            "evenement": "armee"
          }
        ]
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 2
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "cascade": {
          "etat": "armee",
          "etape": 2
        }
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 9
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 1
    },
    "attendu": {
      "statut": 403
    }
  },
  {
    "acteur": "dir",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade",
    "attendu": {
      "statut": 200,
      "texte": "reprise"
    }
  }
]
```

</details>

**Essais Java** : `ControleurCascadesHttpTest.java`

### FX-017 — Délais, séquences, règles d'arrêt et de reprise des cascades

*critère 17 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/alparametres` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "alparametres",
  "prealable": [],
  "preuve": [
    "alp-politiques"
  ]
}
```

</details>

**Scénario par l'API FX-08** (`fx-ao-comportements.spec.ts`) — Une politique de relance arme une cascade ; l'expéditeur la reprend à l'étape choisie, et la trace le dit

<details><summary>Données JSON de FX-08 (8 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/alertes/politiques",
    "corps": {
      "nom": "Code bleu — relance",
      "arret": "prise_en_charge",
      "etapes": [
        {
          "cible": "initiaux",
          "canaux": [
            "poussee"
          ],
          "delaiMinutes": 5
        },
        {
          "cible": "prochain_disponible",
          "cibleId": "{{garde}}",
          "canaux": [
            "texto"
          ],
          "delaiMinutes": 10
        },
        {
          "cible": "initiaux",
          "canaux": [
            "appel"
          ],
          "delaiMinutes": 15
        }
      ]
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "version": 1
      }
    },
    "capturer": {
      "politique": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/alertes/politiques",
    "corps": {
      "nom": "X",
      "arret": "jamais",
      "etapes": []
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Relance de garde",
      "corps": "Répondez",
      "priorite": "urgente",
      "reponseIds": [
        "{{arrive}}"
      ],
      "personnes": [
        "{{p_julie}}"
      ],
      "politiqueId": "{{politique}}"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "relancee": "id"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade",
    "attendu": {
      "statut": 200,
      "corps": {
        "cascade": {
          "etat": "armee",
          "etape": 1,
          "etapes": 3
        },
        "traces": [
          {
            "evenement": "armee"
          }
        ]
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 2
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "cascade": {
          "etat": "armee",
          "etape": 2
        }
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 9
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 1
    },
    "attendu": {
      "statut": 403
    }
  },
  {
    "acteur": "dir",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade",
    "attendu": {
      "statut": 200,
      "texte": "reprise"
    }
  }
]
```

</details>

**Essais Java** : `ControleurCascadesHttpTest.java`

### FX-018 — Alertes critiques, lorsque disponibles et permises

*critère 18 · fonctionnel · statut 🟡*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/alnouvelle` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "alnouvelle",
  "prealable": [],
  "preuve": [
    "aln-critique-refusee|aln-p-critique"
  ]
}
```

</details>

**Scénario par l'API FX-04** (`fx-ao-comportements.spec.ts`) — La console envoie à un groupe ou à des personnes nommées ; critique réservée ; destinataire inconnu refusé

<details><summary>Données JSON de FX-04 (7 étapes)</summary>

```json
[
  {
    "pourquoi": "Recherche du destinataire par son nom dès trois caractères (critère 3)",
    "methode": "GET",
    "chemin": "/api/communication/groupes-transverses/personnes?q=jul",
    "attendu": {
      "statut": 200,
      "texte": "{{p_julie}}"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/groupes",
    "attendu": {
      "statut": 200,
      "texte": "Garde de nuit"
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Code bleu",
      "corps": "Chambre 512",
      "priorite": "urgente",
      "reponseIds": [
        "{{arrive}}",
        "{{indispo}}"
      ],
      "groupes": [
        "{{garde}}"
      ],
      "modeleId": "{{modele}}"
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "bilan": {
          "destinataires": 2,
          "lues": 0,
          "repondues": 0
        }
      }
    },
    "capturer": {
      "alerte": "id"
    }
  },
  {
    "pourquoi": "La priorité critique exige la capacité alertes.critique",
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Critique",
      "corps": "x",
      "priorite": "critique",
      "personnes": [
        "{{p_julie}}"
      ]
    },
    "attendu": {
      "statut": 403
    }
  },
  {
    "acteur": "dir",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Évacuation aile B",
      "corps": "Rendez-vous au poste",
      "priorite": "critique",
      "personnes": [
        "{{p_julie}}",
        "{{p_marc}}"
      ]
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "priorite": "critique",
        "bilan": {
          "destinataires": 2
        }
      }
    },
    "capturer": {
      "critique": "id"
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Info",
      "corps": "x",
      "priorite": "information",
      "personnes": [
        "inconnu-{{suffixe}}"
      ]
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "pourquoi": "Un destinataire n'envoie pas d'alerte",
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "x",
      "corps": "x",
      "priorite": "information",
      "personnes": [
        "{{p_marc}}"
      ]
    },
    "attendu": {
      "statut": 403
    }
  }
]
```

</details>

**Essais Java** : `NotificationsEtPousseeTest.java`

### FX-019 — Traces des relances, appels, réponses, escalades

*critère 19 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/alsuivi` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "alsuivi",
  "prealable": [],
  "preuve": [
    "als-table|als-vide"
  ]
}
```

</details>

**Scénario par l'API FX-08** (`fx-ao-comportements.spec.ts`) — Une politique de relance arme une cascade ; l'expéditeur la reprend à l'étape choisie, et la trace le dit

<details><summary>Données JSON de FX-08 (8 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/alertes/politiques",
    "corps": {
      "nom": "Code bleu — relance",
      "arret": "prise_en_charge",
      "etapes": [
        {
          "cible": "initiaux",
          "canaux": [
            "poussee"
          ],
          "delaiMinutes": 5
        },
        {
          "cible": "prochain_disponible",
          "cibleId": "{{garde}}",
          "canaux": [
            "texto"
          ],
          "delaiMinutes": 10
        },
        {
          "cible": "initiaux",
          "canaux": [
            "appel"
          ],
          "delaiMinutes": 15
        }
      ]
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "version": 1
      }
    },
    "capturer": {
      "politique": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/alertes/politiques",
    "corps": {
      "nom": "X",
      "arret": "jamais",
      "etapes": []
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Relance de garde",
      "corps": "Répondez",
      "priorite": "urgente",
      "reponseIds": [
        "{{arrive}}"
      ],
      "personnes": [
        "{{p_julie}}"
      ],
      "politiqueId": "{{politique}}"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "relancee": "id"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade",
    "attendu": {
      "statut": 200,
      "corps": {
        "cascade": {
          "etat": "armee",
          "etape": 1,
          "etapes": 3
        },
        "traces": [
          {
            "evenement": "armee"
          }
        ]
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 2
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "cascade": {
          "etat": "armee",
          "etape": 2
        }
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 9
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 1
    },
    "attendu": {
      "statut": 403
    }
  },
  {
    "acteur": "dir",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade",
    "attendu": {
      "statut": 200,
      "texte": "reprise"
    }
  }
]
```

</details>

**Essais Java** : `ControleurCascadesHttpTest.java`

### FX-020 — Accès mobile par navigateur, sans équipement dédié

*critère 20 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/mesalertes` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "mesalertes",
  "prealable": [],
  "preuve": [
    "mal-titre"
  ]
}
```

</details>

**Essais Java** : `fx-ao-mobile.spec.ts`

### FX-021 — Application mobile iOS et Android, si proposée

*critère 21 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/mesalertes` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "mesalertes",
  "prealable": [],
  "preuve": [
    "mal-titre"
  ]
}
```

</details>

**Scénario par l'API FX-16** (`fx-ao-comportements.spec.ts`) — Les applications iOS et Android s'abonnent aux alertes ; une plateforme inconnue est refusée

<details><summary>Données JSON de FX-16 (5 étapes)</summary>

```json
[
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/notifications/appareils",
    "corps": {
      "jeton": "ExponentPushToken[ios-{{suffixe}}]",
      "plateforme": "ios",
      "nom": "iPhone de Julie"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/notifications/appareils",
    "corps": {
      "jeton": "ExponentPushToken[android-{{suffixe}}]",
      "plateforme": "android",
      "nom": "Pixel de Julie"
    },
    "attendu": {
      "statut": 201
    }
  },
  {
    "acteur": "julie",
    "methode": "GET",
    "chemin": "/api/notifications/appareils",
    "attendu": {
      "statut": 200,
      "texte": "\"plateforme\":\"ios\"[\\s\\S]*\"plateforme\":\"android\"|\"plateforme\":\"android\"[\\s\\S]*\"plateforme\":\"ios\""
    }
  },
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/notifications/appareils",
    "corps": {
      "jeton": "ExponentPushToken[x-{{suffixe}}]",
      "plateforme": "blackberry"
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "pourquoi": "Un jeton qui n'est pas un jeton Expo est refusé",
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/notifications/appareils",
    "corps": {
      "jeton": "n-importe-quoi",
      "plateforme": "ios"
    },
    "attendu": {
      "statut": 400
    }
  }
]
```

</details>

### FX-022 — Préférences de communication par destinataire, sans contourner les règles obligatoires

*critère 22 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/account` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "account",
  "prealable": [],
  "preuve": [
    "account-tab-profile"
  ]
}
```

</details>

**Scénario par l'API FX-14** (`fx-ao-comportements.spec.ts`) — Une destinataire ferme le texto ; une alerte critique le lui envoie quand même (règle obligatoire), et couper tous les canaux écrits est refusé

<details><summary>Données JSON de FX-14 (5 étapes)</summary>

```json
[
  {
    "pourquoi": "Les préférences se règlent par la personne elle-même ; un canal écrit au moins reste actif (règle du serveur)",
    "acteur": "julie",
    "methode": "PUT",
    "chemin": "/api/notifications/preferences",
    "corps": {
      "courriel": true,
      "texto": false,
      "poussee": false
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "courriel": true,
        "texto": false,
        "poussee": false
      }
    }
  },
  {
    "pourquoi": "Couper tous les canaux écrits est refusé : une alerte doit pouvoir joindre la personne",
    "acteur": "julie",
    "methode": "PUT",
    "chemin": "/api/notifications/preferences",
    "corps": {
      "courriel": false,
      "texto": false,
      "poussee": false
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "julie",
    "methode": "GET",
    "chemin": "/api/notifications/preferences",
    "attendu": {
      "statut": 200,
      "corps": {
        "texto": false
      }
    }
  },
  {
    "pourquoi": "Une alerte critique impose tous les canaux, quelles que soient les préférences (Priorite.canauxImposes)",
    "acteur": "dir",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Code orange — afflux massif",
      "corps": "Rappel du personnel",
      "priorite": "critique",
      "personnes": [
        "{{p_julie}}"
      ]
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "priorite": "critique"
      }
    }
  },
  {
    "pourquoi": "Le journal des livraisons : le texto de l'alerte critique est tenté pour Julie, jamais « désactivé » par sa préférence",
    "methode": "GET",
    "chemin": "/api/notifications/livraisons?evenement=alerte.critique&canal=sms&destinataire={{u_julie}}",
    "attendu": {
      "statut": 200,
      "texte": "\"canal\":\"sms\"",
      "absent": "\"etat\":\"desactive\""
    },
    "reessayer": 10
  }
]
```

</details>

### FX-023 — Interface intuitive, rapide en contexte clinique

*critère 23 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/alnouvelle` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "alnouvelle",
  "prealable": [],
  "preuve": [
    "aln-formulaire"
  ]
}
```

</details>

**Scénario par l'API FX-04** (`fx-ao-comportements.spec.ts`) — La console envoie à un groupe ou à des personnes nommées ; critique réservée ; destinataire inconnu refusé

<details><summary>Données JSON de FX-04 (7 étapes)</summary>

```json
[
  {
    "pourquoi": "Recherche du destinataire par son nom dès trois caractères (critère 3)",
    "methode": "GET",
    "chemin": "/api/communication/groupes-transverses/personnes?q=jul",
    "attendu": {
      "statut": 200,
      "texte": "{{p_julie}}"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/groupes",
    "attendu": {
      "statut": 200,
      "texte": "Garde de nuit"
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Code bleu",
      "corps": "Chambre 512",
      "priorite": "urgente",
      "reponseIds": [
        "{{arrive}}",
        "{{indispo}}"
      ],
      "groupes": [
        "{{garde}}"
      ],
      "modeleId": "{{modele}}"
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "bilan": {
          "destinataires": 2,
          "lues": 0,
          "repondues": 0
        }
      }
    },
    "capturer": {
      "alerte": "id"
    }
  },
  {
    "pourquoi": "La priorité critique exige la capacité alertes.critique",
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Critique",
      "corps": "x",
      "priorite": "critique",
      "personnes": [
        "{{p_julie}}"
      ]
    },
    "attendu": {
      "statut": 403
    }
  },
  {
    "acteur": "dir",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Évacuation aile B",
      "corps": "Rendez-vous au poste",
      "priorite": "critique",
      "personnes": [
        "{{p_julie}}",
        "{{p_marc}}"
      ]
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "priorite": "critique",
        "bilan": {
          "destinataires": 2
        }
      }
    },
    "capturer": {
      "critique": "id"
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Info",
      "corps": "x",
      "priorite": "information",
      "personnes": [
        "inconnu-{{suffixe}}"
      ]
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "pourquoi": "Un destinataire n'envoie pas d'alerte",
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "x",
      "corps": "x",
      "priorite": "information",
      "personnes": [
        "{{p_marc}}"
      ]
    },
    "attendu": {
      "statut": 403
    }
  }
]
```

</details>

**Essais Java** : `fx-ao-mobile.spec.ts`

### FX-024 — Historique de ses messages et confirmations selon ses droits

*critère 24 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/mesalertes` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "mesalertes",
  "prealable": [],
  "preuve": [
    "mal-titre"
  ]
}
```

</details>

**Scénario par l'API FX-05** (`fx-ao-comportements.spec.ts`) — Le destinataire lit, puis prend en charge d'un geste ; une seconde réponse est refusée

<details><summary>Données JSON de FX-05 (5 étapes)</summary>

```json
[
  {
    "acteur": "julie",
    "methode": "GET",
    "chemin": "/api/alertes/recues",
    "attendu": {
      "statut": 200,
      "texte": "Code bleu"
    }
  },
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes/recues/{{alerte}}/lue",
    "attendu": {
      "statut": 200
    }
  },
  {
    "acteur": "marc",
    "methode": "POST",
    "chemin": "/api/alertes/recues/{{alerte}}/reponse",
    "corps": {
      "reponseId": "{{indispo}}"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes/recues/{{alerte}}/reponse",
    "corps": {
      "reponseId": "{{arrive}}"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "pourquoi": "Une réponse donnée ne se réécrit pas",
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes/recues/{{alerte}}/reponse",
    "corps": {
      "reponseId": "{{indispo}}"
    },
    "attendu": {
      "statut": 409
    }
  }
]
```

</details>

### FX-025 — Calendrier interne de disponibilité des destinataires

*critère 25 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/mesdisponibilites` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "mesdisponibilites",
  "prealable": [],
  "preuve": [
    "mdi-titre"
  ]
}
```

</details>

**Scénario par l'API FX-08** (`fx-ao-comportements.spec.ts`) — Une politique de relance arme une cascade ; l'expéditeur la reprend à l'étape choisie, et la trace le dit

<details><summary>Données JSON de FX-08 (8 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/alertes/politiques",
    "corps": {
      "nom": "Code bleu — relance",
      "arret": "prise_en_charge",
      "etapes": [
        {
          "cible": "initiaux",
          "canaux": [
            "poussee"
          ],
          "delaiMinutes": 5
        },
        {
          "cible": "prochain_disponible",
          "cibleId": "{{garde}}",
          "canaux": [
            "texto"
          ],
          "delaiMinutes": 10
        },
        {
          "cible": "initiaux",
          "canaux": [
            "appel"
          ],
          "delaiMinutes": 15
        }
      ]
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "version": 1
      }
    },
    "capturer": {
      "politique": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/alertes/politiques",
    "corps": {
      "nom": "X",
      "arret": "jamais",
      "etapes": []
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Relance de garde",
      "corps": "Répondez",
      "priorite": "urgente",
      "reponseIds": [
        "{{arrive}}"
      ],
      "personnes": [
        "{{p_julie}}"
      ],
      "politiqueId": "{{politique}}"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "relancee": "id"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade",
    "attendu": {
      "statut": 200,
      "corps": {
        "cascade": {
          "etat": "armee",
          "etape": 1,
          "etapes": 3
        },
        "traces": [
          {
            "evenement": "armee"
          }
        ]
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 2
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "cascade": {
          "etat": "armee",
          "etape": 2
        }
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 9
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 1
    },
    "attendu": {
      "statut": 403
    }
  },
  {
    "acteur": "dir",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade",
    "attendu": {
      "statut": 200,
      "texte": "reprise"
    }
  }
]
```

</details>

**Essais Java** : `ControleurCascadesHttpTest.java`

### FX-026 — Console de gestion centralisée pour les administrateurs

*critère 26 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/admin/admin` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "admin/admin",
  "prealable": [],
  "preuve": [
    "admin-page",
    "admin-users-list"
  ]
}
```

</details>

**Scénario par l'API FX-17** (`fx-ao-comportements.spec.ts`) — La console d'administration est centralisée et réservée : comptes, paramètres des alertes, journal

<details><summary>Données JSON de FX-17 (7 étapes)</summary>

```json
[
  {
    "methode": "GET",
    "chemin": "/api/auth/users",
    "attendu": {
      "statut": 200,
      "texte": "{{p_julie}}"
    }
  },
  {
    "methode": "GET",
    "chemin": "/api/alertes/reglages",
    "attendu": {
      "statut": 200
    }
  },
  {
    "methode": "GET",
    "chemin": "/api/alertes/reponses",
    "attendu": {
      "statut": 200
    }
  },
  {
    "methode": "GET",
    "chemin": "/api/auth/journal-actions",
    "attendu": {
      "statut": 200
    }
  },
  {
    "pourquoi": "Une destinataire n'administre rien",
    "acteur": "julie",
    "methode": "GET",
    "chemin": "/api/auth/users",
    "attendu": {
      "statut": 403
    }
  },
  {
    "acteur": "julie",
    "methode": "GET",
    "chemin": "/api/auth/journal-actions",
    "attendu": {
      "statut": 403
    }
  },
  {
    "pourquoi": "Une gestionnaire envoie et suit, mais ne règle pas l'organisme",
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/auth/users",
    "attendu": {
      "statut": 403
    }
  }
]
```

</details>

### FX-027 — Créer, modifier, désactiver utilisateurs et groupes (unité, service, spécialité, établissement)

*critère 27 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/admin/admin` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "admin/admin",
  "prealable": [],
  "preuve": [
    "admin-create",
    "admin-role"
  ]
}
```

</details>

**Scénario par l'API FX-13** (`fx-ao-comportements.spec.ts`) — Un accès se désactive aussitôt, et le journal des actions le trace

<details><summary>Données JSON de FX-13 (3 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/auth/users",
    "corps": {
      "username": "temp-{{suffixe}}",
      "password": "{{mdp}}",
      "role": "employee",
      "mustChangePassword": false
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "temp": "id"
    }
  },
  {
    "methode": "PATCH",
    "chemin": "/api/auth/users/{{temp}}",
    "corps": {
      "active": false
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "methode": "GET",
    "chemin": "/api/auth/journal-actions",
    "attendu": {
      "statut": 200,
      "texte": "/api/auth/users"
    }
  }
]
```

</details>

### FX-028 — Cloisonner les utilisateurs dans un ou plusieurs groupes

*critère 28 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/alparametres` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "alparametres",
  "prealable": [],
  "preuve": [
    "prefixe:alp-gt"
  ]
}
```

</details>

**Scénario par l'API FX-04** (`fx-ao-comportements.spec.ts`) — La console envoie à un groupe ou à des personnes nommées ; critique réservée ; destinataire inconnu refusé

<details><summary>Données JSON de FX-04 (7 étapes)</summary>

```json
[
  {
    "pourquoi": "Recherche du destinataire par son nom dès trois caractères (critère 3)",
    "methode": "GET",
    "chemin": "/api/communication/groupes-transverses/personnes?q=jul",
    "attendu": {
      "statut": 200,
      "texte": "{{p_julie}}"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/groupes",
    "attendu": {
      "statut": 200,
      "texte": "Garde de nuit"
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Code bleu",
      "corps": "Chambre 512",
      "priorite": "urgente",
      "reponseIds": [
        "{{arrive}}",
        "{{indispo}}"
      ],
      "groupes": [
        "{{garde}}"
      ],
      "modeleId": "{{modele}}"
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "bilan": {
          "destinataires": 2,
          "lues": 0,
          "repondues": 0
        }
      }
    },
    "capturer": {
      "alerte": "id"
    }
  },
  {
    "pourquoi": "La priorité critique exige la capacité alertes.critique",
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Critique",
      "corps": "x",
      "priorite": "critique",
      "personnes": [
        "{{p_julie}}"
      ]
    },
    "attendu": {
      "statut": 403
    }
  },
  {
    "acteur": "dir",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Évacuation aile B",
      "corps": "Rendez-vous au poste",
      "priorite": "critique",
      "personnes": [
        "{{p_julie}}",
        "{{p_marc}}"
      ]
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "priorite": "critique",
        "bilan": {
          "destinataires": 2
        }
      }
    },
    "capturer": {
      "critique": "id"
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Info",
      "corps": "x",
      "priorite": "information",
      "personnes": [
        "inconnu-{{suffixe}}"
      ]
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "pourquoi": "Un destinataire n'envoie pas d'alerte",
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "x",
      "corps": "x",
      "priorite": "information",
      "personnes": [
        "{{p_marc}}"
      ]
    },
    "attendu": {
      "statut": 403
    }
  }
]
```

</details>

**Essais Java** : `ControleurGroupesTransversesHttpTest.java`

### FX-029 — Coordonnées des destinataires confidentielles

*critère 29 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/usappels` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "usappels",
  "prealable": [],
  "preuve": [
    "prefixe:sq-appels"
  ]
}
```

</details>

**Scénario par l'API FX-15** (`fx-ao-comportements.spec.ts`) — Le numéro de téléphone d'une personne n'est jamais rendu à un expéditeur ni à un destinataire

<details><summary>Données JSON de FX-15 (5 étapes)</summary>

```json
[
  {
    "pourquoi": "Une fiche porte un vrai numéro",
    "methode": "POST",
    "chemin": "/api/personnel/employes",
    "corps": {
      "firstName": "Nadia",
      "lastName": "Confidentielle",
      "email": "nadia.c@exemple.test",
      "phone": "+1 514 555 0199",
      "jobTitle": "Inhalothérapeute"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "nadia": "id"
    }
  },
  {
    "pourquoi": "La gestionnaire (expéditrice) lit la liste : le nom, jamais le numéro",
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/personnel/employes",
    "attendu": {
      "statut": 200,
      "texte": "Confidentielle",
      "absent": "555.?0199|5145550199"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/personnel/employes/{{nadia}}",
    "attendu": {
      "statut": 200,
      "texte": "Nadia",
      "absent": "555.?0199|5145550199"
    }
  },
  {
    "pourquoi": "Une destinataire cherche une collègue : le numéro n'y est pas non plus",
    "acteur": "julie",
    "methode": "GET",
    "chemin": "/api/personnel/employes/suggestions?q=Confid",
    "attendu": {
      "statut": 200,
      "texte": "Confidentielle",
      "absent": "555.?0199|5145550199"
    }
  },
  {
    "pourquoi": "Même l'administration ne le relit pas dans la liste : il ne sert qu'à l'acheminement",
    "methode": "GET",
    "chemin": "/api/personnel/employes",
    "attendu": {
      "statut": 200,
      "absent": "555.?0199|5145550199"
    }
  }
]
```

</details>

### FX-030 — Comptes utilisateur et administrateur, profils plus granulaires

*critère 30 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/access` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "access",
  "prealable": [],
  "preuve": [
    "access-tabs"
  ]
}
```

</details>

**Scénario par l'API FX-03** (`fx-ao-comportements.spec.ts`) — Les droits se lisent pour l'écran : la gestionnaire envoie sans priorité critique ; la directrice a la critique

<details><summary>Données JSON de FX-03 (3 étapes)</summary>

```json
[
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/droits",
    "attendu": {
      "statut": 200,
      "corps": {
        "envoyer": true,
        "critique": false,
        "configurer": false
      }
    }
  },
  {
    "acteur": "dir",
    "methode": "GET",
    "chemin": "/api/alertes/droits",
    "attendu": {
      "statut": 200,
      "corps": {
        "envoyer": true,
        "critique": true,
        "configurer": false
      }
    }
  },
  {
    "acteur": "julie",
    "methode": "GET",
    "chemin": "/api/alertes/droits",
    "attendu": {
      "statut": 200,
      "corps": {
        "envoyer": false,
        "critique": false,
        "configurer": false
      }
    }
  }
]
```

</details>

### FX-031 — Écran de visionnement des messages envoyés et des statuts

*critère 31 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/alsuivi` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "alsuivi",
  "prealable": [],
  "preuve": [
    "als-table|als-vide"
  ]
}
```

</details>

**Scénario par l'API FX-06** (`fx-ao-comportements.spec.ts`) — Le suivi est partagé entre expéditeurs, filtrable et exportable ; il dit qui a lu, répondu, pris en charge

<details><summary>Données JSON de FX-06 (6 étapes)</summary>

```json
[
  {
    "pourquoi": "La directrice voit l'alerte de la gestionnaire (critère 12) ; répondre vaut lecture",
    "acteur": "dir",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/{{alerte}}",
    "attendu": {
      "statut": 200,
      "corps": {
        "bilan": {
          "destinataires": 2,
          "lues": 2,
          "repondues": 2,
          "relances": 0
        }
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees?etat=prise_en_charge",
    "attendu": {
      "statut": 200,
      "texte": "Code bleu"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees?etat=sans_prise_en_charge",
    "attendu": {
      "statut": 200,
      "texte": "Évacuation aile B"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees?etat=perdue",
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/export?format=csv",
    "attendu": {
      "statut": 200,
      "texte": "relances"
    }
  },
  {
    "pourquoi": "L'export PDF est un PDF (PDF/A-2b, critère 39 ; veraPDF en CI)",
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/export?format=pdf",
    "attendu": {
      "statut": 200,
      "texte": "^%PDF-"
    }
  }
]
```

</details>

### FX-032 — Configuration des messages et réponses prédéfinis, relances, groupes, cascades, droits, disponibilité

*critère 32 · fonctionnel · statut ✅*

**Scénario d'écran** (`fx-ao-exigences.spec.ts`) : ouvrir `/alparametres` ; constater les commandes, puis axe-core.

<details><summary>Données JSON du scénario d'écran</summary>

```json
{
  "page": "alparametres",
  "prealable": [],
  "preuve": [
    "alp-ecran",
    "alp-politiques",
    "alp-reglages"
  ]
}
```

</details>

**Scénario par l'API FX-01** (`fx-ao-comportements.spec.ts`) — Les réponses prédéfinies ont une sémantique fermée ; seule l'administration les tient

<details><summary>Données JSON de FX-01 (4 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/alertes/reponses",
    "corps": {
      "libelle": "J'arrive",
      "semantique": "prise_en_charge"
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "libelle": "J'arrive",
        "semantique": "prise_en_charge",
        "active": true
      }
    },
    "capturer": {
      "arrive": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/alertes/reponses",
    "corps": {
      "libelle": "Indisponible",
      "semantique": "refus"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "indispo": "id"
    }
  },
  {
    "pourquoi": "Une sémantique hors de l'ensemble fermé est refusée",
    "methode": "POST",
    "chemin": "/api/alertes/reponses",
    "corps": {
      "libelle": "Peut-être",
      "semantique": "oui"
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "pourquoi": "La gestionnaire envoie, elle ne paramètre pas",
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes/reponses",
    "corps": {
      "libelle": "X",
      "semantique": "refus"
    },
    "attendu": {
      "statut": 403
    }
  }
]
```

</details>

**Scénario par l'API FX-02** (`fx-ao-comportements.spec.ts`) — Un modèle d'alerte propose ses réponses et se versionne à chaque modification

<details><summary>Données JSON de FX-02 (5 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/alertes/modeles",
    "corps": {
      "titre": "Code bleu",
      "corps": "Chambre 512, 5e étage",
      "priorite": "urgente",
      "reponseIds": [
        "{{arrive}}",
        "{{indispo}}"
      ]
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "version": 1,
        "actif": true
      }
    },
    "capturer": {
      "modele": "id"
    }
  },
  {
    "methode": "PUT",
    "chemin": "/api/alertes/modeles/{{modele}}",
    "corps": {
      "titre": "Code bleu — 5e",
      "corps": "Chambre 512, 5e étage, aile B",
      "priorite": "urgente",
      "reponseIds": [
        "{{arrive}}",
        "{{indispo}}"
      ]
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "version": 2
      }
    }
  },
  {
    "methode": "GET",
    "chemin": "/api/alertes/modeles/{{modele}}/versions",
    "attendu": {
      "statut": 200,
      "corps": [
        {
          "version": 2
        },
        {
          "version": 1
        }
      ]
    }
  },
  {
    "pourquoi": "La priorité est un ensemble fermé",
    "methode": "POST",
    "chemin": "/api/alertes/modeles",
    "corps": {
      "titre": "X",
      "corps": "Y",
      "priorite": "haute"
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "pourquoi": "La gestionnaire voit le modèle actif",
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/modeles",
    "attendu": {
      "statut": 200,
      "texte": "Code bleu — 5e"
    }
  }
]
```

</details>

**Scénario par l'API FX-07** (`fx-ao-comportements.spec.ts`) — La copie par courriel se règle par organisme

<details><summary>Données JSON de FX-07 (3 étapes)</summary>

```json
[
  {
    "methode": "PUT",
    "chemin": "/api/alertes/reglages",
    "corps": {
      "copieCourriel": true
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "copieCourriel": true
      }
    }
  },
  {
    "methode": "GET",
    "chemin": "/api/alertes/reglages",
    "attendu": {
      "statut": 200,
      "corps": {
        "copieCourriel": true
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "PUT",
    "chemin": "/api/alertes/reglages",
    "corps": {
      "copieCourriel": false
    },
    "attendu": {
      "statut": 403
    }
  }
]
```

</details>

**Scénario par l'API FX-08** (`fx-ao-comportements.spec.ts`) — Une politique de relance arme une cascade ; l'expéditeur la reprend à l'étape choisie, et la trace le dit

<details><summary>Données JSON de FX-08 (8 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/alertes/politiques",
    "corps": {
      "nom": "Code bleu — relance",
      "arret": "prise_en_charge",
      "etapes": [
        {
          "cible": "initiaux",
          "canaux": [
            "poussee"
          ],
          "delaiMinutes": 5
        },
        {
          "cible": "prochain_disponible",
          "cibleId": "{{garde}}",
          "canaux": [
            "texto"
          ],
          "delaiMinutes": 10
        },
        {
          "cible": "initiaux",
          "canaux": [
            "appel"
          ],
          "delaiMinutes": 15
        }
      ]
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "version": 1
      }
    },
    "capturer": {
      "politique": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/alertes/politiques",
    "corps": {
      "nom": "X",
      "arret": "jamais",
      "etapes": []
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Relance de garde",
      "corps": "Répondez",
      "priorite": "urgente",
      "reponseIds": [
        "{{arrive}}"
      ],
      "personnes": [
        "{{p_julie}}"
      ],
      "politiqueId": "{{politique}}"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "relancee": "id"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade",
    "attendu": {
      "statut": 200,
      "corps": {
        "cascade": {
          "etat": "armee",
          "etape": 1,
          "etapes": 3
        },
        "traces": [
          {
            "evenement": "armee"
          }
        ]
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 2
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "cascade": {
          "etat": "armee",
          "etape": 2
        }
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 9
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 1
    },
    "attendu": {
      "statut": 403
    }
  },
  {
    "acteur": "dir",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade",
    "attendu": {
      "statut": 200,
      "texte": "reprise"
    }
  }
]
```

</details>

### FX-033 — Fonctionne sur les postes standards de l'établissement

*critère 33 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-034 — Compatible Windows 11

*critère 34 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-035 — Impression par le service Windows ou les paramètres locaux

*critère 35 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-036 — Standards de nomenclature de l'établissement

*critère 36 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-037 — Pilotes universels d'imprimantes HP, Ricoh, Xerox

*critère 37 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-038 — Compatible Microsoft Office

*critère 38 · technique · statut ✅*

**Scénario par l'API FX-06** (`fx-ao-comportements.spec.ts`) — Le suivi est partagé entre expéditeurs, filtrable et exportable ; il dit qui a lu, répondu, pris en charge

<details><summary>Données JSON de FX-06 (6 étapes)</summary>

```json
[
  {
    "pourquoi": "La directrice voit l'alerte de la gestionnaire (critère 12) ; répondre vaut lecture",
    "acteur": "dir",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/{{alerte}}",
    "attendu": {
      "statut": 200,
      "corps": {
        "bilan": {
          "destinataires": 2,
          "lues": 2,
          "repondues": 2,
          "relances": 0
        }
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees?etat=prise_en_charge",
    "attendu": {
      "statut": 200,
      "texte": "Code bleu"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees?etat=sans_prise_en_charge",
    "attendu": {
      "statut": 200,
      "texte": "Évacuation aile B"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees?etat=perdue",
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/export?format=csv",
    "attendu": {
      "statut": 200,
      "texte": "relances"
    }
  },
  {
    "pourquoi": "L'export PDF est un PDF (PDF/A-2b, critère 39 ; veraPDF en CI)",
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/export?format=pdf",
    "attendu": {
      "statut": 200,
      "texte": "^%PDF-"
    }
  }
]
```

</details>

### FX-039 — PDF/A lors de l'extraction en PDF

*critère 39 · technique · statut 🟡*

**Scénario par l'API FX-06** (`fx-ao-comportements.spec.ts`) — Le suivi est partagé entre expéditeurs, filtrable et exportable ; il dit qui a lu, répondu, pris en charge

<details><summary>Données JSON de FX-06 (6 étapes)</summary>

```json
[
  {
    "pourquoi": "La directrice voit l'alerte de la gestionnaire (critère 12) ; répondre vaut lecture",
    "acteur": "dir",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/{{alerte}}",
    "attendu": {
      "statut": 200,
      "corps": {
        "bilan": {
          "destinataires": 2,
          "lues": 2,
          "repondues": 2,
          "relances": 0
        }
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees?etat=prise_en_charge",
    "attendu": {
      "statut": 200,
      "texte": "Code bleu"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees?etat=sans_prise_en_charge",
    "attendu": {
      "statut": 200,
      "texte": "Évacuation aile B"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees?etat=perdue",
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/export?format=csv",
    "attendu": {
      "statut": 200,
      "texte": "relances"
    }
  },
  {
    "pourquoi": "L'export PDF est un PDF (PDF/A-2b, critère 39 ; veraPDF en CI)",
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/export?format=pdf",
    "attendu": {
      "statut": 200,
      "texte": "^%PDF-"
    }
  }
]
```

</details>

**Essais Java** : `PdfAConformiteTest.java`

### FX-040 — Courriel Microsoft Outlook 365

*critère 40 · technique · statut ✅*

**Essais Java** : `CourrierGraphTest.java`

> **Aucun scénario e2e** — prouvé hors e2e (essais Java, infrastructure ou documentation cités)

### FX-041 — Fonctionnelle sur réseaux étendus (WAN)

*critère 41 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-042 — Normes réseau Ethernet, TCP/IP

*critère 42 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-043 — Adressage privé 10.x

*critère 43 · technique · statut 🏢*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-044 — Aucun « broadcasting »

*critère 44 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-045 — WPA2

*critère 45 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-046 — 802.11 a/b/g/n/ac/ax

*critère 46 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-047 — Résolution DNS

*critère 47 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-048 — 802.1x (EAP-TLS, PEAP)

*critère 48 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-049 — Filtrage Web et mandataire

*critère 49 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-050 — SaaS, nuage privé ou installation locale

*critère 50 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-051 — Windows Server 2019+ sur VMware vSphere

*critère 51 · technique · statut ✅*

**Preuve d'infrastructure ou d'exploitation** : `docs/FX-AO-20260810_Dossier/07_validation_vina_geps/09_exploitation_chez_l_etablissement.md`

> **Aucun scénario e2e** — prouvé hors e2e (essais Java, infrastructure ou documentation cités)

### FX-052 — Accessible dans Citrix

*critère 52 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-053 — Données hébergées au Canada

*critère 53 · technique · statut ✅*

**Preuve d'infrastructure ou d'exploitation** : `infra/terraform/aws/versions.tf`

> **Aucun scénario e2e** — prouvé hors e2e (essais Java, infrastructure ou documentation cités)

### FX-054 — Systèmes, navigateurs, versions et dépendances documentés

*critère 54 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-055 — Architecture technologique décrite

*critère 55 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-056 — Infrastructures gérées par l'établissement

*critère 56 · technique · statut 🏢*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-057 — Infrastructures acquises par l'établissement

*critère 57 · technique · statut 🏢*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-058 — Système d'exploitation installé par l'établissement

*critère 58 · technique · statut 🏢*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-059 — Nombre de VM, vCPU et mémoire

*critère 59 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-060 — Volumes de stockage par VM

*critère 60 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-061 — Virtualisable sur vSphere 8+

*critère 61 · technique · statut ✅*

**Preuve d'infrastructure ou d'exploitation** : `docs/FX-AO-20260810_Dossier/07_validation_vina_geps/09_exploitation_chez_l_etablissement.md`

> **Aucun scénario e2e** — prouvé hors e2e (essais Java, infrastructure ou documentation cités)

### FX-062 — Heure NTP de l'établissement

*critère 62 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-063 — Correctifs de sécurité dès leur disponibilité

*critère 63 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-064 — Compatible avec l'agent de sauvegarde

*critère 64 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-065 — Procédure de sauvegarde de tous les environnements

*critère 65 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-066 — Procédure d'archivage

*critère 66 · technique · statut ✅*

**Preuve d'infrastructure ou d'exploitation** : `docs/FX-AO-20260810_Dossier/07_validation_vina_geps/09_exploitation_chez_l_etablissement.md`

> **Aucun scénario e2e** — prouvé hors e2e (essais Java, infrastructure ou documentation cités)

### FX-067 — Destruction permanente des données

*critère 67 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-068 — Tests de restauration périodiques accompagnés

*critère 68 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-069 — Compatible Microsoft Defender EDR

*critère 69 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-070 — Surveillance Zabbix acceptée

*critère 70 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-071 — Lois et politiques (Loi 25)

*critère 71 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-072 — Confidentialité, intégrité, disponibilité

*critère 72 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-073 — Chiffrement en transit

*critère 73 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-074 — Chiffrement au repos, sauvegardes comprises

*critère 74 · technique · statut ✅*

**Preuve d'infrastructure ou d'exploitation** : `infra/terraform/aws/sauvegardes-hors-region.tf`

> **Aucun scénario e2e** — prouvé hors e2e (essais Java, infrastructure ou documentation cités)

### FX-075 — Surveillance et gestion des incidents de sécurité

*critère 75 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-076 — Avis sans délai d'un incident

*critère 76 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-077 — Accès fondé sur les rôles et permissions

*critère 77 · technique · statut ✅*

**Scénario par l'API FX-01** (`fx-ao-comportements.spec.ts`) — Les réponses prédéfinies ont une sémantique fermée ; seule l'administration les tient

<details><summary>Données JSON de FX-01 (4 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/alertes/reponses",
    "corps": {
      "libelle": "J'arrive",
      "semantique": "prise_en_charge"
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "libelle": "J'arrive",
        "semantique": "prise_en_charge",
        "active": true
      }
    },
    "capturer": {
      "arrive": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/alertes/reponses",
    "corps": {
      "libelle": "Indisponible",
      "semantique": "refus"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "indispo": "id"
    }
  },
  {
    "pourquoi": "Une sémantique hors de l'ensemble fermé est refusée",
    "methode": "POST",
    "chemin": "/api/alertes/reponses",
    "corps": {
      "libelle": "Peut-être",
      "semantique": "oui"
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "pourquoi": "La gestionnaire envoie, elle ne paramètre pas",
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes/reponses",
    "corps": {
      "libelle": "X",
      "semantique": "refus"
    },
    "attendu": {
      "statut": 403
    }
  }
]
```

</details>

**Scénario par l'API FX-03** (`fx-ao-comportements.spec.ts`) — Les droits se lisent pour l'écran : la gestionnaire envoie sans priorité critique ; la directrice a la critique

<details><summary>Données JSON de FX-03 (3 étapes)</summary>

```json
[
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/droits",
    "attendu": {
      "statut": 200,
      "corps": {
        "envoyer": true,
        "critique": false,
        "configurer": false
      }
    }
  },
  {
    "acteur": "dir",
    "methode": "GET",
    "chemin": "/api/alertes/droits",
    "attendu": {
      "statut": 200,
      "corps": {
        "envoyer": true,
        "critique": true,
        "configurer": false
      }
    }
  },
  {
    "acteur": "julie",
    "methode": "GET",
    "chemin": "/api/alertes/droits",
    "attendu": {
      "statut": 200,
      "corps": {
        "envoyer": false,
        "critique": false,
        "configurer": false
      }
    }
  }
]
```

</details>

### FX-078 — Privilège minimal

*critère 78 · technique · statut ✅*

**Scénario par l'API FX-03** (`fx-ao-comportements.spec.ts`) — Les droits se lisent pour l'écran : la gestionnaire envoie sans priorité critique ; la directrice a la critique

<details><summary>Données JSON de FX-03 (3 étapes)</summary>

```json
[
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/droits",
    "attendu": {
      "statut": 200,
      "corps": {
        "envoyer": true,
        "critique": false,
        "configurer": false
      }
    }
  },
  {
    "acteur": "dir",
    "methode": "GET",
    "chemin": "/api/alertes/droits",
    "attendu": {
      "statut": 200,
      "corps": {
        "envoyer": true,
        "critique": true,
        "configurer": false
      }
    }
  },
  {
    "acteur": "julie",
    "methode": "GET",
    "chemin": "/api/alertes/droits",
    "attendu": {
      "statut": 200,
      "corps": {
        "envoyer": false,
        "critique": false,
        "configurer": false
      }
    }
  }
]
```

</details>

### FX-079 — Aucun compte générique

*critère 79 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-080 — Révocation rapide d'un accès

*critère 80 · technique · statut ✅*

**Scénario par l'API FX-13** (`fx-ao-comportements.spec.ts`) — Un accès se désactive aussitôt, et le journal des actions le trace

<details><summary>Données JSON de FX-13 (3 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/auth/users",
    "corps": {
      "username": "temp-{{suffixe}}",
      "password": "{{mdp}}",
      "role": "employee",
      "mustChangePassword": false
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "temp": "id"
    }
  },
  {
    "methode": "PATCH",
    "chemin": "/api/auth/users/{{temp}}",
    "corps": {
      "active": false
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "methode": "GET",
    "chemin": "/api/auth/journal-actions",
    "attendu": {
      "statut": 200,
      "texte": "/api/auth/users"
    }
  }
]
```

</details>

### FX-081 — Intégration aux mécanismes d'identité

*critère 81 · technique · statut ✅*

**Scénario par l'API FX-12** (`fx-ao-comportements.spec.ts`) — Un annuaire LDAP ne se déclare qu'en LDAPS

<details><summary>Données JSON de FX-12 (2 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/auth/annuaires",
    "corps": {
      "nom": "AD local",
      "fournisseur": "ldaps",
      "emetteur": "ldap://ad.cissslau.local:389/DC=cissslau,DC=local",
      "clientId": "CN=svc-geps,DC=cissslau,DC=local",
      "variableSecret": "ANNUAIRE_AD",
      "domaines": [
        "ssss.gouv.qc.ca"
      ],
      "roleParDefaut": "employee"
    },
    "attendu": {
      "statut": 400,
      "texte": "ldap:// en clair est refusé"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/auth/annuaires",
    "corps": {
      "nom": "AD local",
      "fournisseur": "ldaps",
      "emetteur": "ldaps://ad.cissslau.local:636/OU=Personnel,DC=cissslau,DC=local",
      "clientId": "CN=svc-geps,DC=cissslau,DC=local",
      "variableSecret": "ANNUAIRE_AD",
      "domaines": [
        "ssss.gouv.qc.ca"
      ],
      "roleParDefaut": "employee"
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "fournisseur": "ldaps"
      }
    }
  }
]
```

</details>

**Essais Java** : `ControleurAnnuairesHttpTest.java`

### FX-082 — LDAPS pour les échanges LDAP

*critère 82 · technique · statut ✅*

**Scénario par l'API FX-12** (`fx-ao-comportements.spec.ts`) — Un annuaire LDAP ne se déclare qu'en LDAPS

<details><summary>Données JSON de FX-12 (2 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/auth/annuaires",
    "corps": {
      "nom": "AD local",
      "fournisseur": "ldaps",
      "emetteur": "ldap://ad.cissslau.local:389/DC=cissslau,DC=local",
      "clientId": "CN=svc-geps,DC=cissslau,DC=local",
      "variableSecret": "ANNUAIRE_AD",
      "domaines": [
        "ssss.gouv.qc.ca"
      ],
      "roleParDefaut": "employee"
    },
    "attendu": {
      "statut": 400,
      "texte": "ldap:// en clair est refusé"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/auth/annuaires",
    "corps": {
      "nom": "AD local",
      "fournisseur": "ldaps",
      "emetteur": "ldaps://ad.cissslau.local:636/OU=Personnel,DC=cissslau,DC=local",
      "clientId": "CN=svc-geps,DC=cissslau,DC=local",
      "variableSecret": "ANNUAIRE_AD",
      "domaines": [
        "ssss.gouv.qc.ca"
      ],
      "roleParDefaut": "employee"
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "fournisseur": "ldaps"
      }
    }
  }
]
```

</details>

**Essais Java** : `LiaisonLdapsTest.java`, `AnnuairesTest.java`

### FX-083 — Information GIA remise au pilote

*critère 83 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-084 — Aucun certificat wildcard

*critère 84 · technique · statut ✅*

**Preuve d'infrastructure ou d'exploitation** : `infra/terraform/aws/certificat.tf`, `infra/terraform/aws/tests/certificat-nominatif.tftest.hcl`

> **Aucun scénario e2e** — prouvé hors e2e (essais Java, infrastructure ou documentation cités)

### FX-085 — Mot de passe : 8 caractères, 4 classes

*critère 85 · technique · statut ✅*

**Scénario par l'API FX-11** (`fx-ao-comportements.spec.ts`) — Mots de passe : la politique se vérifie ; le changement refuse un mot de passe déjà employé

<details><summary>Données JSON de FX-11 (3 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/auth/password-policy/check",
    "corps": {
      "password": "court"
    },
    "attendu": {
      "statut": 200,
      "texte": "issues"
    }
  },
  {
    "acteur": "marc",
    "methode": "POST",
    "chemin": "/api/auth/change-password",
    "corps": {
      "oldPassword": "{{mdp}}",
      "newPassword": "Nouveau!Passe2026x"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "pourquoi": "Un des 10 derniers mots de passe des 6 derniers mois est refusé",
    "acteur": "marc",
    "methode": "POST",
    "chemin": "/api/auth/change-password",
    "corps": {
      "oldPassword": "Nouveau!Passe2026x",
      "newPassword": "{{mdp}}"
    },
    "attendu": {
      "statut": 400
    }
  }
]
```

</details>

**Essais Java** : `PolitiqueMotDePasseTest.java`

### FX-086 — 10 derniers mots de passe refusés pendant 6 mois

*critère 86 · technique · statut ✅*

**Scénario par l'API FX-11** (`fx-ao-comportements.spec.ts`) — Mots de passe : la politique se vérifie ; le changement refuse un mot de passe déjà employé

<details><summary>Données JSON de FX-11 (3 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/auth/password-policy/check",
    "corps": {
      "password": "court"
    },
    "attendu": {
      "statut": 200,
      "texte": "issues"
    }
  },
  {
    "acteur": "marc",
    "methode": "POST",
    "chemin": "/api/auth/change-password",
    "corps": {
      "oldPassword": "{{mdp}}",
      "newPassword": "Nouveau!Passe2026x"
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "pourquoi": "Un des 10 derniers mots de passe des 6 derniers mois est refusé",
    "acteur": "marc",
    "methode": "POST",
    "chemin": "/api/auth/change-password",
    "corps": {
      "oldPassword": "Nouveau!Passe2026x",
      "newPassword": "{{mdp}}"
    },
    "attendu": {
      "statut": 400
    }
  }
]
```

</details>

**Essais Java** : `HistoriqueMotsDePasseTest.java`

### FX-087 — Aucun privilège d'administration à l'exécution

*critère 87 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-088 — Service F pour l'accès à distance

*critère 88 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-089 — Traçabilité complète des communications

*critère 89 · technique · statut ✅*

**Scénario par l'API FX-06** (`fx-ao-comportements.spec.ts`) — Le suivi est partagé entre expéditeurs, filtrable et exportable ; il dit qui a lu, répondu, pris en charge

<details><summary>Données JSON de FX-06 (6 étapes)</summary>

```json
[
  {
    "pourquoi": "La directrice voit l'alerte de la gestionnaire (critère 12) ; répondre vaut lecture",
    "acteur": "dir",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/{{alerte}}",
    "attendu": {
      "statut": 200,
      "corps": {
        "bilan": {
          "destinataires": 2,
          "lues": 2,
          "repondues": 2,
          "relances": 0
        }
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees?etat=prise_en_charge",
    "attendu": {
      "statut": 200,
      "texte": "Code bleu"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees?etat=sans_prise_en_charge",
    "attendu": {
      "statut": 200,
      "texte": "Évacuation aile B"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees?etat=perdue",
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/export?format=csv",
    "attendu": {
      "statut": 200,
      "texte": "relances"
    }
  },
  {
    "pourquoi": "L'export PDF est un PDF (PDF/A-2b, critère 39 ; veraPDF en CI)",
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/export?format=pdf",
    "attendu": {
      "statut": 200,
      "texte": "^%PDF-"
    }
  }
]
```

</details>

**Scénario par l'API FX-08** (`fx-ao-comportements.spec.ts`) — Une politique de relance arme une cascade ; l'expéditeur la reprend à l'étape choisie, et la trace le dit

<details><summary>Données JSON de FX-08 (8 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/alertes/politiques",
    "corps": {
      "nom": "Code bleu — relance",
      "arret": "prise_en_charge",
      "etapes": [
        {
          "cible": "initiaux",
          "canaux": [
            "poussee"
          ],
          "delaiMinutes": 5
        },
        {
          "cible": "prochain_disponible",
          "cibleId": "{{garde}}",
          "canaux": [
            "texto"
          ],
          "delaiMinutes": 10
        },
        {
          "cible": "initiaux",
          "canaux": [
            "appel"
          ],
          "delaiMinutes": 15
        }
      ]
    },
    "attendu": {
      "statut": 201,
      "corps": {
        "version": 1
      }
    },
    "capturer": {
      "politique": "id"
    }
  },
  {
    "methode": "POST",
    "chemin": "/api/alertes/politiques",
    "corps": {
      "nom": "X",
      "arret": "jamais",
      "etapes": []
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes",
    "corps": {
      "titre": "Relance de garde",
      "corps": "Répondez",
      "priorite": "urgente",
      "reponseIds": [
        "{{arrive}}"
      ],
      "personnes": [
        "{{p_julie}}"
      ],
      "politiqueId": "{{politique}}"
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "relancee": "id"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade",
    "attendu": {
      "statut": 200,
      "corps": {
        "cascade": {
          "etat": "armee",
          "etape": 1,
          "etapes": 3
        },
        "traces": [
          {
            "evenement": "armee"
          }
        ]
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 2
    },
    "attendu": {
      "statut": 200,
      "corps": {
        "cascade": {
          "etat": "armee",
          "etape": 2
        }
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 9
    },
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "julie",
    "methode": "POST",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade/reprendre",
    "corps": {
      "etape": 1
    },
    "attendu": {
      "statut": 403
    }
  },
  {
    "acteur": "dir",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/{{relancee}}/cascade",
    "attendu": {
      "statut": 200,
      "texte": "reprise"
    }
  }
]
```

</details>

### FX-090 — Journaux protégés, accès autorisé seulement

*critère 90 · technique · statut ✅*

**Scénario par l'API FX-13** (`fx-ao-comportements.spec.ts`) — Un accès se désactive aussitôt, et le journal des actions le trace

<details><summary>Données JSON de FX-13 (3 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/auth/users",
    "corps": {
      "username": "temp-{{suffixe}}",
      "password": "{{mdp}}",
      "role": "employee",
      "mustChangePassword": false
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "temp": "id"
    }
  },
  {
    "methode": "PATCH",
    "chemin": "/api/auth/users/{{temp}}",
    "corps": {
      "active": false
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "methode": "GET",
    "chemin": "/api/auth/journal-actions",
    "attendu": {
      "statut": 200,
      "texte": "/api/auth/users"
    }
  }
]
```

</details>

### FX-091 — Conservation et exportation des journaux documentées

*critère 91 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-092 — Audit sans préavis accepté

*critère 92 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-093 — Journaux extraits en format exploitable

*critère 93 · technique · statut ✅*

**Scénario par l'API FX-06** (`fx-ao-comportements.spec.ts`) — Le suivi est partagé entre expéditeurs, filtrable et exportable ; il dit qui a lu, répondu, pris en charge

<details><summary>Données JSON de FX-06 (6 étapes)</summary>

```json
[
  {
    "pourquoi": "La directrice voit l'alerte de la gestionnaire (critère 12) ; répondre vaut lecture",
    "acteur": "dir",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/{{alerte}}",
    "attendu": {
      "statut": 200,
      "corps": {
        "bilan": {
          "destinataires": 2,
          "lues": 2,
          "repondues": 2,
          "relances": 0
        }
      }
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees?etat=prise_en_charge",
    "attendu": {
      "statut": 200,
      "texte": "Code bleu"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees?etat=sans_prise_en_charge",
    "attendu": {
      "statut": 200,
      "texte": "Évacuation aile B"
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees?etat=perdue",
    "attendu": {
      "statut": 400
    }
  },
  {
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/export?format=csv",
    "attendu": {
      "statut": 200,
      "texte": "relances"
    }
  },
  {
    "pourquoi": "L'export PDF est un PDF (PDF/A-2b, critère 39 ; veraPDF en CI)",
    "acteur": "chef",
    "methode": "GET",
    "chemin": "/api/alertes/envoyees/export?format=pdf",
    "attendu": {
      "statut": 200,
      "texte": "^%PDF-"
    }
  }
]
```

</details>

**Scénario par l'API FX-13** (`fx-ao-comportements.spec.ts`) — Un accès se désactive aussitôt, et le journal des actions le trace

<details><summary>Données JSON de FX-13 (3 étapes)</summary>

```json
[
  {
    "methode": "POST",
    "chemin": "/api/auth/users",
    "corps": {
      "username": "temp-{{suffixe}}",
      "password": "{{mdp}}",
      "role": "employee",
      "mustChangePassword": false
    },
    "attendu": {
      "statut": 201
    },
    "capturer": {
      "temp": "id"
    }
  },
  {
    "methode": "PATCH",
    "chemin": "/api/auth/users/{{temp}}",
    "corps": {
      "active": false
    },
    "attendu": {
      "statut": 200
    }
  },
  {
    "methode": "GET",
    "chemin": "/api/auth/journal-actions",
    "attendu": {
      "statut": 200,
      "texte": "/api/auth/users"
    }
  }
]
```

</details>

### FX-094 — Maturité hospitalière de 5 ans ou équivalent

*critère 94 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-095 — 2 000 utilisateurs au moins

*critère 95 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-096 — Déploiement multi-sites

*critère 96 · technique · statut ✅*

> **Aucun scénario e2e** — à couvrir

### FX-097 — Engagements de niveau de service

*critère 97 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-098 — Réponse au soutien en 4 h ou moins

*critère 98 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-099 — Préavis de 30 jours ouvrables d'un rehaussement

*critère 99 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-100 — Retour à la version précédente

*critère 100 · technique · statut ✅*

**Essais Java** : `RetourArriereSchemaTest.java`

**Preuve d'infrastructure ou d'exploitation** : `backend-java/src/main/resources/db/retour`

> **Aucun scénario e2e** — prouvé hors e2e (essais Java, infrastructure ou documentation cités)

### FX-101 — Assistance 24/7 en français

*critère 101 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-102 — Méthodologie d'implantation

*critère 102 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-103 — Chargé de projet

*critère 103 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-104 — Suivi, comptes rendus, registres

*critère 104 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-105 — Reprise à ses frais d'un livrable

*critère 105 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-106 — Plan de formation

*critère 106 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-107 — Formation en français

*critère 107 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-108 — Soutien complet

*critère 108 · technique · statut ✅ / 📄*

> **Aucun scénario e2e** — à couvrir

### FX-109 — Soutien en français par canaux identifiés

*critère 109 · technique · statut ✅ / 📄*

> **Aucun scénario e2e** — à couvrir

### FX-110 — Registre des incidents

*critère 110 · technique · statut ✅ / 📄*

> **Aucun scénario e2e** — à couvrir

### FX-111 — Rapports périodiques

*critère 111 · technique · statut ✅ / 📄*

> **Aucun scénario e2e** — à couvrir

### FX-112 — Maintenance incluse

*critère 112 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-113 — Maintenance annoncée

*critère 113 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-114 — Configuration personnalisée

*critère 114 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer

### FX-115 — Continuité et reprise documentées

*critère 115 · technique · statut 📄*

> **Aucun scénario e2e** — engagement contractuel ou responsabilité de l'établissement : aucun comportement logiciel à jouer
