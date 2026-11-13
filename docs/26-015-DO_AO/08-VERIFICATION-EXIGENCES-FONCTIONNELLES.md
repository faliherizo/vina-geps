# 8. Les 90 exigences fonctionnelles, vérifiées une à une

- **Date de la vérification** : 1er novembre 2026
- **Demande** : « Valide une à une chaque exigence fonctionnelle ; s'il y a des erreurs et des
  écarts, ajuste-les ; ajoute une colonne disant comment le tester depuis l'interface. »
- **Portée** : les **90 exigences fonctionnelles** de l'avis 26-015-DO — E-GN (17), E-CH (10),
  E-GR (13), E-GP (15), E-RF (14), et les préférentielles P-GN (3), P-GH (4), P-CH (9), P-GP (4),
  P-RF (1). Les 54 exigences technologiques (E-TN, P-TN) ne sont pas ici : elles ne se vérifient
  par aucun geste d'utilisateur, et la charge les concernant est traitée au document 09.

## 8.1 Ce que la vérification a trouvé

| Constat | Détail |
|---|---|
| **Six jugements étaient faux** | P-GN-1, P-GH-1, P-GH-3, P-GH-4, P-CH-8 et P-RF-1 étaient donnés « partiels ». Les six sont servis par du code **et** par des essais dédiés depuis des semaines. Leur texte d'écart décrivait un produit qui n'existe plus : « aucun avis n'est envoyé » alors que la table des avis et son essai existent ; « regroupements figés dans le code (4 axes) » alors que le constructeur de rapports en offre cinq sources paramétrables. |
| **Quatre exigences n'avaient aucune trace** | E-RF-6, E-RF-8, P-GH-2 et P-GP-2 étaient jugées acquises sans qu'aucun fichier du dépôt ne porte leur identifiant. Les capacités existaient ; la traçabilité, non. |
| **Dix-sept exigences n'étaient nommées par aucun essai** | Leur comportement était couvert — par un essai qui prouvait autre chose et ne les citait pas. Une couverture qu'on ne peut pas retrouver ne se vérifie pas : c'est elle qui a permis aux six jugements de vieillir sans bruit. |
| **Treize commandes déclarées n'étaient pas atteignables** | Trouvées par l'essai lui-même, à la première exécution. Une venait d'un organisme d'essai incomplet (aucun compte de planificateur, alors que l'écran des périmètres écarte volontairement les comptes d'administration) ; douze venaient de preuves que j'avais déclarées sur des commandes qui n'existent qu'après un geste — un tiroir, un choix, une saisie. |
| **Aucun défaut du produit** | Ni sur les 90 exigences, ni à l'accessibilité : **zéro violation axe-core** sur les 22 écrans et leurs 31 états, tiroirs ouverts et panneaux dépliés compris. C'est le résultat, et il ne doit pas faire oublier le précédent : le dispositif de jugement, lui, était défaillant. |

**Après correction : 90 exigences fonctionnelles sur 90 acquises**, chacune avec sa recette de
vérification à l'interface, exécutée à chaque campagne.

## 8.2 Pourquoi la matrice se périmait — et ce qui change

La matrice de couverture a été fausse **trois fois**, toujours dans le même sens : des exigences
données « partielles » alors qu'elles étaient servies. Ce n'est pas de l'inattention. Le dispositif
ne pouvait pas faire autrement :

- `construire-couverture.py` échouait si une exigence n'était **pas jugée** ; jamais si un
  jugement était **faux** ;
- un jugement était de la prose — « aucun essai n'applique un code de long terme » — que rien ne
  confrontait au dépôt ;
- aucun jugement ne portait de date de vérification, donc aucun ne pouvait être déclaré périmé.

Trois portes remplacent la relecture :

