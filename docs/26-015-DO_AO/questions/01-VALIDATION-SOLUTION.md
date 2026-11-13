# 1. Valider la solution implémentée

But : faire confirmer par Urgences-santé que le comportement **déjà livré** correspond à son
intention. Chaque question se pose idéalement **en montrant l'écran** (démonstration 1.16.04 c ou
atelier), avec l'organisme de démonstration semé au contenu des planches
(voir [COMPTES-DEMONSTRATION.md](../../urgences-sante/COMPTES-DEMONSTRATION.md)).

Format de réponse attendu : **Confirmé** · **À corriger** (préciser) · **Hors périmètre**.

---

## 1.1 Modèle d'ensemble

| # | Moment | Exigences | Question | Notre hypothèse (livré) | Si la réponse diffère |
|---|---|---|---|---|---|
| V-01 | DÉMO | E-GN-1, E-GN-2 | L'unité planifiée est-elle bien **un équipage sur un poste-ligne**, avec plusieurs places (p. ex. deux paramédics par véhicule) et un **minimum opérationnel** par quart ? | Quart = poste-ligne × plage ; N places typées par titre d'emploi ; seuil minimal configurable (D-1) | Si l'unité est la personne seule, le comblement et le taux se recalculent autrement |
| V-02 | DÉMO | E-GN-7, E-GN-8 | Les **groupes organisationnels** à segmenter sont-ils CCS (répartiteurs), Interventions (paramédics), Logistique ? Y a-t-il des sous-niveaux (secteur, caserne) ? | Unités hiérarchiques datées ; règles et accès bornés par unité ; refus par défaut hors périmètre | Ajout de niveaux = paramétrage, pas développement |
| V-03 | DÉMO | E-GN-17, E-GP-7 | Le **milieu physique** d'un quart est-il un lieu fixe (centre opérationnel, caserne) ou un véhicule ? | Milieux = lieux avec coordonnées, rayon et mécanismes de preuve | Un véhicule mobile invalide la preuve par distance (voir X-43) |
| V-04 | ATELIER | E-TN-5 | Les **profils** d'utilisateur attendus correspondent-ils à : employé, planificateur, gestionnaire opérationnel, paie/RH, administrateur ? | Deux capacités serveur (`effectifs.configure`, `effectifs.manage`) + périmètre par unité (D-8) | Un profil supplémentaire (p. ex. lecture seule syndicale) = nouvelle capacité |

## 1.2 Confection des horaires — AGPH

| # | Moment | Exigences | Question | Notre hypothèse (livré) | Si la réponse diffère |
|---|---|---|---|---|---|
| V-05 | DÉMO | E-CH-1 | Le cycle d'une période — **préparation → collecte → confection → publiée → close** — reflète-t-il votre AGPH annuelle ? Manque-t-il une étape (consultation syndicale, affichage) ? | Cycle à 5 états, transitions contrôlées et journalisées | Une étape ajoutée modifie le cycle et ses gardes |
| V-06 | DÉMO | P-CH-1, P-CH-4 | Le **rang** au tour de rôle se fige-t-il par **ancienneté** au lancement de la campagne ? Quelle ancienneté (date d'embauche, d'accréditation, heures cumulées) ? | Rang figé par ancienneté à l'ouverture, non recalculé en cours de campagne | Autre critère = changement du calcul de rang |
| V-07 | DÉMO | P-CH-2 | Le **délai** accordé à chaque employé à son tour est-il fixe (combien de minutes/heures) ou variable selon le jour ? | Tour minuté, durée paramétrée par campagne | |
| V-08 | DÉMO | P-CH-2, P-CH-4 | À l'expiration du tour sans choix, faut-il **choisir automatiquement** pour l'employé (selon ses préférences déclarées) ou le **passer** ? | Choix automatique à l'expiration selon les disponibilités/préférences | Passer = l'employé perd son tour ; à confirmer avec le syndicat |
| V-09 | DÉMO | P-CH-5 à P-CH-7 | À la clôture : poste TC → statut **TC** ; poste TPR → **TPR** ; aucun poste → **TP**. Ces trois statuts suffisent-ils ? Y a-t-il des statuts occasionnel/liste de rappel ? | Trois statuts attribués à la clôture | Statut supplémentaire = référentiel, pas code |
| V-10 | DÉMO | E-CH-6, E-CH-9, E-CH-10 | Un poste resté vacant après l'AGPH bascule-t-il bien **en gestion régulière** (comblement), et peut-il rester non comblé sans blocage ? | Transfert automatique des quarts non couverts ; non-comblement admis et visible | |
| V-11 | DÉMO | E-CH-7, E-CH-8, P-CH-9 | Remplacement **long terme** : attribué au **plus ancien** des employés qui se sont manifestés et sont admissibles ? Durée minimale pour parler de « long terme » ? | Manifestations + règle INTERET_REMPLACEMENT ; plus ancien retenu | Si un affichage formel + délai est requis, le cycle s'allonge |

