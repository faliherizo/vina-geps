# FX-AO-20260810 : feuille de route et stories du module « Communications cliniques »

> Écrit pour l'équipe de réalisation et pour la soumission (plan d'implantation, critère 102). La
> feuille de route part de VINA GePS tel qu'il est ; elle remplace les phases P1 et P2 du dossier
> d'origine, qui supposaient tout à construire. Ce n'est **pas** un plan d'implantation à engager
> tant que l'ADR 0074 n'est pas accepté.
>
> **Mise à jour du 14 novembre 2026.** Le socle de R1 est livré (ADR 0074 accepté pour R1). Le plan
> d'exécution à jour, phase par phase, à partir de l'état réel du code, est
> [08_plan_implantation.md](08_plan_implantation.md) : environ 19 semaines jusqu'au pilote.

## 1. Feuille de route

| Phase | Durée | Contenu | Sortie |
|---|---|---|---|
| R0 — Cadrage | 2 sem. | Réponses Q-TEL, Q-ALC, Q-DOS ; choix du profil d'hébergement (A ou B) ; ADR 0074 accepté ; fournisseur voix et texto | Décisions consignées |
| R1 — Socle d'alerte (**livré** ; reste R1-bis, 2 sem.) | 4 sem. | ST-G09 groupes transverses, ST-G02 modèles et réponses, ST-G01 console d'alerte, ST-G03 suivi | Une alerte part, se suit, se confirme (démo interne) |
| R2 — Relances et cascades | 5 sem. | ST-G04 politiques et orchestration, ST-G05 réponse par texto | Cascade de 3 étapes arrêtée par une prise en charge, rejouable après panne |
| R3 — Voix et critique | 3 sem. | ST-G06 appel automatique et DTMF, ST-G07 alerte critique mobile | Escalade jusqu'à l'appel, confirmée au clavier |
| R4 — Conformité | 2 sem. | ST-G08 utilisateurs actifs, ST-G10 PDF/A et historique des mots de passe, ST-G11 Graph et LDAPS, ST-G12 note | Grille fonctionnelle à 31/32, puis 32/32 selon Q-DOS |
| R5 — Exploitation | 2 sem. | ST-G13 Windows Server, VMware, archivage, retour arrière ; JMeter à 2 000 utilisateurs ; essais de restauration | Go/no-go technique |
| R6 — Pilote | 3 sem. | Formation de deux pilotes, import initial, un site (par exemple l'Hôpital de Saint-Jérôme) | Acceptation du pilote |
| R7 — Déploiement | selon calendrier | Les cinq autres sites, vague par vague | Acceptation par site |

Le total jusqu'au pilote est d'environ 21 semaines.

## 2. Stories

Format : *En tant que…, je veux…, afin de…* Chaque story est terminée quand elle satisfait la
définition de « terminé » du dépôt : essai d'acceptation automatisé, JaCoCo ≥ 96 % par classe,
axe-core à zéro violation, OpenAPI à jour, essai BDD pour chaque critère de la grille cité.

### ST-G01 — Console d'alerte · critères 1, 5, 7, 8 · M
*En tant qu'expéditeur autorisé, je veux envoyer une alerte priorisée à une personne, une liste ou
un groupe, afin de joindre vite la bonne équipe.*
- La priorité (`INFORMATION`, `URGENTE`, `CRITIQUE`) est une enum ; la priorité critique exige la
  capacité `alertes.critique`.
- Les destinataires sont résolus **à l'envoi**, et leur snapshot est conservé.
- Les canaux suivent les préférences du destinataire, sauf ceux que la priorité rend obligatoires.
- La copie par courriel est activable par organisme (critère 8).
- Un envoi sans destinataire résolu est refusé (400 nommé), avec la raison.

### ST-G02 — Modèles de message et réponses prédéfinies · critères 6, 10, 32 · S
*En tant qu'administrateur, je veux définir des modèles d'alerte et les réponses proposées aux
destinataires, afin d'uniformiser les messages cliniques.*
- Les modèles et les réponses sont **versionnés et datés** : une alerte garde la version
  qu'elle a envoyée.
- Une réponse porte une sémantique fermée : `PRISE_EN_CHARGE`, `REFUS`, `INFORMATION`.
- La personne ne voit que les modèles de son périmètre.

### ST-G03 — Suivi des communications · critères 9, 11, 12, 19, 31, 89 · M
*En tant qu'expéditeur, je veux voir, par destinataire, si mon alerte est remise, lue, répondue,
prise en charge ou relancée, afin de savoir qui a réagi.*
- Un tableau « Suivi des communications », filtrable, partagé entre les expéditeurs autorisés du
  même groupe.
- Chaque transition est horodatée et attribuée (acteur ou mécanisme), en ajout seul.
- L'export CSV, XLSX et PDF/A reprend le même filtre.

### ST-G04 — Relances et cascades · critères 15, 16, 17, 19, 25, 32 · L
*En tant qu'administrateur, je veux définir une politique : relancer après N minutes, puis passer
au groupe suivant, s'arrêter dès qu'une personne prend en charge, afin qu'aucune alerte ne reste
sans réponse.*
- Une politique est une suite d'étapes (cible, canaux, délai). Le critère d'arrêt et la reprise
  sont configurables et versionnés.
- L'échéance est persistée : un redémarrage du serveur ne perd ni ne double aucune étape
  (essai Java « redémarrage en cours de cascade »).
- L'option « prochain disponible » consulte `DisponibiliteDestinataires`.
- Une **reprise manuelle** relance la cascade à l'étape choisie, et le geste est journalisé.

### ST-G05 — Réponse par texto · critères 4, 9, 10 · M
*En tant que destinataire sans l'application, je veux répondre « 1 » ou « OK » par texto, afin de
confirmer depuis n'importe quel téléphone.*
- Un webhook du fournisseur, signé et vérifié, reçoit le texto entrant, rattaché à la dernière
  alerte ouverte vers ce numéro.
- Un mot inconnu reçoit une réponse d'aide et n'est pas compté comme une confirmation.

### ST-G06 — Appel automatique et retour d'appel · critères 13, 14 · M
*En tant que gestionnaire d'alerte, je veux qu'un appel automatique joigne la personne qui ne
répond pas, afin qu'une alerte critique ne dépende pas d'un écran.*
- L'appel est passé par le port `AppelsSortants`, au numéro de l'organisme.
- Un message vocal en synthèse française est joué ; « appuyez sur 1 » vaut prise en charge.
- Le résultat de l'appel (répondu, messagerie vocale, échec) et sa durée sont journalisés.

### ST-G07 — Alerte critique sur mobile · critères 18, 22 · M
- Android : canal de notification « Critique » à importance maximale.
- iOS : *Critical Alerts* si Apple l'autorise (Q-ALC). Sinon, repli documenté : son prolongé,
  texto et appel.
- Le destinataire ne peut pas couper une alerte rendue obligatoire.

### ST-G08 — Utilisateurs actifs facturables · bordereau · S
- Un utilisateur est compté une fois par mois civil (America/Toronto) dès qu'une tentative de
  transmission le vise, directement ou par un groupe ou une cascade.
- Un export justificatif donne, par utilisateur, la première tentative et l'alerte en cause.

### ST-G09 — Groupes transverses · critère 27 · S
- Des groupes de spécialité, de garde ou de comité, hors des unités, avec appartenance datée et
  modérateurs.
- Ils sont visibles dans la messagerie, les listes et les alertes, sans module d'effectifs.

### ST-G10 — Mots de passe et PDF/A · critères 39, 86 · S
- Les 10 dernières empreintes de mot de passe sont conservées et toute réutilisation est refusée
  pendant 6 mois.
- Les exports PDF sont produits en PDF/A-2b, validés par veraPDF dans la CI.

### ST-G11 — Microsoft 365 et LDAPS · critères 40, 82 · S
- Canal courriel par Microsoft Graph (`Mail.Send`, application) ; le relais SMTP reste possible.
- LDAPS en option pour la synchronisation d'un annuaire local.

### ST-G12 — Note sur la fiche du destinataire · critère 2 · XS
- Une note courte et datée, visible des expéditeurs autorisés ; jamais dans le journal en clair.

### ST-G13 — Exploitation chez l'établissement · critères 51, 61, 66, 74, 100 · M
- Guide d'installation sur Windows Server 2019+ et VMware vSphere 8.
- Procédure d'archivage, sauvegardes chiffrées, retour arrière de version et de schéma, avec des
  changesets de retour.

## 3. Ce qui n'est pas à faire

L'identité, la messagerie, les avis multi-canaux, le journal, la rétention, le superadmin, les
sauvegardes, le soutien et l'accessibilité existent. Les stories ST-001, ST-003, ST-007, ST-011,
ST-017, ST-018, ST-023 et ST-024 du dossier d'origine sont **déjà livrées**, ou réduites à leur
paramétrage pour ce client.
