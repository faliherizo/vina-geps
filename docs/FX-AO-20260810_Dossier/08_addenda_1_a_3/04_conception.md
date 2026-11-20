# FX-AO-20260810 — Conception des stories ST-H01 à ST-H05

> Les décisions et les options écartées sont dans l'[ADR 0085](../../adr/0085-addenda-fx-ao-preavis-suivi-facturation.md).
> Ce document dit **où** chaque story vit, **ce qui change** dans l'API, et **quel essai** la tient.

## Vue d'ensemble

```
alertes (contexte)                                 soutien (contexte)
├─ domaine                                         ├─ domaine
│  ├─ UtilisateursActifs.MinimumFacturable  H01    │  ├─ NatureChangement (enum fermé)          H02
│  ├─ ContenuSansRenseignementDeSante       H04    │  ├─ JoursOuvrablesDuQuebec (pure)          H02
│  └─ Alertes.composer / ContenuModele,            │  ├─ Soutien.validerMaintenance → preavis   H02
│     NotesDestinataires.Note.ecrire        H04    │  └─ EngagementsDeService.Politique
├─ application/requetes                            │        + COMMUNICATIONS_CLINIQUES           H05
│  └─ LectureUtilisateursActifs.Bilan       H01    └─ infrastructure/persistance/DepotSoutien    H02
└─ infrastructure                                     (colonne nature, changeset 0019)
   ├─ configuration : minimum par propriété H01
   └─ web : GardeAlertes.CAPACITE_SUIVI      H03
           ControleurAlertes, ControleurCascades
politique-acces.json : alertes.suivre        H03
```

Les règles restent dans le domaine, qui ne dépend de rien. Le minimum facturable et le droit de suivre arrivent par
la configuration ou par la table des droits, jamais par une condition écrite dans le code métier.

## ST-H01 — Minimum facturable

| Couche | Changement |
|---|---|
| Domaine | `UtilisateursActifs.MinimumFacturable(int)` refuse une valeur négative ; `facturables(actifs) = max(minimum, actifs)`. |
| Application | `LectureUtilisateursActifs(TentativesPort, MinimumFacturable)` ; `Bilan(mois, total, minimumFacturable, facturables, actifs)`. |
| Configuration | `alertes.facturation.minimum-utilisateurs=${ALERTES_MINIMUM_FACTURABLE:100}`. |
| API | `GET /api/alertes/utilisateurs-actifs` rend en plus `minimumFacturable` et `facturables`. L'export les porte en tête du justificatif. |
| Écran | « Utilisateurs actifs » : nouvelle ligne `alp-actifs-facturables`, qui dit quand le minimum s'applique. |
| Essais | `ControleurUtilisateursActifsHttpTest` (minimumFacturable, exportPorteLeFacturable) ; `alertes-cascades.test.tsx`. |

## ST-H02 — Préavis par nature de changement

```
POST /api/soutien/maintenances { debut, fin, description, nature? }
  └─ Soutien.validerMaintenance(m, maintenant)
       └─ preavis(nature, debut, maintenant)
            ├─ NatureChangement.de(code)       absent → ENTRETIEN ; inconnu → « nature : … inconnue (attendu : …) »
            └─ nature.premierDebutAdmis(maintenant)
                 ├─ ENTRETIEN     → maintenant + 72 h      (message d'origine conservé)
                 └─ CORRECTIF (3) / REHAUSSEMENT (21)
                      → JoursOuvrablesDuQuebec.premierDebutAdmis : N jours ouvrables pleins, puis minuit à Montréal
```

| Couche | Changement |
|---|---|
| Base | `soutien_maintenances.nature TEXT NOT NULL DEFAULT 'entretien'` (changeset `0019`, retour `db/retour/0019-retour.sql`). |
| API | `nature` accepté à l'annonce et rendu dans la liste des maintenances. |
| Écran | Soutien › Maintenances : liste « Nature du changement » (`sa-maint-nature`), colonne Nature dans le tableau. |
| Essais | `PreavisDeChangementTest` (fériés, Pâques, comptage, refus) ; `ControleurSoutienHttpTest.maintenanceSelonSaNature` ; `RetourArriereSchemaTest` (0019). |

## ST-H03 — Suivre sans envoyer

| Route | Avant | Après |
|---|---|---|
| `GET /api/alertes/envoyees`, `/envoyees/export`, `/envoyees/{id}` | `alertes.envoyer` | `alertes.suivre` |
| `GET /api/alertes/envoyees/{id}/cascade` | `alertes.envoyer` | `alertes.suivre` |
| `POST /api/alertes`, `POST …/cascade/reprendre`, notes | `alertes.envoyer` | inchangé |
| `GET /api/alertes/droits` | envoyer, critique, configurer | + `suivre` |

Par défaut, `alertes.suivre` revient aux mêmes rôles qu'`alertes.envoyer` (admin, direction, gestion) : aucun accès ne se
perd. Le menu passe de la liste des rôles aux capacités (`PAGE_CAPABILITIES` : `alnouvelle` → `alertes.envoyer`,
`alsuivi` → `alertes.suivre`). L'organisme de démonstration FX-AO sème le rôle propre `expediteur` et une personne qui le
porte (`e2e/donnees/fx-ao/demonstration.json` › `rolesPropres`).

Essais : `ControleurAlertesHttpTest.suivreSansEnvoyer` (surcharge d'organisme qui retire l'envoi au gestionnaire),
`droits`, `DecisionAccesTest.directeur`, `droits-de-menu.test.ts`.

## ST-H04 — Aucun renseignement de santé

`ContenuSansRenseignementDeSante.verifier(champ, texte)` cherche un numéro d'assurance maladie : quatre lettres puis
huit chiffres, séparés ou non par une espace ou un tiret, non collés à une autre lettre ou un autre chiffre. Il est appelé
après la validation de longueur, sur le titre et le corps d'une alerte, d'un modèle, et sur le texte d'une note. Le refus
(400) nomme le champ et jamais le texte. L'écran « Nouvelle alerte » relie au champ Message un rappel lu par les
lecteurs d'écran (`aria-describedby="aln-corps-aide"`).

Essais : `ContenuSansRenseignementDeSanteTest` (refus, absence de faux refus, trois points d'entrée) ; `alertes.test.tsx`.

## ST-H05 — Politique « communications cliniques »

Troisième valeur de `EngagementsDeService.Politique` (`communications-cliniques`). Elle reprend les descriptions et
les résolutions de la politique standard, ajoute une réponse de 4 h en heures comme hors heures, sans plage de soutien
(activité 24/7), un objectif de 95 % et aucun seuil majeur. L'attribution par organisme (superadmin › Soutien) et le
jugement mensuel de disponibilité la prennent sans autre changement : ils parcourent déjà le catalogue.

Essais : `EngagementsDeServiceTest.CommunicationsCliniques`.
