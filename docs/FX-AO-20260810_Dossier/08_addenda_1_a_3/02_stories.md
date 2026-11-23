# FX-AO-20260810 — Stories issues des addenda 1 à 3 et du contrat

> Série **ST-H** (après ST-G, [08_plan_implantation](../07_validation_vina_geps/08_plan_implantation.md)). Chaque story
> nomme sa source exacte. Elle ne compte comme faite que si un essai automatisé tient chacun de ses critères
> d'acceptation. Décision d'architecture : [ADR 0085](../../adr/0085-addenda-fx-ao-preavis-suivi-facturation.md).

| Story | Objet | Source | Taille |
|---|---|---|---|
| ST-H01 | Nombre facturable : minimum de 100 utilisateurs par mois | Contrat 3.03 ; bordereau, en-tête | S |
| ST-H02 | Préavis selon la nature du changement, en jours ouvrables | Grille 99 et 99.1 (addenda 1) ; contrat 10.19.01 | M |
| ST-H03 | Suivre sans envoyer : la preuve de livraison aux gestionnaires ; les quatre profils | Addenda 2, Q20 à Q31 | M |
| ST-H04 | Aucun renseignement de santé dans une alerte, un modèle ou une note | Addenda 1, Q1 ; contrat 10.18.03 a | S |
| ST-H05 | Politique de soutien « communications cliniques » : réponse en 4 h, 95 % | Grille 98 ; contrat 10.19.05 et 10.19.07 ; addenda 2, Q13 | S |

---

## ST-H01 — Nombre facturable : minimum de 100 utilisateurs par mois

**Pour** que la facture mensuelle se vérifie contre le contrat. **En tant que** pilote, **je veux** voir, pour un mois,
le nombre d'utilisateurs actifs **et** le nombre facturable.

Critères d'acceptation :
1. Le bilan d'un mois rend `total` (personnes actives), `minimumFacturable` et `facturables = max(minimum, total)`.
2. Le minimum vient de la configuration (`alertes.facturation.minimum-utilisateurs`, 100 par défaut), jamais d'un nombre
   écrit dans le code métier. Un minimum négatif est refusé au démarrage.
3. L'écran « Utilisateurs actifs » affiche les deux nombres et dit pourquoi ils diffèrent quand le minimum s'applique.
4. L'export garde une ligne par personne active, la pièce justificative de la clause 3.03.

## ST-H02 — Préavis selon la nature du changement, en jours ouvrables

**Pour** respecter les critères 99 et 99.1 sans compter à la main. **En tant que** soutien de la plateforme, **je veux**
annoncer une maintenance en déclarant sa **nature**, et que le préavis exigé suive cette nature.

Critères d'acceptation :
1. Trois natures, ensemble fermé : `entretien` (72 h, la règle actuelle), `correctif` (3 jours ouvrables, critère 99.1),
   `rehaussement` (21 jours ouvrables, critère 99).
2. Les jours ouvrables se comptent à Montréal, du lundi au vendredi, hors jours fériés légaux du Québec. Le Vendredi saint
   **et** le lundi de Pâques sont tous deux exclus : compter large allonge le préavis, jamais l'inverse.
3. Une maintenance sans nature reste un `entretien` : les annonces existantes restent valides.
4. Une nature inconnue est refusée (400) en nommant les natures admises.
5. Le refus d'un préavis insuffisant dit la date la plus proche qui l'aurait respecté.
6. La nature est enregistrée (changeset `0019`), rendue dans la liste des maintenances et montrée à l'écran : le critère
   99.1 exige de « communiquer minimalement la nature du changement ».

## ST-H03 — Suivre sans envoyer ; les quatre profils de l'addenda 2

**Pour** que la preuve de livraison soit visible des expéditeurs, des gestionnaires et des pilotes (Q31), sans donner aux
gestionnaires le droit d'envoyer.

Critères d'acceptation :
1. Nouvelle capacité `alertes.suivre` : lire le suivi, sa fiche, sa cascade et son export. Elle est accordée par défaut
   aux mêmes rôles qu'`alertes.envoyer` (admin, direction, gestion) : aucune régression pour les organismes en place.
2. Les routes de lecture du suivi exigent `alertes.suivre`. Envoyer, reprendre une cascade et tenir les notes exigent
   toujours `alertes.envoyer`.
3. `/api/alertes/droits` rend `suivre`. Le menu « Suivi des alertes » s'affiche à qui détient `alertes.suivre`, et
   « Nouvelle alerte » à qui détient `alertes.envoyer`, y compris pour un rôle propre à l'organisme.
4. L'organisme de démonstration FX-AO a les quatre profils :

   | Profil (addenda 2) | Rôle dans VINA GePS | Droits |
   |---|---|---|
   | Pilote | `admin` | Tout, dont les groupes, les cascades et le journal d'audit (Q22, Q24, Q31) |
   | Gestionnaire | `manager` | Suivi et rapports. Son droit d'envoyer reste un choix de l'organisme. |
   | Expéditeur | rôle propre `expediteur` | `alertes.envoyer` et `alertes.suivre` |
   | Destinataire | `employee` | Recevoir, lire, répondre |

5. Un essai prouve qu'un rôle qui n'a que `alertes.suivre` lit le suivi, mais ne peut pas envoyer (403).

## ST-H04 — Aucun renseignement de santé dans une alerte, un modèle ou une note

**Pour** que l'engagement de l'addenda 1, Q1, tienne dans le produit, et pas seulement dans la proposition.

Critères d'acceptation :
1. Un titre ou un corps d'alerte, un modèle d'alerte, une note sur un destinataire qui contient un **numéro d'assurance
   maladie** (quatre lettres puis huit chiffres, espaces admis) est refusé (400). Le message nomme le champ et la raison.
2. Le texte refusé n'est jamais écrit au journal.
3. L'écran « Nouvelle alerte » rappelle, sous le message, qu'aucun renseignement de santé ne doit y figurer.
4. Les essais couvrent la détection, et l'absence de faux positif sur un texte usuel : numéro de local, heure, poste
   téléphonique.

## ST-H05 — Politique de soutien « communications cliniques »

**Pour** juger les délais et la disponibilité contre **ce** contrat, comme la politique « service essentiel » le fait pour
26-015-DO.

Critères d'acceptation :
1. Nouvelle politique `communications-cliniques`, attribuable à un organisme par le superadmin (onglet Soutien).
2. Délai de **réponse de 4 h** à tous les niveaux, en tout temps (critère 98 ; activité 24/7, addenda 2, Q13). Délais de
   **résolution** : ceux de la politique standard (N1 1 h, N2 2 h, N3 4 h, N4 24 h).
3. Objectif de disponibilité : **95 %** (contrat 10.19.07). Aucun seuil de manquement majeur : le contrat n'en définit
   pas.
4. Le jugement mensuel de la disponibilité rend le verdict contre cette politique quand un organisme l'utilise.

---

## Hors code — engagements suivis au planificateur

- Responsable de la sécurité de l'information nommé dans les 10 jours de l'adjudication (10.18.02 d).
- Politique de télétravail et d'appareils mobiles (10.18.02 c).
- Ligne sans frais de 7 h à 18 h, en semaine, et assistance 24/7 pour les incidents critiques (10.19.02, critère 101).
- Attestation de destruction selon l'annexe 10.18.04 C (60 jours après la fin du contrat).
- RACI, plan d'implantation et ordre de déploiement des six sites (addenda 2, Q8 et Q33 ; critère 102).
