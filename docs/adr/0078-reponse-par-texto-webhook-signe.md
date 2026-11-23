# ADR 0078 — Un texto répond à la dernière alerte ouverte vers ce numéro, par un webhook signé, et un mot inconnu ne vaut jamais confirmation

- **Statut** : accepté, le 14 novembre 2026.
- **Décideurs** : équipe VINA.
- **Prolonge** : l'ADR 0074 (contexte `alertes`), phase R2-b ; Q-TEL tranché pour **Twilio** (14 novembre 2026).
- **Concerne** :
  - `alertes/domaine/TextosEntrants.java` et le port `TextosEntrantsPort` ;
  - `alertes/application/commandes/GestionnaireTextosEntrants.java` ;
  - `alertes/infrastructure/web/ControleurWebhooksAlertes.java` et `persistance/DepotTextosEntrants.java` ;
  - `partage/telephonie/Telephonie.java` (E.164, signature Twilio), vers lequel délèguent les notifications ;
  - le changeset `0014-alertes-textos-entrants.xml`.
- **Story** : ST-G05 (FX-AO-20260810, critères 4, 9, 10).

## Contexte

Un destinataire sans l'application doit pouvoir confirmer depuis n'importe quel téléphone. Le texto entrant arrive par
un rappel public du fournisseur, sans session. Trois risques : une fausse prise en charge (requête forgée), une
double prise en compte (rappel rejoué), et une confirmation devinée (« ok merci » lu comme « OK »).

## Options considérées

1. **Rattacher le texto à la remise sans réponse la plus récente du numéro** (retenue).
2. **Exiger un code d'alerte dans le texto** (« 1 A42 ») : plus sûr si plusieurs alertes sont ouvertes, mais trop
   lourd au milieu d'une intervention ; le devis demande « 1 » ou « OK ».
3. **Un numéro d'envoi par alerte** : rattachement exact, mais un coût de numéros sans rapport avec le volume.

## Décision

- **La garde, c'est la signature.** `X-Twilio-Signature` est vérifiée avant toute lecture (HMAC-SHA1 de l'URL
  publique et des paramètres triés, jeton `TWILIO_AUTH_TOKEN`). Sans jeton configuré, tout est refusé. Le débit est
  borné par le pare-feu applicatif, commun à toutes les routes.
- **Le rattachement** : la remise sans réponse la plus récente dont le destinataire a ce numéro (fiche du personnel,
  normalisée en E.164). Une fois cette alerte répondue, le texto suivant répond à la précédente, si elle attend encore.
- **L'intention** : « 1 », « OK », « oui » valent prise en charge ; « 2 », « NON » valent refus. Accents, casse et
  ponctuation ne comptent pas ; un mot de plus rend le texto **inconnu**. Un texto inconnu reçoit l'aide et n'est
  jamais compté. Si l'alerte ne propose pas la réponse visée, le texto le dit.
- **La réponse passe par la même porte qu'à l'écran** (`GestionnaireReceptionAlertes`). Elle arrête donc une cascade
  comme une réponse à l'écran, et la règle « une seule réponse » s'applique.
- **Le rejeu** : chaque `MessageSid` est consigné. Un message déjà vu est ignoré, sans texto de retour.
- **Rien du contenu n'est conservé** : seulement l'issue (prise en charge, refus, aide, sans alerte, déjà répondu, non
  proposée).
- **La signature et la normalisation des numéros** passent dans `partage/telephonie`. Les notifications y délèguent,
  sans changer leurs signatures : `alertes` ne dépend pas de `notifications`.

## Conséquences

- **Positives** : critère 4 et confirmation d'un geste par texto (critère 9) ; aucune prise en charge sans signature
  ni sans mot clair.
- **Négatives** : avec deux alertes ouvertes, le texto répond à la plus récente. C'est annoncé dans la réponse, qui
  cite le titre de l'alerte visée. Un numéro partagé par deux fiches répond pour la plus récente remise.
- **Essais** : `TextosEntrantsTest` ; `ControleurWebhooksAlertesHttpTest`, avec une vraie signature calculée comme
  Twilio. Il couvre « OK » sur la plus récente, puis « 2 » sur la précédente, un titre échappé en TwiML, un mot
  inconnu, le rejeu, une signature fausse, un numéro inconnu ou illisible, une réponse non proposée et une réponse
  déjà donnée à l'écran. La BDD prouve que la porte reste fermée sans signature.
