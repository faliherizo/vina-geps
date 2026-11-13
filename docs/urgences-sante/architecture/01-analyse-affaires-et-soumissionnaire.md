# Référentiel et limites de l'analyse

- **Source principale :** Appel d'offres 26-015-DO, *Système de gestion des effectifs (Solution infonuagique)*, Urgences-santé.
- **Fichier analysé :** `26-015-DO_AO (1).pdf`, 199 pages.
- **Parties repérées :** Régie, Contrat, Cahier de charges et devis technique, Formulaire de soumission, Questionnaire d'évaluation de la qualité et annexe/grille des critères de conformité.
- **Traçabilité :** les exigences E-* et P-* sont reprises des pages 194 à 198 du PDF (annexe 7.00 / critères de conformité). Le contexte d'affaires est repris principalement des pages PDF 145 à 151.
- **Précaution :** les annexes Excel externes mentionnées dans le PDF (grille détaillée des critères, bordereau de prix, questionnaire d'évaluation distinct) ne sont pas présentes comme fichiers séparés dans la pièce jointe. L'analyse ne prétend donc pas couvrir des éléments qui n'apparaissent pas dans le PDF.
- **Nature du document :** analyse de solution proposée, pas un avis juridique ni une preuve de conformité d'un produit fournisseur. Les choix d'architecture ci-dessous sont des recommandations à valider avec Urgences-santé et l'éditeur.

# 1. Synthèse exécutive

L'appel d'offres vise l'acquisition, l'implantation, l'intégration et l'exploitation en mode SaaS d'un système de gestion des effectifs. La portée fonctionnelle couvre la confection annuelle des horaires (AGPH), la gestion continue des horaires, les disponibilités, les absences, les remplacements, les déclarations de temps (« punch »), les feuilles de temps, les validations, les rapports et les échanges avec l'écosystème applicatif existant.

## 1.1 Contexte et objectifs métier

Le document décrit un environnement actuel fragmenté, peu automatisé et difficile à intégrer. Les règles de planification sont complexes et dépendent notamment des conventions collectives, ententes locales, ancienneté, compétences, statuts d'emploi et besoins opérationnels.

Les résultats attendus sont :
- une expérience employé moderne, conviviale et multi-appareils;
- l'automatisation maximale de la planification et de l'affectation;
- la réduction des validations manuelles par les gestionnaires;
- un meilleur contrôle des absences, présences et coûts de main-d'œuvre;
- des données RH/temps plus fiables pour la paie et l'analyse;
- la simplification de l'architecture TI et le décommissionnement des systèmes actuels ciblés.

## 1.2 Parties prenantes et volumétrie

| Partie prenante | Rôle décrit dans le cahier | Volumétrie explicite |
|---|---|---:|
| Direction Gestion des effectifs | Responsable affaires du projet, planification/suivi/réalisation des lots | 100 |
| Centre de communication santé | Client utilisateur du système des horaires | Non précisée |
| Direction des interventions | Client utilisateur du système des horaires | Non précisée |
| Direction de la logistique | Client utilisateur du système des horaires | Non précisée |
| Bureau de transformation | Encadrement en gestion de projet | Non précisée |
| Direction des ressources humaines | Directives RH, avantages sociaux, acquisition, relations de travail | Non précisée |
| Centre de formation | Planification des formations | Non précisée |
| Service de planification des horaires | Définition des besoins d'affaires | Non précisée |
| Service de la paie | Directives organisationnelles de paie | Non précisée |
| Direction des ressources informationnelles | Appui technique, analystes et techniciens | 10 mentionnés |
| Paramédics | Principaux bénéficiaires de la solution | Environ 1 000 |
| Personnel administratif | Accès via postes de travail et télétravail | Environ 100 |

## 1.3 Écosystème cible à interfacer

Le cahier cite explicitement :
- **RAO** — Répartition assistée par ordinateur;
- **SAAV** — Système d'attitrage automatique de véhicules;
- **PFT** — Production des feuilles de temps, pour téléverser les données des horaires réelles;
- **BD Corpo** — dossier employés/RH, notamment banques de congés et d'absences;
- **Microsoft Entra ID** pour l'authentification;
- **Microsoft Graph** comme passerelle bidirectionnelle mentionnée dans l'architecture cible;
- **ESB** et interfaces **OpenAPI 3 ou supérieure** dans les critères technologiques.

Le détail des contrats d'interface, objets échangés, systèmes maîtres, fréquences et règles de réconciliation n'est pas défini dans le PDF : ces éléments doivent faire l'objet d'ateliers de conception.

## 1.4 Principaux domaines fonctionnels

1. Référentiels organisationnels, employés, statuts, postes, compétences et lieux.
2. Règles d'affaires et conformité des disponibilités.
3. Gestion des absences et droits associés.
4. Confection annuelle des horaires et attribution des postes (AGPH).
5. Gestion régulière des horaires, périodes, quarts ouverts, quotas et échanges.
6. Remplacements long terme et court terme.
7. Déclaration des présences et heures (« punch »), y compris saisie rétroactive.
8. Feuilles de temps, anomalies, approbations et préparation des données de paie.
9. Notifications, formulaires électroniques et rapports.
10. Administration, audit, sécurité, intégrations et exploitation SaaS.

# 2. Exigences du soumissionnaire et du fournisseur

Cette section distingue les obligations de **soumission** (avant adjudication) des obligations de **livraison/exploitation** du Fournisseur (après adjudication). Les références de clauses ci-dessous renvoient à la Régie, au Contrat et au Questionnaire d'évaluation inclus dans le PDF.

## 2.1 Admissibilité et conformité de la soumission

| Domaine | Exigence / preuve attendue | Référence source | Risque si non respecté |
|---|---|---|---|
| Établissement | Établissement permanent au Québec ou dans un territoire visé par un accord intergouvernemental, identifié au nom du soumissionnaire et accessible en heures normales | Régie 1.03 | Inadmissibilité |
| Langue | Soumission rédigée en français | Régie 1.07.01 | Non-conformité |
| Formulaire | Formulaire officiel complet, avec annexes et documents demandés | Régie 1.07.04, 1.07.08 | Non-conformité |
| Double enveloppe | Offre qualitative séparée de l'offre financière; aucun prix dans l'offre qualitative | Régie 1.07.05 | Rejet automatique si prix dans l'enveloppe A |
| Signature | Formulaire et annexes signés par une personne autorisée | Régie 1.07.06-1.07.07 | Non-conformité |
| Dépôt | Dépôt au plus tard le 3 novembre 2026 à 14:00:59, selon les modalités applicables papier/SEAO | Régie, sommaire et 1.07.13-1.07.15 | Rejet si tardif ou mauvais lieu |
| Validité | Soumission valide 180 jours après l'échéance, avec prolongation possible selon les limites prévues | Régie 1.07.16 | Engagement contractuel |
| Critères essentiels | Répondre OUI/NON à chaque ligne, justifier et indiquer les références documentaires; un NON sur un essentiel entraîne le rejet | Régie 1.07.20; Annexe 7.00 P | Rejet automatique |
| Revendeur | Si non manufacturier : preuve de revendeur autorisé et validation de la capacité du manufacturier à honorer les licences/obligations | Régie 1.07.21 | Non-conformité / risque de capacité |
| Autorisation AMP | Autorisation de contracter lorsque le seuil et les conditions de prépondérance des services professionnels s'appliquent | Régie 1.07.22 | Inadmissibilité |
| Revenu Québec | Attestation valide lorsque requise selon l'établissement du soumissionnaire | Régie 1.07.24 | Non-conformité |
| Intégrité et lobbyisme | Déclarations exigées au formulaire de soumission | Régie 7.04 et annexes | Non-conformité |
| Hébergement | Décrire l'emplacement exact, l'hébergeur et les infrastructures; conformité complète au plus tard 60 jours après avis d'adjudication si engagement différé | Régie 1.07.02-1.07.03; Contrat 17.07 | Risque majeur de conformité |
| Sécurité | Joindre les preuves/documentation de sécurité demandées dans la grille et l'annexe sécurité | Régie 1.07.03; annexes 7.00 N/P | Critère essentiel / risque de rejet |
| Références | Au moins trois projets de référence terminés et trois clients joignables dans les critères essentiels E-TN-28/29 | Grille E-TN-28/29 | Non-conformité |
| Personnel clé | Chargé de projet et personnel stratégique proposés non remplaçables sans autorisation; restrictions de conflit d'intérêts | Régie 1.08 | Risque contractuel |
| Prix | Bordereau détaillé, prix en dollars canadiens, selon les inclusions et règles du contrat | Régie 2.00; Formulaire annexe 2.00 | Risque financier / rejet |

## 2.2 Évaluation qualitative : pondération annoncée

| Partie | Objet | Pondération du document |
|---|---|---:|
| 1 | Expérience générale du soumissionnaire | 15 % |
| 2 | Capacité à fournir la solution et les services | 25 % |
| 3 | Organisation du mandat | 10 % |
| 4 | Assurance qualité | 5 % |
| 5 | Services professionnels (formation, intégration, support) | **Incohérence à clarifier** |
| 6 | Critères préférentiels | 25 % |

**Incohérence documentaire à soumettre en clarification :** l'annexe 1.16.03 A de la Régie (PDF p. 45) affiche 20 % pour la Partie 5; le Questionnaire d'évaluation (PDF p. 191) affiche 25 % en titre, alors que les sous-sections visibles indiquent 5 % formation, 10 % intégration et 5 % support, soit 20 %. Les pondérations globales ne doivent pas être recalculées sans addenda/clarification officielle.

Le questionnaire demande notamment : présentation du soumissionnaire, deux projets similaires, compréhension du mandat, CV des ressources stratégiques, démarche/jalons, mesures anti-retard, transfert de connaissances, outils collaboratifs, plan d'assurance qualité, plan de formation, vision d'intégration et plan de soutien.

## 2.3 Obligations du Fournisseur après adjudication

| Domaine | Obligation synthétisée | Référence |
|---|---|---|
| Solution SaaS | Fournir les licences et exploiter la solution SaaS, sans installation serveur côté client selon la définition demandée | Cahier, 2.2; E-TN-1 |
| Configuration | Paramétrer les modules, règles, accès, flux, rapports, journaux et environnements avec l'organisation | E-TN-31; Contrat |
| Intégration | Réaliser les interfaces aux systèmes internes, y compris API/ESB et flux convenus | Cahier 2.3 et 4; E-TN-7/20 |
| Déploiement | Environnements de formation, tests et production; livraison en production pour inspection finale au plus tard 16 semaines après la rencontre de démarrage (contrat, calendrier de livraison) | Contrat 2.03; P-TN-1 |
| Tests et acceptation | Fournir procédures de tests d'acceptation, réussir le banc d'essai et l'inspection finale | E-TN-12; Contrat 2.10-3.03 |
| Formation | Former les usagers pilotes et le soutien TI; former les administrateurs; fournir documentation/capsules en français | E-TN-18/25/26; P-TN-18/19 |
| Transfert de connaissances | Rendre les administrateurs et formateurs autonomes; fournir documentation de modèle de données et d'administration | E-TN-13/27; P-TN-16 |
| Disponibilité | SLA mensuel minimum de 99,7 % selon les règles contractuelles; rapport mensuel et mesures correctives | Cahier 2.4; Contrat 6.01 |
| Support | Soutien en français, notamment 6 h à 20 h du lundi au vendredi selon le fuseau indiqué, et niveaux/priorités/délais du contrat | E-TN-10/11; Régie 1.07.31-32 |
| Continuité | Maintenir plan de relève, tests de reprise, sauvegardes, rollback et continuité; le plan de relève est validé au banc d'essai | Contrat 6.04-6.05; E-TN-15/16; P-TN-3/4/5 |
| Sécurité | Maintenir les mesures, certifications/standards requis, journaux accessibles et conservés au minimum 12 mois | Contrat 6.02-6.03 |
| Mises à jour | Mises à jour régulières, conformité législative et cyber, support inclus aux mises à jour SaaS | E-TN-3/14/30 |
| Réversibilité | Prévoir transition, transfert des données et support minimal de 30 jours dans les circonstances contractuelles prévues | Contrat 6.01.08 |
| Évolution | Fournir des services professionnels additionnels selon le bordereau pour changements de conventions, règles ou besoins non couverts | Cahier 2.5 |

## 2.4 Dossier de réponse fournisseur recommandé

- Matrice de conformité E-* / P-* complétée avec OUI/NON, commentaires et références précises (document/page/section).
- Description de l'hébergement au Québec : fournisseur cloud, adresses/régions, sous-traitants, zones de sauvegarde, accès d'administration et flux transfrontaliers.
- Architecture SaaS et modèle d'isolation des données clients.
- Description Entra ID, Microsoft Graph, OpenAPI 3+, ESB, RAO, SAAV, PFT et BD Corpo.
- Catalogue des rôles/profils et modèle de séparation organisationnelle.
- Politique de sauvegarde, rétention, restauration, rollback, DR et preuves des tests.
- Dossier de sécurité : chiffrement, DDoS, détection d'intrusion, journalisation, gestion des vulnérabilités, accès privilégiés, réponse aux incidents.
- SLA, calendrier de maintenance, rapport mensuel de disponibilité et procédure d'escalade.
- Trois fiches de projets de référence et trois références client autorisées.
- Plan de projet, ressources, CV, calendrier de 16 semaines, gestion des risques et dépendances.
- Plan de formation « train the trainer », capsules paramédics et documentation éditable en français.
- Plan d'assurance qualité et stratégie de tests traçables aux exigences.
- Prix complet : licences, configuration, intégration, formation, capsules, support et services professionnels.
