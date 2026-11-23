# FX-AO-20260810 — Les fonctionnalités, validées sur la démonstration (23 novembre 2026)

> Écrit pour l'équipe de soumission. La question posée : **groupes et garde, disponibilité, et le reste — tout est-il
> implanté ?** La [revue du 22 novembre](10_validation_exigences_cloisonnement_2026-11-22.md) avait vérifié que chaque
> critère a son écran et ses commandes. Celle-ci juge chaque **fonctionnalité** sur ses valeurs, profil par profil, sur
> l'organisme que l'on montre au client (`https://clincom.geps.vinaitsolution.com`).
>
> Les menus et les écrans : [05_maquettes/07_maquette_menus_fonctionnalites.md](../05_maquettes/07_maquette_menus_fonctionnalites.md).
> Le jeu de données : `e2e/donnees/fx-ao/fonctionnalites.json`. L'essai : `e2e/tests/e2e/deploiement-fx-ao-fonctionnalites.spec.ts`,
> joué par la recette après chaque déploiement.

## 1. Le verdict

**Le code couvre les 32 critères fonctionnels**, avec deux réserves déjà connues : le critère 18 (alerte critique qui
passe outre le mode silencieux : droit *Critical Alerts* d'Apple à obtenir, Q-ALC) et le critère 2 (si l'organisme
entend « note au dossier patient », Q-DOS ; l'addenda 1 l'exclut, nous livrons la note sur le destinataire).

La revue a trouvé **quatre écarts**, tous corrigés dans ce changement :

| # | Écart | Gravité | Correctif |
|---|---|---|---|
| 1 | **Critère 3** : la console ne cherchait pas un destinataire par son nom. On choisissait un groupe, puis on parcourait ses membres — 500 cases pour Saint-Jérôme. La seule recherche par nom était réservée au pilote (403 pour un gestionnaire). | Critère non tenu | `GET /api/alertes/personnes?q=` (dès 3 caractères, 20 noms au plus, nom et identifiant seulement, réservé à qui envoie) et le champ « Chercher une personne » de la console |
| 2 | **L'expéditeur** (rôle propre, ADR 0085) ne voyait au menu ni « Mes alertes », ni « Mon compte », ni « Guide », ni « Soutien ». Le serveur les lui sert ; il reçoit les alertes de son site, mais ne pouvait ni les lire ni régler ses préférences (critère 22). La **direction** n'avait pas « Mon compte ». | Profil de l'addenda 2 amputé | L'espace personnel s'ouvre à tout compte d'organisme (`droitsDeMenu.ts`) |
| 3 | **Disponibilité** : le calendrier interne de la démonstration était vide. La cascade « prochain disponible » (critère 25) n'avait rien à montrer : tout le monde était disponible. | Démonstration muette | Le semis déclare le congé du Dr Lavoie, par lui, accepté ; la cascade le saute |
| 4 | **Clincom semé à moitié** : le semis du 23 novembre, 02 h 13, a été interrompu (travail annulé). L'organisme montré au client n'avait **ni modèles, ni réponses, ni politique de cascade, ni copie par courriel**, et deux groupes sur trois. Les semis suivants le croyaient complet (« déjà semé »), et l'essai de présence passait. | Démonstration défaillante | Un organisme naît marqué « [semis en cours] » et ne perd la marque qu'au dernier geste ; l'orchestrateur ignore un exemplaire marqué. Le nouvel essai échoue sur un organisme incomplet en nommant ce qui manque |

## 2. Groupes, garde et disponibilité — comment cela fonctionne

