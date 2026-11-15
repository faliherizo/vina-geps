# ADR 0076 — Un module de paie québécois, alimenté par les seules feuilles approuvées, qui fige ce qu'il paie

- **Statut** : accepté, le 14 novembre 2026.
- **Décideurs** : équipe VINA, à la demande du responsable du produit.
- **Renverse** : la décision **D2** de la séparation ([PLANIFICATION-SEPARATION.md](../../PLANIFICATION-SEPARATION.md) § 4),
  qui excluait la paie du produit (« l'AO 26-015-DO verse les feuilles de temps à la paie de l'organisme, il ne la calcule pas »).
- **Concerne** :
  - le nouveau contexte `paie` (`domaine`, `application`, `infrastructure/{web,persistance,configuration}`) ;
  - les ports partagés `partage/paie/HeuresApprouveesPort` et `partage/paie/PersonnesPourPaiePort`, implantés par `effectifs` ;
  - le barème annuel `src/main/resources/paie/bareme-2026.json` ;
  - le changeset `0012-paie.xml` ;
  - les écrans « Cycles de paie », « Dossiers de rémunération », « Remises et relevés », « Mes bulletins de paie » et « Paramètres de la paie ».
- **Prolonge** : le statut PAYEE et les gestes « rappeler » et « rouvrir » d'une feuille de temps (livrés le même jour).

## Contexte

Le responsable du produit demande « un module de paie complet, comme dans Nexu VINA », avec trois règles :

1. la paie se calcule **à partir des seules feuilles de temps approuvées** ;
2. **avant** la paie, une feuille peut être retournée, rappelée ou rouverte ;
3. **après** la paie, elle ne se modifie plus.

D2 avait écarté la paie pour ne pas emporter les quelque 300 fichiers du module de Nexu VINA. Celui-ci mêle
la paie proprement dite à des contrats, des dépenses, des projets et des calendriers. Ici, le produit n'a
besoin que de la paie elle-même, branchée sur les heures que les effectifs ont déjà approuvées.

Le module de Nexu VINA a été relu (lecture seule) : `rates.ts`, `deductions.ts`, `payrollRun.ts`. Il calcule par
annualisation, garde un cumul annuel (« YTD ») stocké sur l'employé et produit les cases du RL-1 et du T4.
Trois défauts ne doivent pas être recopiés :

- le crédit personnel du Québec 2025 vaut 18 056 $ dans le code, au lieu de 18 571 $ ;
- l'AE de l'employeur est calculée sur la cotisation **arrondie** de l'employé ;
- le cumul stocké sur la fiche diverge dès qu'un bulletin est annulé.

Le contexte `pays` du produit n'est pas une paie : c'est un estimateur multi-pays du net annuel, sans crédit
personnel, sans cumul et sans plafond tenu dans l'année.

### Sources des taux 2026

Revenu Québec refuse les lectures automatisées (HTTP 403), et la publication du ministère des Finances est un PDF
chiffré. Les valeurs retenues viennent donc de sources qui se recoupent :

| Paramètre 2026 | Valeur | Source |
|---|---|---|
| RRQ : taux de base + supplémentaire 1 (employé = employeur) | 6,30 % (5,30 + 1,00) | RCGT, nouvelle fiscale 930R4 (févr. 2026) ; ARC T4032-QC (exemples) |
| RRQ : exemption · MGA · plafond supplémentaire · taux RRQ2 | 3 500 $ · 74 600 $ · 85 000 $ · 4 % | RCGT 930R4 ; ARC T4032-QC |
| AE au Québec : taux employé · maximum assurable · employeur | 1,30 % · 68 900 $ · × 1,4 | RCGT 930R4 ; ARC T4032-QC |
| RQAP : employé · employeur · maximum assurable | 0,430 % · 0,602 % · 103 000 $ | RCGT 930R4 ; ARC T4032-QC |
| FSS (secteur public) | 4,26 % | RCGT 930R4 |
| Normes du travail (CNT) | 0,06 % jusqu'à 103 000 $ | RCGT 930R4 |
| Impôt fédéral : paliers, taux, constantes K | 14 / 20,5 / 26 / 29 / 33 % à 58 523 · 117 045 · 181 440 · 258 482 $ | ARC T4032-QC, tableau 1 |
| Fédéral : montant personnel · montant canadien pour emploi · abattement du Québec | 16 452 $ · 1 501 $ · 16,5 % | ARC T4032-QC |
| Impôt du Québec : paliers et taux | 14 / 19 / 24 / 25,75 % à 54 345 · 108 680 · 132 245 $ | sources secondaires concordantes ; vérifié par l'indexation de 2,05 % (53 255 × 1,0205) |
| Québec : montant personnel de base · déduction pour travailleur | 18 952 $ · 6 % jusqu'à 1 450 $ | idem (18 571 × 1,0205 = 18 952) |

