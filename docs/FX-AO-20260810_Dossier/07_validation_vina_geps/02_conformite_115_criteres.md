# FX-AO-20260810 : conformité des 115 critères de la grille, contre VINA GEPS

> Écrit pour l'équipe de soumission. Chaque critère de la grille reçoit un statut, la preuve dans
> VINA GEPS (module, écran ou route) et, s'il y a lieu, l'écart et la story qui le comble.

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
| Fonctionnel | 1-32 | 17 | 10 | 4 | 0 | 1 |
| Technique (postes, bureautique, réseau) | 33-49 | 13 | 1 | 1 | 2 | 0 |
| Architecture et hébergement | 50-63 | 5 | 2 | 0 | 7 | 0 |
| Sauvegarde, surveillance | 64-70 | 5 | 1 | 0 | 1 | 0 |
| Sécurité, identité, audit | 71-93 | 12 | 4 | 1 | 6 | 0 |
| Performance, fournisseur, soutien | 94-115 | 2 | 1 | 0 | 19 | 0 |
| **Total** | **115** | **54** | **19** | **6** | **35** | **1** |

Sur les **32 critères fonctionnels**, 17 sont acquis, 10 partiels, 4 à bâtir et 1 à clarifier, depuis le socle du module alertes (14 novembre 2026, ADR 0074). Il était de 13 acquis, 12 partiels et 6 à bâtir.
Les 18 critères partiels ou à bâtir forment le module « Communications cliniques » : modèles et
réponses prédéfinis, console d'alerte, suivi, relances et cascades, appel automatique, alertes
critiques.

## 1. Fonctionnel (1 à 32)

| # | Critère (résumé) | Statut | Preuve dans VINA GEPS | Écart, story |
|---|---|---|---|---|
| 1 | Console web d'envoi centralisée, alerte par application mobile ou texto selon la préférence du destinataire | ✅ | **Nouvelle alerte** (ADR 0074) : priorité, modèle, groupes et personnes ; canaux selon les préférences, tous imposés si critique | — |
| 2 | Note au dossier des destinataires | ❓ | Fiche de ressource (effectifs) | Q-DOS ; si c'est une note interne : ST-G12 |
| 3 | Recherche de destinataire par nom et prénom | ✅ | Champ personne avec autocomplétion à 3 caractères ; membres nommés | — |
| 4 | Messages texte bidirectionnels entre équipes, médecins, utilisateurs autorisés | ✅ | Messagerie du personnel : fils à deux et de groupe, réponse privée (ADR 0072) | Réponse **par texto** au message reçu (texto entrant) — ST-G05 |
| 5 | Envoi à une personne, à plusieurs ou à un groupe configurable | ✅ | Fil à deux, fil de groupe, listes de diffusion (personnes et groupes recalculés) | — |
| 6 | Messages prédéfinis pour les expéditeurs | ✅ | **Modèles d'alerte** (Paramètres des alertes, `/api/alertes/modeles`, ADR 0074) | — |
| 7 | Alertes en temps réel | 🟡 | Poussée web et native, texto, cloche ; relève toutes les quelques secondes | Diffusion d'alerte avec priorité — ST-G01 |
| 8 | Copie courriel si configurée par l'organisme | 🟡 | Canal courriel des avis, journal des courriels | Copie d'un message d'alerte, par organisme — ST-G01 |
| 9 | Confirmation de réception, lecture ou prise en charge, simple et automatisée | 🟡 | Accusé reçu et lu automatique (ADR 0072, décision 5) | « Prise en charge » d'un geste — ST-G03 |
| 10 | Réponses prédéfinies, configurées par l'administration | ✅ | **Réponses prédéfinies** à sémantique fermée (prise en charge, refus, information), `/api/alertes/reponses` | — |
| 11 | Statut d'envoi, réception, lecture, réponse, relance, vu par l'expéditeur | 🟡 | Acheminement « Envoyé / Reçu par N / Lu par N » | Réponse et relance — ST-G03, ST-G04 |
| 12 | Historique et statuts partagés entre expéditeurs | 🟡 | Historique des versions d'un message ; fil partagé | Suivi d'alerte partagé entre expéditeurs autorisés — ST-G03 |
| 13 | Confirmer la réception d'un retour d'appel | 🟡 | Appel à deux tracé (sonne, en cours, terminé, manqué) | Lier un retour d'appel à l'alerte — ST-G06 |
| 14 | Appel téléphonique automatique si non-réponse, délai programmable | 🟡 | Port d'appel sortant au numéro de l'organisme (ADR 0022) | Appel déclenché par l'orchestrateur — ST-G06 |
| 15 | Relances automatisées configurables | 🔴 | Ordonnanceur existant | Politique de relance — ST-G04 |
| 16 | Cascades entre utilisateurs d'un groupe ou de groupes distincts | 🔴 | — | ST-G04 |
| 17 | Délais, séquences, règles d'arrêt et de reprise des cascades | 🔴 | — | ST-G04 |
| 18 | Alertes critiques, lorsque disponible et permis | 🟡 | Poussée native (application installable) | Notification critique (iOS *Critical Alerts*, Android canal prioritaire) — Q-ALC, ST-G07 |
| 19 | Traces des relances, appels, réponses, escalades | 🟡 | Journal des actions (ADR 0041), journal de la messagerie | Événements d'escalade — ST-G04 |
| 20 | Accès mobile par navigateur, sans équipement dédié | ✅ | Interface responsive (390 px auditée), application installable (PWA) | — |
| 21 | Application mobile iOS et Android, si proposée | ✅ | PWA ; application native du dépôt `mobile` (poussée native) | — |
| 22 | Préférences par destinataire sans contourner les règles obligatoires | ✅ | Préférences d'avis par événement, canal écrit obligatoire maintenu | Étendre aux alertes — ST-G07 |
| 23 | Interface intuitive, rapide en contexte clinique | ✅ | SGQRI 008 / WCAG 2.2 AA audité ; tableau de bord en un clic | — |
| 24 | Historique de ses messages et confirmations selon ses droits | ✅ | Messagerie : historique, recherche limitée à sa participation | — |
| 25 | Calendrier interne de disponibilité, si activé | ✅ | Disponibilités, horaires, « Mon horaire » en calendrier (module effectifs) | Les cascades consultent la disponibilité — ST-G04 |
| 26 | Console de gestion centralisée pour l'administration | ✅ | Administration de l'organisme, superadmin | — |
| 27 | Créer, modifier, désactiver des utilisateurs et des groupes par unité, service, spécialité, établissement | 🟡 | Comptes, unités datées (référentiels), organigramme | Groupes de **spécialité** et transverses (hors unité) — ST-G09 |
| 28 | Cloisonner les utilisateurs dans un ou plusieurs groupes | ✅ | Périmètres par unité, refus par défaut | — |
| 29 | Coordonnées confidentielles | ✅ | Appel et texto au numéro de l'organisme : le numéro personnel n'est jamais montré (ADR 0022) | — |
| 30 | Comptes utilisateur et administrateur, profils plus fins | ✅ | 7 rôles, rôles propres à l'organisme, capacités (E-TN-5) | — |
| 31 | Écran de visionnement des messages envoyés et des statuts | ✅ | **Suivi des alertes** : lues, réponses, première prise en charge, détail par destinataire | — |
| 32 | L'administration configure modèles, réponses, relances, groupes, cascades, droits, disponibilité | 🔴 | Droits et groupes, oui | Modèles, réponses, relances, cascades — ST-G02, ST-G04 |

