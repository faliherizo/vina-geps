# FX-AO-20260810 : conformité des 115 critères de la grille, contre VINA GePS

> Écrit pour l'équipe de soumission. Chaque critère de la grille reçoit un statut, la preuve dans
> VINA GePS (module, écran ou route) et, s'il y a lieu, l'écart et la story qui le comble.

**Statuts.**
- ✅ **Acquis** : servi aujourd'hui, preuve citée.
- 🟡 **Partiel** : la base existe, un complément est à bâtir.
- 🔴 **À bâtir** : absent.
- 📄 **Engagement** : obligation contractuelle ou documentaire, sans code.
- 🏢 **Établissement** : responsabilité de l'établissement selon la grille.
- ❓ **À clarifier** : la réponse dépend d'une question au client.

Les stories `ST-Gxx` sont détaillées dans [05_roadmap_et_stories.md](05_roadmap_et_stories.md).

## Bilan

| Bloc | Critères | ✅ | 🟡 | 🔴 | 📄 / 🏢 | ❓ |
|---|---|---|---|---|---|---|
| Fonctionnel | 1-32 | 31 | 1 | 0 | 0 | 0 |
| Technique (postes, bureautique, réseau) | 33-49 | 14 | 1 | 0 | 2 | 0 |
| Architecture et hébergement | 50-63 | 7 | 0 | 0 | 7 | 0 |
| Sauvegarde, surveillance | 64-70 | 6 | 0 | 0 | 1 | 0 |
| Sécurité, identité, audit | 71-93 | 17 | 0 | 0 | 6 | 0 |
| Performance, fournisseur, soutien | 94-115, 99.1 | 8 | 0 | 0 | 15 | 0 |
| **Total** | **116** | **83** | **2** | **0** | **31** | **0** |

**Revue du 15 novembre 2026, contre le code.** Les neuf partiels ont été relus un à un. Trois étaient périmés :
- 11 : l'état « relancée » manquait encore ; il est ajouté.
- 51 et 61 : le guide d'installation était écrit.
- 89 : la traçabilité des alertes était déjà complète.

Deux ont été bâtis : 82 (LDAPS, ADR 0083) et 84 (certificat nominatif, Terraform). Les trois qui restent attendent
quelqu'un d'autre que nous :
- 2 : la réponse du client à Q-DOS.
- 18 : l'application mobile, et le droit d'Apple (Q-ALC).
- 39 : une exécution de veraPDF, branchée en CI mais jamais lancée.

Sur les **32 critères fonctionnels**, 30 sont acquis, 2 partiels (la note, livrée dans sa lecture « note interne » en attendant Q-DOS, et l'alerte critique sur l'application mobile), aucun à bâtir ni à clarifier, après l'appel automatique (ST-G06, R3-a), la réponse par texto (ST-G05, R2-b), les relances et cascades (ST-G04, R2-a, 14 novembre 2026) et les compléments R1-bis (14 novembre 2026 : droit du critique, listes, copie par courriel, modèles versionnés à périmètre, suivi filtrable et exportable, groupes transverses). Le socle (ADR 0074) en donnait 17 acquis, 10 partiels et 4 à bâtir ; le dossier d'origine, 13, 12 et 6.
Les 18 critères partiels ou à bâtir forment le module « Communications cliniques » : modèles et
réponses prédéfinis, console d'alerte, suivi, relances et cascades, appel automatique, alertes
critiques.

## Mise à jour du 19 novembre 2026 — addenda 1 à 3 et contrat

Traitement détaillé : [08_addenda_1_a_3/01_traitement_des_addenda.md](../08_addenda_1_a_3/01_traitement_des_addenda.md).
Le dépôt se fait sur la grille de l'**addenda 3**.

