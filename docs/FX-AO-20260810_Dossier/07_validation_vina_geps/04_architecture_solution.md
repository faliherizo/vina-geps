# FX-AO-20260810 : solution logicielle et architecture — un module de VINA GePS, pas un nouveau projet

> Écrit pour l'architecte et l'équipe produit. Ce document tranche « module ou nouveau projet » et
> décrit le contexte « Communications cliniques » à ajouter, ses ports, ses modules activables par
> organisme et ce qu'il réutilise. Il prend la forme d'un ADR proposé (0074), à accepter avant
> toute implantation.

## ADR 0074 (proposé) : les communications cliniques sont un contexte de VINA GePS, activable par organisme

- **Statut** : **accepté pour le socle (R1)** le 14 novembre 2026 — voir `docs/adr/0074-communications-cliniques-contexte-alertes.md`. Les phases R2 et suivantes attendent les réponses Q-TEL, Q-ALC et Q-DOS.
- **Date** : 14 novembre 2026.

### Contexte

L'appel d'offres FX-AO-20260810 (Santé Québec Laurentides, environ 1 500 à 2 000 utilisateurs, six
sites) demande une plateforme de **distribution de communications cliniques** : alertes ciblées,
confirmations, relances, cascades, appel automatique, suivi et audit. VINA GePS, issu du projet
Urgences-santé, sert déjà le personnel de la santé avec la messagerie, les avis multi-canaux,
l'identité, les groupes, les disponibilités et l'audit. Le dossier d'analyse proposait de bâtir une
plateforme neuve.

### Décision

1. Le fonctionnel manquant devient **un nouveau contexte borné** `alertes` dans
   `backend-java/` (`alertes/{domaine,application,infrastructure/{web,persistance,configuration}}`),
   selon la norme du dépôt (ADR 0030 : enums, CQRS, ports, couverture JaCoCo ≥ 96 % par classe).
2. Il **ne touche pas** aux entités des autres contextes. Il passe par des ports de lecture
   (`partage/…`) :

   | Port (nouveau, `partage`) | Implanté par | Sert à |
   |---|---|---|
   | `AnnuaireDestinataires` | `effectifs` + `auth` | Résoudre une personne, un groupe (unité, groupe transverse) ou une liste en destinataires nommés, avec leurs canaux |
   | `DisponibiliteDestinataires` | `effectifs` | Savoir qui est en quart ou disponible maintenant (cascade « au prochain disponible ») |
   | `CanauxDiffusion` | `notifications` | Envoyer par poussée, texto ou courriel, et recevoir les accusés |
   | `AppelsSortants` | `notifications` (port ADR 0022) | Appeler sur non-réponse ; recueillir un DTMF |
   | `FilsDeDiscussion` | `communication` | Ouvrir la conversation de suivi d'une alerte |

3. Le contexte possède ses tables (`alr_*`, changesets Liquibase `0006` et suivants) :
   - `alr_modeles` et `alr_reponses` (versionnés, par organisme) ;
   - `alr_alertes` avec le **snapshot des destinataires** (ADR-003 du dossier) ;
   - `alr_remises`, une machine à états par destinataire et par canal ;
   - `alr_politiques` (relance et cascade, versionnées) et `alr_escalades` (instances durables, échéances) ;
   - `alr_evenements` (journal en ajout seul) et `alr_actifs_mensuels` (facturation).
4. L'orchestration (relances, cascades, appels) s'exécute sur **l'ordonnanceur existant**, avec des
   échéances persistées et des gestes **idempotents**. Il n'y a pas de bus de messages : à
   2 000 utilisateurs, la boîte d'envoi transactionnelle tient la charge, et un bus ajouterait un
   composant à opérer chez l'établissement (profil B).