## 2. Technique (33 à 49)

| # | Statut | Preuve ou écart |
|---|---|---|
| 33, 34 | ✅ | Application web : tout navigateur à jour sous Windows 11, rien à installer (audit des navigateurs `navigateurs-conformite.spec`). |
| 35, 37 | ✅ | L'impression passe par le navigateur et le service Windows ; aucun pilote propre. |
| 36 | 📄 | Engagement de nomenclature : noms d'hôtes et groupes AD fournis par l'établissement. |
| 38 | ✅ | Exports CSV, XLSX et PDF lisibles par Office 2016. |
| 39 | 🔴 | Export **PDF/A** à produire (PDF/A-2b) — ST-G10. |
| 40 | 🟡 | Courriel SMTP authentifié : Outlook 365 admis comme relais. Envoi par Microsoft Graph à ajouter si exigé — ST-G11. |
| 41, 42, 44, 47, 49 | ✅ | HTTPS standard sur le WAN, aucun broadcast, résolution DNS, compatible avec le mandataire (en-têtes `X-Forwarded-*`). |
| 43 | 🏢 | Adressage privé 10.x pour un déploiement local (profil B). |
| 45, 46, 48 | ✅ | Le Wi-Fi est celui du terminal : l'application n'impose rien (WPA2, 802.11ax, 802.1x restent du ressort du réseau). |

## 3. Architecture et hébergement (50 à 63)

