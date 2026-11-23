# FX-AO-20260810 : validation du dossier d'analyse et de conception

> Écrit pour l'équipe qui prépare la soumission à Santé Québec Laurentides (« Solution logicielle de
> gestion et de distribution des communications cliniques ») et pour l'architecte de VINA GePS. Le
> document relit chaque pièce du dossier existant contre les deux sources (devis et bordereau), dit
> ce qui tient, ce qui doit être corrigé, et ce qui manque.

Validé le 14 novembre 2026, contre :
- `00_sources/01_devis_descriptif_converti.md` ;
- `00_sources/02_bordereau_grille_converti.md`, soit la grille de conformité 1 à 115 et le bordereau de prix.

## 1. Verdict d'ensemble

Le dossier est **fidèle aux sources** sur le fonctionnel : les 32 critères fonctionnels (EF-001 à
EF-032) reprennent un à un les critères 1 à 32 de la grille, sans garantie inventée. Ses
questions au client (GAP-01 à GAP-18) sont pertinentes.

Il a cependant trois faiblesses de fond :

1. **Il conçoit une plateforme neuve** (« Java 21, PostgreSQL, RabbitMQ », phases P1 à P3 de
   fondations) sans tenir compte de ce qui existe déjà. Sur les 32 critères fonctionnels, VINA GePS en sert déjà
   **13 entièrement et 12 en partie**, en s'appuyant sur :
   - messagerie bidirectionnelle avec statuts envoyé, reçu et lu ;
   - groupes et listes de diffusion ;
   - SMS, courriel et poussée ;
   - appel sortant au numéro de l'organisme ;
   - Entra ID ;
   - journal et rétention ;
   - modules activables par organisme.
   La feuille de route compte donc comme à bâtir des phases entières qui existent (fondations, communication de base). Voir
   [03_ecarts_fonctionnels.md](03_ecarts_fonctionnels.md).
2. **Aucune réponse critère par critère.** La matrice relie des critères à des besoins, mais
   aucune ligne ne dit, pour chacun des 115, si la solution le satisfait, avec quelle preuve et
   quel document joindre. C'est pourtant ce que la grille exige (colonnes « Réponse » et
   « Documents requis »). Voir [02_conformite_115_criteres.md](02_conformite_115_criteres.md).
3. **Plusieurs critères techniques et contractuels manquent ou sont mal classés** (section 3).

## 2. Pièce par pièce

