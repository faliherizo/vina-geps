# 26-015-DO — Besoins fonctionnels et analyse des écarts

> Ce que demande Urgences-santé, regroupé en capacités métier ; ce que Nexu VINA en fait déjà,
> preuves à l'appui ; ce qui manque, et pourquoi c'est plus structurel qu'il n'y paraît.
>
> **Révision du 8 novembre 2026** — les chemins de code désignent désormais le **backend Java**
> (`backend-java/src/main/java/ca/vina/nexu/…`, la cible : CLAUDE.md, « Quel backend ? ») et les
> preuves de la matrice sont des essais Java. Les verdicts des §§ 2 et 4 sont ceux du 29 septembre,
> conservés comme point de départ de l'analyse ; l'état courant est dans la matrice (90/90 exigences
> fonctionnelles acquises). Le § 5 décrit le code **mesuré le 29 septembre** dans le Node, qui a
> motivé les référentiels datés d'`effectifs` : il reste daté, et son équivalent Java est le
> référentiel `eff_referentiels` (`ca.vina.nexu.effectifs.domaine`).
>
> Mesure du **29 septembre 2026** sur la branche `livraison/refonte-ui-mobile-java-100`. Le
> détail par exigence (texte, verdict, preuves, écart) est généré dans
> [MATRICE-COUVERTURE.md](MATRICE-COUVERTURE.md) :
>
> ```bash
> python3 docs/26-015-DO_AO/convertir-ao.py         # PDF → Markdown + exigences-26-015-DO.json
> python3 docs/26-015-DO_AO/construire-couverture.py  # jugements → matrice et décomptes
> ```

---

## 1. Le métier à servir

Urgences-santé répond à ≈ 1 000 appels 911 par jour et réalise ≈ 700 transports ambulanciers
quotidiens à Montréal et Laval. Ses effectifs tournent **24 h sur 24** : paramédics en équipage,
répartiteurs médicaux d'urgence au centre de communication santé (CCS), logistique. Trois faits
dictent la solution :

1. **Les règles viennent des conventions collectives et des ententes locales**, pas de
   l'organisme : nombre et durée des quarts, ancienneté, tour de rôle, statuts d'emploi. Elles
   changent à chaque renouvellement de convention — le cahier (2.5) prévoit des services
   professionnels exactement pour ça. **Une règle codée en dur est une règle à redévelopper.**