## Options considérées

1. **Un contexte `paie` neuf, réduit à la paie, branché par ports partagés sur les feuilles approuvées** (retenue).
2. **Porter tout le module de Nexu VINA** : environ 300 fichiers, dont une majorité hors sujet (contrats, dépenses,
   projets). C'est précisément le coût que D2 refusait ; et les trois défauts relevés plus haut viendraient avec.
3. **Étendre le contexte `pays`** : son moteur annuel sans cumul ne sait pas appliquer un plafond en cours d'année. Il
   faudrait le réécrire, et cela casserait les comparaisons au centime avec le Node qu'il garantit.
4. **Garder l'export vers la paie de l'organisme (PFT) et ne rien calculer** : c'est la position de D2, refusée par la
   demande.

## Décision

### Le périmètre

| Volet | Ce qui est livré |
|---|---|
| Dossier de rémunération | Par ressource d'effectifs : mode (horaire ou salaire), fréquence (52, 26, 24 ou 12 paies), taux horaire ou salaire annuel, montants personnels déclarés (TP-1015.3-V et TD1, valeurs de base par défaut). |
| Cycle de paie | **Préparer** (période et date de paie) : rassemble les feuilles **approuvées** dont la semaine commence dans la période, calcule un bulletin par personne. **Recalculer** tant que le cycle est préparé. **Traiter** : fige le cycle et passe chaque feuille à PAYEE, dans une seule unité de travail. **Annuler** un cycle préparé. |
| Gains | Heures régulières × taux ; heures supplémentaires × taux × (1 + majoration), 50 % par défaut (Loi sur les normes du travail, art. 55), réglable par organisme. Salarié : salaire annuel ÷ nombre de paies. |
| Retenues | Impôt fédéral (T4127, abattement du Québec), impôt du Québec (TP-1015.F, déduction pour travailleur), RRQ (base + supplémentaire 1, puis RRQ2 entre le MGA et le plafond supplémentaire), AE (taux du Québec), RQAP ; cumuls annuels respectés. |
| Cotisations de l'employeur | RRQ et RRQ2 (à parts égales), AE × 1,4, RQAP, FSS, CNT (plafonnée), CNESST (taux de l'unité de classification de l'organisme, réglable). |
| Remises | Par mois de paie : à Revenu Québec (impôt du Québec, RRQ, RQAP, FSS) et à l'ARC (impôt fédéral, AE), avec l'échéance du 15 du mois suivant (remettant mensuel régulier). La CNT, annuelle, est présentée à part. |
| Relevés annuels | Par personne : cases du RL-1 (A, B, C, E, G, H, I) et du T4 (14, 17, 18, 22, 24, 26, 55, 56). La part RRQ2 est donnée à part, sans numéro de case : son report exact se vérifie au guide RL-1-G de l'année. |
| Bulletins | Chacun lit les siens (« Mes bulletins de paie ») ; le gestionnaire de paie lit tous ceux d'un cycle. |

### Les heures supplémentaires

Une personne peut déclarer elle-même des heures supplémentaires sur sa feuille (un segment d'un type d'heure de
comportement SUPPLEMENTAIRE), et les seuils de l'organisme (jour, semaine) en font naître d'autres. Les deux se
rencontrent par une seule règle : **les heures supplémentaires d'un jour valent le plus grand de « déclarées » et
« excédent des seuils »**. Une heure déclarée l'est même sous le seuil ; au-dessus, l'excédent la contient déjà. Le lot
de paie ne reclasse en supplémentaires que l'excédent **net** des heures déclarées. Avant cette règle, 8 h régulières et
2 h déclarées se payaient 4 h majorées au lieu de 2. La paie reconnaît comme supplémentaires les codes de **tous** les
types de ce comportement, et les paie au taux majoré du dossier.