| # | Statut | Preuve ou écart |
|---|---|---|
| 50 | ✅ | SaaS, ou installation dédiée par organisme : même image, configuration par environnement. |
| 51, 61 | 🟡 | Java 25 et Docker : s'exécutent sur Windows Server 2019+ (Docker ou service Java) et sur VMware vSphere 8. Guide d'installation Windows à rédiger — ST-G13. |
| 52 | ✅ | Application web : accessible dans un navigateur publié par Citrix. |
| 53 | ✅ | Données au Québec (clause 17.07.04 de 26-015-DO ; Terraform `ca-central-1`, OVH Beauharnois). |
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
| 66 | 🟡 | Rétention par classe de données (effectifs, messagerie) ; procédure d'**archivage** à formaliser — ST-G13. |
| 67 | ✅ | Purge par politique de rétention, droit à l'oubli (Administration › Confidentialité). |
| 68 | 📄 | Engagement : test de restauration accompagné, tous les 24 mois. |
| 69 | ✅ | Port antivirus (fail-closed) ; compatible avec Microsoft Defender EDR sur l'hôte. |
| 70 | ✅ | `/api/health` et métriques : supervisables par Zabbix (ping, disque, CPU). |

## 5. Sécurité, identité, audit (71 à 93)

| # | Statut | Preuve ou écart |
|---|---|---|
| 71, 72 | ✅ | Loi 25 : journalisation sans contenu sensible, rétention, droit à l'oubli. |
| 73 | ✅ | TLS en transit (HSTS). |
| 74 | 🟡 | Chiffrement au repos : volume chiffré (EFS, disque OVH) ; chiffrement **des sauvegardes** à démontrer — ST-G13. |
| 75, 76 | 📄 | Engagements : SIEM, incidents (`securite/domaine/Incidents`), avis sans délai. |
| 77, 78, 80 | ✅ | Rôles et capacités, refus par défaut, désactivation immédiate d'un compte. |
| 79 | ✅ | Comptes nominatifs ; aucun compte générique requis. |
| 81 | ✅ | OIDC Entra ID, SAML, plusieurs annuaires par organisme (P-TN-2). |
| 82 | 🟡 | Pas de LDAP direct : l'annuaire passe par OIDC ou SAML. Si LDAP est exigé, ajouter LDAPS — ST-G11. |
| 83 | 📄 | Remise de l'information GIA au pilote (`UTILISATEURS-PROFILS-ACCES.md`). |
| 84 | 🟡 | Certificat **nominatif par hôte** à prévoir : le déploiement par sous-domaine utilise un wildcard. |
| 85 | ✅ | 12 caractères et 4 classes (plus strict que l'exigence). |
| 86 | 🔴 | Historique des 10 derniers mots de passe pendant 6 mois — ST-G10. |
| 87 | ✅ | Navigateur sans privilège ; conteneur sans racine. |
| 88 | 📄 | « Service F » à confirmer (GAP-11). |
| 89 | 🟡 | Journal des actions et de la messagerie ; à étendre aux alertes — ST-G03. |
| 90, 93 | ✅ | Journal en lecture seule, accès selon les droits ; export CSV, XLSX, PDF, JSON. |
| 91, 92 | 📄 | Documenter la conservation des journaux ; accepter l'audit sans préavis. |

## 6. Performance, fournisseur, soutien (94 à 115)

| # | Statut | Preuve ou écart |
|---|---|---|
| 94 | 📄 | Démonstration équivalente : Urgences-santé (26-015-DO) et le module Santé Québec. **Critère à risque.** |
| 95 | ✅ | Campagne JMeter (`perf/jmeter`), à rejouer à 2 000 utilisateurs. |
| 96 | ✅ | Multi-organisme, multi-unité, multi-site (sous-domaines, milieux). |
| 97, 98 | 📄 | SLA : réponse en 4 h ou moins (nos niveaux de soutien par gravité le permettent). |
| 99, 113 | 📄 | Calendrier d'évolution, préavis de 30 jours ouvrables (`CALENDRIER-EVOLUTION-ET-MISES-A-JOUR.md`). |
| 100 | 🟡 | Retour à la version précédente : images versionnées. Procédure de **retour arrière de schéma** (Liquibase) à écrire — ST-G13. |
| 101 | 📄 | **Écart contractuel** : astreinte 24/7 en français pour les incidents critiques (aujourd'hui : 5 h à 23 h). |
| 102-107 | 📄 | Plan d'implantation, chargé de projet, registres, formation en français (`TROUSSE-FORMATION.md`, `GUIDE-ECRANS.md`). |
| 108-111 | ✅ / 📄 | Module Soutien (billets, niveaux, délais, satisfaction) ; rapports périodiques. |
| 112, 114, 115 | 📄 | Maintenance incluse ; configuration personnalisée ; PCA et PRA. |
