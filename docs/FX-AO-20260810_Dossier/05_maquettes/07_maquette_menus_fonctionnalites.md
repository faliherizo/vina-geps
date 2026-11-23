# FX-AO-20260810 — Maquette des menus et des fonctionnalités

> Écrite le 23 novembre 2026 pour l'équipe de soumission et l'évaluateur de l'avis. Elle décrit **ce que VINA GePS
> montre** à chaque profil de l'organisme de démonstration (`https://clincom.geps.vinaitsolution.com`) : le menu, puis
> chaque écran, ses zones et les besoins qu'il sert. Les six maquettes SVG de 2025 (`svg/`, `png/`) étaient indicatives ;
> celle-ci est relevée sur le produit livré.
>
> **Preuve exécutable** : chaque profil et chaque capacité de ce document est une entrée de
> `e2e/donnees/fx-ao/fonctionnalites.json`, jugée par `e2e/tests/e2e/deploiement-fx-ao-fonctionnalites.spec.ts` sur la
> démonstration déployée après chaque déploiement. Les identifiants `CAP-xx` renvoient à ce jeu ; les `EF-xxx`, au
> [catalogue des exigences](../01_analyse/02_exigences_fonctionnelles.md) ; les numéros, à la grille de conformité.

## 1. Les profils de l'organisme

L'addenda 2 (Q20) demande quatre profils minimaux. La démonstration en montre six comptes.

| Profil (addenda 2) | Compte de démonstration | Rôle | Ce qu'il fait |
|---|---|---|---|
| Pilote | `admin` | `admin` | Tient les comptes, les groupes, les cascades, les modèles et les réponses (Q22, Q27) |
| Gestionnaire | `chef.urgence.hsj` — Martin Gagnon | `manager` | Envoie, suit, voit les absences de son équipe ; ne gère ni les groupes ni les cascades (Q22) |
| Expéditeur | `exp.garde.hsj` — Chantal Ouellet | `expediteur` (rôle propre, ADR 0085) | Envoie et suit, sans rien administrer ; reçoit aussi les alertes de son site |
| Expéditrice du critique | `coord.urgence` — Sophie Lalonde | `director` | Seule, avec le pilote, à envoyer en priorité **critique** (critère 18) |
| Destinataire | `inf.roy` — Julie Roy | `employee` | Reçoit, lit, répond d'un geste ; tient ses préférences et ses absences |
| Destinataire de garde | `dre.tremblay` — Dre Isabelle Tremblay | `employee` | Membre et modératrice de la garde de cardiologie |

## 2. Le menu, profil par profil

Le menu est réduit à l'avis : 22 vues sur 56 sont gardées pour l'organisme (`demonstration.json`, `vuesGardees`), et
chaque profil n'en voit que ce que ses droits lui ouvrent. Relevé sur la démonstration le 23 novembre 2026.

```
Menu principal                              admin  coord  chef  exp.  inf.  dre.
├── 📊 Tableau de bord                        ●      ●      ●     ·     ●     ●
├── Disponibilités
│   ├── 🗓️ Mes disponibilités                 ●      ●      ●     ·     ●     ●
│   └── 📋 Disponibilités de la période       ●      ●      ●     ·     ·     ·
├── Absences
│   ├── 🌴 Mes absences d'effectifs           ●      ●      ●     ·     ●     ●
│   └── 📋 Absences et comblement             ●      ●      ●     ·     ·     ·
├── 👥 Ressources                             ●      ●      ●     ·     ·     ·
├── Communication
│   ├── 💬 Messagerie du personnel            ●      ●      ●     ·     ●     ●
│   ├── 📞 Appeler un collègue                ●      ●      ●     ·     ●     ●
│   └── 📇 Listes de diffusion du personnel   ●      ●      ●     ·     ●     ●
└── Alertes
    ├── 🚨 Nouvelle alerte                    ●      ●      ●     ●     ·     ·
    ├── 📋 Suivi des alertes                  ●      ●      ●     ●     ·     ·
    └── 🔔 Mes alertes                        ●      ●      ●     ●*    ●     ●
Administration
├── 📚 Référentiels                           ●      ·      ·     ·     ·     ·
├── 📜 Journal des décisions                  ●      ●      ●     ·     ·     ·
├── ⚙️ Paramètres des alertes                 ●      ·      ·     ·     ·     ·
├── 🛠️ Administration (comptes)               ●      ·      ·     ·     ·     ·
└── 🔐 Objets protégés · Octrois datés · Qui accède à quoi
                                              ●      ·      ·     ·     ·     ·
Aide et compte
├── 🪪 Mon compte                             ●      ●*     ●     ●*    ●     ●
├── ❓ Guide                                  ●      ●      ●     ●*    ●     ●
└── 🛟 Soutien                                ●      ●      ●     ●*    ●     ●

● visible   · absent (le serveur refuse aussi : 403)   * ouvert par le correctif du 23 novembre 2026 (§ 6)
```

