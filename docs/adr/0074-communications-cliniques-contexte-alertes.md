# ADR 0074 — Les communications cliniques sont un contexte `alertes` de VINA GePS, activable par organisme

- **Statut** : accepté pour le **socle** (phase R1), le 14 novembre 2026. Les phases R2 et
  suivantes restent soumises aux réponses du client (section « Points ouverts »).
- **Décideurs** : équipe VINA.
- **Concerne** : `backend-java/.../alertes/` (nouveau contexte), changeset `0006-alertes.xml`,
  `saas/domaine/CatalogueModules.java` et `CatalogueVues.java`, `politique-acces.json`,
  `notifications/domaine/Evenements.java` ; le frontend (`alertes/`, menu, guide).
- **Sources** :
  - `docs/FX-AO-20260810_Dossier/07_validation_vina_geps/04_architecture_solution.md` (version proposée) ;
  - `07_analyse_metier_socle_alertes.md` (règles R1 à R10).

## Contexte

L'appel d'offres FX-AO-20260810 (Santé Québec Laurentides) demande une distribution de
communications cliniques. Sur ses 32 critères fonctionnels, VINA GePS en sert déjà 13 et 12 en
partie : identité, messagerie, avis multi-canaux, groupes, disponibilités, journal. Il manque
l'**alerte** : un objet ciblé, priorisé et suivi, que le fil de discussion n'est pas.

## Décision

1. **Un contexte borné `alertes`** dans le monolithe modulaire, découpé comme les autres
   (`domaine` / `application` / `infrastructure/{web,persistance,configuration}`), selon la norme
   Java du dépôt (ADR 0030).
2. **Le domaine ne dépend de rien.** Ses ports :
   - `AnnuaireAlertes` : résoudre une personne ou un groupe en destinataires nommés, avec le
     compte à aviser. L'adaptateur s'appuie sur `GroupesDeTravailPort` (les unités) et sur la
     table des comptes.
   - `PublicationAvis` (déjà partagé, ADR 0029) : pour les canaux. Le contexte ne connaît ni
     Twilio, ni SMTP, ni APNs.
   - `LectureAlertesPort` et `EcritureAlertesPort` : lecture et écriture séparées.
   - L'horloge et les identifiants sont injectés.
3. **Le destinataire est figé à l'envoi** (snapshot), avec son nom : l'alerte dit qui elle visait,
   même si le groupe change ensuite.
4. **Un module de catalogue, `alertes`**, activable par organisme depuis le superadmin. Il n'exige
   aucun module d'effectifs : un organisme « communications seulement » l'a seul, et les groupes
   sont alors ses unités.
5. **Deux capacités** :
   - `alertes.envoyer` : `admin`, `director`, `manager` ;
   - `alertes.configure` : `admin`.
   Recevoir n'en demande aucune : il suffit d'être destinataire.
6. **Un événement d'avis, `alerte.recue`**, **essentiel** : une personne ne peut pas fermer tous
   ses canaux écrits pour une alerte. La priorité `CRITIQUE` force tous les canaux.
7. **Pas de bus de messages.** L'envoi publie les avis dans la transaction de la requête, comme les
   autres contextes. L'orchestration des relances (R2) s'appuiera sur l'ordonnanceur existant.

## Points ouverts, et ce que le socle suppose d'ici leur réponse

| Question au client | Choix provisoire du socle | Réversible ? |
|---|---|---|
| **Q-TEL** — fournisseur de téléphonie | **Tranché le 14 novembre 2026 par l'équipe VINA (choix technologique qui lui revient) : Twilio**, déjà branché (`notifications/infrastructure/persistance/Twilio.java`, `AppelHttp`). Textos sortants et entrants (R2-b) et appels avec synthèse vocale et DTMF (R3) passent par lui, derrière les ports `PublicationAvis` et `notifications.Appels` (ADR 0022) | Oui : c'est un adaptateur ; un autre fournisseur ne remplacerait que lui |
| **Q-ALC** — alertes critiques iOS | La priorité critique force poussée, texto et courriel. *Critical Alerts* attend l'autorisation d'Apple | Oui : ajout d'un canal |
| **Q-DOS** — « note au dossier » | Interprétée comme une **note interne sur la fiche du destinataire**, non implantée dans le socle (R4) | Oui : rien n'est écrit dans un dossier clinique |

## Complément du 14 novembre 2026 (R1-bis)

- **Les groupes transverses appartiennent à `communication`**, pas à `alertes` : ce sont des données de
  l'organisme que la messagerie, les listes et les alertes partagent, et `communication` ne peut pas dépendre
  d'`alertes` (cycle refusé par `ArchitectureTest`). Un port composé, `GroupesDeCommunication` (unités + groupes
  transverses actifs, appartenance au jour de Montréal), est l'implantation **principale** de
  `GroupesDeTravailPort`. L'acheminement des appels par unité reçoit explicitement les unités seules (bean
  qualifié par son nom, sans dépendance de code vers `effectifs`).
- **La priorité critique est une capacité**, `alertes.critique` (administration et direction par défaut,
  surchargeable par organisme). Un refus est un 403 nommé ; l'écran grise la priorité et ne propose pas un
  modèle critique à qui ne peut pas l'envoyer.
- **Une alerte retient la version du modèle dont elle part** ; les versions sont datées et signées, jamais
  réécrites. Le périmètre d'un modèle se range en table, comme toute liste du contexte.

## Options écartées

- **Un nouveau projet** : il recopierait l'identité, les avis, la messagerie, le journal, le
  superadmin et les sauvegardes, et chaque correctif de sécurité serait à faire deux fois.
- **L'alerte comme message spécial de la messagerie** : la visibilité d'un fil (ADR 0072) et le
  cycle de vie d'une alerte (remise, réponse, prise en charge, puis relance) ne se recouvrent pas.
- **La plateforme neuve du dossier d'origine** (Java 21, PostgreSQL, RabbitMQ) : même motif, plus
  un composant à opérer chez l'établissement.

## Conséquences

- Les tables `alr_reponses`, `alr_modeles`, `alr_alertes` et `alr_remises` naissent du changeset
  `0006`. `SchemaEssai` les reflète.
- Les nouvelles routes `/api/alertes/**` sont servies dans l'OpenAPI, construite à partir des
  routes montées.
- Le menu gagne une notion « Alertes » (Nouvelle alerte, Suivi, Mes alertes) et, dans
  l'administration, « Paramètres des alertes ». Chaque écran a sa fiche d'aide et son audit
  WCAG 2.2.
- La conformité de la grille passe de 13 à **17 critères fonctionnels acquis** : 1 (console d'envoi),
  6 (modèles), 10 (réponses prédéfinies) et 31 (écran de suivi). Les critères 9, 11, 12 et 32
  progressent sans être encore complets (prise en charge, relances, cascades). Le document
  `02_conformite_115_criteres.md` est mis à jour.