2. **Le processus annuel AGPH** (attribution des postes par tour de rôle d'ancienneté) structure
   l'année ; la « gestion régulière » comble ensuite ce que l'AGPH laisse ouvert.
3. **Un quart non couvert est un véhicule qui ne sort pas.** D'où le minimum opérationnel
   (E-GN-2), les quotas de comblement (E-GR-8) et un SLA à 99,7 %.

Le système remplace plusieurs systèmes du réseau de la santé en fin de vie et s'insère dans un
écosystème : **RAO** (répartition assistée par ordinateur), **SAAV** (attitrage automatique des
véhicules), **PFT** (production des feuilles de temps, qui reçoit les horaires réels) et **BD Corpo**
(dossier employé, banques de congés et d'absences), via **Microsoft Entra ID** et une passerelle
**Microsoft Graph** bidirectionnelle (cahier 2.2-2.3, 4).

## 2. Les besoins, regroupés en capacités

Les 144 lignes de la grille se ramènent à **quinze capacités fonctionnelles** et **six familles
non fonctionnelles**. Légende : ✅ acquis · 🟡 partiel · ⛔ à bâtir · 🔒 hors code.
**E** = essentiel (éliminatoire), **P** = préférentiel.

### BF-01 — Organisation et périmètres

| # | Besoin | État |
|---|---|---|
| E-GN-7 | Segmenter la planification **et les règles** par groupe organisationnel | 🟡 groupes plats ; absences et règles communes à tout l'organisme |
| E-GN-8 | Restreindre l'accès et les opérations d'un utilisateur à des groupes précis | 🟡 les routes cœur ne vérifient que le rôle |
| E-GN-17 | Identifier le **milieu physique** d'un quart | ⛔ |

### BF-02 — Traçabilité

| # | Besoin | État |
|---|---|---|
| E-GN-9 | Consigner, horodatée, toute action ou décision touchant horaires, quarts, absences, paramètres ou temps, **humaine ou automatique** | 🟡 le moteur ne journalise pas ses étapes automatiques |
| E-GN-10 | Auditer et consulter a posteriori les traces d'exécution et de paramétrage | 🟡 aucun filtre acteur/date/action, aucun export |

### BF-03 — Quarts et équipages

| # | Besoin | État |
|---|---|---|
| E-GN-1 | Quart à une ou plusieurs ressources **selon le corps d'emploi** | 🟡 une tâche = un seul type de ressource |
| E-GN-2 | **Minimum de ressources** pour qu'un quart soit opérationnel | ⛔ |
| E-GN-3, E-GR-6, E-GR-13 | Quarts **ad hoc**, sans poste | 🟡 un quart exige une tâche |
| E-GN-4 | Mode d'attribution **automatisé ou manuel** choisi par quart | 🟡 |
| E-GR-5 | Ajustements journaliers | 🟡 on change le titulaire, pas les heures |

### BF-04 — Postes

| # | Besoin | État |
|---|---|---|
| E-CH-2, E-CH-3 | Créer et modifier (en lot) quarts et postes liés | 🟡 |
| E-CH-4 | Publier les postes | 🟡 publication par période entière |
| E-CH-5 | Paramètres fonctionnels de chaque poste | 🟡 conditions en enums |

### BF-05 — Disponibilités et leur conformité

| # | Besoin | État |
|---|---|---|
| E-GN-13, E-GR-11 | Déposer et ajuster ses disponibilités **par période ouverte** | 🟡 récurrentes hebdomadaires seulement |
| E-GN-12 | **Règles de conformité** des disponibilités par convention, statut, type de poste, contexte | ⛔ |
| E-GN-15 | Valider à la saisie ; **bloquer ou signaler** selon un réglage | ⛔ |
| E-GN-16 | Dire à l'employé **pourquoi** il est inadmissible | 🟡 motifs pour l'admissibilité au quart seulement |
| E-GN-14 | Exclure du bassin de candidats une disponibilité non conforme | 🟡 |
| P-GH-4 | En fin de collecte, attribuer automatiquement les quarts aux disponibilités correspondantes | ⛔ |

### BF-06 — Périodes et cycle de planification

| # | Besoin | État |
|---|---|---|
| E-GR-10 | Périodes avec **ouverture et fermeture contrôlées** | 🟡 cycle brouillon → publiée → close codé en dur |
| E-GR-1 | Gestion continue hors AGPH | 🟡 aucun cycle ne distingue AGPH et gestion régulière |

### BF-07 — AGPH : attribution annuelle des postes

| # | Besoin | État |
|---|---|---|
| E-CH-1 | Processus annuel structuré, disponibilités émises selon les règles de convention (nombre, durée, budgets), paramétrable par le service de planification | ⛔ |
| P-CH-1 | Voir les postes encore disponibles **à son tour de rôle** | ⛔ |
| P-CH-2 | Choisir **un seul** poste dans un délai défini | ⛔ |
| P-CH-3 | Empêcher un choix non admissible | ⛔ |
| P-CH-4 | Attribuer automatiquement selon le tour de rôle | ⛔ |
| P-CH-5 à P-CH-7 | Statut **TC**, **TPR** ou **temps partiel** selon le poste obtenu | ⛔ |

### BF-08 — Remplacements long terme et reliquats

| # | Besoin | État |
|---|---|---|
| E-CH-6 | Détecter les postes vacants causés par des absences planifiées | 🟡 le scolaire le fait, pas les horaires |
| E-CH-7, E-CH-8 | Ouvrir un processus de remplacement long terme ; **manifestation d'intérêt** | 🟡 / ⛔ |
| E-CH-9 | Laisser un remplacement non comblé | 🟡 |
| E-CH-10, E-GR-2 | Transférer les postes non couverts vers la gestion régulière | ⛔ / 🟡 |
| P-CH-9 | Attribuer automatiquement selon les règles | ⛔ |

### BF-09 — Absences

| # | Besoin | État |
|---|---|---|
| P-GH-1 | Déclaration d'absence paramétrable **selon le titre d'emploi** | 🟡 |
| P-GN-2, P-GH-2 | Validation automatique de l'admissibilité et des restrictions | 🟡 cinq champs de règle codés |
| P-GN-3 | **Acceptation automatique** si toutes les règles sont respectées | 🟡 scolaire seulement |
| E-GN-5 | **Dérogation manuelle** à un refus automatique, avec le code d'absence applicable | ⛔ un refus automatique n'est même pas conservé |
| P-CH-8 | Codes d'absence planifiés de moyen ou long terme | 🟡 |

### BF-10 — Comblement

| # | Besoin | État |
|---|---|---|
| E-GN-6 | Appliquer les règles de comblement **dès** qu'une absence est autorisée | 🟡 |
| E-GR-3 | Évaluer l'impact d'une absence sur le **taux de comblement** | ⛔ |
| E-GR-4 | Identifier automatiquement les quarts à offrir | 🟡 |
| E-GR-8, E-GR-9 | **Quotas** de comblement par période, plage ou type de quart ; ajustement temporaire **non rétroactif** | ⛔ |
| P-GN-1 | Attribuer automatiquement à la ressource prioritaire | 🟡 le moteur clôt la recherche sans poser le titulaire |
| P-GH-3 | Aviser les ressources admissibles | 🟡 scolaire seulement |
| E-GN-11 | Attribution manuelle d'exception malgré les règles de priorité, **tracée** | 🟡 pas de justification obligatoire ; l'IHM n'envoie jamais `forcer` |

### BF-11 — Échanges de quarts

| # | Besoin | État |
|---|---|---|
| E-GR-7 | Échanges avec validation d'admissibilité **et de contiguïté** | 🟡 admissibilité vérifiée dans un seul sens |
| E-GR-12 | Échanges entre périodes : autorisés, restreints ou interdits, **au choix** | ⛔ un échange ne porte que sur une période |

### BF-12 — Pointage et présence

| # | Besoin | État |
|---|---|---|
| E-GP-1 | Déclaration des heures **en temps réel**, selon des profils configurables | ⛔ le pointage est importé, jamais saisi |
| E-GP-2 | Pauses, repas, repos non payé | ⛔ |
| E-GP-4, P-GP-2 | Valider les heures contre les règles du quart | 🟡 |
| E-GP-7 | Présence **multi-site** | ⛔ |
| E-GP-9 | Présence préalable **rémunérée** avant le quart | ⛔ |
| E-GP-10 à E-GP-14 | Valider l'emplacement, un ou plusieurs mécanismes de preuve de présence, réglages, action sur écart, **journal des localisations** | ⛔ ×5 |
| E-GP-15, P-GP-1 | Saisie a posteriori par un utilisateur autorisé | 🟡 |
| P-GP-3, P-GP-4 | Plusieurs segments sur un quart exceptionnel, cumul cohérent | ⛔ / 🟡 |

### BF-13 — Feuille de temps

| # | Besoin | État |
|---|---|---|
| E-RF-1 | Feuille **préparée automatiquement** depuis les horaires et affectations | 🟡 affichée, jamais générée |
| E-RF-2 | Saisie manuelle ou automatisée | 🟡 |
| E-RF-3, E-RF-11, E-GP-5 | Validation **en temps réel**, anomalies affichées dans la feuille | 🟡 |
| E-RF-4 | Approbation selon des profils autorisés | ✅ |
| E-GP-6 | Approbation selon des **flux configurables** | 🟡 le moteur de flux existe mais n'est pas branché |
| E-RF-5 | Heures supplémentaires **évaluées automatiquement** | 🟡 offres HS, pas de calcul par seuil |
| E-RF-6 | Types d'heures distingués | 🟡 enums codés |
| E-GP-8 | Heures planifiées contre déclarées | 🟡 |
| E-RF-9, E-RF-10 | Détection d'anomalies ; **règles de contrôle temporel configurables** | 🟡 seuils codés |
| E-GP-3, E-RF-14 | Modification encadrée par des règles et des profils | 🟡 |

### BF-14 — Rapports et exports

| # | Besoin | État |
|---|---|---|
| E-RF-7 | Rapports opérationnels | 🟡 aucun rapport de temps et de présence |
| E-RF-8, P-TN-8 | Exports standards (Excel, PDF, CSV, JSON) | ✅ |
| P-RF-1 | Relevés statistiques selon des paramètres de l'organisation | 🟡 absences seulement |

### BF-15 — Paie et sources externes

| # | Besoin | État |
|---|---|---|
| E-RF-12 | Données de temps structurées pour la paie (PFT) | 🟡 aucun export ni code de paie |
| E-RF-13 | **Corréler des horodatages de plusieurs logiciels** (RAO, SAAV…) | 🟡 une seule source, réglée par variables d'environnement |

### Besoins non fonctionnels

| Famille | Exigences | Acquis | Écart décisif |
|---|---|---|---|
| **SaaS et exploitation** | E-TN-1, 3, 4, 5, 6, 14, 18, 30 ; P-TN-1 | multilocataire, catalogue de modules, rôles | calendrier d'évolution, **environnement de formation**, sauvegardes séparées par organisme |
| **Disponibilité et reprise** | E-TN-9, 15, 16 ; P-TN-3 à 6 ; Contrat 6.01 | sauvegardes 15 min, essai de restauration, maintenance planifiée | **base mono-zone, RTO 48 h : incompatible avec 99,7 %** ; relève à niveau de service égal |
| **Sécurité** | E-TN-2, 8, 23, 24, 31 ; P-TN-2, 10, 11, 12 ; Contrat 17.07 | Entra ID, WAF, audit chaîné, destruction attestée | IDS, plan DDoS, **zone d'accueil MCN**, alertes par courriel, restriction par pays, **plusieurs annuaires** |
| **Données** | E-TN-13, 19 ; Contrat 2.06, 6.03, 6.05, 6.06 | Québec (ADR 0043), API OpenAPI 3 | **rétention année + 5**, dictionnaire de données généré, plan de sortie, journaux CloudWatch 90 → 365 jours |
| **Intégration** | E-TN-7, 20 ; P-TN-7 ; Cahier 2.2-2.3 | OpenAPI, webhooks signés, clients OAuth2, ETL | **ESB**, **RAO, SAAV, PFT, BD Corpo**, **Microsoft Graph**, entrepôt de données |
| **Usage et langue** | E-TN-10, 17, 21, 22, 25, 26, 27 ; P-TN-9 | français, adaptatif, < 2 s, Chrome et Edge | **logo et couleurs par organisme**, guide d'administration, gabarits de messages éditables |
| **IA** | Contrat 17.08 | assistant IA existant | **non désactivable par organisme** : il doit l'être, et désactivé par défaut |

## 3. Ce qui existe déjà et sera réutilisé

L'analyse ne part pas de zéro. Trois chantiers antérieurs ont bâti l'essentiel du socle :
l'AO Santé Québec 26-432315-1226 (horaires cliniques, lots L1-L8), la planification ouvrière
(lots P-1 à P-6, ADR 0032) et les remplacements scolaires CSSDC (moteur de recherche séquentiel,
tour de rôle, disponibilités, SMS).

| Module existant | Ce qu'il apporte à 26-015-DO | Où |
|---|---|---|
| **horaires** | groupes, périodes publiées/closes, tâches, cases et besoins, génération des quarts, admissibilité avec motifs, contraintes dures/souples, optimiseur de scénarios, **moteur de recherche de remplaçants** (séquentiel/simultané, tour de rôle, délai de réponse), échanges par segments, HS, pointage importé, fériés, vues publiques et iCal, messagerie, console téléphonique, journal `horaire_journal` | `ca.vina.nexu.horaires`, `frontend/src/Horaires.tsx` (20 onglets) |
| **scolaire** | la chaîne complète absence → besoin → recherche → attribution, campagnes de disponibilités, avis SMS/courriel/poussée, corps d'emploi et qualifications | `ca.vina.nexu.scolaire` |
| **absences** | façade unique `/api/absences` (ADR 0026), types d'absence (effet payé/non payé/banque, justificatif), pièces | `ca.vina.nexu.absences` (façade, ADR 0026) |
| **payroll / rh** | feuilles de temps hebdomadaires, approbation hiérarchique et **journalière** (contremaître), banque d'heures, banques de congés, postes RH | `ca.vina.nexu.paie` (feuilles, journées, banque d'heures), `ca.vina.nexu.rh` (banques) |
| **rh_regles** | un premier moteur de règles (contexte, champ, opérateur, valeur, action, priorité, dates d'effet) | `ca.vina.nexu.rh` |
| **flux / approvals** | flux de travail paramétrables (`flx_*`), règles d'approbation | `ca.vina.nexu.approbations` ; **`flux` n'est pas encore porté en Java** (lot mis de côté le 8 novembre 2026) |
| **ordonnanceur** | tâches différées transactionnelles, reprise après panne (ADR 0014) | `ca.vina.nexu.ordonnanceur` |
| **notifications** | courriel, texto, appel, poussée web et native ; catalogue d'événements et préférences (ADR 0029) | `ca.vina.nexu.notifications`, port `ca.vina.nexu.partage.avis` |
| **journal / audit** | journal des actions, audit chaîné SHA-256, rétention 365 jours | `ca.vina.nexu.partage.journal`, `ca.vina.nexu.partage.audit` |
| **saas / auth** | multilocataire, catalogue de modules, rôles personnalisés, **Entra ID (OIDC + PKCE)**, SAML | `ca.vina.nexu.saas`, `ca.vina.nexu.auth` (`ControleurSso`, OIDC Entra) |
| **soutien** | billets N1-N4, fenêtres de maintenance, mesure de disponibilité | `ca.vina.nexu.soutien` |
| **bi / rapports / etl** | exports 7 formats, cubes, constructeur de rapports, chargements | `ca.vina.nexu.rapports`, sérialiseurs `ca.vina.nexu.partage.export` ; **`bi` et `etl` ne sont pas encore portés en Java** |
| **integrations** | webhooks signés avec reprises, clients API OAuth2, **patron « port + simulateur + adaptateur + tests de contrat »** | `ca.vina.nexu.integrations` |
| **mobile** | application Expo : disponibilités, propositions, approbations, feuilles | `mobile/src/ecrans/*` |

## 4. Les écarts, chiffrés

Décompte produit par `construire-couverture.py` :

| Type | ✅ Acquis | 🟡 Partiel | ⛔ À bâtir | 🔒 Hors code | Total |
|---|---:|---:|---:|---:|---:|
| Essentiels (un « Non » élimine) | 13 | 60 | 22 | 5 | 100 |
| Préférentiels (25 % de la qualité) | 4 | 15 | 14 | 11 | 44 |

**Lecture honnête** : dans une grille où **chaque ligne essentielle est éliminatoire**, « partiel »
vaut « Non ». Répondre aujourd'hui, c'est répondre « Non » à **82 critères essentiels** (60 + 22) et
dépendre de faits commerciaux pour 5 autres. La richesse du socle ne change pas ce compte ; elle
change le **coût** de le ramener à zéro.

Les écarts techniques ne sont pas répartis au hasard. Sept causes en expliquent la plupart :

| # | Cause structurelle | Exigences essentielles touchées | Pourquoi c'est structurel |
|---|---|---|---|
| **G1** | **Le temps n'est qu'une quantité d'heures.** `time_entries` ne porte ni début, ni fin, ni segment, ni lien au quart | E-GP-1, 2, 4, 8, 15 ; E-RF-1, 2, 3, 5, 9, 11 ; P-GP-3, 4 | Pointage, pauses, segments, écart planifié/déclaré et HS par seuil reposent tous sur un **segment de temps** qui n'existe pas. Il faut un nouveau modèle, pas une colonne. |
| **G2** | **Aucun lieu.** Ni milieu physique sur le quart, ni localisation sur le pointage ; le pare-feu interdit même la géolocalisation (`Permissions-Policy: geolocation=()`) | E-GN-17 ; E-GP-7, 10, 11, 12, 13, 14 | Six critères éliminatoires dépendent d'un référentiel de lieux et d'une preuve de présence. |
| **G3** | **L'AGPH n'existe pas.** Aucun cycle annuel, aucun tour de rôle de choix, aucun statut d'emploi attribué | E-CH-1, 7, 8, 9, 10 ; E-GR-1, 2 ; P-CH-1 à 9 | C'est le cœur métier de l'avis ; le moteur de recherche classe des **personnes** pour un **quart**, l'AGPH fait choisir des **postes** à des personnes. |
| **G4** | **Absences et horaires ne se parlent pas.** Une absence approuvée ne libère aucun quart côté horaires ; un refus automatique n'est pas conservé | E-GN-5, 6 ; E-CH-6 ; E-GR-3, 4 | Le scolaire a cette chaîne ; les horaires, non. Sans elle, ni dérogation, ni impact sur le comblement. |
| **G5** | **Périmètre par groupe non appliqué.** Un gestionnaire voit et modifie tous les groupes | E-GN-7, 8 | C'est aussi un **défaut de sécurité** (refus par défaut non respecté) : à corriger quel que soit le sort de l'avis. |
| **G6** | **Les règles et les listes sont du code.** Au moins 25 ensembles codés en dur (§ 5) là où la grille dit « configurable » ou « paramétrable » | E-GN-12, 15 ; E-GR-8, 9, 10, 12 ; E-GP-1, 3, 12 ; E-RF-6, 9, 10 | Chaque convention collective renégociée deviendrait un développement. |
| **G7** | **L'infrastructure vise un RTO de 48 h.** Base mono-zone (`multi_az = false`), objectif interne 99,5 % | E-TN-9 ; Contrat 6.01 | L'ADR 0043 a écarté la haute disponibilité pour le CSSDC ; ici elle est contractuelle, avec crédits et résiliation. |

À quoi s'ajoutent, hors grille mais au contrat ou au cahier : **les interfaces RAO, SAAV, PFT, BD
Corpo et Microsoft Graph** (aucune n'existe), **la rétention année courante + 5 ans**, **le plan de
sortie**, et **l'IA non désactivable** (Contrat 17.08).

## 5. Ce qui est codé en dur et doit devenir donnée paramétrable

L'avis répète « paramétrable », « configurable », « selon des profils configurables », « selon les
règles définies par l'organisation ». Les audits ont relevé les ensembles suivants.

| Aujourd'hui (code) | Où | Devient | Exigences |
|---|---|---|---|
| `TimeType` J/S/N/F, `TIME_CATEGORIES` | `payroll/store.ts` | référentiel **types d'heures** (code de paie, majoration, comptabilisation HS) | E-RF-6, E-RF-12 |
| nature du quart régulier/remplacement/hs | `horaires/service.ts` | référentiel **natures de quart** | E-GN-3, E-GR-13 |
| `StatutPeriode` et ses transitions | `horaires/domaine.ts` | **cycle de période** paramétrable (états d'organisme mappés sur des comportements fixes) | E-GR-10, E-GR-11 |
| `PLAGES` 06/14/22 h, `plageDe` | `horaires/planification.ts` | référentiel **plages horaires** | E-GR-8 |
| `CategorieQuart`, seuil de nuit 20 h | `horaires/regles.ts` | paramètre de groupe | E-GR-8, E-RF-5 |
| `CodeMotif` | `horaires/domaine.ts` | catalogue **motifs d'inadmissibilité** (libellés traduits) | E-GN-16 |
| `CritereTri`, `ModeTraitement` | `horaires/remplacements.ts` | stratégies de comblement **nommées** et paramétrées | E-GN-4, P-GN-1 |
| `CHAMPS` (5 champs) de `rh_regles` | `rh/*` | **catalogue de faits** exposés au moteur de règles | E-GN-12, P-GN-2 |
| `NATURES_BESOIN`, `seuilJoursConversion` | `scolaire/*` | **catégories de durée** des codes d'absence (ponctuelle, moyen, long terme) | P-CH-8, E-CH-7 |
| seuils d'anomalie 125 %, 50 %, 12 h | `payroll/timesheets.ts` | **règles de contrôle temporel** | E-RF-9, E-RF-10 |
| `ARRONDIS_ADMIS` [1, 5, 15], `GRACE_MAX` | `horaires/pointage.ts` | **profils de pointage** | E-GP-1, E-GP-12 |
| `MAJORATION_PAR_DEFAUT_POURCENT` 150 | `horaires/heuresSupplementaires.ts` | **règles d'heures supplémentaires** (seuils jour/semaine/période, taux) | E-RF-5 |
| source de pointage par variables `POINTAGE_*` | `horaires/servicePointage.ts` | **sources d'horodatage** déclarées par organisme | E-RF-13 |
| cible de disponibilité 99,5 %, délais N1-N4 | `soutien/domaine.ts` | **niveaux de service** par contrat | Contrat 5.02, 6.01 |
| nom « Nexu VINA » | `frontend/src/App.tsx` | **charte graphique** par organisme | P-TN-9 |

**Ce qui reste un enum**, parce que le moteur lui donne un sens : le sens d'un pointage
(entrée/sortie), le comportement d'un état de période (saisie ouverte, figé, clos), l'effet d'une
action de règle (bloquer, avertir, exiger une approbation, accepter). Ce sont des **ensembles
fermés du logiciel** ; la norme Java (ADR 0030) les veut en enum. Ce que l'organisme nomme,
ordonne et règle devient une **table** dont chaque ligne pointe vers l'un de ces comportements. La
règle est détaillée dans le document [03, § 3](03-CONCEPTION-SOLUTION-LOGICIELLE.md#3-le-principe-qui-rend-les-données-dynamiques).

## 6. Clauses du contrat et du cahier hors grille

| Clause | Exigence | État | Lot |
|---|---|---|---|
| Cahier 2.2 | Passerelle **Microsoft Graph** bidirectionnelle | ⛔ conception seulement (`docs/integration-microsoft-365-sharepoint-teams.md`) | U8 |
| Cahier 2.3 | Interfaces **RAO** et **SAAV** | ⛔ | U8 |
| Cahier 2.3 | **PFT** : téléverser les horaires réels | 🟡 feuilles de temps, pas de format PFT | U7-U8 |
| Cahier 2.3 | **BD Corpo** : dossier employé, banques de congés et d'absences | 🟡 banques internes, pas de synchronisation | U8 |
| Contrat 6.01 | Disponibilité mensuelle ≥ 99,7 %, rapport mensuel des incidents | 🟡 cible codée 99,5 %, infrastructure mono-zone | U0 |
| Contrat 6.03 | Journaux ≥ 12 mois | 🟡 applicatif 365 j ; CloudWatch 90 j | U0 |
| Contrat 2.06.04 | Conservation année courante + 5 ans, accès direct + 2 ans, archives ensuite | ⛔ aucune politique par classe de données | U0 |
| Contrat 6.05-6.06 | Dictionnaire et schéma de données, plan de transition à la sortie | 🟡 exports complets ; ni dictionnaire ni plan | U0 |
| Contrat 17.08 | IA soumise à autorisation écrite | ⛔ `/api/ai` sans garde de module | U0 |
| Contrat 5.02 | Soutien 5 h-23 h 7/7, réponse et résolution par priorité | 🟡 niveaux N1-N4 existants, délais codés et différents | U0 |
| Contrat 2.05 | Deux environnements (essais-formation, production) | 🟡 pas d'environnement de formation dédié | U0 |

## 7. Ce que coûte la fermeture des écarts

Estimations établies sur la vitesse observée des lots STO et AI-2026-03 (domaine, persistance,
routes, jumeau Java, écrans, essais et couverture ≥ 96 % compris). Le séquencement et le contenu
de chaque lot sont au document [03, § 10](03-CONCEPTION-SOLUTION-LOGICIELLE.md#10-lots-de-réalisation).

| Lot | Objet | Essentiels ouverts | Effort (jours-personne) |
|---|---|---:|---:|
| U0 | Socle de conformité : haute disponibilité, IA par organisme, charte, rétention, environnement de formation, sécurité | 14 | 25 |
| U1 | Organisation hiérarchique, périmètres, milieux, référentiels, journal unifié | 5 | 15 |
| U2 | Quarts, postes, équipages, minimum opérationnel | 11 | 18 |
| U3 | Moteur de règles, disponibilités par période, quotas, surcharges, échanges | 12 | 22 |
| U4 | AGPH et remplacements long terme | 7 | 25 |
| U5 | Absences et comblement | 6 | 15 |
| U6 | Pointage et présence | 12 | 25 |
| U7 | Feuille de temps, HS, contrôles et rapports | 13 | 20 |
| U8 | Intégrations RAO, SAAV, PFT, BD Corpo, Graph, ESB, entrepôt | 2 (+ cahier) | 30 |
| | **Total** | **82** | **≈ 195** |

**Face au calendrier** : le dépôt est dans **35 jours civils** (25 jours ouvrables). Fermer les
82 critères ouverts **avant** le dépôt demanderait ≈ 165 jours-personne (U0 à U7) en 25 jours
ouvrables, soit sept personnes à plein temps sans aucun imprévu d'intégration — et ne réglerait
toujours pas E-TN-28 et E-TN-29. Les 16 semaines d'implantation du contrat (2.03) servent à
configurer et intégrer un produit **conforme au dépôt**, pas à le construire.

La recommandation, détaillée au [README](README.md), en découle : décider d'abord sur E-TN-28 et
E-TN-29 ; si la réponse est non, conduire U1 à U7 comme feuille de route produit pour le prochain
avis de ce type, en commençant par **G5** (un défaut de sécurité) et **G1** (le modèle de temps dont
dépend tout le reste).