| Besoin | Dans VINA GePS | Preuve |
|---|---|---|
| Groupes configurables (EF-005, EF-027) | Les **sites** sont des groupes d'office (unités en vigueur) ; les **groupes transverses** s'y ajoutent, de nature `garde`, `comite` ou `specialite` | CAP-01, CAP-04 |
| Garde (EF-016, addenda 2, Q4) | Un groupe de garde a des appartenances **datées** (`depuis`, `jusqua`) et des **modérateurs** ; la relève s'inscrit d'avance | CAP-04 : « Garde de cardiologie », Dre Tremblay modératrice |
| Seul le pilote gère (addenda 2, Q22) | Les groupes transverses et les cascades exigent l'administration de l'organisme ; un gestionnaire reçoit 403 | CAP-04 |
| L'expéditeur vise la garde | `GET /api/alertes/groupes/{id}/membres` lui rend les membres, sans pouvoir les modifier | CAP-04 |
| Calendrier interne (EF-025) | Indisponible le jour où une absence **acceptée** couvre la personne, ou celui où elle s'est déclarée indisponible (`DisponibiliteDepuisEffectifs`) | CAP-05 |
| Ne pas viser un indisponible (synthèse § 4) | L'étape « prochain disponible » prend le premier membre du groupe ni déjà visé, ni indisponible ce jour-là (`GestionnaireCascades.prochainDisponible`) | CAP-06 : la trace de cascade ne nomme jamais le Dr Lavoie |
| Remplacement | La note sur le destinataire le dit à l'expéditeur (« joindre la Dre Tremblay ») ; le comblement d'une absence relève du module d'absences | CAP-09, CAP-05 |

## 3. Les 40 exigences fonctionnelles

| EF | Exigence | État | Capacité jugée |
|---|---|---|---|
| EF-001 | Console Web centralisée | ✅ | CAP-01, CAP-17 |
| EF-002 | Note au dossier | ✅ note sur le destinataire ; 🟡 si dossier patient (Q-DOS) | CAP-09 |
| EF-003 | Recherche par nom et prénom | ✅ **corrigé** | CAP-02 |
| EF-004 | Messagerie bidirectionnelle | ✅ | CAP-15 |
| EF-005 | Une personne, plusieurs, un groupe | ✅ | CAP-01 |
| EF-006 | Messages prédéfinis | ✅ | CAP-03 |
| EF-007 | Alertes en temps réel | ✅ | CAP-01 |
| EF-008 | Copie courriel | ✅ | CAP-10 |
| EF-009 | Confirmation reçue, lue, prise en charge | ✅ | CAP-07, CAP-08 |
| EF-010 | Réponses prédéfinies | ✅ | CAP-03 |
| EF-011 | Suivi des statuts | ✅ | CAP-07 |
| EF-012 | Historique partagé | ✅ | CAP-07 (l'expéditeur voit l'alerte de la coordonnatrice) |
| EF-013 | Confirmation du retour d'appel | ✅ | CAP-07 (prise en charge nommée) |
| EF-014 | Appel automatique | ✅ | CAP-06 (étape 3 : appel vocal) |
| EF-015 | Relances automatiques | ✅ | CAP-06 |
| EF-016 | Cascades entre membres ou groupes | ✅ | CAP-04, CAP-06 |
| EF-017 | Délais, séquences, arrêt, reprise | ✅ | CAP-06 |
| EF-018 | Alertes critiques | 🟡 réservée et multicanal ; *Critical Alerts* iOS à obtenir (Q-ALC) | Profils (droit `critique`) |
| EF-019 | Historique d'escalade | ✅ | CAP-06 (trace de cascade) |
| EF-020 | Accès par navigateur mobile | ✅ | CAP-08 (Mes alertes à 390 px) |
| EF-021 | Application iOS et Android | ✅ abonnement des appareils ; publication dans les magasins hors démonstration | `fx-ao-mobile.spec.ts` |
| EF-022 | Préférences sans contourner les règles | ✅ | CAP-11 |
| EF-023 | Interface clinique | ✅ | CAP-08 |
| EF-024 | Historique personnel | ✅ | CAP-08 |
| EF-025 | Calendrier de disponibilité | ✅ **démonstration complétée** | CAP-05, CAP-06 |
| EF-026 | Console d'administration | ✅ | CAP-13 |
| EF-027 | Utilisateurs et groupes | ✅ | CAP-04, CAP-13, CAP-14 |
| EF-028 | Cloisonnement | ✅ | CAP-04 ; `fx-ao-cloisonnement.spec.ts` |
| EF-029 | Coordonnées protégées | ✅ | CAP-02, CAP-15 |
| EF-030 | Profils d'accès | ✅ **corrigé** (menu de l'expéditeur) | Profils, CAP-12 |
| EF-031 | Vue des messages et statuts | ✅ | CAP-07 |
| EF-032 | Configuration fonctionnelle | ✅ | CAP-03, CAP-04, CAP-06 |
| EF-033 | Gestion des identités | ✅ | CAP-12, CAP-13 |
| EF-034 | Journalisation métier | ✅ | CAP-18 |
| EF-035 | Consultation et export d'audit | ✅ | CAP-18 ; export du suivi |
| EF-036 | Rapports opérationnels | ✅ module Soutien (rapports périodiques, critère 111) | Hors de ce jeu |
| EF-037 | Import initial | ✅ intégrations d'effectifs (masquées dans la démonstration) | Hors de ce jeu |
| EF-038 | Déploiement site par site | ✅ six sites en référentiel | CAP-14 |
| EF-039 | Soutien | ✅ | CAP-18 |
| EF-040 | Facturation des utilisateurs actifs | ✅ minimum de 100 | CAP-16 |

