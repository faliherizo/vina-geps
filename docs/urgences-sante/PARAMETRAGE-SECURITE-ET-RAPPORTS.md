# Paramétrage de sécurité, rapports de sécurité et protection des journaux

> AO 26-015-DO, **E-TN-31** : le paramétrage de la solution est conforme aux exigences du ministère
> de la Cybersécurité et du Numérique (MCN), documentées dans la *Liste de vérification de la
> conformité de la zone d'accueil du nuage externe*, avant le déploiement des charges de travail,
> de concert avec l'organisation ; il comprend les accès sécurisés à la solution, aux **rapports de
> sécurité**, et la sécurité des **journaux** transmis, sauvegardés et gérés par la solution.

## 1. Ce que ce document est, et n'est pas

La liste de vérification du MCN n'est pas jointe aux documents de l'appel d'offres ; elle a été
demandée à l'organisme (questions de précision). Ce document range donc les mesures **déjà en
place** par domaine de contrôle, chacune avec l'endroit du dépôt qui la met en œuvre. À la
réception de la liste, chaque ligne de celle-ci reçoit sa correspondance dans le tableau du § 2,
avant le déploiement, avec l'équipe de l'organisation — c'est l'étape « de concert » de l'exigence.

## 2. Mesures par domaine de contrôle

| Domaine | Mesure | Où |
|---|---|---|
| Identité | Connexion par les annuaires Microsoft Entra ID de l'organisme (OIDC + PKCE), autorité multi-locataire refusée, domaines admis | `auth/domaine/Annuaires.java`, `Oidc.java` |
| Identité | Mot de passe fort, second facteur obligatoire, verrou après échecs | `PolitiqueMotDePasse`, `Totp`, `PareFeu` |
| Accès | Refus par défaut ; rôles et capacités par organisme ; périmètres par unité | `RegistrePolitiqueAcces`, `GardeAcces`, `Perimetres` |
| Accès | Connexion restreinte aux pays autorisés par l'organisme | `GardeConnexionPays` (P-TN-11) |
| Réseau | Pare-feu applicatif (OWASP, limitation de débit), répartiteur seul exposé, tâches et données en sous-réseaux privés | `infra/terraform/aws/pare-feu.tf`, `reseau.tf` |
| Réseau | Détection d'intrusion GuardDuty, alerte de déni de service | `detection.tf` |
| Réseau | VPN IPsec optionnel vers le réseau de l'organisme | `liaison-privee.tf` |
| Chiffrement | TLS 1.2 et plus au répartiteur ; HSTS et cookie `Secure` ; volume et copies chiffrés ; montage en TLS | `calcul.tf`, `stockage-applicatif.tf` |
| Secrets | Aucun secret dans le dépôt ni dans la définition de tâche : Secrets Manager ; déploiement par OIDC, sans clé | `calcul.tf`, `deploiement-ci.tf` |
| Résidence | Données, copies et journaux à Montréal ; copie hors Québec refusée sans autorisation écrite | `variables.tf`, `sauvegardes-hors-region.tf` |
| Vulnérabilités | Balayage hebdomadaire, DAST, mises à jour des dépendances chaque semaine | `ci-cd.yml`, `.github/dependabot.yml` |
| Continuité | Copies quotidiennes et mensuelles (6 ans), essai de restauration mensuel | `stockage-applicatif.tf`, [COPIES-DE-SECURITE.md](COPIES-DE-SECURITE.md) |
| Données | Destruction attestée en fin de vie ; copie séparée par organisme | `Destruction`, `CopiesOrganismes` |

## 3. Le rapport de sécurité mensuel

Chaque mois, l'exploitant remet à l'organisme un rapport fait des pièces suivantes, toutes produites
par la solution et accessibles aux seuls comptes autorisés :

| Pièce | Source | Accès |
|---|---|---|
| Registre des incidents de la période (PDF, CSV) | `GET /api/securite/incidents.pdf`, `.csv` | Administration de l'organisme |
| État de sécurité de l'exploitation (chiffrement, résidence, pare-feu, sauvegardes, versions) | `GET /api/exploitation/securite` | Administration du système |
| Intégrité du journal d'audit (chaîne de hachage vérifiée) | `GET /api/auth/audit/integrite` | Administration |
| Revues périodiques du journal d'audit, consignées | `GET /api/auth/audit/revues` | Administration |
| Alertes de l'organisme (exports téléchargés, connexions refusées par pays) | Administration › Organisme, journal des alertes | Administration de l'organisme |
| Découvertes GuardDuty et alarmes de la période | Sujet d'alertes de l'exploitation | Exploitant |
| Essais de restauration du mois | AWS Backup, « Restore testing » ; `GET /api/exploitation/sauvegardes/restaurations` | Exploitant |

## 4. Sécurité des journaux

- **Journal d'audit chaîné** : chaque entrée porte l'empreinte de la précédente ; une altération
  rompt la chaîne, et la vérification (`/api/auth/audit/integrite`) dit où.
- **Transmission** : l'export SIEM (`/api/auth/audit/siem-export`) part en HTTPS, sur demande d'un
  compte autorisé ; le téléchargement déclenche les règles d'alerte de l'organisme.
- **Conservation** : le journal d'audit vit dans la base, donc dans chacune de ses copies (§ 2,
  continuité) ; les journaux techniques vont à CloudWatch Logs, à Montréal.
- **Contenu** : les journaux applicatifs n'écrivent ni mot de passe, ni jeton, ni renseignement de
  santé (garde-fous statiques, ADR 0041).
- **Erreurs** : chaque requête laisse une ligne `http` (méthode, chemin masqué, statut, durée,
  utilisateur, organisme, `X-Requete-Id`) ; chaque requête mutante d'un compte, une ligne au journal
  des actions (`journal_actions`, écran Administration). S'y ajoutent :
  - `evenement=refus` (info) — un refus du métier rendu en 4xx, par son **type** et son statut ;
    jamais son message, qui nomme des personnes (`JournalErreurs.refus`) ;
  - `evenement=erreur` (warn) — une exception imprévue que la garde des effectifs a tout de même
    rendue en 400 : la pile accompagne la ligne (`JournalErreurs.imprevue`) ;
  - `evenement=erreur` (error) — une exception qu'aucun bord n'a traduite, consignée avec sa pile
    par le filtre du journal ; la ligne `http` qui suit porte alors 500, comme l'action.

## 5. Ce qui reste

- Recevoir la liste de vérification du MCN et en remplir la correspondance avec l'organisation.
- Fixer avec l'organisation le destinataire et le jour de remise du rapport mensuel.