## 3. Les écrans d'alerte

### 3.1 Nouvelle alerte — la console d'envoi (`/admin/alnouvelle`)

Profils : pilote, gestionnaire, expéditeur, expéditrice du critique. CAP-01, CAP-02, CAP-17.

```
┌─ Nouvelle alerte ─────────────────────────────────────────────────────────────────────────┐
│ Une alerte priorisée, à des groupes ou des personnes, avec les réponses qu'ils pourront    │
│ donner d'un geste.                                                                          │
│                                                                                             │
│ Modèle        [ Code blanc                     ▾]   ← remplit titre, texte, priorité,       │
│                                                        réponses (EF-006)                    │
│ Titre         [ Code blanc — urgence, salle 3   ]                                           │
│ Message       [ Personne agressive. Équipe d'intervention demandée.          ]              │
│               ⓘ Aucun renseignement de santé dans ce message (addenda 1, Q1)                │
│ Priorité      ( ) Information  (●) Urgente  ( ) Critique ← réservée : direction, pilote     │
│ Réponses      [x] J'arrive  [x] Indisponible  [x] Je rappelle dans 5 min   (EF-010)         │
│ Relance       [ Code bleu — escalade            ▾]   (EF-015 à EF-017)                      │
│ ┌ Destinataires ────────────────────────────────────────────────────────────────────────┐   │
│ │ 1 groupe(s) et 1 personne(s) choisis : Dr Antoine Lavoie.          (annoncé, status)  │   │
│ │ [x] Tout le groupe Hôpital de Saint-Jérôme   [ ] Tout le groupe Garde de cardiologie  │   │
│ │ [ ] Tout le groupe Comité des mesures d'urgence   [ ] Inhalothérapie régionale  …     │   │
│ │ Listes : [ ] Soins infirmiers — urgences                                              │   │
│ │ Chercher une personne par son nom ou son prénom  [ lav            ]   (EF-003)        │   │
│ │ 1 personne(s) trouvée(s).                                                             │   │
│ │ [x] Dr Antoine Lavoie                                                                 │   │
│ │ Choisir des personnes dans un groupe  [ — Choisir un groupe — ▾]                      │   │
│ └───────────────────────────────────────────────────────────────────────────────────────┘   │
│                                                          [ Envoyer l'alerte ]  (EF-001/007) │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
```

| Zone | Besoin | Règle |
|---|---|---|
| Modèle, réponses | EF-006, EF-010 | Les modèles et les réponses sont tenus par le pilote ; une réponse a une sémantique fermée (prise en charge, refus, information) |
| Priorité critique | EF-018, critère 18 | Inactive et expliquée pour qui n'a pas `alertes.critique` ; le serveur refuse aussi (403) |
| Groupes | EF-005, EF-016 | Sites (unités) et groupes transverses de garde, de comité, de spécialité |
| Recherche | EF-003, EF-029 | Dès 3 caractères, au plus 20 noms, **jamais** un numéro ni un courriel |
| Message | Addenda 1, Q1 | Un numéro d'assurance maladie est refusé (ST-H04) |

### 3.2 Suivi des communications (`/admin/alsuivi`)

Profils : pilote, gestionnaire, expéditeur. Le suivi est **partagé** : chaque expéditeur voit les alertes des autres
(addenda 2, Q26, Q31). CAP-06, CAP-07.

