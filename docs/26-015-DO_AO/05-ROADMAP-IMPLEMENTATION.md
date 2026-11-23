# 26-015-DO — Feuille de route d'implémentation

> Comment les écarts du document [02](02-BESOINS-ET-ANALYSE-ECARTS.md) entrent dans Nexu VINA, lot
> par lot, **et où on en est**. Ce document est mis à jour à chaque lot livré ; l'état fait foi
> seulement s'il cite des essais qui passent.
>
> Dernière mise à jour : **20 novembre 2026, réponses d'Urgences-santé (addenda 1 à 7)** — cinq écarts réels
> trouvés contre le code, lot **R14** (stories AD-01 à AD-08) : voir [12-reponses-addenda](12-reponses-addenda/03_planificateur.md).
> Mise à jour précédente : 1er novembre 2026, après revérification des jugements contre le code.
> La matrice donne **78 essentiels acquis sur 100**, 17 partiels, **0 à bâtir** — et les 17
> partiels sont **tous du lot U0** (SaaS, haute disponibilité, Entra ID, DDoS, formation), c'est-
> à-dire des engagements d'exploitation et de contrat. **Il ne reste aucun écart de code.**
>
> Les douze « écarts de code » annoncés le matin même étaient des jugements du 30 septembre que le
> produit avait dépassés : les douze sont servis, et un essai de bout en bout les tient désormais
> à l'écran (`e2e/tests/e2e/urgences-sante-ecarts.spec.ts`, piloté par
> `e2e/donnees/urgences-sante/ecarts.json`). Détail et trace de l'erreur :
> [06-MAQUETTES-ET-ECARTS-RESTANTS.md](06-MAQUETTES-ET-ECARTS-RESTANTS.md). La question de la
> feuille de temps partagée reste dans
> [07-FEUILLE-DE-TEMPS-PARTAGEE.md](07-FEUILLE-DE-TEMPS-PARTAGEE.md).

---

## 1. Règles communes à tous les lots

| Règle | Comment elle est vérifiée |
|---|---|
| **Chaque lot est un module du catalogue**, activable et désactivable par établissement depuis *Superadmin › Modules & accès*, avec ses prérequis résolus automatiquement | entrée dans `backend/src/saas/catalogueModules.ts`, routeur gardé par `requireModule(code)` (403 nommé), écran filtré par `PAGE_MODULE` / `module:` du menu ; essai d'intégration « module coupé → 403 » et e2e superadmin |
| **Couverture unitaire ≥ 96 % des lignes**, 100 % sur les fichiers `domaine.ts` | seuils `coverageThreshold` par dossier dans `backend/package.json` : la construction échoue sous le seuil |
| **Essais de bout en bout** sur les fonctions essentielles de chaque lot | `e2e/tests/e2e/effectifs-*.spec.ts`, contre le vrai serveur ; chaque écran inscrit à `e2e/tests/ui/audit-wcag22.spec.ts` |
| **Données paramétrables** : aucune liste métier codée dans un écran ; valeurs datées, jamais rétroactives | référentiels (§ 3 du document 03) ; essai de non-rétroactivité par lot |
| **Traçabilité** de toute action et de toute décision automatique (E-GN-9) | journal `eff_journal`, alimenté par les services, jamais à la main par une route |
| **Accessibilité SGQRI 008 3.0 / WCAG 2.2 AA** | libellés liés, tableaux à `caption`/`scope`, cibles ≥ 24 px, messages `role="status"`/`alert`, contrôle axe-core dans les e2e |
| **Isolation par établissement** | `tenantId` NOT NULL sur toute table, filtré dans toute requête ; essai d'isolation par lot |

## 2. Décisions d'implémentation (écarts assumés par rapport au document 03)

| # | Décision | Pourquoi | Conséquence |
|---|---|---|---|
| D-1 | Un **contexte `effectifs`** neuf porte le cycle de planification d'Urgences-santé (périodes à collecte, équipages, postes-lignes) au lieu d'étendre les tables `horaire_*` | `horaires` (4 717 lignes d'écran, 20 onglets, jumeau Java) n'a ni état de collecte, ni équipage, ni poste-ligne ; y greffer ces invariants ferait porter le risque à trois clients existants. Le contexte neuf s'appuie sur les mêmes principes (minutes depuis minuit, place vacante = `personId` nul, période publiée jamais rouverte) | Deux moteurs de planification coexistent un temps ; leur fusion est un lot à part (hors avis), tracé ici |
| D-2 | Les référentiels partagent **une table** `eff_referentiels` (type, code, version datée) et un **registre de descripteurs** dans le code | un écran générique et une datation uniforme ; l'intégrité (type, code, date) est vérifiée par le service à chaque écriture qui y renvoie | les types et comportements admis restent des ensembles fermés du code (enum) |
| D-3 | Les **unités organisationnelles** d'effectifs sont un référentiel hiérarchique (`unites`, attribut `parent`) | le module `organisation` (AI-2026-03) n'a pas d'écran et sert la finance ; le relier est prévu en R12 | les périmètres se déclarent par unité, avec sous-arbre |
| D-4 | **Node d'abord**, jumeau Java au lot R13 | la parité se mesure (`etat-migration.cjs`) ; elle baissera pendant les lots, et le document le dit | chaque route nouvelle est inscrite à la liste des routes à porter ; seuls les **catalogues** (modules, vues) sont portés en Java au fil des lots, parce que `CatalogueModulesTest` et `CatalogueVuesTest` en exigent la parité |
| D-5 | Les écrans R1 (référentiels, milieux, périmètres, journal) et R2 (règles) partagent **une page** « Paramétrage des effectifs » à onglets, sous Administration | une notion de menu, cinq vues adressables par `?onglet=` ; le document 04 prévoyait une notion « Organisation » à part, fusionnée ici pour ne pas disperser le paramétrage | les onglets se montrent selon le rôle **et** le module (`effectifs_regles`) |
| D-6 | Une autre session travaille dans le même dépôt ; les campagnes complètes (Jest, `mvn verify`) sont **annoncées** et les écritures dans `backend/src` suspendues pendant leur mesure | un chiffre de couverture ou d'instabilité n'est attribuable que si l'arbre ne bouge pas pendant la mesure | un lot peut être rédigé hors de l'arbre, puis posé en une fois |
| D-7 | L'absence d'effectifs (R5) se **déclare par la façade `/api/absences`**, comme une quatrième catégorie, et non par un formulaire propre au module | ADR 0034 : la déclaration d'absence n'a qu'un lieu (Absences › Déclarer) ; ADR 0026 : un formulaire, plusieurs natures. Un second formulaire, c'est deux jeux de règles et deux adresses | la moitié « absences » du lot R12 passe dans R5 ; le contexte `effectifs` porte la décision automatique conservée, l'impact sur le comblement, la dérogation et la vacance des places — l'écran de déclaration reste celui d'Absences |
| D-8 | Les droits des routes d'effectifs sont deux **capacités** (`effectifs.configure`, `effectifs.manage`) de `accessPolicy.ts`, pas des listes de rôles | la porte `rbac-architecture` ne reconnaît que les gardes nommées en ligne ; une capacité se règle par organisme (`canInTenant`), un `requireRole` non | toute route nouvelle d'un lot d'effectifs nomme sa capacité ; une route en libre-service (l'employé agit sur lui-même) s'inscrit comme exemption motivée |
| D-9 | La politique d'échange entre périodes (autorisé / restreint / interdit, E-GR-12) est **une règle ECHANGE** sur le fait `memePeriode` (ACCEPTER / EXIGER_APPROBATION / BLOQUER), pas un réglage à part | un réglage de plus serait une seconde source de vérité ; la règle est déjà datée, versionnée, et portée par l'organisme, l'unité ou la convention — « au choix » de l'AO | aucune table ni écran de politique ; l'onglet Échanges renvoie au paramétrage des règles |
| D-10 | R0 (G5, sécurité) : le périmètre par groupe du module `horaires` s'impose **après une migration** qui donne le rôle de groupe « gestionnaire » aux managers dans les groupes où ils agissent déjà, puis refus par défaut partout (choix de l'utilisateur, 29 sept.) | le refus sec couperait l'accès des managers le jour de la livraison ; un interrupteur par organisme laisserait le défaut de sécurité ouvert | la migration est versionnée et tracée ; l'administrateur voit et corrige les rôles attribués ; Node et Java changent ensemble |
| D-11 | R9 : la feuille de temps d'effectifs est un contexte à part (`effectifs/temps`, module `effectifs_temps`) plutôt qu'une extension des feuilles de la paie proposée au document 03 | le module reste activable et désactivable seul, comme les autres ; la paie existante n'est pas touchée pendant la livraison | le lot de paie codé et signé est la frontière ; le reflet dans `time_entries` de la paie passe au lot R12 (liaisons) |
| D-12 | R12 : l'employé déclare son absence d'effectifs depuis **Mon espace › Mes absences**, qui réutilise le **même** composant de formulaire que la page Absences (mode libre-service) | ouvrir la page Absences aux employés (elle est gouvernée par `absence.manage` et montre l'équipe) ; un formulaire propre au module (contraire à l'ADR 0034) | un seul formulaire, deux lieux d'accès selon le rôle ; la liste de l'employé ne montre que ses absences d'effectifs |
| D-13 | R12 : les feuilles de temps d'effectifs **ne se reflètent pas** dans `time_entries` | `time_entries` est le temps **facturable aux projets** (`projectId` obligatoire) : y verser des quarts créerait de faux projets et fausserait la facturation | la paie des effectifs passe par le lot codé et signé (R9) transmis au PFT (R10) ; le document 03 est corrigé sur ce point |

