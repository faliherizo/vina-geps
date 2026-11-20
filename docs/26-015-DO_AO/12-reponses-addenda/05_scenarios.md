# 26-015-DO — Scénarios de validation des stories AD

> Chaque scénario ci-dessous est **joué** : en Gherkin dans `backend-java/src/bdd/resources/features/` (contre le
> serveur Java réel, base Liquibase vierge, un organisme neuf par scénario), ou en essai Java quand il faut une
> horloge figée. Le fichier `.feature` fait foi pour les valeurs ; ce document dit **ce que chaque scénario prouve** et
> pourquoi l'attendu ne passerait pas sans la fonction.

## Conventions

- Acteurs de l'amorce : `admin`, `chef`, `julie` (`{{P1}}`), `marc` (`{{P2}}`), milieu `{{c12}}` (README de la suite).
- Dates de 2030 et après : rien ne dépend de l'instant du passage.
- Un attendu nomme une **valeur** (statut, motif, compte), jamais une simple présence.
