# ADR 0093 — Résolution d'identité par (émetteur, sujet) et liaison des comptes

- **Statut** : accepté, le 22 novembre 2026.
- **Décideurs** : équipe VINA, à la demande du responsable produit.
- **Prolonge** : [ADR 0075](0075-annuaires-oidc-generiques.md) (annuaires OIDC génériques) et
  [ADR 0083](0083-annuaire-ldap-joint-en-ldaps.md) (LDAPS).
- **Concerne** :
  - `auth/domaine` : `IdentitesExternes`, `DemandesLiaison`, `Oidc`, `Saml`, `Annuaires` ;
  - `auth/application` : `ServiceResolutionIdentite`, `ServiceApprobationsLiaison`, `ServiceAnnuaires` ;
  - `auth/infrastructure` : `ServiceSso`, `EchangeurOidcHttp`, les contrôleurs `ControleurSso`, `ControleurAnnuaires` et
    `ControleurLiaisonIdentite`, les dépôts `DepotIdentitesExternes` et `DepotDemandesLiaison` ;
  - le changeset Liquibase `0026-auth-identites-externes.xml` et son retour `db/retour/0026-retour.sql` ;
  - l'interface : la page `RelierCompte`, l'option juste-à-temps et les demandes de liaison dans `AnnuairesOrganisme`.

## Contexte

Après une connexion OIDC (Entra ID, Keycloak) ou SAML, le serveur cherchait le compte **par le courriel**. S'il n'en
trouvait aucun, il en **créait un**, d'office. Trois défauts en découlaient :

1. **Prise de compte par le courriel.** Un fournisseur laisse souvent modifier le courriel, et deux fournisseurs peuvent
   déclarer le même. Une personne qui obtient le courriel d'un administrateur chez un annuaire déclaré entrait dans son
   compte.
2. **Comptes en double.** Jean a déjà un compte. Si son courriel diffère, même d'une lettre, sa première connexion par
   Entra lui crée un second compte, sans historique ni droits.
3. **Création non voulue.** Tout membre authentifié d'un annuaire devenait un compte de l'application, sans décision de
   l'administration.

Le balayage des accès du 22 novembre 2026 a constaté, dans le même code, un défaut plus grave encore :

4. **Mode simulé par défaut.** Le mode « simulé » d'Entra s'activait par la seule **absence** de `AZURE_CLIENT_ID`. Tout
   déploiement sans Entra l'avait donc. `POST /api/auth/sso/entra/session`, sans session, rendait une session et un compte
   à quiconque l'appelait.
5. **Code simulé accepté en mode réel.** `completerEntra` décodait un code `mock.…` même en mode réel ou démonstration. Avec
   un `state` valide (obtenu par `/sso/entra/login`) et le courriel d'un administrateur écrit dans le code, n'importe qui
   ouvrait la session de cet administrateur.

Le responsable produit a fixé le comportement attendu à la première connexion de Jean. Entra authentifie Jean, et le
serveur ne trouve pas `(iss, sub)`. Il ne crée pas de second compte. Le courriel sert d'**indice** au plus. Il faut une
preuve de possession du compte existant : une invitation en cours, le mot de passe saisi dans le même flux, ou une
validation par l'administration. Le serveur relie ensuite `(iss, sub)` au compte.

## Options considérées

1. **Table d'identités externes, résolution par `(émetteur, sujet)`, liaison sur preuve** (retenue).
2. **Garder la recherche par courriel, en n'acceptant que `email_verified`.** C'est déjà exigé par les annuaires, et cela ne
   règle rien. Un courriel vérifié chez un fournisseur reste modifiable, et peut appartenir à une autre personne chez un
   autre fournisseur. Les doublons demeurent.
3. **Lier automatiquement au compte de même courriel, à la première connexion.** C'est la prise de compte du point 1, rendue
   permanente : la première connexion frauduleuse gagne.
4. **Utiliser `oid` (Entra) plutôt que `sub`.** `oid` est propre à Entra : Keycloak et SAML ne le portent pas. `sub` est
   le sujet standard. Pour Entra, il est stable pour une application, ce qui convient : une inscription d'application
   par annuaire.
5. **Garder la liaison en cours dans un jeton signé, transmis dans l'adresse.** Le jeton apparaîtrait dans l'historique, les
   journaux du mandataire et l'en-tête `Referer`. Un témoin `HttpOnly`, `SameSite=Strict`, limité aux routes de liaison,
   n'est ni lisible par une page ni présentable par un site tiers.

## Décision

### Résolution

- **Table `auth_identites_externes`.** Elle a pour colonnes `issuer`, `sujet`, `userId`, `tenantId`, `annuaireId`,
  `lieeLe`, `lieePar`, `preuve` et `derniereConnexion`, et pour clé primaire `(issuer, sujet)`.
- **Retour du fournisseur.** Une fois le jeton vérifié, `ServiceResolutionIdentite.resoudre` cherche `(iss, sub)` :
  - **trouvé** : le compte est autorisé (`IdentitesExternes.autoriser` : actif, et de l'organisme de l'annuaire), son rôle
    suit l'annuaire, et la dernière connexion est notée ;
  - **introuvable** : aucun compte n'est créé, sauf si l'annuaire autorise le provisionnement juste-à-temps (voir plus
    bas). Sinon, une **liaison en cours** est posée : dix minutes, cinq essais. La personne est envoyée à la page
    `/connexion/relier`.