## 1.3 Gestion régulière, comblement, échanges

| # | Moment | Exigences | Question | Notre hypothèse (livré) | Si la réponse diffère |
|---|---|---|---|---|---|
| V-12 | DÉMO | P-GH-4, P-GN-1 | Ordre de priorité d'attribution automatique d'un quart disponible : **préférence déclarée → ancienneté → moins d'heures**. Est-ce l'ordre de votre convention ? | Ordre en trois critères, motifs rendus | Autre ordre = règle versionnée, paramétrage |
| V-13 | DÉMO | E-GN-4 | Le mode **automatique ou manuel** se choisit-il par quart, par poste-ligne ou par unité ? | Par quart (hérité du poste) | |
| V-14 | DÉMO | E-GN-11 | Attribution **forcée** par un utilisateur autorisé : le **motif écrit obligatoire** + trace complète suffit-il, ou faut-il une seconde approbation ? | Forçage permis, motif obligatoire, journalisé et publié au bus | Double approbation = étape supplémentaire |
| V-15 | DÉMO | E-GR-3 | L'**impact d'une absence** se lit-il comme le taux de comblement **avant → après** sur le quart et la période ? Autre indicateur attendu ? | Places rendues vacantes, taux avant/après | |
| V-16 | DÉMO | E-GR-8, E-GR-9 | Les **quotas** de comblement : par période, plage horaire ou type de quart — confirmez-vous les trois axes, et qu'une surcharge temporaire **n'agit pas rétroactivement** ? | Quotas datés + surcharges sans effet sur le passé | |
| V-17 | DÉMO | E-GR-7 | Un **échange** est jugé **dans les deux sens** (repos minimal, plafond hebdomadaire, contiguïté). Quels sont vos seuils (heures de repos entre deux quarts, maximum par semaine) ? | Règles ECHANGE paramétrables ; rejugées à l'acceptation | |
| V-18 | DÉMO | E-GR-12 | Échange entre périodes : **autorisé / soumis à approbation / interdit** — est-ce la granularité voulue ? | Règle ECHANGE sur `memePeriode` (D-9) | |
| V-19 | ATELIER | E-GR-5 | Les **ajustements journaliers** : qui les fait (répartiteur, superviseur de quart) et jusqu'à quand avant le quart ? | Ajustement du jour par un planificateur du périmètre | Rôle « superviseur de quart » à créer si distinct |

## 1.4 Disponibilités et absences

| # | Moment | Exigences | Question | Notre hypothèse (livré) | Si la réponse diffère |
|---|---|---|---|---|---|
| V-20 | DÉMO | E-GN-12, E-GN-15 | Une disponibilité non conforme doit-elle être **refusée** à la saisie, ou **acceptée avec avertissement** ? Selon quelle règle (statut, poste) ? | Les deux, au choix par règle : refus motivé (409), avertissement, ou approbation | |
| V-21 | DÉMO | E-GN-14, E-GN-16 | Le **motif d'exclusion** affiché au candidat écarté est-il assez explicite ? Peut-on l'afficher à l'employé lui-même ? | Bassin rendu avec motifs ; l'employé voit les siens | Restriction de diffusion = affaire de profil |
| V-22 | DÉMO | P-GN-2, P-GN-3, P-GH-2 | Une absence conforme est **acceptée automatiquement** ; une non conforme **refusée automatiquement** mais la décision est **conservée**. Confirmez-vous que le refus automatique est visible par l'employé ? | Décision automatique conservée, refus compris | |
| V-23 | DÉMO | E-GN-5, E-GN-6 | Après une **dérogation manuelle** (absence accordée malgré le refus), le **code d'absence** et le **comblement** se déclenchent automatiquement. Qui peut déroger ? | Dérogation motivée avec code ; places vacantes produites par un mécanisme journalisé | |
| V-24 | DÉMO | P-GH-1 | Les **codes d'absence admissibles par titre d'emploi** : pouvez-vous fournir la liste (maladie, CSST/CNESST, vacances, férié, formation, libération syndicale…) ? | Référentiel de codes par titre d'emploi | Données à charger, pas développement |
| V-25 | DÉMO | — | L'employé déclare son absence depuis **Mon espace › Mes absences**, et le gestionnaire la voit dans le même formulaire. Ce parcours unique vous convient-il ? | Formulaire unique (D-7, D-12) | |

