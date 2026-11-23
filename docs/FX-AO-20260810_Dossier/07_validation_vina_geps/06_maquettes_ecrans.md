# FX-AO-20260810 : maquettes du module « Communications cliniques » dans VINA GePS

> Écrit pour la conception et la soumission. Une maquette par écran, dans le gabarit de VINA GePS
> (barre marine, colonne blanche, ADR 0073), avec le menu, ce que voit chaque profil et les
> critères de la grille que chaque écran sert. Ce sont des maquettes de conception : rien n'est
> implanté (ADR 0074 proposé).

## 0. Le menu d'un organisme « communications seulement » (Santé Québec Laurentides)

```
┌────────────────────────────────────────────────────────────────────────────────┐
│ VINA GePS   CISSS des Laurentides             ✉ 3  🔔 1  ? Aide   Dre Tremblay ▾ │
├──────────────────────┬─────────────────────────────────────────────────────────┤
│ MENU PRINCIPAL       │                                                         │
│ ▣ Tableau de bord    │   (écran courant)                                       │
│   Alertes ›          │                                                         │
│     Nouvelle alerte  │                                                         │
│     Suivi            │                                                         │
│   Mes alertes        │   ← le destinataire : celles qui le visent              │
│   Communication ›    │                                                         │
│     Messagerie       │                                                         │
│     Appeler          │                                                         │
│     Listes           │                                                         │
│   Rapports           │                                                         │
│ ADMINISTRATION       │                                                         │
│   Paramètres des alertes › Modèles · Réponses · Politiques · Groupes           │
│   Utilisateurs et rôles · Accès · Avis · Intégrations                          │
│ AIDE ET COMPTE       │                                                         │
└──────────────────────┴─────────────────────────────────────────────────────────┘
```

Les rubriques d'effectifs (Mon horaire, Gestion des quarts…) n'apparaissent pas : l'organisme n'a
pas ces modules. Pour Urgences-santé, les mêmes entrées « Alertes » s'ajoutent au menu existant
dès que le superadmin active le module `alertes`.

## 1. Nouvelle alerte (console d'envoi) — critères 1, 3, 5, 6, 7, 8, 18

```
Tableau de bord › Alertes › Nouvelle alerte
Nouvelle alerte
┌───────────────────────────────────────────────────────────────────────────┐
│ Modèle         [Code bleu — 5e étage                                   ▾] │
│ Priorité       ( ) Information  ( ) Urgente  (•) Critique                 │
│ Destinataires  [Rechercher une personne, un groupe, une liste…  🔍]       │
│                ✕ Équipe code bleu — Saint-Jérôme (14)   ✕ Dr Gagnon       │
│ Message        [Code bleu chambre 512, 5e étage. Répondez à la prise …]   │
│ Réponses       ☑ J'arrive (prise en charge)  ☑ Indisponible  ☐ Autre      │
│ Politique      [Cascade code bleu : 2 min, puis garde, puis appel     ▾]  │
│ Canaux         Poussée critique · Texto · Appel (imposés par la priorité) │
│ ☑ Copie par courriel aux coordonnateurs (réglé par l'organisme)           │
│                                         [Aperçu]   [ Envoyer l'alerte ]   │
└───────────────────────────────────────────────────────────────────────────┘
15 destinataires résolus · 2 sans appareil : texto puis appel
```

- La recherche par nom ou prénom (critère 3) nomme toujours la personne, jamais un identifiant.
- « Aperçu » montre le snapshot exact des destinataires avant l'envoi.
- Une alerte critique exige la capacité `alertes.critique`. Sans elle, l'option est absente,
  pas grisée.

## 2. Suivi des communications — critères 9, 11, 12, 19, 31, 89

```
Tableau de bord › Alertes › Suivi
Suivi des communications                       [Période ▾] [Priorité ▾] [Groupe ▾]
┌─────┬──────────┬────────────────────────┬────────┬─────────────────────────┬──────┐
│ ●   │ 14:02    │ Code bleu chambre 512  │ 15     │ Pris en charge par      │Ouvrir│
│     │ Critique │ Équipe code bleu       │ 12 lus │ Dr Gagnon à 14:03       │      │
├─────┼──────────┼────────────────────────┼────────┼─────────────────────────┼──────┤
│ ◐   │ 13:40    │ Remplacement inhalo    │ 6      │ Étape 2/3 · relance     │Ouvrir│
│     │ Urgente  │ Inhalothérapie         │ 4 lus  │ dans 3 min              │      │
└─────┴──────────┴────────────────────────┴────────┴─────────────────────────┴──────┘
[Exporter : CSV · Excel · PDF/A]
```

Le statut est écrit en mots et doublé d'une forme (● ◐ ○), jamais porté par la seule couleur.

## 3. Fiche d'une alerte — critères 11, 13, 14, 17, 19