| Critère | Ce qui change | Statut |
|---|---|---|
| 2 | L'addenda 1 (Q1) exclut tout renseignement de santé : la « note au dossier » ne peut pas viser un dossier patient. La note sur le destinataire livrée y répond. Elle refuse aussi un numéro d'assurance maladie (ST-H04). | 🟡 → ✅ |
| 7 | Les alertes sont envoyées par des utilisateurs ; aucune intégration d'appareils biomédicaux. | ✅ |
| 18 | Précisé : passer outre le mode silencieux, « Ne pas déranger » et Critical Alerts d'iOS. Notre lecture était la bonne. | 🟡 (Q-ALC) — chaîne serveur → Expo → APNs/FCM → téléphone prouvée au bac à sable (BAC-01 à 07, ADR 0086) |
| 38 | Extraction des rapports en XLS pour Office 2016 : le XLSX que nous produisons s'y ouvre. | ✅ |
| 39 | PDF/A pour les rapports d'activité et d'analyse. | 🟡 (veraPDF branché au bac à sable et en CI, BAC-08/09 ; exécution à obtenir) |
| 40 | Il suffit que les courriels soient **reçus** dans Outlook 365. | ✅ |
| 44 | Multicast permis avec la DRI ; nous n'en émettons pas. | ✅ |
| 51, 56-61, 64, 69, 70 | **Sans objet** en SaaS exclusif (addenda 3, Q5) : à commenter ainsi dans la grille. | Sans objet |
| 53 | Hébergement et sauvegardes au Canada ; le transit n'est pas de l'hébergement. | ✅ |
| 81 | Souhaité, non obligatoire : SSO Entra ID. Offert. | ✅ |
| 98 | Politique « communications cliniques » (ST-H05). | 📄 → ✅ |
| 99, 99.1 | 21 et 3 jours ouvrables, tenus par le serveur (ST-H02). | 📄 → ✅ |

## 1. Fonctionnel (1 à 32)

