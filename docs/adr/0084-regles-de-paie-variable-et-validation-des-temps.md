# ADR 0084 — Règles de paie variable par organisme, validation des temps par la paie, fiche de paie PDF

- **Statut** : accepté, le 15 novembre 2026.
- **Décideurs** : équipe VINA, à la demande du responsable du produit.
- **Prolonge** : [ADR 0076](0076-module-de-paie-quebecois.md) (module de paie québécois).
- **Conception détaillée** : [docs/paie/CONCEPTION-PAIE-RH-COMPTABLE.md](../paie/CONCEPTION-PAIE-RH-COMPTABLE.md).
- **Concerne** :
  - le domaine `paie` : `ReglePaie`, `TypeRegle`, `CalculGains`, `HeuresAdmissibles`, `Gain`, `ControleTemps`,
    `ValidationTemps` ;
  - les ports partagés `HeuresApprouveesPort` (plages, jours fériés, feuilles d'une période, retourner) et
    `PersonnesPourPaiePort` (titre d'emploi, convention) ;
  - le changeset `0018-paie-regles-et-validation.xml` ;
  - la politique d'accès (`paie.gerer` ouverte à `rh` et `accountant`, nouvelle capacité `paie.configurer`) ;
  - les écrans « Règles de paie variable » et « Validation des temps », et le PDF des fiches et du registre.

## Contexte

Le responsable du produit demande comment un profil **RH** ou **comptable** produit la paie d'un personnel de la
santé : valider les temps, produire la paie, exporter la fiche de paie en PDF. Il veut aussi tenir des **règles de paie
variable** dans l'espace d'administration de l'organisme, « comme dans Nexu VINA ».

Trois obstacles l'empêchaient :

1. `paie.gerer` n'était accordée qu'à l'administration ;
2. le brut ne savait faire que « heures × taux saisi », alors que la santé paie selon une **échelle** (titre d'emploi
   × échelon) et des **primes** de soir, de nuit, de fin de semaine et de férié ;
3. rien ne séparait l'approbation du gestionnaire du contrôle de la paie, et aucune fiche ne sortait en PDF.

Nexu VINA (lecture seule, `varpay/domaine/ReglesPaie.java`) résout un taux par (activité, échelon, profil) : un
multiplicateur ou un taux fixe, sur une fenêtre de validité. Ses activités sont scolaires, et il ne sait rien des
plages horaires.

## Options considérées

### Règles variables

1. **Un catalogue de règles typées (`taux-de-base`, `prime-pourcentage`, `prime-horaire`, `forfait`), avec des
   conditions facultatives combinées par « et » et une fenêtre de validité** (retenue). C'est la forme de Nexu VINA,
   avec des conditions propres à la santé : titre d'emploi, convention, échelon, plage horaire, jours, férié, code de
   paie.
2. **Réutiliser le moteur de règles des effectifs** (`ConditionRegle`, arbres `tous` / `un_de` / `non`). Il juge des
   affectations et rend une action (accepter, avertir, bloquer). Il ne produit pas de montant, et le lier à la paie
   ferait dépendre `paie` d'`effectifs` par son domaine, ce qu'`ArchitectureTest` refuse.
3. **Des primes codées en dur (soir 4 %, nuit 12 %…)** : chaque convention et chaque renouvellement exigeraient une
   livraison. C'est contraire au barème-donnée de l'ADR 0076.
4. **Une table d'échelle séparée des règles** : plus lisible en base, mais deux écrans, deux ports et deux résolutions
   de dates pour la même notion de « valeur en vigueur pour ce profil à cette date ». L'échelle reste un type de règle,
   et l'écran l'affiche à part.

### Validation des temps

1. **Une validation par la paie, enregistrée dans `paie`, qui porte l'empreinte des heures validées, et un réglage qui
   l'exige** (retenue).
2. **Un nouveau statut `VALIDEE` des feuilles dans les effectifs** : il toucherait la machine à états des feuilles, ses
   flux d'approbation, ses écrans et ses essais, pour un contrôle qui appartient à la paie.
3. **Ouvrir l'approbation des feuilles (`approval.decide`) aux RH et aux comptables** : cela confondrait les deux
   contrôles, et le comptable approuverait aussi des absences.

## Décision

- **Règles** : option 1. Le taux de base d'une heure est celui du **dossier** s'il est saisi (hors échelle), sinon celui
  de la règle `taux-de-base` **en vigueur à la date de l'heure**, la plus spécifique (échelon, puis convention, puis la
  plus récente). Sans taux, la personne est nommée dans `sansTaux` et n'est pas payée. Les primes se calculent sur les
  heures admissibles (chevauchement plage × jour, en heure de Montréal) au taux de base de la même date.
- **Gains** : le bulletin porte la liste de ses gains (`REG`, `HS`, `SAL`, une ligne par prime), figée en JSON avec lui.
  Le brut est leur somme, et les retenues s'y appliquent sans changement.
- **Validation** : option 1. Elle est valide tant que l'empreinte (SHA-256 des lignes de paie) n'a pas changé ;
  autrement, l'anomalie `validation-perimee` s'affiche et la feuille sort de la paie. **Retourner** passe par le port, qui
  rouvre la feuille dans les effectifs avec le motif. Le réglage `validationTempsExigee` vaut faux par défaut, ce qui
  garde le comportement de l'ADR 0076.
- **Droits** : `paie.gerer` est accordée à `admin`, `rh` et `accountant` (validation, cycles, PDF, remises, relevés) ;
  `paie.configurer` à `admin` et `rh` (règles, dossiers, réglages, barème). Le comptable paie, mais il ne change ni les
  règles ni les taux.
- **PDF** : la fiche de paie (une page : employeur, personne, période, gains, retenues, net, cumuls de l'année,
  cotisations de l'employeur) et le registre du cycle (une ligne par personne, totaux), en PDF/A-2b par `DocumentPdf`.
  Une personne ne télécharge que ses propres fiches, et seulement celles d'un cycle traité.

## Conséquences

- **Positives** :
  - un service de la paie travaille sans compte d'administration ;
  - une convention se paramètre sans livraison ;
  - une augmentation en milieu de cycle se paie juste, heure par heure ;
  - on ne paie plus d'heures que la paie n'a pas vues, même après une réouverture.
- **Négatives** :
  - les plages en heure locale exigent que les effectifs livrent le début et la fin des segments, et non plus seulement
    des totaux par jour ;
  - les primes sont traitées comme des gains assujettis à toutes les retenues. C'est vrai des primes de quart, mais un
    gain exonéré exigerait un attribut de plus ;
  - le jour d'une heure de nuit est le jour civil où elle tombe, et non celui où le quart a commencé ; une convention
    qui rattache tout le quart à son jour de début demanderait une option.
- **Essais** :
  - domaine : `CalculGainsTest` et `HeuresAdmissiblesTest` (plages qui passent minuit, fin de semaine, férié, taux qui
    change en cours de cycle), `ControleTempsTest` ;
  - HTTP : règles, validation, PDF, droits par rôle ;
  - e2e : `paie-variable.spec.ts` (le comptable valide, prépare et traite ; la personne télécharge sa fiche).
