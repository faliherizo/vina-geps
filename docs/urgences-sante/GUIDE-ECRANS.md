# VINA GePS : guide écran par écran

> Écrit pour les utilisateurs et les formateurs d'Urgences-santé. Pour chaque écran : à quoi il
> sert, qui l'ouvre, comment s'y prendre et un exemple de remplissage avec les libellés réels.

<!-- Généré par frontend/scripts/generer-guide-ecrans.mjs depuis frontend/src/aide/fichesEcrans.ts : ne pas modifier à la main. -->

Dans l'application, le bouton **« Aide sur cet écran »** ouvre directement la fiche de l'écran
courant (`/admin/help?section=fiche-<écran>`). Les exemples reprennent l'organisme de
démonstration ([COMPTES-DEMONSTRATION.md](COMPTES-DEMONSTRATION.md)).

## Menu principal

### Tableau de bord

*Adresse : `/admin/dashboard` — Tous les comptes ; la vue gestionnaire s'ouvre à l'administration, à la direction et aux planificateurs.*

Lire sa journée : pour l'encadrement, la couverture des quarts, les absences à traiter, les demandes en attente et les pointages à approuver ; pour l'employé, ses sept prochains jours.

**Avant de commencer**

1. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : tout rôle pour la vue personnelle ; administration, direction ou gestionnaire pour la vue d'encadrement. (écran `/admin/admin`)
2. Les tuiles lisent les modules des effectifs accordés à l'organisme (« effectifs_planification », « effectifs_absences », « effectifs_temps »…) : un module absent affiche « — », jamais zéro.
3. L'administration donne au compte de l'encadrement un périmètre sur l'unité, au niveau Lecture au moins (Administration › Paramètres des effectifs › Périmètres) : les chiffres d'un gestionnaire ne comptent que les unités de son périmètre. Un compte d'administration voit toutes les unités sans périmètre. (écran `/admin/usperimetres`)
4. Un planificateur (niveau Gestion sur l'unité) ou l'administration crée la ressource de la personne dans Menu principal › Gestion des quarts › Ressources — matricule, nom, unité, corps d'emploi — et choisit son « Compte utilisateur ». C'est ce lien qui manque quand l'écran répond « votre compte n'est relié à aucune ressource planifiable : demandez-le à votre planificateur ». (écran `/admin/usressources`)
5. Un planificateur publie les postes de l'unité (Gestion des quarts › Postes), crée la période (Gestion des quarts › Périodes), la passe en confection, génère ses quarts, attribue les places aux personnes (Quarts et équipages ou Comblement), puis publie la période : seuls les quarts d'une période publiée comptent ici. (écran `/admin/usperiodes`)

**Comment faire**

1. Lire les quatre tuiles : chacune est un bouton qui ouvre l'onglet correspondant.
2. Dans « Couverture des quarts », les quarts à combler sont en tête ; « Combler » mène à l'écran de comblement.
3. Dans les autres onglets, « Décider », « Valider » ou « Examiner » mène à l'écran où l'on agit.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Tuile « Quarts aujourd'hui » | 24 — dont « 8 place(s) à combler » |
| Onglet | Couverture des quarts |
| Ligne | 14:00–22:00 · Nord · 1 / 3 · Sous le minimum → Combler |

**Résultat :** Rien ne s'écrit ici : le tableau de bord montre et oriente, l'action se fait sur l'écran visé.

> **Attention :** Un chiffre « — » veut dire « non lu » (module non souscrit, ou rôle qui ne le consulte pas) : ce n'est pas zéro.

### Mon horaire

*Adresse : `/admin/monhoraire` — Toute personne planifiable (compte relié à une ressource).*

Consulter ses quarts publiés : le mois en calendrier, ou la semaine en liste.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_planification » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration crée les référentiels en vigueur à la date voulue (Administration › Paramètres des effectifs › Référentiels) : l'unité et le corps d'emploi, obligatoires pour toute ressource ; au besoin le titre d'emploi (rattaché à son corps) et le statut d'emploi. (écran `/admin/usreferentiels`)
3. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : n'importe lequel. (écran `/admin/admin`)
4. Un planificateur (niveau Gestion sur l'unité) ou l'administration crée la ressource de la personne dans Menu principal › Gestion des quarts › Ressources — matricule, nom, unité, corps d'emploi — et choisit son « Compte utilisateur ». C'est ce lien qui manque quand l'écran répond « votre compte n'est relié à aucune ressource planifiable : demandez-le à votre planificateur ». (écran `/admin/usressources`)
5. Un planificateur publie les postes de l'unité (Gestion des quarts › Postes), crée la période (Gestion des quarts › Périodes), la passe en confection, génère ses quarts, attribue les places aux personnes (Quarts et équipages ou Comblement), puis publie la période : seuls les quarts d'une période publiée comptent ici. (écran `/admin/usperiodes`)

**Comment faire**

1. Le calendrier du mois s'ouvre par défaut ; « Mois suivant » et « Mois précédent » changent le mois.
2. « Liste de la semaine » donne la table jour, quart, site, poste et statut ; « Semaine précédente » sert à vérifier sa feuille de temps.
3. Un quart visé par un échange ouvert porte la mention « Échange en cours ».

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Affichage | Calendrier du mois |
| Case du lundi 16 | 06:00–14:00 Caserne 12 · PARA |

**Résultat :** Lecture seule. Pour céder un quart : « Proposer un échange », qui mène à Mes échanges.

> **Attention :** « Votre compte n'est relié à aucune ressource planifiable » : demander au planificateur de relier le compte dans Ressources.

### Mes disponibilités

*Adresse : `/admin/mesdisponibilites` — Toute personne planifiable.*

Déposer ses disponibilités pour une période dont la collecte est ouverte.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_disponibilites » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration crée les référentiels en vigueur à la date voulue (Administration › Paramètres des effectifs › Référentiels) : l'unité et le corps d'emploi, obligatoires pour toute ressource ; au besoin le titre d'emploi (rattaché à son corps) et le statut d'emploi. (écran `/admin/usreferentiels`)
3. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : n'importe lequel. (écran `/admin/admin`)
4. Un planificateur (niveau Gestion sur l'unité) ou l'administration crée la ressource de la personne dans Menu principal › Gestion des quarts › Ressources — matricule, nom, unité, corps d'emploi — et choisit son « Compte utilisateur ». C'est ce lien qui manque quand l'écran répond « votre compte n'est relié à aucune ressource planifiable : demandez-le à votre planificateur ». (écran `/admin/usressources`)
5. Un planificateur crée la période de l'unité de la personne (Gestion des quarts › Périodes) puis « Ouvre la collecte » avec sa fin : hors collecte, le serveur répond « la collecte de cette période n'est pas ouverte : les disponibilités ne se déposent plus ». (écran `/admin/usperiodes`)
6. Facultatif : l'administration publie des règles du contexte DISPONIBILITE (Règles des effectifs, module « effectifs_regles ») ; sans règle, tout dépôt est accepté. (écran `/admin/usregles`)

**Comment faire**

1. Choisir la « Période » (seules celles en collecte apparaissent).
2. « Ajouter une plage » pour chaque jour : jour, début, fin, et le type « Disponibilité ».
3. Enregistrer : les règles jugent le dépôt aussitôt ; un refus nomme ce qu'il faut corriger.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Période | Semaine du 16 novembre |
| Jour | lundi 16 |
| Début | 06:00 |
| Fin (avant le début = passe minuit) | 18:00 |
| Disponibilité | Disponible |

**Résultat :** Le dépôt rejoint le bassin de candidats de la période ; « À surveiller dans votre dépôt » liste les avertissements.

> **Attention :** Une fin avant le début (22:00 → 06:00) veut dire que la plage passe minuit.

### Disponibilités de la période

*Adresse : `/admin/usdisponibilites` — Planificateurs et chefs, dans leur périmètre.*

Lire les dépôts de disponibilités d'une période, les approuver, et voir le bassin de candidats d'une place vacante.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_disponibilites » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration, direction ou gestionnaire (capacité « effectifs.manage »). (écran `/admin/admin`)
3. L'administration donne au compte de l'encadrement un périmètre sur l'unité, au niveau Lecture pour lire, Gestion pour approuver un dépôt (Administration › Paramètres des effectifs › Périmètres) : hors périmètre, l'unité n'apparaît pas. Un compte d'administration voit toutes les unités sans périmètre. (écran `/admin/usperimetres`)
4. Une période de l'unité en collecte (Périodes › « Ouvrir la collecte ») et des personnes planifiables, reliées à leur compte, qui y déposent (Mes disponibilités). (écran `/admin/usperiodes`)
5. Pour « Voir les candidats » : des quarts générés avec des places vacantes dans la période (Postes publiés, puis génération en confection). (écran `/admin/usequipages`)

**Comment faire**

1. Choisir la « Période ».
2. Ouvrir un dépôt pour lire ses plages et les « Motifs relevés par les règles au dépôt » ; « Approuver ce dépôt » au besoin.
3. Choisir une « Place vacante » puis « Voir les candidats » : chaque exclusion porte son motif.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Période | Semaine du 16 novembre |
| Place vacante | 2026-11-17 · 06:00–18:00 · Caserne 12 |
| Geste | Voir les candidats → Marc Dubé (admissible) |

**Résultat :** À la fin de la collecte, l'attribution automatique se fait sans geste ; l'écran le rappelle.

### Mes absences d'effectifs

*Adresse : `/admin/mesabsences` — Toute personne planifiable.*

Déclarer une absence sur des quarts prévus, et suivre sa décision.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_absences » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration crée les référentiels en vigueur à la date voulue (Administration › Paramètres des effectifs › Référentiels) : l'unité et le corps d'emploi, obligatoires pour toute ressource ; au besoin le titre d'emploi (rattaché à son corps) et le statut d'emploi. (écran `/admin/usreferentiels`)
3. L'administration crée les codes d'absence (Administration › Paramètres des effectifs › Codes d'absence) avec leurs attributs : comportement (payée ou non), catégorie de durée, « Peut être planifiée d'avance », titres d'emploi admissibles. Le code doit être en vigueur au premier jour ; un code non planifiable ne se déclare pas d'avance ; un code réservé à certains titres est refusé aux autres. (écran `/admin/uscodesabsence`)
4. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : n'importe lequel. (écran `/admin/admin`)
5. Un planificateur (niveau Gestion sur l'unité) ou l'administration crée la ressource de la personne dans Menu principal › Gestion des quarts › Ressources — matricule, nom, unité, corps d'emploi — et choisit son « Compte utilisateur ». C'est ce lien qui manque quand l'écran répond « votre compte n'est relié à aucune ressource planifiable : demandez-le à votre planificateur ». (écran `/admin/usressources`)
6. Pour qu'une absence « à approuver » soit décidée : l'administration donne à un gestionnaire un périmètre de niveau Gestion sur l'unité de la personne (Paramètres des effectifs › Périmètres) ; un compte d'administration décide partout. (écran `/admin/usperimetres`)
7. Facultatif : l'administration publie des règles du contexte ABSENCE (Règles des effectifs, module « effectifs_regles ») ; sans règle, l'absence est acceptée d'office. (écran `/admin/usregles`)
8. Pour que des places soient libérées et offertes au comblement : la personne tient des quarts attribués dans une période publiée (Gestion des quarts › Périodes, puis Quarts et équipages). (écran `/admin/usperiodes`)

**Comment faire**

1. « Déclarer une absence » ouvre le formulaire.
2. Choisir le code d'absence, la période (du, au) et ajouter un motif au besoin.
3. Envoyer : l'absence est acceptée d'office si toutes les règles sont respectées, sinon elle attend une décision.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Code | MAL — Maladie |
| Du | 2026-11-19 |
| Au | 2026-11-20 |
| Motif | Fièvre, certificat à suivre |

**Résultat :** Les places libérées sont offertes au comblement ; le statut (accordée, à approuver, refusée) s'affiche avec ses motifs.

> **Attention :** Une indisponibilité (« je ne peux pas ce soir-là ») se dépose dans Mes disponibilités, pas ici.

### Absences et comblement

*Adresse : `/admin/usabsences` — Planificateurs, chefs, direction.*

Décider des absences de son périmètre en voyant leur impact sur le comblement, et suivre l'offre des places libérées.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_absences » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration, direction ou gestionnaire (capacité « effectifs.manage »). (écran `/admin/admin`)
3. L'administration donne au compte de l'encadrement un périmètre sur l'unité, au niveau Gestion (Administration › Paramètres des effectifs › Périmètres) : approuver, refuser ou déroger l'exige sur l'unité de la personne ; sinon « unité … hors de votre périmètre (niveau GESTION requis) ». Un compte d'administration voit toutes les unités sans périmètre. (écran `/admin/usperimetres`)
4. Les codes d'absence existent (Codes d'absence) : une dérogation attribue un code en vigueur. (écran `/admin/uscodesabsence`)
5. Des ressources reliées à leur compte, qui déclarent dans Mes absences d'effectifs, ou pour qui le planificateur déclare. (écran `/admin/usressources`)
6. Pour mesurer l'impact et offrir les places : des quarts attribués dans une période publiée ; les règles ABSENCE sont facultatives (Règles des effectifs). (écran `/admin/usperiodes`)

**Comment faire**

1. Filtrer par statut (« À approuver »), puis ouvrir une absence.
2. Lire l'impact : places libérées et taux de comblement avant/après.
3. « Approuver » ou « Refuser » ; une dérogation à un refus automatique exige un motif et un code d'absence attribué.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Personne | Julie Roy |
| Code | MAL |
| Période | 2026-11-19 au 2026-11-20 |
| Décision | Approuver |

**Résultat :** Les places libérées sont offertes aux admissibles, attribuées d'office ou réservées au remplacement long terme ; le bilan s'affiche.

### Mon AGPH

*Adresse : `/admin/monagph` — Toute personne planifiable visée par une campagne de son unité et de son corps d'emploi.*

Suivre son tour de rôle pour le choix annuel des postes (AGPH), choisir son poste quand c'est son tour, et manifester son intérêt pour un remplacement long terme ouvert.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_agph » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration crée les référentiels en vigueur à la date voulue (Administration › Paramètres des effectifs › Référentiels) : l'unité et le corps d'emploi, obligatoires pour toute ressource ; au besoin le titre d'emploi (rattaché à son corps) et le statut d'emploi. (écran `/admin/usreferentiels`)
3. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : n'importe lequel. (écran `/admin/admin`)
4. La ressource de la personne est reliée à son compte (Gestion des quarts › Ressources, « Compte utilisateur »), active, avec son « Ancienneté (selon la convention) » : le rang en découle. Sans ce lien : « votre compte n'est relié à aucune ressource planifiable ». (écran `/admin/usressources`)
5. Un planificateur publie les postes de l'unité et du corps d'emploi (Gestion des quarts › Postes) : sans poste publié et libre, la campagne n'a « rien à offrir ». (écran `/admin/uspostes`)
6. Un planificateur (niveau Gestion sur l'unité) crée la campagne de l'année pour l'unité et le corps d'emploi de la personne, puis démarre le tour de rôle (Confection annuelle (AGPH)). (écran `/admin/usagph`)
7. Pour manifester son intérêt : un planificateur ouvre un remplacement long terme du même corps d'emploi, avec sa date de fin des manifestations (Confection annuelle (AGPH)). (écran `/admin/usagph`)

**Comment faire**

1. Lire son rang et l'état de la campagne : « Votre tour de rôle » dit combien de personnes vous précèdent.
2. Quand c'est votre tour, choisir un poste parmi ceux qui restent ; le délai de choix est affiché.
3. Dans « Remplacements long terme ouverts », « Manifester mon intérêt » sur un remplacement de votre corps d'emploi, avant la date de fin des manifestations.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Campagne | AGPH 2027 — Opérations Est, Paramédics |
| Rang | 3 (ancienneté figée : 120) |
| Poste choisi | B-TC — Caserne 12, cycle 4/4 |
| Remplacement | Poste C-TPR, du 2027-06-02 au 2027-06-30 → Manifester mon intérêt |

**Résultat :** Le choix est enregistré et le rang suivant est appelé ; l'intérêt pour un remplacement est enregistré et le planificateur l'attribue à l'ancienneté.

> **Attention :** Si vous ne choisissez pas dans le délai, votre tour passe : le planificateur peut saisir votre choix ensuite.

### Confection annuelle (AGPH)

*Adresse : `/admin/usagph` — Planificateurs et administration.*

Conduire la campagne annuelle de choix des postes : rangs, budget d'heures, tours de rôle, révision, clôture ; et ouvrir les remplacements long terme.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_agph » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration, direction ou gestionnaire (capacité « effectifs.manage »). (écran `/admin/admin`)
3. L'administration donne au compte de l'encadrement un périmètre sur l'unité, au niveau Gestion (Administration › Paramètres des effectifs › Périmètres) : créer et conduire une campagne l'exige sur l'unité. Un compte d'administration voit toutes les unités sans périmètre. (écran `/admin/usperimetres`)
4. L'administration crée les référentiels en vigueur à la date voulue (Administration › Paramètres des effectifs › Référentiels) : l'unité et le corps d'emploi, obligatoires pour toute ressource ; au besoin le titre d'emploi (rattaché à son corps) et le statut d'emploi. (écran `/admin/usreferentiels`)
5. Les ressources actives de l'unité et du corps d'emploi, avec leur ancienneté (Ressources) ; sans elles : « aucune ressource active de … : rien à ordonner ». (écran `/admin/usressources`)
6. Les postes de l'unité et du corps, publiés et libres (Postes) ; sans eux : « aucun poste publié et libre de … : rien à offrir ». (écran `/admin/uspostes`)

**Comment faire**

1. « Nouvelle campagne AGPH » : année, unité, corps d'emploi, délai de choix (minutes).
2. Fixer le budget d'heures par semaine ; lire les disponibilités déposées dans la campagne.
3. Démarrer le tour de rôle ; saisir le choix d'une personne absente ; réviser une attribution avec un motif ; clore.
4. « Ouvrir un remplacement long terme » : poste, origine, du/au, « Manifestations jusqu'au ».

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Année | 2027 |
| Unité | OPS |
| Corps d'emploi | PARA |
| Délai de choix (minutes) | 1440 |
| Budget d'heures par semaine (facultatif) | 36 |

**Résultat :** À la clôture, les postes restés libres passent à la gestion régulière et sont listés ; un remplacement non comblé transfère ses quarts.

### Comblement

*Adresse : `/admin/uscomblement` — Planificateurs, chefs, direction.*

Le tableau du matin : taux de comblement, quarts sous leur minimum opérationnel, jours sous le quota.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_planification » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration, direction ou gestionnaire (capacité « effectifs.manage »). (écran `/admin/admin`)
3. L'administration donne au compte de l'encadrement un périmètre sur l'unité, au niveau Planification (Administration › Paramètres des effectifs › Périmètres) : le tableau ne montre que les unités du périmètre ; forcer une attribution qu'une règle écarte exige le niveau Gestion. Un compte d'administration voit toutes les unités sans périmètre. (écran `/admin/usperimetres`)
4. Une période dont les quarts sont générés depuis des postes publiés (Postes, puis Périodes : confection, générer), et des ressources actives du bon corps d'emploi (Ressources). (écran `/admin/usperiodes`)
5. Facultatif : les quotas d'équipages par jour (Quotas), pour lire les jours sous le quota. (écran `/admin/usquotas`)

**Comment faire**

1. Choisir la « Période ».
2. Lire « Quarts sous leur minimum opérationnel » ; « Combler » ouvre la place à pourvoir.
3. Choisir la personne ; forcer une attribution qu'une règle écarte exige une justification (10 caractères au moins), consignée au journal.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Période | Semaine du 16 novembre |
| Quart | 2026-11-17 · 14:00–22:00 · Nord · 1 / 3 |
| Personne | Marc Dubé |
| Justification pour forcer | Seul paramédic disponible, accord du chef |

**Résultat :** Le quart remonte vers « opérationnel » et le taux de comblement se met à jour.

### Quarts et équipages

*Adresse : `/admin/usequipages` — Planificateurs.*

Gérer les quarts d'une période : leurs places, le minimum opérationnel, le mode d'attribution, et les quarts ad hoc hors cycle.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_planification » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration, direction ou gestionnaire (capacité « effectifs.manage »). (écran `/admin/admin`)
3. L'administration donne au compte de l'encadrement un périmètre sur l'unité, au niveau Planification (Administration › Paramètres des effectifs › Périmètres) : les quarts d'une unité hors périmètre ne s'ouvrent pas. Un compte d'administration voit toutes les unités sans périmètre. (écran `/admin/usperimetres`)
4. Les référentiels en vigueur : natures de quart, plages horaires, corps d'emploi (Référentiels), et les milieux (Milieux physiques). (écran `/admin/usreferentiels`)
5. Une période de l'unité qui n'est pas close (Périodes) ; ses quarts réguliers viennent des postes publiés par « générer ». (écran `/admin/usperiodes`)
6. Des ressources actives du corps d'emploi de la place, pour l'occuper (Ressources). (écran `/admin/usressources`)

**Comment faire**

1. Choisir la « Période », puis un quart (« Sélectionner le quart du … »).
2. Modifier le minimum opérationnel, le mode d'attribution ou la note ; « Libérer la place » d'une personne avec un motif.
3. « Nouveau quart ad hoc » : jour, début, fin, nature, milieu, corps d'emploi et nombre de places.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Jour | 2026-11-21 |
| Début | 10:00 |
| Fin (avant le début = passe minuit) | 16:00 |
| Nature du quart | REG |
| Milieu | Caserne 12 |
| Corps d'emploi | PARA |
| Nombre | 2 |
| Mode d'attribution | Manuel |

**Résultat :** Le quart est créé avec ses places ; il apparaît au comblement et dans l'horaire des personnes attribuées.

### Postes

*Adresse : `/admin/uspostes` — Planificateurs et administration.*

Définir les postes (lignes de quarts) : code, libellé, milieu, corps d'emploi, statut offert et cycle ; les publier, les modifier en lot.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_planification » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration, direction ou gestionnaire (capacité « effectifs.manage »). (écran `/admin/admin`)
3. L'administration donne au compte de l'encadrement un périmètre sur l'unité, au niveau Planification (Administration › Paramètres des effectifs › Périmètres) : créer un poste l'exige sur son unité. Un compte d'administration voit toutes les unités sans périmètre. (écran `/admin/usperimetres`)
4. Les référentiels en vigueur : unité, statut d'emploi, corps d'emploi, nature de quart et chaque plage horaire nommée dans le cycle ; au besoin, les « Paramètres de poste » (Référentiels). (écran `/admin/usreferentiels`)
5. Le milieu où le poste travaille (Milieux physiques). (écran `/admin/usmilieux`)

**Comment faire**

1. Créer un poste avec son code, son libellé, son milieu et son « Premier jour du cycle ».
2. Décrire le cycle (les quarts de chaque jour) ; publier pour qu'il entre dans la planification.
3. Modifier plusieurs postes d'un geste (« Remplacer le cycle par une semaine commune ») : les conflits sont listés avant d'écrire.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Code | B-TC |
| Libellé | Caserne 12 — jour, temps complet |
| Milieu | Caserne 12 |
| Corps d'emploi | PARA |
| Statut offert | TC |
| Premier jour du cycle | 2026-11-16 |

**Résultat :** Les quarts futurs déjà générés suivent la modification ; le bilan dit combien ont été déplacés, requalifiés ou retirés.

### Périodes

*Adresse : `/admin/usperiodes` — Planificateurs.*

Ouvrir les périodes de planification et les faire avancer : préparation, collecte, confection, publication, clôture.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_planification » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration, direction ou gestionnaire (capacité « effectifs.manage »). (écran `/admin/admin`)
3. L'administration donne au compte de l'encadrement un périmètre sur l'unité, au niveau Planification (Administration › Paramètres des effectifs › Périmètres) : créer une période ou la faire avancer l'exige sur son unité. Un compte d'administration voit toutes les unités sans périmètre. (écran `/admin/usperimetres`)
4. L'unité en vigueur (Référentiels). (écran `/admin/usreferentiels`)
5. Pour générer les quarts : des postes publiés de l'unité (Postes) ; pour les occuper : des ressources (Ressources). (écran `/admin/uspostes`)

**Comment faire**

1. « Nouvelle période » : nom, unité, premier et dernier jour.
2. « Ouvrir la collecte » avec la « Fin de la collecte » : les employés déposent leurs disponibilités.
3. Confectionner, puis publier : les horaires deviennent visibles dans Mon horaire.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Nom | Semaine du 16 novembre |
| Unité | OPS |
| Premier jour | 2026-11-16 |
| Dernier jour | 2026-11-22 |
| Fin de la collecte | 2026-11-12 |

**Résultat :** La période change d'état ; une période publiée ne redevient jamais brouillon.

### Ressources

*Adresse : `/admin/usressources` — Planificateurs, ressources humaines, administration.*

Tenir les personnes planifiables : matricule, nom, unité, corps et statut d'emploi, ancienneté, et le compte auquel elles sont reliées.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_planification » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration, direction ou gestionnaire (capacité « effectifs.manage ») pour tenir les ressources ; et, pour chaque personne, son propre compte de connexion. (écran `/admin/admin`)
3. L'administration donne au compte de l'encadrement un périmètre sur l'unité, au niveau Gestion (Administration › Paramètres des effectifs › Périmètres) : créer ou modifier une ressource l'exige sur son unité. Un compte d'administration voit toutes les unités sans périmètre. (écran `/admin/usperimetres`)
4. Les référentiels en vigueur : unité et corps d'emploi (obligatoires) ; titre d'emploi, statut d'emploi et convention collective (facultatifs). (écran `/admin/usreferentiels`)
5. Le compte de la personne existe avant d'être relié (Comptes et rôles) ; seul un compte d'administration voit la liste des comptes à relier. (écran `/admin/admin`)

**Comment faire**

1. Ajouter une ressource avec son matricule, son nom, son unité, son corps d'emploi et son statut.
2. Renseigner l'« Ancienneté (selon la convention) » : elle ordonne l'AGPH et les remplacements.
3. Relier la ressource au compte de la personne (« Compte utilisateur », proposé à l'administration) : sans ce lien, ses écrans personnels répondent « votre compte n'est relié à aucune ressource planifiable ».

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Matricule | P100 |
| Nom | Julie Roy |
| Unité | OPS |
| Corps d'emploi | PARA |
| Statut | TC — Temps complet |
| Ancienneté (selon la convention) | 120 |
| Compte utilisateur (facultatif) | julie.roy |

**Résultat :** La personne devient planifiable et voit son horaire, ses disponibilités et sa feuille de temps.

### Quotas

*Adresse : `/admin/usquotas` — Planificateurs et direction.*

Fixer le nombre d'équipages opérationnels visés par jour, par unité, plage ou nature de quart, et poser une surcharge temporaire.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_planification » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration, direction ou gestionnaire (capacité « effectifs.manage »). (écran `/admin/admin`)
3. L'administration donne au compte de l'encadrement un périmètre sur l'unité, au niveau Planification (Administration › Paramètres des effectifs › Périmètres) : poser un quota l'exige sur son unité. Un compte d'administration voit toutes les unités sans périmètre. (écran `/admin/usperimetres`)
4. Les référentiels : l'unité, et au besoin la plage horaire et la nature de quart visées (Référentiels). (écran `/admin/usreferentiels`)

**Comment faire**

1. « Définir un quota » : unité, plage, nature, valeur et « En vigueur à partir du ».
2. Pour une période exceptionnelle, « Enregistrer la surcharge » avec ses dates et son motif.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Unité | OPS |
| Nature | REG |
| Équipages opérationnels visés par jour | 6 |
| En vigueur à partir du (aujourd'hui ou plus tard) | 2026-11-16 |
| Valeur temporaire | 8 |
| Motif | Festival d'été |

**Résultat :** Le comblement compare chaque jour à son quota ; une surcharge prend effet « à partir de », jamais en arrière.

### Mon pointage

*Adresse : `/admin/monpointage` — Toute personne planifiable qui a un quart en cours.*

Pointer l'entrée et la sortie d'un quart, avec la preuve exigée par le milieu, ou déclarer après coup un quart non pointé.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_presence » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration crée les référentiels en vigueur à la date voulue (Administration › Paramètres des effectifs › Référentiels) : l'unité et le corps d'emploi, obligatoires pour toute ressource ; au besoin le titre d'emploi (rattaché à son corps) et le statut d'emploi. (écran `/admin/usreferentiels`)
3. L'encadrement crée le milieu (Paramètres des effectifs › Milieux physiques) — type de milieu et unité en vigueur — et coche les preuves de présence admises et exigées ; le géorepérage exige latitude, longitude et rayon. (écran `/admin/usmilieux`)
4. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : n'importe lequel. (écran `/admin/admin`)
5. Un planificateur (niveau Gestion sur l'unité) ou l'administration crée la ressource de la personne dans Menu principal › Gestion des quarts › Ressources — matricule, nom, unité, corps d'emploi — et choisit son « Compte utilisateur ». C'est ce lien qui manque quand l'écran répond « votre compte n'est relié à aucune ressource planifiable : demandez-le à votre planificateur ». (écran `/admin/usressources`)
6. Un planificateur attribue à la personne un quart d'une période publiée qui couvre l'heure du pointage ; sinon : « aucun quart publié ne vous attend en ce moment : un quart non pointé se déclare en saisie a posteriori ». (écran `/admin/usequipages`)
7. Facultatif : un profil de pointage (Pointage et présence (console)) fixe le mode — temps réel ou après coup — et la fenêtre de saisie ; un profil « après coup » refuse le pointage en temps réel. Les activités proposées viennent du référentiel « Activités de présence ». (écran `/admin/uspresence`)

**Comment faire**

1. Choisir « Où êtes-vous ? » (le milieu du quart) et, si demandé, saisir le « Code affiché au milieu » ou joindre sa position.
2. « Entrée » au début du quart, « Sortie » à la fin ; une pause se déclare par « Début de pause » et « Fin de pause ».
3. Un oubli : « Déclarer un quart non pointé », avec la date, les heures et « Pourquoi ce quart n'a-t-il pas été pointé ? ».

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Où êtes-vous ? | Caserne 12 |
| Code affiché au milieu (si demandé) | 4821 |
| Que commencez-vous à faire ? | Quart régulier |

**Résultat :** Le pointage est jugé (conforme, écart de lieu, preuve absente) ; un écart part à l'approbation du planificateur.

> **Attention :** La position n'est lue qu'une fois, au geste, et seulement si vous cochez « Joindre ma position ».

### Pointage et présence (console)

*Adresse : `/admin/uspresence` — Planificateurs et chefs.*

Contrôler les pointages de son périmètre, approuver ou rejeter les écarts, importer des horodatages externes et tenir les profils de pointage.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_presence » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration, direction ou gestionnaire (capacité « effectifs.manage »). (écran `/admin/admin`)
3. L'administration donne au compte de l'encadrement un périmètre sur l'unité, au niveau Lecture au moins (Administration › Paramètres des effectifs › Périmètres) : la console ne montre que les pointages des unités du périmètre. Un compte d'administration voit toutes les unités sans périmètre. (écran `/admin/usperimetres`)
4. Les milieux et leurs preuves de présence (Milieux physiques). (écran `/admin/usmilieux`)
5. Des personnes qui pointent (Mon pointage) : une ressource reliée à leur compte et un quart publié en cours. Pour l'import, chaque matricule est celui d'une ressource. (écran `/admin/usressources`)

**Comment faire**

1. Filtrer par site, statut, ou cocher « N'afficher que les écarts de lieu ».
2. Ouvrir un pointage, lire le verdict et la preuve, puis approuver, ou rejeter avec un motif obligatoire.
3. « Importer des horodatages externes » : une ligne « clé;matricule;sens;horodatage ISO » par pointage de borne.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Source (borne, système) | Borne Caserne 12 |
| Lignes « clé;matricule;sens;horodatage ISO » | B12-001;P100;ENTREE;2026-11-16T06:02:00-05:00 |

**Résultat :** Le pointage approuvé alimente la feuille de temps ; un rejet est motivé et journalisé.

### Ma feuille de temps

*Adresse : `/admin/matemps` — Toute personne planifiable.*

Préparer sa feuille d'une période à partir de ses quarts, la corriger segment par segment, puis la soumettre.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_temps » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration crée les référentiels en vigueur à la date voulue (Administration › Paramètres des effectifs › Référentiels) : l'unité et le corps d'emploi, obligatoires pour toute ressource ; au besoin le titre d'emploi (rattaché à son corps) et le statut d'emploi. (écran `/admin/usreferentiels`)
3. L'administration crée les types d'heures (Référentiels › Types d'heures) avec leur code de paie : au moins un de comportement TRAVAILLEE — sinon « aucun type d'heure de comportement TRAVAILLEE n'est en vigueur » — et un de comportement SUPPLEMENTAIRE pour les heures supplémentaires. (écran `/admin/usreferentiels`)
4. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : n'importe lequel. (écran `/admin/admin`)
5. Un planificateur (niveau Gestion sur l'unité) ou l'administration crée la ressource de la personne dans Menu principal › Gestion des quarts › Ressources — matricule, nom, unité, corps d'emploi — et choisit son « Compte utilisateur ». C'est ce lien qui manque quand l'écran répond « votre compte n'est relié à aucune ressource planifiable : demandez-le à votre planificateur ». (écran `/admin/usressources`)
6. Les segments viennent des quarts attribués dans une période publiée et des pointages : sans eux, la feuille est vide et tout se saisit à la main. (écran `/admin/usperiodes`)
7. L'administration donne au compte de l'encadrement un périmètre sur l'unité, au niveau Gestion (Administration › Paramètres des effectifs › Périmètres) : par défaut, chaque palier d'approbation va au niveau Gestion de l'unité de la personne ; sans personne à ce niveau, la feuille soumise n'a pas d'approbateur. Un compte d'administration voit toutes les unités sans périmètre. (écran `/admin/usperimetres`)
8. Facultatif : le nombre de paliers, le flux d'approbation, les supérieurs hiérarchiques et les champs propres à l'organisme se règlent dans Feuilles de temps (console). (écran `/admin/ustemps`)

**Comment faire**

1. « Préparer ma feuille » avec le « Début de la période » et la « Fin de la période » : les segments viennent des quarts et des pointages.
2. Vérifier chaque segment : la ressource (nom et matricule), les heures, le type d'heure, les « Tâches » et les champs propres à votre organisme.
3. « Ajouter un segment » pour un temps manquant (jour, de, à, type d'heure), avec la « Description des tâches » et les champs demandés (ex. Véhicule) ; ou modifier un segment et « Enregistrer la correction ».
4. « Soumettre ma feuille » : elle part au premier palier d'approbation — souvent votre supérieur hiérarchique. « En attente de » dit chez qui elle est, et, s'il est absent, qui le remplace (« en son absence : … »).
5. Des heures supplémentaires ? Ajoutez un segment du type d'heure « Heures supplémentaires » : elles comptent même sous le seuil du jour, et se paient majorées, une seule fois. Au-delà des seuils de l'organisme, l'excédent devient supplémentaire de lui-même.
6. Une erreur après la soumission ? Tant que la paie n'est pas faite, « Rappeler ma feuille » la ramène au brouillon : corrigez-la, puis soumettez-la de nouveau.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Début de la période | 2026-11-09 |
| Fin de la période | 2026-11-15 |
| Ajouter un segment — Jour | 2026-11-12 |
| De | 18:00 |
| À | 19:30 |
| Type d'heure | SUPP — Supplémentaire |
| Description des tâches | Transport prolongé, appel 412 |
| Véhicule (obligatoire) | A-12 |

**Résultat :** La feuille passe à « Soumise » et affiche « En attente de : … » ; chaque correction garde ses versions, sa description et ses champs, et une anomalie est nommée avec ce qu'il faut corriger.

> **Attention :** Une feuille retournée ou rouverte porte son motif : corriger puis soumettre à nouveau. Une feuille payée ne se modifie plus : une erreur se corrige à la paie suivante. Un champ marqué « obligatoire » doit être rempli pour que le segment soit accepté.

### Feuilles de temps (console)

*Adresse : `/admin/ustemps` — Planificateurs, chefs, paie.*

Préparer les feuilles d'une période, les approuver palier par palier, lire les relevés et produire le lot de paie.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_temps » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration, direction ou gestionnaire (capacité « effectifs.manage ») ; les champs de la feuille et les seuils relèvent de l'administration de l'organisme (capacité « effectifs.configure »). (écran `/admin/admin`)
3. L'administration donne au compte de l'encadrement un périmètre sur l'unité, au niveau Planification pour préparer, Gestion pour approuver au palier par défaut (Administration › Paramètres des effectifs › Périmètres) : hors périmètre, une feuille n'apparaît pas, sauf celles routées à son supérieur hiérarchique. Un compte d'administration voit toutes les unités sans périmètre. (écran `/admin/usperimetres`)
4. Les types d'heures (Référentiels › Types d'heures) : un de comportement TRAVAILLEE pour préparer ; un de comportement SUPPLEMENTAIRE aussi pour produire le lot de paie, chacun avec son code de paie. (écran `/admin/usreferentiels`)
5. Des ressources reliées à leur compte et des quarts attribués dans une période publiée (Périodes). (écran `/admin/usperiodes`)
6. Pour transmettre le lot à un système de paie : le connecteur de paie (Intégrations des effectifs). (écran `/admin/usintegrations`)

**Comment faire**

1. « Préparer les feuilles » pour une unité et une période.
2. Ouvrir une feuille soumise : approuver, ou retourner avec un motif obligatoire. Une feuille approuvée mais pas encore payée se « Rouvre pour correction », avec un motif.
3. « Afficher le relevé » (regroupé par personne, unité ou type d'heure) et l'exporter ; « Produire le lot » puis « Télécharger le lot (CSV) ».
4. Régler le « Flux d'approbation des heures » : paliers, conditions et approbateur — niveau de l'unité, rôle, personne désignée ou « Supérieur hiérarchique ».
5. « Supérieurs hiérarchiques » : choisir une « Personne » puis « Son supérieur hiérarchique », et « Enregistrer ». C'est ce que lit l'approbateur « Supérieur hiérarchique ».
6. « Champs de la feuille » (administration) : ajouter ce que chaque segment doit dire selon vos emplois — libellé, code, type (texte, nombre, oui ou non, liste), obligatoire ; « Désactiver » un champ qui ne sert plus.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Unité | OPS |
| Début de la période | 2026-11-09 |
| Fin | 2026-11-15 |
| Motif (obligatoire pour retourner) | Segment du 12 sans preuve de transport |
| Champ — Libellé | Véhicule |
| Code | vehicule |
| Type | Liste de choix |
| Choix offerts | A-12, B-07 |
| Personne | Julie Roy |
| Son supérieur hiérarchique | Marc Lavoie |

**Résultat :** Les feuilles approuvées entrent au lot de paie ; une feuille routée au supérieur lui apparaît même hors de son périmètre, avec « En attente de ».

> **Attention :** Sans supérieur désigné, une feuille routée au « Supérieur hiérarchique » revient au niveau GESTION de l'unité. Un champ se désactive, jamais ne s'efface : les valeurs déjà saisies restent lisibles.

### Mon équipe

*Adresse : `/admin/monequipe` — Gestionnaires, direction, RH et administration ; réorganiser exige le droit de corriger une fiche (gestion, RH, administration).*

Voir les personnes qui relèvent de soi, réorganiser son équipe, suivre les feuilles à approuver et désigner un suppléant pendant son absence.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme le module « effectifs » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : gestionnaire, direction, RH ou administration. (écran `/admin/admin`)
3. Le compte du gestionnaire est rattaché à une fiche du personnel : créer sa ressource reliée à son compte (Gestion des quarts › Ressources) crée et rattache cette fiche ; l'administration peut aussi lier le compte à l'employé (Comptes et rôles). Sans fiche, l'équipe reste vide et réorganiser répond « votre compte n'est relié à aucune fiche du personnel ». (écran `/admin/usressources`)
4. Chaque membre a sa fiche (même geste) et son supérieur direct : « Supérieurs hiérarchiques » dans Feuilles de temps (console), ou « Rattacher une personne » ici. (écran `/admin/ustemps`)
5. Pour désigner un suppléant : il a un rôle qui approuve des feuilles (gestion, direction ou administration).

**Comment faire**

1. Lire « Les membres de mon équipe » : chaque personne, son poste, son supérieur direct et son lien (direct ou indirect).
2. « Rattacher une personne » : choisir une personne encore sans supérieur, puis « Rattacher à mon équipe ».
3. « Changer de supérieur » sur une ligne : choisir le « Nouveau supérieur direct » (vous ou un membre de votre équipe), puis « Enregistrer » ; « Détacher » retire un subordonné direct.
4. « Feuilles de temps à approuver » : les feuilles qui attendent votre décision, puis « Ouvrir la console des feuilles pour décider ».
5. Avant une absence : choisir le « Suppléant » (3 lettres de son nom), les dates « Du » et « Au », puis « Désigner ce suppléant » ; au retour, « Mettre fin ».

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Personne à rattacher | Léa Morin — Paramédic |
| Nouveau supérieur direct de Luc Bouchard | Julie Tremblay |
| Suppléant | Diane Roy |
| Du | 2026-12-21 |
| Au | 2027-01-04 |

**Résultat :** « Luc Bouchard relève maintenant de Julie Tremblay. » ; pendant la suppléance, les feuilles de votre équipe parviennent aussi à Diane Roy, et l'employé lit « en son absence : Diane Roy (jusqu'au 2027-01-04) ».

> **Attention :** Vous ne voyez et ne changez que votre propre équipe : une personne d'une autre branche se demande à son gestionnaire. Le suppléant doit pouvoir approuver des feuilles (gestion, direction ou administration).

### Mes échanges

*Adresse : `/admin/mesechanges` — Toute personne planifiable.*

Proposer d'échanger un de ses quarts contre celui d'un collègue, et accepter ou refuser ceux qu'on vous propose.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_echanges » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration crée les référentiels en vigueur à la date voulue (Administration › Paramètres des effectifs › Référentiels) : l'unité et le corps d'emploi, obligatoires pour toute ressource ; au besoin le titre d'emploi (rattaché à son corps) et le statut d'emploi. (écran `/admin/usreferentiels`)
3. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : n'importe lequel. (écran `/admin/admin`)
4. Un planificateur (niveau Gestion sur l'unité) ou l'administration crée la ressource de la personne dans Menu principal › Gestion des quarts › Ressources — matricule, nom, unité, corps d'emploi — et choisit son « Compte utilisateur ». C'est ce lien qui manque quand l'écran répond « votre compte n'est relié à aucune ressource planifiable : demandez-le à votre planificateur ». (écran `/admin/usressources`)
5. Les deux personnes tiennent chacune un quart à venir d'une période publiée : un quart d'une période non publiée ou une place vacante ne s'échange pas. (écran `/admin/usperiodes`)
6. Facultatif : des règles du contexte ECHANGE (Règles des effectifs), par exemple « le chef valide » ; le chef qui valide a un périmètre de niveau Gestion sur les deux unités. (écran `/admin/usregles`)

**Comment faire**

1. Choisir « Mon quart » (celui que vous cédez) puis « Contre le quart de » (le quart d'un collègue).
2. « Proposer l'échange » : les règles le jugent dans les deux sens ; un refus dit pourquoi.
3. Le collègue l'accepte dans son propre écran ; si la règle l'exige, le chef le valide ensuite.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Mon quart | lun. 2026-11-16 · 06:00–18:00 |
| Contre le quart de | Marc Dubé — lun. 2026-11-23 · 06:00–18:00 |

**Résultat :** « Échange proposé à Marc Dubé. Une fois accepté, il devra être validé par le chef. » ; appliqué, les quarts changent de mains.

### Demandes et échanges (console)

*Adresse : `/admin/usechanges` — Planificateurs et chefs.*

Suivre les échanges de quarts de son périmètre, en créer un pour une personne, et valider ou rejeter ceux que la règle soumet au chef.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_echanges » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration, direction ou gestionnaire (capacité « effectifs.manage »). (écran `/admin/admin`)
3. L'administration donne au compte de l'encadrement un périmètre sur l'unité, au niveau Planification pour proposer pour une personne, Gestion sur les deux unités pour valider ou rejeter (Administration › Paramètres des effectifs › Périmètres) : sinon la console refuse le geste. Un compte d'administration voit toutes les unités sans périmètre. (écran `/admin/usperimetres`)
4. Deux personnes reliées à leur compte, qui tiennent chacune un quart à venir d'une période publiée (Périodes). (écran `/admin/usperiodes`)
5. Facultatif : des règles du contexte ECHANGE qui exigent la validation du chef (Règles des effectifs). (écran `/admin/usregles`)

**Comment faire**

1. Créer : « Proposer un échange pour une personne », choisir la « Personne », le « Quart qu'elle cède » et le « Quart demandé en retour », puis « Proposer l'échange ».
2. La destinataire accepte l'échange dans Mes échanges.
3. Trancher : ouvrir l'échange « à valider », lire « Ce que les règles ont relevé », saisir un motif, puis « Valider » ou « Rejeter ».

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Personne | Julie Roy |
| Quart qu'elle cède | 2026-11-16 · 06:00–18:00 |
| Quart demandé en retour | Marc Dubé — 2026-11-23 · 06:00–18:00 |
| Motif (facultatif) | Remplacement couvert |

**Résultat :** « Échange proposé pour Julie Roy avec Marc Dubé : Marc Dubé doit l'accepter. » ; validé, les quarts changent de mains ; rejeté, chacun garde le sien.

> **Attention :** L'échange est jugé dans les deux sens : un quart qui créerait un conflit chez l'un ou l'autre est refusé, avec la règle nommée.

### Mes demandes

*Adresse : `/admin/mesdemandes` — Toute personne planifiable.*

Demander une inscription à la liste de temps supplémentaire, un événement, une formation ou une autre demande opérationnelle, et suivre sa décision.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_planification » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration déclare les types de demande au référentiel « Types de demande » (Administration › Paramètres des effectifs › Référentiels), chacun avec son comportement : liste de temps supplémentaire, événement, formation, remplacement ou autre. (écran `/admin/usreferentiels`)
3. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : n'importe lequel. (écran `/admin/admin`)
4. Un planificateur (niveau Gestion sur l'unité) ou l'administration crée la ressource de la personne dans Menu principal › Gestion des quarts › Ressources — matricule, nom, unité, corps d'emploi — et choisit son « Compte utilisateur ». C'est ce lien qui manque quand l'écran répond « votre compte n'est relié à aucune ressource planifiable : demandez-le à votre planificateur ». (écran `/admin/usressources`)
5. Pour qu'une demande soit décidée : l'administration donne à un gestionnaire un périmètre de niveau Gestion sur l'unité de la personne (Paramètres des effectifs › Périmètres). (écran `/admin/usperimetres`)

**Comment faire**

1. Choisir le type de demande, la période (du, au) et, au besoin, des précisions.
2. « Envoyer la demande » : elle attend la décision de l'encadrement de votre unité.
3. Suivre son statut ; une demande en attente s'annule d'un geste.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Type de demande | Liste de temps supplémentaire |
| Du | 2026-12-01 |
| Au | 2026-12-31 |
| Précisions | Disponible les soirs de semaine |

**Résultat :** « Demande « Liste de temps supplémentaire » envoyée. » ; acceptée, l'inscription figure à la liste de temps supplémentaire de chaque jour qu'elle couvre.

> **Attention :** Une absence (vacances, férié, formation payée) se déclare dans Mes absences ; une indisponibilité, dans Mes disponibilités.

### Demandes opérationnelles

*Adresse : `/admin/usdemandes` — Planificateurs et chefs, dans leur périmètre.*

Accepter ou refuser, en motivant, les demandes opérationnelles de son périmètre, et lire la liste de temps supplémentaire d'un jour.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_planification » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration, direction ou gestionnaire (capacité « effectifs.manage »). (écran `/admin/admin`)
3. L'administration donne au compte de l'encadrement un périmètre sur l'unité, au niveau Lecture pour voir les demandes, Gestion sur l'unité de la personne pour décider (Administration › Paramètres des effectifs › Périmètres) : sinon la décision est refusée. Un compte d'administration voit toutes les unités sans périmètre. (écran `/admin/usperimetres`)
4. Des personnes reliées à leur compte qui ont envoyé des demandes (Mes demandes), sur des types déclarés au référentiel « Types de demande ». (écran `/admin/usreferentiels`)

**Comment faire**

1. Filtrer par statut (« En attente » par défaut).
2. Saisir un motif (exigé pour refuser), puis « Accepter » ou « Refuser ».
3. Choisir un jour sous « Liste de temps supplémentaire » : les inscriptions acceptées qui le couvrent, par ancienneté.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Statut | En attente |
| Motif | Quota de formation atteint pour décembre |

**Résultat :** « Demande de Julie Roy acceptée. » ; la décision et son motif sont consignés au journal des décisions.

### Mes congés annuels

*Adresse : `/admin/mescongesannuels` — Toute personne planifiable dont l'unité et le corps d'emploi ont une campagne ouverte.*

Classer ses semaines de congé dans la campagne annuelle de son groupe, puis lire ce qui a été octroyé.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_absences » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : n'importe lequel. (écran `/admin/admin`)
3. Un planificateur (niveau Gestion sur l'unité) ou l'administration crée la ressource de la personne dans Menu principal › Gestion des quarts › Ressources — matricule, nom, unité, corps d'emploi — et choisit son « Compte utilisateur ». C'est ce lien qui manque quand l'écran répond « votre compte n'est relié à aucune ressource planifiable : demandez-le à votre planificateur ». (écran `/admin/usressources`)
4. L'encadrement crée la campagne de votre unité et de votre corps d'emploi, puis l'ouvre aux choix (Absences › Choix des congés annuels). (écran `/admin/uscongesannuels`)
5. Le code d'absence octroyé existe aux codes d'absence ; si une règle de solde de banque est publiée, le solde transmis par l'organisme s'applique à chaque semaine octroyée. (écran `/admin/uscodesabsence`)

**Comment faire**

1. Cocher les semaines voulues dans l'ordre de préférence : la première cochée est le premier choix.
2. « Enregistrer mes choix » : on peut les changer tant que les choix sont ouverts.
3. Après l'octroi, chaque semaine porte « Octroyée » ou « Refusée » avec son motif.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| 1ᵉʳ choix | Semaine du 2027-07-05 au 2027-07-11 |
| 2ᵉ choix | Semaine du 2027-07-12 au 2027-07-18 |

**Résultat :** « Vos 2 choix sont enregistrés. » ; octroyées, les semaines deviennent des absences du code de la campagne.

> **Attention :** L'ancienneté décide de l'ordre d'octroi : une semaine déjà au plafond d'absents est refusée, et le choix suivant est examiné.

### Choix des congés annuels

*Adresse : `/admin/uscongesannuels` — Planificateurs et chefs, dans leur périmètre.*

Créer la campagne annuelle d'un groupe d'employés, ouvrir et fermer les choix, octroyer par ancienneté sous un plafond d'absents par semaine.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_absences » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration, direction ou gestionnaire (capacité « effectifs.manage »). (écran `/admin/admin`)
3. L'administration donne au compte de l'encadrement un périmètre sur l'unité, au niveau Gestion sur l'unité de la campagne (Administration › Paramètres des effectifs › Périmètres) : sinon la création et l'octroi sont refusés. Un compte d'administration voit toutes les unités sans périmètre. (écran `/admin/usperimetres`)
4. Le code d'absence à octroyer existe (Codes d'absence), et les personnes du groupe ont une ancienneté à jour (Ressources). (écran `/admin/uscodesabsence`)

**Comment faire**

1. « Nouvelle campagne » : nom, unité, corps d'emploi, code d'absence, année, saison (du, au) et plafond d'absents par semaine.
2. « Ouvrir les choix » : chaque personne du groupe classe ses semaines dans Mes congés annuels.
3. « Fermer les choix », puis « Octroyer par ancienneté » ; « Résultats » montre chaque choix et son octroi.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Nom | Vacances d'été 2027 |
| Saison | 2027-06-01 au 2027-09-30 |
| Code d'absence | VAC — Vacances annuelles |
| Plafond | 2 |

**Résultat :** « Vacances d'été 2027 est octroyée. » ; chaque semaine octroyée devient une absence jugée par les règles de l'organisme.

### Nouvelle alerte

*Adresse : `/admin/alnouvelle` — Administration, direction et planificateurs (capacité « alertes.envoyer »).*

Envoyer une alerte priorisée à des groupes ou à des personnes, avec les réponses qu'elles pourront donner d'un geste.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme le module « alertes » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration, direction ou gestionnaire (capacité « alertes.envoyer ») ; la priorité critique exige « alertes.critique » : administration, direction, ou droit accordé par l'organisme. (écran `/admin/admin`)
3. Les destinataires : les groupes sont les unités en vigueur des effectifs, et leurs membres les ressources actives reliées à un compte (Ressources) ; les listes viennent des Listes de diffusion du personnel ; les groupes transverses, des Paramètres des alertes. (écran `/admin/usressources`)
4. Facultatif : réponses prédéfinies, modèles et politiques de relance (Paramètres des alertes). (écran `/admin/alparametres`)
5. Pour les textos : le fournisseur de textos de l'organisme (Intégrations) et un numéro de téléphone au dossier de chaque personne (Mon compte). (écran `/admin/integrations`)

**Comment faire**

1. Choisir un « Modèle » au besoin : il remplit le titre, le message, la priorité et les réponses ; tout reste modifiable.
2. Écrire le « Titre » et le « Message », puis choisir la « Priorité ». Une alerte critique part sur tous les canaux, quelles que soient les préférences ; elle est réservée à qui en a le droit (direction, administration, ou un droit accordé par l'organisme).
3. Choisir les destinataires : « Tout le groupe … » (une unité ou un groupe transverse, comme une garde), « La liste … » (une liste de distribution), ou des personnes, par leur nom, dans « Choisir des personnes dans un groupe ».
4. Au besoin, choisir des « Relances » : sans réaction, l'alerte est relancée puis escaladée selon la politique, jusqu'à son critère d'arrêt.
5. Cocher les « Réponses proposées », puis « Envoyer l'alerte ».

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Modèle | Code bleu |
| Titre | Code bleu chambre 512 |
| Message | 5e étage, répondez à la prise en charge |
| Priorité | Critique |
| Destinataires | Tout le groupe Opérations Est ; Julie Roy |
| Réponses proposées | J'arrive (prise en charge) · Indisponible (refus) |

**Résultat :** « Alerte … envoyée à N personne(s) » ; les destinataires sont figés à l'envoi, et « Suivre les réponses » ouvre le suivi.

> **Attention :** Vous n'êtes jamais destinataire de votre propre alerte, même si vous êtes dans le groupe visé. Sans le droit du critique, la priorité « Critique » est grisée, et les modèles critiques ne vous sont pas proposés.

### Suivi des alertes

*Adresse : `/admin/alsuivi` — Qui détient la capacité « alertes.suivre » : expéditeurs, gestionnaires et pilotes par défaut. Le suivi est la preuve de livraison, partagée ; il n'exige pas le droit d'envoyer.*

Voir, pour chaque alerte de l'organisme, qui l'a lue, qui a répondu et qui a pris en charge.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme le module « alertes » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration, direction ou gestionnaire (capacité « alertes.suivre »). (écran `/admin/admin`)
3. Des alertes envoyées (Nouvelle alerte). (écran `/admin/alnouvelle`)

**Comment faire**

1. Filtrer au besoin : « Du », « Au », « Priorité », « État » (prise en charge, sans prise en charge, pas encore lue de tous), « Expéditeur », puis « Filtrer ».
2. Lire la table : lues sur destinataires, nombre de réponses, première prise en charge.
3. « Détail » ouvre les destinataires : lue le, état, répondu le.
4. « Exporter » le même suivi, au même filtre : CSV, Excel, PDF ou JSON. « Actualiser » relit les réponses arrivées depuis.
5. Dans le détail, « Relances et escalades » montre la cascade : l'étape attendue et son heure, puis la trace de chaque étape (qui a été visé, par qui). « Reprendre à l'étape … » la relance aussitôt.
6. Dans le détail, la colonne « Note » montre la note de chaque destinataire ; « Ajouter » ou « Modifier » l'écrit (280 caractères, datée et signée), « Effacer » la retire. Elle ne figure jamais en clair au journal.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Du | 2026-11-01 |
| État | Sans prise en charge |
| Alerte | Code bleu chambre 512 |
| Lues | 12 / 15 |
| Prise en charge | Julie Roy, 2026-11-14 14:03 |
| Format d'export | Excel (XLSX) |

**Résultat :** Lecture seule : le suivi ne change rien aux alertes ; l'export est une pièce datée, à archiver.

### Mes alertes

*Adresse : `/admin/mesalertes` — Tout compte de l'organisme.*

Lire les alertes qui vous visent et y répondre d'un geste.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme le module « alertes » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : n'importe lequel. (écran `/admin/admin`)
3. Être destinataire : membre d'un groupe visé (ressource active de l'unité reliée à son compte), d'une liste de diffusion ou d'un groupe transverse, ou nommé personnellement par l'expéditeur. (écran `/admin/usressources`)
4. Pour recevoir aussi le texto : un numéro de téléphone au dossier (Mon compte) et le fournisseur de textos de l'organisme (Intégrations). (écran `/admin/account`)

**Comment faire**

1. Les non lues sont en tête, marquées « Non lue ».
2. Ouvrir une alerte la marque lue.
3. Répondre par un bouton (« J'arrive », « Indisponible ») : une seule réponse par alerte.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Alerte | Critique — Code bleu chambre 512 |
| Réponse | J'arrive |

**Résultat :** « Réponse … envoyée » ; l'expéditeur voit votre réponse dans son suivi.

> **Attention :** Une alerte critique vous arrive aussi par poussée, texto et courriel, même si vous avez fermé ces canaux pour les autres avis. Sans l'application, répondez au texto reçu : « 1 » (ou « OK ») pour prendre en charge, « 2 » (ou « NON ») pour refuser ; tout autre mot reçoit l'aide et ne compte pas.

### Validation des temps

*Adresse : `/admin/paietemps` — Administration, RH et comptabilité (capacité « paie.gerer »). Valider ne remplace pas l'approbation du gestionnaire.*

Relire, avant la paie, les feuilles de la période et leurs anomalies, puis les valider ou les retourner.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme le module « paie » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration, RH ou comptabilité (capacité « paie.gerer »). (écran `/admin/admin`)
3. Des feuilles approuvées par l'encadrement (Feuilles de temps (console)). (écran `/admin/ustemps`)
4. Un dossier de rémunération et un taux pour chaque personne, sinon l'anomalie « sans dossier » ou « sans taux » (Dossiers de rémunération). (écran `/admin/paiedossiers`)
5. Pour que la validation conditionne la paie : cocher « Exiger la validation des temps par la paie » (Paramètres de la paie). (écran `/admin/paieparametres`)

**Comment faire**

1. Choisir la période, puis « Afficher ».
2. Lire les anomalies de chaque feuille : non approuvée, incalculable, sans dossier, sans taux, plus de 60 heures, validation périmée ; et les personnes sans feuille.
3. « Valider les feuilles sans anomalie » d'un coup, puis « Valider » les autres une à une après vérification.
4. « Retourner » une feuille approuvée qui contient une erreur : dire ce qu'il faut corriger ; elle revient à la personne.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Du | 2026-02-15 |
| Au | 2026-02-28 |
| Motif d'un retour | Le quart du 23 février manque |

**Résultat :** Quand l'organisme exige la validation, seules les feuilles validées entrent dans le prochain cycle de paie.

> **Attention :** Une feuille validée dont les heures changent ensuite (rouverte, corrigée, réapprouvée) perd sa validation : elle est à revalider.

### Cycles de paie

*Adresse : `/admin/paiecycles` — Administration, RH et comptabilité (capacité « paie.gerer »).*

Préparer une paie à partir des feuilles de temps approuvées, la relire, puis la traiter.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme le module « paie » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration, RH ou comptabilité (capacité « paie.gerer »). (écran `/admin/admin`)
3. Les feuilles de temps approuvées de la période (module « effectifs_temps », Feuilles de temps (console)) ; si l'organisme exige la validation, validées dans Validation des temps. (écran `/admin/ustemps`)
4. Un dossier de rémunération par personne (Dossiers de rémunération). (écran `/admin/paiedossiers`)
5. Un taux pour chacun : l'échelle salariale et les primes (Règles de paie variable), ou un taux hors échelle au dossier. (écran `/admin/paieregles`)
6. Les taux de l'organisme : FSS, CNESST, majoration des heures supplémentaires (Paramètres de la paie). (écran `/admin/paieparametres`)
7. Le barème fiscal de l'année de paie, livré avec le produit ; sans lui : « aucun barème de paie publié pour <année> » — à demander au soutien. (écran `/admin/soutien`)

**Comment faire**

1. « Préparer un cycle » : le début et la fin de la période de travail, puis la date de paie. Seules les feuilles approuvées dont la semaine commence dans la période entrent.
2. Relire les bulletins : heures régulières et supplémentaires, brut, retenues, net, coût pour l'employeur ; « Détail » montre chaque gain (heures, primes, forfaits) et chaque retenue.
3. « Registre de paie (PDF) » : une ligne par personne et les totaux, à relire avant de traiter ; « Fiche PDF » produit la fiche de paie d'une personne.
4. Les avertissements nomment qui n'est pas payé : sans dossier, sans taux (ni au dossier, ni à l'échelle), ou feuilles que la paie n'a pas validées quand l'organisme l'exige. Corrigez, puis « Recalculer ».
5. « Traiter la paie » : le cycle est recalculé une dernière fois, puis figé ; ses feuilles passent « payées ».
6. « Annuler le cycle » abandonne un cycle préparé ; ses feuilles restent approuvées.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Début de la période | 2026-02-15 |
| Fin de la période | 2026-02-28 |
| Date de paie | 2026-03-05 |

**Résultat :** Les bulletins sont lisibles par chacun dans « Mes bulletins de paie », et les montants entrent dans les remises du mois.

> **Attention :** Si une feuille a été rappelée ou rouverte entre la préparation et le traitement, le traitement est refusé en entier : recalculez, puis traitez de nouveau. Une feuille payée ne se corrige plus ; une erreur se règle à la paie suivante.

### Dossiers de rémunération

*Adresse : `/admin/paiedossiers` — Administration et RH (capacité « paie.configurer »).*

Dire, pour chaque personne des effectifs, comment elle est payée.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme le module « paie » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration ou RH (capacité « paie.configurer »). (écran `/admin/admin`)
3. Les personnes viennent des ressources des effectifs : un dossier ne se tient que pour une ressource de l'organisme (Ressources). (écran `/admin/usressources`)
4. Pour un taux tiré de l'échelle : l'échelle salariale (Règles de paie variable). (écran `/admin/paieregles`)

**Comment faire**

1. « Créer » ou « Modifier » sur la ligne de la personne.
2. Choisir la rémunération (à l'heure ou au salaire annuel) et la fréquence de paie.
3. À l'heure, saisir l'échelon : le taux vient alors de l'échelle salariale (« Règles de paie variable »). Un taux horaire saisi est un taux hors échelle, qui l'emporte. Au salaire, saisir le salaire annuel.
4. Au besoin, les montants personnels déclarés sur les formulaires TP-1015.3-V (Québec) et TD1 (fédéral) ; laissés vides, ce sont ceux de base de l'année.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Rémunération | À l'heure |
| Fréquence de paie | Aux deux semaines (26 paies) |
| Échelon | 3 |

**Résultat :** La prochaine paie préparée ou recalculée applique ce dossier.

### Remises et relevés

*Adresse : `/admin/paiedeclarations` — Administration, RH et comptabilité (capacité « paie.gerer »).*

Lire ce qu'il faut verser chaque mois et les cases des relevés de l'année.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme le module « paie » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration, RH ou comptabilité (capacité « paie.gerer »). (écran `/admin/admin`)
3. Des cycles de paie traités (Cycles de paie) : un cycle préparé ou annulé n'y figure pas. (écran `/admin/paiecycles`)

**Comment faire**

1. Choisir l'année.
2. Remises mensuelles : le montant à verser à Revenu Québec (impôt du Québec, RRQ, RQAP, FSS) et à l'ARC (impôt fédéral, assurance-emploi), avec l'échéance du 15 du mois suivant.
3. Relevés : pour chaque personne, les cases du RL-1 et du T4 à reporter.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Année | 2026 |

**Résultat :** Les montants ne comptent que les paies traitées ; un cycle préparé ou annulé n'y figure pas.

> **Attention :** La transmission électronique des relevés et des remises se fait hors du produit.

### Mes bulletins de paie

*Adresse : `/admin/mesbulletins` — Toute personne dont le compte est relié à une ressource des effectifs.*

Lire ses propres bulletins de paie, du brut au net.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme le module « paie » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : n'importe lequel. (écran `/admin/admin`)
3. La ressource des effectifs de la personne est reliée à son compte (Gestion des quarts › Ressources, « Compte utilisateur ») : les bulletins sont ceux de cette ressource ; sans elle, aucun. (écran `/admin/usressources`)
4. Un cycle de paie traité qui la comprend (Cycles de paie). (écran `/admin/paiecycles`)

**Comment faire**

1. Ouvrir « Mes bulletins de paie » : la paie la plus récente d'abord.
2. « Détail » montre les gains (heures, primes), chaque retenue, le net et ce que paie l'employeur.
3. « Fiche PDF » télécharge la fiche de paie, avec le cumul de l'année, à conserver.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Bulletin | Paie du 2026-03-05 |

**Résultat :** Vous voyez seulement vos bulletins, une fois la paie traitée.

### Messagerie du personnel

*Adresse : `/admin/usmessages` — Tous les comptes de l'organisme qui ont le module de communication.*

Écrire à une personne ou à un groupe, répondre en privé, réagir, joindre un fichier, et appeler depuis un fil.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_communication » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : n'importe lequel. (écran `/admin/admin`)
3. Les personnes joignables sont les membres des groupes de travail : chaque groupe est une unité en vigueur (Référentiels), et ses membres sont les ressources actives reliées à un compte (Ressources). (écran `/admin/usressources`)

**Comment faire**

1. « Écrire à une personne » : choisir la « Personne » puis « Écrire à cette personne » ; le fil s'ouvre dans « Lire et écrire ».
2. Écrire le « Message », joindre une pièce au besoin, puis « Envoyer » ; l'auteur voit Envoyé, Reçu par N, puis Lu par N.
3. Appeler : « Appeler Marc Dubé » en tête du fil. Le bandeau « ça sonne » paraît en haut de page, chez vous comme chez Marc, qui décroche ou refuse ; « Raccrocher » termine l'appel.
4. Un groupe : « Ouvrir une conversation de groupe » (sujet, premier participant), puis « Appeler le groupe » : chacun rejoint ou ignore.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Personne | Marc Dubé |
| Message | Peux-tu prendre mon quart de jeudi ? |
| Pièce jointe | consigne.pdf (≤ 50 Mo, analysée par l'antivirus) |

**Résultat :** Le message est remis, ses réactions et son historique se lisent dans le fil ; l'appel figure à l'historique des appels.

> **Attention :** La voix passe par votre téléphone habituel : l'application ouvre et trace la ligne, elle ne transporte pas encore la voix.

### Appeler un collègue

*Adresse : `/admin/usappels` — Tous les comptes qui ont le module de communication.*

Joindre directement une personne de son groupe, sans passer par un fil, et relire l'historique de ses appels.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_communication » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : n'importe lequel. (écran `/admin/admin`)
3. Les groupes sont les unités en vigueur, et les personnes à appeler les ressources actives reliées à un compte (Ressources) : une personne sans ressource n'apparaît dans aucun groupe. (écran `/admin/usressources`)

**Comment faire**

1. Choisir le groupe (périmètre de travail), puis la « Personne à appeler ».
2. « Appeler » : le bandeau « ça sonne » paraît aussitôt ; la personne décroche ou refuse.
3. L'historique liste les appels passés et reçus, avec leur durée.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Personne à appeler | Marc Dubé |

**Résultat :** Une sonnerie sans réponse expire au bout de 45 secondes et devient un appel manqué.

### Listes de diffusion du personnel

*Adresse : `/admin/usdiffusion` — Encadrement et personnes qui coordonnent.*

Préparer des listes de destinataires (personnes ou groupes entiers) pour écrire à plusieurs d'un seul envoi.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_communication » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : n'importe lequel. (écran `/admin/admin`)
3. Les membres possibles : les groupes de travail (unités en vigueur) et leurs ressources actives reliées à un compte (Ressources). (écran `/admin/usressources`)

**Comment faire**

1. Créer une liste : nom, partagée ou non.
2. Ajouter des membres : une personne, ou un groupe entier (il se recalcule à chaque envoi).
3. Écrire à la liste depuis la messagerie.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Nom | Équipe de nuit — Est |
| Partagée | Oui |
| Membre (groupe) | OPS — Opérations préhospitalières |

**Résultat :** Une arrivée dans l'unité est jointe à la liste sans que personne ait à la modifier.

### Constructeur de rapports

*Adresse : `/admin/rapports` — Administration et planificateurs.*

Composer un rapport sur les données d'effectifs : source, colonnes, filtres, tri, agrégat ; l'exécuter, l'enregistrer et l'exporter.

**Avant de commencer**

1. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration ou gestionnaire ; chaque source exige sa capacité, « effectifs.manage » pour les sources d'effectifs. (écran `/admin/admin`)
2. Les sources proposées sont celles des modules accordés à l'organisme (par l'exploitant, Administration › Superadmin).
3. L'administration donne au compte de l'encadrement un périmètre sur l'unité, au niveau Lecture au moins (Administration › Paramètres des effectifs › Périmètres) : un gestionnaire ne compte que les unités de son périmètre. Un compte d'administration voit toutes les unités sans périmètre. (écran `/admin/usperimetres`)
4. Des données à compter : périodes publiées, absences, feuilles de temps… (Périodes). (écran `/admin/usperiodes`)

**Comment faire**

1. Choisir la « Source de données », puis cocher les colonnes.
2. « Ajouter un filtre » : champ, opérateur, valeur (et seconde borne pour « entre »).
3. Trier, agréger au besoin, puis exécuter ; nommer le rapport pour l'enregistrer et choisir le « Format » d'export.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Source de données | Quarts et places |
| Champ du filtre | Date |
| Opérateur du filtre | entre |
| Du | 2026-11-01 |
| Au | 2026-11-30 |
| Trier par | Milieu |
| Nom du rapport | Taux de comblement — novembre |
| Format | Excel (XLSX) |

**Résultat :** Le rapport enregistré se relance tel quel ; l'export porte les mêmes colonnes et filtres.

### Approbations

*Adresse : `/admin/approvals` — Toute personne qui encadre ou a reçu une délégation.*

Trancher les demandes qui vous sont adressées, régler les règles d'approbation et déléguer vos approbations pendant une absence.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme le module « approvals » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration, gestionnaire ou direction pour décider ; administration ou gestionnaire pour les règles. (écran `/admin/admin`)
3. Les types de document, tenus par l'administration, puis les règles d'approbation (onglet « Règles ») : sans règle, aucune demande n'est adressée.
4. Pour déléguer : le délégataire a un compte qui peut approuver (Comptes et rôles). (écran `/admin/admin`)

**Comment faire**

1. Onglet « Demandes » : ouvrir une demande, puis « Approuver » ou « Rejeter » avec un commentaire.
2. Onglet « Règles » : type de document, champ, opérateur, valeur, niveau et rôle approbateur.
3. « Déléguer mes approbations » : choisir le délégataire et la période.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Délégataire | Denis Lavoie |
| Du | 2026-12-21 |
| Au | 2027-01-04 |

**Résultat :** La demande avance au palier suivant ou se clôt ; la délégation s'arrête d'elle-même à son terme.

> **Attention :** On n'approuve jamais sa propre demande : c'est la séparation des tâches.

## Administration

### Paramètres des alertes

*Adresse : `/admin/alparametres` — Administration (capacité « alertes.configure »).*

Tenir les réponses prédéfinies et les modèles d'alerte de l'organisme.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme le module « alertes » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration (capacité « alertes.configure »). (écran `/admin/admin`)
3. Pour le « Périmètre » d'un modèle et pour les étapes qui visent un groupe : les unités en vigueur des effectifs (Référentiels). (écran `/admin/usreferentiels`)
4. Pour inscrire une personne à un groupe transverse : son compte existe (Comptes et rôles). (écran `/admin/admin`)

**Comment faire**

1. « Nouvelle réponse » : un libellé et ce qu'il veut dire (prise en charge, refus, information).
2. « Nouveau modèle » : titre, texte, priorité, réponses proposées, et son « Périmètre » (les groupes dont les expéditeurs le voient ; aucun coché : tout l'organisme).
3. « Modifier » un modèle en fait une nouvelle version ; « Versions » montre chaque version, qui l'a faite et quand.
4. « Désactiver » retire une réponse ou un modèle des choix ; rien ne s'efface.
5. Réglages : cocher « Envoyer aussi une copie par courriel » pour que chacun reçoive l'alerte par courriel en plus de ses canaux.
6. Politiques de relance : « Nouvelle politique », un nom, quand s'arrêter (une prise en charge, ou tous ont lu), puis chaque étape — combien de minutes après la précédente, qui viser (relancer ceux qui n'ont pas réagi, tout un groupe, une personne, ou le prochain disponible d'un groupe), par quels canaux — « Appel automatique » fait sonner la personne, qui confirme en appuyant sur 1. « Modifier » fait une version nouvelle.
7. Utilisateurs actifs : choisir le « Mois » ; chaque personne qu'une alerte, une relance ou un appel a visée compte une fois, avec sa première tentative et l'alerte en cause. « Exporter » produit le justificatif du bordereau.
8. Groupes transverses : « Nouveau groupe » (nom, nature : garde, spécialité, comité), puis « Gérer » pour inscrire une personne par son nom, pour une période, comme membre ou modératrice.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Libellé | J'arrive |
| Ce qu'elle veut dire | Prise en charge |
| Modèle — Titre | Code bleu |
| Priorité | Critique |
| Périmètre | Garde de nuit |
| Groupe transverse | Garde de nuit (garde) |
| Personne à inscrire | Julie Roy, du 2026-12-21 au 2026-12-27 |

**Résultat :** La réponse, le modèle ou le groupe est proposé dès la prochaine alerte ; les alertes passées gardent ce qu'elles ont proposé, et la version du modèle dont elles sont parties.

### Paramètres de la paie

*Adresse : `/admin/paieparametres` — Administration et RH (capacité « paie.configurer »).*

Régler les taux propres à l'organisme et lire le barème de l'année.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme le module « paie » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration ou RH (capacité « paie.configurer »). (écran `/admin/admin`)
3. Les taux à reporter : celui du FSS selon la masse salariale de l'organisme, et celui de l'avis de cotisation de la CNESST.

**Comment faire**

1. Saisir le taux du Fonds des services de santé (selon la masse salariale et le secteur ; 4,26 % au secteur public).
2. Saisir le taux de la CNESST indiqué sur l'avis de cotisation de l'organisme.
3. Saisir la majoration des heures supplémentaires : au moins 50 % (Loi sur les normes du travail), davantage si la convention le prévoit.
4. Cocher « Exiger la validation des temps par la paie » pour que seules les feuilles validées entrent dans un cycle.
5. « Enregistrer les réglages ».

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Taux du FSS | 4,26 |
| Taux de la CNESST | 1,78 |
| Majoration des heures supplémentaires | 50 |

**Résultat :** Les prochains calculs appliquent ces taux ; les paies déjà traitées ne changent pas.

### Règles de paie variable

*Adresse : `/admin/paieregles` — Administration et RH (capacité « paie.configurer ») ; la comptabilité paie selon ces règles sans les modifier.*

Tenir l'échelle salariale et les primes de l'organisme, sans livraison du produit.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme le module « paie » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration ou RH (capacité « paie.configurer »). (écran `/admin/admin`)
3. Pour cibler une règle : les titres d'emploi et les conventions des référentiels des effectifs (Référentiels). (écran `/admin/usreferentiels`)
4. Pour qu'un échelon serve : le dossier de rémunération de la personne le porte (Dossiers de rémunération). (écran `/admin/paiedossiers`)

**Comment faire**

1. « Nouvelle règle » : choisir le type — taux de base (échelle), prime en pourcentage du taux, prime horaire, ou forfait par paie.
2. Donner le code du gain (il paraît sur la fiche de paie), le libellé et la valeur.
3. Dire qui est visé : titre d'emploi, convention, échelon ; vides, la règle vise tout le personnel.
4. Pour une prime, dire quelles heures : la plage (heure de Montréal ; une fin avant le début passe minuit), les jours, les fériés, les codes de paie.
5. Donner la période de validité. Une augmentation se saisit comme une nouvelle règle qui commence à sa date ; l'ancienne se termine la veille.
6. « Désactiver » retire une règle des prochains calculs, sans l'effacer.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Type | Prime en pourcentage du taux |
| Code | PRIME_NUIT |
| Libellé | Prime de nuit |
| Valeur | 12 % |
| De | 00:00 |
| À | 08:00 |
| En vigueur à partir du | 2026-01-01 |

**Résultat :** Le prochain cycle préparé ou recalculé paie la prime ; une paie déjà traitée garde ses gains.

> **Attention :** Une heure se paie au taux de base du jour où elle est travaillée : un cycle à cheval sur une augmentation montre deux lignes d'heures, une par taux.

### Référentiels

*Adresse : `/admin/usreferentiels` — Administration.*

Tenir les listes de valeurs datées dont tout dépend : unités, corps d'emploi, statuts d'emploi, natures de quart, types d'heures, codes d'absence, types de milieu.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme le module « effectifs » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration de l'organisme (capacité « effectifs.configure ») ; les autres rôles lisent. (écran `/admin/admin`)
3. Dans l'ordre : les unités (une unité peut avoir une parente), puis les corps d'emploi avant les titres d'emploi qui s'y rattachent ; chaque valeur en vigueur à la date où l'on s'en servira.

**Comment faire**

1. Choisir le « Référentiel », puis ajouter une valeur : code, libellé (français, anglais facultatif), comportement et date d'entrée en vigueur.
2. « Fermer la valeur » à une date plutôt que la supprimer : l'historique reste juste.
3. Promouvoir la configuration d'un environnement à l'autre par un paquet JSON (exporter, puis appliquer).

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Référentiel | Codes d'absence |
| Code | MAL |
| Libellé (français) | Maladie |
| Comportement dans le logiciel | ABSENCE_PAYEE |
| En vigueur le | 2026-01-01 |

**Résultat :** La valeur devient choisissable à partir de sa date ; une valeur fermée ne s'offre plus, mais les données passées la gardent.

### Codes d'absence

*Adresse : `/admin/uscodesabsence` — Administration (capacité « effectifs.configure »).*

Tenir la liste des motifs qu'une personne choisit en déclarant une absence : maladie, vacances, formation, congé parental…

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_absences » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration de l'organisme (capacité « effectifs.configure »). (écran `/admin/admin`)
3. Pour réserver un code à certains titres d'emploi : ces titres existent (Référentiels). (écran `/admin/usreferentiels`)

**Comment faire**

1. « Ajouter une valeur » : le code, le libellé en français (et en anglais au besoin), le comportement dans le logiciel (payée ou non), la durée et la planification, puis la date d'entrée en vigueur.
2. « Remplacer » pour modifier un code à partir d'une date : le libellé, la durée ou le comportement changent pour l'avenir, le passé reste tel qu'il était.
3. « Fermer » pour retirer un code à une date : il ne s'offre plus dans « Mes absences », mais les absences déjà déclarées le gardent.
4. « Historique » montre toutes les versions d'un code.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Code | FORM |
| Libellé (français) | Formation |
| Comportement dans le logiciel | Absence payée |
| En vigueur le | 2026-12-01 |

**Résultat :** Le code apparaît (ou disparaît) dans la liste « Code d'absence » de « Mes absences » et de « Absences » à la date choisie.

> **Attention :** On ne supprime pas un code : on le ferme. Une absence passée doit toujours pouvoir dire sous quel code elle a été déclarée.

### Règles des effectifs

*Adresse : `/admin/usregles` — Administration.*

Écrire les règles de l'organisme par contexte (disponibilités, absences, comblement, échanges, feuille de temps), les simuler, puis les publier.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_regles » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration de l'organisme (capacité « effectifs.configure ») ; la lecture des décisions demande « effectifs.manage ». (écran `/admin/admin`)
3. Pour une règle à portée : les conventions, statuts, corps ou titres d'emploi visés (Référentiels). (écran `/admin/usreferentiels`)
4. Une date d'entrée en vigueur aujourd'hui ou plus tard : « une règle ne peut pas prendre effet dans le passé ».

**Comment faire**

1. Choisir le « Contexte », puis composer la condition : fait, comparaison, valeur visée.
2. Choisir l'« Action » (accepter, avertir, exiger une approbation, refuser) et le « Message affiché à la personne ».
3. « Aperçu d'impact » puis « Simulateur » pour juger un cas ; « Publier » avec la date d'entrée en vigueur.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Contexte | ECHANGE |
| Clé de la règle | entre-periodes |
| Fait | memePeriode |
| Comparaison | = |
| Valeur visée | faux |
| Action | EXIGER_APPROBATION |
| Message affiché à la personne | Échange entre deux périodes : le chef valide |

**Résultat :** La règle s'applique aux gestes faits à partir de sa date ; l'onglet « Décisions » montre chaque décision et ses motifs.

### Milieux physiques

*Adresse : `/admin/usmilieux` — Administration.*

Déclarer les lieux de travail (casernes, centres) avec leur position et leur rayon, et les preuves de présence admises.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme le module « effectifs » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration, direction ou gestionnaire (capacité « effectifs.manage »). (écran `/admin/admin`)
3. L'administration donne au compte de l'encadrement un périmètre sur l'unité, au niveau Gestion (Administration › Paramètres des effectifs › Périmètres) : créer ou modifier un milieu l'exige sur son unité. Un compte d'administration voit toutes les unités sans périmètre. (écran `/admin/usperimetres`)
4. Les référentiels : le type de milieu et l'unité (Référentiels). (écran `/admin/usreferentiels`)

**Comment faire**

1. Créer un milieu : code, libellé, type, unité, adresse, latitude, longitude et rayon.
2. Cocher les « Preuves de présence » acceptées (code sur place, code QR, position, réseau) et celles qui sont exigées.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Code | C12 |
| Libellé | Caserne 12 |
| Type | CASERNE |
| Unité | OPS |
| Latitude | 45.5225 |
| Longitude | -73.5960 |
| Rayon en mètres (10 à 5 000) | 150 |
| Plages réseau | 10.12.0.0/16 |

**Résultat :** Le pointage juge la distance et la preuve selon ce milieu.

### Périmètres

*Adresse : `/admin/usperimetres` — Administration.*

Dire qui gère quelle unité, à quel niveau et depuis quand : sans périmètre, une console s'ouvre sur des listes vides.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme le module « effectifs » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration de l'organisme (capacité « effectifs.configure »). (écran `/admin/admin`)
3. Le compte à qui l'on donne l'accès existe (Comptes et rôles). (écran `/admin/admin`)
4. L'unité en vigueur à la date « À partir du » (Référentiels). (écran `/admin/usreferentiels`)

**Comment faire**

1. Choisir le « Compte ».
2. « Ajouter un accès » : unité, étendue (l'unité seule ou son sous-arbre), niveau (lecture, planification, gestion), « À partir du ».

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Compte | claire.nadeau |
| Unité | OPS |
| Étendue | Sous-arbre |
| Niveau | Planification |
| À partir du | 2026-11-14 |

**Résultat :** Les consoles de ce compte montrent désormais les personnes et les quarts de l'unité.

> **Attention :** Le refus est la règle : un accès oublié n'ouvre rien, et c'est voulu.

### Intégrations des effectifs

*Adresse : `/admin/usintegrations` — Administration.*

Régler les connecteurs (paie, bus d'entreprise, entrepôt de données, horodateurs) et suivre leur boîte d'envoi.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme les modules « effectifs » et « effectifs_integrations » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration de l'organisme (capacité « effectifs.configure ») pour régler les connecteurs ; administration, direction ou gestionnaire (capacité « effectifs.manage »), dans son périmètre, pour suivre la boîte d'envoi. (écran `/admin/admin`)
3. Les coordonnées du système visé (dépôt SFTP, bus, entrepôt), fournies par son exploitant.

**Comment faire**

1. Activer un connecteur et choisir son mode.
2. Suivre la « Boîte d'envoi » : état de chaque message, reprise d'un envoi en échec.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Connecteur | Paie — lot CSV |
| Connecteur activé | Oui |
| Mode | Dépôt SFTP |

**Résultat :** Les lots de paie et les équipages partent selon le connecteur ; un échec reste en boîte jusqu'à sa reprise.

### Rétention des effectifs

*Adresse : `/admin/usretention` — Administration (responsable de la protection des renseignements personnels).*

Fixer combien de temps chaque classe de données est gardée, et purger ce qui a dépassé sa durée.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme le module « effectifs » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration de l'organisme (capacité « effectifs.configure »). (écran `/admin/admin`)
3. La politique de conservation de l'organisme, fixée par la personne responsable de la protection des renseignements personnels.

**Comment faire**

1. « Modifier la politique » d'une classe : durée et motif.
2. Appliquer la purge : seules les données échues sont touchées.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Classe | Pointages et localisations |
| Durée | 24 mois |

**Résultat :** La purge est journalisée ; rien de plus récent que la durée n'est retiré.

### Journal des décisions

*Adresse : `/admin/usjournal` — Administration, direction, vérification.*

Retrouver qui a fait quoi, quand, et par quel mécanisme : chaque décision des règles et chaque geste de l'encadrement.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme le module « effectifs » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration, direction ou gestionnaire (capacité « effectifs.manage »). (écran `/admin/admin`)
3. L'administration donne au compte de l'encadrement un périmètre sur l'unité, au niveau Lecture au moins (Administration › Paramètres des effectifs › Périmètres) : un gestionnaire ne lit que les entrées des unités de son périmètre. Un compte d'administration voit toutes les unités sans périmètre. (écran `/admin/usperimetres`)

**Comment faire**

1. Filtrer par période (du, au exclu), unité, acteur ou action.
2. Ouvrir le « Détail » d'une entrée ; exporter au format voulu.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Du | 2026-11-01 |
| Au (exclu) | 2026-12-01 |
| Action | attribution forcée |
| Format d'export | PDF |

**Résultat :** L'export porte les mêmes filtres que l'écran.

### Utilisateurs et rôles

*Adresse : `/admin/admin` — Administration de l'organisme.*

Créer les comptes de l'organisme, leur donner un rôle, tenir les clés d'API, l'audit, la confidentialité et les tâches différées.

**Avant de commencer**

1. L'exploitant crée l'organisme avec son compte d'administration (Administration › Superadmin : identifiant et mot de passe de l'administrateur).
2. Se connecter avec le rôle administration de l'organisme.
3. Pour lier un compte à un employé : sa fiche du personnel existe ; elle se crée aussi avec la ressource des effectifs (Ressources). (écran `/admin/usressources`)
4. Avec Entra ID : l'annuaire déclaré et sa table groupes → rôles (Intégrations). (écran `/admin/integrations`)

**Comment faire**

1. « Comptes et rôles » : identifiant, rôle, mot de passe provisoire, lien facultatif à l'employé.
2. « Rôles et accès » : composer un rôle propre à l'organisme à partir des capacités.
3. « Journal d'audit », « Confidentialité » (droit à l'oubli), « Clés d'API ».

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Identifiant | claire.nadeau |
| Rôle | manager |
| Mot de passe provisoire | (généré, changé à la première connexion) |

**Résultat :** La personne se connecte, change son mot de passe et configure son second facteur ; avec Entra ID, le compte se crée à la première connexion.

> **Attention :** Avec l'authentification Entra ID, le rôle vient des groupes de l'annuaire : voir UTILISATEURS-PROFILS-ACCES.md.

### Accès

*Adresse : `/admin/access` — Administration.*

Restreindre un objet au-delà du rôle, accorder un droit daté à une personne, et revoir qui accède à quoi.

**Avant de commencer**

1. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration. (écran `/admin/admin`)
2. Les comptes visés par un octroi existent (Comptes et rôles).

**Comment faire**

1. « Objets protégés » : code de l'objet, libellé, permission.
2. « Octrois datés » : sujet, objet, permission, « À partir du », « Jusqu'au », pourquoi.
3. « Qui accède à quoi » : choisir un objet et une date d'observation ; exporter pour la revue trimestrielle.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Type de sujet | Personne |
| Identifiant du sujet | denis.lavoie |
| Objet | Feuilles de temps — Est |
| Permission | approuver |
| À partir du | 2026-12-21 |
| Jusqu'au (facultatif) | 2027-01-04 |
| Pourquoi | Intérim de Claire Nadeau |

**Résultat :** À l'échéance, l'octroi tombe de lui-même.

### Avis

*Adresse : `/admin/notifications` — Administration, ressources humaines, planificateurs.*

Régler les avis envoyés au personnel (courriel, texto, poussée) événement par événement, et suivre leur consommation.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme le module « notifications » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration, gestionnaire ou RH. (écran `/admin/admin`)
3. Pour les textos : le fournisseur de textos de l'organisme (Intégrations) ; chacun choisit ensuite ses canaux dans Mon compte. (écran `/admin/integrations`)

**Comment faire**

1. Choisir un événement, puis les canaux et le gabarit.
2. Lire la consommation du mois (textos en segments, courriels).

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Événement | Quart offert |
| Canaux | Texto, poussée |

**Résultat :** Les personnes reçoivent l'avis selon leurs propres préférences, qu'elles règlent dans Mon compte.

### Intégrations

*Adresse : `/admin/integrations` — Administration.*

Brancher l'organisme à ses systèmes : annuaires (Entra ID, SAML), webhooks, fournisseur de textos, antivirus.

**Avant de commencer**

1. L'exploitant de la plateforme (superadmin) accorde à l'organisme le module « integrations » (Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.
2. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration. (écran `/admin/admin`)
3. Pour Entra ID : l'application inscrite dans l'annuaire de l'organisme (ID du locataire, ID d'application), et son secret posé par l'exploitation dans une variable d'environnement du serveur.

**Comment faire**

1. Déclarer un annuaire Entra ID : ID du locataire, ID d'application, variable du secret, domaines admis, table groupes → rôles.
2. Ajouter un webhook : URL, événements.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Annuaire | Urgences-santé — Entra ID |
| Domaines admis | urgences-sante.qc.ca |
| Groupe → rôle | GEPS-Planification → manager |

**Résultat :** Le bouton de connexion de l'annuaire paraît sur la page de connexion de l'organisme.

### API (OpenAPI)

*Adresse : `/admin/apidocs` — Administration, direction, intégrateurs.*

Lire la description OpenAPI de toutes les routes et les essayer avec son propre compte.

**Avant de commencer**

1. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : administration ou direction. (écran `/admin/admin`)
2. Les routes essayées répondent selon vos droits et les modules accordés à l'organisme.

**Comment faire**

1. Ouvrir une route, lire ses paramètres et ses réponses.
2. « Essayer » : l'appel part avec vos droits, pas plus.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Route | GET /api/effectifs/mon-horaire?du=2026-11-16&au=2026-11-22 |

**Résultat :** La réponse réelle s'affiche ; un refus dit quel droit manque.

### Organismes (exploitation)

*Adresse : `/admin/superadmin` — Exploitant de la plateforme seulement.*

Créer un organisme, lui accorder ses modules, régler ses sous-menus et ses sauvegardes.

**Avant de commencer**

1. Un compte superadmin de la plateforme, remis par l'exploitation.
2. Le plan d'abonnement de l'organisme : un module hors abonnement reste fermé.

**Comment faire**

1. Créer l'organisme, puis cocher ses modules.
2. Masquer au besoin des vues de menu ; planifier les sauvegardes.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Organisme | Urgences-santé |
| Modules | effectifs, effectifs_planification, effectifs_communication… |

**Résultat :** Le menu de l'organisme suit ses modules ; un module coupé ferme aussi ses routes.

## Aide et compte

### Mon compte

*Adresse : `/admin/account` — Tous les comptes.*

Tenir son identité, sa photo, son mot de passe, son second facteur, ses clés d'accès et ses préférences d'avis.

**Avant de commencer**

1. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : n'importe lequel. (écran `/admin/admin`)
2. À la première connexion : changer le mot de passe provisoire et configurer le second facteur.

**Comment faire**

1. Changer son mot de passe (le collage est accepté).
2. Ajouter une clé d'accès (passkey) ou régler l'application d'authentification.
3. Choisir ses canaux d'avis : au moins un canal écrit reste ouvert.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Canaux | Courriel et poussée |

**Résultat :** Les préférences s'appliquent au prochain avis.

### Guide

*Adresse : `/admin/help` — Tous les comptes.*

Le présent guide : notions, déroulements, et une fiche par écran avec un exemple de remplissage.

**Avant de commencer**

1. Un compte actif de l'organisme : le guide ne montre que les fiches des modules accordés et des vues gardées au menu.

**Comment faire**

1. Le bouton « Aide sur cet écran », en haut de chaque page, ouvre directement la fiche de l'écran.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Lien | /admin/help?section=fiche-matemps |

**Résultat :** La fiche s'ouvre et reçoit le focus.

### Soutien

*Adresse : `/admin/soutien` — Tous les comptes.*

Ouvrir un billet auprès du soutien, suivre sa réponse et lire les niveaux et délais de service.

**Avant de commencer**

1. L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : n'importe lequel. (écran `/admin/admin`)
2. Rien d'autre à configurer : le niveau et les délais de service suivent l'entente de l'organisme.

**Comment faire**

1. « Ouvrir un billet » : sujet, gravité, description, capture au besoin.
2. Suivre « Mes billets » et donner son avis à la fermeture.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Sujet | Le bouton Appeler ne sonne pas chez mon collègue |
| Gravité | Élevée |

**Résultat :** Le billet reçoit un numéro ; le délai de réponse dépend de sa gravité.