| # | Critère (résumé) | Statut | Preuve dans VINA GePS | Écart, story |
|---|---|---|---|---|
| 1 | Console web d'envoi centralisée, alerte par application mobile ou texto selon la préférence du destinataire | ✅ | **Nouvelle alerte** (ADR 0074) : priorité, modèle, groupes et personnes ; canaux selon les préférences, tous imposés si critique | — |
| 2 | Note au dossier des destinataires | ✅ | **Note courte et datée sur la fiche du destinataire** (ST-G12, 15 novembre 2026) : tenue et lue par les expéditeurs autorisés dans le suivi, jamais au journal en clair (`/api/alertes/notes`, changeset `0017`) | Livré dans la lecture « note interne » ; si Q-DOS répond « dossier patient », c'est une intégration distincte |
| 3 | Recherche de destinataire par nom et prénom | ✅ | Champ personne avec autocomplétion à 3 caractères ; membres nommés | — |
| 4 | Messages texte bidirectionnels entre équipes, médecins, utilisateurs autorisés | ✅ | Messagerie du personnel : fils à deux et de groupe, réponse privée (ADR 0072) ; **réponse par texto** à une alerte (ST-G05, ADR 0078 : webhook Twilio signé, « 1 »/« 2 », rejeu ignoré) | — |
| 5 | Envoi à une personne, à plusieurs ou à un groupe configurable | ✅ | Fil à deux, fil de groupe, listes de diffusion (personnes et groupes recalculés) | — |
| 6 | Messages prédéfinis pour les expéditeurs | ✅ | **Modèles d'alerte** (Paramètres des alertes, `/api/alertes/modeles`, ADR 0074) | — |
| 7 | Alertes en temps réel | ✅ | Poussée web et native, texto, cloche ; **alerte priorisée** (`/api/alertes`), la priorité critique force tous les canaux et se réserve à la capacité `alertes.critique` (R1-bis) | — |
| 8 | Copie courriel si configurée par l'organisme | ✅ | **Réglage d'organisme** « copie par courriel » (Paramètres des alertes, `/api/alertes/reglages`) : une copie à chacun, sauf s'il l'a déjà reçue par courriel (R1-bis) | — |
| 9 | Confirmation de réception, lecture ou prise en charge, simple et automatisée | ✅ | Accusé reçu et lu automatique (ADR 0072) ; **prise en charge d'un geste** à l'écran (ADR 0074) ou **par texto** « 1 » (ST-G05), qui arrête la cascade | — |
| 10 | Réponses prédéfinies, configurées par l'administration | ✅ | **Réponses prédéfinies** à sémantique fermée (prise en charge, refus, information), `/api/alertes/reponses` | — |
| 11 | Statut d'envoi, réception, lecture, réponse, relance, vu par l'expéditeur | ✅ | **Suivi des alertes** : par alerte, lues sur destinataires, réponses, **relances** (une par étape de cascade exécutée) et prise en charge ; filtre « relancée » ; export avec la colonne des relances. Au détail : chaque remise (envoyée, lue, répondue, horodatées) et la trace de la cascade, les personnes nommées (15 novembre 2026) | — |
| 12 | Historique et statuts partagés entre expéditeurs | ✅ | **Suivi des alertes** partagé entre expéditeurs autorisés, filtrable (période, priorité, état, expéditeur) et exportable en CSV, XLSX, PDF, JSON (R1-bis) | PDF/A pour l'archive — ST-G10 |
| 13 | Confirmer la réception d'un retour d'appel | ✅ | Appel à deux tracé (sonne, en cours, terminé, manqué) ; **appel automatique d'alerte** : « appuyez sur 1 » prend en charge et arrête la cascade, rappel signé (ST-G06, ADR 0079) | — |
| 14 | Appel téléphonique automatique si non-réponse, délai programmable | ✅ | Étape de cascade de canal **« appel »**, à son délai (ST-G04) ; synthèse vocale française, touche 1/2, résultat (répondu, messagerie, occupé, sans réponse, échec) et durée tracés (ST-G06) | — |
| 15 | Relances automatisées configurables | ✅ | **Politiques de relance** (ST-G04, ADR 0077) : étapes (cible, canaux, délai) versionnées ; échéances durables dans l'ordonnanceur partagé | — |
| 16 | Cascades entre utilisateurs d'un groupe ou de groupes distincts | ✅ | Étapes « tout un groupe », « une personne », « prochain disponible d'un groupe » ; les personnes escaladées rejoignent l'alerte | — |
| 17 | Délais, séquences, règles d'arrêt et de reprise des cascades | ✅ | Délai par étape ; arrêt à la prise en charge ou quand tous ont lu ; **reprise manuelle** à l'étape choisie, journalisée (`/api/alertes/envoyees/{id}/cascade/reprendre`) | — |
| 18 | Alertes critiques, lorsque disponible et permis | 🟡 | Poussée native et navigateur ; **une alerte critique se pousse en critique** (ST-G07, ADR 0080) : priorité haute sur le canal Android `critique`, niveau d'interruption et son critiques iOS, interaction exigée et vibration prolongée dans le navigateur | Application mobile (hors de ce dépôt) : déclarer le canal Android `critique` à importance maximale ; iOS : droit *Critical Alerts* d'Apple (Q-ALC). En attendant, repli : texto puis appel par la cascade (ST-G04, ST-G06). **Bac à sable** (ADR 0086) : la chaîne est jouée de bout en bout contre le Java — iPhone avec et sans le droit d'Apple, Android avec et sans le canal, désinstallation (`e2e/donnees/fx-ao/bac-a-sable.json`, BAC-01 à 07, verts le 20 nov.) |
| 19 | Traces des relances, appels, réponses, escalades | ✅ | Journal des actions (ADR 0041) ; **trace de cascade** en ajout seul : armement, chaque étape et les personnes visées, arrêt, épuisement, reprise (par qui) | Appels : ST-G06 |
| 20 | Accès mobile par navigateur, sans équipement dédié | ✅ | Interface responsive (390 px auditée), application installable (PWA) | — |
| 21 | Application mobile iOS et Android, si proposée | ✅ | PWA ; application native du dépôt `mobile` (poussée native) | — |
| 22 | Préférences par destinataire sans contourner les règles obligatoires | ✅ | Préférences d'avis par événement, canal écrit obligatoire maintenu ; **alerte critique** : tous les canaux d'avis imposés, événements `alerte.recue` et `alerte.critique` essentiels (ST-G07) ; l'appel n'est jamais imposé d'office (ST-G06) | — |
| 23 | Interface intuitive, rapide en contexte clinique | ✅ | SGQRI 008 / WCAG 2.2 AA audité ; tableau de bord en un clic | — |
| 24 | Historique de ses messages et confirmations selon ses droits | ✅ | Messagerie : historique, recherche limitée à sa participation | — |
| 25 | Calendrier interne de disponibilité, si activé | ✅ | Disponibilités, horaires, « Mon horaire » en calendrier (module effectifs) ; **les cascades « prochain disponible » le consultent** (absence acceptée, indisponibilité déclarée) | — |
| 26 | Console de gestion centralisée pour l'administration | ✅ | Administration de l'organisme, superadmin | — |
| 27 | Créer, modifier, désactiver des utilisateurs et des groupes par unité, service, spécialité, établissement | ✅ | Comptes, unités datées, organigramme ; **groupes transverses** (garde, spécialité, comité) à appartenance datée et modérateurs, visibles dans la messagerie, les listes et les alertes (R1-bis, `/api/communication/groupes-transverses`) | — |
| 28 | Cloisonner les utilisateurs dans un ou plusieurs groupes | ✅ | Périmètres par unité, refus par défaut | — |
| 29 | Coordonnées confidentielles | ✅ | Appel et texto au numéro de l'organisme : le numéro personnel n'est jamais montré (ADR 0022) | — |
| 30 | Comptes utilisateur et administrateur, profils plus fins | ✅ | 7 rôles, rôles propres à l'organisme, capacités (E-TN-5) | — |
| 31 | Écran de visionnement des messages envoyés et des statuts | ✅ | **Suivi des alertes** : lues, réponses, première prise en charge, détail par destinataire | — |
| 32 | L'administration configure modèles, réponses, relances, groupes, cascades, droits, disponibilité | ✅ | Droits, groupes (unités et transverses), réponses, modèles versionnés à périmètre (R1-bis), **politiques de relance et de cascade versionnées** (ST-G04) | — |

