# Livrable — Appel d'offres 26-015-DO

## Contenu

- `architecture/01-analyse-affaires-et-soumissionnaire.md` — contexte, besoins, obligations du soumissionnaire/fournisseur, points de conformité et de risque.
- `architecture/02-architecture-solution.md` — architecture de solution cible, intégrations, sécurité, résilience, projet et décisions ouvertes.
- `architecture/03-architecture-logicielle.md` — architecture logicielle logique, modules, API candidates, événements, cohérence, tests et NFR.
- `architecture/04-referentiel-exigences.md` — tableau de toutes les exigences E-* et P-* reprises de la grille de conformité.
- `maquettes/03-analyse-fonctionnelle-et-maquettes.md` — analyse exigence par exigence, parcours, traçabilité et catalogue des écrans.
- `maquettes/svg/` — 12 maquettes vectorielles éditables.
- `maquettes/png/` — rendus PNG correspondants.

### Exploitation, sécurité et formation (exigences technologiques)

- `GUIDE-ADMINISTRATION-EFFECTIFS.md` — guide d'administration consolidé (E-TN-27).
- `PROCEDURE-ESSAIS-ACCEPTATION.md` — procédure d'essais d'acceptation et cahier de 90 cas, généré par `construire-procedure-acceptation.py` (E-TN-12).
- `TROUSSE-FORMATION.md` — modules et capsules de formation, éditables (E-TN-26).
- `CALENDRIER-EVOLUTION-ET-MISES-A-JOUR.md` — calendrier d'évolution, circuit des correctifs, mises à jour législatives et de cybersécurité, retour arrière (E-TN-3, E-TN-14, E-TN-16).
- `COPIES-DE-SECURITE.md` — sujets, fréquences, conservation et essais des copies de sécurité (E-TN-15).
- `SECURITE-DES-TRANSMISSIONS.md` — matrice de sécurité par type de transmission (E-TN-8).
- `PARAMETRAGE-SECURITE-ET-RAPPORTS.md` — paramétrage de sécurité, rapport mensuel, protection des journaux (E-TN-31).

## Utilisation

Ouvrir les fichiers Markdown dans VS Code, GitHub, GitLab ou tout visualiseur Markdown. Les liens d'images du document de maquettes sont relatifs au dossier `maquettes/`.

## Limites et décisions

Les maquettes sont des propositions UX/UI, avec données fictives. Les interfaces réelles des systèmes RAO, SAAV, PFT et BD Corpo ne sont pas décrites en détail dans le PDF. Les annexes Excel citées mais non jointes ne sont pas reconstituées. Les incohérences documentaires sont signalées et doivent être clarifiées auprès de l'Organisme public.
