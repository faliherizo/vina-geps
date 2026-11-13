# 26-015-DO — Questions à poser à Urgences-santé

Trois questionnaires, un par nature de question. Chaque question porte un identifiant stable, la
source exacte dans les documents d'appel d'offres, **notre hypothèse actuelle** (ce que le produit
fait aujourd'hui) et **l'effet d'une réponse différente** — pour que l'échange produise une décision,
pas une conversation.

| Fichier | Objet | Nombre |
|---|---|---:|
| [01-VALIDATION-SOLUTION.md](01-VALIDATION-SOLUTION.md) | Faire confirmer que ce que nous avons bâti correspond à l'intention d'Urgences-santé, et remplacer les seuils d'essai par ceux de la convention | 54 |
| [02-EXIGENCES-A-CLARIFIER.md](02-EXIGENCES-A-CLARIFIER.md) | Exigences ambiguës, contradictoires ou dont le détail manque (règles, intégrations, technique, hébergement) | 50 |
| [03-CONTRAT-ET-REGIE.md](03-CONTRAT-ET-REGIE.md) | Clauses contractuelles, admissibilité, renvois erronés, engagements hors code | 33 |
| [04-DONNEES-A-OBTENIR.md](04-DONNEES-A-OBTENIR.md) | Les données que seul le client possède : structure, personnes, codes, règles de convention, interfaces | 28 |
| [05-QUESTIONS-RECADREES.md](05-QUESTIONS-RECADREES.md) | **À jour au 10 nov. 2026** : la réponse d'Urgences-santé (configuration renvoyée à plus tard, références = devis + deux grilles) et les seules questions qui restent, neutres | 9 |
| [07-ADDENDA-06-ET-QUESTIONS-METIER.md](07-ADDENDA-06-ET-QUESTIONS-METIER.md) | **À jour au 12 nov. 2026** : l'addenda n° 06 traité réponse par réponse, les ajustements de la solution qu'il impose, et 16 nouvelles questions métier, neutres | 16 |

## Quand les poser — le calendrier a tourné

| Moment | Ce qui est encore possible | Questions visées |
|---|---|---|
| **Avant le dépôt** (clause 1.04.01 : par courriel au gestionnaire du dossier, au plus tard 5 jours ouvrables avant le **3 novembre 2026, 14 h 00 59**) | **Échu.** Une question sur une ambiguïté non posée ne peut plus être invoquée (1.04.02 « présomption »). | Celles marquées **[DÉPÔT]** — à n'utiliser que si un addenda reporte la date (1.05.02, 1.07.17) |
| **Démonstration / essais de conformité** (Régie 1.16.04 b et c, convocation 5 jours avant) | Montrer la solution et faire constater la conformité ; on peut y demander l'interprétation attendue d'une exigence. | **[DÉMO]** |
| **Rencontre de démarrage** (Contrat 2.01.01, dans les 15 jours ouvrables de l'adjudication) | Calendrier de livraison, plan de relève, données à charger. | **[DÉMARRAGE]** |
| **Ateliers de paramétrage** (Contrat 2.07, rencontres hebdomadaires 2.01.02) | Règles de convention, formats d'interfaces, profils. | **[ATELIER]** |

> Règle : une réponse obtenue verbalement ne vaut rien contractuellement (Contrat 0.05.03, 19.05).
> Toute réponse qui modifie une exigence ou un prix doit être **consignée par écrit** — addenda avant
> l'ouverture, avenant signé après.

## Comment lire « notre hypothèse »

Les hypothèses décrivent le comportement **livré et éprouvé** du contexte `effectifs` (lots R0–R13,
voir [05-ROADMAP-IMPLEMENTATION.md](../05-ROADMAP-IMPLEMENTATION.md)) ou une décision consignée
(D-1 à D-13). Les questions Q-1 à Q-13 de [01-EXIGENCES-SOUMISSIONNAIRES.md](../01-EXIGENCES-SOUMISSIONNAIRES.md)
sont reprises ici sous leur nouvel identifiant ; la correspondance est indiquée à chaque fois.
