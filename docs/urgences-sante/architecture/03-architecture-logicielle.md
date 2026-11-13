# Référentiel et limites de l'analyse

- **Source principale :** Appel d'offres 26-015-DO, *Système de gestion des effectifs (Solution infonuagique)*, Urgences-santé.
- **Fichier analysé :** `26-015-DO_AO (1).pdf`, 199 pages.
- **Parties repérées :** Régie, Contrat, Cahier de charges et devis technique, Formulaire de soumission, Questionnaire d'évaluation de la qualité et annexe/grille des critères de conformité.
- **Traçabilité :** les exigences E-* et P-* sont reprises des pages 194 à 198 du PDF (annexe 7.00 / critères de conformité). Le contexte d'affaires est repris principalement des pages PDF 145 à 151.
- **Précaution :** les annexes Excel externes mentionnées dans le PDF (grille détaillée des critères, bordereau de prix, questionnaire d'évaluation distinct) ne sont pas présentes comme fichiers séparés dans la pièce jointe. L'analyse ne prétend donc pas couvrir des éléments qui n'apparaissent pas dans le PDF.
- **Nature du document :** analyse de solution proposée, pas un avis juridique ni une preuve de conformité d'un produit fournisseur. Les choix d'architecture ci-dessous sont des recommandations à valider avec Urgences-santé et l'éditeur.
# 6. Architecture logicielle et conception technique

## 6.1 Style d'architecture

Architecture logique modulaire orientée domaines métier, exposée par API. La solution réelle étant un produit SaaS éditeur, cette architecture décrit les capacités logicielles et les frontières d'intégration attendues; elle n'impose pas une implémentation interne non spécifiée par l'appel d'offres.

## 6.2 Modules et responsabilités

| Module | Responsabilités | Données maîtres / dépendances |
|---|---|---|
| Identity & Access | Fédération Entra, profils, habilitations, périmètres organisationnels | Entra ID + matrice de rôles locale |
| Organization & Workforce | Employés, unités, sites, corps d'emploi, compétences, statuts | BD Corpo / RH |
| Rules & Eligibility | Règles de convention, ancienneté, disponibilité, admissibilité, priorités | Référentiels RH + paramètres |
| Absence & Availability | Demandes, banques, calendriers, disponibilité, décisions | BD Corpo pour soldes/droits à confirmer |
| Annual Scheduling (AGPH) | Cycle annuel, postes, tours de rôle, publication | Rules + workforce |
| Operational Scheduling | Horaires courants, quarts ad hoc, quotas, échanges, couverture | AGPH + règles |
| Staffing & Replacement | Offres de quarts, remplacements court/long terme, sélection | Availability + Rules |
| Time Capture | Punch temps réel/différé, segments, présence, site | RAO/SAAV éventuels |
| Timesheet | Consolidation, validation, heures sup, approbation, export paie | Time Capture + horaires |
| Notification | Courriel/SMS/canaux configurés, modèles, événements | Référentiel de contacts |
| Reporting | Opérationnel, statistiques, export, alimentation DWH | Données métier |
| Audit & Compliance | Historique des opérations, changements de paramètres, consultations | Tous modules |
| Integration Hub | Connecteurs API/ESB/Graph, transformation, retries, dead-letter | Systèmes externes |

## 6.3 Contrats API candidats

Exemples de ressources REST à spécifier dans OpenAPI 3+ :
- `GET/POST /employees`, `GET /organizational-groups`, `GET /work-sites`
- `GET/POST /availability-periods`, `POST /availability-submissions`
- `GET/POST /absence-requests`, `POST /absence-requests/{id}/decision`
- `GET/POST /planning-periods`, `GET/POST /shifts`, `GET/POST /positions`
- `POST /scheduling-runs`, `GET /scheduling-runs/{id}/results`
- `GET /open-shifts`, `POST /shift-offers`, `POST /shift-assignments`
- `POST /shift-exchanges`, `POST /manual-overrides`
- `POST /time-entries`, `POST /presence-events`, `POST /timesheets/{id}/approve`
- `GET /reports`, `POST /exports`, `GET /audit-events`

Les endpoints sont des propositions de conception; ils ne sont pas des URI contractuelles du document source.

## 6.4 Événements métier candidats

`EmployeeUpdated`, `AvailabilitySubmitted`, `AbsenceRequested`, `AbsenceApproved`, `AbsenceOverridden`, `PlanningPeriodOpened`, `PositionPublished`, `PositionSelected`, `ShiftUncovered`, `ShiftOffered`, `ShiftAssigned`, `ShiftExchanged`, `PunchRecorded`, `PresenceValidated`, `TimeAnomalyDetected`, `TimesheetApproved`, `PayrollExportPrepared`.

Chaque événement devrait inclure `eventId`, `occurredAt`, `actorId` ou `actorType=SYSTEM`, `correlationId`, `sourceSystem`, `aggregateId`, `schemaVersion` et les données strictement nécessaires.

## 6.5 Règles de cohérence

- Idempotence sur les opérations d'intégration et de reprise.
- Contrôle de concurrence lors des sélections de postes et affectations pour éviter les doubles attributions.
- Transactions atomiques pour décision d'absence + code d'absence + relance du comblement.
- Versionnement des règles et paramètres; aucune modification rétroactive des quotas temporaires (E-GR-9).
- Conservation des instantanés de règle ayant servi à calculer une attribution pour expliquer a posteriori une décision.
- Gestion des fuseaux horaires et des quarts traversant minuit; définir le fuseau de référence opérationnel.
- Réconciliation des horaires planifiés, déclarations de temps et sources tierces.

## 6.6 Stratégie de tests

| Niveau | Tests |
|---|---|
| Unitaire | Calculs de règles, admissibilité, quotas, temps, heures supplémentaires |
| Contrat API | Schémas OpenAPI, compatibilité, erreurs, versionnement |
| Intégration | Entra, Graph, ESB, RAO, SAAV, BD Corpo, PFT |
| Système | Parcours AGPH → gestion régulière → punch → feuille de temps → export |
| Règles métier | Jeux de tests issus des conventions, statuts et cas limites |
| Sécurité | Authentification, autorisation, isolation, vulnérabilités, DDoS, journaux |
| Performance | Transactions de base < 2 secondes dans un scénario de charge convenu |
| Résilience | Panne connecteur, reprise transaction, restauration, DR, rollback ≤ 24 h |
| Acceptation | Scénarios d'affaires signés par planification, RH, paie, opérations et TI |

## 6.7 Observabilité

- Logs structurés et centralisés; pas de secrets ni de données personnelles superflues.
- Métriques : disponibilité, latence p50/p95/p99, taux d'erreur, files d'attente, succès d'intégration, échecs de règles, anomalies de temps.
- Traces distribuées corrélées par `correlationId`.
- Alertes sur indisponibilité, dégradation, échec d'interface, accumulation de messages, anomalies de sécurité et dépassement des seuils.
- Tableaux de bord distincts exploitation fournisseur, sécurité TI, planification et paie.

## 6.8 Exigences non fonctionnelles et vérification

| NFR | Cible source | Vérification proposée |
|---|---|---|
| Disponibilité | ≥ 99,7 % mensuel | Rapport mensuel et calcul vérifiable |
| Disponibilité opérationnelle | 20 h / 7 j / 365 j dans E-TN-9; contrat basé sur 24/7 | Clarification et mesure continue |
| Performance | Transactions de base < 2 s | Test de charge avec percentile et scénario documentés |
| Authentification | Microsoft Entra ID | Test SSO, MFA/politiques et révocation |
| API | OpenAPI 3+ | Validation des contrats et essais d'intégration |
| Interface | Français, responsive, Chrome/Edge récents | Tests multi-écrans/navigateurs |
| Isolation | Données opérationnelles, sauvegardes, archives séparées | Tests de séparation et revue d'architecture |
| Audit | Traces conservées au moins 12 mois au contrat | Recherche d'événement et contrôle de rétention |
| Reprise | Rollback ≤ 24 h; DR à tester annuellement en préférence | Exercices et preuves |
| Support | Français, plage 6 h-20 h du lundi au vendredi | SLA, tickets, escalades |
| Hébergement | Québec | Attestation et cartographie des régions/flux |

## 6.9 Découpage de livraison logiciel

1. Socle identité, organisation, référentiels, groupes et audit.
2. Règles d'affaires, disponibilité et absences.
3. AGPH et sélection des postes.
4. Gestion régulière, quarts et remplacements.
5. Punch et validation de présence.
6. Feuilles de temps, approbations, paie et rapports.
7. Interfaces, Data Warehouse, exploitation et sécurité.
8. Formation, bascule, stabilisation et réversibilité.
