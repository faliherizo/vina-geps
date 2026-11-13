# Backlog des stories

## ST-001 — Authentification et autorisation
**User story :** En tant qu'utilisateur autorisé, je veux accéder à la solution avec une identité contrôlée afin de protéger les communications.

- **Priorité :** P1
- **Traçabilité :** EF-033; GC 77-88
- **Analyse / conception :** Configurer le fournisseur d'identité; gérer session; appliquer RBAC et portée organisationnelle; refuser par défaut.
- **Critères d'acceptation :** Tests connexion, expiration, révocation, accès interdit et audit. Dépendance: choix GIA.

## ST-002 — Référentiels organisationnels dynamiques
**User story :** En tant qu'administrateur, je veux gérer établissements, sites, unités, services et spécialités afin de refléter l'organisation réelle.

- **Priorité :** P1
- **Traçabilité :** EF-027; GC 27-28
- **Analyse / conception :** CRUD hiérarchique; types configurables; dates d'effet; validation cycles et suppressions; API générique.
- **Critères d'acceptation :** Créer un nouveau type et nouveau site sans changement de code; historiser modifications.

## ST-003 — Gestion des utilisateurs et coordonnées
**User story :** En tant qu'administrateur, je veux gérer les utilisateurs et leurs canaux afin de les rendre joignables.

- **Priorité :** P1
- **Traçabilité :** EF-027; EF-029; GC 27,29
- **Analyse / conception :** Créer/importer/suspendre; contacts chiffrés; masquage; affectations multi-sites; audit.
- **Critères d'acceptation :** Un utilisateur désactivé ne reçoit plus de nouvelle communication; coordonnées masquées selon rôle.

## ST-004 — Groupes dynamiques et adhésions
**User story :** En tant que responsable, je veux configurer des groupes afin de cibler les équipes opérationnelles.

- **Priorité :** P1
- **Traçabilité :** EF-005; EF-027; GC 5,16,27-28
- **Analyse / conception :** Groupes configurables; membres explicites ou règles; validité; résolution et snapshot.
- **Critères d'acceptation :** Modifier un groupe affecte les futurs envois seulement; l'historique conserve le snapshot.

## ST-005 — Recherche de destinataires
**User story :** En tant qu'expéditeur, je veux rechercher par nom/prénom afin de sélectionner rapidement une personne.

- **Priorité :** P2
- **Traçabilité :** EF-003; GC 3
- **Analyse / conception :** Recherche indexée, pagination, filtres d'organisation et contrôle d'accès.
- **Critères d'acceptation :** Résultats pertinents; aucune fuite d'utilisateurs hors périmètre.

## ST-006 — Composer et envoyer un message
**User story :** En tant qu'expéditeur, je veux envoyer une communication à un utilisateur ou groupe afin de transmettre une information clinique.

- **Priorité :** P2
- **Traçabilité :** EF-001, EF-005, EF-007; GC 1,5,7
- **Analyse / conception :** Composer, valider, choisir cibles/canal autorisé; créer communication et snapshot; idempotence.
- **Critères d'acceptation :** Une demande valide crée une communication traçable; double clic ne crée pas de doublon.

## ST-007 — Conversations et messagerie bidirectionnelle
**User story :** En tant qu'utilisateur, je veux répondre à une communication afin de coordonner les équipes.

- **Priorité :** P2
- **Traçabilité :** EF-004; GC 4
- **Analyse / conception :** Fil conversationnel, réponse, attribution, statut et visibilité selon droits.
- **Critères d'acceptation :** Réponse reliée au message/conversation et historisée.

## ST-008 — Modèles et réponses prédéfinies
**User story :** En tant qu'administrateur, je veux gérer modèles et réponses afin d'accélérer les échanges standardisés.

- **Priorité :** P2
- **Traçabilité :** EF-006, EF-010, EF-032; GC 6,10,32
- **Analyse / conception :** CRUD, versions, catégories dynamiques, activation, permissions et variables validées.
- **Critères d'acceptation :** Modification versionnée; messages déjà envoyés inchangés.

## ST-009 — Statuts et confirmations
**User story :** En tant qu'expéditeur, je veux voir les états de livraison, lecture, réponse et prise en charge afin de savoir si l'action est reçue.

