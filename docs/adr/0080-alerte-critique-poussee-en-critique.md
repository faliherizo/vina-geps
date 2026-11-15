# ADR 0080 — Une alerte critique se pousse en critique, par une étiquette, sans changer la forme des poussées

- **Statut** : accepté, le 14 novembre 2026.
- **Décideurs** : équipe VINA.
- **Prolonge** : l'ADR 0029 (avis par événement), l'ADR 0074 (alertes, règle R8 : la priorité critique impose les
  canaux) ; phase R3-b.
- **Concerne** :
  - `notifications/domaine/Evenements.java` (événement `alerte.critique`, `etiquetteDe`) ;
  - `notifications/infrastructure/persistance/DepotAbonnementsPoussee.java` (record `Poussee`, surcharge) ;
  - `NotifierEvenement`, `DepotNotifications`, `PousseeExpoHttp` ;
  - `frontend/public/sw.js` ;
  - `alertes/application/commandes/GestionnaireEnvoiAlertes.evenementDe` et la relance de cascade.
- **Story** : ST-G07 (FX-AO-20260810, critères 18, 22).

## Contexte

Une alerte critique doit se faire remarquer même téléphone en silencieux. Android le permet par un canal de
notification à importance maximale, déclaré par l'application ; iOS, par le droit *Critical Alerts*, qu'Apple accorde
au cas par cas (Q-ALC, sans réponse à ce jour). Le serveur envoie déjà des poussées natives (Expo) et web.

L'analyse d'impact classe la charge de poussée `PousseeWeb.Charge` en risque **critique** : 77 symboles, 185 flux.
L'élargir est hors de proportion pour un drapeau.

## Options considérées

1. **Un événement d'avis `alerte.critique`, et l'étiquette de la charge** (retenue). L'étiquette existe déjà dans la
   charge (`"notification"` partout) ; elle vaut `"critique"` pour cet événement.
2. **Un champ `critique` dans `Charge`** : explicite, mais il touche tout ce qui construit une charge (risque critique).
3. **Un drapeau dans les variables de l'avis** : les variables servent au texte ; y loger un comportement serait
   invisible.

## Décision

- **Le catalogue décide.** `alerte.critique` est un événement essentiel. `Evenements.etiquetteDe(code)` rend
  `critique` pour lui et l'étiquette ordinaire pour tout autre. Ajouter un événement critique plus tard, c'est
  l'étendre, sans toucher aux envois.
- **Les alertes choisissent l'événement par la priorité** (`evenementDe`, un `switch` exhaustif) : une alerte
  critique, sa copie par courriel et chacune de ses relances de cascade se publient sous `alerte.critique`. Une relance
  planifiée avant ce changement, sans événement dans sa charge, garde l'événement ordinaire.
- **Expo** : priorité haute, `channelId: critique`, `interruptionLevel: critical`, et le son critique (`critical: true`,
  volume 1). iOS ignore les deux derniers sans le droit d'Apple : la poussée arrive alors comme une autre.
- **Navigateur** : la notification reste affichée jusqu'à ce qu'on y touche (`requireInteraction`), avec une vibration
  prolongée. Un navigateur ne contourne pas le mode silencieux.
- **La règle R8 tient** : la priorité critique impose tous les canaux d'avis, et les deux événements d'alerte sont
  essentiels. Une personne ne les ferme pas.

## Conséquences

- **Positives** : le serveur est prêt pour Android et pour iOS ; aucune forme existante ne change.
- **Négatives** : l'étiquette porte deux sens (regroupement et urgence). Elle reste un ensemble fermé de deux
  constantes nommées.
- **Hors de ce dépôt** : l'application mobile doit déclarer le canal Android `critique`, à importance maximale, avec le
  contournement du « ne pas déranger » si l'utilisateur l'autorise. iOS attend Q-ALC.
- **Repli documenté** : tant qu'un appareil n'honore pas le critique, la cascade (ST-G04) enchaîne texto puis appel
  automatique (ST-G06). C'est elle qui garantit qu'une alerte critique ne reste pas sans réponse.
- **Essais** : `AdaptateursFournisseursTest` (charge Expo critique, étiquette par événement, événement essentiel) ;
  `AlertesTest` (événement selon la priorité) ; `ControleurAlertesHttpTest` (une alerte critique se publie sous
  `alerte.critique`).
