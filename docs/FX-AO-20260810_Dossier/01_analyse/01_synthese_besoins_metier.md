# Analyse des besoins métier

## 1. Contexte et problème
Santé Québec Laurentides souhaite acquérir, implanter et exploiter une solution pour remplacer le système de téléavertisseurs en place. La solution doit rendre les communications cliniques rapides, fiables, bidirectionnelles et traçables entre équipes, médecins et utilisateurs autorisés.

## 2. Objectifs métier
1. Réduire la dépendance aux téléavertisseurs.
2. Accélérer la diffusion d'alertes et la coordination des équipes.
3. Obtenir une confirmation explicite de réception, lecture ou prise en charge.
4. Automatiser les relances et escalades lorsque les destinataires ne répondent pas.
5. Administrer les utilisateurs, groupes, disponibilités et règles de communication de façon centralisée.
6. Préserver confidentialité, intégrité, disponibilité et auditabilité des communications.
7. Déployer progressivement sur les six sites ciblés, avec formation et stabilisation.

## 3. Parties prenantes et rôles métier proposés
| Acteur | Responsabilités principales |
|---|---|
| Expéditeur clinique | Composer, cibler et envoyer une communication; suivre les statuts |
| Destinataire | Recevoir, lire, répondre, confirmer ou prendre en charge |
| Responsable de groupe | Maintenir la composition opérationnelle des groupes et leur disponibilité |
| Administrateur fonctionnel | Gérer utilisateurs, rôles, messages prédéfinis, règles, cascades et paramètres |
| Administrateur technique | Superviser intégrations, environnements, journaux et configuration technique |
| Pilote / superutilisateur | Accompagner le déploiement et la formation locale |
| Gestionnaire / auditeur | Consulter rapports, historiques et preuves de conformité |
| Équipe de soutien | Traiter incidents, demandes et anomalies |

Ces rôles sont une proposition de modélisation; les responsabilités officielles et délégations doivent être confirmées.

## 4. Capacités métier et règles
| Capacité | Résultat métier attendu | Règles / contrôles |
|---|---|---|
| Composer et envoyer | Communication ciblée depuis une console Web | Seuls les expéditeurs autorisés; validation des destinataires |
| Diffuser à plusieurs | Envoi individuel, multiple ou par groupe | Groupes configurables et cloisonnés |
| Répondre | Échange bidirectionnel | Réponses prédéfinies possibles; historisation |
| Confirmer | Savoir si reçu, lu ou pris en charge | États distincts; horodatage et identité |
| Relancer | Obtenir une réponse dans le délai attendu | Délais, nombre, canal et condition d'arrêt configurables |
| Escalader | Transférer à un autre membre/groupe | Cascade séquencée, règles de reprise et arrêt |
| Joindre une note | Conserver une note au dossier du destinataire | Portée, confidentialité et intégration au dossier à clarifier |
| Gérer disponibilité | Éviter de cibler un destinataire indisponible | Calendrier interne facultatif; règles de remplacement |
| Administrer | Maintenir les paramètres sans changement logiciel | Configuration pilotée par données, auditée et versionnée |
| Auditer | Reconstituer une communication | Journaux protégés et exportables |

## 5. Parcours métier cible
1. L'expéditeur s'authentifie et ouvre la console.
2. Il recherche un destinataire ou sélectionne un groupe dynamique.
3. Il compose un message libre ou utilise un modèle autorisé.
4. Il choisit le niveau de priorité et le mécanisme de confirmation attendu.
5. Le moteur résout les membres du groupe, leurs canaux et disponibilités selon les règles actives.
6. Le service de distribution transmet les notifications.
7. Les accusés, lectures, réponses et prises en charge sont enregistrés.
8. En absence de réponse dans le délai, le moteur exécute les relances et escalades configurées.
9. L'expéditeur consulte les statuts et l'historique; les activités sont auditables.

## 6. Indicateurs métier proposés
- Délai médian entre création et première tentative de livraison.
- Taux de livraison et de confirmation dans le délai configuré.
- Taux de prise en charge.
- Nombre de relances et escalades par période, site et groupe.
- Disponibilité du service et incidents par sévérité.
- Nombre d'utilisateurs actifs facturables, selon la définition du bordereau.
- Taux d'adoption par site et profil.

Les cibles chiffrées autres que celles explicitement définies dans les pièces doivent être déterminées avec le client.

## 7. Périmètre
**Inclus par les documents :** licences, analyse, configuration, utilisateurs/groupes/rôles, environnements, intégrations nécessaires, import initial applicable, formation, accompagnement, stabilisation, maintenance, soutien et rapports.

**Sites explicitement cités :** CMSSS d'Argenteuil; Hôpital de Saint-Eustache; Hôpital de Saint-Jérôme; CMSSS de Ste-Agathe; CMSSS de Rivière-Rouge; Hôpital de Mont-Laurier.

**Non spécifié / à confirmer :** intégration au dossier clinique, contenu clinique autorisé, pièces jointes, accusé SMS opérateur, haute disponibilité cible, RTO/RPO, rétention des messages, volumétrie de pointe et exigences d'accessibilité détaillées.
