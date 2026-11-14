# Validation du dossier FX-AO-20260810 contre VINA GEPS

> Écrit pour l'équipe de soumission et l'architecte de VINA GEPS. Ces pièces relisent le dossier
> d'analyse contre les sources et contre ce que VINA GEPS fait déjà. Le **socle** (phase R1) est implanté
> le 14 novembre 2026 (ADR 0074, accepté pour R1) ; les phases suivantes attendent les réponses du client.

| Pièce | Contenu |
|---|---|
| [01_validation_du_dossier.md](01_validation_du_dossier.md) | Verdict pièce par pièce, critères manquants ou mal classés |
| [02_conformite_115_criteres.md](02_conformite_115_criteres.md) | Les 115 critères : statut, preuve dans VINA GEPS, écart et story |
| [03_ecarts_fonctionnels.md](03_ecarts_fonctionnels.md) | Ce qui existe, ce qui manque (ECF-01 à ECF-13), tailles |
| [04_architecture_solution.md](04_architecture_solution.md) | ADR 0074 proposé : un contexte `alertes` dans VINA GEPS, modules par organisme, ports, options écartées |
| [05_roadmap_et_stories.md](05_roadmap_et_stories.md) | Feuille de route R0-R7, stories ST-G01 à ST-G13 avec critères d'acceptation |
| [07_analyse_metier_socle_alertes.md](07_analyse_metier_socle_alertes.md) | Analyse métier du socle implanté : acteurs, glossaire, règles R1 à R10, cas d'utilisation |
| [08_plan_implantation.md](08_plan_implantation.md) | Plan d'exécution à partir de l'état réel du code : phases R0 à R7, tâches par couche, briques réutilisées, jalons, risques (environ 19 semaines jusqu'au pilote) |
| [06_maquettes_ecrans.md](06_maquettes_ecrans.md) | Maquettes : menu, console d'alerte, suivi, fiche, mobile, modèles, politiques, groupes, superadmin, tableau de bord, rapports |

**En une phrase.** Un module « Communications cliniques » activable par organisme dans VINA GEPS
couvre la grille fonctionnelle avec environ 17 à 22 semaines-personne. Un nouveau projet
recopierait l'identité, les avis, la messagerie, le journal et le superadmin.
