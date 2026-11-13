# Hypothèses, risques et critères de validation

## Hypothèses de conception
- Les six sites du devis constituent le périmètre initial.
- Le nombre planifié au bordereau est de 1 500 utilisateurs; la solution doit toutefois supporter au moins 2 000 utilisateurs selon la grille.
- Les canaux SMS, voix, push et courriel dépendent de fournisseurs et configurations institutionnels à sélectionner.
- Les entités et valeurs métier doivent être configurables dynamiquement.
- Les interfaces et livrables sont en français.

## Registre de risques
| ID | Risque | Probabilité / impact | Mesure |
|---|---|---|---|
| R-01 | Ambiguïté hébergement / responsabilités | Élevée / Élevé | Arbitrage architecture et RACI avant design détaillé |
| R-02 | Accusé SMS non équivalent à lecture | Élevée / Élevé | Définir sémantique et UX des statuts |
| R-03 | Intégration téléphonie complexe | Moyenne / Élevé | Atelier technique et preuve de concept |
| R-04 | Groupes/horaires de mauvaise qualité | Élevée / Élevé | Profilage, mapping et validation des données |
| R-05 | Règles d'escalade non validées cliniquement | Moyenne / Critique | Approbation métier et tests de scénarios |
| R-06 | RTO/RPO non définis | Élevée / Élevé | Inscrire objectifs au SLA/PRA |
| R-07 | Volume de pointe inconnu | Moyenne / Élevé | Mesure et test de charge |
| R-08 | Conservation incompatible avec exigences légales | Moyenne / Élevé | Analyse vie privée et calendrier de conservation |
| R-09 | Déploiement multi-sites perturbe les opérations | Moyenne / Élevé | Pilote, vagues et rollback |
| R-10 | Facturation active contestée | Moyenne / Moyen | Rapport détaillé et règles de déduplication validées |

## Critères de validation transverses
- Toutes les exigences applicables de la grille disposent d'une réponse et d'une preuve.
- Tous les parcours fonctionnels prioritaires sont démontrés.
- Les groupes, règles et paramètres peuvent être modifiés sans déploiement logiciel.
- Les tests d'autorisation démontrent le cloisonnement multi-sites.
- Les événements permettent de reconstituer l'historique d'une communication.
- Les canaux et accusés sont testés avec les fournisseurs réels.
- Les tests de charge couvrent le volume approuvé.
- Les tests de sauvegarde/restauration et rollback sont concluants.
- Le PCA/PRA et les SLA sont approuvés.
- Chaque site signe ses critères de mise en service.

## Checklist de complétude documentaire
- [x] Conversion du devis descriptif en Markdown.
- [x] Conversion du bordereau et de la grille en Markdown.
- [x] Analyse des besoins métier.
- [x] Catalogue des exigences fonctionnelles.
- [x] Catalogue des exigences fournisseur.
- [x] Exigences techniques et non fonctionnelles.
- [x] Matrice des écarts et questions ouvertes.
- [x] Architecture de solution.
- [x] Diagrammes C1, C2, C3 et C4.
- [x] Processus BPMN 2.0 en tableaux et diagrammes.
- [x] MCD dynamique.
- [x] Diagrammes de séquence.
- [x] Sécurité, intégrations, exploitation et continuité.
- [x] ADR transverses.
- [x] Roadmap et backlog.
- [x] Analyse individuelle des stories.
- [x] Questions client.
- [x] Maquettes SVG et PNG.
- [ ] Validation des choix avec le client.
- [ ] Validation juridique, sécurité, architecture et opérations.
- [ ] Production d'un BPMN XML formel et d'un modèle UML outillé si exigés contractuellement.