## 3. Les lots

Chemin critique : **R1 → R2 → R3 → (R4, R5, R6, R7, R8) → R9 → R10**. R0, R11 et R12 sont indépendants.

| Lot | Module (catalogue) | Requiert | Contenu | Exigences essentielles | Préférentielles |
|---|---|---|---|---|---|
| **R0** | *(correctif)* | — | Garde de périmètre sur les routes cœur des horaires existants (écart G5) | E-GN-8 (sécurité) | — |
| **R1** | `effectifs` | — | Référentiels datés et descripteurs, unités et périmètres utilisateur, milieux physiques et preuves exigées, journal unifié filtrable et exportable | E-GN-7, 8, 9, 10, 17 | — |
| **R2** | `effectifs_regles` | `effectifs` | Moteur de règles versionné, catalogue de faits, combinaison par sévérité, motifs traduits, aperçu d'impact, trace des évaluations | E-GN-12, 15, 16 ; E-RF-10 | P-GN-2, P-GH-2 |
| **R3** | `effectifs_planification` | `effectifs_regles` | Périodes à cycle (préparation → collecte → confection → publiée → close), postes-lignes et paramètres, équipages et places par corps d'emploi, minimum opérationnel, quarts ad hoc, mode d'attribution, attribution forcée justifiée, modification en lot, ajustements journaliers, quotas et surcharges non rétroactives, taux de comblement calculé | E-GN-1, 2, 3, 4, 11 ; E-CH-2, 3, 4, 5 ; E-GR-1, 5, 6, 8, 9, 10, 13 | — |
| **R4** | `effectifs_disponibilites` | `effectifs_planification` | Dépôt par période ouverte, validation par règles (bloquer / avertir), motifs, exclusion du bassin, attribution automatique en fin de collecte | E-GN-13, 14 ; E-GR-11 | P-GH-4 |
| **R5** | `effectifs_absences` | `effectifs_planification` | Catégorie « effectifs » de la façade `/api/absences` (D-7) ; codes d'absence par titre d'emploi et durée, décision automatique conservée, dérogation motivée avec code, impact sur le comblement, vacance des places, quarts à offrir, recherche et attribution au prioritaire, avis aux admissibles | E-GN-5, 6 ; E-CH-6 ; E-GR-2, 3, 4 | P-GN-1, 3 ; P-GH-1, 3 ; P-CH-8 |
| **R6** | `effectifs_echanges` | `effectifs_planification` | Échanges validés dans les deux sens (admissibilité, repos, contiguïté), politique inter-périodes autorisé / restreint / interdit | E-GR-7, 12 | — |
| **R7** | `effectifs_agph` | `effectifs_planification` | Campagnes annuelles, rangs d'ancienneté figés, tour de rôle minuté, choix unique contrôlé, attribution automatique à l'expiration, statuts TC / TPR / TP, reliquats vers la gestion régulière, remplacements long terme avec manifestation d'intérêt | E-CH-1, 7, 8, 9, 10 | P-CH-1 à 7, 9 |
| **R8** | `presence` | `effectifs_planification` | Pointage en temps réel par profil, pauses et repas, présence préalable rémunérée, preuves (géorepérage, code QR de lieu, réseau), écarts et action configurable, journal des localisations, segments de temps, saisie a posteriori, sources d'horodatage corrélées | E-GP-1, 2, 4, 7, 9 à 15 ; E-RF-13 | P-GP-1 à 4 |
| **R9** | `effectifs_temps` | `presence` | Feuille générée depuis l'horaire, segments, écart planifié / déclaré, anomalies par règles, heures supplémentaires par seuils, types d'heures, approbation à paliers, verrouillage, historique des corrections, rapports et relevés, export PFT signé | E-GP-3, 5, 6, 8 ; E-RF-1 à 7, 9, 11, 12, 14 | P-RF-1 |
| **R10** | `effectifs_integrations` | `effectifs_temps` | Connecteurs RAO, SAAV, PFT, BD Corpo, Microsoft Graph, ESB, entrepôt : port + simulateur + adaptateur HTTP + suite de contrat, boîte d'envoi transactionnelle | E-TN-7 | P-TN-7 |
| **R11** | `ia`, options d'organisme | — | IA désactivée par défaut par établissement, charte graphique, alertes par courriel, pays autorisés, politique de rétention, niveaux de service par contrat, infrastructure multi-AZ | E-TN-9 (partie code) | P-TN-9, 10, 11 |
| **R12** | *(liaison)* | — | Unités ↔ structure `organisation`, absences d'effectifs ↔ façade `/api/absences` | — | — |
| **R13** | *(parité)* | — | Jumeau Java des routes R1 à R11 | — | — |
| **R14** | *(réponses)* | R3 à R9 | Réponses des addenda 1 à 7 : heures sur 24 h (AD-01), seuil par code d'absence (AD-02), heures supplémentaires par période de paie et par convention (AD-03), optimisation du budget AGPH (AD-04), renonciation et délai échu (AD-05) ; puis API d'échange (AD-06), offre en cascade (AD-07), démonstration SP-CSN (AD-08) | E-GR-7 ; E-CH-1, 6, 7 ; E-RF-5 ; E-TN-7 | P-CH-2, 4, 7, 8 ; P-GH-3 ; P-TN-17 |

## 4. État d'avancement

Légende : ⬜ à faire · 🔄 en cours · ✅ livré (essais verts cités).

