# 26-015-DO — Conception de la solution et du logiciel

> Comment fermer les écarts du document [02](02-BESOINS-ET-ANALYSE-ECARTS.md) **dans** Nexu VINA,
> sans bâtir un second produit à côté : architecture de solution, principe des données
> paramétrables, moteur de règles, modèle de données, séquences, états, API, exigences non
> fonctionnelles et lots.
>
> Lecteurs : l'équipe de développement (Node et Java) et l'architecte qui rédigera les ADR 0047 à
> 0054. Chaque décision renvoie aux exigences qu'elle sert ; une exigence sans décision est un
> oubli, à signaler.

---

## 1. Portée et principes

**Portée** : les 82 critères essentiels ouverts, les 29 préférentiels ouverts et les clauses
hors grille (RAO, SAAV, PFT, BD Corpo, Graph, rétention, IA, disponibilité).

**Principes**, dans l'ordre où ils tranchent un conflit :

1. **Étendre les contextes existants, n'en créer que si un invariant nouveau l'exige.** Les
   horaires, les absences, le moteur de recherche et le journal existent et sont éprouvés. Deux
   contextes seulement naissent : `agph` (un processus annuel avec son propre invariant : une
   personne, un choix, à son tour) et `presence` (le temps déclaré et sa preuve).
2. **Ce que l'organisme nomme est une donnée ; ce que le moteur comprend est un enum.** § 3.
3. **Une règle est une donnée versionnée, évaluée par une fonction pure qui explique sa
   décision.** § 4. C'est ce qui rend vraies à la fois « paramétrable » (E-GN-12), « motifs
   d'inadmissibilité » (E-GN-16) et « traçabilité des décisions automatiques » (E-GN-9).
4. **Rien de calculé n'est stocké** — taux de comblement, statut opérationnel d'un quart,
   anomalies d'une feuille ouverte. Seul un **instantané** est figé au moment où une décision s'y
   appuie (soumission, approbation, clôture).
5. **Clean architecture et parité Node ↔ Java** : domaine pur, ports dans le domaine, adaptateurs
   en infrastructure ; chaque lot livre les deux moteurs, comme les lots STO et AI-2026-03.
6. **Toute intégration passe par un port, un simulateur, un adaptateur et une suite de contrat
   exécutée contre les deux** (patron des lots scolaires : SMS, GRICS, antivirus).

## 2. Architecture de solution

### 2.1 Contexte (C4 niveau 1)

```mermaid
flowchart LR
    subgraph US["Urgences-santé"]
        PAR["Paramédics<br/>≈ 1 000, mobile"]
        RMU["Répartiteurs (CCS)"]
        PLA["Service de planification<br/>des horaires"]
        PAI["Service de la paie"]
        GES["Gestionnaires et RH"]
        DRI["DRI — administrateurs"]
    end

    NV["**Nexu VINA — Gestion des effectifs**<br/>SaaS multilocataire<br/>AWS ca-central-1 (Montréal), multi-AZ"]

    ENTRA["Microsoft Entra ID<br/>(un ou plusieurs annuaires)"]
    GRAPH["Microsoft Graph<br/>calendriers, présence, Teams"]
    RAO["RAO — répartition<br/>assistée par ordinateur"]
    SAAV["SAAV — attitrage<br/>automatique des véhicules"]
    PFT["PFT — production des<br/>feuilles de temps (paie)"]
    CORPO["BD Corpo — dossier employé,<br/>banques de congés"]
    ESB["Bus d'entreprise (ESB)"]
    DW["Entrepôt de données"]
    COM["Courriel · SMS · poussée"]

    PAR & RMU & PLA & PAI & GES & DRI --> NV
    NV <-->|OIDC + PKCE| ENTRA
    NV <-->|passerelle bidirectionnelle| GRAPH
    NV <-->|horodatages, équipages| RAO
    NV <-->|équipages ↔ véhicules| SAAV
    NV -->|horaires réels, heures codées| PFT
    CORPO -->|employés, ancienneté, soldes| NV
    NV <-->|événements| ESB
    NV -->|lots SFTP/HTTPS signés| DW
    NV --> COM
```

### 2.2 Conteneurs (C4 niveau 2)

```mermaid
flowchart TB
    subgraph CLIENTS["Postes et appareils"]
        SPA["SPA React<br/>frontend/"]
        MOB["Application Expo<br/>mobile/ — pointage, choix AGPH"]
    end
    subgraph AWS["AWS ca-central-1 — deux zones de disponibilité"]
        WAF["WAF v2 + Shield<br/>géo-restriction par organisme"]
        ALB["Répartiteur de charge"]
        API1["API Node — ECS<br/>zone a"]
        API2["API Java — ECS<br/>zone b"]
        ORD["Ordonnanceur<br/>tâches différées"]
        PG[("PostgreSQL 16 RDS<br/>**multi_az = true**")]
        ARC[("Schéma archive<br/>lecture seule")]
        S3[("S3 — pièces, exports,<br/>sauvegardes (Québec)")]
        GD["GuardDuty · CloudWatch 365 j"]
    end
    SPA & MOB --> WAF --> ALB --> API1 & API2
    API1 & API2 --> PG
    ORD --> PG
    PG -.-> ARC
    API1 & API2 --> S3
    API1 & API2 -.->|boîte d'envoi| INT["Adaptateurs d'intégration<br/>RAO · SAAV · PFT · Corpo · Graph · ESB · DW"]
```

### 2.3 Contextes et dépendances

```mermaid
flowchart LR
    ORG["organisation<br/>(existant, étendu)<br/>unités datées, périmètres, milieux"]
    REG["regles<br/>(rh_regles généralisé)<br/>moteur + catalogue de faits"]
    HOR["horaires<br/>(existant, étendu)<br/>quarts, postes, disponibilités,<br/>quotas, échanges, comblement"]
    AGPH["agph<br/>(nouveau)<br/>campagnes, tour de rôle,<br/>remplacements long terme"]
    ABS["absences<br/>(existant, étendu)<br/>décision automatique, dérogation"]
    PRE["presence<br/>(nouveau)<br/>pointage, preuves, segments"]
    TPS["paie · feuilles de temps<br/>(existant, étendu)"]
    INTG["integrations<br/>(existant, étendu)"]

    HOR --> ORG
    HOR --> REG
    ABS --> REG
    PRE --> REG
    AGPH -->|port Postes / Attribution| HOR
    ABS -.->|événement AbsenceAutorisee| HOR
    PRE -->|port QuartsPlanifies| HOR
    PRE --> ORG
    TPS -->|port SegmentsDeTemps| PRE
    INTG -->|ports de lecture| TPS & HOR & PRE
```

Règles de dépendance, vérifiées par `ArchitectureTest` (Java) et `rbac-architecture.test.ts` (Node) :

- `horaires` ne dépend ni de `rh` ni de `agph` (cycle interdit, ADR 0032) ; `agph` agit sur les
  quarts **par un port** `Horaires.Attribution` que `horaires` implémente.
- `absences` ne connaît pas les quarts : il publie `AbsenceAutorisee` ; `horaires` s'y abonne pour
  rendre vacantes les places du titulaire (E-CH-6, E-GN-6).
- `regles` ne connaît aucun contexte métier : chaque contexte lui **déclare ses faits** (§ 4.2).

## 3. Le principe qui rend les données dynamiques

### 3.1 Référentiel d'organisme → comportement du logiciel

L'avis exige que presque tout soit « paramétrable ». La norme Java (ADR 0030) exige qu'un ensemble
fermé soit un enum. Les deux se concilient en séparant **ce que l'organisme nomme** de **ce que le
logiciel sait faire** :

