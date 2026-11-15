# ADR 0082 — Le courriel peut partir par Microsoft Graph ; LDAPS est différé tant que la fédération couvre l'annuaire

- **Statut** : accepté, le 15 novembre 2026.
- **Décideurs** : équipe VINA.
- **Concerne** : `partage/courrier/infrastructure/CourrierGraph.java`, `ConfigurationCourrier.java`, `.env.exemple`.
- **Story** : ST-G11 (FX-AO-20260810, critères 40 et 82).

## Contexte

Le client exploite Microsoft 365. Le courriel part aujourd'hui par SMTP (Outlook 365 est admis comme relais). Or
Microsoft retire progressivement l'authentification SMTP de base, et recommande Graph pour les applications. Le
devis demande aussi, « si exigé », LDAPS pour synchroniser un annuaire local.

## Options considérées

**Courriel**
1. **Un troisième adaptateur du port `Courrier`, par Graph et la permission d'application `Mail.Send`** (retenue).
2. **SMTP OAuth (XOAUTH2) vers Outlook** : il faudrait un jeton délégué par boîte ; Microsoft en restreint l'usage.
3. **Le SDK Graph** : une dépendance lourde pour deux requêtes HTTP.

**Annuaire**
1. **Différer LDAPS ; l'annuaire passe par OIDC ou SAML** (retenue). L'ADR 0075 couvre Entra ID, Keycloak et AD FS,
   donc un AD local fédéré.
2. **Bâtir une synchronisation LDAPS maintenant.** C'est un geste sensible (création et désactivation de comptes). Il
   ne s'éprouve que contre un serveur LDAP réel ; aucun n'est disponible dans la chaîne de construction (UnboundID
   absent du dépôt Maven). Livrer sans pouvoir le prouver serait livrer à l'aveugle.

## Décision

- **`CourrierGraph`** obtient un jeton d'application par le flux « client credentials » (`/oauth2/v2.0/token`,
  portée `https://graph.microsoft.com/.default`). Il le garde jusqu'à une minute de son expiration, puis envoie par
  `POST /v1.0/users/{boîte}/sendMail` : texte brut, un destinataire, rien dans les éléments envoyés.
- **Le choix reste de la configuration** : Graph si `GRAPH_CLIENT_ID` est posé, sinon SMTP si `SMTP_HOST` l'est,
  sinon le journal. `MAIL_FROM` nomme la boîte ; l'adresse est extraite d'un `Nom <adresse>`.
- **Comme SMTP, un refus ne lève jamais** : réponse non 202, jeton refusé, service injoignable ou fil interrompu. Il
  se journalise avec le destinataire masqué, et le geste métier continue.
- **Côté Entra ID** (à faire par l'organisme) : inscrire l'application, accorder `Mail.Send` en permission
  d'application avec le consentement de l'administrateur, et la **restreindre à la boîte d'expédition** par une
  stratégie d'accès d'application. Sans cette restriction, l'application pourrait écrire au nom de n'importe quelle
  boîte du locataire.
- **LDAPS est différé**, et la question Q-LDAP est posée au client : la fédération OIDC ou SAML de son AD
  suffit-elle ? Si non, la synchronisation se bâtira avec un serveur UnboundID embarqué dans les essais.

## Conséquences

- **Positives** : le courriel suit la voie que Microsoft recommande ; aucune dépendance ajoutée (client HTTP du JDK).
- **Négatives** : un secret client de plus à tenir et à renouveler (`GRAPH_CLIENT_SECRET`) ; le critère 82 reste
  partiel jusqu'à la réponse à Q-LDAP.
- **Essais** : `CourrierGraphTest`, contre un faux Entra et un faux Graph sur un port local. Il couvre le jeton, son
  cache et son renouvellement, la forme de `sendMail`, les refus (403, jeton 401, service arrêté, fil interrompu), le
  choix du transport et l'extraction de l'adresse.
