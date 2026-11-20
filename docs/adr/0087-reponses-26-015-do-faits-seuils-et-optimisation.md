# ADR 0087 — Réponses 26-015-DO : nouveaux faits de règles, seuils par code et par convention, optimisation exacte du budget AGPH

- **Statut** : accepté, le 20 novembre 2026.
- **Décideurs** : équipe VINA.
- **Concerne** : contexte `effectifs` du serveur Java — `domaine/{Planification, EchangeEffectifs, ContexteRegle,
  TypeReferentiel, AbsencesEffectifs, TempsEffectifs, Agph}`, `application/commandes/{GestionnairePlaces,
  GestionnaireComblementAbsences, TourDeRoleAgph, ClotureAgph}` ; changesets Liquibase de la série 0020.
- **Stories** : AD-01 à AD-05 ([02_stories.md](../26-015-DO_AO/12-reponses-addenda/02_stories.md)).
- **Source** : addenda 7 (réponses 7, 8, 11, 12, 15), addenda 4 (convention SP-CSN, art. 14 à 16), addenda 5.

## Contexte

L'addenda 7 répond aux seize questions métier que nous avions posées. Cinq réponses fixent un comportement que le
produit n'a pas (traitement : [01_traitement_des_reponses.md](../26-015-DO_AO/12-reponses-addenda/01_traitement_des_reponses.md)) :

1. **Q8** : la contiguïté d'un échange vise aussi le **nombre d'heures maximal par jour de travail** (24 h). Le moteur
   de règles n'a aucun fait pour l'exprimer.
2. **Q7** : un **seuil configurable par code d'absence** choisit entre remplacement long terme et gestion régulière,
   sauf pendant une période horaire en cours. Le produit décide par la **catégorie** du code.
3. **Q15** : les heures supplémentaires se jugent par jour, par semaine **et par période de paie**, avec des seuils
   **par groupe et par convention**. Le produit n'a que deux seuils, pour tout l'organisme.
4. **Q11** : l'« optimisation des budgets » de l'AGPH est un **calcul proposé par la solution**, en heures. Le produit
   ne connaît le budget que comme une limite.
5. **Q12** : le traitement du délai échu est **configurable**, et l'employé peut **renoncer** (il devient temps
   partiel occasionnel).

L'addenda 6 (Q2) exige que les règles conventionnées se paramètrent **sans
développement** : chaque décision ci-dessous doit élargir ce qui se paramètre, pas coder une règle.

## Décisions

### 1. Le maximum d'heures sur 24 h est un **fait** du catalogue, pas un réglage

Le fait `heures24h` s'ajoute aux contextes `ECHANGE` et `ATTRIBUTION`. Le seuil (16 h à l'art. 16.02), la portée
(convention, statut, unité) et l'action (bloquer, exiger une approbation) restent des **règles**.

Options écartées :
- *Un paramètre « heures maximales par jour » de l'organisme* : une seconde source de vérité à côté des règles, sans
  portée par convention ni par statut, que l'addenda exige.
- *Les heures du jour civil* : un quart de nuit de 19 h à 7 h se couperait en deux jours et passerait toujours sous le
  seuil ; l'addenda parle d'une « période de 24 h ».

### 2. Le seuil de durée est un **attribut du code d'absence**, et la catégorie reste la valeur par défaut

`seuilJoursLongTerme`, facultatif, s'ajoute aux attributs datés du référentiel des codes d'absence. Sans seuil, la
catégorie décide comme avant. La période en cours prime sur le seuil.

Options écartées :
- *Une règle du contexte `ABSENCE`* : une règle rend une action (accepter, avertir, bloquer), pas un processus ; lui
  faire choisir une voie de comblement mêlerait deux notions.
- *Remplacer la catégorie par le seuil* : changerait sans préavis le comportement des codes déjà paramétrés.

### 3. Seuils d'heures supplémentaires : un troisième seuil, et des **surcharges par convention**

`seuilPeriodePaieHeures` et `debutPeriodesPaie` s'ajoutent aux paramètres de l'organisme ; une table de surcharges par
convention porte les trois seuils. L'excédent se compte jour, puis semaine, puis période de paie, chaque passe sur le
reste de la précédente : une minute n'est jamais comptée deux fois.

Options écartées :
- *Des règles `HEURES_SUPPLEMENTAIRES` pour le calcul* : les règles jugent et rendent une action ; le calcul des
  minutes payées majorées alimente la paie et doit rester un calcul.
- *Des seuils par unité* : l'addenda nomme le groupe d'employés **et** les règles conventionnées ; la convention est
  la clé que porte déjà la ressource et que les règles connaissent (`PorteeRegle.CONVENTION`). Le groupe s'obtient par
  la convention de ce groupe.

### 4. L'optimisation du budget est un calcul **exact**, en lecture seule

Un sac à dos 0-1 en programmation dynamique sur les quarts d'heure choisit les postes couverts qui maximisent les
heures sous le budget. La proposition ne modifie rien.

Options écartées :
- *Un glouton (postes triés par heures)* : rapide mais pas optimal ; « optimisation » serait une promesse fausse
  vérifiable en démonstration.
- *Un solveur externe (programmation linéaire)* : une dépendance et un service de plus pour un problème que la
  programmation dynamique résout en millisecondes à cette taille.
- *Appliquer la proposition d'office* : la décision d'ouvrir un poste reste au service de planification ; une
  proposition automatique appliquée sans geste humain serait une décision non tracée.

### 5. Renoncer est un **état du rang**, le délai échu un **réglage de la campagne**

`EtatRang.RENONCE` et `TraitementDelaiEchu { ATTRIBUER, PASSER }` sont des ensembles fermés (`enum`), convertis à la
frontière. La clôture traite `RENONCE` comme un rang sans poste : temps partiel occasionnel.

Option écartée : *une règle `CHOIX_AGPH` qui déciderait du délai échu* — même objection qu'au point 2.

## Conséquences

- Le catalogue des faits grandit ; l'éditeur de règles les propose sans changement d'écran.
- Deux changesets (AD-03, AD-05), chacun avec son script de retour et sa table dans `SchemaEssai`.
- Les stories AD-06 (API d'échange de l'addenda 5) et AD-07 (offre en cascade) restent à décider et à concevoir :
  elles touchent la sécurité des routes et l'ordonnanceur, et ne relèvent pas de cet ADR.
- Une réponse « Oui » à E-CH-1, E-GR-7 et E-RF-5 ne vaut que si la version **commercialisée** le 26 novembre porte
  ces stories (addenda 6, Q4).