```
Référentiel de l'organisme (table, par locataire)        Comportement (enum, dans le code)
─────────────────────────────────────────────────        ─────────────────────────────────
TPR  « Temps partiel régulier »          ───────────▶   TEMPS_PARTIEL_REGULIER
TC   « Temps complet »                    ───────────▶   TEMPS_COMPLET
SUP  « Surnuméraire »                     ───────────▶   TEMPS_PARTIEL_OCCASIONNEL
```

L'organisme crée, renomme, ordonne, date et désactive ses lignes ; le moteur ne raisonne que sur
les comportements. Un nouveau statut d'emploi négocié dans la prochaine convention devient une
ligne, pas une livraison — sauf s'il demande un **comportement** inédit, et c'est alors bien un
développement.

| Référentiel (contexte propriétaire) | Comportement fermé | Exigences |
|---|---|---|
| Statuts d'emploi (`rh`) | `TEMPS_COMPLET`, `TEMPS_PARTIEL_REGULIER`, `TEMPS_PARTIEL_OCCASIONNEL` | P-CH-5 à 7, E-GN-12 |
| Corps et titres d'emploi (`rh`) | — (pur vocabulaire) | E-GN-1, P-GH-1 |
| Conventions collectives (`rh`) | — | E-GN-12, E-CH-1 |
| Natures de quart (`horaires`) | `REGULIER`, `AD_HOC`, `REMPLACEMENT`, `HEURES_SUPPLEMENTAIRES` | E-GN-3, E-GR-13 |
| Plages horaires (`horaires`) | — | E-GR-8 |
| États de période (`horaires`) | `PREPARATION`, `COLLECTE`, `CONFECTION`, `PUBLIEE`, `CLOSE` | E-GR-10, E-GR-11 |
| Paramètres de poste (`horaires`) | type de valeur : `TEXTE`, `NOMBRE`, `BOOLEEN`, `REFERENCE` | E-CH-5 |
| Codes d'absence (`absences`) | durée : `PONCTUELLE`, `MOYEN_TERME`, `LONG_TERME` ; effet : `PAYE`, `NON_PAYE`, `BANQUE` | P-CH-8, P-GH-1 |
| Types d'heures (`paie`) | `TRAVAILLEE`, `PAUSE_PAYEE`, `PAUSE_NON_PAYEE`, `PRESENCE_PREALABLE`, `ABSENCE_PAYEE`, `SUPPLEMENTAIRE` | E-RF-6, E-GP-2, E-GP-9 |
| Profils de pointage (`presence`) | mode : `TEMPS_REEL`, `A_POSTERIORI`, `MIXTE` | E-GP-1, P-GP-1 |
| Mécanismes de preuve (`presence`) | `GEOREPERAGE`, `CODE_QR_DE_LIEU`, `RESEAU`, `BORNE`, `SOURCE_EXTERNE` | E-GP-11 |
| Milieux physiques (`organisation`) | type : `CASERNE`, `CENTRE_OPERATIONNEL`, `POINT_DE_SERVICE`, `AUTRE` | E-GN-17, E-GP-7 |
| Motifs d'inadmissibilité (`regles`) | — (clé de message traduite) | E-GN-16 |
| Sources d'horodatage (`presence`) | type : `RAO`, `SAAV`, `BADGE`, `FICHIER` | E-RF-13 |

### 3.2 Forme commune d'un référentiel

Chaque référentiel est une **table typée** dans son contexte (intégrité référentielle, requêtes
lisibles) qui partage la même forme :

| Colonne | Rôle |
|---|---|
| `tenantId`, `code` | clé naturelle, unique par locataire |
| `libelleFr`, `libelleEn` | affichage (i18n) |
| `comportement` | l'enum du § 3.1, quand il y en a un |
| `ordre`, `actif` | présentation, retrait sans suppression |
| `effectifDepuis`, `effectifJusqua` | **datation** : une valeur s'applique aux faits de sa période |
| `attributs` | JSON validé par le descripteur (paramètres propres) |

Un registre **en lecture seule**, `partage/referentiels`, décrit chaque référentiel (contexte,
colonnes, schéma des attributs, comportements admis, droits). C'est ce registre qui permet un
**écran de paramétrage générique** (document 04, § 5) sans que l'écran connaisse les tables — et
sans qu'un contexte lise les tables d'un autre.

### 3.3 Non-rétroactivité : datation et surcharges

E-GR-9 : « ajustement temporaire de paramètres opérationnels incluant les quotas, **sans effet
rétroactif** ». Deux mécanismes, un seul principe — **un paramètre se lit à la date du fait** :

- une **modification durable** ferme la version courante (`effectifJusqua = aujourd'hui`) et en
  ouvre une nouvelle ; les faits antérieurs gardent leur valeur ;
