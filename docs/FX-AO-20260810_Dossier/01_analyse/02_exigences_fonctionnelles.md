# Catalogue des exigences fonctionnelles

**Convention :** `EF-xxx` = exigence fonctionnelle reformulée. Les références de critères renvoient aux numéros de la grille de conformité du bordereau. Une reformulation n'ajoute pas de garantie technique absente des sources.

| ID | Exigence | Détail / règle d'acceptation | Source |
|---|---|---|---|
| EF-001 | Console Web centralisée | Accessible aux expéditeurs autorisés pour envoyer des alertes via application mobile et/ou SMS selon préférences | Grille 1 |
| EF-002 | Note au dossier | Permettre l'insertion d'une note au dossier du destinataire; définir l'intégration et le dossier cible | Grille 2 |
| EF-003 | Recherche destinataire | Recherche par nom et prénom | Grille 3 |
| EF-004 | Messagerie bidirectionnelle | Échanges texte entre équipes, médecins et utilisateurs autorisés | Grille 4 |
| EF-005 | Ciblage multi-destinataires | Envoi à une personne, plusieurs personnes ou groupe configurable | Grille 5 |
| EF-006 | Messages prédéfinis expéditeur | Sélection de modèles de messages par les expéditeurs | Grille 6 |
| EF-007 | Alertes temps réel | Diffusion aux destinataires visés | Grille 7 |
| EF-008 | Copie courriel | Copie par courriel si configurée par l'organisme | Grille 8 |
| EF-009 | Confirmation | Confirmation de réception, lecture ou prise en charge par mécanisme simple et automatisé | Grille 9 |
| EF-010 | Réponses prédéfinies | Réponses destinataires configurables par administrateurs | Grille 10 |
| EF-011 | Suivi des statuts | Visualiser envoi, réception, lecture, réponse et relance | Grille 11 |
| EF-012 | Historique partagé | Partager historique et statuts entre expéditeurs autorisés | Grille 12 |
| EF-013 | Confirmation retour d'appel | Confirmer réception d'un retour d'appel ou réponse équivalente | Grille 13 |
| EF-014 | Appel téléphonique automatique | Déclencher un appel si absence de réponse, selon délai programmable | Grille 14 |
| EF-015 | Relances automatiques | Configurer relances en cas de non-réponse ou non-confirmation | Grille 15 |
| EF-016 | Cascades | Créer des cascades entre membres d'un groupe ou groupes distincts | Grille 16 |
| EF-017 | Paramétrage cascade | Configurer délais, séquences, arrêts et reprises | Grille 17 |
| EF-018 | Alertes critiques | Utiliser les mécanismes disponibles et permis, ou équivalents | Grille 18 |
| EF-019 | Historique escalade | Tracer relances, appels générés, réponses et escalades | Grille 19 |
| EF-020 | Accès mobile navigateur | Accès depuis appareil mobile par navigateur sans équipement dédié fourni par l'organisme | Grille 20 |
| EF-021 | Application mobile | Si proposée, compatibilité iOS et Android | Grille 21 |
| EF-022 | Préférences destinataire | Personnaliser les préférences sans contourner les règles obligatoires administratives | Grille 22 |
| EF-023 | Interface clinique | Interface intuitive et rapide en contexte clinique | Grille 23 |
| EF-024 | Historique personnel | Consulter messages et confirmations selon droits | Grille 24 |
| EF-025 | Calendrier disponibilité | Utiliser/consulter un calendrier interne lorsque module activé | Grille 25 |
| EF-026 | Console administration | Console centralisée pour administrateurs autorisés | Grille 26 |
| EF-027 | Gestion utilisateurs/groupes | Créer, modifier, désactiver et organiser par unité, service, spécialité, établissement ou regroupement | Grille 27 |
| EF-028 | Cloisonnement | Isoler utilisateurs dans un ou plusieurs groupes selon opérations et droits | Grille 28 |
| EF-029 | Confidentialité coordonnées | Masquer/protéger les numéros ou coordonnées des destinataires | Grille 29 |
| EF-030 | Profils d'accès | Au minimum utilisateur et administrateur; granularité supplémentaire possible | Grille 30 |
| EF-031 | Vue messages/statuts | Page de consultation des messages envoyés et statuts de réponse | Grille 31 |
| EF-032 | Configuration fonctionnelle | Administrer modèles, réponses, relances, groupes, cascades, droits et disponibilités | Grille 32 |
| EF-033 | Gestion des identités | Gérer rôles/permissions, suspension, révocation et désactivation rapide | Grille 77-83 |
| EF-034 | Journalisation métier | Tracer expéditeur, destinataire, date/heure, contenu ou métadonnées, confirmations, relances, réponses et statuts | Grille 89 |
| EF-035 | Consultation et export audit | Journaux protégés, accessibles selon droits, exportables dans format exploitable | Grille 90-93 |
| EF-036 | Rapports opérationnels | Rapports périodiques disponibilité, incidents, délais, changements et recommandations | Grille 111 |
| EF-037 | Import initial | Importer utilisateurs, groupes, horaires et paramètres lorsque applicable | Devis §1.2 |
| EF-038 | Gestion des sites | Déploiement progressif site par site selon ordre convenu | Devis §5.1 |
| EF-039 | Support demandes/incidents | Enregistrer et suivre incidents, demandes, anomalies, correctifs, relances et résolutions | Grille 110 |
| EF-040 | Facturation utilisateurs actifs | Produire données vérifiables du nombre d'utilisateurs ciblés par au moins une tentative de transmission durant le mois | Bordereau p.1 |

## Critères transverses
- Toute configuration métier doit être persistée et administrable, pas codée en dur.
- Toute transition d'état d'une communication doit être horodatée et attribuée à un acteur ou système.
- Les règles obligatoires administratives prévalent sur les préférences personnelles.
- Les opérations sensibles sont journalisées.
- Les permissions s'appliquent côté serveur, pas seulement dans l'interface.

## Points d'interprétation à valider
- Définition de « réception » et disponibilité de l'accusé opérateur SMS.
- Sens exact de « note au dossier » et dossier cible.
- Comportement en cas de destinataire absent, doublon de membre ou canal indisponible.
- Sémantique des priorités/alertes critiques et obligations cliniques associées.
