# FX-AO-20260810 : écarts fonctionnels de VINA GEPS

> Écrit pour l'équipe produit. Ce document liste ce qui manque à VINA GEPS pour satisfaire la grille
> de Santé Québec Laurentides, l'estime et le rattache à une story. Le détail par critère est dans
> [02_conformite_115_criteres.md](02_conformite_115_criteres.md).

## 1. Ce qui existe déjà et sera réutilisé

| Capacité | Où dans VINA GEPS | Critères servis |
|---|---|---|
| Messagerie bidirectionnelle à deux et en groupe, réponse privée, réactions | `communication` (ADR 0072) | 4, 5, 24 |
| Accusés « reçu » et « lu » automatiques, acheminement vu par l'auteur | `Messagerie.Acheminement` | 9 et 11 (en partie) |
| Historique des versions, suppression tracée | `msg_message_versions` | 12 (en partie), 89 |
| Listes de diffusion (personnes et groupes recalculés) | `ListesDistribution` | 5 |
| Avis par événement : courriel, texto (segments, quota), poussée web et native, préférences | `notifications` (ADR 0029) | 1, 7, 8, 22 |
| Appel sortant au numéro de l'organisme, numéro personnel masqué | `notifications.Appels` (ADR 0022) | 14 (base), 29 |
| Appel à deux et de groupe en session (sonne, en cours, manqué) | `AppelsDirects`, `AppelsGroupe` | 13 (base) |
| Groupes = unités datées ; périmètres en refus par défaut | `effectifs` | 27, 28 |
| Disponibilités, horaires, calendrier | `effectifs` | 25 |
| Identité Entra ID / SAML, plusieurs annuaires ; rôles et capacités | `auth`, `acces` | 30, 77-81 |
| Journal des actions, rétention, droit à l'oubli, exports | `partage.journal`, `effectifs.retention` | 67, 89-93 |
| Modules activables par organisme | `saas` / superadmin | administration par organisme |
| Soutien : billets, niveaux, délais, satisfaction | `soutien` | 108-110 |

## 2. Écarts à combler

| ID | Écart | Critères | Taille | Story |
|---|---|---|---|---|
| ECF-01 | **Console d'alerte** : rédiger une alerte avec priorité (information, urgente, critique), ciblage personne, groupe ou liste, canaux selon les préférences et les règles obligatoires, copie courriel par organisme | 1, 5, 7, 8 | M | ST-G01 |
| ECF-02 | **Modèles de message** (expéditeur) et **réponses prédéfinies** (destinataire), administrés par organisme, versionnés | 6, 10, 32 | S | ST-G02 |
| ECF-03 | **Suivi des communications** : par alerte et par destinataire, les états envoyé, remis, lu, répondu, pris en charge, relancé, escaladé ; partagé entre expéditeurs autorisés ; snapshot des destinataires à l'envoi | 9, 11, 12, 19, 31, 89 | M | ST-G03 |
| ECF-04 | **Relances et cascades** : politique versionnée (délais, séquence, groupes, arrêt sur prise en charge, reprise), orchestration durable sur l'ordonnanceur, disponibilité consultée | 15, 16, 17, 19, 25, 32 | L | ST-G04 |
| ECF-05 | **Réponse par texto** : un destinataire répond par un mot-clé (« 1 », « OK ») ou par le texte d'une réponse prédéfinie ; le texto entrant est rattaché à l'alerte | 4, 9, 10 | M | ST-G05 |
| ECF-06 | **Appel automatique sur non-réponse** et confirmation de retour d'appel (DTMF « appuyez sur 1 ») | 13, 14 | M | ST-G06 |
| ECF-07 | **Alerte critique** sur mobile : canal Android prioritaire, iOS *Critical Alerts* (autorisation d'Apple), son et vibration distincts ; repli par texto et appel | 18, 22 | M | ST-G07 |
| ECF-08 | **Compteur d'utilisateurs actifs** facturables : dédupliqués par mois, toute tentative comptée (y compris par groupe ou cascade), export justificatif | Bordereau | S | ST-G08 |
| ECF-09 | **Groupes transverses** : spécialité, garde, comité, hors de l'organigramme des unités, avec appartenance datée | 27 | S | ST-G09 |
| ECF-10 | **Sécurité** : historique des 10 derniers mots de passe pendant 6 mois ; export PDF/A-2b | 39, 86 | S | ST-G10 |
| ECF-11 | **Intégrations Microsoft** : envoi par Graph (Outlook 365) ; LDAPS si un annuaire LDAP est imposé | 40, 82 | S | ST-G11 |
| ECF-12 | **Note sur la fiche du destinataire** (si Q-DOS conclut à une note interne) | 2 | XS | ST-G12 |
| ECF-13 | **Exploitation sur infrastructure de l'établissement** : guide Windows Server et VMware, archivage, chiffrement des sauvegardes, retour arrière de schéma | 51, 61, 66, 74, 100 | M | ST-G13 |

Tailles : XS est inférieur à 3 jours, S de 3 à 5 jours, M de 2 à 3 semaines, L de 4 à 6 semaines,
pour une personne. Le total est d'environ **17 à 22 semaines-personne**, contre 22 semaines de
phases P1 à P5 dans la feuille de route d'origine, qui supposait tout à construire.

## 3. Écarts non logiciels

| Écart | Nature | Réponse proposée |
|---|---|---|
| Critère 94 : cinq ans en milieu hospitalier | Admissibilité | Démonstration équivalente : Urgences-santé (26-015-DO), module Santé Québec ; références client |
| Critère 101 : soutien 24/7 en français pour les incidents critiques | Contractuel | Astreinte téléphonique 24/7 sur les seuls incidents critiques, à coûter |
| Critère 84 : pas de certificat wildcard | Exploitation | Certificats nominatifs (Let's Encrypt par hôte, ou PKI de l'établissement) |
| Fournisseur voix et texto canadien | Approvisionnement | Twilio (région canadienne) ou un fournisseur québécois ; coûts à l'unité dans le bordereau |