### Les règles d'architecture

- **Le calcul est un domaine pur.** `CalculRetenues` ne lit ni horloge ni base. Il reçoit le brut, la fréquence, le barème
  et les cumuls, et rend les retenues. Les montants sont des `BigDecimal` arrondis au cent, **par retenue**, comme le
  font les tables officielles ; l'AE de l'employeur se calcule sur la base assurable, et non sur la cotisation arrondie
  de l'employé.
- **Le cumul annuel se dérive, il ne se stocke pas.** C'est la somme des bulletins des cycles **traités** de l'année civile
  de la date de paie. Un cycle annulé ou seulement préparé n'y entre pas, et il n'y a rien à « défaire ».
- **Le barème est une donnée de configuration, pas du code.** Un fichier par année (`paie/bareme-AAAA.json`) est lu par un
  adaptateur derrière le port `Baremes`. Une date de paie sans barème est refusée par un message qui nomme l'année
  manquante. Mettre la paie à jour au 1er janvier, c'est ajouter un fichier.
- **Les effectifs restent maîtres du temps.** La paie lit les heures par `HeuresApprouveesPort` (feuilles APPROUVEE d'une
  période, lignes déjà codées et reclassées en heures supplémentaires par `TempsEffectifs.lignesDePaie`). Elle fige les
  feuilles par `marquerPayees`, qui refuse toute feuille qui n'est plus approuvée. Une feuille rappelée ou rouverte entre
  la préparation et le traitement fait donc échouer le traitement entier (409), avec l'invitation à recalculer.
  Les deux ports vivent dans `partage/paie`, car `paie` dépend d'`effectifs` par eux et non l'inverse.
- **Une unité de travail.** Le traitement (bulletins figés, feuilles payées, cycle traité) passe par le port
  `UniteDeTravail` : tout ou rien.
- **Les droits.** Le module `paie` est souscrit par l'organisme. La capacité `paie.gerer` (administration) prépare,
  traite, tient les dossiers et les réglages, et lit les remises et les relevés. Lire ses propres bulletins n'exige que
  le module.

## Conséquences

- **Positives** : la règle du responsable est tenue par construction (seules les feuilles approuvées entrent ; une feuille
  payée ne se rappelle ni ne se rouvre) ; l'ajout d'une année ne touche pas au code ; le calcul se prouve par des essais
  de domaine sans base.
- **Négatives** :
  - le produit porte désormais une responsabilité fiscale. Avant toute production, chaque barème doit être **validé
    contre WinRAS** (Revenu Québec) et le calculateur en ligne de l'ARC : 10 cas par fréquence, écart toléré de 0,02 $
    par retenue ;
  - la méthode est celle de l'annualisation sans les éléments rares (commissions, paies rétroactives, paiements
    forfaitaires, avantages imposables, REER retenu à la source, choix de cesser de cotiser au RRQ à 65 ans).
- **Hors périmètre, à décider plus tard** :
  - la transmission électronique des relevés (XML du RL-1, XML du T4) et du NAS, qui exige le chiffrement au repos
    d'un renseignement personnel sensible (Loi 25) ;
  - le dépôt direct (fichier bancaire) ;
  - le formulaire PDF du bulletin ;
  - la ventilation comptable.
- **Essais** : `CalculRetenuesTest` (cas de référence calculés à la main pour chaque retenue, plafonds atteints en cours
  d'année) ; `CyclesPaieTest` (préparer, recalculer, traiter, annuler, conflit d'une feuille rappelée) ; essais HTTP du
  contrôleur ; parcours e2e : approuver une feuille, la payer, constater qu'on ne peut plus la rappeler.
- `SchemaLiquibaseTest` ne vérifie plus l'absence de `varpay_pay_rules` au nom de D2 : la table de Nexu VINA reste retirée,
  et les tables `paie_*` du nouveau contexte sont vérifiées à sa place.
