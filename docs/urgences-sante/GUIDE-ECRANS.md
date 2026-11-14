# VINA GEPS : guide écran par écran

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

**Comment faire**

1. Ajouter une ressource avec son matricule, son nom, son unité, son corps d'emploi et son statut.
2. Renseigner l'« Ancienneté (selon la convention) » : elle ordonne l'AGPH et les remplacements.
3. Relier la ressource au compte de la personne (« Personne ») : sans ce lien, ses écrans personnels restent vides.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Matricule | P100 |
| Nom | Julie Roy |
| Unité | OPS |
| Corps d'emploi | PARA |
| Statut | TC — Temps complet |
| Ancienneté (selon la convention) | 120 |
| Personne | julie.roy |

**Résultat :** La personne devient planifiable et voit son horaire, ses disponibilités et sa feuille de temps.

### Quotas

*Adresse : `/admin/usquotas` — Planificateurs et direction.*

Fixer le nombre d'équipages opérationnels visés par jour, par unité, plage ou nature de quart, et poser une surcharge temporaire.

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

**Comment faire**

1. « Préparer ma feuille » avec le « Début de la période » et la « Fin de la période » : les segments viennent des quarts et des pointages.
2. Vérifier chaque segment : la ressource (nom et matricule), les heures, le type d'heure, les « Tâches » et les champs propres à votre organisme.
3. « Ajouter un segment » pour un temps manquant (jour, de, à, type d'heure), avec la « Description des tâches » et les champs demandés (ex. Véhicule) ; ou modifier un segment et « Enregistrer la correction ».
4. « Soumettre ma feuille » : elle part au premier palier d'approbation — souvent votre supérieur hiérarchique. « En attente de » dit chez qui elle est.

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

> **Attention :** Une feuille retournée porte son motif : corriger puis soumettre à nouveau. Un champ marqué « obligatoire » doit être rempli pour que le segment soit accepté.

### Feuilles de temps (console)

*Adresse : `/admin/ustemps` — Planificateurs, chefs, paie.*

Préparer les feuilles d'une période, les approuver palier par palier, lire les relevés et produire le lot de paie.

**Comment faire**

1. « Préparer les feuilles » pour une unité et une période.
2. Ouvrir une feuille soumise : approuver, ou retourner avec un motif obligatoire.
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

### Mes échanges

*Adresse : `/admin/mesechanges` — Toute personne planifiable.*

Proposer d'échanger un de ses quarts contre celui d'un collègue, et accepter ou refuser ceux qu'on vous propose.

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

### Nouvelle alerte

*Adresse : `/admin/alnouvelle` — Administration, direction et planificateurs (capacité « alertes.envoyer »).*

Envoyer une alerte priorisée à des groupes ou à des personnes, avec les réponses qu'elles pourront donner d'un geste.

**Comment faire**

1. Choisir un « Modèle » au besoin : il remplit le titre, le message, la priorité et les réponses ; tout reste modifiable.
2. Écrire le « Titre » et le « Message », puis choisir la « Priorité ». Une alerte critique part sur tous les canaux, quelles que soient les préférences.
3. Choisir les destinataires : « Tout le groupe … », ou des personnes, par leur nom, dans « Choisir des personnes dans un groupe ».
4. Cocher les « Réponses proposées », puis « Envoyer l'alerte ».

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

> **Attention :** Vous n'êtes jamais destinataire de votre propre alerte, même si vous êtes dans le groupe visé.

### Suivi des alertes

*Adresse : `/admin/alsuivi` — Administration, direction et planificateurs : le suivi est partagé entre expéditeurs.*

Voir, pour chaque alerte de l'organisme, qui l'a lue, qui a répondu et qui a pris en charge.

**Comment faire**

1. Lire la table : lues sur destinataires, nombre de réponses, première prise en charge.
2. « Détail » ouvre les destinataires : lue le, état, répondu le.
3. « Actualiser » relit les réponses arrivées depuis.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Alerte | Code bleu chambre 512 |
| Lues | 12 / 15 |
| Prise en charge | Julie Roy, 2026-11-14 14:03 |

**Résultat :** Lecture seule : le suivi ne change rien aux alertes.

### Mes alertes

*Adresse : `/admin/mesalertes` — Tout compte de l'organisme.*

Lire les alertes qui vous visent et y répondre d'un geste.

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

> **Attention :** Une alerte critique vous arrive aussi par poussée, texto et courriel, même si vous avez fermé ces canaux pour les autres avis.

### Messagerie du personnel

*Adresse : `/admin/usmessages` — Tous les comptes de l'organisme qui ont le module de communication.*

Écrire à une personne ou à un groupe, répondre en privé, réagir, joindre un fichier, et appeler depuis un fil.

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

**Comment faire**

1. « Nouvelle réponse » : un libellé et ce qu'il veut dire (prise en charge, refus, information).
2. « Nouveau modèle » : titre, texte, priorité et réponses proposées.
3. « Désactiver » retire une réponse ou un modèle des choix ; rien ne s'efface.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Libellé | J'arrive |
| Ce qu'elle veut dire | Prise en charge |
| Modèle — Titre | Code bleu |
| Priorité | Critique |

**Résultat :** La réponse ou le modèle est proposé dès la prochaine alerte ; les alertes passées gardent ce qu'elles ont proposé.

### Référentiels

*Adresse : `/admin/usreferentiels` — Administration.*

Tenir les listes de valeurs datées dont tout dépend : unités, corps d'emploi, statuts d'emploi, natures de quart, types d'heures, codes d'absence, types de milieu.

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

### Règles des effectifs

*Adresse : `/admin/usregles` — Administration.*

Écrire les règles de l'organisme par contexte (disponibilités, absences, comblement, échanges, feuille de temps), les simuler, puis les publier.

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

**Comment faire**

1. « Ouvrir un billet » : sujet, gravité, description, capture au besoin.
2. Suivre « Mes billets » et donner son avis à la fermeture.

**Exemple de remplissage**

| Champ | Valeur saisie |
|---|---|
| Sujet | Le bouton Appeler ne sonne pas chez mon collègue |
| Gravité | Élevée |

**Résultat :** Le billet reçoit un numéro ; le délai de réponse dépend de sa gravité.