1. **Une recette par exigence, exécutable.**
   `e2e/donnees/urgences-sante/exigences-fonctionnelles.json` dit, pour chaque exigence, l'écran,
   l'onglet, les gestes et les commandes qui doivent s'y trouver.
   `e2e/tests/e2e/urgences-sante-exigences.spec.ts` ouvre les 22 écrans, pose les 18 séries de gestes et vérifie les 261 commandes déclarées.
2. **Les références d'essai sont relevées, pas écrites.**
   `docs/26-015-DO_AO/relever-essais.cjs` relève du dépôt les fichiers qui **nomment** chaque
   exigence (213 références). Un essai renommé disparaît de la liste à la régénération suivante.
3. **Le générateur refuse de générer.**
   `construire-couverture.py` s'arrête si une exigence fonctionnelle n'a pas de recette, si une
   recette vise une exigence que l'avis ne contient pas, ou si une référence d'essai ne désigne
   plus de fichier. Il écrit alors la colonne « Comment le vérifier à l'interface » dans
   `MATRICE-COUVERTURE.md` **et** dans les deux tableaux de
   [04-referentiel-exigences.md](../urgences-sante/architecture/04-referentiel-exigences.md).

Un document ne peut donc plus affirmer un essai qui n'existe pas, et une exigence ne peut plus être
déclarée servie sans que quelqu'un ait dit **où** et **comment** le constater.

## 8.3 Ce que l'essai prouve, et ce qu'il ne prouve pas

| Niveau | Ce qu'il établit | Ce qu'il ne peut pas établir |
|---|---|---|
| **L'essai de surface** (`urgences-sante-exigences.spec.ts`) | Que l'écran annoncé existe, que l'onglet s'ouvre, que les gestes mènent à l'état utile, que les commandes y sont, et que l'état obtenu passe axe-core. | Que la commande **décide juste**. Un bouton présent n'est pas une règle appliquée. |
| **L'essai profond** (`essaiProfond`, relevé du dépôt) | Que la règle, le calcul, l'autorisation et les cas d'erreur sont justes, par l'API réelle et sur le domaine. | Que le geste soit **atteignable**. Un essai d'intégration vert sur un écran mort est une conformité de papier. |

Les deux sont nécessaires, et c'est pour cela que la recette porte les deux. L'état vérifié est
l'état **attaché** au document : un panneau replié garde ses commandes, et ce qu'on veut prouver
est que la commande existe et que son résumé l'ouvre — non qu'elle soit visible sans un geste.

Trois exigences se prouvent par une commande « au choix » (`mft-anomalies|mft-sans-anomalie`) :
« aucune anomalie » et « trois anomalies » montrent l'une comme l'autre que la feuille affiche ses
anomalies. Exiger la seconde obligerait à semer une anomalie pour satisfaire un essai, et on ne
saurait plus ce qu'on mesure.

## 8.4 Rejouer la vérification

```bash
# 1. Les références d'essai, relevées du dépôt (écrit le JSON ; --verifier échoue si périmé)
node docs/26-015-DO_AO/relever-essais.cjs

# 2. La matrice et les tableaux du référentiel, régénérés (échoue si une recette manque)
python3 docs/26-015-DO_AO/construire-couverture.py

# 3. Les 90 exigences à l'écran, axe-core compris (≈ 3 min)
cd e2e && E2E_API_PORT=4010 E2E_WEB_PORT=5190 \
  npx playwright test tests/e2e/urgences-sante-exigences.spec.ts --project=e2e
```

Couvrir une exigence de plus, ou corriger un écran qui a changé, se fait **dans le fichier de
données** : l'essai n'a pas à être touché.

## 8.5 Décompte après vérification

| Type | ✅ Acquis | 🟡 Partiel | ⛔ À bâtir | 🔒 Hors code | Total |
|---|---:|---:|---:|---:|---:|
| Fonctionnelles (E-GN, E-CH, E-GR, E-GP, E-RF, P-*) | **90** | 0 | 0 | 0 | 90 |
| Technologiques (E-TN, P-TN) | 15 | 23 | 0 | 16 | 54 |
| **Total** | **105** | 23 | 0 | 16 | 144 |

