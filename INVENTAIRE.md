# Inventaire de la copie — module Urgences-santé

> Généré le 13 nov. 2026 depuis `freelance-invoicing` (branche `features/urgence-sante`, commit `52087a5`, arbre de travail propre).

- `backend-java/src/main` : 291 fichier(s)
- `backend-java/src/test` : 43 fichier(s)
- `frontend/src` : 73 fichier(s)
- `frontend/tests` : 11 fichier(s)
- `e2e` : 26 fichier(s)
- `docs` : 86 fichier(s)
- `infra` : 19 fichier(s)
- `scripts` : 1 fichier(s)
- `schema` : 1 fichier(s)

## Interface — dépendances hors module (fermeture des imports depuis `urgencessante/index.tsx` et `effectifs/`)

| Fichier | Rôle | Sort prévu |
|---|---|---|
| `ChampPersonne.tsx` | socle commun | phase 2 : rapatrier |
| `FileViewerModal.tsx` | socle commun | phase 2 : rapatrier |
| `MIFI.tsx` | couplage à couper (MIFI / scolaire) | phase 2 : retirer de la déclaration d'absence |
| `MaFeuilleTemps.tsx` | libre-service du module | **copié** |
| `MesAbsences.tsx` | libre-service du module | **copié** |
| `MesDisponibilites.tsx` | libre-service du module | **copié** |
| `MesEchanges.tsx` | libre-service du module | **copié** |
| `MifiPersonnes.tsx` | couplage à couper (MIFI / scolaire) | phase 2 : retirer de la déclaration d'absence |
| `Modal.tsx` | socle commun | phase 2 : rapatrier |
| `MonAgph.tsx` | libre-service du module | **copié** |
| `MonHoraire.tsx` | libre-service du module | **copié** |
| `MonPointage.tsx` | libre-service du module | **copié** |
| `Pagination.tsx` | socle commun | phase 2 : rapatrier |
| `SoutienPieces.tsx` | socle commun | phase 2 : rapatrier |
| `TbodyPagine.tsx` | socle commun | phase 2 : rapatrier |
| `absences/DeclarationEffectifs.tsx` | libre-service du module | **copié** |
| `absences/DeclarationScolaire.tsx` | couplage à couper (MIFI / scolaire) | phase 2 : retirer de la déclaration d'absence |
| `absences/FormulaireAbsence.tsx` | socle commun | phase 2 : rapatrier |
| `apercuTableur.ts` | socle commun | phase 2 : rapatrier |
| `composants/Avatar.tsx` | socle commun | phase 2 : rapatrier |
| `composants/Badge.tsx` | socle commun | phase 2 : rapatrier |
| `composants/BarreFiltres.tsx` | socle commun | phase 2 : rapatrier |
| `composants/Calendrier.tsx` | socle commun | phase 2 : rapatrier |
| `composants/EnTeteEcran.tsx` | socle commun | phase 2 : rapatrier |
| `composants/Etapes.tsx` | socle commun | phase 2 : rapatrier |
| `composants/EtatVide.tsx` | socle commun | phase 2 : rapatrier |
| `composants/FilAriane.tsx` | socle commun | phase 2 : rapatrier |
| `composants/Icone.tsx` | socle commun | phase 2 : rapatrier |
| `composants/MenuActions.tsx` | socle commun | phase 2 : rapatrier |
| `composants/Message.tsx` | socle commun | phase 2 : rapatrier |
| `composants/Onglets.tsx` | socle commun | phase 2 : rapatrier |
| `composants/PageMaquette.tsx` | socle commun | phase 2 : rapatrier |
| `composants/SommaireSections.tsx` | socle commun | phase 2 : rapatrier |
| `composants/Tableau.tsx` | socle commun | phase 2 : rapatrier |
| `composants/Tiroir.tsx` | socle commun | phase 2 : rapatrier |
| `composants/Tuile.tsx` | socle commun | phase 2 : rapatrier |
| `composants/index.ts` | socle commun | phase 2 : rapatrier |
| `formeRecue.ts` | socle commun | phase 2 : rapatrier |
| `i18n.tsx` | socle commun | phase 2 : rapatrier |
| `i18nEcrans.ts` | socle commun | phase 2 : rapatrier |
| `mifi/CalendrierContrat.tsx` | couplage à couper (MIFI / scolaire) | phase 2 : retirer de la déclaration d'absence |
| `mifi/ContractSchedulePanel.tsx` | couplage à couper (MIFI / scolaire) | phase 2 : retirer de la déclaration d'absence |
| `mifi/SemaineType.tsx` | couplage à couper (MIFI / scolaire) | phase 2 : retirer de la déclaration d'absence |
| `moduleAccess.tsx` | socle commun | phase 2 : rapatrier |
| `navigation.tsx` | socle commun | phase 2 : rapatrier |
| `santequebec/appels.tsx` | communication réutilisée de Santé Québec | phase 2 : déplacer dans `urgencessante/communication` |
| `santequebec/menu.ts` | communication réutilisée de Santé Québec | phase 2 : déplacer dans `urgencessante/communication` |
| `santequebec/noyau.tsx` | communication réutilisée de Santé Québec | phase 2 : déplacer dans `urgencessante/communication` |
| `santequebec/pages/sqlistesdiffusion.tsx` | communication réutilisée de Santé Québec | phase 2 : déplacer dans `urgencessante/communication` |
| `santequebec/pages/sqmessages.tsx` | communication réutilisée de Santé Québec | phase 2 : déplacer dans `urgencessante/communication` |
| `santequebec/perimetre.tsx` | communication réutilisée de Santé Québec | phase 2 : déplacer dans `urgencessante/communication` |
| `santequebec/references.tsx` | communication réutilisée de Santé Québec | phase 2 : déplacer dans `urgencessante/communication` |
| `telechargerIcs.ts` | socle commun | phase 2 : rapatrier |
