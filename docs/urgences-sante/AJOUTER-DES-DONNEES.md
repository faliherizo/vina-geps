# Remplir les écrans personnels d'Urgences-santé

> Écrit pour qui ouvre `/monhoraire`, `/mesdisponibilites`, `/monagph`, `/matemps` ou `/usagph` et
> les trouve **vides**, et pour qui reprend le module. La réponse courte est en bas du tableau :
> un écran personnel ne se remplit pas par lui-même, il affiche ce que la planification a produit.

## La règle qui explique presque tous les écrans vides

Les sept écrans personnels du module sont des **lectures**. Ce qu'ils montrent naît ailleurs :

1. un **compte relié à un dossier d'effectif** (`eff_ressources.utilisateurId`) — sans lui, aucun
   des sept n'a de quoi parler, et chacun le dit désormais nommément plutôt que d'afficher une
   liste vide ;
2. une **période de planification** dans le bon état — `COLLECTE` ouvre le dépôt des
   disponibilités, `PUBLIEE` fait exister l'horaire et les feuilles de temps ;
3. pour l'AGPH, une **campagne démarrée**, et non seulement créée.

Un écran qui paraît cassé est presque toujours l'un de ces trois points qui manque. C'est pourquoi
les états vides nomment ce qui manque **et à qui le demander**.

## Écran par écran

| Écran | Ce qu'il montre | Ce qu'il faut pour qu'il montre quelque chose | Qui le crée, et où |
|---|---|---|---|
| `/monhoraire` | Les quarts publiés de la **semaine affichée** | une période `PUBLIEE`, un quart, une **place attribuée** à votre ressource | planificateur : `/usperiodes` (créer, publier) puis `/usequipages` (quart, puis « Attribuer ») |
| `/mesdisponibilites` | Vos plages déposées, et les **règles qui les jugent** | une période en `COLLECTE` dont la date de fin de collecte n'est pas passée | planificateur : `/usperiodes` → ouvrir la collecte ; les règles : `/usregles`, contexte « Disponibilités » |
| `/monagph` | Votre rang au tour de rôle, les postes offerts, les remplacements long terme | une campagne **démarrée** où vous figurez, et/ou un remplacement ouvert | planificateur : `/usagph` → « Nouvelle campagne », « Ouvrir », « Démarrer le tour de rôle », « Ouvrir un remplacement » |
| `/matemps` | Votre feuille de temps, ses segments, ses anomalies | une feuille **préparée** pour une période — par vous (« Préparer ma feuille », un dialogue) ou par le planificateur | vous : `/matemps` ; ou planificateur : `/ustemps` → « Préparer les feuilles » |
| `/usagph` | La console de la campagne annuelle | une unité, un corps d'emploi, des **postes publiés** et un périmètre de niveau `GESTION` | planificateur : `/usreferentiels`, `/uspostes` (publier), `/usperimetres` |

## Où se fait chaque ajout ou modification

**Dans un dialogue, toujours** — c'est la convention du dépôt depuis le 13 septembre 2026, et elle
est maintenant **vérifiée** sur les 26 écrans du module : l'audit géométrique relève tout
formulaire d'écriture visible alors qu'aucun dialogue n'est ouvert. Restent en ligne, et le
déclarent sur l'élément (`data-formulaire`) : les **filtres et contextes** (le journal, la période
de la console des feuilles, le simulateur de règles) et les **grilles éditées là où elles se
lisent** (la semaine type de « Mon AGPH »).