5. Les fonctions se vendent et s'activent **par organisme**, depuis le superadmin, comme les
   modules d'effectifs :

   | Code de module | Exige | Contenu |
   |---|---|---|
   | `alertes` | — | Socle : console d'alerte, modèles et réponses, suivi, journal |
   | `alertes_relances` | `alertes` | Relances et cascades, politiques |
   | `alertes_telephonie` | `alertes_relances` | Appel automatique, retour d'appel par DTMF |
   | `alertes_critiques` | `alertes` | Canal critique mobile |
   | `alertes_disponibilite` | `alertes` + `effectifs` | Cascade qui consulte les horaires et disponibilités |
   | `effectifs_communication` (existant) | — | Messagerie, appels, listes |

   Un organisme qui n'achète que les communications cliniques (Santé Québec Laurentides) a
   `alertes*` et `effectifs_communication`, **sans** la planification : son menu ne montre que la
   rubrique « Communications ». Les groupes viennent alors des **groupes transverses** (ECF-09)
   plutôt que des unités d'effectifs. C'est pour cela que `AnnuaireDestinataires` est un port.
6. Le produit reste **un seul code source et une seule image**. Le profil B (infrastructure de
   l'établissement, critères 51 et 56 à 61) est le même conteneur, déployé pour un seul organisme.

### Options écartées

| Option | Pourquoi écartée |
|---|---|
| **Nouveau projet indépendant** | Il recopierait l'identité (Entra, SAML, mots de passe, MFA), les avis multi-canaux, la messagerie, le journal, la rétention, le superadmin et les sauvegardes, soit environ 40 % de l'effort, et chaque correctif de sécurité serait à faire deux fois. Le dépôt a déjà payé ce prix avec la séparation Nexu VINA → Urgences-santé (dettes D1-D5). |
| **Plateforme neuve du dossier** (Java 21, PostgreSQL, RabbitMQ, microservices à terme) | Même motif ; et RabbitMQ ajoute un composant à opérer chez l'établissement sans besoin de charge démontré. |
| **Étendre la messagerie** (une alerte = un message spécial) | Une alerte a un cycle de vie (relances, cascade, prise en charge, appel) que le fil de discussion n'a pas ; les mêler rendrait la visibilité (ADR 0072) et l'audit illisibles. L'alerte **ouvre** un fil, elle n'en est pas un. |
| **SaaS tiers d'alertes** (intégration) | Critères 53 (données au Canada), 89 (traçabilité complète) et 29 (coordonnées masquées) difficiles à garantir chez un tiers ; dépendance de prix. |

### Conséquences

- Le dépôt garde son nom produit (VINA GePS) ; Urgences-santé et Santé Québec Laurentides sont deux
  organismes avec des modules différents.
- `ArchitectureTest` doit refuser toute dépendance de `alertes` vers les entités `effectifs`,
  `communication` ou `notifications` : seulement les ports de `partage`.
- Le bordereau facture par **utilisateur actif** : `alr_actifs_mensuels` est une donnée de
  facturation, exportable et justifiable (ECF-08).
- Le transport de la **voix** reste chez un fournisseur (Twilio région Canada ou un équivalent
  québécois) : à approvisionner, et à chiffrer dans le bordereau.

## Vue des conteneurs (C2)

```
 Navigateur / PWA / app mobile ──HTTPS──► nginx ──► Java (VINA GePS)
                                                    ├─ communication   (fils, appels, listes)
                                                    ├─ notifications   (poussée, texto, courriel, appel sortant)
                                                    ├─ alertes  ◄─ NOUVEAU (console, suivi, relances, cascades)
                                                    ├─ effectifs       (unités, horaires, disponibilités)
                                                    ├─ auth / acces    (Entra ID, SAML, rôles, capacités)
                                                    └─ ordonnanceur    (échéances durables)
                                                         │
                         base de données + volume (Québec) ┘
 Fournisseurs : texto et voix (Twilio CA ou équivalent) · courriel (Microsoft 365 / SMTP) · APNs / FCM
```

## Machine à états d'une remise (par destinataire et par canal)

```
CREEE ─► ENVOYEE ─► REMISE ─► LUE ─► REPONDUE ─► PRISE_EN_CHARGE (fin, arrête la cascade)
   │         │          │        └──────────────► (délai) ─► RELANCEE ─► … ─► ESCALADEE (étape suivante)
   │         └─ ECHEC (canal) ─► repli sur le canal suivant de la politique
   └─ IGNOREE (préférence non obligatoire, hors garde)
```

`PRISE_EN_CHARGE` par une seule personne arrête la cascade pour tous ; une reprise manuelle la
relance à l'étape choisie (critère 17).
