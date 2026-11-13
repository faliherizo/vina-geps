# ADR 0071 — La politique de soutien suit le contrat de l'organisme ; les lots s'échangent aussi par SFTP

- **Statut** : accepté
- **Date** : 12 novembre 2026
- **Décideurs** : équipe Nexu VINA
- **Concerne** : `backend-java/.../soutien/domaine/EngagementsDeService.java`, `Soutien.java`,
  `DepotSoutien`, `DepotEngagementsSoutien`, `ControleurEngagementsSoutien` ;
  `backend-java/.../effectifs/domaine/IntegrationsEffectifs.java` (mode `SFTP`),
  `ClientSftpIntegration`, `DestinationSftpIntegration`, `LotsSftpIntegration` ;
  migration Node `0173` ; `frontend/src/soutien/EngagementsSoutien.tsx`, `Soutien.tsx`,
  `effectifs/Integrations.tsx`
- **Source** : AO 26-015-DO, addenda 06 (réponses 1, 3, 9, 10) ; contrat, clauses 5.02 et 6.01

## Contexte

Le module Soutien portait **en constantes** les engagements d'un autre appel d'offres : un seul
délai par niveau, compté depuis l'ouverture (1 h, 2 h, 4 h, 24 h), et 99,5 % de disponibilité.
Le contrat 26-015-DO en exige d'autres : un **délai de réponse** (retour d'appel du technicien)
qui dépend de l'heure d'envoi par rapport aux heures de soutien (5 h à 23 h, 7 j/7), puis un
**délai de résolution** compté depuis ce retour d'appel (2 h, 6 h, 48 h, 7 jours) ; 99,7 % par
mois, manquement majeur sous 99,0 %. Les autres organismes gardent leurs engagements.

L'addenda 06 nomme aussi les mécanismes d'intégration « compatibles » avec l'ESB d'Urgences-santé :
API REST, OAuth/OIDC par Entra ID, webhooks, et **SFTP pour les lots**. Le dernier manquait.

## Décisions

### 1. Une politique par organisme, choisie dans un catalogue fermé

Deux politiques, `standard` et `service-essentiel`, sont des valeurs d'une `enum` : leurs délais
restent dans le code. Ce qui varie par organisme, c'est **laquelle** s'applique, une donnée en
base (`soutien_engagements`), fixée par le soutien (superadmin) et datée. Sans ligne, la
politique standard.

*Écarté* — des délais libres saisis par organisme : un engagement contractuel n'est pas un
réglage, et une valeur saisie à la main deviendrait la preuve d'un engagement que personne n'a
signé. Un nouveau contrat ajoute une valeur au catalogue, avec ses essais.

### 2. Les délais se calculent à la lecture ; rien de calculé n'est stocké

L'échéance d'un billet dépend de la politique **en vigueur** et du retour d'appel, la prise en
charge (`prisEnChargeLe`). Elle se calcule à chaque lecture. La colonne `echeanceLe` héritée garde
l'échéance standard pour le Node, sans plus servir au jugement. Changer la politique d'un
organisme juge autrement ses billets ouverts : c'est voulu, la politique suit le contrat signé.

### 3. La maintenance planifiée sort du temps prévu

Clause 6.01.02 : le temps prévu d'utilisation est le total d'heures du mois moins la maintenance
planifiée. Le calcul existant le faisait déjà ; seul l'objectif changeait. Le mois est jugé
contre chaque politique en usage (`aucun`, `manquement`, `manquement_majeur`).

### 4. SFTP : une implémentation de plus des ports existants, empreinte d'hôte épinglée

Le mode `SFTP` s'ajoute à `SIMULATEUR` et `HTTP` (OCP) : `DestinationIntegrationPort`,
`SourceHorodatagesIntegrationPort` et `SourceDossiersIntegrationPort` reçoivent chacun leur
adaptateur, choisi par la configuration du connecteur. Trois choix :

- **L'empreinte de la clé d'hôte est obligatoire** dans l'adresse (`?empreinte=SHA256:…`). La
  confiance au premier contact (*trust on first use*) livrerait les lots de paie au premier
  serveur qui usurpe l'adresse.
- **Authentification par clé seulement**, lue dans une variable d'environnement nommée, comme le
  jeton HTTP : ni mot de passe, ni secret en base.
- **Dépôt atomique et idempotent** : nom temporaire puis renommage ; le nom du fichier est la clé
  d'idempotence, un message déjà déposé n'est pas réécrit.

La suite de contrat du port de destination est **scindée** : toute destination tient
l'acceptation et l'idempotence ; seule une destination qui répond (simulateur, HTTP) tient le
refus motivé. Un dépôt de fichier n'a pas de réponse synchrone : lui imposer `Rejetee` serait
un contrat qu'il ne peut pas tenir (LSP).

*Écarté* — un serveur SFTP hébergé par nous (AWS Transfer Family) : un service de plus à
exploiter et à facturer, alors que l'ESB d'un organisme public expose d'ordinaire son propre
dépôt. Il reste possible sans toucher au code : l'adaptateur parle à n'importe quel serveur SFTP.

Bibliothèque : Apache MINA SSHD 2.15 (client en production, serveur en mémoire dans les essais
d'intégration). La version 2.7, présente dans le cache local, était exposée à Terrapin
(CVE-2023-48795).

## Conséquences

- E-TN-11 reste « hors code » (dotation 5 h-23 h), mais le logiciel mesure désormais les délais
  du contrat ; E-TN-9 juge le mois contre 99,7 % ; E-TN-7 offre les quatre mécanismes admis.
- Les essais : `EngagementsDeServiceTest`, `ControleurEngagementsSoutienHttpTest`,
  `IntegrationsSftpTest` (serveur SFTP réel), `engagements-soutien.test.tsx`, `soutien.spec.ts`,
  `effectifs-integrations.spec.ts`.
- Le Node n'est pas porté : il ne sert plus la pile par défaut. Son `/api/soutien` garde la
  politique standard.
- Une seule tâche écrivaine (SQLite) : le client SFTP ouvre une session par dépôt ou cueillette.
  Si le volume de lots le justifie, une session partagée par passage de l'ordonnanceur.