## 2. Technique (33 à 49)

| # | Statut | Preuve ou écart |
|---|---|---|
| 33, 34 | ✅ | Application web : tout navigateur à jour sous Windows 11, rien à installer (audit des navigateurs `navigateurs-conformite.spec`). |
| 35, 37 | ✅ | L'impression passe par le navigateur et le service Windows ; aucun pilote propre. |
| 36 | 📄 | Engagement de nomenclature : noms d'hôtes et groupes AD fournis par l'établissement. |
| 38 | ✅ | Exports CSV, XLSX et PDF lisibles par Office 2016. |
| 39 | 🟡 | **Tous les PDF du produit sont produits en PDF/A-2b** (ST-G10, ADR 0081) : polices Liberation Sans incorporées, profil sRGB, XMP de conformité, identifiant — vérifiés par `PdfAConformiteTest`. Reste : la validation **veraPDF**, branchée en CI (`scripts/verifier-pdfa.sh`, et le travail `bacs-a-sable` qui soumet les exports réels du Java à veraPDF REST, BAC-08/09 — ADR 0086), pas encore exécutée. |
| 40 | ✅ | Courriel SMTP authentifié (Outlook 365 admis comme relais) **ou Microsoft Graph** (`Mail.Send`, application Entra ID, jeton gardé) — choisi par la configuration (ST-G11, ADR 0082). |
| 41, 42, 44, 47, 49 | ✅ | HTTPS standard sur le WAN, aucun broadcast, résolution DNS, compatible avec le mandataire (en-têtes `X-Forwarded-*`). |
| 43 | 🏢 | Adressage privé 10.x pour un déploiement local (profil B). |
| 45, 46, 48 | ✅ | Le Wi-Fi est celui du terminal : l'application n'impose rien (WPA2, 802.11ax, 802.1x restent du ressort du réseau). |

## 3. Architecture et hébergement (50 à 63)