- **Priorité :** P2
- **Traçabilité :** EF-009, EF-011, EF-013; GC 9,11,13
- **Analyse / conception :** Modèle d'événements; horodatage; distinction reçu opérateur/reçu appareil/lu/confirmé.
- **Critères d'acceptation :** Chaque statut indique sa sémantique et sa source; pas de fausse confirmation.

## ST-010 — Adaptateur SMS
**User story :** En tant qu'utilisateur, je veux recevoir et répondre par SMS lorsque ce canal est configuré.

- **Priorité :** P3
- **Traçabilité :** EF-001, EF-004, EF-007; GC 1,4,7
- **Analyse / conception :** Connecteur provider; webhooks sécurisés; retry; idempotence; suivi des coûts et erreurs.
- **Critères d'acceptation :** Tests bout-en-bout avec fournisseur retenu; accusés documentés.

## ST-011 — Notifications push / mobile
**User story :** En tant que destinataire, je veux recevoir les alertes sur mobile afin de rester joignable.

- **Priorité :** P3
- **Traçabilité :** EF-001, EF-020, EF-021; GC 1,20-21
- **Analyse / conception :** UI mobile responsive; application si retenue; token device; préférences; révocation.
- **Critères d'acceptation :** Accès navigateur mobile; iOS/Android si app livrée.

## ST-012 — Copie courriel Outlook 365
**User story :** En tant qu'expéditeur, je veux qu'une copie courriel soit envoyée lorsque configurée.

- **Priorité :** P3
- **Traçabilité :** EF-008; GC 8,40
- **Analyse / conception :** Adaptateur courriel; modèles; permissions Microsoft; suivi d'échec.
- **Critères d'acceptation :** Envoi seulement si option activée; erreur visible et journalisée.

## ST-013 — Relances automatiques
**User story :** En tant qu'administrateur, je veux configurer les relances afin d'obtenir une réponse dans un délai donné.

- **Priorité :** P3
- **Traçabilité :** EF-014, EF-015; GC 14-15
- **Analyse / conception :** Politiques versionnées; délais; nombre; condition arrêt; scheduler durable.
- **Critères d'acceptation :** Relance exécutée à échéance; arrêt immédiat si condition atteinte.

## ST-014 — Cascades et escalades multi-groupes
**User story :** En tant qu'administrateur, je veux définir des cascades afin de transférer l'alerte en cas de non-réponse.

- **Priorité :** P3
- **Traçabilité :** EF-016 à EF-019; GC 16-19
- **Analyse / conception :** Étapes dynamiques; groupes et canaux; règles reprise/arrêt; audit complet.
- **Critères d'acceptation :** Chaque étape est traçable; changement de politique n'altère pas les instances actives.

## ST-015 — Disponibilités et calendrier
**User story :** En tant qu'utilisateur, je veux gérer mes disponibilités afin que le routage prenne en compte mes périodes d'absence.

- **Priorité :** P4
- **Traçabilité :** EF-025; GC 25
- **Analyse / conception :** Calendriers, fuseaux, exceptions, source importée et activation par module.
- **Critères d'acceptation :** Résolution prend en compte disponibilités uniquement si module activé.

## ST-016 — Préférences et règles obligatoires
**User story :** En tant que destinataire, je veux configurer mes préférences sans neutraliser les règles institutionnelles.

- **Priorité :** P4
- **Traçabilité :** EF-022; GC 22
- **Analyse / conception :** Moteur de politique priorisé; règles obligatoires; explication du canal choisi.
- **Critères d'acceptation :** Préférences respectées uniquement si compatibles avec règle obligatoire.

## ST-017 — Console d'administration et RBAC
**User story :** En tant qu'administrateur, je veux configurer paramètres, rôles et permissions afin d'administrer la solution sans développement.

- **Priorité :** P4
- **Traçabilité :** EF-026, EF-030, EF-032; GC 26-32,77-80
- **Analyse / conception :** CRUD des rôles/permissions; scope; profils utilisateur/admin; audit de changements.
- **Critères d'acceptation :** Nouvelle permission/règle configurable; privilège minimal.