| Écran | Le dialogue | S'ouvre par |
|---|---|---|
| `/matemps` | Préparer ma feuille · Ajouter un segment · Corriger un segment | `mft-preparation-ouvrir` · `mft-ajouter-ouvrir` · « Modifier » sur la ligne |
| `/monpointage` | Déclarer un quart non pointé | `mpo-declarer-ouvrir` |
| `/mesabsences` | Déclarer une absence (le même tiroir que « Absences ») | `mab-declarer` |
| `/uscomblement` | Combler un quart sous son minimum (le tiroir d'attribution de « Quarts et équipages ») | « Combler » sur la ligne |
| `/ustemps` | Paramètres des feuilles · Flux d'approbation | `feuilles-parametres-ouvrir` · `feuilles-approbations-ouvrir` |
| `/usechanges` | Proposer un échange pour une personne | `pla-ech-pour-ouvrir` |
| `/usagph` | Fixer ou modifier le budget d'heures | `pla-agph-budget-edition-ouvrir` |
| `/uspresence` | Importer des horodatages (reste ouvert pour son bilan) | `pla-pre-import-ouvrir` |
| `/usperimetres` | Ajouter un accès | `per-ajout-ouvrir` |
| `/usretention` | Modifier la politique · Purger (confirmation) | `ret-politique-ouvrir` · `ret-purger` |

Les identifiants des **champs** n'ont pas changé : seul l'ouvreur s'ajoute. Un essai qui constate
l'**absence** d'un geste le fait sur l'ouvreur — les champs d'un dialogue fermé sont absents pour
tout le monde.

**Le cas qui trompe le plus** : `/matemps` offre « Préparer ma feuille » **uniquement** si votre
compte est relié à une ressource. Avant le 2 novembre 2026, le bouton était là pour tout le monde
et répondait « « ressourceId » ou « uniteCode » : pour qui préparer les feuilles ? » — la question
d'un planificateur, posée à quelqu'un qui demandait la sienne.

## La voie courte : semer un organisme entier

```bash
node scripts/semer-urgences-sante.cjs --api http://localhost:4000
```

Il crée l'organisme, son administration, quatre personnes avec leur compte, les trois sites des
planches, les quarts du jour avec leur comblement, les postes, les règles nommées par s07 et s11,
et les feuilles de temps de la semaine écoulée. Les identifiants sont imprimés à la fin, et les
données viennent de `e2e/donnees/urgences-sante/demonstration.json`, où **chaque valeur nomme sa
planche**.

## La voie longue : par l'API, dans l'ordre

L'ordre n'est pas négociable — chaque appel suppose le précédent. C'est exactement la séquence que
joue `amorcerOrganisme` dans `e2e/tests/e2e/helpers/urgences-sante.ts`, et c'est la référence à
lire plutôt que cette liste si les deux divergent.

```
POST /api/effectifs/referentiels/unites            { code, libelle, effectifDepuis }
POST /api/effectifs/referentiels/corps_emploi      { code, libelle, effectifDepuis }
POST /api/effectifs/referentiels/statuts_emploi    { code, libelle, comportement, effectifDepuis }
POST /api/effectifs/referentiels/natures_quart     { code, libelle, comportement, effectifDepuis }
POST /api/effectifs/referentiels/types_heure       { code, libelle, comportement, attributs }
POST /api/effectifs/milieux                        { code, libelle, typeCode, uniteCode, … }
POST /api/effectifs/planification/ressources       { matricule, nom, uniteCode, corpsEmploiCode, utilisateurId }
POST /api/effectifs/planification/postes           { code, libelle, uniteCode, cycle, debutCycle, … }
POST /api/effectifs/planification/postes/publier   { ids: [...] }
POST /api/effectifs/planification/periodes         { nom, uniteCode, debut, fin }
POST /api/effectifs/planification/periodes/:id/etat{ etat: "COLLECTE", finCollecte }   ← /mesdisponibilites
POST /api/effectifs/planification/periodes/:id/etat{ etat: "CONFECTION" } puis "PUBLIEE"
POST /api/effectifs/planification/equipages        { periodeId, date, debut, fin, composition }
POST /api/effectifs/planification/places/:id/attribuer { ressourceId }                 ← /monhoraire
POST /api/effectifs/temps/feuilles/preparer        { uniteCode, debut, fin }           ← /matemps
POST /api/effectifs/agph/campagnes                 { uniteCode, corpsEmploiCode, annee, delaiChoixMinutes }
POST /api/effectifs/agph/campagnes/:id/ouvrir      {}
POST /api/effectifs/agph/campagnes/:id/demarrer    {}                                  ← /monagph (le rang)
POST /api/effectifs/agph/remplacements             { posteId, origine, debut, fin, finManifestation }
```

**`demarrer` est le pas qu'on oublie.** Une campagne « ouverte » n'a pas de tour de rôle : `/monagph`
n'affiche alors aucun rang, et l'essai qui l'acceptait (`agm-info|agm-sans-campagne`) passait sans
aucune donnée.

## Ce qui est mesuré, et par quoi

| Essai | Ce qu'il prouve |
|---|---|
| `e2e/tests/e2e/urgences-sante-saisies.spec.ts` | qu'on peut **écrire** dans ces écrans, et que la donnée revient — par son **contenu**, non par la présence du tableau qui l'héberge |
| `e2e/tests/e2e/urgences-sante-parcours.spec.ts` | que chaque écran est **atteignable** par la personne à qui il est destiné |
| `e2e/tests/e2e/urgences-sante-exigences.spec.ts` | que chaque commande de chaque exigence est à l'écran |
| `e2e/tests/ui/audit-wcag22.spec.ts` | la géométrie et l'accessibilité, à quatre largeurs, **état par état** ; chaque dialogue `…-ouvrir` ouvert et mesuré ; aucun formulaire d'écriture hors dialogue ; aucun écran effondré |
| `backend/tests/unit/module-us-verbes.test.ts` | que chaque écran **agit** — ou dit pourquoi non, avec un cliquet à zéro |
| `e2e/tests/ui/barre-et-menu-collants.spec.ts` | que le menu reste collé **sous** la barre après défilement, à chaque largeur de poste |

Les scénarios de saisie vivent dans `e2e/donnees/urgences-sante/saisies.json` : ajouter un
scénario ne demande pas de toucher au code de l'essai, et chaque geste y dit **pourquoi** il est
posé. `{jour:N}` y vaut la date de dans N jours — un fichier de données ne calcule pas, et une date
figée ferait échouer l'essai le lendemain de son écriture.