```
┌─ Suivi des communications ────────────────────────────────────────────────────────────────┐
│ Filtres : Priorité [toutes ▾]  État [tous ▾]  Expéditeur [ ]  Du [    ] Au [    ] [Filtrer] │
│ ┌ Alertes envoyées ──────────────────────────────────────────────────────────────────────┐ │
│ │ Titre                              Priorité  Expéditeur      Visés Lues Rép. Prise en charge │
│ │ Code bleu — 4e étage, chambre 412  critique  coord.urgence     3    1    0   —          │ │
│ │ Rappel de garde — cardiologie      info      chef.urgence.hsj  2    2    2   Dre Tremblay│ │
│ │ Code blanc — urgence, salle 3      urgente   chef.urgence.hsj  4    0    0   — (cascade) │ │
│ └────────────────────────────────────────────────────────────────────────────────────────┘ │
│ [ Exporter CSV ] [ XLSX ] [ PDF ]                                                          │
│ ┌ Fiche : Code blanc — urgence, salle 3 ─────────────────────────────────────────────────┐ │
│ │ Remises : personne · envoyée · lue · réponse · relances  (EF-011)                     │ │
│ │ Note sur le destinataire : « En congé jusqu'au 20 : joindre la Dre Tremblay » (EF-002)│ │
│ │ Cascade « Code bleu — escalade », étape 2 sur 3, prochaine échéance 14 h 07 (EF-019)  │ │
│ │  Quand   Événement  Étape  Personnes visées                         Par               │ │
│ │  14:02   armée        1    —                                         chef.urgence.hsj  │ │
│ │  14:04   étape        1    exp.garde.hsj, dre.tremblay, inf.roy, …   cascade          │ │
│ │  14:09   étape        2    — (Dr Lavoie en congé : non visé)         cascade          │ │
│ │  Appels vocaux : personne · étape · résultat · touche · durée (EF-014)                │ │
│ │ [ Reprendre à l'étape … ]                                                             │ │
│ └────────────────────────────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 3.3 Mes alertes — au téléphone (`/admin/mesalertes`, 390 px)

Profils : tous les comptes de l'organisme. CAP-08 (EF-009, EF-020, EF-024).

```
┌───────────────────────────────┐
│ ☰  Mes alertes                │
│ Les non lues d'abord.         │
│ ┌───────────────────────────┐ │
│ │ CRITIQUE · non lue        │ │
│ │ Code bleu — 4e étage,     │ │
│ │ chambre 412               │ │
│ │ Arrêt cardiorespiratoire… │ │
│ │ De : Sophie Lalonde       │ │
│ │ [ J'arrive ] [Indisponible]│ │
│ └───────────────────────────┘ │
│ ┌───────────────────────────┐ │
│ │ Information · répondue    │ │
│ │ Rappel de garde —         │ │
│ │ cardiologie   « J'arrive »│ │
│ └───────────────────────────┘ │
└───────────────────────────────┘
```

Une réponse se donne d'un geste ; la prise en charge arrête la cascade ; une seconde réponse est refusée.

### 3.4 Paramètres des alertes (`/admin/alparametres`)

Profil : pilote seulement (addenda 2, Q22). CAP-03, CAP-04, CAP-06, CAP-10, CAP-16.

```
┌─ Paramètres des alertes ──────────────────────────────────────────────────────────────────┐
│ Réglages de l'organisme : [x] Copie par courriel de chaque alerte (EF-008)                 │
│                                                                                            │
│ Réponses prédéfinies               [ Nouvelle réponse ]                                    │
│   J'arrive · prise en charge · active      Indisponible · refus · active                   │
│   Je rappelle dans 5 min · information · active                                            │
│ Modèles d'alerte                   [ Nouveau modèle ]   (versionnés, signés, périmètre)    │
│   Code bleu · critique · 2 réponses   Code blanc · urgente   Rappel de garde · information │
│                                                                                            │
│ Politiques de relance              [ Nouvelle politique ]   (EF-015 à EF-017)              │
│   Code bleu — escalade · arrêt à la prise en charge · v1                                   │
│     1. initiaux · poussée + texto · après 2 min                                            │
│     2. prochain disponible de « Garde de cardiologie » · texto · après 5 min               │
│     3. initiaux · appel vocal · après 10 min                                               │
│                                                                                            │
│ Utilisateurs actifs du mois : 7 ciblés · facturables 100 (minimum du contrat, 3.03)        │
│   [ Exporter le justificatif ]                                                             │
│                                                                                            │
│ Groupes transverses                [ Nouveau groupe ]                                      │
│   Nom                            Nature       Membres   Action                             │
│   Garde de cardiologie           garde           2      [ Gérer le groupe ]                │
│   Comité des mesures d'urgence   comité          3      [ Gérer le groupe ]                │
│   Inhalothérapie régionale       spécialité      1      [ Gérer le groupe ]                │
│ ┌ Groupe « Garde de cardiologie » (dialogue) ───────────────────────────────────────────┐  │
│ │ Inscrire [ Personne… ] Depuis [2026-10-24] Jusqu'à [          ] [x] Modérateur        │  │
│ │ Personne               Période              Rôle                        Action        │  │
│ │ Dre Isabelle Tremblay  depuis le 24 oct.    Modératrice ou modérateur   [ Retirer ]   │  │
│ │ Dr Antoine Lavoie      depuis le 24 oct.    Membre                      [ Retirer ]   │  │
│ └───────────────────────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────────────────────┘
```

**La garde** est un groupe transverse de nature `garde`, aux appartenances **datées** : on inscrit la personne de
garde pour sa période, et la relève se prépare d'avance. **La disponibilité** se lit au calendrier interne (§ 4) : à
l'étape « prochain disponible », la cascade prend le premier membre de la garde qui n'est ni déjà visé, ni absent ce
jour-là (`GestionnaireCascades.prochainDisponible`, `DisponibiliteDepuisEffectifs`).

## 4. Disponibilité et absences

CAP-05 (EF-025). Une personne est indisponible le jour où une absence **acceptée** la couvre, ou celui où elle s'est
déclarée indisponible. Une absence se déclare par la personne ; une règle peut la soumettre à approbation.

```
┌─ Mes absences d'effectifs (dr.lavoie) ─────────┐   ┌─ Absences et comblement (pilote, gestionnaire) ───────┐
│ [ Déclarer une absence ]                        │   │ Personne            Code  Du         Au       Statut  │
│ Code   Du          Au          Statut           │   │ Dr Antoine Lavoie   CONG  22 nov.    7 déc.   Acceptée│
│ CONG   22 nov.     7 déc.      Acceptée         │   │ [ Approuver ] [ Refuser ] [ Comblement ]              │
└─────────────────────────────────────────────────┘   └───────────────────────────────────────────────────────┘
┌─ Mes disponibilités ───────────────────────────┐
│ Période de collecte, plages, conformité aux     │   Disponibilités de la période : la vue du gestionnaire.
│ règles ; une indisponibilité déclarée retire    │
│ la personne des cascades ce jour-là.            │
└─────────────────────────────────────────────────┘
```

## 5. Communication, compte et administration

| Écran | Profils | Zones | Besoins (CAP) |
|---|---|---|---|
| Messagerie du personnel | tous sauf l'expéditeur | Fils à deux et de groupe, pièces, réactions, accusé de lecture | EF-004 (CAP-15) |
| Appeler un collègue | idem | Appel par le numéro du centre : le numéro du collègue n'est jamais affiché | EF-029 (CAP-15) |
| Listes de diffusion | idem | Listes personnelles ou partagées, visées par la console | EF-005 (CAP-01) |
| Mon compte | tous | Profil, mot de passe, double authentification, clés d'accès, **préférences d'avis** par canal ; une règle obligatoire (texto d'une alerte critique) passe outre | EF-022 (CAP-11) |
| Administration | pilote | Comptes par profil et par site, désactivation immédiate, annuaire LDAPS déclaré, journal des actions | EF-026, EF-027, EF-033 (CAP-13) |
| Objets protégés · Octrois datés · Qui accède à quoi | pilote | Droits par rôle, octrois temporaires, revue périodique (addenda 2, Q27 à Q30) | EF-030 (CAP-12) |
| Référentiels | pilote | Les six sites du devis, les corps d'emploi, les codes d'absence | EF-038 (CAP-14) |
| Journal des décisions · Soutien | pilote ; soutien pour tous | Décisions tracées ; demandes et incidents suivis | EF-034, EF-039 (CAP-18) |

## 6. Ce que la validation du 23 novembre 2026 a changé

| Constat | Correctif | Preuve |
|---|---|---|
| L'expéditeur (rôle propre) ne voyait ni « Mes alertes », ni « Mon compte », ni « Guide », ni « Soutien », alors que le serveur les lui sert ; la direction n'avait pas « Mon compte » | L'espace personnel s'ouvre à tout compte d'organisme (`droitsDeMenu.ts`, `espacePersonnel`) | `droits-de-menu.test.ts` ; profils `exp.garde.hsj`, `coord.urgence` |
| Critère 3 : la console ne cherchait pas un destinataire par son nom ; la seule recherche était réservée au pilote | `GET /api/alertes/personnes?q=` pour qui envoie, et le champ « Chercher une personne » | `ControleurAlertesHttpTest.rechercheParNom`, `alertes.test.tsx` ; CAP-02 |
| Le calendrier interne était vide dans la démonstration : la cascade « prochain disponible » n'avait rien à montrer | Le semis déclare le congé du Dr Lavoie, accepté (`demonstration.json`, `absences`) | CAP-05, CAP-06 |
| L'organisme Clincom déployé avait été semé à moitié (semis annulé) : ni modèles, ni réponses, ni cascade, un groupe sur trois manquant ; les semis suivants le croyaient complet | Un organisme naît marqué « [semis en cours] » et perd la marque au dernier geste ; l'orchestrateur ignore un exemplaire marqué | `scripts/lib/api-semis.test.cjs` ; CAP-03, CAP-04, CAP-06 |