## 4. Les gestes, posés à l'écran sur la démonstration

`e2e/tests/e2e/deploiement-fx-ao-parcours.spec.ts`, piloté par `e2e/donnees/fx-ao/parcours-demonstration.json`,
**écrit** dans Clincom avec les comptes de démonstration, chacun dans sa session, et vérifie que le geste de l'un
arrive chez l'autre. Il s'y tient propre : marque « Exercice e2e » et heure dans chaque texte, absence annulée à la
fin, dépôt de disponibilité remplacé, fil de groupe repris. Il ne s'exécute qu'avec `E2E_PARCOURS_DEMO=1` et n'est
pas dans la recette automatique.

| Geste | Comptes | Ce qui est constaté |
|---|---|---|
| Déclarer une absence | `inf.cote`, puis `admin` | « Absence déclarée » ; l'administration la lit **acceptée** au nom de Marc Côté ; annulée ensuite |
| Déposer une disponibilité | `inf.roy` | Période « Collecte — Hôpital de Saint-Jérôme » ouverte ; « Dépôt enregistré », sans refus |
| Alerte à un groupe et à une personne | `chef.urgence.hsj`, `dre.tremblay`, `inf.roy` | Modèle « Rappel de garde », garde de cardiologie cochée, Julie Roy **cherchée par son nom** ; envoyée à 3 personnes ; la Dre Tremblay prend en charge d'un geste ; Julie Roy la lit ; le suivi nomme la Dre Tremblay |
| Écrire à une personne | `inf.roy` → `dre.tremblay` | Le message arrive dans leur fil à deux |
| Écrire à un groupe | `inf.roy` → `dre.tremblay`, `chef.urgence.hsj` | Fil de groupe ouvert (ou repris), participants ajoutés ; le chef y lit le message |

Chaque état d'écran passe axe-core sans violation.

**Constat d'ergonomie, corrigé** : la messagerie s'ouvrait sur la **première unité** de la liste (Argenteuil), et
non sur celle de la personne : une infirmière de Saint-Jérôme y trouvait « Aucun membre dans ce groupe ». Elle s'ouvre
désormais sur l'unité de la personne (`GET /api/communication/groupes/miens`, `groupeParDefaut` dans
`santequebec/perimetre.tsx`) ; le choix fait dans la session reste prioritaire. Le parcours le vérifie.

## 5. Résultats sur la démonstration (23 novembre 2026, soir)

Après le déploiement de `develop` (`118885e`), le nouveau semis de Clincom (exemplaire `e7b1eee6`, complet) et la
suspension de l'exemplaire semé à moitié (`b79004a4`) :

| Essai | Résultat |
|---|---|
| `deploiement-fx-ao-fonctionnalites.spec.ts` — 6 profils, 18 capacités (lecture) | **24 / 24** |
| `deploiement-fx-ao.spec.ts` — modules, menu, écrans sans violation axe-core, 30 critères | **3 / 3** |
| `deploiement-fx-ao-parcours.spec.ts` — absence, disponibilité, alerte, messages (écriture) | **5 / 5** |

Avant le nouveau semis, la recette du déploiement échouait sur CAP-01 (« Inhalothérapie régionale » et la liste
« Soins infirmiers — urgences » introuvables) : c'est l'écart n° 4, que l'essai de présence laissait passer.
