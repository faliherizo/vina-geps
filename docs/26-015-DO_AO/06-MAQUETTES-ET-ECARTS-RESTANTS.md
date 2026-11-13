# 26-015-DO — Les douze maquettes, les écrans livrés, et ce qui reste

> Mesuré le **1er novembre 2026**. Les chiffres viennent de
> `python3 docs/26-015-DO_AO/construire-couverture.py`, jamais de mémoire ; les écrans viennent de
> `backend/src/saas/catalogueVues.ts`, lu à la source.
>
> ⚠️ **Révision du 1er novembre, en fin de journée.** La première version de ce document reprenait
> douze « écarts de code » des jugements portés le **30 septembre**. En rouvrant chacun dans le
> code, **les douze étaient servis** — implantation *et* essais dédiés. La matrice n'avait pas
> suivi un mois de livraisons. Les douze sont passés à ✅ avec leur preuve, et un essai de bout en
> bout les tient désormais à l'écran : `e2e/tests/e2e/urgences-sante-ecarts.spec.ts`, piloté par
> `e2e/donnees/urgences-sante/ecarts.json`. Le § 4 ci-dessous conserve la trace de ce qui était
> affirmé, parce qu'un document qui efface ses erreurs n'apprend rien à qui le relit.
>
> ⚠️ **Deuxième révision, même jour.** La vérification a été étendue aux **90** exigences
> fonctionnelles : **six préférentielles** de plus (P-GN-1, P-GH-1, P-GH-3, P-GH-4, P-CH-8,
> P-RF-1) étaient elles aussi servies et testées tout en étant jugées « partielles ». Les 90 sont
> acquises. Le dispositif qui empêche désormais un jugement de vieillir — une recette de
> vérification par exigence, exécutée, et un générateur qui refuse de générer sans elle — est au
> [document 08](08-VERIFICATION-EXIGENCES-FONCTIONNELLES.md) ; la charge et les niveaux de service
> au [document 09](09-CHARGE-ET-NIVEAUX-DE-SERVICE.md).

---

## 1. La question qu'il faut poser avant toutes les autres

La demande était : « analyse les écarts avec les fonctionnalités du **module Santé Québec** ».

La réponse est que **ce n'est pas le module Santé Québec qui répond à cet appel d'offres**, et
c'est une décision consignée — [décision D-1 du document 05](05-ROADMAP-IMPLEMENTATION.md). Deux
moteurs de planification coexistent dans le produit :

| | Module **Santé Québec** (`horaires` + 5 sections) | Contexte **effectifs** (`effectifs*`, 11 modules) |
|---|---|---|
| Sert | AO **2027-005** — horaires médicaux, gardes cliniques | AO **26-015-DO** — effectifs paramédicaux d'Urgences-santé |
| Unité planifiée | un **quart** sur une tâche, une personne | un **équipage** sur un poste-ligne, plusieurs places |
| Cycle | période d'horaire, publication, échanges | **campagne annuelle AGPH**, tour de rôle, remplacements long terme |
| Présence | aucune | **pointage** horodaté, milieu physique, corroboration |
| Écrans | 36 pages (`sq*`) | 1 page de paramétrage à 7 onglets + 1 page de planification à 11 onglets |

Greffer les invariants d'Urgences-santé — état de collecte, équipage, poste-ligne — sur les tables
`horaire_*` aurait fait porter le risque à trois clients existants. **Ce qu'il faut retenir pour
la suite** : les deux modules partagent des notions (périmètre par groupe, absences par la façade
`/api/absences`, journal, référentiels datés) mais pas leur noyau, et leur fusion est un lot à
part qui n'est pas demandé ici.

## 2. Où en est la couverture

| | ✅ Acquis | 🟡 Partiel | ⛔ À bâtir | 🔒 Hors code | Total |
|---|---:|---:|---:|---:|---:|
| Critères **essentiels** | **78** | 17 | **0** | 5 | 100 |
| Critères **préférentiels** | 21 | 12 | **0** | 11 | 44 |

**Zéro exigence à bâtir, et zéro écart de code.** Les **17** partiels essentiels qui restent sont
**tous du lot U0** — SaaS, haute disponibilité, Entra ID, protection DDoS, détection d'intrusion,
environnement de formation. Ce sont des engagements d'exploitation et de contrat : ils ne se
ferment pas en écrivant du code, et les classer ailleurs donnerait une fausse idée de l'effort.

## 3. Les douze maquettes, écran par écran

Les maquettes de `docs/urgences-sante/maquettes/svg/` partagent un menu principal à dix entrées.
Voici à quoi chacune correspond **dans le produit tel qu'il est aujourd'hui**.