Les 23 partiels restants sont tous technologiques : hébergement, haute disponibilité, Entra ID,
protection contre le déni de service, environnement de formation, documentation d'exploitation.
Ce sont des engagements d'entreprise et d'exploitation, pas du code — sauf **E-TN-17** (« les
fonctionnalités transactionnelles de base s'exécutent en moins de 2 secondes »), qui est une
mesure, et qui est traitée au [document 09](09-CHARGE-ET-NIVEAUX-DE-SERVICE.md).

---

## Révision du 1er novembre 2026 (soir) — trois niveaux, et non plus deux

Le document décrivait deux niveaux de preuve : l'**essai de surface** (chaque commande est à
l'écran) et l'**essai profond** (le comportement, par les suites d'intégration). Il en manquait un,
et c'est celui qui attrape le défaut le plus embarrassant à montrer.

| Niveau | Ce qu'il prouve | Où |
|---|---|---|
| Surface | la commande est à l'écran | `urgences-sante-exigences.spec.ts` — 90 exigences, 22 écrans |
| **Parcours** | **la personne concernée y arrive**, sous son propre compte | `urgences-sante-parcours.spec.ts` — 3 profils, 8 parcours |
| Profond | le comportement est juste | les suites nommées par `essaiProfond` |

**Pourquoi le niveau du milieu manquait.** L'essai de surface ouvre tout depuis le compte
d'**administration**. Un écran peut donc être complet pour l'administratrice et inatteignable pour
la paramédic à qui il est destiné : un rôle manquant, une ressource non reliée, un module fermé —
et rien ne le signale, parce que la preuve a été faite sous un autre compte.

Les profils et les parcours vivent dans `e2e/donnees/urgences-sante/profils.json` : ajouter une
exigence à prouver ne demande pas de toucher au code de l'essai, et le fichier dit **qui** fait
**quoi**, avec le motif de chaque geste et de chaque preuve attendue.

**Deux défauts d'outillage ont été trouvés en les écrivant**, et les deux faisaient accuser
l'amorce :

- lire les options d'un `<select>` **dès le rendu** donne une liste vide — l'écran se rend avant
  que la requête ne réponde. L'essai concluait « l'amorce n'a rien créé », alors que la donnée
  était là (vérifié en interrogeant l'API avec le jeton du profil). Les gestes partagés
  (`helpers/gestes.ts`) attendent désormais la **première option réelle** ;
- enchaîner deux profils sur la même page retombe sur le tableau de bord du premier : le
  formulaire de connexion n'existe pas, et l'attente expire sur une page parfaitement
  fonctionnelle. La session se ferme donc avant chaque connexion.

## Un organisme de démonstration, au contenu des planches

`node scripts/semer-urgences-sante.cjs` crée l'organisme, son administration, quatre personnes avec
leur compte, les trois sites des planches (Centre-ville, Nord, Sud), les trois quarts du jour avec
leur comblement (3/4, 1/3, 2/2), les postes P-104 à P-106, les deux règles nommées par s07 et s11,
et les feuilles de temps de la semaine écoulée.

L'essai `urgences-sante-demonstration.spec.ts` **joue ce script** plutôt que de recopier ses
appels : un double finirait par diverger, et c'est alors la démonstration livrée au client qui
serait la version non éprouvée.

⚠️ **`POST /api/saas/tenants` crée un organisme sans compte d'administration.** On se connecte
alors avec le compte global, dont la session ne porte aucun établissement : les écrans s'ouvrent
vides et la moitié du menu disparaît, **sans aucune erreur affichée**. Le semoir utilise
`POST /api/superadmin/tenants`, qui crée l'organisme **et** son admin.

## Révision du 8 novembre 2026 — les preuves sont celles du backend Java

**La demande** : valider que les exigences fonctionnelles sont toutes implémentées, remplacer les
liens vers le backend Node par des liens vers le backend Java, et prouver le comportement par des
essais pilotés par données JSON.

### Ce qui a changé

| Avant | Après |
|---|---|
| `relever-essais.cjs` relevait `backend/tests` (Node) et `e2e/tests` | il relève `backend-java/src/test` et `e2e/tests` : une preuve profonde est un essai **Java** |
| La colonne « Preuves » de la matrice était une prose qui nommait des fichiers du Node | pour les 90 exigences fonctionnelles, elle est **calculée** depuis les essais relevés ; les 27 jugements technologiques renvoient aux jumeaux Java |
| Trois niveaux de preuve : surface, parcours, profond | un quatrième : le **comportement par l'API** (`urgences-sante-comportements.spec.ts`), joué contre le Java |

### Ce que la vérification a trouvé

| Constat | Traitement |
|---|---|
| **26 exigences** n'étaient nommées par aucun essai Java, alors que 64 l'étaient | 25 étaient prouvées par un essai Java qui ne les citait pas : chaque citation n'a été ajoutée **qu'après lecture de l'assertion** (p. ex. E-GP-10 : « Ana à 10 km : bloquée, rien d'enregistré »). La 26ᵉ, **E-GR-2**, ne l'était qu'à moitié : un essai neuf prouve que les quarts du poste laissé vacant par l'AGPH naissent sans titulaire et sous leur minimum (`ControleurAgphEffectifsHttpTest`) |
| **Défaut réel** (E-TN-19) : la console de sécurité classait `ca-central-1`, la région AWS de **Montréal**, « hors Québec » — en Java comme en Node | corrigé des deux côtés, avec l'essai des deux cas qui comptent (Montréal au Québec, Toronto au Canada seulement) |
| **Défaut de parité** trouvé par la campagne e2e contre le Java : dans tout le contexte `horaires`, le Java prenait pour acteur l'**identifiant de compte**, le Node le **nom de connexion**. Les deux partagent la même base : Julie ouvrait une messagerie vide sur un fil bien présent, sans aucune erreur | l'identité vit désormais en un seul endroit, `IdentiteActeur` (nom de connexion, comme le Node), à laquelle délèguent les 11 contrôleurs et les 2 gardes ; 6 attentes d'essais Java corrigées, `IdentiteActeurTest` ajouté. Décision de l'utilisateur, le 9 novembre 2026, après avertissement d'un risque élevé |
| Le paquet `horaires.domaine` à 99 % de lignes, sous sa porte de 100 % | sept cas limites essayés (`BranchesDomaineHorairesTest`) |
| Aucun défaut des règles d'effectifs | les 9 scénarios de `comportements.json` passent contre le Java du premier coup une fois le jeu corrigé — la seule erreur était **la mienne** (un paramètre de poste non déclaré, que le serveur a refusé en nommant le référentiel à compléter) |

### Le quatrième niveau : le comportement, par l'API

`e2e/donnees/urgences-sante/comportements.json` décrit neuf scénarios ordonnés — une amorce, puis
des appels HTTP dont chaque attendu porte sur une **valeur** (statut, motif, taux, compte). L'essai
n'est qu'un interprète : prouver une exigence de plus, c'est ajouter un scénario au fichier.

```bash
# contre le backend Java seul, sans toucher à la pile Docker
backend-java/scripts/campagne-e2e.sh tests/e2e/urgences-sante-comportements.spec.ts
```

| Niveau | Ce qu'il prouve | Où |
|---|---|---|
| Surface | la commande est à l'écran | `urgences-sante-exigences.spec.ts` |
| Parcours | la personne concernée y arrive | `urgences-sante-parcours.spec.ts` |
| **Comportement** | **la règle décide juste, par l'API du backend livré** | `urgences-sante-comportements.spec.ts` — 9 scénarios, 28 exigences |
| Profond | chaque cas, y compris les erreurs | les essais Java relevés par `relever-essais.cjs` |