```
Code bleu chambre 512 · Critique · envoyée 14:02 par Inf. Lavoie
Cascade « code bleu » — étape 1/3 · arrêtée à 14:03 (prise en charge)     [Reprendre ▾]
┌──────────────────┬────────┬────────┬────────┬───────────────┬──────────────┐
│ Destinataire     │ Canal  │ Remise │ Lue    │ Réponse       │ Appel        │
│ Dr Gagnon        │ Poussée│ 14:02  │ 14:02  │ J'arrive 14:03│ —            │
│ Inf. Côté        │ Texto  │ 14:02  │ —      │ —             │ 14:04 ✓ « 1 »│
│ Inh. Morin       │ Poussée│ échec  │ —      │ —             │ messagerie   │
└──────────────────┴────────┴────────┴────────┴───────────────┴──────────────┘
Journal : 14:02 envoi (15) · 14:02 remise (13) · 14:03 prise en charge · 14:04 appel (Côté)…
[Ouvrir la conversation de suivi]
```

## 4. Mes alertes (destinataire, mobile) — critères 9, 10, 20, 21, 24

```
┌──────────────────────────────┐
│ VINA GePS            🔔 2  ☰ │
│ ⚠ CRITIQUE · 14:02           │
│ Code bleu chambre 512,       │
│ 5e étage.                    │
│ ┌──────────────────────────┐ │
│ │ J'arrive                 │ │  ← cible de 44 px, prise en charge
│ ├──────────────────────────┤ │
│ │ Indisponible             │ │
│ └──────────────────────────┘ │
│ Envoyée par Inf. Lavoie      │
│ ─ Historique ─               │
│ 13:40 Remplacement inhalo ✓  │
└──────────────────────────────┘
```

Sans l'application, le même choix se fait par texto : « Répondez 1 pour J'arrive, 2 pour
Indisponible » (ST-G05).

## 5. Paramètres des alertes › Modèles et réponses — critères 6, 10, 32

```
Modèles d'alerte                                             [+ Nouveau modèle]
┌────────────────────────┬──────────┬──────────────────────┬─────────┬────────┐
│ Modèle                 │ Priorité │ Réponses proposées   │ Version │ Actions│
│ Code bleu — 5e étage   │ Critique │ J'arrive · Indispo.  │ v3      │ ⋯      │
│ Remplacement de quart  │ Urgente  │ Je prends · Non      │ v1      │ ⋯      │
└────────────────────────┴──────────┴──────────────────────┴─────────┴────────┘
Réponses prédéfinies : libellé · sémantique (Prise en charge | Refus | Information) · mot-clé texto
```

## 6. Paramètres des alertes › Politiques de relance et cascades — critères 15, 16, 17, 25

```
Politique « Cascade code bleu »                                     version 4
┌───┬───────────────────────────┬───────────────────┬───────┬──────────────────┐
│ # │ Cible                     │ Canaux            │ Délai │ Arrêt            │
│ 1 │ Équipe code bleu (groupe) │ Poussée critique  │ 2 min │ 1 prise en charge│
│ 2 │ Médecin de garde (dispo.) │ Poussée + texto   │ 3 min │ 1 prise en charge│
│ 3 │ Coordonnateur             │ Appel automatique │ 5 min │ réponse « 1 »    │
└───┴───────────────────────────┴───────────────────┴───────┴──────────────────┘
Relances : 2 par étape, toutes les 60 s · Reprise : manuelle, à l'étape choisie
[Simuler sur l'horaire de ce soir]   [Publier la version 5]
```

« Simuler » rejoue la politique sur les disponibilités réelles, sans rien envoyer. Le simulateur des
règles d'effectifs fonctionne de la même façon.

## 7. Paramètres des alertes › Groupes transverses — critères 27, 28

```
Groupes                                                     [+ Nouveau groupe]
Nom                       Type          Membres  Modérateurs     Site
Équipe code bleu — SJ     Garde         14       Inf. Lavoie     Saint-Jérôme
Inhalothérapie            Spécialité    22       —               Tous
Comité des mesures d'urg. Comité         9       Dre Tremblay    Tous
```

## 8. Superadmin › Organismes › Modules — gestion par organisme

```
Organisme : CISSS des Laurentides
Modules                                        Activé   Exige
Communication du personnel (messagerie)         ☑        —
Alertes (console, suivi, modèles)               ☑        —
Relances et cascades                            ☑        Alertes
Téléphonie (appel automatique, DTMF)            ☑        Relances et cascades
Alertes critiques mobiles                       ☐        Alertes
Disponibilités pour les cascades                ☐        Alertes + Effectifs
Planification des effectifs                     ☐        —
[Enregistrer]       Un module coupé ferme son menu et ses routes (garde au serveur).
```

## 9. Tableau de bord des alertes — critères 11, 31

```
Tableau de bord gestionnaire · Alertes du jour
[ 07 alertes envoyées ] [ 02 en cascade ] [ 00 sans réponse ] [ 94 % prises en 5 min ]
Onglets : En cours · Sans réponse · Escaladées · Terminées
```

Les tuiles suivent le patron de la planche s01 : chaque tuile est un bouton qui ouvre l'onglet
correspondant.

## 10. Rapports — critères 93, 111

Les sources de données « Alertes » et « Remises » s'ajoutent au constructeur de rapports existant :
délai de prise en charge par groupe, taux de réponse par canal, utilisateurs actifs du mois.