| Maquette | Écran servant | État |
|---|---|---|
| `s01` Tableau de bord gestionnaire | `dashboard` + tuiles de module | 🟡 L'accueil se compose par module (ADR 0066) mais **aucune tuile d'effectifs** n'est au catalogue des widgets |
| `s02` Portail employé | `dashboard`, `me`, `mesdisponibilites`, `mesechanges`, `monagph`, `monpointage`, `matemps`, `mesabsences` | ✅ Les sept vues libre-service existent, chacune avec son module |
| `s03` Mon horaire | `planification:periodes` + `matemps` | 🟡 La vue personnelle d'horaire d'effectifs n'a pas d'écran propre (le module `horaires` en a un) |
| `s04` Confection annuelle AGPH | `planification:agph`, `monagph` | 🟡 Campagne et tour de rôle livrés ; **phase DEPOT vide** (E-CH-1) |
| `s05` Centre de couverture des quarts | `planification:equipages`, `:postes`, `:quotas` | ✅ |
| `s06` Détail du quart | tiroir de `planification:equipages` | ✅ |
| `s07` Demandes et échanges | `planification:echanges`, `mesechanges` | ✅ |
| `s08` Pointage et présence | `planification:presence`, `monpointage` | ✅ |
| `s09` Feuille de temps | `planification:temps`, `matemps` | 🟡 Voir § 5 — c'est l'objet de la demande du 1er novembre |
| `s10` Rapports et indicateurs | `rapports` | 🟡 Les relevés d'effectifs ne sont pas dans l'écran de rapports |
| `s11` Administration des règles | `effectifs:regles` | ✅ |
| `s12` Administration technique | `effectifs:integrations`, `:journal`, `:retention` | ✅ |

**Ce que cette lecture apprend et que la matrice ne dit pas** : les écrans existent presque tous,
mais **quatre maquettes sur douze n'ont pas d'hôte dédié** — `s01`, `s03`, `s09` et `s10`
retombent sur un écran partagé ou sur un onglet. Ce n'est pas un manquement aux exigences (elles
sont servies) ; c'est un écart de **présentation** par rapport à ce qui a été montré au client, et
c'est exactement le genre d'écart qu'une grille de conformité ne voit pas.

## 4. Les douze écarts annoncés — et ce qu'ils étaient réellement

**Tous servis.** Le tableau garde la colonne « ce qui était affirmé manquant » : c'est elle qui dit
pourquoi une matrice se revérifie contre le code et non contre elle-même.

| Exigence | Ce qui était affirmé manquant le 30 septembre | Ce que le code contient |
|---|---|---|
| **E-CH-1** | « la phase DEPOT de la campagne AGPH est vide » | `agph/serviceDepotsCampagne` : dépôt rattaché à la campagne, faits, poids sur le budget, règles `DEPOT_AGPH`, refus motivé |
| **E-CH-3** | « postes modifiables un à un seulement » | `modifierPostesEnLot` + `modifierEnLot`, routes `POST /postes/lot` et `/equipages/lot` |
| **E-CH-9** | « l'état non comblé n'est pas enregistré » | `NON_COMBLE` est un état final, avec `motifNonComble` et son entrée de journal |
| **E-CH-10** · **E-GR-2** | « aucun essai ne montre les quarts transférés dans les écrans » | `transfereLe` + `quartsTransferes` ; l'onglet Comblement est désormais **ouvert par l'e2e** |
| **E-GN-6** | « aucune recherche, offre ni attribution de remplacement » | tables `eff_comblements` / `eff_comblement_avis`, ouverture de remplacement depuis l'absence autorisée, dérogation tracée |
| **E-GR-5** | « aucun essai sur une période publiée » | une période publiée **n'accepte que des ajustements** ; `servicePeriodes` les porte jusqu'à la clôture |
| **E-GP-3** | « aucune règle configurable de délai, de rôle ou de fermeture de période » | contexte de règles `MODIFICATION_HEURES`, dont les faits portent **exactement ces trois éléments** |
| **E-GP-6** | « ni approbateur par palier, ni conditions de routage » | chaque palier porte son approbateur ; le routage se pose sur champ, opérateur et valeur |
| **E-RF-13** | « corroboration non utilisée dans la feuille de temps » | `toleranceCorroborationMinutes`, `actionNonCorrobore`, `actionSourcesDivergentes` paramétrables |
| **E-GN-9** | « aucun essai ne prouve l'exhaustivité du journal » | `effectifs-journal-exhaustivite.integration.test.ts` |
| **E-TN-7** | « relais déclenché à la main, format supposé » | connecteur ESB sortant, événements tirés du journal **à partir d'un curseur** |

### Ce que l'essai prouve, et ce qu'il ne prouve pas

`urgences-sante-ecarts.spec.ts` ouvre chaque écran et vérifie que les commandes servant l'exigence
y sont. Pour **E-GN-6** et **E-GP-3**, trois commandes n'apparaissent qu'une fois une absence
décidée ou une feuille ouverte — un dossier métier que ce parcours ne crée pas. Le jeu de données
le déclare (`preuveProfonde`) et nomme la suite qui les éprouve, plutôt que d'abaisser la preuve
jusqu'à ce qui s'affiche sans données : un essai vert obtenu ainsi signerait « prouvé à l'écran »
pour une console vide.

### Un défaut réel, trouvé au passage

L'essai a relevé un manquement de contraste présent **sur toutes les vues** : le badge
d'établissement pose un voile blanc à 12 % sur la colonne de navigation, ce qui l'éclaircit de
`#073f70` à `#255681` ; le texte qui en héritait la couleur tombait à **3,87:1**. Corrigé par des
couleurs calculées (6,10:1 et 5,00:1). Le relevé de contraste maison du dépôt ne pouvait pas le
voir : il **sautait** les couches d'opacité ≤ 0,5 au lieu de les composer. Il les compose
désormais.
Quatre des douze se ferment **par des essais** et non par du code : la fonction existe, c'est sa
preuve qui manque. Les confondre avec les autres gonflerait l'estimation de moitié.

## 5. La feuille de temps — s09

C'est l'objet de la demande du 1er novembre 2026, traitée à part dans
[07-FEUILLE-DE-TEMPS-PARTAGEE.md](07-FEUILLE-DE-TEMPS-PARTAGEE.md).