- une **surcharge temporaire** (`parametre_surcharges`) s'applique sur une fenêtre `[depuis,
  jusqua[` dont le début ne peut pas précéder l'instant de sa création : la base refuse une
  surcharge rétroactive (contrainte vérifiée par le domaine **et** par un essai de propriété).

## 4. Le moteur de règles

### 4.1 Modèle

`rh_regles` existe déjà (contexte, champ, opérateur, valeur, action, priorité, dates d'effet) mais
n'expose que cinq champs et ne sert que les absences scolaires. Il devient le contexte `regles` :

| Élément | Contenu |
|---|---|
| **Contexte d'évaluation** (enum) | `DISPONIBILITE`, `ABSENCE`, `ATTRIBUTION`, `ECHANGE`, `POINTAGE`, `FEUILLE_DE_TEMPS`, `HEURES_SUPPLEMENTAIRES`, `CHOIX_AGPH`, `INTERET_REMPLACEMENT` |
| **Portée** | organisme, unité (et son sous-arbre), groupe, convention, statut d'emploi, corps d'emploi, poste |
| **Condition** | arbre JSON `tous` / `un_de` / `non` sur des **faits déclarés** (§ 4.2) avec opérateurs typés |
| **Action** (enum) | `ACCEPTER`, `AVERTIR`, `EXIGER_APPROBATION`, `EXCLURE_DU_BASSIN`, `BLOQUER`, `REFUSER` |
| **Message** | clé traduite + paramètres, pour le motif affiché (E-GN-16) |
| **Versionnage** | toute modification crée une version ; l'évaluation retient la version appliquée |

```json
{
  "contexte": "DISPONIBILITE",
  "portee": { "type": "CONVENTION", "code": "FSSS-PARAMEDICS" },
  "condition": { "tous": [
    { "fait": "disponibilite.quartsParSemaine", "op": "<", "valeur": 3 },
    { "fait": "personne.statutEmploi.comportement", "op": "=", "valeur": "TEMPS_PARTIEL_REGULIER" }
  ]},
  "action": "BLOQUER",
  "message": { "cle": "regle.dispo.minimumQuarts", "parametres": { "minimum": 3 } },
  "priorite": 100,
  "effectifDepuis": "2027-01-01"
}
```

### 4.2 Catalogue de faits

Chaque contexte déclare les faits qu'il expose au moteur (nom, type, libellé, calcul). Le moteur
ne lit jamais une table : il reçoit un **dossier de faits** construit par le contexte appelant.
Ajouter un fait est un développement (il faut le calculer) ; composer des règles avec les faits
existants est du paramétrage. Faits du premier jet, par contexte :

| Contexte | Faits |
|---|---|
| `DISPONIBILITE` | quarts par semaine, heures par période, durée d'un quart, fins de semaine, nuits, écart minimal entre quarts, plage, statut d'emploi, corps d'emploi, convention |
| `ABSENCE` | code, catégorie de durée, préavis en jours, solde de banque, quota d'absences simultanées du groupe, taux de comblement après absence, titre d'emploi |
| `ATTRIBUTION` / `ECHANGE` | admissibilité au poste, repos depuis le dernier quart, heures cumulées de la semaine, contiguïté, formation requise, période du quart |
| `POINTAGE` | écart au début prévu, distance au milieu, mécanisme de preuve, source, présence préalable exigée |
| `FEUILLE_DE_TEMPS` / `HEURES_SUPPLEMENTAIRES` | heures du jour, de la semaine, de la période, écart planifié/déclaré, type d'heure, jour férié |
| `CHOIX_AGPH` | rang, statut d'emploi visé, corps requis par le poste, qualifications |

### 4.3 Évaluation

```mermaid
flowchart LR
    F["Dossier de faits<br/>(contexte appelant)"] --> S["Sélection<br/>contexte + portée + date du fait"]
    S --> O["Ordre : du général<br/>au particulier, puis priorité"]
    O --> E["Évaluation pure<br/>de chaque condition"]
    E --> C["Combinaison : l'action<br/>la plus restrictive gagne"]
    C --> V["Verdict<br/>action + motifs + versions"]
    V --> T["Trace : regles_evaluations<br/>(E-GN-9)"]
```

- **Ordre** : organisme → unité → groupe → convention → statut → corps → poste (le particulier
  précise le général, exigence préférentielle A7 de l'AI-2026-03 et E-GN-12 ici) ; à spécificité
  égale, la priorité.
- **Combinaison** : `REFUSER` > `BLOQUER` > `EXCLURE_DU_BASSIN` > `EXIGER_APPROBATION` > `AVERTIR`
  > `ACCEPTER`. Tous les motifs sont conservés, pas seulement le décisif.
- **Aperçu d'impact** : une règle en brouillon s'évalue contre les faits de la période en cours
  sans rien écrire (« 14 disponibilités deviendraient non conformes ») avant d'être activée.
- **Réglage « bloquer ou signaler »** (E-GN-15) : c'est l'action de la règle, pas un réglage à
  part.

## 5. Modèle de données

Conventions : `tenantId` sur toute table (omis des diagrammes) ; heures en **minutes entières
depuis minuit** et horodatages en UTC ISO ; montants en cents (ADR 0002) ; toute table métier
est journalisée par le domaine.

### 5.1 Organisation, périmètres et milieux (U1)

```mermaid
erDiagram
    ORG_UNITE ||--o{ ORG_UNITE_VERSION : "versions datées (existant)"
    ORG_UNITE_VERSION ||--o{ PERIMETRE_UTILISATEUR : "portée"
    UTILISATEUR ||--o{ PERIMETRE_UTILISATEUR : "a accès à"
    ORG_UNITE_VERSION ||--o{ HORAIRE_GROUPE : "rattache"
    ORG_UNITE_VERSION ||--o{ MILIEU : "héberge"
    MILIEU ||--o{ MILIEU_PREUVE : "se prouve par"

    PERIMETRE_UTILISATEUR {
        string utilisateurId FK
        string uniteId FK
        string etendue "UNITE | SOUS_ARBRE"
        string roleLocal "ref. rôles locaux"
        date depuis
        date jusqua "null = en cours"
    }
    HORAIRE_GROUPE {
        string id PK "existant"
        string uniteId FK "nouveau"
    }
    MILIEU {
        string id PK
        string code "unique"
        string libelle
        string typeComportement "CASERNE | CENTRE_OPERATIONNEL | POINT_DE_SERVICE | AUTRE"
        string adresse
        decimal latitude
        decimal longitude
        int rayonMetres "géorepérage"
        bool actif
    }
    MILIEU_PREUVE {
        string milieuId FK
        string mecanisme "GEOREPERAGE | CODE_QR_DE_LIEU | RESEAU | BORNE | SOURCE_EXTERNE"
        json parametres "plages IP, secret QR rotatif, borne"
        bool exige
    }
```

La garde de périmètre (E-GN-8) devient une **garde de route** au même titre que `requireRole` :
`exigerPerimetre(ressource → uniteId)`, refus par défaut (403), exemptions nommées dans
`rbac-architecture.test.ts`. Elle s'applique aux routes cœur des horaires, qui n'en ont pas
aujourd'hui (écart **G5**).

### 5.2 Référentiels, règles et surcharges (U1, U3)

```mermaid
erDiagram
    REF_STATUT_EMPLOI ||--o{ STATUT_EMPLOI_HISTORIQUE : "attribué"
    REF_CORPS_EMPLOI ||--o{ EQUIPAGE_COMPOSITION : "exigé"
    REF_CONVENTION ||--o{ REGLE : "portée"
    REGLE ||--o{ REGLE_VERSION : "versionnée"
    REGLE_VERSION ||--o{ REGLE_EVALUATION : "tracée"
    FAIT_DECLARE }o--o{ REGLE_VERSION : "utilisé par"

    REF_STATUT_EMPLOI {
        string code PK
        string libelleFr
        string comportement "TEMPS_COMPLET | TEMPS_PARTIEL_REGULIER | TEMPS_PARTIEL_OCCASIONNEL"
        int ordre
        bool actif
        date effectifDepuis
        date effectifJusqua
    }
    STATUT_EMPLOI_HISTORIQUE {
        string personId FK
        string statutCode FK
        date depuis
        date jusqua
        string origine "AGPH | RH | CORPO"
        string campagneId "si AGPH"
    }
    REGLE {
        string id PK
        string contexte "DISPONIBILITE | ABSENCE | ... "
        string porteeType
        string porteeRef
        bool active
    }
    REGLE_VERSION {
        string regleId FK
        int version
        json condition
        string action "ACCEPTER | AVERTIR | EXIGER_APPROBATION | EXCLURE_DU_BASSIN | BLOQUER | REFUSER"
        string messageCle
        int priorite
        date effectifDepuis
        date effectifJusqua
        string creePar
    }
    REGLE_EVALUATION {
        string id PK
        string contexte
        string objetType
        string objetId
        json versionsAppliquees
        string action
        json motifs
        json faits "instantané des faits évalués"
        datetime horodatage
        string declencheur "utilisateur ou tâche système"
    }
    PARAMETRE_SURCHARGE {
        string id PK
        string cibleType "QUOTA | PARAMETRE_GROUPE | PROFIL_POINTAGE"
        string cibleId
        string valeur
        datetime depuis "≥ création : jamais rétroactive"
        datetime jusqua
        string motif
        string auteur
    }
```

### 5.3 Quarts, équipages, postes, disponibilités et quotas (U2, U3)

Glossaire, pour ne pas confondre : le **quart** du métier (un véhicule, un équipage, une plage)
devient `horaire_equipages` ; la table historique `horaire_quarts` en est la **place** (une
personne, un rang). Le renommage de la table serait plus propre, mais `horaire_quarts` est lue par
la paie, le pointage, les échanges et le Java ; l'en-tête qui manquait s'ajoute au-dessus d'elle.

```mermaid
erDiagram
    HORAIRE_PERIODE ||--o{ HORAIRE_EQUIPAGE : "contient"
    HORAIRE_EQUIPAGE ||--|{ HORAIRE_QUART : "places (existant)"
    HORAIRE_EQUIPAGE }o--|| MILIEU : "se tient à"
    HORAIRE_EQUIPAGE }o--o| POSTE_LIGNE : "issu de"
    POSTE_LIGNE ||--o{ POSTE_PARAMETRE : "paramètres"
    PARAMETRE_POSTE_DEF ||--o{ POSTE_PARAMETRE : "définit"
    HORAIRE_PERIODE ||--o{ DISPONIBILITE_PERIODE : "collecte"
    HORAIRE_GROUPE ||--o{ QUOTA_COMBLEMENT : "fixe"

    HORAIRE_EQUIPAGE {
        string id PK
        string periodeId FK
        date date
        int debut "minutes"
        int fin "minutes, < debut = passe minuit"
        string natureCode FK
        string milieuId FK
        string modeAttribution "AUTOMATISE | MANUEL (E-GN-4)"
        int minimumOperationnel "E-GN-2"
        string tacheId "null = ad hoc (E-GN-3)"
        string posteLigneId "null = hors poste (E-GR-13)"
    }
    HORAIRE_QUART {
        string id PK "place, existant"
        string equipageId FK "nouveau"
        string corpsEmploiCode FK "nouveau (E-GN-1)"
        int rang
        string personId "null = place vacante"
        string attribueePar "UTILISATEUR | MOTEUR | AGPH"
        string justificationForcage "E-GN-11"
    }
    POSTE_LIGNE {
        string id PK
        string groupeId FK
        string code
        string statutEmploiOffert FK "TC, TPR…"
        string corpsEmploiCode FK
        string milieuId FK
        json cycle "suite de plages sur N semaines"
        string etat "BROUILLON | PUBLIE | ATTRIBUE | VACANT"
        string titulaireId
    }
    DISPONIBILITE_PERIODE {
        string id PK
        string personId
        string periodeId FK
        date date
        int debut
        int fin
        string type "DISPONIBLE | PREFERENCE | INDISPONIBLE"
        string conformite "CONFORME | AVERTIE | APPROBATION_REQUISE"
        string evaluationId FK
    }
    QUOTA_COMBLEMENT {
        string id PK
        string groupeId FK
        string periodeId "null = toutes"
        string plageCode "null = toutes"
        string natureCode "null = toutes"
        int quota
        date effectifDepuis
        date effectifJusqua
    }
```

Le **statut opérationnel** d'un équipage (`places pourvues ≥ minimumOperationnel`) et le **taux de
comblement** ne sont pas stockés : ils se calculent à la lecture (principe 4).

### 5.4 AGPH et remplacements long terme (U4)

```mermaid
erDiagram
    CAMPAGNE_AGPH ||--o{ CAMPAGNE_RANG : "ordre des choix"
    CAMPAGNE_AGPH ||--o{ CAMPAGNE_POSTE : "postes offerts"
    CAMPAGNE_POSTE }o--|| POSTE_LIGNE : "offre"
    CAMPAGNE_RANG ||--o| CHOIX_POSTE : "un seul choix"
    CHOIX_POSTE }o--|| CAMPAGNE_POSTE : "retient"
    REMPLACEMENT_LT ||--o{ MANIFESTATION_INTERET : "reçoit"
    REMPLACEMENT_LT }o--o| POSTE_LIGNE : "remplace"

    CAMPAGNE_AGPH {
        string id PK
        string uniteId FK
        int annee
        string conventionCode FK
        string etat "PREPARATION | DEPOT | CHOIX | ATTRIBUTION | CLOSE"
        int delaiChoixMinutes
        datetime debutChoix
        string regleOrdreId "rang = ancienneté selon la convention"
    }
    CAMPAGNE_RANG {
        string campagneId FK
        string personId
        int rang
        decimal anciennete "instantané figé à l'ouverture"
        string etat "EN_ATTENTE | A_SON_TOUR | A_CHOISI | PASSE | EXCLU"
        datetime tourDebut
        datetime tourFin
    }
    CHOIX_POSTE {
        string campagneId FK
        string personId
        string campagnePosteId FK
        datetime choisiLe
        string mode "EMPLOYE | AUTOMATIQUE | PLANIFICATEUR"
        string evaluationId FK "contrôle d'admissibilité"
    }
    REMPLACEMENT_LT {
        string id PK
        string origine "ABSENCE | POSTE_VACANT | AGPH"
        string referenceId
        date debut
        date fin
        datetime finManifestation
        string etat "OUVERT | ATTRIBUE | NON_COMBLE | TRANSFERE"
        string attribueA
    }
    MANIFESTATION_INTERET {
        string remplacementId FK
        string personId
        datetime deposeeLe
        datetime retireeLe
        string evaluationId FK
    }
```

L'ancienneté est **figée à l'ouverture** de la campagne (instantané venu de BD Corpo ou saisi) :
un recalcul pendant le tour de rôle déplacerait des rangs déjà servis.

### 5.5 Absences : décision automatique et dérogation (U5)

```mermaid
erDiagram
    ABSENCE_DEMANDE ||--o| ABSENCE_DECISION_AUTO : "évaluée"
    ABSENCE_DEMANDE ||--o| ABSENCE_DEROGATION : "peut être dérogée"
    REF_CODE_ABSENCE ||--o{ ABSENCE_DEMANDE : "code"
    REF_CODE_ABSENCE ||--o{ CODE_ABSENCE_ADMISSIBLE : "par titre d'emploi"

    ABSENCE_DEMANDE {
        string id PK "absence_requests, existant"
        string typeCode FK
        string statut "EN_ATTENTE | ACCEPTEE | REFUSEE | ANNULEE"
    }
    ABSENCE_DECISION_AUTO {
        string absenceId FK
        string resultat "ACCEPTEE | REFUSEE | A_APPROUVER"
        string evaluationId FK "motifs, versions"
        datetime decideeLe
    }
    ABSENCE_DEROGATION {
        string absenceId FK
        string auteur
        string motif "obligatoire"
        string codeAbsenceAttribue FK "E-GN-5"
        datetime le
    }
    REF_CODE_ABSENCE {
        string code PK "absence_types, étendu"
        string effet "PAYE | NON_PAYE | BANQUE"
        string categorieDuree "PONCTUELLE | MOYEN_TERME | LONG_TERME"
        bool planifiable
    }
    CODE_ABSENCE_ADMISSIBLE {
        string codeAbsence FK
        string titreEmploi FK
    }
```

Le refus automatique **est conservé** (aujourd'hui l'action `refuser` lève une exception et rien ne
reste) : c'est la condition de la dérogation.

### 5.6 Présence, segments de temps et feuille (U6, U7)

Le **segment de temps** devient l'unité du temps (écart **G1**) ; `time_entries` reste la vue en
heures que la paie lit, **dérivée** des segments pour les organismes qui pointent.

> **Révision (lot R12, décision D-13 de la feuille de route).** `time_entries` s'est révélé être le temps **facturable aux projets** (`projectId` obligatoire), pas la vue de paie : les segments d'effectifs n'y sont pas versés. La paie des effectifs passe par le lot codé et signé de la feuille de temps (R9), transmis au PFT par la boîte d'envoi (R10).

```mermaid
erDiagram
    POINTAGE }o--o| HORAIRE_QUART : "rattaché à la place"
    POINTAGE ||--o{ PREUVE_PRESENCE : "prouvé par"
    POINTAGE ||--o| ECART_PRESENCE : "peut produire"
    SEGMENT_TEMPS }o--o| HORAIRE_QUART : "couvre"
    SEGMENT_TEMPS }o--|| REF_TYPE_HEURE : "type"
    SEGMENT_TEMPS }o--o| SEGMENT_TEMPS : "remplace (historique)"
    FEUILLE_TEMPS ||--o{ SEGMENT_TEMPS : "regroupe"
    SOURCE_HORODATAGE ||--o{ HORODATAGE_EXTERNE : "fournit"
    HORODATAGE_EXTERNE }o--o| POINTAGE : "corrobore"
    FEUILLE_TEMPS ||--o{ EXPORT_PAIE_LIGNE : "transmise"

    POINTAGE {
        string id PK "pointage_punchs, étendu"
        string personId
        string quartId FK
        datetime horodatage
        string sens "ENTREE | SORTIE | DEBUT_PAUSE | FIN_PAUSE"
        string source "MOBILE | WEB | BORNE | IMPORT"
        string milieuDeclareId FK
        string resultat "CONFORME | ECART | NON_VERIFIE"
    }
    PREUVE_PRESENCE {
        string pointageId FK
        string mecanisme
        int distanceMetres "jamais la trace, seulement la distance"
        decimal latitude "conservée pour audit, rétention bornée"
        decimal longitude
        int precisionMetres
        string empreinteQr
        string reseau
    }
    ECART_PRESENCE {
        string pointageId FK
        string type "HORS_MILIEU | MAUVAIS_MILIEU | PREUVE_ABSENTE"
        string actionAppliquee "AVERTIR | EXIGER_APPROBATION | REFUSER"
        string traitePar
        datetime traiteLe
    }
    SEGMENT_TEMPS {
        string id PK
        string personId
        string quartId FK
        datetime debut
        datetime fin
        string typeHeureCode FK
        string source "POINTAGE | SAISIE | GENERE | IMPORT"
        string activite
        string etat "OUVERT | SOUMIS | APPROUVE"
        string remplaceId "E-RF-14 : une correction crée une version"
    }
    REF_TYPE_HEURE {
        string code PK
        string comportement
        string codePaie "PFT"
        int majorationPourcent
        bool compteHeuresSupplementaires
    }
    SOURCE_HORODATAGE {
        string code PK
        string type "RAO | SAAV | BADGE | FICHIER"
        json correspondance
        bool actif
    }
    FEUILLE_TEMPS {
        string id PK "timesheets, étendu à la période de paie"
        string personId
        date debut
        date fin
        string statut
        string fluxInstanceId "E-GP-6 : flux configurable"
        json instantaneAnomalies "figé à la soumission"
    }
```

### 5.7 Organisme, conformité et intégrations (U0, U8)

```mermaid
erDiagram
    ORGANISME ||--o| CHARTE_ORGANISME : "logo, couleurs"
    ORGANISME ||--o{ ANNUAIRE : "Entra, SAML"
    ORGANISME ||--o{ REGLE_ALERTE : "alertes courriel"
    ORGANISME ||--o{ POLITIQUE_RETENTION : "par classe"
    ORGANISME ||--o{ NIVEAU_SERVICE : "par priorité"
    ORGANISME ||--o{ CONNECTEUR : "RAO, SAAV, PFT…"
    CONNECTEUR ||--o{ MESSAGE_SORTANT : "boîte d'envoi"

    CHARTE_ORGANISME {
        string logoAssetId
        string couleurPrimaire "contraste ≥ 4,5:1 vérifié à l'enregistrement"
        string couleurAccent
        string nomAffiche
    }
    ANNUAIRE {
        string id PK
        string type "ENTRA | SAML"
        string tenantEntraId
        string clientId
        string secretRef "coffre, jamais en base"
        json correspondanceRoles
        json paysAutorises "P-TN-11"
    }
    REGLE_ALERTE {
        string evenement "export.telecharge, role.modifie…"
        json condition
        json destinataires
    }
    POLITIQUE_RETENTION {
        string classe "HORAIRES | TEMPS | ABSENCES | JOURNAUX | PRESENCE"
        int anneesAccesDirect "courante + 2"
        int anneesArchive "courante + 5"
        string action "ARCHIVER | DETRUIRE"
    }
    NIVEAU_SERVICE {
        string priorite "N1 | N2 | N3 | N4"
        int reponseMinutesHeures
        int reponseMinutesHorsHeures
        int resolutionMinutes
        string plageSoutien "5 h-23 h, 7 j/7"
    }
    CONNECTEUR {
        string code "RAO | SAAV | PFT | BD_CORPO | GRAPH | ESB | ENTREPOT"
        string mode "SIMULATEUR | HTTP | SFTP | FILE_MESSAGES"
        string configurationRef
        bool actif
    }
    MESSAGE_SORTANT {
        string id PK
        string connecteur
        string type
        json charge
        string etat "EN_ATTENTE | ENVOYE | ECHEC | ABANDONNE"
        int tentatives
    }
```

## 6. Séquences

### 6.1 Dépôt de disponibilités contrôlé par les règles (E-GN-12 à 16, E-GR-11)

```mermaid
sequenceDiagram
    autonumber
    actor E as Employé (mobile ou web)
    participant H as horaires — Disponibilites
    participant P as Période (état)
    participant R as regles — Evaluateur
    participant J as Journal

    E->>H: PUT /periodes/{id}/disponibilites (plages)
    H->>P: la période est-elle en COLLECTE ?
    alt hors fenêtre de collecte
        P-->>H: non
        H-->>E: 409 « la collecte est fermée depuis le … »
    else fenêtre ouverte
        H->>H: construire le dossier de faits (quarts/sem., nuits, statut, convention…)
        H->>R: evaluer(DISPONIBILITE, faits, date du fait)
        R-->>H: verdict {action, motifs[], versions}
        alt action = BLOQUER
            H->>J: disponibilite.refusee (motifs, versions)
            H-->>E: 422 + motifs traduits (E-GN-16)
        else AVERTIR ou EXIGER_APPROBATION
            H->>H: enregistrer, conformite = AVERTIE | APPROBATION_REQUISE
            H->>J: disponibilite.deposee (verdict)
            H-->>E: 200 + avertissements affichés
        else ACCEPTER
            H->>H: enregistrer, conformite = CONFORME
            H->>J: disponibilite.deposee
            H-->>E: 200
        end
    end
    Note over H,R: À la recherche de candidats, une disponibilité non conforme<br/>exclut la personne du bassin (E-GN-14) avec le même motif.
```

### 6.2 Absence planifiée : décision automatique, dérogation, vacance, comblement (E-GN-5, 6 ; E-CH-6 ; E-GR-3, 4 ; P-GN-1 à 3 ; P-GH-3)

```mermaid
sequenceDiagram
    autonumber
    actor E as Employé
    actor G as Gestionnaire
    participant A as absences
    participant R as regles
    participant H as horaires
    participant M as Moteur de recherche
    participant N as notifications

    E->>A: POST /absences (code, dates)
    A->>R: evaluer(ABSENCE, faits : solde, préavis, quota simultané, comblement après absence)
    R-->>A: REFUSER (motif : quota d'absences simultanées atteint)
    A->>A: enregistrer ABSENCE_DECISION_AUTO = REFUSEE (conservée)
    A-->>E: refusée, motif affiché
    G->>A: GET /absences/{id}/impact
    A->>H: impactSurComblement(personne, dates)
    H-->>A: places touchées, taux avant → après (calculé, E-GR-3)
    G->>A: POST /absences/{id}/derogation (motif, code d'absence attribué)
    A->>A: ACCEPTEE par dérogation (E-GN-5), journalisée sous l'auteur
    A-)H: événement AbsenceAutorisee
    H->>H: rendre vacantes les places du titulaire (E-CH-6)
    H->>H: pour chaque place : modeAttribution ? quota atteint ?
    alt AUTOMATISE
        H->>M: ouvrirRecherche(place, stratégie nommée)
        M->>N: solliciter les admissibles (P-GH-3)
        M-->>H: premier consentement du mieux classé
        H->>H: poser personId, attribueePar = MOTEUR (P-GN-1)
    else MANUEL
        H->>N: aviser les planificateurs du groupe
    end
```

### 6.3 AGPH : tour de rôle, choix unique, attribution et reliquats (E-CH-1, E-CH-10, P-CH-1 à 7)

```mermaid
sequenceDiagram
    autonumber
    actor PL as Planificateur
    participant C as agph — Campagne
    participant CO as BD Corpo (port)
    participant O as ordonnanceur
    actor E as Employé à son tour
    participant R as regles
    participant H as horaires (port Attribution)
    participant N as notifications

    PL->>C: ouvrir la campagne (unité, année, convention, délai de choix)
    C->>CO: ancienneté des employés visés
    CO-->>C: liste
    C->>C: figer CAMPAGNE_RANG (instantané d'ancienneté)
    PL->>C: publier les postes (CAMPAGNE_POSTE ← POSTE_LIGNE publiés)
    C->>O: planifier « tour suivant » à debutChoix
    loop pour chaque rang
        O->>C: tourSuivant()
        C->>N: « c'est votre tour, vous avez 30 min »
        E->>C: GET /agph/campagnes/{id}/postes-disponibles (P-CH-1)
        E->>C: POST choix (poste)
        C->>R: evaluer(CHOIX_AGPH, statut visé, corps requis, qualifications)
        alt non admissible
            R-->>C: BLOQUER + motif
            C-->>E: 422, poste refusé (P-CH-3)
        else admissible
            C->>C: CHOIX_POSTE (unique par rang, P-CH-2)
            C->>H: attribuerPoste(ligne, personne)
            C->>C: statut d'emploi ← statut offert TC ou TPR (P-CH-5, P-CH-6)
        end
        opt délai expiré sans choix
            O->>C: expirer(rang) → choix automatique selon règle (P-CH-4) ou PASSE
        end
    end
    C->>C: clore : sans poste → TEMPS_PARTIEL (P-CH-7)
    C->>H: transférer les postes non choisis à la gestion régulière (E-CH-10)
    H->>H: équipages vacants → comblement régulier (E-GR-2)
```

### 6.4 Pointage mobile avec preuve de présence (E-GP-1, E-GP-7, E-GP-10 à 14)

```mermaid
sequenceDiagram
    autonumber
    actor P as Paramédic
    participant APP as Application mobile
    participant PR as presence
    participant H as horaires (port QuartsPlanifies)
    participant R as regles
    participant J as Journal

    P->>APP: « Débuter mon quart »
    APP->>APP: position ponctuelle (une lecture, pas de suivi) ou lecture du code QR du lieu
    APP->>PR: POST /presence/pointages {sens, horodatage, preuve}
    PR->>H: place attendue pour cette personne à cette heure
    H-->>PR: place + milieu + début prévu
    PR->>PR: vérifier la preuve (distance au milieu ≤ rayon, QR valide et récent, réseau)
    PR->>R: evaluer(POINTAGE, écart horaire, distance, mécanisme, présence préalable)
    R-->>PR: verdict
    alt CONFORME
        PR->>PR: ouvrir le SEGMENT_TEMPS (type TRAVAILLEE ou PRESENCE_PREALABLE)
    else ÉCART (hors milieu, mauvais milieu, preuve absente)
        PR->>PR: ECART_PRESENCE + action configurée (E-GP-13)
        PR-->>APP: avertissement affiché immédiatement (E-GP-5)
    end
    PR->>J: pointage.enregistre (preuve, distance, verdict) — auditables (E-GP-14)
    PR-->>APP: 201 + état du quart
```

**Vie privée (Loi 25)** : la position est lue **au moment du pointage seulement**, jamais en
continu ; seule la distance sert à la décision ; les coordonnées brutes, conservées pour l'audit,
suivent la politique de rétention de la classe `PRESENCE`. Une **évaluation des facteurs relatifs
à la vie privée** précède l'activation, et un mécanisme sans géolocalisation (code QR de lieu,
borne) reste offert. Le pare-feu doit alors autoriser la géolocalisation pour l'origine de
l'application (`Permissions-Policy: geolocation=(self)`), au lieu de l'interdire.

### 6.5 De l'horaire à la paie (E-RF-1 à 13, E-GP-6, E-GP-8)

```mermaid
sequenceDiagram
    autonumber
    participant O as ordonnanceur
    participant T as paie — Feuilles de temps
    participant H as horaires
    participant PR as presence
    participant SX as Sources externes (RAO, SAAV)
    participant R as regles
    participant F as flux
    participant PFT as PFT (port)

    O->>T: préparer les feuilles de la période de paie
    T->>H: places planifiées par personne
    T->>PR: segments pointés
    T->>T: GÉNÉRER un segment pour chaque place sans pointage (source GENERE, E-RF-1)
    O->>SX: tirer les horodatages (idempotent par idExterne)
    SX-->>PR: HORODATAGE_EXTERNE
    PR->>PR: corréler pointage ↔ horodatage externe (tolérance paramétrable, E-RF-13)
    T->>R: evaluer(FEUILLE_DE_TEMPS et HEURES_SUPPLEMENTAIRES)
    R-->>T: anomalies + HS calculées (E-RF-5, E-RF-9)
    Note over T: écart planifié/déclaré (E-GP-8) et anomalies<br/>affichés dans la grille, calculés à la lecture
    T->>F: soumettre → instance du flux configuré (E-GP-6)
    F-->>T: approuvée au dernier palier
    T->>T: figer l'instantané, verrouiller (E-GP-3)
    T->>PFT: lot codé (type d'heure → code de paie), empreinte SHA-256
    PFT-->>T: accusé de réception
```

### 6.6 Attribution manuelle d'exception (E-GN-11)

```mermaid
sequenceDiagram
    autonumber
    actor PL as Planificateur
    participant H as horaires
    participant R as regles
    participant J as Journal

    PL->>H: PATCH /places/{id} {personId}
    H->>R: evaluer(ATTRIBUTION)
    R-->>H: BLOQUER — « repos de 8 h non respecté », rang 4 sur 6
    H-->>PL: 409 + motifs + classement
    PL->>H: PATCH /places/{id} {personId, forcer: true, justification}
    alt justification absente
        H-->>PL: 422 « la justification est obligatoire pour forcer »
    else justification fournie et droit « forcer » dans le périmètre
        H->>H: poser personId, attribueePar = UTILISATEUR, justificationForcage
        H->>J: quart.affectation.forcee (motifs ignorés, rang, justification, versions de règles)
        H-->>PL: 200
    end
```

### 6.7 Échange de quarts entre périodes (E-GR-7, E-GR-12)

```mermaid
sequenceDiagram
    autonumber
    actor A as Demandeur
    actor B as Destinataire
    participant X as horaires — Échanges
    participant R as regles

    A->>X: proposer (segments A en période P1, segments B en période P2)
    X->>X: politique inter-périodes du groupe : AUTORISE | RESTREINT | INTERDIT
    alt INTERDIT et P1 ≠ P2
        X-->>A: 409 « échange entre périodes interdit »
    else autorisé ou restreint
        X->>R: evaluer(ECHANGE, faits de B sur les segments de A)
        X->>R: evaluer(ECHANGE, faits de A sur les segments de B)
        Note right of R: les deux sens (aujourd'hui un seul),<br/>contiguïté et repos compris
        R-->>X: verdicts
        X-->>B: proposition + avertissements
        B->>X: accepter
        X->>X: RESTREINT → validation du planificateur, sinon appliquer
    end
```

### 6.8 Surcharge temporaire d'un quota (E-GR-8, E-GR-9)

```mermaid
sequenceDiagram
    autonumber
    actor PL as Planificateur
    participant H as horaires — Quotas
    participant J as Journal

    PL->>H: POST /quotas/{id}/surcharges {valeur: 6, depuis: 2027-01-20T18:00, jusqua: 2027-01-27, motif}
    alt depuis < maintenant
        H-->>PL: 422 « une surcharge ne peut pas être rétroactive »
    else
        H->>H: PARAMETRE_SURCHARGE
        H->>J: parametre.surcharge (avant, après, fenêtre, motif)
        H-->>PL: 201
    end
    Note over H: quotaEffectif(date) = surcharge active à cette date ?? version datée du quota.<br/>Les quarts déjà passés relisent leur propre date : rien ne change pour eux.
```

### 6.9 Authentification : plusieurs annuaires, pays autorisés (E-TN-2, P-TN-2, P-TN-11)

```mermaid
sequenceDiagram
    autonumber
    actor U as Usager
    participant W as WAF (géo-correspondance)
    participant A as auth
    participant D as Annuaires de l'organisme
    participant E as Entra ID

    U->>W: GET /connexion (sous-domaine de l'organisme)
    W->>W: pays de l'adresse ∈ pays autorisés de l'organisme ?
    alt pays refusé
        W-->>U: 403, journalisé, alerte si une règle le demande (P-TN-10)
    else
        W->>A: transmettre
        A->>D: annuaires actifs de l'organisme (ADR 0033 : le locataire vient de l'hôte)
        A-->>U: choix de l'annuaire s'il y en a plusieurs
        U->>E: autorisation OIDC + PKCE
        E-->>A: jeton (groupes, rôles)
        A->>A: correspondance groupes → rôles et périmètres de l'annuaire
        A-->>U: session
    end
```

## 7. États

### 7.1 Période de planification (E-GR-10, E-GR-11)

```mermaid
stateDiagram-v2
    [*] --> Preparation
    Preparation --> Collecte: ouvrir la collecte (date de fin)
    Collecte --> Confection: fermer la collecte (manuel ou échéance)
    Confection --> Publiee: publier
    Publiee --> Publiee: ajustements journaliers, échanges, comblement
    Publiee --> Close: clore (fin de période + délai)
    Close --> [*]
    note right of Collecte
        Seul état où l'employé dépose
        ou ajuste ses disponibilités
    end note
    note right of Publiee
        Une période publiée ne redevient
        jamais brouillon (invariant L1)
    end note
```

Les libellés et les délais sont des données de l'organisme ; les **cinq comportements** et leurs
transitions sont du code.

### 7.2 Campagne AGPH

```mermaid
stateDiagram-v2
    [*] --> Preparation
    Preparation --> Depot: ouvrir (ancienneté figée)
    Depot --> Choix: postes publiés, tour de rôle démarré
    Choix --> Choix: tour suivant (choix ou expiration)
    Choix --> Attribution: dernier rang servi
    Attribution --> Close: statuts d'emploi posés, reliquats transférés
    Close --> [*]
```

### 7.3 Demande d'absence

```mermaid
stateDiagram-v2
    [*] --> Evaluee: déclarée
    Evaluee --> Acceptee: règles → ACCEPTER (P-GN-3)
    Evaluee --> AApprouver: règles → EXIGER_APPROBATION
    Evaluee --> RefuseeAuto: règles → REFUSER
    AApprouver --> Acceptee: approuvée
    AApprouver --> Refusee: refusée
    RefuseeAuto --> Acceptee: dérogation motivée + code (E-GN-5)
    RefuseeAuto --> [*]
    Acceptee --> Annulee: annulation
    Acceptee --> [*]
    Refusee --> [*]
    Annulee --> [*]
```

### 7.4 Remplacement long terme

```mermaid
stateDiagram-v2
    [*] --> Ouvert: absence longue ou poste vacant (E-CH-7)
    Ouvert --> Ouvert: manifestations d'intérêt (E-CH-8)
    Ouvert --> Attribue: fin de fenêtre, attribution selon les règles (P-CH-9)
    Ouvert --> NonComble: aucun intéressé admissible (E-CH-9)
    NonComble --> Transfere: quarts versés à la gestion régulière (E-CH-10)
    Attribue --> [*]
    Transfere --> [*]
```

## 8. API

Toutes les routes sont documentées dans l'OpenAPI 3.0.3 existant (`services/openapi.ts`, ADR 0019
et 0028), gardées par `requireModule`, `requireRole` **et** la nouvelle garde de périmètre, et
portées à l'identique en Java (`codeDe` partagé pour les codes 404/409/422).

| Contexte | Routes principales | Exigences |
|---|---|---|
| `organisation` | `GET/POST /api/organisation/milieux`, `PUT /milieux/:id/preuves`, `GET/PUT /api/organisation/perimetres/:utilisateurId` | E-GN-7, 8, 17 |
| `referentiels` | `GET /api/referentiels` (registre), `GET/POST/PUT /api/referentiels/:type`, `POST /:type/:code/fermer` | § 3 |
| `regles` | `GET/POST /api/regles`, `POST /api/regles/:id/versions`, `POST /api/regles/:id/apercu`, `GET /api/regles/evaluations?objet=` | E-GN-9, 10, 12, 15, 16 |
| `horaires` | `POST /api/horaires/equipages` (ad hoc), `PATCH /equipages/:id` (heures, mode, minimum), `PATCH /places/:id` (`forcer` + `justification`), `POST /equipages/lot` (modification en lot), `GET/PUT /periodes/:id/disponibilites`, `POST /periodes/:id/etat`, `GET/POST /quotas`, `POST /quotas/:id/surcharges`, `GET /periodes/:id/comblement`, `GET/POST /postes-lignes`, `POST /postes-lignes/publier` | E-GN-1 à 4, E-CH-2 à 5, E-GR-3 à 13 |
| `agph` | `POST /api/agph/campagnes`, `POST /:id/ouvrir`, `GET /:id/rangs`, `GET /:id/postes-disponibles`, `POST /:id/choix`, `POST /:id/clore`, `GET/POST /api/agph/remplacements`, `POST /remplacements/:id/interets` | E-CH-1, 7 à 10, P-CH-1 à 9 |
| `absences` | `GET /api/absences/:id/impact`, `POST /api/absences/:id/derogation`, `GET/PUT /api/absences/codes/:code/admissibilite` | E-GN-5, E-GR-3, P-GH-1 |
| `presence` | `POST /api/presence/pointages`, `GET /pointages?personne=&du=&au=`, `GET/PUT /profils`, `GET /ecarts`, `POST /ecarts/:id/traiter`, `GET/POST /sources`, `POST /sources/:code/tirer` | E-GP-1 à 15, E-RF-13 |
| `paie` | `POST /api/payroll/feuilles/preparer`, `GET /feuilles/:id/segments`, `PUT /segments/:id` (version), `GET /feuilles/:id/anomalies`, `POST /exports/pft` | E-RF-1 à 14 |
| `saas` | `GET/PUT /api/saas/tenants/:id/charte`, `/annuaires`, `/alertes`, `/retention`, `/niveaux-service` ; module `ia` au catalogue | P-TN-2, 9, 10, 11 ; Contrat 2.06, 5.02, 17.08 |
| `integrations` | `GET/PUT /api/integrations/connecteurs/:code`, `POST /connecteurs/:code/essai`, `GET /messages?etat=` | E-TN-7, P-TN-7, Cahier 2.3 |

## 9. Exigences non fonctionnelles

### 9.1 Disponibilité 99,7 % au Québec (E-TN-9, Contrat 6.01)

Un mois de 30 jours compte 43 200 minutes : **99,7 % laisse 129 minutes d'indisponibilité**, hors
maintenance approuvée trois jours à l'avance ; sous 99,0 % (432 minutes), le manquement est majeur.

| Élément | Aujourd'hui (ADR 0043, CSSDC) | Cible 26-015-DO |
|---|---|---|
| Base de données | RDS PostgreSQL 16, `multi_az = false` | **`multi_az = true`**, bascule automatique (≈ 1-2 min) |
| Application | 2 tâches ECS | ≥ 2 tâches **réparties sur 2 zones**, sonde de santé et retour arrière automatique |
| Objectifs | RTO 48 h, RPO 15 min | **RTO 1 h, RPO 5 min** |
| Relève (P-TN-4) | IaC seulement | environnement tiède dans une **autre zone de ca-central-1** |
| Mesure | battement interne, cible 99,5 % | sondes synthétiques externes, cible **par contrat** (`NIVEAU_SERVICE`), rapport mensuel des incidents |

**La relève ne peut pas sortir du Québec** (Contrat 17.07.04) : ca-west-1 est à Calgary. La
redondance se fait donc **entre zones de disponibilité de la région de Montréal**, pas entre
régions. C'est un amendement de l'ADR 0043 pour cette offre (ADR 0052).

### 9.2 Performance (E-TN-17)

Scénario JMeter à ajouter au kit `perf/jmeter` : 1 100 usagers, **pointe de relève de quart**
(≈ 300 pointages en 10 minutes à 6 h et 18 h), consultation d'horaire, dépôt de disponibilités en
fin de collecte, tour de rôle AGPH. Cible : p95 < 2 000 ms sur les transactions de base, mesurée
contre les deux moteurs.

### 9.3 Sécurité (E-TN-8, 23, 24, 31 ; P-TN-10, 11)

- **GuardDuty** et journaux CloudWatch à **365 jours** (aujourd'hui 90) ; Shield Standard et
  règles de débit du WAF ; Shield Advanced chiffré en option.
- **Géo-restriction** par organisme dans le WAF, doublée d'un contrôle applicatif.
- **Règles d'alerte** par courriel sur événements du journal (export téléchargé, rôle modifié,
  forçage d'attribution, pays refusé).
- **Correspondance MCN** : un tableau par contrôle de la liste de vérification de la zone
  d'accueil, avec preuve (à obtenir, question Q-12), et un rapport de sécurité mensuel.

### 9.4 Données (Contrat 2.06, 6.05, 6.06)

- **Rétention par classe** : tâche `archiver_donnees` de l'ordonnanceur ; au-delà de l'année
  courante + 2, les lignes passent au schéma `archive` (lecture seule, consultable sur requête) ;
  au-delà de + 5, destruction attestée (mécanisme de `saas/destruction.ts`).
- **Dictionnaire de données généré** à partir des migrations et des descripteurs de référentiels
  (E-TN-13), publié avec chaque version.
- **Plan de sortie** : export complet par organisme dans un format ouvert, dictionnaire joint.

### 9.5 IA (Contrat 17.08)

Module `ia` au catalogue, **désactivé par défaut**, `requireModule("ia")` sur `/api/ai`, widget
masqué sans le module, registre des usages (finalité, données, fournisseur, lieu de traitement)
exigé par 17.08.02 et consultable par l'organisme.

## 10. Lots de réalisation

```mermaid
flowchart LR
    U1["U1 Organisation,<br/>référentiels, journal"] --> U2["U2 Quarts, postes,<br/>équipages"]
    U1 --> U3["U3 Moteur de règles,<br/>disponibilités, quotas"]
    U2 --> U3
    U3 --> U4["U4 AGPH et<br/>remplacements LT"]
    U3 --> U5["U5 Absences<br/>et comblement"]
    U1 --> U6["U6 Pointage<br/>et présence"]
    U3 --> U6
    U6 --> U7["U7 Feuille de temps,<br/>HS, rapports"]
    U1 --> U8["U8 Intégrations"]
    U7 --> U8
    U0["U0 Socle de conformité"]

    style U1 fill:#ffe6e6
    style U3 fill:#ffe6e6
    style U6 fill:#ffe6e6
```

| Lot | Contenu | Essentiels fermés | Effort (j-p) | ADR |
|---|---|---:|---:|---|
| **U0** | Multi-AZ et RTO 1 h ; module `ia` ; charte par organisme ; annuaires par organisme ; géo-restriction ; alertes ; rétention et archive ; environnement de formation ; niveaux de service configurables ; GuardDuty, CloudWatch 365 j ; dictionnaire de données ; retour arrière applicatif | 14 | 25 | 0052, 0053 |
| **U1** | Unités ↔ groupes, périmètres utilisateur et garde, milieux et preuves, registre de référentiels, journal unifié consultable (filtres, export), traces des étapes automatiques | 5 | 15 | 0047 |
| **U2** | `horaire_equipages`, composition par corps d'emploi, minimum opérationnel, quarts ad hoc, mode d'attribution, ajustements journaliers, postes-lignes et paramètres, modification en lot | 11 | 18 | — |
| **U3** | Contexte `regles` (catalogue de faits, versions, aperçu), cycle de période paramétrable, disponibilités par période, quotas et surcharges, échanges inter-périodes bidirectionnels, contrôles temporels | 12 | 22 | 0048 |
| **U4** | Contexte `agph` : campagnes, rangs figés, choix unique minuté, attribution automatique, statuts d'emploi, remplacements long terme, reliquats vers la gestion régulière | 7 | 25 | 0051 |
| **U5** | Décision automatique conservée, dérogation, impact sur le comblement, événement `AbsenceAutorisee` → vacance → recherche, attribution par le moteur, avis aux admissibles, codes par titre d'emploi | 6 | 15 | — |
| **U6** | Contexte `presence` : segments de temps, pointage temps réel (mobile, web, borne), pauses, présence préalable, preuves, écarts et actions, journal des localisations, sources d'horodatage corrélées | 12 | 25 | 0049, 0050 |
| **U7** | Feuille générée depuis les horaires, validation en temps réel, anomalies en ligne, HS par règles, types d'heures, flux d'approbation branchés, historique des corrections, rapports et relevés paramétrables, export PFT | 13 | 20 | — |
| **U8** | Connecteurs RAO, SAAV, PFT, BD Corpo, Graph, ESB (boîte d'envoi transactionnelle), entrepôt de données — chacun port + simulateur + adaptateur + contrat | 2 (+ cahier) | 30 | 0054 |
| | **Total** | **82** | **≈ 195** | |

Chemin critique : **U1 → U2 → U3 → U6 → U7 → U8** (≈ 130 jours-personne) ; U0, U4 et U5 se
parallélisent.

## 11. Stratégie d'essai

| Niveau | Ce qui est vérifié | Seuil |
|---|---|---|
| Unitaires de domaine | moteur de règles (ordre, combinaison, non-rétroactivité), tour de rôle, corrélation, calcul HS, statut opérationnel | **100 %** lignes et branches |
| Propriétés | `quotaEffectif(date)` ne change jamais pour une date passée ; un rang AGPH servi n'est jamais re-servi ; Σ segments d'une place = durée pointée | mêmes graines Node et Java |
| Intégration (PostgreSQL réel) | chaînes 6.1 à 6.8 de bout en bout côté serveur | ≥ 96 % par classe |
| **Contrat par port** | RAO, SAAV, PFT, BD Corpo, Graph, ESB, entrepôt, source d'horodatage : simulateur **et** adaptateur HTTP passent la même suite | toutes les implémentations |
| Architecture | `horaires ↛ rh`, `horaires ↛ agph`, `regles ↛ contextes métier` ; garde de périmètre présente sur chaque route mutante des horaires | 0 violation |
| Bout en bout + accessibilité | chaque écran du document 04 dans chaque état, quatre largeurs, axe-core | 0 violation |
| Charge | scénario § 9.2 | p95 < 2 s |
| Mutation | Stryker (Node), PIT (Java) sur `regles`, `agph`, `presence` | MSI ≥ 90 % sur le domaine |

## 12. ADR à rédiger

| # | Titre | Décision |
|---|---|---|
| 0047 | Référentiels de l'organisme, comportements du logiciel | table typée par référentiel, registre de descripteurs, enum pour le comportement |
| 0048 | Un moteur de règles unique, versionné et explicable | `rh_regles` devient le contexte `regles` ; faits déclarés par les contextes ; trace de chaque évaluation |
| 0049 | Le segment de temps, unité du temps | ~~`time_entries` devient une vue dérivée~~ — écarté (D-13) : `time_entries` est le temps de projet ; la paie passe par le lot PFT |
| 0050 | Preuve de présence et vie privée | lecture ponctuelle de la position, distance comme critère, EFVP, alternative sans géolocalisation |
| 0051 | L'AGPH, contexte distinct des horaires | invariant propre (une personne, un choix, à son tour) ; action sur les quarts par port |
| 0052 | Haute disponibilité intra-Québec | amende l'ADR 0043 pour les contrats à 99,7 % : multi-AZ, RTO 1 h, relève dans une autre zone de Montréal |
| 0053 | IA désactivée par défaut, par organisme | module `ia`, registre des usages, autorisation préalable (Contrat 17.08) |
| 0054 | Intégrations par boîte d'envoi | outbox transactionnelle, reprises, idempotence, adaptateur ESB |