- **Les trois preuves**, toutes à `/api/auth/sso/liaison/…` :
  - **(a) invitation** : le lien reçu par courriel. L'invitation doit être offerte, adressée au courriel vérifié et de
    l'organisme de l'annuaire. Le compte invité naît relié.
  - **(b) mot de passe** du compte existant, sous le verrou du pare-feu comme la connexion. Sans identifiant saisi, le
    courriel vérifié en tient lieu.
  - **(c) approbation** : la demande vise le compte que le courriel désigne dans l'organisme. Elle attend dans
    `auth_demandes_liaison`. L'administrateur de l'organisme l'approuve ou la refuse (`/api/auth/liaisons`, section
    « Demandes de liaison d'identité » de l'écran Organisme). Rien n'est relié avant.
- **Provisionnement juste-à-temps.** C'est une option de chaque annuaire (`provisionnementJat`), **désactivée par
  défaut**. Activée, elle crée le compte avec l'organisme et le rôle de l'annuaire. Elle ne le fait jamais si un compte de
  l'organisme porte déjà ce courriel : la preuve est alors exigée. Le fournisseur d'instance (Entra ou SAML réglé par
  l'environnement) suit `SSO_PROVISIONNEMENT_JAT`, faux par défaut. Seuls la démonstration embarquée et le mode simulé,
  qui n'authentifient personne de réel, provisionnent d'office.
- **LDAPS.** La clé est `(adresse LDAPS, identifiant en minuscules)`. SAML prend `(émetteur de l'IdP, NameID)`.

### Vérification du jeton

`EchangeurOidcHttp.verifierIdToken` fait les contrôles suivants :

- signature RS256 par le JWKS ;
- `aud` = client ;
- `iss` = l'émetteur du document de découverte ;
- pour un fournisseur générique, émetteur publié = émetteur de l'annuaire (OIDC Discovery § 4.3) ;
- `nonce` = celui de la demande, présent dans l'URL d'autorisation ;
- `exp` **exigé**, avec 60 s de tolérance d'horloge ;
- `sub` exigé ;
- `azp` = client quand `aud` en nomme plusieurs.

### Mode simulé, SAML

- **Mode simulé d'Entra.** Il n'existe que demandé (`SSO_SIMULE=1`) **et** en essai (`NEXU_ESSAI`). Sans configuration,
  Entra d'instance est **désactivé** : son statut répond `enabled: false`, et ses routes répondent 404.
- **Code simulé.** Hors du mode simulé, il est refusé en 401, avant tout échange.
- **`POST /sso/entra/session`.** Il n'existe qu'en mode simulé.
- **SAML.** Il n'avait pas de mode simulé, et les assertions non signées étaient déjà refusées. Mais sans
  `SAML_IDP_ISSUER`, l'émetteur de l'assertion n'était pas vérifié. L'émetteur est désormais exigé pour activer SAML.

### Session et journal

- **Témoin de session.** Il est inchangé : `HttpOnly`, `SameSite=Lax`, `Secure` si `nexu.cookie.secure`.
- **Témoin de liaison.** Il est `HttpOnly`, `SameSite=Strict`, `Path=/api/auth/sso/liaison`, avec dix minutes de durée.
- **Événements consignés.** Ils sont écrits sans mot de passe ni jeton, et le courriel est masqué tant que rien n'est
  relié :
  - `auth.sso.liaison.requise` ;
  - `auth.sso.lie`, avec la preuve et l'émetteur ;
  - `auth.sso.refus`, avec le motif ;
  - `auth.sso.liaison.demandee`, `.approuvee` et `.refusee` ;
  - `auth.sso.login.ok`.

## Conséquences

- **Première connexion par annuaire.** Un compte existant n'est plus jamais repris sur la foi d'un courriel. La première
  connexion par annuaire demande une preuve, une seule fois. Ensuite, le compte est retrouvé même si le courriel change
  chez le fournisseur.
- **Annuaires existants.** Ils ne créent plus de comptes. L'administration qui le veut coche « Créer le compte à la
  première connexion ». Les essais OIDC contre Keycloak (`oidc-keycloak.spec.ts`) le font.
- **Comptes déjà créés.** Les comptes nés de l'ancien comportement n'ont pas d'identité reliée. Leur prochaine connexion
  passe par la liaison : leur mot de passe est aléatoire, donc la preuve passe par l'invitation ou l'approbation. Une reprise
  ponctuelle peut relier ces comptes par script, sur décision de l'organisme. Elle n'est pas automatisée, pour ne pas
  refaire le défaut 1.
- **Déploiements sans Entra.** Ils perdent le bouton Microsoft simulé. La campagne e2e le demande par `SSO_SIMULE=1`.
- **Liaisons en cours.** Elles vivent en mémoire du processus, comme les demandes OIDC. En multi-instance, il faut une
  affinité de session ou un cache partagé. Les demandes d'approbation, elles, sont en base.
- **Écarts connus :**
  - le sujet LDAPS est l'identifiant de connexion, réattribuable dans AD. `objectGUID` serait plus sûr, mais la liaison
    LDAPS ne le lit pas encore ;
  - le fournisseur d'instance, sans organisme, ne restreint pas l'organisme du compte relié par mot de passe ;
  - le retour arrière (`0026-retour.sql`) efface les liaisons.
