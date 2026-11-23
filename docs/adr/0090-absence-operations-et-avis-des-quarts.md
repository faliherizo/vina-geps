# ADR 0090 — L'absence constatée aux opérations et l'avis des quarts modifiés

- **Statut** : accepté, le 22 novembre 2026.
- **Décideurs** : équipe VINA.
- **Prolonge** : [ADR 0088](0088-api-echanges-organisme-machine.md) (API d'échange de l'organisme).
- **Concerne** :
  - `effectifs/domaine` : `EchangesApi.cibles`, `EchangesApi.AbsenceOperations`, `AbsencesEffectifs.Origine` ;
  - `effectifs/application` : `GestionnaireAbsencesEffectifs.constater`, `GestesEchangesApi.absence`, `JournalAvecAvisDesQuarts`, `LectureEchangesApi` ;
  - `effectifs/infrastructure` : `DepotEchangesApi`, `ControleurEchangesApi`, `ConfigurationIntegrationsEffectifs` ;
  - `integrations/domaine/Webhooks` (événement `effectifs.quart.modifie`).
- **Story** : AD-06c (26-015-DO, addenda 5 b et d ; E-TN-7, P-TN-17).

## Contexte

L'addenda 5 demande deux choses que l'API d'AD-06a et AD-06b ne faisait pas encore :

- **(b)** que l'organisme soit *informé des modifications sur les quarts en quasi temps réel* ;
- **(d)** qu'il puisse *transmettre des modifications sur les quarts réels, par exemple une absence déclarée aux opérations*.

Deux défauts sont apparus à l'examen :

1. **Des quarts modifiés n'apparaissaient pas parmi les correctifs.** L'absence qui rend des places vacantes consigne
   `places.vacantes` sous l'objet `absence`, et l'échange qui permute deux titulaires consigne `echange.applique` sous
   l'objet `echange`. La lecture des correctifs ne retenait que les objets `equipage` et `place` : un quart vidé par une
   absence ne sortait jamais. C'est pourtant le correctif le plus utile au répartiteur.
2. **Une absence ne pouvait entrer que comme une demande.** Elle passait donc par les règles, qui peuvent la refuser, et
   par le préavis, qui refuse un code non planifiable déclaré d'avance. Or une absence constatée aux opérations est un fait,
   pas une demande.

## Options considérées

### Pour l'avis (b)

1. **Un décorateur du journal qui annonce chaque écriture touchant un quart exposé** (retenue). Le journal est déjà la
   source unique des correctifs et du relais vers le bus. Tout geste, présent ou à venir, qui consigne une place est annoncé
   sans y penser.
2. **Appeler l'émetteur dans chaque gestionnaire** (places, échanges, absences, offres, équipages). C'est sept points
   d'appel, dont chacun peut être oublié par le prochain geste ; c'est précisément le défaut 1, en pire.
3. **Un relais périodique du journal vers les webhooks**, comme celui du bus. Il ajoute un curseur et une tâche de plus. Son
   délai s'ajoute à celui de l'ordonnanceur, et il n'apporte rien que la lecture par curseur ne donne déjà.
4. **Un avis gras, portant l'état du quart.** Plusieurs écritures d'un même geste se coalescent en un avis, qui
   porterait alors l'état d'avant la dernière écriture. Un avis mince, qui renvoie à la lecture, ne ment jamais.

### Pour l'absence (d)

1. **Une origine de l'absence (`DEMANDE`, `OPERATIONS`) dans le même gestionnaire** (retenue). Les validations restent les
   mêmes : code admissible, chevauchement, journal. Les règles sont évaluées et leur verdict consigné (un solde dépassé
   reste visible), mais seule la demande en dépend.
2. **Créer une demande, puis la déroger d'office.** Deux entrées au journal pour un seul fait, et une dérogation qui
   exige un motif humain qu'aucune application ne donne.
3. **Une table distincte des absences constatées.** Le comblement, les vues et le relevé de présence devraient alors en
   lire deux.

## Décision

- **Les cibles d'une entrée** : `EchangesApi.cibles(action, objetType, objetId, apres)` dit quels quarts une entrée du
  journal touche :
  - l'objet lui-même, pour un équipage ou une place ;
  - les places nommées par `places.vacantes` (`apres.places`) et par `echange.applique` (les clés de `apres`).

  La lecture des correctifs rend un correctif par place, sous le curseur de son entrée. Le dépôt retient aussi ces deux
  actions.
- **L'avis** : le décorateur `JournalAvecAvisDesQuarts` est le `JournalEffectifsPort` principal (`@Primary`). Après
  l'écriture, il résout les places en équipages, ne garde que les quarts **exposés** (période publiée ou close) et émet
  `effectifs.quart.modifie`. L'avis contient `quartId`, `date`, `uniteCode`, `action`, `horodatage` et `lire` (le chemin
  des correctifs). La clé `quartId@instant` fait qu'un geste produit un seul avis par quart. La livraison est celle de
  tout webhook : signature HMAC, reprises, suspension. Elle part au cycle suivant de l'ordonnanceur
  (`ORDONNANCEUR_INTERVALLE_MS`, 60 s par défaut, abaissable).
- **L'absence constatée** : `POST /api/echanges/v1/employes/{matricule}/absences` (portée `write`) reçoit
  `{quartId | du[, au], codeAbsence, motif}`. Sur un quart, elle en prend la date et exige que l'employé y tienne une
  place (409 sinon). `Origine.OPERATIONS` accepte d'office, sans exigence de préavis, et consigne `absence.constatee`,
  relayée au bus comme `effectifs.absence.acceptee`. La même absence renvoyée (même code, mêmes dates, active) est
  rendue telle quelle (200) ; toute autre absence chevauchante reste un conflit (409).

## Conséquences

- **Positives** :
  - L'organisme peut se passer de l'interrogation périodique. Le webhook l'avise, et la lecture par curseur reste
    l'unique source de vérité ainsi que le moyen de rattraper un avis perdu.
  - Le défaut des correctifs manquants est fermé aussi pour la lecture par curseur, indépendamment des webhooks.
  - Un geste futur qui consigne une place est annoncé sans développement.
- **Négatives** :
  - Une écriture touchant un quart coûte une ou deux lectures indexées de plus (places → équipages, quarts exposés).
  - L'avis attend le cycle de l'ordonnanceur, 60 s au plus par défaut.
  - Une absence aux opérations couvre des jours entiers, comme toute absence d'effectifs : un départ en cours de quart
    se transmet comme l'absence du jour.
- **Non couvert** :
  - La publication d'une période n'est pas annoncée ; ses quarts se lisent par `GET /quarts`.
  - L'avis exige le module `integrations`, où se déclarent les webhooks.
- **Essais** :
  - `EchangesApiTest.cibles`, `.absenceOperations` ;
  - `AbsencesEffectifsDomaineTest.origine`, `.laMeme` ;
  - `JournalAvecAvisDesQuartsTest`, `LectureEchangesApiTest.plusieursPlaces` ;
  - `ControleurAbsencesEffectifsHttpTest.absenceConstateeAuxOperations`, `.placeVacanteAnnoncee` ;
  - BDD `11-technologie.feature` (AD-06c).
