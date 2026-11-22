# 26-015-DO — Scénarios de validation des stories AD

> Chaque scénario ci-dessous est **joué** : en Gherkin dans `backend-java/src/bdd/resources/features/` (contre le
> serveur Java réel, base Liquibase vierge, un organisme neuf par scénario), ou en essai Java quand il faut une
> horloge figée. Le fichier `.feature` fait foi pour les valeurs ; ce document dit **ce que chaque scénario prouve** et
> pourquoi l'attendu ne passerait pas sans la fonction.

## Conventions

- Acteurs de l'amorce : `admin`, `chef`, `julie` (`{{P1}}`), `marc` (`{{P2}}`), milieu `{{c12}}` (README de la suite).
- Dates de 2030 et après : rien ne dépend de l'instant du passage.
- Un attendu nomme une **valeur** (statut, motif, compte), jamais une simple présence.

## Par story

| Story | Exigences | Scénarios |
|---|---|---|
| AD-01 Heures sur 24 h | E-GR-7 | [05_scenarios/AD-01.md](05_scenarios/AD-01.md) |
| AD-02 Seuil par code d'absence | E-CH-6, E-CH-7, P-CH-8, E-GR-4 | [05_scenarios/AD-02.md](05_scenarios/AD-02.md) |
| AD-03 Heures supplémentaires par période de paie et par convention | E-RF-5 | [05_scenarios/AD-03.md](05_scenarios/AD-03.md) |
| AD-04 Optimisation du budget AGPH | E-CH-1 | [05_scenarios/AD-04.md](05_scenarios/AD-04.md) |
| AD-05 Renoncer ; délai échu paramétrable | P-CH-2, P-CH-4, P-CH-7 | [05_scenarios/AD-05.md](05_scenarios/AD-05.md) |
| AD-07 Offre par ancienneté et à tour de rôle | P-GH-3, P-GN-1 | [05_scenarios/AD-07.md](05_scenarios/AD-07.md) |
| AD-08 Paramétrage de démonstration de la convention SP-CSN | E-GN-12, E-GR-7, E-GR-12, E-RF-5, E-CH-6 | [05_scenarios/AD-08.md](05_scenarios/AD-08.md) (scénarios e2e pilotés par `convention-sp-csn.json`) |

Un lien sans fichier signale une story pas encore livrée (voir le [planificateur](03_planificateur.md)).