## ST-018 — Audit, export et historique
**User story :** En tant qu'auditeur, je veux reconstituer les communications et exporter les journaux afin de démontrer la traçabilité.

- **Priorité :** P4
- **Traçabilité :** EF-012, EF-034, EF-035; GC 12,89-93
- **Analyse / conception :** Audit append-only; filtres; export; rétention; protection intégrité.
- **Critères d'acceptation :** Export exploitable; accès restreint; journal d'accès aux journaux.

## ST-019 — Rapports opérationnels
**User story :** En tant que gestionnaire, je veux consulter disponibilité, incidents, délais et volumes afin de piloter le service.

- **Priorité :** P4
- **Traçabilité :** EF-036; GC 97,111
- **Analyse / conception :** Agrégats; filtres site/groupe/période; export; définitions KPI documentées.
- **Critères d'acceptation :** Les chiffres sont reproductibles et alignés aux événements source.

## ST-020 — Facturation des utilisateurs actifs
**User story :** En tant que responsable contractuel, je veux vérifier les utilisateurs actifs facturés afin de valider la facture mensuelle.

- **Priorité :** P4
- **Traçabilité :** EF-040; BP p.1
- **Analyse / conception :** Dédupliquer par utilisateur et mois; toute tentative compte, directe ou indirecte; détail exportable.
- **Critères d'acceptation :** Rapport prouve qui a été ciblé et quand; minimum contractuel 100 traité distinctement.

## ST-021 — Import et synchronisation initiale
**User story :** En tant qu'administrateur, je veux importer utilisateurs, groupes et horaires afin de démarrer sans ressaisie exhaustive.

- **Priorité :** P1
- **Traçabilité :** Devis §1.2
- **Analyse / conception :** Mapping configurable; prévisualisation; validation; rapport d'erreurs; reprise idempotente.
- **Critères d'acceptation :** Import répétable sans doublons; erreurs et transformations traçables.

## ST-022 — Déploiement multi-sites
**User story :** En tant que pilote de site, je veux activer progressivement la solution afin de limiter les risques opérationnels.

- **Priorité :** P5
- **Traçabilité :** EF-038; Devis §5.1
- **Analyse / conception :** Paramétrage par site; checklist; feature toggles configurables; critères go/no-go.
- **Critères d'acceptation :** Activation par site; retour arrière; acceptation locale documentée.

## ST-023 — Supervision et gestion incidents
**User story :** En tant qu'équipe opérationnelle, je veux superviser la solution afin de détecter les défaillances rapidement.

- **Priorité :** P5
- **Traçabilité :** FOUR-016, FOUR-027; GC 69-70,108-111
- **Analyse / conception :** Métriques, logs, alertes, intégration Zabbix/EDR selon périmètre; registre incident.
- **Critères d'acceptation :** Alertes testées; suivi incident et rapport disponibles.

## ST-024 — Sauvegarde, restauration et archivage
**User story :** En tant qu'exploitant, je veux sauvegarder, restaurer et archiver les données afin d'assurer continuité et conformité.

- **Priorité :** P5
- **Traçabilité :** FOUR-015; GC 64-68
- **Analyse / conception :** Procédures tous environnements; agent institutionnel; tests de restauration; destruction contrôlée.
- **Critères d'acceptation :** Restauration démontrée; preuve de test; archivage/destruction auditables.

## ST-025 — Sécurité, performance et conformité
**User story :** En tant que responsable sécurité, je veux vérifier les contrôles afin d'autoriser la mise en production.

- **Priorité :** P5
- **Traçabilité :** GC 33-100
- **Analyse / conception :** Tests réseau, compatibilité, chiffrement, vulnérabilités, charge, audit, rollback et SLA.
- **Critères d'acceptation :** Rapports de tests et écarts fermés ou acceptés formellement.

## ST-026 — Formation, documentation et hypercare
**User story :** En tant que pilote, je veux former les utilisateurs afin d'assurer l'adoption et l'autonomie.

- **Priorité :** P5
- **Traçabilité :** FOUR-026; Devis §5.3; GC 102,106-107
- **Analyse / conception :** Plan par profil; guides, aide-mémoire, capsules; sessions; support de proximité.
- **Critères d'acceptation :** Pilotes formés; présence et évaluation; matériel en français accessible.