## 1.5 Présence (« punch »)

| # | Moment | Exigences | Question | Notre hypothèse (livré) | Si la réponse diffère |
|---|---|---|---|---|---|
| V-26 | DÉMO | E-GP-1 | Fenêtre de pointage : un employé peut pointer **2 heures** autour du quart publié. Quelle fenêtre souhaitez-vous ? | Fenêtre de 2 h, séquence entrée → pauses → sortie | Paramétrage |
| V-27 | DÉMO | E-GP-10 à E-GP-13 | Mécanismes de preuve : **distance au milieu** (avec tolérance de précision plafonnée), **code QR de lieu** renouvelé aux 5 min, **réseau**. Lesquels activer, par milieu ? | Trois mécanismes, jugés par milieu ; écart → action configurable | Voir X-43 (mécanismes acceptables) |
| V-28 | DÉMO | E-GP-14 | Nous **ne conservons aucune position** : seulement le verdict (conforme / écart / mécanisme utilisé). Cela suffit-il à l'audit, ou la **coordonnée brute** doit-elle être consignée ? | Aucune position stockée (protection des renseignements) | Conserver la position exige une EFVP et une durée de rétention |
| V-29 | DÉMO | E-GP-9 | **Présence préalable rémunérée** (p. ex. vérification du véhicule avant le quart) : quelle durée, pour quelles natures de quart ? | Durée par nature de quart | |
| V-30 | DÉMO | E-GP-3, E-GP-15 | Saisie **a posteriori** : justification obligatoire et lot approuvé d'un geste. Qui peut saisir pour autrui et jusqu'à quel délai ? | Profil autorisé + justification | Délai maximal = règle à ajouter |
| V-31 | DÉMO | E-RF-13 | Horodatages externes (RAO) **corroborés à ±5 minutes** du pointage. Est-ce la tolérance voulue ? Quelle source prime en cas d'écart ? | ±5 min ; écart signalé, aucune source n'écrase l'autre | Priorité de source = règle à préciser |

## 1.6 Feuille de temps et paie