| # | Statut | Preuve ou écart |
|---|---|---|
| 50 | ✅ | SaaS, ou installation dédiée par organisme : même image, configuration par environnement. |
| 51, 61 | ✅ | Java 25, un seul `.jar` : service Windows (WinSW) sur Windows Server 2019+, sur une VM vSphere 8 chiffrée ; frontal IIS/ARR. Guide d'installation et de retour arrière : [09_exploitation_chez_l_etablissement.md](09_exploitation_chez_l_etablissement.md) — ST-G13. L'essai sur l'infrastructure de l'établissement se fait à l'implantation (R5). |
| 52 | ✅ | Application web : accessible dans un navigateur publié par Citrix. |
| 53 | ✅ | Données, bases et sauvegardes **au Canada** (addenda 3, Q7 : le transit des SMS, appels et courriels n'est pas de l'hébergement) ; Terraform `ca-central-1`. |
| 54, 55 | 📄 | Documents existants : `SECURITE-DES-TRANSMISSIONS.md`, architecture, `navigateurs`. À assembler pour la soumission. |
| 56, 57, 58 | 🏢 | Profil B : l'établissement acquiert, gère et installe le système d'exploitation. |
| 59, 60 | 📄 | Dimensionnement : 2 VM (application 4 vCPU / 8 Go ; base 2 vCPU / 8 Go, 200 Go), tiré des mesures JMeter. |
| 62 | ✅ | Heure du système (NTP de l'établissement) ; horodatage UTC, affichage America/Toronto. |
| 63 | ✅ | Image reconstruite à chaque correctif ; aucun agent figé sur les postes. |

## 4. Sauvegarde et surveillance (64 à 70)

| # | Statut | Preuve ou écart |
|---|---|---|
| 64 | ✅ | Base en fichier et volume de données : sauvegardables par l'agent de l'établissement (`COPIES-DE-SECURITE.md`). |
| 65 | ✅ | Sauvegardes applicatives planifiées, copies par organisme (superadmin › Sauvegardes). |
| 66 | ✅ | Rétention par classe de données (effectifs, messagerie) ; **archivage** formalisé : copie complète annuelle par organisme, conservation 6 ans recommandée (profil A : AWS Backup mensuel 6 ans ; profil B : [09_exploitation_chez_l_etablissement.md](09_exploitation_chez_l_etablissement.md), § 3) — ST-G13. |
| 67 | ✅ | Purge par politique de rétention, droit à l'oubli (Administration › Confidentialité). |
| 68 | 📄 | Engagement : test de restauration accompagné, tous les 24 mois. |
| 69 | ✅ | Port antivirus (fail-closed) ; compatible avec Microsoft Defender EDR sur l'hôte. |
| 70 | ✅ | `/api/health` et métriques : supervisables par Zabbix (ping, disque, CPU). |

## 5. Sécurité, identité, audit (71 à 93)

| # | Statut | Preuve ou écart |
|---|---|---|
| 71, 72 | ✅ | Loi 25 : journalisation sans contenu sensible, rétention, droit à l'oubli. |
| 73 | ✅ | TLS en transit (HSTS). |
| 74 | ✅ | Chiffrement au repos : volume chiffré (EFS, disque OVH) ; **sauvegardes chiffrées** — profil A : coffre AWS Backup et EFS chiffrés par KMS (`infra/terraform/aws`, COPIES-DE-SECURITE.md § 4) ; profil B : VM chiffrée vSphere ou BitLocker, et outil de sauvegarde chiffré (guide § 3) — ST-G13. |
| 75, 76 | 📄 | Engagements : SIEM, incidents (`securite/domaine/Incidents`), avis sans délai. |
| 77, 78, 80 | ✅ | Rôles et capacités, refus par défaut, désactivation immédiate d'un compte. |
| 79 | ✅ | Comptes nominatifs ; aucun compte générique requis. |
| 81 | ✅ | OIDC Entra ID, SAML, plusieurs annuaires par organisme (P-TN-2). |
| 82 | ✅ | **Annuaire LDAP joint en LDAPS seulement** (ADR 0083) : `ldap://` refusé à la saisie, certificat et nom d'hôte vérifiés, liaison au nom de la personne, filtre échappé, verrou après échecs ; rôle par les groupes AD. Éprouvé contre un vrai serveur LDAPS en mémoire (`LiaisonLdapsTest`). La fédération OIDC (Entra, Keycloak, AD FS — ADR 0075) ou SAML reste offerte. |
| 83 | 📄 | Remise de l'information GIA au pilote (`UTILISATEURS-PROFILS-ACCES.md`). |
| 84 | ✅ | **Certificat nominatif** demandé par Terraform à ACM (`infra/terraform/aws/certificat.tf`) pour les seuls hôtes nommés ; un nom en `*.` est refusé au `plan` (`tests/certificat-nominatif.tftest.hcl`, en CI). Profil B : le certificat de l'établissement, nominatif, sur le frontal. |
| 85 | ✅ | 12 caractères et 4 classes (plus strict que l'exigence). |
| 86 | ✅ | **Historique des 10 dernières empreintes** ; un mot de passe employé ces 6 derniers mois, le courant compris, est refusé sur tous les chemins (changement, premier mot de passe, réinitialisation, administration) — ST-G10, ADR 0081, changeset `0016`. |
| 87 | ✅ | Navigateur sans privilège ; conteneur sans racine. |
| 88 | 📄 | « Service F » à confirmer (GAP-11). |
| 89 | ✅ | Par alerte : expéditeur, contenu, envoi horodaté ; par destinataire : envoyée, lue, réponse, horodatées ; relances, escalades et reprises (trace de cascade en ajout seul, avec qui ou quel mécanisme) ; appels (résultat, durée) ; réponses par texto. Toute requête mutante est de plus au journal des actions (ADR 0041). Messagerie : journal propre. |
| 90, 93 | ✅ | Journal en lecture seule, accès selon les droits ; export CSV, XLSX, PDF, JSON. |
| 91, 92 | 📄 | Documenter la conservation des journaux ; accepter l'audit sans préavis. |

## 6. Performance, fournisseur, soutien (94 à 115)

| # | Statut | Preuve ou écart |
|---|---|---|
| 94 | 📄 | Démonstration équivalente : Urgences-santé (26-015-DO) et le module Santé Québec. **Critère à risque.** |
| 95 | ✅ | Campagne JMeter (`perf/jmeter`), à rejouer à 2 000 utilisateurs. |
| 96 | ✅ | Multi-organisme, multi-unité, multi-site (sous-domaines, milieux). |
| 97, 98 | ✅ | **Politique de soutien « communications cliniques »** (ST-H05, ADR 0085) : réponse en 4 h ou moins à tous les niveaux, en tout temps ; résolution N1 1 h, N2 2 h, N3 4 h, N4 24 h ; 95 % d'efficacité (contrat 10.19.07). Attribuée par le superadmin (onglet Soutien). |
| 99, 99.1, 113 | ✅ | **Préavis selon la nature du changement, tenu par le serveur** (ST-H02, ADR 0085) : rehaussement **21 jours ouvrables** (99, addenda 1), correctif **3 jours ouvrables** avec sa nature communiquée (99.1), entretien 72 h ; jours fériés du Québec exclus (`PreavisDeChangementTest`). |
| 100 | ✅ | Retour à la version précédente : images versionnées et `.jar` N-1 ; **retour arrière de schéma** par un script de retour par changeset depuis `0006` (`db/retour/`), éprouvé par `RetourArriereSchemaTest` (retour complet, puis remigration) — ST-G13. |
| 101 | 📄 | **Écart contractuel** : astreinte 24/7 en français pour les incidents critiques (aujourd'hui : 5 h à 23 h). |
| 102-107 | 📄 | Plan d'implantation, chargé de projet, registres, formation en français (`TROUSSE-FORMATION.md`, `GUIDE-ECRANS.md`). |
| 108-111 | ✅ / 📄 | Module Soutien (billets, niveaux, délais, satisfaction) ; rapports périodiques. |
| 112, 114, 115 | 📄 | Maintenance incluse ; configuration personnalisée ; PCA et PRA. |