| Pièce | Constat | À faire |
|---|---|---|
| `01_analyse/01_synthese_besoins_metier.md` | Juste. Six sites, déploiement progressif, deux pilotes formateurs. | Ajouter le volume : 1 500 utilisateurs planifiés au bordereau, 2 000 en capacité à la grille. |
| `01_analyse/02_exigences_fonctionnelles.md` | EF-001 à EF-032 fidèles. EF-036 (rapports, critère 111) et EF-039 (registre d'incidents, critère 110) sont des **obligations du fournisseur**, pas des fonctions du logiciel. | Les reclasser dans `03_exigences_fournisseur.md`. Ajouter EF-041 (export PDF/A, critère 39), EF-042 (copie dans Outlook 365, critère 40), EF-043 (politique de mot de passe, critères 85 et 86). |
| `01_analyse/03_exigences_fournisseur.md` | Couvre le soutien et la gestion de projet. | Ajouter le critère 94 (cinq ans en milieu hospitalier ou démonstration équivalente), qui est **éliminatoire en pratique**. Préparer la preuve : Urgences-santé et Santé Québec, AO 2027-005. |
| `01_analyse/04_exigences_non_fonctionnelles.md` | Correct sur la sécurité. | Chiffrer ce que la grille ne chiffre pas (GAP-05 et GAP-06) avec **nos** engagements : 99,7 % (repris de 26-015-DO), RPO de 24 h, RTO de 4 h. |
| `01_analyse/05_matrice_tracabilite_et_ecarts.md` | GAP-01 à GAP-18 pertinents. | GAP-04 : ajouter que le critère 95 (2 000 utilisateurs) est démontrable par notre campagne JMeter. GAP-17 : la règle de facturation demande un **compteur dédupliqué par mois** (story ST-G08). |
| `02_architecture/01_architecture_solution.md` | Découpage logique sain. | La remplacer par l'architecture réelle (VINA GePS, un contexte « alertes » de plus). Voir [04_architecture_solution.md](04_architecture_solution.md). |
| `02_architecture/02_diagrammes_C1_C4.md` | Génériques. | Les redessiner sur les conteneurs réels : nginx, Java, base de données, volume, fournisseurs SMS, voix et courriel. |
| `02_architecture/03_BPMN_2_0_processus.md` | Les processus d'envoi, de relance et de cascade sont bons. | À garder : ils deviennent les machines à états du contexte `alertes`. |
| `02_architecture/04_MCD_modele_donnees_dynamique.md` | Bon (snapshot des destinataires, versions de politiques). | Fusionner avec le schéma existant : `msg_*` pour les fils, et des tables `alr_*` nouvelles pour les alertes. |
| `02_architecture/07_ADR.md` | ADR-003 (snapshot), ADR-004 (orchestration durable) et ADR-007 (contenu, métadonnées et audit séparés) sont justes. | ADR-001 : un **monolithe modulaire existe déjà**, c'est la décision. ADR-005 : un transport asynchrone (RabbitMQ) n'est pas nécessaire à 2 000 utilisateurs ; la boîte d'envoi transactionnelle déjà en place suffit. |
| `03_roadmap_stories/*` | 26 stories, bien découpées. | Réduire les phases P1 et P2, qui existent déjà. Voir [05_roadmap_et_stories.md](05_roadmap_et_stories.md). |
| `04_questions_client` | Bonnes questions. | Ajouter Q-TEL (fournisseur voix), Q-ALC (alertes critiques iOS : *Critical Alerts* exige une autorisation d'Apple) et Q-DOS (critère 2, « note au dossier » : quel dossier ?). |
| `05_maquettes` | Six planches. | Compléter : réponse du destinataire sur mobile, modèles et réponses, relances, calendrier de disponibilité, modules par organisme. Voir [06_maquettes_ecrans.md](06_maquettes_ecrans.md). |
| `06_gouvernance` | Correct. | Ajouter le risque « téléphonie » (coût à l'appel, fournisseur canadien) au registre. |

## 3. Critères absents de l'analyse ou mal classés

| Critère | Texte (résumé) | Manque dans le dossier |
|---|---|---|
| 2 | Note au dossier des destinataires | Interprétée comme une intégration clinique. Il peut s'agir d'une **note interne sur la fiche du destinataire** (ex. « préfère le texto après 22 h »). À trancher (Q-DOS). |
| 39 | Extraction PDF conforme PDF/A | Absent. Nos exports PDF ne sont pas encore PDF/A : c'est un écart. |
| 40 | Utiliser Outlook 365 | Interprété comme une copie par courriel (critère 8). Il faut aussi une **messagerie Microsoft 365 en envoi** (relais SMTP authentifié ou Graph). |
| 84 | Pas de certificat wildcard | Absent. Il contredit le déploiement par sous-domaine (`*.vina…`). Il faut un certificat nominatif par hôte. |
| 85-86 | Mot de passe : 8 caractères et 4 classes ; pas de réutilisation des 10 derniers pendant 6 mois | Absent. Nous imposons 12 caractères et 4 classes, ce qui est plus strict et conforme. L'**historique des 10 derniers** n'existe pas : c'est un écart. |
| 94 | Cinq ans en milieu hospitalier, ou démonstration équivalente | Non préparé. Voie « démonstration équivalente » : Urgences-santé (26-015-DO) et le module Santé Québec. |
| 101 | Assistance 24/7 en français pour les incidents critiques | Non chiffré. Notre engagement actuel (26-015-DO) est de 5 h à 23 h, 7 j sur 7 : c'est un **écart contractuel** à couvrir par une astreinte. |

## 4. Recommandation

Répondre avec **VINA GePS et un module « Communications cliniques » activable par organisme**,
plutôt qu'avec un nouveau produit. Les motifs, les options écartées et les conséquences sont dans
[04_architecture_solution.md](04_architecture_solution.md) (ADR 0074, proposé).
