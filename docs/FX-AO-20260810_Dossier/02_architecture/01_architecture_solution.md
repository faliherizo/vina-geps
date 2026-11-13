# Architecture de solution proposée

## 1. Principes
- Architecture logique modulaire, évolutive et multi-sites.
- Configuration métier pilotée par les données : aucune liste de groupes, unités, sites, rôles, canaux, cascades, priorités ou modèles de messages codée en dur.
- API sécurisées; contrôles d'accès côté serveur.
- Séparation des domaines : identité, organisation, communication, distribution, orchestration des escalades, disponibilité, audit et intégrations.
- Résilience et idempotence pour les tentatives de distribution.
- Traçabilité de bout en bout et horodatage synchronisé.
- Adaptation au modèle d'hébergement choisi par l'organisme.

## 2. Architecture logique
| Composant | Responsabilité |
|---|---|
| Portail Web responsive | Console expéditeur, destinataire, administration et suivi |
| API Gateway / BFF | Entrée sécurisée, validation, limitation, routage et adaptation UI |
| Identity Adapter | SSO/GIA, LDAP(S), rôles et synchronisation d'identité |
| Organization & Directory | Sites, établissements, unités, services, spécialités, utilisateurs et groupes |
| Communication Service | Messages, conversations, destinataires, pièces/métadonnées et états |
| Policy & Template Service | Modèles, réponses, préférences, règles de priorité et politique obligatoire |
| Recipient Resolver | Résolution dynamique des utilisateurs/groupes/gardes/disponibilités |
| Orchestration Service | Relances, cascades, timers, conditions d'arrêt et reprise |
| Channel Adapters | SMS, push mobile, courriel, téléphonie et canaux futurs |
| Audit & Reporting | Événements immuables, recherche, export et rapports |
| Integration Adapter | Outlook 365, téléphonie, annuaire et systèmes autorisés |
| Relational DB | Configuration, référentiels dynamiques, messages et états transactionnels |
| Event transport | Distribution asynchrone, retry, DLQ et événements |
| Observability | Logs, métriques, traces, alertes et tableaux de bord |

## 3. Flux nominal
1. UI envoie une demande à l'API.
2. API authentifie l'acteur et vérifie ses permissions.
3. Communication Service crée message et demande de diffusion.
4. Recipient Resolver calcule les destinataires à partir des groupes et règles actifs.
5. Policy Service applique règles obligatoires et préférences compatibles.
6. Orchestration Service crée les étapes de distribution et délais.
7. Channel Adapters envoient les notifications et rapportent les résultats.
8. Les événements mettent à jour les statuts et l'audit.
9. Les règles d'escalade déclenchent les étapes suivantes lorsque requises.

## 4. Architecture de déploiement
Deux profils sont possibles, à arbitrer :
- **Profil A — SaaS / nuage privé fournisseur :** le fournisseur opère la plateforme; données hébergées au Canada; responsabilités, sous-traitants, sauvegardes et accès support contractuellement définis.
- **Profil B — infrastructure établissement :** l'établissement acquiert et opère serveurs/stockage/OS; le fournisseur fournit sizing, installation applicative, maintenance logicielle et assistance selon RACI.

La conception applicative doit rester portable. La sélection de Kubernetes, VM, base de données, cloud ou middleware est une décision technique à valider après choix d'hébergement et normes d'entreprise.

## 5. Stack de référence (proposition, non exigence source)
- Backend : Java 21 LTS + Spring Boot, modules métier séparés, API REST.
- Frontend : React + TypeScript, design system accessible.
- Persistance : PostgreSQL ou SGBD approuvé.
- Événements : RabbitMQ ou service de messagerie approuvé.
- Authentification : OIDC/SAML via fournisseur d'identité institutionnel; LDAPS pour intégration annuaire lorsque requis.
- Observabilité : OpenTelemetry + solution institutionnelle.
- Déploiement : conteneurs ou VM selon modèle approuvé.
- Adaptateurs : fournisseurs SMS, voix, courriel et push sélectionnés après validation.

Ces choix ne sont pas prescrits par l'appel d'offres. Ils constituent une option de conception, à confirmer avec les équipes d'architecture et d'exploitation.