| Lot | État | Livré | Essais | Couverture (lignes) |
|---|---|---|---|---|
| R0 | ✅ Node et Java | quatre défauts corrigés ; périmètre par groupe sur 36 routes d'écriture + décisions HS/pointage + échanges + pour autrui ; lectures de gestion bornées ; migration 0125 ; écran Horaires ; jumeau Java (`DepotPerimetreGroupe` + 5 contrôleurs) | Node : `horaires-securite-r0` 4/4, `horaires-perimetre-groupe-r0` 7/7, 670 essais horaires ; Java : 73 essais ciblés verts (dont `ControleurHorairesPerimetreGroupeHttpTest`), `ArchitectureTest`, `NormesJavaTest` | JaCoCo par classe : au `mvn verify` complet |
| R1 | ✅ serveur, écran, e2e | module `effectifs` ; référentiels datés (11 types), unités et périmètres avec garde, milieux et preuves, journal filtrable + CSV ; page « Paramétrage des effectifs » (4 onglets) ; catalogues Node + Java | 51 essais Jest verts ; audit WCAG 2.2 (4 largeurs, 3 états) vert ; scanner statique vert ; e2e `effectifs-socle.spec.ts` écrit, **pas encore exécuté** | **100 %** lignes, 97,4 % branches (`src/effectifs/`) ; seuil 96 % inscrit à la construction |
| R2 | ✅ Node, écran, e2e, Java (R13) | module `effectifs_regles` ; règles versionnées par contexte et portée, sélection « le particulier précise le général », action la plus restrictive, motifs rendus avec les faits, simulateur, décisions tracées ; onglet « Règles » | essais verts **hors de l'arbre** (copie d'essai) : domaine + intégration ; pose en attente de la fin de campagne de l'autre session | domaine **100 %** lignes et branches ; `regles/` 100 % lignes |
| R3 | ✅ Node, écran, e2e, Java (R13) | module `effectifs_planification` ; ressources, périodes à cycle (préparation → collecte → confection → publiée → close), postes à cycle, quarts et équipages, attribution contrôlée par les règles avec forçage justifié, comblement, quotas datés et surcharges | essais verts hors de l'arbre ; e2e `effectifs-planification.spec.ts` écrit | domaine **100 %** ; `planification/` 100 % lignes |
| R4 | ✅ Node, écran, e2e, Java (R13) | module `effectifs_disponibilites` ; dépôt par période soumis aux règles (refus motivé 409, avertissement, approbation), bassin de candidats **avec motifs d'exclusion**, attribution de fin de collecte (préférence → ancienneté → moins d'heures) ; pages « Mes disponibilités » et onglet Planification › Disponibilités | essais verts hors de l'arbre : 135 Jest (R1–R4 + porte RBAC) ; e2e `effectifs-disponibilites.spec.ts` écrit | domaine **100 %** ; `disponibilites/` 99,5 % lignes |
| R5 | ✅ Node, écran, e2e, Java (R13) | module `effectifs_absences` ; catégorie « effectifs » de la façade `/api/absences` (port `SourceAbsences`, D-7), décision automatique **conservée** (refus compris), code admissible par titre d'emploi, impact calculé (places, taux avant → après), dérogation motivée avec code, approbation, annulation, places rendues vacantes par un mécanisme journalisé ; formulaire unique (nature « effectifs ») et onglet Planification › Absences et comblement | essais verts hors de l'arbre : domaine + 8 intégration + essai historique de la façade adapté (4 catégories) ; vitest du formulaire ; e2e `effectifs-absences.spec.ts` écrit | domaine **100 %** ; `absences/` ≥ 99 % lignes |
| R6 | ✅ Node, écran, e2e, Java (R13) | module `effectifs_echanges` ; échange de deux places jugé **dans les deux sens** (repos, semaine, contiguïté), politique entre périodes par règle ECHANGE sur `memePeriode` (D-9), rejugé à l'acceptation, validation / rejet du planificateur, horaire changé détecté ; pages Mon espace › Mes échanges et Planification › Échanges | 14 essais (domaine + intégration) ; e2e `effectifs-echanges.spec.ts` écrit | domaine **100 %** ; `echanges/` 100 % lignes |
| R7 | ✅ Node, écran, e2e, catalogues Java | module `effectifs_agph` ; campagne AGPH (rangs figés par ancienneté, tour de rôle minuté, un seul choix, choix automatique à l'expiration, règles CHOIX_AGPH, clôture : statuts TC / TPR / TP et postes restants vacants) ; remplacements long terme (manifestations, admissibilité INTERET_REMPLACEMENT, attribution au plus ancien sur les quarts vacants du poste, non comblé → transfert automatique) ; pages Mon AGPH et Planification › AGPH | 14 essais Jest (domaine + intégration à horloge fixe) ; e2e `effectifs-agph.spec.ts` vert ; audit WCAG 4 largeurs vert | domaine **100 %** ; `agph/` ≥ 98 % lignes |
| R8 | ✅ Node, écran, e2e, catalogues Java | module `effectifs_presence` ; pointage du quart publié (fenêtre de 2 h, séquence entrée → pauses → sortie), preuves jugées par milieu (distance avec tolérance de précision plafonnée, code QR de lieu à fenêtre de 5 min, réseau), **aucune position conservée**, règles POINTAGE (retard, distance, mécanisme), écart → approbation, présence préalable rémunérée par nature de quart, segments et temps payé calculés, saisie a posteriori justifiée (lot approuvé d'un geste), horodatages externes importés et corroborés (±5 min) ; pages Mon pointage et Planification › Présence | 26 essais Jest (domaine + intégration à horloge fixe) ; e2e `effectifs-presence.spec.ts` vert (géolocalisation réelle du navigateur) ; audit WCAG 4 largeurs vert | domaine **100 %** lignes et branches ; `presence/` **100 %** lignes |
| R9 | ✅ Node, écran, e2e écrit, catalogues Java | module `effectifs_temps` (D-11) ; feuille préparée depuis l'horaire publié et les pointages (quart non pointé = temps planifié signalé), segments typés et **versionnés**, saisie et correction jugées en temps réel par les règles FEUILLE_DE_TEMPS (refus 409 défait), heures supplémentaires jour puis semaine sans double compte, jours fériés (nouveau référentiel `jours_feries`), écart planifié / déclaré, anomalies calculées à la lecture et figées à la soumission, approbation à 1–3 paliers par des personnes différentes, verrouillage, lot de paie codé avec reclassement des HS et empreinte SHA-256, relevés par unité / personne / type d'heure / semaine ; pages Ma feuille de temps et Planification › Feuilles de temps | 32 essais Jest (domaine + intégration) | domaine **100 %** lignes et branches ; `temps/` **100 %** lignes |
| R10 | ✅ Node, écran, e2e, catalogues Java | module `effectifs_integrations` ; sept connecteurs (RAO, BD Corpo entrants ; SAAV, PFT, Graph, ESB, entrepôt sortants), chacun **port + simulateur + adaptateur HTTP + suite de contrat** ; mode, adresse https et **nom** de la variable du jeton par organisme (aucun secret en base) ; boîte d'envoi transactionnelle (clé d'idempotence par connecteur), livrée par l'ordonnanceur, rejet consigné, panne retentée puis avouée, rejeu ; lot de paie au PFT, équipages au SAAV, quarts au calendrier, journal relayé au bus (événements) et à l'entrepôt (versements) par curseur, horodatages du RAO et anciennetés de BD Corpo tirés ; onglet Paramétrage › Intégrations | 24 essais Jest (domaine, contrat × 2 implémentations, intégration avec l'ordonnanceur réel) ; e2e `effectifs-integrations.spec.ts` vert ; audit WCAG 4 largeurs vert | domaine, adaptateurs, registre, service, routes : **100 %** lignes |
| R11 | ✅ Node, écrans, e2e, Terraform · 🟡 niveaux de service | **IA** : module `ia` hors de tout plan (désactivé par défaut par établissement), garde sur les cinq routes qui appellent le modèle, migration 0126 qui le garde aux établissements existants, assistant et rédactions masqués ; **charte** (P-TN-9) : nom, logo matriciel, deux couleurs **refusées à l'enregistrement si un contraste tombe sous 4,5:1** (six mesures, voile compris), appliquée à toute l'application ; **pays autorisés** (P-TN-11) : restriction à la connexion d'un compte d'organisme, pays lu dans l'en-tête de la passerelle (`PAYS_ENTETE`), activation refusée si la passerelle ne le transmet pas ; **alertes** (P-TN-10) : règles par événement (export téléchargé, connexion refusée), déclenchées pour **tout** export en pièce jointe, journal des envois ; **rétention** : durée par classe (horaires, présence, feuilles, absences, journaux), aperçu, purge confirmée et journalisée ; **haute disponibilité** (E-TN-9) : RDS Multi-AZ paramétrable, profil `urgences-sante.tfvars`, garde-fous RTO ↔ HA au `plan` | 41 essais Jest (IA 5, organisme 17, rétention 5, historique adaptés) ; e2e `organisme-options.spec.ts` vert, `ai-screen-context` et `invoice` adaptés et verts ; audit WCAG 4 largeurs vert ; `terraform validate` vert | `organisme/` et `effectifs/retention/` : **100 %** lignes |
| R12 | ✅ Node, écrans, e2e | **unités ↔ organigramme** (D-3) : plan d'alignement lu par la fonction de lecture du module `organisation` (jamais ses tables) — à créer, à modifier, identiques, ignorées avec motif, absentes de l'organigramme — puis application par les services du référentiel daté (historique et journal), bouton « Aligner sur l'organigramme » sur le référentiel Unités ; **Mes absences** (D-12) : déclaration en libre-service dans le formulaire unique, liste et annulation ; **échange proposé pour une personne** par le planificateur (dernière route « à câbler » des effectifs) ; `time_entries` écarté (D-13) | 3 essais Jest (plan pur + intégration contre un organigramme réel) ; e2e `effectifs-mes-absences.spec.ts` vert ; audit WCAG 4 largeurs vert ; routes : 0 orpheline, **0 à câbler** | `liaisons/` : **100 %** lignes |
| R13 | ✅ Java · ⏳ porte JaCoCo au `mvn verify` complet | jumeau Java de tous les lots : `/api/effectifs` **125/125**, `/api/organisme` 9/9, `/api/absences` 8/8 (catégorie « effectifs » branchée), `/api/ai` 7/7 — projet 90,8 % | `mvn verify` complet : 2 920 essais, 2 échecs trouvés et corrigés (bean `Gardes` absent au démarrage ; barre oblique finale) puis verts en exécution ciblée avec `DemarrageTest`, `ArchitectureTest`, `NormesJavaTest` ; essais par lot verts (R1–R3 + organisme/IA 111, R4 18, R5 10 + façade 29, R6 14, R7 22, R8 30, R9 15, R10 28, rétention 10) | ≥ 96 % lignes par classe créée, mesuré au JaCoCo par classe hors de `target/` ; la porte `check` de Maven reste à confirmer par un `mvn verify` complet ininterrompu (≈ 40 min) |

## 5. Journal des livraisons

### R1 — Socle `effectifs` · 29 septembre 2026

- **Serveur** : `backend/src/effectifs/` — `erreurs.ts`, `contexte.ts` (horloge et acteur injectés à la frontière), `http.ts`, `routes.ts` ; `socle/` : `referentiels.ts`, `perimetres.ts`, `milieux.ts`, `journal.ts` (domaine pur), `db.ts`, `serviceReferentiels.ts`, `servicePerimetres.ts` (garde `exigerPerimetre`, refus par défaut), `serviceMilieux.ts`, `serviceJournal.ts`, `lectureJournal.ts`, `routes.ts`. Monté sous `/api/effectifs` (`app.ts`, impact GitNexus : LOW).
- **Catalogue** : famille « Gestion des effectifs (préhospitalier) », module `effectifs` (Node + Java) ; vues `effectifs`, `effectifs:milieux`, `effectifs:perimetres`, `effectifs:journal` (Node + Java). La case apparaît dans *Superadmin › Modules & accès*.
- **Écran** : `frontend/src/Effectifs.tsx` + `frontend/src/effectifs/` (formulaire de référentiel rendu **depuis le descripteur du serveur** : aucune liste métier codée) ; menu Urgences-santé › Paramétrage des effectifs ; i18n fr/en/mg.
- **Essais** : `tests/unit/effectifs-socle-domaine.test.ts` (26), `tests/unit/effectifs-http.test.ts` (4), `tests/integration/effectifs-socle.integration.test.ts` (21) ; `e2e/tests/e2e/effectifs-socle.spec.ts` (3 parcours avec axe-core) ; page inscrite à `e2e/tests/ui/audit-wcag22.spec.ts`.
- **Exigences couvertes** : E-GN-7, E-GN-8 (pour les milieux et le journal ; les routes des lots suivants appelleront la même garde), E-GN-9, E-GN-10, E-GN-17 ; fondation de toutes les « paramétrables ».
- **Écarts restants** : e2e à exécuter (serveur Docker sur :4000 → lancer avec `E2E_API_PORT=4010`) ; jumeau Java des routes (R13) ; liaison des unités avec la structure `organisation` (R12).

### Porte RBAC — capacités `effectifs.*` · 29 septembre 2026

- **Constat** (signalé par l'autre session) : `tests/unit/rbac-architecture.test.ts` refusait 8 routes mutantes du socle. Les gardes existaient, mais derrière des alias (`const ADMIN = requireRole(...)`) que la porte ne sait pas lire — et que `accessPolicy.ts` proscrit : une route déclare une **capacité**, elle ne réénumère pas de rôles.
- **Correctif** : deux capacités, `effectifs.configure` (administrateur — référentiels, périmètres, règles) et `effectifs.manage` (administrateur, directeur, gestionnaire — milieux, journal, planification, disponibilités ; bornée ensuite par le périmètre de l'unité). Déclarées dans `accessPolicy.ts` **et** `politique-acces.json` (Java). Chaque route des lots R1–R4 nomme sa capacité en ligne ; le routeur de planification la pose sur le routeur entier.
- **Seule exemption** : `PUT /moi/:periodeId` (disponibilités) — l'employé dépose les siennes ; la ressource vient de la session, jamais du corps. Motif inscrit dans la liste de la porte.
- **Bénéfice** : l'établissement peut désormais ajuster ces droits par rôle (`canInTenant`), ce que `requireRole` interdisait.

### Méthode de pose pendant la fenêtre de mesure de l'autre session

Les lots R2–R4 sont rédigés hors de l'arbre puis éprouvés dans une **copie d'essai** du dépôt (`refaire-run.sh` : copie de `backend/`, `frontend/`, des catalogues Java, puis rejeu des scripts de pose `poser-r2-r3.py`, `poser-gardes-r4.py`, `poser-front-r4.py`). La pose dans l'arbre est donc un rejeu de scripts déjà vérifiés, en une fois, sans état intermédiaire cassé pour les portes front ↔ catalogue.

Défauts trouvés par la copie d'essai avant la pose :
- une indisponibilité « toute la journée » (0 h – 24 h) était refusée par la règle des **quarts** (« jamais 24 h pile ») : une plage n'est pas un quart — `creneauDePlage` l'admet ;
- un essai R2 attendait `versionCourante: null` pour une règle à venir, alors que l'écran s'appuie sur la version à venir (null = retirée) : l'essai était faux, pas le service ;
- branches manquantes des domaines (entrées non textuelles, départage par clé, règle moins sévère après une plus sévère) : essais ajoutés, 100 % atteint ;
- une erreur imprévue pendant l'attribution de fin de collecte doit **remonter**, jamais passer pour un refus : essai ajouté.

### R5 — Absences d'effectifs (rédigé, éprouvé hors de l'arbre) · 29 septembre 2026

- **Serveur** : `backend/src/effectifs/absences/` — `domaine.ts` (statuts et transitions du diagramme 7.3, décision depuis l'action des règles, admissibilité du code, préavis, taux après), `db.ts` (`eff_absences`), `service.ts` (`declarer`, `impact`, `decider`, `lister`, `absenceVisible`), `routes.ts` (`/api/effectifs/absences` : lecture, impact, décisions ; **pas de déclaration**), `sourceFacade.ts` (la source branchée sur la façade).
- **Façade `/api/absences`** : un **port** `SourceAbsences` (`backend/src/absences/sources.ts`) reçu par `createAbsencesRouter(sources)` depuis `app.ts` — injection par la composition, pas de registre statique. Les trois catégories historiques restent traduites par la façade ; la quatrième s'y branche (catégories, liste, pièces, déclaration). `attestable` nomme désormais les catégories attestables au lieu d'exclure le dossier (une catégorie branchée aurait visé une table inexistante). Champ facultatif `personneNom` dans la forme unifiée ; OpenAPI mis à jour.
- **Rayon d'impact** : GitNexus ne résout pas ces symboles sur cette branche (index de `cd32822`, homonymes) ; relevé textuel : `app.ts`, `absences/{routes,domaine,pieces,attestation}.ts`, `services/openapi.ts`, trois essais (`absences-domaine`, `absences-types-pieces-attestation`, `absences-unifiees`). Seul `absences-unifiees` fige la liste des catégories : il reçoit la quatrième, fermée avec sa raison — changement de comportement attendu, pas une régression.
- **Écran** : nature « Absence d'effectifs » dans le formulaire unique (`DeclarationEffectifs.tsx` : ressource, code d'absence du référentiel, commentaire ; pas d'heures) ; message après déclaration selon la décision automatique (refus + motifs + dérogation possible) ; onglet **Planification › Absences et comblement** (`AbsencesEffectifs.tsx` : filtre, impact, approuver / refuser / déroger).
- **Exigences couvertes** : E-GN-5 (dérogation + code), E-GN-6 (décision automatique conservée), E-CH-6 (vacance des places), E-GR-2 (codes par titre d'emploi), E-GR-3 (impact calculé), E-GR-4 (places vacantes = quarts à offrir, repris par le bassin de R4) ; P-GN-3.
- **Écarts restants** :
  - le **libre-service employé** passe par l'API de la façade (la ressource vient de la session), mais la page Absences est gouvernée par `absence.manage`, que l'employé n'a pas (« une demande pour soi-même relève d'un autre chemin ») : l'écran employé est à décider avec R12 ;
  - l'attribution automatique au prioritaire et l'avis aux admissibles (P-GN-1, P-GH-1, P-GH-3) : les places vacantes rejoignent le bassin motivé de R4, sans sollicitation poussée — lot à part ;
  - le fait `soldeBanqueHeures` retiré du catalogue ABSENCE jusqu'au lot R9 (types d'heures, banques) : un fait qu'aucun lot ne fournit ferait échouer toute règle qui le lit.

### Pose R2 → R6 dans l'arbre · 29 septembre 2026

- **Posé** : serveur Node et écrans des lots R2 à R6, par rejeu des sept scripts éprouvés sur la copie. **Pas encore** les catalogues Java (`CatalogueModules`, `CatalogueVues`, `politique-acces.json`) : deux campagnes `mvn verify` d'autres sessions tournaient ; les modifications Java attendent dans une copie d'attente et seront posées à leur signal (`CatalogueModulesTest` / `CatalogueVuesTest` exigent la parité : le Java reste en retard d'un pas jusque-là).
- **Mesuré dans l'arbre** (`--runInBand`, charge machine 160–400 selon ADR 0046) : **229 essais Jest verts** (effectifs, absences, portes catalogue et RBAC), **100 % des lignes** de `src/effectifs` ; front : typage propre, 16 vitest verts, scanner d'accessibilité sans manquement.

### R6 — Échanges de quarts · 29 septembre 2026

- **Serveur** : `backend/src/effectifs/echanges/` — `domaine.ts` (statuts et gestes, issue de l'acceptation, faits d'un sens, verdict combiné qui **nomme la personne** de chaque motif), `db.ts` (`eff_echanges`), `service.ts` (`proposer`, `gerer`, `lister`, `mesQuarts`, `quartsDemandables`), `routes.ts` (`/api/effectifs/echanges`).
- **Règles métier** : on n'échange que deux places **tenues**, de **périodes publiées**, entre personnes **admissibles** à la place de l'autre (corps d'emploi) ; un quart ne peut faire l'objet que d'un échange ouvert ; l'acceptation et la validation **rejugent** l'échange et refusent si l'horaire a changé depuis la proposition.
- **Droits** : proposer, accepter, refuser, annuler = gestes de pair à pair (la partie elle-même, ou l'encadrement de son unité ; exemption motivée à la porte RBAC) ; valider, rejeter = `effectifs.manage` + niveau GESTION des deux unités.
- **Exigences couvertes** : E-GR-7 (admissibilité **et** contiguïté, dans les deux sens), E-GR-12 (entre périodes, au choix de l'organisme).

### Recette dans l'arbre : e2e et audit WCAG · 29 septembre 2026

- **E2E** (`E2E_API_PORT=4010 E2E_WEB_PORT=5183`, Docker tenant :4000) : **9/9 verts** — `effectifs-socle` (3), `effectifs-regles`, `effectifs-planification` (2), `effectifs-disponibilites`, `effectifs-absences`, `effectifs-echanges`, axe-core à chaque état.
- **Audit géométrique WCAG 2.2** (390, 768, 1280, 1920 px) : tous les écrans d'effectifs, onglets, tiroirs et les deux pages employé passent. L'audit a trouvé trois défauts, corrigés :
  - l'éditeur de plages (tableau à cinq colonnes) débordait de 275 px sur téléphone → liste de groupes libellés qui se replient (1.4.10) ;
  - les tiroirs « impact d'une absence » et « dépôt d'une ressource » s'ouvraient **au retour du serveur** : fermés trop tôt, ils surgissaient ensuite par-dessus la page → ouverture immédiate, chargement ensuite, `role="status"` pendant l'attente ;
  - la boucle de l'audit ne refermait pas un tiroir laissé ouvert par le dernier état d'un écran → elle referme tout dialogue avant l'écran suivant (sinon avalanche d'« écrans jamais rendus »).
- **Hors lot, signalé** : l'écran Achats était injoignable à l'audit (module `approvisionnement` absent des modules ouverts par l'essai) — ajouté ; un compte créé par le **superadmin** via `POST /api/auth/users` naît sans organisme même avec `X-Tenant-Id` — les e2e d'effectifs créent désormais leurs employés par l'administrateur de l'organisme, comme en vrai.

### R0 — Périmètre par groupe du module `horaires` : inventaire et plan · 29 septembre 2026

**Inventaire** (lecture seule, Node et Java) : environ 60 routes mutantes portent sur un groupe — directement (`/groupes/:id`) ou par un objet qui y mène (période, tâche, quart, échange, liste et recherche de remplacement, contrainte, cible, scénario, offre HS, proposition de pointage). Toutes les gardent **par rôle seulement** ; `manager` et `director` y sont partout « larges ». Seuls les lots P-4, P-5, P-6 bornent au groupe — et seulement le contremaître. Même situation côté Java (`GardeAcces.exigerRole`).

**Faits qui conditionnent la conception**
- Un membre de groupe (`horaire_membres.personId`) est un **identifiant d'employé** ; le jeton porte `employeeId`. Le Node journalise l'acteur par **nom d'utilisateur**, le Java par **identifiant** : la migration joint `horaire_journal.acteurId` à `users` par l'un ou l'autre.
- Aucun prédicat « planificateur ou gestionnaire du groupe G » n'existe : seulement « membre de » et « contremaître de ».

**Trois défauts trouvés en passant, corrigés en premier (phase 1)**
1. `DELETE /api/horaires/indisponibilites/:id` : aucune garde, aucun contrôle du propriétaire — n'importe quel compte efface l'indisponibilité de n'importe qui.
2. `GET /api/horaires/journal` : rend le journal de tout l'établissement à tout compte.
3. ~~`PUT /api/horaires/groupes/:id/regle-pointage` : non borné au groupe~~ — vérification faite, l'écriture est **plus** stricte que la lecture (le contremaître lit, seuls les planificateurs écrivent) : ce n'est pas un défaut ; elle entre dans la phase 2 avec le reste du périmètre de groupe.
4. Au passage : `journalPourLecteur` compare un nom d'utilisateur à des identifiants d'employé — le filtre par groupe du journal des transactions ne trouve jamais rien.

**Plan**
| Phase | Contenu | Essais |
|---|---|---|
| 1 | Les quatre défauts ci-dessus, Node et Java | un essai d'intégration par défaut, qui échoue avant le correctif |
| 2 | Prédicat `gereLeGroupe` (planificateur ou gestionnaire actif, ou rôle large admin / superadmin / directeur / RH) ; résolveurs objet → groupe ; garde sur chaque route mutante de groupe ; **migration versionnée** qui inscrit les managers comme gestionnaires là où le journal montre qu'ils agissent ; porte RBAC : garde nommée reconnue | essais de contrat Node/Java ; les essais existants où un manager sans rôle de groupe agit reçoivent le rôle (changement de comportement voulu, listé) |
| 3 | Lectures : listes de groupes, périodes, quarts, journaux filtrées par le périmètre pour le manager | idem |

- **Catalogues Java posés** le 29 sept. après la fin des campagnes `mvn` des autres sessions : 5 modules, 12 vues, 2 capacités, par insertion. Un premier rejeu des scripts de pose a **dupliqué** trois blocs Node (entrées R2/R3 du catalogue des modules et des vues, notion « planification » du menu) : la vérification d'idempotence de `remplacer` passait après l'assertion. Doublons retirés, portes catalogue revertes (42/42) ; le Java se pose désormais par un script à lui (`poser-java-r4-r6.py`).

### R0 phase 1 — quatre défauts de sécurité (Node) · 29 septembre 2026

- **Essai d'abord** : `backend/tests/integration/horaires-securite-r0.integration.test.ts`, **4 échecs** sur le code d'avant (201 attendu / 400 reçu ; 403 / 200 ; etc.), **4 succès** après.
- **Correctifs** :
  1. `retirerIndisponibilite` exige d'être la personne (dossier d'employé de la session) ou l'encadrement ; sinon 403 nommé ;
  2. `saisirIndisponibilite` compare la personne au **dossier d'employé** de la session, plus au nom d'utilisateur — la saisie « pour soi-même » ne marchait que par coïncidence ;
  3. `GET /journal` réservé à l'encadrement (admin, superadmin, directeur, RH, manager) ; l'écran Horaires ne le demande plus pour un membre (sinon bandeau d'erreur) ;
  4. `journalPourLecteur` cherche les groupes du lecteur par son dossier d'employé.
- **Rayon** (GitNexus, index à jour : ces symboles sont commis) : risque **LOW**, un seul appelant (le routeur) pour chacun. Porte RBAC : l'exemption `/indisponibilites` dit désormais vrai (retrait borné par le service).
- **Non-régression** : 654 essais horaires verts ; typage front propre ; scanner d'accessibilité sans manquement.
- **Java** : les mêmes quatre défauts existent dans `ControleurHoraires` / `DepotQuarts` / `DepotExploitationHoraires` (l'acteur y est l'identifiant utilisateur) ; correctif prêt, posé à la fin de la dernière campagne `mvn` d'une autre session.

### R0 phases 2 et 3 — périmètre par groupe (Node) · 29 septembre 2026

- **Prédicat et garde** : `backend/src/horaires/perimetreGroupe.ts` — `gereLeGroupe` (rôles d'établissement admin / superadmin / directeur / RH : tous les groupes ; manager : ceux où il est planificateur ou gestionnaire, par son dossier d'employé), `groupesGeres`, douze résolveurs objet → groupe (paramètre, corps, période, tâche, quart, échange, liste et recherche de remplacement, contrainte, cible, scénario) et la garde `groupe(resolveur)` posée **après** le `requireRole` de chaque route : objet inconnu 404, groupe non confié 403 nommé, aucune cible → le service répond.
- **Écritures bornées** : 36 routes (groupes, membres, périodes et fériés, tâches, cases, blocages, éligibilités, poste, besoins, quarts, génération, remplacement, règle de pointage, contraintes, cibles, scénarios, listes et recherches de remplacement). Décideurs HS et pointage : gère le groupe ou contremaître. Échanges : accepter pour autrui et valider = groupe géré (le téléphoniste garde son droit, 12.1.22). Indisponibilités pour autrui et retrait : membres des groupes gérés.
- **Créer un groupe** : le manager qui le crée en devient gestionnaire.
- **Lectures (phase 3)** : la **consultation** des horaires reste ouverte aux membres (conception L1) ; les lectures de **gestion** sont bornées pour le manager — journal complet (filtré aux groupes gérés, période comprise), offres HS, diagnostic, scénarios, candidats d'un quart, recherche.
- **Migration 0125** : chaque manager devient gestionnaire des groupes où le journal le montre agissant (groupe direct ou par la période), sans ranimer un rattachement retiré ni changer un rôle plus précis que « membre » ; chaque ajout journalisé sous `migration-0125`. **Défaut trouvé par son essai** : en base, le nom d'utilisateur d'un compte d'établissement est suffixé `@@<tenantId>` alors que le journal Node porte le nom de connexion — la jointure les réunit désormais (sans quoi la migration n'aurait trouvé presque personne).
- **Écran Horaires** : les gestes de planification ne s'offrent au manager que pour un groupe qui lui est confié.
- **Changement de comportement voulu, essai existant adapté** : `horaires-planification` (un manager « chef » sans rôle de groupe) — l'essai prouve désormais le 403 puis inscrit chef comme gestionnaire.
- **Reste** : le jumeau Java (mêmes gardes dans `ControleurHoraires`, `ControleurHeuresSupplementaires`, `ControleurPointage`, ~85 usages d'un MANAGER sans groupe dans `ControleurHorairesHttpTest`) — dès la fin de la campagne `mvn` en cours.

### R7 — AGPH et remplacements long terme · 29 septembre 2026

- **Serveur** : `backend/src/effectifs/agph/` — `domaine.ts` (transitions, délai 5 min à une semaine, ordre total ancienneté puis matricule, fin de tour exacte, statut d'après campagne, classement des intéressés), `db.ts`, `serviceCampagnes.ts`, `serviceRemplacements.ts`, `routes.ts` (`/api/effectifs/agph`).
- **Le temps par rattrapage** : aucune tâche planifiée — chaque lecture ou geste traite d'abord les tours écoulés (choix automatique au premier poste admissible, sinon « passé » ; le tour suivant commence à la fin du précédent) et les fenêtres de manifestation fermées. Le déroulé est donc déterministe, et l'essai le rejoue à horloge fixe.
- **Choix** : la personne à son tour, pour elle-même ; ou le planificateur pour elle (niveau PLANIFICATION) ; un seul par rang ; corps d'emploi exigé, puis les règles `CHOIX_AGPH` de l'organisme (ex. « le temps complet est réservé aux deux premiers rangs ») — un refus est motivé.
- **Clôture** : statut du poste obtenu (TC, TPR), ou statut de comportement « temps partiel occasionnel » de l'organisme pour qui n'a rien obtenu ; les postes offerts et non choisis deviennent **vacants** (gestion régulière). Tout geste du mécanisme est journalisé sous `agph-tour-de-role` / `remplacement-long-terme`.
- **Remplacements long terme** : ouverts sur un poste (absence longue ou vacance), fenêtre de manifestation qui se ferme au plus tard le premier jour ; le plus ancien admissible reçoit les quarts vacants du poste sur la durée, sans chevauchement de son propre horaire ; sans intéressé : non comblé, puis transféré.
- **Défauts trouvés par les essais** : une règle datée de 2027 ne jugeait pas un choix fait aujourd'hui (un choix se juge à sa date) ; une personne hors campagne et hors périmètre reçoit 404, pas une liste vide.
- **Exigences couvertes** : E-CH-1, E-CH-7, E-CH-8, E-CH-9, E-CH-10 ; P-CH-1 à P-CH-7, P-CH-9.
- **Écart restant** : l'avis « c'est votre tour » (notification) — le tour se voit à l'écran et se journalise ; l'envoi passera par le catalogue d'avis (ADR 0029) avec R10.

### R8 — Présence et pointage · 29 septembre 2026

- **Serveur** : `backend/src/effectifs/presence/` — `domaine.ts` (séquence, code QR HMAC par fenêtre de 5 min, jugement des preuves, résultat nommé, segments, saisie en segments, heure locale du Québec, corroboration), `db.ts`, `service.ts`, `routes.ts` (`/api/effectifs/presence`).
- **Le quart pointé** vient de l'horaire publié : la place de la personne dont la fenêtre (2 h avant, ou la présence préalable si plus longue, jusqu'à 2 h après) contient l'instant. Hors quart : 409 qui dit d'utiliser la saisie a posteriori.
- **Preuves** : chaque mécanisme du milieu est jugé ; la précision annoncée par l'appareil s'ajoute au rayon, plafonnée à 100 m. Un code QR d'un autre milieu est un « mauvais milieu », distinct d'une preuve absente. **Loi 25** : la position est lue une fois, au geste, avec le consentement de la case à cocher ; le serveur n'en garde que la distance (un essai vérifie qu'aucune colonne de position n'existe).
- **Règles POINTAGE** : faits `ecartDebutMinutes`, `distanceMetres` (−1 sans position : le moteur exige chaque fait qu'une règle lit, et −1 ne franchit aucune règle de distance), `mecanisme`, `preuveExigeePresente`. Bloquant → refus 409 motivé, rien n'est enregistré ; `EXIGER_APPROBATION` ou écart de preuve → « à approuver ».
- **Décisions** : niveau GESTION de l'unité ; jamais sur ses propres pointages ; un rejet se motive ; une saisie a posteriori se tranche en un geste pour tout son lot. Une saisie faite par l'encadrement pour autrui est approuvée à son nom.
- **Horodatages externes** : import rejouable (clé unique par source), lignes fautives nommées, hors périmètre refusé ligne à ligne ; ils **corroborent** un pointage du même sens à 5 min près, sans le remplacer.
- **Navigateur** : `frontend/nginx.conf` passe de `geolocation=()` à `geolocation=(self)` — sans quoi le navigateur refuse la lecture de position sur la page. L'en-tête des réponses JSON de l'API reste `geolocation=()` (sans effet sur la page).
- **Défauts trouvés par les essais** : fait manquant (`distanceMetres`) sans position ; mes propres attentes de présence préalable (10 min comptées pour une entrée à 5 h 50, pas 15).
- **Exigences couvertes** : E-GP-1, E-GP-2 (pauses non payées), E-GP-4, E-GP-9 à E-GP-14, E-GP-15, E-RF-13 ; P-GP-1, P-GP-3, P-GP-4.
- **Écarts restants** : E-GP-7 (présence multi-site) n'est couvert que quart par quart — chaque pointage se juge au milieu de **son** quart ; un déplacement entre milieux au cours d'un même quart reste à modéliser. Le « journal des localisations » (E-GP-14) est tenu sous forme de verdicts et de distances, jamais de positions. Restent aussi la lecture du code QR par la caméra (saisie du code aujourd'hui) et le jumeau Java du contexte (R13).

### R0 — jumeau Java vérifié · 29 septembre 2026

- `mvn -o test` ciblé : **73 essais verts**, dont `ControleurHorairesPerimetreGroupeHttpTest`, `ArchitectureTest` et `NormesJavaTest`.
- L'essai historique `ControleurHorairesHttpTest` écrivait trois gestes que R0 interdit désormais ; il prouve maintenant le refus puis le geste permis : une indisponibilité **pour autrui** exige que la personne soit membre d'un groupe géré (403, puis 201 après rattachement) ; la retirer n'appartient qu'à la personne ou à l'encadrement (403 pour un simple membre, avec le motif) ; le journal d'un groupe se lit par l'encadrement (403 pour un membre).

### R9 — Feuilles de temps · 29 septembre 2026

- **Serveur** : `backend/src/effectifs/temps/` — `domaine.ts` (statuts et paliers, paramètres bornés, segments, heures supplémentaires, écarts, anomalies, lignes de paie, empreinte), `db.ts`, `service.ts`, `routes.ts` (`/api/effectifs/temps`).
- **Décision D-11** : contexte à part, voir § 2.
- **Préparer** : une feuille par personne et période (31 jours au plus), rejouable sans doublon ; un quart pointé donne ses segments pointés (présence préalable comprise), un quart non pointé son temps planifié (anomalie « non pointé ») ; les saisies a posteriori **approuvées** entrent aussi. Régénérer reprend les pointages du moment sans toucher aux segments corrigés.
- **Corriger** : chaque correction est une nouvelle version, l'ancienne reste (historique consultable) ; corriger le temps d'autrui ou un temps pointé se motive ; la journée est rejugée par les règles et une règle bloquante **défait** l'écriture (point de sauvegarde SQLite).
- **Soumettre** : impossible tant qu'une anomalie bloquante demeure — chevauchement, pointage en attente d'approbation, quart sans sortie, règle bloquante ; l'instantané des anomalies est figé, les évaluations tracées.
- **Approuver** : 1 à 3 paliers (paramètre de l'organisme), niveau GESTION de l'unité, jamais sa propre feuille, jamais deux paliers par la même personne ; un retour se motive et rouvre la feuille.
- **Paie** : lignes par jour et code de paie, heures supplémentaires reclassées du code régulier vers le code supplémentaire, empreinte SHA-256 que le destinataire recalcule ; l'export est journalisé.
- **Moteur de règles** : `jugerSansTrace` évalue sans consigner — une lecture ne laisse pas de trace, une décision en laisse (un essai le vérifie).
- **Défauts trouvés** : message d'erreur dépendant de l'ordre des données (type d'heure manquant) — le type du travail régulier se vérifie d'abord ; mes propres attentes de relevé (recalculées à la main).
- **Exigences couvertes** : E-GP-3, E-GP-5, E-GP-6, E-GP-8 ; E-RF-1 à E-RF-7, E-RF-9, E-RF-11, E-RF-12, E-RF-14 ; P-RF-1.
- **Écarts restants** : l'envoi du lot au PFT (port + simulateur, lot R10) ; le reflet dans `time_entries` (R12) ; les flux d'approbation du module `flux` (les paliers sont ici un paramètre, pas un flux dessiné).

### Routes sans écran : les 13 orphelines des lots R2 à R9 · 29 septembre 2026

La porte `endpoints-orphelins.cjs --strict` en relevait 13. **Douze ont reçu leur écran**, dans les écrans existants : aperçu d'impact et versions d'une règle (Paramétrage › Règles), historique d'une valeur de référentiel, libérer une place (Quarts et équipages), relire et annuler une absence, relire un échange / un remplacement / un pointage à l'ouverture de leur fiche, pointages récents d'une personne, paramètres des feuilles et versions d'un segment. **Une** reste déclarée « à câbler » dans `docs/ROUTES-SANS-INTERFACE.md` : l'échange proposé par le planificateur pour une personne (R12).


### Fiches relues à l'ouverture : garde de forme · 29 septembre 2026

- L'audit géométrique a déclaré « jamais rendus » tous les onglets de la planification après la fiche d'une absence : le doublon d'essai, apparié **par préfixe**, rendait la *liste* des absences à `GET /absences/{id}`, et une liste rendue comme une absence fait tomber l'arbre React. Chaque lecture ajoutée par le câblage passe désormais par `objet()` / `liste()` (`frontend/src/formeRecue.ts`) : une réponse de forme inattendue vaut « absente », jamais page blanche. L'audit reçoit un doublon par route de détail.
- **Restent à mesurer**, la machine étant saturée au moment d'écrire (charge > 500, 85 démons Gradle lancés par l'extension Java de l'éditeur) : l'audit géométrique des quatre écrans touchés, l'e2e `effectifs-temps.spec.ts`, et le `mvn verify` complet (JaCoCo par classe pour `DepotPerimetreGroupe`).

### R10 — Intégrations · 29 septembre 2026

- **Serveur** : `backend/src/effectifs/integrations/` — `domaine.ts` (connecteurs et sens, configuration validée, lecture d'une réponse HTTP, événements du bus, lots de versement), `ports.ts` (une destination sortante, deux sources entrantes : ISP), `adaptateurs.ts` (simulateur et client HTTP par port, appel réseau injectable), `registre.ts` (la configuration choisit l'implémentation : DIP), `db.ts`, `service.ts`, `routes.ts` (`/api/effectifs/integrations`).
- **Suite de contrat** : chaque port passe la même suite contre le simulateur et contre le client HTTP branché sur un faux serveur qui applique la convention documentée — ce que la démonstration montre, la production le tient (LSP).
- **Boîte d'envoi** : le dépôt se fait dans la transaction du geste qui le produit ; la clé d'idempotence (empreinte du lot, composition des équipages, identifiant de journal…) rend tout dépôt rejouable sans doublon. L'ordonnanceur livre ; 400/409/422 = rejet consigné avec le motif du destinataire ; 5xx ou réseau = passager, retenté puis « en échec » au troisième essai. Un message accepté ne se rejoue jamais.
- **Le journal comme source** : le bus et l'entrepôt ne sont appelés par aucun module — un relais lit le journal depuis un curseur. Couper un connecteur ne perd rien : le curseur attend.
- **Sécurité** : https exigé en mode HTTP (P-TN-7, protocole sécurisé), sauf un poste local ; seul le **nom** d'une variable d'environnement est gardé, le jeton est lu au moment de l'appel et une variable absente est nommée ; une source injoignable répond 502, pas 400.
- **Défauts trouvés** : l'attribution forcée n'était pas un événement du bus (elle l'est) ; un helper HTTP asynchrone manquait (`repondreAsync`) — le synchrone aurait rendu `{}`.
- **Exigences couvertes** : E-TN-7 ; P-TN-7 ; E-RF-12 (transmission), E-RF-13 (tirage des horodatages RAO).
- **Écarts restants** : les formats réels de chaque système (à obtenir d'Urgences-santé : ce sont des conventions **supposées**, comme pour la GRICS) ; le déclenchement planifié des relais et tirages (aujourd'hui par geste) ; Microsoft Graph exige une inscription d'application Entra (hors code).

### R11 — IA, options de l'organisme, rétention, haute disponibilité · 29 septembre 2026

- **IA désactivée par défaut** : module `ia` (famille « Infrastructure et options »), absent de tous les plans. Les routes qui envoient des données au modèle (`/api/ai/status`, `/chat`, `/verify-accounting`, `/suggest-description`, `/reminder`) exigent le module pour un **établissement** ; l'instance de base (l'opérateur, `default`) garde son assistant ; les gabarits sans modèle (`/job-description`, `/skill-suggestions`) restent ouverts. La migration 0126 le garde aux établissements existants — le déploiement ne retire rien à personne. **Défaut trouvé par l'e2e** : l'assistant était monté hors du fournisseur de modules et ne voyait donc jamais le réglage.
- **Charte (P-TN-9)** : `backend/src/organisme/` — nom affiché, logo PNG/JPEG/WebP (SVG refusé : il peut porter du script), couleur principale et couleur de la barre latérale. Six contrastes mesurés à l'enregistrement, dont celui du bloc de l'organisme **sous son voile blanc à 12 %** — trouvé par axe-core sur une charte bleu marine (3,99:1). Les nuances (survol, fond doux, anneau de focus) se déduisent, elles ne se saisissent pas. Onglet Administration › Organisme, avec les mesures affichées avant l'envoi.
- **Pays autorisés (P-TN-11)** : liste ISO par organisme, contrôlée à la connexion des comptes de l'organisme (les comptes globaux ne sont pas concernés) ; le pays vient de l'en-tête que pose la passerelle (CloudFront, Cloudflare…), nommé par `PAYS_ENTETE`. Sans cette variable, **activer** la restriction est refusé — la configuration échoue tôt au lieu de bloquer tout le monde à la première connexion. Un pays inconnu est refusé (refus par défaut).
- **Alertes (P-TN-10)** : règles par événement et destinataires ; un intergiciel repère **toute** réponse en pièce jointe (`Content-Disposition: attachment`) : un export ajouté demain est couvert sans qu'on y pense. L'alerte part après la réponse, ne retient rien du contenu, et chaque envoi est journalisé.
- **Rétention** : `backend/src/effectifs/retention/` — cinq classes, durée en années, aperçu des volumes, purge confirmée à l'écran, dans une transaction, journalisée avec ses décomptes ; une feuille non approuvée n'est jamais purgée. **Défaut trouvé** : better-sqlite3 refuse les paramètres numérotés répétés (`?1`) — paramètres nommés.
- **Haute disponibilité (E-TN-9)** : `infra/terraform/aws` — `multi_az` devient la variable `haute_disponibilite` (fausse par défaut : CSSDC inchangé) ; le garde-fou « pas de HA pour un RTO de 48 h » devient une règle de cohérence dans les deux sens (HA seulement si RTO ≤ 4 h ; RTO ≤ 4 h exige HA et deux tâches) ; profil `urgences-sante.tfvars` (RTO 1 h, RPO 15 min, Multi-AZ, deux tâches).
- **Java** : `DecisionAccesTest` comptait 25 capacités — il en compte 27 depuis les capacités `effectifs.*` du lot R1 (le directeur gère les effectifs de son périmètre).
- **Écarts restants** : les **niveaux de service par contrat** (le module Soutien porte des délais N1–N4 fixes, non paramétrables par organisme) ; la configuration de la passerelle qui transmet le pays (hors code) ; le jumeau Java des nouvelles routes (R13).

### R12 — Liaisons · 29 septembre 2026

- **Unités ↔ organigramme** : `backend/src/effectifs/liaisons/organisation.ts`. L'organigramme fait foi ; la liaison lit `organigramme(tenant, date)` (un DTO), calcule un plan pur, et l'applique par `creerValeur` / `remplacerValeur` à la date choisie (jamais passée : les référentiels ne sont pas rétroactifs). Un code d'organigramme hors du format des référentiels est ignoré avec son motif, et ses sous-unités aussi ; une unité d'effectifs absente de l'organigramme est **signalée, pas fermée** — des quarts et des périmètres y tiennent. Routes `/api/effectifs/liaisons/organisation` (aperçu, application), réservées à `effectifs.configure`.
- **Mes absences** (D-12) : Mon espace › Mes absences — le formulaire unique en libre-service, la liste des absences d'effectifs de la personne avec décision et motifs, l'annulation tant qu'elle attend une décision ou n'a pas commencé.
- **Échange pour une personne** : Planification › Échanges › « Proposer un échange pour une personne » — personne, quart cédé, quart demandé ; mêmes contrôles qu'un échange proposé par la personne. La dette « à câbler » de `docs/ROUTES-SANS-INTERFACE.md` est soldée.
- **`time_entries`** : écarté (D-13).
- **Java (R0 et catalogues)** : `mvn verify` complet — 2 659 essais ; les sept échecs relevés (compte des capacités, parité des catalogues après l'ajout de `ia`, rétention et Mes absences) sont corrigés, et verts en exécution ciblée (31 essais). JaCoCo : `DepotPerimetreGroupe` conforme ; `ControleurHoraires` était à 94 % de lignes — deux essais de décisions R0 (blocage retiré dans son groupe seulement ; validation d'un échange hors périmètre refusée) couvrent 6 des 12 lignes manquantes — 97 % au calcul, **à confirmer par JaCoCo au prochain `mvn verify` complet**. Reste le ratio de branches global à 0,91 pour 0,92, porte transverse antérieure au lot.


### R13 — Jumeau Java des lots R1 à R12 · 30 septembre 2026

- **Parité mesurée** (`contract/outils/etat-migration.cjs`) : `/api/effectifs` **125/125**, `/api/organisme` 9/9, `/api/absences` 8/8, `/api/ai` 7/7 ; projet 1 641/1 808 (90,8 %). Mêmes chemins, codes HTTP, messages (au caractère près) et tables SQLite (DDL identique aux `db.ts`) que le Node.
- **Découpe** : un contexte `ca.vina.nexu.effectifs` en CQRS (inscrit à `ArchitectureTest.CONTEXTES_CQRS`, plafond `effectifs=0` de `NormesJavaTest`), un domaine pur par lot (`Planification`, `Regles`, `Disponibilites`, `AbsencesEffectifs`, `EchangeEffectifs`, `Agph`, `PresenceEffectifs`, `TempsEffectifs`, `IntegrationsEffectifs`, `RetentionEffectifs`, `AlignementOrganigramme`), ports étroits, application en Java nu, une `Configuration<Lot>Effectifs` par lot, beans nommés `effectifs…`. Contexte `organisme` (charte, pays, alertes ; `organisme=0`) ; garde du module `ia` dans `ControleurIa` ; contrôle du pays à la connexion par un port d'`auth` tenu par `organisme` (pas de cycle).
- **Façade `/api/absences`** : catégorie **branchée** `EFFECTIFS` (port `SourcesAbsences.Effectifs`, adaptateur `AdaptateurEffectifsPourAbsences`) — catégories, liste, pièces, attestation refusée (« s'approuve, elle ne s'atteste pas »), déclaration jugée par le module ; Mes absences fonctionne contre le Java.
- **Intégrations** : les sept connecteurs ont chacun simulateur et client HTTP ; une **suite de contrat** s'exécute contre les deux (le client HTTP contre un vrai serveur local) — preuve LSP. Jeton lu par un port au moment de l'appel, jamais en base ; source injoignable → 502 (`GardeEffectifs`).
- **Essais par lot** (domaine pur + HTTP sur SQLite réelle, rejouant les scénarios d'intégration Node) : R1–R3 + organisme/IA 111, R4 18, R5 10 (+ façade 29), R6 14, R7 22, R8 30, R9 15, R10 28, rétention 10. Chaque classe créée ≥ 96 % de lignes au JaCoCo par classe (domaines à 100 % lignes et branches), hors classes `Configuration…` couvertes par `DemarrageTest`. Empreinte SHA-256 du lot de paie identique au Node (vérifiée contre une valeur calculée par le Node) ; empreinte du code QR de présence identique.
- **Défauts trouvés** : `GardeEffectifs` rendait l'erreur **sans annuler** la transaction de l'appel — un lot d'équipages à moitié appliqué aurait été validé ; il l'annule désormais (sauf refus des règles, 409, qui garde sa trace d'évaluation comme le Node). Un conflit de nom de record (`PaysAutorises.Verdict`) empêchait la compilation. Deux attentes d'essai fausses (« «  » » du Node rendu avec un espace).
- **Écarts assumés** (consignés dans les rapports de lot) : tri par `compareTo` au lieu de `localeCompare` (diffère seulement sur accents et casse) ; instants acceptés : ISO avec décalage ou date seule ; une barre oblique finale n'est pas servie (comme partout en Java) ; en R9, la saisie est jugée **avant** l'écriture au lieu d'être défaite par SAVEPOINT (même verdict) ; une soumission refusée par `ConflitError` ne garde pas ses traces d'évaluation (transaction annulée).
- **Vérification Maven** : premier `mvn verify` complet — 2 920 essais, dont 2 échecs imputables au lot (le record `ControleurAbsencesUnifiees.Gardes` sans `@Bean`, que seul `DemarrageTest` voit ; un essai d'échanges appelant `…/echanges/` que Spring 7 ne sert plus depuis le passage au `@GetMapping` nu) : corrigés, verts en exécution ciblée. La seconde passe complète a été interrompue (≈ 40 min sur cette machine) : la porte JaCoCo `check` de Maven reste à confirmer.
- **Méthode** : ports menés en parallèle par lot selon les dépendances (R1–R3 ; puis R4–R8 et la liaison ; puis R9 ; puis R10 et la rétention), chacun compilé et éprouvé **hors de `target/`** (javac + JUnit + JaCoCo) pour ne pas se gêner — six compilations complètes simultanées saturaient la machine : la compilation est devenue incrémentale sur une base partagée.

### Revalidation des 144 exigences contre le PDF · 30 septembre 2026

- **Source** : `exigences-26-015-DO.json` recontrôlé contre le PDF (199 pages) — 144/144 identifiants et libellés retrouvés au texte.
- **Méthode** : sept revues par famille, une exigence à la fois ; « ✅ Acquis » seulement si la fonction existe **et** qu'un essai automatisé la prouve (cité dans la matrice), toute l'exigence et non une partie. Six « acquis » fragiles ramenés à « partiel » (E-CH-9, E-CH-10, E-GR-2, E-GR-5, E-GN-9, P-TN-6/P-TN-8 par la revue).
- **Résultat** (`MATRICE-COUVERTURE.md`, régénérée) : essentiels **61 ✅ · 34 🟡 · 0 ⛔ · 5 🔒** (étaient 13 · 60 · 22 · 5) ; préférentiels 19 · 14 · 0 · 11 ; total 80 ✅ / 144 — **plus aucune exigence « à bâtir »**.
- **Constats bloquants pour la soumission** : E-TN-9 — la base PostgreSQL Multi-AZ du Terraform n'est lue par aucun code (Node et Java tournent sur SQLite) : la haute disponibilité ne protège aucune donnée et deux tâches ECS divergeraient ; E-TN-19 — `exploitation/securite.ts` classe ca-central-1 (Montréal) hors Québec et la résidence par défaut est `canadacentral` (Ontario) ; E-TN-17 — aucun plan JMeter ne couvre les modules d'effectifs.

### R14 — Réponses d'Urgences-santé (addenda 1 à 7) · 20 novembre 2026

- **Sources** : sept addenda et la grille v2 convertis (`questions/convertir-reponses.py`) ; l'ancien `addenda-01.md`
  était l'addenda 6 (vérifié mot pour mot, renommé). Grille v2 : seuls E-TN-9, E-TN-11, E-TN-19 changent ; leur texte est
  désormais repris mot pour mot.
- **Écarts réels**, vérifiés dans le code : E-GR-7 (heures sur 24 h, addenda 7 Q8), E-CH-6/E-CH-7/P-CH-8 (seuil par code
  d'absence, Q7), E-RF-5 (seuil de période de paie et par convention, Q15), E-CH-1 (optimisation proposée, Q11),
  P-CH-2/P-CH-4/P-CH-7 (renoncer, délai échu configurable, Q12), E-TN-7 (API pour les applications de l'Organisme,
  addenda 5 : jetons `client_credentials` émis mais acceptés par aucune route métier ; aucun solde de banque).
- **Conformes** après vérification : Q1 à Q6, Q9, Q10, Q13, Q14, Q16.
- **Décisions** : [ADR 0087](../adr/0087-reponses-26-015-do-faits-seuils-et-optimisation.md). **État** : tenu dans
  [12-reponses-addenda/03_planificateur.md](12-reponses-addenda/03_planificateur.md).
- **Livré le 20 novembre** (develop b90afe3) : AD-01 à AD-05, changesets 0020 et 0021 ; `mvn -o verify` vert
  (1 411 essais, JaCoCo par classe respecté, BDD 72/72). Restent AD-06 (API d'échange de l'addenda 5), AD-07 (offre en
  cascade) et AD-08 (démonstration de la convention).
