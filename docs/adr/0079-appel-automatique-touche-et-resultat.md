# ADR 0079 — L'appel automatique est un canal d'étape de cascade, confirmé au clavier, tracé par son résultat

- **Statut** : accepté, le 14 novembre 2026.
- **Décideurs** : équipe VINA.
- **Prolonge** : les ADR 0074 (alertes), 0077 (cascades durables) et 0078 (webhook signé) ; ADR 0022 (appels au numéro
  de l'organisme) ; phase R3-a, Q-TEL tranché pour **Twilio**.
- **Concerne** :
  - `alertes/domaine/AppelsAlertes.java` (`Resultat`, `Touche`, `StatutFournisseur`, `ReponduPar`, message vocal) ;
  - `alertes/application/commandes/GestionnaireAppelsAlertes.java` ;
  - `partage/telephonie/AppelsVocauxPort.java`, implanté par `notifications/.../AppelsVocaux.java` ;
  - la tâche durable `alerte.appel`, les rappels `/api/alertes/webhooks/voix` et `/voix/statut` ;
  - le changeset `0015-alertes-appels.xml`.
- **Story** : ST-G06 (FX-AO-20260810, critères 13, 14).

## Contexte

Une alerte critique ne doit pas dépendre d'un écran : si personne ne réagit, un appel doit joindre la personne, et sa
confirmation doit arrêter l'escalade. Le dépôt sait déjà mettre deux personnes en relation par Twilio (ADR 0022).
Il ne sait pas encore dire un message ni attendre une touche.

## Options considérées

1. **Un canal « appel » dans les étapes de cascade, une tâche durable par personne, confirmation au clavier** (retenue).
2. **L'appel imposé par la priorité critique**, comme les autres canaux. Tout un service serait réveillé au téléphone
   dès l'envoi, avant toute relance. Le devis demande un appel « si non-réponse, délai programmable » : c'est une
   étape, pas un canal d'office.
3. **Réutiliser `PorteurAppelSortant`** (mise en relation) : il compose vers un agent, puis relie. Il faudrait le
   détourner, alors que le besoin est un message et une touche.

## Décision

- **`APPEL` est un canal d'étape.** Les canaux d'avis (courriel, texto, poussée) partent en une relance ; l'appel part
  en une tâche durable `alerte.appel` par personne. La priorité critique n'impose jamais l'appel.
- **La voix passe par un port partagé** (`AppelsVocauxPort`), implanté par les notifications, qui détiennent le client
  et la configuration de Twilio. Twilio dit le message en synthèse `fr-CA` dans un `<Gather>` à une touche ; la
  détection de répondeur est active, et le statut de fin rappelle. Sans `APPEL_FOURNISSEUR=twilio`, un simulateur
  trace l'appel sans rien composer.
- **Idempotence** : un appel déjà lancé pour la même alerte, la même étape et la même personne ne se relance pas. Une
  personne qui a déjà répondu n'est pas appelée. Sans numéro lisible sur sa fiche, l'appel est tracé en échec.
- **La touche** : « 1 » prend en charge et « 2 » refuse, par la même porte qu'à l'écran (`GestionnaireReceptionAlertes`),
  ce qui arrête la cascade. Toute autre touche est ignorée et renvoie vers l'application. Une seconde réponse est
  annoncée comme « déjà enregistrée ».
- **Les rappels sont signés** (`X-Twilio-Signature` sur l'URL avec `?appel=…`, plus les seuls paramètres du corps).
  Un statut pour un appel inconnu est ignoré, sans erreur, pour que le fournisseur ne le réessaie pas.
- **Le résultat** est une enum : lancé, répondu au clavier, décroché, messagerie, occupé, sans réponse, échec. Une
  touche reçue reste « répondu », quoi que dise la fin d'appel. La durée est conservée, le numéro ne l'est pas.

## Conséquences

- **Positives** : une escalade « relance par poussée, puis appel au groupe de garde » se compose sans code ; une
  confirmation au clavier arrête tout.
- **Négatives** : la voix exige Twilio (la passerelle HTTP générique et Telnyx n'ont pas de `<Gather>` équivalent
  câblé) ; le message est borné à 400 caractères pour rester audible.
- **Essais** : `AppelsAlertesTest` ; `ControleurCascadesHttpTest` couvre l'étape d'appel, la touche signée, la
  signature fausse, la tâche rejouée, la messagerie, l'absence de numéro, le refus et la touche inconnue ;
  `AdaptateursFournisseursTest` vérifie le TwiML et la requête Twilio ; la BDD prouve que les deux rappels restent
  fermés sans signature.