| # | Moment | Exigences | Question | Notre hypothèse (livré) | Si la réponse diffère |
|---|---|---|---|---|---|
| V-32 | DÉMO | E-RF-1 | Un quart **non pointé** apparaît dans la feuille au **temps planifié, signalé** comme anomalie. Est-ce le comportement voulu, ou doit-il être à zéro ? | Temps planifié + anomalie | |
| V-33 | DÉMO | E-RF-5 | Heures supplémentaires calculées **par jour puis par semaine, sans double compte**. Quels seuils (8 h/jour ? 40 h/semaine ? quarts de 12 h ?) et quels taux ? | Seuils paramétrables par règle | |
| V-34 | DÉMO | E-RF-6 | Types d'heures distingués : régulières, supplémentaires, fériées, préalables, primes (soir, nuit, fin de semaine) ? Fournir la liste attendue par la paie. | Segments typés, jours fériés au référentiel | Liste = paramétrage |
| V-35 | DÉMO | E-RF-4 | Approbation de la feuille à **1 à 3 paliers**, par des **personnes différentes**. Combien de paliers chez vous, et qui ? | 1–3 paliers configurables | |
| V-36 | DÉMO | E-RF-14 | Une correction après approbation crée une **nouvelle version** (l'ancienne reste lisible). Est-ce conforme à votre exigence de traçabilité paie ? | Segments versionnés | |
| V-37 | ATELIER | E-RF-12 | Le **lot de paie** est-il transmis au **PFT** (et non à un logiciel de paie directement) ? À quelle fréquence (par période de paie, quotidien) ? | Lot de versement au PFT par la boîte d'envoi | |

## 1.7 Rapports, audit, intégrations, options

| # | Moment | Exigences | Question | Notre hypothèse (livré) | Si la réponse diffère |
|---|---|---|---|---|---|
| V-38 | DÉMO | E-RF-7, P-RF-1 | Quels **rapports opérationnels** vous sont indispensables au premier jour (taux de comblement, heures supplémentaires, absentéisme, écarts de présence) ? | Rapports livrés : voir 08-VERIFICATION | Rapport manquant = ajout à prioriser |
| V-39 | DÉMO | E-GN-9, E-GN-10 | Le **journal** horodaté (auteur personne **ou mécanisme automatisé**, avant/après, motif) avec filtre et export CSV couvre-t-il votre besoin d'audit ? | Journal filtrable + CSV | |
| V-40 | DÉMO | P-TN-8 | Formats d'export : CSV, Excel, PDF, JSON — suffisant ? | Quatre formats | |
| V-41 | DÉMO | P-TN-9 | Personnalisation : nom, logo, **deux couleurs**, **refusées si le contraste tombe sous 4,5:1**. Acceptez-vous ce refus (accessibilité) ? | Contrôle de contraste à l'enregistrement | |
| V-42 | DÉMO | P-TN-10 | Alertes par courriel sur **tout téléchargement** de données (et autres événements) : quels événements et quels destinataires ? | Règles par événement | |
| V-43 | DÉMO | P-TN-11 | Restriction de connexion par **pays** : seulement le Canada ? Et les employés en vacances à l'étranger ? | Liste ISO par organisme | |
| V-44 | DÉMO | 17.08 | L'**assistant IA** est **désactivé par défaut** et absent de tout plan. Confirmez-vous qu'il doit rester désactivé, sauf autorisation écrite ? | Module `ia` hors plan (reprend Q-13) | Voir K-20 |
| V-45 | DÉMO | E-TN-21, E-TN-22 | Interface en français, adaptative (bureau, tablette, mobile ; Chrome et Edge), conforme **SGQRI 008 / WCAG 2.2 AA**. Y a-t-il des appareils imposés (tablettes véhicules, terminaux MDT) à essayer ? | Audit à 390, 768, 1280, 1920 px | Un terminal embarqué doit être ajouté aux essais |
| V-46 | DÉMO | P-TN-2, E-TN-2 | Authentification par **Microsoft Entra ID**, plusieurs annuaires possibles. Un seul locataire Entra chez vous, ou plusieurs (partenaires, CISSS) ? | SSO Entra multi-locataire + SAML | |

## 1.8 Les seuils que nos essais emploient — à remplacer par les vôtres

Les essais de comportement (`e2e/donnees/urgences-sante/comportements.json`, joués contre le
backend Java) prouvent que **le moteur applique une règle**. Les valeurs qu'ils emploient sont des
exemples. Chacune doit être remplacée par la valeur de la convention ou de l'entente locale avant
la mise en production — sans quoi le produit applique fidèlement une règle que personne n'a votée.

| # | Moment | Exigences | Question | Valeur d'essai | Si la réponse diffère |
|---|---|---|---|---|---|
| V-47 | ATELIER | E-GN-11, E-GR-7 | **Repos minimal** entre deux quarts : combien d'heures, et la règle **bloque**-t-elle ou exige-t-elle une approbation ? | 8 h, bloquante (forçage justifié possible) | Paramétrage d'une règle ATTRIBUTION |
| V-48 | ATELIER | P-GN-2, E-GR-3 | Sous quel **taux de comblement** une absence doit-elle être **refusée d'office** ? Le seuil varie-t-il selon le site, la plage ou la saison ? | 60 % | Règle ABSENCE, une par portée |
| V-49 | ATELIER | E-GN-12, E-GN-15 | **Nombre minimal de quarts** à offrir par semaine dans un dépôt de disponibilités, par statut (TC, TPR, TP) ? | 2 quarts, bloquant | Règle DISPONIBILITE par statut |
| V-50 | ATELIER | E-GN-15 | Sous quel **nombre d'heures offertes** par période faut-il **avertir** sans bloquer ? | 30 h | Règle DISPONIBILITE, action AVERTIR |
| V-51 | ATELIER | E-GN-5 | Longueur minimale du **motif** d'une dérogation ou d'un forçage : 10 caractères suffisent-ils, ou faut-il une liste de motifs normalisés ? | 10 caractères, texte libre | Une liste fermée devient un référentiel |
| V-52 | DÉMO | E-GR-2 | Après l'AGPH, les quarts d'un **poste resté vacant** naissent sans titulaire, sous leur minimum, et la fin de collecte les attribue d'elle-même aux disponibilités déposées. Est-ce le circuit voulu, ou ces quarts doivent-ils d'abord être **affichés** comme remplacement ? | Attribution automatique en fin de collecte | Un affichage préalable ajoute une étape au cycle |
| V-53 | DÉMO | P-GH-1 | Un code d'absence peut être **réservé à des titres d'emploi** (p. ex. « Formation PSA »). Quels codes sont réservés, et à quels titres ? | « FORM » réservé au titre PSA | Données du référentiel des codes |
| V-54 | DÉMO | E-GN-8 | Un gestionnaire **sans périmètre** n'a accès à rien (refus par défaut). Qui attribue les périmètres, et à quel niveau (lecture, planification, gestion) chaque rôle a-t-il droit ? | Refus par défaut, périmètre par unité | Matrice rôles × niveaux à fournir |
